// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class DynamicGlyphEvidenceTest {
    @Test
    public void exactIndependentMezzoReadingCorroborates() {
        assertTrue(DynamicGlyphEvidence.corroborated("mf", .45f, .11f, "mf"));
    }

    @Test
    public void truncatedOcrCannotConfirmDifferentLevel() {
        assertFalse(DynamicGlyphEvidence.corroborated("mf", .45f, .11f, "m"));
    }

    @Test
    public void ambiguousShapeStillFails() {
        assertFalse(DynamicGlyphEvidence.corroborated("mp", .45f, .03f, "mp"));
    }

    @Test
    public void poorShapeStillFails() {
        assertFalse(DynamicGlyphEvidence.corroborated("mf", .25f, .15f, "mf"));
    }

    @Test
    public void arbitrarySingleLetterNotRelaxed() {
        assertFalse(DynamicGlyphEvidence.corroborated("m", .45f, .11f, "m"));
    }

    @Test
    public void detachedFDoesNotEraseMezzoPrefix() {
        assertTrue(DynamicGlyphEvidence.clippedCompound("f", "mf", 30, 50, 10, 52));
    }

    @Test
    public void detachedPDoesNotErasePianissimo() {
        assertTrue(DynamicGlyphEvidence.clippedCompound("p", "pp", 30, 50, 10, 52));
    }

    @Test
    public void detachedMDoesNotEraseForteSuffix() {
        assertTrue(DynamicGlyphEvidence.clippedCompound("m", "mf", 10, 30, 9, 52));
    }

    @Test
    public void detachedLeadingFDoesNotEraseFortissimo() {
        assertTrue(DynamicGlyphEvidence.clippedCompound("f", "ff", 10, 30, 9, 52));
    }

    @Test
    public void CompleteShapeMayCorrectOcr() {
        assertFalse(DynamicGlyphEvidence.clippedCompound("f", "mf", 10, 50, 9, 52));
    }

    @Test
    public void DifferentMarkIsNotASuffix() {
        assertFalse(DynamicGlyphEvidence.clippedCompound("p", "mf", 30, 50, 10, 52));
    }

    @Test
    public void FullCompoundCanReplaceTruncatedOcr() {
        assertFalse(DynamicGlyphEvidence.clippedCompound("ff", "f", 10, 50, 30, 52));
    }

    @Test
    public void NearbyNonoverlappingWordDoesNotVeto() {
        assertFalse(DynamicGlyphEvidence.clippedCompound("f", "mf", 60, 80, 10, 52));
    }

    @Test
    public void completeTemplateBankReusesOriginalOrderAndRawValuesWithoutReload() {
        var cache = new DynamicGlyphEvidence.TemplateBankCache<java.util.List<Integer>>();
        Object assets = new EqualToken("assets"),
                italic = new EqualToken("italic"),
                bold = new EqualToken("bold");
        var key = new DynamicGlyphEvidence.TemplateKey(assets, "config", italic, bold);
        int[] loads = {0};
        java.util.List<Integer> first =
                cache.resolve(
                        key,
                        () -> {
                            loads[0]++;
                            return new DynamicGlyphEvidence.TemplateLoad<>(syntheticBank(), true);
                        });
        java.util.List<Integer> same =
                cache.resolve(
                        new DynamicGlyphEvidence.TemplateKey(
                                assets, new String("config"), italic, bold),
                        () -> {
                            throw new AssertionError("A complete bank was rebuilt");
                        });
        assertSame(first, same);
        assertEquals(1, loads[0]);
        assertEquals(43, same.size());
        for (int i = 0; i < 43; i++)
            assertEquals(Integer.valueOf(0x7fc00425 + i * 579733), same.get(i));
        assertThrows(UnsupportedOperationException.class, () -> same.set(0, 0));
    }

    @Test
    public void resourceConfigurationAndEitherFontReplacementInvalidateTheBank() {
        var cache = new DynamicGlyphEvidence.TemplateBankCache<java.util.List<Integer>>();
        Object assets = new EqualToken("assets"),
                italic = new EqualToken("italic"),
                bold = new EqualToken("bold");
        var first = new DynamicGlyphEvidence.TemplateKey(assets, "config", italic, bold);
        int[] loads = {0};
        java.util.function.Supplier<DynamicGlyphEvidence.TemplateLoad<java.util.List<Integer>>>
                loader =
                        () -> {
                            loads[0]++;
                            return new DynamicGlyphEvidence.TemplateLoad<>(syntheticBank(), true);
                        };
        var bank = cache.resolve(first, loader);
        DynamicGlyphEvidence.TemplateKey[] changed = {
            new DynamicGlyphEvidence.TemplateKey(new EqualToken("assets"), "config", italic, bold),
            new DynamicGlyphEvidence.TemplateKey(assets, "changed", italic, bold),
            new DynamicGlyphEvidence.TemplateKey(assets, "config", new EqualToken("italic"), bold),
            new DynamicGlyphEvidence.TemplateKey(assets, "config", italic, new EqualToken("bold"))
        };
        for (var key : changed) {
            cache.resolve(first, loader);
            var replacement = cache.resolve(key, loader);
            assertNotSame(bank, replacement);
            assertEquals(bank, replacement);
        }
        assertEquals(8, loads[0]);
        assertEquals(43, bank.size());
    }

    @Test
    public void partialAndChangedDuringLoadBanksRemainUsableButRetry() {
        var cache = new DynamicGlyphEvidence.TemplateBankCache<java.util.List<Integer>>();
        var key =
                new DynamicGlyphEvidence.TemplateKey(
                        new Object(), "config", new Object(), new Object());
        int[] loads = {0};
        java.util.List<Integer> partial = java.util.List.of(1, 2, 3);
        for (int i = 0; i < 2; i++)
            assertSame(
                    partial,
                    cache.resolve(
                            key,
                            () -> {
                                loads[0]++;
                                return new DynamicGlyphEvidence.TemplateLoad<>(partial, false);
                            }));
        var complete =
                cache.resolve(
                        key,
                        () -> {
                            loads[0]++;
                            return new DynamicGlyphEvidence.TemplateLoad<>(syntheticBank(), true);
                        });
        assertSame(
                complete,
                cache.resolve(
                        key,
                        () -> {
                            throw new AssertionError("Retry not completed");
                        }));
        assertEquals(3, loads[0]);
    }

    @Test
    public void failedLoadRetainsPrimaryIdentityAndDoesNotReplaceThePreviousBank() {
        var cache = new DynamicGlyphEvidence.TemplateBankCache<java.util.List<Integer>>();
        Object assets = new Object(), italic = new Object(), bold = new Object();
        var original = new DynamicGlyphEvidence.TemplateKey(assets, "old", italic, bold);
        var changed = new DynamicGlyphEvidence.TemplateKey(assets, "new", italic, bold);
        var bank =
                cache.resolve(
                        original,
                        () -> new DynamicGlyphEvidence.TemplateLoad<>(syntheticBank(), true));
        RuntimeException failed = new IllegalStateException("Original synthetic loader failure");
        assertSame(
                failed,
                assertThrows(
                        RuntimeException.class,
                        () ->
                                cache.resolve(
                                        changed,
                                        () -> {
                                            throw failed;
                                        })));
        AssertionError error = new AssertionError("Original synthetic loader Error");
        assertSame(
                error,
                assertThrows(
                        AssertionError.class,
                        () ->
                                cache.resolve(
                                        changed,
                                        () -> {
                                            throw error;
                                        })));
        assertSame(
                bank,
                cache.resolve(
                        original,
                        () -> {
                            throw new AssertionError("Old bank poisoned");
                        }));
        var retried =
                cache.resolve(
                        changed,
                        () -> new DynamicGlyphEvidence.TemplateLoad<>(syntheticBank(), true));
        assertNotSame(bank, retried);
        assertEquals(bank, retried);
    }

    private record EqualToken(String name) {}

    private static java.util.List<Integer> syntheticBank() {
        var values = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 43; i++) values.add(0x7fc00425 + i * 579733);
        return java.util.List.copyOf(values);
    }
}
