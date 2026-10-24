// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Length-framed semantic extension for guide263. Starts belong to this page; span endpoints may
 * reach later pages and are validated against the complete score after page joining.
 */
public final class ScoreSemanticWire {
    private ScoreSemanticWire() {}

    private static final int MAX_RECORD_BYTES = 262144;
    private static final List<ScoreExpressiveEvent.Kind> EXPRESSION_KINDS =
            List.of(
                    ScoreExpressiveEvent.Kind.UNRESOLVED_DIRECTION,
                    ScoreExpressiveEvent.Kind.RITARDANDO,
                    ScoreExpressiveEvent.Kind.RALLENTANDO,
                    ScoreExpressiveEvent.Kind.RITENUTO,
                    ScoreExpressiveEvent.Kind.ACCELERANDO,
                    ScoreExpressiveEvent.Kind.A_TEMPO,
                    ScoreExpressiveEvent.Kind.TEMPO_PRIMO,
                    ScoreExpressiveEvent.Kind.SAME_TEMPO,
                    ScoreExpressiveEvent.Kind.FERMATA,
                    ScoreExpressiveEvent.Kind.BREATH,
                    ScoreExpressiveEvent.Kind.CAESURA,
                    ScoreExpressiveEvent.Kind.SFORZANDO,
                    ScoreExpressiveEvent.Kind.SFORZATO,
                    ScoreExpressiveEvent.Kind.SFORZANDO_PIANO,
                    ScoreExpressiveEvent.Kind.PEDAL_DOWN,
                    ScoreExpressiveEvent.Kind.PEDAL_UP,
                    ScoreExpressiveEvent.Kind.ARPEGGIO,
                    ScoreExpressiveEvent.Kind.CRESCENDO,
                    ScoreExpressiveEvent.Kind.DIMINUENDO,
                    ScoreExpressiveEvent.Kind.METRIC_MODULATION,
                    ScoreExpressiveEvent.Kind.NATURAL_HARMONIC,
                    ScoreExpressiveEvent.Kind.FALL_OFF,
                    ScoreExpressiveEvent.Kind.PARENTHESIZED_NOTE_GROUP);
    private static final List<ScoreExpressiveEvent.Scope> SCOPES =
            List.of(
                    ScoreExpressiveEvent.Scope.SCORE,
                    ScoreExpressiveEvent.Scope.PART,
                    ScoreExpressiveEvent.Scope.VOICE,
                    ScoreExpressiveEvent.Scope.NOTE,
                    ScoreExpressiveEvent.Scope.REST,
                    ScoreExpressiveEvent.Scope.UNRESOLVED);
    private static final List<ScoreExpressiveEvent.Strength> STRENGTHS =
            List.of(
                    ScoreExpressiveEvent.Strength.UNSPECIFIED,
                    ScoreExpressiveEvent.Strength.POCO,
                    ScoreExpressiveEvent.Strength.MOLTO);
    private static final List<ScorePlaybackDirection.AfterJumpRepeats> POLICIES =
            List.of(
                    ScorePlaybackDirection.AfterJumpRepeats.DEFAULT,
                            ScorePlaybackDirection.AfterJumpRepeats.SKIP,
                    ScorePlaybackDirection.AfterJumpRepeats.PLAY,
                            ScorePlaybackDirection.AfterJumpRepeats.UNKNOWN);

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static void frame(DataOutput out, Writer writer) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var record = new DataOutputStream(bytes);
        writer.write(record);
        record.flush();
        if (bytes.size() > MAX_RECORD_BYTES) throw new IOException("Semantic record too large");
        out.writeInt(bytes.size());
        out.write(bytes.toByteArray());
    }

    private static DataInputStream frame(DataInput input) throws IOException {
        int length = count(input, MAX_RECORD_BYTES);
        var bytes = new byte[length];
        input.readFully(bytes);
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }

    private static void finished(DataInputStream record) throws IOException {
        if (record.available() != 0) throw new IOException("Unexpected semantic record bytes");
    }

    private static int count(DataInput in, int maximum) throws IOException {
        int value = in.readInt();
        if (value < 0 || value > maximum) throw new IOException("Invalid semantic count");
        return value;
    }

    private static void text(DataOutput out, String value, int limit) throws IOException {
        if (value.length() > limit) throw new IOException("Semantic text too long");
        var encoded =
                StandardCharsets.UTF_8
                        .newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .encode(java.nio.CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String text(DataInput in, int limit) throws IOException {
        int length = count(in, limit * 4);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        String result =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
        if (result.length() > limit) throw new IOException("Semantic text too long");
        return result;
    }

    private static void anchor(DataOutput out, Optional<ScoreAnchor> value) throws IOException {
        out.writeBoolean(value.isPresent());
        if (value.isPresent()) {
            if (value.get().measureIndex() > 100000)
                throw new IOException("Semantic anchor exceeds source limit");
            out.writeInt(value.get().measureIndex());
            out.writeDouble(value.get().quarterBeatOffset());
        }
    }

    private static Optional<ScoreAnchor> anchor(DataInput in) throws IOException {
        return bool(in)
                ? Optional.of(new ScoreAnchor(count(in, 100000), in.readDouble()))
                : Optional.empty();
    }

    private static boolean bool(DataInput in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) throw new IOException("Invalid semantic boolean");
        return value == 1;
    }

    private static void evidence(DataOutput out, List<ScoreExpressiveEvent.Evidence> values)
            throws IOException {
        if (values.size() > 64) throw new IOException("Too much semantic evidence");
        out.writeInt(values.size());
        for (var value : values) {
            if (value.pageIndex() > 100000)
                throw new IOException("Evidence page exceeds source limit");
            text(out, value.sourceId(), 256);
            out.writeInt(value.pageIndex());
            out.writeFloat(value.visualX());
            out.writeInt(value.staffIndex());
            out.writeInt(value.staffCount());
            text(out, value.printedText(), 4096);
        }
    }

    private static List<ScoreExpressiveEvent.Evidence> evidence(DataInput in) throws IOException {
        int size = count(in, 64);
        var values = new ArrayList<ScoreExpressiveEvent.Evidence>();
        for (int i = 0; i < size; i++)
            values.add(
                    new ScoreExpressiveEvent.Evidence(
                            text(in, 256),
                            count(in, 100000),
                            in.readFloat(),
                            in.readInt(),
                            in.readInt(),
                            text(in, 4096)));
        return List.copyOf(values);
    }

    private static <T> T enumValue(List<T> values, DataInput in) throws IOException {
        int id = in.readInt();
        if (id < 0 || id >= values.size()) throw new IOException("Unknown semantic wire ID");
        return values.get(id);
    }

    public static void writeDirections(
            DataOutput out, List<ScorePlaybackDirection> directions, int measures)
            throws IOException {
        if (directions.size() > Math.min(100000, (measures + 1L) * 64))
            throw new IOException("Too many navigation records");
        out.writeInt(directions.size());
        for (var direction : directions)
            frame(
                    out,
                    record -> {
                        if (direction.measureBoundary() > measures
                                || direction.measureBoundary() == measures
                                        && direction.details().quarterBeatOffset() != 0)
                            throw new IOException("Navigation start outside page");
                        record.writeInt(direction.measureBoundary());
                        record.writeInt(direction.kind().wireId());
                        var d = direction.details();
                        record.writeDouble(d.quarterBeatOffset());
                        text(record, d.eventId(), 256);
                        text(record, d.targetId(), 256);
                        text(record, d.codaTargetId(), 256);
                        text(record, d.repeatGroupId(), 256);
                        record.writeInt(d.totalPlays());
                        record.writeInt(d.passes().size());
                        for (int pass : d.passes()) record.writeInt(pass);
                        anchor(record, d.end());
                        record.writeInt(POLICIES.indexOf(d.afterJumpRepeats()));
                        text(record, d.printedText(), 4096);
                        evidence(record, d.evidence());
                    });
    }

    public static List<ScorePlaybackDirection> readDirections(DataInput in, int measures)
            throws IOException {
        int size = count(in, (int) Math.min(100000, (measures + 1L) * 64));
        var values = new ArrayList<ScorePlaybackDirection>();
        try {
            for (int i = 0; i < size; i++) {
                var record = frame(in);
                int boundary = count(record, measures);
                var kind = ScorePlaybackDirection.Kind.fromWireId(record.readInt());
                double offset = record.readDouble();
                String id = text(record, 256),
                        target = text(record, 256),
                        coda = text(record, 256),
                        group = text(record, 256);
                int plays = record.readInt(), passCount = count(record, 128);
                var passes = new ArrayList<Integer>();
                for (int p = 0; p < passCount; p++) passes.add(record.readInt());
                var end = anchor(record);
                var policy = enumValue(POLICIES, record);
                String printed = text(record, 4096);
                var details =
                        new ScorePlaybackDirection.Details(
                                offset,
                                id,
                                target,
                                coda,
                                group,
                                plays,
                                passes,
                                end,
                                policy,
                                printed,
                                evidence(record));
                var direction = new ScorePlaybackDirection(boundary, kind, details);
                if (boundary == measures && offset != 0)
                    throw new IOException("Nonzero terminal navigation offset");
                finished(record);
                values.add(direction);
            }
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid navigation semantics", invalid);
        }
        return List.copyOf(values);
    }

    public static void writeExpressions(
            DataOutput out, List<ScoreExpressiveEvent> expressions, int measures)
            throws IOException {
        writeExpressions(out, expressions, measures, 277);
    }

    /** Guide 277 adds kind 19; older guide headers cannot contain a metric relation. */
    public static void writeExpressions(
            DataOutput out, List<ScoreExpressiveEvent> expressions, int measures, int guideVersion)
            throws IOException {
        if (guideVersion < 277
                && expressions.stream()
                        .anyMatch(e -> e.kind() == ScoreExpressiveEvent.Kind.METRIC_MODULATION))
            throw new IOException("Metric modulation requires guide 277");
        if (guideVersion < 294
                && expressions.stream()
                        .anyMatch(e -> e.kind() == ScoreExpressiveEvent.Kind.NATURAL_HARMONIC))
            throw new IOException("Natural harmonic requires guide 294");
        if (guideVersion < 295
                && expressions.stream()
                        .anyMatch(e -> e.kind() == ScoreExpressiveEvent.Kind.FALL_OFF))
            throw new IOException("Falloff requires guide 295");
        if (guideVersion < 296
                && expressions.stream()
                        .anyMatch(
                                e ->
                                        e.kind()
                                                == ScoreExpressiveEvent.Kind
                                                        .PARENTHESIZED_NOTE_GROUP))
            throw new IOException("Parenthesized note group requires guide 296");
        if (expressions.stream()
                .anyMatch(
                        e ->
                                e.kind() == ScoreExpressiveEvent.Kind.PARENTHESIZED_NOTE_GROUP
                                        && ScoreParenthesizedNoteGroup.targetMeasure(
                                                                e.targetEventId().orElse(null))
                                                        .orElse(-1)
                                                >= measures))
            throw new IOException("Group target outside page");
        if (expressions.size() > Math.min(250000, (measures + 1L) * 256))
            throw new IOException("Too many expressive records");
        out.writeInt(expressions.size());
        for (var event : expressions)
            frame(
                    out,
                    record -> {
                        if (event.start()
                                .filter(
                                        a ->
                                                a.measureIndex() > measures
                                                        || a.measureIndex() == measures
                                                                && a.quarterBeatOffset() != 0)
                                .isPresent())
                            throw new IOException("Expression start outside page");
                        text(record, event.eventId(), 256);
                        record.writeInt(EXPRESSION_KINDS.indexOf(event.kind()));
                        anchor(record, event.start());
                        anchor(record, event.end());
                        record.writeInt(SCOPES.indexOf(event.scope()));
                        record.writeInt(event.staffIndex());
                        record.writeInt(event.staffCount());
                        record.writeBoolean(event.targetEventId().isPresent());
                        if (event.targetEventId().isPresent())
                            text(record, event.targetEventId().get(), 256);
                        record.writeInt(STRENGTHS.indexOf(event.strength()));
                        text(record, event.qualifierText(), 4096);
                        evidence(record, event.evidence());
                    });
    }

    public static List<ScoreExpressiveEvent> readExpressions(DataInput in, int measures)
            throws IOException {
        return readExpressions(in, measures, 277);
    }

    public static List<ScoreExpressiveEvent> readExpressions(
            DataInput in, int measures, int guideVersion) throws IOException {
        int size = count(in, (int) Math.min(250000, (measures + 1L) * 256));
        var values = new ArrayList<ScoreExpressiveEvent>();
        var identities = new HashSet<String>();
        try {
            for (int i = 0; i < size; i++) {
                var record = frame(in);
                String id = text(record, 256);
                var kind = enumValue(EXPRESSION_KINDS, record);
                if (guideVersion < 277 && kind == ScoreExpressiveEvent.Kind.METRIC_MODULATION)
                    throw new IOException("Metric modulation is outside this guide format");
                if (guideVersion < 294 && kind == ScoreExpressiveEvent.Kind.NATURAL_HARMONIC)
                    throw new IOException("Natural harmonic is outside this guide format");
                if (guideVersion < 295 && kind == ScoreExpressiveEvent.Kind.FALL_OFF)
                    throw new IOException("Falloff is outside this guide format");
                if (guideVersion < 296
                        && kind == ScoreExpressiveEvent.Kind.PARENTHESIZED_NOTE_GROUP)
                    throw new IOException("Parenthesized note group is outside this guide format");
                var start = anchor(record);
                var end = anchor(record);
                var scope = enumValue(SCOPES, record);
                int staff = record.readInt(), staffCount = record.readInt();
                var target =
                        bool(record) ? Optional.of(text(record, 256)) : Optional.<String>empty();
                var strength = enumValue(STRENGTHS, record);
                String qualifier = text(record, 4096);
                var event =
                        new ScoreExpressiveEvent(
                                id,
                                kind,
                                start,
                                end,
                                scope,
                                staff,
                                staffCount,
                                target,
                                strength,
                                qualifier,
                                evidence(record));
                if (kind == ScoreExpressiveEvent.Kind.PARENTHESIZED_NOTE_GROUP
                        && ScoreParenthesizedNoteGroup.targetMeasure(target.orElse(null)).orElse(-1)
                                >= measures) throw new IOException("Group target outside page");
                if (!identities.add(id)
                        || start.filter(
                                        a ->
                                                a.measureIndex() > measures
                                                        || a.measureIndex() == measures
                                                                && a.quarterBeatOffset() != 0)
                                .isPresent())
                    throw new IOException("Invalid expressive identity or start");
                finished(record);
                values.add(event);
            }
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid expressive semantics", invalid);
        }
        return List.copyOf(values);
    }
}
