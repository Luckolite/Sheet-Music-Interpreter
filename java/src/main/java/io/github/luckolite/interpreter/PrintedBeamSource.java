// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Final-note raw beam intake. Uncertain ownership is retained as unknown, never isolated flags. */
public final class PrintedBeamSource {
    private PrintedBeamSource() {}

    static final class Limit extends RuntimeException {}

    static final class UnknownCarrier extends RuntimeException {
        UnknownCarrier(String message) {
            super(message);
        }
    }

    static final class Work {
        long remaining;

        Work(long n) {
            remaining = n;
        }

        void take(long n) {
            if (n < 0 || n > remaining) throw new Limit();
            remaining -= n;
        }
    }

    record Statistics(
            int heads,
            int bound,
            int bodies,
            int shaftOwners,
            int columns,
            int groups,
            int bridges,
            int hooks,
            int unbound,
            int rivals,
            String refusal,
            long geometryWork,
            int flagCandidates,
            int detachedColumns,
            long flagWork,
            boolean flagRefused) {}

    record Capture(
            ScoreBeamSource source,
            Statistics statistics,
            List<Map<String, Object>> diagnostics,
            List<Map<String, Object>> columns) {}

    private record Lane(
            int id,
            PlayingTechniqueDetector.Staff staff,
            StaffPitchTrack track,
            PrintedCurveRuleInk ink,
            Map<Long, Optional<PrintedBeamRuleWitness.Witness>> witnesses,
            Map<Long, PrintedBeamLocalRuleInk> localInks) {}

    private record Binding(Lane lane, float bottom, float gap) {}

    private record Candidate(
            int owner,
            Binding binding,
            PrintedBeamShafts.Shaft shaft,
            float glyphGap,
            PrintedBeamShaftPixels.Certificate pixels,
            PrintedBeamShafts.Shaft envelope) {}

    private static final class Node {
        final int id, measure;
        Binding binding;
        boolean coreBinding;
        final List<Candidate> candidates = new ArrayList<>();
        final List<Integer> owners = new ArrayList<>();
        boolean rival;

        Node(int id, int measure, Binding binding) {
            this.id = id;
            this.measure = measure;
            this.binding = binding;
        }
    }

    /** Same-capture optional evidence. No constructor accepts persisted beam values. */
    static final class CaretEvidence implements PrintedBeamGeometry.PrimaryObserver {
        private final ScorePageInterpretation source;
        private final byte[] raw, labels;
        private final int width, height, page;
        private final Thread thread = Thread.currentThread();
        private long remaining, bytes;
        private boolean failed, resolved, consumed;
        private PrintedBeamShaftPixels.Frame frame;
        private final IdentityHashMap<
                        FiniteRawBeamStrip.Proof,
                        IdentityHashMap<PrintedBeamGeometry.Column, Contact>>
                contacts = new IdentityHashMap<>();
        private final List<PrintedBeamGeometry.Primary> primaries = new ArrayList<>();
        private List<NoteArticulationDetector.Anchor> anchors = List.of();
        private List<HeadBeam>[] owners;
        private int[] additions;
        private long legacyStorageQuota;
        private final long producerLimit, admissionLimit, storageLimit;

        private CaretEvidence(
                ScorePageInterpretation source,
                byte[] labels,
                byte[] raw,
                int width,
                int height,
                int page,
                long work,
                long bytes,
                long legacyBytes,
                long producerLimit,
                long admissionLimit,
                long storageLimit) {
            this.source = source;
            this.labels = labels;
            this.raw = raw;
            this.width = width;
            this.height = height;
            this.page = page;
            remaining = Math.min(1_000_000, work);
            this.bytes = Math.min(1_000_000, bytes);
            this.legacyStorageQuota = Math.min(512_000_000, legacyBytes);
            this.producerLimit = producerLimit;
            this.admissionLimit = admissionLimit;
            this.storageLimit = storageLimit;
        }

        private static final class Contact {
            int next, last;
            final List<PrintedBeamShaftPixels.Certificate> candidates;

            Contact(int first, int last, List<PrintedBeamShaftPixels.Certificate> candidates) {
                next = first;
                this.last = last;
                this.candidates = new ArrayList<>(candidates);
            }
        }

        private record HeadBeam(
                int owner,
                int left,
                int right,
                int start,
                int cap,
                int direction,
                int first,
                int last,
                int thickness,
                int rasterLow,
                int rasterHigh) {}

        public boolean take(long work, long storage) {
            if (failed
                    || thread != Thread.currentThread()
                    || work < 0
                    || storage < 0
                    || work > remaining
                    || storage > bytes) {
                failed = true;
                contacts.clear();
                primaries.clear();
                return false;
            }
            remaining -= work;
            bytes -= storage;
            return true;
        }

        private boolean same(
                ScorePageInterpretation score,
                byte[] labels,
                byte[] gray,
                int width,
                int height,
                int page) {
            return !failed
                    && thread == Thread.currentThread()
                    && source == score
                    && this.labels == labels
                    && raw == gray
                    && this.width == width
                    && this.height == height
                    && this.page == page;
        }

        private void bind(PrintedBeamShaftPixels.Frame frame) {
            if (!take(64, 64)) return;
            try {
                frame.check(source, raw, width, height, page);
                this.frame = frame;
            } catch (IllegalArgumentException invalid) {
                failed = true;
            }
        }

        public void contact(
                byte[] gray,
                int width,
                int height,
                PrintedBeamGeometry.Column shaft,
                FiniteRawBeamStrip.Proof proof,
                int top,
                int firstTop,
                int lastTop,
                int[] accepted,
                int count,
                int left,
                int low,
                int stride) {
            if (failed) return;
            if (raw != gray
                    || this.width != width
                    || this.height != height
                    || shaft.targets() == null
                    || shaft.targets().frame != frame) {
                failed = true;
                return;
            }
            var certificates = shaft.targets().certificates();
            if (!take(
                    128L + 8L * certificates.size() * (count + 1),
                    128L + 32L * certificates.size())) return;
            var columns = contacts.computeIfAbsent(proof, k -> new IdentityHashMap<>());
            Contact contact = columns.get(shaft);
            if (contact == null) {
                if (top != firstTop) {
                    failed = true;
                    return;
                }
                contact = new Contact(firstTop, lastTop, certificates);
                columns.put(shaft, contact);
            }
            if (contact.next != top || contact.last != lastTop) {
                failed = true;
                return;
            }
            for (int i = contact.candidates.size() - 1; i >= 0; i--) {
                var certificate = contact.candidates.get(i);
                boolean reached = false;
                for (int n = 0; n < count; n++) {
                    int slot = accepted[n], x = left + slot % stride, y = low + slot / stride;
                    if (certificate.has(x, y)) {
                        reached = true;
                        break;
                    }
                }
                if (!reached) contact.candidates.remove(i);
            }
            contact.next++;
        }

        public void primary(PrintedBeamGeometry.Primary proof) {
            if (!take(64, 64)) return;
            if (proof.raw != raw || proof.width != width || proof.height != height) {
                failed = true;
                return;
            }
            primaries.add(proof);
        }

