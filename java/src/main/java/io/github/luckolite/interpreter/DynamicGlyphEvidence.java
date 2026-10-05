// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Independent literal OCR can corroborate, but never replace, a bounded glyph comparison. */
final class DynamicGlyphEvidence {
    private DynamicGlyphEvidence() {}

    static boolean corroborated(String glyph, float score, float margin, String literal) {
        return (glyph.equals("mf") || glyph.equals("mp"))
                && glyph.equals(literal)
                && score >= .40f
                && margin >= .08f;
    }

    /** A detached f/p component must not erase the independently read compound mark. */
    static boolean clippedCompound(
            String glyph,
            String literal,
            float glyphLeft,
            float glyphRight,
            float wordLeft,
            float wordRight) {
        if (glyph.isEmpty()
                || literal == null
                || !literal.matches("(?:m[fp]|f{2,3}|p{2,3})")
                || literal.length() <= glyph.length()) return false;
        float width = glyphRight - glyphLeft;
        return width > 0
                && wordRight - wordLeft > 1.25f * width
                && (literal.endsWith(glyph)
                                && glyphLeft - wordLeft > .25f * width
                                && Math.abs(wordRight - glyphRight) < .5f * width
                        || literal.startsWith(glyph)
                                && wordRight - glyphRight > .25f * width
                                && Math.abs(wordLeft - glyphLeft) < .5f * width);
    }

    /**
     * One process-local immutable bank. Configuration is an owned immutable snapshot;
     * resource and font tokens use identity because equal replacements may render differently.
     */
    static final class TemplateKey {
        final Object assets;
        final Object configuration;
        final Object italic;
        final Object boldItalic;

        TemplateKey(Object assets, Object configuration, Object italic, Object boldItalic) {
            this.assets = java.util.Objects.requireNonNull(assets);
            this.configuration = java.util.Objects.requireNonNull(configuration);
            this.italic = java.util.Objects.requireNonNull(italic);
            this.boldItalic = java.util.Objects.requireNonNull(boldItalic);
        }

        boolean matches(TemplateKey other) {
            return assets == other.assets
                    && configuration.equals(other.configuration)
                    && italic == other.italic
                    && boldItalic == other.boldItalic;
        }
    }

    /** An incomplete or unstable build still supplies its original uncached fallback bank. */
    record TemplateLoad<T>(T bank, boolean reusable) {}

    static final class TemplateBankCache<T> {
        private TemplateKey key;
        private T bank;

        synchronized T resolve(
                TemplateKey requested, java.util.function.Supplier<TemplateLoad<T>> loader) {
            if (bank != null && key.matches(requested)) return bank;
            TemplateLoad<T> loaded = loader.get();
            T result = java.util.Objects.requireNonNull(loaded.bank());
            // Publish last. A partial result or thrown Exception/Error cannot poison reuse.
            if (loaded.reusable()) {
                key = requested;
                bank = result;
            }
            return result;
        }
    }
}
