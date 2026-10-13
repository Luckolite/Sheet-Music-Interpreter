// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;

/** Length-framed source identities and original page-local arcs, independent of edit history. */
public final class ScoreSlurSourceWire {
    private static final int MAGIC = 0x534c5431; // SLT1
    private static final int MAX_NOTES = ScoreSlurSource.MAX_NOTES;
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    private ScoreSlurSourceWire() {}

    public static void write(DataOutput out, ScoreSlurSource source) throws IOException {
        if (source.noteOrigins().size() > MAX_NOTES)
            throw new IOException("Too many retained slur note identities");
        var bytes = new ByteArrayOutputStream();
        var frame = new DataOutputStream(bytes);
        frame.writeInt(source.noteOrigins().size());
        frame.writeInt(source.hasOriginalOwners() ? source.noteOrigins().size() : 0);
        if (source.hasOriginalOwners())
            for (var origin : source.noteOrigins()) {
                frame.writeInt(origin.map(ScoreSlurPages.Anchor::pageIndex).orElse(-1));
                frame.writeInt(origin.map(ScoreSlurPages.Anchor::noteIndex).orElse(-1));
            }
        ScoreSlurPageWire.write(frame, source.spans());
        frame.flush();
        if (bytes.size() > MAX_BYTES) throw new IOException("Retained slur source too large");
        out.writeInt(MAGIC);
        out.writeInt(bytes.size());
        out.write(bytes.toByteArray());
    }

    public static ScoreSlurSource read(DataInput in, int expectedNotes) throws IOException {
        if (expectedNotes < 0 || expectedNotes > MAX_NOTES)
            throw new IOException("Invalid expected retained note count");
        if (in.readInt() != MAGIC) throw new IOException("Unknown retained slur source layout");
        int length = in.readInt();
        if (length < 0 || length > MAX_BYTES)
            throw new IOException("Invalid retained slur source length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        var frame = new DataInputStream(new ByteArrayInputStream(bytes));
        int count = frame.readInt();
        if (count != expectedNotes)
            throw new IOException("Retained slur identities do not match score notes");
        int originCount = frame.readInt();
        if (originCount != 0 && originCount != count)
            throw new IOException("Incomplete retained slur identity map");
        List<Optional<ScoreSlurPages.Anchor>> origins =
                originCount == 0 ? ScoreSlurSource.empty(count).noteOrigins() : new ArrayList<>();
        try {
            for (int i = 0; i < originCount; i++) {
                int page = frame.readInt(), note = frame.readInt();
                if (page == -1 && note == -1) origins.add(Optional.empty());
                else origins.add(Optional.of(new ScoreSlurPages.Anchor(page, note)));
            }
            var source = new ScoreSlurSource(ScoreSlurPageWire.read(frame), origins);
            if (frame.available() != 0)
                throw new IOException("Unexpected retained slur source bytes");
            return source;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid retained slur source identity", invalid);
        }
    }
}
