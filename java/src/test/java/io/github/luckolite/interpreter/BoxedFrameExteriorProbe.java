// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;

/** Root may bind immutable pre-V2 actual API outputs; no source-role inference for unknown letters. */
public final class BoxedFrameExteriorProbe {
    static List<String> unknownScenes() throws Exception {
        return new String(
                        BoxedRehearsalFFixture.resource("exterior-capital-scenes.txt"),
                        java.nio.charset.StandardCharsets.UTF_8)
                .lines()
                .filter(x -> !x.isBlank())
                .toList();
    }

    static Properties genuineScenes() throws Exception {
        var p = new Properties();
        try (var in =
                new ByteArrayInputStream(
                        BoxedRehearsalFFixture.resource("exterior-dynamic-scenes.properties"))) {
            p.load(in);
        }
        return p;
    }

    static String key(String scene, String text) {
        return scene + "|" + text;
    }

    static String actual(String scene, String text) throws Exception {
        return new BoxedRehearsalFFixture(scene).detect(text).toString();
    }
}
