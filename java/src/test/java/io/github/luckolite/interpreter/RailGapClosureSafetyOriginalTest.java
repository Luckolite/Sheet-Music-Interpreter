// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Original literal raster fields exercise closure/refusal independently of musical fixtures. */
public class RailGapClosureSafetyOriginalTest {
    static final class Sample {
        final int x, first, last, group;

        Sample(int x, int first, int last, int group) {
            this.x = x;
            this.first = first;
            this.last = last;
            this.group = group;
        }
    }

    static Class<?> nested(String name) {
        return Arrays.stream(PrintedSlurContours.class.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    static final class Field {
        final int w, h = 240;
        final float bottom = 170.5f, gap = 14;
        final byte[] gray, labels;
        final StaffPitchTrack track;
        final Object frames;
        final PrintedSlurContours.HeadEvidence evidence;
        final List<Sample> samples = new ArrayList<>();
        final int trueGroups;
        int[] parents;
        Map<Integer, Integer> roots;

        Field(int noise, boolean heavy) throws Exception {
            w = Math.max(320, 410 + (heavy ? ((noise + 5) / 6) * 40 : noise * 3));
            gray = new byte[w * h];
            labels = new byte[w * h];
            Arrays.fill(gray, (byte) 255);
            var original = BroadRailCrossingPhraseOriginalTest.scene(.5f);
            for (int y = 0; y < h; y++) {
                System.arraycopy(original.gray, y * original.w, gray, y * w, original.w);
                System.arraycopy(original.labels, y * original.w, labels, y * w, original.w);
            }
            track =
                    noise == 0
                            ? StaffPitchTrack.fromVerifiedSamples(
                                    List.of(
                                            new float[] {0, bottom, gap},
                                            new float[] {w - 1, bottom, gap}))
                            : StaffPitchTrack.fromVerifiedSamples(
                                    List.of(
                                            new float[] {0, bottom, gap},
                                            new float[] {319, bottom, gap},
                                            new float[] {390, 170, 20},
                                            new float[] {w - 1, 170, 20}));
            float[] bases = new float[w], gaps = new float[w];
            for (int x = 0; x < w; x++) {
                float[] local = track.at(x);
                bases[x] = local[0];
                gaps[x] = local[1];
            }
            var fc = nested("Frames").getDeclaredConstructor(float[].class, float[].class);
            fc.setAccessible(true);
            frames = fc.newInstance(bases, gaps);
            evidence = PrintedSlurOwners.headEvidence(original.heads, gray, w, h, track);
            var ink =
                    PrintedSlurContours.class.getDeclaredMethod(
                            "ink",
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            float.class,
                            float.class,
                            nested("Frames"),
                            PrintedSlurContours.HeadEvidence.class);
            ink.setAccessible(true);
            int previous = -2, group = -1;
            for (int x = 50; x <= 210; x++) {
                double t = (x - 50) / 160., cy = bottom + 14 - 34 * t + 100 * t * (1 - t);
                int first = Integer.MAX_VALUE, last = -1;
                for (int y = (int) Math.round(cy) - 5; y <= (int) Math.round(cy) + 5; y++)
                    if ((boolean)
                            ink.invoke(
                                    null, gray, labels, w, h, x, y, bottom, gap, frames,
                                    evidence)) {
                        first = Math.min(first, y);
                        last = Math.max(last, y);
                    }
                if (last < 0) continue;
                // One analytic ribbon has only literal connected raw stroke ink in this column.
                for (int y = first; y <= last; y++) assertTrue((gray[y * w + x] & 255) <= 205);
                if (x != previous + 1) group++;
                samples.add(new Sample(x, first, last, group));
                previous = x;
            }
            trueGroups = group + 1;
            assertTrue(trueGroups >= 2);
            for (int x = 390; x < w; x++)
                for (int r = 0; r < 5; r++)
                    for (int y = (heavy ? 164 : 170) - r * 20;
                            y <= (heavy ? 176 : 170) - r * 20;
                            y++) gray[y * w + x] = 0;
            for (int i = 0; i < noise; i++) {
                int x = 400 + (heavy ? (i / 6) * 40 + (i % 6) * 3 : i * 3),
                        y = i % 2 == 0 ? 163 : 177,
                        id = trueGroups + i;
                gray[y * w + x] = gray[y * w + x + 1] = 0;
                samples.add(new Sample(x, y, y, id));
                samples.add(new Sample(x + 1, y, y, id));
            }
        }

        void branchTheFirstReceivingComponent() {
            Sample first = samples.stream().filter(s -> s.group == 1).findFirst().orElseThrow();
            int y = first.first - 2;
            gray[y * w + first.x] = 0;
            gray[(y + 1) * w + first.x] = (byte) 255;
            samples.add(new Sample(first.x, y, y, 1));
            // The added tip touches the genuine next-column ribbon: one unknown fork, not two
            // invented components.
            Sample next =
                    samples.stream()
                            .filter(s -> s.group == 1 && s.x == first.x + 1)
                            .findFirst()
                            .orElseThrow();
            assertTrue((gray[next.first * w + next.x] & 255) < 165);
            assertTrue(Math.abs(next.first - (y + 1)) <= 1);
            gray[(y + 1) * w + next.x] = 0;
            samples.remove(next);
            samples.add(new Sample(next.x, Math.min(next.first, y + 1), next.last, 1));
        }

        void run() throws Exception {
            samples.sort(Comparator.comparingInt((Sample s) -> s.x).thenComparingInt(s -> s.first));
            parents = new int[samples.size()];
            roots = new HashMap<>();
            var rc =
                    nested("Run")
                            .getDeclaredConstructor(
                                    int.class,
                                    float.class,
                                    float.class,
                                    float.class,
                                    float.class,
                                    float.class,
                                    List.class);
            rc.setAccessible(true);
            var bc = nested("RawBand").getDeclaredConstructor(int.class, int.class);
            bc.setAccessible(true);
            var rows = new ArrayList<Object>();
            for (int i = 0; i < samples.size(); i++) {
                var s = samples.get(i);
                roots.putIfAbsent(s.group, i);
                parents[i] = roots.get(s.group);
                float y = (s.first + s.last) * .5f;
                float[] local = track.at(s.x);
                rows.add(
                        rc.newInstance(
                                s.x,
                                y,
                                (y - local[0]) / local[1],
                                local[1],
                                (s.first - local[0]) / local[1],
                                (s.last - local[0]) / local[1],
                                List.of(bc.newInstance(s.first, s.last))));
            }
            var method =
                    PrintedSlurContours.class.getDeclaredMethod(
                            "completeRuleGaps",
                            List.class,
                            int[].class,
                            byte[].class,
                            byte[].class,
                            int.class,
                            int.class,
                            StaffPitchTrack.class,
                            nested("Frames"),
                            PrintedSlurContours.HeadEvidence.class);
            method.setAccessible(true);
            byte[] g0 = gray.clone(), l0 = labels.clone();
            method.invoke(null, rows, parents, gray, labels, w, h, track, frames, evidence);
            assertArrayEquals(g0, gray);
            assertArrayEquals(l0, labels);
        }

        int root(int group) {
            int p = roots.get(group);
            while (parents[p] != p) p = parents[p];
            return p;
        }

        void originalTrueChainJoined() {
            for (int i = 1; i < trueGroups; i++) assertEquals(root(0), root(i));
        }

        void everyComponentUnchanged() {
            for (var s : samples) assertEquals(roots.get(s.group).intValue(), root(s.group));
        }
    }

    @Test
    public void completeLiteralRibbonStillClosesInTheDirectPhase() throws Exception {
        var f = new Field(0, false);
        f.run();
        f.originalTrueChainJoined();
    }

    @Test
    public void unknownConnectedForkTaintsTheWholeProposedChain() throws Exception {
        var f = new Field(0, false);
        f.branchTheFirstReceivingComponent();
        f.run();
        f.everyComponentUnchanged();
    }

    @Test
    public void anIndependentAmbiguousFieldCannotDiscardTheValidRibbon() throws Exception {
        var f = new Field(9, true);
        f.run();
        f.originalTrueChainJoined();
        for (int i = f.trueGroups; i < f.trueGroups + 9; i++)
            assertEquals(f.roots.get(i).intValue(), f.root(i));
    }

    @Test
    public void weightedWorkRefusalCannotPublishAnEarlierProvedJoin() throws Exception {
        var f = new Field(5000, false);
        f.run();
        f.everyComponentUnchanged();
    }

    @Test
    public void linkCountRefusalCannotPublishAnEarlierProvedJoin() throws Exception {
        var f = new Field(6150, true);
        f.run();
        f.everyComponentUnchanged();
    }
}