        @SuppressWarnings("unchecked")
        private void resolve(
                List<PrintedSlurOwners.GeometryHead> heads,
                Binding[] bindings,
                List<Lane> lanes,
                Map<Integer, Node> byId) {
            if (!take(128L * heads.size() + 128, 128L * heads.size() + 256)) return;
            var list = new ArrayList<NoteArticulationDetector.Anchor>(heads.size());
            owners = (List<HeadBeam>[]) new List<?>[heads.size()];
            additions = new int[heads.size()];
            for (var head : heads) {
                int owner = head.owner();
                Binding binding = bindings[owner];
                Lane lane = binding == null ? null : binding.lane();
                if (owner != list.size()) {
                    failed = true;
                    return;
                }
                if (lane == null) {
                    double best = Double.POSITIVE_INFINITY;
                    for (var candidate : lanes) {
                        if (!take(16, 0)) return;
                        double d =
                                Math.abs(
                                        head.y()
                                                - (candidate.staff().top()
                                                                + candidate.staff().bottom())
                                                        * .5);
                        if (d < best) {
                            best = d;
                            lane = candidate;
                        }
                    }
                }
                if (lane == null) {
                    failed = true;
                    return;
                }
                list.add(
                        new NoteArticulationDetector.Anchor(
                                head.x(), head.y(), lane.staff().gap(), lane.id(), lane.track()));
                owners[owner] = new ArrayList<>();
            }
            anchors = List.copyOf(list);
            for (var primary : primaries)
                for (var column : new PrintedBeamGeometry.Column[] {primary.a, primary.b}) {
                    Node node = byId.get(column.id());
                    if (node == null || node.rival || column.direction() != -1) continue;
                    var columnContacts = contacts.get(primary.strip);
                    Contact contact = columnContacts == null ? null : columnContacts.get(column);
                    if (contact == null || contact.next != contact.last + 1) continue;
                    int index = column == primary.a ? 0 : primary.strip.columns().size() - 1;
                    var raster = primary.strip.rasterTops().get(index);
                    for (var certificate : contact.candidates) {
                        if (!take(
                                128L
                                        + 32L * node.candidates.size()
                                        + 16L * certificate.shaft.columns().size(),
                                192)) return;
                        Candidate candidate = null;
                        for (var actual : node.candidates)
                            if (actual.pixels() == certificate) candidate = actual;
                        if (candidate == null
                                || certificate.frame != frame
                                || !node.owners.contains(candidate.owner())) continue;
                        var shaft = certificate.shaft;
                        boolean terminal = false;
                        for (var end : shaft.columns())
                            if (certificate.has(end.x(), end.endY())
                                    && end.endY() >= raster.low()
                                    && end.endY() <= raster.high() + primary.strip.thickness() - 1)
                                terminal = true;
                        if (!terminal) continue;
                        owners[candidate.owner()].add(
                                new HeadBeam(
                                        candidate.owner(),
                                        shaft.left(),
                                        shaft.right(),
                                        shaft.startY(),
                                        shaft.endY(),
                                        shaft.direction(),
                                        primary.a.right() + 2,
                                        primary.b.left() - 2,
                                        primary.strip.thickness(),
                                        raster.low(),
                                        raster.high()));
                    }
                }
            // Drop all certificates, bindings, carrier caches and geometry objects before rerun.
            contacts.clear();
            primaries.clear();
            resolved = true;
        }

        private boolean ready() {
            if (failed || !resolved) return false;
            for (int i = 0; i < owners.length; i++)
                if (!owners[i].isEmpty()
                        && (source.notes().get(i).articulations() & NoteArticulation.ARTICULATIONS)
                                == 0) return true;
            return false;
        }

        List<NoteArticulationDetector.Anchor> anchors() {
            return anchors;
        }

        boolean inputs(
                byte[] labels,
                byte[] raw,
                int width,
                int height,
                List<NoteArticulationDetector.Anchor> notes) {
            return resolved
                    && !failed
                    && this.labels == labels
                    && this.raw == raw
                    && this.width == width
                    && this.height == height
                    && anchors == notes
                    && thread == Thread.currentThread();
        }

        int[] currentBits() {
            if (!take(8L * source.notes().size() + 32, 4L * source.notes().size() + 32))
                return new int[0];
            int[] bits = new int[source.notes().size()];
            for (int i = 0; i < bits.length; i++)
                bits[i] = source.notes().get(i).articulations() & NoteArticulation.ARTICULATIONS;
            return bits;
        }

        boolean proves(
                int owner,
                NoteArticulationDetector.Anchor note,
                int left,
                int top,
                int right,
                int bottom) {
            if (!resolved
                    || failed
                    || owner < 0
                    || owner >= anchors.size()
                    || anchors.get(owner) != note
                    || thread != Thread.currentThread()) return false;
            float gap = note.gap();
            double x = (left + right) * .5, y = (top + bottom) * .5, dy = (note.y() - y) / gap;
            if (dy < .7 || dy > 8 || Math.abs(x - note.x()) > gap * .7) return false;
            if (!take(32L + 32L * owners[owner].size(), 0)) return false;
            for (var proof : owners[owner]) {
                int separation = proof.cap - bottom, head = Math.round(note.y() - gap * .3f);
                int thickness = Math.max(3, (int) Math.ceil(gap * .25f)),
                        reach = Math.max(4, Math.round(gap * 1.5f));
                if (proof.direction != -1
                        || separation < Math.max(1, Math.round(gap * .25f))
                        || separation > Math.round(gap * 2.5f)
                        || head - proof.cap < gap * 3
                        || proof.thickness < thickness) continue;
                if (proof.right - proof.first < reach && proof.last - proof.left < reach) continue;
                if (NoteArticulationDetector.ownedCaretBeyond(
                        raw,
                        width,
                        height,
                        note,
                        proof.left,
                        proof.right,
                        proof.cap,
                        thickness,
                        separation,
                        this)) return true;
                if (failed) return false;
            }
            return false;
        }

        boolean sameAttack(int a, int b) {
            return ScoreTechniqueChange.attackOf(source.notes().get(a))
                    .equals(ScoreTechniqueChange.attackOf(source.notes().get(b)));
        }

        void claim(int owner) {
            if (failed
                    || owner < 0
                    || owner >= additions.length
                    || !take(16L * anchors.size() + 64, 0)) return;
            var head = anchors.get(owner);
            for (int i = 0; i < anchors.size(); i++)
                if (anchors.get(i).staff() == head.staff()
                        && Math.abs(anchors.get(i).x() - head.x()) < head.gap() * .45f
                        && sameAttack(owner, i)) additions[i] |= NoteArticulation.MARCATO;
        }

        void discard() {
            failed = true;
            if (additions != null) Arrays.fill(additions, 0);
            contacts.clear();
            primaries.clear();
        }

        private ScorePageInterpretation apply(
                ScorePageInterpretation score,
                byte[] labels,
                byte[] gray,
                int width,
                int height,
                int page) {
            if (!same(score, labels, gray, width, height, page) || !ready() || consumed)
                return score;
            consumed = true;
            long area = (long) width * height;
            // Deterministic conservative storage admission charge, not a physical heap
            // allocation or a Runtime.freeMemory threshold. Existing consumer helpers
            // retain their input/allocation limits. One rerun consumes this quota once.
            // The separate proof book and the caret-stage8M byte ledger are unchanged.
            long capacity = 64L * area + 4096L * source.notes().size() + 8_000_000;
            if (area > 20_000_000 || capacity > legacyStorageQuota) return score;
            legacyStorageQuota -= capacity;
            int[] current = currentBits();
            if (current.length != anchors.size()) return score;
            NoteArticulationDetector.detectOwnedCarets(
                    labels,
                    gray,
                    width,
                    height,
                    anchors,
                    current,
                    this,
                    producerLimit,
                    admissionLimit,
                    storageLimit);
            if (failed) return score;
            if (!take(64L * source.notes().size() + 128, 64L * source.notes().size() + 256))
                return score;
            var notes = new ArrayList<ScoreNoteEvent>(source.notes());
            boolean changed = false;
            for (int i = 0; i < notes.size(); i++)
                if (additions[i] != 0
                        && (notes.get(i).articulations() & NoteArticulation.ARTICULATIONS) == 0) {
                    notes.set(
                            i,
                            notes.get(i)
                                    .withArticulations(
                                            notes.get(i).articulations() | additions[i]));
                    changed = true;
                }
            // This producer only adds MARCATO to the same ordered notes; retain the
            // already-bound clock context. Generic note edits still invalidate it.
            return changed
                    ? score.withNotes(
                                    notes,
                                    java.util.stream.IntStream.range(0, notes.size())
                                            .boxed()
                                            .toList())
                            .withClockContextSource(score.clockContextSource())
                    : score;
        }
    }

