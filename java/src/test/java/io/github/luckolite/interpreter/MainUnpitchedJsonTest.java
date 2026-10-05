// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original synthetic export records; no raster recognition or private score. */
public class MainUnpitchedJsonTest {
    private static ScoreNoteEvent note(float x) {
        return new ScoreNoteEvent(
                0,
                x,
                0,
                0,
                1,
                .3f,
                false,
                0,
                0,
                ScoreNoteEvent.ACCIDENTAL_FROM_KEY,
                1,
                1,
                0,
                0,
                ScoreNoteEvent.CLEF_TREBLE);
    }

    @Test
    public void typedIdentityOmitsMidiAndIgnoresKey() throws Exception {
        var unpitched = note(.1f).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var identity = Main.noteIdentity(unpitched, 7);
        assertEquals(identity, Main.noteIdentity(unpitched, -7));
        assertEquals("UNPITCHED", identity.get("kind"));
        assertEquals("E", identity.get("displayStep"));
        assertEquals(4, identity.get("displayOctave"));
        assertFalse(identity.containsKey("midi"));
        assertFalse(identity.containsKey("boundaryPitch"));
        assertFalse(identity.containsKey("boundaryAccidental"));
        assertFalse(identity.containsKey("octaveShift"));
    }

    @Test
    public void legacyPitchedIdentityJsonStaysExact() throws Exception {
        assertEquals(
                "{\"midi\":64,\"clefInferred\":false}", Main.json(Main.noteIdentity(note(.1f), 0)));
        assertEquals(
                "{\"midi\":65,\"clefInferred\":false}", Main.json(Main.noteIdentity(note(.1f), 7)));
        assertEquals(
                "{\"midi\":88,\"clefInferred\":false}",
                Main.json(Main.noteIdentity(note(.1f).withOctaveShift(2), 0)));
        assertFalse(Main.noteIdentity(note(.1f), 0).containsKey("kind"));
    }

    @Test
    public void rawRecordJsonCarriesUAndLegacyPDefault() throws Exception {
        var unpitched =
                note(.1f)
                        .withKind(ScoreNoteEvent.Kind.UNPITCHED)
                        .withStemDirection(-1)
                        .withTupletRatio(5, 3);
        var json = Main.json(unpitched);
        assertTrue(json.contains("\"kind\":\"UNPITCHED\""));
        assertTrue(json.contains("\"stemDirection\":-1"));
        assertTrue(json.contains("\"tupletNormalNotes\":3"));
        assertFalse(Main.json(note(.1f)).contains("\"kind\":\"PITCHED\""));
    }

    @Test
    public void typedTupletPerformanceRetainsRatioAndNoGlissTarget() throws Exception {
        var unpitched = note(.1f).withKind(ScoreNoteEvent.Kind.UNPITCHED).withTupletRatio(5, 3);
        var event = new LinkedHashMap<>(Main.noteIdentity(unpitched, 0));
        Main.attachNotePerformance(List.of(unpitched), List.of(event));
        assertEquals(5, event.get("tupletActualNotes"));
        assertEquals(3, event.get("tupletNormalNotes"));
        assertFalse(event.containsKey("midi"));
        assertFalse(event.containsKey("glissando"));
    }

    @Test
    public void unsupportedSourceIdentityFailsExplicitly() {
        var unpitched = note(.1f).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        assertThrows(IOException.class, () -> Main.noteIdentity(unpitched.withBoundaryTies(1), 0));
        assertThrows(IOException.class, () -> Main.noteIdentity(unpitched.withOctaveShift(2), 0));
        assertThrows(
                IOException.class,
                () -> Main.noteIdentity(unpitched.withArticulations(NoteOrnament.GLISSANDO), 0));
    }

    @Test
    public void unpitchedAttackBlocksInventingADistantPitchedGlissTarget() {
        var source = note(.1f).withArticulations(NoteOrnament.GLISSANDO);
        var cross = note(.4f).withKind(ScoreNoteEvent.Kind.UNPITCHED);
        var distant = note(.8f);
        var first =
                new LinkedHashMap<String, Object>(
                        Map.of("startBeat", 0., "durationBeats", 1., "midi", 64));
        var middle =
                new LinkedHashMap<String, Object>(
                        Map.of("startBeat", .5, "durationBeats", .5, "kind", "UNPITCHED"));
        var last =
                new LinkedHashMap<String, Object>(
                        Map.of("startBeat", 1., "durationBeats", 1., "midi", 65));
        Main.attachNotePerformance(List.of(source, cross, distant), List.of(first, middle, last));
        assertFalse(first.containsKey("glissando"));
    }

    @Test
    public void actualJavaJsonIsAvailableForPythonConsumerRoundTrip() throws Exception {
        var events = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 4; i++) {
            var event =
                    new LinkedHashMap<>(
                            Main.noteIdentity(
                                    note(.1f + i * .2f).withKind(ScoreNoteEvent.Kind.UNPITCHED),
                                    0));
            event.put("startBeat", i);
            event.put("durationBeats", 1);
            event.put("staffIndex", 0);
            event.put("staffCount", 1);
            event.put("tiedFromPrevious", false);
            events.add(event);
        }
        var page =
                Map.of(
                        "measureBeats",
                        List.of(4),
                        "totalBeats",
                        4,
                        "events",
                        events,
                        "score",
                        Map.of("tempoChanges", List.of()));
        String json =
                Main.json(
                        Map.of(
                                "inputName",
                                "Original four typed quarters",
                                "pages",
                                List.of(page)));
        String output = System.getProperty("export.test.output");
        if (output != null) Files.writeString(Path.of(output), json + "\n");
        assertFalse(json.contains("\"midi\""));
    }
}
