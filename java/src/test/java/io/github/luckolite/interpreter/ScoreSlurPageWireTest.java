// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScoreSlurPageWireTest {
    private ScoreSlurPages.Span span(String id) {
        return new ScoreSlurPages.Span(
                id,
                new ScoreSlurPages.Anchor(2, 3),
                new ScoreSlurPages.Anchor(5, 3),
                ScoreSlurSpan.Placement.AUTO,
                ScoreSlurSpan.LineType.SOLID,
                List.of(
                        new ScoreSlurSpan.ArcEvidence(2, .7f, .2f, .9f, .3f),
                        new ScoreSlurSpan.ArcEvidence(5, .1f, .2f, .3f, .2f)));
    }

    private byte[] write(List<ScoreSlurPages.Span> spans) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ScoreSlurPageWire.write(new DataOutputStream(bytes), spans);
        return bytes.toByteArray();
    }

    private List<ScoreSlurPages.Span> read(byte[] bytes) throws Exception {
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        var spans = ScoreSlurPageWire.read(in);
        assertEquals(0, in.available());
        return spans;
    }

    private void reject(byte[] bytes) throws Exception {
        try {
            read(bytes);
            fail("Invalid page-local extension accepted");
        } catch (IOException expected) {
        }
    }

    private byte[] integer(byte[] bytes, int offset, int value) {
        var copy = bytes.clone();
        java.nio.ByteBuffer.wrap(copy).putInt(offset, value);
        return copy;
    }

    @Test
    public void sameLocalIndexOnDifferentPagesSurvivesSaveReload() throws Exception {
        var original = span("phrase-𝄞");
        assertEquals(List.of(original), read(write(List.of(original))));
    }

    @Test
    public void unresolvedCrossPageSourceCanBeSavedBeforeMissingPageArrives() throws Exception {
        var original = span("arc");
        var pages = List.of(new ScoreSlurPages.Page(2, 4));
        assertEquals(1, ScoreSlurPages.join(List.of(original), pages).unresolved().size());
        var reloaded = read(write(List.of(original)));
        var completed =
                ScoreSlurPages.join(
                        reloaded,
                        List.of(new ScoreSlurPages.Page(2, 4), new ScoreSlurPages.Page(5, 6)));
        assertEquals(3, completed.resolved().get(0).startOwner());
        assertEquals(7, completed.resolved().get(0).endOwner());
        assertEquals(original.evidence(), completed.resolved().get(0).evidence());
    }

    @Test
    public void everyTruncatedPrefixFails() throws Exception {
        var bytes = write(List.of(span("a")));
        for (int i = 0; i < bytes.length; i++) reject(Arrays.copyOf(bytes, i));
    }

    @Test
    public void framingIdentityEnumsAndGeometryAreStrict() throws Exception {
        var bytes = write(List.of(span("a")));
        reject(integer(bytes, 0, 0));
        reject(integer(bytes, 4, -1));
        reject(integer(bytes, 4, 100001));
        reject(integer(bytes, 8, 8193));
        reject(integer(bytes, 12, 1025));
        reject(integer(bytes, 17, -1));
        reject(integer(bytes, 33, 3));
        reject(integer(bytes, 37, 3));
        reject(integer(bytes, 41, 65));
        reject(integer(bytes, 49, Float.floatToIntBits(Float.NaN)));
        var invalidUtf8 = bytes.clone();
        invalidUtf8[16] = (byte) 0xff;
        reject(invalidUtf8);
        var extended = Arrays.copyOf(bytes, bytes.length + 1);
        reject(integer(extended, 8, java.nio.ByteBuffer.wrap(bytes).getInt(8) + 1));
    }

    @Test
    public void duplicateIdentityAndIdenticalAnchorsFailRead() throws Exception {
        var bytes = write(List.of(span("a"), span("b")));
        int secondFrame = 16 + java.nio.ByteBuffer.wrap(bytes).getInt(8);
        bytes[secondFrame + 4] = 'a';
        reject(bytes);
        bytes = write(List.of(span("a")));
        reject(integer(bytes, 25, 2)); // end page becomes start page; both local slots already3
    }

    @Test
    public void invalidLaterRecordLeavesOutputUntouched() throws Exception {
        for (var spans :
                List.of(
                        List.of(span("a"), span("a")),
                        List.of(span("valid"), span("x".repeat(257))),
                        List.of(span("valid"), span("bad\ud800")))) {
            var bytes = new ByteArrayOutputStream();
            bytes.write(42);
            try {
                ScoreSlurPageWire.write(new DataOutputStream(bytes), spans);
                fail();
            } catch (IOException expected) {
            }
            assertArrayEquals(new byte[] {42}, bytes.toByteArray());
        }
    }

    @Test
    public void followingSectionIsNotConsumed() throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        ScoreSlurPageWire.write(out, List.of(span("a")));
        out.writeInt(12345);
        var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1, ScoreSlurPageWire.read(in).size());
        assertEquals(12345, in.readInt());
    }
}