    public static ScorePageInterpretation apply(
            ScorePageInterpretation score,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            int physicalPage) {
        return applyWithCaretLimits(
                score,
                labels,
                gray,
                width,
                height,
                staffs,
                physicalPage,
                1_000_000,
                1_000_000,
                512_000_000,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE);
    }

    static ScorePageInterpretation applyWithCaretLimits(
            ScorePageInterpretation score,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            int physicalPage,
            long proofWork,
            long proofBytes,
            long legacyBytes,
            long producerLimit,
            long admissionLimit,
            long storageLimit) {
        if (score == null || physicalPage < 0) return score;
        if (!score.beamSource().groups().isEmpty()
                || !score.beamSource().detachedColumns().isEmpty()) return score;
        CaretEvidence evidence =
                new CaretEvidence(
                        score,
                        labels,
                        gray,
                        width,
                        height,
                        physicalPage,
                        proofWork,
                        proofBytes,
                        legacyBytes,
                        producerLimit,
                        admissionLimit,
                        storageLimit);
        Capture result =
                capture(
                        score,
                        labels,
                        gray,
                        width,
                        height,
                        staffs,
                        physicalPage,
                        8_000_000,
                        256_000_000,
                        64_000_000,
                        evidence);
        return result.statistics().refusal().isEmpty()
                ? evidence.apply(score, labels, gray, width, height, physicalPage)
                        .withBeamSource(result.source())
                : score;
    }

    static Capture capture(
            ScorePageInterpretation score,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            int page,
            long shaftAllowance,
            long geometryAllowance) {
        return capture(
                score,
                labels,
                gray,
                width,
                height,
                staffs,
                page,
                shaftAllowance,
                geometryAllowance,
                64_000_000);
    }

    static Capture capture(
            ScorePageInterpretation score,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            int page,
            long shaftAllowance,
            long geometryAllowance,
            long flagAllowance) {
        return capture(
                score,
                labels,
                gray,
                width,
                height,
                staffs,
                page,
                shaftAllowance,
                geometryAllowance,
                flagAllowance,
                null);
    }

