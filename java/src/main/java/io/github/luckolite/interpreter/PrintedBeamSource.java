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
            int owner, Binding binding, PrintedBeamShafts.Shaft shaft, float glyphGap) {}

    private static final class Node {
        final int id, measure;
        final Binding binding;
        final List<Candidate> candidates = new ArrayList<>();
        final List<Integer> owners = new ArrayList<>();
        boolean rival;

        Node(int id, int measure, Binding binding) {
            this.id = id;
            this.measure = measure;
            this.binding = binding;
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
        if (score == null || physicalPage < 0) return score;
        if (!score.beamSource().groups().isEmpty()
                || !score.beamSource().detachedColumns().isEmpty()) return score;
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
                        256_000_000);
        return result.statistics().refusal().isEmpty()
                ? score.withBeamSource(result.source())
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
            var shaftBudget = new PrintedBeamShafts.Budget(shaftAllowance);
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
                Binding binding = bind(head, note, lanes, gray, width, height, work);
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
                var shafts =
                        PrintedBeamShafts.at(
                                gray, width, height, head.x(), head.y(), glyphGap, shaftBudget);
                if (shaftBudget.refused()) throw new Limit();
                for (var shaft : shafts)
                    if (headContact(gray, width, height, head, shaft, glyphGap, work, binding)) {
                        candidates.add(new Candidate(owner, binding, shaft, glyphGap));
                        choices[owner]++;
                    }
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
                if (choices[c.owner()] == 1) node.owners.add(c.owner());
                else node.rival = true;
            }
            var byId = new HashMap<Integer, Node>();
            for (var node : nodes.values()) {
                var first = node.candidates.get(0);
                var shaft = first.shaft();
                float gap =
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
                    int low = Math.min(shaft.startY(), shaft.endY()),
                            high = Math.max(shaft.startY(), shaft.endY());
                    if (head.y() < low - gap
                            || head.y() > high + gap
                            || head.x() < shaft.left() - gap
                            || head.x() > shaft.right() + gap) continue;
                    if (headContact(
                            gray,
                            width,
                            height,
                            head,
                            shaft,
                            bindings[head.owner()] == null
                                    ? gap
                                    : bindings[head.owner()].gap() * glyphScale(n),
                            work,
                            bindings[head.owner()] == null
                                    ? node.binding
                                    : bindings[head.owner()])) {
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
                for (var c : node.candidates) {
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
                                start);
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
                            geometryBudget);
            geometryWork = result.work();
            if (result.budgetExceeded()) throw new Limit();
            var unlinkedIds = new HashSet<>(result.unlinkedColumns());
            var diagnosticBudget = new Work(128_000_000);
            for (var column : columns) {
                var node = byId.get(column.id());
                var row = new LinkedHashMap<String, Object>();
                row.put("column", column);
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
        } catch (Limit exhausted) {
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
                                        PrintedBeamRuleWitness.at(raw, w, h, x, bottom, staffGap));
                        witnesses.put(x, at);
                    }
                    return at.isPresent() && at.get().contains(y);
                };
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++)
                if (Math.abs(x - head.x()) <= gap * .35f
                        && Math.abs(y - head.y()) <= gap * .35f
                        && (raw[y * w + x] & 255) < 165
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
                                : y <= shaft.startY() && y >= col.endY())) return true;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (dx == 0 && dy == 0
                            || nx < l
                            || nx > r
                            || ny < t
                            || ny > b
                            || (raw[ny * w + nx] & 255) >= 165) continue;
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

    private static Binding bind(
            PrintedSlurOwners.Location head,
            ScoreNoteEvent note,
            List<Lane> lanes,
            byte[] raw,
            int w,
            int h,
            Work budget) {
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
                        left = PrintedBeamRuleWitness.at(raw, w, h, a, s.bottom(), s.gap());
                        if (left != null) lx = a;
                    }
                    if (right == null && b < w) {
                        right = PrintedBeamRuleWitness.at(raw, w, h, b, s.bottom(), s.gap());
                        if (right != null) rx = b;
                    }
                }
                if (left == null || right == null) continue;
                // Witness pixels bound phase for the supplied quantized gap; no verified track is
                // fabricated.
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
                if (loL > hiL
                        || loR > hiR
                        || Math.abs((loL + hiL - loR - hiR) * .5) > (rx - lx) * .2) continue;
                double f = (head.x() - lx) / (rx - lx);
                bottom = (float) (((loL + hiL) * .5) * (1 - f) + ((loR + hiR) * .5) * f);
                gap = s.gap();
            }
            if (Math.abs(head.y() - (bottom - note.staffStep() * gap * .5f)) > gap * .45f) continue;
            if (found != null) return null;
            found = new Binding(lane, bottom, gap);
        }
        return found;
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
            localInk = new PrintedBeamLocalRuleInk(null, raw, w, h, (float) bottom, (float) gap);
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
