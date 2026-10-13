// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;

/** Public pinned snapshot integrity and cancellation; original fictional nomination geometry. */
public final class CatalogueSnapshotTest {
    private static int passed;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        passed++;
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void fails(Class<? extends Exception> type, Action action, String label)
            throws Exception {
        Exception failure = null;
        try {
            action.run();
        } catch (Exception caught) {
            failure = caught;
        }
        check(failure != null && type.isInstance(failure), label);
    }

    @Test
    public void pinnedSnapshotBoundaries() throws Exception {
        Path assets =
                Path.of(
                        System.getProperty(
                                "musicMetadataCatalogue", "data/music-metadata-catalogue"));
        var requested = new ArrayList<String>();
        var closed = new ArrayList<String>();
        var loaded =
                MusicMetadataCatalogueSnapshot.load(
                        name -> {
                            requested.add(name);
                            return new FilterInputStream(
                                    Files.newInputStream(assets.resolve(name))) {
                                @Override
                                public void close() throws IOException {
                                    super.close();
                                    closed.add(name);
                                }
                            };
                        });
        var expected =
                List.of(
                        "manifest.json",
                        "numbered-work-aliases.tsv",
                        "musical-person-labels.tsv",
                        "instrument-kind-labels.tsv");
        check(requested.equals(expected), "only exact allowlisted snapshot assets opened");
        check(closed.equals(expected), "each source stream closed");
        String person =
                Files.readAllLines(assets.resolve("musical-person-labels.tsv"))
                        .get(0)
                        .split("\t", -1)[1];
        check(loaded.isPersonName(person), "loaded snapshot retains a public positive person veto");
        fails(
                IOException.class,
                () -> MusicMetadataCatalogueSnapshot.load(null),
                "missing source rejected");
        for (String target : expected) {
            fails(
                    IOException.class,
                    () ->
                            MusicMetadataCatalogueSnapshot.load(
                                    name -> {
                                        if (name.equals(target))
                                            throw new FileNotFoundException(target);
                                        return Files.newInputStream(assets.resolve(name));
                                    }),
                    "missing " + target);
            fails(
                    IOException.class,
                    () ->
                            MusicMetadataCatalogueSnapshot.load(
                                    name -> {
                                        byte[] bytes = Files.readAllBytes(assets.resolve(name));
                                        if (name.equals(target)) bytes[bytes.length / 2] ^= 1;
                                        return new ByteArrayInputStream(bytes);
                                    }),
                    "tampered " + target);
            fails(
                    IOException.class,
                    () ->
                            MusicMetadataCatalogueSnapshot.load(
                                    name -> {
                                        byte[] bytes = Files.readAllBytes(assets.resolve(name));
                                        if (name.equals(target))
                                            bytes = Arrays.copyOf(bytes, bytes.length + 1);
                                        return new ByteArrayInputStream(bytes);
                                    }),
                    "overlong " + target);
            fails(
                    IOException.class,
                    () ->
                            MusicMetadataCatalogueSnapshot.load(
                                    name -> {
                                        byte[] bytes = Files.readAllBytes(assets.resolve(name));
                                        if (name.equals(target))
                                            bytes = Arrays.copyOf(bytes, bytes.length - 1);
                                        return new ByteArrayInputStream(bytes);
                                    }),
                    "truncated " + target);
        }
        Thread.currentThread().interrupt();
        try {
            fails(
                    InterruptedException.class,
                    () ->
                            MusicMetadataCatalogueSnapshot.load(
                                    name -> {
                                        throw new AssertionError("must not open");
                                    }),
                    "early snapshot cancellation");
        } finally {
            Thread.interrupted();
        }
        try {
            fails(
                    InterruptedException.class,
                    () ->
                            MusicMetadataCatalogueSnapshot.load(
                                    name ->
                                            new ByteArrayInputStream(
                                                    Files.readAllBytes(assets.resolve(name))) {
                                                @Override
                                                public synchronized int read(
                                                        byte[] bytes, int offset, int length) {
                                                    Thread.currentThread().interrupt();
                                                    return super.read(bytes, offset, length);
                                                }
                                            }),
                    "snapshot read cancellation");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void boundedSourceGeometry() {
        var heading = new ScoreCreditsDetector.Line("The Amber Gate", 110, 212, 320, 245);
        var caption = new ScoreCreditsDetector.Line("Nocturne VII", 70, 270, 620, 340);
        var page = new ScoreCreditsDetector.Page(1400, 2000, 260, List.of(heading, caption));
        var geometry = OpeningWorkHeading.geometryCandidates(page);
        check(
                geometry.size() == 1
                        && geometry.get(0).heading().equals(heading)
                        && geometry.get(0).caption().equals(caption),
                "original source geometry");
        check(OpeningWorkHeading.geometryCandidates(null).isEmpty(), "null geometry");
        var invalid = new ScoreCreditsDetector.Line("invalid", 0, 0, Float.NaN, 20);
        check(
                OpeningWorkHeading.geometryCandidates(
                                new ScoreCreditsDetector.Page(
                                        1400, 2000, 260, List.of(heading, caption, invalid)))
                        .isEmpty(),
                "invalid trailing row");
        var rows = new ArrayList<ScoreCreditsDetector.Line>();
        for (int n = 0; n < 6; n++)
            rows.add(new ScoreCreditsDetector.Line("heading " + n, 110, 212 + n, 320, 245 + n));
        rows.add(caption);
        check(
                OpeningWorkHeading.geometryCandidates(
                                        new ScoreCreditsDetector.Page(1400, 2000, 260, rows))
                                .size()
                        == 5,
                "bounded overflow nomination");
    }
}
