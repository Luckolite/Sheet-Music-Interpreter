// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.HashMap;
import java.util.Map;

/** Bounded per-page accounting for joined-staff lane discovery. */
final class JoinedLaneWorkBudget {
    static final long LEGACY_LANE_LIMIT = 2_000_000L;
    static final long JOINED_LANE_HARD_LIMIT = 12_000_000L;
    static final long PAGE_HARD_LIMIT = 64_000_000L;

    private final Map<String, Long> remainingByLane = new HashMap<>();
    private final Map<String, Long> laneCeilingByLane = new HashMap<>();
    private long pageRemaining = PAGE_HARD_LIMIT;

    long remaining(String lane) {
        return remainingByLane.getOrDefault(lane, LEGACY_LANE_LIMIT);
    }

    long pageRemaining() {
        return pageRemaining;
    }

    boolean chargePage(long amount) {
        if (amount < 0 || amount > pageRemaining) return false;
        pageRemaining -= amount;
        return true;
    }

    /** Charge candidate/semantic work once against both ledgers. */
    boolean charge(String lane, long amount) {
        if (amount < 0) return false;
        long laneRemaining = remaining(lane);
        if (amount > laneRemaining || amount > pageRemaining) return false;
        remainingByLane.put(lane, laneRemaining - amount);
        pageRemaining -= amount;
        return true;
    }

    /** Activate one geometry-derived joined allowance after independent carrier proof. */
    boolean activateJoined(String lane, long derivedCeiling) {
        if (derivedCeiling < 1 || derivedCeiling > JOINED_LANE_HARD_LIMIT) return false;
        long ceiling = Math.max(LEGACY_LANE_LIMIT, derivedCeiling);
        Long priorCeiling = laneCeilingByLane.get(lane);
        if (priorCeiling != null) return priorCeiling == ceiling;
        laneCeilingByLane.put(lane, ceiling);
        long priorRemaining = remaining(lane);
        long oneTimeExtension = Math.max(0L, ceiling - LEGACY_LANE_LIMIT);
        try {
            remainingByLane.put(lane, Math.addExact(priorRemaining, oneTimeExtension));
        } catch (ArithmeticException overflow) {
            laneCeilingByLane.remove(lane);
            return false;
        }
        return true;
    }

    /** The helper receives the intersection; charging its result cannot refund either ledger. */
    long helperAllowance(String lane) {
        return Math.min(remaining(lane), pageRemaining);
    }

    boolean chargeHelper(String lane, long allowance, long workUsed) {
        if (allowance < 0 || workUsed < 0 || workUsed > allowance) return false;
        long laneRemaining = remaining(lane);
        if (allowance > laneRemaining || allowance > pageRemaining) return false;
        remainingByLane.put(lane, laneRemaining - workUsed);
        pageRemaining -= workUsed;
        return true;
    }
}
