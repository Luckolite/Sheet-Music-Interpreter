// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;
import java.lang.reflect.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class InvocationPartitionBatchOriginalTest {
    static ExactComponentMembers.Budget large() {
        return new ExactComponentMembers.Budget(1000000, 4000000);
    }

    static final class Scene {
        final int w = 16, h = 8;
        final byte[] labels = new byte[w * h], gray = new byte[w * h];
        final Object parent = new Object(), a = new Object(), b = new Object();
        final ExactComponentMembers.Collector collector;

        Scene(boolean residual) {
            Arrays.fill(gray, (byte) 255);
            for (int y : new int[] {2, 3})
                for (int x = 2; x <= 7; x++) {
                    labels[y * w + x] = 2;
                    gray[y * w + x] = 0;
                }
            collector = new ExactComponentMembers.Collector(gray, labels, w, h, large(), large());
            assertTrue(collector.bindInput(labels, gray, w, h));
            var builder = collector.begin();
            builder.add(2, 2, 7);
            builder.add(3, 2, 7);
            collector.record(
                    parent,
                    new ExactComponentMembers.Stats(12, 2, 7, 2, 3, 4.5f, 2.5f),
                    (byte) 2,
                    builder);
            var split = collector.beginSplit(parent, labels, w);
            var first = split.beginChild();
            first.add(2, 2, 7);
            split.capture(a, new ExactComponentMembers.Stats(6, 2, 7, 2, 2, 4.5f, 2), first);
            var second = split.beginChild();
            second.add(3, 2, residual ? 6 : 7);
            split.capture(
                    b,
                    new ExactComponentMembers.Stats(
                            residual ? 5 : 6, 2, residual ? 6 : 7, 3, 3, residual ? 4 : 4.5f, 3),
                    second);
            assertTrue(
                    residual
                            ? split.commitRetainingResidual(List.of(a, b))
                            : split.commit(List.of(a, b)));
            assertTrue(collector.seal(large()));
        }
    }

    @Test
    public void batchMatchesExistingPartitionsAndPreservesResiduals() {
        for (boolean residual : new boolean[] {false, true}) {
            var s = new Scene(residual);
            var batch = s.collector.producerPartitions(List.of(s.a, s.b), large());
            assertNotNull(batch);
            assertEquals(2, batch.rows().size());
            for (Object id : List.of(s.a, s.b)) {
                var old = s.collector.producerPartition(id, large());
                var now = batch.get(id);
                assertEquals(old.parentPixels(), now.parentPixels());
                assertEquals(old.leaves(), now.leaves());
                assertEquals(old.residual(), now.residual());
                assertEquals(old.parentStats(), now.parentStats());
            }
            assertEquals(residual ? List.of(3 * s.w + 7) : List.of(), batch.get(s.a).residual());
        }
    }

    @Test
    public void duplicateAndForeignRequestsRemainExplicitUnknownRows() {
        var s = new Scene(false);
        Object foreign = new Object();
        var batch =
                s.collector.producerPartitions(
                        Arrays.asList(s.a, s.a, s.b, foreign, null), large());
        assertNotNull(batch);
        assertEquals(5, batch.rows().size());
        assertNull(batch.get(s.a));
        assertNotNull(batch.get(s.b));
        assertNull(batch.get(foreign));
        assertEquals("UNKNOWN_DUPLICATE_IDENTITY", batch.rows().get(0).status());
        assertEquals("UNKNOWN_NULL_IDENTITY", batch.rows().get(4).status());
    }

    @Test
    public void foreignEqualBytesCannotBorrowAuthenticatedFrame() {
        var s = new Scene(false);
        var batch = s.collector.producerPartitions(List.of(s.a, s.b), large());
        assertTrue(batch.sharesFrame(s.collector));
        byte[] labels = s.labels.clone(), gray = s.gray.clone();
        var other = new ExactComponentMembers.Collector(gray, labels, s.w, s.h, large(), large());
        assertTrue(other.bindInput(labels, gray, s.w, s.h));
        assertTrue(other.seal(large()));
        assertFalse(batch.sharesFrame(other));
        assertFalse(batch.authenticates(s.labels.clone(), s.gray, s.w, s.h, large()));
    }

    @Test
    public void entryAndLaterMutationRefuseWithoutMemoization() {
        var s = new Scene(false);
        var batch = s.collector.producerPartitions(List.of(s.a, s.b), large());
        assertNotNull(batch);
        s.gray[0] = 0;
        assertFalse(batch.authenticates(s.labels, s.gray, s.w, s.h, large()));
        assertNull(s.collector.producerPartitions(List.of(s.a, s.b), large()));
        var beforeEntry = new Scene(false);
        beforeEntry.labels[0] = 5;
        assertNull(
                beforeEntry.collector.producerPartitions(
                        List.of(beforeEntry.a, beforeEntry.b), large()));
    }

    @Test
    public void mutationAfterEntryIsCaughtAtExit() {
        var s = new Scene(false);
        List<Object> malicious =
                new AbstractList<>() {
                    public int size() {
                        return 2;
                    }

                    public Object get(int i) {
                        s.gray[0] = 0;
                        return i == 0 ? s.a : s.b;
                    }
                };
        assertNull(s.collector.producerPartitions(malicious, large()));
    }

    @Test
    public void reservedExitBudgetKeepsUnknownRequestsAndCannotPublishUnauthenticatedRows() {
        var s = new Scene(false);
        assertNull(
                s.collector.producerPartitions(
                        List.of(s.a, s.b), new ExactComponentMembers.Budget(0, 0)));
        var budget = new ExactComponentMembers.Budget(4L * s.gray.length + 2, 100000);
        var batch = s.collector.producerPartitions(List.of(s.a, s.b), budget);
        assertNotNull(batch);
        assertEquals(2, batch.rows().size());
        assertNull(batch.get(s.a));
        assertNull(batch.get(s.b));
        assertEquals(0, budget.work);
    }

    @Test
    public void manyMembersFitBoundedBatchWhereRepeatedAuthenticationRefuses() {
        int w = 64, h = 64, n = 32;
        byte[] g = new byte[w * h], l = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        var ids = new ArrayList<Object>();
        for (int i = 0; i < n; i++) for (int y = 2; y <= 4; y++) l[y * w + i * 2] = 2;
        var c = new ExactComponentMembers.Collector(g, l, w, h, large(), large());
        assertTrue(c.bindInput(l, g, w, h));
        for (int i = 0; i < n; i++) {
            Object id = new Object();
            ids.add(id);
            var b = c.begin();
            for (int y = 2; y <= 4; y++) b.add(y, i * 2, i * 2);
            c.record(
                    id,
                    new ExactComponentMembers.Stats(3, i * 2, i * 2, 2, 4, i * 2, 3),
                    (byte) 2,
                    b);
        }
        assertTrue(c.seal(large()));
        var oldBudget = new ExactComponentMembers.Budget(100000, 4000000);
        int old = 0;
        for (Object id : ids) if (c.producerPartition(id, oldBudget) != null) old++;
        var batch = c.producerPartitions(ids, new ExactComponentMembers.Budget(100000, 4000000));
        assertTrue(old < n);
        assertNotNull(batch);
        assertEquals(n, batch.rows().size());
        assertTrue(batch.rows().stream().allMatch(r -> r.partition() != null));
    }

    @Test
    public void snapshotConstructorsAndCollectionsStayPrivateAndImmutable() {
        var s = new Scene(false);
        var batch = s.collector.producerPartitions(List.of(s.a, s.b), large());
        assertTrue(
                Arrays.stream(
                                ExactComponentMembers.ProducerPartitionBatch.class
                                        .getDeclaredConstructors())
                        .allMatch(c -> Modifier.isPrivate(c.getModifiers())));
        try {
            batch.rows().clear();
            fail("immutable");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            batch.get(s.a).parentPixels().clear();
            fail("immutable");
        } catch (UnsupportedOperationException expected) {
        }
    }
}
