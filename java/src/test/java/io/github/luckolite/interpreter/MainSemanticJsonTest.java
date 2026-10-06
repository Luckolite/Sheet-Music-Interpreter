// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import java.util.Optional;
import org.junit.Test;
import static org.junit.Assert.*;

public class MainSemanticJsonTest {
    private String json(Object value) throws Exception {
        var method = Main.class.getDeclaredMethod("json", Object.class);
        method.setAccessible(true);
        return (String) method.invoke(null, value);
    }

    @Test
    public void richDirectionDetailsDoNotBreakStandaloneJson() throws Exception {
        String value =
                json(
                        new ScorePlaybackDirection(
                                2,
                                ScorePlaybackDirection.Kind.SEGNO,
                                new ScorePlaybackDirection.Details(
                                        0,
                                        "segno",
                                        "",
                                        "",
                                        "",
                                        2,
                                        List.of(),
                                        Optional.empty(),
                                        ScorePlaybackDirection.AfterJumpRepeats.DEFAULT,
                                        "",
                                        List.of())));
        assertTrue(value.contains("\"kind\":0"));
        assertTrue(value.contains("\"end\":null"));
        assertTrue(value.contains("\"afterJumpRepeats\":\"DEFAULT\""));
    }

    @Test
    public void canonicalOptionalAnchorRemainsStructuredJson() throws Exception {
        assertEquals(
                "{\"measureIndex\":1,\"quarterBeatOffset\":1.5}",
                json(Optional.of(new ScoreAnchor(1, 1.5))));
        assertEquals("null", json(Optional.empty()));
    }

    @Test
    public void quotedCharacterRetainsBothEscapeAndOriginalCharacter() throws Exception {
        assertEquals("\"\\\"\"", json("\""));
    }

    @Test
    public void backslashRetainsBothEscapeAndOriginalCharacter() throws Exception {
        assertEquals("\"\\\\\"", json("\\"));
        assertEquals("\"a\\\\b\"", json("a\\b"));
        assertEquals("\"a\\\"b\"", json("a\"b"));
    }

    @Test
    public void emptyPlainAndSolidusStringsKeepTheirBytes() throws Exception {
        assertEquals("\"\"", json(""));
        assertEquals("\"plain text\"", json("plain text"));
        assertEquals("\"/\"", json("/"));
    }

    @Test
    public void everyAsciiControlKeepsItsFourDigitUnicodeEscape() throws Exception {
        var controls = new StringBuilder();
        for (char c = 0; c < 32; c++) controls.append(c);
        assertEquals(
                "\"\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007"
                        + "\\u0008\\u0009\\u000a\\u000b\\u000c\\u000d\\u000e\\u000f"
                        + "\\u0010\\u0011\\u0012\\u0013\\u0014\\u0015\\u0016\\u0017"
                        + "\\u0018\\u0019\\u001a\\u001b\\u001c\\u001d\\u001e\\u001f\"",
                json(controls.toString()));
    }

    @Test
    public void validBmpAndSupplementaryUnicodeKeepTheirCharacters() throws Exception {
        String text = "café 水 " + new String(Character.toChars(0x1D11E));
        assertEquals("\"" + text + "\"", json(text));
    }

    public record EscapedRecord(String text, int count, boolean enabled) {}

    @Test
    public void nestedStringsUseTheSameEscapesWithoutChangingScalarFields() throws Exception {
        assertEquals(
                "{\"text\":\"a\\\"b\\\\c\",\"count\":7,\"enabled\":true}",
                json(new EscapedRecord("a\"b\\c", 7, true)));
        assertEquals(
                "[\"\\\"\",\"\\\\\",7,true,null]", json(new Object[] {"\"", "\\", 7, true, null}));
    }

    @Test
    public void mapKeysAndValuesUseTheSameStringContract() throws Exception {
        assertEquals("{\"a\\\"b\":\"c\\\\d\"}", json(java.util.Map.of("a\"b", "c\\d")));
    }
}
