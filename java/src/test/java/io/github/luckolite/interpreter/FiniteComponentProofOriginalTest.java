// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FiniteComponentProofOriginalTest {
    private static SparseStaffContact.Budget budget() {
        return new SparseStaffContact.Budget(1000000, 16000000);
    }

    @Test
    public void tinyWholeComponentGetsAnUnforgeableExactTokenBoundProof() {
        var f = AllProducerFiniteRoleControls.fixture(false);
        var h = f.heads().get(5);
        var proof = f.evidence().proveWholeComponent(f.g(), 150, 130, h, budget());
        assertNotNull(proof);
        assertEquals(9, proof.core());
        assertEquals(0, proof.staff());
        assertEquals(0, proof.fringe());
        assertTrue(proof.matches(h.memberToken()));
        assertFalse(proof.matches(f.heads().get(0).memberToken()));
        assertTrue(
                Arrays.stream(
                                ObservedFiniteRasterRoles.ComponentProof.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    public void equalPixelForeignTokenAndForeignSnapshotCannotBorrowAProof() {
        var f = AllProducerFiniteRoleControls.fixture(false);
        var other = AllProducerFiniteRoleControls.fixture(false);
        var h = f.heads().get(5);
        var proof = f.evidence().proveWholeComponent(f.g(), 150, 130, h, budget());
        assertNotNull(proof);
        assertFalse(proof.matches(other.heads().get(5).memberToken()));
        assertNull(f.evidence().proveWholeComponent(f.g().clone(), 150, 130, h, budget()));
    }

    @Test
    public void paperMemberCannotProduceASummaryEvenWhenSizeIsTiny() {
        var f = AllProducerFiniteRoleControls.fixture(false);
        assertNull(f.evidence().proveWholeComponent(f.g(), 150, 130, f.heads().get(6), budget()));
    }

    @Test
    public void ledgerAcceptsOnlyTheSameActualProducerObject() {
        var f = AllProducerFiniteRoleControls.fixture(false);
        var h = f.heads().get(5);
        var proof = f.evidence().proveWholeComponent(f.g(), 150, 130, h, budget());
        Object actual = h.memberToken().originalIdentity();
        var roles = new IdentityHashMap<Object, ObservedFiniteRasterRoles.ComponentProof>();
        roles.put(actual, proof);
        var ledger =
                ScoreClockProducerSource.capture(
                        f.collector(),
                        List.of(actual),
                        List.of(),
                        0,
                        new ExactComponentMembers.Budget(1000000, 1000000),
                        roles);
        assertEquals(
                "PROVED_NON_NOTE_FINITE_TREMOLO_BODY", ledger.emissions().get(0).disposition());
        assertEquals(9, ledger.emissions().get(0).nonNoteRole().core());
        Object wrong = f.heads().get(0).memberToken().originalIdentity();
        roles.clear();
        roles.put(wrong, proof);
        ledger =
                ScoreClockProducerSource.capture(
                        f.collector(),
                        List.of(wrong),
                        List.of(),
                        0,
                        new ExactComponentMembers.Budget(1000000, 1000000),
                        roles);
        assertNull(ledger.emissions().get(0).nonNoteRole());
    }

    @Test
    public void conflictingNoteAndBarFactsAreRetainedAsAConflict() {
        var f = AllProducerFiniteRoleControls.fixture(false);
        var h = f.heads().get(5);
        Object actual = h.memberToken().originalIdentity();
        var proof = f.evidence().proveWholeComponent(f.g(), 150, 130, h, budget());
        var roles = new IdentityHashMap<Object, ObservedFiniteRasterRoles.ComponentProof>();
        roles.put(actual, proof);
        var ledger =
                ScoreClockProducerSource.capture(
                        f.collector(),
                        List.of(actual),
                        List.of(actual),
                        1,
                        new ExactComponentMembers.Budget(1000000, 1000000),
                        roles);
        assertEquals(List.of(0), ledger.emissions().get(0).finalNoteOwners());
        assertNotNull(ledger.emissions().get(0).nonNoteRole());
        assertEquals(
                "CONFLICTING_FINAL_NOTE_AND_FINITE_BODY_ROLE",
                ledger.emissions().get(0).disposition());
    }
}
