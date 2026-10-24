// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Recover a bracketed Three over a separately bound curve without altering original raster samples. */
final class JoinedTupletNumeral {
    enum Status {
        SUPPORTED,
        UNKNOWN,
        RESOURCE_UNKNOWN
    }

    record Glyph(int left, int top, int right, int bottom) {}

    record Result(
            Status status,
            String reason,
            Glyph glyph,
            int frames,
            int curves,
            int boundCurves,
            int components,
            int sharedPixels,
            int rawCandidates,
            int pathStates,
            int completedPaths,
            int rawComponents,
            int proposals,
            int fringeProofs,
            boolean completeForegroundOwnership,
            long work) {}

    private static final int MAX_PAGE = 20000000,
            MAX_WINDOW = 16384,
            MAX_CURVE = 65536,
            MAX_COMPONENTS = 64;

    private static final class Exhausted extends RuntimeException {
        Exhausted(String reason) {
            super(reason);
        }
    }

    private static final class Budget implements ObservedFringeContinuation.Charge {
        final long limit;
        long used;

        Budget(long limit) {
            this.limit = limit;
        }

        public void tick() {
            if (++used > limit) throw new Exhausted("operation-budget");
        }
    }

    /** No image copy or synthetic paper: shape predicates sample original unmasked raw values. */
    private record MemberInk(byte[] gray, int width, int height, Budget budget) {
        boolean dark(int x, int y) {
            budget.tick();
            return x >= 0
                    && y >= 0
                    && x < width
                    && y < height
                    && (gray[y * width + x] & 255) <= 165;
        }
    }

    private record FringeKey(Glyph box, BitSet curveProjection) {
        FringeKey {
            curveProjection = (BitSet) curveProjection.clone();
        }
    }

    private record TerminalKey(Glyph box, BitSet exactCurve) {
        // Curve is fully built then readonly; identity retains actual path, not equal-value
        // evidence.
        @Override
        public boolean equals(Object other) {
            return other instanceof TerminalKey key
                    && box.equals(key.box)
                    && exactCurve == key.exactCurve;
        }

        @Override
        public int hashCode() {
            return 31 * box.hashCode() + System.identityHashCode(exactCurve);
        }
    }

    // C reads include the complete box, contact ring and bounded top-cap AA rays.
    // Source, graph window, staff gap and raw raster are fixed in this per-frame cache.
    // Full-curve terminal/fringe keys and final exposed-curve ambiguity remain separate.
    record TerminalFootprint(Glyph box, BitSet allCurveSamples) {
        TerminalFootprint {
            allCurveSamples = (BitSet) allCurveSamples.clone();
        }
    }

