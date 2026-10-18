// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original procedural scene access; frozen expectations are classpath resources. */
public final class CorrectedObliqueFrameOriginalProbe {
    static List<String> unknownScenes() throws Exception {
        return new String(
                        BoxedRehearsalFFixture.resource("oblique-unknown-scenes.txt"),
                        StandardCharsets.UTF_8)
                .lines()
                .filter(x -> !x.isBlank())
                .toList();
    }

    static List<String> allScenes() throws Exception {
        return new String(
                        BoxedRehearsalFFixture.resource("oblique-all-scenes.txt"),
                        StandardCharsets.UTF_8)
                .lines()
                .filter(x -> !x.isBlank())
                .toList();
    }

    static String actual(String scene, String text) throws Exception {
        return new BoxedRehearsalFFixture(scene).detect(text).toString();
    }

    static String key(String scene, String text) {
        return scene + "|" + text;
    }
}
