// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Private TRS1 extension; no cache version is assigned by this codec. */
public final class ScoreTremoloWire {
    private ScoreTremoloWire() {}

    private static final int MAGIC = 0x54525331, MAX_BYTES = 32 * 1024 * 1024, MAX_PAIRS = 100_000;

    public static void write(DataOutput out, ScoreTremoloSource source) throws IOException {
        out.write(encode(source));
    }

    public static ScoreTremoloSource read(DataInput in, int expectedNotes) throws IOException {
        int magic = in.readInt(), length = in.readInt();
        if (magic != MAGIC || length < 0 || length > MAX_BYTES)
            throw new IOException("Invalid tremolo frame header");
        byte[] bytes = new byte[length + 8];
        ByteBuffer.wrap(bytes).putInt(magic).putInt(length);
        in.readFully(bytes, 8, length);
        return decode(bytes, expectedNotes);
    }

    static int count(DataInputStream in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max) throw new IOException("Invalid tremolo count");
        return n;
    }

    static void anchors(DataOutputStream out, List<ScoreTremoloSource.Anchor> anchors)
            throws IOException {
        out.writeInt(anchors.size());
        for (var a : anchors) {
            out.writeInt(a.pageIndex());
            out.writeInt(a.noteIndex());
        }
    }

    static List<ScoreTremoloSource.Anchor> anchors(DataInputStream in) throws IOException {
        int n = count(in, 128);
        List<ScoreTremoloSource.Anchor> result = new ArrayList<>();
        for (int i = 0; i < n; i++)
            result.add(new ScoreTremoloSource.Anchor(in.readInt(), in.readInt()));
        return result;
    }

    public static byte[] encode(ScoreTremoloSource source) throws IOException {
        if (source.pairs().size() > MAX_PAIRS) throw new IOException("Too many tremolo pairs");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(source.noteOrigins().size());
        boolean stored = source.noteOrigins().stream().anyMatch(Optional::isPresent);
        out.writeInt(stored ? source.noteOrigins().size() : 0);
        if (stored)
            for (var origin : source.noteOrigins()) {
                out.writeInt(origin.map(ScoreTremoloSource.Anchor::pageIndex).orElse(-1));
                out.writeInt(origin.map(ScoreTremoloSource.Anchor::noteIndex).orElse(-1));
            }
        out.writeInt(source.pairs().size());
        for (var pair : source.pairs()) {
            byte[] id;
            try {
                ByteBuffer buffer =
                        StandardCharsets.UTF_8
                                .newEncoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .encode(java.nio.CharBuffer.wrap(pair.id()));
                id = new byte[buffer.remaining()];
                buffer.get(id);
            } catch (CharacterCodingException e) {
                throw new IOException("Invalid tremolo identity encoding", e);
            }
            out.writeInt(id.length);
            out.write(id);
            out.writeInt(pair.strokes());
            anchors(out, pair.first());
            anchors(out, pair.second());
            out.writeInt(pair.evidence().size());
            for (var e : pair.evidence()) {
                out.writeInt(e.pageIndex());
                out.writeFloat(e.left());
                out.writeFloat(e.top());
                out.writeFloat(e.right());
                out.writeFloat(e.bottom());
            }
            if (bytes.size() > MAX_BYTES) throw new IOException("Tremolo extension too large");
        }
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(frame);
        header.writeInt(MAGIC);
        header.writeInt(bytes.size());
        bytes.writeTo(header);
        return frame.toByteArray();
    }

    public static ScoreTremoloSource decode(byte[] bytes, int expectedNotes) throws IOException {
        if (bytes == null
                || bytes.length < 8
                || bytes.length > MAX_BYTES + 8
                || expectedNotes < 0
                || expectedNotes > 1_000_000) throw new IOException("Invalid tremolo frame");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC || in.readInt() != bytes.length - 8)
                throw new IOException("Invalid tremolo framing");
            int notes = count(in, 1_000_000), stored = count(in, 1_000_000);
            if (notes != expectedNotes || stored != 0 && stored != notes)
                throw new IOException("Tremolo note ownership mismatch");
            List<Optional<ScoreTremoloSource.Anchor>> origins = new ArrayList<>();
            for (int i = 0; i < stored; i++) {
                int page = in.readInt(), note = in.readInt();
                origins.add(
                        page == -1 && note == -1
                                ? Optional.empty()
                                : Optional.of(new ScoreTremoloSource.Anchor(page, note)));
            }
            if (stored == 0) origins.addAll(Collections.nCopies(notes, Optional.empty()));
            int pairs = count(in, MAX_PAIRS);
            List<ScoreTremoloSource.Pair> result = new ArrayList<>();
            for (int i = 0; i < pairs; i++) {
                int length = count(in, 1024);
                byte[] id = in.readNBytes(length);
                if (id.length != length) throw new EOFException("Truncated tremolo identity");
                String name =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(id))
                                .toString();
                int strokes = in.readInt();
                var first = anchors(in);
                var second = anchors(in);
                int evidence = count(in, 64);
                List<ScoreTremoloSource.Evidence> proof = new ArrayList<>();
                for (int j = 0; j < evidence; j++)
                    proof.add(
                            new ScoreTremoloSource.Evidence(
                                    in.readInt(),
                                    in.readFloat(),
                                    in.readFloat(),
                                    in.readFloat(),
                                    in.readFloat()));
                result.add(new ScoreTremoloSource.Pair(name, first, second, strokes, proof));
            }
            if (in.available() != 0) throw new IOException("Trailing tremolo bytes");
            return new ScoreTremoloSource(result, origins);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid tremolo source", e);
        }
    }
}
