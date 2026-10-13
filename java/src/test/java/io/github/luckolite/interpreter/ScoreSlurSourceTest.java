// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreSlurSourceTest {
    private ScoreSlurSource source() {
        var span =
                new ScoreSlurPages.Span(
                        "phrase",
                        new ScoreSlurPages.Anchor(2, 1),
                        new ScoreSlurPages.Anchor(5, 1),
                        ScoreSlurSpan.Placement.ABOVE,
                        ScoreSlurSpan.LineType.DOTTED,
                        List.of(new ScoreSlurSpan.ArcEvidence(2, .2f, .1f, .8f, .2f)));
        return ScoreSlurSource.capture(
                List.of(span),
                List.of(new ScoreSlurPages.Page(2, 2), new ScoreSlurPages.Page(5, 3)));
    }

    private byte[] write(ScoreSlurSource source) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ScoreSlurSourceWire.write(new DataOutputStream(bytes), source);
        return bytes.toByteArray();
    }

    private ScoreSlurSource read(byte[] bytes, int notes) throws Exception {
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        var result = ScoreSlurSourceWire.read(in, notes);
        assertEquals(0, in.available());
        return result;
    }

    private void reject(byte[] bytes, int notes) throws Exception {
        try {
            read(bytes, notes);
            fail("Invalid retained identities accepted");
        } catch (IOException expected) {
        }
    }

    @Test
    public void captureProducesExactJoinedOwners() {
        var source = source();
        assertEquals(5, source.noteOrigins().size());
        assertEquals(1, source.resolve().resolved().get(0).startOwner());
        assertEquals(3, source.resolve().resolved().get(0).endOwner());
    }

    @Test
    public void repeatedReorderAndInsertionKeepOriginalEndpoints() throws Exception {
        var source = source().edited(List.of(3, -1, 0, 1, 4)).edited(List.of(1, 3, 4, 0));
        var saved = read(write(source), 4);
        assertEquals(source, saved);
        assertEquals(1, saved.resolve().resolved().get(0).startOwner());
        assertEquals(3, saved.resolve().resolved().get(0).endOwner());
        assertTrue(saved.resolve().unresolved().isEmpty());
    }

    @Test
    public void deletedEndpointSurvivesSaveAndReloadWithoutBorrowingNewNotes() throws Exception {
        var edited = source().edited(List.of(0, 1, -1, 4));
        var saved = read(write(edited), 4);
        assertTrue(saved.resolve().resolved().isEmpty());
        assertEquals(source().spans(), saved.resolve().unresolved());
        assertEquals(edited.noteOrigins(), saved.noteOrigins());
    }

    @Test
    public void immutableUndoSnapshotRecoversOriginalPhrase() throws Exception {
        var original = source();
        var edited = original.edited(List.of(0, 1));
        assertTrue(edited.resolve().resolved().isEmpty());
        assertEquals(original, read(write(original), 5));
        assertEquals(1, original.resolve().resolved().size());
    }

    @Test
    public void emptyLegacySourceHasNoInventedOwnership() throws Exception {
        var source = ScoreSlurSource.empty(3);
        assertEquals(source, read(write(source), 3));
        assertTrue(source.resolve().resolved().isEmpty());
        assertTrue(source.noteOrigins().stream().allMatch(Optional::isEmpty));
    }

    @Test
    public void everyTruncatedPrefixFails() throws Exception {
        var bytes = write(source());
        for (int i = 0; i < bytes.length; i++) reject(Arrays.copyOf(bytes, i), 5);
    }

    @Test
    public void scoreCountAndFrameLengthAreStrict() throws Exception {
        var bytes = write(source());
        reject(bytes, 4);
        reject(bytes, -1);
        var invalid = bytes.clone();
        java.nio.ByteBuffer.wrap(invalid).putInt(0, 0);
        reject(invalid, 5);
        invalid = bytes.clone();
        java.nio.ByteBuffer.wrap(invalid).putInt(4, Integer.MAX_VALUE);
        reject(invalid, 5);
        invalid = Arrays.copyOf(bytes, bytes.length + 1);
        java.nio.ByteBuffer.wrap(invalid).putInt(4, bytes.length - 8 + 1);
        reject(invalid, 5);
    }

    @Test
    public void duplicateOriginsAndPartialAbsentSentinelFail() throws Exception {
        var bytes = write(source());
        var invalid = bytes.clone();
        // First origin is page2/note0; second origin must not repeat it.
        java.nio.ByteBuffer.wrap(invalid).putInt(28, 0);
        reject(invalid, 5);
        invalid = bytes.clone();
        java.nio.ByteBuffer.wrap(invalid).putInt(16, -1);
        reject(invalid, 5);
        invalid = bytes.clone();
        java.nio.ByteBuffer.wrap(invalid).putInt(12, 1);
        reject(invalid, 5);
    }

    @Test
    public void duplicateEditsAreRejectedEvenForUnownedInsertedNotes() {
        var source = ScoreSlurSource.empty(2);
        try {
            source.edited(List.of(0, 0));
            fail();
        } catch (IllegalArgumentException expected) {
        }
        assertEquals(2, source.edited(List.of(-1, -1)).noteOrigins().size());
    }

    @Test
    public void oversizedCaptureFailsBeforeAllocatingOrigins() {
        try {
            ScoreSlurSource.capture(List.of(), List.of(new ScoreSlurPages.Page(0, 1000001)));
            fail();
        } catch (IllegalArgumentException expected) {
        }
        try {
            ScoreSlurSource.empty(1000001);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void pageJoinDeduplicatesIdenticalCrossPageEvidenceAndRestoresOwners() {
        var original = source();
        var first = new ScoreSlurSource(original.spans(), original.noteOrigins().subList(0, 2));
        var second = new ScoreSlurSource(original.spans(), original.noteOrigins().subList(2, 5));
        assertTrue(first.resolve().resolved().isEmpty());
        assertTrue(second.resolve().resolved().isEmpty());
        assertEquals(original, ScoreSlurSource.join(List.of(first, second)));
    }

    @Test
    public void conflictingPageSpanIdentityCannotBeSilentlyMerged() {
        var original = source();
        var old = original.spans().get(0);
        var conflicting =
                new ScoreSlurPages.Span(
                        old.id(),
                        old.start(),
                        old.end(),
                        ScoreSlurSpan.Placement.BELOW,
                        old.lineType(),
                        old.evidence());
        var second = new ScoreSlurSource(List.of(conflicting), List.of());
        try {
            ScoreSlurSource.join(List.of(original, second));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void excludedArrangementEndpointDoesNotBreakEngravingOrBorrowInsertedNotes() {
        var source = source();
        assertEquals(1, source.engravingSpans(List.of(3, 1)).size());
        assertTrue(source.engravingSpans(List.of(1, -1)).isEmpty());
        assertEquals(1, source.resolve().resolved().size());
    }

    @Test
    public void emptyLargeSourceUsesAnImmutableUnownedView() {
        var source = ScoreSlurSource.empty(1000000);
        assertEquals(1000000, source.noteOrigins().size());
        assertTrue(source.noteOrigins().get(999999).isEmpty());
        assertTrue(source.resolve().resolved().isEmpty());
        try {
            source.noteOrigins().set(0, Optional.empty());
            fail();
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void unownedMillionNoteMapHasConstantSizeWireRepresentation() throws Exception {
        var original = ScoreSlurSource.empty(1000000);
        var bytes = write(original);
        assertEquals(24, bytes.length);
        var restored = read(bytes, 1000000);
        assertEquals(1000000, restored.noteOrigins().size());
        assertFalse(restored.hasOriginalOwners());
    }
}
