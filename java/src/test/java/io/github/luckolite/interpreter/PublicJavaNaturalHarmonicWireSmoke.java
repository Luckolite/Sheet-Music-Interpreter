// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class PublicJavaNaturalHarmonicWireSmoke {
    public static void main(String[] args) throws Exception {
        var event =
                new ScoreExpressiveEvent(
                        "natural-harmonic:exact-note",
                        ScoreExpressiveEvent.Kind.NATURAL_HARMONIC,
                        Optional.empty(),
                        Optional.empty(),
                        ScoreExpressiveEvent.Scope.UNRESOLVED,
                        0,
                        1,
                        Optional.of("natural-harmonic-note-v1:0:0:1:3f000000:3:3e800000"),
                        ScoreExpressiveEvent.Strength.UNSPECIFIED,
                        "",
                        List.of(
                                new ScoreExpressiveEvent.Evidence(
                                        "page", 2, .5f, 0, 1, "natural harmonic")));
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes);
        out.writeInt(0);
        ScoreSemanticWire.writeExpressions(out, List.of(event), 1, 294);
        out.flush();
        Path p = Path.of(args[0]);
        Files.write(p, bytes.toByteArray());
        var in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        if (in.readInt() != 0) throw new AssertionError("direction count");
        var decoded = ScoreSemanticWire.readExpressions(in, 1, 294);
        if (decoded.size() != 1
                || decoded.get(0).scope() != ScoreExpressiveEvent.Scope.UNRESOLVED
                || !decoded.get(0)
                        .targetEventId()
                        .orElseThrow()
                        .equals("natural-harmonic-note-v1:0:0:1:3f000000:3:3e800000"))
            throw new AssertionError("Java roundtrip mismatch");
        if (!event.offset(2)
                .targetEventId()
                .orElseThrow()
                .equals("natural-harmonic-note-v1:2:0:1:3f000000:3:3e800000"))
            throw new AssertionError("Natural-harmonic target did not follow measure offset");
        String malformed = "natural-harmonic-note-v1:1:bad";
        if (!ScoreNaturalHarmonicNotation.offsetTarget(malformed, 2).equals(malformed))
            throw new AssertionError("Malformed target was rewritten");
        try {
            ScoreNaturalHarmonicNotation.offsetTarget(
                    "natural-harmonic-note-v1:0:0:1:3f000000:3:3e800000", -1);
            throw new AssertionError("Negative target measure was accepted");
        } catch (IllegalArgumentException expected) {
        }
        try {
            ScoreSemanticWire.writeExpressions(
                    new DataOutputStream(new ByteArrayOutputStream()), List.of(event), 1, 293);
            throw new AssertionError("guide 293 unexpectedly accepted natural harmonic");
        } catch (IOException expected) {
        }
        System.out.println(
                "PASS public Java guide294 exact-owner wire roundtrip and guide293 refusal");
    }
}
