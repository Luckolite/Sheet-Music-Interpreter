// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Immutable, lazily loaded recognition templates bundled with the standalone JAR. */
final class GlyphResources {
    private GlyphResources() {}

    static BufferedImage image(String relative) throws IOException {
        try (var input = GlyphResources.class.getResourceAsStream("glyphs/" + relative)) {
            if (input == null) throw new IOException("Missing bundled glyph template: " + relative);
            var image = ImageIO.read(input);
            if (image == null) throw new IOException("Invalid bundled glyph template: " + relative);
            return image;
        }
    }

    private static final class DynamicHolder {
        static final NativeDynamicGlyphs INSTANCE = create();

        private static NativeDynamicGlyphs create() {
            try {
                return new NativeDynamicGlyphs();
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot load dynamic templates", failure);
            }
        }
    }

    private static final class OrnamentHolder {
        static final NativeOrnamentGlyphs INSTANCE = create();

        private static NativeOrnamentGlyphs create() {
            try {
                return new NativeOrnamentGlyphs();
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot load ornament templates", failure);
            }
        }
    }

    static NativeDynamicGlyphs dynamics() {
        return DynamicHolder.INSTANCE;
    }

    static PortableOrnamentGlyphs ornaments() {
        return OrnamentHolder.INSTANCE.portable();
    }

    private static final class ExpressionHolder {
        static final PrintedExpressionGlyphs INSTANCE = create();

        private static PrintedExpressionGlyphs create() {
            try (var input =
                    GlyphResources.class.getResourceAsStream(
                            "glyphs/expression_templates/glyphs.bin")) {
                if (input == null) throw new IOException("Missing expression templates");
                return PrintedExpressionGlyphs.load(input);
            } catch (IOException error) {
                throw new IllegalStateException("Cannot load expression templates", error);
            }
        }
    }

    static PrintedExpressionGlyphs expressions() {
        return ExpressionHolder.INSTANCE;
    }

    private static final class PedalHolder {
        static final PrintedPedalGlyphs INSTANCE = create();

        private static PrintedPedalGlyphs create() {
            try (var input =
                    GlyphResources.class.getResourceAsStream("glyphs/pedal_templates/glyphs.bin")) {
                if (input == null) throw new IOException("Missing pedal templates");
                return PrintedPedalGlyphs.load(input);
            } catch (IOException error) {
                throw new IllegalStateException("Cannot load pedal templates", error);
            }
        }
    }

    static PrintedPedalGlyphs pedals() {
        return PedalHolder.INSTANCE;
    }
}
