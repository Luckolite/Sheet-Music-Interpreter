// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreParenthesizedNoteGroupTest {
    private ScoreNoteEvent note(int measure, float x, int step, float y) {
        return new ScoreNoteEvent(
                measure,
                x,
                step,
                0,
                1,
                y,
                false,
                0,
                0,
                0,
                2f,
                1,
                0,
                0,
                30,
                false,
                0,
                false,
                0,
                0,
                1,
                -1,
                ScoreNoteEvent.Kind.PITCHED);
    }

    private List<ScoreNoteEvent> notes() {
        return List.of(note(3, .2f, 4, .6f), note(3, .4f, 9, .5f), note(3, .7f, 8, .52f));
    }

    private ScoreParenthesizedNoteGroup.Pair pair() {
        return new ScoreParenthesizedNoteGroup.Pair(
                notes().stream().map(ScoreParenthesizedNoteGroup.Member::of).toList(),
                -1.2f,
                -.1f,
                1.1f,
                0,
                1.75f,
                1.75f);
    }

    @Test
    public void canonicalPairRoundTripsAllMembersAndOffsets() {
        var p = pair();
        assertEquals(p, ScoreParenthesizedNoteGroup.decode(p.qualifier()).orElseThrow());
    }

    @Test
    public void targetNamesFirstExactPhysicalMember() {
        var p = pair();
        assertEquals(3, ScoreParenthesizedNoteGroup.targetMeasure(p.target()).orElseThrow());
        assertTrue(p.target().endsWith(p.members().get(0).token()));
    }

    @Test
    public void allMembersMustSurviveSelection() {
        assertTrue(ScoreParenthesizedNoteGroup.owners(pair(), notes().subList(0, 2)).isEmpty());
    }

    @Test
    public void selectionReorderingRetainsOriginalGroupOrder() {
        var n = notes();
        assertEquals(
                List.of(2, 0, 1),
                ScoreParenthesizedNoteGroup.owners(pair(), List.of(n.get(1), n.get(2), n.get(0)))
                        .orElseThrow());
    }

    @Test
    public void duplicateExactSourceSlotCannotChooseArbitraryOwner() {
        var n = new ArrayList<>(notes());
        n.add(n.get(1));
        assertTrue(ScoreParenthesizedNoteGroup.owners(pair(), n).isEmpty());
    }

    @Test
    public void movedMiddleNoteCannotKeepOldPair() {
        var n = new ArrayList<>(notes());
        n.set(1, note(3, .41f, 9, .5f));
        assertTrue(ScoreParenthesizedNoteGroup.owners(pair(), n).isEmpty());
    }

    @Test
    public void changedWrittenPitchCannotKeepOldPair() {
        var n = new ArrayList<>(notes());
        n.set(1, note(3, .4f, 10, .5f));
        assertTrue(ScoreParenthesizedNoteGroup.owners(pair(), n).isEmpty());
    }

    @Test
    public void pageJoinOffsetsEveryMember() {
        var p = pair();
        var q =
                ScoreParenthesizedNoteGroup.decode(
                                ScoreParenthesizedNoteGroup.offsetQualifier(p.qualifier(), 12))
                        .orElseThrow();
        assertEquals(
                List.of(15, 15, 15),
                q.members().stream().map(ScoreParenthesizedNoteGroup.Member::measure).toList());
        assertEquals(ScoreParenthesizedNoteGroup.offsetTarget(p.target(), 12), q.target());
        assertEquals(p.leftX(), q.leftX(), 0);
    }

    @Test
    public void pageSplitRestoresCanonicalPair() {
        var p = pair();
        assertEquals(p, p.offset(12).offset(-12));
    }

    @Test
    public void movementBeforeScoreStartRefuses() {
        assertThrows(IllegalArgumentException.class, () -> pair().offset(-4));
    }

    @Test
    public void overflowingMeasureShiftRefuses() {
        assertThrows(ArithmeticException.class, () -> pair().offset(Integer.MAX_VALUE));
    }

    @Test
    public void reversedMembersAreNotSequentialGroup() {
        var p = pair();
        var m = new ArrayList<>(p.members());
        Collections.reverse(m);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreParenthesizedNoteGroup.Pair(
                                m,
                                p.leftX(),
                                p.leftY(),
                                p.rightX(),
                                p.rightY(),
                                p.leftHeight(),
                                p.rightHeight()));
    }

    @Test
    public void duplicateMemberIsNotOnePairAroundTwoNotes() {
        var p = pair();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreParenthesizedNoteGroup.Pair(
                                List.of(p.members().get(0), p.members().get(0)),
                                -1,
                                0,
                                1,
                                0,
                                1.7f,
                                1.7f));
    }

    @Test
    public void singleNoteCannotPretendToBePrintedMultiNoteGroup() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreParenthesizedNoteGroup.Pair(
                                pair().members().subList(0, 1), -1, 0, 1, 0, 1.7f, 1.7f));
    }

    @Test
    public void mixedSourceMeasuresRefuse() {
        var p = pair();
        var m = new ArrayList<>(p.members());
        m.set(1, m.get(1).offset(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScoreParenthesizedNoteGroup.Pair(m, -1, 0, 1, 0, 1.7f, 1.7f));
    }

    @Test
    public void wrongSideOffsetRefuses() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreParenthesizedNoteGroup.Pair(
                                pair().members(), 1, 0, 1, 0, 1.7f, 1.7f));
    }

    @Test
    public void nanGeometryRefuses() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreParenthesizedNoteGroup.Pair(
                                pair().members(), -1, Float.NaN, 1, 0, 1.7f, 1.7f));
    }

    @Test
    public void malformedTokensCannotBypassWireContract() {
        var p = pair();
        for (String q :
                List.of(
                        "",
                        p.qualifier() + "/",
                        p.qualifier().replace("3,0,1,", "03,0,1,"),
                        p.qualifier().replace("bdcccccd", "nan"),
                        "x".repeat(4097)))
            assertTrue(q, ScoreParenthesizedNoteGroup.decode(q).isEmpty());
    }

    @Test
    public void uppercaseBitsAreNotCanonicalWire() {
        assertTrue(
                ScoreParenthesizedNoteGroup.decode(pair().qualifier().toUpperCase(Locale.ROOT))
                        .isEmpty());
    }

    @Test
    public void typedGroupDoesNotChangeNoteTimingOrPitch() {
        var n = notes();
        var before = List.copyOf(n);
        var p = pair();
        assertEquals(List.of(0, 1, 2), ScoreParenthesizedNoteGroup.owners(p, n).orElseThrow());
        assertEquals(before, n);
    }

    @Test
    public void omittedInteriorSourceNoteCannotBeHiddenByMemberList() {
        var p = pair();
        var incomplete =
                new ScoreParenthesizedNoteGroup.Pair(
                        List.of(p.members().get(0), p.members().get(2)),
                        p.leftX(),
                        p.leftY(),
                        p.rightX(),
                        p.rightY(),
                        p.leftHeight(),
                        p.rightHeight());
        assertTrue(ScoreParenthesizedNoteGroup.owners(incomplete, notes()).isEmpty());
    }

    private ScoreExpressiveEvent mark(String evidence) {
        var p = pair();
        return new ScoreExpressiveEvent(
                "original-group",
                ScoreExpressiveEvent.Kind.PARENTHESIZED_NOTE_GROUP,
                Optional.empty(),
                Optional.empty(),
                ScoreExpressiveEvent.Scope.UNRESOLVED,
                0,
                1,
                Optional.of(p.target()),
                ScoreExpressiveEvent.Strength.UNSPECIFIED,
                p.qualifier(),
                List.of(
                        new ScoreExpressiveEvent.Evidence(
                                evidence, 2, .2f, 0, 1, "( note group )")));
    }

    private byte[] write(ScoreExpressiveEvent mark, int version) throws IOException {
        var bytes = new ByteArrayOutputStream();
        ScoreSemanticWire.writeExpressions(new DataOutputStream(bytes), List.of(mark), 8, version);
        return bytes.toByteArray();
    }

    private List<ScoreExpressiveEvent> read(byte[] bytes, int version) throws IOException {
        return ScoreSemanticWire.readExpressions(
                new DataInputStream(new ByteArrayInputStream(bytes)), 8, version);
    }

    @Test
    public void typedMarkNeedsPrintedEvidenceToOwnNotes() {
        assertEquals(
                List.of(0, 1, 2),
                ScoreParenthesizedNoteGroup.owners(
                                mark(ScoreParenthesizedNoteGroup.SOURCE), notes())
                        .orElseThrow());
        assertTrue(ScoreParenthesizedNoteGroup.owners(mark("unrelated"), notes()).isEmpty());
    }

    @Test
    public void typedMarkOffsetsAllMembersAndTargetTogether() {
        var m = mark(ScoreParenthesizedNoteGroup.SOURCE);
        var q = m.offset(12);
        assertEquals(
                pair().offset(12),
                ScoreParenthesizedNoteGroup.decode(q.qualifierText()).orElseThrow());
        assertEquals(pair().offset(12).target(), q.targetEventId().orElseThrow());
        assertEquals(m.evidence(), q.evidence());
        assertEquals(m.eventId(), q.eventId());
    }

    @Test
    public void pageSplitKeepsPairOnItsPhysicalBarOnly() {
        var m = mark(ScoreParenthesizedNoteGroup.SOURCE);
        assertTrue(ScorePageInterpretation.expressionsStartingOnPage(List.of(m), 0, 2).isEmpty());
        var found = ScorePageInterpretation.expressionsStartingOnPage(List.of(m), 2, 4);
        assertEquals(List.of(m.offset(-2)), found);
    }

    @Test
    public void guide296KeepsOnePairAllMembersAndEvidence() throws Exception {
        var m = mark(ScoreParenthesizedNoteGroup.SOURCE);
        assertEquals(List.of(m), read(write(m, 296), 296));
    }

    @Test
    public void guide296AppendsExplicitKind22() throws Exception {
        var in =
                new DataInputStream(
                        new ByteArrayInputStream(
                                write(mark(ScoreParenthesizedNoteGroup.SOURCE), 296)));
        assertEquals(1, in.readInt());
        assertTrue(in.readInt() > 0);
        in.skipNBytes(in.readInt());
        assertEquals(22, in.readInt());
    }

    @Test
    public void guide295WriterRefusesPairBeforeAnyPrefix() throws Exception {
        var b = new ByteArrayOutputStream();
        assertThrows(
                IOException.class,
                () ->
                        ScoreSemanticWire.writeExpressions(
                                new DataOutputStream(b),
                                List.of(mark(ScoreParenthesizedNoteGroup.SOURCE)),
                                8,
                                295));
        assertEquals(0, b.size());
    }

    @Test
    public void guide295ReaderCannotMisinterpretKind22() throws Exception {
        assertThrows(
                IOException.class,
                () -> read(write(mark(ScoreParenthesizedNoteGroup.SOURCE), 296), 295));
    }

    @Test
    public void groupOutsideHeaderPageRefusesBeforePrefix() throws Exception {
        var b = new ByteArrayOutputStream();
        assertThrows(
                IOException.class,
                () ->
                        ScoreSemanticWire.writeExpressions(
                                new DataOutputStream(b),
                                List.of(mark(ScoreParenthesizedNoteGroup.SOURCE)),
                                3,
                                296));
        assertEquals(0, b.size());
    }

    @Test
    public void readerRejectsGroupOutsideSuppliedPage() throws Exception {
        byte[] data = write(mark(ScoreParenthesizedNoteGroup.SOURCE), 296);
        assertThrows(
                IOException.class,
                () ->
                        ScoreSemanticWire.readExpressions(
                                new DataInputStream(new ByteArrayInputStream(data)), 3, 296));
    }

    @Test
    public void missingTargetCannotTurnGroupIntoGeneralDirection() {
        var m = mark(ScoreParenthesizedNoteGroup.SOURCE);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreExpressiveEvent(
                                m.eventId(),
                                m.kind(),
                                m.start(),
                                m.end(),
                                m.scope(),
                                0,
                                1,
                                Optional.empty(),
                                m.strength(),
                                m.qualifierText(),
                                m.evidence()));
    }

    @Test
    public void wrongLaneCannotOwnTypedGroup() {
        var m = mark(ScoreParenthesizedNoteGroup.SOURCE);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreExpressiveEvent(
                                m.eventId(),
                                m.kind(),
                                m.start(),
                                m.end(),
                                m.scope(),
                                1,
                                2,
                                m.targetEventId(),
                                m.strength(),
                                m.qualifierText(),
                                m.evidence()));
    }

    @Test
    public void groupCannotInventDynamicStrength() {
        var m = mark(ScoreParenthesizedNoteGroup.SOURCE);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScoreExpressiveEvent(
                                m.eventId(),
                                m.kind(),
                                m.start(),
                                m.end(),
                                m.scope(),
                                0,
                                1,
                                m.targetEventId(),
                                ScoreExpressiveEvent.Strength.MOLTO,
                                m.qualifierText(),
                                m.evidence()));
    }
}
