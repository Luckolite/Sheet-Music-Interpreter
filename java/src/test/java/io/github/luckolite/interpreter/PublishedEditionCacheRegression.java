// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.*;
import java.security.*;
import java.util.*;

/** Fictional cache layout/integrity/expiry and terminal-repair boundaries. */
public final class PublishedEditionCacheRegression {
    static int passed;

    static void check(boolean b, String label) {
        if (!b) throw new AssertionError(label);
        passed++;
    }

    interface Test {
        void run() throws Exception;
    }

    static void invalid(Test t) throws Exception {
        try {
            t.run();
            throw new AssertionError("invalid cache accepted");
        } catch (IOException | IllegalArgumentException expected) {
            passed++;
        }
    }

    static String resign(byte[] b) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(b, b.length - 32));
        System.arraycopy(hash, 0, b, b.length - 32, 32);
        return Base64.getEncoder().encodeToString(b);
    }

    public static void main(String[] args) throws Exception {
        var facts =
                PublishedEditionCreditRegression.edition(
                        "The Copper Harbor", List.of("Mira Valén"), List.of("Adrian Solberg"));
        long fetched = 1000000L;
        var entry = new PublishedEditionCacheRecord.Entry(facts, fetched);
        String encoded = PublishedEditionCacheRecord.encode(entry);
        check(
                PublishedEditionCacheRecord.decode(encoded, fetched + 1000).equals(entry),
                "UTF-8 metadata/cache round trip");
        check(
                PublishedEditionCacheRecord.decode(
                                encoded, fetched + PublishedEditionCacheRecord.MAX_AGE_MILLIS)
                        .equals(entry),
                "expiry boundary inclusive");
        invalid(
                () ->
                        PublishedEditionCacheRecord.decode(
                                encoded, fetched + PublishedEditionCacheRecord.MAX_AGE_MILLIS + 1));
        invalid(() -> PublishedEditionCacheRecord.decode(encoded, fetched - 300001));
        invalid(() -> PublishedEditionCacheRecord.decode("not base64", fetched));
        byte[] changed = Base64.getDecoder().decode(encoded);
        changed[20] ^= 1;
        invalid(
                () ->
                        PublishedEditionCacheRecord.decode(
                                Base64.getEncoder().encodeToString(changed), fetched));
        byte[] truncated =
                Arrays.copyOf(
                        Base64.getDecoder().decode(encoded),
                        Base64.getDecoder().decode(encoded).length - 1);
        invalid(
                () ->
                        PublishedEditionCacheRecord.decode(
                                Base64.getEncoder().encodeToString(truncated), fetched));
        byte[] unknown = Base64.getDecoder().decode(encoded);
        unknown[3] = 2;
        String unknownEncoded = resign(unknown);
        invalid(() -> PublishedEditionCacheRecord.decode(unknownEncoded, fetched));
        byte[] wrongId = Base64.getDecoder().decode(encoded);
        var cursor = ByteBuffer.wrap(wrongId);
        cursor.getInt();
        cursor.getLong();
        int size = cursor.getInt();
        wrongId[cursor.position() + size - 1] = '8';
        String wrongIdEncoded = resign(wrongId);
        invalid(() -> PublishedEditionCacheRecord.decode(wrongIdEncoded, fetched));
        byte[] original = Base64.getDecoder().decode(encoded);
        byte[] trailing = new byte[original.length + 1];
        System.arraycopy(original, 0, trailing, 0, original.length - 32);
        trailing[original.length - 32] = 1;
        String trailingEncoded = resign(trailing);
        invalid(() -> PublishedEditionCacheRecord.decode(trailingEncoded, fetched));
        invalid(() -> PublishedEditionCacheRecord.decode("a".repeat(50000), fetched));
        invalid(() -> new PublishedEditionCacheRecord.Entry(facts, 0));
        var mutable = new ArrayList<>(List.of("Mira Valen"));
        var immutable =
                PublishedEditionCreditRegression.edition("The Copper Harbor", mutable, List.of());
        mutable.set(0, "Other Person");
        check(
                immutable.composers().equals(List.of("Mira Valen")),
                "caller cannot mutate cached provenance");
        var shortFamily =
                PublishedEditionCreditRegression.edition(
                        "The Copper Harbor", List.of("Mira Earl"), List.of("Adrian Solberg"));
        check(
                PublishedEditionCreditRegression.select(
                                PublishedEditionCreditRegression.pages(
                                        "Music by Mira Pearl", "Arranged by Adrian Solberg"),
                                shortFamily,
                                Set.of())
                        == null,
                "do not delete a surname prefix to match a catalogue person");
        var internal =
                PublishedEditionCreditRegression.edition(
                        "The Copper Harbor", List.of("Mira Robson"), List.of("Adrian Solberg"));
        check(
                PublishedEditionCreditRegression.select(
                                PublishedEditionCreditRegression.pages(
                                        "Music by Mira Ronson", "Arranged by Adrian Solberg"),
                                internal,
                                Set.of())
                        == null,
                "do not exchange internal surname letters");
        check(
                PublishedEditionCreditRegression.select(
                                PublishedEditionCreditRegression.pages(
                                        "By Mira Valem", "Arranged by Adrian Solber"),
                                PublishedEditionCreditRegression.edition(
                                        "The Copper Harbor",
                                        List.of("Mira Valen"),
                                        List.of("Adrian Solberg")),
                                Set.of())
                        != null,
                "score and explicit arrangement contextualize a bare by credit");
        check(
                PublishedEditionCreditRecovery.select(
                                "import",
                                Collections.singletonList(null),
                                Set.of(),
                                Map.of(),
                                PublishedEditionCreditRegression.catalogue(Set.of()))
                        == null,
                "null source page rejected");
        System.out.println("passed=" + passed);
    }
}
