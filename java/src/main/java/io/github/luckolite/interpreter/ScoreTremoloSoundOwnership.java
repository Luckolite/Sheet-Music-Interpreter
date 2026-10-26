// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.Collection;
import java.util.Objects;

/** Original sound-alias indices are independent of a filtered or repeated performed row list. */
public final class ScoreTremoloSoundOwnership {
    private ScoreTremoloSoundOwnership() {}

    /** Select retained original-domain data; this does not certify a new performance. */
    public static ScoreTremoloSource forPerformedNotes(
            ScoreTremoloSource currentRows, ScoreTremoloSource originalSounds) {
        Objects.requireNonNull(currentRows);
        return originalSounds == null ? currentRows : originalSounds;
    }

    public static void requireAlias(ScoreTremoloSource originalSounds, int originalIndex) {
        Objects.requireNonNull(originalSounds);
        if (originalIndex < 0 || originalIndex >= originalSounds.noteOrigins().size())
            throw new IllegalArgumentException("Performed sound outside tremolo source domain");
    }

    /** Membership only: the scheduler must still prove complete independent endpoint sounds. */
    public static boolean containsEndpoint(
            ScoreTremoloSource.Resolved pair, Collection<Integer> originalAliases) {
        Objects.requireNonNull(pair);
        Objects.requireNonNull(originalAliases);
        for (int originalIndex : originalAliases)
            if (pair.first().contains(originalIndex) || pair.second().contains(originalIndex))
                return true;
        return false;
    }
}
