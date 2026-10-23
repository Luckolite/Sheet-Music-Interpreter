// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PrintedMultiRestWireOriginalTest {
    private static final List<ScoreMultiRestGroup> GROUPS =
            List.of(new ScoreMultiRestGroup(0, 4, 0, 1));

    private static byte[] wire() throws Exception {
        var b = new ByteArrayOutputStream();
        ScoreMultiRestWire.write(new DataOutputStream(b), GROUPS, 4);
        return b.toByteArray();
    }

    @Test
    public void framedFourBarGroupRoundTripsExactly() throws Exception {
        var b = wire();
        assertEquals(28, b.length);
        assertEquals(
                GROUPS,
                ScoreMultiRestWire.read(new DataInputStream(new ByteArrayInputStream(b)), 4));
    }

    @Test
    public void countPastPageIsRejected() throws Exception {
        assertThrows(
                IOException.class,
                () ->
                        ScoreMultiRestWire.read(
                                new DataInputStream(new ByteArrayInputStream(wire())), 3));
    }

    @Test
    public void overlappingGroupsAreRejected() {
        assertThrows(
                IOException.class,
                () ->
                        ScoreMultiRestWire.write(
                                new DataOutputStream(new ByteArrayOutputStream()),
                                List.of(GROUPS.get(0), new ScoreMultiRestGroup(2, 2, 0, 1)),
                                4));
    }

    @Test
    public void truncatedFrameIsRejected() throws Exception {
        byte[] b = Arrays.copyOf(wire(), 27);
        assertThrows(
                IOException.class,
                () -> ScoreMultiRestWire.read(new DataInputStream(new ByteArrayInputStream(b)), 4));
    }

    @Test
    public void recordNeverAcceptsCountOneOrInvalidStaff() {
        assertThrows(IllegalArgumentException.class, () -> new ScoreMultiRestGroup(0, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ScoreMultiRestGroup(0, 4, 1, 1));
    }

    private static ScorePageInterpretation page() {
        var measures = Collections.nCopies(4, new MeasureRegion(.1f, .9f, .2f, .4f));
        var rests = new ArrayList<ScoreRestEvent>();
        for (int i = 0; i < 4; i++) rests.add(ScoreRestEvent.fullMeasure(i, .5f, .3f, .04f, 0, 1));
        return new ScorePageInterpretation(
                measures, List.of(), 1, List.of(), List.of(), List.of(), rests);
    }

    @Test
    public void oldConstructorKeepsOrdinarySilenceUngrouped() {
        assertTrue(page().multiRestGroups().isEmpty());
    }

    @Test
    public void pageCopiesKeepExplicitGroup() {
        var p = page().withMultiRestGroups(GROUPS);
        assertEquals(
                GROUPS,
                p.withOpeningPickup(false)
                        .withExpressiveEvents(List.of())
                        .withPlaybackDirections(List.of())
                        .withSlurSource(p.slurSource())
                        .withTremoloSource(p.tremoloSource())
                        .multiRestGroups());
    }

    @Test
    public void literalRestCannotBeRelabeledByGroup() {
        var p = page();
        var rests = new ArrayList<>(p.rests());
        var r = rests.get(1);
        rests.set(
                1,
                new ScoreRestEvent(1, r.positionInMeasure(), r.pageY(), r.pageHeight(), 0, 1, 4));
        var q =
                new ScorePageInterpretation(
                        p.measures(), p.notes(), 1, List.of(), List.of(), List.of(), rests);
        assertThrows(IllegalArgumentException.class, () -> q.withMultiRestGroups(GROUPS));
    }
}