    private static Capture capture(
            ScorePageInterpretation score,
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<PlayingTechniqueDetector.Staff> staffs,
            int page,
            long shaftAllowance,
            long geometryAllowance,
            long flagAllowance,
            CaretEvidence evidence) {
        int count = score == null ? 0 : score.notes().size(),
                bound = 0,
                bodies = 0,
                shaftOwners = 0,
                rivals = 0;
        long geometryWork = 0;
        var diagnostics = new ArrayList<Map<String, Object>>();
        var columnDiagnostics = new ArrayList<Map<String, Object>>();
        var columns = new ArrayList<PrintedBeamGeometry.Column>();
        try {
            if (score == null
                    || gray == null
                    || labels != null && labels.length != gray.length
                    || width < 3
                    || height < 3
                    || gray.length != (long) width * height
                    || gray.length > 32_000_000
                    || page < 0
                    || staffs == null
                    || staffs.size() > 128
                    || count > 4096
                    || score.measures().isEmpty())
                throw new IllegalArgumentException("Invalid beam intake");
            var work = new Work(128_000_000);
            work.take(64);
            var contextBudget =
                    new FaintQuarterRestInk.Budget(
                            128_000_000,
                            64_000_000,
                            (addedWork, addedBytes) ->
                                    work.take(
                                            Math.addExact(
                                                    addedWork, Math.addExact(addedBytes, 7) / 8)));

            var shaftBudget = new PrintedBeamShafts.Budget(shaftAllowance);
            var pixelFrame =
                    new PrintedBeamShaftPixels.Frame(
                            score, gray, width, height, page, work::take, true);
            if (evidence != null) evidence.bind(pixelFrame);
            var coreFrame = pixelFrame;
            var heads =
                    PrintedSlurContext.geometryHeads(
                            score.notes(), score.measures(), width, height);
            List<Lane> lanes = new ArrayList<>();
            for (int i = 0; i < staffs.size(); i++) {
                var staff = Objects.requireNonNull(staffs.get(i));
                if (!Float.isFinite(staff.top() + staff.bottom() + staff.gap())
                        || staff.gap() < 3
                        || staff.gap() > 64
                        || staff.count() < 1
                        || staff.index() < 0
                        || staff.index() >= staff.count())
                    throw new IllegalArgumentException("Invalid staff lane");
                var track =
                        StaffPitchTrack.forPrintedSlurs(
                                gray, width, height, staff.top(), staff.bottom(), staff.gap());
                lanes.add(
                        new Lane(
                                i,
                                staff,
                                track,
                                track == null
                                        ? null
                                        : new PrintedCurveRuleInk(
                                                null, gray, width, height, 165, track),
                                new HashMap<>(),
                                new HashMap<>()));
            }
            Binding[] bindings = new Binding[count];
            boolean[] body = new boolean[count];
            List<Candidate> candidates = new ArrayList<>();
            int[] choices = new int[count];
            for (var head : heads) {
                int owner = head.owner();
                var note = score.notes().get(owner);
                Binding binding =
                        bind(
                                head,
                                note,
                                lanes,
                                gray,
                                width,
                                height,
                                work,
                                contextBudget,
                                score,
                                pixelFrame,
                                page);
                bindings[owner] = binding;
                var row = new LinkedHashMap<String, Object>();
                row.put("owner", owner);
                row.put("x", head.x());
                row.put("y", head.y());
                row.put("staff", note.staffIndex());
                row.put("count", note.staffCount());
                row.put("step", note.staffStep());
                row.put("beamCount", note.beamCount());
                diagnostics.add(row);
                if (binding == null) {
                    row.put("status", "unbound");
                    continue;
                }
                bound++;
                row.put("lane", binding.lane().id());
                row.put("bottom", binding.bottom());
                row.put("gap", binding.gap());
                row.put("track", binding.lane().track() != null);
                work.take((long) Math.ceil(binding.gap() * binding.gap() * 3));
                float glyphGap = binding.gap() * glyphScale(note);
                row.put("glyphGap", glyphGap);
                body[owner] =
                        PrintedSlurOwners.headBody(gray, width, height, head, glyphGap)
                                && wideBody(gray, width, height, head, glyphGap);
                if (!body[owner]) {
                    row.put("status", "no body");
                    continue;
                }
                bodies++;
                var coreShafts =
                        PrintedBeamShafts.atStrictVisited(
                                gray,
                                width,
                                height,
                                head.x(),
                                head.y(),
                                glyphGap,
                                shaftBudget,
                                coreFrame);
                if (shaftBudget.refused()) throw new Limit();
                var envelopes =
                        PrintedBeamShafts.atAllVisited(
                                gray,
                                width,
                                height,
                                head.x(),
                                head.y(),
                                glyphGap,
                                shaftBudget,
                                pixelFrame);
                if (shaftBudget.refused()) throw new Limit();
                work.take(16);
                row.put("rawStrongShafts", coreShafts.size());
                row.put("rawFaintShafts", envelopes.size());
                var cores =
                        qualify(
                                coreShafts,
                                gray,
                                width,
                                height,
                                head,
                                glyphGap,
                                work,
                                binding,
                                owner,
                                coreFrame);
                var faint =
                        qualify(
                                envelopes,
                                gray,
                                width,
                                height,
                                head,
                                glyphGap,
                                work,
                                binding,
                                owner,
                                pixelFrame);
                work.take(16);
                row.put("strongShafts", cores.size());
                row.put("faintShafts", faint.size());
                var selected = preserveCores(cores, faint, envelopes, work);
                candidates.addAll(selected);
                choices[owner] = selected.size();
                row.put("shafts", choices[owner]);
                row.put(
                        "status",
                        choices[owner] == 1
                                ? "one shaft"
                                : choices[owner] > 1 ? "rivals" : "no shaft");
                if (choices[owner] == 1) shaftOwners++;
            }
            if (shaftBudget.refused()) throw new Limit();
            int[] parent = new int[candidates.size()];
            for (int i = 0; i < parent.length; i++) parent[i] = i;
            for (int i = 0; i < candidates.size(); i++)
                for (int j = i + 1; j < candidates.size(); j++) {
                    work.take(1);
                    var a = candidates.get(i);
                    var b = candidates.get(j);
                    if (shared(a, b, score.notes())) parent[root(parent, j)] = root(parent, i);
                }
            Map<Integer, Node> nodes = new LinkedHashMap<>();
            for (int i = 0; i < candidates.size(); i++) {
                var c = candidates.get(i);
                int key = root(parent, i);
                Node node = nodes.get(key);
                if (node == null) {
                    node =
                            new Node(
                                    nodes.size(),
                                    score.notes().get(c.owner()).measureIndex(),
                                    c.binding());
                    nodes.put(key, node);
                }
                node.candidates.add(c);
                if (!c.shaft().pixels().contrast && !node.coreBinding) {
                    node.binding = c.binding();
                    node.coreBinding = true;
                }
                if (choices[c.owner()] == 1) node.owners.add(c.owner());
                else node.rival = true;
            }
            var byId = new HashMap<Integer, Node>();
            for (var node : nodes.values()) {
                work.take(node.candidates.size() + 32L);
                var cores = new ArrayList<Candidate>();
                for (var c : node.candidates) if (!c.shaft().pixels().contrast) cores.add(c);
                List<Candidate> geometryCandidates = cores.isEmpty() ? node.candidates : cores;
                var first = geometryCandidates.get(0);
                var shaft = first.shaft();
                float gap =
                        geometryCandidates.stream()
                                .map(Candidate::glyphGap)
                                .max(Float::compare)
                                .orElseThrow();
                float contextGap =
                        node.candidates.stream()
                                .map(Candidate::glyphGap)
                                .max(Float::compare)
                                .orElseThrow();
                // Every final head remains competing context, including non-beamed and non-shaft
                // heads.
                work.take(heads.size());
                for (var head : heads) {
                    if (node.owners.contains(head.owner())) continue;
                    var n = score.notes().get(head.owner());
                    if (n.measureIndex() != node.measure
                            || n.staffIndex() != first.binding().lane().staff().index()
                            || n.staffCount() != first.binding().lane().staff().count()) continue;
                    boolean touched = false;
                    for (var candidate : node.candidates) {
                        var contextShaft = candidate.envelope();
                        int low = Math.min(contextShaft.startY(), contextShaft.endY()),
                                high = Math.max(contextShaft.startY(), contextShaft.endY());
                        if (head.y() < low - contextGap
                                || head.y() > high + contextGap
                                || head.x() < contextShaft.left() - contextGap
                                || head.x() > contextShaft.right() + contextGap) continue;
                        work.take(1);
                        if (headContact(
                                gray,
                                width,
                                height,
                                head,
                                contextShaft,
                                bindings[head.owner()] == null
                                        ? contextGap
                                        : bindings[head.owner()].gap() * glyphScale(n),
                                work,
                                bindings[head.owner()] == null
                                        ? node.binding
                                        : bindings[head.owner()])) {
                            touched = true;
                            break;
                        }
                    }
                    if (touched) {
                        node.rival = true;
                        rivals++;
                        break;
                    }
                }
                // Duplicate retained heads cannot turn one physical head into fabricated aliases.
                for (int a : node.owners)
                    for (int b : node.owners)
                        if (a < b
                                && heads.get(a).x() == heads.get(b).x()
                                && heads.get(a).y() == heads.get(b).y()) node.rival = true;
                var ends = new TreeMap<Integer, Integer>();
                int start = shaft.startY(), cap = shaft.endY();
                for (var c : geometryCandidates) {
                    var s = c.shaft();
                    start =
                            shaft.direction() > 0
                                    ? Math.min(start, s.startY())
                                    : Math.max(start, s.startY());
                    cap = shaft.direction() > 0 ? Math.max(cap, s.endY()) : Math.min(cap, s.endY());
                    for (var col : s.columns())
                        ends.merge(
                                col.x(), col.endY(), shaft.direction() > 0 ? Math::max : Math::min);
                }
                int maximum =
                        node.rival || node.owners.isEmpty()
                                ? 0
                                : node.owners.stream()
                                        .mapToInt(i -> score.notes().get(i).beamCount())
                                        .max()
                                        .orElse(0);
                var physical =
                        ends.entrySet().stream()
                                .map(e -> new PrintedBeamGeometry.End(e.getKey(), e.getValue()))
                                .toList();
                work.take(node.candidates.size() + 32L);
                var column =
                        new PrintedBeamGeometry.Column(
                                node.id,
                                node.measure,
                                first.binding().lane().staff().index(),
                                first.binding().lane().staff().count(),
                                shaft.direction(),
                                ends.firstKey(),
                                ends.lastKey(),
                                cap,
                                gap,
                                maximum,
                                physical,
                                start,
                                new PrintedBeamShaftPixels.Targets(
                                        pixelFrame,
                                        node.id,
                                        node.candidates.stream().map(Candidate::pixels).toList()));
                columns.add(column);
                byId.put(node.id, node);
            }
            var geometryBudget = new PrintedBeamGeometry.Budget(geometryAllowance);
            var result =
                    PrintedBeamGeometry.detect(
                            gray,
                            width,
                            height,
                            columns,
                            new PrintedBeamGeometry.CarrierPlane() {
                                public boolean permitsPair(
                                        PrintedBeamGeometry.Column a,
                                        PrintedBeamGeometry.Column b) {
                                    work.take(heads.size() + 16L);
                                    var first = byId.get(a.id());
                                    var last = byId.get(b.id());
                                    // A proved shaft in another direction is distinct voice
                                    // context.
                                    // An authenticated same-lane head with no unique owned shaft is
                                    // still competing context; missing it cannot erase a beam
                                    // column.
                                    for (var head : heads) {
                                        int owner = head.owner();
                                        var binding = bindings[owner];
                                        if (choices[owner] == 1
                                                || first.binding.lane().id()
                                                        != last.binding.lane().id()
                                                || score.notes().get(owner).measureIndex()
                                                        != a.measure()
                                                || !(head.x() > a.right() && head.x() < b.left()))
                                            continue;
                                        if (binding != null) {
                                            if (body[owner]
                                                    && binding.lane().id()
                                                            == first.binding.lane().id())
                                                return false;
                                            continue;
                                        }
                                        // A necessary lane range only vetoes; it never binds this
                                        // head.
                                        // Include every phase allowed by the complete local
                                        // five-rule proof.
                                        var note = score.notes().get(owner);
                                        var staff = first.binding.lane().staff();
                                        work.take(64);
                                        if (note.staffIndex() != staff.index()
                                                || note.staffCount() != staff.count()
                                                || !PrintedBeamBindingRange.possible(
                                                        head.y(),
                                                        note.staffStep(),
                                                        staff.bottom(),
                                                        staff.gap(),
                                                        staff.gap() * .8f,
                                                        staff.gap() * .12f,
                                                        .45f)) continue;
                                        float gap = first.binding.gap();
                                        float glyphGap = gap * glyphScale(note);
                                        work.take((long) Math.ceil(gap * gap * 3));
                                        if (PrintedSlurOwners.headBody(
                                                        gray, width, height, head, glyphGap)
                                                && wideBody(gray, width, height, head, glyphGap))
                                            return false;
                                    }
                                    return true;
                                }

                                public boolean strictAt(
                                        PrintedBeamGeometry.Column a,
                                        PrintedBeamGeometry.Column b,
                                        int x,
                                        int y) {
                                    return carrierStrict(
                                                    byId.get(a.id()),
                                                    byId.get(b.id()),
                                                    x,
                                                    y,
                                                    labels,
                                                    gray,
                                                    width,
                                                    height,
                                                    work)
                                            > 0;
                                }

                                public boolean strictUnknown(
                                        PrintedBeamGeometry.Column a,
                                        PrintedBeamGeometry.Column b,
                                        int x,
                                        int y) {
                                    return carrierStrict(
                                                    byId.get(a.id()),
                                                    byId.get(b.id()),
                                                    x,
                                                    y,
                                                    labels,
                                                    gray,
                                                    width,
                                                    height,
                                                    work)
                                            < 0;
                                }

                                public boolean contrastEnabled() {
                                    return true;
                                }

                                public void chargeContrast(long units) {
                                    work.take(units);
                                }

                                public boolean at(
                                        PrintedBeamGeometry.Column a,
                                        PrintedBeamGeometry.Column b,
                                        int x,
                                        int y) {
                                    return carrier(
                                                    byId.get(a.id()),
                                                    byId.get(b.id()),
                                                    x,
                                                    y,
                                                    labels,
                                                    gray,
                                                    width,
                                                    height,
                                                    work)
                                            > 0;
                                }

                                public boolean unknown(
                                        PrintedBeamGeometry.Column a,
                                        PrintedBeamGeometry.Column b,
                                        int x,
                                        int y) {
                                    return carrier(
                                                    byId.get(a.id()),
                                                    byId.get(b.id()),
                                                    x,
                                                    y,
                                                    labels,
                                                    gray,
                                                    width,
                                                    height,
                                                    work)
                                            < 0;
                                }
                            },
                            geometryBudget,
                            evidence);
            geometryWork = result.work();
            if (result.budgetExceeded()) throw new Limit();
            var unlinkedIds = new HashSet<>(result.unlinkedColumns());
            var diagnosticBudget = new Work(128_000_000);
            for (var column : columns) {
                var node = byId.get(column.id());
                var row = new LinkedHashMap<String, Object>();
                row.put("column", diagnosticColumn(column, work));
                row.put("owners", List.copyOf(node.owners));
                row.put("unlinked", unlinkedIds.contains(column.id()));
                row.put("rival", node.rival);
                row.put("staffGap", node.binding.gap());
                row.put("staffBottom", node.binding.bottom());
                columnDiagnostics.add(row);
                if (Boolean.getBoolean("musicsheets.beam.flagDiagnostics")
                        && unlinkedIds.contains(column.id()))
                    try {
                        int left = Math.max(0, (int) Math.floor(column.left() - 2 * column.gap())),
                                right =
                                        Math.min(
                                                width - 1,
                                                (int) Math.ceil(column.right() + 3 * column.gap()));
                        int
                                top =
                                        Math.max(
                                                0,
                                                (int)
                                                        Math.floor(
                                                                Math.min(
                                                                        column.cap()
                                                                                - 3 * column.gap(),
                                                                        column.shaftStart()
                                                                                - column.gap()))),
                                bottom =
                                        Math.min(
                                                height - 1,
                                                (int)
                                                        Math.ceil(
                                                                Math.max(
                                                                        column.cap()
                                                                                + 3 * column.gap(),
                                                                        column.shaftStart()
                                                                                + column.gap())));
                        byte[] states = new byte[(right - left + 1) * (bottom - top + 1)];
                        int index = 0;
                        for (int y = top; y <= bottom; y++)
                            for (int x = left; x <= right; x++) {
                                int role =
                                        carrier(
                                                node,
                                                node,
                                                x,
                                                y,
                                                labels,
                                                gray,
                                                width,
                                                height,
                                                diagnosticBudget);
                                states[index++] = (byte) (role < 0 ? 2 : role);
                            }
                        row.put("roi", List.of(left, top, right, bottom));
                        row.put("carrierStatesBase64", Base64.getEncoder().encodeToString(states));
                    } catch (Limit refused) {
                        row.put("diagnosticRefusal", "budget");
                    }
            }
            List<ScoreBeamSource.Group> groups = new ArrayList<>();
            int bridges = 0, hooks = 0;
            for (var group : result.groups()) {
                List<ScoreBeamSource.Column> members = new ArrayList<>();
                boolean valid = true;
                for (int id : group.columns()) {
                    var node = byId.get(id);
                    if (node.rival || node.owners.isEmpty()) {
                        valid = false;
                        break;
                    }
                    members.add(
                            new ScoreBeamSource.Column(
                                    node.owners.stream()
                                            .sorted()
                                            .map(i -> new ScoreBeamSource.Anchor(page, i))
                                            .toList()));
                }
                if (!valid) continue;
                List<ScoreBeamSource.Hook> marks = new ArrayList<>();
                for (var h : group.hooks())
                    marks.add(
                            new ScoreBeamSource.Hook(
                                    group.columns().indexOf(h.columnId()),
                                    h.level(),
                                    h.direction() > 0
                                            ? ScoreBeamSource.Direction.FORWARD
                                            : ScoreBeamSource.Direction.BACKWARD));
                String id =
                        "printed-beam:" + page + ":" + members.get(0).members().get(0).noteIndex();
                groups.add(new ScoreBeamSource.Group(id, members, group.bridges(), marks));
                for (int mask : group.bridges()) bridges += Integer.bitCount(mask);
                hooks += marks.size();
            }
            // Only independent positive attached-flag AND exhaustive primary-absence proofs
            // admit unlinked columns. Every retained head and authenticated shaft is context.
            var detached = new ArrayList<ScoreBeamSource.Column>();
            var flagHeads = new ArrayList<PrintedIsolatedFlags.Head>();
            for (var head : heads) {
                var binding = bindings[head.owner()];
                double g =
                        binding == null
                                ? 64
                                : binding.gap() * glyphScale(score.notes().get(head.owner()));
                // Unknown glyph sizing expands competing veto context; it never proves a core.
                flagHeads.add(
                        new PrintedIsolatedFlags.Head(
                                head.owner(), head.x(), head.y(), g < 3 ? 64 : g));
            }
            var flagBudget = new PrintedIsolatedFlags.Budget(flagAllowance);
            var flagCarrierWork = new Work(64_000_000);
            boolean flagRefused = false;
            int flagCandidates = 0;
            try {
                for (var column : columns) {
                    var node = byId.get(column.id());
                    if (!unlinkedIds.contains(column.id()) || node.rival || node.owners.isEmpty())
                        continue;
                    flagCandidates++;
                    var proof =
                            PrintedIsolatedFlags.prove(
                                    gray,
                                    width,
                                    height,
                                    column,
                                    List.copyOf(node.owners),
                                    flagHeads,
                                    columns,
                                    new FiniteRawBeamStrip.Carrier() {
                                        public boolean contrastEnabled() {
                                            return true;
                                        }

                                        public boolean at(int x, int y) {
                                            return carrier(
                                                            node,
                                                            node,
                                                            x,
                                                            y,
                                                            labels,
                                                            gray,
                                                            width,
                                                            height,
                                                            flagCarrierWork)
                                                    > 0;
                                        }

                                        public boolean unknown(int x, int y) {
                                            return carrier(
                                                            node,
                                                            node,
                                                            x,
                                                            y,
                                                            labels,
                                                            gray,
                                                            width,
                                                            height,
                                                            flagCarrierWork)
                                                    < 0;
                                        }
                                    },
                                    flagBudget);
                    if (proof.isPresent()) {
                        var p = proof.get();
                        if (p.columnId() != column.id() || !p.owners().equals(node.owners))
                            throw new IllegalArgumentException("Flag owner mismatch");
                        detached.add(
                                new ScoreBeamSource.Column(
                                        p.owners().stream()
                                                .sorted()
                                                .map(i -> new ScoreBeamSource.Anchor(page, i))
                                                .toList()));
                    }
                    if (flagBudget.exhausted()) {
                        flagRefused = true;
                        break;
                    }
                }
            } catch (Limit exhausted) {
                flagRefused = true;
            }
            if (flagBudget.exhausted() || flagRefused) {
                detached.clear();
                flagRefused = true;
            }
            var source = ScoreBeamSource.capture(page, count, groups, detached);
            if (evidence != null) evidence.resolve(heads, bindings, lanes, byId);
            return new Capture(
                    source,
                    new Statistics(
                            count,
                            bound,
                            bodies,
                            shaftOwners,
                            columns.size(),
                            groups.size(),
                            bridges,
                            hooks,
                            count - bound,
                            rivals,
                            "",
                            geometryWork,
                            flagCandidates,
                            detached.size(),
                            flagBudget.used(),
                            flagRefused),
                    List.copyOf(diagnostics),
                    List.copyOf(columnDiagnostics));
        } catch (Limit | FaintQuarterRestInk.Refusal exhausted) {
            return failed(
                    count,
                    bound,
                    bodies,
                    shaftOwners,
                    columns.size(),
                    rivals,
                    "budget",
                    geometryWork,
                    diagnostics);
        } catch (UnknownCarrier uncertain) {
            return failed(
                    count,
                    bound,
                    bodies,
                    shaftOwners,
                    columns.size(),
                    rivals,
                    "unknown thin-rule carrier: " + uncertain.getMessage(),
                    geometryWork,
                    diagnostics);
        } catch (IllegalArgumentException invalid) {
            return failed(
                    count,
                    bound,
                    bodies,
                    shaftOwners,
                    columns.size(),
                    rivals,
                    "invalid context: " + invalid.getMessage(),
                    geometryWork,
                    diagnostics);
        }
    }

