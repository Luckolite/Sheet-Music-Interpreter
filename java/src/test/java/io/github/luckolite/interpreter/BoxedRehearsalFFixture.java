// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

/** Loads independently frozen source-free inputs; never supplies glyph or box ownership to production. */
final class BoxedRehearsalFFixture {
    static final int W = 400, H = 320;
    final byte[] gray;
    final Properties metadata;
    final String name;

    BoxedRehearsalFFixture(String name) throws Exception {
        this.name = name;
        gray = resource(name + ".gray");
        assertEquals(W * H, gray.length);
        metadata = new Properties();
        try (var stream = new ByteArrayInputStream(resource(name + ".properties"))) {
            metadata.load(stream);
        }
    }

    static byte[] resource(String name) throws Exception {
        try (var input =
                BoxedRehearsalFFixture.class.getResourceAsStream(
                        "/boxed_rehearsal_f_originals/" + name)) {
            assertNotNull("Missing frozen original resource " + name, input);
            return input.readAllBytes();
        }
    }

    float number(String key) {
        return Float.parseFloat(metadata.getProperty(key));
    }

    PlayingTechniqueDetector.Word word(String text) {
        return new PlayingTechniqueDetector.Word(
                text,
                number("left") / W,
                number("top") / H,
                number("right") / W,
                number("bottom") / H);
    }

    List<PlayingTechniqueDetector.Staff> staffs() {
        float top = number("staffTop"), gap = number("gap");
        var normal = new PlayingTechniqueDetector.Staff(top, top + 4 * gap, gap, 0, 1);
        return Boolean.parseBoolean(metadata.getProperty("foreign"))
                ? List.of(new PlayingTechniqueDetector.Staff(64, 104, 10, 0, 1), normal)
                : List.of(normal);
    }

    List<ScoreDynamicChange> detect(String text) {
        return detect(text, gray);
    }

    List<ScoreDynamicChange> detect(String text, byte[] ownership) {
        byte[] before = gray.clone(), ownedBefore = ownership.clone();
        var words = List.of(word(text));
        var bars = List.of(new MeasureRegion(.05f, .95f, .4f, .82f));
        var answer =
                ScoreDynamicsDetector.detectDirectionCore(
                                words, staffs(), bars, List.of(), gray, W, H, ownership, true)
                        .changes();
        assertArrayEquals("Original pixels changed", before, gray);
        assertArrayEquals("Ownership pixels changed", ownedBefore, ownership);
        assertEquals(List.of(word(text)), words);
        return answer.stream().filter(x -> x.direction() == 0).toList();
    }

    static void dynamic(String scene, String text, float expectedDb) throws Exception {
        var found = new BoxedRehearsalFFixture(scene).detect(text);
        assertEquals(scene + " actual detector must retain one literal dynamic", 1, found.size());
        assertEquals(expectedDb, found.get(0).decibels(), 0);
        assertEquals(0, found.get(0).staffIndex());
        assertEquals(1, found.get(0).staffCount());
        assertEquals(0, found.get(0).measureIndex());
    }

    static void rehearsal(String scene) throws Exception {
        for (String text : List.of("F", "f"))
            assertTrue(
                    scene + " boxed rehearsal letter must not emit forte",
                    new BoxedRehearsalFFixture(scene).detect(text).isEmpty());
    }
}
