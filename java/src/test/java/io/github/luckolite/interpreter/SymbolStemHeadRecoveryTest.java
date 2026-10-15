// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original artificial silhouettes; no source-score pixels. */
public class SymbolStemHeadRecoveryTest {
    final int w = 180, h = 150;
    byte[] gray = new byte[w * h], labels = new byte[w * h];

    void fixture() {
        Arrays.fill(gray, (byte) 250);
        for (int y = 43; y <= 57; y++)
            for (int x = 60; x <= 80; x++)
                if (Math.pow((x - 70) / 10.0, 2) + Math.pow((y - 50) / 7.0, 2) <= 1) {
                    gray[y * w + x] = (byte) 190;
                    labels[y * w + x] = 5;
                }
        for (int y = 59; y <= 100; y++) {
            gray[y * w + 60] = 30;
            labels[y * w + 60] = 1;
        }
    }

    List<SymbolStemHeadRecovery.Head> find() {
        return SymbolStemHeadRecovery.find(labels, gray, w, h, 14, 20, 120);
    }

    @Test
    public void completeSymbolicOvalWithPrintedStemIsCandidate() {
        fixture();
        assertEquals(1, find().size());
    }

    @Test
    public void semanticStemWithoutInkIsNotEnough() {
        fixture();
        for (int y = 59; y <= 100; y++) gray[y * w + 60] = (byte) 250;
        assertTrue(find().isEmpty());
    }

    @Test
    public void rawStemWithoutSemanticSupportIsNotEnough() {
        fixture();
        for (int y = 59; y <= 100; y++) labels[y * w + 60] = 0;
        assertTrue(find().isEmpty());
    }

    @Test
    public void alreadyRecognizedHeadIsNotDuplicated() {
        fixture();
        for (int i = 0; i < labels.length; i++) if (labels[i] == 5) labels[i] = 2;
        assertTrue(find().isEmpty());
    }

    @Test
    public void crossingConnectedLineIsNotCompactHead() {
        fixture();
        for (int x = 10; x < 170; x++) gray[50 * w + x] = 30;
        assertTrue(find().isEmpty());
    }

    @Test
    public void connectedBeamAdmitsMissingHeadWithEighthDuration() {
        fixture();
        for (int y = 59; y <= 100; y++) {
            gray[y * w + 60] = (byte) 250;
            labels[y * w + 60] = 0;
        }
        for (int y : new int[] {70, 84, 98, 112, 126})
            for (int x = 15; x < 165; x++) {
                gray[y * w + x] = 30;
                labels[y * w + x] = 4;
            }
        for (int y = 43; y <= 57; y++)
            for (int x = 120; x <= 140; x++)
                if (Math.pow((x - 130) / 10.0, 2) + Math.pow((y - 50) / 7.0, 2) <= 1) {
                    gray[y * w + x] = 30;
                    labels[y * w + x] = 2;
                }
        for (int y = 50; y <= 107; y++) {
            gray[y * w + 120] = 30;
            labels[y * w + 120] = 1;
        }
        for (int y = 56; y <= 107; y++) {
            gray[y * w + 61] = 30;
            labels[y * w + 61] = 1;
        }
        for (int y = 102; y <= 107; y++)
            for (int x = 61; x <= 120; x++) {
                gray[y * w + x] = (byte) 185;
                labels[y * w + x] = 5;
            }
        var notes =
                OmrScoreInterpreter.extract(
                        labels, gray, w, h, List.of(new MeasureRegion(.05f, .95f, .2f, .95f)));
        assertEquals(2, notes.size());
        assertEquals(notes.get(1).staffStep(), notes.get(0).staffStep());
        assertEquals(1, notes.get(0).beamCount());
        assertEquals(0f, notes.get(0).unbeamedDurationBeats(), .001f);
    }

    @Test
    public void brokenBeamDoesNotAdmitSymbolHead() {
        fixture();
        for (int y = 59; y <= 100; y++) {
            gray[y * w + 60] = (byte) 250;
            labels[y * w + 60] = 0;
        }
        for (int y : new int[] {70, 84, 98, 112, 126})
            for (int x = 15; x < 165; x++) {
                gray[y * w + x] = 30;
                labels[y * w + x] = 4;
            }
        for (int y = 43; y <= 57; y++)
            for (int x = 120; x <= 140; x++)
                if (Math.pow((x - 130) / 10.0, 2) + Math.pow((y - 50) / 7.0, 2) <= 1) {
                    gray[y * w + x] = 30;
                    labels[y * w + x] = 2;
                }
        for (int y = 50; y <= 107; y++) {
            gray[y * w + 120] = 30;
            labels[y * w + 120] = 1;
        }
        for (int y = 56; y <= 107; y++) {
            gray[y * w + 61] = 30;
            labels[y * w + 61] = 1;
        }
        for (int y = 102; y <= 107; y++)
            for (int x = 61; x <= 120; x++) {
                gray[y * w + x] = (byte) 185;
                labels[y * w + x] = 5;
            }
        for (int y = 90; y <= 115; y++) gray[y * w + 90] = (byte) 250;
        var notes =
                OmrScoreInterpreter.extract(
                        labels, gray, w, h, List.of(new MeasureRegion(.05f, .95f, .2f, .95f)));
        assertEquals(1, notes.size());
    }
}