    /** Diagnostic copies never retain page-owned certificate/frame/raw authority. */
    static PrintedBeamGeometry.Column diagnosticColumn(
            PrintedBeamGeometry.Column column, Work work) {
        // Bound the clone/list/constructor metadata before allocating it.
        work.take(column.shaftColumns().size() + 32L);
        return new PrintedBeamGeometry.Column(
                column.id(),
                column.measure(),
                column.staff(),
                column.staffCount(),
                column.direction(),
                column.left(),
                column.right(),
                column.cap(),
                column.gap(),
                column.maximumRails(),
                column.shaftColumns(),
                column.shaftStart());
    }

    private static float glyphScale(ScoreNoteEvent note) {
        return (note.articulations() & NoteOrnament.GRACE) != 0
                ? .65f
                : note.visualSize() == ScoreNoteEvent.VisualSize.CUE ? .7f : 1f;
    }

    private static Capture failed(
            int n,
            int bound,
            int bodies,
            int owners,
            int columns,
            int rivals,
            String why,
            long work,
            List<Map<String, Object>> diagnostics) {
        return new Capture(
                ScoreBeamSource.empty(n),
                new Statistics(
                        n, bound, bodies, owners, columns, 0, 0, 0, n - bound, rivals, why, work, 0,
                        0, 0, false),
                List.copyOf(diagnostics),
                List.of());
    }

