// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import org.junit.Test;
import static org.junit.Assert.*;

public class FolkOriginReviewTest {
    boolean excluded(String text) throws Exception {
        var m = ScoreCreditsDetector.class.getDeclaredMethod("folkOriginDescription", String.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, text);
    }

    @Test
    public void joinedLabel() throws Exception {
        assertTrue(excluded("Scottish Folksong"));
    }

    @Test
    public void spacedLabel() throws Exception {
        assertTrue(excluded("Polish Folk Song"));
    }

    @Test
    public void accentNoise() throws Exception {
        assertTrue(excluded("Polish Folk Śong"));
    }

    @Test
    public void traditionalOrigin() throws Exception {
        assertTrue(excluded("Traditional Celtic Folksong"));
    }

    @Test
    public void tuneLabel() throws Exception {
        assertTrue(excluded("Welsh Folk Tune"));
    }

    @Test
    public void singleTuneIdentifier() throws Exception {
        assertTrue(excluded("Polish Folk Song Meadowlight"));
    }

    @Test
    public void quotedTuneTitle() throws Exception {
        assertTrue(excluded("Welsh Folk Song “The Quiet Meadow”"));
    }

    @Test
    public void namedPerson() throws Exception {
        assertFalse(excluded("Alice Folk"));
    }

    @Test
    public void mixedByline() throws Exception {
        assertFalse(excluded("Scottish Folk Song by Alice Bell"));
    }

    @Test
    public void mixedSlash() throws Exception {
        assertFalse(excluded("Scottish Folksong/Alice Bell"));
    }

    @Test
    public void serviceCredit() throws Exception {
        assertFalse(excluded("Transcribed by Celtic Folk Song Studio"));
    }

    @Test
    public void unlabelledService() throws Exception {
        assertFalse(excluded("Celtic Folk Song Studio"));
    }

    @Test
    public void namedGroup() throws Exception {
        assertFalse(excluded("Irish Folk Song Ensemble"));
    }

    @Test
    public void ambiguousBareText() throws Exception {
        assertFalse(excluded("Folk Song"));
    }

    @Test
    public void multipleUnlabelledNames() throws Exception {
        assertFalse(excluded("Welsh Folk Song Alice Bell"));
    }
}
