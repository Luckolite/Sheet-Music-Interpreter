// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.util.*;

/** MRG1 framed printed multi-rest identity; old guides have no groups. */
public final class ScoreMultiRestWire {
    public static final int FIRST_VERSION = 292;
    private static final int MAGIC = 0x4d524731, MAX_GROUPS = 2048;

    private ScoreMultiRestWire() {}

    public static void write(DataOutput out, List<ScoreMultiRestGroup> groups, int measures)
            throws IOException {
        check(groups, measures);
        out.writeInt(MAGIC);
        out.writeInt(4 + groups.size() * 16);
        out.writeInt(groups.size());
        for (var g : groups) {
            out.writeInt(g.firstMeasureIndex());
            out.writeInt(g.measureCount());
            out.writeInt(g.staffIndex());
            out.writeInt(g.staffCount());
        }
    }

    public static List<ScoreMultiRestGroup> read(DataInput in, int measures) throws IOException {
        int magic = in.readInt(), length = in.readInt(), count = in.readInt();
        if (magic != MAGIC || count < 0 || count > MAX_GROUPS || length != 4 + count * 16)
            throw new IOException("Invalid multi-rest frame");
        var groups = new ArrayList<ScoreMultiRestGroup>(count);
        try {
            for (int i = 0; i < count; i++)
                groups.add(
                        new ScoreMultiRestGroup(
                                in.readInt(), in.readInt(), in.readInt(), in.readInt()));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid multi-rest identity", e);
        }
        check(groups, measures);
        return List.copyOf(groups);
    }

    private static void check(List<ScoreMultiRestGroup> groups, int measures) throws IOException {
        if (groups == null || groups.size() > MAX_GROUPS || measures < 0 || measures > 100000)
            throw new IOException("Invalid multi-rest dimensions");
        var owned = new HashSet<Long>();
        for (var g : groups) {
            if (g == null || g.firstMeasureIndex() > measures - g.measureCount())
                throw new IOException("Multi-rest outside score");
            for (int d = 0; d < g.measureCount(); d++) {
                long key =
                        ((long) (g.firstMeasureIndex() + d) << 10)
                                | ((long) (g.staffCount() - 1) << 5)
                                | g.staffIndex();
                if (!owned.add(key)) throw new IOException("Overlapping multi-rest identity");
            }
        }
    }
}
