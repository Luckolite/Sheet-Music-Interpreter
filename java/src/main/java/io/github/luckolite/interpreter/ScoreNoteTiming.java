package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared rhythmic clock for synthesized playback and its on-page note highlight. */
public final class ScoreNoteTiming {
    private static final ThreadLocal<TimingSession> SESSION = new ThreadLocal<>();

    private record TimingKey(ScoreNoteEvent note, double beats, double onset, boolean candidate) {}

    private record StaffKey(int staffIndex, int staffCount) {}

    private record MeasureKey(int measureIndex, int staffCount) {}

    private record MeasureStaffKey(int measureIndex, int staffIndex, int staffCount) {}

    private record SystemProfile(float shift, java.util.Set<Integer> openingMeasures) {}

    private static final class ScoreIndex {
        final java.util.Map<MeasureKey, List<ScoreNoteEvent>> measures = new java.util.HashMap<>();
        final java.util.Map<MeasureStaffKey, List<ScoreNoteEvent>> voices =
                new java.util.HashMap<>();
        final java.util.Map<StaffKey, List<ScoreNoteEvent>> staves = new java.util.HashMap<>();

        ScoreIndex(List<ScoreNoteEvent> notes) {
            for (ScoreNoteEvent note : notes)
                if (note != null) {
                    measures.computeIfAbsent(
                                    new MeasureKey(note.measureIndex(), note.staffCount()),
                                    ignored -> new ArrayList<>())
                            .add(note);
                    voices.computeIfAbsent(
                                    new MeasureStaffKey(
                                            note.measureIndex(),
                                            note.staffIndex(),
                                            note.staffCount()),
                                    ignored -> new ArrayList<>())
                            .add(note);
                    if (Float.isFinite(note.positionInMeasure()))
                        staves.computeIfAbsent(
                                        new StaffKey(note.staffIndex(), note.staffCount()),
                                        ignored -> new ArrayList<>())
                                .add(note);
                }
            Comparator<ScoreNoteEvent> order =
                    Comparator.comparingInt(ScoreNoteEvent::measureIndex)
                            .thenComparingDouble(ScoreNoteEvent::positionInMeasure)
                            .thenComparingInt(ScoreNoteEvent::staffStep);
            voices.values().forEach(value -> value.sort(order));
            staves.values().forEach(value -> value.sort(order));
        }
    }

    /** Bounded, thread-confined reuse while the caller resolves an unchanged score snapshot. */
    public static final class TimingSession implements AutoCloseable {
        private final TimingSession previous = SESSION.get();
        private final java.util.IdentityHashMap<
                        List<ScoreNoteEvent>, java.util.Map<TimingKey, Double>>
                values = new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<
                        List<ScoreNoteEvent>, java.util.Map<StaffKey, Float>>
                leadingInsets = new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<List<ScoreNoteEvent>, List<ScoreNoteEvent>>
                metricalNotes = new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<List<ScoreNoteEvent>, ScoreIndex> indexes =
                new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<List<ScoreNoteEvent>, List<RhythmGroup>>
                rhythmGroups = new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<
                        List<ScoreNoteEvent>, java.util.IdentityHashMap<ScoreNoteEvent, Boolean>>
                independentDurations = new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<
                        List<ScoreNoteEvent>, java.util.Map<StaffKey, SystemProfile>>
                systemProfiles = new java.util.IdentityHashMap<>();
        private int leadingInsetCalculations;
        private int metricalListCalculations;
        private int scoreIndexCalculations;
        private int rhythmGroupCalculations;
        private int independentDurationCalculations;
        private int independentDurationCount;
        private int size;
        private boolean closed;

        private TimingSession() {
            SESSION.set(this);
        }

        private Double get(List<ScoreNoteEvent> notes, TimingKey key) {
            var map = values.get(notes);
            return map == null ? null : map.get(key);
        }

        private double put(List<ScoreNoteEvent> notes, TimingKey key, double value) {
            if (size >= 8192) {
                values.clear();
                size = 0;
            }
            var map = values.computeIfAbsent(notes, ignored -> new java.util.HashMap<>());
            if (map.put(key, value) == null) size++;
            return value;
        }

        private Float leadingInset(List<ScoreNoteEvent> notes, StaffKey key) {
            var map = leadingInsets.get(notes);
            return map == null ? null : map.get(key);
        }

        private float putLeadingInset(List<ScoreNoteEvent> notes, StaffKey key, float value) {
            leadingInsets
                    .computeIfAbsent(notes, ignored -> new java.util.HashMap<>())
                    .put(key, value);
            return value;
        }

        int leadingInsetCalculationCount() {
            return leadingInsetCalculations;
        }

        private ScoreIndex index(List<ScoreNoteEvent> notes) {
            ScoreIndex found = indexes.get(notes);
            if (found != null) return found;
            scoreIndexCalculations++;
            ScoreIndex result = new ScoreIndex(notes);
            indexes.put(notes, result);
            return result;
        }

        private List<ScoreNoteEvent> metrical(List<ScoreNoteEvent> notes) {
            List<ScoreNoteEvent> found = metricalNotes.get(notes);
            if (found != null) return found;
            metricalListCalculations++;
            boolean hasGrace = false;
            for (ScoreNoteEvent note : notes)
                if (grace(note)) {
                    hasGrace = true;
                    break;
                }
            if (!hasGrace) {
                metricalNotes.put(notes, notes);
                return notes;
            }
            List<ScoreNoteEvent> result = new ArrayList<>(notes.size());
            for (ScoreNoteEvent note : notes) if (!grace(note)) result.add(note);
            metricalNotes.put(notes, result);
            metricalNotes.put(result, result);
            return result;
        }

        int metricalListCalculationCount() {
            return metricalListCalculations;
        }

        int scoreIndexCalculationCount() {
            return scoreIndexCalculations;
        }

        private List<RhythmGroup> groups(List<ScoreNoteEvent> voice) {
            List<RhythmGroup> found = rhythmGroups.get(voice);
            if (found != null) return found;
            rhythmGroupCalculations++;
            List<RhythmGroup> result = buildRhythmGroups(voice);
            if (rhythmGroups.size() >= 8192) rhythmGroups.clear();
            rhythmGroups.put(voice, result);
            return result;
        }

        int rhythmGroupCalculationCount() {
            return rhythmGroupCalculations;
        }

        private boolean independentDuration(ScoreNoteEvent note, List<ScoreNoteEvent> notes) {
            var map = independentDurations.get(notes);
            Boolean found = map == null ? null : map.get(note);
            if (found != null) return found;
            independentDurationCalculations++;
            boolean result = uncachedIndependentDuration(note, measureVoice(note, notes));
            if (independentDurationCount >= 8192) {
                independentDurations.clear();
                independentDurationCount = 0;
            }
            independentDurations
                    .computeIfAbsent(notes, ignored -> new java.util.IdentityHashMap<>())
                    .put(note, result);
            independentDurationCount++;
            return result;
        }

        int independentDurationCalculationCount() {
            return independentDurationCalculations;
        }

        @Override
        public void close() {
            if (closed) return;
            if (SESSION.get() != this)
                throw new IllegalStateException("Timing sessions must close in nesting order");
            closed = true;
            values.clear();
            leadingInsets.clear();
            metricalNotes.clear();
            indexes.clear();
            rhythmGroups.clear();
            independentDurations.clear();
            systemProfiles.clear();
            if (previous == null) SESSION.remove();
            else SESSION.set(previous);
        }
    }

    public static TimingSession beginTimingSession() {
        return new TimingSession();
    }

    private static final float SAME_ONSET_POSITION = .018f;
    private static final float CROSS_STAFF_ONSET_POSITION = .022f;
    private static final float MAX_ORDINARY_MEASURE_INSET = .14f;
    private static final float LEARNED_INSET_TOLERANCE = .045f;
    private static final float MAX_LEARNABLE_MEASURE_INSET = .22f;
    private static final float SYSTEM_CHANGE_Y = .06f;
    private static final float MIN_SYSTEM_HEADER_SHIFT = .035f;
    private static final float MAX_SYSTEM_HEADER_SHIFT = .30f;

    private static final class RhythmGroup {
        final float position;
        final List<ScoreNoteEvent> notes = new ArrayList<>();

        RhythmGroup(float position) {
            this.position = position;
        }

        private List<ScoreNoteEvent> attackNotes;
        // Group membership is fixed during timing queries. Reuse symbol evidence,
        // including unknown durations, and invalidate every property if a note is added.
        private int cachedBeams = -1, cachedDots = -1;
        private double cachedWritten;
        private boolean writtenReady, longReady, tupletReady, cachedLong, cachedTuplet;

        void add(ScoreNoteEvent note) {
            notes.add(note);
            attackNotes = null;
            cachedBeams = cachedDots = -1;
            writtenReady = longReady = tupletReady = false;
        }

        List<ScoreNoteEvent> attacks() {
            if (attackNotes == null) {
                List<ScoreNoteEvent> moving = null;
                for (ScoreNoteEvent note : notes)
                    if (!hasIndependentSustain(note)) {
                        if (moving == null) moving = new ArrayList<>(notes.size());
                        moving.add(note);
                    }
                attackNotes = moving == null ? notes : moving;
            }
            return attackNotes;
        }

        int beamCount() {
            if (cachedBeams < 0) cachedBeams = calculateBeamCount();
            return cachedBeams;
        }

        private int calculateBeamCount() {
            int result = 0;
            for (ScoreNoteEvent note : attacks())
                result = Math.max(result, rhythmicBeamCount(note));
            return result;
        }

        int augmentationDots() {
            if (cachedDots < 0) cachedDots = calculateAugmentationDots();
            return cachedDots;
        }

        private int calculateAugmentationDots() {
            int result = 0;
            for (ScoreNoteEvent note : attacks())
                result = Math.max(result, note.augmentationDots());
            return result;
        }

        double writtenDuration() {
            if (!writtenReady) {
                cachedWritten = calculateWrittenDuration();
                writtenReady = true;
            }
            return cachedWritten;
        }

        private double calculateWrittenDuration() {
            int beams = beamCount();
            if (beams > 0 && hasTuplet()) {
                double shortest = Double.POSITIVE_INFINITY;
                for (ScoreNoteEvent note : attacks())
                    if (rhythmicBeamCount(note) == beams)
                        shortest =
                                Math.min(
                                        shortest,
                                        durationForBeam(beams, note.augmentationDots())
                                                * note.durationScale());
                return shortest;
            }
            if (beams > 0)
                return durationForBeam(beams, augmentationDots()) * notes.get(0).durationScale();
            double result = Double.NaN;
            for (ScoreNoteEvent note : attacks()) {
                double written = writtenDurationBeats(note);
                if (Double.isFinite(written))
                    result = Double.isFinite(result) ? Math.max(result, written) : written;
            }
            return result;
        }

        boolean hasReliableLongDuration() {
            if (!longReady) {
                cachedLong = calculateReliableLongDuration();
                longReady = true;
            }
            return cachedLong;
        }

        private boolean calculateReliableLongDuration() {
            for (ScoreNoteEvent note : notes) {
                // The white center of a half/whole head is direct symbol evidence. Horizontal
                // spacing and neighbouring beams must not turn that sustained note into part of
                // a rapid run merely because the engraving happens to leave similar gaps.
                if (note.beamCount() == 0
                        && note.unbeamedDurationBeats() >= ScoreNoteEvent.DURATION_HALF)
                    return true;
            }
            return false;
        }

