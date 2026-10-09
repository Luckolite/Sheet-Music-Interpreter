// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

/** Versioned, bounded desktop decoder records. No Android types or Java object deserialization. */
public final class NativeDecoderWire {
    public static final int MAGIC = 0x4d534431, PORT = 45924, MAX_PIXELS = 20_000_000;
    public static final int MAX_PACKET = 45_000_000, MAX_MEASURES = 4000, MAX_EVENTS = 100_000;
    public static final int ANALYZE = 1, GEOMETRY = 2;

    /** Negative marker distinguishes the 22-field analysis from legacy 21-field counts. */
    static final int ANALYSIS_STEM_FORMAT = -22;

    /** Typed records append one bounded kind byte to the unchanged 22 fields. */
    static final int ANALYSIS_KIND_FORMAT = -23;

    static final int ANALYSIS_REST_KIND_FORMAT = -24;

    /** Same record sizes; typed tab string identity in the existing boundary int. */
    static final int ANALYSIS_TAB_TIE_FORMAT = -25;

    public record Request(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            int operation) {
        public Request(
                byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
            this(labels, gray, width, height, measures, ANALYZE);
        }
    }

    public interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private interface Reader<T> {
        T read(DataInputStream in) throws IOException;
    }

    static OmrScoreInterpreter.Analysis exchange(
            String host, int port, String secret, String fingerprint, Request request)
            throws IOException {
        return exchange(
                host,
                port,
                secret,
                fingerprint,
                request,
                body -> readAnalysis(body, request.measures.size()));
    }

    static NativeDecoderStages.Geometry exchangeGeometry(
            String host, int port, String secret, String fingerprint, Request request)
            throws IOException {
        return exchange(
                host,
                port,
                secret,
                fingerprint,
                request,
                body -> readGeometry(body, request.width, request.height));
    }

