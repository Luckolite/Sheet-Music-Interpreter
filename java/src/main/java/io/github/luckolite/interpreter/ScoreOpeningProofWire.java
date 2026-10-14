// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;

/** Guide286 retains independent opening-number proof as a strict zero/one byte. */
public final class ScoreOpeningProofWire {
    private ScoreOpeningProofWire() {}

    public static void write(DataOutput out, boolean proof, int measures, int first)
            throws IOException {
        validate(proof, measures, first);
        out.writeByte(proof ? 1 : 0);
    }

    public static boolean read(DataInput in, int measures, int first) throws IOException {
        int flag = in.readUnsignedByte();
        if (flag > 1) throw new IOException("Invalid opening pickup flag");
        boolean proof = flag == 1;
        validate(proof, measures, first);
        return proof;
    }

    private static void validate(boolean proof, int measures, int first) throws IOException {
        if (measures < 0 || first < 0 || proof && (measures == 0 || first > 1))
            throw new IOException("Opening pickup conflicts with source geometry or numbering");
    }
}
