// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

final class NavigationSignGlyphs {
    private NavigationSignGlyphs() {}

    static List<ScoreNavigationDetector.Glyph> detect(
            byte[] gray, int width, int height, List<PlayingTechniqueDetector.Staff> staffs) {
        var marks = new ArrayList<>(NavigationSegnoGlyphs.detect(gray, width, height, staffs));
        marks.addAll(NavigationCodaGlyphs.detect(gray, width, height, staffs));
        return List.copyOf(marks);
    }
}
