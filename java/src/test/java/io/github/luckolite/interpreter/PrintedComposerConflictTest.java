// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrintedComposerConflictTest {
    static ScoreCreditsDetector.Page page(
            String title, String first, String second, boolean arranged) {
        var l = new ArrayList<ScoreCreditsDetector.Line>();
        l.add(new ScoreCreditsDetector.Line(title, 650, 100, 1150, 160));
        l.add(new ScoreCreditsDetector.Line(first, 1150, 200, 1700, 228));
        if (!second.isBlank()) l.add(new ScoreCreditsDetector.Line(second, 1150, 238, 1700, 266));
        if (arranged) l.add(new ScoreCreditsDetector.Line("arr. Rowan Lake", 1150, 276, 1700, 302));
        return new ScoreCreditsDetector.Page(1800, 2400, 400, l);
    }

    static ScoreCreditsDetector.Result read(ScoreCreditsDetector.Page... p) {
        return ScoreCreditsDetector.detect("Evening Study", List.of(p), Set.of());
    }

    @Test
    public void retainPrintedSpellingAndInitialVariants() {
        var r =
                read(
                        page(
                                "Evening Study",
                                "Mara Ellis, Jon Vale,",
                                "Devon Reed, Quinn A. Stone",
                                false),
                        page(
                                "Evening Study",
                                "Mara Ellis, Jon Valle,",
                                "Devon Reed, Quinn Stone",
                                true));
        assertTrue(r.composer().contains("Jon Vale;"));
        assertTrue(r.composer().contains("Jon Valle"));
        assertTrue(r.composer().contains("Quinn A. Stone"));
        assertTrue(r.composer().contains("Quinn Stone"));
        assertEquals(1, r.creditConflicts().size());
        assertEquals(2, r.creditConflicts().get(0).printedCredits().size());
        assertTrue(r.unclassifiedCredits().isEmpty());
        assertEquals("", r.artist());
    }

    @Test
    public void explicitAndBareVariantsRetained() {
        var r =
                read(
                        page("Evening Study", "Music by Mara Ellis", "and Devon Reed", false),
                        page("Evening Study", "Mara Ellis, Devon James Reed", "", true));
        assertTrue(r.composer().contains("Devon Reed"));
        assertTrue(r.composer().contains("Devon James Reed"));
        assertEquals(1, r.creditConflicts().size());
    }

    @Test
    public void producerSuffixDoesNotTruncateComposerGroup() {
        var r =
                read(
                        page(
                                "Evening Study",
                                "Mara Ellis, Devon Reed,",
                                "Quinn Stone, Produced by Aurora Studio",
                                false),
                        page("Evening Study", "Mara Ellis, Devon Reed, Quinn Stone", "", true));
        assertTrue(r.composer().contains("Quinn Stone"));
        assertFalse(r.composer().contains("Aurora"));
        assertTrue(r.creditConflicts().isEmpty());
    }

    @Test
    public void additionalContributorRetained() {
        var r =
                read(
                        page("Evening Study", "Mara Ellis and Devon Reed", "", false),
                        page("Evening Study", "Mara Ellis, Devon Reed, Quinn Stone", "", true));
        assertTrue(r.composer().contains("Quinn Stone"));
        assertEquals(1, r.creditConflicts().size());
    }

    @Test
    public void reorderedSameNamesAreNotConflict() {
        var r =
                read(
                        page("Evening Study", "Mara Ellis, Devon Reed", "", false),
                        page("Evening Study", "Devon Reed, Mara Ellis", "", true));
        assertTrue(r.creditConflicts().isEmpty());
    }

    @Test
    public void unrelatedWorkDoesNotCorroborateBareCredit() {
        var r =
                read(
                        page("Morning Study", "Mara Ellis, Devon Reed, Jon Vale", "", false),
                        page("Evening Study", "Mara Ellis, Devon Reed", "", true));
        assertFalse(r.composer().contains("Jon Vale"));
        assertTrue(r.creditConflicts().isEmpty());
    }

    @Test
    public void oneSharedNameInsufficient() {
        var r =
                read(
                        page("Evening Study", "Mara Ellis, Jon Vale", "", false),
                        page("Evening Study", "Mara Ellis, Devon Reed", "", true));
        assertFalse(r.composer().contains("Jon Vale"));
        assertTrue(r.creditConflicts().isEmpty());
    }

    @Test
    public void noRoleEvidenceDoesNotAssignComposer() {
        var r = read(page("Evening Study", "Mara Ellis, Devon Reed", "", false));
        assertEquals("", r.composer());
        assertTrue(r.creditConflicts().isEmpty());
    }

    @Test
    public void oldResultConstructorRemainsCompatible() {
        var r = new ScoreCreditsDetector.Result("Title", "", "", "", List.of());
        assertTrue(r.creditConflicts().isEmpty());
    }

    @Test
    public void conflictListsAreImmutable() {
        var source = new ArrayList<String>(List.of("A", "B"));
        var c = new ScoreCreditsDetector.CreditConflict("Composer", "Work", source);
        source.clear();
        assertEquals(List.of("A", "B"), c.printedCredits());
    }
}
