// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import io.github.luckolite.interpreter.ScoreClockContextSource.*;

/** Guide299 context frame, appended strictly after the complete Guide298 beam/TRS2 body. */
public final class ScoreClockContextWire {
    private ScoreClockContextWire() {}

    public static final int MAGIC = 0x43545831, MAX_BYTES = 4 * 1024 * 1024;

    public static void requireLegacyCompatible(ScoreClockContextSource value) throws IOException {
        if (!value.equals(
                ScoreClockContextSource.unavailable(
                        value.noteCount(), value.measureCount(), value.restCount())))
            throw new IOException("Legacy guide cannot preserve source clock context");
    }

    private static final class Limited extends OutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        public void write(int b) throws IOException {
            if (bytes.size() == MAX_BYTES) throw new IOException("Clock frame budget");
            bytes.write(b);
        }

        public void write(byte[] b, int o, int n) throws IOException {
            if (n < 0 || n > MAX_BYTES - bytes.size()) throw new IOException("Clock frame budget");
            bytes.write(b, o, n);
        }
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        ByteBuffer b =
                StandardCharsets.UTF_8
                        .newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .encode(java.nio.CharBuffer.wrap(value));
        if (b.remaining() > 4096) throw new IOException("Clock text budget");
        out.writeInt(b.remaining());
        byte[] v = new byte[b.remaining()];
        b.get(v);
        out.write(v);
    }

    private static String text(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 4096 || n > in.available()) throw new IOException("Clock text length");
        byte[] v = new byte[n];
        in.readFully(v);
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(v))
                .toString();
    }

    private static <T> T enumeration(DataInputStream in, T[] values) throws IOException {
        int n = in.readUnsignedByte();
        if (n >= values.length) throw new IOException("Clock enum");
        return values[n];
    }

    private static boolean bool(DataInputStream in) throws IOException {
        int n = in.readUnsignedByte();
        if (n > 1) throw new IOException("Clock boolean");
        return n == 1;
    }

    private static int count(DataInputStream in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max || n > in.available()) throw new IOException("Clock collection count");
        return n;
    }

    private static void indices(DataOutputStream out, List<Integer> values) throws IOException {
        out.writeInt(values.size());
        for (int value : values) out.writeInt(value);
    }

    private static List<Integer> indices(DataInputStream in) throws IOException {
        int n = count(in, 128);
        if (4L * n > in.available()) throw new IOException("Clock index length");
        var values = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) values.add(in.readInt());
        return values;
    }

    private static void writeOrigin(DataOutputStream out, Origin value) throws IOException {
        text(out, value.documentSha256());
        out.writeInt(value.physicalPageIndex());
        out.writeInt(value.physicalPageCount());
        out.writeInt(value.width());
        out.writeInt(value.height());
        text(out, value.rasterSha256());
        text(out, value.labelsSha256());
    }

    private static Origin readOrigin(DataInputStream in) throws IOException {
        return new Origin(
                text(in),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                text(in),
                text(in));
    }

    private static void writeBox(DataOutputStream out, Box value) throws IOException {
        out.writeFloat(value.left());
        out.writeFloat(value.top());
        out.writeFloat(value.right());
        out.writeFloat(value.bottom());
    }

    private static Box readBox(DataInputStream in) throws IOException {
        return new Box(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
    }

    private static void writeRasterBox(DataOutputStream out, RasterBox value) throws IOException {
        out.writeInt(value.left());
        out.writeInt(value.top());
        out.writeInt(value.right());
        out.writeInt(value.bottom());
    }

    private static RasterBox readRasterBox(DataInputStream in) throws IOException {
        return new RasterBox(in.readInt(), in.readInt(), in.readInt(), in.readInt());
    }

    private static void writePartWord(DataOutputStream out, PartWord value) throws IOException {
        out.writeInt(value.ordinal());
        out.writeInt(value.system());
        out.writeInt(value.firstMeasure());
        out.writeInt(value.measureAfterLast());
        text(out, value.text());
        text(out, value.textSha256());
        text(out, value.canonicalPart());
        writeBox(out, value.bounds());
        out.writeByte(value.role().ordinal());
        out.writeInt(value.sampledMembers());
        text(out, value.sampledSha256());
        out.writeByte(value.status().ordinal());
    }

    private static PartWord readPartWord(DataInputStream in) throws IOException {
        return new PartWord(
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                text(in),
                text(in),
                text(in),
                readBox(in),
                enumeration(in, WordRole.values()),
                in.readInt(),
                text(in),
                enumeration(in, Status.values()));
    }

    private static void writeNumberRead(DataOutputStream out, NumberRead value) throws IOException {
        out.writeInt(value.ordinal());
        out.writeInt(value.system());
        text(out, value.text());
        out.writeInt(value.value());
        out.writeInt(value.originCandidate());
        writeBox(out, value.bounds());
        out.writeByte(value.route().ordinal());
        out.writeByte(value.status().ordinal());
    }

    private static NumberRead readNumberRead(DataInputStream in) throws IOException {
        return new NumberRead(
                in.readInt(),
                in.readInt(),
                text(in),
                in.readInt(),
                in.readInt(),
                readBox(in),
                enumeration(in, NumberRoute.values()),
                enumeration(in, Status.values()));
    }

    private static void writeMeterRead(DataOutputStream out, MeterRead value) throws IOException {
        out.writeInt(value.ordinal());
        out.writeInt(value.measure());
        text(out, value.text());
        out.writeInt(value.numerator());
        out.writeInt(value.denominator());
        writeRasterBox(out, value.bounds());
        out.writeFloat(value.firstLine());
        out.writeFloat(value.gap());
        out.writeInt(value.votes());
        out.writeInt(value.sampledMembers());
        text(out, value.sampledSha256());
        out.writeByte(value.route().ordinal());
        out.writeByte(value.originalProducerAccepted() ? 1 : 0);
        out.writeByte(value.status().ordinal());
    }

    private static MeterRead readMeterRead(DataInputStream in) throws IOException {
        return new MeterRead(
                in.readInt(),
                in.readInt(),
                text(in),
                in.readInt(),
                in.readInt(),
                readRasterBox(in),
                in.readFloat(),
                in.readFloat(),
                in.readInt(),
                in.readInt(),
                text(in),
                enumeration(in, MeterRoute.values()),
                bool(in),
                enumeration(in, Status.values()));
    }

    private static void writePrintedValue(DataOutputStream out, PrintedValue value)
            throws IOException {
        out.writeInt(value.noteIndex());
        out.writeInt(value.measure());
        out.writeInt(value.staff());
        out.writeInt(value.staffs());
        out.writeByte(value.kind().ordinal());
        out.writeFloat(value.baseBeats());
        out.writeInt(value.dots());
        out.writeInt(value.beams());
        out.writeByte(value.route().ordinal());
        text(out, value.originalExit());
        out.writeInt(value.admissionFlags());
        out.writeInt(value.selectedMembers());
        out.writeInt(value.parentMembers());
        out.writeInt(value.parentLeaves());
        out.writeInt(value.residualMembers());
        text(out, value.selectedSha256());
        text(out, value.parentSha256());
        out.writeByte(value.model().ordinal());
        out.writeByte(value.status().ordinal());
    }

    private static PrintedValue readPrintedValue(DataInputStream in) throws IOException {
        return new PrintedValue(
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                enumeration(in, ValueKind.values()),
                in.readFloat(),
                in.readInt(),
                in.readInt(),
                enumeration(in, ValueRoute.values()),
                text(in),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                text(in),
                text(in),
                enumeration(in, Model.values()),
                enumeration(in, Status.values()));
    }

    private static void writeGlyphLedger(DataOutputStream out, GlyphLedger value)
            throws IOException {
        out.writeInt(value.measure());
        out.writeInt(value.semanticLabel());
        out.writeLong(value.population());
        out.writeLong(value.coveredUnique());
        out.writeLong(value.noteMembers());
        out.writeLong(value.nonRhythmicMembers());
        out.writeLong(value.restMembers());
        out.writeLong(value.unknownMembers());
        text(out, value.coverageSha256());
        text(out, value.producerRoute());
        out.writeByte(value.status().ordinal());
    }

    private static GlyphLedger readGlyphLedger(DataInputStream in) throws IOException {
        return new GlyphLedger(
                in.readInt(),
                in.readInt(),
                in.readLong(),
                in.readLong(),
                in.readLong(),
                in.readLong(),
                in.readLong(),
                in.readLong(),
                text(in),
                text(in),
                enumeration(in, Status.values()));
    }

    private static void writeRestValue(DataOutputStream out, RestValue value) throws IOException {
        out.writeInt(value.restIndex());
        out.writeInt(value.measure());
        out.writeInt(value.staff());
        out.writeInt(value.staffs());
        out.writeByte(value.kind().ordinal());
        out.writeFloat(value.baseBeats());
        out.writeInt(value.dots());
        text(out, value.membersSha256());
        text(out, value.producerRoute());
        out.writeByte(value.status().ordinal());
    }

    private static RestValue readRestValue(DataInputStream in) throws IOException {
        return new RestValue(
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                enumeration(in, RestKind.values()),
                in.readFloat(),
                in.readInt(),
                text(in),
                text(in),
                enumeration(in, Status.values()));
    }

    private static void writeVoiceGroup(DataOutputStream out, VoiceGroup value) throws IOException {
        out.writeInt(value.measure());
        out.writeInt(value.staff());
        out.writeInt(value.staffs());
        text(out, value.voiceId());
        out.writeInt(value.ordinal());
        out.writeByte(value.kind().ordinal());
        indices(out, value.noteIndices());
        indices(out, value.restIndices());
        text(out, value.membersSha256());
        text(out, value.sourceRoute());
        out.writeByte(value.status().ordinal());
    }

    private static VoiceGroup readVoiceGroup(DataInputStream in) throws IOException {
        return new VoiceGroup(
                in.readInt(),
                in.readInt(),
                in.readInt(),
                text(in),
                in.readInt(),
                enumeration(in, GroupKind.values()),
                indices(in),
                indices(in),
                text(in),
                text(in),
                enumeration(in, Status.values()));
    }

    private static void writeSyncAnchor(DataOutputStream out, SyncAnchor value) throws IOException {
        out.writeInt(value.measure());
        text(out, value.firstVoice());
        out.writeInt(value.firstThroughOrdinal());
        text(out, value.secondVoice());
        out.writeInt(value.secondThroughOrdinal());
        out.writeByte(value.kind().ordinal());
        writeBox(out, value.bounds());
        text(out, value.membersSha256());
        text(out, value.sourceRoute());
        out.writeByte(value.status().ordinal());
    }

    private static SyncAnchor readSyncAnchor(DataInputStream in) throws IOException {
        return new SyncAnchor(
                in.readInt(),
                text(in),
                in.readInt(),
                text(in),
                in.readInt(),
                enumeration(in, SyncKind.values()),
                readBox(in),
                text(in),
                text(in),
                enumeration(in, Status.values()));
    }

    private static void writeMissing(DataOutputStream out, Missing value) throws IOException {
        out.writeByte(value.requirement().ordinal());
        out.writeInt(value.measure());
    }

    private static Missing readMissing(DataInputStream in) throws IOException {
        return new Missing(enumeration(in, Requirement.values()), in.readInt());
    }

    public static byte[] encode(ScoreClockContextSource value) throws IOException {
        var limited = new Limited();
        var out = new DataOutputStream(limited);
        int schema =
                value.origin().annotationRasterSha256().equals(value.origin().rasterSha256())
                        ? 1
                        : 2;
        out.writeInt(schema);
        out.writeInt(value.noteCount());
        out.writeInt(value.measureCount());
        out.writeInt(value.restCount());
        writeOrigin(out, value.origin());
        if (schema == 2) text(out, value.origin().annotationRasterSha256());
        out.writeInt(value.partWords().size());
        for (var row : value.partWords()) writePartWord(out, row);
        out.writeInt(value.numbers().size());
        for (var row : value.numbers()) writeNumberRead(out, row);
        out.writeInt(value.meters().size());
        for (var row : value.meters()) writeMeterRead(out, row);
        out.writeInt(value.printedValues().size());
        for (var row : value.printedValues()) writePrintedValue(out, row);
        out.writeInt(value.glyphLedgers().size());
        for (var row : value.glyphLedgers()) writeGlyphLedger(out, row);
        out.writeInt(value.restValues().size());
        for (var row : value.restValues()) writeRestValue(out, row);
        out.writeInt(value.voiceGroups().size());
        for (var row : value.voiceGroups()) writeVoiceGroup(out, row);
        out.writeInt(value.syncAnchors().size());
        for (var row : value.syncAnchors()) writeSyncAnchor(out, row);
        out.writeInt(value.missing().size());
        for (var row : value.missing()) writeMissing(out, row);
        byte[] payload = limited.bytes.toByteArray();
        var framed = new ByteArrayOutputStream(payload.length + 8);
        var frame = new DataOutputStream(framed);
        frame.writeInt(MAGIC);
        frame.writeInt(payload.length);
        frame.write(payload);
        return framed.toByteArray();
    }

    public static void write(DataOutput out, ScoreClockContextSource value) throws IOException {
        out.write(encode(value));
    }

    public static ScoreClockContextSource read(DataInput input, int notes, int measures, int rests)
            throws IOException {
        int magic = input.readInt(), n = input.readInt();
        if (magic != MAGIC || n < 80 || n > MAX_BYTES) throw new IOException("Clock frame header");
        byte[] frame = new byte[n + 8];
        ByteBuffer.wrap(frame).putInt(magic).putInt(n);
        input.readFully(frame, 8, n);
        return decode(frame, notes, measures, rests);
    }

    public static ScoreClockContextSource decode(byte[] frame, int notes, int measures, int rests)
            throws IOException {
        try {
            if (frame.length < 88 || frame.length > MAX_BYTES + 8)
                throw new IOException("Clock frame length");
            var in = new DataInputStream(new ByteArrayInputStream(frame));
            if (in.readInt() != MAGIC || in.readInt() != frame.length - 8)
                throw new IOException("Clock frame identity");
            int schema = in.readInt();
            if (schema != 1 && schema != 2) throw new IOException("Clock schema");
            int noteCount = in.readInt(), measureCount = in.readInt(), restCount = in.readInt();
            if (noteCount != notes || measureCount != measures || restCount != rests)
                throw new IOException("Clock page counts");
            Origin origin = readOrigin(in);
            if (schema == 2)
                origin =
                        new Origin(
                                origin.documentSha256(),
                                origin.physicalPageIndex(),
                                origin.physicalPageCount(),
                                origin.width(),
                                origin.height(),
                                origin.rasterSha256(),
                                origin.labelsSha256(),
                                text(in));
            long total = 0;
            int npartWords = count(in, 10000);
            total += npartWords;
            if (total > 100000) throw new IOException("Clock fact budget");
            var partWords = new ArrayList<PartWord>();
            for (int i = 0; i < npartWords; i++) partWords.add(readPartWord(in));
            int nnumbers = count(in, 20000);
            total += nnumbers;
            if (total > 100000) throw new IOException("Clock fact budget");
            var numbers = new ArrayList<NumberRead>();
            for (int i = 0; i < nnumbers; i++) numbers.add(readNumberRead(in));
            int nmeters = count(in, 10000);
            total += nmeters;
            if (total > 100000) throw new IOException("Clock fact budget");
            var meters = new ArrayList<MeterRead>();
            for (int i = 0; i < nmeters; i++) meters.add(readMeterRead(in));
            int nprintedValues = count(in, 100000);
            total += nprintedValues;
            if (total > 100000) throw new IOException("Clock fact budget");
            var printedValues = new ArrayList<PrintedValue>();
            for (int i = 0; i < nprintedValues; i++) printedValues.add(readPrintedValue(in));
            int nglyphLedgers = count(in, 100000);
            total += nglyphLedgers;
            if (total > 100000) throw new IOException("Clock fact budget");
            var glyphLedgers = new ArrayList<GlyphLedger>();
            for (int i = 0; i < nglyphLedgers; i++) glyphLedgers.add(readGlyphLedger(in));
            int nrestValues = count(in, 100000);
            total += nrestValues;
            if (total > 100000) throw new IOException("Clock fact budget");
            var restValues = new ArrayList<RestValue>();
            for (int i = 0; i < nrestValues; i++) restValues.add(readRestValue(in));
            int nvoiceGroups = count(in, 100000);
            total += nvoiceGroups;
            if (total > 100000) throw new IOException("Clock fact budget");
            var voiceGroups = new ArrayList<VoiceGroup>();
            for (int i = 0; i < nvoiceGroups; i++) voiceGroups.add(readVoiceGroup(in));
            int nsyncAnchors = count(in, 100000);
            total += nsyncAnchors;
            if (total > 100000) throw new IOException("Clock fact budget");
            var syncAnchors = new ArrayList<SyncAnchor>();
            for (int i = 0; i < nsyncAnchors; i++) syncAnchors.add(readSyncAnchor(in));
            int nmissing = count(in, 100000);
            total += nmissing;
            if (total > 100000) throw new IOException("Clock fact budget");
            var missing = new ArrayList<Missing>();
            for (int i = 0; i < nmissing; i++) missing.add(readMissing(in));
            if (in.available() != 0) throw new IOException("Trailing clock context bytes");
            return new ScoreClockContextSource(
                    origin,
                    notes,
                    measures,
                    rests,
                    partWords,
                    numbers,
                    meters,
                    printedValues,
                    glyphLedgers,
                    restValues,
                    voiceGroups,
                    syncAnchors,
                    missing);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid clock context record", invalid);
        }
    }
}