        boolean hasTuplet() {
            if (!tupletReady) {
                cachedTuplet = calculateHasTuplet();
                tupletReady = true;
            }
            return cachedTuplet;
        }

        private boolean calculateHasTuplet() {
            for (ScoreNoteEvent note : notes) if (note.tupletDivisor() > 1) return true;
            return false;
        }

        boolean contains(ScoreNoteEvent target) {
            return notes.contains(target);
        }
    }

    private record MeasureLayout(int measureIndex, float firstPosition, float centerY) {}

    private ScoreNoteTiming() {}

    public static double beatInMeasure(ScoreNoteEvent note, float beatsPerMeasure) {
        if (note == null || !Float.isFinite(note.positionInMeasure())) return 0;
        double safeBeats = Math.max(.125, Math.min(128, beatsPerMeasure));
        double spatialBeat = Math.max(0, Math.min(1, note.positionInMeasure())) * safeBeats;
        return quantize(spatialBeat, .25);
    }

    public static double absoluteBeat(ScoreNoteEvent note, float beatsPerMeasure) {
        if (note == null) return 0;
        double safeBeats = Math.max(.125, Math.min(128, beatsPerMeasure));
        return Math.max(0, note.measureIndex()) * safeBeats + beatInMeasure(note, beatsPerMeasure);
    }

    private static boolean grace(ScoreNoteEvent note) {
        return note != null && (note.articulations() & NoteOrnament.GRACE) != 0;
    }

    private record GracePlayback(
            List<ScoreNoteEvent> metrical,
            ScoreNoteEvent principal,
            int index,
            int count,
            boolean trailing,
            boolean bothSides) {}

    private static GracePlayback gracePlayback(ScoreNoteEvent target, List<ScoreNoteEvent> notes) {
        if (target == null || notes == null) return null;
        TimingSession session = SESSION.get();
        if (session == null && notes.stream().noneMatch(ScoreNoteTiming::grace)) return null;
        List<ScoreNoteEvent> metrical =
                session == null
                        ? notes.stream()
                                .filter(n -> !grace(n))
                                .collect(java.util.stream.Collectors.toList())
                        : session.metrical(notes);
        if (metrical == notes) return null;
        List<ScoreNoteEvent> prefix = new ArrayList<>();
        ScoreNoteEvent prior = null;
        GracePlayback prefixContext = null;
        for (ScoreNoteEvent note : measureVoice(target, notes)) {
            if (grace(note)) {
                prefix.add(note);
                continue;
            }
            if (!prefix.isEmpty()
                    && (Math.abs(note.positionInMeasure() - target.positionInMeasure())
                                            <= SAME_ONSET_POSITION
                                    && !grace(target)
                            || prefix.contains(target)))
                prefixContext =
                        new GracePlayback(
                                metrical,
                                note,
                                prefix.indexOf(target),
                                prefix.size(),
                                false,
                                false);
            prefix.clear();
            prior = note;
        }
        if (!prefix.isEmpty()
                && prior != null
                && (prefix.contains(target) || prior.equals(target))) {
            if (prefixContext != null && prior.equals(target))
                return new GracePlayback(metrical, prior, -1, prefixContext.count, false, true);
            return new GracePlayback(
                    metrical, prior, prefix.indexOf(target), prefix.size(), true, false);
        }
        return prefixContext != null
                ? prefixContext
                : new GracePlayback(metrical, null, -1, 0, false, false);
    }

    private static double graceBudget(GracePlayback context, float beats) {
        double principal = resolvedWrittenDurationBeats(context.principal, context.metrical, beats);
        return Double.isFinite(principal) && principal > 0 ? Math.min(.25, principal * .25) : .125;
    }

    /** Written engraving clock; grace values do not consume the principal's metric duration. */
    public record WrittenPlacement(
            double onsetBeats,
            double durationBeats,
            int principalIndex,
            int ordinal,
            boolean afterGrace,
            double stealPercent) {
        public boolean grace() {
            return principalIndex >= 0;
        }
    }

    /** Unknown grace ownership stays unresolved rather than acquiring a geometric beat. */
    public static java.util.Optional<WrittenPlacement> writtenPlacement(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes, float beats) {
        if (target == null || notes == null || !Float.isFinite(beats) || beats <= 0)
            return java.util.Optional.empty();
        if (notes.stream().noneMatch(n -> n == target)) return java.util.Optional.empty();
        TimingSession session = SESSION.get();
        var metrical =
                session == null
                        ? notes.stream()
                                .filter(n -> !grace(n))
                                .collect(java.util.stream.Collectors.toList())
                        : session.metrical(notes);
        GracePlayback context = grace(target) ? gracePlayback(target, notes) : null;
        int principal = -1, ordinal = -1;
        boolean after = false;
        double onset, duration, steal = 0;
        if (grace(target)) {
            if (context == null || context.principal == null || context.index < 0)
                return java.util.Optional.empty();
            for (int i = 0; i < notes.size(); i++)
                if (notes.get(i) == context.principal) {
                    principal = i;
                    break;
                }
            if (principal < 0) return java.util.Optional.empty();
            onset = beatInMeasure(context.principal, metrical, beats);
            double principalDuration =
                    resolvedWrittenDurationBeats(context.principal, metrical, beats);
            if (!Double.isFinite(principalDuration) || principalDuration <= 0)
                return java.util.Optional.empty();
            after = context.trailing;
            ordinal = context.index;
            if (after) onset += principalDuration;
            duration = writtenDurationBeats(target);
            steal = 100 * graceBudget(context, beats) / context.count / principalDuration;
        } else {
            onset = beatInMeasure(target, metrical, beats);
            duration = resolvedWrittenDurationBeats(target, metrical, beats);
        }
        if (!Double.isFinite(onset) || onset < 0 || !Double.isFinite(duration) || duration <= 0)
            return java.util.Optional.empty();
        return java.util.Optional.of(
                new WrittenPlacement(onset, duration, principal, ordinal, after, steal));
    }

    /** Uses written beam/dot values to correct close onsets that spatial spacing compresses. */
    public static double beatInMeasure(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes, float beatsPerMeasure) {
        if (target == null || notes == null || notes.isEmpty())
            return beatInMeasure(target, beatsPerMeasure);
        GracePlayback grace = gracePlayback(target, notes);
        if (grace != null) {
            if (grace.principal == null)
                return beatInMeasure(target, grace.metrical, beatsPerMeasure);
            double onset = beatInMeasure(grace.principal, grace.metrical, beatsPerMeasure);
            double budget = graceBudget(grace, beatsPerMeasure);
            if (grace.trailing) {
                if (grace.index < 0) return onset;
                double duration =
                        resolvedWrittenDurationBeats(
                                grace.principal, grace.metrical, beatsPerMeasure);
                return onset + duration - budget + budget * grace.index / grace.count;
            }
            return onset + (grace.index < 0 ? budget : budget * grace.index / grace.count);
        }
        double safeBeats = Math.max(.125, Math.min(128, beatsPerMeasure));
        if (hasIndependentSustain(target)
                && target.leadingRestBeats() == 0
                && (target.positionInMeasure() <= MAX_LEARNABLE_MEASURE_INSET
                        || measureVoice(target, notes).stream()
                                .noneMatch(
                                        n ->
                                                n.positionInMeasure()
                                                        < target.positionInMeasure()
                                                                - SAME_ONSET_POSITION))
                && Math.abs(writtenDurationBeats(target) - safeBeats) < .001) return 0;
        var parallelTriplets =
                ParallelTripletClock.find(target, measureNotes(target, notes), safeBeats);
        if (parallelTriplets != null) {
            double onset = parallelTriplets.onset(target, measureNotes(target, notes));
            if (Double.isFinite(onset)) return onset;
        }
        List<ScoreNoteEvent> crossStaff = crossStaffPhrase(target, notes, safeBeats);
        if (!crossStaff.isEmpty()) {
            double onset = 0;
            for (int i = 0; i < crossStaff.size(); ) {
                ScoreNoteEvent first = crossStaff.get(i);
                int next = i;
                double advance = Double.POSITIVE_INFINITY;
                while (next < crossStaff.size()
                        && Math.abs(
                                        crossStaff.get(next).positionInMeasure()
                                                - first.positionInMeasure())
                                <= SAME_ONSET_POSITION) {
                    ScoreNoteEvent note = crossStaff.get(next++);
                    if (note.equals(target)
                            || hasIndependentSustain(target)
                                    && Math.abs(
                                                    note.positionInMeasure()
                                                            - target.positionInMeasure())
                                            <= SAME_ONSET_POSITION) return onset;
                    advance = Math.min(advance, crossStaffDuration(note, crossStaff, safeBeats));
                }
                onset += advance;
                i = next;
            }
        }
        double voiceOnset = voiceBeatInMeasure(target, notes, safeBeats);
        return alignedCrossStaffOnset(target, notes, safeBeats, voiceOnset);
    }

    /** Builds the rhythmic clock for one staff without recursively applying cross-staff anchors. */
    private static double voiceBeatInMeasure(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes, double safeBeats) {
        var session = SESSION.get();
        if (session == null) return uncachedVoiceBeat(target, notes, safeBeats);
        var key = new TimingKey(target, safeBeats, 0, false);
        Double found = session.get(notes, key);
        return found != null
                ? found
                : session.put(notes, key, uncachedVoiceBeat(target, notes, safeBeats));
    }

