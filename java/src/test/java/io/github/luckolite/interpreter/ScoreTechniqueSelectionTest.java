// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Synthetic printed-staff instructions; no platform APIs or private score evidence. */
public class ScoreTechniqueSelectionTest {
    @Test
    public void omittedLowerStaffPizzicatoCannotBecomeAnUpperInstruction() {
        var bass = new ScoreTechniqueChange(0, .1f, 1, 2, ScoreTechniqueChange.PIZZICATO);
        assertTrue(ScoreTechniqueSelection.upperStaff(List.of(bass)).isEmpty());
    }

    @Test
    public void upperTechniqueProgressionKeepsItsContextAndOrder() {
        var upper = new ScoreTechniqueChange(0, .1f, 0, 2, ScoreTechniqueChange.PIZZICATO);
        var bass = new ScoreTechniqueChange(0, .2f, 1, 2, ScoreTechniqueChange.MARCATO);
        var arco = new ScoreTechniqueChange(1, .8f, 0, 2, ScoreTechniqueChange.ARCO);
        assertEquals(
                List.of(upper, arco),
                ScoreTechniqueSelection.upperStaff(List.of(upper, bass, arco)));
    }

    @Test
    public void singleStaffInstructionsRemainAvailable() {
        var mark = new ScoreTechniqueChange(0, .1f, 0, 1, ScoreTechniqueChange.CANTABILE);
        assertEquals(List.of(mark), ScoreTechniqueSelection.upperStaff(List.of(mark)));
    }

    @Test
    public void selectionDoesNotChangeRetainedSourceOrExposeMutableResults() {
        var upper = new ScoreTechniqueChange(0, .1f, 0, 3, ScoreTechniqueChange.SOSTENUTO);
        var bass = new ScoreTechniqueChange(0, .2f, 2, 3, ScoreTechniqueChange.PIZZICATO);
        var source = new ArrayList<>(List.of(upper, bass));
        var selected = ScoreTechniqueSelection.upperStaff(source);
        assertEquals(List.of(upper, bass), source);
        assertSame(upper, selected.get(0));
        source.clear();
        assertEquals(List.of(upper), selected);
        assertThrows(UnsupportedOperationException.class, () -> selected.clear());
    }
}
