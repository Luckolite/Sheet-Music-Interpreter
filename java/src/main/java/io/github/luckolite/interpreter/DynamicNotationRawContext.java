// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.security.MessageDigest;

/** Retains literal curve-run provenance; unknown fringe is never assigned by a tube/bbox. */
final class DynamicNotationRawContext {
    record OwnedCurve(
            PrintedSlurContours.Bow bow,
            PrintedSlurOwners.GeometryHead start,
            PrintedSlurOwners.GeometryHead end,
            StaffPitchTrack track,
            List<PrintedSlurContours.RawColumn> componentProfiles,
            List<PrintedSlurContours.RawColumn> selectedContourProfiles) {
        OwnedCurve {
            componentProfiles = List.copyOf(componentProfiles);
            selectedContourProfiles = List.copyOf(selectedContourProfiles);
        }
    }

    record Evidence(
            List<OwnedCurve> curves,
            boolean incomplete,
            int width,
            int height,
            String sourceSha256) {
        Evidence {
            curves = List.copyOf(curves);
        }

        Map<Integer, Integer> selectedContourPixelValues(byte[] gray, int w, int h) {
            if (gray == null
                    || w != width
                    || h != height
                    || gray.length != (long) w * h
                    || !digest(gray).equals(sourceSha256)) return Map.of();
            var out = new LinkedHashMap<Integer, Integer>();
            for (var curve : curves)
                for (var c : curve.selectedContourProfiles())
                    for (var band : c.members())
                        for (int y = band.first(); y <= band.last(); y++) {
                            int value = c.rawProfile().get(y - c.first());
                            // Exact samples already accepted by the original ink predicate, not a
                            // new threshold route.
                            int at = y * w + c.x();
                            if ((gray[at] & 255) != value)
                                throw new IllegalStateException("Changed curve source");
                            out.put(at, value);
                        }
            return Collections.unmodifiableMap(out);
        }

        /** Compatibility for frozen PRIVATE V2 controls only; returns SELECTED contour samples. */
        @Deprecated
        Map<Integer, Integer> ownedPixelValues(byte[] gray, int w, int h) {
            return selectedContourPixelValues(gray, w, h);
        }
    }

    private DynamicNotationRawContext() {}

    private static String lowerHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 255;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 15];
        }
        return new String(out);
    }

    private static String digest(byte[] g) {
        try {
            return lowerHex(MessageDigest.getInstance("SHA-256").digest(g));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    static Evidence capture(
            byte[] gray,
            int w,
            int h,
            List<PlayingTechniqueDetector.Staff> staffs,
            List<MeasureRegion> regions,
            List<ScoreNoteEvent> notes) {
        if (gray == null
                || w <= 0
                || h <= 0
                || gray.length != (long) w * h
                || staffs == null
                || regions == null
                || notes == null) return new Evidence(List.of(), true, w, h, "");
        var heads = PrintedSlurContext.geometryHeads(notes, regions, w, h);
        var prepared = PrintedSlurFrames.prepare(gray, w, h, staffs, notes, heads);
        var out = new ArrayList<OwnedCurve>();
        boolean incomplete = prepared.unprovedStaffs() > 0 || !prepared.unboundOwners().isEmpty();
        for (var frame : prepared.frames()) {
            var raw =
                    PrintedSlurContours.findWithRawEvidence(
                            gray,
                            null,
                            w,
                            h,
                            frame.left(),
                            frame.top(),
                            frame.right(),
                            frame.bottom(),
                            frame.track(),
                            null,
                            PrintedSlurOwners.headEvidence(
                                    frame.heads(), gray, w, h, frame.track()));
            incomplete |=
                    raw.result()
                            .limitReached(); // Retain independently proved bows even when the new
                                             // search is incomplete.
            var byId = new HashMap<Integer, PrintedSlurOwners.GeometryHead>();
            for (var head : frame.heads()) byId.put(head.owner(), head);
            int index = 0;
            for (var bow : raw.result().bows()) {
                var binding =
                        PrintedSlurOwners.bind(
                                "direction-raw-curve-" + (index++),
                                0,
                                bow,
                                frame.heads(),
                                gray,
                                w,
                                h,
                                frame.track());
                if (binding.status() != PrintedSlurOwners.Status.GEOMETRY_OWNED) continue;
                var span = binding.span().orElseThrow();
                var a = byId.get(span.start().noteIndex());
                var b = byId.get(span.end().noteIndex());
                if (a == null
                        || b == null
                        || !closed(gray, w, h, frame.track(), a, bow.start(), true)
                        || !closed(gray, w, h, frame.track(), b, bow.end(), false)) continue;
                var full = raw.witnesses().get(bow);
                var selected = raw.selectedContourWitnesses().get(bow);
                if (full == null || selected == null)
                    throw new IllegalStateException("Lost curve identity");
                out.add(new OwnedCurve(bow, a, b, frame.track(), full, selected));
            }
        }
        return new Evidence(out, incomplete, w, h, digest(gray));
    }

    private static boolean closed(
            byte[] gray,
            int w,
            int h,
            StaffPitchTrack track,
            PrintedSlurOwners.GeometryHead head,
            PrintedSlurContours.Point cap,
            boolean first) {
        var evidence = PrintedSlurOwners.headEvidence(List.of(head), gray, w, h, track);
        float gap = track.at(head.x())[1];
        int l = Math.max(0, (int) Math.floor(head.x() - gap)),
                r = Math.min(w - 1, (int) Math.ceil(head.x() + gap));
        int t = Math.max(0, (int) Math.floor(head.y() - gap)),
                b = Math.min(h - 1, (int) Math.ceil(head.y() + gap));
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) if (evidence.closes(cap, x, y, first)) return true;
        return false;
    }
}
