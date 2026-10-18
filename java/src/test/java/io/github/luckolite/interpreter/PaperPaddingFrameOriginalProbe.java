// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independent fixture access; root-only exclusive collector can freeze actual pre-V4 decisions. */
public final class PaperPaddingFrameOriginalProbe {
    static List<String> scenes(String group) throws Exception {
        return new String(
                        BoxedRehearsalFFixture.resource("paper-pad-" + group + ".txt"),
                        StandardCharsets.UTF_8)
                .lines()
                .filter(x -> !x.isBlank())
                .toList();
    }

    static String key(String scene, String text) {
        return scene + "|" + text;
    }

    static String actual(String scene, String text) throws Exception {
        return new BoxedRehearsalFFixture(scene).detect(text).toString();
    }
}
