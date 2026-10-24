// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Shared conservative budget. Resource failure conveys UNKNOWN, never absence. */
final class SlurEvidenceBudget {
    static final class Unknown extends RuntimeException {
        Unknown(String reason) {
            super(reason);
        }
    }

    long work, bytes;

    SlurEvidenceBudget(long work, long bytes) {
        this.work = work;
        this.bytes = bytes;
    }

    void use(long w, long b) {
        if (w < 0 || b < 0 || w > work || b > bytes) throw new Unknown("RESOURCE_UNKNOWN");
        work -= w;
        bytes -= b;
    }
}
