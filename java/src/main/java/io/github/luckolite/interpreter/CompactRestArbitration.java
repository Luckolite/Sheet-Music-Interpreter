// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Pure commit policy for an already matched rest/head conflict.
 * Proof production is separate; flags and structural equality confer no ownership. */
final class CompactRestArbitration {
    private final Set<Object> owned;

    private CompactRestArbitration(List<?> heads) {
        Set<Object> copy = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object head : heads) if (head != null) copy.add(head);
        owned = Collections.unmodifiableSet(copy);
    }

    static CompactRestArbitration empty() {
        return new CompactRestArbitration(List.of());
    }

    static CompactRestArbitration proved(List<?> heads) {
        return new CompactRestArbitration(heads);
    }

    boolean mayRemove(Object head) {
        return !owned.contains(head);
    }
}