    private static double uncachedVoiceBeat(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes, double safeBeats) {
        List<ScoreNoteEvent> voice = measureVoice(target, notes);
        List<RhythmGroup> groups = rhythmGroups(voice);
        if (groups.isEmpty()) return beatInMeasure(target, (float) safeBeats);
        double[] parallelClock = parallelWrittenClock(groups, safeBeats);
        if (parallelClock != null) {
            double onset = leadingRest(groups);
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).contains(target)) return onset;
                onset += parallelClock[i] + followingRest(groups.get(i));
            }
        }
        double pickupStart = openingPickupStart(target, notes, safeBeats);
        if (Double.isFinite(pickupStart)) {
            double onset = pickupStart;
            for (RhythmGroup group : groups) {
                if (group.contains(target)) return onset;
                onset += group.writtenDuration();
            }
        }
        // A half-note melody can overlap a short beamed tail in another voice on this same
        // staff. Its sounding length must not push that tail (or its tie) later in the bar.
        if (!hasIndependentSustain(target)
                && groups.get(0).notes.stream().allMatch(ScoreNoteTiming::hasIndependentSustain)) {
            List<RhythmGroup> tail =
                    groups.stream()
                            .filter(
                                    g ->
                                            g.notes.stream()
                                                    .noneMatch(
                                                            ScoreNoteTiming::hasIndependentSustain))
                            .collect(java.util.stream.Collectors.toList());
            double[] written = tail.stream().mapToDouble(RhythmGroup::writtenDuration).toArray();
            boolean printedTail = completePrintedRestRhythm(tail, safeBeats);
            double start =
                    printedTail
                            ? leadingRest(tail)
                            : contiguousTailRunStart(tail, written, safeBeats);
            if (Double.isFinite(start) && start < groups.get(0).writtenDuration() - .03125) {
                for (int i = 0; i < tail.size(); i++) {
                    if (tail.get(i).contains(target)) return start;
                    start += written[i] + (printedTail ? followingRest(tail.get(i)) : 0);
                }
            }
        }
        if (completePrintedRestRhythm(groups, safeBeats)
                || sharedShortRestRhythm(target, notes, groups, safeBeats)) {
            double onset = leadingRest(groups);
            for (RhythmGroup group : groups) {
                if (group.contains(target)) return onset;
                onset += group.writtenDuration() + followingRest(group);
            }
        }
        double[] durations = stabilizedDurations(groups, safeBeats);
        double tailStart = contiguousTailRunStart(groups, durations, safeBeats);
        if (Double.isFinite(tailStart)) {
            double onset = tailStart;
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).contains(target)) return onset;
                onset += durations[i];
            }
        }
        double grid = rhythmicGrid(voice);
        float systemHeaderShift = systemHeaderShift(target, notes);
        float firstPosition = correctedSystemPosition(groups.get(0).position, systemHeaderShift);
        boolean startsAtBarline =
                startsAtBarline(target, notes, groups, durations, safeBeats, firstPosition);
        boolean fillsMeasure = fillsMeasure(durations, safeBeats);
        boolean overfillsMeasure = overfillsMeasure(durations, safeBeats);
        double positionPerBeat = learnedPositionPerBeat(groups, durations, safeBeats);
        double firstSpatial = quantize(firstPosition * safeBeats, grid);
        boolean underDetectedMeasure =
                underDetectedMeasure(
                        groups,
                        durations,
                        safeBeats,
                        grid,
                        systemHeaderShift,
                        startsAtBarline,
                        firstSpatial);
        // The final note may sustain through the barline, but every remaining attack must
        // begin before it. Reserve the written intervals up to that final attack.
        double[] timeToLastAttack = new double[groups.size()];
        for (int index = groups.size() - 2; index >= 0; index--)
            timeToLastAttack[index] = durations[index] + timeToLastAttack[index + 1];
        double previousOnset = 0, previousDuration = Double.NaN;
        double previousPosition = Double.NaN;
        for (int index = 0; index < groups.size(); index++) {
            RhythmGroup group = groups.get(index);
            double groupPosition = correctedSystemPosition(group.position, systemHeaderShift);
            double spatial = quantize(groupPosition * safeBeats, grid);
            // Engraving padding shifts every note, not just the first one. Move the whole clock
            // to the barline so un-beamed notes do not begin late and then rush to catch up.
            // A genuine leading rest leaves startsAtBarline false and keeps its spatial onset.
            if (startsAtBarline) {
                // Remove the engraved left inset before quantizing. Quantizing the inset and
                // onset independently can erase one short-rest slot when both land on opposite
                // sides of a grid midpoint.
                spatial = quantize(Math.max(0, groupPosition - firstPosition) * safeBeats, grid);
            }
            // An optically incomplete bar can still have a confidently read leading rest.
            double leading = leadingRest(groups);
            double onset = index == 0 && leading < safeBeats ? Math.max(spatial, leading) : spatial;
            // A read leading rest already accounts for its silence. Small
            // engraving insets must not add a second, invented rest slot.
            if (index == 0
                    && leading > 0
                    && leading < safeBeats
                    && spatial - leading <= safeBeats * MAX_ORDINARY_MEASURE_INSET) onset = leading;
            if (index > 0 && Double.isFinite(previousDuration)) {
                double writtenRest = followingRest(groups.get(index - 1));
                // A rest between attacks must leave room for the later written notes in this
                // measure. A misplaced whole-rest glyph cannot move them beyond the barline.
                if (writtenRest > 0
                        && previousOnset + previousDuration + writtenRest + timeToLastAttack[index]
                                >= safeBeats - .001) writtenRest = 0;
                double predicted = previousOnset + previousDuration + writtenRest;
                double positionGap =
                        Double.isFinite(previousPosition)
                                ? groupPosition - previousPosition
                                : Double.POSITIVE_INFINITY;
                double expectedPositionGap = previousDuration * positionPerBeat;
                double restBeats =
                        !fillsMeasure && writtenRest <= 0
                                ? inferredShortRest(
                                        positionGap, previousDuration, positionPerBeat, grid)
                                : 0;
                boolean clearWrittenRest =
                        restBeats > 0 && predicted + restBeats <= safeBeats + grid * .5;
                // Once the symbols account for the complete measure, the horizontal engraving is
                // no longer timing evidence: every bar has the same beat count whether a publisher
                // draws it narrow, wide, or under a slur. In an incomplete optical measure, keep
                // geometry only for a conspicuously large gap that can represent a written rest.
                if (overfillsMeasure) {
                    // Corrupt values used to advance past beat four and then fall back to a
                    // smaller spatial onset. ActiveScoreNotes consequently treated distinct
                    // notes across the bar as one giant chord. In an overfull optical measure,
                    // symbol durations are self-contradictory, so geometry is the safer clock;
                    // retain strict reading order even when quantization lands twice on a slot.
                    double separation = Math.min(grid, safeBeats / groups.size());
                    onset = Math.max(spatial, previousOnset + separation);
                } else if (writtenRest > 0) onset = predicted;
                else if (underDetectedMeasure) onset = Math.max(predicted, spatial);
                else if (clearWrittenRest) onset = predicted + restBeats;
                else if (predicted < safeBeats
                        && (fillsMeasure
                                || spatial < predicted
                                || positionGap <= 0
                                || positionGap <= Math.max(.075, expectedPositionGap * 1.8)))
                    onset = predicted;

                // Every group is sorted in printed reading order. Conflicting dot/beam evidence
                // must never make a later group move backwards and merge several attacks into a
                // giant chord. If the clock is already at the bar's edge, share the remaining
                // fraction between this and the still-unvisited groups rather than crossing it.
                if (onset <= previousOnset) {
                    double remainingSlots = groups.size() - index + 1.0;
                    double separation =
                            Math.min(
                                    grid,
                                    Math.max(.001, (safeBeats - previousOnset) / remainingSlots));
                    onset = previousOnset + separation;
                }
            }
            if (group.contains(target)) return onset;
            previousOnset = onset;
            previousDuration = durations[index];
            previousPosition = groupPosition;
        }
        return beatInMeasure(target, (float) safeBeats);
    }

    /**
     * Notes occupying the same horizontal slot on connected staves are one score onset. Optical
     * damage can nevertheless make each staff's independent duration repair choose a different
     * beat, which sounds like the parts take turns at a barline. Use the common engraving as a
     * tie-breaker, then return one of the already-valid staff clocks rather than inventing a new
     * in-between beat. A note must be the reciprocal nearest attack on the other staff, so a real
     * leading rest or intentionally staggered entrance remains independent.
     */
    private static double alignedCrossStaffOnset(
            ScoreNoteEvent target,
            List<ScoreNoteEvent> allNotes,
            double beatsPerMeasure,
            double voiceOnset) {
        double proposed = candidateCrossStaffOnset(target, allNotes, beatsPerMeasure, voiceOnset);
        if (Math.abs(proposed - voiceOnset) < .0001) return voiceOnset;
        if (target.staffCount() > 1) {
            // Validate this staff's full candidate map, so shifting a voice
            // cannot collapse two attacks. A conflict in an unrelated third
            // staff must not disable a consistent accompaniment alignment.
            var voice = measureVoice(target, allNotes);
            double previous = -1;
            float position = -1;
            for (var n : voice) {
                double candidate =
                        candidateCrossStaffOnset(
                                n,
                                allNotes,
                                beatsPerMeasure,
                                voiceBeatInMeasure(n, allNotes, beatsPerMeasure));
                if (n.positionInMeasure() - position > SAME_ONSET_POSITION
                        && candidate <= previous + .0001) return voiceOnset;
                previous = candidate;
                position = n.positionInMeasure();
            }
        }
        return proposed;
    }

    private static double candidateCrossStaffOnset(
            ScoreNoteEvent target,
            List<ScoreNoteEvent> allNotes,
            double beatsPerMeasure,
            double voiceOnset) {
        var session = SESSION.get();
        if (session == null)
            return uncachedCrossStaffCandidate(target, allNotes, beatsPerMeasure, voiceOnset);
        var key = new TimingKey(target, beatsPerMeasure, voiceOnset, true);
        Double found = session.get(allNotes, key);
        return found != null
                ? found
                : session.put(
                        allNotes,
                        key,
                        uncachedCrossStaffCandidate(target, allNotes, beatsPerMeasure, voiceOnset));
    }

    private static double uncachedCrossStaffCandidate(
            ScoreNoteEvent target,
            List<ScoreNoteEvent> allNotes,
            double beatsPerMeasure,
            double voiceOnset) {
        if (target.staffCount() <= 1 || !Float.isFinite(target.positionInMeasure()))
            return voiceOnset;

        List<ScoreNoteEvent> measure =
                measureNotes(target, allNotes).stream()
                        .filter(note -> Float.isFinite(note.positionInMeasure()))
                        .collect(java.util.stream.Collectors.toList());
        if (measure.isEmpty()) return voiceOnset;

        List<ScoreNoteEvent> aligned = new ArrayList<>();
        aligned.add(target);
        for (int staff = 0; staff < target.staffCount(); staff++) {
            if (staff == target.staffIndex()) continue;
            ScoreNoteEvent candidate = nearestNote(measure, staff, target.positionInMeasure());
            if (candidate == null
                    || Math.abs(candidate.positionInMeasure() - target.positionInMeasure())
                            > CROSS_STAFF_ONSET_POSITION) continue;
            ScoreNoteEvent reciprocal =
                    nearestNote(measure, target.staffIndex(), candidate.positionInMeasure());
            if (reciprocal == null
                    || Math.abs(reciprocal.positionInMeasure() - target.positionInMeasure())
                            > SAME_ONSET_POSITION) continue;
            aligned.add(candidate);
        }
        if (aligned.size() < 2) return voiceOnset;

        // A fully written note/rest rhythm supplies a stronger shared onset than
        // an incomplete neighbouring staff's geometric estimate. Conflicting
        // complete voices keep their own clocks rather than choosing arbitrarily.
        Double printedOnset = null;
        for (ScoreNoteEvent anchor : aligned) {
            List<RhythmGroup> groups = rhythmGroups(measureVoice(anchor, allNotes));
            if (!completePrintedRestRhythm(groups, beatsPerMeasure)
                    && !completeWrittenRhythm(groups, beatsPerMeasure)) continue;
            double onset = voiceBeatInMeasure(anchor, allNotes, beatsPerMeasure);
            if (printedOnset != null && Math.abs(printedOnset - onset) > .0001) return voiceOnset;
            printedOnset = onset;
        }
        if (printedOnset != null) return printedOnset;

        // A complete written voice establishes beat zero even when grace notes
        // reserve a wide left inset on another staff. Spatial proximity must not
        // pull that accompaniment toward an incomplete melody's later guess.
        if (target.leadingRestBeats() == 0)
            for (ScoreNoteEvent anchor : aligned) {
                List<RhythmGroup> groups = rhythmGroups(measureVoice(anchor, allNotes));
                if (groups.isEmpty() || !groups.get(0).contains(anchor) || leadingRest(groups) > 0)
                    continue;
                double total = 0;
                for (RhythmGroup group : groups)
                    total += group.writtenDuration() + followingRest(group);
                if (Math.abs(total - beatsPerMeasure) < .001
                        && Math.abs(voiceBeatInMeasure(anchor, allNotes, beatsPerMeasure)) < .001)
                    return 0;
            }

        // Use one representative position per staff so a chord cannot pull the shared slot toward
        // whichever staff happened to expose more noteheads.
        double positionTotal = 0;
        float headerShift = 0;
        List<Double> candidateOnsets = new ArrayList<>();
        for (ScoreNoteEvent note : aligned) {
            positionTotal += note.positionInMeasure();
            headerShift = Math.max(headerShift, systemHeaderShift(note, allNotes));
            candidateOnsets.add(voiceBeatInMeasure(note, allNotes, beatsPerMeasure));
        }
        float alignedPosition =
                correctedSystemPosition((float) (positionTotal / aligned.size()), headerShift);
        float firstPosition = 1;
        for (ScoreNoteEvent note : measure)
            firstPosition =
                    Math.min(
                            firstPosition,
                            correctedSystemPosition(note.positionInMeasure(), headerShift));

        float ordinaryLimit = MAX_ORDINARY_MEASURE_INSET;
        for (ScoreNoteEvent note : aligned) {
            float learnedInset = learnedLeadingInset(note, allNotes);
            if (Float.isFinite(learnedInset) && learnedInset <= MAX_LEARNABLE_MEASURE_INSET)
                ordinaryLimit = Math.max(ordinaryLimit, learnedInset + LEARNED_INSET_TOLERANCE);
        }
        boolean startsAtBarline = firstPosition <= ordinaryLimit;
        double sharedGrid = rhythmicGrid(measure);
        double spatialOnset =
                quantize(
                        (startsAtBarline
                                        ? Math.max(0, alignedPosition - firstPosition)
                                        : alignedPosition)
                                * beatsPerMeasure,
                        sharedGrid);

        double best = voiceOnset;
        double bestDistance = Math.abs(voiceOnset - spatialOnset);
        for (double candidate : candidateOnsets) {
            double distance = Math.abs(candidate - spatialOnset);
            if (distance < bestDistance - .0001
                    || (Math.abs(distance - bestDistance) <= .0001 && candidate < best)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static ScoreNoteEvent nearestNote(
            List<ScoreNoteEvent> notes, int staffIndex, float position) {
        ScoreNoteEvent best = null;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (ScoreNoteEvent note : notes) {
            if (note.staffIndex() != staffIndex) continue;
            float distance = Math.abs(note.positionInMeasure() - position);
            if (distance < bestDistance) {
                best = note;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Corrects one missing or over-counted beam inside an otherwise even written run. */
    public static double resolvedWrittenDurationBeats(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes) {
        return resolvedWrittenDurationBeats(target, notes, Float.NaN);
    }

    public static double resolvedWrittenDurationBeats(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes, float beatsPerMeasure) {
        GracePlayback grace = gracePlayback(target, notes);
        if (grace != null) {
            if (grace.principal == null)
                return resolvedWrittenDurationBeats(target, grace.metrical, beatsPerMeasure);
            double budget = graceBudget(grace, beatsPerMeasure);
            return grace.index < 0
                    ? resolvedWrittenDurationBeats(target, grace.metrical, beatsPerMeasure)
                            - budget * (grace.bothSides ? 2 : 1)
                    : budget / grace.count;
        }
        if (target != null && notes != null && Float.isFinite(beatsPerMeasure)) {
            var parallelTriplets =
                    ParallelTripletClock.find(target, measureNotes(target, notes), beatsPerMeasure);
            if (parallelTriplets != null) return parallelTriplets.duration(target);
        }
        // A hollow notehead may share an onset/staff with a faster independent voice.
        // Group rhythm repairs describe the attack clock, not that note's sounding length.
        if (hasIndependentDuration(target, notes)) return writtenDurationBeats(target);
        if (target == null || notes == null || notes.isEmpty()) return writtenDurationBeats(target);
        List<ScoreNoteEvent> phrase = crossStaffPhrase(target, notes, beatsPerMeasure);
        if (!phrase.isEmpty()) return crossStaffDuration(target, phrase, beatsPerMeasure);
        List<RhythmGroup> groups = rhythmGroups(measureVoice(target, notes));
        double safeBeats =
                Float.isFinite(beatsPerMeasure)
                        ? Math.max(.125, Math.min(128, beatsPerMeasure))
                        : Double.NaN;
        if (parallelWrittenClock(groups, safeBeats) != null) return writtenDurationBeats(target);
        if (completePrintedRestRhythm(groups, safeBeats)
                || sharedShortRestRhythm(target, notes, groups, safeBeats))
            return writtenDurationBeats(target);
        double[] durations = stabilizedDurations(groups, safeBeats);
        for (int index = 0; index < groups.size(); index++)
            if (groups.get(index).contains(target)) return durations[index];
        return writtenDurationBeats(target);
    }

    public static boolean hasIndependentSustain(ScoreNoteEvent note) {
        return note != null
                && note.beamCount() == 0
                && note.unbeamedDurationBeats() >= ScoreNoteEvent.DURATION_HALF;
    }

    /** Mixed written values at one chord onset prove parallel voices on this stave.
     * Their quarter notes may overlap the other voice's eighth-note attacks too. */
    public static boolean hasIndependentDuration(ScoreNoteEvent note, List<ScoreNoteEvent> notes) {
        if (hasIndependentSustain(note)) return true;
        if (note == null || notes == null) return false;
        // Every check below is confined to this bar/staff. Reuse the session index
        // instead of rescanning the whole piece (including a nested all-note scan).
        TimingSession session = SESSION.get();
        return session == null
                ? uncachedIndependentDuration(note, measureVoice(note, notes))
                : session.independentDuration(note, notes);
    }

    private static boolean uncachedIndependentDuration(
            ScoreNoteEvent note, List<ScoreNoteEvent> notes) {
        if (note.beamCount() > 0 && hasRepeatedDottedVoice(rhythmGroups(notes))) return true;
        // Explicitly different tuplet values at one attack prove parallel rhythms.
        // The faster attack clock must not shorten the other voice's written value.
        for (ScoreNoteEvent other : notes)
            if (other.measureIndex() == note.measureIndex()
                    && other.staffIndex() == note.staffIndex()
                    && other.staffCount() == note.staffCount()
                    && Math.abs(other.positionInMeasure() - note.positionInMeasure())
                            <= SAME_ONSET_POSITION
                    && (other.tupletDivisor() != note.tupletDivisor()
                            || other.tupletNormalNotes() != note.tupletNormalNotes())) return true;
        if (note.beamCount() != 0 || note.unbeamedDurationBeats() < 1) return false;
        for (ScoreNoteEvent a : notes) {
            if (a.measureIndex() != note.measureIndex()
                    || a.staffIndex() != note.staffIndex()
                    || a.staffCount() != note.staffCount()
                    || a.beamCount() != 0
                    || a.unbeamedDurationBeats() < 1) continue;
            for (ScoreNoteEvent b : notes)
                if (b.measureIndex() == a.measureIndex()
                        && b.staffIndex() == a.staffIndex()
                        && b.staffCount() == a.staffCount()
                        && b.beamCount() > 0
                        && independentVoiceOnset(a, b, notes)
                        && writtenDurationBeats(b) < writtenDurationBeats(a)) return true;
        }
        return false;
    }

    /** Repeated coincident dotted/shorter values prove two beamed rhythms, rather than
     * one isolated missing chord dot. Keep the existing dense-onset spacing guard. */
    private static boolean hasRepeatedDottedVoice(List<RhythmGroup> groups) {
        int mixed = 0;
        List<ScoreNoteEvent> voice = new ArrayList<>();
        for (RhythmGroup group : groups) voice.addAll(group.notes);
        for (RhythmGroup group : groups) {
            boolean different = false;
            for (ScoreNoteEvent dotted : group.notes) {
                if (dotted.beamCount() == 0 || dotted.augmentationDots() == 0) continue;
                for (ScoreNoteEvent moving : group.notes) {
                    if (moving.beamCount() > 0
                            && moving.augmentationDots() == 0
                            && writtenDurationBeats(moving) < writtenDurationBeats(dotted)
                            && independentVoiceOnset(dotted, moving, voice)) different = true;
                }
            }
            if (different && ++mixed >= 2) return true;
        }
        return false;
    }

    /** Sounding length belongs to each voice. The shortest coincident value advances
     * the moving attack clock only when every symbol/rest exactly fills this meter. */
    private static double[] parallelWrittenClock(List<RhythmGroup> groups, double beats) {
        if (!Double.isFinite(beats) || !hasRepeatedDottedVoice(groups)) return null;
        double[] result = new double[groups.size()];
        double total = leadingRest(groups);
        for (int i = 0; i < groups.size(); i++) {
            double shortest = Double.POSITIVE_INFINITY;
            for (ScoreNoteEvent note : groups.get(i).notes)
                shortest = Math.min(shortest, writtenDurationBeats(note));
            if (!Double.isFinite(shortest) || shortest <= 0) return null;
            result[i] = shortest;
            total += shortest + followingRest(groups.get(i));
        }
        return Math.abs(total - beats) < .001 ? result : null;
    }

    /** In a dense passage, the fixed onset tolerance can span successive attacks.
     * A mixed-value pair must be closer than half the ordinary attack spacing. */
    private static boolean independentVoiceOnset(
            ScoreNoteEvent a, ScoreNoteEvent b, List<ScoreNoteEvent> notes) {
        float distance = Math.abs(a.positionInMeasure() - b.positionInMeasure());
        if (distance > SAME_ONSET_POSITION) return false;
        if (distance < .00001f) return true;
        List<Float> positions = new ArrayList<>();
        for (ScoreNoteEvent n : notes)
            if (n.measureIndex() == a.measureIndex()
                    && n.staffIndex() == a.staffIndex()
                    && n.staffCount() == a.staffCount()
                    && !grace(n)) positions.add(n.positionInMeasure());
        positions.sort(Float::compare);
        List<Float> gaps = new ArrayList<>();
        for (int i = 1; i < positions.size(); i++) {
            float gap = positions.get(i) - positions.get(i - 1);
            if (gap > .00001f) gaps.add(gap);
        }
        if (gaps.size() < 7) return true;
        gaps.sort(Float::compare);
        // Weight gaps by horizontal span so several slightly offset chord heads
        // cannot outvote the spaces between their actual attacks.
        float span = 0;
        for (float gap : gaps) span += gap;
        float accumulated = 0, ordinary = gaps.get(gaps.size() - 1);
        for (float gap : gaps) {
            accumulated += gap;
            if (accumulated >= span * .5f) {
                ordinary = gap;
                break;
            }
        }
        return distance < ordinary * .5f;
    }

    /** Only a proved beam bridge and a complete single phrase can share a cross-staff clock.
     * Parallel voices, chords, sustained melody and rests keep their independent timing. */
    private static List<ScoreNoteEvent> crossStaffPhrase(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes, double beats) {
        if (target.staffCount() != 2 || !Double.isFinite(beats)) return List.of();
        List<ScoreNoteEvent> phrase = new ArrayList<>();
        int bridges = 0;
        for (ScoreNoteEvent note : measureNotes(target, notes)) {
            // A held melody/bass is a separate voice; it does not break a proved moving beam.
            if (hasIndependentSustain(note)) continue;
            if (note.followingRestBeats() > 0 || note.leadingRestBeats() > 0) return List.of();
            phrase.add(note);
            if (note.crossStaffBeam()) bridges++;
        }
        if (bridges < 2 || phrase.size() < 3) return List.of();
        phrase.sort(Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure));
        if (phrase.get(0).positionInMeasure() > .22f) return List.of();
        double sum = crossStaffClockBeats(phrase);
        if (Double.isFinite(sum) && Math.abs(sum - beats) < .03125) return phrase;
        if (implicitCrossStaffTriplets(phrase, beats)) return phrase;
        // An overlapping dotted held head can contaminate the first moving head's dot/beam.
        // Accept exactly one repair only when the beam, adjacent slots, and total bar agree.
        ScoreNoteEvent first = phrase.get(0), next = phrase.get(1);
        double gap = next.positionInMeasure() - first.positionInMeasure();
        double following = phrase.get(2).positionInMeasure() - next.positionInMeasure();
        if (next.beamCount() > 0
                && first.beamCount() <= next.beamCount()
                && first.tupletDivisor() == next.tupletDivisor()
                && first.tupletNormalNotes() == next.tupletNormalNotes()
                && gap >= following * .75
                && gap <= following * 1.32) {
            double repaired = durationForBeam(rhythmicBeamCount(next), 0) * first.durationScale();
            if (Double.isFinite(sum)
                    && Math.abs(sum - writtenDurationBeats(first) + repaired - beats) < .03125)
                return phrase;
        }
        return List.of();
    }

    /** Coincident voices share an attack; the shorter value advances the moving line. */
    private static double crossStaffClockBeats(List<ScoreNoteEvent> phrase) {
        double total = 0;
        for (int i = 0; i < phrase.size(); ) {
            float position = phrase.get(i).positionInMeasure();
            double advance = Double.POSITIVE_INFINITY;
            do {
                advance = Math.min(advance, writtenDurationBeats(phrase.get(i++)));
            } while (i < phrase.size()
                    && Math.abs(phrase.get(i).positionInMeasure() - position)
                            <= SAME_ONSET_POSITION);
            total += advance;
        }
        return total;
    }

    private static double crossStaffDuration(
            ScoreNoteEvent note, List<ScoreNoteEvent> phrase, double beats) {
        if (implicitCrossStaffTriplets(phrase, beats)) return writtenDurationBeats(note) * 2 / 3;
        double sum = crossStaffClockBeats(phrase);
        if (note.equals(phrase.get(0)) && Math.abs(sum - beats) >= .03125)
            return writtenDurationBeats(note) + beats - sum;
        return writtenDurationBeats(note);
    }

    private static boolean implicitCrossStaffTriplets(List<ScoreNoteEvent> phrase, double beats) {
        List<RhythmGroup> groups = rhythmGroups(phrase);
        double[] values = new double[groups.size()];
        for (int i = 0; i < values.length; i++) values[i] = groups.get(i).writtenDuration();
        return implicitTriplets(groups, values, beats, true);
    }

    public static double absoluteBeat(
            ScoreNoteEvent note, List<ScoreNoteEvent> notes, float beatsPerMeasure) {
        if (note == null) return 0;
        double safeBeats = Math.max(.125, Math.min(128, beatsPerMeasure));
        return Math.max(0, note.measureIndex()) * safeBeats
                + beatInMeasure(note, notes, beatsPerMeasure);
    }

    private static double followingRest(RhythmGroup group) {
        double rest = 0;
        for (ScoreNoteEvent note : group.attacks())
            rest = Math.max(rest, note.followingRestBeats());
        return rest;
    }

    private static double leadingRest(List<RhythmGroup> groups) {
        if (groups.isEmpty()) return 0;
        List<ScoreNoteEvent> notes = groups.get(0).notes;
        double rest = Double.NEGATIVE_INFINITY;
        for (ScoreNoteEvent note : notes) rest = Math.max(rest, note.leadingRestBeats());
        return notes.isEmpty() ? 0 : rest;
    }

    /** A complete sequence of explicitly read values is an anchor even without rests. */
    private static boolean completeWrittenRhythm(List<RhythmGroup> groups, double beats) {
        if (groups.isEmpty() || !Double.isFinite(beats)) return false;
        double total = leadingRest(groups);
        for (RhythmGroup group : groups) {
            double written = group.writtenDuration();
            if (!Double.isFinite(written) || written <= 0) return false;
            total += written + followingRest(group);
        }
        if (Math.abs(total - beats) >= .001) return false;
        // Four falsely unflagged short notes can coincidentally sum to a bar
        // while occupying only its opening. Require the written sequence to
        // span the bar before letting it override a neighbouring clock.
        double expectedSpan = (beats - groups.get(groups.size() - 1).writtenDuration()) / beats;
        double printedSpan = groups.get(groups.size() - 1).position - groups.get(0).position;
        return expectedSpan <= 0 || printedSpan >= expectedSpan * .8;
    }

    private static boolean completePrintedRestRhythm(List<RhythmGroup> groups, double beats) {
        double total = leadingRest(groups), silence = total;
        for (RhythmGroup group : groups) {
            double rest = followingRest(group);
            silence += rest;
            total += group.writtenDuration() + rest;
        }
        return silence > 0
                && Double.isFinite(total)
                && Double.isFinite(beats)
                && Math.abs(total - beats) < .03125;
    }

    /** Matching explicit closing silence on every stave proves a shorter written bar.
     * Its engraving may fill a nominal full-bar width, so positions cannot add beats. */
    private static boolean sharedShortRestRhythm(
            ScoreNoteEvent target,
            List<ScoreNoteEvent> notes,
            List<RhythmGroup> groups,
            double beats) {
        if (target.staffCount() < 2 || !Double.isFinite(beats) || groups.isEmpty()) return false;
        double span = restBoundedSpan(groups);
        if (!Double.isFinite(span) || span <= 0 || span >= beats - .03125) return false;
        for (int staff = 0; staff < target.staffCount(); staff++)
            if (staff != target.staffIndex()) {
                var voice = new ArrayList<ScoreNoteEvent>();
                for (ScoreNoteEvent note : notes)
                    if (note.measureIndex() == target.measureIndex()
                            && note.staffIndex() == staff
                            && note.staffCount() == target.staffCount()
                            && !grace(note)) voice.add(note);
                double other = restBoundedSpan(rhythmGroups(voice));
                if (!Double.isFinite(other) || Math.abs(other - span) >= .03125) return false;
            }
        return true;
    }

    private static double restBoundedSpan(List<RhythmGroup> groups) {
        if (groups.isEmpty() || followingRest(groups.get(groups.size() - 1)) <= 0)
            return Double.NaN;
        double span = leadingRest(groups);
        for (RhythmGroup group : groups) {
            double written = group.writtenDuration();
            if (!Double.isFinite(written) || written <= 0) return Double.NaN;
            span += written + followingRest(group);
        }
        return span;
    }

    /** Quarter-note beats encoded by flags/beams and one or two augmentation dots. */
    public static double writtenDurationBeats(ScoreNoteEvent note) {
        if (note == null) return Double.NaN;
        double base =
                rhythmicBeamCount(note) > 0
                        ? 1.0 / (1 << rhythmicBeamCount(note))
                        : note.unbeamedDurationBeats() > 0
                                ? note.unbeamedDurationBeats()
                                : note.augmentationDots() > 0 ? 1.0 : Double.NaN;
        if (!Double.isFinite(base)) return Double.NaN;
        return base * dotMultiplier(note.augmentationDots()) * note.durationScale();
    }

    /** Finest written subdivision in one staff/measure, capped at the ordinary 16th-note grid. */
    static double rhythmicGrid(List<ScoreNoteEvent> voice) {
        double grid = .25;
        double minimum = .0625;
        if (voice == null) return grid;
        for (ScoreNoteEvent note : voice) {
            if (note.tupletDivisor() == 3 || note.tupletDivisor() == 6)
                grid = Math.min(grid, 1.0 / 12.0);
            if (note.tupletDivisor() == 5) {
                grid = Math.min(grid, 1.0 / 20.0);
                minimum = Math.min(minimum, 1.0 / 20.0);
            }
            if (note.tupletDivisor() == 7) {
                grid = Math.min(grid, 1.0 / 28.0);
                minimum = 1.0 / 28.0;
            }
            double written = writtenDurationBeats(note);
            if (Double.isFinite(written))
                grid =
                        Math.min(
                                grid,
                                rhythmicBeamCount(note) <= 0
                                        ? .25
                                        : 1.0 / (1 << rhythmicBeamCount(note)));
        }
        return Math.max(minimum, grid);
    }

    private static List<ScoreNoteEvent> measureVoice(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes) {
        TimingSession session = SESSION.get();
        if (session != null)
            return session.index(notes)
                    .voices
                    .getOrDefault(
                            new MeasureStaffKey(
                                    target.measureIndex(),
                                    target.staffIndex(),
                                    target.staffCount()),
                            List.of());
        List<ScoreNoteEvent> voice = new ArrayList<>();
        for (ScoreNoteEvent note : notes)
            if (note != null
                    && note.measureIndex() == target.measureIndex()
                    && note.staffIndex() == target.staffIndex()
                    && note.staffCount() == target.staffCount()) voice.add(note);
        voice.sort(
                Comparator.comparingDouble(ScoreNoteEvent::positionInMeasure)
                        .thenComparingInt(ScoreNoteEvent::staffStep));
        return voice;
    }

    private static List<ScoreNoteEvent> measureNotes(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes) {
        TimingSession session = SESSION.get();
        if (session != null)
            return session.index(notes)
                    .measures
                    .getOrDefault(
                            new MeasureKey(target.measureIndex(), target.staffCount()), List.of());
        List<ScoreNoteEvent> result = new ArrayList<>();
        for (ScoreNoteEvent note : notes)
            if (note != null
                    && note.measureIndex() == target.measureIndex()
                    && note.staffCount() == target.staffCount()) result.add(note);
        return result;
    }

    private static List<RhythmGroup> rhythmGroups(List<ScoreNoteEvent> voice) {
        TimingSession session = SESSION.get();
        return session == null ? buildRhythmGroups(voice) : session.groups(voice);
    }

    /** Groups are read-only after assembly; reuse them only for an unchanged session snapshot. */
    private static List<RhythmGroup> buildRhythmGroups(List<ScoreNoteEvent> voice) {
        List<RhythmGroup> groups = new ArrayList<>();
        for (ScoreNoteEvent note : voice) {
            RhythmGroup group = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (group == null || note.positionInMeasure() - group.position > SAME_ONSET_POSITION) {
                group = new RhythmGroup(note.positionInMeasure());
                groups.add(group);
            }
            group.add(note);
        }
        return groups;
    }

    private static double[] stabilizedDurations(List<RhythmGroup> groups) {
        return stabilizedDurations(groups, Double.NaN);
    }

    private static double[] stabilizedDurations(List<RhythmGroup> groups, double beatsPerMeasure) {
        double[] raw = new double[groups.size()];
        double[] result = new double[groups.size()];
        for (int index = 0; index < groups.size(); index++)
            raw[index] = result[index] = groups.get(index).writtenDuration();
        if (groups.size() < 3) return result;
        double written = 0;
        for (double value : raw) written += value;
        // Chord heads share one attack/rest slot. Known silence occupies bar time just as
        // notes do; omitting it makes otherwise sound short flags look like missing beats.
        double silence = leadingRest(groups);
        for (RhythmGroup group : groups) silence += followingRest(group);
        double noteBudget =
                Double.isFinite(beatsPerMeasure)
                                && Double.isFinite(silence)
                                && silence >= 0
                                && silence < beatsPerMeasure
                        ? beatsPerMeasure - silence
                        : beatsPerMeasure;
        double[] optical = raw.clone();
        // Exact symbol accounting is stronger than a spacing-based repair vote. Otherwise
        // eighth-quarter-eighth figures that already fill the bar become six equal eighths.
        if (Double.isFinite(beatsPerMeasure)
                && Double.isFinite(written)
                && Math.abs(written - noteBudget) < .001) return result;
        double[] continued = continuedPrintedTuplets(groups, raw, beatsPerMeasure);
        if (continued != null) return continued;
        // Repeated equal triplets are sometimes engraved without another numeral.
        // Accept only a complete, uniform beamed lane whose exact 3:2 ratio fills
        // the selected meter; arbitrary overfull or partly missing bars stay optical.
        if (implicitTriplets(groups, raw, beatsPerMeasure)) {
            for (int i = 0; i < result.length; i++) result[i] *= 2.0 / 3.0;
            return result;
        }
        // A single locally contradicted beam that exactly repairs an overfull bar is stronger
        // evidence than a measure-wide spacing vote. Preserve the opening quarter and mixed
        // eighth/sixteenth groups instead of flattening the entire phrase.
        if (Double.isFinite(noteBudget) && written > noteBudget) {
            for (int index = 1; index + 1 < groups.size(); index++) {
                RhythmGroup current = groups.get(index);
                int left = groups.get(index - 1).beamCount(),
                        right = groups.get(index + 1).beamCount();
                if (left <= 0
                        || left != right
                        || current.hasReliableLongDuration()
                        || current.augmentationDots() > 0
                        || current.hasTuplet()
                        || groups.get(index - 1).hasTuplet()
                        || groups.get(index + 1).hasTuplet()
                        || !denseMissingBeam(groups, index)) continue;
                double candidate = durationForBeam(left, 0);
                if (Math.abs(written - raw[index] + candidate - noteBudget) < .001) {
                    result[index] = candidate;
                    return result;
                }
            }
        }

        // The model can miss one end of a sloped two-note beam. Treat a dense note surrounded by
        // the same subdivision as part of that run. If the supposed dot is followed by an
        // ordinary short slot, it is an articulation/ink fragment rather than duration evidence.
        for (int index = 1; index + 1 < groups.size(); index++) {
            RhythmGroup current = groups.get(index);
            if (current.beamCount() > 0
                    || current.hasReliableLongDuration()
                    || current.hasTuplet()
                    || hasEngravedQuarterSlot(groups, index)) continue;
            if (groups.get(index - 1).hasTuplet() || groups.get(index + 1).hasTuplet()) continue;
            int leftBeams = groups.get(index - 1).beamCount();
            int rightBeams = groups.get(index + 1).beamCount();
            if (leftBeams <= 0 || leftBeams != rightBeams || !denseMissingBeam(groups, index))
                continue;
            raw[index] =
                    result[index] = durationForBeam(leftBeams, recoveredBeamDots(groups, index));
        }
        // At the first/last note there are not two neighbours to vote. Recover only when the
        // adjacent beamed note and the local engraved slot agree with the measure's ordinary gap.
        recoverEdgeBeam(groups, raw, result, 0, 1);
        recoverEdgeBeam(groups, raw, result, groups.size() - 1, groups.size() - 2);
        stabilizeEqualSpacingRuns(groups, raw, result);
        raiseToGeometricSubdivisionFloor(groups, result, beatsPerMeasure);
        // Engraving is not proportional time. A compressed quarter followed by a long run
        // may look like another short slot. Reject a repair set that makes known symbol
        // accounting worse (or merely swaps underfill for equal overfill).
        if (Double.isFinite(beatsPerMeasure)
                && Double.isFinite(written)
                && groups.get(groups.size() - 1).position - groups.get(0).position > .65f) {
            double repaired = 0;
            for (double value : result) repaired += value;
            double rawError = Math.abs(written - noteBudget);
            double repairedError = Math.abs(repaired - noteBudget);
            if (Double.isFinite(repaired)
                    && repairedError > .001
                    && repairedError >= rawError - .001) return optical;
        }
        return result;
    }

    /** Two complete printed groups can establish a repeated, unnumbered beamed tail. */
    private static double[] continuedPrintedTuplets(
            List<RhythmGroup> groups, double[] raw, double beats) {
        if (!Double.isFinite(beats)
                || beats < 2
                || beats > 8
                || groups.size() < 10
                || groups.get(0).position > .18f
                || groups.get(groups.size() - 1).position < .8f) return null;
        int start = 0;
        double prefix = 0;
        while (start < groups.size() && groups.get(start).beamCount() == 0) {
            var group = groups.get(start);
            if (group.hasTuplet()
                    || group.augmentationDots() != 0
                    || raw[start] < 1
                    || raw[start] > 4) return null;
            for (var n : group.notes)
                if (n.leadingRestBeats() > 0
                        || n.followingRestBeats() > 0
                        || n.crossStaffBeam()
                        || grace(n)
                        || n.tiedFromPrevious()
                        || Math.abs(writtenDurationBeats(n) - raw[start]) > .001) return null;
            prefix += raw[start++];
        }
        int count = groups.size() - start;
        if (start == 0 || count < 9 || count % 3 != 0) return null;
        int beams = groups.get(start).beamCount();
        if (beams != 1 && beams != 2) return null;
        double unit = beams == 1 ? .5 : .25;
        if (Math.abs(prefix + count * unit * 2 / 3 - beats) > .001) return null;
        int printed = 0;
        boolean tail = false;
        float smallest = Float.MAX_VALUE, largest = 0;
        double[] result = raw.clone();
        for (int i = start; i < groups.size(); i++) {
            var group = groups.get(i);
            int ratio = 0, direction = 0;
            if (group.beamCount() != beams || group.augmentationDots() != 0) return null;
            for (var n : group.notes) {
                int thisRatio =
                        n.tupletDivisor() == 3 && n.tupletNormalNotes() == 2
                                ? 3
                                : n.tupletDivisor() == 1 && n.tupletNormalNotes() == 1 ? 1 : 0;
                if (thisRatio == 0
                        || ratio != 0 && ratio != thisRatio
                        || n.beamCount() != beams
                        || n.unbeamedDurationBeats() != 0
                        || n.augmentationDots() != 0
                        || n.leadingRestBeats() > 0
                        || n.followingRestBeats() > 0
                        || n.crossStaffBeam()
                        || grace(n)
                        || n.tiedFromPrevious()
                        || n.stemDirection() == 0
                        || direction != 0 && direction != n.stemDirection()) return null;
                ratio = thisRatio;
                direction = n.stemDirection();
            }
            if ((i - start) % 3 != 0) {
                var previous = groups.get(i - 1).notes.get(0);
                if (previous.stemDirection() != direction || previous.tupletDivisor() != ratio)
                    return null;
            }
            if (ratio == 3) {
                if (tail) return null;
                printed++;
            } else {
                if (printed < 6 || printed % 3 != 0) return null;
                tail = true;
                result[i] = unit * 2 / 3;
            }
            if (i > start) {
                float gap = group.position - groups.get(i - 1).position;
                smallest = Math.min(smallest, gap);
                largest = Math.max(largest, gap);
            }
        }
        return tail && smallest > SAME_ONSET_POSITION && largest <= smallest * 2.4f ? result : null;
    }

    private static boolean implicitTriplets(
            List<RhythmGroup> groups, double[] values, double beats) {
        return implicitTriplets(groups, values, beats, false);
    }

    private static boolean implicitTriplets(
            List<RhythmGroup> groups, double[] values, double beats, boolean sharedPhrase) {
        if (!Double.isFinite(beats)
                || beats < 2
                || beats > 4
                || groups.size() < 6
                || groups.size() % 3 != 0
                || groups.get(0).position > .18f
                || groups.get(groups.size() - 1).position < .8f) return false;
        double unit = values[0];
        if (unit != .5 && unit != .25 || Math.abs(unit * groups.size() * 2 / 3 - beats) > .001)
            return false;
        float smallest = Float.MAX_VALUE, largest = 0;
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            if (values[i] != unit || group.hasTuplet() || group.augmentationDots() != 0)
                return false;
            for (var n : group.notes)
                if (n.beamCount() < 1
                        || n.leadingRestBeats() > 0
                        || n.followingRestBeats() > 0
                        || n.crossStaffBeam() && !sharedPhrase
                        || n.tiedFromPrevious()
                        || grace(n)) return false;
            if (i > 0) {
                float distance = group.position - groups.get(i - 1).position;
                smallest = Math.min(smallest, distance);
                largest = Math.max(largest, distance);
            }
        }
        return smallest > 0 && largest <= smallest * 2.4f;
    }

    private static double openingPickupStart(
            ScoreNoteEvent target, List<ScoreNoteEvent> notes, double beats) {
        if (!target.compactOpening()) return Double.NaN;
        List<ScoreNoteEvent> opening =
                measureNotes(target, notes).stream()
                        .filter(n -> !grace(n))
                        .collect(java.util.stream.Collectors.toList());
        if (opening.isEmpty()
                || opening.stream()
                        .anyMatch(
                                n ->
                                        !n.compactOpening()
                                                || n.leadingRestBeats() > 0
                                                || n.followingRestBeats() > 0
                                                || n.tiedFromPrevious())) return Double.NaN;
        double span = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ScoreNoteEvent note : opening) {
            if (!seen.add(note.staffIndex() + ":" + note.staffCount())) continue;
            double voice = 0;
            for (RhythmGroup group : rhythmGroups(measureVoice(note, opening))) {
                double duration = group.writtenDuration();
                if (!Double.isFinite(duration) || duration <= 0) return Double.NaN;
                voice += duration;
            }
            // Every printed part must account for the same pickup span. Otherwise an omitted
            // rest or a separate voice still needs resolving before changing the shared clock.
            if (span > 0 && Math.abs(span - voice) > .0001) return Double.NaN;
            span = voice;
        }
        return span > 0 && span < beats - .0001 ? beats - span : Double.NaN;
    }

    /** A complete, evenly engraved short-note tail after a leading rest ends at the barline. */
    private static double contiguousTailRunStart(
            List<RhythmGroup> groups, double[] durations, double beats) {
        if (groups.size() < 3
                || groups.get(0).position < .22f
                || groups.get(groups.size() - 1).position < .88f) return Double.NaN;
        double total = 0, minGap = Double.MAX_VALUE, maxGap = 0;
        int beams = groups.get(0).beamCount();
        if (beams <= 0) return Double.NaN;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).beamCount() != beams
                    || groups.get(i).augmentationDots() != 0
                    || !Double.isFinite(durations[i])
                    || Math.abs(durations[i] - durations[0]) > .0001) return Double.NaN;
            total += durations[i];
            if (i > 0) {
                double gap = groups.get(i).position - groups.get(i - 1).position;
                minGap = Math.min(minGap, gap);
                maxGap = Math.max(maxGap, gap);
            }
        }
        if (minGap <= SAME_ONSET_POSITION
                || maxGap > minGap * 1.30
                || 1 - groups.get(groups.size() - 1).position > minGap * .90
                || total >= beats
                || total < beats * .25) return Double.NaN;
        double start = beats - total;
        // Only correct modest engraving padding; never fill large missing-note/rest slots.
        return Math.abs(start - groups.get(0).position * beats) <= .35 ? start : Double.NaN;
    }

    /**
     * If attacks visibly demonstrate a regular subdivision, one fragmented optical beam must not
     * make a tone shorter than that unit. This fixes the common pattern where the attack clock is
     * correct but a 16th is released as a 32nd. A written rest still remains silent because gaps
     * larger than the smallest ordinary slot are never filled.
     */
    private static void raiseToGeometricSubdivisionFloor(
            List<RhythmGroup> groups, double[] durations, double beatsPerMeasure) {
        if (groups == null
                || groups.size() < 4
                || durations == null
                || durations.length != groups.size()
                || !Double.isFinite(beatsPerMeasure)) return;
        if (groups.get(groups.size() - 1).position - groups.get(0).position < .68f) return;
        List<Double> gaps = new ArrayList<>();
        for (int index = 0; index + 1 < groups.size(); index++) {
            double gap = groups.get(index + 1).position - groups.get(index).position;
            if (gap > SAME_ONSET_POSITION) gaps.add(gap);
        }
        if (gaps.size() < 3) return;
        gaps.sort(Double::compare);
        double representative = gaps.get((gaps.size() - 1) / 4) * beatsPerMeasure;
        double floor = nearestWrittenSubdivision(representative);
        if (!Double.isFinite(floor)) return;
        // Require actual matching symbols, not solely a page-width calculation. In particular,
        // a mostly-sixteenth passage must not become all eighths because it is widely engraved.
        int support = 0;
        for (RhythmGroup group : groups)
            if (group.beamCount() > 0
                    && !group.hasTuplet()
                    && sameDuration(group.writtenDuration(), floor)) support++;
        if (support < 2) return;
        double proposed = 0;
        for (int index = 0; index < groups.size(); index++) {
            RhythmGroup group = groups.get(index);
            double value = durations[index];
            if (group.augmentationDots() == 0
                    && !group.hasReliableLongDuration()
                    && !group.hasTuplet()
                    && Double.isFinite(value)
                    && value > 0
                    && value < floor) value = floor;
            proposed += value;
        }
        if (Double.isFinite(proposed) && proposed > beatsPerMeasure + .001) return;
        for (int index = 0; index < groups.size(); index++) {
            RhythmGroup group = groups.get(index);
            if (group.augmentationDots() > 0
                    || group.hasReliableLongDuration()
                    || group.hasTuplet()) continue;
            if (Double.isFinite(durations[index])
                    && durations[index] > 0
                    && durations[index] < floor) durations[index] = floor;
        }
    }

    private static double nearestWrittenSubdivision(double beats) {
        if (!Double.isFinite(beats) || beats <= 0) return Double.NaN;
        double[] values = {.125, .25, .5, 1, 2, 4};
        double best = values[0], error = Double.MAX_VALUE;
        for (double value : values) {
            double candidateError = Math.abs(Math.log(beats / value) / Math.log(2));
            if (candidateError < error) {
                error = candidateError;
                best = value;
            }
        }
        return best;
    }

    /**
     * Printed notes that own the same horizontal slot normally have the same subdivision even
     * when a slur, staff-line crossing, or beam angle damages one optical beam count. A previous
     * immediate-neighbour vote could create a checkerboard: 1,3,1 made the middle note 1 while
     * its neighbour saw 3,1,3 and became 3. Vote across every comparable slot in the measure and
     * prefer the longer value on an exact tie; synthesis is still capped at the next attack, so
     * this avoids audible holes without allowing overlap.
     */
    private static void stabilizeEqualSpacingRuns(
            List<RhythmGroup> groups, double[] raw, double[] result) {
        if (groups == null || groups.size() < 3) return;
        for (int index = 0; index < groups.size(); index++) {
            RhythmGroup current = groups.get(index);
            if (current.augmentationDots() > 0
                    || current.hasReliableLongDuration()
                    || current.hasTuplet()
                    || hasEngravedQuarterSlot(groups, index)) continue;
            double slot = ownedPositionGap(groups, index);
            if (!Double.isFinite(slot) || slot <= 0) continue;
            int[] votes = new int[4];
            int comparable = 0;
            for (int candidate = 0; candidate < groups.size(); candidate++) {
                double candidateSlot = ownedPositionGap(groups, candidate);
                if (!similarGap(slot, candidateSlot)) continue;
                int beams = groups.get(candidate).beamCount();
                if (beams <= 0
                        || beams >= votes.length
                        || groups.get(candidate).augmentationDots() > 0
                        || groups.get(candidate).hasTuplet()) continue;
                votes[beams]++;
                comparable++;
            }
            if (comparable < 3) continue;
            int consensusBeams = 1;
            for (int beams = 2; beams < votes.length; beams++)
                if (votes[beams] > votes[consensusBeams]) consensusBeams = beams;
            if (votes[consensusBeams] < 2) continue;
            result[index] = durationForBeam(consensusBeams, 0);
        }
    }

    private static double ownedPositionGap(List<RhythmGroup> groups, int index) {
        if (groups == null || groups.size() < 2 || index < 0 || index >= groups.size())
            return Double.NaN;
        // The gap before the final attack belongs to the previous note/rest, not the final
        // note's written value. Without a following slot there is no independent spacing vote.
        if (index + 1 >= groups.size()) return Double.NaN;
        return groups.get(index + 1).position - groups.get(index).position;
    }

    /**
     * Protects an isolated written quarter between two matching beamed figures. The optical pass
     * deliberately reports a filled, unbeamed head as a quarter; ordinarily the stabilizer may
     * reinterpret that as one missed beam in an even run. Here the following engraved slot is
     * about twice the surrounding beamed-note spacing, which is independent evidence that the
     * quarter really owns a full beat. Collapsing it to an eighth leaves the second half silent
     * even though the next attack remains correctly positioned.
     */
    private static boolean hasEngravedQuarterSlot(List<RhythmGroup> groups, int index) {
        if (groups == null || index <= 0 || index + 1 >= groups.size()) return false;
        RhythmGroup current = groups.get(index);
        RhythmGroup previous = groups.get(index - 1);
        RhythmGroup next = groups.get(index + 1);
        if (current.beamCount() != 0
                || current.augmentationDots() != 0
                || !sameDuration(current.writtenDuration(), ScoreNoteEvent.DURATION_QUARTER))
            return false;
        int surroundingBeams = previous.beamCount();
        if (surroundingBeams <= 0 || next.beamCount() != surroundingBeams) return false;

        double incoming = current.position - previous.position;
        double ownedSlot = next.position - current.position;
        double nextFastSlot;
        if (index + 2 < groups.size()) {
            RhythmGroup afterNext = groups.get(index + 2);
            if (afterNext.beamCount() != surroundingBeams) return false;
            nextFastSlot = afterNext.position - next.position;
        } else {
            // A quarter before the final eighth still owns its following slot. At this edge,
            // use the preceding proved pair instead of requiring a note beyond the barline.
            if (index < 2 || groups.get(index - 2).beamCount() != surroundingBeams) return false;
            nextFastSlot = previous.position - groups.get(index - 2).position;
        }
        if (incoming <= 0 || ownedSlot <= 0 || nextFastSlot <= 0) return false;
        double fastSlot = (incoming + nextFastSlot) * .5;
        return Math.max(incoming, nextFastSlot) / Math.min(incoming, nextFastSlot) <= 1.48
                && ownedSlot >= fastSlot * (index + 2 < groups.size() ? 1.55 : 1.50)
                && ownedSlot <= fastSlot * 2.65;
    }

    private static boolean denseMissingBeam(List<RhythmGroup> groups, int index) {
        double left = groups.get(index).position - groups.get(index - 1).position;
        double right = groups.get(index + 1).position - groups.get(index).position;
        if (left <= 0 || right <= 0) return false;
        return Math.max(left, right) <= .18
                && Math.max(left, right) / Math.min(left, right) <= 3.25;
    }

    private static void recoverEdgeBeam(
            List<RhythmGroup> groups, double[] raw, double[] result, int index, int neighbor) {
        RhythmGroup current = groups.get(index);
        int beams = groups.get(neighbor).beamCount();
        if (current.beamCount() > 0
                || current.hasReliableLongDuration()
                || current.hasTuplet()
                || groups.get(neighbor).hasTuplet()
                || beams <= 0) return;
        double gap = Math.abs(current.position - groups.get(neighbor).position);
        double ordinary = ordinaryPositionGap(groups);
        if (!Double.isFinite(ordinary) || gap > ordinary * 1.45) return;
        raw[index] = result[index] = durationForBeam(beams, recoveredBeamDots(groups, index));
    }

    private static int recoveredBeamDots(List<RhythmGroup> groups, int index) {
        int dots = groups.get(index).augmentationDots();
        if (dots <= 0 || index + 1 >= groups.size()) return dots;
        double nextGap = groups.get(index + 1).position - groups.get(index).position;
        double ordinary = ordinaryPositionGap(groups);
        // A real dotted value normally reserves a visibly longer following slot. This check is
        // deliberately limited to notes whose beam was already proven missing by neighbours.
        return Double.isFinite(ordinary) && nextGap <= ordinary * 1.32 ? 0 : dots;
    }

    private static double ordinaryPositionGap(List<RhythmGroup> groups) {
        List<Double> gaps = new ArrayList<>();
        for (int index = 1; index < groups.size(); index++) {
            double gap = groups.get(index).position - groups.get(index - 1).position;
            if (gap > .001) gaps.add(gap);
        }
        if (gaps.isEmpty()) return Double.NaN;
        gaps.sort(Double::compare);
        return gaps.get((gaps.size() - 1) / 2);
    }

    /** The optical decoder requires four complete, independently aligned ink cores
     * before emitting a fourth beam. Preserve that proved 64th-note value here. */
    private static int rhythmicBeamCount(ScoreNoteEvent note) {
        return note == null ? 0 : Math.max(0, Math.min(4, note.beamCount()));
    }

    private static double durationForBeam(int beams, int dots) {
        return 1.0 / (1 << Math.max(1, Math.min(4, beams))) * dotMultiplier(dots);
    }

    private static double dotMultiplier(int dots) {
        return dots >= 2 ? 1.75 : dots == 1 ? 1.5 : 1.0;
    }

    private static boolean startsAtBarline(
            ScoreNoteEvent target,
            List<ScoreNoteEvent> allNotes,
            List<RhythmGroup> groups,
            double[] durations,
            double beatsPerMeasure,
            float correctedFirstPosition) {
        if (groups.isEmpty()) return false;
        // A fully written measure cannot also contain a leading rest without overflowing.
        if (fillsMeasure(durations, beatsPerMeasure)) return true;

        float learnedInset = learnedLeadingInset(target, allNotes);
        float ordinaryLimit = MAX_ORDINARY_MEASURE_INSET;
        if (Float.isFinite(learnedInset) && learnedInset <= MAX_LEARNABLE_MEASURE_INSET)
            ordinaryLimit = Math.max(ordinaryLimit, learnedInset + LEARNED_INSET_TOLERANCE);
        return correctedFirstPosition <= ordinaryLimit;
    }

    private static boolean fillsMeasure(double[] durations, double beatsPerMeasure) {
        if (durations == null || durations.length == 0) return false;
        double writtenBeats = 0;
        for (double duration : durations) {
            if (!Double.isFinite(duration)) return false;
            writtenBeats += duration;
        }
        return writtenBeats >= beatsPerMeasure - .25 && writtenBeats <= beatsPerMeasure + .5;
    }

    private static boolean overfillsMeasure(double[] durations, double beatsPerMeasure) {
        if (durations == null || durations.length == 0) return false;
        double writtenBeats = 0;
        for (double duration : durations) {
            if (!Double.isFinite(duration)) return false;
            writtenBeats += duration;
        }
        return writtenBeats > beatsPerMeasure + .5;
    }

    /** If only a small amount of written rhythm was recognized but those notes visibly occupy
     * most of the measure, some fast heads were missed. Preserve their empty rhythmic slots by
     * using normalized in-measure geometry instead of packing every surviving note at the front. */
    private static boolean underDetectedMeasure(
            List<RhythmGroup> groups,
            double[] durations,
            double beatsPerMeasure,
            double grid,
            float systemHeaderShift,
            boolean startsAtBarline,
            double firstSpatial) {
        if (groups == null
                || groups.size() < 3
                || durations == null
                || durations.length != groups.size()) return false;
        double written = 0;
        for (double duration : durations) {
            if (!Double.isFinite(duration) || duration <= 0) return false;
            written += duration;
        }
        double lastPosition =
                correctedSystemPosition(groups.get(groups.size() - 1).position, systemHeaderShift);
        double lastSpatial = quantize(lastPosition * beatsPerMeasure, grid);
        if (startsAtBarline) lastSpatial = Math.max(0, lastSpatial - firstSpatial);
        // Dense passages with repeated short rests can contain three quarters of a measure in
        // sounding notes while the remaining quarter is distributed between them. Packing a
        // 75%-full optical measure moves every rest to the tail (the Humoresque regression).
        // Preserve geometry through that narrow incomplete range when the final note still
        // visibly reaches the end of the bar.
        return written <= beatsPerMeasure * .78
                && lastSpatial >= beatsPerMeasure * .68
                && lastSpatial >= written + grid * .75;
    }

    /** Learns this engraved measure's ordinary horizontal space per written beat. The lower
     * quartile ignores deliberately enlarged rest gaps and makes the estimate independent of the
     * absolute measure width chosen by the publisher. */
    private static double learnedPositionPerBeat(
            List<RhythmGroup> groups, double[] durations, double beatsPerMeasure) {
        List<Double> ratios = new ArrayList<>();
        for (int index = 0; index + 1 < groups.size() && index < durations.length; index++) {
            double duration = durations[index];
            double gap = groups.get(index + 1).position - groups.get(index).position;
            if (!Double.isFinite(duration) || duration <= 0 || gap <= 0) continue;
            double ratio = gap / duration;
            if (Double.isFinite(ratio) && ratio >= .006 && ratio <= 2) ratios.add(ratio);
        }
        if (ratios.isEmpty()) return 1.0 / Math.max(.125, beatsPerMeasure);
        ratios.sort(Double::compare);
        return ratios.get((ratios.size() - 1) / 4);
    }

    /** Reads silent rhythmic slots from relative engraving rather than absolute measure width.
     * One missing 16th is a .25-beat slot and one missing eighth is .5 beats. */
    private static double inferredShortRest(
            double positionGap, double previousDuration, double positionPerBeat, double grid) {
        if (!Double.isFinite(positionGap)
                || !Double.isFinite(previousDuration)
                || !Double.isFinite(positionPerBeat)
                || positionGap <= 0
                || previousDuration <= 0
                || positionPerBeat <= 0) return 0;
        double engravedBeats = positionGap / positionPerBeat;
        double missing = engravedBeats - previousDuration;
        double ordinaryGap = previousDuration * positionPerBeat;
        if (!Double.isFinite(missing) || missing < grid * .68 || positionGap < ordinaryGap * 1.88)
            return 0;
        double quantized = quantize(missing, grid);
        // This optical inference is intentionally scoped to short rests. Larger gaps remain
        // spatial evidence because they can be line-layout padding, lyrics, or an OMR omission.
        return quantized >= grid && quantized <= 1.0 + grid * .25 ? quantized : 0;
    }

    /**
     * The first measure on a new printed system can retain clef/key/time-signature width.
     * Learn that shared horizontal shift from the page's systems and remove only the header;
     * extra distance beyond it remains available for a real leading rest.
     */
    private static float systemHeaderShift(ScoreNoteEvent target, List<ScoreNoteEvent> allNotes) {
        TimingSession session = SESSION.get();
        StaffKey staffKey = new StaffKey(target.staffIndex(), target.staffCount());
        if (session != null) {
            var profiles =
                    session.systemProfiles.computeIfAbsent(
                            allNotes, ignored -> new java.util.HashMap<>());
            SystemProfile profile = profiles.get(staffKey);
            if (profile == null) {
                profile =
                        systemProfile(
                                session.index(allNotes).staves.getOrDefault(staffKey, List.of()));
                profiles.put(staffKey, profile);
            }
            return profile.openingMeasures().contains(target.measureIndex()) ? profile.shift() : 0;
        }
        List<ScoreNoteEvent> sameStaff = new ArrayList<>();
        for (ScoreNoteEvent note : allNotes)
            if (note != null
                    && note.staffIndex() == target.staffIndex()
                    && note.staffCount() == target.staffCount()
                    && Float.isFinite(note.positionInMeasure())
                    && Float.isFinite(note.pageY())) sameStaff.add(note);
        sameStaff.sort(
                Comparator.comparingInt(ScoreNoteEvent::measureIndex)
                        .thenComparingDouble(ScoreNoteEvent::positionInMeasure));
        SystemProfile profile = systemProfile(sameStaff);
        return profile.openingMeasures().contains(target.measureIndex()) ? profile.shift() : 0;
    }

    private static SystemProfile systemProfile(List<ScoreNoteEvent> sameStaff) {
        if (sameStaff.stream().anyMatch(note -> !Float.isFinite(note.pageY()))) {
            List<ScoreNoteEvent> finite = new ArrayList<>();
            for (ScoreNoteEvent note : sameStaff)
                if (Float.isFinite(note.pageY())) finite.add(note);
            sameStaff = finite;
        }
        List<MeasureLayout> measures = new ArrayList<>();
        for (int start = 0; start < sameStaff.size(); ) {
            int measureIndex = sameStaff.get(start).measureIndex();
            float firstPosition = sameStaff.get(start).positionInMeasure();
            float yTotal = 0;
            int end = start;
            while (end < sameStaff.size() && sameStaff.get(end).measureIndex() == measureIndex) {
                yTotal += sameStaff.get(end).pageY();
                end++;
            }
            measures.add(
                    new MeasureLayout(
                            measureIndex, firstPosition, yTotal / Math.max(1, end - start)));
            start = end;
        }
        List<Float> systemFirstPositions = new ArrayList<>();
        List<Float> ordinaryFirstPositions = new ArrayList<>();
        java.util.Set<Integer> openingMeasures = new java.util.HashSet<>();
        for (int index = 0; index < measures.size(); index++) {
            MeasureLayout measure = measures.get(index);
            boolean startsSystem =
                    index == 0
                            || Math.abs(measure.centerY() - measures.get(index - 1).centerY())
                                    >= SYSTEM_CHANGE_Y;
            if (startsSystem) {
                systemFirstPositions.add(measure.firstPosition());
                openingMeasures.add(measure.measureIndex());
            } else ordinaryFirstPositions.add(measure.firstPosition());
        }
        if (systemFirstPositions.size() < 2 || ordinaryFirstPositions.isEmpty())
            return new SystemProfile(0, openingMeasures);
        float normalInset = lowerQuartile(ordinaryFirstPositions);
        float systemInset = lowerQuartile(systemFirstPositions);
        // systemInset = header + (1 - header) * normalInset. Solve for the
        // header rather than merely subtracting the two normalized positions.
        float shift = (systemInset - normalInset) / Math.max(.001f, 1 - normalInset);
        if (!Float.isFinite(shift) || shift < MIN_SYSTEM_HEADER_SHIFT) shift = 0;
        return new SystemProfile(Math.min(MAX_SYSTEM_HEADER_SHIFT, shift), openingMeasures);
    }

    private static float correctedSystemPosition(float position, float headerShift) {
        float safe = Math.max(0, Math.min(1, position));
        if (headerShift <= 0 || headerShift >= 1) return safe;
        return Math.max(0, Math.min(1, (safe - headerShift) / (1 - headerShift)));
    }

    private static float lowerQuartile(List<Float> values) {
        if (values == null || values.isEmpty()) return Float.NaN;
        List<Float> sorted = new ArrayList<>(values);
        sorted.sort(Float::compare);
        return sorted.get((sorted.size() - 1) / 4);
    }

    /** Lower-quartile first-note position estimates normal barline padding without allowing
     * measures that genuinely begin with rests to pull the learned inset to the right. */
    private static float learnedLeadingInset(ScoreNoteEvent target, List<ScoreNoteEvent> allNotes) {
        TimingSession session = SESSION.get();
        StaffKey key = new StaffKey(target.staffIndex(), target.staffCount());
        if (session != null) {
            Float cached = session.leadingInset(allNotes, key);
            if (cached != null) return cached;
            session.leadingInsetCalculations++;
        }
        List<ScoreNoteEvent> sameStaff;
        if (session == null) {
            sameStaff = new ArrayList<>();
            for (ScoreNoteEvent note : allNotes)
                if (note != null
                        && note.staffIndex() == target.staffIndex()
                        && note.staffCount() == target.staffCount()
                        && Float.isFinite(note.positionInMeasure())) sameStaff.add(note);
            sameStaff.sort(
                    Comparator.comparingInt(ScoreNoteEvent::measureIndex)
                            .thenComparingDouble(ScoreNoteEvent::positionInMeasure));
        } else sameStaff = session.index(allNotes).staves.getOrDefault(key, List.of());
        List<Float> firstPositions = new ArrayList<>();
        int previousMeasure = Integer.MIN_VALUE;
        for (ScoreNoteEvent note : sameStaff) {
            if (note.measureIndex() == previousMeasure) continue;
            firstPositions.add(note.positionInMeasure());
            previousMeasure = note.measureIndex();
        }
        if (firstPositions.isEmpty())
            return session == null ? Float.NaN : session.putLeadingInset(allNotes, key, Float.NaN);
        firstPositions.sort(Float::compare);
        float result = firstPositions.get((firstPositions.size() - 1) / 4);
        return session == null ? result : session.putLeadingInset(allNotes, key, result);
    }

    private static boolean sameDuration(double first, double second) {
        return Double.isFinite(first)
                && Double.isFinite(second)
                && Math.abs(first - second) <= .0001;
    }

    private static boolean similarGap(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second) || first <= 0 || second <= 0)
            return false;
        return Math.max(first, second) / Math.min(first, second) <= 1.65;
    }

    private static double quantize(double beat, double grid) {
        return Math.round(beat / grid) * grid;
    }
}
