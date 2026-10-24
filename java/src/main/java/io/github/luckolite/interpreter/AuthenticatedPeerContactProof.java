// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Classifies only contacts already present in the same authenticated finite-body discovery.
 * It never synthesizes members or promotes unmatched dark pixels.
 */
final class AuthenticatedPeerContactProof {
    private static final long MAX_MEMBERS = 500_000L;
    private static final long MAX_NEIGHBOR_VISITS = 4_500_000L;

    private AuthenticatedPeerContactProof() {}

    record Result(
            boolean complete,
            long members,
            long peerContacts,
            long staffContacts,
            long unknownContacts,
            String refusal) {}

    static Result classify(
            byte[] gray,
            int width,
            int height,
            int gap,
            List<MaximalRawBarExtractor.Body> bodies,
            Set<Integer> staff,
            SparseStaffContact.Budget budget)
            throws SparseStaffContact.Unknown {
        if (gray == null
                || width <= 0
                || height <= 0
                || (long) width * height != gray.length
                || gap < 5
                || gap > 64
                || bodies == null
                || bodies.size() < 2
                || bodies.size() > MaximalRawBarExtractor.MAX_BODIES
                || staff == null) {
            return refused("INPUT_DOMAIN", 0, 0, 0, 0);
        }
        long members = 0;
        for (MaximalRawBarExtractor.Body body : bodies) {
            if (body == null || body.rawPixels().isEmpty())
                return refused("EMPTY_BODY", members, 0, 0, 0);
            try {
                members = Math.addExact(members, body.rawPixels().size());
            } catch (ArithmeticException overflow) {
                return refused("MEMBER_OVERFLOW", 0, 0, 0, 0);
            }
            if (members > MAX_MEMBERS) return refused("MEMBER_CAP", members, 0, 0, 0);
        }
        long visits;
        try {
            visits = Math.multiplyExact(members, 9L);
        } catch (ArithmeticException overflow) {
            return refused("VISIT_OVERFLOW", members, 0, 0, 0);
        }
        if (visits > MAX_NEIGHBOR_VISITS) return refused("VISIT_CAP", members, 0, 0, 0);
        budget.work(visits);
        budget.memory(Math.addExact(1024L, Math.multiplyExact(members, 48L)));

        Map<Integer, Integer> owner = new HashMap<>((int) Math.min(1_000_000L, members * 2L));
        for (int i = 0; i < bodies.size(); i++) {
            for (int pixel : bodies.get(i).rawPixels()) {
                if (pixel < 0
                        || pixel >= gray.length
                        || (gray[pixel] & 255) >= MaximalRawBarExtractor.DARK_LIMIT
                        || owner.putIfAbsent(pixel, i) != null) {
                    return refused("MEMBER_MISMATCH_OR_OVERLAP", members, 0, 0, 0);
                }
            }
        }
        long peers = 0, staffContacts = 0, unknown = 0;
        for (Map.Entry<Integer, Integer> entry : owner.entrySet()) {
            int p = entry.getKey(), bodyId = entry.getValue(), x = p % width, y = p / width;
            for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++) {
                for (int xx = Math.max(0, x - 1); xx <= Math.min(width - 1, x + 1); xx++) {
                    int q = yy * width + xx;
                    if (owner.containsKey(q)) {
                        if (owner.get(q) != bodyId) peers++;
                    } else if (staff.contains(q)) {
                        staffContacts++;
                    } else if ((gray[q] & 255) < MaximalRawBarExtractor.DARK_LIMIT) {
                        unknown++;
                    }
                }
            }
        }
        if (unknown != 0)
            return refused("UNASSIGNED_DARK_CONTACT", members, peers, staffContacts, unknown);
        if (peers == 0 || staffContacts == 0)
            return refused("NO_JOINT_PEER_STAFF_CONTACT", members, peers, staffContacts, 0);
        return new Result(true, members, peers, staffContacts, 0, null);
    }

    private static Result refused(
            String reason, long members, long peers, long staff, long unknown) {
        return new Result(false, members, peers, staff, unknown, reason);
    }
}
