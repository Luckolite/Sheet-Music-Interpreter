// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class WrittenTieOwnersTest {
    @Test
    public void overlappingShortUnisonCannotHideTheReleasingHeldVoice() {
        var rows = List.of(row(0, 4, false), row(1, 2, false), row(4, 5, true));
        assertEquals(List.of(new WrittenTieOwners.Link(0, 2)), WrittenTieOwners.links(rows));
    }

    @Test
    public void inputOrderDoesNotSelectAnotherPredecessor() {
        var rows = List.of(row(4, 5, true), row(1, 2, false), row(0, 4, false));
        assertEquals(List.of(new WrittenTieOwners.Link(2, 0)), WrittenTieOwners.links(rows));
    }

    @Test
    public void ambiguousReleasingOwnersAreRefused() {
        assertTrue(
                WrittenTieOwners.links(List.of(row(0, 4, false), row(1, 4, false), row(4, 5, true)))
                        .isEmpty());
    }

    @Test
    public void oneHeldOwnerCannotChooseBetweenTwoIdenticalContinuations() {
        assertTrue(
                WrittenTieOwners.links(List.of(row(0, 4, false), row(4, 5, true), row(4, 6, true)))
                        .isEmpty());
    }

    @Test
    public void staffPitchAndGraceBoundariesRemainIndependent() {
        var held = row(0, 4, false);
        assertTrue(
                WrittenTieOwners.links(
                                List.of(
                                        held,
                                        new WrittenTieOwners.Row(
                                                1, 60, 0, 4, 5, true, false, true)))
                        .isEmpty());
        assertTrue(
                WrittenTieOwners.links(
                                List.of(
                                        held,
                                        new WrittenTieOwners.Row(
                                                0, 60, 1, 4, 5, true, false, true)))
                        .isEmpty());
        assertTrue(
                WrittenTieOwners.links(
                                List.of(
                                        held,
                                        new WrittenTieOwners.Row(0, 60, 0, 4, 5, true, true, true)))
                        .isEmpty());
    }

    private static WrittenTieOwners.Row row(long start, long end, boolean tied) {
        return new WrittenTieOwners.Row(0, 60, 0, start, end, true, false, tied);
    }
}
