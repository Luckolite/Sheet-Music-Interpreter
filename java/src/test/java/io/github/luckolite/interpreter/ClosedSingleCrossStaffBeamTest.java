// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClosedSingleCrossStaffBeamTest {
    @Test
    public void recoversAWholeSingleRailAcrossACombinedUnisonBody() throws Exception {
        var s = new ClosedCrossStaffBeamOriginalTest.Scene();
        int[] rows = {416, 400, 384, 248, 232, 216};
        for (int i = 0; i < rows.length; i++) {
            int x = 300 + i * 40;
            s.stem(x, rows[i], 480);
            s.add(x, rows[i], i < 3 ? 1 : 0, 1, 0, false);
        }
        s.rails(290, 491, 1);
        s.add(278, 416, 1, 0, 4, true);
        for (int x = 283; x <= 294; x++)
            s.ink(x, 416, OmrMeasurePostProcessor.NOTEHEAD);
        for (int id : new int[] {0, 6}) {
            var n = s.notes.get(id);
            s.notes.set(id, new ScoreNoteEvent(0, .11125f, n.staffStep(), 1, 2, n.pageY(),
                    false, 0, n.beamCount(), n.writtenAccidental(), n.unbeamedDurationBeats())
                    .withClef(n.clefBottomDiatonic()).withStemDirection(id == 0 ? -1 : 0));
            s.heads.set(id, new PrintedSlurOwners.GeometryHead(id, 289, 416, 1, 2));
        }
        var origins = new ArrayList<Optional<ScoreBeamSource.Anchor>>();
        for (int i = 0; i < s.notes.size(); i++)
            origins.add(Optional.of(new ScoreBeamSource.Anchor(0, i)));
        var seed = new ScoreBeamSource.Group("partial-single",
                List.of(new ScoreBeamSource.Column(List.of(origins.get(1).orElseThrow())),
                        new ScoreBeamSource.Column(List.of(origins.get(2).orElseThrow()))),
                List.of(1), List.of());
        var original = new ScoreBeamSource(List.of(seed), List.of(), origins);
        var proved = ClosedCrossStaffBeamOriginalTest.run(s, original);
        assertEquals(s.notes, proved.output.notes());
        assertEquals(1, proved.groups.size());
        var rail = proved.output.beamSource().groups().get(0);
        assertEquals(List.of(0, 1, 2, 3, 4, 5), rail.columns().stream()
                .map(c -> c.members().get(0).noteIndex()).toList());
        assertEquals(List.of(1, 1, 1, 1, 1), rail.bridges());

        for (int x = 380; x < 395; x++)
            for (int y = 473; y <= 480; y++) s.clear(x, y);
        assertTrue(ClosedCrossStaffBeamOriginalTest.run(s, original).groups.isEmpty());
    }
}
