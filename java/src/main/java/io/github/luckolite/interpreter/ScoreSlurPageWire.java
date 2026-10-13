// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Page-local source spans, including endpoints on pages absent from a particular join. */
public final class ScoreSlurPageWire {
    private static final int MAGIC = 0x534c5031; // SLP1
    private static final int MAX_SPANS = 100000, MAX_FRAME = 8192, MAX_BYTES = 16 * 1024 * 1024;
    private static final List<ScoreSlurSpan.Placement> PLACEMENTS =
            List.of(
                    ScoreSlurSpan.Placement.AUTO,
                    ScoreSlurSpan.Placement.ABOVE,
                    ScoreSlurSpan.Placement.BELOW);
    private static final List<ScoreSlurSpan.LineType> LINES =
            List.of(
                    ScoreSlurSpan.LineType.SOLID,
                    ScoreSlurSpan.LineType.DASHED,
                    ScoreSlurSpan.LineType.DOTTED);

    private ScoreSlurPageWire() {}

    public static void write(DataOutput out, List<ScoreSlurPages.Span> spans) throws IOException {
        try {
            if (spans.size() > MAX_SPANS) throw new IOException("Too many page-local slurs");
            var ids = new HashSet<String>();
            var frames = new ArrayList<byte[]>();
            long total = 8;
            for (var span : spans) {
                if (!ids.add(span.id()) || span.id().length() > 256 || span.evidence().size() > 64)
                    throw new IOException("Invalid page-local slur identity or evidence count");
                var id =
                        StandardCharsets.UTF_8
                                .newEncoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .encode(java.nio.CharBuffer.wrap(span.id()));
                var bytes = new ByteArrayOutputStream();
                var frame = new DataOutputStream(bytes);
                frame.writeInt(id.remaining());
                while (id.hasRemaining()) frame.writeByte(id.get());
                frame.writeInt(span.start().pageIndex());
                frame.writeInt(span.start().noteIndex());
                frame.writeInt(span.end().pageIndex());
                frame.writeInt(span.end().noteIndex());
                frame.writeInt(PLACEMENTS.indexOf(span.placement()));
                frame.writeInt(LINES.indexOf(span.lineType()));
                frame.writeInt(span.evidence().size());
                for (var arc : span.evidence()) {
                    frame.writeInt(arc.pageIndex());
                    frame.writeFloat(arc.startX());
                    frame.writeFloat(arc.startY());
                    frame.writeFloat(arc.endX());
                    frame.writeFloat(arc.endY());
                }
                frame.flush();
                total += 4L + bytes.size();
                if (bytes.size() > MAX_FRAME || total > MAX_BYTES)
                    throw new IOException("Page-local slur extension too large");
                frames.add(bytes.toByteArray());
            }
            out.writeInt(MAGIC);
            out.writeInt(frames.size());
            for (byte[] frame : frames) {
                out.writeInt(frame.length);
                out.write(frame);
            }
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid page-local slur source", invalid);
        }
    }

    public static List<ScoreSlurPages.Span> read(DataInput in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Unknown page-local slur layout");
        int count = bounded(in.readInt(), MAX_SPANS);
        long total = 8;
        var spans = new ArrayList<ScoreSlurPages.Span>();
        var ids = new HashSet<String>();
        try {
            for (int i = 0; i < count; i++) {
                int length = bounded(in.readInt(), MAX_FRAME);
                total += 4L + length;
                if (total > MAX_BYTES) throw new IOException("Page-local slur extension too large");
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                var frame = new DataInputStream(new ByteArrayInputStream(bytes));
                byte[] idBytes = new byte[bounded(frame.readInt(), 1024)];
                frame.readFully(idBytes);
                String id =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(idBytes))
                                .toString();
                if (id.length() > 256 || !ids.add(id))
                    throw new IOException("Invalid page-local slur identity");
                var start = new ScoreSlurPages.Anchor(frame.readInt(), frame.readInt());
                var end = new ScoreSlurPages.Anchor(frame.readInt(), frame.readInt());
                var placement = PLACEMENTS.get(bounded(frame.readInt(), 2));
                var line = LINES.get(bounded(frame.readInt(), 2));
                int evidenceCount = bounded(frame.readInt(), 64);
                var evidence = new ArrayList<ScoreSlurSpan.ArcEvidence>();
                for (int j = 0; j < evidenceCount; j++)
                    evidence.add(
                            new ScoreSlurSpan.ArcEvidence(
                                    frame.readInt(),
                                    frame.readFloat(),
                                    frame.readFloat(),
                                    frame.readFloat(),
                                    frame.readFloat()));
                if (frame.available() != 0)
                    throw new IOException("Unexpected page-local slur bytes");
                spans.add(new ScoreSlurPages.Span(id, start, end, placement, line, evidence));
            }
            return List.copyOf(spans);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid page-local slur source", invalid);
        }
    }

    private static int bounded(int value, int max) throws IOException {
        if (value < 0 || value > max)
            throw new IOException("Invalid page-local slur wire count or ID");
        return value;
    }
}