    static TerminalFootprint terminalFootprint(
            Glyph box,
            BitSet curve,
            int width,
            int height,
            float gap,
            ObservedFringeContinuation.Charge work) {
        if (box == null
                || curve == null
                || work == null
                || width < 1
                || height < 1
                || !Float.isFinite(gap)
                || gap < 5
                || gap > 100
                || box.left() < 0
                || box.top() < 0
                || box.right() >= width
                || box.bottom() >= height
                || box.left() > box.right()
                || box.top() > box.bottom()
                || (long) (box.right() - box.left() + 1) * (box.bottom() - box.top() + 1) > 16384)
            throw new IllegalArgumentException("terminal footprint");
        int margin = Math.max(2, Math.round(gap * .30f));
        int l = Math.max(0, box.left() - margin), r = Math.min(width - 1, box.right() + margin);
        int t = Math.max(0, box.top() - margin),
                b = Math.min(height - 1, box.bottom() + margin),
                w = r - l + 1;
        for (int i = 0; i < ((w * (b - t + 1) + 63) / 64) * 2 + 8; i++) work.tick();
        BitSet retained = new BitSet(w * (b - t + 1));
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                work.tick();
                if (curve.get(y * width + x)) retained.set((y - t) * w + x - l);
            }
        return new TerminalFootprint(box, retained);
    }

    record NumeralMeaning(int digit, int first, int middle, int last, Glyph completeGlyph) {}

    private record TurnWinner(
            int attempt, Glyph box, BitSet exactCurve, FilledTurnRibbon.Proof proof) {}

    private static final class State {
        int frames,
                curves,
                bound,
                components,
                shared,
                rawCandidates,
                pathStates,
                completedPaths,
                rawComponents,
                proposals,
                fringeProofs;
        boolean competing;
        int roleStates, terminalAttempts;
        boolean numeralConflict;
        TurnWinner lastScanTurn, chosenTurn;

        void newRoleState() {
            if (++roleStates + pathStates > 4096) throw new Exhausted("joint-role-state-cap");
        }

        void newKey() {
            if (++components > MAX_COMPONENTS) throw new Exhausted("component-work-cap");
        }

        void newRawComponent() {
            newKey();
            rawComponents++;
        }

        Result result(Status status, String why, Glyph glyph, Budget b) {
            return new Result(
                    status,
                    why,
                    glyph,
                    frames,
                    curves,
                    bound,
                    components,
                    shared,
                    rawCandidates,
                    pathStates,
                    completedPaths,
                    rawComponents,
                    proposals,
                    fringeProofs,
                    false,
                    b.used);
        }
    }

    static Result inspect(
            byte[] gray,
            int width,
            int height,
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            int first,
            int middle,
            int last,
            long operationBudget) {
        JoinedTupletObservation.begin(width, height, first, middle, last);
        try {
            Result result =
                    inspectCore(
                            gray,
                            width,
                            height,
                            notes,
                            measures,
                            first,
                            middle,
                            last,
                            operationBudget);
            JoinedTupletObservation.end(result.status().ordinal(), result.reason());
            return result;
        } catch (RuntimeException | Error failure) {
            JoinedTupletObservation.end(-1, failure.getClass().getName());
            throw failure;
        }
    }

    private static Result inspectCore(
            byte[] gray,
            int width,
            int height,
            List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,
            int first,
            int middle,
            int last,
            long operationBudget) {
        Budget budget = new Budget(operationBudget);
        State state = new State();
        if (operationBudget < 1)
            return state.result(Status.RESOURCE_UNKNOWN, "budget", null, budget);
        if (gray == null
                || width < 1
                || height < 1
                || (long) width * height > MAX_PAGE
                || gray.length != (long) width * height
                || notes == null
                || measures == null
                || notes.size() > 20000
                || measures.size() > 5000
                || first < 0
                || middle <= first
                || last <= middle
                || last >= notes.size())
            return state.result(Status.UNKNOWN, "invalid-input-or-group", null, budget);
        try {
            var a = notes.get(first);
            var m = notes.get(middle);
            var z = notes.get(last);
            for (var n : List.of(a, m, z))
                if (n.measureIndex() != a.measureIndex()
                        || n.staffIndex() != a.staffIndex()
                        || n.staffCount() != a.staffCount()
                        || n.beamCount() != a.beamCount()
                        || n.beamCount() < 1
                        || n.tupletDivisor() != 1
                        || n.tupletNormalNotes() != 1
                        || n.augmentationDots() != 0
                        || (n.articulations() & 32768) != 0
                        || Float.floatToRawIntBits(n.unbeamedDurationBeats())
                                != Float.floatToRawIntBits(a.unbeamedDurationBeats()))
                    return state.result(Status.UNKNOWN, "group-contract", null, budget);
            if (a.measureIndex() < 0 || a.measureIndex() >= measures.size())
                return state.result(Status.UNKNOWN, "missing-measure", null, budget);
            var bar = measures.get(a.measureIndex());
            var heads = PrintedSlurContext.geometryHeads(notes, measures, width, height);
            var ah = heads.get(first);
            var mh = heads.get(middle);
            var zh = heads.get(last);
            if (!(ah.x() < mh.x() && mh.x() < zh.x()))
                return state.result(Status.UNKNOWN, "head-order", null, budget);
            // Actual raw five-rule observations; frame preparation proves tracks and predicted
            // heads.
            var inside = new ArrayList<RawStaffLineDetector.StaffLines>();
            for (var staff : RawStaffLineDetector.detect(gray, width, height)) {
                budget.tick();
                if (staff.center() >= bar.top() * height && staff.center() <= bar.bottom() * height)
                    inside.add(staff);
            }
            inside.sort(Comparator.comparingInt(RawStaffLineDetector.StaffLines::top));
            if (inside.size() != a.staffCount()
                    || a.staffIndex() < 0
                    || a.staffIndex() >= inside.size())
                return state.result(Status.UNKNOWN, "physical-staff-count", null, budget);
            if (inside.size() > 512) throw new Exhausted("staff-frame-cap");
            var staffs = new ArrayList<PlayingTechniqueDetector.Staff>();
            for (int i = 0; i < inside.size(); i++) {
                var staff = inside.get(i);
                staffs.add(
                        new PlayingTechniqueDetector.Staff(
                                staff.top(), staff.bottom(), staff.gap(), i, inside.size()));
            }
            var prepared = PrintedSlurFrames.prepare(gray, width, height, staffs, notes, heads);
            state.frames = prepared.frames().size();
            if (prepared.unboundOwners().contains(first)
                    || prepared.unboundOwners().contains(middle)
                    || prepared.unboundOwners().contains(last))
                return state.result(Status.UNKNOWN, "predicted-head-track-mismatch", null, budget);
            Glyph winner = null;
            BitSet winnerExposed = null;
            NumeralMeaning winnerMeaning = null;
            for (var frame : prepared.frames()) {
                if (frame.heads().stream().noneMatch(h -> h.owner() == first)
                        || frame.heads().stream().noneMatch(h -> h.owner() == last)) continue;
                float gap = frame.track().at((ah.x() + zh.x()) * .5f)[1];
                int left = Math.max(frame.left(), (int) Math.floor(ah.x() - 2 * gap));
                int right = Math.min(frame.right(), (int) Math.ceil(zh.x() + 2 * gap));
                long area = (long) (right - left + 1) * (frame.bottom() - frame.top() + 1);
                if (area < 1 || area > MAX_PAGE) throw new Exhausted("curve-scan-area");
                // Original core graph is acquired once. No per-curve rediscovery/duplicate
                // component charges.
                float centre = (ah.x() + zh.x()) * .5f;
                int graphLeft = Math.max(0, Math.round(centre - 2.1f * gap));
                int graphRight = Math.min(width - 1, Math.round(centre + 2.1f * gap));
                int graphTop = Math.max(0, Math.round(Math.min(ah.y(), zh.y()) - 8 * gap));
                int graphBottom = Math.min(height - 1, Math.round(Math.min(ah.y(), zh.y()) - gap));
                long graphArea = (long) (graphRight - graphLeft + 1) * (graphBottom - graphTop + 1);
                if (graphArea < 1 || graphArea > MAX_WINDOW)
                    throw new Exhausted("glyph-window-cap");
                var graph =
                        RawNumeralComponents.capture(
                                gray,
                                width,
                                height,
                                graphLeft,
                                graphTop,
                                graphRight,
                                graphBottom,
                                budget,
                                state::newRawComponent);
                var proposalCache = new HashMap<Glyph, Boolean>();
                var fringeCache = new HashMap<FringeKey, ObservedFringeContinuation.Assessment>();
                // Source/frame/owners fixed by this call+frame scope; fullC includes in-box
                // contact.
                var terminalCache = new HashMap<TerminalKey, JointTerminalThree.Outcome>();
                var footprintCache = new HashMap<TerminalFootprint, JointTerminalThree.Outcome>();
                var exactFringeCache =
                        new HashMap<TerminalKey, ObservedFringeContinuation.Assessment>();
                var turnAttempts = new HashMap<TerminalKey, Integer>();
                JoinedTupletObservation.event("PHASE_ORDINARY");
                Glyph ordinary =
                        scan(
                                gray,
                                width,
                                height,
                                ah,
                                zh,
                                gap,
                                new BitSet(),
                                graph,
                                proposalCache,
                                fringeCache,
                                terminalCache,
                                footprintCache,
                                exactFringeCache,
                                turnAttempts,
                                false,
                                state,
                                budget);
                if (state.competing)
                    return state.result(Status.UNKNOWN, "competing-ordinary-glyphs", null, budget);
                if (ordinary != null)
                    return state.result(
                            Status.SUPPORTED, "complete-observed-detached-three", ordinary, budget);
                var joint =
                        JoinedNumeralCurves.jointForHeads(
                                gray,
                                null,
                                width,
                                height,
                                left,
                                frame.top(),
                                right,
                                frame.bottom(),
                                frame.track(),
                                PrintedSlurOwners.headEvidence(
                                        frame.heads(), gray, width, height, frame.track()),
                                ah,
                                zh);
                var evidence = joint.raw();
                state.rawCandidates += joint.components();
                state.pathStates += joint.states();
                state.completedPaths += joint.completedPaths();
                JoinedTupletObservation.event(
                        "ACQUISITION",
                        joint.components(),
                        joint.states(),
                        joint.completedPaths(),
                        evidence.result().bows().size(),
                        evidence.result().limitReached() ? 1 : 0);
                state.curves += evidence.result().bows().size();
                if (evidence.result().limitReached())
                    return state.result(Status.RESOURCE_UNKNOWN, "curve-incomplete", null, budget);
                for (var bow : evidence.result().bows()) {
                    budget.tick();
                    var binding =
                            PrintedSlurOwners.bind(
                                    "joint-numeral-prototype",
                                    0,
                                    bow,
                                    frame.heads(),
                                    gray,
                                    width,
                                    height,
                                    frame.track());
                    if (binding.status() != PrintedSlurOwners.Status.GEOMETRY_OWNED || !bow.above())
                        continue;
                    var span = binding.span().orElseThrow();
                    if (span.start().noteIndex() != first || span.end().noteIndex() != last)
                        continue;
                    var selected = evidence.selectedContourWitnesses().get(bow);
                    if (selected == null) continue;
                    state.bound++;
                    BitSet curve = new BitSet(gray.length);
                    for (var column : selected)
                        for (var band : column.members())
                            for (int y = band.first(); y <= band.last(); y++) {
                                budget.tick();
                                int local = y - column.first();
                                if (column.x() < 0
                                        || column.x() >= width
                                        || y < 0
                                        || y >= height
                                        || local < 0
                                        || local >= column.rawProfile().size()
                                        || (gray[y * width + column.x()] & 255)
                                                != column.rawProfile().get(local))
                                    return state.result(
                                            Status.UNKNOWN,
                                            "profile-source-mismatch",
                                            null,
                                            budget);
                                curve.set(y * width + column.x());
                            }
                    if (curve.cardinality() > MAX_CURVE) throw new Exhausted("curve-pixel-cap");
                    JoinedTupletObservation.event("PHASE_JOINT", state.bound);
                    Glyph found =
                            scan(
                                    gray,
                                    width,
                                    height,
                                    ah,
                                    zh,
                                    gap,
                                    curve,
                                    graph,
                                    proposalCache,
                                    fringeCache,
                                    terminalCache,
                                    footprintCache,
                                    exactFringeCache,
                                    turnAttempts,
                                    true,
                                    state,
                                    budget);
                    if (state.competing)
                        return state.result(
                                Status.UNKNOWN, "competing-residual-components", null, budget);
                    if (state.numeralConflict && (found != null || winner != null))
                        return state.result(
                                Status.UNKNOWN, "competing-complete-numeral-classes", null, budget);
                    if (found != null) {
                        var meaning = new NumeralMeaning(3, first, middle, last, found);
                        if (winnerMeaning != null && !winnerMeaning.equals(meaning))
                            return state.result(
                                    Status.UNKNOWN,
                                    "competing-numeral-interpretations",
                                    null,
                                    budget);
                        winnerMeaning = meaning;
                        if (winner != null && !winner.equals(found))
                            return state.result(Status.UNKNOWN, "competing-glyphs", null, budget);
                        BitSet exposed = (BitSet) curve.clone();
                        for (int y = found.top(); y <= found.bottom(); y++)
                            exposed.clear(y * width + found.left(), y * width + found.right() + 1);
                        if (winnerExposed != null && !winnerExposed.equals(exposed))
                            JoinedTupletObservation.event(
                                    "EQUIVALENT_VERIFIED_CURVE_INTERPRETATION",
                                    first,
                                    middle,
                                    last,
                                    found.left(),
                                    found.top(),
                                    found.right(),
                                    found.bottom());
                        // Each hypothesis already passed complete curve acquisition, same exact
                        // endpoint owners, original raw graph, all numeral rivals and fringe.
                        // This route writes only the same tuplet ratio to the same note triple;
                        // it does not alter a slur or erase/reassign any pixel. Retain
                        // alternatives.

                        winnerExposed = exposed;
                        winner = found;
                        if (state.lastScanTurn != null) state.chosenTurn = state.lastScanTurn;
                    }
                }
            }
            if (winner != null && state.chosenTurn != null) {
                var q = state.chosenTurn;
                JoinedTupletObservation.event(
                        "FINAL_TURN_WITNESS",
                        q.attempt(),
                        q.box().left(),
                        q.box().top(),
                        q.box().right(),
                        q.box().bottom(),
                        q.proof().cells(),
                        q.proof().measuredFit().quarterRadius());
            }
            return state.result(
                    winner == null ? Status.UNKNOWN : Status.SUPPORTED,
                    winner != null
                            ? "observed-three-with-bound-curve"
                            : state.bound == 0
                                    ? "no-bound-complete-curve"
                                    : "no-complete-observed-three",
                    winner,
                    budget);
        } catch (Exhausted exhausted) {
            return state.result(Status.RESOURCE_UNKNOWN, exhausted.getMessage(), null, budget);
        } catch (IllegalArgumentException invalid) {
            return state.result(Status.UNKNOWN, "invalid-frame-or-owner", null, budget);
        }
    }

    private static Glyph scan(
            byte[] gray,
            int width,
            int height,
            PrintedSlurOwners.GeometryHead first,
            PrintedSlurOwners.GeometryHead last,
            float gap,
            BitSet curve,
            RawNumeralComponents graph,
            Map<Glyph, Boolean> cache,
            Map<FringeKey, ObservedFringeContinuation.Assessment> fringeCache,
            Map<TerminalKey, JointTerminalThree.Outcome> terminalCache,
            Map<TerminalFootprint, JointTerminalThree.Outcome> footprintCache,
            Map<TerminalKey, ObservedFringeContinuation.Assessment> exactFringeCache,
            Map<TerminalKey, Integer> turnAttempts,
            boolean joint,
            State state,
            Budget budget) {
        JoinedTupletObservation.event(
                "SCAN",
                graph.left,
                graph.top,
                graph.right,
                graph.bottom,
                Float.floatToRawIntBits(gap));
        Glyph winner = null;
        TurnWinner scanTurn = null;
        state.lastScanTurn = null;
        for (var component : graph.components()) {
            int l = width, r = -1, t = height, b = -1, residual = 0, owned = 0;
            for (int i = 0; i < component.size(); i++) {
                budget.tick();
                int at = component.pixel(i);
                if (joint && curve.get(at)) {
                    owned++;
                    continue;
                }
                int x = at % width, y = at / width;
                l = Math.min(l, x);
                r = Math.max(r, x);
                t = Math.min(t, y);
                b = Math.max(b, y);
                residual++;
            }
            JoinedTupletObservation.event(
                    "RAW_ROLE",
                    component.left,
                    component.top,
                    component.right,
                    component.bottom,
                    component.size(),
                    owned,
                    residual);
            JoinedTupletObservation.event("ROLE_ELIGIBILITY", joint ? 1 : 0, residual, owned);
            if (residual == 0 || joint && owned == 0) continue;
            Glyph box = new Glyph(l, t, r, b);
            JoinedTupletObservation.event("ROLE_BOX", l, t, r, b, residual);
            Boolean core = cache.get(box);
            if (core == null) {
                state.newKey();
                state.proposals++;
                core = coreShape(gray, width, height, first, last, gap, box, graph, budget);
                cache.put(box, core);
            } else JoinedTupletObservation.event("CORE_CACHE", l, t, r, b, core ? 1 : 0);
            JoinedTupletObservation.event("CORE_RESULT", l, t, r, b, core ? 1 : 0);
            TurnWinner turnCandidate = null;
            TerminalKey acceptedTerminalKey = null;
            if (!core && joint) {
                budget.tick(); // Constant-size exact identity key, no full-page bitset clone/hash.
                var terminalKey = new TerminalKey(box, curve);
                var terminal = terminalCache.get(terminalKey);
                if (terminal == null) {
                    var footprint = terminalFootprint(box, curve, width, height, gap, budget);
                    terminal = footprintCache.get(footprint);
                    if (terminal == null) {
                        state.newKey();
                        terminal =
                                JointTerminalThree.inspect(
                                        gray,
                                        width,
                                        height,
                                        l,
                                        t,
                                        r,
                                        b,
                                        graph.left,
                                        graph.top,
                                        graph.right,
                                        graph.bottom,
                                        curve,
                                        gap,
                                        budget,
                                        state::newRoleState);
                        footprintCache.put(footprint, terminal);
                    } else
                        JoinedTupletObservation.event("EXACT_TERMINAL_FOOTPRINT_REUSE", l, t, r, b);
                    terminalCache.put(terminalKey, terminal);
                    budget.tick();
                    turnAttempts.put(terminalKey, ++state.terminalAttempts);
                }
                JoinedTupletObservation.event(
                        "JOINT_TERMINAL_RESULT",
                        l,
                        t,
                        r,
                        b,
                        terminal.supported() ? 1 : 0,
                        state.roleStates);
                if (!terminal.supported()
                        && terminal.classifiedDigit() >= 0
                        && terminal.classifiedDigit() != 3) state.numeralConflict = true;

                if (terminal.supported()) {
                    if (terminal.glyphExtent() != null) {
                        var extent = terminal.glyphExtent();
                        l = extent.l();
                        t = extent.t();
                        r = extent.r();
                        b = extent.b();
                        box = new Glyph(l, t, r, b);
                    }
                    int area = 0;
                    for (int yy = t; yy <= b; yy++)
                        for (int xx = l; xx <= r; xx++) {
                            budget.tick();
                            if ((gray[yy * width + xx] & 255) <= 165) area++;
                        }
                    var ink = new MemberInk(gray, width, height, budget);
                    core =
                            !completeFourCrossbar(ink, area, l, t, r - l + 1, b - t + 1)
                                    && !looksLikeFingerFour(ink, area, l, r, t, b)
                                    && bracketArm(
                                            gray,
                                            width,
                                            height,
                                            Math.round(first.x() - gap * .3f),
                                            l - 2,
                                            t,
                                            gap,
                                            budget)
                                    && bracketArm(
                                            gray,
                                            width,
                                            height,
                                            r + 2,
                                            Math.round(last.x() + gap * .3f),
                                            t,
                                            gap,
                                            budget)
                                    && !TupletNumeralNeighbors.joinedText(
                                            gray, width, height, l, t, r, b);
                    if (core) {
                        acceptedTerminalKey = terminalKey;
                        if (terminal.witness() != null && terminal.witness().upperTurn() != null) {
                            budget.tick();
                            turnCandidate =
                                    new TurnWinner(
                                            turnAttempts.get(terminalKey),
                                            box,
                                            curve,
                                            terminal.witness().upperTurn());
                        }
                    }
                }
            }
            if (!core) continue;
            // Original unmasked gray within a residual-derived proposal. Shared curve is visible,
            // not deleted. No core boundary is invented and no pixel is replaced by paper.
            int pl = Math.max(0, graph.left - 1), pt = Math.max(0, graph.top - 1);
            int pr = Math.min(width - 1, graph.right + 1),
                    pb = Math.min(height - 1, graph.bottom + 1),
                    pw = pr - pl + 1;
            BitSet projection = new BitSet((pr - pl + 1) * (pb - pt + 1));
            int shared = 0;
            for (int at = curve.nextSetBit(0); at >= 0; at = curve.nextSetBit(at + 1)) {
                budget.tick();
                int x = at % width, y = at / width;
                if (x >= l && x <= r && y >= t && y <= b) {
                    if ((gray[at] & 255) <= 165) shared++;
                    continue;
                }
                if (x >= pl && x <= pr && y >= pt && y <= pb)
                    projection.set((y - pt) * pw + x - pl);
            }
            state.shared += shared;
            FringeKey key = new FringeKey(box, projection);
            var fringe =
                    acceptedTerminalKey == null
                            ? fringeCache.get(key)
                            : exactFringeCache.get(acceptedTerminalKey);
            if (fringe == null) {
                state.newKey();
                state.fringeProofs++;
                fringe =
                        ObservedFringeContinuation.inspect(
                                gray,
                                width,
                                height,
                                curve,
                                l,
                                t,
                                r,
                                b,
                                graph.left,
                                graph.top,
                                graph.right,
                                graph.bottom,
                                gap,
                                budget);
                if (acceptedTerminalKey == null) fringeCache.put(key, fringe);
                else exactFringeCache.put(acceptedTerminalKey, fringe);
            } else JoinedTupletObservation.event("FRINGE_CACHE", l, t, r, b);
            JoinedTupletObservation.event(
                    "FRINGE",
                    fringe.kind().ordinal(),
                    fringe.paper(),
                    fringe.core(),
                    fringe.visited(),
                    fringe.accepted(),
                    shared,
                    fringe.reachablePixels(),
                    fringe.ridgeStates());
            if (fringe.kind() == ObservedFringeContinuation.Kind.RESOURCE_UNKNOWN)
                throw new Exhausted("fringe-state-cap");
            if (JoinedTupletObservation.reject(
                    "G.FRINGE", fringe.kind() != ObservedFringeContinuation.Kind.CLEAR)) continue;
            if (winner != null && !winner.equals(box)) {
                state.competing = true;
                return null;
            }
            winner = box;
            scanTurn = turnCandidate;
        }
        state.lastScanTurn = scanTurn;
        return winner;
    }

    private static boolean coreShape(
            byte[] gray,
            int width,
            int height,
            PrintedSlurOwners.GeometryHead first,
            PrintedSlurOwners.GeometryHead last,
            float gap,
            Glyph box,
            RawNumeralComponents graph,
            Budget budget) {
        int l = box.left(),
                t = box.top(),
                r = box.right(),
                b = box.bottom(),
                gw = r - l + 1,
                gh = b - t + 1,
                n = 0;
        for (int y = t; y <= b; y++)
            for (int x = l; x <= r; x++) {
                budget.tick();
                if ((gray[y * width + x] & 255) <= 165) n++;
            }
        JoinedTupletObservation.event(
                "CORE_GEOMETRY",
                l,
                t,
                r,
                b,
                gw,
                gh,
                n,
                Float.floatToRawIntBits(gap),
                graph.left,
                graph.top,
                graph.right,
                graph.bottom);
        if (JoinedTupletObservation.reject("G.LEFT", l <= graph.left)
                || JoinedTupletObservation.reject("G.RIGHT", r >= graph.right)
                || JoinedTupletObservation.reject("G.TOP", t <= graph.top)
                || JoinedTupletObservation.reject("G.BOTTOM", b >= graph.bottom)
                || JoinedTupletObservation.reject("G.MIN_HEIGHT", gh < gap * .7f)
                || JoinedTupletObservation.reject("G.MAX_HEIGHT", gh > gap * 2.3f)
                || JoinedTupletObservation.reject("G.MIN_WIDTH", gw < gh * .30f)
                || JoinedTupletObservation.reject("G.MAX_WIDTH", gw > gh * .95f)
                || JoinedTupletObservation.reject("G.MIN_FILL", n < gw * gh * .15f)
                || JoinedTupletObservation.reject("G.MAX_FILL", n > gw * gh * .70f)) return false;
        if (JoinedTupletObservation.reject(
                "G.THREE",
                !looksLikeThree(new MemberInk(gray, width, height, budget), l, t, gw, gh)))
            return false;
        if (JoinedTupletObservation.reject(
                        "G.LEFT_ARM",
                        !bracketArm(
                                gray,
                                width,
                                height,
                                Math.round(first.x() - gap * .3f),
                                l - 2,
                                t,
                                gap,
                                budget))
                || JoinedTupletObservation.reject(
                        "G.RIGHT_ARM",
                        !bracketArm(
                                gray,
                                width,
                                height,
                                r + 2,
                                Math.round(last.x() + gap * .3f),
                                t,
                                gap,
                                budget))) return false;
        return !JoinedTupletObservation.reject(
                "G.JOINED_TEXT",
                TupletNumeralNeighbors.joinedText(gray, width, height, l, t, r, b));
    }

    private static boolean bracketArm(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            float gap,
            Budget budget) {
        left = Math.max(0, left);
        right = Math.min(width - 1, right);
        JoinedTupletObservation.event("ARM_DOMAIN", left, right, top, Float.floatToRawIntBits(gap));
        if (right - left < gap) return false;
        int occupied = 0;
        for (int x = left; x <= right; x++)
            for (int y = Math.max(0, top - Math.round(gap * .5f));
                    y <= Math.min(height - 1, top + Math.round(gap * .5f));
                    y++) {
                budget.tick();
                if ((gray[y * width + x] & 255) <= 165) {
                    occupied++;
                    break;
                }
            }
        JoinedTupletObservation.event("ARM_OCCUPIED", left, right, top, occupied);
        return occupied >= (right - left + 1) * .76f;
    }

    private static boolean looksLikeFingerFour(
            MemberInk ink, int area, int minX, int maxX, int minY, int maxY) {
        int glyphWidth = maxX - minX + 1, glyphHeight = maxY - minY + 1;
        float fill = area / (float) (glyphWidth * glyphHeight);
        if (fill < .14f || fill > .66f) return false;
        int[] rows = new int[glyphHeight];
        int[] columns = new int[glyphWidth];
        int upperLeft = 0, lowerLeft = 0;
        for (int y = minY; y <= maxY; y++)
            for (int x = minX; x <= maxX; x++) {
                if (!ink.dark(x, y)) continue;
                int localX = x - minX, localY = y - minY;
                rows[localY]++;
                columns[localX]++;
                if (localX < glyphWidth * .55f && localY < glyphHeight * .58f) upperLeft++;
                if (localX < glyphWidth * .45f && localY > glyphHeight * .72f) lowerLeft++;
            }
        int rightSpine = 0;
        for (int x = Math.max(0, Math.round(glyphWidth * .52f)); x < glyphWidth; x++)
            rightSpine = Math.max(rightSpine, columns[x]);
        int middleCrossbar = 0;
        for (int y = Math.max(0, Math.round(glyphHeight * .36f));
                y <= Math.min(glyphHeight - 1, Math.round(glyphHeight * .74f));
                y++) middleCrossbar = Math.max(middleCrossbar, rows[y]);
        int topBar = 0;
        for (int y = 0; y < Math.max(1, Math.round(glyphHeight * .28f)); y++)
            topBar = Math.max(topBar, rows[y]);
        JoinedTupletObservation.event(
                "FOUR_FEATURES",
                area,
                glyphWidth,
                glyphHeight,
                Float.floatToRawIntBits(fill),
                rightSpine,
                middleCrossbar,
                topBar,
                upperLeft,
                lowerLeft);
        return rightSpine >= glyphHeight * .58f
                && middleCrossbar >= glyphWidth * .50f
                && topBar < glyphWidth * .68f
                && upperLeft >= Math.max(2, Math.round(area * .10f))
                && lowerLeft <= Math.max(2, Math.round(area * .16f));
    }

    private static boolean looksLikeThree(MemberInk ink, int left, int top, int w, int h) {
        int[] min = new int[h], max = new int[h];
        for (int y = 0; y < h; y++) {
            min[y] = w;
            max[y] = -1;
            for (int x = 0; x < w; x++)
                if (ink.dark(left + x, top + y)) {
                    min[y] = Math.min(min[y], x);
                    max[y] = x;
                }
        }
        int upperOpen = 0, lowerOpen = 0, upperPocket = 0, lowerPocket = 0;
        int upperRasterPocket = 0, lowerRasterPocket = 0;
        int upperLobe = -1, lowerLobe = -1, waist = w, waistLeft = w, lowerOpenLeft = w;
        for (int y = 0; y < h; y++) {
            // Staff-rule removal can leave a blank scanline through a rest hook.
            // No ink is not an opening or an indented waist of a printed 3.
            if (max[y] < 0) continue;
            float fraction = y / (float) h;
            // A row occupies a whole pixel band; include a short opening that
            // crosses a lobe boundary instead of discarding it at small sizes.
            float nextFraction = (y + 1) / (float) h;
            if (nextFraction > .15f && fraction <= .36f + 1f / h) {
                upperLobe = Math.max(upperLobe, max[y]);
                if (min[y] >= w * .40f) upperOpen++;
                if (hasLobePocket(ink, left, top + y, w)) upperPocket++;
                if (hasLobePocket(ink, left, top + y, w, true)) upperRasterPocket++;
            }
            if (nextFraction > .60f && fraction <= .82f) {
                // The lower curve must bulge before the baseline; a 2 only widens at its foot.
                if (fraction <= .75f) lowerLobe = Math.max(lowerLobe, max[y]);
                // An italic lower bowl sits left of the upper bowl. Its opening
                // is relative to its own right edge, not the whole glyph width.
                if (max[y] >= w * .5f && min[y] >= Math.min(w * .40f, max[y] * .50f)) {
                    lowerOpen++;
                    lowerOpenLeft = Math.min(lowerOpenLeft, min[y]);
                }
                if (hasLobePocket(ink, left, top + y, w)) lowerPocket++;
                if (hasLobePocket(ink, left, top + y, w, true)) lowerRasterPocket++;
            }
            if (fraction >= .37f && fraction <= .55f) {
                waist = Math.min(waist, max[y]);
                waistLeft = Math.min(waistLeft, min[y]);
            }
        }
        int required = Math.max(2, h / 12);
        // Curled terminals put ink on the left of an otherwise open lobe. Allow
        // that ink only with a wide interior pocket and a truly open row in each
        // lobe: a closed 8 and the solid upper-left stem of a 5 still fail.
        boolean upper = upperOpen >= required || upperOpen >= 1 && upperPocket >= required;
        boolean lower = lowerOpen >= required || lowerOpen >= 1 && lowerPocket >= required;
        int indentation = Math.max(1, (int) Math.floor(w * .08f));
        int foot = -1, capLeft = w;
        for (int y = 0; y < Math.max(2, (int) Math.ceil(h * .10)); y++)
            capLeft = Math.min(capLeft, min[y]);
        for (int y = (int) Math.ceil(h * .92); y < h; y++) foot = Math.max(foot, max[y]);
        int extendedSolidFoot = 0;
        for (int y = (int) Math.ceil(h * .55f); y < (int) Math.ceil(h * .92f); y++) {
            if (min[y] > w * .4f || max[y] - min[y] + 1 < w * .6f) continue;
            boolean growsFromDiagonal = false;
            for (int prior = Math.max(0, y - Math.max(2, Math.round(h * .15f))); prior < y; prior++)
                if (max[prior] >= 0
                        && max[y] - max[prior] >= Math.max(2, Math.ceil(w * .2f))
                        && min[y] <= min[prior] + indentation) growsFromDiagonal = true;
            if (!growsFromDiagonal) continue;
            int occupied = 0;
            for (int x = min[y]; x <= max[y]; x++) if (ink.dark(left + x, top + y)) occupied++;
            if (occupied >= (max[y] - min[y] + 1) * .9f) extendedSolidFoot++;
        }
        // A tilted 2's full terminal can end above the last antialiased rows.
        // Its solid foot extends beyond the preceding diagonal, not around a lower bowl.
        boolean lobes =
                extendedSolidFoot < required
                        && lowerLobe - foot >= indentation
                        && upperLobe - waist >= indentation;
        boolean strictLower =
                lowerLobe - waist >= indentation
                        || lowerLobe >= w * .5f
                                && lowerOpen >= required
                                && lowerOpenLeft - waistLeft >= indentation
                                && upperLobe - capLeft >= Math.max(2, (int) Math.ceil(w * .25f));
        JoinedTupletObservation.event(
                "THREE_CORE",
                left,
                top,
                w,
                h,
                upperOpen,
                lowerOpen,
                upperPocket,
                lowerPocket,
                upperLobe,
                lowerLobe,
                waist,
                waistLeft,
                lowerOpenLeft,
                extendedSolidFoot,
                required,
                foot,
                capLeft,
                lobes ? 1 : 0,
                upper ? 1 : 0,
                lower ? 1 : 0,
                strictLower ? 1 : 0);
        JoinedTupletObservation.event(
                "THREE_EXTENTS",
                indentation,
                capLeft,
                foot,
                upperLobe,
                lowerLobe,
                waist,
                waistLeft,
                lowerOpenLeft);
        // Preserve every released recognition path before considering raster recovery.
        if (lobes && upper && lower && strictLower) return true;
        boolean rasterUpper =
                upperOpen >= required || upperOpen >= 1 && upperRasterPocket >= required;
        boolean rasterLower =
                lowerOpen >= required || lowerOpen >= 1 && lowerRasterPocket >= required;
        boolean curledLower =
                lowerLobe - waist >= indentation
                        || lowerLobe >= w * .5f
                                && rasterLower
                                && lowerOpenLeft - waistLeft >= indentation
                                && upperLobe - capLeft >= Math.max(2, (int) Math.ceil(w * .25f));
        JoinedTupletObservation.event(
                "THREE_RASTER",
                upperRasterPocket,
                lowerRasterPocket,
                rasterUpper ? 1 : 0,
                rasterLower ? 1 : 0,
                curledLower ? 1 : 0,
                lobes ? 1 : 0);
        if (!(lobes && rasterUpper && rasterLower && curledLower)) return false;
        // A four's complete spine and crossbar contradict the added two-bowl proposal.
        // Do not reinterpret a four's narrow diagonal paper pocket as a curled terminal.
        int area = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) if (ink.dark(left + x, top + y)) area++;
        return !completeFourCrossbar(ink, area, left, top, w, h);
    }

    /** A four needs a separately inked crossbar, not a terminal and bowl separated by paper. */
    private static boolean completeFourCrossbar(
            MemberInk ink, int area, int left, int top, int w, int h) {
        if (!looksLikeFingerFour(ink, area, left, left + w - 1, top, top + h - 1)) return false;
        for (int y = Math.max(0, Math.round(h * .36f));
                y <= Math.min(h - 1, Math.round(h * .74f));
                y++) {
            int run = 0;
            for (int x = 0; x < w; x++) {
                run = ink.dark(left + x, top + y) ? run + 1 : 0;
                if (run >= w * .50f) {
                    JoinedTupletObservation.event("FOUR_CROSSBAR", left, top, w, h, y, run);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasLobePocket(MemberInk ink, int left, int y, int w) {
        return hasLobePocket(ink, left, y, w, false);
    }

    private static boolean hasLobePocket(
            MemberInk ink, int left, int y, int w, boolean pixelCenter) {
        int run = 0;
        for (int x = Math.round(w * .25f); x < w; x++) {
            if (!ink.dark(left + x, y)) run++;
            else {
                // Only the additive recovery compares the sampled pixel center.
                if (run >= Math.max(2, Math.round(w * .22f))
                        && x + (pixelCenter ? .5f : 0) >= w * .55f) return true;
                run = 0;
            }
        }
        return false;
    }
}