    private static <T> T exchange(
            String host,
            int port,
            String secret,
            String fingerprint,
            Request request,
            Reader<T> reader)
            throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            socket.setSoTimeout(30_000);
            socket.setTcpNoDelay(true);
            var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            var in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out.writeInt(MAGIC);
            out.writeUTF(secret);
            out.writeUTF(fingerprint);
            out.flush();
            if (in.readUnsignedByte() != 0) throw new IOException("Decoder source mismatch");
            packet(out, data -> writeRequest(data, request));
            try (var body = packet(in)) {
                return reader.read(body);
            }
        }
    }

    public static void packet(DataOutputStream out, Writer writer) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes);
                var data = new DataOutputStream(gzip)) {
            writer.write(data);
        }
        if (bytes.size() > MAX_PACKET) throw new IOException("Decoder packet limit");
        out.writeInt(bytes.size());
        bytes.writeTo(out);
        out.flush();
    }

    public static DataInputStream packet(DataInputStream in) throws IOException {
        int length = count(in, MAX_PACKET);
        byte[] data = new byte[length];
        in.readFully(data);
        var gzip = new GZIPInputStream(new ByteArrayInputStream(data));
        // Bound decompression too: a small compressed body must not expand without limit.
        return new DataInputStream(
                new FilterInputStream(gzip) {
                    int remaining = MAX_PACKET;

                    @Override
                    public int read() throws IOException {
                        if (remaining <= 0) throw new IOException("Decoder expansion limit");
                        int v = super.read();
                        if (v >= 0) remaining--;
                        return v;
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        if (remaining <= 0) throw new IOException("Decoder expansion limit");
                        int n = in.read(b, off, Math.min(len, remaining));
                        if (n > 0) remaining -= n;
                        return n;
                    }
                });
    }

    static int count(DataInputStream in, int limit) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > limit) throw new IOException("Decoder count limit");
        return n;
    }

    public static void writeRequest(DataOutputStream out, Request r) throws IOException {
        validateDimensions(r.width, r.height);
        if (r.labels.length != (long) r.width * r.height
                || r.gray.length != r.labels.length
                || r.measures.size() > MAX_MEASURES) throw new IOException("Decoder dimensions");
        if (r.operation != ANALYZE && r.operation != GEOMETRY)
            throw new IOException("Decoder operation");
        out.writeInt(r.operation);
        out.writeInt(r.width);
        out.writeInt(r.height);
        out.write(r.labels);
        out.write(r.gray);
        out.writeInt(r.measures.size());
        for (var m : r.measures) {
            out.writeFloat(m.left());
            out.writeFloat(m.right());
            out.writeFloat(m.top());
            out.writeFloat(m.bottom());
        }
    }

    public static Request readRequest(DataInputStream in) throws IOException {
        int operation = in.readInt();
        if (operation != ANALYZE && operation != GEOMETRY)
            throw new IOException("Decoder operation");
        int width = in.readInt(), height = in.readInt();
        validateDimensions(width, height);
        byte[] labels = new byte[width * height], gray = new byte[labels.length];
        in.readFully(labels);
        in.readFully(gray);
        for (byte label : labels)
            if (label < 0 || label > 5) throw new IOException("Decoder label range");
        int count = count(in, MAX_MEASURES);
        var measures = new ArrayList<MeasureRegion>(count);
        for (int i = 0; i < count; i++)
            measures.add(new MeasureRegion(finite(in), finite(in), finite(in), finite(in)));
        if (in.read() != -1) throw new IOException("Trailing decoder input");
        return new Request(labels, gray, width, height, List.copyOf(measures), operation);
    }

    private static void validateDimensions(int w, int h) throws IOException {
        if (w < 1 || h < 1 || (long) w * h > MAX_PIXELS)
            throw new IOException("Decoder dimensions");
    }

    private static float finite(DataInputStream in) throws IOException {
        float v = in.readFloat();
        if (!Float.isFinite(v)) throw new IOException("Nonfinite decoder value");
        return v;
    }

    static void writeGeometry(DataOutputStream out, NativeDecoderStages.Geometry geometry)
            throws IOException {
        if (geometry.labels().length > MAX_PIXELS || geometry.measures().size() > MAX_MEASURES)
            throw new IOException("Decoder geometry limit");
        out.writeInt(geometry.labels().length);
        out.write(geometry.labels());
        out.writeInt(geometry.measures().size());
        for (var m : geometry.measures()) {
            out.writeFloat(m.left());
            out.writeFloat(m.right());
            out.writeFloat(m.top());
            out.writeFloat(m.bottom());
        }
    }

    static NativeDecoderStages.Geometry readGeometry(DataInputStream in, int width, int height)
            throws IOException {
        validateDimensions(width, height);
        int size = count(in, MAX_PIXELS);
        if (size != (long) width * height) throw new IOException("Decoder geometry dimensions");
        byte[] labels = new byte[size];
        in.readFully(labels);
        for (byte label : labels)
            if (label < 0 || label > 5) throw new IOException("Decoder label range");
        int count = count(in, MAX_MEASURES);
        var measures = new ArrayList<MeasureRegion>(count);
        for (int i = 0; i < count; i++)
            measures.add(new MeasureRegion(finite(in), finite(in), finite(in), finite(in)));
        if (in.read() != -1) throw new IOException("Trailing geometry output");
        return new NativeDecoderStages.Geometry(labels, List.copyOf(measures));
    }

    static void writeAnalysis(DataOutputStream out, OmrScoreInterpreter.Analysis score)
            throws IOException {
        writeAnalysis(out, score, ANALYSIS_TAB_TIE_FORMAT);
    }

    static void writeAnalysis(DataOutputStream out, OmrScoreInterpreter.Analysis score, int marker)
            throws IOException {
        if (marker != ANALYSIS_TAB_TIE_FORMAT
                && marker != ANALYSIS_REST_KIND_FORMAT
                && marker != ANALYSIS_KIND_FORMAT)
            throw new IOException("Unsupported analysis writer marker");
        if (marker == ANALYSIS_KIND_FORMAT
                && score.rests().stream().anyMatch(ScoreRestEvent::isFullMeasure))
            throw new IOException("Legacy analysis would lose full-measure rest kind");
        for (var note : score.notes()) {
            if (!TabTieIdentity.valid(note.boundaryTies())
                    || TabTieIdentity.typed(note.boundaryTies())
                            && (marker != ANALYSIS_TAB_TIE_FORMAT
                                    || note.kind() != ScoreNoteEvent.Kind.PITCHED))
                throw new IOException("Unsupported or invalid typed tab tie evidence");
        }
        if (score.notes().size() > MAX_EVENTS
                || score.rests().size() > MAX_EVENTS
                || score.keyChanges().size() > MAX_MEASURES)
            throw new IOException("Decoder event limit");
        out.writeInt(marker);
        out.writeInt(score.notes().size());
        for (var n : score.notes()) {
            out.writeInt(n.measureIndex());
            out.writeFloat(n.positionInMeasure());
            out.writeInt(n.staffStep());
            out.writeInt(n.staffIndex());
            out.writeInt(n.staffCount());
            out.writeFloat(n.pageY());
            out.writeBoolean(n.tiedFromPrevious());
            out.writeInt(n.augmentationDots());
            out.writeInt(n.beamCount());
            out.writeInt(n.writtenAccidental());
            out.writeFloat(n.unbeamedDurationBeats());
            out.writeInt(n.tupletDivisor());
            out.writeFloat(n.followingRestBeats());
            out.writeInt(n.articulations());
            out.writeInt(n.clefBottomDiatonic());
            out.writeBoolean(n.crossStaffBeam());
            out.writeFloat(n.leadingRestBeats());
            out.writeBoolean(n.compactOpening());
            out.writeInt(n.octaveShift());
            out.writeInt(n.boundaryTies());
            out.writeInt(n.tupletNormalNotes());
            out.writeInt(n.stemDirection());
            out.writeByte(noteKindId(n.kind()));
        }
        out.writeInt(score.keyChanges().size());
        for (var k : score.keyChanges()) {
            out.writeInt(k.measureIndex());
            out.writeInt(k.fifths());
        }
        out.writeInt(score.rests().size());
        for (var r : score.rests()) {
            out.writeInt(r.measureIndex());
            out.writeFloat(r.positionInMeasure());
            out.writeFloat(r.pageY());
            out.writeFloat(r.pageHeight());
            out.writeInt(r.staffIndex());
            out.writeInt(r.staffCount());
            out.writeDouble(r.durationBeats());
            if (marker == ANALYSIS_REST_KIND_FORMAT || marker == ANALYSIS_TAB_TIE_FORMAT)
                out.writeByte(restKindId(r.kind()));
        }
    }

    private static int boundaryTies(DataInputStream in, boolean tabTyped) throws IOException {
        int evidence = in.readInt();
        if (!TabTieIdentity.valid(evidence) || !tabTyped && TabTieIdentity.typed(evidence))
            throw new IOException("Unsupported or invalid boundary tie evidence");
        return evidence;
    }

    static OmrScoreInterpreter.Analysis readAnalysis(DataInputStream in, int measures)
            throws IOException {
        int marker = in.readInt();
        boolean tabTyped = marker == ANALYSIS_TAB_TIE_FORMAT;
        boolean restTyped = tabTyped || marker == ANALYSIS_REST_KIND_FORMAT;
        boolean typed = restTyped || marker == ANALYSIS_KIND_FORMAT;
        boolean stems = typed || marker == ANALYSIS_STEM_FORMAT;
        int count = stems ? count(in, MAX_EVENTS) : marker;
        if (count < 0 || count > MAX_EVENTS) throw new IOException("Decoder analysis format/count");
        var notes = new ArrayList<ScoreNoteEvent>(count);
        for (int i = 0; i < count; i++)
            notes.add(
                    new ScoreNoteEvent(
                            index(in, measures),
                            finite(in),
                            in.readInt(),
                            in.readInt(),
                            in.readInt(),
                            finite(in),
                            in.readBoolean(),
                            in.readInt(),
                            in.readInt(),
                            in.readInt(),
                            finite(in),
                            in.readInt(),
                            finite(in),
                            in.readInt(),
                            in.readInt(),
                            in.readBoolean(),
                            finite(in),
                            in.readBoolean(),
                            in.readInt(),
                            boundaryTies(in, tabTyped),
                            normalCount(in),
                            stems ? stemDirection(in) : 0,
                            typed ? noteKind(in) : ScoreNoteEvent.Kind.PITCHED));
        for (var note : notes)
            if (TabTieIdentity.typed(note.boundaryTies())
                    && note.kind() != ScoreNoteEvent.Kind.PITCHED)
                throw new IOException("Unpitched tab tie identity");
        count = count(in, MAX_MEASURES);
        var keys = new ArrayList<ScoreKeyChange>(count);
        for (int i = 0; i < count; i++) {
            int index = index(in, measures), fifths = in.readInt();
            if (fifths < -7 || fifths > 7) throw new IOException("Decoder key");
            keys.add(new ScoreKeyChange(index, fifths));
        }
        count = count(in, MAX_EVENTS);
        var rests = new ArrayList<ScoreRestEvent>(count);
        for (int i = 0; i < count; i++) {
            int index = index(in, measures);
            float position = finite(in), y = finite(in), height = finite(in);
            int staff = in.readInt(), staffs = in.readInt();
            double duration = in.readDouble();
            if (!Double.isFinite(duration) || duration <= 0)
                throw new IOException("Decoder rest duration");
            ScoreRestEvent.Kind kind = restTyped ? restKind(in) : ScoreRestEvent.Kind.LITERAL;
            if (kind == ScoreRestEvent.Kind.FULL_MEASURE && duration != 4)
                throw new IOException("Decoder full-measure rest glyph base");
            rests.add(
                    new ScoreRestEvent(index, position, y, height, staff, staffs, duration, kind));
        }
        if (in.read() != -1) throw new IOException("Trailing decoder result");
        return new OmrScoreInterpreter.Analysis(notes, keys, rests);
    }

    private static int index(DataInputStream in, int measures) throws IOException {
        int index = in.readInt();
        if (index < 0 || index >= measures) throw new IOException("Decoder measure index");
        return index;
    }

    private static int stemDirection(DataInputStream in) throws IOException {
        int direction = in.readInt();
        if (direction < -1 || direction > 1) throw new IOException("Decoder stem direction");
        return direction;
    }

    private static int noteKindId(ScoreNoteEvent.Kind kind) throws IOException {
        if (kind == ScoreNoteEvent.Kind.PITCHED) return 0;
        if (kind == ScoreNoteEvent.Kind.UNPITCHED) return 1;
        throw new IOException("Decoder note kind");
    }

    private static ScoreNoteEvent.Kind noteKind(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 0 -> ScoreNoteEvent.Kind.PITCHED;
            case 1 -> ScoreNoteEvent.Kind.UNPITCHED;
            default -> throw new IOException("Decoder note kind");
        };
    }

    private static int restKindId(ScoreRestEvent.Kind kind) throws IOException {
        if (kind == ScoreRestEvent.Kind.LITERAL) return 0;
        if (kind == ScoreRestEvent.Kind.FULL_MEASURE) return 1;
        throw new IOException("Decoder rest kind");
    }

    private static ScoreRestEvent.Kind restKind(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 0 -> ScoreRestEvent.Kind.LITERAL;
            case 1 -> ScoreRestEvent.Kind.FULL_MEASURE;
            default -> throw new IOException("Decoder rest kind");
        };
    }

    private static int normalCount(DataInputStream in) throws IOException {
        int normal = count(in, 16);
        if (normal == 0) throw new IOException("Decoder tuplet ratio");
        return normal;
    }
}
