// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import static org.junit.Assert.*;

/** Original small fixtures, suitable for identical standalone/app execution. */
public class MusicXmlDivisionGridTest {
    private static Element part(String body) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader("<part>" + body + "</part>")))
                .getDocumentElement();
    }

    private static String note(long duration, String type, String extra) {
        return "<note><duration>"
                + duration
                + "</duration>"
                + (type == null ? "" : "<type>" + type + "</type>")
                + extra
                + "</note>";
    }

    private static String tuplet(long duration) {
        return note(
                duration,
                "quarter",
                "<time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification>");
    }

    private static void rejected(String body) throws Exception {
        try {
            MusicXmlDivisionGrid.initialDivisions(part(body));
            fail("Absent divisions must have one proved grid");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    @Test
    public void absentGridIsProvedByConsistentEighthRestAndQuarterNotes() throws Exception {
        assertEquals(
                2,
                MusicXmlDivisionGrid.initialDivisions(
                        part(
                                "<measure>"
                                        + note(1, "eighth", "<rest/>")
                                        + note(2, "quarter", "")
                                        + note(1, "eighth", "")
                                        + "</measure>")));
    }

    @Test
    public void augmentationDotsContributeExactOrdinaryEvidence() throws Exception {
        assertEquals(
                2,
                MusicXmlDivisionGrid.initialDivisions(
                        part(note(3, "quarter", "<dot/>") + note(1, "eighth", ""))));
    }

    @Test
    public void explicitGridPreservesIntentionalMetricDuration() throws Exception {
        var p = part("<attributes><divisions>1</divisions></attributes>" + note(2, "quarter", ""));
        assertEquals(1, MusicXmlDivisionGrid.initialDivisions(p));
        assertEquals(
                20160,
                MusicXmlWrittenDuration.durationTicks(
                        (Element) p.getElementsByTagName("note").item(0), 1, 10080));
    }

    @Test
    public void explicitLaterChangesRemainConsumerControlled() throws Exception {
        assertEquals(
                1,
                MusicXmlDivisionGrid.initialDivisions(
                        part(
                                "<measure><attributes><divisions>2</divisions></attributes>"
                                        + note(1, "eighth", "")
                                        + "</measure><measure><attributes><divisions>3</divisions></attributes></measure>")));
    }

    @Test
    public void unspecifiedFullBarRestAndClockMovesDoNotInventTypes() throws Exception {
        assertEquals(
                2,
                MusicXmlDivisionGrid.initialDivisions(
                        part(
                                note(8, null, "<rest measure='yes'/>")
                                        + "<backup><duration>8</duration></backup><forward><duration>8</duration></forward>"
                                        + note(2, "quarter", ""))));
        rejected(
                note(8, null, "<rest measure='yes'/>") + "<backup><duration>8</duration></backup>");
    }

    @Test
    public void graceNotesDoNotEstablishMetricGrid() throws Exception {
        rejected("<note><grace/><type>eighth</type></note>");
        assertEquals(
                2,
                MusicXmlDivisionGrid.initialDivisions(
                        part("<note><grace/><type>eighth</type></note>" + note(2, "quarter", ""))));
    }

    @Test
    public void conflictingOrdinaryEvidenceIsRejected() throws Exception {
        rejected(note(2, "quarter", "") + note(2, "eighth", ""));
    }

    @Test
    public void nonintegralGridIsRejected() throws Exception {
        rejected(note(1, "quarter", "<dot/>"));
    }

    @Test
    public void overflowingGridIsRejected() throws Exception {
        rejected(note(Long.MAX_VALUE, "16th", ""));
    }

    @Test
    public void nonpositiveAndMissingMetricDurationsAreRejected() throws Exception {
        rejected(note(0, "quarter", ""));
        rejected("<note><type>quarter</type></note>");
        rejected(note(1, "eighth", "<grace/>"));
    }

    @Test
    public void malformedTypesDotsAndRatiosAreRejected() throws Exception {
        rejected(note(1, "unknown", ""));
        rejected(note(1, "quarter", "<dot/>".repeat(9)));
        rejected(
                note(
                        1,
                        "quarter",
                        "<time-modification><actual-notes>0</actual-notes><normal-notes>2</normal-notes></time-modification>"));
    }

    @Test
    public void tupletOnlyExactOrRoundedEvidenceCannotEstablishGrid() throws Exception {
        rejected(tuplet(1));
        rejected(
                note(
                        1,
                        "eighth",
                        "<time-modification><actual-notes>3</actual-notes><normal-notes>2</normal-notes></time-modification>"));
    }

    @Test
    public void provedOrdinaryGridRetainsExistingRoundedTupletSemantics() throws Exception {
        var p = part(note(1, "quarter", "") + tuplet(1));
        long grid = MusicXmlDivisionGrid.initialDivisions(p);
        assertEquals(1, grid);
        assertEquals(
                6720,
                MusicXmlWrittenDuration.durationTicks(
                        (Element) p.getElementsByTagName("note").item(1), grid, 10080));
        var contradictory = part(note(1, "quarter", "") + tuplet(2));
        grid = MusicXmlDivisionGrid.initialDivisions(contradictory);
        try {
            MusicXmlWrittenDuration.durationTicks(
                    (Element) contradictory.getElementsByTagName("note").item(1), grid, 10080);
            fail("Contradictory rounded units remain invalid");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("contradicts"));
        }
    }
}
