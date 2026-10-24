// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.security.*;
import java.util.*;

/** Private exact traversal side data. No recognition, musical head truth or recovery from boxes. */
final class ExactComponentMembers {
    static final class Budget {
        long work, bytes;

        Budget(long w, long b) {
            work = w;
            bytes = b;
        }

        boolean take(long w, long b) {
            if (w < 0 || b < 0 || w > work || b > bytes) return false;
            work -= w;
            bytes -= b;
            return true;
        }
    }

    record Stats(int area, int minX, int maxX, int minY, int maxY, float centerX, float centerY) {}

    private static String digest(byte[] b) {
        try {
            return RasterEvidenceIdentity.hex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    static final class Collector {
        final int width, height;
        final String rawSha, labelSha;
        private final byte[] raw, labels;
        private final Budget sparse;
        private boolean valid = true, sealed = false, bound = false;
        private final IdentityHashMap<Object, Original> records = new IdentityHashMap<>();
        private final IdentityHashMap<Object, AcceptedProbeHead> accepted = new IdentityHashMap<>();

        Collector(byte[] gray, byte[] mask, int w, int h, Budget authentication, Budget sparse) {
            this.width = w;
            this.height = h;
            this.raw = gray;
            this.labels = mask;
            this.sparse = sparse;
            if (gray == null
                    || mask == null
                    || w <= 0
                    || h <= 0
                    || (long) w * h != gray.length
                    || mask.length != gray.length
                    || !authentication.take(2L * gray.length, 4096)) {
                valid = false;
                rawSha = "";
                labelSha = "";
                return;
            }
            rawSha = digest(gray);
            labelSha = digest(mask);
        }

        boolean sameInputFrame(byte[] mask, byte[] gray, int w, int h) {
            return valid
                    && bound
                    && sealed
                    && labels == mask
                    && raw == gray
                    && width == w
                    && height == h;
        }

        boolean bindInput(byte[] mask, byte[] gray, int w, int h) {
            if (!valid || bound || mask != labels || gray != raw || w != width || h != height) {
                invalidate();
                return false;
            }
            bound = true;
            return true;
        }

        Builder begin() {
            if (!valid || sealed || !sparse.take(1, 256)) {
                invalidate();
                return null;
            }
            return new Builder(this);
        }

        private final IdentityHashMap<Object, Boolean> splitParents = new IdentityHashMap<>();

        Split beginSplit(Object parent, byte[] mask, int w) {
            if (!valid || sealed || mask != labels || w != width || !sparse.take(1, 128))
                return null;
            if (splitParents.containsKey(parent)) {
                invalidate();
                return null;
            }
            Original original = records.get(parent);
            if (original == null) return null;
            return new Split(this, original);
        }

        private void invalidate() {
            valid = false;
            records.clear();
            accepted.clear();
        }

        /** Capture ONLY from the actual producer traversal. Never reconstruct members from Stats. */
        void record(Object identity, Stats stats, byte target, Builder builder) {
            recordInto(identity, stats, target, builder, records);
        }

        private void recordInto(
                Object identity,
                Stats stats,
                byte target,
                Builder builder,
                IdentityHashMap<Object, Original> output) {
            if (identity == null
                    || !valid
                    || sealed
                    || builder == null
                    || builder.collector != this
                    || builder.used
                    || records.containsKey(identity)) {
                invalidate();
                return;
            }
            builder.used = true;
            long count = 0, sx = 0, sy = 0;
            int l = width, r = -1, t = height, b = -1;
            for (int[] run : builder.runs) {
                int y = run[0], first = run[1], last = run[2];
                if (y < 0 || y >= height || first < 0 || last >= width || first > last) {
                    invalidate();
                    return;
                }
                int n = last - first + 1;
                if (!sparse.take(2L * n + builder.runs.size(), 0)) {
                    invalidate();
                    return;
                }
                // Distinct same-row traversal runs must not overlap. Original IDs are never
                // renumbered.
                for (int[] prior : builder.runs) {
                    if (prior == run) break;
                    if (prior[0] == y && prior[1] <= last && prior[2] >= first) {
                        invalidate();
                        return;
                    }
                }
                for (int x = first; x <= last; x++)
                    if (labels[y * width + x] != target) {
                        invalidate();
                        return;
                    }
                count += n;
                sx += ((long) first + last) * n / 2;
                sy += (long) y * n;
                l = Math.min(l, first);
                r = Math.max(r, last);
                t = Math.min(t, y);
                b = Math.max(b, y);
            }
            if (count < 3
                    || count != stats.area
                    || l != stats.minX
                    || r != stats.maxX
                    || t != stats.minY
                    || b != stats.maxY
                    || Float.floatToRawIntBits(sx / (float) count)
                            != Float.floatToRawIntBits(stats.centerX)
                    || Float.floatToRawIntBits(sy / (float) count)
                            != Float.floatToRawIntBits(stats.centerY)) {
                invalidate();
                return;
            }
            if (!sparse.take(1, 192 + 16L * builder.runs.size())) {
                invalidate();
                return;
            }
            output.put(
                    identity,
                    new Original(this, identity, stats, List.copyOf(builder.runs), target));
        }

        /** One final full-plane rehash, separately prepaid; all outputs remain UNKNOWN until this passes.
         * No plane clone/page-sized membership bitmap. Arrays must remain caller-owned read-only.
         */
        boolean seal(Budget authentication) {
            if (sealed) return valid;
            sealed = true;
            if (!valid
                    || !authentication.take(2L * raw.length, 4096)
                    || !rawSha.equals(digest(raw))
                    || !labelSha.equals(digest(labels))) {
                invalidate();
                return false;
            }
            return true;
        }

        boolean revalidateForExport(Budget budget) {
            if (!valid || !sealed || !budget.take(2L * raw.length, 4096)) return false;
            if (!rawSha.equals(digest(raw)) || !labelSha.equals(digest(labels))) {
                invalidate();
                return false;
            }
            return true;
        }

        long targetPopulation(byte target, Budget budget) {
            if (!valid || !sealed || !budget.take(labels.length, 0)) return -1;
            long count = 0;
            for (byte value : labels) if (value == target) count++;
            return count;
        }

        AcceptedProbeHead registeredProbeToken(Object identity) {
            return valid && sealed ? accepted.get(identity) : null;
        }

        /** Exact traversal facts without inventing a measure/staff or an accepted-head role. */
        ProducerFootprint producerFootprint(Object identity, Budget budget) {
            if (!valid || !sealed) return null;
            Original original = records.get(identity);
            if (original == null
                    || !budget.take(original.stats.area, 4L * original.stats.area + 64))
                return null;
            int[] pixels = new int[original.stats.area];
            int at = 0;
            for (int[] run : original.runs)
                for (int x = run[1]; x <= run[2]; x++) pixels[at++] = run[0] * width + x;
            Original root = original;
            while (root.parent != null) root = root.parent;
            return new ProducerFootprint(
                    rawSha,
                    labelSha,
                    pixels,
                    original.parent == null ? null : original.parent.identity,
                    root.identity);
        }

        /** Source facts only: real current descendant leaves plus every unassigned parent member.
         * The original parent remains available through producer lineage even after its record was
         * replaced by genuine split children. No component is reconstructed from a bounding box.
         */
        ProducerPartition producerPartition(Object selectedIdentity, Budget budget) {
            if (!valid || !sealed || budget == null || !revalidateForExport(budget)) return null;
            return partitionAfterAuthentication(selectedIdentity, budget);
        }

        /** One invocation-scoped snapshot; no digest or partition cache survives this call. */
        ProducerPartitionBatch producerPartitions(List<?> identities, Budget budget) {
            if (!valid || !sealed || identities == null || budget == null) return null;
            long authenticationWork = 2L * raw.length;
            // Reserve exit authentication before any partial partition export can spend the budget.
            if (!budget.take(authenticationWork, 4096)
                    || !revalidateForExport(budget)
                    || !budget.take(identities.size(), 64L + 64L * identities.size())) return null;
            var exit = new Budget(authenticationWork, 4096);
            var requested = new ArrayList<Object>(identities);
            var counts = new IdentityHashMap<Object, Integer>();
            for (Object identity : requested) counts.merge(identity, 1, Integer::sum);
            var rows = new ArrayList<PartitionRow>();
            for (int i = 0; i < requested.size(); i++) {
                Object identity = requested.get(i);
                ProducerPartition partition = null;
                String status = "UNKNOWN_NULL_IDENTITY";
                if (identity != null) {
                    if (counts.get(identity) != 1) status = "UNKNOWN_DUPLICATE_IDENTITY";
                    else {
                        partition = partitionAfterAuthentication(identity, budget);
                        status =
                                partition == null
                                        ? "UNKNOWN_UNBOUND_OR_BUDGET"
                                        : "BOUND_PRODUCER_PARTITION";
                    }
                }
                rows.add(new PartitionRow(i, identity, partition, status));
            }
            if (!revalidateForExport(exit)) return null;
            return new ProducerPartitionBatch(this, rows);
        }

        private ProducerPartition partitionAfterAuthentication(
                Object selectedIdentity, Budget budget) {
            if (!valid || !sealed || budget == null) return null;
            Original selected = records.get(selectedIdentity);
            if (selected == null) return null;
            Original parent = selected.parent == null ? selected : selected.parent;
            int n = parent.stats.area;
            if (n > 1_000_000
                    || !budget.take(65L * records.size() + n, 16L * records.size() + 4L * n + 64))
                return null;
            var leaves = new ArrayList<Original>();
            long count = 0;
            for (Original candidate : records.values()) {
                Original cursor = candidate;
                int depth = 0;
                while (cursor != null && cursor != parent && depth++ < 64) cursor = cursor.parent;
                if (cursor == parent) {
                    leaves.add(candidate);
                    count += candidate.stats.area;
                } else if (cursor != null) return null;
            }
            if (count > n || leaves.isEmpty()) return null;
            int log = 64 - Long.numberOfLeadingZeros(Math.max(1, n));
            if (!budget.take(4L * n + 2L * n * log, 40L * n + 256L * leaves.size())) return null;
            int[] expected = partitionPixels(parent), actual = new int[(int) count];
            int at = 0;
            var rows = new ArrayList<PartitionLeaf>();
            for (Original leaf : leaves) {
                int[] pixels = partitionPixels(leaf);
                for (int pixel : pixels) actual[at++] = pixel;
                var vector = new ArrayList<Integer>(pixels.length);
                for (int pixel : pixels) vector.add(pixel);
                rows.add(new PartitionLeaf(leaf.identity, leaf.stats, List.copyOf(vector)));
            }
            Arrays.sort(expected);
            Arrays.sort(actual);
            var residual = new ArrayList<Integer>();
            int next = 0;
            for (int pixel : expected) {
                if (next < actual.length && actual[next] < pixel) return null;
                if (next < actual.length && actual[next] == pixel) {
                    next++;
                    if (next < actual.length && actual[next] == pixel) return null;
                } else residual.add(pixel);
            }
            if (next != actual.length) return null;
            rows.sort(Comparator.comparingInt(row -> row.pixels().get(0)));
            var parentVector = new ArrayList<Integer>(expected.length);
            for (int pixel : expected) parentVector.add(pixel);
            return new ProducerPartition(
                    this,
                    selectedIdentity,
                    parent.identity,
                    parent.stats,
                    List.copyOf(parentVector),
                    List.copyOf(rows),
                    List.copyOf(residual));
        }

        private int[] partitionPixels(Original original) {
            int[] out = new int[original.stats.area];
            int at = 0;
            for (int[] run : original.runs)
                for (int x = run[1]; x <= run[2]; x++) out[at++] = run[0] * width + x;
            Arrays.sort(out);
            return out;
        }

        /** Every target member omitted by the ordinary minimum-area traversal remains explicit. */
        int[] unrepresentedGlyphMembers(byte target, Budget budget) {
            if (!valid
                    || !sealed
                    || (target != 3 && target != 5)
                    || !budget.take(labels.length, (labels.length + 7L) / 8 + 64)) return null;
            var covered = new BitSet(labels.length);
            for (var original : records.values()) {
                if (original.target != target) continue;
                if (!budget.take(original.stats.area, 0)) return null;
                for (int[] run : original.runs)
                    covered.set(run[0] * width + run[1], run[0] * width + run[2] + 1);
            }
            var unknown = new ArrayList<Integer>();
            for (int at = 0; at < labels.length; at++) {
                if (labels[at] != target || covered.get(at)) continue;
                if (!budget.take(1, 24)) return null;
                unknown.add(at);
            }
            int[] answer = new int[unknown.size()];
            for (int i = 0; i < answer.length; i++) answer[i] = unknown.get(i);
            return answer;
        }

        /** Non-head traversal capability: it cannot be promoted through acceptedProbeHead. */
        AcceptedGlyph acceptedGlyph(Object exactOriginal) {
            if (!valid || !sealed || !sparse.take(1, 128)) return null;
            Original original = records.get(exactOriginal);
            if (original == null || (original.target != 3 && original.target != 5)) return null;
            return new AcceptedGlyph(original);
        }

        /** Existing paired-probe role only. This does NOT assert final emitted/musically complete head. */
        AcceptedProbeHead acceptedProbeHead(Object exactOriginal, int measure, int staff) {
            if (!valid || !sealed || measure < 0 || staff < 0 || !sparse.take(1, 0)) return null;
            Original original = records.get(exactOriginal);
            if (original == null || original.target != OmrMeasurePostProcessor.NOTEHEAD)
                return null;
            AcceptedProbeHead old = accepted.get(exactOriginal);
            if (old != null) return old.measure == measure && old.staff == staff ? old : null;
            if (!sparse.take(1, 128)) return null;
            AcceptedProbeHead answer = new AcceptedProbeHead(original, measure, staff);
            accepted.put(exactOriginal, answer);
            return answer;
        }
    }

    static final class Builder {
        private final Collector collector;
        private final List<int[]> runs = new ArrayList<>();
        private boolean used;

        private Builder(Collector c) {
            collector = c;
        }

        void add(int y, int left, int right) {
            if (used || !collector.valid) return;
            if (!collector.sparse.take(1, 96)) {
                collector.invalidate();
                return;
            }
            runs.add(new int[] {y, left, right});
        }
    }

    /** One genuine producer partition; uncommitted/failed candidates grant no child tokens. */
    static final class Split {
        final Collector c;
        final Original parent;
        private final IdentityHashMap<Object, Original> pending = new IdentityHashMap<>();
        private boolean done;

        private Split(Collector c, Original p) {
            this.c = c;
            parent = p;
        }

        Builder beginChild() {
            return done ? null : c.begin();
        }

        void capture(Object child, Stats stats, Builder members) {
            if (done || pending.containsKey(child) || c.records.containsKey(child)) {
                c.invalidate();
                return;
            }
            c.recordInto(child, stats, parent.target, members, pending);
        }

        /** Observer capture only: synthetic bounds/centroids refuse locally, never repair recognition. */
        boolean captureIfExact(Object child, Stats stats, Builder members) {
            if (done || members == null || members.collector != c || members.used) return false;
            long n = 0, sx = 0, sy = 0;
            int l = c.width, r = -1, t = c.height, b = -1;
            for (int[] q : members.runs) {
                if (!c.sparse.take(1, 0)) return false;
                int count = q[2] - q[1] + 1;
                if (count <= 0) return false;
                n += count;
                sx += ((long) q[1] + q[2]) * count / 2;
                sy += (long) q[0] * count;
                l = Math.min(l, q[1]);
                r = Math.max(r, q[2]);
                t = Math.min(t, q[0]);
                b = Math.max(b, q[0]);
            }
            if (n < 3
                    || n != stats.area
                    || l != stats.minX
                    || r != stats.maxX
                    || t != stats.minY
                    || b != stats.maxY
                    || Float.floatToRawIntBits(sx / (float) n)
                            != Float.floatToRawIntBits(stats.centerX)
                    || Float.floatToRawIntBits(sy / (float) n)
                            != Float.floatToRawIntBits(stats.centerY)) return false;
            capture(child, stats, members);
            return pending.containsKey(child);
        }

        /** Complete producer partition, including every omitted parent member as unresolved residual.
         * A child token grants exact provenance only; residuals never acquire a head/staff/slur role.
         */
        boolean commitRetainingResidual(List<?> children) {
            if (done || c.splitParents.containsKey(parent.identity)) {
                c.invalidate();
                return false;
            }
            if (!c.valid || c.sealed || children == null || children.size() < 2) return false;
            done = true;
            int n = parent.stats.area;
            int log = 32 - Integer.numberOfLeadingZeros(Math.max(1, n));
            if (!c.sparse.take(
                    8L * n + 4L * n * log + children.size(),
                    16L * n + 128L * children.size() + 128)) return false;
            int[] expected = new int[n], actual = new int[n];
            int at = 0;
            for (int[] r : parent.runs)
                for (int x = r[1]; x <= r[2]; x++) expected[at++] = r[0] * c.width + x;
            Arrays.sort(expected);
            at = 0;
            IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
            for (Object child : children) {
                Original o = pending.get(child);
                if (o == null
                        || seen.put(child, true) != null
                        || c.records.containsKey(child)
                        || o.stats.area > n - at) return false;
                for (int[] r : o.runs)
                    for (int x = r[1]; x <= r[2]; x++) actual[at++] = r[0] * c.width + x;
            }
            Arrays.sort(actual, 0, at);
            for (int i = 0; i < at; i++)
                if ((i > 0 && actual[i] == actual[i - 1])
                        || Arrays.binarySearch(expected, actual[i]) < 0) return false;
            int cursor = 0, remaining = 0;
            int[] residual = new int[n - at];
            for (int i = 0; i < n; i++) {
                if (cursor < at && actual[cursor] == expected[i]) {
                    cursor++;
                } else residual[remaining++] = expected[i];
            }
            // Duplicates, foreign members, and overlapping bounding boxes never silently disappear.
            if (cursor != at || remaining != residual.length) return false;
            c.records.remove(parent.identity);
            c.accepted.remove(parent.identity);
            c.splitParents.put(parent.identity, true);
            for (int i = 0; i < children.size(); i++) {
                Object child = children.get(i);
                Original o = pending.get(child);
                o.parent = parent;
                o.residual = residual;
                o.emissionIndex = i;
                o.emissionCount = children.size();
                c.records.put(child, o);
            }
            return true;
        }

        boolean commit(List<?> children) {
            if (done || c.splitParents.containsKey(parent.identity)) {
                c.invalidate();
                return false;
            }
            if (!c.valid || c.sealed) return false;
            done = true;
            int n = parent.stats.area;
            long count = 0;
            if (children == null
                    || children.size() < 2
                    || !c.sparse.take(children.size(), 128L * children.size())) return false;
            IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
            for (Object child : children) {
                Original o = pending.get(child);
                if (o == null || seen.put(child, true) != null || c.records.containsKey(child))
                    return false;
                count += o.stats.area;
            }
            if (count != n) return false;
            // Allocate only actual parent-sized sparse vectors, never page-sized state.
            int log = 32 - Integer.numberOfLeadingZeros(Math.max(1, n));
            if (!c.sparse.take(4L * n + 2L * n * log + children.size(), 8L * n + 64)) return false;
            int[] expected = new int[n], actual = new int[n];
            int at = 0;
            for (int[] r : parent.runs)
                for (int x = r[1]; x <= r[2]; x++) expected[at++] = r[0] * c.width + x;
            at = 0;
            for (Object child : children)
                for (int[] r : pending.get(child).runs)
                    for (int x = r[1]; x <= r[2]; x++) actual[at++] = r[0] * c.width + x;
            Arrays.sort(expected);
            Arrays.sort(actual);
            for (int i = 0; i < n; i++)
                if (expected[i] != actual[i] || (i > 0 && actual[i] == actual[i - 1])) return false;
            if (!c.sparse.take(children.size(), 64L * children.size() + 64)) return false;
            c.records.remove(parent.identity);
            c.accepted.remove(parent.identity);
            c.splitParents.put(parent.identity, true);
            for (Object child : children) {
                Original o = pending.get(child);
                o.parent = parent;
                c.records.put(child, o);
            }
            return true;
        }
    }

    private static final class Original {
        final Collector collector;
        final Object identity;
        final Stats stats;
        final List<int[]> runs;
        final byte target;
        Original parent;
        int[] residual;
        int emissionIndex = -1, emissionCount;

        Original(Collector c, Object i, Stats s, List<int[]> r, byte t) {
            collector = c;
            identity = i;
            stats = s;
            runs = r;
            target = t;
        }
    }

    static final class AcceptedGlyph {
        private final Original original;

        private AcceptedGlyph(Original original) {
            this.original = original;
        }

        Object identity() {
            return original.identity;
        }

        byte label() {
            return original.target;
        }

        Stats stats() {
            return original.stats;
        }

        String rawSha() {
            return original.collector.rawSha;
        }

        String labelSha() {
            return original.collector.labelSha;
        }

        boolean authenticates(byte[] labels, byte[] gray, int width, int height) {
            return original.collector.sameInputFrame(labels, gray, width, height);
        }

        int[] pixels(Budget budget) {
            var c = original.collector;
            if (!c.valid
                    || !c.sealed
                    || !budget.take(original.stats.area, 4L * original.stats.area + 32))
                return null;
            return c.partitionPixels(original);
        }
    }

    static final class AcceptedProbeHead {
        private final Original original;
        final int measure, staff;

        private AcceptedProbeHead(Original o, int m, int s) {
            original = o;
            measure = m;
            staff = s;
        }

        String rawSha() {
            return original.collector.rawSha;
        }

        String labelSha() {
            return original.collector.labelSha;
        }

        boolean authenticates(byte[] gray, int w, int h) {
            Collector c = original.collector;
            return c.valid && c.sealed && c.raw == gray && c.width == w && c.height == h;
        }

        Stats stats() {
            return original.stats;
        }

        int emittedChildIndex() {
            return original.emissionIndex;
        }

        int emittedChildCount() {
            return original.emissionCount;
        }

        int[] unresolvedResidualPixels(Budget b) {
            Collector c = original.collector;
            if (!c.valid || !c.sealed) return null;
            int[] r = original.residual;
            if (r == null) return new int[0];
            if (!b.take(r.length, 4L * r.length + 32)) return null;
            return r.clone();
        }

        Object parentIdentity() {
            return original.parent == null ? null : original.parent.identity;
        }

        Object originalIdentity() {
            Original root = original;
            while (root.parent != null) root = root.parent;
            return root.identity;
        }

        int[] pixels(Budget b) {
            Collector c = original.collector;
            if (!c.valid
                    || !c.sealed
                    || !b.take(original.stats.area, 4L * original.stats.area + 32)) return null;
            int[] out = new int[original.stats.area];
            int n = 0;
            for (int[] r : original.runs)
                for (int x = r[1]; x <= r[2]; x++) out[n++] = r[0] * c.width + x;
            return out;
        }
    }

    record PartitionRow(int ordinal, Object identity, ProducerPartition partition, String status) {}

    /** Immutable membership snapshot, not an accepted-head, accidental, or clock capability. */
    static final class ProducerPartitionBatch {
        private final Collector source;
        private final List<PartitionRow> rows;
        private final IdentityHashMap<Object, ProducerPartition> unique = new IdentityHashMap<>();

        private ProducerPartitionBatch(Collector source, List<PartitionRow> rows) {
            this.source = source;
            this.rows = List.copyOf(rows);
            for (var row : rows)
                if (row.partition() != null) unique.put(row.identity(), row.partition());
        }

        List<PartitionRow> rows() {
            return rows;
        }

        ProducerPartition get(Object exactIdentity) {
            return unique.get(exactIdentity);
        }

        boolean sharesFrame(Collector other) {
            return other != null
                    && source.valid
                    && source.sealed
                    && source.bound
                    && other.valid
                    && other.sealed
                    && other.bound
                    && source.raw == other.raw
                    && source.labels == other.labels
                    && source.width == other.width
                    && source.height == other.height
                    && source.rawSha.equals(other.rawSha)
                    && source.labelSha.equals(other.labelSha);
        }

        boolean authenticates(byte[] labels, byte[] gray, int width, int height, Budget budget) {
            return source.sameInputFrame(labels, gray, width, height)
                    && source.revalidateForExport(budget);
        }
    }

    record PartitionLeaf(Object identity, Stats stats, List<Integer> pixels) {
        PartitionLeaf {
            pixels = List.copyOf(pixels);
        }
    }

    /** An authentic producer partition is not an oval, stem, chord, or written-value proof. */
    static final class ProducerPartition {
        private final Collector source;
        private final Object selected, parent;
        private final Stats parentStats;
        private final List<Integer> parentPixels, residual;
        private final List<PartitionLeaf> leaves;

        private ProducerPartition(
                Collector source,
                Object selected,
                Object parent,
                Stats stats,
                List<Integer> pixels,
                List<PartitionLeaf> leaves,
                List<Integer> residual) {
            this.source = source;
            this.selected = selected;
            this.parent = parent;
            parentStats = stats;
            parentPixels = pixels;
            this.leaves = leaves;
            this.residual = residual;
        }

        String rawSha() {
            return source.rawSha;
        }

        String labelSha() {
            return source.labelSha;
        }

        boolean sameProducer(ProducerPartition other) {
            return other != null && source == other.source;
        }

        Object selectedIdentity() {
            return selected;
        }

        Object parentIdentity() {
            return parent;
        }

        Stats parentStats() {
            return parentStats;
        }

        List<Integer> parentPixels() {
            return parentPixels;
        }

        List<PartitionLeaf> leaves() {
            return leaves;
        }

        List<Integer> residual() {
            return residual;
        }

        boolean authenticates(byte[] labels, byte[] gray, int w, int h, Budget budget) {
            return source.sameInputFrame(labels, gray, w, h) && source.revalidateForExport(budget);
        }
    }

    static final class ProducerFootprint {
        private final String rawSha, labelSha;
        private final int[] pixels;
        private final Object parentIdentity, originalIdentity;

        private ProducerFootprint(
                String raw, String labels, int[] members, Object parent, Object original) {
            rawSha = raw;
            labelSha = labels;
            pixels = members;
            parentIdentity = parent;
            originalIdentity = original;
        }

        String rawSha() {
            return rawSha;
        }

        String labelSha() {
            return labelSha;
        }

        int[] pixels() {
            return pixels.clone();
        }

        Object parentIdentity() {
            return parentIdentity;
        }

        Object originalIdentity() {
            return originalIdentity;
        }
    }
}