    private static List<Candidate> qualify(
            List<PrintedBeamShafts.Shaft> shafts,
            byte[] raw,
            int width,
            int height,
            PrintedSlurOwners.Location head,
            float gap,
            Work work,
            Binding binding,
            int owner,
            PrintedBeamShaftPixels.Frame frame) {
        work.take(shafts.size() + 32L);
        var result = new ArrayList<Candidate>();
        for (var shaft : shafts) {
            work.take(24);
            if (shaft.pixels() == null || shaft.pixels().frame != frame)
                throw new PrintedBeamShaftPixels.IdentityFault();
            int[] point = new int[2];
            if (!headContact(raw, width, height, head, shaft, gap, work, binding, point)) continue;
            var token = new Object();
            var pixels =
                    PrintedBeamShaftPixels.certify(
                            frame, token, binding, owner, shaft, point[0], point[1]);
            result.add(new Candidate(owner, binding, shaft, gap, pixels, shaft));
        }
        return result;
    }

    private static List<Candidate> preserveCores(
            List<Candidate> cores,
            List<Candidate> faint,
            List<PrintedBeamShafts.Shaft> envelopes,
            Work work) {
        work.take(cores.size() + faint.size() * 2L + 64);
        var result = new ArrayList<Candidate>();
        boolean[] used = new boolean[faint.size()];
        for (var core : cores) {
            PrintedBeamShafts.Shaft matched = null;
            for (var envelope : envelopes)
                if (PrintedBeamShaftPixels.covers(envelope, core.shaft())) {
                    if (matched != null)
                        throw new IllegalArgumentException("Ambiguous core envelope");
                    matched = envelope;
                }
            if (matched != null) {
                for (int i = 0; i < faint.size(); i++)
                    if (faint.get(i).shaft() == matched) used[i] = true;
                work.take(16);
                result.add(
                        new Candidate(
                                core.owner(),
                                core.binding(),
                                core.shaft(),
                                core.glyphGap(),
                                core.pixels(),
                                matched));
            } else result.add(core);
        }
        for (int i = 0; i < faint.size(); i++) if (!used[i]) result.add(faint.get(i));
        return result;
    }

    private static int root(int[] p, int i) {
        while (p[i] != i) {
            p[i] = p[p[i]];
            i = p[i];
        }
        return i;
    }

    private static boolean shared(Candidate a, Candidate b, List<ScoreNoteEvent> notes) {
        if (a.binding().lane().id() != b.binding().lane().id()
                || notes.get(a.owner()).measureIndex() != notes.get(b.owner()).measureIndex()
                || a.shaft().direction() != b.shaft().direction()) return false;
        // Both individually authenticated head-to-cap paths must share an actual observed terminal
        // pixel.
        for (var x : a.shaft().columns())
            for (var y : b.shaft().columns())
                if (x.x() == y.x() && x.endY() == y.endY()) return true;
        return false;
    }

    private static boolean wideBody(
            byte[] raw, int w, int h, PrintedSlurOwners.Location head, float gap) {
        int l = Math.max(0, (int) Math.floor(head.x() - gap * .8f)),
                r = Math.min(w - 1, (int) Math.ceil(head.x() + gap * .8f)),
                rows = 0;
        for (int y = Math.max(0, Math.round(head.y() - gap * .45f));
                y < h && y <= head.y() + gap * .45f;
                y++) {
            if ((raw[y * w + l] & 255) < 165 || (raw[y * w + r] & 255) < 165) continue;
            int first = r + 1, last = l - 1, dark = 0;
            for (int x = l; x <= r; x++)
                if ((raw[y * w + x] & 255) < 165) {
                    first = Math.min(first, x);
                    last = x;
                    dark++;
                }
            if (last - first + 1 >= gap * .5f
                    && last - first + 1 <= gap * 1.8f
                    && dark >= Math.max(3, Math.ceil(gap * .25f))) rows++;
        }
        return rows >= Math.max(2, Math.ceil(gap * .18f));
    }

    private static boolean headContact(
            byte[] raw,
            int w,
            int h,
            PrintedSlurOwners.Location head,
            PrintedBeamShafts.Shaft shaft,
            float gap,
            Work budget,
            Binding context) {
        return headContact(raw, w, h, head, shaft, gap, budget, context, null);
    }

