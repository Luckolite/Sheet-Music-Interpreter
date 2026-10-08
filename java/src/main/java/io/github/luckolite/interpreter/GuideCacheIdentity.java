// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

/** Recognition revisions invalidate derived results without changing their binary record layout. */
public final class GuideCacheIdentity {
    public static final int RECOGNITION_REVISION = 19;

    private GuideCacheIdentity() {}

    public static String portable(int format, String source, String variant, int revision) {
        return "guide|" + format + "|0|" + source + "|" + variant + suffix(revision);
    }

    public static String local(String source, int page, String cleanup, int format, int revision) {
        return source
                + "|page="
                + page
                + "|cleanup="
                + cleanup
                + "|engine="
                + format
                + suffix(revision);
    }

    private static String suffix(int revision) {
        if (revision < 0) throw new IllegalArgumentException("Negative recognition revision");
        return revision == 0 ? "" : "|recognition=" + revision;
    }
}