    private static boolean headContact(
            byte[] raw,
            int w,
            int h,
            PrintedSlurOwners.Location head,
            PrintedBeamShafts.Shaft shaft,
            float gap,
            Work budget,
            Binding context,
            int[] point) {
        boolean faintMode = shaft.pixels() != null && shaft.pixels().contrast;
        int l = Math.max(0, (int) Math.floor(Math.min(head.x() - gap * .8f, shaft.left()))),
                r = Math.min(w - 1, (int) Math.ceil(Math.max(head.x() + gap * .8f, shaft.right())));
        int t = Math.max(0, (int) Math.floor(head.y() - gap * .65f)),
                b = Math.min(h - 1, (int) Math.ceil(head.y() + gap * .65f));
        int cw = r - l + 1, ch = b - t + 1;
        if (cw < 1 || ch < 1) return false;
        budget.take((long) cw * ch * 9);
        boolean[] seen = new boolean[cw * ch];
        int[] queue = new int[cw * ch];
        int from = 0, to = 0;
        var bodyRows = new HashMap<Integer, int[]>();
        int bl = Math.max(0, (int) Math.floor(head.x() - gap * .8f)),
                br = Math.min(w - 1, (int) Math.ceil(head.x() + gap * .8f));
        for (int yy = t; yy <= b; yy++)
            if ((raw[yy * w + bl] & 255) >= 165 && (raw[yy * w + br] & 255) >= 165) {
                int first = br + 1, last = bl - 1, dark = 0;
                for (int xx = bl; xx <= br; xx++)
                    if ((raw[yy * w + xx] & 255) < 165) {
                        first = Math.min(first, xx);
                        last = xx;
                        dark++;
                    }
                if (last - first + 1 >= gap * .5f
                        && last - first + 1 <= gap * 1.8f
                        && dark >= Math.max(3, Math.ceil(gap * .25f)))
                    bodyRows.put(yy, new int[] {first, last});
            }
        java.util.function.BiPredicate<Integer, Integer> ownedBody =
                (x, y) -> {
                    var band = bodyRows.get(y);
                    return band != null && x >= band[0] && x <= band[1];
                };
        var witnesses = new HashMap<Integer, Optional<PrintedBeamRuleWitness.Witness>>();
        java.util.function.BiPredicate<Integer, Integer> carrier =
                (x, y) -> {
                    var at = witnesses.get(x);
                    if (at == null) {
                        float bottom = context.bottom(), staffGap = context.gap();
                        if (context.lane().track() != null
                                && context.lane().track().verifiedAt(x)) {
                            var local = context.lane().track().at(x);
                            bottom = local[0];
                            staffGap = local[1];
                        }
                        budget.take((long) Math.ceil(staffGap * 10));
                        at =
                                Optional.ofNullable(
                                        faintMode
                                                ? PrintedBeamContrastWitness.at(
                                                        raw,
                                                        w,
                                                        h,
                                                        x,
                                                        bottom,
                                                        staffGap,
                                                        budget::take)
                                                : PrintedBeamRuleWitness.at(
                                                        raw, w, h, x, bottom, staffGap));
                        witnesses.put(x, at);
                    }
                    if (at.isPresent()) return at.get().contains(y);
                    if (faintMode && (raw[y * w + x] & 255) >= 165) {
                        float bottom = context.bottom(), staffGap = context.gap();
                        if (context.lane().track() != null
                                && context.lane().track().verifiedAt(x)) {
                            budget.take(16);
                            var local = context.lane().track().at(x);
                            bottom = local[0];
                            staffGap = local[1];
                        }
                        double top = bottom - 4 * staffGap;
                        int line = (int) Math.round((y - top) / staffGap);
                        if (line >= 0
                                && line <= 4
                                && Math.abs(y - top - line * staffGap) <= staffGap * .35)
                            return true; // UNKNOWN blocks the same unowned path as carrier ink.
                    }
                    return false;
                };
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++)
                if (Math.abs(x - head.x()) <= gap * .35f
                        && Math.abs(y - head.y()) <= gap * .35f
                        && ((raw[y * w + x] & 255) < 165
                                || faintMode
                                        && (headForeground(raw, w, h, x, y, gap, budget)
                                                || savedPixel(shaft, raw, w, h, x, y)))
                        && (!carrier.test(x, y) || ownedBody.test(x, y))) {
                    int at = (y - t) * cw + x - l;
                    seen[at] = true;
                    queue[to++] = at;
                }
        while (from < to) {
            int at = queue[from++], x = l + at % cw, y = t + at / cw;
            for (var col : shaft.columns())
                if (x == col.x()
                        && (shaft.direction() > 0
                                ? y >= shaft.startY() && y <= col.endY()
                                : y <= shaft.startY() && y >= col.endY())) {
                    if (point != null) {
                        point[0] = x;
                        point[1] = y;
                    }
                    return true;
                }
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (dx == 0 && dy == 0
                            || nx < l
                            || nx > r
                            || ny < t
                            || ny > b
                            || !((raw[ny * w + nx] & 255) < 165
                                    || faintMode
                                            && (headForeground(raw, w, h, nx, ny, gap, budget)
                                                    || savedPixel(shaft, raw, w, h, nx, ny))))
                        continue;
                    boolean spine = false;
                    for (var col : shaft.columns())
                        if (nx == col.x()
                                && (shaft.direction() > 0
                                        ? ny >= shaft.startY() && ny <= col.endY()
                                        : ny <= shaft.startY() && ny >= col.endY())) spine = true;
                    if (!spine && carrier.test(nx, ny) && !ownedBody.test(nx, ny)) continue;
                    int next = (ny - t) * cw + nx - l;
                    if (!seen[next]) {
                        seen[next] = true;
                        queue[to++] = next;
                    }
                }
        }
        return false;
    }

    private static final class BindingAttempt {
        boolean ambiguous;
    }

    private static Binding bind(
            PrintedSlurOwners.GeometryHead head,
            ScoreNoteEvent note,
            List<Lane> lanes,
            byte[] raw,
            int w,
            int h,
            Work budget,
            FaintQuarterRestInk.Budget contextBudget,
            ScorePageInterpretation score,
            PrintedBeamShaftPixels.Frame pixelFrame,
            int page) {
        // Covers the additive attempt result and constant wrapper work, before allocation.
        FaintQuarterRestInk.charge(contextBudget, 64, 64);
        var ambiguity = new BindingAttempt();
        Binding old = bindLegacy(head, note, lanes, raw, w, h, budget, ambiguity);
        if (old != null || ambiguity.ambiguous) return old;
        Binding found = null;
        for (var lane : lanes) {
            var staff = lane.staff();
            if (staff.index() != note.staffIndex() || staff.count() != note.staffCount()) continue;
            FaintQuarterRestInk.charge(contextBudget, 64, 0);
            if (!PrintedBeamBindingRange.possible(
                    head.y(),
                    note.staffStep(),
                    staff.bottom(),
                    staff.gap(),
                    staff.gap() * .8f,
                    staff.gap() * .12f,
                    .45f)) continue;

            PrintedBeamLocalFrame.Witness frame;
            try {
                frame =
                        PrintedBeamLocalFrame.at(
                                pixelFrame,
                                score,
                                raw,
                                w,
                                h,
                                page,
                                head,
                                note,
                                staff,
                                contextBudget);
            } catch (FaintQuarterRestInk.Refusal refused) {
                throw new Limit();
            }
            if (frame == null) continue;
            if (!frame.matches(
                    pixelFrame, score, raw, w, h, page, head, note, staff, contextBudget))
                throw new PrintedBeamShaftPixels.IdentityFault();
            if (found != null) return null;
            FaintQuarterRestInk.charge(contextBudget, 16, 64);
            found = new Binding(lane, frame.bottom(), frame.gap());
        }

        return found;
    }

    // Retained default entry: identical float arithmetic, acceptance and work prices.
    private static Binding bind(
            PrintedSlurOwners.Location head,
            ScoreNoteEvent note,
            List<Lane> lanes,
            byte[] raw,
            int w,
            int h,
            Work budget) {
        return bindLegacy(head, note, lanes, raw, w, h, budget, null);
    }

    private static Binding bindLegacy(
            PrintedSlurOwners.Location head,
            ScoreNoteEvent note,
            List<Lane> lanes,
            byte[] raw,
            int w,
            int h,
            Work budget,
            BindingAttempt ambiguity) {
        Binding found = null;
        for (var lane : lanes) {
            var s = lane.staff();
            if (s.index() != note.staffIndex() || s.count() != note.staffCount()) continue;
            float bottom, gap;
            if (lane.track() != null && lane.track().verifiedAt(head.x())) {
                var at = lane.track().at(head.x());
                bottom = at[0];
                gap = at[1];
            } else {
                PrintedBeamRuleWitness.Witness left = null, right = null;
                int lx = 0, rx = 0;
                for (int d = 1; d <= 16 && (left == null || right == null); d++) {
                    int step = Math.max(1, Math.round(s.gap() * .8f * d));
                    int a = Math.round(head.x()) - step, b = Math.round(head.x()) + step;
                    budget.take((long) Math.ceil(s.gap() * 12));
                    if (left == null && a >= 0) {
                        left =
                                PrintedBeamContrastWitness.at(
                                        raw, w, h, a, s.bottom(), s.gap(), budget::take);
                        if (left != null) lx = a;
                    }
                    if (right == null && b < w) {
                        right =
                                PrintedBeamContrastWitness.at(
                                        raw, w, h, b, s.bottom(), s.gap(), budget::take);
                        if (right != null) rx = b;
                    }
                }
                if (left == null || right == null) continue;
                // Preserve the established float offset arithmetic and its work path
                // whenever the supplied spacing already fits both observed witnesses.
                double loL = Double.NEGATIVE_INFINITY,
                        hiL = Double.POSITIVE_INFINITY,
                        loR = loL,
                        hiR = hiL;
                for (int line = 0; line < 5; line++) {
                    var a = left.rules().get(line);
                    var b = right.rules().get(line);
                    double offset = (4 - line) * s.gap();
                    loL = Math.max(loL, a.top() - .5 + offset);
                    hiL = Math.min(hiL, a.bottom() + .5 + offset);
                    loR = Math.max(loR, b.top() - .5 + offset);
                    hiR = Math.min(hiR, b.bottom() + .5 + offset);
                }
                double f = (head.x() - lx) / (rx - lx);
                if (loL <= hiL && loR <= hiR) {
                    if (Math.abs((loL + hiL - loR - hiR) * .5) > (rx - lx) * .2) continue;
                    bottom = (float) (((loL + hiL) * .5) * (1 - f) + ((loR + hiR) * .5) * f);
                    gap = s.gap();
                } else {
                    budget.take(128);
                    var frame = PrintedBeamRuleFrame.fit(left, right, s.gap());
                    if (frame == null
                            || Math.abs(frame.leftBottom() - frame.rightBottom()) > (rx - lx) * .2)
                        continue;
                    bottom = (float) (frame.leftBottom() * (1 - f) + frame.rightBottom() * f);
                    gap = (float) frame.gap();
                }
            }
            if (Math.abs(head.y() - (bottom - note.staffStep() * gap * .5f)) > gap * .45f) continue;
            if (found != null) {
                if (ambiguity != null) ambiguity.ambiguous = true;
                return null;
            }
            found = new Binding(lane, bottom, gap);
        }
        return found;
    }

    private static boolean savedPixel(
            PrintedBeamShafts.Shaft shaft, byte[] raw, int w, int h, int x, int y) {
        var scan = shaft.pixels();
        if (scan == null || !scan.matches(shaft)) return false;
        scan.frame.check(raw, w, h);
        return scan.has(x, y);
    }

    private static boolean headForeground(
            byte[] raw, int w, int h, int x, int y, double gap, Work work) {
        return (raw[y * w + x] & 255) < 165
                || PrintedBeamRawContrast.neck(raw, w, h, x, y, gap, work::take)
                || PrintedBeamRawContrast.faint(raw, w, h, x, y, gap, false, work::take);
    }

    private static boolean foreground(
            byte[] raw, int w, int h, int x, int y, double gap, Work work) {
        return (raw[y * w + x] & 255) < 165
                || PrintedBeamRawContrast.faint(raw, w, h, x, y, gap, false, work::take);
    }

    private static int carrier(
            Node a, Node b, int x, int y, byte[] labels, byte[] raw, int w, int h, Work work) {
        var lane = a.binding.lane();
        if (lane.id() != b.binding.lane().id()) return 0;
        boolean verified = lane.track() != null && lane.track().verifiedAt(x);
        var sa = a.candidates.get(0).shaft();
        var sb = b.candidates.get(0).shaft();
        double ax = (sa.left() + sa.right()) * .5,
                bx = (sb.left() + sb.right()) * .5,
                f = Math.abs(bx - ax) < 1e-6 ? 0 : (x - ax) / (bx - ax);
        double gap = a.binding.gap() + (b.binding.gap() - a.binding.gap()) * f,
                bottom = a.binding.bottom() + (b.binding.bottom() - a.binding.bottom()) * f;
        long context = ((long) a.id << 38) | ((long) b.id << 25) | x;
        if (verified && (raw[y * w + x] & 255) < 165) return lane.ink().sample(x, y) >= 165 ? 1 : 0;
        if (verified) {
            work.take(16);
            var at = lane.track().at(x);
            bottom = at[0];
            gap = at[1];
        }
        var cached = lane.witnesses().get(context);
        if (cached == null) {
            work.take((long) Math.ceil(gap * 10));
            cached =
                    Optional.ofNullable(
                            PrintedBeamContrastWitness.at(raw, w, h, x, bottom, gap, work::take));
            lane.witnesses().put(context, cached);
        }
        var witness = cached.orElse(null);
        if (witness != null) return witness.contains(y) ? 1 : 0;
        var localInk = lane.localInks().get(context);
        if (localInk == null) {
            localInk =
                    new PrintedBeamLocalRuleInk(
                            null, raw, w, h, (float) bottom, (float) gap, work::take);
            lane.localInks().put(context, localInk);
        }
        if (localInk.carrier(x, y)) return 1;
        if ((raw[y * w + x] & 255) < 165 && localInk.provedAt(x, y)) return 0;
        // Unproved plausible rule pixels are UNKNOWN even inside a thick co-owned component.
        // They cannot subsidize a selected strip's non-carrier core or its terminal contact.
        if (foreground(raw, w, h, x, y, gap, work)) {
            double top = bottom - 4 * gap;
            int line = (int) Math.round((y - top) / gap);
            if (line >= 0 && line <= 4 && Math.abs(y - top - line * gap) <= gap * .35) return -1;
        }
        return 0;
    }

    private static int carrierStrict(
            Node a, Node b, int x, int y, byte[] labels, byte[] raw, int w, int h, Work work) {
        var lane = a.binding.lane();
        if (lane.id() != b.binding.lane().id()) return 0;
        boolean verified = lane.track() != null && lane.track().verifiedAt(x);
        var sa = a.candidates.get(0).shaft();
        var sb = b.candidates.get(0).shaft();
        double ax = (sa.left() + sa.right()) * .5,
                bx = (sb.left() + sb.right()) * .5,
                f = Math.abs(bx - ax) < 1e-6 ? 0 : (x - ax) / (bx - ax);
        double gap = a.binding.gap() + (b.binding.gap() - a.binding.gap()) * f,
                bottom = a.binding.bottom() + (b.binding.bottom() - a.binding.bottom()) * f;
        long context = Long.MIN_VALUE | ((long) a.id << 38) | ((long) b.id << 25) | x;
        if (verified) return (raw[y * w + x] & 255) < 165 && lane.ink().sample(x, y) >= 165 ? 1 : 0;
        var cached = lane.witnesses().get(context);
        if (cached == null) {
            work.take((long) Math.ceil(gap * 10));
            cached = Optional.ofNullable(PrintedBeamRuleWitness.at(raw, w, h, x, bottom, gap));
            lane.witnesses().put(context, cached);
        }
        var witness = cached.orElse(null);
        if (witness != null) return witness.contains(y) ? 1 : 0;
        var localInk = lane.localInks().get(context);
        if (localInk == null) {
            localInk =
                    new PrintedBeamLocalRuleInk(
                            null, raw, w, h, (float) bottom, (float) gap, work::take);
            lane.localInks().put(context, localInk);
        }
        if (localInk.carrier(x, y)) return 1;
        if (localInk.provedAt(x, y)) return 0;
        // Unproved plausible rule pixels are UNKNOWN even inside a thick co-owned component.
        // They cannot subsidize a selected strip's non-carrier core or its terminal contact.
        if ((raw[y * w + x] & 255) < 165) {
            double top = bottom - 4 * gap;
            int line = (int) Math.round((y - top) / gap);
            if (line >= 0 && line <= 4 && Math.abs(y - top - line * gap) <= gap * .35) return -1;
        }
        return 0;
    }
}
