package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts HOMR's staff/notehead mask into a deliberately transparent diagnostic melody. */
final class OmrScoreInterpreter {
    /** Two staves in one grand staff are close; consecutive compact violin systems are not. */
    private static final float MAX_STAFFS_IN_SYSTEM_SEPARATION_GAPS = 6.75f;

    /** High piano chord tones can extend eight gaps; emission still requires ledger ink. */
    private static final float MAX_HEAD_LEDGER_GAPS = 8f;

    /** Key-signature order is shared only after an inherited accidental needs it. */
    private static final class TieKeyOrder {
        private static final int[] SHARPS = {3, 0, 4, 1, 5, 2, 6};
        private static final int[] FLATS = {6, 2, 5, 1, 4, 0, 3};
    }

    private OmrScoreInterpreter() {}

    static List<PlayingTechniqueDetector.Staff> techniqueStaffs(
            byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
        List<PlayingTechniqueDetector.Staff> result = new ArrayList<>();
        for (Staff staff : findStaffs(labels, gray, width, height, measures))
            result.add(
                    new PlayingTechniqueDetector.Staff(
                            staff.top, staff.bottom, staff.gap, staff.index, staff.count));
        return result;
    }

    static List<ScoreNoteEvent> extract(
            byte[] labels, int width, int height, List<MeasureRegion> measures) {
        return extract(labels, null, width, height, measures);
    }

    static List<ScoreNoteEvent> extract(
            byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
        return analyze(labels, gray, width, height, measures).notes();
    }

    static Analysis analyze(
            byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
        if (labels == null
                || labels.length != width * height
                || width <= 0
                || height <= 0
                || measures == null
                || measures.isEmpty()) return new Analysis(List.of(), List.of());
        List<Staff> staffs = findStaffs(labels, gray, width, height, measures);
        if (staffs.isEmpty()) return new Analysis(List.of(), List.of());
        List<Component> rawHeadComponents =
                findComponents(labels, width, height, OmrMeasurePostProcessor.NOTEHEAD);
        List<Component> recoveredFadedHeads = new ArrayList<>();
        List<Component> recoveredFilledFragmentHeads = new ArrayList<>();
        Map<Component, Integer> recoveredCreaseGraces = new HashMap<>();
        List<Component> recoveredGraceCaps = new ArrayList<>();
        List<Component> originalHeads = List.copyOf(rawHeadComponents);
        for (Component first : originalHeads) {
            Staff owner = nearestHeadStaff(staffs, first.centerY);
            if (owner == null) continue;
            for (Component principal : originalHeads) {
                if (first == principal || recoveredCreaseGraces.containsKey(first)) continue;
                CreaseGracePairRecovery.Pair pair =
                        CreaseGracePairRecovery.find(
                                gray,
                                width,
                                height,
                                owner.gap,
                                owner.top,
                                owner.bottom,
                                new CreaseGracePairRecovery.Box(
                                        first.minX,
                                        first.minY,
                                        first.maxX,
                                        first.maxY,
                                        first.centerX,
                                        first.centerY),
                                new CreaseGracePairRecovery.Box(
                                        principal.minX,
                                        principal.minY,
                                        principal.maxX,
                                        principal.maxY,
                                        principal.centerX,
                                        principal.centerY));
                if (pair == null) continue;
                var box = pair.recovered();
                Component second = null;
                for (Component candidate : rawHeadComponents)
                    if (Math.abs(candidate.centerX - box.x()) < owner.gap * .6f
                            && Math.abs(candidate.centerY - box.y()) < owner.gap * .45f) {
                        second = candidate;
                        break;
                    }
                if (second == null) {
                    second =
                            new Component(
                                    (box.right() - box.left() + 1) * (box.bottom() - box.top() + 1),
                                    box.left(),
                                    box.right(),
                                    box.top(),
                                    box.bottom(),
                                    box.x(),
                                    box.y());
                    rawHeadComponents.add(second);
                    recoveredFadedHeads.add(second);
                    recoveredFilledFragmentHeads.add(second);
                }
                recoveredCreaseGraces.put(first, pair.beams());
                recoveredCreaseGraces.put(second, pair.beams());
                for (Component cap : originalHeads)
                    if (cap != first
                            && cap != second
                            && cap.maxX - cap.minX + 1 <= owner.gap
                            && cap.maxY - cap.minY + 1 <= owner.gap * 1.1f
                            && cap.centerX >= pair.firstStem() - owner.gap * .25f
                            && cap.centerX <= pair.firstStem() + owner.gap * .7f
                            && cap.centerY >= pair.firstEnd() - owner.gap * .2f
                            && cap.centerY <= pair.firstEnd() + owner.gap)
                        recoveredGraceCaps.add(cap);
            }
        }
        rawHeadComponents.removeAll(recoveredGraceCaps);
        for (int first = 0; first < rawHeadComponents.size(); first++) {
            Component a = rawHeadComponents.get(first);
            Staff owner = nearestHeadStaff(staffs, a.centerY);
            if (owner == null) continue;
            for (int second = first + 1; second < rawHeadComponents.size(); second++) {
                Component b = rawHeadComponents.get(second);
                ClosedHeadFragmentJoiner.Box joined =
                        ClosedHeadFragmentJoiner.join(
                                gray,
                                width,
                                height,
                                owner.gap,
                                new ClosedHeadFragmentJoiner.Box(
                                        a.minX, a.minY, a.maxX, a.maxY, a.centerX, a.centerY),
                                new ClosedHeadFragmentJoiner.Box(
                                        b.minX, b.minY, b.maxX, b.maxY, b.centerX, b.centerY));
                if (joined == null) continue;
                Component replacement =
                        new Component(
                                a.area + b.area,
                                joined.left(),
                                joined.right(),
                                joined.top(),
                                joined.bottom(),
                                joined.x(),
                                joined.y());
                recoveredFilledFragmentHeads.remove(a);
                recoveredFilledFragmentHeads.remove(b);
                if (joined.filled()) recoveredFilledFragmentHeads.add(replacement);
                rawHeadComponents.set(first, replacement);
                rawHeadComponents.remove(second);
                a = replacement;
                second--;
            }
        }
        for (Staff staff : staffs)
            for (FadedNoteheadRecovery.Head recovered :
                    FadedNoteheadRecovery.find(
                            labels,
                            gray,
                            width,
                            height,
                            staff.gap,
                            Math.round(staff.top - staff.gap),
                            Math.round(staff.bottom + staff.gap))) {
                float x = (recovered.left() + recovered.right()) * .5f,
                        y = (recovered.top() + recovered.bottom()) * .5f;
                if (rawHeadComponents.stream()
                        .anyMatch(
                                head ->
                                        Math.abs(head.centerX - x) < staff.gap * .8f
                                                && Math.abs(head.centerY - y) < staff.gap * .65f))
                    continue;
                Component head =
                        new Component(
                                (recovered.right() - recovered.left() + 1)
                                        * (recovered.bottom() - recovered.top() + 1),
                                recovered.left(),
                                recovered.right(),
                                recovered.top(),
                                recovered.bottom(),
                                x,
                                y);
                rawHeadComponents.add(head);
                recoveredFadedHeads.add(head);
            }
        for (Staff staff : staffs)
            for (ShadedNoteheadRecovery.Head recovered :
                    ShadedNoteheadRecovery.find(
                            labels,
                            gray,
                            width,
                            height,
                            staff.gap,
                            Math.round(staff.top - staff.gap * 2),
                            Math.round(staff.bottom + staff.gap * 2))) {
                float x = recovered.centerX(), y = recovered.centerY();
                Component existing = null;
                for (Component head : rawHeadComponents)
                    if (Math.abs(head.centerX - x) < staff.gap * .8f
                            && Math.abs(head.centerY - y) < staff.gap * .65f) {
                        existing = head;
                        break;
                    }
                if (existing != null) {
                    if (existing.area >= staff.gap * staff.gap * .55f
                            || existing.maxX - existing.minX + 1 >= staff.gap * .95f
                                    && existing.maxY - existing.minY + 1 >= staff.gap * .6f)
                        continue;
                    rawHeadComponents.remove(existing);
                    recoveredFadedHeads.remove(existing);
                    recoveredFilledFragmentHeads.remove(existing);
                }
                // One printed oval can have two disjoint, individually small mask
                // islands. Consume only fragments wholly enclosed by that proven
                // oval; a nearby separate note keeps its own noncontained bounds.
                List<Component> enclosedFragments = new ArrayList<>();
                for (Component fragment : rawHeadComponents)
                    if (fragment.area < staff.gap * staff.gap * .55f
                            && fragment.minX >= recovered.left() - 1
                            && fragment.maxX <= recovered.right() + 1
                            && fragment.minY >= recovered.top() - 1
                            && fragment.maxY <= recovered.bottom() + 1
                            && Math.abs(fragment.centerX - x) < staff.gap * .8f
                            && Math.abs(fragment.centerY - y) < staff.gap * .65f)
                        enclosedFragments.add(fragment);
                rawHeadComponents.removeAll(enclosedFragments);
                recoveredFadedHeads.removeAll(enclosedFragments);
                recoveredFilledFragmentHeads.removeAll(enclosedFragments);
                Component head =
                        new Component(
                                (recovered.right() - recovered.left() + 1)
                                        * (recovered.bottom() - recovered.top() + 1),
                                recovered.left(),
                                recovered.right(),
                                recovered.top(),
                                recovered.bottom(),
                                x,
                                y);
                rawHeadComponents.add(head);
                recoveredFadedHeads.add(head);
                recoveredFilledFragmentHeads.add(head);
            }
        List<Component> recoveredPaleChordHeads = new ArrayList<>();
        Map<Component, Component> paleChordRhythmHeads = new HashMap<>();
        for (Staff staff : staffs)
            for (PaleChordHeadRecovery.Head recovered :
                    PaleChordHeadRecovery.find(
                            labels,
                            gray,
                            width,
                            height,
                            staff.gap,
                            Math.round(staff.top - staff.gap * 2),
                            Math.round(staff.bottom + staff.gap * 2))) {
                Component existing = null;
                for (Component head : rawHeadComponents)
                    if (Math.abs(head.centerX - recovered.centerX()) < staff.gap * .8f
                            && Math.abs(head.centerY - recovered.centerY()) < staff.gap * .65f) {
                        existing = head;
                        break;
                    }
                if (existing != null) {
                    rawHeadComponents.remove(existing);
                    recoveredFadedHeads.remove(existing);
                }
                Component head =
                        new Component(
                                (recovered.right() - recovered.left() + 1)
                                        * (recovered.bottom() - recovered.top() + 1),
                                recovered.left(),
                                recovered.right(),
                                recovered.top(),
                                recovered.bottom(),
                                recovered.centerX(),
                                recovered.centerY());
                rawHeadComponents.add(head);
                recoveredFadedHeads.add(head);
                recoveredPaleChordHeads.add(head);
                paleChordRhythmHeads.put(
                        head,
                        new Component(
                                head.area,
                                head.minX,
                                head.maxX,
                                Math.round(recovered.stemHeadY() - staff.gap * .45f),
                                Math.round(recovered.stemHeadY() + staff.gap * .45f),
                                recovered.centerX(),
                                recovered.stemHeadY()));
            }
        List<Component> recoveredShadedChordHeads = new ArrayList<>();
        List<Component> shadedChordSemanticEvidence = new ArrayList<>();
        for (Staff staff : staffs)
            for (ShadedChordHeadRecovery.Chord chord :
                    ShadedChordHeadRecovery.find(
                            labels,
                            gray,
                            width,
                            height,
                            staff.gap,
                            Math.round(staff.top - staff.gap * 3),
                            Math.round(staff.bottom + staff.gap * 4))) {
                List<Component> replaced = new ArrayList<>();
                for (Component existing : rawHeadComponents)
                    if (existing.centerX >= chord.left() - staff.gap * .15f
                            && existing.centerX <= chord.right() + staff.gap * .15f
                            && existing.centerY >= chord.top() - staff.gap * .15f
                            && existing.centerY <= chord.bottom() + staff.gap * .15f)
                        replaced.add(existing);
                for (Component existing : replaced)
                    if (!recoveredFadedHeads.contains(existing))
                        shadedChordSemanticEvidence.add(existing);
                rawHeadComponents.removeAll(replaced);
                recoveredFadedHeads.removeAll(replaced);
                recoveredFilledFragmentHeads.removeAll(replaced);
                recoveredPaleChordHeads.removeAll(replaced);
                for (Component existing : replaced) paleChordRhythmHeads.remove(existing);
                for (var recovered : chord.heads()) {
                    Component head =
                            new Component(
                                    (recovered.right() - recovered.left() + 1)
                                            * (recovered.bottom() - recovered.top() + 1),
                                    recovered.left(),
                                    recovered.right(),
                                    recovered.top(),
                                    recovered.bottom(),
                                    recovered.centerX(),
                                    recovered.centerY());
                    rawHeadComponents.add(head);
                    recoveredFadedHeads.add(head);
                    recoveredFilledFragmentHeads.add(head);
                    recoveredShadedChordHeads.add(head);
                }
            }
        List<Component> symbolComponents =
                findComponents(labels, width, height, OmrMeasurePostProcessor.SYMBOL);
        List<Component> clefOrKeyComponents =
                findComponents(labels, width, height, OmrMeasurePostProcessor.CLEF_OR_KEY);
        clefOrKeyComponents =
                splitClefKeyBridges(labels, gray, width, height, clefOrKeyComponents, staffs);
        // Inspect a complete meter digit before chord splitting can turn its
        // two bowls into separate, individually plausible noteheads.
        List<Component> sharpBars =
                sharpCrossbarHeads(gray, width, height, rawHeadComponents, staffs);
        List<Component> notationHeads = new ArrayList<>(rawHeadComponents);
        notationHeads.removeAll(sharpBars);
        notationHeads.removeIf(
                head ->
                        PrintedNoteContrast.paperTexture(
                                gray, width, height, head.minX, head.minY, head.maxX, head.maxY));
        List<Component> headerGlyphs = clefOrKeyComponents;
        notationHeads.removeIf(
                head ->
                        isRoundedHeaderMeter(
                                labels, gray, width, height, head, staffs, headerGlyphs));
        notationHeads.removeIf(
                head ->
                        isHeaderMeterDigit(
                                labels, gray, width, height, head, staffs, headerGlyphs));
        notationHeads.removeIf(head -> inlineEightMeterFragment(gray, width, height, head, staffs));
        notationHeads.removeIf(
                head -> isStackedOpeningMeterFragment(gray, width, height, head, staffs, measures));
        notationHeads.removeIf(
                head ->
                        commonTimeGlyphBounds(
                                        labels, gray, width, height, head, staffs, headerGlyphs)
                                != null);
        notationHeads.removeIf(head -> isTempoUnitHead(gray, width, height, head, staffs));
        notationHeads.removeIf(
                head ->
                        uprightTextBowlBounds(gray, width, height, head, staffs) != null
                                || staffTextBounds(gray, width, height, head, staffs) != null);
        notationHeads.removeIf(head -> isHeavyRestBarFragment(gray, width, height, head, staffs));
        notationHeads.removeIf(head -> isWholeMeasureRestHead(gray, width, height, head, staffs));
        notationHeads.removeIf(head -> isThickBarlineHead(gray, width, height, head, staffs));
        notationHeads.removeIf(
                head -> isHeaderFlatHead(labels, gray, width, height, head, staffs, headerGlyphs));
        notationHeads.removeIf(
                head ->
                        isOwnedHeaderCrossbar(
                                labels,
                                gray,
                                width,
                                height,
                                head,
                                staffs,
                                rawHeadComponents,
                                headerGlyphs,
                                symbolComponents));
        notationHeads.removeIf(
                head -> isForteHookHead(gray, width, height, head, staffs, symbolComponents));
        notationHeads.removeIf(
                head -> {
                    Staff owner = nearestHeadStaff(staffs, head.centerY);
                    return owner != null
                            && head.minY > owner.pitchBottom + owner.pitchGap * .8f
                            && head.centerY < owner.pitchBottom + owner.pitchGap * 4.5f
                            && attachedRawStem(gray, width, height, head, owner.pitchGap * .65f)
                                    == null
                            && ConnectedItalicDynamic.matches(
                                    gray,
                                    width,
                                    height,
                                    head.minX,
                                    head.minY,
                                    head.maxX,
                                    head.maxY,
                                    owner.pitchGap);
                });
        notationHeads.removeIf(
                head ->
                        isZigzagOrnamentHead(
                                labels, gray, width, height, head, staffs, notationHeads));
        notationHeads.removeIf(
                head -> isTrebleTailHead(labels, width, height, head, staffs, headerGlyphs));
        List<Component> headComponents =
                splitStackedHeads(labels, gray, width, height, notationHeads, staffs);
        List<Component> heads = new ArrayList<>();
        List<Component> rejectedSlurHeads = new ArrayList<>();
        for (Component head : headComponents) {
            Staff staff = staffForHead(labels, gray, width, height, staffs, head);
            if (staff != null && plausibleHead(head, staff.gap)) {
                if (flatStemlessFragment(gray, width, height, head, staff.gap))
                    rejectedSlurHeads.add(head);
                else heads.add(head);
            }
        }
        List<Component> rejectedBeamHeads = beamJunctionHeads(gray, width, height, heads, staffs);
        rejectedBeamHeads.addAll(recoveredGraceCaps);
        rejectedBeamHeads.addAll(mergedBeamInteriorHeads(gray, width, height, heads, staffs));
        rejectedBeamHeads.addAll(singleBeamInteriorHeads(gray, width, height, heads, staffs));
        rejectedBeamHeads.addAll(shortPairedMergedBeamHeads(gray, width, height, heads, staffs));
        rejectedBeamHeads.addAll(narrowPairedBeamHeads(gray, width, height, heads, staffs));
        rejectedBeamHeads.addAll(offsetParallelBeamIslandHeads(gray, width, height, heads, staffs));
        heads.removeAll(rejectedBeamHeads);
        heads.removeAll(outlinedHookHeads(gray, width, height, heads, staffs));
        heads.removeAll(isolatedBeamTipHeads(gray, width, height, heads, staffs));
        heads.removeAll(trailingGraceFlagHeads(heads, staffs));
        heads.removeAll(inclinedFlagTipHeads(gray, width, height, heads, staffs));
        heads.removeAll(detachedFingeringHeads(gray, width, height, heads, staffs));
        byte[] beamLabels = withoutBeamHeadIslands(labels, width, rejectedBeamHeads);
        heads.removeAll(entranceStrokeFragments(gray, width, height, heads, staffs));
        var curvedExits = curvedExitHeadFragments(labels, gray, width, height, heads, staffs);
        heads.removeAll(curvedExits.heads());
        List<Component> shortTies = shortTieBowlHeads(labels, gray, width, height, heads, staffs);
        shortTies.addAll(graceSlurHeads(gray, width, height, heads, staffs));
        heads.removeAll(shortTies);
        rejectedSlurHeads.addAll(shortTies);
        // A bright paper halo can enlarge a printed augmentation dot enough for HOMR to label it
        // as a second plausible notehead. Demote only small, stemless components immediately to
        // the right of a substantially larger head; grace notes retain their attached stem.
        List<AccidentalCandidate> accidentalCandidates = new ArrayList<>();
        // Preserve the proved accidental strokes as seeds for the following note.
        for (Component bar : sharpBars)
            accidentalCandidates.add(
                    new AccidentalCandidate(bar, OmrMeasurePostProcessor.NOTEHEAD));
        for (Component component : clefOrKeyComponents)
            accidentalCandidates.add(
                    new AccidentalCandidate(component, OmrMeasurePostProcessor.CLEF_OR_KEY));
        for (Component component : symbolComponents)
            accidentalCandidates.add(
                    new AccidentalCandidate(component, OmrMeasurePostProcessor.SYMBOL));
        // A cautionary natural can be circled. Its enclosure is neither a flat
        // beside the next chord nor a tiny independent head beneath the natural.
        List<float[]> naturalEnclosures = new ArrayList<>();
        List<AccidentalCandidate> enclosedCenters = new ArrayList<>();
        for (AccidentalCandidate candidate : accidentalCandidates) {
            Staff staff = nearestHeadStaff(staffs, candidate.component.centerY);
            Component c = candidate.component;
            if (staff == null
                    || candidate.label != OmrMeasurePostProcessor.CLEF_OR_KEY
                    || c.maxX - c.minX < staff.gap * .48f
                    || c.maxX - c.minX > staff.gap * 1.1f
                    || c.maxY - c.minY < staff.gap * 1.6f
                    || c.maxY - c.minY > staff.gap * 3.2f
                    || c.area < staff.gap * staff.gap * .5f) continue;
            float[] ring =
                    AccidentalEnclosure.find(
                            gray, width, height, c.minX, c.minY, c.maxX, c.maxY, staff.gap);
            if (ring != null) {
                naturalEnclosures.add(ring);
                enclosedCenters.add(candidate);
            }
        }
        heads.removeIf(
                head ->
                        naturalEnclosures.stream()
                                .anyMatch(
                                        ring ->
                                                AccidentalEnclosure.contains(
                                                        ring, head.minX, head.minY, head.maxX,
                                                        head.maxY)));
        accidentalCandidates.removeIf(
                candidate ->
                        naturalEnclosures.stream()
                                .anyMatch(
                                        ring -> {
                                            Component c = candidate.component;
                                            return AccidentalEnclosure.contains(
                                                            ring, c.minX, c.minY, c.maxX, c.maxY)
                                                    && !enclosedCenters.contains(candidate);
                                        }));
        heads.removeAll(
                naturalCrossbarHeads(gray, width, height, heads, staffs, accidentalCandidates));
        // Recovered boxes have no semantic area measurement. Comparing their
        // rectangular area with a clipped semantic head can falsely demote that
        // neighboring real note to a dot. Keep the existing semantic evidence
        // for these relative-size decisions; recovered heads have their own stem.
        List<Component> dotEvidenceHeads = new ArrayList<>(heads);
        dotEvidenceHeads.removeAll(recoveredFadedHeads);
        // Replacing a fused semantic chord must not erase its preexisting dot
        // ownership. Preserve only measured mask parts, never recovered boxes.
        dotEvidenceHeads.addAll(
                splitStackedHeads(
                        labels, gray, width, height, shadedChordSemanticEvidence, staffs));
        List<Component> demotedDotHeads =
                augmentationDotHeads(labels, gray, width, height, dotEvidenceHeads, staffs);
        demotedDotHeads.addAll(
                articulationDotHeads(labels, gray, width, height, dotEvidenceHeads, staffs));
        List<Component> accidentalGraces = new ArrayList<>();
        var graceSeeds =
                joinLocalAccidentalFragments(
                        labels, gray, width, height, accidentalCandidates, staffs);
        for (Component head : demotedDotHeads)
            if (reducedSharpGrace(
                    labels, gray, width, height, head, heads, staffs, measures, graceSeeds))
                accidentalGraces.add(head);
        demotedDotHeads.removeAll(accidentalGraces);
        heads.removeAll(demotedDotHeads);
        List<Component> stemSlashHeads = stemSlashFragments(gray, width, height, heads, staffs);
        heads.removeAll(stemSlashHeads);
        // A returning tie shoulder can share this stem-adjacent shape. Once rejected
        // as a note, its mask must not obscure the two-ended raw tie proof.
        rejectedSlurHeads.addAll(stemSlashHeads);

        List<Component> longSlurFragments = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null
                    || candidate.area > staff.gap * staff.gap * .65f
                    || attachedRawStem(gray, width, height, candidate, staff.gap) != null) continue;
            boolean owner = false;
            for (Component main : heads)
                if (main != candidate
                        && main.area >= candidate.area * 1.5f
                        && Math.abs(main.centerX - candidate.centerX) < staff.gap * 1.5f
                        && Math.abs(main.centerY - candidate.centerY) > staff.gap * .7f
                        && Math.abs(main.centerY - candidate.centerY) < staff.gap * 3
                        && attachedRawStem(gray, width, height, main, staff.gap) != null) {
                    owner = true;
                    break;
                }
            if (owner
                    && LongSlurFragmentInk.matches(
                            gray,
                            width,
                            height,
                            candidate.minX,
                            candidate.minY,
                            candidate.maxX,
                            candidate.maxY,
                            candidate.area,
                            staff.gap)) longSlurFragments.add(candidate);
        }
        heads.removeAll(longSlurFragments);
        List<Component> handwrittenTwos = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null
                    || candidate.centerY > staff.top + staff.gap * .4f
                    || candidate.centerY < staff.top - staff.gap * 5
                    || candidate.area > staff.gap * staff.gap * 1.5f) continue;
            boolean owner = false;
            for (Component main : heads)
                if (main != candidate
                        && main.maxX - main.minX + 1 > staff.gap * .8f
                        && Math.abs(main.centerX - candidate.centerX) < staff.gap * 2
                        && main.centerY - candidate.centerY > staff.gap * 1.2f
                        && main.centerY - candidate.centerY < staff.gap * 5) {
                    owner = true;
                    break;
                }
            if (owner
                    && HandwrittenTwoHeadInk.matches(
                            gray,
                            width,
                            height,
                            candidate.minX,
                            candidate.minY,
                            candidate.maxX,
                            candidate.maxY,
                            staff.gap)) handwrittenTwos.add(candidate);
        }
        heads.removeAll(handwrittenTwos);
        List<Component> segnoFragments = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null
                    || candidate.centerY > staff.top - staff.gap * 1.5f
                    || candidate.area > staff.gap * staff.gap * .5f) continue;
            if (SegnoFragmentInk.matches(
                    gray,
                    width,
                    height,
                    candidate.minX,
                    candidate.minY,
                    candidate.maxX,
                    candidate.maxY,
                    staff.gap)) segnoFragments.add(candidate);
        }
        heads.removeAll(segnoFragments);
        Map<Component, AttachedTremoloInk.Mark> attachedTremolos = new HashMap<>();
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null
                    || head.maxX - head.minX + 1 < staff.gap
                    || !hasOpenCenter(labels, gray, width, height, head, staff.gap)) continue;
            int[] stem = attachedRawStem(gray, width, height, head, staff.gap);
            var mark =
                    stem == null
                            ? null
                            : AttachedTremoloInk.find(
                                    gray,
                                    width,
                                    height,
                                    stem[0],
                                    stem[2] > 0 ? head.maxY : head.minY,
                                    stem[1],
                                    stem[2],
                                    staff.gap);
            if (mark == null)
                mark =
                        AttachedTremoloInk.fadedStem(
                                gray, width, height, head.minX, head.minY, head.maxX, head.maxY,
                                staff.gap);
            if (mark != null) {
                boolean chord = false;
                for (Component other : heads)
                    if (other != head
                            && other.centerY >= mark.top()
                            && other.centerY <= mark.bottom()
                            && Math.abs(other.centerX - (mark.left() + mark.right()) * .5f)
                                    < staff.gap * 2
                            && hasOpenCenter(labels, gray, width, height, other, staff.gap)) {
                        chord = true;
                        break;
                    }
                if (!chord) attachedTremolos.put(head, mark);
            }
        }
        heads.removeIf(
                head ->
                        attachedTremolos.entrySet().stream()
                                .anyMatch(
                                        entry -> {
                                            var mark = entry.getValue();
                                            return head != entry.getKey()
                                                    && head.minX >= mark.left()
                                                    && head.maxX <= mark.right()
                                                    && head.minY >= mark.top()
                                                    && head.maxY <= mark.bottom();
                                        }));
        Map<Component, int[]> detachedTremolos = new HashMap<>();
        for (Component head : heads) {
            if (attachedTremolos.containsKey(head)) continue;
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null
                    || head.maxX - head.minX + 1 < staff.gap * 1.4f
                    || !hasOpenCenter(labels, gray, width, height, head, staff.gap)
                    || attachedRawStem(gray, width, height, head, staff.gap) != null) continue;
            int[] mark = detachedTremolo(gray, width, height, head, staff.gap);
            if (mark != null) {
                boolean chord = false;
                for (Component other : heads)
                    if (other != head
                            && other.centerX >= mark[0]
                            && other.centerX <= mark[1]
                            && other.centerY >= mark[2]
                            && other.centerY <= mark[3]
                            && hasOpenCenter(labels, gray, width, height, other, staff.gap)
                            && enclosedHollowInk(gray, width, other)) {
                        chord = true;
                        break;
                    }
                if (!chord) detachedTremolos.put(head, mark);
            }
        }
        heads.removeIf(
                head ->
                        detachedTremolos.entrySet().stream()
                                .anyMatch(
                                        entry -> {
                                            int[] mark = entry.getValue();
                                            return head != entry.getKey()
                                                    && head.minX >= mark[0]
                                                    && head.maxX <= mark[1]
                                                    && head.minY >= mark[2]
                                                    && head.maxY <= mark[3];
                                        }));
        heads.removeIf(head -> isTrebleCurlFragment(head, staffs, headerGlyphs));
        heads.removeIf(
                head ->
                        isHeaderMeterDigit(
                                labels, gray, width, height, head, staffs, headerGlyphs));
        List<PlayingTechniqueDetector.Staff> directionStaffs = new ArrayList<>();
        for (Staff staff : staffs)
            directionStaffs.add(
                    new PlayingTechniqueDetector.Staff(
                            staff.top, staff.bottom, staff.gap, staff.index, staff.count));
        var printedOctaves = OctaveMarkDetector.printedWords(gray, width, height, directionStaffs);
        heads.removeIf(
                head ->
                        OctaveMarkDetector.containsPrintedMark(
                                printedOctaves, head.centerX, head.centerY, width, height));
        List<Component> dotCandidates = new ArrayList<>(symbolComponents);
        for (Component component : headComponents)
            if (!heads.contains(component)) dotCandidates.add(component);
        // A repeated key signature may sit close enough to the first note to look local.
        // Keep its recognized glyphs out of both semantic and raw accidental recovery.
        List<Component> headerAccidentals = new ArrayList<>();
        detectKeyChangesWithHeaders(
                labels,
                gray,
                width,
                height,
                measures,
                staffs,
                accidentalCandidates,
                heads,
                headerAccidentals);
        List<AccidentalCandidate> localAccidentals =
                new ArrayList<>(
                        joinLocalAccidentalFragments(
                                labels, gray, width, height, accidentalCandidates, staffs));
        localAccidentals.removeIf(
                candidate ->
                        headerAccidentals.stream()
                                .anyMatch(
                                        header ->
                                                candidate.component.minX >= header.minX
                                                        && candidate.component.maxX <= header.maxX
                                                        && candidate.component.minY >= header.minY
                                                        && candidate.component.maxY
                                                                <= header.maxY));
        localAccidentals.removeIf(
                candidate -> {
                    Component c = candidate.component;
                    Staff owner = nearestHeadStaff(staffs, c.centerY);
                    return candidate.label == OmrMeasurePostProcessor.SYMBOL
                            && owner != null
                            && RestVerticalWave.crosses(
                                    gray, width, height, c.minX, c.maxX, c.minY, c.maxY, owner.gap);
                });
        localAccidentals.removeIf(candidate -> joinedGraceTailAccidental(candidate, heads, staffs));
        localAccidentals.removeIf(
                candidate ->
                        attachedGraceFlag(labels, gray, width, height, candidate, heads, staffs));
        localAccidentals.removeAll(
                noteParentheses(gray, width, height, localAccidentals, heads, staffs));
        localAccidentals =
                splitTouchingChordAccidentals(
                        labels, width, height, localAccidentals, heads, staffs);
        // A header key glyph must not act as the local accidental of a grace head.
        List<Component> rejectedAccidentalGraces = new ArrayList<>();
        for (Component head : accidentalGraces)
            if (!reducedSharpGrace(
                    labels, gray, width, height, head, heads, staffs, measures, localAccidentals))
                rejectedAccidentalGraces.add(head);
        heads.removeAll(rejectedAccidentalGraces);
        dotCandidates.addAll(rejectedAccidentalGraces);
        accidentalGraces.removeAll(rejectedAccidentalGraces);
        List<Component> accidentalInk = new ArrayList<>();
        for (AccidentalCandidate candidate : localAccidentals) {
            Staff staff = nearestHeadStaff(staffs, candidate.component.centerY);
            if (staff != null
                    && (isFlatGlyph(labels, width, height, candidate, staff.gap)
                            || isNaturalGlyph(labels, width, height, candidate, staff.gap)
                            || isSharpGlyph(labels, width, height, candidate, staff.gap)
                            || DoubleSharpGlyph.matches(
                                    labels,
                                    width,
                                    height,
                                    candidate.component.minX,
                                    candidate.component.minY,
                                    candidate.component.maxX,
                                    candidate.component.maxY,
                                    (byte) 0,
                                    staff.gap)
                            || DoubleSharpGlyph.matchesRaw(
                                    gray,
                                    width,
                                    height,
                                    candidate.component.minX,
                                    candidate.component.minY,
                                    candidate.component.maxX,
                                    candidate.component.maxY,
                                    staff.gap))) accidentalInk.add(candidate.component);
        }
        List<Component> roundedLedgerGraces =
                roundedLedgerGraceHeads(labels, gray, width, height, heads, staffs);
        List<Component> bowMarks = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null || candidate.area > staff.gap * staff.gap * 1.1f) continue;
            for (Component other : heads) {
                float dy = other.centerY - candidate.centerY;
                if (other == candidate
                        || other.area < candidate.area * 1.35f
                        || other.maxX - other.minX + 1 < staff.gap * .9f
                        || nearestHeadStaff(staffs, other.centerY) != staff
                        || Math.abs(other.centerX - candidate.centerX) > staff.gap * .7f
                        || dy < staff.gap * 1.2f
                        || dy > staff.gap * 4f) continue;
                if (NoteArticulationDetector.upBowAtHead(
                                gray,
                                width,
                                height,
                                candidate.minX,
                                candidate.minY,
                                candidate.maxX,
                                candidate.maxY,
                                staff.gap)
                        || DetachedAnnotationInk.downBow(
                                gray,
                                width,
                                height,
                                candidate.minX,
                                candidate.minY,
                                candidate.maxX,
                                candidate.maxY,
                                staff.gap)) {
                    bowMarks.add(candidate);
                    break;
                }
            }
        }
        // Bow direction changes technique, not the pitch or accent of the following note.
        heads.removeAll(bowMarks);
        Map<Component, Integer> recoveredArticulations = new HashMap<>();
        List<Component> angularMarks = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null || candidate.area > staff.gap * staff.gap * .65f) continue;
            Component owner = null;
            float distance = Float.MAX_VALUE;
            for (Component other : heads) {
                float dy = Math.abs(other.centerY - candidate.centerY);
                if (other == candidate
                        || other.area < candidate.area * 2
                        || other.maxX - other.minX + 1 < staff.gap * .9f
                        || Math.abs(other.centerX - candidate.centerX) > staff.gap * .7f
                        || dy < staff.gap * 1.2f
                        || dy > staff.gap * 6f
                        || dy >= distance) continue;
                if (NoteArticulationDetector.marcatoAtHead(
                        gray,
                        width,
                        height,
                        candidate.minX,
                        candidate.minY,
                        candidate.maxX,
                        candidate.maxY,
                        staff.gap,
                        candidate.centerY < other.centerY)) {
                    owner = other;
                    distance = dy;
                }
            }
            if (owner != null) {
                angularMarks.add(candidate);
                Staff ownerStaff = nearestHeadStaff(staffs, owner.centerY);
                for (Component chord : heads)
                    if (Math.abs(chord.centerX - owner.centerX) < staff.gap * .45f
                            && nearestHeadStaff(staffs, chord.centerY) == ownerStaff)
                        recoveredArticulations.merge(
                                chord, NoteArticulation.MARCATO, (a, b) -> a | b);
            }
        }
        heads.removeAll(angularMarks);
        List<Component> accentMarks = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null
                    || candidate.area > staff.gap * staff.gap * .65f
                    || attachedRawStem(gray, width, height, candidate, staff.gap * .65f) != null)
                continue;
            Component owner = null;
            float distance = Float.MAX_VALUE;
            for (Component main : heads) {
                float dy = Math.abs(main.centerY - candidate.centerY);
                if (main == candidate
                        || main.area < candidate.area * 2
                        || main.maxX - main.minX + 1 < staff.gap * .9f
                        || Math.abs(main.centerX - candidate.centerX) > staff.gap * .7f
                        || dy < staff.gap * 1.2f
                        || dy > staff.gap * 6f
                        || dy >= distance) continue;
                if (DetachedAnnotationInk.accent(
                        gray,
                        width,
                        height,
                        candidate.minX,
                        candidate.minY,
                        candidate.maxX,
                        candidate.maxY,
                        staff.gap)) {
                    owner = main;
                    distance = dy;
                }
            }
            if (owner != null) {
                accentMarks.add(candidate);
                recoveredArticulations.merge(owner, NoteArticulation.ACCENT, (a, b) -> a | b);
            }
        }
        heads.removeAll(accentMarks);
        List<DetectedNote> detected = new ArrayList<>();
        java.util.Set<CrossHeadIdentity> retainedCrossHeads = new java.util.HashSet<>();
        for (Component head : heads) {
            Staff staff = staffForHead(labels, gray, width, height, staffs, head);
            if (staff == null) continue;
            if (isHeavyRestCount(gray, width, height, head, staff)) continue;
            Component crossHead =
                    recoveredShadedChordHeads.contains(head)
                            ? null
                            : unpitchedCrossHead(gray, width, height, head, staff);
            boolean unpitched = crossHead != null;
            int[] crossStem =
                    crossHead == null
                            ? null
                            : unpitchedStem(gray, width, height, crossHead, staff.gap);
            if (unpitched) {
                // Raw crossing diagonals establish the head, but a printed shaft must own it.
                // A detached cross is not promoted into either a pitched or unpitched note.
                if (crossStem == null) continue;
                head = crossHead;
                if (!retainedCrossHeads.add(
                        new CrossHeadIdentity(
                                Math.round(head.centerX),
                                Math.round(head.centerY),
                                staff.index,
                                staff.count))) continue;
            }
            // Heads far outside a staff require printed ledger lines. A nearby
            // text stroke can look like a stem, so a semantic stem alone cannot
            // promote a tempo digit or other text into an extreme pitch.
            // Cross-staff stems may assign a note to the other voice. Validate
            // its printed position against the nearest physical staff instead.
            Staff physicalStaff = printedLedgerOwner(gray, width, height, staffs, head);
            if (physicalStaff == null) physicalStaff = nearestHeadStaff(staffs, head.centerY);
            float ledgerClearance = 1.8f;
            // A detached head-like mark on the first ledger has no shaft to
            // establish notation ownership. Its printed ledger must do so.
            if (gray != null
                    && physicalStaff != null
                    && !roundedLedgerGraces.contains(head)
                    && head.maxX - head.minX + 1 <= physicalStaff.pitchGap * .85f
                    && head.maxY - head.minY + 1 <= physicalStaff.pitchGap * .85f
                    && head.area <= physicalStaff.pitchGap * physicalStaff.pitchGap * .5f
                    && attachedRawStem(gray, width, height, head, physicalStaff.pitchGap) == null)
                ledgerClearance = .95f;
            if (gray != null
                    && physicalStaff != null
                    && (head.centerY < physicalStaff.top - physicalStaff.gap * ledgerClearance
                            || head.centerY
                                    > physicalStaff.bottom + physicalStaff.gap * ledgerClearance)
                    && !hasHeadLedgerSupport(
                            labels,
                            gray,
                            width,
                            height,
                            head,
                            physicalStaff,
                            roundedLedgerGraces.contains(head))
                    && !(recoveredShadedChordHeads.contains(head)
                            && ShadedChordHeadRecovery.hasLedgerRails(
                                    gray,
                                    width,
                                    height,
                                    head.minX,
                                    head.maxX,
                                    head.centerY,
                                    physicalStaff.top,
                                    physicalStaff.bottom,
                                    physicalStaff.gap))) continue;
            float normalizedX = head.centerX() / width;
            float normalizedY = head.centerY() / height;
            int measureIndex =
                    containingMeasureForStaff(measures, normalizedX, normalizedY, staff, height);
            if (measureIndex < 0
                    && reducedLedgerHead(gray, width, height, head, staff.pitchGap)
                    && openingGraceFlag(gray, width, height, head, staff.pitchGap))
                measureIndex = openingGraceMeasure(measures, head.centerX, staff, width, height);
            if (measureIndex < 0) continue;
            MeasureRegion measure = measures.get(measureIndex);
            // Repeated geometry represents an expanded multi-measure rest. Its printed count
            // can look like a hollow notehead, but every logical bar in that span is silent.
            if ((measureIndex > 0 && measure.equals(measures.get(measureIndex - 1)))
                    || (measureIndex + 1 < measures.size()
                            && measure.equals(measures.get(measureIndex + 1)))) continue;
            float position =
                    (normalizedX - measure.left())
                            / Math.max(0.0001f, measure.right() - measure.left());
            float[] localPitch = localStaffPitch(labels, gray, width, height, staff, head);
            float localGap = localPitch[1];
            float localBottom =
                    printedLedgerBottom(gray, width, height, head, localPitch[0], localGap);
            int step = printedPitchStep(gray, width, height, head, localBottom, localGap);
            Staff rhythmStaff = beamStaffFrame(staff, localPitch, width);
            int beamCount =
                    unpitched
                            ? detectBeamCount(
                                    beamLabels,
                                    gray,
                                    width,
                                    height,
                                    head,
                                    rhythmStaff,
                                    false,
                                    false,
                                    crossStem)
                            : detectBeamCount(
                                    beamLabels,
                                    gray,
                                    width,
                                    height,
                                    paleChordRhythmHeads.getOrDefault(head, head),
                                    rhythmStaff,
                                    heads);
            int[] tremolo = tremoloStrokeCounts(gray, width, height, head, staff.gap, heads);
            beamCount = Math.max(0, beamCount - tremolo[1]);
            if (tremolo[0] > 0)
                beamCount =
                        Math.max(
                                beamCount,
                                beamsBeyondTremolo(gray, beamLabels, width, height, head, staff));
            float unbeamedDuration =
                    detectUnbeamedDuration(
                            labels, gray, width, height, head, staff.gap, beamCount, heads);
            if (recoveredPaleChordHeads.contains(head)
                    || recoveredFilledFragmentHeads.contains(head))
                unbeamedDuration = beamCount == 0 ? 1f : 0f;
            if (detachedTremolos.containsKey(head))
                unbeamedDuration = ScoreNoteEvent.DURATION_WHOLE;
            if (attachedTremolos.containsKey(head)) unbeamedDuration = ScoreNoteEvent.DURATION_HALF;
            if (recoveredCreaseGraces.containsKey(head)) {
                beamCount = recoveredCreaseGraces.get(head);
                unbeamedDuration = 0;
            }
            if (unpitched) unbeamedDuration = beamCount == 0 ? ScoreNoteEvent.DURATION_QUARTER : 0f;
            // Beamed notes cannot have open heads. If a staff line, slur, or artwork edge near
            // an open half/whole head looked like a beam, retain the notehead's stronger direct
            // evidence instead of collapsing the sustained passage into eighths/sixteenths.
            if (unbeamedDuration >= ScoreNoteEvent.DURATION_HALF) beamCount = 0;
            Component dotAnchor =
                    unbeamedDuration == ScoreNoteEvent.DURATION_WHOLE
                            ? wholeChordDotAnchor(
                                    labels, gray, width, height, head, heads, staff.gap)
                            : unbeamedDuration == ScoreNoteEvent.DURATION_HALF
                                    ? halfChordDotAnchor(
                                            labels, gray, width, height, head, heads, staff.gap)
                                    : head;
            float dotGap =
                    (staff.printedPhase || staff.printedSlope || staff.pitchTrack != null)
                            ? localGap
                            : staff.gap;
            int augmentationDots =
                    countAugmentationDots(
                            dotCandidates,
                            dotAnchor,
                            dotGap,
                            gray,
                            width,
                            height,
                            unbeamedDuration >= ScoreNoteEvent.DURATION_HALF,
                            accidentalInk,
                            heads);
            if (augmentationDots > 0
                    && beamCount > 0
                    && hasHollowUnisonToRight(labels, gray, width, height, head, heads, staff.gap))
                augmentationDots = 0;
            float accidentalGap =
                    accidentalGraces.contains(head) ? localPitch[1] * .65f : localPitch[1];
            int writtenAccidental = ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
            if (!unpitched) {
                writtenAccidental =
                        detectWrittenAccidental(
                                labels,
                                width,
                                height,
                                localAccidentals,
                                head,
                                accidentalGap,
                                heads,
                                gray);
                if (writtenAccidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                        && (rawWideDoubleSharp(
                                        gray, width, height, localAccidentals, head, accidentalGap)
                                || rawCompactDoubleSharp(
                                        gray,
                                        width,
                                        height,
                                        localAccidentals,
                                        head,
                                        accidentalGap)))
                    writtenAccidental = ScoreNoteEvent.ACCIDENTAL_DOUBLE_SHARP;
                if (writtenAccidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                                && rawSharpFromSeed(
                                        gray, width, height, localAccidentals, head, accidentalGap)
                        || writtenAccidental == ScoreNoteEvent.ACCIDENTAL_FLAT
                                && completeRawSharpFromSeed(
                                        gray, width, height, localAccidentals, head, accidentalGap))
                    writtenAccidental = ScoreNoteEvent.ACCIDENTAL_SHARP;
                if (writtenAccidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                        && rawFlatFromBowl(
                                gray,
                                width,
                                height,
                                withoutRecognizedSharps(
                                        labels, width, height, localAccidentals, accidentalGap),
                                head,
                                accidentalGap)) writtenAccidental = ScoreNoteEvent.ACCIDENTAL_FLAT;
                if (writtenAccidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                        && flatBowlUnderBeamedStem(
                                labels, gray, width, height, localAccidentals, head, accidentalGap))
                    writtenAccidental = ScoreNoteEvent.ACCIDENTAL_FLAT;
                if ((writtenAccidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                                || writtenAccidental == ScoreNoteEvent.ACCIDENTAL_FLAT
                                || writtenAccidental == ScoreNoteEvent.ACCIDENTAL_SHARP)
                        && rawNaturalFromCrossbars(
                                gray,
                                width,
                                height,
                                localAccidentals,
                                head,
                                accidentalGap,
                                writtenAccidental == ScoreNoteEvent.ACCIDENTAL_FLAT
                                        ? nearestFlatRightEdge(
                                                labels,
                                                width,
                                                height,
                                                localAccidentals,
                                                head,
                                                accidentalGap)
                                        : -1))
                    writtenAccidental = ScoreNoteEvent.ACCIDENTAL_NATURAL;
                if (writtenAccidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                        && naturalFromUpperSpine(
                                labels, gray, width, height, localAccidentals, head, accidentalGap))
                    writtenAccidental = ScoreNoteEvent.ACCIDENTAL_NATURAL;
            }
            ScoreNoteEvent event =
                    new ScoreNoteEvent(
                                    measureIndex,
                                    clamp(position),
                                    Math.max(-32, Math.min(32, step)),
                                    staff.index,
                                    staff.count,
                                    clamp(normalizedY),
                                    false,
                                    augmentationDots,
                                    beamCount,
                                    writtenAccidental,
                                    unbeamedDuration)
                            .withKind(
                                    unpitched
                                            ? ScoreNoteEvent.Kind.UNPITCHED
                                            : ScoreNoteEvent.Kind.PITCHED)
                            .withStemDirection(unpitched ? crossStem[2] : 0);
            if (accidentalGraces.contains(head))
                event = event.withArticulations(event.articulations() | NoteOrnament.GRACE);
            if (recoveredCreaseGraces.containsKey(head))
                event = event.withArticulations(event.articulations() | NoteOrnament.GRACE);
            if (tremolo[0] > 0)
                event =
                        event.withArticulations(
                                NoteOrnament.withTremolo(
                                        event.articulations(), beamCount + tremolo[0]));
            if (detachedTremolos.containsKey(head))
                event = event.withArticulations(NoteOrnament.withTremolo(event.articulations(), 3));
            if (attachedTremolos.containsKey(head))
                event =
                        event.withArticulations(
                                NoteOrnament.withTremolo(
                                        event.articulations(), attachedTremolos.get(head).beams()));
            event =
                    event.withArticulations(
                            event.articulations() | recoveredArticulations.getOrDefault(head, 0));
            // A shared shaft between the two ovals is not on the combined component's
            // outer edge. Check the separate heads before accepting a whole-note guess.
            List<Component> unison =
                    unpitched
                            ? List.of()
                            : sideBySideUnison(labels, gray, width, height, head, staff.gap);
            if (!unison.isEmpty()) {
                // Opposite stems share a printed pitch/attack but have separate durations.
                for (int partIndex = 0; partIndex < unison.size(); partIndex++) {
                    Component part = unison.get(partIndex);
                    Component partCross = unpitchedCrossHead(gray, width, height, part, staff);
                    int[] partStem =
                            partCross == null
                                    ? null
                                    : unpitchedStem(gray, width, height, partCross, staff.gap);
                    if (partCross != null && partStem == null) continue;
                    boolean partUnpitched = partCross != null;
                    if (partUnpitched) part = partCross;
                    int partBeams =
                            partIndex == 0
                                    ? detectBeamCount(beamLabels, gray, width, height, part, staff)
                                    : 0;
                    int[] partTremolo =
                            tremoloStrokeCounts(gray, width, height, part, staff.gap, heads);
                    if (partIndex == 0 && partTremolo[0] > 0)
                        partBeams =
                                Math.max(
                                        Math.max(0, partBeams - partTremolo[1]),
                                        beamsBeyondTremolo(
                                                gray, beamLabels, width, height, part, staff));
                    float partDuration = partIndex == 0 ? (partBeams > 0 ? 0 : 1) : 2;
                    int partDots =
                            partIndex == 0
                                    ? 0
                                    : countAugmentationDots(
                                            dotCandidates,
                                            part,
                                            staff.gap,
                                            gray,
                                            width,
                                            height,
                                            true);
                    if (partUnpitched) {
                        partBeams =
                                detectBeamCount(
                                        beamLabels,
                                        gray,
                                        width,
                                        height,
                                        part,
                                        rhythmStaff,
                                        false,
                                        false,
                                        partStem);
                        partDuration = partBeams == 0 ? ScoreNoteEvent.DURATION_QUARTER : 0f;
                    }
                    var separate =
                            new ScoreNoteEvent(
                                            measureIndex,
                                            clamp(position),
                                            partUnpitched
                                                    ? printedPitchStep(
                                                            gray,
                                                            width,
                                                            height,
                                                            part,
                                                            localBottom,
                                                            localGap)
                                                    : step,
                                            staff.index,
                                            staff.count,
                                            partUnpitched
                                                    ? clamp(part.centerY / height)
                                                    : clamp(normalizedY),
                                            false,
                                            partDots,
                                            partBeams,
                                            partUnpitched
                                                    ? ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                                                    : writtenAccidental,
                                            partDuration)
                                    .withKind(
                                            partUnpitched
                                                    ? ScoreNoteEvent.Kind.UNPITCHED
                                                    : ScoreNoteEvent.Kind.PITCHED)
                                    .withStemDirection(partUnpitched ? partStem[2] : 0);
                    if (partTremolo[0] > 0)
                        separate =
                                separate.withArticulations(
                                        NoteOrnament.withTremolo(0, partBeams + partTremolo[0]));
                    detected.add(new DetectedNote(separate, part, staff.gap));
                }
                continue;
            }
            List<Component> seconds =
                    unpitched ? List.of() : sideBySideSeconds(labels, width, head, staff.gap);
            if (seconds.isEmpty()) detected.add(new DetectedNote(event, head, staff.gap));
            else
                for (Component part : seconds) {
                    Component partCross = unpitchedCrossHead(gray, width, height, part, staff);
                    int[] partStem =
                            partCross == null
                                    ? null
                                    : unpitchedStem(gray, width, height, partCross, staff.gap);
                    if (partCross != null && partStem == null) continue;
                    boolean partUnpitched = partCross != null;
                    if (partUnpitched) part = partCross;
                    int partStep =
                            printedPitchStep(gray, width, height, part, localBottom, localGap);
                    int partAccidental =
                            partUnpitched
                                    ? ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                                    : displacedSecondAccidental(
                                            labels,
                                            gray,
                                            width,
                                            height,
                                            localAccidentals,
                                            head,
                                            part,
                                            seconds,
                                            accidentalGap,
                                            heads);
                    // Displaced seconds share the stem/attack, despite their two horizontal
                    // centres.
                    int partBeams =
                            partUnpitched
                                    ? detectBeamCount(
                                            beamLabels,
                                            gray,
                                            width,
                                            height,
                                            part,
                                            rhythmStaff,
                                            false,
                                            false,
                                            partStem)
                                    : beamCount;
                    float partDuration =
                            partUnpitched
                                    ? (partBeams == 0 ? ScoreNoteEvent.DURATION_QUARTER : 0f)
                                    : unbeamedDuration;
                    var chord =
                            new ScoreNoteEvent(
                                            measureIndex,
                                            clamp(position),
                                            partStep,
                                            staff.index,
                                            staff.count,
                                            clamp(part.centerY / height),
                                            false,
                                            augmentationDots,
                                            partBeams,
                                            partAccidental,
                                            partDuration)
                                    .withKind(
                                            partUnpitched
                                                    ? ScoreNoteEvent.Kind.UNPITCHED
                                                    : ScoreNoteEvent.Kind.PITCHED)
                                    .withStemDirection(partUnpitched ? partStem[2] : 0)
                                    .withArticulations(event.articulations());
                    detected.add(new DetectedNote(chord, part, staff.gap));
                }
        }
        detected = alignDisplacedSeconds(detected, gray, width, height);
        detected = reconcileSingleShaftChords(detected, labels, gray, width, height);
        detected =
                applyPrintedClefs(
                        detected, staffs, clefOrKeyComponents, labels, gray, width, height);
        detected.sort(
                Comparator.comparingInt((DetectedNote note) -> note.event.measureIndex())
                        .thenComparingDouble(note -> note.event.positionInMeasure())
                        .thenComparingInt(note -> note.event.staffIndex())
                        .thenComparingInt(note -> note.event.staffStep()));
        List<Component> printedAccidentalHeads = new ArrayList<>();
        for (DetectedNote note : detected)
            if (note.event.writtenAccidental() != ScoreNoteEvent.ACCIDENTAL_FROM_KEY)
                printedAccidentalHeads.add(note.head);
        byte[] tieLabels = tieLabelsWithoutSlurHeads(labels, width, rejectedSlurHeads, heads);
        if (!recoveredShadedChordHeads.isEmpty()) {
            tieLabels = tieLabels.clone();
            for (Component head : recoveredShadedChordHeads)
                for (int y = head.minY; y <= head.maxY; y++)
                    for (int x = head.minX; x <= head.maxX; x++)
                        tieLabels[y * width + x] = OmrMeasurePostProcessor.NOTEHEAD;
        }
        var tieInk = CurvedExitInk.withoutOwnedCurves(tieLabels, gray, curvedExits.marks());
        tieLabels = tieInk.labels();
        byte[] tieGray = tieInk.gray();
        List<DetectedNote> joined =
                applyAccidentalState(
                        markTieContinuations(
                                tieLabels,
                                tieGray,
                                width,
                                height,
                                removeSplitDuplicates(detected),
                                staffs));
        logHeadCoverage(staffs, rawHeadComponents, headComponents, heads, demotedDotHeads, joined);
        List<ScoreNoteEvent> result = new ArrayList<>(joined.size());
        for (DetectedNote note : joined) result.add(note.event);
        List<ScoreNoteEvent> notation =
                TripletRhythmDetector.withoutNumeralHeads(result, measures, gray, width, height);
        if (notation.size() != result.size()) {
            for (DetectedNote note : joined)
                if (!notation.contains(note.event)) heads.remove(note.head);
            joined.removeIf(note -> !notation.contains(note.event));
            result.clear();
            result.addAll(notation);
        }
        List<SixteenthRestDetector.Staff> restStaffs = new ArrayList<>();
        for (Staff staff : staffs) {
            StaffPitchTrack restTrack = staff.pitchTrack;
            if (restTrack == null && staff.pitchSlope != 0)
                restTrack =
                        StaffPitchTrack.linear(
                                width, staff.pitchBottom, staff.pitchGap, staff.pitchSlope);
            restStaffs.add(
                    new SixteenthRestDetector.Staff(
                            staff.pitchBottom - staff.pitchGap * 4,
                            staff.pitchBottom,
                            staff.pitchGap,
                            staff.index,
                            staff.count,
                            restTrack));
        }
        List<ScoreRestEvent> rests =
                SixteenthRestDetector.detect(gray, width, height, measures, restStaffs, result);
        // Small stemless model heads can be augmentation dots of an independently
        // recognized rest. Re-read those dots without letting the mistaken head
        // claim ownership, but require the same rest to have survived the first pass.
        List<DetectedNote> compactDots = new ArrayList<>();
        List<DetectedNote> restBodyHeads = new ArrayList<>();
        for (DetectedNote note : joined) {
            Component h = note.head;
            float gap = note.staffGap;
            if (gray == null || note.event.kind() == ScoreNoteEvent.Kind.UNPITCHED) continue;
            int[] stem = attachedRawStem(gray, width, height, h, gap);
            if (stem == null
                    && h.maxX - h.minX + 1 <= gap * .7f
                    && h.maxY - h.minY + 1 <= gap * .7f
                    && h.area <= gap * gap * .32f) compactDots.add(note);
            // The zigzag body can generate a larger prediction than an
            // augmentation dot. Its complete raw shape supplies separate
            // evidence, so do not make it pass the tiny-dot size gate.
            // A quarter-rest zigzag can resemble a stem when several blank
            // rows are bridged. Preserve continuous stems, but let complete
            // rest recognition adjudicate a compact head on that broken path.
            if (h.maxX - h.minX + 1 <= gap * 1.05f
                    && h.maxY - h.minY + 1 <= gap * 1.05f
                    && h.area <= gap * gap * .65f
                    && (stem == null
                            || attachedRawStem(gray, width, height, h, gap, 1) == null
                            || CompactQuarterRestContour.compactTail(
                                    h.maxX - h.minX + 1, h.maxY - h.minY + 1, h.area, gap)))
                restBodyHeads.add(note);
        }
        if (!compactDots.isEmpty() || !restBodyHeads.isEmpty()) {
            List<ScoreNoteEvent> owners = new ArrayList<>(result);
            for (DetectedNote note : compactDots) owners.remove(note.event);
            for (DetectedNote note : restBodyHeads) owners.remove(note.event);
            var evidence =
                    SixteenthRestDetector.detectWithDots(
                            gray, width, height, measures, restStaffs, owners);
            List<DetectedNote> removed = new ArrayList<>();
            List<ScoreRestEvent> verifiedBodies = new ArrayList<>();
            // An independently recognized complete quarter-rest body can
            // establish ownership even when its false head blocked the first
            // pass. Continuous stems remain excluded from the body candidates.
            for (DetectedNote note : restBodyHeads)
                for (ScoreRestEvent rest : evidence.rests()) {
                    if (rest.durationBeats() < .25
                            || rest.durationBeats() > 1.75
                            || rest.measureIndex() != note.event.measureIndex()
                            || rest.staffIndex() != note.event.staffIndex()
                            || rest.staffCount() != note.event.staffCount()) continue;
                    // Only the newly admitted continuous-stem compact-tail path needs the
                    // deep polyphonic placement guard. Preserve legacy stemless eighth rests.
                    if (rest.durationBeats() < 1
                            && attachedRawStem(gray, width, height, note.head, note.staffGap)
                                    != null
                            && attachedRawStem(gray, width, height, note.head, note.staffGap, 1)
                                    != null) {
                        Staff bodyStaff = nearestHeadStaff(staffs, note.head.centerY);
                        if (bodyStaff == null
                                || rest.pageY() * height - rest.pageHeight() * height * .5f
                                        <= bodyStaff.bottom + note.staffGap * .3f) continue;
                    }
                    MeasureRegion region = measures.get(rest.measureIndex());
                    float x =
                            (region.left()
                                            + rest.positionInMeasure()
                                                    * (region.right() - region.left()))
                                    * width;
                    if (Math.abs(x - note.head.centerX)
                                    <= note.staffGap * (rest.durationBeats() < 1 ? .5f : .4f)
                            && Math.abs(rest.pageY() * height - note.head.centerY)
                                    <= rest.pageHeight() * height * .5f) {
                        removed.add(note);
                        verifiedBodies.add(rest);
                        break;
                    }
                }
            for (DetectedNote note : compactDots)
                for (var dot : evidence.dots()) {
                    ScoreRestEvent parent = dot.rest();
                    if (parent.measureIndex() != note.event.measureIndex()
                            || parent.staffIndex() != note.event.staffIndex()
                            || parent.staffCount() != note.event.staffCount()
                            || Math.abs(dot.x() - note.head.centerX) > note.staffGap * .3f
                            || Math.abs(dot.y() - note.head.centerY) > note.staffGap * .3f)
                        continue;
                    boolean verified =
                            verifiedBodies.contains(parent)
                                    || rests.stream()
                                            .anyMatch(
                                                    rest ->
                                                            rest.measureIndex()
                                                                            == parent.measureIndex()
                                                                    && rest.staffIndex()
                                                                            == parent.staffIndex()
                                                                    && rest.staffCount()
                                                                            == parent.staffCount()
                                                                    && Math.abs(
                                                                                    rest
                                                                                                    .positionInMeasure()
                                                                                            - parent
                                                                                                    .positionInMeasure())
                                                                            < .005f
                                                                    && Math.abs(
                                                                                    rest.pageY()
                                                                                            - parent
                                                                                                    .pageY())
                                                                            < .005f);
                    if (verified) {
                        removed.add(note);
                        break;
                    }
                }
            if (!removed.isEmpty()) {
                joined.removeAll(removed);
                for (DetectedNote note : removed) {
                    result.remove(note.event);
                    heads.remove(note.head);
                }
                rests =
                        SixteenthRestDetector.detect(
                                gray, width, height, measures, restStaffs, result);
            }
        }
        List<ScoreKeyChange> keyChanges =
                detectKeyChanges(
                        labels,
                        gray,
                        width,
                        height,
                        measures,
                        staffs,
                        accidentalCandidates,
                        joined.stream()
                                .map(n -> n.head)
                                .collect(java.util.stream.Collectors.toList()));
        List<Component> resolvedHeads =
                joined.stream().map(n -> n.head).collect(java.util.stream.Collectors.toList());
        List<ScoreNoteEvent> withRests = new ArrayList<>();
        for (DetectedNote note : joined) {
            ScoreNoteEvent event = note.event;
            Staff restOwner = staffForHead(labels, gray, width, height, staffs, note.head);
            float[] restFrame =
                    restOwner.pitchTrack == null
                            ? new float[] {restOwner.bottom, restOwner.gap}
                            : restOwner.pitchTrack.at(note.head.centerX);
            float restMiddle = restFrame[0] - 2 * restFrame[1];
            int[] voiceStem = attachedRawStem(gray, width, height, note.head, note.staffGap);
            int voiceDirection = voiceStem == null ? 0 : voiceStem[2];
            float next = 1.01f;
            for (ScoreNoteEvent other : result)
                if (other.measureIndex() == event.measureIndex()
                        && other.staffIndex() == event.staffIndex()
                        && other.staffCount() == event.staffCount()
                        && other.positionInMeasure() > event.positionInMeasure() + .018f)
                    next = Math.min(next, other.positionInMeasure());
            float silence = 0;
            boolean hasSixteenthRest = false;
            for (ScoreRestEvent rest : rests)
                if (rest.measureIndex() == event.measureIndex()
                        && rest.staffIndex() == event.staffIndex()
                        && rest.staffCount() == event.staffCount()
                        && restSharesStemVoice(
                                rest.pageY() * height, restMiddle, note.staffGap, voiceDirection)
                        && rest.positionInMeasure() > event.positionInMeasure()
                        && rest.positionInMeasure() < next
                        && restIsSeparateAttack(
                                rest,
                                event,
                                result,
                                measures.get(event.measureIndex()),
                                gray,
                                width,
                                height,
                                note.staffGap)) {
                    silence += (float) rest.durationBeats();
                    hasSixteenthRest |= rest.durationBeats() == .25;
                }
            float leading = 0;
            boolean first =
                    result.stream()
                            .noneMatch(
                                    other ->
                                            other.measureIndex() == event.measureIndex()
                                                    && other.staffIndex() == event.staffIndex()
                                                    && other.staffCount() == event.staffCount()
                                                    && other.positionInMeasure()
                                                            < event.positionInMeasure() - .018f);
            if (first)
                for (ScoreRestEvent rest : rests)
                    if (rest.measureIndex() == event.measureIndex()
                            && rest.staffIndex() == event.staffIndex()
                            && rest.staffCount() == event.staffCount()
                            && restSharesStemVoice(
                                    rest.pageY() * height,
                                    restMiddle,
                                    note.staffGap,
                                    voiceDirection)
                            && rest.positionInMeasure() < event.positionInMeasure()
                            && restIsSeparateAttack(
                                    rest,
                                    event,
                                    result,
                                    measures.get(event.measureIndex()),
                                    gray,
                                    width,
                                    height,
                                    note.staffGap)) leading += (float) rest.durationBeats();
            // The first moving attack can follow a printed rest while another voice
            // already holds a half note in that rest's column.
            if (!first
                    && !ScoreNoteTiming.hasIndependentSustain(event)
                    && result.stream()
                            .noneMatch(
                                    other ->
                                            other.measureIndex() == event.measureIndex()
                                                    && other.staffIndex() == event.staffIndex()
                                                    && other.staffCount() == event.staffCount()
                                                    && other.positionInMeasure()
                                                            < event.positionInMeasure() - .018f
                                                    && !ScoreNoteTiming.hasIndependentSustain(
                                                            other))) {
                for (ScoreRestEvent rest : rests)
                    if (rest.measureIndex() == event.measureIndex()
                            && rest.staffIndex() == event.staffIndex()
                            && rest.staffCount() == event.staffCount()
                            && restSharesStemVoice(
                                    rest.pageY() * height,
                                    restMiddle,
                                    note.staffGap,
                                    voiceDirection)
                            && rest.positionInMeasure() < event.positionInMeasure() - .018f
                            && restIsSeparateAttack(
                                    rest,
                                    event,
                                    result,
                                    measures.get(event.measureIndex()),
                                    gray,
                                    width,
                                    height,
                                    note.staffGap)
                            && result.stream()
                                    .anyMatch(
                                            other ->
                                                    other.measureIndex() == event.measureIndex()
                                                            && other.staffIndex()
                                                                    == event.staffIndex()
                                                            && other.staffCount()
                                                                    == event.staffCount()
                                                            && ScoreNoteTiming
                                                                    .hasIndependentSustain(other)
                                                            && Math.abs(
                                                                            other
                                                                                            .positionInMeasure()
                                                                                    - rest
                                                                                            .positionInMeasure())
                                                                    <= .018f))
                        leading += (float) rest.durationBeats();
            }
            int beams = event.beamCount();
            if (hasSixteenthRest && beams <= 2 && !ScoreNoteTiming.hasIndependentSustain(event)) {
                int flags = rawDetachedFlags(gray, labels, width, height, note.head, note.staffGap);
                if (flags > beams) beams = flags;
            }
            float dotValidationGap =
                    event.augmentationDots() > 0
                                    && (restOwner.printedPhase
                                            || restOwner.printedSlope
                                            || restOwner.pitchTrack != null)
                            ? localStaffPitch(labels, gray, width, height, restOwner, note.head)[1]
                            : note.staffGap;
            withRests.add(
                    new ScoreNoteEvent(
                                    event.measureIndex(),
                                    event.positionInMeasure(),
                                    event.staffStep(),
                                    event.staffIndex(),
                                    event.staffCount(),
                                    event.pageY(),
                                    event.tiedFromPrevious(),
                                    dotsOutsideRests(
                                            dotCandidates,
                                            note,
                                            rests,
                                            measures,
                                            gray,
                                            width,
                                            height,
                                            accidentalInk,
                                            event.unbeamedDurationBeats()
                                                            == ScoreNoteEvent.DURATION_HALF
                                                    ? halfChordDotAnchor(
                                                            labels,
                                                            gray,
                                                            width,
                                                            height,
                                                            note.head,
                                                            resolvedHeads,
                                                            note.staffGap)
                                                    : note.head,
                                            resolvedHeads,
                                            dotValidationGap),
                                    beams,
                                    event.writtenAccidental(),
                                    beams != event.beamCount() ? 0 : event.unbeamedDurationBeats(),
                                    event.tupletDivisor(),
                                    silence,
                                    event.articulations(),
                                    event.clefBottomDiatonic(),
                                    event.crossStaffBeam(),
                                    event.leadingRestBeats(),
                                    event.compactOpening(),
                                    event.octaveShift(),
                                    event.boundaryTies(),
                                    event.tupletNormalNotes())
                            .withStemDirection(event.stemDirection())
                            .withTupletRatio(event.tupletDivisor(), event.tupletNormalNotes())
                            .withLeadingRest(leading)
                            .withKind(event.kind()));
        }
        // Rest exclusion revalidates dots beside individual ovals. A displaced
        // second still shares the surviving printed duration of its proved shaft.
        List<DetectedNote> validatedRhythms = new ArrayList<>(joined.size());
        for (int i = 0; i < joined.size(); i++)
            validatedRhythms.add(
                    new DetectedNote(withRests.get(i), joined.get(i).head, joined.get(i).staffGap));
        List<DetectedNote> chordRhythms =
                reconcileSingleShaftChords(validatedRhythms, labels, gray, width, height);
        for (int i = 0; i < chordRhythms.size(); i++) withRests.set(i, chordRhythms.get(i).event);
        List<NoteArticulationDetector.Anchor> anchors = new ArrayList<>();
        for (DetectedNote note : joined)
            anchors.add(
                    new NoteArticulationDetector.Anchor(
                            note.head.centerX,
                            note.head.centerY,
                            note.staffGap,
                            staffs.indexOf(
                                    staffForHead(labels, gray, width, height, staffs, note.head))));
        int[] marks = NoteArticulationDetector.detect(labels, gray, width, height, anchors);
        for (int i = 0; i < withRests.size(); i++)
            withRests.set(
                    i,
                    withRests
                            .get(i)
                            .withArticulations(withRests.get(i).articulations() | marks[i]));
        markGraceHeads(labels, gray, width, height, joined, withRests);
        List<CrossStaffBeamDetector.Head> beamHeads = new ArrayList<>();
        for (DetectedNote note : joined)
            beamHeads.add(
                    new CrossStaffBeamDetector.Head(
                            note.head.centerX, note.head.centerY, note.staffGap));
        withRests = CrossStaffBeamDetector.mark(withRests, beamHeads, gray, width, height);
        boolean[] printedAccidental = new boolean[joined.size()];
        for (int i = 0; i < joined.size(); i++)
            printedAccidental[i] = printedAccidentalHeads.contains(joined.get(i).head);
        var finalNotes =
                withoutPitchChangedTies(
                        OpeningMeasureLayout.mark(withRests, rests, measures),
                        keyChanges,
                        printedAccidental);
        List<ScoreNoteEvent> tiedNotes =
                markBoundaryTieEvidence(
                        tieLabels, tieGray, width, height, joined, finalNotes, measures.size());
        List<ScoreNoteEvent> voicedNotes = new ArrayList<>();
        for (int i = 0; i < tiedNotes.size(); i++) {
            DetectedNote printed = joined.get(i);
            voicedNotes.add(
                    tiedNotes
                            .get(i)
                            .withStemDirection(
                                    tiedNotes.get(i).kind() == ScoreNoteEvent.Kind.UNPITCHED
                                            ? tiedNotes.get(i).stemDirection()
                                            : tiedNotes.get(i).unbeamedDurationBeats()
                                                            == ScoreNoteEvent.DURATION_WHOLE
                                                    ? 0
                                                    : PrintedStemDirection.detect(
                                                            gray,
                                                            width,
                                                            height,
                                                            printed.head.centerX,
                                                            printed.head.centerY,
                                                            printed.staffGap)));
        }
        return new Analysis(voicedNotes, keyChanges, rests);
    }

    /** Keep both page-edge shoulders as evidence; assembly must still match pitch and continuity. */
    private static List<ScoreNoteEvent> markBoundaryTieEvidence(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<DetectedNote> detected,
            List<ScoreNoteEvent> notes,
            int measureCount) {
        if (gray == null || gray.length != (long) width * height) return notes;
        var result = new ArrayList<>(notes);
        for (int i = 0; i < notes.size(); i++) {
            var note = notes.get(i);
            int evidence = 0;
            if (note.measureIndex() != 0 && note.measureIndex() != measureCount - 1) continue;
            boolean first = true, last = true;
            for (var other : notes)
                if (other.measureIndex() == note.measureIndex()
                        && other.staffIndex() == note.staffIndex()
                        && other.staffCount() == note.staffCount()) {
                    if (other.positionInMeasure() < note.positionInMeasure() - .018f) first = false;
                    if (other.positionInMeasure() > note.positionInMeasure() + .018f) last = false;
                }
            if (note.measureIndex() == 0 && first && note.leadingRestBeats() == 0)
                for (int side : new int[] {-1, 1})
                    if (hasSystemEndTieArc(
                            labels, gray, width, height, detected.get(i), false, side))
                        evidence |= side < 0 ? 1 : 2;
            if (note.measureIndex() == measureCount - 1 && last && note.followingRestBeats() == 0)
                for (int side : new int[] {-1, 1})
                    if (hasSystemEndTieArc(
                            labels, gray, width, height, detected.get(i), true, side))
                        evidence |= side < 0 ? 4 : 8;
            if (evidence != 0) result.set(i, note.withBoundaryTies(evidence));
        }
        return result;
    }

    /** A printed slur may connect two heads at the same staff step while an
     * explicit accidental changes the sounding pitch. Such an arc is not a tie. */
    private static List<ScoreNoteEvent> withoutPitchChangedTies(
            List<ScoreNoteEvent> notes, List<ScoreKeyChange> keys, boolean[] printedAccidental) {
        if (keys.isEmpty()) return notes;
        if (printedAccidental.length != notes.size())
            throw new IllegalArgumentException("Note evidence mismatch");
        List<ScoreNoteEvent> result = new ArrayList<>(notes);
        for (int i = 0; i < notes.size(); i++) {
            ScoreNoteEvent current = notes.get(i);
            if (current.kind() != ScoreNoteEvent.Kind.PITCHED) continue;
            if (!printedAccidental[i]
                    || !current.tiedFromPrevious()
                    || current.writtenAccidental() == ScoreNoteEvent.ACCIDENTAL_FROM_KEY) continue;
            int currentAcc = resolvedTieAccidental(current, keys);
            if (currentAcc == Integer.MIN_VALUE) continue;
            boolean prior = false, matching = false;
            for (int j = i - 1; j >= 0; j--) {
                ScoreNoteEvent candidate = notes.get(j);
                if (current.measureIndex() - candidate.measureIndex() > 1) break;
                if (candidate.kind() != ScoreNoteEvent.Kind.PITCHED
                        || candidate.staffIndex() != current.staffIndex()
                        || candidate.staffCount() != current.staffCount()
                        || candidate.diatonicPitchIdentity() != current.diatonicPitchIdentity())
                    continue;
                prior = true;
                if (resolvedTieAccidental(candidate, keys) == currentAcc) {
                    matching = true;
                    break;
                }
            }
            if (!prior || matching) continue;
            result.set(
                    i,
                    new ScoreNoteEvent(
                                    current.measureIndex(),
                                    current.positionInMeasure(),
                                    current.staffStep(),
                                    current.staffIndex(),
                                    current.staffCount(),
                                    current.pageY(),
                                    false,
                                    current.augmentationDots(),
                                    current.beamCount(),
                                    current.writtenAccidental(),
                                    current.unbeamedDurationBeats(),
                                    current.tupletDivisor(),
                                    current.followingRestBeats(),
                                    current.articulations(),
                                    current.clefBottomDiatonic(),
                                    current.crossStaffBeam(),
                                    current.leadingRestBeats(),
                                    current.compactOpening(),
                                    current.octaveShift(),
                                    current.boundaryTies(),
                                    current.tupletNormalNotes())
                            .withStemDirection(current.stemDirection())
                            .withKind(current.kind()));
        }
        return result;
    }

    private static int resolvedTieAccidental(ScoreNoteEvent note, List<ScoreKeyChange> keys) {
        if (note.kind() != ScoreNoteEvent.Kind.PITCHED) return Integer.MIN_VALUE;
        if (note.writtenAccidental() != ScoreNoteEvent.ACCIDENTAL_FROM_KEY)
            return ScoreNoteEvent.accidentalSemitones(note.writtenAccidental());
        Integer fifths = null;
        for (ScoreKeyChange key : keys)
            if (key.measureIndex() <= note.measureIndex()) fifths = key.fifths();
        if (fifths == null) return Integer.MIN_VALUE;
        int letter = Math.floorMod(note.diatonicPitchIdentity(), 7);
        int[] order = fifths >= 0 ? TieKeyOrder.SHARPS : TieKeyOrder.FLATS;
        for (int k = 0; k < Math.min(7, Math.abs(fifths)); k++)
            if (order[k] == letter) return fifths > 0 ? 1 : -1;
        return 0;
    }

    /** The model can label the loose lower curl of a treble clef as a small head. */
    private static boolean isTrebleCurlFragment(
            Component head, List<Staff> staffs, List<Component> glyphs) {
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.gap;
        if (head.maxX - head.minX + 1 > gap * .95f
                || head.maxY - head.minY + 1 > gap * .8f
                || head.area > gap * gap * .5f
                || head.centerY < staff.bottom + gap * .2f
                || head.centerY > staff.bottom + gap * 2) return false;
        for (Component glyph : glyphs)
            if (glyph.maxY - glyph.minY > gap * 4.8f
                    && glyph.maxX - glyph.minX > gap * 1.25f
                    && glyph.minY < staff.top - gap * .35f
                    && glyph.maxY > staff.bottom
                    && Math.abs(glyph.centerY - (staff.top + staff.bottom) * .5f) < gap * 1.1f
                    && head.minX <= glyph.maxX + gap * .4f
                    && head.maxX >= glyph.minX
                    && head.centerX <= glyph.maxX + gap * .75f) return true;
        return false;
    }

    /** Excludes proven non-note header ink before OCR rest reconciliation, without modifying input masks. */
    static byte[] normalizeHeaderSymbols(
            byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
        if (labels == null
                || gray == null
                || width <= 0
                || height <= 0
                || labels.length != (long) width * height
                || gray.length != labels.length
                || measures == null
                || measures.isEmpty()) return labels;
        List<Staff> staffs = findStaffs(labels, gray, width, height, measures);
        List<Component> heads =
                findComponents(labels, width, height, OmrMeasurePostProcessor.NOTEHEAD);
        List<Component> glyphs =
                findComponents(labels, width, height, OmrMeasurePostProcessor.CLEF_OR_KEY);
        List<Component> symbols =
                findComponents(labels, width, height, OmrMeasurePostProcessor.SYMBOL);
        byte[] result = labels;
        for (Component head : heads) {
            int[] bounds =
                    PrintedNoteContrast.paperTexture(
                                    gray, width, height, head.minX, head.minY, head.maxX, head.maxY)
                            ? new int[] {head.minX, head.maxX, head.minY, head.maxY}
                            : null;
            if (bounds == null) bounds = uprightTextBowlBounds(gray, width, height, head, staffs);
            if (bounds == null && staffTextBounds(gray, width, height, head, staffs) != null)
                bounds = new int[] {head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null)
                bounds = commonTimeGlyphBounds(labels, gray, width, height, head, staffs, glyphs);
            if (bounds == null && isTempoUnitHead(gray, width, height, head, staffs))
                bounds = new int[] {head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null
                    && isRoundedHeaderMeter(labels, gray, width, height, head, staffs, glyphs))
                bounds = new int[] {head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null
                    && isStackedOpeningMeterFragment(gray, width, height, head, staffs, measures))
                bounds = new int[] {head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null && isHeavyRestBarFragment(gray, width, height, head, staffs))
                bounds = new int[] {head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null
                    && isHeaderFlatHead(labels, gray, width, height, head, staffs, glyphs))
                bounds = new int[] {head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null
                    && isOwnedHeaderCrossbar(
                            labels, gray, width, height, head, staffs, heads, glyphs, symbols))
                bounds = new int[] {head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null && isForteHookHead(gray, width, height, head, staffs, symbols))
                bounds = new int[] {head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null
                    && isZigzagOrnamentHead(labels, gray, width, height, head, staffs, heads))
                bounds = new int[] {head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null) continue;
            for (int y = bounds[2]; y <= bounds[3]; y++)
                for (int x = bounds[0]; x <= bounds[1]; x++) {
                    int at = y * width + x;
                    if (result[at] == OmrMeasurePostProcessor.NOTEHEAD) {
                        if (result == labels) result = labels.clone();
                        // Keep source ink for OCR; do not promote removed fragments to accidental
                        // candidates.
                        result[at] = 0;
                    }
                }
        }
        return normalizeDoubleBarSharps(result, gray, width, height, measures, staffs);
    }

    /** Recover a mixed-label sharp only in a separated slot after a full double bar. */
    private static byte[] normalizeDoubleBarSharps(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs) {
        byte[] result = labels;
        List<Component> noteHeads = findComponents(labels, width, height, (byte) 2);
        List<Component> seeds = new ArrayList<>();
        for (byte label : new byte[] {2, 3, 5})
            seeds.addAll(findComponents(labels, width, height, label));
        for (Staff staff : staffs)
            for (MeasureRegion measure : measures) {
                float cy = (staff.top + staff.bottom) * .5f, gap = staff.pitchGap;
                if (cy < measure.top() * height || cy > measure.bottom() * height) continue;
                float boundary = measure.left() * width;
                if (!hasDoubleBar(labels, gray, width, height, boundary, staff)) continue;
                for (Component seed : seeds) {
                    if (seed.centerX < boundary + gap * .2f
                            || seed.centerX > boundary + gap * 2.1f
                            || seed.centerY < staff.top - gap * 1.7f
                            || seed.centerY > staff.bottom + gap * 1.7f) continue;
                    int left = Math.max(0, Math.round(seed.centerX - gap * .7f)),
                            right = Math.min(width - 1, Math.round(seed.centerX + gap * .7f));
                    int top = Math.max(0, Math.round(seed.centerY - gap * 2.5f)),
                            bottom = Math.min(height - 1, Math.round(seed.centerY + gap * 2.5f));
                    if (left < boundary + gap * .05f) continue;
                    int w = right - left + 1, h = bottom - top + 1;
                    byte[] ink = new byte[w * h];
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++) {
                            byte v = labels[(top + y) * width + left + x];
                            if (v != 0 && v != 4) ink[y * w + x] = 3;
                        }
                    // Keep only the largest eight-connected glyph; nearby isolated marks stay
                    // unchanged.
                    byte[] remaining = ink.clone();
                    int[] queue = new int[ink.length], largest = new int[ink.length];
                    int largestSize = 0;
                    for (int origin = 0; origin < remaining.length; origin++) {
                        if (remaining[origin] == 0) continue;
                        int tail = 1;
                        queue[0] = origin;
                        remaining[origin] = 0;
                        for (int head = 0; head < tail; head++) {
                            int at = queue[head], xx = at % w, yy = at / w;
                            for (int ny = Math.max(0, yy - 1); ny <= Math.min(h - 1, yy + 1); ny++)
                                for (int nx = Math.max(0, xx - 1);
                                        nx <= Math.min(w - 1, xx + 1);
                                        nx++) {
                                    int next = ny * w + nx;
                                    if (remaining[next] != 0) {
                                        remaining[next] = 0;
                                        queue[tail++] = next;
                                    }
                                }
                        }
                        if (tail > largestSize) {
                            largestSize = tail;
                            System.arraycopy(queue, 0, largest, 0, tail);
                        }
                    }
                    java.util.Arrays.fill(ink, (byte) 0);
                    for (int i = 0; i < largestSize; i++) ink[largest[i]] = 3;
                    int area = 0, l = w, r = -1, t = h, b = -1;
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++)
                            if (ink[y * w + x] == 3) {
                                area++;
                                l = Math.min(l, x);
                                r = Math.max(r, x);
                                t = Math.min(t, y);
                                b = Math.max(b, y);
                            }
                    if (area == 0 || l == 0 || r == w - 1 || t == 0 || b == h - 1) continue;
                    Component glyph = null;
                    for (Component piece : findComponents(ink, w, h, (byte) 3))
                        if (glyph == null || piece.area > glyph.area) glyph = piece;
                    if (glyph == null) continue;
                    if (!isSharpGlyph(ink, w, h, new AccidentalCandidate(glyph, (byte) 3), gap))
                        continue;
                    // A first played chord must not be swallowed as a signature.
                    float firstHead = Float.POSITIVE_INFINITY;
                    for (Component head : noteHeads)
                        if (head.centerX > right
                                && Math.abs(head.centerY - cy) < gap * 5
                                && head.minY <= staff.bottom + gap * 3
                                && head.maxY >= staff.top - gap * 3)
                            firstHead = Math.min(firstHead, head.minX);
                    if (!Float.isFinite(firstHead)
                            || firstHead - (left + glyph.centerX) < gap * 1.35f) continue;
                    if (result == labels) result = labels.clone();
                    for (int y = t; y <= b; y++)
                        for (int x = l; x <= r; x++)
                            if (ink[y * w + x] == 3) result[(top + y) * width + left + x] = 3;
                }
            }
        return result;
    }

    /** A treble clef's round lower tip may be split into the notehead class. */
    private static boolean isTrebleTailHead(
            byte[] labels,
            int width,
            int height,
            Component head,
            List<Staff> staffs,
            List<Component> glyphs) {
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.pitchGap;
        if (head.centerY < staff.pitchBottom + gap * .25f
                || head.centerY > staff.pitchBottom + gap * 1.6f
                || head.maxX - head.minX + 1 > gap * 1.2f
                || head.maxY - head.minY + 1 > gap * 1.2f) return false;
        for (Component original : glyphs) {
            Component glyph =
                    joinTrebleCurl(
                            joinSmallTrebleFragments(original, glyphs, staff), glyphs, staff);
            float gh = glyph.maxY - glyph.minY + 1, gw = glyph.maxX - glyph.minX + 1;
            if (gh < gap * 4.8f
                    || gh > gap * 8.8f
                    || gw < gap * 1.25f
                    || gw > gap * 3.4f
                    || glyph.area < gap * gap * 1.65f
                    || glyph.minY >= staff.top - gap * .35f
                    || glyph.maxY <= staff.bottom + gap * .2f
                    || Math.abs(glyph.centerY - (staff.top + staff.bottom) * .5f) >= gap * 1.1f
                    || head.minX < glyph.minX
                    || head.maxX > glyph.maxX
                    || head.maxY > glyph.maxY + gap * .3f) continue;
            // Require direct contact with clef ink, not merely a nearby note below it.
            for (int y = head.minY; y <= head.maxY; y++)
                for (int x = head.minX; x <= head.maxX; x++) {
                    if (labels[y * width + x] != OmrMeasurePostProcessor.NOTEHEAD) continue;
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx, yy = y + dy;
                            if (xx < glyph.minX
                                    || xx > glyph.maxX
                                    || yy < glyph.minY
                                    || yy > glyph.maxY
                                    || xx < 0
                                    || xx >= width
                                    || yy < 0
                                    || yy >= height) continue;
                            if (labels[yy * width + xx] == OmrMeasurePostProcessor.CLEF_OR_KEY)
                                return true;
                        }
                }
        }
        return false;
    }

    /** Small connected header crossbars belong to a symbolic glyph, not a chord.
     * This establishes ownership only; it does not infer an accidental value. */
    private static boolean isOwnedHeaderCrossbar(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Staff> staffs,
            List<Component> heads,
            List<Component> clefs,
            List<Component> symbols) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.pitchGap, top = staff.pitchBottom - gap * 4;
        if (head.area > gap * gap * .5f
                || head.maxX - head.minX + 1 > gap * 1.05f
                || head.maxY - head.minY + 1 > gap * .8f
                || head.centerX > width * .25f
                || head.centerY < top - gap * .8f
                || head.centerY > staff.pitchBottom + gap * .8f
                || attachedRawStem(gray, width, height, head, gap * .65f, 2, 180) != null
                || hasOpenCenter(labels, gray, width, height, head, gap)) return false;
        boolean clef = false;
        List<Component> parts = new ArrayList<>(clefs);
        parts.addAll(symbols);
        for (Component seed : clefs) {
            if (seed.area < gap * gap * .6f
                    || seed.maxX >= head.minX - gap * .6f
                    || head.minX - seed.maxX > gap * 4
                    || Math.abs(seed.centerY - (top + gap * 2)) > gap * 2) continue;
            Component joined = seed;
            for (int pass = 0; pass < 3; pass++)
                for (Component part : parts) {
                    if (part.minX >= joined.minX
                            && part.maxX <= joined.maxX
                            && part.minY >= joined.minY
                            && part.maxY <= joined.maxY) continue;
                    if (part.area < gap * gap * .025f
                            || part.minX < seed.minX - gap * .8f
                            || part.maxX > head.minX - gap * .6f
                            || part.maxX > seed.maxX + gap * 1.2f
                            || part.minY < top - gap * 3
                            || part.maxY > staff.pitchBottom + gap * 1.8f
                            || part.minY > joined.maxY + gap * 1.2f
                            || part.maxY < joined.minY - gap * 1.2f) continue;
                    int area = joined.area + part.area;
                    joined =
                            new Component(
                                    area,
                                    Math.min(joined.minX, part.minX),
                                    Math.max(joined.maxX, part.maxX),
                                    Math.min(joined.minY, part.minY),
                                    Math.max(joined.maxY, part.maxY),
                                    (joined.centerX * joined.area + part.centerX * part.area)
                                            / area,
                                    (joined.centerY * joined.area + part.centerY * part.area)
                                            / area);
                }
            if (joined.maxY - joined.minY >= gap * 5
                    && joined.maxY - joined.minY <= gap * 8.8f
                    && joined.maxX - joined.minX >= gap * 1.25f
                    && joined.maxX - joined.minX <= gap * 4
                    && joined.area >= gap * gap * 3
                    && joined.minY < top - gap * .6f
                    && joined.maxY > staff.pitchBottom + gap * .2f) {
                clef = true;
                break;
            }
        }
        if (!clef) return false;
        for (Component other : heads) {
            if (other == head
                    || other.area > gap * gap * .5f
                    || other.maxX - other.minX + 1 > gap * 1.05f
                    || other.maxY - other.minY + 1 > gap * .8f
                    || Math.abs(other.centerX - head.centerX) > gap * .3f
                    || Math.abs(other.centerY - head.centerY) < gap * .8f
                    || Math.abs(other.centerY - head.centerY) > gap * 1.4f
                    || attachedRawStem(gray, width, height, other, gap * .65f, 2, 180) != null
                    || hasOpenCenter(labels, gray, width, height, other, gap)) continue;
            boolean following = false;
            for (Component main : heads)
                if (main.area > gap * gap * .65f
                        && main.minX > Math.max(head.maxX, other.maxX) + gap
                        && main.minX < Math.max(head.maxX, other.maxX) + gap * 6
                        && nearestHeadStaff(staffs, main.centerY) == staff) {
                    following = true;
                    break;
                }
            if (!following) continue;
            boolean primarySlot =
                    Math.abs((other.centerY + head.centerY) * .5f - top) <= gap * .25f;
            boolean orderedSecond = false;
            // The next signature sharp is a fourth below the first. Its faded spine
            // may lose the extreme tips, but two connected bars plus the independently
            // owned first glyph establish the ordered header pair, not a played chord.
            if (!primarySlot
                    && Math.abs((other.centerY + head.centerY) * .5f - (top + gap * 1.5f))
                            <= gap * .3f)
                for (Component before : heads)
                    if (before.centerX < head.centerX - gap * .7f
                            && before.centerX > head.centerX - gap * 2.2f
                            && Math.abs(before.centerY - top) < gap * .65f
                            && isOwnedHeaderCrossbar(
                                    labels, gray, width, height, before, staffs, heads, clefs,
                                    symbols)) {
                        orderedSecond = true;
                        break;
                    }
            int left = Math.round(Math.min(head.minX, other.minX) - gap * .6f),
                    right = Math.round(Math.max(head.maxX, other.maxX) + gap * .6f);
            int first = Math.min(head.minY, other.minY), last = Math.max(head.maxY, other.maxY);
            int y0 = Math.round(first - gap * .8f), y1 = Math.round(last + gap * .8f);
            int reach = Math.round(gap * .6f), probe = Math.max(2, Math.round(gap * .16f));
            if (left < reach || right + reach >= width || y0 < probe || y1 + probe >= height)
                continue;
            int w = right - left + 1, h = y1 - y0 + 1;
            byte[] ink = new byte[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if ((gray[(y0 + y) * width + left + x] & 255) < 235
                            && (headerInkContrast(gray, width, left + x, y0 + y, reach) >= 12
                                    || (gray[(y0 + y - probe) * width + left + x] & 255) < 235
                                            && (gray[(y0 + y + probe) * width + left + x] & 255)
                                                    < 235
                                            && headerInkContrast(
                                                            gray,
                                                            width,
                                                            left + x,
                                                            y0 + y - probe,
                                                            reach)
                                                    >= 12
                                            && headerInkContrast(
                                                            gray,
                                                            width,
                                                            left + x,
                                                            y0 + y + probe,
                                                            reach)
                                                    >= 12)) ink[y * w + x] = 5;
            // A faded staff crossing may interrupt an otherwise narrow upright spine.
            // Bridge only short vertical gaps; separate crossbars remain unconnected.
            byte[] originalInk = ink.clone();
            for (int x = 1; x < w - 1; x++)
                for (int y = 1; y < h - 1; y++)
                    if (originalInk[y * w + x] == 0 && originalInk[(y - 1) * w + x] != 0) {
                        int end = y;
                        while (end < h - 1
                                && originalInk[end * w + x] == 0
                                && end - y < Math.round(gap * .35f)) end++;
                        if (end < h - 1 && originalInk[end * w + x] != 0)
                            for (int fill = y; fill < end; fill++) ink[fill * w + x] = 5;
                    }
            Component glyph = retainSeedConnectedInk(ink, w, h, head, left, y0);
            if (glyph == null) continue;
            if (glyph.minX == 0
                    || glyph.maxX == w - 1
                    || glyph.minY == 0
                    || glyph.maxY == h - 1
                    || glyph.maxY - glyph.minY < gap * (orderedSecond ? 1.85f : 2)
                    || glyph.maxY - glyph.minY > gap * 3.8f
                    || glyph.maxX - glyph.minX > gap * 1.8f
                    || y0 + glyph.minY > first - gap * (orderedSecond ? .1f : .3f)
                    || y0 + glyph.maxY < last + gap * (orderedSecond ? .1f : .3f)) continue;
            int shared = 0;
            for (int y = other.minY; y <= other.maxY; y++)
                for (int x = other.minX; x <= other.maxX; x++)
                    if (ink[(y - y0) * w + x - left] != 0) shared++;
            if (shared >= other.area * .5f
                    && (primarySlot
                            || orderedSecond
                            || isSharpGlyph(
                                    ink,
                                    w,
                                    h,
                                    new AccidentalCandidate(glyph, OmrMeasurePostProcessor.SYMBOL),
                                    gap))) return true;
        }
        return false;
    }

    /** A key flat can be split between an accidental spine and a note-labelled bowl.
     * Require a nearby printed clef, semantic spine evidence and the complete raw flat. */
    private static boolean isHeaderFlatHead(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Staff> staffs,
            List<Component> glyphs) {
        if (gray == null || gray.length != width * height) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.pitchGap;
        if (head.maxX - head.minX + 1 > gap
                || head.maxY - head.minY + 1 > gap
                || head.area > gap * gap * .55f
                || head.centerY < staff.top - gap
                || head.centerY > staff.bottom + gap) return false;
        boolean header = false;
        for (Component original : glyphs) {
            Component c =
                    joinTrebleCurl(
                            joinSmallTrebleFragments(original, glyphs, staff), glyphs, staff);
            c = joinHeaderClefFragments(c, glyphs, staff);
            if (c.maxX >= head.minX || head.minX - c.maxX > gap * 4) continue;
            float gh = c.maxY - c.minY + 1, gw = c.maxX - c.minX + 1;
            boolean treble =
                    gh >= gap * 4.8f
                            && gh <= gap * 8.8f
                            && gw >= gap * 1.25f
                            && gw <= gap * 3.4f
                            && c.area >= gap * gap * 1.65f
                            && c.minY < staff.top - gap * .35f
                            && c.maxY > staff.bottom + gap * .2f
                            && Math.abs(c.centerY - (staff.top + staff.bottom) * .5f) < gap * 1.1f;
            if (treble || rawBassClef(c, gray, width, height, staff)) {
                header = true;
                break;
            }
        }
        if (!header) return false;
        for (Component original : glyphs) {
            Component seed = joinHeaderFlatSpine(original, glyphs, head, gap);
            if (seed.minX >= head.minX
                    || seed.maxX < head.minX - gap * .6f
                    || seed.maxX - seed.minX + 1 > gap * .85f
                    || seed.maxY - seed.minY + 1 < gap * .55f
                    || seed.minY > head.centerY - gap * .9f
                    || seed.maxY < head.minY - gap * .9f) continue;
            for (int threshold : new int[] {180, 235}) {
                int margin = Math.max(1, Math.round(gap * .16f));
                int left = Math.max(0, Math.min(seed.minX, head.minX) - margin);
                int right =
                        Math.min(
                                width - 1,
                                Math.max(seed.maxX, head.maxX)
                                        + (threshold == 180 ? margin : Math.round(gap * .5f)));
                int top = Math.max(0, Math.round(head.centerY - gap * 2.7f));
                int bottom = Math.min(height - 1, Math.round(head.centerY + gap * .8f));
                int w = right - left + 1, h = bottom - top + 1;
                if (w <= 0 || h <= 0) continue;
                byte[] ink = new byte[w * h];
                int area = 0, minX = w, maxX = -1, minY = h, maxY = -1;
                long sx = 0, sy = 0;
                int reach = Math.max(3, Math.round(gap * .6f)),
                        probe = Math.max(2, Math.round(gap * .2f));
                for (int y = top; y <= bottom; y++) {
                    int outside = 0, dark = 0;
                    for (int x = Math.max(0, left - reach);
                            x <= Math.min(width - 1, right + reach);
                            x++)
                        if (x < left || x > right) {
                            outside++;
                            if ((gray[y * width + x] & 255) <= threshold) dark++;
                        }
                    boolean rule = outside > 0 && dark >= outside * .8f;
                    for (int x = left; x <= right; x++) {
                        if ((gray[y * width + x] & 255) > threshold) continue;
                        if (rule
                                && (y < probe
                                        || y + probe >= height
                                        || (gray[(y - probe) * width + x] & 255) > threshold
                                        || (gray[(y + probe) * width + x] & 255) > threshold))
                            continue;
                        if (threshold > 180) {
                            int contrastReach = Math.round(gap * .6f);
                            if (x >= contrastReach
                                    && x + contrastReach < width
                                    && headerInkContrast(gray, width, x, y, contrastReach) < 12
                                    && (!rule
                                            || headerInkContrast(
                                                            gray,
                                                            width,
                                                            x,
                                                            y - probe,
                                                            contrastReach)
                                                    < 12
                                            || headerInkContrast(
                                                            gray,
                                                            width,
                                                            x,
                                                            y + probe,
                                                            contrastReach)
                                                    < 12)) continue;
                        }
                        int xx = x - left, yy = y - top;
                        ink[yy * w + xx] = OmrMeasurePostProcessor.SYMBOL;
                        area++;
                        sx += xx;
                        sy += yy;
                        minX = Math.min(minX, xx);
                        maxX = Math.max(maxX, xx);
                        minY = Math.min(minY, yy);
                        maxY = Math.max(maxY, yy);
                    }
                }
                if (threshold > 180) {
                    Component connected = retainSeedConnectedInk(ink, w, h, head, left, top);
                    if (connected == null) continue;
                    area = connected.area;
                    minX = connected.minX;
                    maxX = connected.maxX;
                    minY = connected.minY;
                    maxY = connected.maxY;
                    sx = Math.round(connected.centerX * area);
                    sy = Math.round(connected.centerY * area);
                }
                if (area == 0) continue;
                var flat =
                        new AccidentalCandidate(
                                new Component(
                                        area,
                                        minX,
                                        maxX,
                                        minY,
                                        maxY,
                                        sx / (float) area,
                                        sy / (float) area),
                                OmrMeasurePostProcessor.SYMBOL);
                // A fragmented note label may cover only the lower tip of a
                // proven flat bowl, away from the accidental's pitch center.
                if (!isNaturalGlyph(ink, w, h, flat, gap)
                        && !isSharpGlyph(ink, w, h, flat, gap)
                        && isFlatGlyph(ink, w, h, flat, gap)
                        && (threshold == 180
                                ? Math.abs(top + flatPitchCenter(ink, w, flat, gap) - head.centerY)
                                        <= gap * .5f
                                : head.centerY >= top + minY + (maxY - minY + 1) * .55f
                                        && head.centerY <= top + maxY)) return true;
            }
        }
        return false;
    }

    private static float headerInkContrast(byte[] gray, int width, int x, int y, int reach) {
        return ((gray[y * width + x - reach] & 255) + (gray[y * width + x + reach] & 255)) * .5f
                - (gray[y * width + x] & 255);
    }

    /** Reconnect upper and lower clef pieces only around an already large body.
     * The complete header-clef checks still decide whether this is a clef. */
    private static Component joinHeaderClefFragments(
            Component body, List<Component> glyphs, Staff staff) {
        float gap = staff.gap;
        if (body.maxY - body.minY < gap * 3
                || body.maxY - body.minY > gap * 7
                || body.maxX - body.minX < gap * 1.3f
                || body.maxX - body.minX > gap * 3.2f
                || body.area < gap * gap * 1.65f
                || body.minY > staff.top + gap * .5f
                || body.minY < staff.top - gap * 3) return body;
        Component joined = body;
        for (Component part : glyphs) {
            if (part.minX >= joined.minX
                    && part.maxX <= joined.maxX
                    && part.minY >= joined.minY
                    && part.maxY <= joined.maxY) continue;
            if (part == body
                    || part.minX < body.minX - gap * .2f
                    || part.maxX > body.maxX + gap * .6f
                    || part.minY < body.minY - gap * 2.5f
                    || part.maxY > body.maxY + gap * 2
                    || part.minY > joined.maxY + gap * 1.2f
                    || part.maxY < joined.minY - gap * 1.2f) continue;
            int area = joined.area + part.area;
            joined =
                    new Component(
                            area,
                            Math.min(joined.minX, part.minX),
                            Math.max(joined.maxX, part.maxX),
                            Math.min(joined.minY, part.minY),
                            Math.max(joined.maxY, part.maxY),
                            (joined.centerX * joined.area + part.centerX * part.area) / area,
                            (joined.centerY * joined.area + part.centerY * part.area) / area);
        }
        return joined;
    }

    /** Staff labels can interrupt a flat spine into several narrow pieces. */
    private static Component joinHeaderFlatSpine(
            Component seed, List<Component> glyphs, Component head, float gap) {
        if (seed.maxX - seed.minX + 1 > gap * .85f) return seed;
        Component joined = seed;
        for (Component part : glyphs) {
            if (part.minX >= joined.minX
                    && part.maxX <= joined.maxX
                    && part.minY >= joined.minY
                    && part.maxY <= joined.maxY) continue;
            if (part == seed
                    || part.minX < seed.minX - gap * .2f
                    || part.maxX > seed.maxX + gap * .2f
                    || part.minY < head.centerY - gap * 2.7f
                    || part.maxY > head.centerY + gap * .65f
                    || part.minY > joined.maxY + gap * .65f
                    || part.maxY < joined.minY - gap * .65f
                    || Math.max(joined.maxX, part.maxX) - Math.min(joined.minX, part.minX) + 1
                            > gap * .85f) continue;
            int area = joined.area + part.area;
            joined =
                    new Component(
                            area,
                            Math.min(joined.minX, part.minX),
                            Math.max(joined.maxX, part.maxX),
                            Math.min(joined.minY, part.minY),
                            Math.max(joined.maxY, part.maxY),
                            (joined.centerX * joined.area + part.centerX * part.area) / area,
                            (joined.centerY * joined.area + part.centerY * part.area) / area);
        }
        return joined;
    }

    /** A tiny upper hook can belong to an italic forte rather than a note.
     * Require the descending symbol body, separate cross-stroke and lower hook. */
    private static boolean isForteHookHead(
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Staff> staffs,
            List<Component> symbols) {
        if (gray == null || gray.length != width * height) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.pitchGap;
        if (head.minY < staff.pitchBottom + gap * .75f
                || head.centerY > staff.pitchBottom + gap * 3.5f
                || head.maxX - head.minX + 1 > gap
                || head.maxY - head.minY + 1 > gap * .8f
                || head.area > gap * gap * .55f
                || attachedRawStem(gray, width, height, head, gap) != null) return false;
        for (Component body : symbols) {
            if (body.area < head.area * 2
                    || body.maxY < head.maxY + gap * 1.2f
                    || body.minY > head.maxY + gap * .3f
                    || body.maxY > head.maxY + gap * 3
                    || body.minX > head.minX - gap * .6f
                    || body.maxX < head.minX
                    || body.maxX > head.maxX + gap * .5f
                    || body.maxX - body.minX < gap) continue;
            int margin = Math.max(2, Math.round(gap * .2f));
            int left = Math.max(0, Math.min(body.minX, head.minX) - margin);
            int right = Math.min(width - 1, Math.max(body.maxX, head.maxX) + margin);
            int top = Math.max(0, Math.min(body.minY, head.minY) - margin);
            int bottom = Math.min(height - 1, Math.max(body.maxY, head.maxY) + margin);
            int w = right - left + 1, h = bottom - top + 1;
            if (w > gap * 3.2f || h > gap * 4.2f) continue;
            byte[] ink = new byte[w * h];
            for (int y = top; y <= bottom; y++)
                for (int x = left; x <= right; x++)
                    if ((gray[y * width + x] & 255) <= 205) ink[(y - top) * w + x - left] = 5;
            Component glyph = retainSeedConnectedInk(ink, w, h, head, left, top);
            if (glyph == null
                    || rawStrokeLeavesCrop(gray, width, height, ink, w, h, left, top, gap))
                continue;
            int gh = glyph.maxY - glyph.minY + 1, gw = glyph.maxX - glyph.minX + 1;
            if (gh < gap * 1.8f
                    || gh > gap * 3.8f
                    || gw < gap
                    || gw > gap * 3
                    || head.centerY > top + glyph.minY + gh * .3f) continue;
            if (ForteInkShape.matches(ink, w, glyph.minX, glyph.maxX, glyph.minY, glyph.maxY, gap))
                return true;
        }
        return false;
    }

    /** A bounded zigzag above a larger note can contain a false semantic oval.
     * Its dark center must fall, rise, and fall again across four distinct lobes. */
    private static boolean isZigzagOrnamentHead(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Staff> staffs,
            List<Component> heads) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.pitchGap;
        if (head.maxY > staff.pitchBottom - gap * 4.5f
                || head.centerY < staff.pitchBottom - gap * 8
                || head.area > gap * gap * .9f
                || head.maxX - head.minX + 1 > gap * 1.3f
                || head.maxY - head.minY + 1 > gap
                || attachedRawStem(gray, width, height, head, gap * .65f, 2, 180) != null)
            return false;
        boolean owner = false;
        for (Component main : heads)
            if (main != head
                    && main.area >= head.area * 1.4f
                    && main.maxX - main.minX + 1 >= gap
                    && Math.abs(main.centerX - head.centerX) < gap
                    && main.centerY - head.centerY >= gap * 2
                    && main.centerY - head.centerY <= gap * 7
                    && nearestHeadStaff(staffs, main.centerY) == staff) {
                owner = true;
                break;
            }
        if (!owner) return false;
        int left = Math.round(head.minX - gap * 1.4f), right = Math.round(head.maxX + gap * .8f);
        int top = Math.round(head.minY - gap * .3f), bottom = Math.round(head.maxY + gap * .3f);
        if (left < 0 || right >= width || top < 0 || bottom >= height) return false;
        int w = right - left + 1, h = bottom - top + 1;
        byte[] ink = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((gray[(top + y) * width + left + x] & 255) < 205) ink[y * w + x] = 5;
        Component glyph = retainSeedConnectedInk(ink, w, h, head, left, top);
        if (glyph == null) return false;
        int span = glyph.maxX - glyph.minX + 1, rise = glyph.maxY - glyph.minY + 1, symbol = 0;
        if (glyph.minX == 0
                || glyph.maxX == w - 1
                || glyph.minY == 0
                || glyph.maxY == h - 1
                || span < gap * 1.8f
                || span > gap * 3.2f
                || rise < gap * .6f
                || rise > gap * 1.3f) return false;
        float[] centers = new float[w];
        java.util.Arrays.fill(centers, Float.NaN);
        int first = w, last = -1;
        for (int x = glyph.minX; x <= glyph.maxX; x++) {
            int count = 0, sum = 0, runs = 0;
            boolean previous = false;
            for (int y = glyph.minY; y <= glyph.maxY; y++) {
                if (ink[y * w + x] != 0 && labels[(top + y) * width + left + x] == 5) symbol++;
                boolean dark =
                        ink[y * w + x] != 0 && (gray[(top + y) * width + left + x] & 255) < 165;
                if (dark) {
                    count++;
                    sum += y;
                    if (!previous) runs++;
                }
                previous = dark;
            }
            if (runs > 1) return false;
            if (count > 0) {
                centers[x] = sum / (float) count;
                first = Math.min(first, x);
                last = x;
            }
        }
        if (symbol < glyph.area * .2f || last - first + 1 < gap * 1.4f) return false;
        float[] extremes = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        int[] at = new int[4];
        for (int x = first + 1; x < last; x++) {
            if (!Float.isFinite(centers[x - 1] + centers[x] + centers[x + 1])) return false;
            float value = (centers[x - 1] + centers[x] + centers[x + 1]) / 3;
            int bin = Math.min(3, (x - first) * 4 / (last - first + 1));
            if (bin % 2 == 0 ? value < extremes[bin] : value > extremes[bin]) {
                extremes[bin] = value;
                at[bin] = x;
            }
        }
        for (int i = 1; i < 4; i++) if (at[i] - at[i - 1] < gap * .3f) return false;
        return extremes[1] - extremes[0] >= gap * .12f
                && extremes[1] - extremes[2] >= gap * .12f
                && extremes[3] - extremes[2] >= gap * .12f;
    }

    /** A tall detached count above a fully capped multimeasure-rest bar. */
    private static boolean isHeavyRestCount(
            byte[] gray, int width, int height, Component head, Staff staff) {
        if (gray == null) return false;
        float gap = staff.pitchGap, top = staff.pitchBottom - gap * 4;
        if (head.maxY >= top - gap * .1f || head.centerY < top - gap * 3.5f) return false;
        boolean countShape = false;
        for (TempoInk glyph :
                tempoInk(
                        gray,
                        width,
                        height,
                        Math.round(head.centerX - gap * 2.5f),
                        Math.round(head.centerX + gap * 2.5f),
                        Math.round(top - gap * 4),
                        Math.round(top))) {
            if (head.centerX < glyph.left
                    || head.centerX > glyph.right
                    || head.centerY < glyph.top
                    || head.centerY > glyph.bottom) continue;
            if (glyph.height() > gap * 1.3f
                    && glyph.height() < gap * 3.3f
                    && glyph.width() > gap * .4f
                    && glyph.width() < glyph.height() * .95f) countShape = true;
        }
        if (!countShape) return false;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        if (stem != null && Math.abs(stem[1] - head.centerY) > gap * 2) return false;
        for (int y = Math.max(0, Math.round(staff.pitchBottom - gap * 2.7f));
                y <= Math.min(height - 1, Math.round(staff.pitchBottom - gap * 1.3f));
                y++)
            if (heavyRestBarAtRow(gray, width, height, Math.round(head.centerX), y, gap))
                return true;
        return false;
    }

    /** A small semantic oval can lie entirely inside a thick ending barline. */
    private static boolean isThickBarlineHead(
            byte[] gray, int width, int height, Component head, List<Staff> staffs) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.pitchGap, top = staff.pitchBottom - gap * 4, bottom = staff.pitchBottom;
        if (head.minY < top || head.maxY > bottom || head.maxX - head.minX + 1 > gap * .85f)
            return false;
        int cx = Math.round(head.centerX),
                first = Math.round(top + gap * .2f),
                last = Math.round(bottom - gap * .2f);
        if (cx < 0 || cx >= width || first < 0 || last >= height) return false;
        int minLeft = width, maxLeft = -1, minRight = width, maxRight = -1, rows = 0;
        for (int y = first; y <= last; y++) {
            float ruleDistance = Math.abs((y - top) / gap - Math.round((y - top) / gap)) * gap;
            if (ruleDistance < gap * .18f) continue;
            if ((gray[y * width + cx] & 255) >= 165) return false;
            int left = cx, right = cx;
            while (left > 0 && (gray[y * width + left - 1] & 255) < 165) left--;
            while (right + 1 < width && (gray[y * width + right + 1] & 255) < 165) right++;
            int span = right - left + 1;
            if (span < gap * .25f || span > gap * .85f) return false;
            minLeft = Math.min(minLeft, left);
            maxLeft = Math.max(maxLeft, left);
            minRight = Math.min(minRight, right);
            maxRight = Math.max(maxRight, right);
            rows++;
        }
        int tolerance = Math.max(1, Math.round(gap * .12f));
        if (rows < gap
                || maxLeft - minLeft > tolerance
                || maxRight - minRight > tolerance
                || head.minX < minLeft - 1
                || head.maxX > maxRight + 1) return false;
        // A true head protrudes from its stem; a plain bar ends at the outer staff rules.
        for (int y : new int[] {Math.round(top - gap * .3f), Math.round(bottom + gap * .3f)}) {
            if (y < 0 || y >= height || (gray[y * width + cx] & 255) < 165) return false;
        }
        return true;
    }

    /** A whole-measure rest hangs as a filled rectangle below the second rule. */
    private static boolean isWholeMeasureRestHead(
            byte[] gray, int width, int height, Component head, List<Staff> staffs) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.pitchGap, rule = staff.pitchBottom - gap * 3;
        if (head.centerY < rule + gap * .12f
                || head.centerY > rule + gap * .7f
                || attachedRawStem(gray, width, height, head, gap) != null) return false;
        int cx = Math.round(head.centerX), start = Math.max(0, Math.round(rule + gap * .15f));
        int end = Math.min(height - 1, Math.round(rule + gap * .8f));
        int minX = width, maxX = -1, first = -1, last = -1;
        int[] lefts = new int[end - start + 1], rights = new int[end - start + 1];
        java.util.Arrays.fill(lefts, -1);
        for (int y = start; y <= end; y++) {
            if ((gray[y * width + cx] & 255) >= 180) continue;
            int left = cx, right = cx;
            while (left > 0 && (gray[y * width + left - 1] & 255) < 180) left--;
            while (right + 1 < width && (gray[y * width + right + 1] & 255) < 180) right++;
            if (right - left + 1 > gap * 1.8f) return false;
            lefts[y - start] = left;
            rights[y - start] = right;
            minX = Math.min(minX, left);
            maxX = Math.max(maxX, right);
            if (first < 0) first = y;
            last = y;
        }
        int w = maxX - minX + 1, h = last - first + 1;
        if (first < 0
                || first > rule + gap * .25f
                || last >= end
                || w < gap * .9f
                || h < gap * .3f
                || h > gap * .65f) return false;
        int tolerance = Math.max(1, Math.round(gap * .1f));
        for (int y = first; y <= last; y++) {
            int left = lefts[y - start], right = rights[y - start];
            if (left < 0 || left - minX > tolerance || maxX - right > tolerance) return false;
        }
        Component block =
                new Component(
                        w * h, minX, maxX, first, last, (minX + maxX) * .5f, (first + last) * .5f);
        if (attachedRawStem(gray, width, height, block, gap) != null) return false;
        // The supporting staff rule must continue on both sides of the block.
        int lineY = Math.round(rule), support = 0;
        for (int side : new int[] {-1, 1}) {
            int x = Math.round((side < 0 ? minX : maxX) + side * gap * .5f);
            if (x < 0 || x >= width) continue;
            for (int y = Math.max(0, lineY - 2); y <= Math.min(height - 1, lineY + 2); y++)
                if ((gray[y * width + x] & 255) < 180) {
                    support++;
                    break;
                }
        }
        return support == 2;
    }

    /** A compact prediction inside the thick centre of a two-capped multimeasure rest.
     * Both end caps must extend beyond both edges of the horizontal band. */
    private static boolean isHeavyRestBarFragment(
            byte[] gray, int width, int height, Component head, List<Staff> staffs) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.pitchGap;
        if (Math.abs(head.centerY - (staff.pitchBottom - gap * 2)) > gap * .7f) return false;
        boolean compact =
                head.maxX - head.minX + 1 <= gap * .8f
                        && head.maxY - head.minY + 1 <= gap * 1.25f
                        && head.area <= gap * gap * .4f;
        for (int y = head.minY; y <= head.maxY; y++)
            if (compact && heavyRestBarAtRow(gray, width, height, Math.round(head.centerX), y, gap)
                    || countedRestBodyAtRow(gray, width, height, head, y, staff)) return true;
        return false;
    }

    /** A readable count corroborates shorter H-rests and larger mask islands in their band. */
    private static boolean countedRestBodyAtRow(
            byte[] gray, int width, int height, Component head, int y, Staff staff) {
        float gap = staff.pitchGap;
        int x = Math.round(head.centerX);
        if (head.maxY - head.minY + 1 > gap * 1.2f || (gray[y * width + x] & 255) >= 165)
            return false;
        int left = x, right = x;
        while (left > 0 && (gray[y * width + left - 1] & 255) < 165) left--;
        while (right + 1 < width && (gray[y * width + right + 1] & 255) < 165) right++;
        if (right - left < gap * 4 || right - left > gap * 60) return false;
        int top = y, bottom = y;
        while (top > 0 && restBandRow(gray, width, left, right, top - 1)) top--;
        while (bottom + 1 < height && restBandRow(gray, width, left, right, bottom + 1)) bottom++;
        if (bottom - top + 1 < gap * .35f
                || bottom - top + 1 > gap * 1.2f
                || head.minX < left
                || head.maxX > right
                || head.minY < top - gap * .2f
                || head.maxY > bottom + gap * .2f
                || !restEndCap(gray, width, height, left, top, bottom, gap)
                || !restEndCap(gray, width, height, right, top, bottom, gap)) return false;
        float staffTop = staff.pitchBottom - gap * 4;
        MeasureRegion region =
                new MeasureRegion(
                        Math.max(0, (left - gap) / width),
                        Math.min(1, (right + gap) / width),
                        Math.max(0, (staffTop - gap * 2) / height),
                        Math.min(1, (staff.pitchBottom + gap * 2) / height));
        var count =
                MultiMeasureRestDetector.standaloneCount(
                        gray,
                        width,
                        height,
                        new MultiMeasureRestDetector.RestBarCandidate(0, region));
        return count != null
                && count.bottom() * height < staffTop
                && Math.abs((count.left() + count.right()) * .5f * width - (left + right) * .5f)
                        < gap;
    }

    private static boolean heavyRestBarAtRow(
            byte[] gray, int width, int height, int x, int y, float gap) {
        // A thin staff rule can run through the centre of the thick rest. Inspect
        // the predicted fragment's rows so that rule cannot hide the end caps.
        if ((gray[y * width + x] & 255) >= 165) return false;
        int left = x, right = x;
        while (left > 0 && (gray[y * width + left - 1] & 255) < 165) left--;
        while (right + 1 < width && (gray[y * width + right + 1] & 255) < 165) right++;
        if (right - left < gap * 6) return false;
        int top = y, bottom = y;
        while (top > 0 && restBandRow(gray, width, left, right, top - 1)) top--;
        while (bottom + 1 < height && restBandRow(gray, width, left, right, bottom + 1)) bottom++;
        int thick = bottom - top + 1;
        if (thick < gap * .35f || thick > gap * 1.2f) return false;
        return restEndCap(gray, width, height, left, top, bottom, gap)
                && restEndCap(gray, width, height, right, top, bottom, gap);
    }

    private static boolean restBandRow(byte[] gray, int width, int left, int right, int y) {
        for (int i = 1; i <= 5; i++)
            if ((gray[y * width + left + (right - left) * i / 6] & 255) >= 165) return false;
        return true;
    }

    private static boolean restEndCap(
            byte[] gray, int width, int height, int edge, int top, int bottom, float gap) {
        int margin = Math.max(2, Math.round(gap * .4f));
        if (top - margin < 0 || bottom + margin >= height) return false;
        for (int x = Math.max(0, Math.round(edge - gap * .25f));
                x <= Math.min(width - 1, Math.round(edge + gap * .25f));
                x++) {
            boolean solid = true;
            for (int y = top - margin; y <= bottom + margin; y++)
                if ((gray[y * width + x] & 255) >= 165) {
                    solid = false;
                    break;
                }
            if (solid) return true;
        }
        return false;
    }

    /** Remove only complete upright double-bowl text from geometric head vetoes.
     * Other header removals retain their original geometry to avoid turning
     * partial numeral stems into new barlines. */
    static byte[] normalizeTextGeometry(
            byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
        if (labels == null
                || gray == null
                || labels.length != (long) width * height
                || gray.length != labels.length
                || measures == null
                || measures.isEmpty()) return labels;
        List<Staff> staffs = findStaffs(labels, gray, width, height, measures);
        byte[] result = labels;
        for (Component head :
                findComponents(labels, width, height, OmrMeasurePostProcessor.NOTEHEAD)) {
            int[] bounds = uprightTextBowlBounds(gray, width, height, head, staffs);
            if (bounds == null) continue;
            for (int y = bounds[2]; y <= bounds[3]; y++)
                for (int x = bounds[0]; x <= bounds[1]; x++) {
                    int at = y * width + x;
                    if (result[at] != OmrMeasurePostProcessor.NOTEHEAD) continue;
                    if (result == labels) result = labels.clone();
                    result[at] = 0;
                }
        }
        return result;
    }

    /** Complete upright text with two vertically elongated counters is not a note oval. */
    private static int[] uprightTextBowlBounds(
            byte[] gray, int width, int height, Component head, List<Staff> staffs) {
        if (gray == null) return null;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return null;
        float gap = staff.pitchGap;
        if (head.maxX - head.minX + 1 > gap * 1.6f || head.maxY - head.minY + 1 > gap * 2.5f)
            return null;
        for (TempoInk glyph :
                tempoInk(
                        gray,
                        width,
                        height,
                        Math.round(head.minX - gap),
                        Math.round(head.maxX + gap),
                        Math.round(head.minY - gap * 2),
                        Math.round(head.maxY + gap * 2))) {
            int w = glyph.width(), h = glyph.height();
            if (head.centerX < glyph.left
                    || head.centerX > glyph.right
                    || head.centerY < glyph.top
                    || head.centerY > glyph.bottom
                    || w < gap * .7f
                    || w > gap * 1.5f
                    || h < gap * 1.3f
                    || h > gap * 2.5f
                    || h < w * 1.3f
                    || h > w * 2.2f
                    || glyph.area < w * h * .25f
                    || glyph.area > w * h * .85f) continue;
            byte[] white = new byte[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if ((gray[(glyph.top + y) * width + glyph.left + x] & 255) >= 155)
                        white[y * w + x] = 1;
            List<Component> holes = new ArrayList<>();
            for (Component c : findComponents(white, w, h, (byte) 1)) {
                if (c.minX == 0 || c.minY == 0 || c.maxX == w - 1 || c.maxY == h - 1) continue;
                if (c.area >= Math.max(3, Math.round(gap * gap * .025f))) holes.add(c);
            }
            if (holes.size() != 2) continue;
            holes.sort(Comparator.comparingDouble(c -> c.centerY));
            Component upper = holes.get(0), lower = holes.get(1);
            boolean upright = true;
            for (Component c : holes) {
                int cw = c.maxX - c.minX + 1, ch = c.maxY - c.minY + 1;
                upright &=
                        cw >= gap * .12f
                                && ch >= gap * .22f
                                && cw <= ch * 1.25f
                                && ch <= h * .45f
                                && c.area >= glyph.area * .07f;
            }
            if (!upright
                    || Math.abs(upper.centerX - lower.centerX) > w * .25f
                    || upper.centerY > h * .45f
                    || lower.centerY < h * .55f
                    || lower.minY - upper.maxY < gap * .15f
                    || lower.minY - upper.maxY > gap * .9f
                    || upper.area + lower.area < glyph.area * .18f) continue;
            return new int[] {glyph.left, glyph.right, glyph.top, glyph.bottom};
        }
        return null;
    }

    /** Small baseline-aligned lettering with a dotted ascender is not a group of note ovals. */
    private static int[] staffTextBounds(
            byte[] gray, int width, int height, Component head, List<Staff> staffs) {
        if (gray == null) return null;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return null;
        float gap = staff.pitchGap;
        if (head.maxX - head.minX + 1 > gap * 1.4f
                || head.maxY - head.minY + 1 > gap * .85f
                || attachedRawStem(gray, width, height, head, gap) != null) return null;
        int left = Math.max(0, Math.round(head.centerX - gap * 3)),
                right = Math.min(width - 1, Math.round(head.centerX + gap * 3));
        for (int baseline = Math.round(head.centerY);
                baseline <= Math.round(head.centerY + gap * .75f);
                baseline++) {
            if (baseline < 0 || baseline >= height) continue;
            int ink = 0;
            for (int x = left; x <= right; x++) if ((gray[baseline * width + x] & 255) < 155) ink++;
            if (ink < (right - left + 1) * .9f) continue;
            // The final row is the notation rule; a one-pixel white margin keeps the
            // original glyph edges complete after removing that rule.
            int top = Math.max(0, Math.round(baseline - gap * 1.2f)),
                    rw = right - left + 1,
                    rh = baseline - top + 1;
            byte[] patch = new byte[rw * rh];
            java.util.Arrays.fill(patch, (byte) 255);
            for (int y = top; y < baseline; y++)
                for (int x = left; x <= right; x++)
                    patch[(y - top) * rw + x - left] = gray[y * width + x];
            List<TempoInk> glyphs = tempoInk(patch, rw, rh, 0, rw - 1, 0, rh - 1);
            List<TempoInk> bodies = new ArrayList<>();
            for (TempoInk c : glyphs)
                if (c.height() > gap * .4f
                        && c.height() < gap * 1.1f
                        && c.width() > gap * .1f
                        && c.width() < gap * .85f
                        && baseline - (c.bottom + top) < gap * .22f) bodies.add(c);
            bodies.sort(Comparator.comparingInt(c -> c.left));
            for (int start = 0; start < bodies.size(); start++) {
                List<TempoInk> word = new ArrayList<>();
                word.add(bodies.get(start));
                for (int i = start + 1; i < bodies.size(); i++) {
                    TempoInk last = word.get(word.size() - 1), next = bodies.get(i);
                    if (next.left - last.right > gap * .5f) break;
                    word.add(next);
                }
                if (word.size() < 3) continue;
                boolean dotted = false, ascender = false, contains = false;
                for (TempoInk c : word) {
                    contains |=
                            head.centerX >= c.left + left - gap * .1f
                                    && head.centerX <= c.right + left + gap * .1f;
                    ascender |= c.height() > gap * .7f && c.width() < gap * .5f;
                    if (c.width() > gap * .4f) continue;
                    for (TempoInk dot : glyphs)
                        if (dot.height() >= 1
                                && dot.height() < gap * .3f
                                && dot.width() < gap * .3f
                                && dot.area >= 2
                                && dot.bottom < c.top
                                && c.top - dot.bottom < gap * .4f
                                && Math.abs((dot.left + dot.right) - (c.left + c.right))
                                        < gap * .3f) dotted = true;
                }
                if (dotted && ascender && contains)
                    return new int[] {
                        word.get(0).left + left,
                        word.get(word.size() - 1).right + left,
                        top,
                        baseline - 1
                    };
            }
        }
        return null;
    }

    /** A small isolated beat symbol can be separated from its equation by the staff. */
    private static boolean isDetachedTempoUnit(
            byte[] gray, int width, int height, Component head, Staff staff) {
        float gap = staff.pitchGap, top = staff.pitchBottom - 4 * gap;
        if (head.maxY >= top
                || head.minY < top - 3 * gap
                || head.maxX - head.minX + 1 > gap * .9f
                || head.maxY - head.minY + 1 > gap * .75f) return false;
        boolean smallBeat = false;
        for (TempoInk glyph :
                tempoInk(
                        gray,
                        width,
                        height,
                        Math.round(head.minX - gap),
                        Math.round(head.maxX + gap),
                        Math.round(head.minY - 3 * gap),
                        Math.round(head.maxY + gap * .3f))) {
            if (glyph.left > head.centerX
                    || glyph.right < head.centerX
                    || glyph.bottom < head.centerY
                    || glyph.width() > gap * .95f
                    || glyph.height() < gap * 1.3f
                    || glyph.height() > gap * 2.8f
                    || glyph.top > head.minY - gap * .7f
                    || glyph.bottom > head.maxY + gap * .2f) continue;
            int[] stem = attachedRawStem(gray, width, height, head, gap * .5f);
            if (stem != null && stem[1] < head.minY - gap * .7f) smallBeat = true;
        }
        if (!smallBeat) return false;
        List<TempoInk> ink =
                tempoInk(
                        gray,
                        width,
                        height,
                        Math.round(head.maxX - gap * .2f),
                        Math.round(head.maxX + gap * 7),
                        Math.round(staff.pitchBottom + gap * .3f),
                        Math.round(staff.pitchBottom + gap * 3.5f));
        for (TempoInk upper : ink)
            for (TempoInk lower : ink) {
                if (upper.top >= lower.top
                        || upper.width() < gap * .5f
                        || upper.width() > gap * 1.6f
                        || lower.width() < gap * .5f
                        || lower.width() > gap * 1.6f
                        || upper.height() > gap * .35f
                        || lower.height() > gap * .35f
                        || upper.area < upper.width() * upper.height() * .7f
                        || lower.area < lower.width() * lower.height() * .7f
                        || Math.abs(upper.left - lower.left) > gap * .15f
                        || Math.abs(upper.right - lower.right) > gap * .2f
                        || lower.top - upper.bottom < gap * .08f
                        || lower.top - upper.bottom > gap * .5f
                        || lower.bottom - upper.top > gap
                        || upper.left - head.maxX > gap * 1.5f) continue;
                List<TempoInk> digits = new ArrayList<>();
                for (TempoInk text : ink)
                    if (text.left > lower.right + gap * .15f
                            && text.left < lower.right + gap * 4.5f
                            && text.height() > gap * .9f
                            && text.height() < gap * 2.3f
                            && text.width() > gap * .3f
                            && text.width() < gap * 1.5f
                            && text.area > gap * gap * .18f
                            && text.top < upper.top
                            && text.bottom >= lower.bottom) digits.add(text);
                digits.sort(Comparator.comparingInt(c -> c.left));
                if (digits.size() < 2
                        || digits.size() > 3
                        || digits.get(0).left > lower.right + gap * 1.5f) continue;
                boolean aligned = true;
                for (int i = 1; i < digits.size(); i++) {
                    TempoInk a = digits.get(i - 1), b = digits.get(i);
                    aligned &=
                            b.left > a.right
                                    && b.left - a.right < gap * .75f
                                    && Math.abs(a.top - b.top) < gap * .2f
                                    && Math.abs(a.bottom - b.bottom) < gap * .2f;
                }
                if (aligned) return true;
            }
        return false;
    }

    /** A note followed by an equals sign and text above the staff is a tempo beat unit. */
    private static boolean isTempoUnitHead(
            byte[] gray, int width, int height, Component head, List<Staff> staffs) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        if (isDetachedTempoUnit(gray, width, height, head, staff)) return true;
        float gap = staff.pitchGap, top = staff.pitchBottom - gap * 4;
        float y = head.centerY, x = head.maxX;
        if (head.maxY > top + gap * .15f || y < top - gap * MAX_HEAD_LEDGER_GAPS) return false;
        int[] stem = attachedRawStem(gray, width, height, head, gap * .65f);
        if (stem == null || stem[1] > y - gap * 1.5f) return false;
        List<TempoInk> glyphs =
                tempoInk(
                        gray,
                        width,
                        height,
                        Math.round(x + gap * .1f),
                        Math.round(x + gap * 5.5f),
                        Math.round(y - gap * 2),
                        Math.round(y + gap));
        for (TempoInk upper : glyphs)
            for (TempoInk lower : glyphs) {
                if (upper.top >= lower.top
                        || upper.width() < gap * .5f
                        || upper.width() > gap * 1.6f
                        || lower.width() < gap * .5f
                        || lower.width() > gap * 1.6f
                        || upper.height() > Math.max(2, Math.round(gap * .25f))
                        || lower.height() > Math.max(2, Math.round(gap * .25f))
                        || upper.area < upper.width() * upper.height() * .7f
                        || lower.area < lower.width() * lower.height() * .7f
                        || Math.abs(upper.left - lower.left) > gap * .15f
                        || Math.abs(upper.right - lower.right) > gap * .2f
                        || lower.top - upper.bottom < gap * .08f
                        || lower.top - upper.bottom > gap * .5f
                        || lower.bottom - upper.top > gap * .8f
                        || upper.left - x > gap * 2.5f
                        || lower.bottom > y + gap * .1f) continue;
                // This is shape evidence for following text, not OCR of the BPM value.
                // Ordinary ledger rules are farther apart; connected beams are not two isolated
                // strokes.
                for (TempoInk text : glyphs) {
                    if (text.left > lower.right + gap * .15f
                            && text.left < lower.right + gap * 1.5f
                            && text.height() > gap * .75f
                            && text.height() < gap * 2
                            && text.area > gap * gap * .18f
                            && text.top < upper.top
                            && text.bottom > lower.bottom) return true;
                }
            }
        return false;
    }

    private record TempoInk(int left, int right, int top, int bottom, int area) {
        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }

    /** Complete printed components only; clipping a letter must not manufacture an equals stroke. */
    private static List<TempoInk> tempoInk(
            byte[] gray, int width, int height, int left, int right, int top, int bottom) {
        left = Math.max(0, left);
        right = Math.min(width - 1, right);
        top = Math.max(0, top);
        bottom = Math.min(height - 1, bottom);
        int rw = right - left + 1, rh = bottom - top + 1;
        if (rw <= 0 || rh <= 0) return List.of();
        boolean[] seen = new boolean[rw * rh];
        int[] queue = new int[seen.length];
        List<TempoInk> result = new ArrayList<>();
        for (int start = 0; start < seen.length; start++) {
            if (seen[start] || (gray[(top + start / rw) * width + left + start % rw] & 255) >= 155)
                continue;
            int take = 0, size = 1, minX = rw, maxX = -1, minY = rh, maxY = -1;
            boolean edge = false;
            queue[0] = start;
            seen[start] = true;
            while (take < size) {
                int pos = queue[take++], x = pos % rw, y = pos / rw;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                edge |= x == 0 || y == 0 || x == rw - 1 || y == rh - 1;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= rw || ny < 0 || ny >= rh) continue;
                        int next = ny * rw + nx;
                        if (!seen[next] && (gray[(top + ny) * width + left + nx] & 255) < 155) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (!edge)
                result.add(new TempoInk(left + minX, left + maxX, top + minY, top + maxY, size));
        }
        return result;
    }

    /** The tall open-right C can contribute tiny false heads at its curved terminals. */
    private static int[] commonTimeGlyphBounds(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Staff> staffs,
            List<Component> glyphs) {
        if (gray == null) return null;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return null;
        float gap = staff.pitchGap, bottom = staff.pitchBottom, top = bottom - gap * 4;
        float mid = (top + bottom) * .5f;
        if (head.area > gap * gap * .5f
                || head.maxX - head.minX > gap * .8f
                || head.maxY - head.minY > gap * .8f
                || Math.abs(head.centerY - mid) > gap * 1.15f) return null;
        boolean header = false;
        for (Component glyph : glyphs) {
            if (glyph.maxX < head.minX
                    && head.minX - glyph.maxX < gap * 9
                    && (glyph.maxY - glyph.minY > gap * 4.5f
                                    && glyph.maxX - glyph.minX > gap * 1.1f
                                    && Math.abs(glyph.centerY - mid) < gap * 3
                            || rawBassClef(glyph, gray, width, height, staff))) header = true;
        }
        if (!header) return null;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        if (stem != null && (stem[1] < top - gap * .25f || stem[1] > bottom + gap * .25f))
            return null;

        // Follow the printed glyph across narrow antialiasing gaps, ignoring the five staff rules.
        int searchLeft = Math.max(0, Math.round(head.minX - gap * 2.5f));
        int searchRight = Math.min(width - 1, Math.round(head.maxX + gap * 2.5f));
        int[] columns = new int[searchRight - searchLeft + 1];
        int scanTop = Math.max(0, Math.round(top)),
                scanBottom = Math.min(height - 1, Math.round(bottom));
        for (int x = searchLeft; x <= searchRight; x++)
            for (int y = scanTop; y <= scanBottom; y++) {
                if (offHeaderStaffLine(y, top, gap) && (gray[y * width + x] & 255) < 155)
                    columns[x - searchLeft]++;
            }
        int left = head.minX, right = head.maxX, blank = 0;
        int maxBlank = Math.max(1, Math.round(gap * .35f));
        for (int x = left - 1; x >= searchLeft; x--) {
            if (columns[x - searchLeft] > 0) {
                left = x;
                blank = 0;
            } else if (++blank > maxBlank) break;
        }
        blank = 0;
        for (int x = right + 1; x <= searchRight; x++) {
            if (columns[x - searchLeft] > 0) {
                right = x;
                blank = 0;
            } else if (++blank > maxBlank) break;
        }
        if (left == searchLeft
                || right == searchRight
                || right - left < gap * .9f
                || right - left > gap * 2.5f) return null;
        int noteInk = 0, minY = height, maxY = -1;
        for (int y = scanTop; y <= scanBottom; y++)
            for (int x = left; x <= right; x++) {
                if (labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) noteInk++;
                if (offHeaderStaffLine(y, top, gap) && (gray[y * width + x] & 255) < 155) {
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        if (noteInk > gap * gap * .55f || maxY - minY < gap * 1.5f || maxY - minY > gap * 3.8f)
            return null;
        int span = right - left;
        int[] open =
                headerInkBand(
                        gray,
                        width,
                        height,
                        Math.round(left + span * .65f),
                        right,
                        Math.round(mid - gap * .25f),
                        Math.round(mid + gap * .25f),
                        top,
                        gap);
        int[] upper =
                headerInkBand(
                        gray,
                        width,
                        height,
                        Math.round(left + span * .60f),
                        right,
                        Math.round(mid - gap * 1.05f),
                        Math.round(mid - gap * .35f),
                        top,
                        gap);
        int[] lower =
                headerInkBand(
                        gray,
                        width,
                        height,
                        Math.round(left + span * .60f),
                        right,
                        Math.round(mid + gap * .35f),
                        Math.round(mid + gap * 1.05f),
                        top,
                        gap);
        int[] leftUp =
                headerInkBand(
                        gray,
                        width,
                        height,
                        left,
                        Math.round(left + span * .35f),
                        Math.round(mid - gap * .65f),
                        Math.round(mid - gap * .15f),
                        top,
                        gap);
        int[] leftDown =
                headerInkBand(
                        gray,
                        width,
                        height,
                        left,
                        Math.round(left + span * .35f),
                        Math.round(mid + gap * .15f),
                        Math.round(mid + gap * .65f),
                        top,
                        gap);
        // Require both curved terminals and the left arc, with a genuinely open middle at the
        // right.
        if (open[1] == 0
                || open[0] > open[1] * .2f
                || upper[0] < gap * .7f
                || lower[0] < gap * .7f
                || leftUp[0] < gap * .7f
                || leftDown[0] < gap * .7f) return null;
        return new int[] {left, right, scanTop, scanBottom};
    }

    private static boolean offHeaderStaffLine(int y, float top, float gap) {
        return Math.abs((y - top) / gap - Math.round((y - top) / gap)) * gap
                > Math.max(1, gap * .15f);
    }

    private static int[] headerInkBand(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float staffTop,
            float gap) {
        int ink = 0, total = 0;
        for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++) {
            if (!offHeaderStaffLine(y, staffTop, gap)) continue;
            for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++) {
                total++;
                if ((gray[y * width + x] & 255) < 155) ink++;
            }
        }
        return new int[] {ink, total};
    }

    /** Stacked rounded meter digits can arrive as one tall semantic head blob.
     * Inspect their printed counters before splitting that blob into chord tones. */
    private static boolean isRoundedHeaderMeter(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Staff> staffs,
            List<Component> glyphs) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.gap;
        boolean whole =
                head.maxY - head.minY >= gap * 3
                        && head.maxY - head.minY <= gap * 4.3f
                        && head.maxX - head.minX <= gap * 1.9f
                        && head.maxX - head.minX >= gap * .65f
                        && head.minY >= staff.top - gap * .2f
                        && head.maxY <= staff.bottom + gap * .3f;
        boolean denominator =
                head.maxY - head.minY >= gap * 1.45f
                        && head.maxY - head.minY <= gap * 2.65f
                        && head.maxX - head.minX <= gap * 2.2f
                        && head.maxX - head.minX >= gap * .65f
                        && head.minY + 1 >= Math.floor(staff.top + gap * 1.75f)
                        && head.centerY >= staff.top + gap * 2.2f
                        && head.maxY <= staff.bottom + gap * .3f;
        if (!whole && !denominator) return false;
        boolean header = false;
        for (Component glyph : glyphs)
            if (glyph.maxX < head.minX
                    && head.minX - glyph.maxX < gap * 7
                    && glyph.maxY - glyph.minY > gap * 4.5f
                    && glyph.maxX - glyph.minX > gap * 1.1f
                    && glyph.centerY > staff.top - gap
                    && glyph.centerY < staff.bottom + gap) header = true;
        // Meter changes also occur after a barline within or at the end of a system.
        // Require a complete printed rule, not a short note stem or numeral stroke.
        if (!header)
            for (int x = Math.max(0, Math.round(head.minX - gap * 3.8f));
                    x < Math.min(width, Math.round(head.minX - gap * .3f));
                    x++) {
                int ink = 0, total = 0;
                for (int y = Math.max(0, Math.round(staff.top));
                        y <= Math.min(height - 1, Math.round(staff.bottom));
                        y++) {
                    total++;
                    if ((gray[y * width + x] & 255) < 155) ink++;
                }
                if (total >= gap * 3.8f && ink >= total * .93f) {
                    header = true;
                    break;
                }
            }
        if (!header) return false;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        if (stem != null
                && (stem[1] < staff.top - gap * .25f || stem[1] > staff.bottom + gap * .25f))
            return false;
        int left = Math.max(0, Math.round(head.minX - gap * .3f));
        int right = Math.min(width - 1, Math.round(head.maxX + gap * .3f));
        int top = Math.max(0, Math.round(staff.top - gap * .2f));
        int bottom = Math.min(height - 1, Math.round(staff.bottom + gap * .2f));
        int w = right - left + 1, h = bottom - top + 1, upper = 0, lower = 0, tallLower = 0;
        boolean[] visited = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int seed = 0; seed < w * h; seed++) {
            if (visited[seed] || (gray[(top + seed / w) * width + left + seed % w] & 255) <= 155)
                continue;
            int take = 0, size = 1, minX = w, maxX = -1, minY = h, maxY = -1;
            boolean edge = false;
            queue[0] = seed;
            visited[seed] = true;
            while (take < size) {
                int index = queue[take++], x = index % w, y = index / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                edge |= x == 0 || y == 0 || x == w - 1 || y == h - 1;
                for (int direction = 0; direction < 4; direction++) {
                    int nx = x + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
                    int ny = y + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int next = ny * w + nx;
                    if (!visited[next] && (gray[(top + ny) * width + left + nx] & 255) > 155) {
                        visited[next] = true;
                        queue[size++] = next;
                    }
                }
            }
            int cw = maxX - minX + 1, ch = maxY - minY + 1;
            // Full stacked glyphs need upright counters. A separate denominator
            // can have wider bowls; it also requires printed numerator evidence.
            // Broad shallow hollow-note counters remain excluded.
            if (edge
                    || size < gap * gap * .07f
                    || cw < gap * .2f
                    || cw > gap * 1.1f
                    || ch < gap * .35f
                    || ch > gap * 1.1f
                    || ch < cw * (whole ? .75f : .5f)) continue;
            float cy = top + (minY + maxY) * .5f;
            if (cy < staff.top + gap * 2) upper++;
            else {
                lower++;
                if (ch >= cw * .6f) tallLower++;
            }
        }
        if (whole) return upper >= 1 && lower >= 2;
        if (lower < 2 || tallLower < 1) return false;
        // An 8 denominator may be the only part labelled as a head. Its printed
        // numerator must span the upper half, without an actual note/chord there.
        int ink = 0, heads = 0, minY = height, maxY = -1;
        for (int y = Math.max(0, Math.round(staff.top));
                y
                        <= Math.min(
                                head.minY - 1,
                                Math.min(height - 1, Math.round(staff.top + gap * 1.85f)));
                y++)
            for (int x = Math.max(0, Math.round(head.minX - gap * .8f));
                    x <= Math.min(width - 1, Math.round(head.maxX + gap * .25f));
                    x++) {
                if (labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) heads++;
                float lineDistance =
                        Math.abs((y - staff.top) / gap - Math.round((y - staff.top) / gap)) * gap;
                if (lineDistance <= Math.max(1, gap * .15f)) continue;
                if ((gray[y * width + x] & 255) < 155) {
                    ink++;
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        return heads < gap * gap * .15f && ink > gap * 3 && maxY - minY > gap * 1.1f;
    }

    /** A tall, rounded lower meter digit can be painted as a hollow notehead. */
    /** A staff-labelled bridge can join the clef to the first key accidental.
     * Split only at a printed gap containing no ink away from the five rules. */
    private static List<Component> splitClefKeyBridges(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Component> source,
            List<Staff> staffs) {
        if (gray == null) return source;
        List<Component> result = new ArrayList<>();
        for (Component c : source) {
            Staff staff = nearestHeadStaff(staffs, c.centerY);
            Component left = null, right = null;
            if (staff != null
                    && c.maxY - c.minY > staff.gap * 4.5f
                    && c.maxX - c.minX > staff.gap * 3.4f) {
                int clear = 0;
                for (int x = Math.round(c.minX + staff.gap * 2.4f);
                        x < c.maxX - staff.gap * .6f;
                        x++) {
                    boolean empty = true;
                    for (int y = c.minY; y <= c.maxY; y++) {
                        boolean rule = false;
                        for (int line = 0; line < 5; line++)
                            if (Math.abs(y - (staff.pitchBottom - line * staff.pitchGap))
                                    <= staff.gap * .30f) rule = true;
                        if (!rule && (gray[y * width + x] & 255) < 170) {
                            empty = false;
                            break;
                        }
                    }
                    clear = empty ? clear + 1 : 0;
                    if (clear < Math.max(2, Math.round(staff.gap * .15f))) continue;
                    left = clefKeySlice(labels, width, c, c.minX, x, staff);
                    right = clefKeySlice(labels, width, c, x + 1, c.maxX, staff);
                    if (left != null
                            && right != null
                            && left.maxY - left.minY > staff.gap * 4.5f
                            && right.maxY - right.minY > staff.gap * 1.4f
                            && right.maxY - right.minY < staff.gap * 3.8f) break;
                    left = null;
                    right = null;
                }
            }
            if (left != null && right != null) {
                result.add(left);
                result.add(right);
            } else result.add(c);
        }
        return result;
    }

    private static Component clefKeySlice(
            byte[] labels, int width, Component c, int left, int right, Staff staff) {
        int minX = right, maxX = left, minY = c.maxY, maxY = c.minY, area = 0;
        long sx = 0, sy = 0;
        for (int y = c.minY; y <= c.maxY; y++)
            for (int x = left; x <= right; x++) {
                if (labels[y * width + x] != OmrMeasurePostProcessor.CLEF_OR_KEY) continue;
                boolean rule = false;
                for (int line = 0; line < 5; line++)
                    if (Math.abs(y - (staff.pitchBottom - line * staff.pitchGap))
                            <= staff.gap * .30f) rule = true;
                if (rule) continue;
                area++;
                sx += x;
                sy += y;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        return area < 3
                ? null
                : new Component(area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
    }

    /** A small head prediction can cover just one corner of a repeated meter digit.
     * Require two tall matching printed glyphs after a full barline, with no upper
     * note prediction or attached stem extending beyond the staff. */

    /** Two enclosed counters in an inline 8, immediately after a complete barline,
     * cannot be the two attacks that a semantic notehead mask may suggest. */
    private static boolean inlineEightMeterFragment(
            byte[] gray, int width, int height, Component head, List<Staff> staffs) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.gap, top = staff.top, bottom = staff.bottom;
        if (head.area > gap * gap * .8f
                || head.maxX - head.minX + 1 > gap * 1.1f
                || head.maxY - head.minY + 1 > gap * 1.65f
                || head.centerY < top + gap * 2.2f
                || head.centerY > bottom + gap * .1f) return false;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        if (stem != null && (stem[1] < top - gap * .25f || stem[1] > bottom + gap * .25f))
            return false;
        boolean rule = false;
        for (int x = Math.max(0, Math.round(head.minX - gap * 4f));
                x < head.minX - gap * .7f;
                x++) {
            int ink = 0, total = 0;
            for (int y = Math.max(0, Math.round(top));
                    y <= Math.min(height - 1, Math.round(bottom));
                    y++) {
                total++;
                if ((gray[y * width + x] & 255) < 155) ink++;
            }
            if (total >= gap * 3.8f && ink >= total * .94f) {
                rule = true;
                break;
            }
        }
        if (!rule) return false;
        int left = Math.max(0, Math.round(head.centerX - gap * .95f));
        int right = Math.min(width - 1, Math.round(head.centerX + gap * .95f));
        int upper = Math.max(0, Math.round(top + gap * 2f));
        int lower = Math.min(height - 1, Math.round(bottom + gap * .2f));
        int w = right - left + 1, h = lower - upper + 1;
        if (w < 3 || h < 3) return false;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        List<float[]> holes = new ArrayList<>();
        for (int seed = 0; seed < w * h; seed++) {
            if (seen[seed] || (gray[(upper + seed / w) * width + left + seed % w] & 255) < 200)
                continue;
            int read = 0, write = 0;
            queue[write++] = seed;
            seen[seed] = true;
            int minX = w, maxX = -1, minY = h, maxY = -1, area = 0;
            float sx = 0, sy = 0;
            while (read < write) {
                int point = queue[read++], px = point % w, py = point / w;
                area++;
                sx += px;
                sy += py;
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
                minY = Math.min(minY, py);
                maxY = Math.max(maxY, py);
                for (int step : new int[] {-1, 1, -w, w}) {
                    int next = point + step;
                    if (next < 0 || next >= w * h) continue;
                    if (step == -1 && px == 0 || step == 1 && px == w - 1) continue;
                    if (seen[next]
                            || (gray[(upper + next / w) * width + left + next % w] & 255) < 200)
                        continue;
                    seen[next] = true;
                    queue[write++] = next;
                }
            }
            if (minX == 0
                    || maxX == w - 1
                    || minY == 0
                    || maxY == h - 1
                    || area < gap * gap * .055f
                    || area > gap * gap * .35f
                    || maxX - minX + 1 > gap * .7f
                    || maxY - minY + 1 > gap * .8f) continue;
            float cx = left + sx / area, cy = upper + sy / area;
            if (Math.abs(cx - head.centerX) < gap * .35f
                    && Math.abs(cy - head.centerY) < gap * .35f) holes.add(0, new float[] {cx, cy});
            else holes.add(new float[] {cx, cy});
        }
        if (holes.size() < 2) return false;
        for (int i = 0; i < holes.size(); i++)
            for (int j = i + 1; j < holes.size(); j++) {
                float[] first = holes.get(i), other = holes.get(j);
                float dy = Math.abs(first[1] - other[1]);
                if (Math.abs(first[0] - other[0]) >= gap * .3f
                        || dy < gap * .55f
                        || dy > gap * 1.1f) continue;
                float midX = (first[0] + other[0]) * .5f, midY = (first[1] + other[1]) * .5f;
                if (Math.abs(midX - head.centerX) < gap * .35f
                        && (Math.abs(first[1] - head.centerY) < gap * .35f
                                || Math.abs(other[1] - head.centerY) < gap * .35f
                                || Math.abs(midY - head.centerY) < gap * .35f)) return true;
            }
        return false;
    }

    private static boolean isRepeatedMeterFragment(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        float gap = staff.gap;
        if (head.area > gap * gap * .55f
                || head.maxX - head.minX > gap
                || head.maxY - head.minY > gap) return false;
        float[] pitch = localStaffPitch(labels, gray, width, height, staff, head);
        gap = pitch[1];
        float staffBottom = pitch[0], staffTop = staffBottom - gap * 4;
        if (head.area > gap * gap * .55f
                || head.maxX - head.minX > gap
                || head.maxY - head.minY > gap
                || head.centerY < staffTop + gap * 2.25f
                || head.centerY > staffBottom) return false;
        boolean boundary = false;
        for (int x = Math.max(0, Math.round(head.minX - gap * 4)); x < head.minX - gap * .7f; x++) {
            int ink = 0, total = 0;
            for (int y = Math.max(0, Math.round(staffTop));
                    y <= Math.min(height - 1, Math.round(staffBottom));
                    y++) {
                total++;
                if ((gray[y * width + x] & 255) < 155) ink++;
            }
            if (total >= gap * 3.8f && ink >= total * .94f) {
                boundary = true;
                break;
            }
        }
        if (!boundary) return false;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        if (stem != null && (stem[1] < staffTop - gap * .25f || stem[1] > staffBottom + gap * .25f))
            return false;
        int left = Math.max(0, Math.round(head.centerX - gap * 1.25f));
        int right = Math.min(width - 1, Math.round(head.centerX + gap * 1.25f));
        int top = Math.max(0, Math.round(staffTop)),
                bottom = Math.min(height - 1, Math.round(staffTop + gap * 1.95f));
        int upperHeads = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) upperHeads++;
        if (upperHeads > gap * gap * .08f) return false;
        for (int dy = Math.round(gap * 1.75f); dy <= Math.round(gap * 2.15f); dy++)
            for (int dx = -Math.round(gap * .2f); dx <= Math.round(gap * .2f); dx++) {
                int intersection = 0,
                        union = 0,
                        inkA = 0,
                        inkB = 0,
                        minX = width,
                        maxX = -1,
                        minY = height,
                        maxY = -1;
                for (int y = top; y <= bottom; y++)
                    for (int x = left; x <= right; x++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0
                                || xx >= width
                                || yy < 0
                                || yy >= height
                                || yy > staffBottom + gap * .15f) continue;
                        float aDistance =
                                Math.abs((y - staffTop) / gap - Math.round((y - staffTop) / gap))
                                        * gap;
                        float bDistance =
                                Math.abs((yy - staffTop) / gap - Math.round((yy - staffTop) / gap))
                                        * gap;
                        if (aDistance < gap * .16f || bDistance < gap * .16f) continue;
                        boolean a = (gray[y * width + x] & 255) < 155,
                                b = (gray[yy * width + xx] & 255) < 155;
                        if (a) {
                            inkA++;
                            minX = Math.min(minX, x);
                            maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y);
                        }
                        if (b) inkB++;
                        if (a || b) union++;
                        if (a && b) intersection++;
                    }
                if (inkA > gap * gap * .45f
                        && inkB > gap * gap * .45f
                        && maxX - minX > gap * .75f
                        && maxY - minY > gap * 1.2f
                        && intersection > union * .72f) return true;
            }
        return false;
    }

    /** A tiny semantic head can cover one corner of a stacked opening meter.
     * Compare both complete printed numerals away from staff rules, and only
     * demote fragments in the first measure of a system. */
    private static boolean isStackedOpeningMeterFragment(
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Staff> staffs,
            List<MeasureRegion> measures) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.gap;
        if (head.area > gap * gap * .3f
                || head.maxX - head.minX + 1 > gap * .65f
                || head.maxY - head.minY + 1 > gap * .85f
                || head.centerY < staff.top + gap * .9f
                || head.centerY > staff.top + gap * 2.2f) return false;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        // A numeral's vertical stroke stays inside the staff; an independent
        // note stem extending above or below it still protects the note.
        if (stem != null
                && (stem[1] < staff.top - gap * .25f || stem[1] > staff.bottom + gap * .25f))
            return false;
        boolean opening = false;
        for (MeasureRegion measure : measures)
            if (head.centerY >= measure.top() * height - gap
                    && head.centerY <= measure.bottom() * height + gap
                    && head.centerX >= measure.left() * width - gap * .5f
                    && head.centerX <= measure.left() * width + gap * 2.6f) {
                boolean earlier = false;
                for (MeasureRegion other : measures)
                    if (other.left() < measure.left()
                            && Math.abs(other.top() - measure.top()) * height < gap * 2)
                        earlier = true;
                if (!earlier) {
                    opening = true;
                    break;
                }
            }
        if (!opening) return false;
        int left = Math.max(0, Math.round(head.minX - gap * 1.2f));
        int right = Math.min(width - 1, Math.round(head.maxX + gap * .45f));
        int top = Math.max(0, Math.round(head.centerY - gap * 1.45f));
        int bottom = Math.min(height - 1, Math.round(head.centerY + gap * .4f));
        if (right - left < gap * 1.1f || bottom - top < gap * 1.5f) return false;
        for (int shift = Math.round(gap * 2.05f); shift <= Math.round(gap * 2.4f); shift++) {
            if (bottom + shift >= height || bottom + shift > staff.bottom + gap * .4f) continue;
            int overlap = 0,
                    union = 0,
                    upper = 0,
                    lower = 0,
                    minX = width,
                    maxX = -1,
                    minY = height,
                    maxY = -1;
            for (int y = top; y <= bottom; y++) {
                if (!offHeaderStaffLine(y, staff.top, gap)
                        || !offHeaderStaffLine(y + shift, staff.top, gap)) continue;
                for (int x = left; x <= right; x++) {
                    boolean a = (gray[y * width + x] & 255) < 155;
                    boolean b = (gray[(y + shift) * width + x] & 255) < 155;
                    if (a) {
                        upper++;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                    if (b) lower++;
                    if (a || b) union++;
                    if (a && b) overlap++;
                }
            }
            if (upper > gap * gap * .55f
                    && lower > gap * gap * .55f
                    && maxX - minX > gap * 1.1f
                    && maxY - minY > gap * 1.2f
                    && overlap > union * .77f) return true;
        }
        return false;
    }

    private static boolean isHeaderMeterDigit(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Staff> staffs,
            List<Component> glyphs) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.gap;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        // Establish a real oval stack before a repeated-shape meter heuristic:
        // two identical filled heads are not repeated numerals.
        List<Component> chord = splitRegularStack(labels, width, head, staff);
        if (stem != null
                && chord.size() >= 2
                && chord.stream()
                        .allMatch(
                                part ->
                                        printedOpenOval(labels, gray, width, height, part, gap)
                                                || printedFilledOval(gray, width, part, gap)))
            return false;
        if (isRepeatedMeterFragment(labels, gray, width, height, head, staff)) return true;
        if (head.centerY < staff.top + gap * 2.2f
                || head.centerY > staff.bottom + gap * .2f
                || head.maxX - head.minX > gap * 2.2f
                || head.maxY - head.minY < gap * 1.5f
                || head.maxY - head.minY > gap * 2.6f) return false;
        // The upper numeral can supply a vertical stroke inside the stave.
        // A stem extending beyond that numeral band still protects a real note.
        if (stem != null
                && (stem[1] < staff.top - gap * .25f || stem[1] > staff.bottom + gap * .25f))
            return false;
        boolean header = false;
        for (Component glyph : glyphs)
            if (glyph.maxX < head.minX
                    && head.minX - glyph.maxX < gap * 7
                    && glyph.maxY - glyph.minY > gap * 4.5f
                    && glyph.maxX - glyph.minX > gap * 1.1f
                    && glyph.centerY > staff.top - gap
                    && glyph.centerY < staff.bottom + gap) header = true;
        if (!header) return false;
        int left = Math.max(0, Math.round(head.minX - gap * .55f)),
                right = Math.min(width - 1, Math.round(head.maxX + gap * .25f));
        int top = Math.max(0, Math.round(staff.top));
        int bottom =
                Math.min(head.minY - 1, Math.min(height - 1, Math.round(staff.top + gap * 1.85f)));
        int ink = 0, heads = 0, minY = height, maxY = -1;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                if (labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) heads++;
                float lineDistance =
                        Math.abs((y - staff.top) / gap - Math.round((y - staff.top) / gap)) * gap;
                if (lineDistance <= Math.max(1, gap * .15f)) continue;
                if ((gray[y * width + x] & 255) <= 155) {
                    ink++;
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        // Another genuine note above this head is a chord, not a stacked numeral pair.
        return heads < gap * gap * .15f && ink > gap * 3 && maxY - minY > gap * 1.1f;
    }

    /** Adjacent chord pitches can occupy opposite sides of one shared stem even
     * when segmentation returns two separate components. Their horizontal offset
     * is engraving, not an extra attack between the surrounding eighth notes. */
    private static List<DetectedNote> alignDisplacedSeconds(
            List<DetectedNote> source, byte[] gray, int width, int height) {
        if (gray == null) return source;
        List<DetectedNote> result = new ArrayList<>(source);
        for (int i = 0; i < result.size(); i++)
            for (int j = i + 1; j < result.size(); j++) {
                DetectedNote a = result.get(i), b = result.get(j);
                if (a.event.measureIndex() != b.event.measureIndex()
                        || a.event.staffIndex() != b.event.staffIndex()
                        || a.event.staffCount() != b.event.staffCount()
                        || Math.abs(a.event.staffStep() - b.event.staffStep()) != 1) continue;
                float gap = (a.staffGap + b.staffGap) * .5f,
                        dx = Math.abs(a.head.centerX - b.head.centerX);
                boolean whole =
                        a.event.unbeamedDurationBeats() == ScoreNoteEvent.DURATION_WHOLE
                                && b.event.unbeamedDurationBeats() == ScoreNoteEvent.DURATION_WHOLE;
                if (dx < gap * .9f
                        || dx > gap * (whole ? 2.2f : 1.8f)
                        || Math.abs(a.head.centerY - b.head.centerY) > gap * .75f) continue;
                int left = Math.max(a.head.minX, b.head.minX) - 1,
                        right = Math.min(a.head.maxX, b.head.maxX) + 1;
                if (left > right) continue;
                boolean shared = whole;
                for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++)
                    for (int direction : new int[] {-1, 1}) {
                        int edge =
                                direction < 0
                                        ? Math.min(a.head.minY, b.head.minY)
                                        : Math.max(a.head.maxY, b.head.maxY);
                        int count = 0, samples = 0;
                        for (int k = 1; k <= Math.round(gap * 1.8f); k++) {
                            int y = edge + direction * k;
                            if (y < 0 || y >= height) break;
                            samples++;
                            if ((gray[y * width + x] & 255) < 165) count++;
                        }
                        if (samples >= gap * 1.6f && count >= samples * .92f) shared = true;
                    }
                // Opposing voices can place their touching second on opposite sides
                // of two outer shafts. Their offset is also engraving, while the
                // independently printed beams/dots must remain different.
                if (!shared
                        && a.event.beamCount() > 0
                        && b.event.beamCount() > 0
                        && a.event.augmentationDots() != b.event.augmentationDots()) {
                    DetectedNote upper = a.head.centerY < b.head.centerY ? a : b;
                    DetectedNote lower = upper == a ? b : a;
                    shared =
                            directionalVoiceShaft(gray, width, height, upper.head, gap, -1) != null
                                    && directionalVoiceShaft(
                                                    gray, width, height, lower.head, gap, 1)
                                            != null;
                }
                if (!shared) continue;
                float position = Math.min(a.event.positionInMeasure(), b.event.positionInMeasure());
                // A displaced second may share its original column with further chord tones.
                // Move that whole attack together, preserving every tone's independent duration.
                List<Integer> chord = new ArrayList<>();
                for (int index = 0; index < result.size(); index++) {
                    DetectedNote n = result.get(index);
                    ScoreNoteEvent e = n.event;
                    if (e.measureIndex() != a.event.measureIndex()
                            || e.staffIndex() != a.event.staffIndex()
                            || e.staffCount() != a.event.staffCount()) continue;
                    if (index != i
                            && index != j
                            && Math.abs(n.head.centerX - a.head.centerX) > gap * .3f
                            && Math.abs(n.head.centerX - b.head.centerX) > gap * .3f) continue;
                    chord.add(index);
                    position = Math.min(position, e.positionInMeasure());
                }
                for (int index : chord) {
                    DetectedNote n = result.get(index);
                    ScoreNoteEvent e = n.event;
                    result.set(
                            index,
                            new DetectedNote(
                                    new ScoreNoteEvent(
                                                    e.measureIndex(),
                                                    position,
                                                    e.staffStep(),
                                                    e.staffIndex(),
                                                    e.staffCount(),
                                                    e.pageY(),
                                                    e.tiedFromPrevious(),
                                                    e.augmentationDots(),
                                                    e.beamCount(),
                                                    e.writtenAccidental(),
                                                    e.unbeamedDurationBeats(),
                                                    e.tupletDivisor(),
                                                    e.followingRestBeats(),
                                                    e.articulations(),
                                                    e.clefBottomDiatonic(),
                                                    e.crossStaffBeam(),
                                                    e.leadingRestBeats(),
                                                    e.compactOpening(),
                                                    e.octaveShift(),
                                                    e.boundaryTies(),
                                                    e.tupletNormalNotes())
                                            .withStemDirection(e.stemDirection())
                                            .withKind(e.kind()),
                                    n.head,
                                    n.staffGap));
                }
            }
        return result;
    }

    /** Each printed stave has its own clef stream, including small in-row changes. */
    private static List<DetectedNote> applyPrintedClefs(
            List<DetectedNote> notes,
            List<Staff> staffs,
            List<Component> glyphs,
            byte[] labels,
            byte[] gray,
            int width,
            int height) {
        List<DetectedNote> result = new ArrayList<>(notes);
        Map<Integer, Integer> inherited = new HashMap<>();
        for (Staff staff : staffs) {
            List<ClefGlyph> clefs = new ArrayList<>();
            float gap = staff.gap;
            for (Component original : glyphs) {
                Component glyph =
                        joinTrebleCurl(
                                joinSmallTrebleFragments(original, glyphs, staff), glyphs, staff);
                float gh = glyph.maxY - glyph.minY + 1, gw = glyph.maxX - glyph.minX + 1;
                int clef = ScoreNoteEvent.CLEF_UNKNOWN;
                Staff clefReference = staff;
                // A treble clef crosses the whole stave and projects beyond both outer lines.
                // This excludes tall accidentals and the thin bracket joining two staves.
                if (gh >= gap * 4.8f
                        && gh <= gap * 8.8f
                        && gw >= gap * 1.25f
                        && gw <= gap * 3.4f
                        && glyph.area >= gap * gap * 1.65f
                        && glyph.minY < staff.top - gap * .35f
                        && glyph.maxY > staff.bottom + gap * .20f
                        && Math.abs(glyph.centerY - (staff.top + staff.bottom) * .5f) < gap * 1.1f)
                    clef = ScoreNoteEvent.CLEF_TREBLE;
                // Bass clef: curved body plus the two distinct dots straddling the F line.
                if (gh >= gap * 2.3f
                        && gh <= gap * 3.7f
                        && gw >= gap * 1.35f
                        && gw <= gap * 2.8f
                        && Math.abs(glyph.minY - staff.top) < gap * .65f) {
                    boolean above = false, below = false;
                    for (Component dot : glyphs) {
                        float dh = dot.maxY - dot.minY + 1, dw = dot.maxX - dot.minX + 1;
                        if (dot.centerX < glyph.maxX + gap * .1f
                                || dot.centerX > glyph.maxX + gap * 1.1f
                                || dw < gap * .2f
                                || dw > gap * .7f
                                || dh < gap * .2f
                                || dh > gap * .7f) continue;
                        above |= Math.abs(dot.centerY - (staff.top + gap * .5f)) < gap * .3f;
                        below |= Math.abs(dot.centerY - (staff.top + gap * 1.5f)) < gap * .3f;
                    }
                    if (above && below) clef = ScoreNoteEvent.CLEF_BASS;
                }
                if (clef == ScoreNoteEvent.CLEF_UNKNOWN) {
                    int cClef =
                            PrintedCClef.detect(
                                    gray,
                                    width,
                                    height,
                                    original.minX,
                                    original.maxX,
                                    original.minY,
                                    original.maxY,
                                    staff.pitchBottom,
                                    staff.pitchGap);
                    if (cClef != ScoreNoteEvent.CLEF_UNKNOWN) clef = cClef;
                }
                if (clef == ScoreNoteEvent.CLEF_UNKNOWN
                        && rawBassClef(original, labels, gray, width, height, staff))
                    clef = ScoreNoteEvent.CLEF_BASS;
                if (clef == ScoreNoteEvent.CLEF_UNKNOWN) {
                    Component cue = joinCueTreble(original, glyphs, staff);
                    if (cue != null) {
                        glyph = cue;
                        clef = ScoreNoteEvent.CLEF_TREBLE;
                    } else
                        for (float scale : new float[] {.7f, .8f, .9f}) {
                            float cueGap = staff.pitchGap * scale;
                            float cueTop = staff.pitchBottom - 3 * staff.pitchGap - cueGap;
                            Staff cueStaff = new Staff(cueTop, cueTop + 4 * cueGap, cueGap);
                            Component cueBass = joinCueBassTail(original, glyphs, cueStaff);
                            if (rawBassClef(cueBass, labels, gray, width, height, cueStaff)) {
                                glyph = cueBass;
                                clef = ScoreNoteEvent.CLEF_BASS;
                                break;
                            }
                        }
                }
                // A globally curved staff can move its header beyond the flat
                // frame. Recover only a complete treble glyph; keep existing bass
                // and cue-clef decisions unchanged.
                if (clef == ScoreNoteEvent.CLEF_UNKNOWN && staff.pitchTrack != null) {
                    float[] local = staff.pitchTrack.at(original.centerX);
                    Staff printed = new Staff(local[0] - 4 * local[1], local[0], local[1]);
                    Component candidate =
                            joinTrebleCurl(
                                    joinSmallTrebleFragments(original, glyphs, printed),
                                    glyphs,
                                    printed);
                    float cg = printed.gap,
                            ch = candidate.maxY - candidate.minY + 1,
                            cw = candidate.maxX - candidate.minX + 1;
                    if (ch >= cg * 4.8f
                            && ch <= cg * 8.8f
                            && cw >= cg * 1.25f
                            && cw <= cg * 3.4f
                            && candidate.area >= cg * cg * 1.65f
                            && candidate.minY < printed.top - cg * .35f
                            && candidate.maxY > printed.bottom + cg * .20f
                            && Math.abs(candidate.centerY - (printed.top + printed.bottom) * .5f)
                                    < cg * 1.1f) {
                        glyph = candidate;
                        clef = ScoreNoteEvent.CLEF_TREBLE;
                        clefReference = printed;
                    }
                }
                if (clef == ScoreNoteEvent.CLEF_TREBLE
                        && OctaveClefDigit.above(
                                gray,
                                width,
                                height,
                                glyph.minX,
                                glyph.minY,
                                glyph.maxX,
                                clefReference.top,
                                clefReference.gap)) clef = ScoreNoteEvent.CLEF_TREBLE_OTTAVA;
                if (clef != ScoreNoteEvent.CLEF_UNKNOWN) clefs.add(new ClefGlyph(glyph.maxX, clef));
            }
            clefs.sort(Comparator.comparingDouble(ClefGlyph::x));
            int voice = staff.count * 16 + staff.index;
            int initial = inherited.getOrDefault(voice, ScoreNoteEvent.CLEF_UNKNOWN);
            for (int i = 0; i < result.size(); i++) {
                DetectedNote n = result.get(i);
                if (staffForHead(labels, gray, width, height, staffs, n.head) != staff) continue;
                int active = initial;
                for (ClefGlyph clef : clefs) if (clef.x < n.head.centerX) active = clef.clef;
                result.set(i, new DetectedNote(n.event.withClef(active), n.head, n.staffGap));
            }
            if (!clefs.isEmpty()) inherited.put(voice, clefs.get(clefs.size() - 1).clef);
        }
        return result;
    }

    private record ClefGlyph(float x, int clef) {}

    /** A cue-sized bass tail can cross a staff rule and lose its lower-left classified tip. */
    private static Component joinCueBassTail(Component body, List<Component> glyphs, Staff staff) {
        float g = staff.gap;
        if (body.maxY - body.minY < g * 1.8f
                || body.maxY - body.minY > g * 3f
                || body.maxX - body.minX < g * .9f
                || body.maxX - body.minX > g * 1.7f
                || Math.abs(body.minY - staff.top) > g * .65f) return body;
        Component joined = body;
        for (Component part : glyphs)
            if (part != body
                    && part.minX >= body.minX - g * 1.5f
                    && part.maxX <= body.maxX
                    && part.minY >= body.minY
                    && part.maxY <= staff.top + g * 3.7f
                    && part.area <= body.area * .4f
                    && part.maxY >= staff.top + g * .7f) {
                int area = joined.area + part.area;
                joined =
                        new Component(
                                area,
                                Math.min(joined.minX, part.minX),
                                Math.max(joined.maxX, part.maxX),
                                Math.min(joined.minY, part.minY),
                                Math.max(joined.maxY, part.maxY),
                                (joined.centerX * joined.area + part.centerX * part.area) / area,
                                (joined.centerY * joined.area + part.centerY * part.area) / area);
            }
        return joined;
    }

    /** An in-row treble change is smaller, but its G curl still owns the second staff line. */
    private static Component joinCueTreble(Component body, List<Component> glyphs, Staff staff) {
        float gap = staff.pitchGap, bottom = staff.pitchBottom, top = bottom - 4 * gap;
        if (body.maxY - body.minY < gap * 2.6f
                || body.maxY - body.minY > gap * 4.1f
                || body.maxX - body.minX < gap * .9f
                || body.maxX - body.minX > gap * 1.55f
                || Math.abs(body.minY - top) > gap * .3f
                || body.maxY < bottom - gap * .8f
                || body.maxY > bottom) return null;
        Component joined = body;
        for (Component part : glyphs)
            if (part != body
                    && part.minX >= body.minX - gap * .55f
                    && part.maxX <= body.maxX + gap * .55f
                    && part.minY >= top - gap * .75f
                    && part.maxY <= bottom + gap * 1.3f
                    && part.minY < body.maxY
                    && part.maxY > body.minY) {
                int area = joined.area + part.area;
                joined =
                        new Component(
                                area,
                                Math.min(joined.minX, part.minX),
                                Math.max(joined.maxX, part.maxX),
                                Math.min(joined.minY, part.minY),
                                Math.max(joined.maxY, part.maxY),
                                (joined.centerX * joined.area + part.centerX * part.area) / area,
                                (joined.centerY * joined.area + part.centerY * part.area) / area);
            }
        float gh = joined.maxY - joined.minY + 1, gw = joined.maxX - joined.minX + 1;
        if (joined == body
                || gh < gap * 4.7f
                || gh > gap * 6.4f
                || gw < gap * 1.65f
                || gw > gap * 2.65f
                || joined.area < gap * gap * 2.4f
                || joined.maxY < bottom + gap * .6f
                || joined.minY > top + gap * .1f
                || Math.abs(joined.centerY - (bottom - gap * 1.5f)) > gap * .65f) return null;
        return joined;
    }

    private static boolean rawBassClef(
            Component body, byte[] labels, byte[] gray, int width, int height, Staff staff) {
        // A key accidental beside rounded meter terminals can mimic a bass clef.
        // Preserve a sharp's crossbars or a flat's left spine and lower bowl.
        AccidentalCandidate candidate =
                new AccidentalCandidate(body, OmrMeasurePostProcessor.CLEF_OR_KEY);
        return !isSharpGlyph(labels, width, height, candidate, staff.gap)
                && !isFlatGlyph(labels, width, height, candidate, staff.gap)
                && rawBassClef(body, gray, width, height, staff);
    }

    /** A small bass-clef tail and one dot may be painted as generic symbols.
     * Confirm its two round dots and descending body in the printed pixels. */
    private static boolean rawBassClef(
            Component body, byte[] gray, int width, int height, Staff staff) {
        if (gray == null
                || body.minX <= 0
                || body.minY <= 0
                || body.maxX >= width - 1
                || body.maxY >= height - 1) return false;
        float gap = staff.pitchGap, top = staff.pitchBottom - gap * 4;
        float gh = body.maxY - body.minY + 1, gw = body.maxX - body.minX + 1;
        // Segmentation may lose the left curl of a small bass clef. A narrower
        // seed still needs both printed dots and the descending tail below them.
        if (gh < gap * 1.3f
                || gh > gap * 3.7f
                || gw < gap * 1.0f
                || gw > gap * 2.8f
                || body.area < gap * gap * .5f
                || Math.abs(body.minY - top) > gap * .65f) return false;
        int left = Math.max(0, Math.round(body.maxX + gap * .10f));
        int right = Math.min(width - 1, Math.round(body.maxX + gap * 1.15f));
        int first = Math.max(0, Math.round(top + gap * .1f)),
                last = Math.min(height - 1, Math.round(top + gap * 1.95f));
        int w = right - left + 1, h = last - first + 1;
        if (w < 1 || h < 1) return false;
        byte[] ink = new byte[w * h];
        for (int y = first; y <= last; y++) {
            float distance = Math.abs((y - top) / gap - Math.round((y - top) / gap)) * gap;
            if (distance < gap * .12f) continue;
            for (int x = left; x <= right; x++)
                if ((gray[y * width + x] & 255) < 165) ink[(y - first) * w + x - left] = 1;
        }
        List<Component> dots = new ArrayList<>();
        for (Component dot : findComponents(ink, w, h, (byte) 1)) {
            int dw = dot.maxX - dot.minX + 1, dh = dot.maxY - dot.minY + 1;
            if (dw >= gap * .18f
                    && dw <= gap * .7f
                    && dh >= gap * .18f
                    && dh <= gap * .7f
                    && dot.area >= dw * dh * .45f) dots.add(dot);
        }
        boolean paired = false;
        for (Component a : dots)
            for (Component b : dots)
                if (b.centerY > a.centerY
                        && Math.abs(a.centerX - b.centerX) < gap * .25f
                        && b.centerY - a.centerY >= gap * .55f
                        && b.centerY - a.centerY <= gap * 1.4f
                        && Math.abs((a.centerY + b.centerY) * .5f + first - (top + gap))
                                < gap * .3f) paired = true;
        if (!paired) return false;
        // A two-dot punctuation mark beside another glyph is insufficient: the
        // clef must also have a printed tail reaching below its lower dot.
        for (int y = Math.max(0, Math.round(top + gap * 2.35f));
                y <= Math.min(height - 1, Math.round(top + gap * 3.4f));
                y++) {
            float distance = Math.abs((y - top) / gap - Math.round((y - top) / gap)) * gap;
            if (distance < gap * .15f) continue;
            int count = 0;
            for (int x = Math.max(0, body.minX); x <= Math.min(width - 1, body.maxX); x++)
                if ((gray[y * width + x] & 255) < 165) count++;
            if (count >= gap * .25f && count < gw * .8f) return true;
        }
        return false;
    }

    private static Component joinSmallTrebleFragments(
            Component body, List<Component> glyphs, Staff staff) {
        float gap = staff.gap;
        if (body.maxY - body.minY < gap * 3
                || body.maxY - body.minY > gap * 4.8f
                || body.maxX - body.minX < gap * 1.3f
                || body.maxX - body.minX > gap * 2.5f
                || body.minY > staff.top + gap * .3f
                || body.minY < staff.top - gap
                || body.maxY < staff.bottom - gap
                || body.maxY > staff.bottom + gap * .2f) return body;
        Component joined = body;
        for (Component part : glyphs)
            if (part != body
                    && part.minX >= body.minX - gap * .4f
                    && part.maxX <= body.maxX + gap * .4f
                    && part.minY >= staff.top - gap
                    && part.maxY <= staff.bottom + gap * 1.4f) {
                int area = joined.area + part.area;
                joined =
                        new Component(
                                area,
                                Math.min(joined.minX, part.minX),
                                Math.max(joined.maxX, part.maxX),
                                Math.min(joined.minY, part.minY),
                                Math.max(joined.maxY, part.maxY),
                                (joined.centerX * joined.area + part.centerX * part.area) / area,
                                (joined.centerY * joined.area + part.centerY * part.area) / area);
            }
        return joined;
    }

    /** Staff-line predictions can sever the bottom curl from an otherwise complete treble clef. */
    private static Component joinTrebleCurl(Component body, List<Component> glyphs, Staff staff) {
        float gap = staff.gap;
        float gh = body.maxY - body.minY + 1, gw = body.maxX - body.minX + 1;
        // Require the large, wide, above-staff body first: never construct a clef from accidentals,
        // bass-clef dots, a bracket, or arbitrary small symbols near the bottom of a stave.
        if (gh < gap * 4.8f
                || gh > gap * 8.8f
                || gw < gap * 1.25f
                || gw > gap * 3.4f
                || body.area < gap * gap * 1.65f
                || body.minY >= staff.top - gap * .35f
                || body.maxY < staff.bottom - gap
                || body.maxY > staff.bottom + gap * .20f
                || Math.abs(body.centerY - (staff.top + staff.bottom) * .5f) >= gap * 1.1f)
            return body;
        Component joined = body;
        // Compare integer row coordinates with a distance rounded to the same raster grid.
        for (Component tail : glyphs) {
            if (tail == body
                    || tail.minX < body.minX - gap * .2f
                    || tail.maxX > body.maxX + gap * .2f
                    || tail.minY < staff.bottom - gap
                    || tail.minY > body.maxY + Math.round(gap * .45f)
                    || tail.maxY <= staff.bottom + gap * .2f
                    || tail.maxY > staff.bottom + gap * 2.2f
                    || tail.area < gap * gap * .15f
                    || tail.area > body.area * .75f) continue;
            int area = joined.area + tail.area;
            joined =
                    new Component(
                            area,
                            Math.min(joined.minX, tail.minX),
                            Math.max(joined.maxX, tail.maxX),
                            Math.min(joined.minY, tail.minY),
                            Math.max(joined.maxY, tail.maxY),
                            (joined.centerX * joined.area + tail.centerX * tail.area) / area,
                            (joined.centerY * joined.area + tail.centerY * tail.area) / area);
        }
        return joined;
    }

    /** Count separated flag attachments close to the stem, only beside a proven short rest. */
    private static int rawDetachedFlags(
            byte[] gray, byte[] labels, int width, int height, Component head, float gap) {
        if (gray == null) return 0;
        int[] attached = attachedRawStem(gray, width, height, head, gap);
        // This local flag window must not reinterpret another chord head as
        // a flag when the actual beam lies beyond its search limit.
        if (attached != null && Math.abs(attached[1] - head.centerY) > gap * 4.8f) return 0;
        int stemX = head.maxX, best = 0, direction = -1;
        int top = Math.max(0, Math.round(head.centerY - gap * 4.8f));
        int bottom = Math.max(0, Math.round(head.centerY - gap * .65f));
        int belowTop = Math.min(height - 1, Math.round(head.centerY + gap * .65f));
        int belowBottom = Math.min(height - 1, Math.round(head.centerY + gap * 4.8f));
        for (int x = Math.max(0, head.minX - 3); x <= Math.min(width - 1, head.maxX + 4); x++) {
            int count = countVertical(labels, width, x, top, bottom);
            if (count > best) {
                best = count;
                stemX = x;
                direction = -1;
            }
            count = countVertical(labels, width, x, belowTop, belowBottom);
            if (count > best) {
                best = count;
                stemX = x;
                direction = 1;
            }
        }
        // A staff crossing can fragment the semantic stem below one gap. A continuously
        // attached raw shaft independently proves its direction and endpoint; do not discard
        // it and then measure flags from a short semantic island inside that shaft.
        if (attached == null && best < gap) return 0;
        if (attached != null) {
            stemX = attached[0];
            direction = attached[2];
        }
        int limit = direction < 0 ? top : belowBottom;
        bottom = direction < 0 ? bottom : belowTop;
        int end =
                attached != null
                        ? attached[1]
                        : findStemEnd(
                                labels,
                                width,
                                stemX,
                                direction < 0,
                                direction < 0 ? top : belowTop,
                                direction < 0 ? bottom : belowBottom);
        // A staff crossing can hide the first flag's stem segment in the semantic mask.
        // Follow the attached raw stem outward from the head, allowing only tiny ink gaps.
        int blank = 0, rawEnd = Math.round(head.centerY);
        for (int y = Math.round(head.centerY + direction * gap * .3f);
                direction < 0 ? y >= limit : y <= limit;
                y += direction) {
            boolean dark = false;
            for (int x = Math.max(0, stemX - 1); x <= Math.min(width - 1, stemX + 1); x++)
                if ((gray[y * width + x] & 255) < 170) dark = true;
            if (dark) {
                rawEnd = y;
                blank = 0;
            } else if (++blank > Math.max(2, Math.round(gap * .2f))) break;
        }
        if (Math.abs(rawEnd - head.centerY) > gap * 1.5f && Math.abs(rawEnd - end) < gap * 1.8f)
            end = rawEnd;
        int left = Math.min(width - 1, Math.round(stemX + gap * .3f));
        int right = Math.min(width - 1, Math.round(stemX + gap * .55f));
        int groups = 0, run = 0, first = -1, last = -1;
        int span = Math.abs(bottom - end);
        for (int offset = 0; offset <= span + 1; offset++) {
            int y = end - direction * offset;
            int rowLeft = Math.max(0, stemX - Math.round(gap * 2));
            int rowRight = Math.min(width - 1, stemX + Math.round(gap * 2));
            int rowInk = 0;
            if (offset <= span)
                for (int x = rowLeft; x <= rowRight; x++)
                    if ((gray[y * width + x] & 255) < 170) rowInk++;
            if (rowInk > (rowRight - rowLeft + 1) * .85f) continue;
            int leftInk = 0, rightInk = 0;
            if (offset <= span) {
                for (int x = Math.max(0, stemX - Math.round(gap)); x < stemX - gap * .2f; x++)
                    if ((gray[y * width + x] & 255) < 170) leftInk++;
                for (int x = stemX + 1; x <= Math.min(width - 1, stemX + Math.round(gap)); x++)
                    if ((gray[y * width + x] & 255) < 170) rightInk++;
            }
            if (leftInk > gap * .55f && rightInk > gap * .45f) continue;
            int ink = 0;
            if (offset <= span)
                for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) < 170) ink++;
            if (ink >= 2) run++;
            else {
                if (run >= Math.max(2, gap * .12f)) {
                    groups++;
                    if (first < 0) first = offset;
                    last = offset;
                }
                run = 0;
            }
        }
        return groups == 2 && last - first >= gap * .75f && last - first <= gap * 2f
                ? 2
                : groups == 1 && first <= gap * 1.8f ? 1 : 0;
    }

    /**
     * Finds a compact run of signature accidentals immediately after a measure boundary and
     * before that stave's first note. Requiring at least two same-kind glyphs deliberately avoids
     * treating an ordinary local accidental as a modulation; one-flat/one-sharp changes remain
     * eligible only when the raw score also shows a double bar at the boundary.
     */
    private static List<ScoreKeyChange> detectKeyChanges(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<AccidentalCandidate> candidates,
            List<Component> heads) {
        return detectKeyChangesWithHeaders(
                labels, gray, width, height, measures, staffs, candidates, heads, null);
    }

    private static List<ScoreKeyChange> detectKeyChangesWithHeaders(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<AccidentalCandidate> candidates,
            List<Component> heads,
            List<Component> headerAccidentals) {
        List<ScoreKeyChange> result = new ArrayList<>();
        candidates = joinSignatureFragments(labels, width, candidates, staffs);
        candidates = splitSignatureSharps(labels, width, height, candidates, staffs);
        Map<Integer, Integer> courtesyKeys =
                courtesyKeyChanges(labels, gray, width, height, measures, staffs, candidates);
        for (int measureIndex = 0; measureIndex < measures.size(); measureIndex++) {
            MeasureRegion measure = measures.get(measureIndex);
            Map<Integer, Integer> votes = new HashMap<>();
            if (courtesyKeys.containsKey(measureIndex))
                votes.put(courtesyKeys.get(measureIndex), staffs.size() + 1);
            for (Staff staff : staffs) {
                float staffCenter = (staff.top + staff.bottom) * .5f / height;
                float tolerance = staff.gap * .75f / height;
                if (staffCenter < measure.top() - tolerance
                        || staffCenter > measure.bottom() + tolerance) continue;
                float left = measure.left() * width;
                float boundary = left;
                // Measure rectangles start after clefs/signatures. Include a verified system
                // header so signatures repeated after a page/row break can restore the key.
                boolean firstInRow = true;
                for (MeasureRegion other : measures)
                    if (other.left() < measure.left()
                            && staffCenter >= other.top() - tolerance
                            && staffCenter <= other.bottom() + tolerance) firstInRow = false;
                Component clef = null;
                for (AccidentalCandidate candidate : candidates) {
                    Component c = candidate.component;
                    // An overlapping earlier rectangle can precede the real system header.
                    // A clef immediately beside this boundary still owns its full signature.
                    // Dense signatures and their meter can place the playable edge farther
                    // away. Unlike joined flats, a treble clef extends beyond both outer rules.
                    if ((firstInRow || boundary - c.maxX <= staff.gap * 2.2f)
                            && candidate.label == OmrMeasurePostProcessor.CLEF_OR_KEY
                            && c.maxX < boundary
                            && c.maxX > boundary - staff.gap * (firstInRow ? 16 : 10)
                            && (c.maxY - c.minY > staff.gap * 4.5f
                                            && c.maxX - c.minX > staff.gap * 1.1f
                                    || firstInRow
                                            && rawBassClef(c, labels, gray, width, height, staff))
                            && c.centerY > staff.top - staff.gap
                            && c.centerY < staff.bottom + staff.gap
                            && (clef == null
                                    || completeHeaderClef(c, staff)
                                            && !completeHeaderClef(clef, staff)
                                    || completeHeaderClef(c, staff)
                                                    == completeHeaderClef(clef, staff)
                                            && c.maxX > clef.maxX)) clef = c;
                }
                if (firstInRow && (clef == null || !completeHeaderClef(clef, staff))) {
                    Component joined =
                            fragmentedHeaderClef(candidates, gray, width, height, staff, boundary);
                    if (joined != null) clef = joined;
                }
                if (clef != null) left = clef.maxX + staff.gap * .2f;
                float right = Math.min(measure.right() * width, left + staff.gap * 10.5f);
                float firstHead = Float.MAX_VALUE;
                for (Component head : heads) {
                    if (head.centerX < left - staff.gap * .2f || head.centerX > right) continue;
                    // Ledger notes from the neighboring system cannot truncate this header.
                    if (head.centerX < boundary
                            && (head.centerY < measure.top() * height
                                    || head.centerY > measure.bottom() * height)) continue;
                    Staff owner = nearestHeadStaff(staffs, head.centerY);
                    if (owner == staff) firstHead = Math.min(firstHead, head.minX);
                }
                if (Float.isFinite(firstHead))
                    right = Math.min(right, firstHead - staff.gap * .28f);
                if (right <= left) continue;

                List<SignatureGlyph> glyphs = new ArrayList<>();
                Map<AccidentalCandidate, Float> recognized = new HashMap<>();
                for (AccidentalCandidate candidate : candidates) {
                    Component glyph = candidate.component;
                    if (glyph.centerX < left - staff.gap * .12f
                            || glyph.centerX > right
                            || glyph.centerY < staff.top - staff.gap * 2.25f
                            || glyph.centerY > staff.bottom + staff.gap * 2.25f) continue;
                    // A fragmented semantic double bar may resemble a flat bowl.
                    // Its source column still crosses the complete staff.
                    if (glyph.maxX - glyph.minX + 1 <= Math.round(staff.gap * .65f)
                            && fullStaffRule(gray, width, height, Math.round(glyph.centerX), staff))
                        continue;
                    int accidental =
                            isNaturalGlyph(labels, width, height, candidate, staff.gap)
                                    ? ScoreNoteEvent.ACCIDENTAL_NATURAL
                                    : isSharpGlyph(labels, width, height, candidate, staff.gap)
                                            ? ScoreNoteEvent.ACCIDENTAL_SHARP
                                            : isFlatGlyph(
                                                            labels, width, height, candidate,
                                                            staff.gap)
                                                    ? ScoreNoteEvent.ACCIDENTAL_FLAT
                                                    : ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
                    // Recover a substantial fragment in a verified header from the printed
                    // sharp. Tiny semantic specks can also occur on nearby meter digits.
                    float signatureX = glyph.centerX;
                    boolean printedFlat =
                            clef != null
                                    && PrintedFlatGlyph.matches(
                                            gray,
                                            width,
                                            height,
                                            glyph.minX,
                                            glyph.minY,
                                            glyph.maxX,
                                            glyph.maxY,
                                            staff.gap);
                    if (printedFlat) accidental = ScoreNoteEvent.ACCIDENTAL_FLAT;
                    if (!printedFlat
                            && (accidental == ScoreNoteEvent.ACCIDENTAL_FLAT
                                    || accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                                            && clef != null
                                            && glyph.area >= staff.gap * staff.gap * .18f)) {
                        float printedX =
                                printedSignatureSharpCenter(
                                        gray, width, height, candidate, staff.gap);
                        if (Float.isFinite(printedX)) {
                            accidental = ScoreNoteEvent.ACCIDENTAL_SHARP;
                            // A nearby rule fragment may recover the same printed sharp.
                            // Position it at that glyph so it cannot count as a second sharp.
                            signatureX = printedX;
                        }
                    }
                    // Flats lie in the staff's signature pitch band; text above it does not.
                    if (accidental == ScoreNoteEvent.ACCIDENTAL_FLAT
                            && (glyph.centerY < staff.top - staff.gap * .8f
                                    || glyph.centerY > staff.bottom + staff.gap * .8f)) continue;
                    // A cut-time C can leave two apparent spines in its classified shoulder.
                    if (clef != null && accidental == ScoreNoteEvent.ACCIDENTAL_SHARP) {
                        float[] local = localStaffPitch(labels, gray, width, height, staff, glyph);
                        Staff localStaff = new Staff(local[0] - 4 * local[1], local[0], local[1]);
                        int cx = Math.round(glyph.centerX),
                                cy = Math.round(local[0] - 2 * local[1]);
                        Component seed = new Component(4, cx - 1, cx + 1, cy - 1, cy + 1, cx, cy);
                        if (commonTimeGlyphBounds(
                                        labels,
                                        gray,
                                        width,
                                        height,
                                        seed,
                                        List.of(localStaff),
                                        List.of(clef))
                                != null) continue;
                    }
                    if (accidental != ScoreNoteEvent.ACCIDENTAL_FROM_KEY) {
                        glyphs.add(new SignatureGlyph(signatureX, accidental));
                        recognized.put(candidate, signatureX);
                    }
                }
                if (clef != null && !glyphs.isEmpty()) {
                    var printed = printedHeaderSymbols(gray, width, height, left, right, staff);
                    for (int pass = 0; pass < 7; pass++) {
                        boolean added = false;
                        for (var raw : printed)
                            if (glyphs.stream()
                                            .anyMatch(
                                                    g ->
                                                            g.accidental == raw.accidental
                                                                    && Math.abs(g.x - raw.x)
                                                                            < staff.gap * 1.85f)
                                    && glyphs.stream()
                                            .noneMatch(
                                                    g ->
                                                            Math.abs(g.x - raw.x)
                                                                    < staff.gap * .45f)) {
                                glyphs.add(raw);
                                added = true;
                            }
                        if (!added) break;
                    }
                }
                glyphs.sort(Comparator.comparingDouble(SignatureGlyph::x));
                List<SignatureGlyph> run = densestSignatureRun(glyphs, staff.gap);
                if (run.isEmpty()) {
                    if (measureIndex == 0
                            && firstInRow
                            && clef != null
                            && completeHeaderClef(clef, staff)) {
                        int sx = Math.round(clef.maxX + staff.gap * 2),
                                sy = Math.round(staff.bottom - staff.gap * 2);
                        Component seed = new Component(4, sx - 1, sx + 1, sy - 1, sy + 1, sx, sy);
                        float[] local = localStaffPitch(labels, gray, width, height, staff, seed);
                        if (ExplicitEmptySignature.matches(
                                gray, width, height, clef.maxX, firstHead, local[0], local[1]))
                            votes.put(0, votes.getOrDefault(0, 0) + 1);
                    }
                    continue;
                }
                int flats = 0, sharps = 0, naturals = 0;
                for (SignatureGlyph glyph : run) {
                    if (glyph.accidental == ScoreNoteEvent.ACCIDENTAL_FLAT) flats++;
                    else if (glyph.accidental == ScoreNoteEvent.ACCIDENTAL_SHARP) sharps++;
                    else if (glyph.accidental == ScoreNoteEvent.ACCIDENTAL_NATURAL) naturals++;
                }
                boolean doubleBar = hasDoubleBar(labels, gray, width, height, boundary, staff);
                boolean cancelledSignature = false;
                List<SignatureGlyph> completeRun = run;
                // Cancellation naturals precede, rather than replace, the new signature.
                // Require boundary evidence and a single ordered sharp/flat suffix.
                if (naturals > 0 && (flats > 0 || sharps > 0) && doubleBar) {
                    int split = 0;
                    while (split < run.size()
                            && run.get(split).accidental == ScoreNoteEvent.ACCIDENTAL_NATURAL)
                        split++;
                    List<SignatureGlyph> suffix = run.subList(split, run.size());
                    if (split == naturals
                            && !suffix.isEmpty()
                            && (flats == 0 || sharps == 0)
                            && (suffix.size() == 1
                                    || orderedSignaturePitches(
                                            labels, width, height, suffix, recognized, staff))) {
                        run = suffix;
                        naturals = 0;
                        cancelledSignature = true;
                    }
                }
                boolean signatureHeader =
                        clef != null
                                && run.get(0).x < clef.maxX + staff.gap * 2.2f
                                && firstHead - run.get(run.size() - 1).x >= staff.gap * 1.35f;
                // Staff lines can cut a flat's bowl away from its spine, producing two semantic
                // components that are individually too incomplete for the local-accidental
                // classifier. Once at least one flat establishes the run's glyph family, count
                // the repeated tall left spines in the same pre-note slot.
                if (flats > 0 && sharps == 0 && naturals == 0 && !cancelledSignature) {
                    int spines =
                            countHeaderFlatSpines(
                                    labels,
                                    gray,
                                    width,
                                    height,
                                    left,
                                    right,
                                    staff,
                                    doubleBar && clef == null,
                                    signatureHeader);
                    // countFlatSpines verifies the alternating fourth/fifth heights;
                    // parallel spines of one sharp cannot establish that ordered run.
                    flats = Math.max(flats, spines);
                }
                int strongest = Math.max(naturals, Math.max(flats, sharps));
                if (strongest > 7
                        || (flats > 0 ? sharps + naturals > 0 : sharps > 0 ? naturals > 0 : false))
                    continue;
                if (strongest < 2 && !doubleBar && !signatureHeader) continue;
                if (strongest > 1
                        && !doubleBar
                        && firstHead - run.get(run.size() - 1).x < staff.gap * 1.65f
                        && !orderedSignaturePitches(labels, width, height, run, recognized, staff))
                    continue;
                // A lone signature follows its bar closely; a distant clef fragment does not.
                if (strongest == 1
                        && clef == null
                        && !cancelledSignature
                        && run.get(0).x - boundary > staff.gap * 3) continue;
                // Close accidentals on a chord are not a new key. Multiple glyphs
                // need a boundary or the ordered fourth/fifth signature pattern.
                if (!signatureHeader
                        && firstHead - run.get(run.size() - 1).x < staff.gap * 1.35f
                        && (strongest == 1
                                || !doubleBar
                                        && !orderedSignaturePitches(
                                                labels, width, height, run, recognized, staff)))
                    continue;
                // Octave chord accidentals can share one column while a third sharp
                // supplies the apparent F/C key order. A signature never repeats
                // two complete sharp glyphs an octave apart in the same column.
                if (!signatureHeader
                        && sharps > 1
                        && stackedSignatureSharpColumn(
                                labels, width, height, run, recognized, staff)) continue;
                int fifths = naturals > 0 ? 0 : flats > 0 ? -flats : sharps;
                if (clef != null
                        && !result.isEmpty()
                        && fifths > 0
                        && result.get(result.size() - 1).fifths() > fifths
                        && run.get(0).x - clef.maxX > staff.gap * 2.2f) continue;
                if (signatureHeader
                        && !result.isEmpty()
                        && fifths > 0
                        && result.get(result.size() - 1).fifths() > fifths
                        && unfinishedSharpTail(
                                labels, gray, width, height, candidates, run, staff, firstHead))
                    continue;
                if (headerAccidentals != null
                        && (cancelledSignature
                                || signatureHeader
                                        && orderedSignaturePitches(
                                                labels, width, height, run, recognized, staff)))
                    for (var entry : recognized.entrySet())
                        if (completeRun.stream()
                                .anyMatch(
                                        glyph ->
                                                Math.abs(glyph.x - entry.getValue())
                                                        < staff.gap * .1f))
                            headerAccidentals.add(entry.getKey().component);
                votes.put(fifths, votes.getOrDefault(fifths, 0) + 1);
            }
            int bestFifths = 0, bestVotes = 0;
            for (Map.Entry<Integer, Integer> vote : votes.entrySet())
                if (vote.getValue() > bestVotes) {
                    bestFifths = vote.getKey();
                    bestVotes = vote.getValue();
                }
            // A damaged repeated header on one stave must not outvote the intact matching
            // header on another. Preserve the established key when support is tied.
            if (!result.isEmpty()) {
                int inherited = result.get(result.size() - 1).fifths();
                if (votes.getOrDefault(inherited, 0) == bestVotes && bestVotes > 0)
                    bestFifths = inherited;
            }
            if (bestVotes > 0
                    && (result.isEmpty() || result.get(result.size() - 1).fifths() != bestFifths))
                result.add(new ScoreKeyChange(measureIndex, bestFifths));
        }
        return List.copyOf(result);
    }

    /** Complete end-of-system courtesy signatures apply at the next row. */
    private static Map<Integer, Integer> courtesyKeyChanges(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<MeasureRegion> measures,
            List<Staff> staffs,
            List<AccidentalCandidate> candidates) {
        var result = new HashMap<Integer, Integer>();
        var conflicted = new java.util.HashSet<Integer>();
        for (Staff staff : staffs) {
            float center = (staff.top + staff.bottom) * .5f / height, gap = staff.gap;
            int last = -1;
            for (int i = 0; i < measures.size(); i++) {
                var m = measures.get(i);
                if (center < m.top() - gap / height || center > m.bottom() + gap / height) continue;
                if (last < 0 || m.right() >= measures.get(last).right()) last = i;
            }
            if (last < 0 || last + 1 >= measures.size()) continue;
            var measure = measures.get(last);
            var next = measures.get(last + 1);
            if (next.top() <= measure.top() + gap * 3 / height) continue;
            float boundary = measure.right() * width;
            if (!hasDoubleBar(labels, gray, width, height, boundary, staff)) continue;
            var glyphs = new ArrayList<SignatureGlyph>();
            for (var candidate : candidates) {
                var c = candidate.component;
                if (c.centerX < boundary + gap * .1f
                        || c.centerX > boundary + gap * 8
                        || c.centerY < staff.top - gap * 2
                        || c.centerY > staff.bottom + gap * 2
                        || c.maxX - c.minX < gap * .35f
                        || c.maxX - c.minX > gap * 1.6f) continue;
                float sx = printedSignatureSharpCenter(gray, width, height, candidate, gap);
                int accidental = ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
                float y = Float.NaN;
                if (Float.isFinite(sx)) {
                    accidental = ScoreNoteEvent.ACCIDENTAL_SHARP;
                    y = printedSignatureSharp(gray, width, height, candidate, gap, true);
                } else {
                    sx = c.centerX;
                    if (PrintedFlatGlyph.matches(
                            gray, width, height, c.minX, c.minY, c.maxX, c.maxY, gap)) {
                        accidental = ScoreNoteEvent.ACCIDENTAL_FLAT;
                        y = flatPitchCenter(labels, width, candidate, gap);
                    } else {
                        // The right crop boundary replaces the absent following note; the
                        // complete asymmetric natural still has to be proved in raw pixels.
                        int x = Math.round(c.maxX + gap), cy = Math.round(c.centerY);
                        Component cropBoundary = new Component(1, x, x, cy, cy, x, c.centerY);
                        if (isNaturalGlyph(labels, width, height, candidate, gap)
                                || rawNaturalAtSeed(gray, width, height, c, cropBoundary, gap))
                            accidental = ScoreNoteEvent.ACCIDENTAL_NATURAL;
                    }
                }
                if (accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY) continue;
                boolean duplicate = false;
                for (var existing : glyphs)
                    if (Math.abs(existing.x - sx) < gap * .5f) duplicate = true;
                if (!duplicate) glyphs.add(new SignatureGlyph(sx, accidental, y));
            }
            glyphs.sort(Comparator.comparingDouble(SignatureGlyph::x));
            if (glyphs.isEmpty() || glyphs.size() > 7 || glyphs.get(0).x - boundary > gap * 2)
                continue;
            int family = glyphs.get(0).accidental;
            boolean valid = true;
            for (int i = 1; i < glyphs.size(); i++) {
                var a = glyphs.get(i - 1);
                var b = glyphs.get(i);
                int interval = Math.round((a.pitchY - b.pitchY) * 2 / gap);
                if (b.accidental != family
                        || b.x - a.x < gap * .65f
                        || b.x - a.x > gap * 1.9f
                        || family != ScoreNoteEvent.ACCIDENTAL_NATURAL
                                && (!Float.isFinite(a.pitchY)
                                        || !Float.isFinite(b.pitchY)
                                        || Math.floorMod(interval, 7)
                                                != (family == ScoreNoteEvent.ACCIDENTAL_FLAT
                                                        ? 3
                                                        : 4))) valid = false;
            }
            if (!valid) continue;
            int fifths = family == ScoreNoteEvent.ACCIDENTAL_NATURAL ? 0 : family * glyphs.size();
            if (result.containsKey(last + 1) && result.get(last + 1) != fifths) {
                result.remove(last + 1);
                conflicted.add(last + 1);
            } else if (!conflicted.contains(last + 1)) result.put(last + 1, fifths);
        }
        return result;
    }

    private static List<Component> sharpCrossbarHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        var rejected = new ArrayList<Component>();
        var centers = new HashMap<Component, Float>();
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.area > gap * gap * .65f
                    || head.area < gap * gap * .1f
                    || head.maxX - head.minX + 1 > gap
                    || head.maxY - head.minY + 1 > gap * .85f) continue;
            float center =
                    printedSignatureSharpCenter(
                            gray,
                            width,
                            height,
                            new AccidentalCandidate(head, OmrMeasurePostProcessor.SYMBOL),
                            gap);
            if (Float.isFinite(center) && Math.abs(center - head.centerX) < gap * .4f)
                centers.put(head, center);
        }
        for (var entry : centers.entrySet()) {
            Component head = entry.getKey();
            float gap = nearestHeadStaff(staffs, head.centerY).gap;
            for (var other : centers.entrySet()) {
                Component pair = other.getKey();
                float dy = Math.abs(pair.centerY - head.centerY);
                if (pair == head
                        || dy < gap * .5f
                        || dy > gap * 1.5f
                        || Math.abs(pair.centerX - head.centerX) > gap * .4f
                        || Math.abs(entry.getValue() - other.getValue()) > gap * .3f) continue;
                rejected.add(head);
                break;
            }
        }
        return rejected;
    }

    private static List<SignatureGlyph> printedHeaderSymbols(
            byte[] gray, int width, int height, float from, float to, Staff staff) {
        var result =
                new ArrayList<SignatureGlyph>(
                        printedHeaderSymbols(gray, width, height, from, to, staff, false));
        for (var glyph : printedHeaderSymbols(gray, width, height, from, to, staff, true))
            if (glyph.accidental == ScoreNoteEvent.ACCIDENTAL_SHARP
                    && result.stream().noneMatch(g -> Math.abs(g.x - glyph.x) < staff.gap * .45f))
                result.add(glyph);
        return result;
    }

    private static List<SignatureGlyph> printedHeaderSymbols(
            byte[] gray, int width, int height, float from, float to, Staff staff, boolean wide) {
        if (gray == null) return List.of();
        int left = Math.max(0, Math.round(from)), right = Math.min(width - 1, Math.round(to));
        int top = Math.max(0, Math.round(staff.top - staff.gap * 2.5f)),
                bottom = Math.min(height - 1, Math.round(staff.bottom + staff.gap * 2.5f));
        int w = right - left + 1, h = bottom - top + 1;
        if (w < 1 || h < 1) return List.of();
        byte[] mask = new byte[w * h];
        int probe = Math.max(2, Math.round(staff.gap * (wide ? .42f : .25f)));
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                if ((gray[y * width + x] & 255) > 205) continue;
                boolean rule = false;
                for (int i = 0; i < 5; i++)
                    if (Math.abs(y - staff.top - i * staff.gap) < staff.gap * (wide ? .35f : .16f))
                        rule = true;
                if (rule
                        && (y < probe
                                || y + probe >= height
                                || (gray[(y - probe) * width + x] & 255) > 205
                                || (gray[(y + probe) * width + x] & 255) > 205)) continue;
                mask[(y - top) * w + x - left] = OmrMeasurePostProcessor.SYMBOL;
            }
        var result = new ArrayList<SignatureGlyph>();
        for (var c : findComponents(mask, w, h, OmrMeasurePostProcessor.SYMBOL)) {
            if (c.maxX - c.minX > staff.gap * 1.65f
                    || c.maxY - c.minY > staff.gap * 3.65f
                    || c.area < staff.gap * staff.gap * .25f) continue;
            var candidate = new AccidentalCandidate(c, OmrMeasurePostProcessor.SYMBOL);
            if (isSharpGlyph(mask, w, h, candidate, staff.gap))
                result.add(
                        new SignatureGlyph(
                                left + c.centerX,
                                ScoreNoteEvent.ACCIDENTAL_SHARP,
                                top + sharpPitchCenter(mask, w, h, candidate, staff.gap)));
            else if (PrintedFlatGlyph.matches(
                    gray,
                    width,
                    height,
                    left + c.minX,
                    top + c.minY,
                    left + c.maxX,
                    top + c.maxY,
                    staff.gap))
                result.add(new SignatureGlyph(left + c.centerX, ScoreNoteEvent.ACCIDENTAL_FLAT));
        }
        return result;
    }

    private static Component fragmentedHeaderClef(
            List<AccidentalCandidate> candidates,
            byte[] gray,
            int width,
            int height,
            Staff staff,
            float boundary) {
        Component best = null;
        float g = staff.gap;
        for (var lower : candidates) {
            Component b = lower.component;
            if (b.maxX >= boundary
                    || b.maxX < boundary - g * 16
                    || b.maxY < staff.bottom + g * .5f
                    || b.minY < staff.top - g * .1f
                    || b.maxY - b.minY < g * 2.5f
                    || b.maxY - b.minY > g * 6f) continue;
            for (var upper : candidates) {
                Component a = upper.component;
                if (a == b
                        || a.minY >= staff.top - g * .35f
                        || a.maxY < staff.top + g * .8f
                        || a.maxY > staff.bottom
                        || a.maxX < b.minX
                        || a.minX > b.maxX
                        || Math.abs(a.centerX - b.centerX) > g * 1.2f) continue;
                int x0 = Math.min(a.minX, b.minX),
                        x1 = Math.max(a.maxX, b.maxX),
                        y0 = Math.min(a.minY, b.minY),
                        y1 = Math.max(a.maxY, b.maxY);
                if (x1 - x0 < g * 1.6f
                        || x1 - x0 > g * 3.8f
                        || y1 - y0 < g * 6f
                        || y1 - y0 > g * 9f
                        || a.area + b.area < g * g * 3.5f) continue;
                int rules = 0, sampleX = Math.min(width - 1, Math.round(x1 + g));
                if (gray != null)
                    for (int line = 0; line < 5; line++) {
                        int cy = Math.round(staff.top + line * g);
                        boolean ink = false;
                        for (int yy = Math.max(0, Math.round(cy - g * .3f));
                                yy <= Math.min(height - 1, Math.round(cy + g * .3f));
                                yy++) if ((gray[yy * width + sampleX] & 255) <= 205) ink = true;
                        if (ink) rules++;
                    }
                if (rules < 3) continue;
                int area = a.area + b.area;
                Component c =
                        new Component(
                                area,
                                x0,
                                x1,
                                y0,
                                y1,
                                (a.centerX * a.area + b.centerX * b.area) / area,
                                (a.centerY * a.area + b.centerY * b.area) / area);
                if (best == null || c.area > best.area) best = c;
            }
        }
        return best;
    }

    /** Prefer a complete treble over a later joined accidental cluster, while retaining
     * the existing damaged-clef fallback when no complete glyph survives. */
    private static boolean completeHeaderClef(Component glyph, Staff staff) {
        return glyph.minY < staff.top - staff.gap * .35f
                && glyph.maxY > staff.bottom + staff.gap * .20f;
    }

    private static boolean stackedSignatureSharpColumn(
            byte[] labels,
            int width,
            int height,
            List<SignatureGlyph> run,
            Map<AccidentalCandidate, Float> recognized,
            Staff staff) {
        var entries = new ArrayList<>(recognized.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            var a = entries.get(i);
            if (run.stream()
                    .noneMatch(
                            g ->
                                    g.accidental == ScoreNoteEvent.ACCIDENTAL_SHARP
                                            && Math.abs(g.x - a.getValue()) < staff.gap * .45f))
                continue;
            for (int j = i + 1; j < entries.size(); j++) {
                var b = entries.get(j);
                if (Math.abs(a.getValue() - b.getValue()) >= staff.gap * .45f) continue;
                if (!isSharpGlyph(labels, width, height, a.getKey(), staff.gap)
                        || !isSharpGlyph(labels, width, height, b.getKey(), staff.gap)) continue;
                float ay = sharpPitchCenter(labels, width, height, a.getKey(), staff.pitchGap);
                float by = sharpPitchCenter(labels, width, height, b.getKey(), staff.pitchGap);
                if (Float.isFinite(ay)
                        && Float.isFinite(by)
                        && Math.abs(ay - by) > staff.pitchGap * 2.75f) return true;
            }
        }
        return false;
    }

    /** Adjacent key symbols follow fourths/fifths; a nearby note accidental need not. */
    private static boolean orderedSignaturePitches(
            byte[] labels,
            int width,
            int height,
            List<SignatureGlyph> run,
            Map<AccidentalCandidate, Float> recognized,
            Staff staff) {
        if (run.size() < 2) return false;
        int family = run.get(0).accidental;
        float previous = 0;
        if (family != ScoreNoteEvent.ACCIDENTAL_FLAT && family != ScoreNoteEvent.ACCIDENTAL_SHARP)
            return false;
        for (int i = 0; i < run.size(); i++) {
            SignatureGlyph symbol = run.get(i);
            if (symbol.accidental != family) return false;
            AccidentalCandidate owner = null;
            for (var entry : recognized.entrySet())
                if (Math.abs(entry.getValue() - symbol.x) < staff.gap * .1f) {
                    owner = entry.getKey();
                    break;
                }
            if (owner == null) return false;
            float center =
                    family == ScoreNoteEvent.ACCIDENTAL_FLAT
                            ? flatPitchCenter(labels, width, owner, staff.pitchGap)
                            : sharpPitchCenter(labels, width, height, owner, staff.pitchGap);
            if (!Float.isFinite(center)) return false;
            int interval = Math.round((previous - center) * 2 / staff.pitchGap);
            if (i > 0
                    && Math.floorMod(interval, 7)
                            != (family == ScoreNoteEvent.ACCIDENTAL_FLAT ? 3 : 4)) return false;
            previous = center;
        }
        return true;
    }

    /** A staff stripe can leave only one lobe of a sharp in the semantic mask.
     * Recover its narrow printed column only when both complete spines and both
     * crossbars prove a sharp; a flat's bowl alone cannot supply that evidence. */
    private static float printedSignatureSharpCenter(
            byte[] gray, int width, int height, AccidentalCandidate candidate, float gap) {
        return printedSignatureSharp(gray, width, height, candidate, gap, false);
    }

    private static float printedSignatureSharp(
            byte[] gray,
            int width,
            int height,
            AccidentalCandidate candidate,
            float gap,
            boolean pitch) {
        for (float margin : new float[] {.3f, .45f, .6f}) {
            float result =
                    printedSignatureSharpInCrop(gray, width, height, candidate, gap, pitch, margin);
            if (Float.isFinite(result)) return result;
        }
        return Float.NaN;
    }

    private static float printedSignatureSharpInCrop(
            byte[] gray,
            int width,
            int height,
            AccidentalCandidate candidate,
            float gap,
            boolean pitch,
            float cropMargin) {
        if (gray == null || gap < 3) return Float.NaN;
        Component seed = candidate.component;
        if (seed.maxX - seed.minX + 1 > gap * 1.6f || seed.maxY - seed.minY + 1 > gap * 3.65f)
            return Float.NaN;
        int margin = Math.max(1, Math.round(gap * cropMargin));
        int left = Math.max(0, seed.minX - margin), right = Math.min(width - 1, seed.maxX + margin);
        int top = Math.max(0, Math.round(seed.centerY - gap * 2.5f));
        int bottom = Math.min(height - 1, Math.round(seed.centerY + gap * 2.5f));
        int w = right - left + 1, h = bottom - top + 1, reach = Math.max(3, Math.round(gap * .7f));
        int probe = Math.max(2, Math.round(gap * .2f));
        for (int threshold : new int[] {180, 205}) {
            byte[] ink = new byte[w * h];
            for (int y = top; y <= bottom; y++) {
                int outside = 0, dark = 0;
                for (int x = Math.max(0, left - reach);
                        x <= Math.min(width - 1, right + reach);
                        x++)
                    if (x < left || x > right) {
                        outside++;
                        if ((gray[y * width + x] & 255) <= threshold) dark++;
                    }
                boolean rule = outside > 0 && dark >= outside * .8f;
                for (int x = left; x <= right; x++) {
                    if ((gray[y * width + x] & 255) > threshold) continue;
                    if (rule
                            && (y < probe
                                    || y + probe >= height
                                    || (gray[(y - probe) * width + x] & 255) > threshold
                                    || (gray[(y + probe) * width + x] & 255) > threshold)) continue;
                    ink[(y - top) * w + x - left] = OmrMeasurePostProcessor.SYMBOL;
                }
            }
            Component glyph = retainSeedConnectedInk(ink, w, h, seed, left, top);
            if (glyph == null
                    || rawStrokeLeavesCrop(gray, width, height, ink, w, h, left, top, gap))
                continue;
            if (isSharpGlyph(
                    ink, w, h, new AccidentalCandidate(glyph, OmrMeasurePostProcessor.SYMBOL), gap))
                return pitch
                        ? top
                                + sharpPitchCenter(
                                        ink,
                                        w,
                                        h,
                                        new AccidentalCandidate(
                                                glyph, OmrMeasurePostProcessor.SYMBOL),
                                        gap)
                        : left + glyph.centerX;
        }
        byte[] faint = FaintSharpInk.crop(gray, width, height, left, top, right, bottom, gap);
        if (faint != null) {
            Component glyph = retainSeedConnectedInk(faint, w, h, seed, left, top);
            if (glyph != null
                    && !rawStrokeLeavesCrop(gray, width, height, faint, w, h, left, top, gap)) {
                float center =
                        sharpPitchCenter(
                                faint,
                                w,
                                h,
                                new AccidentalCandidate(glyph, OmrMeasurePostProcessor.CLEF_OR_KEY),
                                gap);
                if (Float.isFinite(center)) return pitch ? top + center : left + glyph.centerX;
            }
        }
        return Float.NaN;
    }

    /** A visibly unfinished extra sharp cannot prove that a repeated key has fewer sharps. */
    private static boolean unfinishedSharpTail(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            List<SignatureGlyph> run,
            Staff staff,
            float firstHead) {
        float lastX = run.get(run.size() - 1).x, lastPitch = Float.NaN;
        for (AccidentalCandidate candidate : candidates)
            if (Math.abs(candidate.component.centerX - lastX) < staff.gap * .35f
                    && candidate.component.centerY >= staff.top - staff.gap * 2.25f
                    && candidate.component.centerY <= staff.bottom + staff.gap * 2.25f) {
                float pitch = sharpPitchCenter(labels, width, height, candidate, staff.gap);
                if (!Float.isFinite(pitch))
                    pitch = printedSignatureSharp(gray, width, height, candidate, staff.gap, true);
                if (Float.isFinite(pitch)) lastPitch = pitch;
            }
        if (!Float.isFinite(lastPitch))
            for (var glyph :
                    printedHeaderSymbols(
                            gray,
                            width,
                            height,
                            run.get(0).x - staff.gap,
                            firstHead - staff.gap * .28f,
                            staff))
                if (glyph.accidental == ScoreNoteEvent.ACCIDENTAL_SHARP
                        && Math.abs(glyph.x - lastX) < staff.gap * .35f
                        && Float.isFinite(glyph.pitchY)) lastPitch = glyph.pitchY;
        if (!Float.isFinite(lastPitch)) return false;
        for (AccidentalCandidate candidate : candidates) {
            Component c = candidate.component;
            float dx = c.centerX - lastX;
            if (candidate.label != OmrMeasurePostProcessor.CLEF_OR_KEY
                            && candidate.label != OmrMeasurePostProcessor.SYMBOL
                    || c.centerY < staff.top - staff.gap * 2.25f
                    || c.centerY > staff.bottom + staff.gap * 2.25f
                    || dx < staff.gap * .65f
                    || dx > staff.gap * 1.85f
                    || firstHead - c.centerX < staff.gap * 1.35f
                    || c.maxX - c.minX + 1 < staff.gap * .5f
                    || c.maxX - c.minX + 1 > staff.gap * 1.8f
                    || c.maxY - c.minY + 1 < staff.gap * 1.2f
                    || c.maxY - c.minY + 1 > staff.gap * 3.65f
                    || c.area < staff.gap * staff.gap * .3f
                    || isNaturalGlyph(labels, width, height, candidate, staff.gap)
                    || isFlatGlyph(labels, width, height, candidate, staff.gap)
                    || isSharpGlyph(labels, width, height, candidate, staff.gap)) continue;
            for (float expected :
                    new float[] {lastPitch + staff.gap * 1.5f, lastPitch - staff.gap * 2f})
                if (expected >= c.minY - staff.gap * .2f && expected <= c.maxY + staff.gap * .2f)
                    return true;
        }
        return false;
    }

    /** Resolve joined signature sharps only when each slice has a complete sharp shape. */
    private static List<AccidentalCandidate> splitSignatureSharps(
            byte[] labels,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            List<Staff> staffs) {
        List<AccidentalCandidate> result = new ArrayList<>();
        for (AccidentalCandidate candidate : candidates) {
            Staff staff = nearestHeadStaff(staffs, candidate.component.centerY);
            List<AccidentalCandidate> parts =
                    candidate.label == OmrMeasurePostProcessor.CLEF_OR_KEY && staff != null
                            ? splitSignatureSharpRun(labels, width, height, candidate, staff.gap, 7)
                            : List.of();
            if (parts.size() >= 2) result.addAll(parts);
            else result.add(candidate);
        }
        return result;
    }

    private static List<AccidentalCandidate> splitSignatureSharpRun(
            byte[] labels,
            int width,
            int height,
            AccidentalCandidate candidate,
            float gap,
            int remaining) {
        Component box = candidate.component;
        if (isSharpGlyph(labels, width, height, candidate, gap)) return List.of(candidate);
        if (remaining < 2
                || box.maxX - box.minX + 1 < gap * 1.65f
                || box.maxX - box.minX + 1 > gap * remaining * 1.8f
                || box.maxY - box.minY + 1 > gap * 7f) return List.of();
        int first = box.minX + Math.max(3, Math.round(gap * .65f));
        int last =
                Math.min(
                        box.maxX - Math.max(3, Math.round(gap * .65f)),
                        box.minX + Math.round(gap * 1.8f));
        for (int cut = first; cut <= last; cut++) {
            AccidentalCandidate left = signatureSlice(labels, width, candidate, box.minX, cut);
            if (left == null) continue;
            float leftPitch = sharpPitchCenter(labels, width, height, left, gap);
            if (!Float.isFinite(leftPitch)) continue;
            AccidentalCandidate right = signatureSlice(labels, width, candidate, cut + 1, box.maxX);
            if (right == null) continue;
            List<AccidentalCandidate> rest =
                    splitSignatureSharpRun(labels, width, height, right, gap, remaining - 1);
            if (rest.isEmpty()) continue;
            float delta = sharpPitchCenter(labels, width, height, rest.get(0), gap) - leftPitch;
            // Consecutive signature sharps alternate a fourth down or a fifth up.
            if (Math.abs(delta - gap * 1.5f) > gap * .55f
                    && Math.abs(delta + gap * 2f) > gap * .55f) continue;
            List<AccidentalCandidate> result = new ArrayList<>();
            result.add(left);
            result.addAll(rest);
            return result;
        }
        return List.of();
    }

    private static AccidentalCandidate signatureSlice(
            byte[] labels, int width, AccidentalCandidate candidate, int left, int right) {
        Component box = candidate.component;
        int cropWidth = right - left + 1, cropHeight = box.maxY - box.minY + 1;
        byte[] crop = new byte[cropWidth * cropHeight];
        for (int y = box.minY; y <= box.maxY; y++)
            for (int x = left; x <= right; x++)
                if (candidate.matches(labels[y * width + x]))
                    crop[(y - box.minY) * cropWidth + x - left] = 3;
        Component best = null;
        for (Component piece : findComponents(crop, cropWidth, cropHeight, (byte) 3))
            if (best == null || piece.area > best.area) best = piece;
        return best == null
                ? null
                : new AccidentalCandidate(
                        new Component(
                                best.area,
                                best.minX + left,
                                best.maxX + left,
                                best.minY + box.minY,
                                best.maxY + box.minY,
                                best.centerX + left,
                                best.centerY + box.minY),
                        candidate.label);
    }

    /** Reconnect a signature glyph cut horizontally by a staff-labelled scan line. */
    private static List<AccidentalCandidate> joinSignatureFragments(
            byte[] labels, int width, List<AccidentalCandidate> candidates, List<Staff> staffs) {
        List<AccidentalCandidate> joined = new ArrayList<>(candidates);
        for (int i = 0; i < joined.size(); i++) {
            AccidentalCandidate a = joined.get(i);
            if (a.label != OmrMeasurePostProcessor.CLEF_OR_KEY) continue;
            Staff staff = nearestHeadStaff(staffs, a.component.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            for (int j = i + 1; j < joined.size(); j++) {
                AccidentalCandidate b = joined.get(j);
                if (b.label != a.label) continue;
                Component upper = a.component.minY < b.component.minY ? a.component : b.component;
                Component lower = upper == a.component ? b.component : a.component;
                int separation = lower.minY - upper.maxY - 1;
                int left = Math.max(upper.minX, lower.minX),
                        right = Math.min(upper.maxX, lower.maxX);
                if (separation < 0
                        || separation > gap * .35f
                        // A staff line can separate a flat's thin upper stem from its wider
                        // lower bowl. Require overlap of the narrower fragment; requiring
                        // the bowl's width leaves a truncated glyph that can resemble a sharp.
                        || right - left + 1
                                < Math.min(upper.maxX - upper.minX + 1, lower.maxX - lower.minX + 1)
                                        * .70f
                        || lower.maxY - upper.minY + 1 > gap * 3.65f
                        || Math.max(upper.maxX, lower.maxX) - Math.min(upper.minX, lower.minX) + 1
                                > gap * 1.65f) continue;
                boolean staffCut = false;
                for (int y = upper.maxY; y <= lower.minY; y++)
                    if (rowLabelCount(labels, width, y, left, right, OmrMeasurePostProcessor.STAFF)
                            >= (right - left + 1) * .7f) staffCut = true;
                if (!staffCut) continue;
                int area = upper.area + lower.area;
                Component merged =
                        new Component(
                                area,
                                Math.min(upper.minX, lower.minX),
                                Math.max(upper.maxX, lower.maxX),
                                upper.minY,
                                lower.maxY,
                                (upper.centerX * upper.area + lower.centerX * lower.area) / area,
                                (upper.centerY * upper.area + lower.centerY * lower.area) / area);
                a = new AccidentalCandidate(merged, a.label);
                joined.set(i, a);
                joined.remove(j--);
            }
        }
        return joined;
    }

    private static int countFlatSpines(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            float boundary,
            float right,
            Staff staff,
            boolean doubleBar) {
        return countHeaderFlatSpines(
                labels, gray, width, height, boundary, right, staff, doubleBar, false);
    }

    private static int countHeaderFlatSpines(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            float boundary,
            float right,
            Staff staff,
            boolean doubleBar,
            boolean endpoints) {
        return Math.max(
                countFlatSpines(
                        labels, gray, width, height, boundary, right, staff, doubleBar, false,
                        endpoints),
                countFlatSpines(
                        labels, gray, width, height, boundary, right, staff, doubleBar, true,
                        endpoints));
    }

    private static int countFlatSpines(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            float boundary,
            float right,
            Staff staff,
            boolean doubleBar,
            boolean recover,
            boolean endpoints) {
        int leftX = Math.max(0, Math.round(boundary + (doubleBar ? staff.gap * .42f : 0f)));
        int rightX = Math.min(width - 1, Math.round(right));
        int top = Math.max(0, Math.round(staff.top - staff.gap * 2.25f));
        int bottom = Math.min(height - 1, Math.round(staff.bottom + staff.gap * 2.25f));
        int threshold = Math.max(3, Math.round(staff.gap * 1.55f));
        List<float[]> spines = new ArrayList<>();
        float[] active = null;
        for (int x = leftX; x <= rightX; x++) {
            int pixels = 0,
                    run = 0,
                    longest = 0,
                    blanks = 0,
                    start = top,
                    bestTop = top,
                    bestBottom = top;
            for (int y = top; y <= bottom; y++) {
                byte label = labels[y * width + x];
                boolean symbol =
                        label == OmrMeasurePostProcessor.CLEF_OR_KEY
                                || label == OmrMeasurePostProcessor.SYMBOL;
                if (symbol) pixels++;
                boolean ink = gray == null ? symbol : (gray[y * width + x] & 255) <= 205;
                if (ink) {
                    if (run == 0) start = y;
                    run++;
                    blanks = 0;
                    if (run > longest) {
                        longest = run;
                        bestTop = start;
                        bestBottom = y;
                    }
                } else if (++blanks > 1) run = 0;
            }
            boolean strong =
                    pixels >= threshold
                            && longest >= threshold
                            && !fullStaffRule(gray, width, height, x, staff);
            if (recover
                    && !strong
                    && gray != null
                    && !fullStaffRule(gray, width, height, x, staff)) {
                int length = 0, blank = 0, first = top;
                for (int y = top; y <= bottom; y++) {
                    if ((gray[y * width + x] & 255) <= 165) {
                        if (length == 0) {
                            // A staff-rule pixel alone cannot start a recovered vertical shaft.
                            if (y + 2 >= height
                                    || (gray[(y + 1) * width + x] & 255) > 165
                                    || (gray[(y + 2) * width + x] & 255) > 165) continue;
                            first = y;
                        }
                        length++;
                        blank = 0;
                        if (length >= threshold
                                && y - first >= staff.gap * 1.5f
                                && y - first <= staff.gap * 3.5f
                                && PrintedFlatGlyph.matches(
                                        gray,
                                        width,
                                        height,
                                        Math.max(0, x - 1),
                                        first,
                                        Math.min(width - 1, x + Math.round(staff.gap * .9f)),
                                        y,
                                        staff.gap)) {
                            strong = true;
                            bestTop = first;
                            bestBottom = y;
                            longest = length;
                        }
                    } else if (++blank > Math.max(1, Math.round(staff.gap * .6f))) length = 0;
                }
            }
            if (strong) {
                if (active == null)
                    active =
                            new float[] {
                                x, (bestTop + bestBottom) * .5f, longest, bestBottom, x, bestTop
                            };
                else if (longest > active[2]) {
                    active[0] = x;
                    active[1] = (bestTop + bestBottom) * .5f;
                    active[2] = longest;
                    active[3] = bestBottom;
                    active[5] = bestTop;
                }
            } else if (active != null) {
                spines.add(active);
                active = null;
            }
        }
        if (active != null) spines.add(active);
        if (!spines.isEmpty() && gray != null) {
            float[] edge = spines.get(0);
            if ((endpoints || edge[1] < staff.top)
                    && edge[2] < staff.gap * 2.4f
                    && edge[4] - leftX < staff.gap * .25f
                    && !PrintedFlatGlyph.matches(
                            gray,
                            width,
                            height,
                            Math.max(0, Math.round(edge[0]) - 1),
                            Math.round(edge[5]),
                            Math.min(width - 1, Math.round(edge[0] + staff.gap * .9f)),
                            Math.round(edge[3]),
                            staff.gap)) spines.remove(0);
        }
        if (spines.isEmpty()) return 0;
        // Full flat shafts may pick up an extra rule at their upper end. Their lower
        // endpoints retain the signature pattern; shorter clef/meter strokes do not.
        var full =
                spines.stream()
                        .filter(p -> p[2] >= staff.gap * 2.4f && p[2] <= staff.gap * 3.5f)
                        .collect(java.util.stream.Collectors.toList());
        return Math.max(
                orderedFlatSpines(spines, staff.gap, 1),
                endpoints && full.size() >= 3 ? orderedFlatSpines(full, staff.gap, 3) : 0);
    }

    private static int orderedFlatSpines(List<float[]> spines, float gap, int ordinate) {
        int groups = 1;
        float[] previous = spines.get(0);
        float spacing = 0;
        for (int i = 1; i < spines.size(); i++) {
            float[] next = spines.get(i);
            float dx = next[0] - previous[0];
            // A bowl edge is not another accidental. Keys have separate, closely
            // spaced spines, alternating a fourth up or a fifth down on the page.
            if (dx < gap * .5f) continue;
            // The wider space before a meter must end a well-established flat run.
            if (dx > gap * 1.85f || (groups >= 4 && dx > spacing / (groups - 1) * 1.6f)) break;
            float dy = next[ordinate] - previous[ordinate];
            if (Math.abs(dy + gap * 1.5f) > gap * .55f && Math.abs(dy - gap * 2f) > gap * .55f)
                continue;
            spacing += dx;
            groups++;
            previous = next;
        }
        return groups;
    }

    private static boolean fullStaffRule(byte[] gray, int width, int height, int x, Staff staff) {
        return fullStaffRule(gray, width, height, x, staff, 205);
    }

    private static boolean fullStaffRule(
            byte[] gray, int width, int height, int x, Staff staff, int threshold) {
        if (gray == null) return false;
        int covered = 0, sampled = 0;
        int margin = Math.max(1, Math.round(staff.gap * .14f));
        for (int line = 0; line < 4; line++) {
            int first = Math.max(0, Math.round(staff.top + line * staff.gap) + margin + 1);
            int last =
                    Math.min(
                            height - 1,
                            Math.round(staff.top + (line + 1) * staff.gap) - margin - 1);
            int space = 0;
            for (int y = first; y <= last; y++)
                if ((gray[y * width + x] & 255) <= threshold) space++;
            int count = Math.max(0, last - first + 1);
            if (space < count * .55f) return false;
            covered += space;
            sampled += count;
        }
        return sampled > 0 && covered >= sampled * .90f;
    }

    private static List<SignatureGlyph> densestSignatureRun(
            List<SignatureGlyph> glyphs, float gap) {
        List<SignatureGlyph> best = List.of();
        for (int start = 0; start < glyphs.size(); start++) {
            List<SignatureGlyph> current = new ArrayList<>();
            current.add(glyphs.get(start));
            for (int index = start + 1; index < glyphs.size(); index++) {
                SignatureGlyph previous = current.get(current.size() - 1);
                SignatureGlyph next = glyphs.get(index);
                // Stacked local accidentals belong to chord pitches, not to a
                // horizontally ordered key signature.
                if (next.x - previous.x < gap * .5f) continue;
                if (next.x - previous.x > gap * 1.85f || next.x - current.get(0).x > gap * 7.8f)
                    break;
                current.add(next);
            }
            if (current.size() > best.size()) best = current;
        }
        return best;
    }

    private static boolean hasDoubleBar(
            byte[] labels, byte[] gray, int width, int height, float boundaryX, Staff staff) {
        int left = Math.max(0, Math.round(boundaryX - staff.gap * 1.1f));
        int right = Math.min(width - 1, Math.round(boundaryX + staff.gap * 1.1f));
        int top = Math.max(0, Math.round(staff.top - staff.gap * .3f));
        int bottom = Math.min(height - 1, Math.round(staff.bottom + staff.gap * .3f));
        int groups = 0;
        boolean previous = false;
        // Two unrelated shafts spanning the search window are not a double bar.
        int lastStrong = -10000;
        for (int x = left; x <= right; x++) {
            int ink = 0;
            for (int y = top; y <= bottom; y++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST
                        || gray != null && (gray[y * width + x] & 0xff) < 180) ink++;
            boolean strong = ink >= (bottom - top + 1) * .72f;
            if (strong && !previous) groups = x - lastStrong <= staff.gap * .6f ? groups + 1 : 1;
            if (strong) lastStrong = x;
            if (groups >= 2) return true;
            previous = strong;
        }
        if (groups >= 2) return true;
        // Thin faded bars can lose their semantic stem labels. Require two
        // separate raw columns crossing every staff space, not just the rules
        // or the shorter parallel spines of a sharp.
        for (int threshold : new int[] {205, 225}) {
            groups = 0;
            previous = false;
            lastStrong = -10000;
            for (int x = left; x <= right; x++) {
                boolean strong = fullStaffRule(gray, width, height, x, staff, threshold);
                if (strong && !previous)
                    groups = x - lastStrong <= staff.gap * .6f ? groups + 1 : 1;
                if (strong) lastStrong = x;
                if (groups >= 2) return true;
                previous = strong;
            }
            if (groups >= 2) return true;
        }
        return false;
    }

    private static void logHeadCoverage(
            List<Staff> staffs,
            List<Component> rawComponents,
            List<Component> splitComponents,
            List<Component> accepted,
            List<Component> demoted,
            List<DetectedNote> emitted) {
        int[] rawByStaff = new int[staffs.size()];
        int[] splitByStaff = new int[staffs.size()];
        int[] acceptedByStaff = new int[staffs.size()];
        int[] demotedByStaff = new int[staffs.size()];
        int[] emittedByStaff = new int[staffs.size()];
        int[] tallByStaff = new int[staffs.size()];
        float[] maxHeightByStaff = new float[staffs.size()];
        for (Component component : rawComponents)
            incrementNearest(staffs, rawByStaff, component.centerY);
        for (Component component : splitComponents)
            incrementNearest(staffs, splitByStaff, component.centerY);
        for (Component component : rawComponents) {
            Staff nearest = nearestStaff(staffs, component.centerY);
            int index = nearest == null ? -1 : staffs.indexOf(nearest);
            if (index < 0) continue;
            float ratio = (component.maxY - component.minY + 1f) / nearest.gap;
            if (ratio >= .90f) tallByStaff[index]++;
            maxHeightByStaff[index] = Math.max(maxHeightByStaff[index], ratio);
        }
        for (Component component : accepted)
            incrementNearest(staffs, acceptedByStaff, component.centerY);
        for (Component component : demoted)
            incrementNearest(staffs, demotedByStaff, component.centerY);
        for (DetectedNote note : emitted)
            incrementNearest(staffs, emittedByStaff, note.head.centerY);
        try {
            Diagnostics.log(
                    "Head coverage raw="
                            + java.util.Arrays.toString(rawByStaff)
                            + " split="
                            + java.util.Arrays.toString(splitByStaff)
                            + " accepted="
                            + java.util.Arrays.toString(acceptedByStaff)
                            + " demotedDots="
                            + java.util.Arrays.toString(demotedByStaff)
                            + " emitted="
                            + java.util.Arrays.toString(emittedByStaff)
                            + " tall="
                            + java.util.Arrays.toString(tallByStaff)
                            + " maxHeight="
                            + java.util.Arrays.toString(maxHeightByStaff));
        } catch (RuntimeException ignored) {
            // Logging may be unavailable in a plain JVM test environment.
        }
    }

    private static void incrementNearest(List<Staff> staffs, int[] counts, float centerY) {
        Staff nearest = nearestStaff(staffs, centerY);
        int index = nearest == null ? -1 : staffs.indexOf(nearest);
        if (index >= 0) counts[index]++;
    }

    /** HOMR occasionally joins the two vertically aligned ovals of a printed dyad into one
     * NOTEHEAD component. Split only an abnormally tall component with two strong ink lobes and
     * a real valley between them; ordinary single heads, dots, and solid artifacts stay intact. */
    private static List<Component> splitStackedHeads(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Component> source,
            List<Staff> staffs) {
        List<Component> result = new ArrayList<>();
        for (Component component : source) {
            Staff staff = nearestHeadStaff(staffs, component.centerY);
            float componentHeight = component.maxY - component.minY + 1f;
            List<Component> wholeSeconds =
                    splitWholeSeconds(labels, gray, width, height, component, staff);
            if (!wholeSeconds.isEmpty()) {
                result.addAll(wholeSeconds);
                continue;
            }
            List<Component> run =
                    splitRepeatedHeadRun(labels, gray, width, height, component, staff);
            if (!run.isEmpty()) {
                result.addAll(run);
                continue;
            }
            List<Component> attacks =
                    splitJoinedChordAttacks(labels, gray, width, height, component, staff);
            if (!attacks.isEmpty()) {
                result.addAll(splitStackedHeads(labels, gray, width, height, attacks, staffs));
                continue;
            }
            List<Component> filledChord =
                    splitFilledSecondChord(labels, gray, width, height, component, staff, 0);
            // Standalone seconds retain their joint ledger validation below.
            if (filledChord.size() >= 3) {
                result.addAll(filledChord);
                continue;
            }
            List<Component> filled =
                    splitTouchingFilledVoices(labels, gray, width, height, component, staff);
            if (!filled.isEmpty()) {
                result.addAll(filled);
                continue;
            }
            List<Component> mixed =
                    splitMixedUnisonStack(labels, gray, width, height, component, staff);
            if (!mixed.isEmpty()) {
                result.addAll(mixed);
                continue;
            }
            List<Component> regular = splitRegularStack(labels, width, component, staff);
            if (!regular.isEmpty()) {
                result.addAll(regular);
                continue;
            }
            List<Component> hollow =
                    splitHollowStack(labels, gray, width, height, component, staff);
            if (!hollow.isEmpty()) {
                result.addAll(hollow);
                continue;
            }
            if (staff == null
                    || componentHeight < staff.gap * 1.45f
                    || componentHeight > staff.gap * 3.2f) {
                List<Component> displaced =
                        staff != null && componentHeight > staff.gap * 3.2f
                                ? splitHollowSecondChord(
                                        labels, gray, width, height, component, staff, 0)
                                : List.of();
                if (displaced.isEmpty()) result.add(component);
                else result.addAll(displaced);
                continue;
            }
            int inset = Math.max(2, Math.round(staff.gap * .42f));
            int firstSplit = component.minY + inset;
            int lastSplit = component.maxY - inset;
            int split = -1, valley = Integer.MAX_VALUE;
            int upperPeak = 0, lowerPeak = 0;
            int[] rowInk = new int[component.maxY - component.minY + 1];
            for (int y = component.minY; y <= component.maxY; y++) {
                int count = 0;
                for (int x = component.minX; x <= component.maxX; x++)
                    if (labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) count++;
                rowInk[y - component.minY] = count;
            }
            for (int y = firstSplit; y <= lastSplit; y++) {
                int count = rowInk[y - component.minY];
                if (count < valley) {
                    valley = count;
                    split = y;
                }
            }
            if (split >= 0) {
                for (int y = component.minY; y < split; y++)
                    upperPeak = Math.max(upperPeak, rowInk[y - component.minY]);
                for (int y = split + 1; y <= component.maxY; y++)
                    lowerPeak = Math.max(lowerPeak, rowInk[y - component.minY]);
            }
            Component upper =
                    split < 0
                            ? null
                            : componentSlice(labels, width, component, component.minY, split);
            Component lower =
                    split < 0
                            ? null
                            : componentSlice(labels, width, component, split + 1, component.maxY);
            boolean twoLobes =
                    upper != null
                            && lower != null
                            && upperPeak >= Math.max(2, Math.round(staff.gap * .24f))
                            && lowerPeak >= Math.max(2, Math.round(staff.gap * .24f))
                            && valley <= Math.min(upperPeak, lowerPeak) * .82f
                            && plausibleHead(upper, staff.gap)
                            && plausibleHead(lower, staff.gap)
                            && lower.centerY - upper.centerY >= staff.gap * .58f
                            && lower.centerY - upper.centerY <= staff.gap * 2.25f
                            && Math.abs(lower.centerX - upper.centerX) <= staff.gap * 1.45f;
            if (!twoLobes
                    && gray != null
                    && componentHeight >= staff.gap * 1.7f
                    && componentHeight <= staff.gap * 2.6f
                    && component.maxX - component.minX + 1 <= staff.gap * 1.7f) {
                // A semantic bridge can fill the neck between two solid heads.
                // The printed silhouette must independently contain two lobes.
                // A darker core can reveal the neck when pale edge ink joins it.
                for (int inkThreshold : new int[] {165, 120}) {
                    int[] rawRows = new int[rowInk.length];
                    for (int y = component.minY; y <= component.maxY; y++)
                        for (int x = component.minX; x <= component.maxX; x++)
                            if ((gray[y * width + x] & 255) <= inkThreshold)
                                rawRows[y - component.minY]++;
                    int neck = -1, minimum = Integer.MAX_VALUE;
                    for (int y = firstSplit; y <= lastSplit; y++)
                        if (rawRows[y - component.minY] < minimum) {
                            minimum = rawRows[y - component.minY];
                            neck = y;
                        }
                    int peakAbove = 0, peakBelow = 0;
                    for (int y = component.minY; y < neck; y++)
                        peakAbove = Math.max(peakAbove, rawRows[y - component.minY]);
                    for (int y = neck + 1; y <= component.maxY; y++)
                        peakBelow = Math.max(peakBelow, rawRows[y - component.minY]);
                    if (inkThreshold < 165) {
                        int upperCore = 0, lowerCore = 0, coreRun = 0;
                        for (int y = component.minY; y < neck; y++) {
                            coreRun =
                                    rawRows[y - component.minY] >= peakAbove * .6f
                                            ? coreRun + 1
                                            : 0;
                            upperCore = Math.max(upperCore, coreRun);
                        }
                        coreRun = 0;
                        for (int y = neck + 1; y <= component.maxY; y++) {
                            coreRun =
                                    rawRows[y - component.minY] >= peakBelow * .6f
                                            ? coreRun + 1
                                            : 0;
                            lowerCore = Math.max(lowerCore, coreRun);
                        }
                        // Isolated dark rules or strokes cannot supply filled oval cores.
                        if (upperCore < staff.gap * .35f || lowerCore < staff.gap * .35f) continue;
                    }
                    Component a =
                            neck < 0
                                    ? null
                                    : componentSlice(
                                            labels, width, component, component.minY, neck);
                    Component b =
                            neck < 0
                                    ? null
                                    : componentSlice(
                                            labels, width, component, neck + 1, component.maxY);
                    if (a != null
                            && b != null
                            && minimum <= Math.min(peakAbove, peakBelow) * .78f
                            && peakAbove >= staff.gap * .65f
                            && peakBelow >= staff.gap * .65f
                            && plausibleHead(a, staff.gap)
                            && plausibleHead(b, staff.gap)
                            && !hasOpenCenter(labels, gray, width, height, a, staff.gap)
                            && !hasOpenCenter(labels, gray, width, height, b, staff.gap)
                            && b.centerY - a.centerY >= staff.gap * .58f
                            && b.centerY - a.centerY <= staff.gap * 2.25f
                            && Math.abs(a.centerX - b.centerX) <= staff.gap * 1.45f) {
                        upper = a;
                        lower = b;
                        twoLobes = true;
                        break;
                    }
                }
            }
            if (!twoLobes
                    && gray != null
                    && componentHeight >= staff.gap * 1.7f
                    && componentHeight <= staff.gap * 2.6f
                    && component.maxX - component.minX + 1 <= staff.gap * 1.8f) {
                // The model can fill both hollow ovals and their connecting ledger line into
                // one solid mask. Two separate white centres in the original engraving are
                // stronger evidence than a missing valley in that semantic mask.
                int middle = (component.minY + component.maxY) / 2;
                Component a = componentSlice(labels, width, component, component.minY, middle);
                Component b = componentSlice(labels, width, component, middle + 1, component.maxY);
                if (a != null
                        && b != null
                        && plausibleHead(a, staff.gap)
                        && plausibleHead(b, staff.gap)
                        && b.centerY - a.centerY >= staff.gap * .75f
                        && ((hasOpenCenter(labels, gray, width, height, a, staff.gap)
                                        && hasOpenCenter(labels, gray, width, height, b, staff.gap))
                                || mixedStemmedVerticalVoices(
                                        labels, gray, width, height, a, b, staff.gap))) {
                    upper = a;
                    lower = b;
                    twoLobes = true;
                }
            }
            if (twoLobes) {
                result.add(upper);
                result.add(lower);
            } else result.add(component);
        }
        return List.copyOf(result);
    }

    private static boolean mixedStemmedVerticalVoices(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component a,
            Component b,
            float gap) {
        boolean aOpen = hasOpenCenter(labels, gray, width, height, a, gap);
        boolean bOpen = hasOpenCenter(labels, gray, width, height, b, gap);
        if (aOpen == bOpen
                || !hasAttachedStem(labels, width, height, a, gap)
                || !hasAttachedStem(labels, width, height, b, gap)) return false;
        Component hollow = aOpen ? a : b, filled = aOpen ? b : a;
        if (!hasRawUnisonHollowCore(gray, width, height, hollow)) return false;
        int ink = 0, total = 0;
        for (int y = Math.round(filled.centerY - gap * .2f);
                y <= Math.round(filled.centerY + gap * .2f);
                y++)
            for (int x = Math.round(filled.centerX - gap * .25f);
                    x <= Math.round(filled.centerX + gap * .25f);
                    x++) {
                if (x < 0 || x >= width || y < 0 || y >= height) return false;
                total++;
                if ((gray[y * width + x] & 255) < 165) ink++;
            }
        return total >= 8 && ink >= total * .9f;
    }

    /** Thin semantic bridges can join a tightly engraved repeated run. Require
     * distinct raw oval lobes and an attached stem for every recovered attack. */
    private static List<Component> splitRepeatedHeadRun(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        if (gray == null || staff == null) return List.of();
        float gap = staff.gap;
        int w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1;
        if (w < gap * 3.2f || h < gap * .65f || h > gap * 1.4f) return List.of();
        int[] columns = new int[w];
        for (int x = 0; x < w; x++)
            for (int y = head.minY; y <= head.maxY; y++)
                if ((gray[y * width + head.minX + x] & 255) <= 165) columns[x]++;
        List<Integer> cuts = new ArrayList<>();
        for (int x = 0; x < w; ) {
            if (columns[x] > h * .35f) {
                x++;
                continue;
            }
            int start = x;
            while (x < w && columns[x] <= h * .35f) x++;
            if (start > 0 && x < w) cuts.add(head.minX + (start + x - 1) / 2);
        }
        if (cuts.size() < 2) return List.of();
        cuts.add(head.maxX);
        List<Component> result = new ArrayList<>();
        int left = head.minX;
        for (int right : cuts) {
            Component part = horizontalHeadSlice(labels, width, head, left, right);
            left = right + 1;
            if (part == null
                    || !plausibleHead(part, gap)
                    || part.maxX - part.minX + 1 < gap * .8f
                    || part.maxX - part.minX + 1 > gap * 1.9f
                    || part.area < gap * gap * .4f
                    || Math.abs(part.centerY - head.centerY) > gap * .25f
                    || hasOpenCenter(labels, gray, width, height, part, gap)
                    || attachedRawStem(gray, width, height, part, gap) == null) return List.of();
            int peak = 0;
            for (int x = part.minX; x <= part.maxX; x++)
                peak = Math.max(peak, columns[x - head.minX]);
            if (peak < h * .7f) return List.of();
            result.add(part);
        }
        return List.copyOf(result);
    }

    /** A wide mask may bridge two close chord attacks. Separate only two
     * independently printed shafts whose pieces each contain proved ovals. */
    private static List<Component> splitJoinedChordAttacks(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        if (gray == null || staff == null) return List.of();
        float gap = staff.gap;
        int w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1;
        if (w < gap * 3.2f || w > gap * 7 || h < gap * 1.6f || h > gap * 4.4f) return List.of();
        for (int direction : new int[] {-1, 1}) {
            List<Integer> shafts = new ArrayList<>();
            for (int x = head.minX; x <= head.maxX; x++) {
                int ink = 0, total = 0;
                for (int d = 1; d <= gap * 1.8f; d++) {
                    int y = (direction < 0 ? head.minY : head.maxY) + direction * d;
                    if (y < 0 || y >= height) break;
                    total++;
                    if ((gray[y * width + x] & 255) < 170) ink++;
                }
                if (total < gap * 1.6f || ink < total * .9f) continue;
                if (shafts.isEmpty() || x - shafts.get(shafts.size() - 1) > gap * .3f)
                    shafts.add(x);
            }
            for (int i = 1; i < shafts.size(); i++) {
                int a = shafts.get(i - 1), b = shafts.get(i);
                if (b - a < gap * 2 || b - a > gap * 5) continue;
                int cut = (a + b) / 2;
                Component left = horizontalHeadSlice(labels, width, head, head.minX, cut);
                Component right = horizontalHeadSlice(labels, width, head, cut + 1, head.maxX);
                List<Component> first =
                        provedFilledChordPieces(labels, gray, width, height, left, staff);
                List<Component> second =
                        provedFilledChordPieces(labels, gray, width, height, right, staff);
                if (!first.isEmpty() && !second.isEmpty()) {
                    List<Component> result = new ArrayList<>(first);
                    result.addAll(second);
                    return result;
                }
            }
        }
        return List.of();
    }

    private static List<Component> provedFilledChordPieces(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        if (head == null) return List.of();
        for (int trim = 0; trim <= Math.round(staff.gap * .5f); trim++)
            for (boolean top : new boolean[] {false, true}) {
                Component candidate =
                        componentSlice(
                                labels,
                                width,
                                head,
                                head.minY + (top ? trim : 0),
                                head.maxY - (top ? 0 : trim));
                if (candidate == null) continue;
                List<Component> parts = splitRegularStack(labels, width, candidate, staff);
                if (parts.isEmpty()) parts = sideBySideSeconds(labels, width, candidate, staff.gap);
                if (parts.size() < 2) continue;
                boolean valid = true;
                List<Component> trimmed = new ArrayList<>();
                for (Component p : parts) {
                    Component oval = trimFilledLobe(labels, gray, width, p, staff.gap);
                    if (hasOpenCenter(labels, gray, width, height, p, staff.gap)
                            || !printedFilledOval(gray, width, oval, staff.gap)) {
                        valid = false;
                        break;
                    }
                    trimmed.add(oval);
                }
                if (valid) return trimmed;
            }
        return List.of();
    }

    /** A narrow raw-ink neck and opposing stems identify two touching filled voices.
     * Split before event decoding so each head retains its own position and duration. */
    private static List<Component> splitTouchingFilledVoices(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        if (gray == null || staff == null) return List.of();
        float gap = staff.pitchGap;
        int w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1;
        if (w < gap * 1.8f || w > gap * 3.1f || h < gap * .65f || h > gap * 1.4f) return List.of();
        int middle = (head.minX + head.maxX) / 2;
        Component left = horizontalHeadSlice(labels, width, head, head.minX, middle);
        Component right = horizontalHeadSlice(labels, width, head, middle + 1, head.maxX);
        if (left == null
                || right == null
                || !plausibleHead(left, gap)
                || !plausibleHead(right, gap)
                || Math.abs(left.centerY - right.centerY) > gap * .3f
                || left.area < gap * gap * .35f
                || right.area < gap * gap * .35f
                || hasOpenCenter(labels, gray, width, height, left, gap)
                || hasOpenCenter(labels, gray, width, height, right, gap)) return List.of();
        int leftUp = attachedStemReach(labels, width, height, left, gap, true);
        int leftDown = attachedStemReach(labels, width, height, left, gap, false);
        int rightUp = attachedStemReach(labels, width, height, right, gap, true);
        int rightDown = attachedStemReach(labels, width, height, right, gap, false);
        int minimum = Math.max(3, Math.round(gap * .72f));
        boolean opposing =
                (leftDown >= minimum
                                && rightUp >= minimum
                                && leftUp < minimum
                                && rightDown < minimum)
                        || (leftUp >= minimum
                                && rightDown >= minimum
                                && leftDown < minimum
                                && rightUp < minimum);
        if (!opposing) return List.of();
        int leftPeak = 0, rightPeak = 0, neck = h;
        for (int x = head.minX; x <= head.maxX; x++) {
            int ink = 0;
            for (int y = head.minY; y <= head.maxY; y++)
                if ((gray[y * width + x] & 255) < 165) ink++;
            if (x < middle) leftPeak = Math.max(leftPeak, ink);
            if (x > middle) rightPeak = Math.max(rightPeak, ink);
            if (Math.abs(x - middle) <= Math.max(1, Math.round(gap * .15f)))
                neck = Math.min(neck, ink);
        }
        if (Math.min(leftPeak, rightPeak) < gap * .65f
                || neck > Math.min(leftPeak, rightPeak) * .6f) return List.of();
        return List.of(left, right);
    }

    /** A filled voice can touch two hollow chord heads on the opposite side
     * of the stem. Require the two printed open centres before splitting this
     * unusually wide component; an ordinary filled chord is not sufficient. */
    private static List<Component> splitMixedUnisonStack(
            byte[] labels, byte[] gray, int width, int height, Component source, Staff staff) {
        if (gray == null || staff == null) return List.of();
        float gap = staff.gap;
        int w = source.maxX - source.minX + 1, h = source.maxY - source.minY + 1;
        if (w < gap * 2.6f || w > gap * 3.6f || h < gap * 1.8f || h > gap * 2.7f) return List.of();
        int middle = source.minX + Math.round(gap * 1.45f);
        Component left = horizontalHeadSlice(labels, width, source, source.minX, middle);
        Component right = horizontalHeadSlice(labels, width, source, middle + 1, source.maxX);
        if (left == null
                || right == null
                || !plausibleHead(left, gap)
                || left.maxY - left.minY > gap * 1.4f
                || hasOpenCenter(labels, gray, width, height, left, gap)
                || !hasAttachedStem(labels, width, height, left, gap)) return List.of();
        List<Component> parts = splitRegularStack(labels, width, right, staff);
        if (parts.size() != 2) return List.of();
        for (Component part : parts)
            if (!hasOpenCenter(labels, gray, width, height, part, gap)) return List.of();
        if (parts.stream().noneMatch(part -> Math.abs(part.centerY - left.centerY) < gap * .3f))
            return List.of();
        return List.of(left, parts.get(0), parts.get(1));
    }

    /** Follow every broad oval lobe, rather than choosing the single darkest neck.
     * A filled-in ledger bridge can move that neck inside a hollow head, and a
     * triad has two necks. Staff-spaced broad lobes establish the actual centres. */
    private static List<Component> splitRegularStack(
            byte[] labels, int width, Component component, Staff staff) {
        if (staff == null) return List.of();
        float gap = staff.gap;
        int height = component.maxY - component.minY + 1;
        if (height < gap * 1.7f
                || height > gap * 4.4f
                || component.maxX - component.minX + 1 > gap * 1.8f) return List.of();
        int[] rows = new int[height];
        int peak = 0;
        for (int y = 0; y < height; y++) {
            for (int x = component.minX; x <= component.maxX; x++)
                if (labels[(component.minY + y) * width + x] == OmrMeasurePostProcessor.NOTEHEAD)
                    rows[y]++;
            peak = Math.max(peak, rows[y]);
        }
        if (peak < gap * .75f) return List.of();
        List<Float> centers = new ArrayList<>();
        for (int y = 0; y < height; ) {
            if (rows[y] < peak * .85f) {
                y++;
                continue;
            }
            int start = y;
            while (y < height && rows[y] >= peak * .85f) y++;
            // A one-row contour island is not another oval lobe.
            if (y - start < Math.max(2, Math.round(gap * .20f))) continue;
            centers.add(component.minY + (start + y - 1) * .5f);
        }
        if (centers.size() < 2 || centers.size() > 4) return List.of();
        for (int i = 1; i < centers.size(); i++) {
            float separation = centers.get(i) - centers.get(i - 1);
            if (separation < gap * .75f || separation > gap * 1.4f) return List.of();
        }
        List<Component> parts = new ArrayList<>();
        int top = component.minY;
        for (int i = 0; i < centers.size(); i++) {
            int bottom =
                    i + 1 == centers.size()
                            ? component.maxY
                            : Math.round((centers.get(i) + centers.get(i + 1)) * .5f);
            Component part = componentSlice(labels, width, component, top, bottom);
            if (part == null
                    || !plausibleHead(part, gap)
                    || Math.abs(part.centerY - centers.get(i)) > gap * .3f) return List.of();
            parts.add(part);
            top = bottom + 1;
        }
        return parts;
    }

    /** A displaced second can connect a complete filled chord into one wide mask. */
    private static List<Component> splitFilledSecondChord(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            int depth) {
        if (gray == null || staff == null || depth > 4) return List.of();
        float gap = staff.pitchGap;
        if (head.maxX - head.minX + 1 < gap * 2.1f || head.maxX - head.minX + 1 > gap * 3.2f)
            return List.of();
        List<Component> seconds = sideBySideSeconds(labels, width, head, gap);
        if (!seconds.isEmpty()) {
            // Do not trim a genuine hollow oval down to its dark lower rim.
            if (seconds.stream()
                    .anyMatch(
                            p ->
                                    hasOpenCenter(labels, gray, width, height, p, gap)
                                            && hasRawUnisonHollowCore(gray, width, height, p)))
                return List.of();
            List<Component> trimmed = new ArrayList<>();
            for (Component part : seconds)
                trimmed.add(trimFilledLobe(labels, gray, width, part, gap));
            seconds = trimmed;
        }
        if (!seconds.isEmpty()
                && seconds.stream().allMatch(p -> printedFilledOval(gray, width, p, gap))) {
            // Require the actual common shaft, not just two nearby dark blobs.
            int left = Math.max(seconds.get(0).minX, seconds.get(1).minX) - 2;
            int right = Math.min(seconds.get(0).maxX, seconds.get(1).maxX) + 2;
            for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++)
                for (int direction : new int[] {-1, 1}) {
                    int ink = 0, total = 0;
                    for (int d = 1; d <= gap * 1.8f; d++) {
                        int y = (direction < 0 ? head.minY : head.maxY) + direction * d;
                        if (y < 0 || y >= height) break;
                        total++;
                        if ((gray[y * width + x] & 255) < 165) ink++;
                    }
                    if (total >= gap * 1.6f && ink >= total * .9f) return seconds;
                }
            return List.of();
        }
        int span = head.maxY - head.minY + 1;
        if (span < gap * 2.2f || span > gap * 6) return List.of();
        for (boolean top : new boolean[] {true, false}) {
            int edge = top ? head.minY + Math.round(gap) - 1 : head.maxY - Math.round(gap) + 1;
            Component outer =
                    componentSlice(
                            labels, width, head, top ? head.minY : edge, top ? edge : head.maxY);
            Component rest =
                    componentSlice(
                            labels,
                            width,
                            head,
                            top ? edge + 1 : head.minY,
                            top ? head.maxY : edge - 1);
            if (outer == null
                    || rest == null
                    || !plausibleHead(outer, gap)
                    || outer.maxX - outer.minX + 1 > gap * 1.8f
                    || !printedFilledOval(gray, width, outer, gap)) continue;
            List<Component> tail =
                    splitFilledSecondChord(labels, gray, width, height, rest, staff, depth + 1);
            if (tail.isEmpty()) continue;
            List<Component> result = new ArrayList<>(tail);
            result.add(outer);
            result.sort(Comparator.comparingDouble(Component::centerY));
            return result;
        }
        return List.of();
    }

    private static Component trimFilledLobe(
            byte[] labels, byte[] gray, int width, Component head, float gap) {
        int h = head.maxY - head.minY + 1, w = head.maxX - head.minX + 1;
        int[] rows = new int[h];
        for (int y = 0; y < h; y++)
            for (int x = head.minX; x <= head.maxX; x++)
                if ((gray[(head.minY + y) * width + x] & 255) < 165) rows[y]++;
        int bestStart = -1, bestEnd = -1;
        for (int y = 0; y < h; ) {
            if (rows[y] < w * .48f) {
                y++;
                continue;
            }
            int start = y;
            while (y < h && rows[y] >= w * .48f) y++;
            if (y - start >= gap * .4f
                    && y - start <= gap * 1.25f
                    && y - start > bestEnd - bestStart) {
                bestStart = start;
                bestEnd = y;
            }
        }
        if (bestStart < 0) return head;
        Component trimmed =
                componentSlice(
                        labels,
                        width,
                        head,
                        Math.max(head.minY, head.minY + bestStart - 2),
                        Math.min(head.maxY, head.minY + bestEnd + 1));
        return trimmed == null ? head : trimmed;
    }

    private static boolean printedFilledOval(byte[] gray, int width, Component head, float gap) {
        int w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1;
        if (w < gap * .8f || w > gap * 1.8f || h < gap * .55f || h > gap * 1.3f) return false;
        int[] rows = new int[h];
        int total = 0, peak = 0;
        for (int y = 0; y < h; y++)
            for (int x = head.minX; x <= head.maxX; x++)
                if ((gray[(head.minY + y) * width + x] & 255) < 165) {
                    rows[y]++;
                    total++;
                    peak = Math.max(peak, rows[y]);
                }
        int edge = Math.max(0, Math.round(h * .12f));
        return total >= w * h * .48f
                && peak >= w * .75f
                && Math.min(rows[edge], rows[h - 1 - edge]) <= Math.round(peak * .86f);
    }

    /** Peel independently open outer ovals until a displaced hollow second remains. */
    private static List<Component> splitHollowSecondChord(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            int depth) {
        if (gray == null || staff == null || depth > 4) return List.of();
        float gap = staff.pitchGap;
        if (head.maxX - head.minX + 1 < gap * 2.1f || head.maxX - head.minX + 1 > gap * 3.2f)
            return List.of();
        List<Component> seconds = sideBySideSeconds(labels, width, head, gap);
        if (!seconds.isEmpty()
                && seconds.stream()
                        .allMatch(p -> printedOpenOval(labels, gray, width, height, p, gap)))
            return seconds;
        int span = head.maxY - head.minY + 1;
        if (span < gap * 2.2f || span > gap * 6) return List.of();
        for (boolean top : new boolean[] {true, false}) {
            int edge = top ? head.minY + Math.round(gap) - 1 : head.maxY - Math.round(gap) + 1;
            Component outer =
                    componentSlice(
                            labels, width, head, top ? head.minY : edge, top ? edge : head.maxY);
            Component rest =
                    componentSlice(
                            labels,
                            width,
                            head,
                            top ? edge + 1 : head.minY,
                            top ? head.maxY : edge - 1);
            if (outer == null
                    || rest == null
                    || !plausibleHead(outer, gap)
                    || outer.maxX - outer.minX + 1 > gap * 1.8f
                    || !printedOpenOval(labels, gray, width, height, outer, gap)) continue;
            List<Component> tail =
                    splitHollowSecondChord(labels, gray, width, height, rest, staff, depth + 1);
            if (tail.isEmpty()) continue;
            List<Component> result = new ArrayList<>(tail);
            result.add(outer);
            result.sort(Comparator.comparingDouble(Component::centerY));
            return result;
        }
        return List.of();
    }

    private static boolean printedOpenOval(
            byte[] labels, byte[] gray, int width, int height, Component head, float gap) {
        if (!hasOpenCenter(labels, gray, width, height, head, gap)) return false;
        int ink = 0, samples = 0;
        for (int y = Math.max(0, head.minY); y <= Math.min(height - 1, head.maxY); y++)
            for (int x = Math.max(0, head.minX); x <= Math.min(width - 1, head.maxX); x++) {
                samples++;
                if ((gray[y * width + x] & 255) < 165) ink++;
            }
        // A white semantic oval is not a printed note. Staff/stem ink alone is sparse.
        return samples > 0 && ink >= samples * .22f;
    }

    /** Ledger paint can hide a middle lobe of a hollow triad in the mask. Accept
     * equal slices only when every slice contains an independently printed open oval. */
    private static List<Component> splitHollowStack(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        if (gray == null || staff == null) return List.of();
        float gap = staff.pitchGap;
        int span = head.maxY - head.minY + 1;
        int count = Math.round(span / gap);
        if (count < 3
                || count > 4
                || span < gap * (count - .25f)
                || span > gap * (count + .4f)
                || head.maxX - head.minX + 1 > gap * 2.2f) return List.of();
        List<Component> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int top = head.minY + Math.round(i * span / (float) count);
            int bottom = head.minY + Math.round((i + 1) * span / (float) count) - 1;
            Component part = componentSlice(labels, width, head, top, bottom);
            if (part == null
                    || !plausibleHead(part, gap)
                    || part.maxX - part.minX + 1 < gap * .75f
                    || !hasOpenCenter(labels, gray, width, height, part, gap)) return List.of();
            if (!parts.isEmpty()) {
                float separation = part.centerY - parts.get(parts.size() - 1).centerY;
                if (separation < gap * .75f || separation > gap * 1.4f) return List.of();
            }
            parts.add(part);
        }
        return parts;
    }

    /** Dots beyond the right-hand held voice do not lengthen its beamed unison. */
    private static boolean hasHollowUnisonToRight(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Component> heads,
            float gap) {
        if (gray == null) return false;
        for (Component other : heads)
            if (other.centerX - head.centerX >= gap
                    && other.centerX - head.centerX <= gap * 2
                    && Math.abs(other.centerY - head.centerY) < gap * .3f
                    && hasAttachedStem(labels, width, height, other, gap)
                    && hasOpenCenter(labels, gray, width, height, other, gap)) return true;
        return false;
    }

    private static List<Component> sideBySideUnison(
            byte[] labels, byte[] gray, int width, int height, Component head, float gap) {
        float w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1;
        // Compare component bounds on their integer-pixel grid when staff spacing is fractional.
        if (gray == null
                || w < gap * 1.8f
                || w > Math.round(gap * 3.1f)
                || h < gap * .65f
                || h > gap * 1.4f) return List.of();
        int middle = (head.minX + head.maxX) / 2;
        Component left = horizontalHeadSlice(labels, width, head, head.minX, middle);
        Component right = horizontalHeadSlice(labels, width, head, middle + 1, head.maxX);
        if (left == null
                || right == null
                || Math.abs(left.centerY - right.centerY) > gap * .3f
                || left.area < gap * gap * .35f
                || right.area < gap * gap * .3f
                || !hasAttachedStem(labels, width, height, left, gap)
                || !hasAttachedStem(labels, width, height, right, gap)) return List.of();
        boolean leftOpen = hasOpenCenter(labels, gray, width, height, left, gap);
        boolean rightOpen = hasOpenCenter(labels, gray, width, height, right, gap);
        if (leftOpen == rightOpen) return List.of();
        Component hollow = leftOpen ? left : right, filled = leftOpen ? right : left;
        if (!hasRawUnisonHollowCore(gray, width, height, hollow)) return List.of();
        return List.of(filled, hollow);
    }

    /** A white notch above a filled head is not the interior of a second, held oval. */
    private static boolean hasRawUnisonHollowCore(
            byte[] gray, int width, int height, Component head) {
        int rx = Math.max(1, Math.round((head.maxX - head.minX + 1) * .18f));
        int ry = Math.max(1, Math.round((head.maxY - head.minY + 1) * .1f));
        int cx = Math.round(head.centerX), cy = Math.round(head.centerY);
        int bright = 0, samples = 0;
        for (int y = Math.max(head.minY, cy - ry); y <= Math.min(head.maxY, cy + ry); y++)
            for (int x = Math.max(head.minX, cx - rx); x <= Math.min(head.maxX, cx + rx); x++) {
                if (x < 0 || x >= width || y < 0 || y >= height) continue;
                samples++;
                if ((gray[y * width + x] & 255) >= 185) bright++;
            }
        if (samples >= 3 && bright >= Math.max(2, Math.round(samples * .34f))) return true;
        // A printed staff rule can fill the centre of an otherwise open oval.
        // Require separate white pockets on both sides of that dark rule;
        // a filled note with only an upper white notch still fails.
        int innerLeft = Math.max(head.minX, cx - rx), innerRight = Math.min(head.maxX, cx + rx);
        // The semantic oval centroid can round one pixel below a two-pixel
        // staff rule. Centre the two-pocket proof on the actual dark rule.
        int bestRule = 0, ruleY = cy;
        for (int y = Math.max(head.minY, cy - 1); y <= Math.min(head.maxY, cy + 1); y++) {
            int dark = 0;
            for (int x = innerLeft; x <= innerRight; x++)
                if ((gray[y * width + x] & 255) < 130) dark++;
            if (dark > bestRule) {
                bestRule = dark;
                ruleY = y;
            }
        }
        if (bestRule >= (innerRight - innerLeft + 1) * .7f) cy = ruleY;
        int upperStart =
                Math.max(
                        head.minY + 1,
                        cy - Math.max(3, Math.round((head.maxY - head.minY + 1) * .35f)));
        int upperEnd = cy - 2, lowerStart = cy + 2;
        int lowerEnd =
                Math.min(
                        head.maxY - 1,
                        cy + Math.max(3, Math.round((head.maxY - head.minY + 1) * .35f)));
        if (upperEnd < upperStart || lowerEnd < lowerStart) return false;
        int upperWhite = 0,
                lowerWhite = 0,
                upperCount = 0,
                lowerCount = 0,
                darkRule = 0,
                ruleCount = 0;
        for (int x = innerLeft; x <= innerRight; x++) {
            if (x < 0 || x >= width) continue;
            for (int y = upperStart; y <= upperEnd; y++)
                if (y >= 0 && y < height) {
                    upperCount++;
                    if ((gray[y * width + x] & 255) >= 185) upperWhite++;
                }
            for (int y = lowerStart; y <= lowerEnd; y++)
                if (y >= 0 && y < height) {
                    lowerCount++;
                    if ((gray[y * width + x] & 255) >= 185) lowerWhite++;
                }
            if (cy >= 0 && cy < height) {
                ruleCount++;
                if ((gray[cy * width + x] & 255) < 130) darkRule++;
            }
        }
        return upperCount >= 4
                && lowerCount >= 4
                && ruleCount >= 3
                && upperWhite >= Math.max(2, Math.round(upperCount * .3f))
                && lowerWhite >= Math.max(2, Math.round(lowerCount * .1f))
                && darkRule >= Math.round(ruleCount * .7f);
    }

    private static List<Component> sideBySideSeconds(
            byte[] labels, int width, Component head, float gap) {
        float w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1;
        if (w < gap * 2.1f || w > gap * 3.1f || h < gap * 1.05f || h > gap * 1.9f) return List.of();
        int middle = (head.minX + head.maxX) / 2;
        Component a = horizontalHeadSlice(labels, width, head, head.minX, middle);
        Component b = horizontalHeadSlice(labels, width, head, middle + 1, head.maxX);
        if (a == null
                || b == null
                || a.area < gap * gap * .4f
                || b.area < gap * gap * .4f
                || Math.abs(a.centerY - b.centerY) < gap * .3f
                || Math.abs(a.centerY - b.centerY) > gap * .8f
                || a.maxY - a.minY < gap * .65f
                || b.maxY - b.minY < gap * .65f) return List.of();
        return List.of(a, b);
    }

    private static Component horizontalHeadSlice(
            byte[] labels, int width, Component head, int left, int right) {
        int area = 0, minY = head.maxY, maxY = head.minY;
        long sumX = 0, sumY = 0;
        for (int y = head.minY; y <= head.maxY; y++)
            for (int x = left; x <= right; x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) {
                    area++;
                    sumX += x;
                    sumY += y;
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
        return area == 0
                ? null
                : new Component(
                        area, left, right, minY, maxY, sumX / (float) area, sumY / (float) area);
    }

    private static Component componentSlice(
            byte[] labels, int width, Component source, int top, int bottom) {
        int area = 0, minX = source.maxX + 1, maxX = source.minX - 1;
        int minY = bottom + 1, maxY = top - 1;
        long sumX = 0, sumY = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = source.minX; x <= source.maxX; x++) {
                if (labels[y * width + x] != OmrMeasurePostProcessor.NOTEHEAD) continue;
                area++;
                sumX += x;
                sumY += y;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        return area < 3
                ? null
                : new Component(
                        area, minX, maxX, minY, maxY, sumX / (float) area, sumY / (float) area);
    }

    private static List<Staff> findStaffs(
            byte[] labels, byte[] gray, int width, int height, List<MeasureRegion> measures) {
        int[] projection = new int[height];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++)
                if (labels[row + x] == OmrMeasurePostProcessor.STAFF) projection[y]++;
        }
        int threshold = Math.max(8, Math.round(width * 0.055f));
        List<Staff> staffs = new ArrayList<>();
        for (RawStaffLineDetector.StaffLines semantic :
                RawStaffLineDetector.detectFromStrength(projection, threshold, height, gray, width))
            staffs.add(new Staff(semantic.top(), semantic.bottom(), semantic.gap()));

        // The peak detector is deliberately selective so a slur/beam cannot masquerade as a
        // second staff. On a faded scan that selectivity can omit a complete, otherwise clean
        // system (Phantom of the Opera pages 4/5 exposed this). Recover only legacy five-band
        // candidates that line up with an already accepted measure row and the page's staff
        // scale. This forms a union on real score rows without restoring the old stray-text
        // candidates that made Hedwig's Theme unreadable.
        for (Staff legacy : legacySemanticStaffs(projection, threshold, height)) {
            if (!alignedWithMeasureRow(legacy, measures, height)
                    || !compatibleStaffScale(legacy, staffs)
                    || !isolatedMissingSystem(legacy, staffs)
                    || representedStaff(legacy, staffs, height)) continue;
            staffs.add(legacy);
        }
        // Dense notes can interrupt one semantic stripe enough to miss the
        // pitch reader's stronger threshold even though the measure pass has
        // already established the row. Recover only a separate, correctly
        // spaced system inside those existing measure bounds.
        for (RawStaffLineDetector.StaffLines weak :
                RawStaffLineDetector.detectFromStrength(
                        projection, Math.max(10, width / 80), height, gray, width)) {
            Staff recovered = new Staff(weak.top(), weak.bottom(), weak.gap());
            if (alignedWithMeasureRow(recovered, measures, height)
                    && compatibleStaffScale(recovered, staffs)
                    && isolatedMissingSystem(recovered, staffs)
                    && !representedStaff(recovered, staffs, height)) staffs.add(recovered);
        }
        // The measure reader already deskews semantic rules. Use that same
        // evidence for missing pitch staffs so an accepted row cannot go silent.
        float semanticSlope = OmrMeasurePostProcessor.estimateStaffSlope(labels, width, height);
        if (Math.abs(semanticSlope) > .001f) {
            int[] deskewed = new int[height];
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    if (labels[y * width + x] == OmrMeasurePostProcessor.STAFF) {
                        int row = Math.round(y - semanticSlope * (x - width * .5f));
                        if (row >= 0 && row < height) deskewed[row]++;
                    }
            for (var lines :
                    RawStaffLineDetector.detectFromStrength(
                            deskewed, Math.max(10, width / 80), height)) {
                Staff recovered = new Staff(lines.top(), lines.bottom(), lines.gap());
                recovered.pitchSlope = semanticSlope;
                // A tilted rule can produce two peaks in the uncorrected row
                // projection, inventing a half-spacing staff. A complete,
                // strong deskewed five-rule group with no intervening rules
                // resolves that alias before the page-scale gate can reject it.
                boolean printed =
                        completePrintedDeskewedStaff(gray, width, height, lines, semanticSlope);
                boolean replaced = false;
                if (alignedWithMeasureRow(recovered, measures, height)
                        && (printed || unambiguousDeskewedRules(deskewed, lines, width))) {
                    for (int i = staffs.size() - 1; i >= 0; i--) {
                        Staff prior = staffs.get(i);
                        float center = (prior.top + prior.bottom) * .5f;
                        if (recovered.gap >= prior.gap * (printed ? 1.18f : 1.8f)
                                && recovered.gap <= prior.gap * (printed ? 2.4f : 2.2f)
                                && Math.abs(center - (recovered.top + recovered.bottom) * .5f)
                                        <= recovered.gap * (printed ? 1.75f : 1f)
                                && prior.top
                                        >= recovered.top - recovered.gap * (printed ? .8f : .25f)
                                && prior.bottom
                                        <= recovered.bottom
                                                + recovered.gap * (printed ? .8f : .25f)) {
                            staffs.remove(i);
                            replaced = true;
                        }
                    }
                }
                if (replaced
                        || alignedWithMeasureRow(recovered, measures, height)
                                && (printed || compatibleStaffScale(recovered, staffs))
                                && (printed && staffs.isEmpty()
                                        || isolatedMissingSystem(recovered, staffs))
                                && !representedStaff(recovered, staffs, height))
                    staffs.add(recovered);
            }
        }
        for (RawStaffLineDetector.StaffLines raw :
                RawStaffLineDetector.detect(gray, width, height)) {
            Staff recovered = new Staff(raw.top(), raw.bottom(), raw.gap());
            float[] pitch = printedStaffPitch(gray, width, height, raw);
            // Semantic paint can shorten the outer-line spacing by several pixels. That
            // error accumulates on ledger notes and moves them to a different printed pitch.
            // Keep semantic geometry for symbol ownership, but calibrate pitch to a matching
            // complete five-line staff measured directly from the page.
            // A missing semantic outer rule can admit a ledger line and shift the
            // entire five-line group by one gap. A complete printed staff also
            // calibrates that case; the nearby group must still overlap four rules.
            for (Staff staff : staffs)
                if (pitch != null
                        && Math.abs(staff.top - raw.top()) <= staff.gap * 1.2f
                        && Math.abs(staff.bottom - raw.bottom()) <= staff.gap * 1.2f
                        && raw.gap() >= staff.gap * .85f
                        && raw.gap() <= staff.gap * 1.18f) {
                    staff.pitchGap = pitch[1];
                    staff.pitchBottom = pitch[0];
                }
            if (pitch != null) {
                recovered.pitchGap = pitch[1];
                recovered.pitchBottom = pitch[0];
            }
            if (!representedStaff(recovered, staffs, height)) staffs.add(recovered);
        }
        staffs.sort(Comparator.comparingDouble(staff -> staff.top));
        float[] pageGaps = new float[staffs.size()];
        for (int i = 0; i < staffs.size(); i++) pageGaps[i] = staffs.get(i).pitchGap;
        float pageGap = pageGaps.length == 0 ? 0 : median(pageGaps);
        for (Staff staff : staffs) {
            float[] pitch = regionalStaffPitch(gray, width, height, staff);
            if (pitch != null
                    && (pitch[2] >= 3
                            || (staffs.size() >= 3
                                    && Math.abs(pitch[1] - pageGap) <= pageGap * .08f))) {
                // One unobscured strip may be at the sloping page edge. When other
                // staffs corroborate its spacing, keep the central vertical anchor.
                if (pitch[2] >= 3 || Math.abs(pitch[0] - staff.pitchBottom) > pitch[1] * .5f)
                    staff.pitchBottom = pitch[0];
                staff.pitchGap = pitch[1];
                staff.pitchSlope = pitch[3];
            }
        }
        // Recover staff geometry from five continuous printed rules on a tilted page.
        // Match nearby groups and require page-scale support for compressed aliases.
        if (gray != null && Math.abs(semanticSlope) > .001f) {
            int[] printedRows = new int[height];
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    if ((gray[y * width + x] & 255) < 170) {
                        int row = Math.round(y - semanticSlope * (x - width * .5f));
                        if (row >= 0 && row < height) printedRows[row]++;
                    }
            int[] interruptedRows = printedRows.clone();
            for (int row = 0; row < height; row++)
                if (printedRows[row] >= width * .25f) {
                    int run = 0, longest = 0;
                    for (int x = 0; x < width; x++) {
                        int y = Math.round(row + semanticSlope * (x - width * .5f));
                        boolean ink = false;
                        for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
                            if ((gray[yy * width + x] & 255) < 180) {
                                ink = true;
                                break;
                            }
                        if (ink) longest = Math.max(longest, ++run);
                        else run = 0;
                    }
                    if (longest < width * .25f) printedRows[row] = 0;
                }
            for (var raw :
                    RawStaffLineDetector.detectFromStrength(
                            printedRows, Math.max(24, Math.round(width * .25f)), height)) {
                if (!completePrintedDeskewedStaff(gray, width, height, raw, semanticSlope))
                    continue;
                for (int i = 0; i < staffs.size(); i++) {
                    Staff staff = staffs.get(i);
                    Staff printed = new Staff(raw.top(), raw.bottom(), raw.gap());
                    // Multiple peaks within each tilted rule can invent a tiny staff.
                    // Five continuous printed rules, clear spaces, measure alignment and
                    // the other staffs' scale resolve it without a fixed alias ratio.
                    if (raw.gap() > staff.gap * 1.18f
                            && staff.top >= raw.top() - raw.gap() * .8f
                            && staff.bottom <= raw.bottom() + raw.gap() * .8f
                            && Math.abs((staff.top + staff.bottom - raw.top() - raw.bottom()) * .5f)
                                    <= raw.gap() * 1.75f
                            && alignedWithMeasureRow(printed, measures, height)) {
                        List<Staff> others = new ArrayList<>(staffs);
                        others.remove(i);
                        if (compatibleStaffScale(printed, others)) {
                            printed.pitchSlope = semanticSlope;
                            printed.printedPhase = true;
                            staffs.set(i, printed);
                            continue;
                        }
                    }
                    if (raw.gap() < staff.gap * .85f
                            || raw.gap() > staff.gap * 1.18f
                            || Math.abs(staff.top - raw.top()) > raw.gap() * 1.2f
                            || Math.abs(staff.bottom - raw.bottom()) > raw.gap() * 1.2f) continue;
                    // A close bottom rule does not guarantee the correct scale on ledger notes.
                    float error =
                            Math.max(
                                    Math.abs(staff.pitchBottom - raw.bottom()),
                                    Math.abs(staff.bottom - raw.bottom()));
                    error = Math.max(error, Math.abs(staff.pitchGap - raw.gap()) * 6);
                    if (error < raw.gap() * .45f) {
                        // A quarter-gap edge displacement reaches half a diatonic step:
                        // even a centered seed can then round an edge note to the next pitch.
                        // Correct that seed without locking the local reader to a new staff phase.
                        if (!staff.printedPhase
                                && Math.abs(staff.pitchSlope - semanticSlope) * width * .5f
                                        >= raw.gap() * .25f) {
                            staff.pitchBottom = raw.bottom();
                            staff.pitchGap = raw.gap();
                            staff.pitchSlope = semanticSlope;
                            staff.printedSlope = true;
                        }
                        continue;
                    }
                    staff.pitchBottom = raw.bottom();
                    staff.pitchGap = raw.gap();
                    staff.pitchSlope = semanticSlope;
                    staff.printedPhase = true;
                }
                Staff missing = new Staff(raw.top(), raw.bottom(), raw.gap());
                if (alignedWithMeasureRow(missing, measures, height)
                        && compatibleStaffScale(missing, staffs)
                        && !representedStaff(missing, staffs, height)) {
                    missing.pitchSlope = semanticSlope;
                    missing.printedPhase = true;
                    staffs.add(missing);
                }
            }
            calibrateInterruptedStaffs(gray, width, height, staffs, interruptedRows, semanticSlope);
        }
        recoverFadedStaffAliases(gray, width, height, staffs, measures, semanticSlope);
        calibrateContrastedFadedStaffs(gray, width, height, staffs, semanticSlope);
        removeCompressedOverlappingStaffAliases(staffs);
        staffs.sort(Comparator.comparingDouble(staff -> staff.top));
        for (Staff staff : staffs) {
            if (!staff.printedPhase && !staff.printedSlope) {
                float[] straight =
                        StaffPitchTrack.straightPitch(
                                labels, gray, width, height, staff.pitchBottom, staff.pitchGap);
                if (straight != null) {
                    staff.pitchBottom = straight[0];
                    staff.pitchGap = straight[1];
                    staff.pitchSlope = 0;
                    staff.printedPhase = true;
                }
            }
            staff.pitchTrack =
                    StaffPitchTrack.detect(
                            gray,
                            width,
                            height,
                            staff.printedPhase || staff.printedSlope
                                    ? staff.pitchBottom - staff.pitchGap * 4
                                    : staff.top,
                            staff.printedPhase || staff.printedSlope
                                    ? staff.pitchBottom
                                    : staff.bottom,
                            staff.pitchGap);
            if (staff.printedPhase
                    && staff.pitchTrack != null
                    && Math.abs(staff.pitchTrack.at(width * .5f)[0] - staff.pitchBottom)
                            > staff.pitchGap * .5f) staff.pitchTrack = null;
            if (staff.pitchTrack == null && !staff.printedPhase && !staff.printedSlope)
                staff.pitchTrack =
                        StaffPitchTrack.closedTail(
                                gray, width, height, staff.pitchBottom, staff.pitchGap);
        }
        assignSystemPositions(staffs, measures, height);
        return staffs;
    }

    /** A compressed semantic alias can overlap a complete page-scale staff and steal its notes. */
    private static void removeCompressedOverlappingStaffAliases(List<Staff> staffs) {
        float[] gaps = new float[staffs.size()];
        for (int i = 0; i < staffs.size(); i++) gaps[i] = staffs.get(i).gap;
        float pageGap = gaps.length == 0 ? 0 : median(gaps);
        staffs.removeIf(
                alias -> {
                    if (alias.gap >= pageGap * .6f) return false;
                    for (Staff printed : staffs)
                        if (printed != alias
                                && printed.gap >= pageGap * .85f
                                && printed.gap >= alias.gap * 1.6f
                                && Math.min(printed.bottom, alias.bottom)
                                                - Math.max(printed.top, alias.top)
                                        >= alias.gap * 2f
                                && Math.abs(
                                                (printed.top
                                                                + printed.bottom
                                                                - alias.top
                                                                - alias.bottom)
                                                        * .5f)
                                        <= printed.gap * 2.5f) return true;
                    return false;
                });
    }

    /** Short breaks in otherwise broad rules must not leave an admitted staff
     * on the wrong pitch phase. This never admits or removes a staff. */
    private static void calibrateInterruptedStaffs(
            byte[] gray, int width, int height, List<Staff> staffs, int[] rows, float slope) {
        if (staffs.size() < 4) return;
        for (var raw :
                RawStaffLineDetector.detectFromStrength(
                        rows, Math.max(24, Math.round(width * .25f)), height)) {
            if (!completeInterruptedStaff(gray, width, height, raw, slope)) continue;
            for (Staff staff : staffs) {
                if (staff.printedPhase
                        || staff.printedSlope
                        || raw.gap() < staff.pitchGap * .85f
                        || raw.gap() > staff.pitchGap * 1.18f
                        || Math.abs(staff.top - raw.top()) > raw.gap() * 1.2f
                        || Math.abs(staff.bottom - raw.bottom()) > raw.gap() * 1.2f) continue;
                int corroboration = 0;
                for (Staff other : staffs)
                    if (other != staff && Math.abs(other.pitchGap - raw.gap()) <= raw.gap() * .08f)
                        corroboration++;
                if (corroboration < 3) continue;
                float error =
                        Math.max(
                                Math.abs(staff.pitchBottom - raw.bottom()),
                                Math.abs(staff.pitchGap - raw.gap()) * 6);
                error = Math.max(error, Math.abs(staff.pitchSlope - slope) * width * .5f);
                if (error < raw.gap() * .45f) continue;
                staff.pitchBottom = raw.bottom();
                staff.pitchGap = raw.gap();
                staff.pitchSlope = slope;
                staff.printedPhase = true;
            }
        }
    }

    private static boolean completeInterruptedStaff(
            byte[] gray,
            int width,
            int height,
            RawStaffLineDetector.StaffLines staff,
            float slope) {
        int step = Math.max(1, width / 512), radius = Math.max(1, Math.round(staff.gap() * .15f));
        int flank = Math.max(2, Math.round(staff.gap() * .32f));
        int partialRules = 0;
        for (int i = 0; i < 9; i++) {
            float row = i < 5 ? staff.rows()[i] : (staff.rows()[i - 5] + staff.rows()[i - 4]) * .5f;
            int supported = 0, samples = 0;
            for (int x = 0; x < width; x += step) {
                int y = Math.round(row + slope * (x - width * .5f));
                samples++;
                for (int yy = Math.max(flank, y - radius);
                        yy <= Math.min(height - 1 - flank, y + radius);
                        yy++) {
                    int ink = gray[yy * width + x] & 255;
                    if (i < 5
                            ? ink <= 205
                                    && (gray[(yy - flank) * width + x] & 255) >= ink + 12
                                    && (gray[(yy + flank) * width + x] & 255) >= ink + 12
                            : ink <= 180) {
                        supported++;
                        break;
                    }
                }
            }
            // One rule can cross denser notation, but the other four must
            // retain the stronger page-wide thin-ink support.
            if (i < 5) {
                if (supported < samples * .70f || supported < samples * .75f && ++partialRules > 1)
                    return false;
            } else if (supported >= samples * .4f) return false;
        }
        return true;
    }

    /** Pale rules can leave a compressed semantic group. Require three other
     * systems to confirm its true scale and broad printed support for every rule. */
    private static void recoverFadedStaffAliases(
            byte[] gray,
            int width,
            int height,
            List<Staff> staffs,
            List<MeasureRegion> measures,
            float slope) {
        if (gray == null || staffs.size() < 4) return;
        List<Float> gaps = new ArrayList<>();
        for (Staff s : staffs) gaps.add(s.pitchGap);
        gaps.sort(Float::compare);
        float typical = gaps.get(gaps.size() / 2);
        if (staffs.stream().noneMatch(s -> s.pitchGap < typical * .8f)) return;
        int[] rows = new int[height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if ((gray[y * width + x] & 255) < 205) {
                    int row = Math.round(y - slope * (x - width * .5f));
                    if (row >= 0 && row < height) rows[row]++;
                }
        for (var raw :
                RawStaffLineDetector.detectFromStrength(
                        rows, Math.max(24, Math.round(width * .25f)), height)) {
            if (Math.abs(raw.gap() - typical) > typical * .08f) continue;
            int corroboration = 0;
            for (Staff s : staffs)
                if (Math.abs(s.pitchGap - raw.gap()) <= raw.gap() * .08f) corroboration++;
            if (corroboration < 3 || !completeFadedStaff(gray, width, height, raw, slope)) continue;
            for (int i = 0; i < staffs.size(); i++) {
                Staff prior = staffs.get(i);
                if (prior.pitchGap >= raw.gap() * .8f
                        || prior.pitchGap < raw.gap() * .45f
                        || prior.top < raw.top() - raw.gap() * .8f
                        || prior.bottom > raw.bottom() + raw.gap() * .8f
                        || Math.abs((prior.top + prior.bottom - raw.top() - raw.bottom()) * .5f)
                                > raw.gap() * 1.75f) continue;
                Staff recovered = new Staff(raw.top(), raw.bottom(), raw.gap());
                if (!alignedWithMeasureRow(recovered, measures, height)) continue;
                recovered.pitchSlope = slope;
                recovered.printedPhase = true;
                staffs.set(i, recovered);
            }
        }
    }

    /** Thin pale rules can establish scale despite a moderately compressed mask.
     * Three independent systems must corroborate the complete printed group. */
    private static void calibrateContrastedFadedStaffs(
            byte[] gray, int width, int height, List<Staff> staffs, float slope) {
        if (gray == null || staffs.size() < 4) return;
        List<Float> gaps = new ArrayList<>();
        for (Staff staff : staffs) gaps.add(staff.pitchGap);
        gaps.sort(Float::compare);
        float typical = gaps.get(gaps.size() / 2);
        int flank = Math.max(2, Math.round(typical * .22f));
        int[] rows = new int[height];
        for (int y = flank; y < height - flank; y++)
            for (int x = 0; x < width; x++) {
                int ink = gray[y * width + x] & 255;
                if (ink > 225
                        || (gray[(y - flank) * width + x] & 255) < ink + 12
                        || (gray[(y + flank) * width + x] & 255) < ink + 12) continue;
                int row = Math.round(y - slope * (x - width * .5f));
                if (row >= 0 && row < height) rows[row]++;
            }
        for (var raw :
                RawStaffLineDetector.detectFromStrength(
                        rows, Math.max(24, Math.round(width * .25f)), height)) {
            if (Math.abs(raw.gap() - typical) > typical * .08f) continue;
            int corroboration = 0;
            for (float originalGap : gaps)
                if (Math.abs(originalGap - raw.gap()) <= raw.gap() * .08f) corroboration++;
            if (corroboration < 3 || !completeContrastedFadedStaff(gray, width, height, raw, slope))
                continue;
            for (Staff staff : staffs) {
                int independent =
                        corroboration
                                - (Math.abs(staff.pitchGap - raw.gap()) <= raw.gap() * .08f
                                        ? 1
                                        : 0);
                if (independent < 3) continue;
                boolean compressed = staff.pitchGap < raw.gap() * .8f;
                if (staff.printedPhase
                        || staff.printedSlope
                        || staff.pitchTrack != null
                        || staff.pitchGap < raw.gap() * .6f
                        || staff.pitchGap > raw.gap() * 1.2f
                        || Math.abs(staff.pitchBottom - raw.bottom())
                                > raw.gap() * (compressed ? 1.5f : .45f)) continue;
                // A compressed seed must still belong to this complete printed group.
                if (compressed
                        && (Math.abs(staff.top - raw.top()) > raw.gap() * .5f
                                || Math.abs(
                                                (staff.top
                                                                + staff.bottom
                                                                - raw.top()
                                                                - raw.bottom())
                                                        * .5f)
                                        > raw.gap())) continue;
                staff.pitchBottom = raw.bottom();
                staff.pitchGap = raw.gap();
                staff.pitchSlope = slope;
                staff.printedPhase = true;
            }
        }
    }

    private static boolean completeContrastedFadedStaff(
            byte[] gray,
            int width,
            int height,
            RawStaffLineDetector.StaffLines staff,
            float slope) {
        int radius = Math.max(1, Math.round(staff.gap() * .15f)),
                flank = Math.max(2, Math.round(staff.gap() * .22f));
        for (int i = -2; i <= 10; i++) {
            float row = staff.top() + i * staff.gap() * .5f;
            int supported = 0, samples = 0;
            for (int x = Math.round(width * .1f); x < width * .94f; x += Math.max(1, width / 512)) {
                int center = Math.round(row + slope * (x - width * .5f));
                samples++;
                for (int y = Math.max(flank, center - radius);
                        y <= Math.min(height - 1 - flank, center + radius);
                        y++) {
                    int ink = gray[y * width + x] & 255;
                    if (ink <= 225
                            && (gray[(y - flank) * width + x] & 255) >= ink + 12
                            && (gray[(y + flank) * width + x] & 255) >= ink + 12) {
                        supported++;
                        break;
                    }
                }
            }
            boolean rule = i >= 0 && i <= 8 && i % 2 == 0;
            if (samples < 24
                    || (rule ? supported < Math.round(samples * .75f) : supported >= samples * .4f))
                return false;
        }
        return true;
    }

    private static boolean completeFadedStaff(
            byte[] gray,
            int width,
            int height,
            RawStaffLineDetector.StaffLines staff,
            float slope) {
        int step = Math.max(1, width / 512), radius = Math.max(1, Math.round(staff.gap() * .15f));
        for (int i = 0; i < 9; i++) {
            float row = i < 5 ? staff.rows()[i] : (staff.rows()[i - 5] + staff.rows()[i - 4]) * .5f;
            int dark = 0, samples = 0;
            for (int x = 0; x < width; x += step) {
                int y = Math.round(row + slope * (x - width * .5f));
                samples++;
                for (int yy = Math.max(0, y - radius); yy <= Math.min(height - 1, y + radius); yy++)
                    if ((gray[yy * width + x] & 255) < 205) {
                        dark++;
                        break;
                    }
            }
            if (i < 5 ? dark < samples * .75f : dark >= samples * .4f) return false;
        }
        return true;
    }

    /** Verify all five sloped rules in the printed page, with clear spaces between them. */
    private static boolean completePrintedDeskewedStaff(
            byte[] gray,
            int width,
            int height,
            RawStaffLineDetector.StaffLines staff,
            float slope) {
        if (gray == null || gray.length != width * height) return false;
        int step = Math.max(1, width / 512), radius = Math.max(1, Math.round(staff.gap() * .15f));
        for (int i = 0; i < 9; i++) {
            float row = i < 5 ? staff.rows()[i] : (staff.rows()[i - 5] + staff.rows()[i - 4]) * .5f;
            int dark = 0, samples = 0;
            for (int x = 0; x < width; x += step) {
                int y = Math.round(row + slope * (x - width * .5f));
                samples++;
                for (int yy = Math.max(0, y - radius); yy <= Math.min(height - 1, y + radius); yy++)
                    if ((gray[yy * width + x] & 255) < 180) {
                        dark++;
                        break;
                    }
            }
            if (i < 5 ? dark < samples * .55f : dark >= samples * .5f)
                return completePrintedDeskewedSpan(gray, width, height, staff, slope);
        }
        return true;
    }

    /** Short systems must prove five printed rules across their own extent. */
    private static boolean completePrintedDeskewedSpan(
            byte[] gray,
            int width,
            int height,
            RawStaffLineDetector.StaffLines staff,
            float slope) {
        int step = Math.max(1, width / 512), radius = Math.max(1, Math.round(staff.gap() * .15f));
        int first = -1, last = -1;
        for (int x = 0; x < width; x += step) {
            int lines = 0, spaces = 0;
            for (int i = 0; i < 9; i++) {
                float row =
                        i < 5 ? staff.rows()[i] : (staff.rows()[i - 5] + staff.rows()[i - 4]) * .5f;
                int y = Math.round(row + slope * (x - width * .5f));
                boolean dark = false;
                for (int yy = Math.max(0, y - radius); yy <= Math.min(height - 1, y + radius); yy++)
                    if ((gray[yy * width + x] & 255) < 180) {
                        dark = true;
                        break;
                    }
                if (dark) {
                    if (i < 5) lines++;
                    else spaces++;
                }
            }
            if (lines >= 4 && spaces <= 1) {
                if (first < 0) first = x;
                last = x;
            }
        }
        if (first < 0 || last - first < Math.max(staff.gap() * 12, width * .18f)) return false;
        for (int i = 0; i < 9; i++) {
            float row = i < 5 ? staff.rows()[i] : (staff.rows()[i - 5] + staff.rows()[i - 4]) * .5f;
            int dark = 0, samples = 0;
            for (int x = first; x <= last; x += step) {
                int y = Math.round(row + slope * (x - width * .5f));
                samples++;
                for (int yy = Math.max(0, y - radius); yy <= Math.min(height - 1, y + radius); yy++)
                    if ((gray[yy * width + x] & 255) < 180) {
                        dark++;
                        break;
                    }
            }
            if (i < 5 ? dark < samples * .75f : dark >= samples * .35f) return false;
        }
        return true;
    }

    private static boolean unambiguousDeskewedRules(
            int[] strength, RawStaffLineDetector.StaffLines staff, int width) {
        int radius = Math.max(1, Math.round(staff.gap() * .15f));
        for (int row : staff.rows()) {
            int peak = 0;
            for (int y = Math.max(0, row - radius);
                    y <= Math.min(strength.length - 1, row + radius);
                    y++) peak = Math.max(peak, strength[y]);
            if (peak < width * .25f) return false;
        }
        for (int i = 0; i < 4; i++) {
            int middle = Math.round((staff.rows()[i] + staff.rows()[i + 1]) * .5f);
            for (int y = Math.max(0, middle - radius);
                    y <= Math.min(strength.length - 1, middle + radius);
                    y++) if (strength[y] >= width * .20f) return false;
        }
        return true;
    }

    /** Center complete printed rule bands instead of using whichever edge pixel wins a peak. */
    private static float[] printedStaffPitch(
            byte[] gray, int width, int height, RawStaffLineDetector.StaffLines raw) {
        return printedStaffPitch(gray, width, height, raw, .25f);
    }

    private static float[] printedStaffPitch(
            byte[] gray,
            int width,
            int height,
            RawStaffLineDetector.StaffLines raw,
            float support) {
        float[] centers = new float[5];
        int radius = Math.max(2, Math.round(raw.gap() * .28f));
        int strongRules = 0;
        for (int line = 0; line < 5; line++) {
            int first = Math.max(0, raw.rows()[line] - radius),
                    last = Math.min(height - 1, raw.rows()[line] + radius);
            int[] strength = new int[last - first + 1];
            int strongest = 0, peak = 0;
            for (int y = first; y <= last; y++) {
                int n = 0;
                for (int x = 0; x < width; x++) if ((gray[y * width + x] & 255) <= 170) n++;
                strength[y - first] = n;
                if (n > strongest) {
                    strongest = n;
                    peak = y - first;
                }
            }
            if (strongest < width * support) return null;
            if (strongest >= width * .9f) strongRules++;
            int start = peak, end = peak;
            while (start > 0 && strength[start - 1] >= strongest * .85f) start--;
            while (end + 1 < strength.length && strength[end + 1] >= strongest * .85f) end++;
            if (end - start + 1 > Math.max(3, raw.gap() * .4f)) return null;
            centers[line] = first + (start + end) * .5f;
            // A dense row of beams can outscore an outer staff rule in the
            // page projection. A printed rule stays thin in individual columns,
            // even on a skewed scan; beams do not. Do not let such a candidate
            // move the pitch reference by an entire staff gap.
            int thinColumns = 0, maxThickness = Math.max(3, Math.round(raw.gap() * .4f));
            for (int x = 0; x < width; x++) {
                for (int y = first; y <= last; y++) {
                    if ((gray[y * width + x] & 255) > 170) continue;
                    int a = y, b = y;
                    while (a > 0
                            && y - a <= maxThickness
                            && (gray[(a - 1) * width + x] & 255) <= 170) a--;
                    while (b + 1 < height
                            && b - a < maxThickness
                            && (gray[(b + 1) * width + x] & 255) <= 170) b++;
                    if (b - a + 1 <= maxThickness) {
                        thinColumns++;
                        break;
                    }
                    y = b;
                }
            }
            if (thinColumns < width * support) return null;
        }
        // Regional recovery is deliberately stricter than the page-wide fallback. One
        // interrupted rule is common under a note or dynamic; two make the vertical phase
        // ambiguous enough that semantic staff geometry is safer than re-anchoring it.
        if (support > .5f && strongRules < 4) return null;
        float gap = (centers[4] - centers[0]) / 4;
        for (int line = 1; line < 5; line++)
            if (Math.abs(centers[line] - centers[line - 1] - gap)
                    > Math.max(.75f, gap * (support > .5f ? .20f : .12f))) return null;
        return new float[] {centers[4], gap};
    }

    /** Skew spreads page-wide peaks. Several short, independent strips can still
     * establish the complete five-rule spacing without trusting semantic paint. */
    private static float[] regionalStaffPitch(byte[] gray, int width, int height, Staff staff) {
        if (gray == null) return null;
        int stripWidth = Math.min(width, Math.max(80, Math.round(staff.gap * 10)));
        int top = Math.max(0, Math.round(staff.top - staff.gap * 2));
        int bottom = Math.min(height, Math.round(staff.bottom + staff.gap * 2));
        List<float[]> pitches = new ArrayList<>();
        for (int strip = 0; strip < 7; strip++) {
            int left =
                    Math.max(
                            0,
                            Math.min(
                                    width - stripWidth,
                                    Math.round(width * (.15f + strip * .12f) - stripWidth * .5f)));
            byte[] local = new byte[stripWidth * (bottom - top)];
            for (int y = top; y < bottom; y++)
                System.arraycopy(gray, y * width + left, local, (y - top) * stripWidth, stripWidth);
            int[] thinStrength = new int[bottom - top];
            int maxThickness = Math.max(3, Math.round(staff.gap * .4f));
            for (int x = 0; x < stripWidth; x++)
                for (int y = 0; y < bottom - top; ) {
                    if ((local[y * stripWidth + x] & 255) > 170) {
                        y++;
                        continue;
                    }
                    int first = y;
                    while (y < bottom - top && (local[y * stripWidth + x] & 255) <= 170) y++;
                    if (y - first <= maxThickness)
                        for (int row = first; row < y; row++) thinStrength[row]++;
                }
            float[] best = null;
            float distance = Float.MAX_VALUE;
            for (var raw :
                    RawStaffLineDetector.detectFromStrength(
                            thinStrength,
                            Math.max(24, Math.round(stripWidth * .55f)),
                            bottom - top)) {
                float[] pitch = printedStaffPitch(local, stripWidth, bottom - top, raw, .55f);
                if (pitch == null
                        || pitch[1] < staff.gap * .75f
                        || pitch[1] > staff.gap * 1.4f
                        || Math.abs(raw.top() + top - staff.top) > staff.gap * 1.6f
                        || Math.abs(raw.bottom() + top - staff.bottom) > staff.gap * 1.6f) continue;
                float d = Math.abs(pitch[0] + top - staff.pitchBottom);
                if (d < distance) {
                    distance = d;
                    best = new float[] {pitch[0] + top, pitch[1], left + stripWidth * .5f};
                }
            }
            if (best != null) pitches.add(best);
        }
        if (pitches.isEmpty()) return null;
        float[] gaps = new float[pitches.size()], bottoms = new float[pitches.size()];
        for (int i = 0; i < pitches.size(); i++) {
            gaps[i] = pitches.get(i)[1];
            bottoms[i] = pitches.get(i)[0];
        }
        float gap = median(gaps), base = median(bottoms);
        int consistent = 0;
        for (float[] pitch : pitches)
            if (Math.abs(pitch[1] - gap) <= gap * .08f && Math.abs(pitch[0] - base) <= gap * .6f)
                consistent++;
        float slope = 0;
        if (consistent >= 3) {
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            int n = 0;
            float first = width, last = 0;
            for (float[] pitch : pitches)
                if (Math.abs(pitch[1] - gap) <= gap * .08f
                        && Math.abs(pitch[0] - base) <= gap * .6f) {
                    sx += pitch[2];
                    sy += pitch[0];
                    sxx += pitch[2] * pitch[2];
                    sxy += pitch[2] * pitch[0];
                    n++;
                    first = Math.min(first, pitch[2]);
                    last = Math.max(last, pitch[2]);
                }
            if (last - first >= width * .25f && n * sxx - sx * sx > 0) {
                float fitted = (float) ((n * sxy - sx * sy) / (n * sxx - sx * sx));
                float intercept = (float) ((sy - fitted * sx) / n);
                int fits = 0;
                for (float[] pitch : pitches)
                    if (Math.abs(pitch[0] - (intercept + fitted * pitch[2])) <= gap * .20f) fits++;
                if (fits >= consistent && Math.abs(fitted) * width <= gap * 2) {
                    slope = fitted;
                    base = intercept + slope * width * .5f;
                }
            }
        }
        return consistent * 2 > pitches.size()
                        && continuousPrintedRules(gray, width, height, base, gap, slope) >= 4
                ? new float[] {base, gap, consistent, slope}
                : null;
    }

    /** A local strip can land entirely between repeated occlusions and make a broken rule look
     * continuous. Recheck the fitted five rules across the page: four must remain independently
     * traceable, leaving room for one rule to be covered by ordinary notation. */
    private static int continuousPrintedRules(
            byte[] gray, int width, int height, float bottom, float gap, float slope) {
        int strong = 0, radius = Math.max(1, Math.round(gap * .22f));
        for (int line = 0; line < 5; line++) {
            int columns = 0;
            for (int x = 0; x < width; x++) {
                int center = Math.round(bottom - line * gap + slope * (x - width * .5f));
                boolean ink = false;
                for (int y = Math.max(0, center - radius);
                        y <= Math.min(height - 1, center + radius);
                        y++)
                    if ((gray[y * width + x] & 255) <= 170) {
                        ink = true;
                        break;
                    }
                if (ink) columns++;
            }
            if (columns >= width * .84f) strong++;
        }
        return strong;
    }

    /** Original contiguous-band reader retained as a conservative fallback on known score rows. */
    private static List<Staff> legacySemanticStaffs(int[] projection, int threshold, int height) {
        List<Float> lines = new ArrayList<>();
        for (int row = 0; row < height; ) {
            if (projection[row] < threshold) {
                row++;
                continue;
            }
            long weighted = 0, strength = 0;
            int end = row;
            while (end < height && projection[end] >= threshold) {
                weighted += (long) end * projection[end];
                strength += projection[end++];
            }
            lines.add(strength == 0 ? (float) row : weighted / (float) strength);
            row = end;
        }
        List<Staff> result = new ArrayList<>();
        for (int start = 0; start + 4 < lines.size(); ) {
            float[] gaps = new float[4];
            for (int index = 0; index < gaps.length; index++)
                gaps[index] = lines.get(start + index + 1) - lines.get(start + index);
            float gap = median(gaps);
            boolean regular = gap >= 2f && gap <= height * .045f;
            for (float candidate : gaps)
                regular &= Math.abs(candidate - gap) <= Math.max(1.5f, gap * .34f);
            if (!regular) {
                start++;
                continue;
            }
            result.add(new Staff(lines.get(start), lines.get(start + 4), gap));
            start += 5;
        }
        return result;
    }

    private static boolean alignedWithMeasureRow(
            Staff staff, List<MeasureRegion> measures, int pageHeight) {
        if (measures == null || measures.isEmpty()) return false;
        float center = (staff.top + staff.bottom) * .5f / pageHeight;
        float tolerance = staff.gap * .75f / pageHeight;
        for (MeasureRegion measure : measures)
            if (center >= measure.top() - tolerance && center <= measure.bottom() + tolerance)
                return true;
        return false;
    }

    private static boolean compatibleStaffScale(Staff candidate, List<Staff> accepted) {
        if (accepted == null || accepted.isEmpty()) return true;
        List<Float> gaps = new ArrayList<>();
        for (Staff staff : accepted) gaps.add(staff.gap);
        gaps.sort(Float::compare);
        float median = gaps.get(gaps.size() / 2);
        return candidate.gap >= median * .68f && candidate.gap <= median * 1.47f;
    }

    private static boolean isolatedMissingSystem(Staff candidate, List<Staff> accepted) {
        if (accepted == null || accepted.isEmpty()) return false;
        float center = (candidate.top + candidate.bottom) * .5f;
        for (Staff staff : accepted) {
            float other = (staff.top + staff.bottom) * .5f;
            // assignSystemPositions uses the same separation boundary. A candidate inside that
            // radius belongs to an already represented grand/orchestral system, not a missing
            // line of music, and admitting it can perturb the selected melody staff.
            if (Math.abs(other - center)
                    <= Math.max(candidate.gap, staff.gap) * MAX_STAFFS_IN_SYSTEM_SEPARATION_GAPS)
                return false;
        }
        return true;
    }

    private static boolean representedStaff(Staff candidate, List<Staff> accepted, int height) {
        float center = (candidate.top + candidate.bottom) * .5f;
        for (Staff staff : accepted) {
            float other = (staff.top + staff.bottom) * .5f;
            if (Math.abs(other - center) <= Math.max(candidate.gap * 2.2f, height * .008f))
                return true;
        }
        return false;
    }

    /** Uses the measure rectangles already resolved by the barline/bracket pass as the authority
     * for system membership. This permits a page to switch between solo rows and connected duet
     * rows without a global spacing guess joining the wrong pair or serializing the duet. */
    private static void assignSystemPositions(
            List<Staff> staffs, List<MeasureRegion> measures, int pageHeight) {
        int start = 0;
        while (start < staffs.size()) {
            int end = start + 1;
            while (end < staffs.size()) {
                Staff previous = staffs.get(end - 1), next = staffs.get(end);
                if (!shareMeasureSystem(previous, next, measures, pageHeight)) break;
                end++;
            }
            int count = end - start;
            for (int index = start; index < end; index++) {
                Staff staff = staffs.get(index);
                staff.index = index - start;
                staff.count = count;
            }
            start = end;
        }
    }

    private static boolean shareMeasureSystem(
            Staff first, Staff second, List<MeasureRegion> measures, int pageHeight) {
        if (measures == null || pageHeight <= 0) return false;
        float firstCenter = (first.top + first.bottom) * .5f / pageHeight;
        float secondCenter = (second.top + second.bottom) * .5f / pageHeight;
        float tolerance = Math.max(first.gap, second.gap) * .45f / pageHeight;
        for (MeasureRegion measure : measures)
            if (firstCenter >= measure.top() - tolerance
                    && firstCenter <= measure.bottom() + tolerance
                    && secondCenter >= measure.top() - tolerance
                    && secondCenter <= measure.bottom() + tolerance) return true;
        return false;
    }

    private static List<Component> findComponents(
            byte[] labels, int width, int height, byte target) {
        // Keep the prior behavior for non-raster arguments, including partial rows.
        if (width <= 0 || height <= 0 || (long) width * height != labels.length)
            return findComponentsByPixel(labels, width, height, target);
        boolean[] visited = new boolean[labels.length];
        int[] stack = new int[Math.min(labels.length, 256)];
        List<Component> result = new ArrayList<>();
        for (int origin = 0; origin < labels.length; origin++) {
            if (visited[origin] || labels[origin] != target) continue;
            int stackSize = 0;
            stack[stackSize++] = origin;
            visited[origin] = true;
            int area = 0, minX = width, minY = height, maxX = 0, maxY = 0;
            long sumX = 0, sumY = 0;
            while (stackSize > 0) {
                int current = stack[--stackSize];
                int x = current % width, y = current / width, row = y * width;
                int left = x, right = x;
                while (left > 0 && !visited[row + left - 1] && labels[row + left - 1] == target)
                    left--;
                while (right + 1 < width
                        && !visited[row + right + 1]
                        && labels[row + right + 1] == target) right++;
                java.util.Arrays.fill(visited, row + left, row + right + 1, true);
                int count = right - left + 1;
                area += count;
                sumX += ((long) left + right) * count / 2;
                sumY += (long) y * count;
                minX = Math.min(minX, left);
                maxX = Math.max(maxX, right);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                int first = Math.max(0, left - 1), last = Math.min(width - 1, right + 1);
                for (int dy = -1; dy <= 1; dy += 2) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= height) continue;
                    int nextRow = ny * width;
                    for (int nx = first; nx <= last; nx++) {
                        int next = nextRow + nx;
                        if (visited[next] || labels[next] != target) continue;
                        visited[next] = true;
                        if (stackSize == stack.length) {
                            int capacity;
                            if (stack.length < 65536)
                                capacity = (int) Math.min(labels.length, stack.length * 2L);
                            else {
                                // Each target pixel supplies at most one queued seed.
                                capacity = 0;
                                for (byte label : labels) if (label == target) capacity++;
                            }
                            stack = java.util.Arrays.copyOf(stack, capacity);
                        }
                        stack[stackSize++] = next;
                        // One queued seed covers this as-yet-unvisited horizontal run.
                        while (nx < last && !visited[next + 1] && labels[next + 1] == target) {
                            nx++;
                            next++;
                        }
                    }
                }
            }
            if (area >= 3)
                result.add(
                        new Component(
                                area,
                                minX,
                                maxX,
                                minY,
                                maxY,
                                sumX / (float) area,
                                sumY / (float) area));
        }
        return result;
    }

    private static List<Component> findComponentsByPixel(
            byte[] labels, int width, int height, byte target) {
        boolean[] visited = new boolean[labels.length];
        int[] stack = new int[labels.length];
        List<Component> result = new ArrayList<>();
        for (int origin = 0; origin < labels.length; origin++) {
            if (visited[origin] || labels[origin] != target) continue;
            int stackSize = 0;
            stack[stackSize++] = origin;
            visited[origin] = true;
            int area = 0, minX = width, minY = height, maxX = 0, maxY = 0;
            long sumX = 0, sumY = 0;
            while (stackSize > 0) {
                int current = stack[--stackSize];
                int x = current % width, y = current / width;
                area++;
                sumX += x;
                sumY += y;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!visited[next] && labels[next] == target) {
                            visited[next] = true;
                            stack[stackSize++] = next;
                        }
                    }
            }
            if (area >= 3)
                result.add(
                        new Component(
                                area,
                                minX,
                                maxX,
                                minY,
                                maxY,
                                sumX / (float) area,
                                sumY / (float) area));
        }
        return result;
    }

    private static Staff nearestStaff(List<Staff> staffs, float y) {
        return nearestStaff(staffs, y, 5f);
    }

    private static Staff nearestHeadStaff(List<Staff> staffs, float y) {
        Staff staff = nearestStaff(staffs, y, MAX_HEAD_LEDGER_GAPS);
        // The extended range covers high piano tones, not footer/text islands below a staff.
        return staff != null && y > staff.bottom + staff.gap * 6.75f ? null : staff;
    }

    /**
     * Ledger notes between two adjacent staves can be a fraction of a pixel closer to the
     * wrong stave. Their attached stem normally points back toward the owning stave, so use that
     * topology only for genuinely ambiguous adjacent staves and retain nearest-staff everywhere
     * else. This prevents a high piano note from joining and lengthening a simultaneous violin
     * note in mixed violin/piano scores.
     */
    private static Staff staffForHead(
            byte[] labels, byte[] gray, int width, int height, List<Staff> staffs, Component head) {
        Staff ledgerOwner = printedLedgerOwner(gray, width, height, staffs, head);
        if (ledgerOwner != null) return ledgerOwner;
        Staff nearest = nearestHeadStaff(staffs, head.centerY);
        if (nearest == null) return null;
        Staff upper = null, lower = null;
        for (Staff staff : staffs) {
            if (staff.bottom < head.centerY && (upper == null || staff.bottom > upper.bottom))
                upper = staff;
            if (staff.top > head.centerY && (lower == null || staff.top < lower.top)) lower = staff;
        }
        if (upper == null || lower == null) return nearest;
        // A head within a third staff is not between these two candidates.
        if (nearest != upper && nearest != lower) return nearest;
        boolean adjacentParts = upper.count == lower.count && upper.index + 1 == lower.index;
        boolean adjacentSoloRows = upper.count == 1 && lower.count == 1;
        if (!adjacentParts && !adjacentSoloRows) return nearest;
        float upperDistance = head.centerY - upper.bottom;
        float lowerDistance = lower.top - head.centerY;
        float smaller = Math.max(.001f, Math.min(upperDistance, lowerDistance));
        if (Math.max(upperDistance, lowerDistance) > smaller * 1.55f) return nearest;

        // Beamed voices may keep an upward stem even above the bass staff.
        // The ledger chain, when visible, identifies the printed pitch staff
        // more reliably than that stem direction.
        int upperLedgers = innerLedgerCount(gray, width, height, head, upper);
        int lowerLedgers = innerLedgerCount(gray, width, height, head, lower);
        if (upperLedgers > 0 && lowerLedgers == 0) return upper;
        if (lowerLedgers > 0 && upperLedgers == 0) return lower;
        float gap = Math.min(upper.gap, lower.gap);
        int upward = attachedStemReach(labels, width, height, head, gap, true);
        int downward = attachedStemReach(labels, width, height, head, gap, false);
        int minimum = Math.max(3, Math.round(gap * .72f));
        int advantage = Math.max(2, Math.round(gap * .24f));
        if (upward >= minimum && upward >= downward + advantage) return upper;
        if (downward >= minimum && downward >= upward + advantage) return lower;
        return nearest;
    }

    /** A complete distant ledger chain can be stronger than proximity to another staff. */
    private static Staff printedLedgerOwner(
            byte[] gray, int width, int height, List<Staff> staffs, Component head) {
        if (gray == null) return null;
        Staff upper = null, lower = null;
        for (Staff staff : staffs) {
            if (head.centerY >= staff.top && head.centerY <= staff.bottom) return null;
            if (staff.bottom < head.centerY && (upper == null || staff.bottom > upper.bottom))
                upper = staff;
            if (staff.top > head.centerY && (lower == null || staff.top < lower.top)) lower = staff;
        }
        if (upper == null || lower == null) return null;
        int above = innerLedgerCount(gray, width, height, head, upper);
        int below = innerLedgerCount(gray, width, height, head, lower);
        float upperBottom = upper.pitchBottom + upper.pitchSlope * (head.centerX - width * .5f);
        float lowerTop =
                lower.pitchBottom
                        + lower.pitchSlope * (head.centerX - width * .5f)
                        - lower.pitchGap * 4;
        if (upper.pitchTrack != null && upper.pitchTrack.activeAt(head.centerX))
            upperBottom = upper.pitchTrack.at(head.centerX)[0];
        if (lower.pitchTrack != null && lower.pitchTrack.activeAt(head.centerX)) {
            float[] local = lower.pitchTrack.at(head.centerX);
            lowerTop = local[0] - local[1] * 4;
        }
        // A head half a space beyond the second ledger has only one *inner*
        // rule: the second rule lies beside its oval. Its continuous shaft
        // still proves the upper staff, including just beyond the close-staff band.
        if (above >= 1
                && below == 0
                && head.centerY - upperBottom >= upper.gap * 2.2f
                && head.centerY - upperBottom <= upper.gap * 3.2f) {
            int[] shaft =
                    attachedRawStem(gray, width, height, head, Math.min(upper.gap, lower.gap));
            if (shaft != null && shaft[2] < 0 && shaft[1] <= upperBottom) return upper;
        }
        // Close staves can surround a long ledger stem. Require that stem to reach
        // its own staff before allowing the distant ledger chain to win.
        if (head.centerY - upperBottom <= upper.gap * 1.8f
                || lowerTop - head.centerY <= lower.gap * 1.8f) {
            int[] stem = attachedRawStem(gray, width, height, head, Math.min(upper.gap, lower.gap));
            if (stem == null) return null;
            if (above >= 2 && below == 0 && stem[2] < 0 && stem[1] <= upperBottom) return upper;
            if (below >= 2 && above == 0 && stem[2] > 0 && stem[1] >= lowerTop) return lower;
            return null;
        }
        // Two inner rules can establish ownership; the rule beside/through the head is excluded.
        if (above >= 2 && above >= below + 2 && head.centerY - upper.bottom <= upper.gap * 6.75f)
            return upper;
        if (below >= 2
                && below >= above + 2
                && lower.top - head.centerY <= lower.gap * MAX_HEAD_LEDGER_GAPS) return lower;
        return null;
    }

    private static int innerLedgerCount(
            byte[] gray, int width, int height, Component head, Staff staff) {
        if (gray == null) return 0;
        float gap = staff.pitchGap;
        boolean above = head.centerY < staff.pitchBottom - gap * 4;
        float outer = above ? staff.pitchBottom - gap * 4 : staff.pitchBottom;
        float direction = above ? -1 : 1;
        int left = Math.max(0, Math.round(head.minX - gap * .3f));
        int right = Math.min(width - 1, Math.round(head.maxX + gap * .3f));
        int count = 0;
        for (float line = outer + direction * gap;
                direction * (head.centerY - line) > gap * .65f;
                line += direction * gap) {
            boolean found = false;
            for (int y = Math.max(0, Math.round(line - gap * .18f));
                    y <= Math.min(height - 1, Math.round(line + gap * .18f));
                    y++) {
                int ink = 0, leftInk = 0, rightInk = 0;
                for (int x = left; x <= right; x++)
                    if ((gray[y * width + x] & 255) < 165) {
                        ink++;
                        if (x < head.minX) leftInk++;
                        if (x > head.maxX) rightInk++;
                    }
                int margin = Math.max(1, Math.round(gap * .18f));
                if (ink >= (right - left + 1) * .85f
                        && leftInk >= margin
                        && rightInk >= margin
                        && shortLedgerRule(gray, width, y, head, gap)) found = true;
            }
            if (found) count++;
        }
        return count;
    }

    /** Ending brackets and long beams do not identify a ledger pitch. */
    private static boolean shortLedgerRule(
            byte[] gray, int width, int y, Component head, float gap) {
        int center = Math.max(0, Math.min(width - 1, Math.round(head.centerX)));
        if ((gray[y * width + center] & 255) >= 165) return false;
        int left = center, right = center, limit = Math.max(4, Math.round(gap * 4.5f));
        while (left > 0 && center - left <= limit && (gray[y * width + left - 1] & 255) < 165)
            left--;
        while (right + 1 < width
                && right - center <= limit
                && (gray[y * width + right + 1] & 255) < 165) right++;
        return right - left + 1 <= limit;
    }

    private static int attachedStemReach(
            byte[] labels, int width, int height, Component head, float gap, boolean upward) {
        int left = Math.max(0, Math.round(head.minX - gap * .42f));
        int right = Math.min(width - 1, Math.round(head.maxX + gap * .42f));
        int first = upward ? head.minY - 1 : head.maxY + 1;
        int limit =
                upward
                        ? Math.max(0, Math.round(head.centerY - gap * 4.8f))
                        : Math.min(height - 1, Math.round(head.centerY + gap * 4.8f));
        int reach = 0, blanks = 0;
        for (int y = first; upward ? y >= limit : y <= limit; y += upward ? -1 : 1) {
            boolean stem = false;
            for (int x = left; x <= right; x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST) {
                    stem = true;
                    break;
                }
            if (stem) {
                reach++;
                blanks = 0;
            } else if (++blanks > 1) break;
        }
        return reach;
    }

    private static Staff nearestStaff(List<Staff> staffs, float y, float maximumGapDistance) {
        Staff best = null;
        float distance = Float.MAX_VALUE;
        for (Staff staff : staffs) {
            float candidate =
                    y < staff.top ? staff.top - y : y > staff.bottom ? y - staff.bottom : 0;
            if (candidate < distance) {
                distance = candidate;
                best = staff;
            }
        }
        return best != null && distance <= best.gap * maximumGapDistance ? best : null;
    }

    /** A displaced second shares a stem between two ovals. Its combined bounds
     * are not one stemless head: require ledger evidence for both actual tones. */
    private static boolean hasHeadLedgerSupport(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            boolean roundedGrace) {
        List<Component> parts = sideBySideSeconds(labels, width, head, staff.gap);
        if (parts.isEmpty()) parts = List.of(head);
        for (Component part : parts)
            if (!hasLedgerInk(gray, width, height, part, staff.pitchGap, roundedGrace)
                    || !hasInnerLedgerInk(gray, width, height, part, staff, roundedGrace))
                return false;
        return true;
    }

    private static int[] ledgerInkLimits(
            byte[] gray, int width, int height, Component head, float gap) {
        int[] normal = {160, 185};
        if (gray == null) return normal;
        int[] ink = new int[256], paper = new int[256];
        int ni = 0, np = 0;
        for (int y = Math.max(0, head.minY); y <= Math.min(height - 1, head.maxY); y++)
            for (int x = Math.max(0, head.minX); x <= Math.min(width - 1, head.maxX); x++) {
                ink[gray[y * width + x] & 255]++;
                ni++;
            }
        int radius = Math.max(8, Math.round(gap * 2));
        for (int y = Math.max(0, head.minY - radius);
                y <= Math.min(height - 1, head.maxY + radius);
                y += 2)
            for (int x = Math.max(0, head.minX - radius);
                    x <= Math.min(width - 1, head.maxX + radius);
                    x += 2) {
                paper[gray[y * width + x] & 255]++;
                np++;
            }
        if (ni < 6 || np < 12) return normal;
        int dark = 0, light = 0, sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += ink[i];
            if (sum >= Math.max(1, ni / 4)) {
                dark = i;
                break;
            }
        }
        sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += paper[i];
            if (sum >= np * .9f) {
                light = i;
                break;
            }
        }
        if (dark < 70 || dark > 205 || light - dark < 45) return normal;
        // A pale antialiased curve on an otherwise crisp scan is not faded ink.
        // Bound the head estimate by the surrounding printed strokes.
        sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += paper[i];
            if (sum >= np * .05f) {
                dark = Math.min(dark, i);
                break;
            }
        }
        if (dark < 70 || dark > 205 || light - dark < 45) return normal;
        return new int[] {
            Math.min(225, Math.max(160, Math.round(dark + (light - dark) * 160f / 255))),
            Math.min(235, Math.max(185, Math.round(dark + (light - dark) * 185f / 255)))
        };
    }

    private static boolean hasLedgerInk(
            byte[] gray, int width, int height, Component head, float gap, boolean roundedGrace) {
        int[] limits = ledgerInkLimits(gray, width, height, head, gap);
        // Use the same local contrast for a ledger note's stem and horizontal rules.
        int stemInk = Math.min(limits[1], limits[0] + 10);
        boolean stemless =
                attachedRawStem(
                                gray,
                                width,
                                height,
                                head,
                                gap,
                                Math.max(1, Math.round(gap * .16f)),
                                stemInk)
                        == null;
        boolean reduced = roundedGrace || reducedLedgerHead(gray, width, height, head, gap);
        float minimum = ledgerRunMinimum(head, gap, reduced);
        int left = Math.max(0, Math.round(head.centerX - gap * 1.2f));
        int right = Math.min(width - 1, Math.round(head.centerX + gap * 1.2f));
        float headReach = !stemless && !reduced ? .9f : .65f;
        for (int y = Math.max(0, Math.round(head.centerY - gap * headReach));
                y <= Math.min(height - 1, Math.round(head.centerY + gap * headReach));
                y++) {
            int run = 0, strongInRun = 0;
            for (int x = left; x <= right; x++) {
                boolean strong = (gray[y * width + x] & 255) < limits[0];
                if (!strong && y > 0 && y + 1 < height)
                    strong =
                            (gray[(y - 1) * width + x] & 255) < limits[0]
                                    || (gray[(y + 1) * width + x] & 255) < limits[0];
                boolean dark = strong || (gray[y * width + x] & 255) < limits[1];
                if (!dark && y > 0 && y + 1 < height)
                    dark =
                            (gray[(y - 1) * width + x] & 255) < limits[1]
                                    || (gray[(y + 1) * width + x] & 255) < limits[1];
                run = dark ? run + 1 : 0;
                strongInRun = dark ? strongInRun + (strong ? 1 : 0) : 0;
                // A horizontal instruction arrow also ends in a head-like blob.
                // A stemless ledger note has rule ink on both sides of its oval.
                if (run >= minimum
                        && strongInRun >= minimum * .6f
                        && (!stemless && !reduced || (x - run + 1 < head.minX && x > head.maxX)))
                    return true;
            }
        }
        return false;
    }

    /** Rounded grace heads must survive ledger validation before ornament grouping.
     * Require a short, beamed prefix and a substantially larger following principal. */
    private static List<Component> roundedLedgerGraceHeads(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Component> heads,
            List<Staff> staffs) {
        List<Component> result = new ArrayList<>();
        if (gray == null) return result;
        List<Component> ordered = new ArrayList<>(heads);
        ordered.sort(Comparator.comparingDouble(h -> h.centerX));
        for (int i = 0; i < ordered.size(); i++) {
            Component first = ordered.get(i);
            Staff staff = nearestHeadStaff(staffs, first.centerY);
            if (staff == null || !roundedRawGraceHead(labels, gray, width, height, first, staff))
                continue;
            List<Component> prefix = new ArrayList<>();
            prefix.add(first);
            Component previous = first;
            for (int j = i + 1; j < ordered.size(); j++) {
                Component next = ordered.get(j);
                if (nearestHeadStaff(staffs, next.centerY) != staff) continue;
                float dx = next.centerX - previous.centerX;
                if (dx < staff.pitchGap * .65f
                        || dx > staff.pitchGap * 2.8f
                        || Math.abs(next.centerY - previous.centerY) > staff.pitchGap * 2.5f) break;
                if (roundedRawGraceHead(labels, gray, width, height, next, staff)
                        && graceStemsShareBeam(
                                labels, gray, width, height, previous, next, staff.pitchGap)) {
                    prefix.add(next);
                    previous = next;
                    continue;
                }
                if (prefix.size() >= 2
                        && next.maxX - next.minX + 1 > staff.pitchGap * 1.05f
                        && prefix.stream().allMatch(head -> next.area > head.area * 1.65f))
                    result.addAll(prefix);
                break;
            }
        }
        return result;
    }

    private static boolean graceStemsShareBeam(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component first,
            Component next,
            float gap) {
        int[] a = attachedRawStem(gray, width, height, first, gap * .65f),
                b = attachedRawStem(gray, width, height, next, gap * .65f);
        if (a == null || b == null || a[2] != b[2] || b[0] - a[0] < gap * .6f) return false;
        for (int offset = 0; offset <= Math.round(gap * .8f); offset++) {
            int hits = 0, clear = 0, samples = 0;
            for (int x = a[0] + 2; x <= b[0] - 2; x++) {
                float t = (x - a[0]) / (float) (b[0] - a[0]);
                int y = Math.round(a[1] + t * (b[1] - a[1]) - a[2] * offset);
                boolean ink = false, nonStaff = false;
                for (int dy = -1; dy <= 1; dy++)
                    if (y + dy >= 0 && y + dy < height) {
                        int at = (y + dy) * width + x;
                        if ((gray[at] & 255) <= 165) {
                            ink = true;
                            if (labels == null
                                    || labels[at] != OmrMeasurePostProcessor.STAFF
                                            && labels[at] != OmrMeasurePostProcessor.NOTEHEAD)
                                nonStaff = true;
                        }
                    }
                samples++;
                if (ink) hits++;
                if (nonStaff) clear++;
            }
            if (samples >= 6 && hits >= samples * .85f && clear >= samples * .65f) return true;
        }
        return false;
    }

    private static boolean roundedRawGraceHead(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        if (head.maxX - head.minX + 1 > Math.round(staff.pitchGap * 1.10f)
                || head.maxY - head.minY + 1 > Math.round(staff.pitchGap)
                || head.area > staff.pitchGap * staff.pitchGap * .80f) return false;
        int[] stem = attachedRawStem(gray, width, height, head, staff.pitchGap * .65f);
        if (stem == null || Math.abs(stem[1] - head.centerY) > staff.pitchGap * 3.1f) return false;
        int beams = detectBeamCount(labels, gray, width, height, head, staff);
        return beams > 0
                && detectUnbeamedDuration(labels, gray, width, height, head, staff.pitchGap, beams)
                        < ScoreNoteEvent.DURATION_HALF;
    }

    /** Grace-sized heads use shorter ledger rules but retain the normal staff spacing. */
    private static boolean reducedLedgerHead(
            byte[] gray, int width, int height, Component head, float gap) {
        if (head.maxX - head.minX + 1 > Math.round(gap * 1.05f)
                || head.maxY - head.minY + 1 > Math.round(gap * .95f)
                || head.area > gap * gap * .72f
                || attachedRawStem(gray, width, height, head, gap * .65f) == null) return false;
        if (head.maxX - head.minX + 1 <= Math.round(gap * .95f) && head.area <= gap * gap * .60f)
            return true;
        // Slightly wider grace masks need the printed oblique oval. Rounded
        // blobs still require the separate shared-beam/prefix proof.
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        int count = 0;
        for (int y = head.minY; y <= head.maxY; y++)
            for (int x = head.minX; x <= head.maxX; x++)
                if ((gray[y * width + x] & 255) < 165) {
                    double dx = x - head.minX, dy = y - head.minY;
                    count++;
                    sx += dx;
                    sy += dy;
                    sxx += dx * dx;
                    syy += dy * dy;
                    sxy += dx * dy;
                }
        if (count < 8) return false;
        double variance = (sxx - sx * sx / count) * (syy - sy * sy / count);
        return variance > 0 && (sxy - sx * sy / count) / Math.sqrt(variance) < -.18;
    }

    private static float ledgerRunMinimum(Component head, float gap, boolean reduced) {
        return reduced
                ? Math.max(
                        gap,
                        head.maxX - head.minX + 1 + 2 * Math.max(1, (int) Math.floor(gap * .1f)))
                : Math.round(gap * 1.5f);
    }

    private static boolean hasInnerLedgerInk(
            byte[] gray, int width, int height, Component head, Staff staff, boolean roundedGrace) {
        // Beyond two staff spaces, real notation needs another ledger toward
        // the staff. One instruction arrow or underline is insufficient.
        float gap = staff.pitchGap;
        boolean stemless = attachedRawStem(gray, width, height, head, gap) == null;
        int[] limits = ledgerInkLimits(gray, width, height, head, staff.pitchGap);
        boolean reduced = roundedGrace || reducedLedgerHead(gray, width, height, head, gap);
        float minimum = ledgerRunMinimum(head, gap, reduced);
        float direction = head.centerY < staff.top ? 1 : -1;
        float distance =
                head.centerY < staff.top ? staff.top - head.centerY : head.centerY - staff.bottom;
        // A remote head needs more than a pair of nearby horizontal strokes.
        // A stemless oval at the third ledger needs both inner ledgers;
        // a nearby lettering stroke cannot supply that stack. Keep the
        // wider legacy tolerance when a shaft independently owns the head.
        int required =
                distance > gap * (stemless && !roundedGrace && !reduced ? 2.8f : 3.5f) ? 2 : 1;
        int left = Math.max(0, Math.round(head.centerX - gap * 1.2f));
        int right = Math.min(width - 1, Math.round(head.centerX + gap * 1.2f));
        float totalStrongSupport = 0;
        for (int inner = 1; inner <= required; inner++) {
            float center = head.centerY + direction * gap * inner;
            float bestSupport = 0;
            for (int y = Math.max(1, Math.round(center - gap * .55f));
                    y <= Math.min(height - 2, Math.round(center + gap * .55f));
                    y++) {
                int run = 0, strongInRun = 0;
                for (int x = left; x <= right; x++) {
                    boolean strong =
                            (gray[y * width + x] & 255) < limits[0]
                                    || (gray[(y - 1) * width + x] & 255) < limits[0]
                                    || (gray[(y + 1) * width + x] & 255) < limits[0];
                    boolean dark =
                            strong
                                    || (gray[y * width + x] & 255) < limits[1]
                                    || (gray[(y - 1) * width + x] & 255) < limits[1]
                                    || (gray[(y + 1) * width + x] & 255) < limits[1];
                    run = dark ? run + 1 : 0;
                    strongInRun = dark ? strongInRun + (strong ? 1 : 0) : 0;
                    // Faded portions may complete a printed rule, but pale underlines alone
                    // cannot supply the additional ledger required for a remote note.
                    if (run >= minimum && (!reduced || (x - run + 1 < head.minX && x > head.maxX)))
                        bestSupport = Math.max(bestSupport, Math.min(1, strongInRun / minimum));
                }
            }
            // A faded middle rule can be supported by the next complete rule,
            // but every required rule must retain a continuous visible span
            // and its own dark core. One strong underline cannot replace it.
            if (bestSupport < (inner < required && !reduced ? .4f : .6f)) return false;
            totalStrongSupport += bestSupport;
        }
        return totalStrongSupport >= required * .6f;
    }

    /** Rejected slur islands must not continue masking a real tie's curve.
     * Keep the caller's segmentation and every retained head unchanged. */
    private static byte[] tieLabelsWithoutSlurHeads(
            byte[] labels, int width, List<Component> rejected, List<Component> retained) {
        if (rejected.isEmpty()) return labels;
        byte[] result = labels.clone();
        for (Component head : rejected)
            for (int y = head.minY; y <= head.maxY; y++)
                for (int x = head.minX; x <= head.maxX; x++) {
                    if (result[y * width + x] != OmrMeasurePostProcessor.NOTEHEAD) continue;
                    boolean protectedHead = false;
                    for (Component other : retained)
                        if (x >= other.minX
                                && x <= other.maxX
                                && y >= other.minY
                                && y <= other.maxY) {
                            protectedHead = true;
                            break;
                        }
                    if (!protectedHead) result[y * width + x] = OmrMeasurePostProcessor.SYMBOL;
                }
        return result;
    }

    /** A thin slur fragment joined to a staff line can form a false semantic oval.
     * Require a stem for this unusually flat shape; normal whole notes are taller. */

    /** A short tie clipped by a staff rule can look like a complete small oval.
     * Require two larger stemmed endpoints and a continuous arc through it. */
    private static List<Component> shortTieBowlHeads(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Component> heads,
            List<Staff> staffs) {
        List<Component> result = new ArrayList<>();
        if (gray == null) return result;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.maxX - head.minX + 1 > gap * 1.4f
                    || head.maxY - head.minY + 1 > gap * .7f
                    || head.area > gap * gap * .6f
                    || attachedRawStem(gray, width, height, head, gap * .65f) != null) continue;
            Component left = null, right = null;
            for (Component other : heads) {
                float dx = other.centerX - head.centerX,
                        dy = Math.abs(other.centerY - head.centerY);
                if (other == head
                        || Math.abs(dx) < gap * .9f
                        || Math.abs(dx) > gap * 3.3f
                        || dy < gap * .4f
                        || dy > gap * 1.4f
                        || other.area < head.area * 1.8f
                        || other.maxX - other.minX + 1 < gap * 1.1f
                        || attachedRawStem(gray, width, height, other, gap * .65f) == null)
                    continue;
                if (dx < 0 && (left == null || other.centerX > left.centerX)) left = other;
                if (dx > 0 && (right == null || other.centerX < right.centerX)) right = other;
            }
            if (left == null
                    || right == null
                    || Math.abs(left.centerY - right.centerY) > gap * .2f
                    || head.minX <= left.maxX
                    || head.maxX >= right.minX) continue;
            int a = left.maxX + 1, b = right.minX - 1;
            if (b - a <= 2 || b - a + 1 > gap * 4.5f || right.centerX - left.centerX < gap * 1.8f)
                continue;
            List<Component> retained = new ArrayList<>(heads);
            retained.remove(head);
            byte[] arcLabels = tieLabelsWithoutSlurHeads(labels, width, List.of(head), retained);
            float center = (left.centerY + right.centerY) * .5f;
            boolean proved =
                    hasContinuousTieArc(arcLabels, gray, width, height, a, b, center, gap, head);
            // A compact tie returns under the heads. Its full dark curve must
            // pass through the small island, with both stemmed endpoints retained.
            if (!proved && b - a + 1 < gap * 1.3f) {
                int overlap = Math.round(gap * .6f), step = Math.max(1, Math.round(gap * .1f));
                int arcLeft = Math.max(Math.round(left.centerX), a - overlap);
                int arcRight = Math.min(Math.round(right.centerX), b + overlap);
                int count = (int) Math.ceil(gap * .75f / step);
                for (int first = 0; first <= count && !proved; first++)
                    for (int last = 0; last <= count && !proved; last++) {
                        int from = arcLeft + first * step, to = arcRight - last * step;
                        if (to - from >= gap)
                            proved =
                                    hasContinuousTieArc(
                                            arcLabels, gray, width, height, from, to, center, gap,
                                            head);
                    }
            }
            if (proved) result.add(head);
        }
        return result;
    }

    /** The small curved connector below a grace and its principal note has no
     * independent stem. Its unequal endpoints are not the geometry of a tie. */
    private static List<Component> graceSlurHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> result = new ArrayList<>();
        if (gray == null) return result;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float g = staff.gap;
            if (head.area > g * g * .65f
                    || head.maxX - head.minX + 1 > g * 1.15f
                    || head.maxY - head.minY + 1 > g * .85f) continue;
            int threshold = slurInkThreshold(gray, width, height, head, g);
            if (attachedRawStem(
                            gray,
                            width,
                            height,
                            head,
                            g * .65f,
                            Math.max(1, Math.round(g * .1f)),
                            threshold)
                    != null) continue;
            boolean grace = false, principal = false;
            for (Component other : heads) {
                float dx = other.centerX - head.centerX, dy = head.centerY - other.centerY;
                if (other == head
                        || nearestHeadStaff(staffs, other.centerY) != staff
                        || dy < g * .25f
                        || dy > g * 1.8f) continue;
                if (dx < -g * .35f
                        && dx > -g * 1.6f
                        && other.area >= g * g * .35f
                        && other.maxX - other.minX + 1 <= g * 1.1f
                        && attachedRawStem(gray, width, height, other, g * .65f) != null)
                    grace = true;
                if (dx > g * .5f
                        && dx < g * 2.8f
                        && other.area >= Math.max(head.area * 1.8f, g * g * .8f)
                        && other.maxX - other.minX + 1 >= g
                        && attachedRawStem(gray, width, height, other, g * .65f) != null)
                    principal = true;
            }
            if (grace
                    && principal
                    && (rawSlurBowl(gray, width, height, head, g, threshold, true)
                            || rawSlurBowl(
                                    gray,
                                    width,
                                    height,
                                    head,
                                    g,
                                    Math.min(100, Math.round(threshold * .7f)),
                                    true)
                            || GraceConnectorInk.isHook(
                                    gray,
                                    width,
                                    height,
                                    head.minX,
                                    head.minY,
                                    head.maxX,
                                    head.maxY,
                                    head.centerX,
                                    head.centerY,
                                    head.area,
                                    g,
                                    Math.min(100, Math.round(threshold * .7f)))
                            || GraceConnectorInk.isHook(
                                    gray,
                                    width,
                                    height,
                                    head.minX,
                                    head.minY,
                                    head.maxX,
                                    head.maxY,
                                    head.centerX,
                                    head.centerY,
                                    head.area,
                                    g,
                                    Math.min(90, Math.round(threshold * .62f)))
                            || GraceConnectorInk.isHook(
                                    gray,
                                    width,
                                    height,
                                    head.minX,
                                    head.minY,
                                    head.maxX,
                                    head.maxY,
                                    head.centerX,
                                    head.centerY,
                                    head.area,
                                    g,
                                    Math.min(65, Math.round(threshold * .45f))))) result.add(head);
        }
        return result;
    }

    /** Small semantic islands at a beam endpoint can borrow the actual note's stem.
     * Reject only a thin beam tip on a stem already attached to a larger head. */
    private static List<Component> beamJunctionHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            boolean pairedCorner =
                    head.maxX - head.minX + 1 <= gap * .65f
                            && head.maxY - head.minY + 1 <= gap * 1.1f
                            && head.area <= gap * gap * .36f;
            boolean thinCorner =
                    head.maxX - head.minX + 1 <= gap * .85f
                            && head.maxY - head.minY + 1 <= gap * .60f
                            && head.area <= gap * gap * .36f;
            if (!thinCorner && !pairedCorner) continue;
            for (Component main : heads) {
                if (main == head
                        || main.area < head.area * 3f
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || Math.abs(main.centerX - head.centerX) > gap
                        || Math.abs(main.centerY - head.centerY) < gap * 2
                        || Math.abs(main.centerY - head.centerY) > gap * 6) continue;
                int[] stem = attachedRawStem(gray, width, height, main, gap);
                if (stem == null)
                    stem =
                            attachedRawStem(
                                    gray,
                                    width,
                                    height,
                                    main,
                                    gap,
                                    Math.max(1, Math.round(gap * .3f)),
                                    205);
                if (stem == null
                        || stem[0] < head.minX - gap * .2f
                        || stem[0] > head.maxX + gap * .2f
                        || Math.abs(stem[1] - head.centerY) > gap * (pairedCorner ? 1.1f : .45f))
                    continue;
                boolean owned = false;
                for (Component other : heads) {
                    if (other == head
                            || other == main
                            || other.area < Math.max(head.area * 3f, gap * gap)
                            || nearestHeadStaff(staffs, other.centerY) != staff
                            || Math.abs(other.centerX - main.centerX)
                                    > gap * (pairedCorner ? 16 : 6)) continue;
                    int[] partner = attachedRawStem(gray, width, height, other, gap);
                    if (pairedCorner
                            && StemOwnedBeamTip.pairedCorner(
                                    gray,
                                    width,
                                    height,
                                    stem,
                                    partner,
                                    head.centerX,
                                    head.centerY,
                                    head.maxX - head.minX + 1,
                                    head.maxY - head.minY + 1,
                                    head.area,
                                    gap)) {
                        owned = true;
                        break;
                    }
                    if (StemOwnedBeamTip.matches(
                            gray,
                            width,
                            height,
                            stem,
                            partner,
                            head.centerX,
                            head.centerY,
                            head.maxX - head.minX + 1,
                            head.maxY - head.minY + 1,
                            head.area,
                            gap)) {
                        owned = true;
                        break;
                    }
                }
                if (owned
                        || thinCorner
                                && narrowBeamTip(gray, width, height, stem[0], head.centerY, gap)) {

                    rejected.add(head);
                    break;
                }
            }
        }
        return rejected;
    }

    private static List<Component> outlinedHookHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.maxX - head.minX + 1 > gap * .65f
                    || head.maxY - head.minY + 1 > gap * .6f
                    || head.area > gap * gap * .25f
                    || attachedRawStem(gray, width, height, head, gap) != null) continue;
            for (Component main : heads) {
                if (main == head
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || main.area < head.area * 3
                        || Math.abs(main.centerX - head.centerX) > gap * 1.5f) continue;
                int[] stem = attachedRawStem(gray, width, height, main, gap);
                if (OutlinedHookHead.matches(
                        gray,
                        width,
                        height,
                        head.centerX,
                        head.centerY,
                        head.maxX - head.minX + 1,
                        head.maxY - head.minY + 1,
                        head.area,
                        main.centerX,
                        main.centerY,
                        main.area,
                        stem,
                        gap)) {
                    rejected.add(head);
                    break;
                }
            }
        }
        return rejected;
    }

    /** Reject a tiny beam-tip island only when its own attached shaft leads to a
     * sustained narrow beam with no oval bulge. An independent head thickens it. */
    private static List<Component> isolatedBeamTipHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.maxX - head.minX + 1 > gap * .85f
                    || head.maxY - head.minY + 1 > gap * .6f
                    || head.area > gap * gap * .36f) continue;
            boolean anchored = false;
            for (Component other : heads)
                if (other != head
                        && nearestHeadStaff(staffs, other.centerY) == staff
                        && Math.abs(other.centerX - head.centerX) > gap * .6f
                        && Math.abs(other.centerX - head.centerX) < gap * 4f
                        && Math.abs(other.centerY - head.centerY) < gap * 4f
                        && other.area >= head.area * .8f) anchored = true;
            if (!anchored) continue;
            int[] stem = attachedRawStem(gray, width, height, head, gap);
            if (stem != null
                    && (stem[1] < staff.top - gap * .3f || stem[1] > staff.bottom + gap * .3f)
                    && narrowBeamTip(gray, width, height, stem[0], head.centerY, gap * .9f))
                rejected.add(head);
        }
        return rejected;
    }

    /** A small curved flag below an upper grace head can be labelled as a
     * second notehead where the staff begins. Require a larger aligned head. */
    private static List<Component> trailingGraceFlagHeads(
            List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.centerY < staff.top - gap * .1f
                    || head.centerY > staff.top + gap * .6f
                    || head.area > gap * gap * .35f
                    || head.maxX - head.minX + 1 > gap * .75f
                    || head.maxY - head.minY + 1 > gap * .95f) continue;
            for (Component upper : heads) {
                if (upper == head
                        || nearestHeadStaff(staffs, upper.centerY) != staff
                        || upper.centerY > staff.top - gap * .75f
                        || upper.centerY < staff.top - gap * 2.1f
                        || head.centerY - upper.centerY < gap * 1.55f
                        || head.centerY - upper.centerY > gap * 2.1f
                        || Math.abs(head.centerX - upper.centerX) > gap * .2f
                        || upper.area < head.area * 1.2f
                        || upper.maxX - upper.minX + 1 < (head.maxX - head.minX + 1) * 1.1f
                        || upper.maxY - upper.minY + 1 > gap * .8f) continue;
                rejected.add(head);
                break;
            }
        }
        return rejected;
    }

    /** A stemless semantic island inside a diagonal flag is not a notehead.
     * Require a long uninterrupted sloping print instead of an oval core. */
    private static List<Component> inclinedFlagTipHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.area < gap * gap * .35f
                    || head.area > gap * gap * .55f
                    || head.maxX - head.minX + 1 < gap * .9f
                    || head.maxX - head.minX + 1 > gap * 1.15f
                    || head.maxY - head.minY + 1 < gap * .6f
                    || head.maxY - head.minY + 1 > gap * .9f
                    || head.centerY < staff.top + gap * 1.2f
                    || head.centerY > staff.top + gap * 2.3f
                    || attachedRawStem(gray, width, height, head, gap) != null) continue;
            int first = Math.round(gap * .4f), last = Math.round(gap * 1.8f);
            boolean diagonal = false;
            for (int side : new int[] {-1, 1})
                for (int vertical : new int[] {-1, 1})
                    for (float slope : new float[] {.35f, .4f, .45f, .5f, .55f, .6f}) {
                        int supported = 0, total = 0;
                        for (int d = first; d <= last; d++) {
                            int x = Math.round(head.centerX) + side * d;
                            int y = Math.round(head.centerY) + vertical * Math.round(slope * d);
                            if (x < 0 || x >= width || y < 0 || y >= height) break;
                            total++;
                            if ((gray[y * width + x] & 255) < 155) supported++;
                        }
                        if (total >= gap * 1.3f && supported >= total * .94f) diagonal = true;
                    }
            if (diagonal) rejected.add(head);
        }
        return rejected;
    }

    /** A small stemless mask fragment can sit inside a single printed beam.
     * Two larger heads must independently anchor both ends of a thin beam. */
    private static List<Component> singleBeamInteriorHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.area > gap * gap * .36f
                    || head.maxX - head.minX + 1 > gap * .85f
                    || head.maxY - head.minY + 1 > gap * .65f
                    || attachedRawStem(gray, width, height, head, gap) != null) continue;
            List<int[]> stems = new ArrayList<>();
            for (Component main : heads) {
                if (main == head
                        || main.area < head.area * 3
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || Math.abs(main.centerX - head.centerX) > gap * 6
                        || Math.abs(main.centerY - head.centerY) < gap * 2
                        || Math.abs(main.centerY - head.centerY) > gap * 6) continue;
                int[] stem = attachedRawStem(gray, width, height, main, gap);
                if (stem != null && Math.abs(stem[1] - head.centerY) < gap * 1.5f) stems.add(stem);
            }
            boolean found = false;
            for (int[] left : stems)
                for (int[] right : stems) {
                    if (found
                            || left[2] != right[2]
                            || left[0] >= head.minX - gap * .2f
                            || right[0] <= head.maxX + gap * .2f
                            || right[0] - left[0] < gap * 2
                            || right[0] - left[0] > gap * 6) continue;
                    if (thinBeamBetween(
                                    gray,
                                    width,
                                    height,
                                    left[0],
                                    left[1],
                                    right[0],
                                    right[1],
                                    head.centerX,
                                    head.centerY,
                                    gap)
                            || shortParallelBeam(gray, width, height, left, right, head, gap)) {
                        rejected.add(head);
                        found = true;
                    }
                }
        }
        return rejected;
    }

    /** A secondary beam can end between the two stems of its complete primary beam. */
    private static boolean shortParallelBeam(
            byte[] gray,
            int width,
            int height,
            int[] left,
            int[] right,
            Component head,
            float gap) {
        float slope = (right[1] - left[1]) / (float) (right[0] - left[0]);
        float primaryY = left[1] + slope * (head.centerX - left[0]);
        float separation = (primaryY - head.centerY) * left[2];
        if (separation < gap * .5f
                || separation > gap * 1.35f
                || !thinBeamBetween(
                        gray,
                        width,
                        height,
                        left[0],
                        left[1],
                        right[0],
                        right[1],
                        head.centerX,
                        primaryY,
                        gap)) return false;
        int margin = Math.max(2, Math.round(gap * .18f)), radius = Math.round(gap);
        for (int[] end : new int[][] {left, right}) {
            float distance = Math.abs(end[0] - head.centerX);
            if (distance < gap * .65f || distance > gap * 2.3f) continue;
            int direction = end[0] > head.centerX ? 1 : -1;
            int start = Math.round(head.centerX), finish = end[0] - direction * margin;
            int low = Integer.MAX_VALUE, high = 0, columns = 0;
            boolean valid = true;
            for (int x = start; direction * (finish - x) >= 0; x += direction) {
                int y = Math.round(head.centerY + slope * (x - head.centerX));
                if (x < 0
                        || x >= width
                        || y - radius < 0
                        || y + radius >= height
                        || (gray[y * width + x] & 255) >= 165) {
                    valid = false;
                    break;
                }
                int top = y, bottom = y;
                while (top > y - radius && (gray[(top - 1) * width + x] & 255) < 165) top--;
                while (bottom < y + radius && (gray[(bottom + 1) * width + x] & 255) < 165)
                    bottom++;
                int span = bottom - top + 1;
                if (span < gap * .35f || span > gap * .8f) {
                    valid = false;
                    break;
                }
                low = Math.min(low, span);
                high = Math.max(high, span);
                columns++;
            }
            if (!valid || columns < gap * .65f || high - low > gap * .2f) continue;
            // The short stroke must actually reach the already established stem.
            for (int x = finish; direction * (end[0] - x) >= 0; x += direction) {
                int y = Math.round(head.centerY + slope * (x - head.centerX));
                if ((gray[y * width + x] & 255) >= 165) {
                    valid = false;
                    break;
                }
            }
            if (valid) return true;
        }
        return false;
    }

    static boolean thinBeamBetween(
            byte[] gray,
            int width,
            int height,
            int left,
            int leftY,
            int right,
            int rightY,
            float headX,
            float headY,
            float gap) {
        if (gray == null || gap < 8 || right - left < gap * 2) return false;
        float slope = (rightY - leftY) / (float) (right - left);
        if (Math.abs(slope) > .6f) return false;
        int margin = Math.max(3, Math.round(gap * .3f)), core = Math.max(1, Math.round(gap * .08f));
        int search = Math.max(2, Math.round(gap * .45f)), radius = Math.round(gap);
        for (int offset = -search; offset <= search; offset++) {
            if (Math.abs(leftY + offset + slope * (headX - left) - headY) > gap * .25f) continue;
            int valid = 0, total = 0, near = 0, nearValid = 0;
            for (int x = left + margin; x <= right - margin; x++) {
                int y = Math.round(leftY + offset + slope * (x - left));
                if (x < 0 || x >= width || y - radius < 0 || y + radius >= height) return false;
                total++;
                boolean ink = true;
                for (int d = -core; d <= core; d++)
                    if ((gray[(y + d) * width + x] & 255) >= 165) ink = false;
                int top = y, bottom = y;
                while (top > y - radius && (gray[(top - 1) * width + x] & 255) < 165) top--;
                while (bottom < y + radius && (gray[(bottom + 1) * width + x] & 255) < 165)
                    bottom++;
                int span = bottom - top + 1;
                boolean thin = ink && span >= gap * .25f && span <= gap * .8f;
                if (thin) valid++;
                if (Math.abs(x - headX) <= gap * .4f) {
                    near++;
                    if (thin) nearValid++;
                }
            }
            if (total >= gap && valid >= total * .93f && near >= 3 && nearValid == near)
                return true;
        }
        return false;
    }

    private static byte[] withoutBeamHeadIslands(
            byte[] labels, int width, List<Component> rejected) {
        if (rejected.isEmpty()) return labels;
        byte[] result = labels.clone();
        for (Component head : rejected)
            for (int y = head.minY; y <= head.maxY; y++)
                for (int x = head.minX; x <= head.maxX; x++)
                    if (result[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD)
                        result[y * width + x] = 0;
        return result;
    }

    /** A small finger numeral can sit directly beneath its beamed note. It has
     * no stem of its own, while the aligned full-sized note has an upward stem.
     * Requiring that ownership protects independently stemmed low notes. */
    private static List<Component> detachedFingeringHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.centerY < staff.bottom + gap * .3f
                    || head.centerY > staff.bottom + gap * 1.1f
                    || head.area > gap * gap * .6f
                    || head.maxX - head.minX + 1 > gap * 1.05f
                    || head.maxY - head.minY + 1 > gap * .85f
                    || attachedRawStem(gray, width, height, head, gap) != null) continue;
            for (Component main : heads) {
                if (main == head
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || Math.abs(main.centerX - head.centerX) > gap * .3f
                        || head.centerY - main.centerY < gap * 2.2f
                        || head.centerY - main.centerY > gap * 3.4f
                        || main.area < Math.max(head.area * 2f, gap * gap)) continue;
                int[] stem = attachedRawStem(gray, width, height, main, gap);
                if (stem != null && stem[2] < 0) {
                    rejected.add(head);
                    break;
                }
            }
        }
        return rejected;
    }

    /** Two complete notes can bound a short pair of merged beams. A head-sized
     * semantic island inside that strip belongs to the beams even when one
     * neighboring stem passes through the island's raw-image column. */
    private static List<Component> shortPairedMergedBeamHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.area > gap * gap * .55f
                    || head.maxX - head.minX + 1 > gap * 1.1f
                    || head.maxY - head.minY + 1 > gap * .9f) continue;
            List<int[]> left = new ArrayList<>(), right = new ArrayList<>();
            for (Component main : heads) {
                if (main == head
                        || main.area < Math.max(head.area * 2.2f, gap * gap * 1.05f)
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || Math.abs(main.centerX - head.centerX) > gap * 3.5f
                        || Math.abs(main.centerY - head.centerY) < gap * 2
                        || Math.abs(main.centerY - head.centerY) > gap * 6) continue;
                int[] stem = attachedRawStem(gray, width, height, main, gap);
                if (stem == null || Math.abs(stem[1] - head.centerY) > gap * 1.5f) continue;
                if (stem[0] < head.minX - gap * .25f) left.add(stem);
                if (stem[0] > head.maxX + gap * .25f) right.add(stem);
            }
            boolean found = false;
            for (int[] a : left)
                for (int[] b : right) {
                    if (a[2] != b[2] || b[0] - a[0] < gap * 2 || b[0] - a[0] > gap * 6) continue;
                    if (shortMergedBeamBetween(gray, width, height, a, b, head, staff))
                        found = true;
                }
            if (found) rejected.add(head);
        }
        return rejected;
    }

    private static List<Component> narrowPairedBeamHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.area > gap * gap * .4f
                    || head.maxX - head.minX + 1 > gap * .8f
                    || head.maxY - head.minY + 1 > gap * .8f) continue;
            List<int[]> left = new ArrayList<>(), right = new ArrayList<>();
            for (Component main : heads) {
                if (main == head
                        || main.area < Math.max(head.area * 2.5f, gap * gap * .9f)
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || Math.abs(main.centerX - head.centerX) > gap * 2.5f
                        || Math.abs(main.centerY - head.centerY) < gap * 1.3f
                        || Math.abs(main.centerY - head.centerY) > gap * 5) continue;
                int[] stem = attachedRawStem(gray, width, height, main, gap);
                if (stem == null || Math.abs(stem[1] - head.centerY) > gap * 2) continue;
                if (stem[0] < head.minX) left.add(stem);
                if (stem[0] > head.maxX) right.add(stem);
            }
            for (int[] a : left)
                for (int[] b : right)
                    if (narrowParallelBeamInk(
                                    gray, width, height, a, b, head.centerX, head.centerY, gap)
                            && !rejected.contains(head)) rejected.add(head);
        }
        return rejected;
    }

    static boolean narrowParallelBeamInk(
            byte[] gray,
            int width,
            int height,
            int[] left,
            int[] right,
            float cx,
            float cy,
            float gap) {
        int span = right[0] - left[0];
        if (left[2] != right[2] || span < gap * 1.2f || span > gap * 2.1f) return false;
        float slope = (right[1] - left[1]) / (float) span;
        if (Math.abs(slope) > 1.1f) return false;
        int first = left[0] + Math.max(2, Math.round(gap * .2f));
        int last = right[0] - Math.max(2, Math.round(gap * .2f));
        int valid = 0, total = 0;
        for (int x = first; x <= last; x++) {
            int predicted = Math.round(cy + slope * (x - cx));
            int radius = Math.round(gap * 1.3f), start = -1, previousEnd = -1, previousSize = 0;
            boolean paired = false, owns = false;
            if (x < 0 || x >= width || predicted - radius < 0 || predicted + radius >= height)
                return false;
            for (int y = predicted - radius; y <= predicted + radius + 1; y++) {
                boolean ink = y <= predicted + radius && (gray[y * width + x] & 255) < 165;
                if (ink) {
                    if (start < 0) start = y;
                } else if (start >= 0) {
                    int size = y - start;
                    if (size >= gap * .3f && size <= gap * .8f) {
                        if (Math.abs((start + y - 1) * .5f - predicted) <= gap * .35f) owns = true;
                        if (previousSize >= gap * .3f
                                && previousSize <= gap * .8f
                                && start - previousEnd - 1 >= 1
                                && start - previousEnd - 1 <= gap * .65f) paired = true;
                    }
                    previousSize = size;
                    previousEnd = y - 1;
                    start = -1;
                }
            }
            total++;
            if (paired && owns) valid++;
        }
        return total >= gap * .7f && valid >= total * .65f;
    }

    /** A tiny mask island may sit on one core of a paired beam, close to its end.
     * The seam between the two cores is offset from the island itself. */
    private static List<Component> offsetParallelBeamIslandHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.area > gap * gap * .25f
                    || head.maxX - head.minX + 1 > gap * .75f
                    || head.maxY - head.minY + 1 > gap * .45f
                    || attachedRawStem(gray, width, height, head, gap) != null) continue;
            if (ParallelBeamTip.matches(
                            gray, width, height, head.centerX, head.centerY + gap * .5f, gap)
                    || ParallelBeamTip.matches(
                            gray, width, height, head.centerX, head.centerY - gap * .5f, gap))
                rejected.add(head);
        }
        return rejected;
    }

    private static boolean shortMergedBeamBetween(
            byte[] gray,
            int width,
            int height,
            int[] left,
            int[] right,
            Component head,
            Staff staff) {
        float gap = staff.gap;
        int first = left[0] + Math.round(gap * .4f), last = right[0] - Math.round(gap * .65f);
        if (last - first < gap * 1.7f) return false;
        float slope = (right[1] - left[1]) / (float) (right[0] - left[0]);
        if (Math.abs(slope) > .4f) return false;
        int valid = 0,
                total = 0,
                leftValid = 0,
                rightValid = 0,
                paired = 0,
                radius = Math.round(gap * 1.35f);
        for (int x = first; x <= last; x++) {
            int predicted = Math.round(head.centerY + slope * (x - head.centerX));
            if (x < 0 || x >= width || predicted - radius < 0 || predicted + radius >= height)
                return false;
            total++;
            int top = height, bottom = -1, runStart = -1, previousEnd = -1, previousSize = 0;
            boolean twoCores = false;
            for (int y = predicted - radius; y <= predicted + radius + 1; y++) {
                boolean ink =
                        y <= predicted + radius
                                && (Math.abs(y - predicted) <= gap * .65f
                                        || offHeaderStaffLine(y, staff.top, gap))
                                && (gray[y * width + x] & 255) < 165;
                if (ink) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    if (runStart < 0) runStart = y;
                } else if (runStart >= 0) {
                    int size = y - runStart;
                    if (previousSize >= gap * .3f
                            && size >= gap * .3f
                            && runStart - previousEnd - 1 <= gap * .3f) twoCores = true;
                    previousEnd = y - 1;
                    previousSize = size;
                    runStart = -1;
                }
            }
            if (bottom < top) continue;
            int span = bottom - top + 1;
            if (span < gap * .85f
                    || span > gap * 1.8f
                    || Math.abs((top + bottom) * .5f - predicted) > gap * .35f) continue;
            valid++;
            if (x < head.centerX) leftValid++;
            else rightValid++;
            if (twoCores) paired++;
        }
        return total >= gap * 1.7f
                && valid >= total * .75f
                && paired >= gap * .45f
                && leftValid >= gap * .35f
                && rightValid >= gap * .55f;
    }

    /** Blurred parallel beams can merge into one broad strip with small mask islands inside. */
    private static List<Component> mergedBeamInteriorHeads(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.maxX - head.minX + 1 > gap * 2
                    || head.maxY - head.minY + 1 > gap * 1.2f
                    || head.area > gap * gap) continue;
            for (Component main : heads) {
                if (main == head
                        || main.area < Math.max(head.area * 1.3f, gap * gap * 1.05f)
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || Math.abs(main.centerX - head.centerX) > gap * 1.5f
                        || Math.abs(main.centerY - head.centerY) < gap * 2
                        || Math.abs(main.centerY - head.centerY) > gap * 6) continue;
                int[] stem = attachedRawStem(gray, width, height, main, gap);
                if (stem == null
                        && head.maxX - head.minX + 1 <= Math.ceil(gap * .55f)
                        && head.maxY - head.minY + 1 <= Math.ceil(gap * .5f)
                        && head.area <= gap * gap * .22f)
                    stem =
                            attachedRawStem(
                                    gray,
                                    width,
                                    height,
                                    main,
                                    gap,
                                    Math.max(1, Math.round(gap * .16f)),
                                    205);
                if (stem == null
                        || stem[0] < head.minX - gap * .4f
                        || stem[0] > head.maxX + gap * .4f
                        || Math.abs(stem[1] - head.centerY) > gap * 1.85f) continue;
                if (mergedBeamStrip(gray, width, height, head.centerX, head.centerY, gap)
                        || ParallelBeamTip.matches(
                                gray, width, height, head.centerX, head.centerY, gap)
                        || ParallelBeamTip.matches(
                                gray, width, height, head.centerX, head.centerY + gap * .5f, gap)
                        || ParallelBeamTip.matches(
                                gray, width, height, head.centerX, head.centerY - gap * .5f, gap)) {
                    rejected.add(head);
                    break;
                }
            }
        }
        return rejected;
    }

    static boolean mergedBeamStrip(
            byte[] gray, int width, int height, float centerX, float centerY, float gap) {
        if (gray == null || gap < 8) return false;
        int cy = Math.round(centerY), radius = Math.round(gap * 1.8f);
        if (cy - radius < 0 || cy + radius >= height) return false;
        for (int direction : new int[] {-1, 1}) {
            int valid = 0, total = 0;
            List<Integer> tops = new ArrayList<>(), bottoms = new ArrayList<>();
            for (int dx = 0; dx <= Math.round(gap * 3); dx++) {
                int x = Math.round(centerX) + direction * dx;
                if (x < 0 || x >= width) break;
                total++;
                if ((gray[cy * width + x] & 255) >= 165) continue;
                int top = cy, bottom = cy;
                while (top > cy - radius && (gray[(top - 1) * width + x] & 255) < 165) top--;
                while (bottom < cy + radius && (gray[(bottom + 1) * width + x] & 255) < 165)
                    bottom++;
                int span = bottom - top + 1;
                if (span < gap * .85f || span > gap * 1.8f) continue;
                valid++;
                tops.add(top);
                bottoms.add(bottom);
            }
            if (total < Math.round(gap * 3) || valid < total * .9f) continue;
            var sortedTops = new ArrayList<>(tops);
            var sortedBottoms = new ArrayList<>(bottoms);
            sortedTops.sort(Integer::compare);
            sortedBottoms.sort(Integer::compare);
            int a = sortedTops.get(tops.size() / 2),
                    b = sortedBottoms.get(bottoms.size() / 2),
                    aligned = 0;
            for (int i = 0; i < tops.size(); i++)
                if (Math.abs(tops.get(i) - a) <= gap * .15f
                        && Math.abs(bottoms.get(i) - b) <= gap * .15f) aligned++;
            if (aligned >= valid * .9f) return true;
        }
        return false;
    }

    /** A beam has an extended straight core and no rounded head bulge at its tip. */
    static boolean narrowBeamTip(
            byte[] gray, int width, int height, float stemX, float centerY, float gap) {
        if (gray == null || gap < 8) return false;
        int core = Math.max(1, Math.round(gap * .07f));
        int flank = Math.max(core + 2, Math.round(gap * .45f));
        int first = Math.max(3, Math.round(gap * .35f)), last = Math.round(gap * 2.2f);
        int search = Math.max(2, Math.round(gap * .3f));
        for (int side : new int[] {-1, 1})
            for (int offset = -search; offset <= search; offset++) {
                float origin = centerY + offset;
                for (int angle = -12; angle <= 12; angle++) {
                    float slope = angle * .05f;
                    int supported = 0, total = 0, tip = 0, tipTotal = 0;
                    for (int distance = first; distance <= last; distance++) {
                        int x = Math.round(stemX) + side * distance,
                                y = Math.round(origin + slope * distance);
                        if (x < 0 || x >= width || y - flank < 0 || y + flank >= height) break;
                        boolean ink = true;
                        for (int dy = -core; dy <= core; dy++)
                            if ((gray[(y + dy) * width + x] & 255) >= 165) ink = false;
                        boolean thin =
                                ParallelBeamTip.clearOrRule(gray, width, height, x, y - flank, gap)
                                        && ParallelBeamTip.clearOrRule(
                                                gray, width, height, x, y + flank, gap);
                        total++;
                        if (ink && thin) supported++;
                        if (distance <= gap * .85f) {
                            tipTotal++;
                            if (ink && thin) tip++;
                        }
                    }
                    if (total == last - first + 1
                            && supported >= total * .88f
                            && tipTotal >= 3
                            && tip >= tipTotal * .85f
                            && !beamTipBulges(gray, width, height, stemX, origin, side, slope, gap))
                        return true;
                }
            }
        return false;
    }

    private static boolean beamTipBulges(
            byte[] gray,
            int width,
            int height,
            float x,
            float y,
            int side,
            float slope,
            float gap) {
        var far = new ArrayList<Integer>();
        int near = 0;
        for (int d = Math.round(gap * .35f); d <= Math.round(gap * 2.2f); d++) {
            int xx = Math.round(x) + side * d, cy = Math.round(y + slope * d);
            if (xx < 0
                    || xx >= width
                    || cy < 0
                    || cy >= height
                    || (gray[cy * width + xx] & 255) >= 165) continue;
            int a = cy, b = cy;
            while (a > 0 && (gray[(a - 1) * width + xx] & 255) < 165) a--;
            while (b + 1 < height && (gray[(b + 1) * width + xx] & 255) < 165) b++;
            int span = b - a + 1;
            if (d <= gap * .9f) near = Math.max(near, span);
            else if (span <= gap * 1.2f) far.add(span);
        }
        if (far.size() < gap * .5f) return true;
        far.sort(Integer::compare);
        return near > far.get(far.size() / 2) + Math.max(2, Math.round(gap * .18f));
    }

    private record CurvedExitHeads(List<Component> heads, List<CurvedExitInk.Mark> marks) {}

    private static CurvedExitHeads curvedExitHeadFragments(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Component> heads,
            List<Staff> staffs) {
        List<Component> result = new ArrayList<>();
        List<CurvedExitInk.Mark> marks = new ArrayList<>();
        if (gray == null) return new CurvedExitHeads(result, marks);
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.area > gap * gap * .8f
                    || head.maxX - head.minX + 1 > gap * 1.3f
                    || head.maxY - head.minY + 1 > gap * 1.1f) continue;
            for (Component main : heads) {
                if (main == head
                        || main.centerX >= head.centerX
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || head.centerX - main.centerX < gap
                        || head.centerX - main.centerX > gap * 3
                        || main.maxX - main.minX + 1 < gap * 1.4f
                        || !hasOpenCenter(labels, gray, width, height, main, gap)) continue;
                float[] local = localStaffPitch(labels, gray, width, height, staff, main);
                var mark =
                        CurvedExitInk.find(
                                gray,
                                width,
                                height,
                                new CurvedExitInk.Box(
                                        head.minX,
                                        head.minY,
                                        head.maxX,
                                        head.maxY,
                                        head.centerX,
                                        head.centerY),
                                new CurvedExitInk.Box(
                                        main.minX,
                                        main.minY,
                                        main.maxX,
                                        main.maxY,
                                        main.centerX,
                                        main.centerY),
                                local[0],
                                local[1]);
                if (mark != null) {
                    result.add(head);
                    marks.add(mark);
                    break;
                }
            }
        }
        return new CurvedExitHeads(result, marks);
    }

    /** A small mask island on a straight or curved entrance stroke is not a separate attack. */
    private static List<Component> entranceStrokeFragments(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> rejected = new ArrayList<>();
        if (gray == null) return rejected;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap =
                    staff.pitchTrack != null
                            ? staff.pitchTrack.at(head.centerX)[1]
                            : staff.printedPhase || staff.printedSlope ? staff.pitchGap : staff.gap;
            if (head.area > gap * gap * .3f
                    || head.maxX - head.minX + 1 > gap * .85f
                    || head.maxY - head.minY + 1 > gap * .9f
                    || attachedRawStem(gray, width, height, head, gap * .65f) != null) continue;
            for (Component main : heads) {
                float dx = main.centerX - head.centerX, dy = head.centerY - main.centerY;
                if (main == head
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || main.area < head.area * 3
                        || main.maxX - main.minX + 1 < gap
                        || dx < gap
                        || dx > gap * 3
                        || dy < gap * .5f
                        || dy > gap * 2.5f) continue;
                boolean stem = attachedRawStem(gray, width, height, main, gap * .65f) != null;
                boolean ordinary =
                        stem
                                && (head.maxY - head.minY + 1 <= gap * .65f
                                                && rawStraightEntrance(
                                                        gray, width, height, head, main, gap)
                                        || rawCurvedEntrance(gray, width, height, head, main, gap));
                boolean pale =
                        !ordinary
                                && attachedRawStem(gray, width, height, head, gap * .65f, 2, 180)
                                        == null
                                && attachedRawStem(gray, width, height, main, gap, 2, 180) != null
                                && rawCurvedEntrance(
                                        gray, width, height, head, main, gap, 180, true);
                if (ordinary || pale) {
                    rejected.add(head);
                    break;
                }
            }
        }
        return rejected;
    }

    /** A scoop has one thin rising curve ending beside the destination, not an oval attack. */
    private static boolean rawCurvedEntrance(
            byte[] gray, int width, int height, Component head, Component main, float gap) {
        return rawCurvedEntrance(gray, width, height, head, main, gap, 165, false);
    }

    private static boolean rawCurvedEntrance(
            byte[] gray,
            int width,
            int height,
            Component head,
            Component main,
            float gap,
            int inkThreshold,
            boolean joined) {
        int left = head.minX - Math.round(gap * (joined ? 1.8f : 1)),
                right = main.minX - Math.max(2, Math.round(gap * .2f));
        int top = Math.round(main.centerY - gap * .5f), bottom = head.maxY + Math.round(gap * .65f);
        int ruleLeft = left - Math.round(gap * 2), ruleRight = right + Math.round(gap * 2);
        if (ruleLeft < 0
                || ruleRight >= width
                || top < 1
                || bottom >= height - 1
                || right - left < gap) return false;
        int w = right - left + 1, h = bottom - top + 1;
        byte[] ink = new byte[w * h];
        boolean[] rules = new boolean[h];
        for (int y = top; y <= bottom; y++) {
            int dark = 0;
            for (int x = ruleLeft; x <= ruleRight; x++)
                if ((gray[y * width + x] & 255) < inkThreshold) dark++;
            rules[y - top] = dark >= (ruleRight - ruleLeft + 1) * .9f;
        }
        for (int y = 0; y < h; ) {
            int start = y;
            while (y < h && rules[y]) y++;
            if (y - start > Math.max(3, Math.round(gap * .3f)))
                java.util.Arrays.fill(rules, start, y, false);
            if (y == start) y++;
        }
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (!rules[y] && (gray[(top + y) * width + left + x] & 255) < inkThreshold)
                    ink[y * w + x] = OmrMeasurePostProcessor.SYMBOL;
        // Restore only crossings supported on both sides of a narrow staff stripe.
        for (int y = 0; y < h; ) {
            if (!rules[y]) {
                y++;
                continue;
            }
            int first = y;
            while (y < h && rules[y]) y++;
            if (first == 0 || y == h) continue;
            for (int x = 1; x < w - 1; x++) {
                if (ink[(first - 1) * w + x] != 0 && ink[y * w + x] != 0)
                    for (int yy = first; yy < y; yy++)
                        ink[yy * w + x] = OmrMeasurePostProcessor.SYMBOL;
            }
        }
        Component curve = retainSeedConnectedInk(ink, w, h, head, left, top);
        if (curve == null) return false;
        int span = curve.maxX - curve.minX + 1, rise = curve.maxY - curve.minY + 1;
        if (curve.minX == 0
                || curve.maxX == w - 1 && !joined
                || curve.minY == 0
                || curve.maxY == h - 1
                || span < gap * 1.2f
                || span > gap * 2.5f
                || rise < gap * .9f
                || rise > gap * 2.2f
                || span < (head.maxX - head.minX + 1) * 1.6f
                || rise < (head.maxY - head.minY + 1) * 1.5f
                || curve.area > span * rise * .6f
                || main.minX - (left + curve.maxX) > gap * .65f
                || Math.abs(top + curve.minY - main.centerY) > gap * (joined ? .8f : .6f))
            return false;
        if (joined
                && !entranceJoinsHead(
                        gray, width, height, ink, w, h, left, top, main, inkThreshold))
            return false;
        float[] centers = new float[3], columnCenters = new float[w];
        int[] bins = new int[3], columnCounts = new int[w];
        for (int x = curve.minX; x <= curve.maxX; x++) {
            int count = 0, sum = 0, runs = 0;
            boolean previous = false;
            for (int y = curve.minY; y <= curve.maxY; y++) {
                boolean dark = ink[y * w + x] != 0;
                if (dark) {
                    count++;
                    sum += y;
                    if (!previous) runs++;
                }
                previous = dark;
            }
            if (count == 0 || runs > 1) return false;
            columnCenters[x] = sum / (float) count;
            columnCounts[x] = count;
            int bin = Math.min(2, (x - curve.minX) * 3 / span);
            centers[bin] += columnCenters[x];
            bins[bin]++;
        }
        int checked = 0, thin = 0, strongColumns = 0;
        for (int x = curve.minX + 2; x <= curve.maxX - 2; x++) {
            float slope = (columnCenters[x + 2] - columnCenters[x - 2]) * .25f;
            int core = columnCounts[x];
            if (joined) {
                core = 0;
                for (int y = curve.minY; y <= curve.maxY; y++)
                    if (ink[y * w + x] != 0 && (gray[(top + y) * width + left + x] & 255) < 165)
                        core++;
            }
            if (core > 0) strongColumns++;
            checked++;
            if (core / Math.sqrt(1 + slope * slope) <= Math.max(3, gap * .35f)) thin++;
        }
        if (checked < gap * .7f || thin < checked * .85f || joined && strongColumns < checked * .8f)
            return false;
        for (int i = 0; i < 3; i++) {
            if (bins[i] == 0) return false;
            centers[i] /= bins[i];
        }
        return centers[0] - centers[1] >= gap * .08f
                && centers[1] - centers[2] >= gap * .25f
                && centers[0] - centers[2] >= gap * .6f
                && centers[1] - (centers[0] + centers[2]) * .5f >= gap * .08f;
    }

    /** A clipped scoop is accepted only when its ascending ink actually reaches
     * the destination head within the short omitted clearance. */
    private static boolean entranceJoinsHead(
            byte[] gray,
            int width,
            int height,
            byte[] ink,
            int w,
            int h,
            int left,
            int top,
            Component main,
            int threshold) {
        boolean[] previous = new boolean[h];
        for (int y = 0; y < h; y++) previous[y] = ink[y * w + w - 1] != 0;
        for (int x = left + w; x <= main.minX + 2; x++) {
            if (x < 0 || x >= width) return false;
            boolean[] next = new boolean[h];
            for (int y = 0; y < h; y++)
                if (top + y >= 0
                        && top + y < height
                        && (gray[(top + y) * width + x] & 255) < threshold)
                    for (int dy = -1; dy <= 1; dy++)
                        if (y + dy >= 0 && y + dy < h && previous[y + dy]) {
                            next[y] = true;
                            break;
                        }
            previous = next;
        }
        for (int y = Math.max(0, main.minY - top); y <= Math.min(h - 1, main.maxY - top); y++)
            if (previous[y]) return true;
        return false;
    }

    private static boolean rawStraightEntrance(
            byte[] gray, int width, int height, Component head, Component main, float gap) {
        int left = head.minX - Math.round(gap * .5f),
                right = main.minX - Math.max(2, Math.round(gap * .25f));
        int top = Math.round(head.centerY - gap * 2.2f),
                bottom = Math.round(head.centerY + gap * 2.2f);
        int ruleLeft = left - Math.round(gap * 2), ruleRight = right + Math.round(gap * 2);
        if (ruleLeft < 0
                || ruleRight >= width
                || top < 0
                || bottom >= height
                || right - left < gap * 1.3f) return false;
        boolean[] rules = new boolean[bottom - top + 1];
        for (int y = top; y <= bottom; y++) {
            int n = 0;
            for (int x = ruleLeft; x <= ruleRight; x++) if ((gray[y * width + x] & 255) < 165) n++;
            rules[y - top] = n >= (ruleRight - ruleLeft + 1) * .9f;
        }
        for (int y = 0; y < rules.length; ) {
            int start = y;
            while (y < rules.length && rules[y]) y++;
            if (y - start > Math.max(3, Math.round(gap * .3f)))
                java.util.Arrays.fill(rules, start, y, false);
            if (y == start) y++;
        }
        for (float slope = .65f; slope <= 1.56f; slope += .1f)
            for (int offset = -1; offset <= 1; offset++) {
                int visible = 0, supported = 0, thin = 0;
                for (int x = left; x <= right; x++) {
                    float cy = head.centerY + offset - slope * (x - head.centerX);
                    int y0 = Math.round(cy), radius = Math.max(3, Math.round(gap * .6f));
                    if (y0 - radius < top || y0 + radius > bottom || rules[y0 - top]) continue;
                    visible++;
                    boolean ink = false, wide = false;
                    for (int y = y0 - radius; y <= y0 + radius; y++) {
                        if (rules[y - top] || (gray[y * width + x] & 255) >= 165) continue;
                        float distance = Math.abs(y - cy);
                        if (distance <= Math.max(1.5f, gap * .18f)) ink = true;
                        if (distance > Math.max(3, gap * .3f)) wide = true;
                    }
                    if (ink) supported++;
                    if (ink && !wide) thin++;
                }
                if (visible >= gap && supported >= visible * .9f && thin >= visible * .85f)
                    return true;
            }
        return false;
    }

    private static int slurInkThreshold(
            byte[] gray, int width, int height, Component head, float gap) {
        int[] values = new int[256];
        int count = 0;
        int left = Math.max(0, Math.round(head.centerX - gap * 2)),
                right = Math.min(width - 1, Math.round(head.centerX + gap * 2));
        int top = Math.max(0, Math.round(head.centerY - gap * 1.2f)),
                bottom = Math.min(height - 1, Math.round(head.centerY + gap * 1.2f));
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                values[gray[y * width + x] & 255]++;
                count++;
            }
        int cumulative = 0, paper = 255;
        for (int i = 0; i < 256; i++) {
            cumulative += values[i];
            if (cumulative >= count * .85f) {
                paper = i;
                break;
            }
        }
        return Math.min(165, Math.max(40, paper - 30));
    }

    private static boolean flatStemlessFragment(
            byte[] gray, int width, int height, Component head, float gap) {
        if (gray == null) return false;
        int inkThreshold = slurInkThreshold(gray, width, height, head, gap);
        float w = head.maxX - head.minX + 1f, h = head.maxY - head.minY + 1f;
        if (h < gap * .65f
                && w > h * 1.8f
                && attachedRawStem(
                                gray,
                                width,
                                height,
                                head,
                                gap * .65f,
                                Math.max(1, Math.round(gap * .65f * .16f)),
                                inkThreshold)
                        == null) return true;
        if (w > gap * 1.2f
                || h > gap * .95f
                || head.area > gap * gap * .5f
                || attachedRawStem(
                                gray,
                                width,
                                height,
                                head,
                                gap * .65f,
                                Math.max(1, Math.round(gap * .65f * .16f)),
                                inkThreshold)
                        != null) return false;
        return rawSlurBowl(gray, width, height, head, gap);
    }

    /** A segmentation island can cover only the roundest part of a longer slur.
     * Inspect its complete raw component after removing thin, continuous staff rules. */
    private static boolean rawSlurBowl(
            byte[] gray, int width, int height, Component head, float gap) {
        int inkThreshold = slurInkThreshold(gray, width, height, head, gap);
        return rawSlurBowl(gray, width, height, head, gap, inkThreshold, false);
    }

    private static boolean rawSlurBowl(
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int inkThreshold,
            boolean graceConnector) {
        int left = Math.max(0, Math.round(head.centerX - gap * 2)),
                right = Math.min(width - 1, Math.round(head.centerX + gap * 2));
        int top = Math.max(0, Math.round(head.centerY - gap * 1.2f)),
                bottom = Math.min(height - 1, Math.round(head.centerY + gap * 1.2f));
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] rules = new boolean[h];
        for (int y = 0; y < h; y++) {
            int count = 0;
            for (int x = left; x <= right; x++)
                if ((gray[(top + y) * width + x] & 255) <= inkThreshold) count++;
            rules[y] = count >= w * .9f;
        }
        for (int y = 0; y < h; ) {
            int start = y;
            while (y < h && rules[y]) y++;
            if (y - start > Math.max(2, Math.round(gap * .3f)))
                java.util.Arrays.fill(rules, start, y, false);
            if (y == start) y++;
        }
        boolean[] visited = new boolean[w * h];
        int[] stack = new int[w * h];
        for (int origin = 0; origin < visited.length; origin++) {
            int ox = origin % w, oy = origin / w;
            if (visited[origin]
                    || rules[oy]
                    || (gray[(top + oy) * width + left + ox] & 255) > inkThreshold) continue;
            int size = 0;
            stack[size++] = origin;
            visited[origin] = true;
            int minX = w, maxX = -1, minY = h, maxY = -1, overlap = 0;
            int[] counts = new int[w], sums = new int[w];
            while (size > 0) {
                int at = stack[--size], x = at % w, y = at / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                counts[x]++;
                sums[x] += y;
                if (x + left >= head.minX
                        && x + left <= head.maxX
                        && y + top >= head.minY
                        && y + top <= head.maxY) overlap++;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= w || yy < 0 || yy >= h || rules[yy]) continue;
                        int next = yy * w + xx;
                        if (!visited[next]
                                && (gray[(top + yy) * width + left + xx] & 255) <= inkThreshold) {
                            visited[next] = true;
                            stack[size++] = next;
                        }
                    }
            }
            int span = maxX - minX + 1, rise = maxY - minY + 1;
            if (minX == 0
                    || maxX == w - 1
                    || minY == 0
                    || maxY == h - 1
                    || overlap < head.area * (graceConnector ? .35f : .55f)
                    || span < gap * (graceConnector ? .9f : 1.25f)
                    || span < (head.maxX - head.minX + 1) * 1.5f
                    || rise > gap * 1.2f
                    || span < rise * (graceConnector ? .95f : 1.6f)) continue;
            float[] centers = new float[3];
            int[] bins = new int[3];
            for (int x = minX; x <= maxX; x++)
                if (counts[x] > 0) {
                    int bin = Math.min(2, (x - minX) * 3 / span);
                    centers[bin] += sums[x] / (float) counts[x];
                    bins[bin]++;
                }
            if (bins[0] == 0 || bins[1] == 0 || bins[2] == 0) continue;
            for (int i = 0; i < 3; i++) centers[i] /= bins[i];
            if (graceConnector
                    && centers[1] - (centers[0] + centers[2]) * .5f >= Math.max(1.1f, gap * .10f)
                    && Math.abs(centers[0] - centers[2]) <= gap * .9f) return true;
            // Straight ledger extensions and small intact ovals lack this returning bend.
            // Compact grace slurs can be deeper than a shallow tie. Require a stronger
            // returning bend and closer endpoint heights when admitting that geometry.
            boolean deep = rise > gap * .85f || span < rise * 2.4f;
            float shallowBend =
                    span < gap * 1.4f && Math.abs(centers[0] - centers[2]) < gap * .1f ? .09f : .1f;
            if (Math.abs(centers[1] - (centers[0] + centers[2]) * .5f)
                            >= Math.max(1.25f, gap * (deep ? .25f : shallowBend))
                    && Math.abs(centers[0] - centers[2]) <= gap * (deep ? .4f : .65f)) return true;
        }
        return false;
    }

    /** Tremolo strokes cross both sides of a stem. A fragmented mask may join
     * two strokes into a plausible small head; the repeated short raw bands and
     * the larger head owning that stem distinguish them from a chord. */
    private static List<Component> stemSlashFragments(
            byte[] gray, int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> fragments = new ArrayList<>();
        if (gray == null) return fragments;
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (candidate.area > gap * gap * .65f
                    || candidate.maxX - candidate.minX + 1 > gap * 1.2f) continue;
            for (Component main : heads) {
                float distance = Math.abs(main.centerY - candidate.centerY);
                if (main == candidate
                        || main.area < candidate.area * 2.2f
                        || main.maxX - main.minX + 1 < gap
                        || distance < gap * 1.2f
                        || distance > gap * 7f
                        || Math.abs(main.centerX - candidate.centerX) > gap * 1.5f) continue;
                int[] stem = attachedRawStem(gray, width, height, main, gap);
                if (stem == null
                        || (candidate.centerY - main.centerY) * stem[2] <= 0
                        || (candidate.centerY - stem[1]) * stem[2] > gap * .3f
                        || Math.abs(candidate.centerX - stem[0]) > gap * .9f) continue;
                int edge = stem[2] > 0 ? main.maxY : main.minY;
                // A staff rule through the stroke can hide both of its far ends.
                // Follow only thick ink on either side of the stem in that case.
                boolean isolatedStroke = false;
                int radius = Math.max(2, Math.round(gap * .35f)),
                        wing = Math.max(2, Math.round(gap * .5f));
                for (int y = Math.max(0, Math.round(candidate.centerY) - radius);
                        y <= Math.min(height - 1, Math.round(candidate.centerY) + radius);
                        y++) {
                    if (stem[0] - Math.round(gap * 1.5f) < 0
                            || stem[0] + Math.round(gap * 1.5f) >= width) break;
                    if (thickStrokeInk(gray, width, height, stem[0] - wing, y, gap)
                            && thickStrokeInk(gray, width, height, stem[0] + wing, y, gap)
                            && boundedStrokeWing(gray, width, height, stem[0], y, gap, -1)
                            && boundedStrokeWing(gray, width, height, stem[0], y, gap, 1)) {
                        isolatedStroke = true;
                        break;
                    }
                }
                if (isolatedStroke) {
                    fragments.add(candidate);
                    break;
                }
                int first = Math.max(1, Math.min(edge + stem[2] * Math.round(gap * .25f), stem[1]));
                int last =
                        Math.min(
                                height - 2,
                                Math.max(edge + stem[2] * Math.round(gap * .25f), stem[1]));
                int side = Math.max(2, Math.round(gap * .42f)), far = Math.round(gap * 2f);
                if (stem[0] - far < 0 || stem[0] + far >= width) continue;
                int run = 0, bands = 0;
                boolean touches = false, provenCrossStroke = false;
                for (int y = first; y <= last + 1; y++) {
                    boolean shortStroke =
                            y <= last
                                    && rawColumnInk(gray, width, stem[0] - side, y)
                                    && rawColumnInk(gray, width, stem[0] + side, y)
                                    && !(rawColumnInk(gray, width, stem[0] - far, y)
                                            && rawColumnInk(gray, width, stem[0] + far, y));
                    if (shortStroke) run++;
                    else {
                        if (run >= Math.max(2, Math.round(gap * .18f)) && run <= gap * .85f) {
                            bands++;
                            float center = y - (run + 1) * .5f;
                            if (Math.abs(center - candidate.centerY) < gap * .75f) {
                                touches = true;
                                provenCrossStroke |=
                                        boundedStrokeWing(
                                                        gray, width, height, stem[0], center, gap,
                                                        -1)
                                                && boundedStrokeWing(
                                                        gray, width, height, stem[0], center, gap,
                                                        1);
                            }
                        }
                        run = 0;
                    }
                }
                if (touches && (bands >= 2 || provenCrossStroke)) {
                    fragments.add(candidate);
                    break;
                }
            }
        }
        return fragments;
    }

    /** Reduced heads in a close, stemmed prefix are ornaments, not extra metrical beats. */
    private static void markGraceHeads(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<DetectedNote> detected,
            List<ScoreNoteEvent> events) {
        for (int i = 0; i < detected.size(); i++) {
            DetectedNote first = detected.get(i);
            if (!(smallGraceHead(first)
                            || roundedBeamedGraceHead(first, gray, width, height)
                            || compactBeamedGraceHead(first, gray, width, height))
                    || (gray != null
                            ? attachedRawStem(
                                            gray, width, height, first.head, first.staffGap * .65f)
                                    == null
                            : !hasAttachedStem(labels, width, height, first.head, first.staffGap)))
                continue;
            List<Integer> prefix = new ArrayList<>();
            prefix.add(i);
            DetectedNote previous = first;
            for (int j = i + 1; j < detected.size(); j++) {
                DetectedNote next = detected.get(j);
                if (next.event.measureIndex() != first.event.measureIndex()) break;
                if (next.event.staffIndex() != first.event.staffIndex()
                        || next.event.staffCount() != first.event.staffCount()) continue;
                float dx = next.head.centerX - previous.head.centerX;
                if (dx < first.staffGap * .65f
                        || dx > first.staffGap * 2.8f
                        || Math.abs(next.head.centerY - previous.head.centerY)
                                > first.staffGap * 2.5f) break;
                if (smallGraceHead(next)
                        || roundedBeamedGraceHead(next, gray, width, height)
                        || compactBeamedGraceHead(next, gray, width, height)) {
                    if (gray != null
                            ? attachedRawStem(gray, width, height, next.head, next.staffGap * .65f)
                                    == null
                            : !hasAttachedStem(labels, width, height, next.head, next.staffGap))
                        break;
                    prefix.add(j);
                    previous = next;
                    continue;
                }
                if (extendsMetricalBeam(gray, width, height, detected, i)) break;
                boolean compactGroup = prefix.size() >= 2;
                for (int k = 0; k < prefix.size() && compactGroup; k++) {
                    DetectedNote member = detected.get(prefix.get(k));
                    compactGroup =
                            compactBeamedGraceHead(member, gray, width, height)
                                    && next.head.area > member.head.area * 1.28f
                                    && next.head.maxX - next.head.minX + 1
                                            > (member.head.maxX - member.head.minX + 1) * 1.10f
                                    && (k == 0
                                            || graceStemsShareBeam(
                                                    labels,
                                                    gray,
                                                    width,
                                                    height,
                                                    detected.get(prefix.get(k - 1)).head,
                                                    member.head,
                                                    member.staffGap));
                }
                if (compactGroup
                        || next.head.area > first.head.area * 1.65f
                                && next.head.maxX - next.head.minX + 1 > first.staffGap * 1.05f
                                && (prefix.stream()
                                                .allMatch(
                                                        index ->
                                                                smallGraceHead(detected.get(index)))
                                        || prefix.size() >= 2
                                                && prefix.stream()
                                                        .allMatch(
                                                                index ->
                                                                        next.head.area
                                                                                > detected.get(
                                                                                                        index)
                                                                                                .head
                                                                                                .area
                                                                                        * 1.65f)))
                    for (int index : prefix)
                        events.set(
                                index,
                                events.get(index)
                                        .withArticulations(
                                                events.get(index).articulations()
                                                        | NoteOrnament.GRACE));
                break;
            }
        }
        markPairedGraces(gray, width, height, detected, events);
        markSlashedGracePrefixes(gray, width, height, detected, events);
    }

    private static void markPairedGraces(
            byte[] gray,
            int width,
            int height,
            List<DetectedNote> detected,
            List<ScoreNoteEvent> events) {
        if (gray == null) return;
        for (int i = 0; i + 1 < detected.size(); i++) {
            DetectedNote a = detected.get(i);
            int bIndex = adjacentGraceVoiceIndex(detected, i, 1);
            if (bIndex < 0) continue;
            DetectedNote b = detected.get(bIndex);
            float gap = a.staffGap;
            if (!sameGraceVoice(a, b)
                    || !reducedPairedGrace(a)
                    || !reducedPairedGrace(b)
                    || b.head.centerX - a.head.centerX < gap * .95f
                    || b.head.centerX - a.head.centerX > gap * 3
                    || Math.abs(b.head.centerY - a.head.centerY) > gap * 1.3f) continue;
            int threshold =
                    BeamInkThreshold.at(
                                    gray,
                                    width,
                                    height,
                                    Math.round(a.head.centerX),
                                    Math.round(a.head.centerY - gap * 5),
                                    Math.round(a.head.centerY + gap * 5),
                                    gap)
                            + 5;
            int[] first =
                    attachedRawStem(
                            gray,
                            width,
                            height,
                            a.head,
                            gap * .65f,
                            Math.max(1, Math.round(gap * .16f)),
                            threshold);
            int[] last =
                    attachedRawStem(
                            gray,
                            width,
                            height,
                            b.head,
                            gap * .65f,
                            Math.max(1, Math.round(gap * .16f)),
                            threshold);
            if (first == null)
                first =
                        attachedRawStem(
                                gray,
                                width,
                                height,
                                a.head,
                                gap * .65f,
                                Math.max(1, Math.round(gap * .16f)),
                                185);
            if (last == null)
                last =
                        attachedRawStem(
                                gray,
                                width,
                                height,
                                b.head,
                                gap * .65f,
                                Math.max(1, Math.round(gap * .16f)),
                                185);
            int provedBeams = PairedGraceBeamInk.count(gray, width, height, first, last, gap);
            if (first == null
                    || last == null
                    || Math.abs(first[1] - a.head.centerY) > gap * 4.8f
                    || Math.abs(last[1] - b.head.centerY) > gap * 4.8f
                    || provedBeams < 2) continue;
            DetectedNote principal = null;
            int nextIndex = adjacentGraceVoiceIndex(detected, bIndex, 1);
            int priorIndex = adjacentGraceVoiceIndex(detected, i, -1);
            if (nextIndex >= 0) {
                DetectedNote next = detected.get(nextIndex);
                float dx = next.head.centerX - b.head.centerX;
                if (dx >= gap * .65f && dx <= gap * 4) principal = next;
            } else if (priorIndex >= 0) {
                DetectedNote prior = detected.get(priorIndex);
                float dx = a.head.centerX - prior.head.centerX;
                if (dx >= gap * .65f
                        && dx <= gap * 4.5f
                        && ScoreNoteTiming.writtenDurationBeats(prior.event) >= 1)
                    principal = prior;
            }
            if (principal == null
                    || principal.head.maxX - principal.head.minX + 1 <= gap * 1.05f
                    || principal.head.area <= a.head.area * 1.65f
                    || principal.head.area <= b.head.area * 1.65f) continue;
            if (extendsMetricalBeam(gray, width, height, detected, i)) continue;
            events.set(i, asEngravedGrace(events.get(i), provedBeams));
            events.set(bIndex, asEngravedGrace(events.get(bIndex), provedBeams));
            if (bIndex == i + 1) i++;
        }
    }

    /** A continuous beam from an ordinary attack keeps its smaller heads metrical. */
    private static boolean extendsMetricalBeam(
            byte[] gray, int width, int height, List<DetectedNote> detected, int index) {
        if (gray == null) return false;
        DetectedNote first = detected.get(index), next = first;
        int cursor = index;
        for (int count = 0; count < 8; count++) {
            int prior = adjacentGraceVoiceIndex(detected, cursor, -1);
            if (prior < 0) return false;
            DetectedNote previous = detected.get(prior);
            if (!graceStemsShareBeam(
                    null, gray, width, height, previous.head, next.head, first.staffGap))
                return false;
            int[] stem = attachedRawStem(gray, width, height, previous.head, first.staffGap * .65f);
            if (stem != null && Math.abs(stem[1] - previous.head.centerY) > first.staffGap * 4.8f)
                return true;
            if (previous.head.area > first.head.area * 1.65f
                    && previous.head.maxX - previous.head.minX + 1 > first.staffGap * 1.05f)
                return true;
            next = previous;
            cursor = prior;
        }
        return false;
    }

    /** Accompaniment on another staff cannot split an ornamental pair. */
    private static int adjacentGraceVoiceIndex(List<DetectedNote> notes, int index, int direction) {
        DetectedNote anchor = notes.get(index);
        for (int candidate = index + direction;
                candidate >= 0 && candidate < notes.size();
                candidate += direction) {
            DetectedNote next = notes.get(candidate);
            if (next.event.measureIndex() != anchor.event.measureIndex()) break;
            if (sameGraceVoice(anchor, next)) return candidate;
        }
        return -1;
    }

    /** An independently slashed short shaft can survive a modestly faded scan. */
    private static void markSlashedGracePrefixes(
            byte[] gray,
            int width,
            int height,
            List<DetectedNote> detected,
            List<ScoreNoteEvent> events) {
        if (gray == null) return;
        for (int i = 0; i + 1 < detected.size(); i++) {
            DetectedNote a = detected.get(i), principal = detected.get(i + 1);
            float gap = a.staffGap;
            if ((events.get(i).articulations() & NoteOrnament.GRACE) != 0
                    || !smallGraceHead(a)
                    || a.event.beamCount() > 1
                    || !sameGraceVoice(a, principal)
                    || smallGraceHead(principal)
                    || principal.head.area <= a.head.area * 1.65f
                    || principal.head.maxX - principal.head.minX + 1 <= gap * 1.05f) continue;
            float dx = principal.head.centerX - a.head.centerX;
            if (dx < gap * .65f
                    || dx > gap * 3.2f
                    || Math.abs(principal.head.centerY - a.head.centerY) > gap * 2.5f) continue;
            if (i > 0) {
                DetectedNote previous = detected.get(i - 1);
                if (sameGraceVoice(a, previous)
                        && smallGraceHead(previous)
                        && a.head.centerX - previous.head.centerX < gap * 3) continue;
            }
            int[] stem = attachedRawStem(gray, width, height, a.head, gap * .65f);
            if (stem == null)
                stem =
                        attachedRawStem(
                                gray,
                                width,
                                height,
                                a.head,
                                gap * .65f,
                                Math.max(1, Math.round(gap * .16f)),
                                185);
            if (stem == null
                    || stem[2] != -1
                    || a.head.centerY - stem[1] > gap * 3.3f
                    || SlashedGraceFlagInk.count(
                                    gray,
                                    width,
                                    height,
                                    a.head.centerX,
                                    a.head.centerY,
                                    stem[0],
                                    gap)
                            != 1) continue;
            events.set(i, asEngravedGrace(events.get(i), 1));
        }
    }

    private static ScoreNoteEvent asEngravedGrace(ScoreNoteEvent e, int beams) {
        return new ScoreNoteEvent(
                        e.measureIndex(),
                        e.positionInMeasure(),
                        e.staffStep(),
                        e.staffIndex(),
                        e.staffCount(),
                        e.pageY(),
                        e.tiedFromPrevious(),
                        e.augmentationDots(),
                        beams,
                        e.writtenAccidental(),
                        0,
                        e.tupletDivisor(),
                        e.followingRestBeats(),
                        e.articulations() | NoteOrnament.GRACE,
                        e.clefBottomDiatonic(),
                        e.crossStaffBeam(),
                        e.leadingRestBeats(),
                        e.compactOpening(),
                        e.octaveShift(),
                        e.boundaryTies(),
                        e.tupletNormalNotes())
                .withStemDirection(e.stemDirection())
                .withKind(e.kind());
    }

    private static boolean sameGraceVoice(DetectedNote a, DetectedNote b) {
        return a.event.measureIndex() == b.event.measureIndex()
                && a.event.staffIndex() == b.event.staffIndex()
                && a.event.staffCount() == b.event.staffCount();
    }

    private static boolean reducedPairedGrace(DetectedNote n) {
        return n.head.maxX - n.head.minX + 1 <= n.staffGap * 1.05f
                && n.head.maxY - n.head.minY + 1 <= n.staffGap * 1.2f
                && n.head.area <= n.staffGap * n.staffGap * .8f
                && n.event.unbeamedDurationBeats() < 2
                && n.event.augmentationDots() == 0;
    }

    /** Slightly enlarged masks still need a short, shared beam and a larger principal. */
    private static boolean compactBeamedGraceHead(
            DetectedNote n, byte[] gray, int width, int height) {
        if (gray == null
                || n.head.maxX - n.head.minX + 1 > Math.ceil(n.staffGap * 1.25f)
                || n.head.maxY - n.head.minY + 1 > Math.ceil(n.staffGap * 1.10f)
                || n.head.area > n.staffGap * n.staffGap
                || n.event.augmentationDots() != 0
                || n.event.beamCount() < 1
                || n.event.unbeamedDurationBeats() >= ScoreNoteEvent.DURATION_HALF) return false;
        int[] stem = attachedRawStem(gray, width, height, n.head, n.staffGap * .65f);
        return stem != null && Math.abs(stem[1] - n.head.centerY) <= n.staffGap * 3.1f;
    }

    private static boolean smallGraceHead(DetectedNote n) {
        // Raster rounding can add a boundary pixel to both dimensions of a small
        // ellipse. Round its envelope outward before checking the filled area;
        // the attached stem and substantially larger principal remain required.
        return n.head.maxX - n.head.minX + 1 <= Math.ceil(n.staffGap * .95f)
                && n.head.maxY - n.head.minY + 1 <= Math.ceil(n.staffGap * .78f)
                && n.head.area <= Math.ceil(n.staffGap * .95f) * Math.ceil(n.staffGap * .78f) * .8f
                && n.event.augmentationDots() == 0
                && n.event.unbeamedDurationBeats() < ScoreNoteEvent.DURATION_HALF;
    }

    private static boolean rawColumnInk(byte[] gray, int width, int x, int y) {
        return (gray[(y - 1) * width + x] & 255) < 170
                || (gray[y * width + x] & 255) < 170
                || (gray[(y + 1) * width + x] & 255) < 170;
    }

    /** Cross heads denote an unpitched attack. A partial semantic mask can keep
     * only one corner, so inspect the two converging diagonals in the raw ink.
     * Hollow oval sides diverge toward their centre, the opposite topology. */
    private static boolean isUnpitchedCrossHead(
            byte[] gray, int width, int height, Component head, Staff staff) {
        return unpitchedCrossHead(gray, width, height, head, staff) != null;
    }

    private static int[] unpitchedStem(
            byte[] gray, int width, int height, Component head, float gap) {
        // A cross shaft meets an outer corner instead of the horizontal centre of an oval.
        // The existing shaft proof still requires at least 2.3 staff gaps of printed ink.
        return attachedRawStem(
                gray, width, height, head, gap, Math.max(1, Math.round(gap * .4f)), 170, 9);
    }

    private static Component unpitchedCrossHead(
            byte[] gray, int width, int height, Component head, Staff staff) {
        if (gray == null
                || staff.gap < 9
                || head.maxX - head.minX > staff.gap * 1.7f
                || head.maxY - head.minY > staff.gap * 1.2f) return null;
        float gap = staff.gap;
        int inner = Math.max(2, Math.round(gap * .22f)),
                outer = Math.max(inner + 2, Math.round(gap * .38f));
        int radius = Math.round(gap),
                searchX = Math.round(gap * .8f),
                searchY = Math.round(gap * .65f);
        Component best = null;
        int bestSymmetry = Integer.MAX_VALUE;
        for (int cy = Math.max(outer, Math.round(head.centerY) - searchY);
                cy <= Math.min(height - outer - 1, Math.round(head.centerY) + searchY);
                cy++) {
            boolean rule = false;
            int ruleLeft = Math.max(0, Math.round(head.centerX - gap * 3)),
                    ruleRight = Math.min(width - 1, Math.round(head.centerX + gap * 3));
            for (int dy : new int[] {-outer, -inner, inner, outer}) {
                int ink = 0;
                for (int x = ruleLeft; x <= ruleRight; x++)
                    if ((gray[(cy + dy) * width + x] & 255) < 165) ink++;
                if (ink > (ruleRight - ruleLeft + 1) * .8f) rule = true;
            }
            if (rule) continue;
            for (int cx = Math.max(radius, Math.round(head.centerX) - searchX);
                    cx <= Math.min(width - radius - 1, Math.round(head.centerX) + searchX);
                    cx++) {
                int[] upper = crossHeadSides(gray, width, cx, cy - outer, radius, gap);
                if (upper == null) continue;
                int[] lower = crossHeadSides(gray, width, cx, cy + outer, radius, gap);
                if (lower == null) continue;
                int[] upperInner = crossHeadSides(gray, width, cx, cy - inner, radius, gap);
                int[] lowerInner = crossHeadSides(gray, width, cx, cy + inner, radius, gap);
                if (upperInner == null || lowerInner == null) continue;
                boolean converges = true;
                for (int[] pair : new int[][] {upper, lower}) {
                    int[] near = pair == upper ? upperInner : lowerInner;
                    if (near[1] - pair[1] < gap * .1f
                            || pair[2] - near[2] < gap * .1f
                            || pair[2] - pair[1] < gap * .45f) converges = false;
                }
                if (!converges
                        || Math.abs(upper[1] - lower[1]) > gap * .25f
                        || Math.abs(upper[2] - lower[2]) > gap * .25f) continue;
                int cornerMargin = Math.max(1, Math.round(gap * .2f));
                if (head.centerX < Math.min(upper[0], lower[0]) - cornerMargin
                        || head.centerX > Math.max(upper[3], lower[3]) + cornerMargin
                        || Math.abs(head.centerY - cy) > outer + 1) continue;
                int symmetry =
                        Math.abs(upper[1] - lower[1])
                                + Math.abs(upper[2] - lower[2])
                                + Math.abs(upper[0] + upper[3] - 2 * cx)
                                + Math.abs(lower[0] + lower[3] - 2 * cx);
                if (symmetry < bestSymmetry) {
                    bestSymmetry = symmetry;
                    best =
                            new Component(
                                    head.area,
                                    Math.min(upper[0], lower[0]),
                                    Math.max(upper[3], lower[3]),
                                    cy - outer,
                                    cy + outer,
                                    cx,
                                    cy);
                }
            }
        }
        return best;
    }

    private static int[] crossHeadSides(
            byte[] gray, int width, int cx, int y, int radius, float gap) {
        if ((gray[y * width + cx] & 255) < 165) return null;
        int left = cx - 1, right = cx + 1;
        while (left >= cx - radius && (gray[y * width + left] & 255) >= 165) left--;
        while (right <= cx + radius && (gray[y * width + right] & 255) >= 165) right++;
        if (left < cx - radius || right > cx + radius) return null;
        int begin = left, end = right;
        while (begin > cx - radius && (gray[y * width + begin - 1] & 255) < 165) begin--;
        while (end < cx + radius && (gray[y * width + end + 1] & 255) < 165) end++;
        if (left - begin + 1 < 2
                || end - right + 1 < 2
                || left - begin + 1 > gap * .55f
                || end - right + 1 > gap * .55f
                || end - begin < gap * .45f
                || end - begin > gap * 1.9f) return null;
        return new int[] {begin, left, right, end};
    }

    private static boolean plausibleHead(Component head, float gap) {
        float width = head.maxX - head.minX + 1f, height = head.maxY - head.minY + 1f;
        return width >= Math.max(2f, gap * .38f)
                && height >= Math.max(2f, gap * .30f)
                && width <= gap * 3.2f
                && height <= gap * 3.2f
                && head.area >= Math.max(4, Math.round(gap * gap * .11f));
    }

    /** A reduced head with its own reduced sharp and a nearby principal is a
     * grace note even when its shaft merges with the following accidental.
     * An augmentation dot has no local sharp of its own. */
    private static boolean reducedSharpGrace(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Component> heads,
            List<Staff> staffs,
            List<MeasureRegion> measures,
            List<AccidentalCandidate> candidates) {
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (gray == null || staff == null) return false;
        float gap = staff.gap;
        if (head.maxX - head.minX + 1 > gap * .82f
                || head.maxY - head.minY + 1 > gap * .82f
                || head.area > gap * gap * .48f) return false;
        int measure =
                containingMeasureForStaff(
                        measures, head.centerX / width, head.centerY / height, staff, height);
        if (measure < 0) return false;
        Component principal = null;
        for (Component other : heads) {
            float dx = other.centerX - head.centerX;
            if (other == head
                    || dx < gap * .65f
                    || dx > gap * 2.8f
                    || nearestHeadStaff(staffs, other.centerY) != staff
                    || Math.abs(other.centerY - head.centerY) > gap * 2.5f
                    || containingMeasureForStaff(
                                    measures,
                                    other.centerX / width,
                                    other.centerY / height,
                                    staff,
                                    height)
                            != measure) continue;
            if (principal == null || other.centerX < principal.centerX) principal = other;
        }
        if (principal == null
                || principal.area <= head.area * 1.65f
                || principal.maxX - principal.minX + 1 <= gap * 1.05f
                || attachedRawStem(gray, width, height, principal, gap) == null) return false;
        float reduced = gap * .65f;
        int accidental = detectWrittenAccidental(labels, width, height, candidates, head, reduced);
        return accidental == ScoreNoteEvent.ACCIDENTAL_SHARP
                || accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                        && rawSharpFromSeed(gray, width, height, candidates, head, reduced);
    }

    private static List<Component> augmentationDotHeads(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Component> heads,
            List<Staff> staffs) {
        List<Component> dots = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            float candidateWidth = candidate.maxX - candidate.minX + 1f;
            float candidateHeight = candidate.maxY - candidate.minY + 1f;
            if (candidateWidth > gap * .82f
                    || candidateHeight > gap * .82f
                    || candidate.area > gap * gap * .48f
                    || hasAttachedStem(labels, width, height, candidate, gap)
                    || attachedRawStem(gray, width, height, candidate, gap * .65f) != null)
                continue;
            for (Component main : heads) {
                if (main == candidate || nearestHeadStaff(staffs, main.centerY) != staff) continue;
                float mainWidth = main.maxX - main.minX + 1f;
                float mainHeight = main.maxY - main.minY + 1f;
                float horizontal = candidate.minX - main.maxX;
                if (horizontal < gap * .10f
                        || horizontal > gap * 2.20f
                        || Math.abs(candidate.centerY - main.centerY) > gap * .78f) continue;
                if (mainWidth < gap * .62f
                        || mainHeight < gap * .44f
                        || main.area < candidate.area * 1.55f) continue;
                dots.add(candidate);
                break;
            }
        }
        return dots;
    }

    /** A nearby bow/rest is not a duration stem unless it reaches the head. Raw ink can
     * independently bridge a model-label gap; do not require every stem pixel to be labelled. */
    private static boolean hasStemAtHead(
            byte[] labels, int width, int height, Component head, float gap) {
        int pad = Math.max(1, Math.round(gap * .3f));
        for (int y = Math.max(0, head.minY - pad); y <= Math.min(height - 1, head.maxY + pad); y++)
            for (int x = Math.max(0, head.minX - pad);
                    x <= Math.min(width - 1, head.maxX + pad);
                    x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST) return true;
        return false;
    }

    private static boolean hasAttachedStem(
            byte[] labels, int width, int height, Component head, float gap) {
        int left = Math.max(0, Math.round(head.minX - gap * .36f));
        int right = Math.min(width - 1, Math.round(head.maxX + gap * .36f));
        int top = Math.max(0, Math.round(head.minY - gap * 2.8f));
        int bottom = Math.min(height - 1, Math.round(head.maxY + gap * 2.8f));
        int rows = 0, above = 0, below = 0;
        for (int y = top; y <= bottom; y++) {
            boolean stem = false;
            for (int x = left; x <= right; x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST) {
                    stem = true;
                    break;
                }
            if (!stem) continue;
            rows++;
            if (y < head.minY) above++;
            if (y > head.maxY) below++;
        }
        return rows >= Math.max(4, Math.round(gap * .72f))
                && Math.max(above, below) >= Math.max(3, Math.round(gap * .42f));
    }

    /** A partial semantic island can flatten a round staccato dot. Require the
     * complete, isolated raw component before relaxing its semantic aspect ratio. */
    private static boolean rawRoundArticulationDot(
            byte[] labels, byte[] gray, int width, int height, Component head, float gap) {
        if (gray == null) return false;
        int padding = Math.max(2, Math.round(gap * .30f));
        int left = Math.max(0, head.minX - padding),
                right = Math.min(width - 1, head.maxX + padding);
        int top = Math.max(0, head.minY - padding),
                bottom = Math.min(height - 1, head.maxY + padding);
        int w = right - left + 1, h = bottom - top + 1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int start = 0; start < w * h; start++) {
            if (seen[start] || (gray[(top + start / w) * width + left + start % w] & 255) >= 165)
                continue;
            int read = 0, count = 1;
            queue[0] = start;
            seen[start] = true;
            int minX = w, maxX = -1, minY = h, maxY = -1, overlap = 0;
            while (read < count) {
                int point = queue[read++], x = point % w, y = point / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                int xx = left + x, yy = top + y;
                if (xx >= head.minX
                        && xx <= head.maxX
                        && yy >= head.minY
                        && yy <= head.maxY
                        && labels[yy * width + xx] == OmrMeasurePostProcessor.NOTEHEAD) overlap++;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int next = ny * w + nx;
                        if (!seen[next] && (gray[(top + ny) * width + left + nx] & 255) < 165) {
                            seen[next] = true;
                            queue[count++] = next;
                        }
                    }
            }
            if (minX == 0
                    || maxX == w - 1
                    || minY == 0
                    || maxY == h - 1
                    || overlap < head.area * .5f) continue;
            float rw = maxX - minX + 1, rh = maxY - minY + 1;
            if (Math.max(rw, rh) <= gap * .72f
                    && Math.max(rw, rh) <= Math.min(rw, rh) * 1.45f
                    && count >= Math.max(4, gap * gap * .04f)
                    && count >= rw * rh * .55f) return true;
        }
        return false;
    }

    /** Mirrored printed parentheses enclosing a note do not alter the next pitch. */
    private static List<AccidentalCandidate> noteParentheses(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            List<Component> heads,
            List<Staff> staffs) {
        List<AccidentalCandidate> result = new ArrayList<>();
        if (gray == null) return result;
        for (Component head : heads) {
            Staff staff = nearestHeadStaff(staffs, head.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (head.maxX - head.minX + 1 > gap * 2.2f || head.maxY - head.minY + 1 > gap * 1.35f)
                continue;
            for (AccidentalCandidate left : candidates) {
                Component a = left.component;
                if (a.maxX >= head.minX
                        || head.minX - a.maxX > gap
                        || a.minY > head.minY
                        || a.maxY < head.maxY
                        || Math.abs((a.minY + a.maxY) * .5f - head.centerY) > gap * .35f
                        || !printedParenthesis(gray, width, height, a, gap, true)) continue;
                for (AccidentalCandidate right : candidates) {
                    Component b = right.component;
                    if (b.minX <= head.maxX
                            || b.minX - head.maxX > gap
                            || b.minY > head.minY
                            || b.maxY < head.maxY
                            || Math.abs(a.minY - b.minY) > gap * .3f
                            || Math.abs(a.maxY - b.maxY) > gap * .3f
                            || Math.abs((a.maxX + b.minX) * .5f - head.centerX) > gap * .4f
                            || !printedParenthesis(gray, width, height, b, gap, false)) continue;
                    result.add(left);
                    result.add(right);
                }
            }
        }
        return result;
    }

    private static boolean printedParenthesis(
            byte[] gray, int width, int height, Component glyph, float gap, boolean opening) {
        int w = glyph.maxX - glyph.minX + 1, h = glyph.maxY - glyph.minY + 1;
        if (w < gap * .24f
                || w > gap * .8f
                || h < gap * 1.3f
                || h > gap * 2.8f
                || glyph.minX < 0
                || glyph.maxX >= width
                || glyph.minY < 0
                || glyph.maxY >= height) return false;
        float[] centers = new float[h];
        java.util.Arrays.fill(centers, Float.NaN);
        for (int y = glyph.minY; y <= glyph.maxY; y++) {
            int n = 0, sum = 0;
            for (int x = glyph.minX; x <= glyph.maxX; x++)
                if ((gray[y * width + x] & 255) < 170) {
                    sum += x - glyph.minX;
                    n++;
                }
            if (n > 0) centers[y - glyph.minY] = sum / (float) n;
        }
        float top = parenthesisBand(centers, 0, .15f),
                middle = parenthesisBand(centers, .35f, .65f),
                bottom = parenthesisBand(centers, .85f, 1);
        if (!Float.isFinite(top) || !Float.isFinite(middle) || !Float.isFinite(bottom))
            return false;
        float bend = Math.max(1, w * .2f), sign = opening ? 1 : -1;
        return sign * (top - middle) >= bend
                && sign * (bottom - middle) >= bend
                && Math.abs(top - bottom) <= gap * .25f;
    }

    private static float parenthesisBand(float[] centers, float from, float to) {
        int first = Math.round(from * (centers.length - 1)),
                last = Math.round(to * (centers.length - 1)),
                n = 0;
        float sum = 0;
        for (int i = first; i <= last; i++)
            if (Float.isFinite(centers[i])) {
                sum += centers[i];
                n++;
            }
        return n >= Math.max(2, (last - first + 1) * .5f) ? sum / n : Float.NaN;
    }

    /** A joined grace head and its descending flag is not a local accidental
     * for the next regular note. The reduced head is enclosed at the top. */
    private static boolean joinedGraceTailAccidental(
            AccidentalCandidate candidate, List<Component> heads, List<Staff> staffs) {
        Component mark = candidate.component;
        Staff staff = nearestHeadStaff(staffs, mark.centerY);
        if (staff == null) return false;
        float gap = staff.gap;
        if (mark.maxX - mark.minX + 1 < gap * .75f
                || mark.maxX - mark.minX + 1 > gap * 1.35f
                || mark.maxY - mark.minY + 1 < gap * 1.8f
                || mark.maxY - mark.minY + 1 > gap * 2.55f
                || mark.area > gap * gap * 1.1f) return false;
        for (Component head : heads)
            if (nearestHeadStaff(staffs, head.centerY) == staff
                    && head.area < gap * gap * .55f
                    && head.maxX - head.minX + 1 < gap * .9f
                    && head.centerX >= mark.minX - gap * .2f
                    && head.centerX <= mark.maxX + gap * .2f
                    && Math.abs(head.centerY - mark.minY) < gap * .4f
                    && mark.maxY - head.centerY > gap * 1.8f
                    && mark.maxY - head.centerY < gap * 2.5f) return true;
        return false;
    }

    /** A flag attached to an accepted grace head cannot flatten the next note. */
    private static boolean attachedGraceFlag(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            AccidentalCandidate candidate,
            List<Component> heads,
            List<Staff> staffs) {
        if (gray == null) return false;
        Component glyph = candidate.component;
        Staff staff = nearestHeadStaff(staffs, glyph.centerY);
        if (staff == null) return false;
        float gap = staff.gap;
        if (!isFlatGlyph(labels, width, height, candidate, gap)) return false;
        int spine = glyph.minX, strongest = 0;
        for (int x = glyph.minX; x <= glyph.maxX; x++) {
            int ink = 0;
            for (int y = glyph.minY; y <= glyph.maxY; y++)
                if (candidate.matches(labels[y * width + x])) ink++;
            if (ink > strongest) {
                strongest = ink;
                spine = x;
            }
        }
        for (int i = 0; i < heads.size(); i++) {
            Component original = heads.get(i), head = original;
            if (head.maxX - head.minX + 1 > gap * 1.25f
                    || head.maxY - head.minY + 1 > gap * .95f
                    || head.area > gap * gap * .85f) {
                head = printedGraceCore(gray, width, height, original, glyph, spine, gap);
                if (head == null) continue;
            }
            if (head.maxX - head.minX + 1 > gap * 1.25f
                    || head.maxY - head.minY + 1 > gap * .95f
                    || head.area > gap * gap * .85f
                    || nearestHeadStaff(staffs, head.centerY) != staff
                    || head.centerY - glyph.maxY < gap * .35f
                    || head.centerY - glyph.maxY > gap * 1.1f
                    || Math.abs(head.maxX - spine) > gap * .35f) continue;
            int[] stem = attachedRawStem(gray, width, height, head, gap * .65f);
            if (stem == null
                    || stem[2] != -1
                    || Math.abs(stem[0] - spine) > gap * .22f
                    || Math.abs(stem[1] - glyph.minY) > gap * .65f) continue;
            if (head != original) heads.set(i, head);
            return true;
        }
        return false;
    }

    /** A short grace slur can enlarge the semantic head. Recover only a compact
     * printed core beneath the flag, stable across three ink thresholds. */
    private static Component printedGraceCore(
            byte[] gray,
            int width,
            int height,
            Component source,
            Component glyph,
            int spine,
            float gap) {
        if (gap < 8
                || source.maxX - source.minX + 1 > gap * 2.4f
                || source.maxY - source.minY + 1 > gap * 1.8f
                || source.area > gap * gap * 2f
                || source.minX >= spine
                || source.maxX < spine
                || source.centerY - glyph.maxY < 0
                || source.centerY - glyph.maxY > gap * 1.4f) return null;
        int pad = Math.max(2, Math.round(gap * .25f)),
                left = Math.max(0, source.minX - pad),
                top = Math.max(0, source.minY - pad);
        int w = Math.min(width, source.maxX + pad + 1) - left,
                h = Math.min(height, source.maxY + pad + 1) - top;
        int rx = Math.max(2, Math.round(gap * .2f)), ry = Math.max(2, Math.round(gap * .18f));
        List<int[]> kernel = new ArrayList<>();
        for (int y = -ry; y <= ry; y++)
            for (int x = -rx; x <= rx; x++)
                if (x * x / (float) (rx * rx) + y * y / (float) (ry * ry) <= 1)
                    kernel.add(new int[] {x, y});
        Component reference = null;
        for (int threshold : new int[] {60, 100, 140}) {
            byte[] opened = new byte[w * h];
            for (int y = ry; y < h - ry; y++)
                for (int x = rx; x < w - rx; x++) {
                    boolean solid = true;
                    for (int[] k : kernel)
                        if ((gray[(top + y + k[1]) * width + left + x + k[0]] & 255) >= threshold) {
                            solid = false;
                            break;
                        }
                    if (solid) for (int[] k : kernel) opened[(y + k[1]) * w + x + k[0]] = 1;
                }
            Component accepted = null;
            for (Component c : findComponents(opened, w, h, (byte) 1)) {
                Component core =
                        new Component(
                                c.area,
                                c.minX + left,
                                c.maxX + left,
                                c.minY + top,
                                c.maxY + top,
                                c.centerX + left,
                                c.centerY + top);
                if (core.area < gap * gap * .25f
                        || core.area > gap * gap * .85f
                        || core.maxX - core.minX + 1 < gap * .6f
                        || core.maxX - core.minX + 1 > gap * 1.25f
                        || core.maxY - core.minY + 1 < gap * .4f
                        || core.maxY - core.minY + 1 > Math.round(gap * .95f)
                        || Math.abs(core.maxX - spine) > gap * .35f
                        || core.centerX >= spine
                        || core.centerY - glyph.maxY < gap * .35f
                        || core.centerY - glyph.maxY > gap * 1.1f) continue;
                if (accepted != null) return null;
                accepted = core;
            }
            if (accepted == null) return null;
            if (reference == null) reference = accepted;
            else if (Math.abs(reference.centerX - accepted.centerX) > gap * .12f
                    || Math.abs(reference.centerY - accepted.centerY) > gap * .12f) return null;
        }
        return reference;
    }

    /** A small isolated dot above/below a full head is an articulation, not another pitch. */
    private static List<Component> articulationDotHeads(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<Component> heads,
            List<Staff> staffs) {
        List<Component> dots = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            float w = candidate.maxX - candidate.minX + 1f;
            float h = candidate.maxY - candidate.minY + 1f;
            if (w > gap * .75f || h > gap * .75f || candidate.area > gap * gap * .36f) continue;
            if (Math.max(w, h) > Math.min(w, h) * 1.6f
                    && !rawRoundArticulationDot(labels, gray, width, height, candidate, gap))
                continue;
            // The broad semantic stem window can borrow the main note's stem.
            // Trace contiguous raw ink from this component when pixels exist.
            if (gray != null
                    ? attachedRawStem(gray, width, height, candidate, gap) != null
                    : hasAttachedStem(labels, width, height, candidate, gap)) continue;
            for (Component main : heads) {
                if (main == candidate
                        || nearestHeadStaff(staffs, main.centerY) != staff
                        || main.area < candidate.area * 2.5f) continue;
                float distance = Math.abs(main.centerY - candidate.centerY);
                if (Math.abs(main.centerX - candidate.centerX) <= gap * .90f
                        && distance >= gap * .78f
                        && distance <= gap * 2.5f
                        && main.maxX - main.minX + 1 >= gap * .85f) {
                    dots.add(candidate);
                    break;
                }
            }
        }
        return dots;
    }

    /**
     * Beams already encode eighth notes and shorter values. For otherwise identical un-beamed
     * notes, read the two features that spacing cannot provide: a stem distinguishes whole notes,
     * and an open notehead distinguishes half/whole notes from quarters. Keeping this written
     * value on the event prevents engraving spacing from randomly changing playback speed.
     */
    private static float detectUnbeamedDuration(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int beamCount) {
        return detectUnbeamedDuration(labels, gray, width, height, head, gap, beamCount, List.of());
    }

    private static float detectUnbeamedDuration(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int beamCount,
            List<Component> heads) {
        boolean open = hasOpenCenter(labels, gray, width, height, head, gap);
        if (open
                && beamCount > 0
                && PaleFilledHeadCore.isFilled(
                        gray,
                        width,
                        height,
                        head.minX,
                        head.minY,
                        head.maxX,
                        head.maxY,
                        head.centerX,
                        head.centerY,
                        gap)) open = false;
        boolean stem = hasAttachedStem(labels, width, height, head, gap);
        // A horizontal whole oval can sit immediately below a separate melody
        // head. Nearby semantic stem pixels do not establish physical attachment.
        if (open
                && stem
                && gray != null
                && head.maxX - head.minX + 1 >= gap * 1.7f
                && head.maxY - head.minY + 1 <= gap * 1.25f
                && attachedRawStem(
                                gray,
                                width,
                                height,
                                head,
                                gap,
                                Math.max(1, Math.round(gap * .16f)),
                                205)
                        == null) stem = false;
        if (open
                && stem
                && gray != null
                && wideOpenChordHasNoExteriorShaft(labels, gray, width, height, head, heads, gap))
            stem = false;
        if (open
                && stem
                && gray != null
                && !hasStemAtHead(labels, width, height, head, gap)
                && attachedRawStem(gray, width, height, head, gap) == null) stem = false;
        if (open) return stem ? ScoreNoteEvent.DURATION_HALF : ScoreNoteEvent.DURATION_WHOLE;
        if (beamCount > 0) return ScoreNoteEvent.DURATION_UNKNOWN;
        // A filled head is a quarter even when a thin stem was missed by segmentation. Treating it
        // as unknown would send the renderer back to noisy horizontal spacing.
        return ScoreNoteEvent.DURATION_QUARTER;
    }

    /** An aligned open chord needs a shaft beyond its outer ovals, not just their walls. */
    private static boolean wideOpenChordHasNoExteriorShaft(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Component> heads,
            float gap) {
        List<Component> chord = new ArrayList<>();
        boolean wide = false;
        int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        for (var other : heads) {
            if (Math.abs(other.centerX - head.centerX) > gap * .45f
                    || Math.abs(other.centerY - head.centerY) > gap * 3.5f
                    || other.maxX - other.minX + 1 < gap * 1.4f
                    || other.maxY - other.minY + 1 > gap * 1.25f
                    || other.maxX - other.minX + 1 < gap * 1.7f
                            && other.maxY - other.minY + 1 > gap * 1.1f
                    || !hasOpenCenter(labels, gray, width, height, other, gap)) continue;
            chord.add(other);
            wide |= other.maxX - other.minX + 1 >= gap * 1.7f;
            top = Math.min(top, other.minY);
            bottom = Math.max(bottom, other.maxY);
        }
        if (chord.size() < 2 || !wide || !chord.contains(head)) return false;
        for (var other : chord) {
            int[] shaft =
                    attachedRawStem(
                            gray,
                            width,
                            height,
                            other,
                            gap,
                            Math.max(1, Math.round(gap * .16f)),
                            205);
            if (shaft != null
                    && (shaft[2] < 0 ? shaft[1] < top - gap * .4f : shaft[1] > bottom + gap * .4f))
                return false;
        }
        return true;
    }

    private static boolean hasOpenCenter(
            byte[] labels, byte[] gray, int width, int height, Component head, float gap) {
        float headWidth = head.maxX - head.minX + 1f;
        float headHeight = head.maxY - head.minY + 1f;
        float semanticFill = head.area / Math.max(1f, headWidth * headHeight);
        // A merged pair of filled seconds has low rectangular fill too. When raw ink is
        // available require a real white pocket, not just the model component's outline.
        if (gray == null || gray.length != width * height) return semanticFill <= .62f;

        int left = Math.max(0, Math.round(head.centerX - headWidth * .23f));
        int right = Math.min(width - 1, Math.round(head.centerX + headWidth * .23f));
        int top = Math.max(0, Math.round(head.centerY - headHeight * .28f));
        int bottom = Math.min(height - 1, Math.round(head.centerY + headHeight * .28f));
        int samples = 0, brightHole = 0;
        for (int y = top; y <= bottom; y++)
            for (int x = left; x <= right; x++) {
                samples++;
                int index = y * width + x;
                // The segmentation model sometimes paints the complete oval as NOTEHEAD even when
                // the rendered page has a plainly white center. The raw page pixel is the direct
                // evidence for a hollow half/whole head; requiring a non-NOTEHEAD semantic label
                // here
                // converted those sustained notes to one-beat quarters and left silent measure
                // tails.
                if ((gray[index] & 0xff) >= 185) brightHole++;
            }
        if (samples >= 3 && brightHole >= Math.max(2, Math.round(samples * .34f))) return true;
        // A full-size oval crossed by a rule can retain only two tiny pockets.
        // Prove closed raw-ink topology instead of counting exterior corner pixels.
        if (headWidth >= gap * .95f
                && headWidth <= gap * 1.3f
                && headHeight >= gap * .65f
                && headHeight <= gap * 1.1f
                && centralHeadRule(gray, width, height, head, gap)
                && enclosedHollowInk(gray, width, head, true)) return true;
        // A tiny grace head or a fragmented model component can enclose a few antialiased
        // corner pixels without being a hollow half. Expanded pocket recovery is full-size only.
        if (headWidth < gap * 1.05f
                || headHeight < gap * .7f
                || headHeight < gap * .85f && headWidth < gap * 1.15f)
            return headWidth >= gap * .95f
                    && headHeight >= gap * .65f
                    && centralHeadRule(gray, width, height, head, gap)
                    && enclosedHollowInk(gray, width, head);
        // Tinted scans and staff lines leave gray, rather than white, enclosed pockets.
        // Adapt only this four-sided pocket test; the simple center test stays conservative.
        int[] shades = new int[256];
        int shadeCount = 0;
        int margin = Math.max(2, Math.round(gap));
        for (int y = Math.max(0, head.minY - margin);
                y <= Math.min(height - 1, head.maxY + margin);
                y++)
            for (int x = Math.max(0, head.minX - margin);
                    x <= Math.min(width - 1, head.maxX + margin);
                    x++) {
                shades[gray[y * width + x] & 255]++;
                shadeCount++;
            }
        int background = 255, seen = 0;
        for (int value = 0; value < 256; value++)
            if ((seen += shades[value]) >= shadeCount * .8) {
                background = value;
                break;
            }
        int pocketThreshold = Math.min(185, Math.max(145, background - 40));
        // A thick ledger/staff line can cover the middle of a hollow oval. Look for the
        // remaining enclosed white pockets above/below it, not just an average over its centre.
        // Requiring dark ink on both sides rejects the exterior corners of a filled/slanted head.
        int enclosed = 0, pocketRows = 0, abovePocket = 0, belowPocket = 0;
        for (int y = Math.max(head.minY + 1, top - 2);
                y <= Math.min(head.maxY - 1, bottom + 2);
                y++) {
            int rowHoles = 0;
            for (int x = Math.max(head.minX + 1, left - 1);
                    x <= Math.min(head.maxX - 1, right + 1);
                    x++) {
                if ((gray[y * width + x] & 0xff) < pocketThreshold) continue;
                boolean inkLeft = false, inkRight = false, inkAbove = false, inkBelow = false;
                for (int xx = head.minX; xx < x; xx++)
                    if ((gray[y * width + xx] & 0xff) <= 135) {
                        inkLeft = true;
                        break;
                    }
                for (int xx = x + 1; xx <= head.maxX; xx++)
                    if ((gray[y * width + xx] & 0xff) <= 135) {
                        inkRight = true;
                        break;
                    }
                for (int yy = head.minY; yy < y; yy++)
                    if ((gray[yy * width + x] & 0xff) <= 135) {
                        inkAbove = true;
                        break;
                    }
                for (int yy = y + 1; yy <= head.maxY; yy++)
                    if ((gray[yy * width + x] & 0xff) <= 135) {
                        inkBelow = true;
                        break;
                    }
                if (inkLeft && inkRight && inkAbove && inkBelow) rowHoles++;
            }
            enclosed += rowHoles;
            if (rowHoles >= 2) pocketRows++;
            if (y < head.centerY - 1) abovePocket += rowHoles;
            if (y > head.centerY + 1) belowPocket += rowHoles;
        }
        // Heavy outlines can leave small pockets above and below a staff rule.
        // Require more enclosed rows before accepting their smaller combined area.
        return pocketRows >= 2
                        && enclosed >= Math.max(4, Math.round(headWidth * headHeight * .055f))
                || headWidth <= gap * 1.3f && headHeight <= gap && pocketRows >= 3 && enclosed >= 6
                || headWidth <= gap * 1.3f
                        && headHeight <= gap * .95f
                        && enclosed >= 5
                        && abovePocket >= 2
                        && belowPocket >= 2
                        && centralHeadRule(gray, width, height, head, gap)
                || pocketRows >= Math.max(4, Math.round(gap * .22f))
                        && enclosed >= Math.max(8, Math.round(headWidth * headHeight * .04f))
                || fadedEnclosedHeadPocket(gray, width, head, gap, left, right, top, bottom);
    }

    private static boolean centralHeadRule(
            byte[] gray, int width, int height, Component head, float gap) {
        int left = Math.max(0, head.minX - Math.round(gap * .5f)),
                right = Math.min(width - 1, head.maxX + Math.round(gap * .5f));
        for (int y = Math.max(0, Math.round(head.centerY) - 1);
                y <= Math.min(height - 1, Math.round(head.centerY) + 1);
                y++) {
            int ink = 0;
            for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) <= 135) ink++;
            if (ink >= (right - left + 1) * .9f) return true;
        }
        return false;
    }

    /** A closed white pocket, not the exterior beside a diagonal tremolo stroke. */
    private static boolean enclosedHollowInk(byte[] gray, int width, Component head) {
        return enclosedHollowInk(gray, width, head, false);
    }

    private static boolean enclosedHollowInk(
            byte[] gray, int width, Component head, boolean interiorOnly) {
        int w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1, total = 0;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int seed = 0; seed < w * h; seed++) {
            if (seen[seed]
                    || (gray[(head.minY + seed / w) * width + head.minX + seed % w] & 255) < 185)
                continue;
            int size = 1, read = 0;
            boolean edge = false;
            queue[0] = seed;
            seen[seed] = true;
            while (read < size) {
                int at = queue[read++], x = at % w, y = at / w;
                edge |= x == 0 || x == w - 1 || y == 0 || y == h - 1;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
                        int next = yy * w + xx;
                        if (!seen[next]
                                && (gray[(head.minY + yy) * width + head.minX + xx] & 255) >= 185) {
                            seen[next] = true;
                            queue[size++] = next;
                        }
                    }
            }
            if (!edge && size >= 2) {
                if (!interiorOnly) total += size;
                else
                    for (int i = 0; i < size; i++) {
                        int x = head.minX + queue[i] % w, y = head.minY + queue[i] / w;
                        // Adjacent filled heads can enclose an exterior corner. Only the
                        // central oval supplies this ruled-head recovery evidence.
                        if (Math.abs(x - head.centerX) <= w * .3f
                                && Math.abs(y - head.centerY) <= h * .3f) total++;
                    }
            }
        }
        return total >= 5;
    }

    /** Light outlines around a ledger-obscured hollow head can miss the dark
     * wall cutoff. Require substantial enclosed area over several rows, with
     * independently darker ink on all four sides of every accepted pixel. */
    private static boolean fadedEnclosedHeadPocket(
            byte[] gray,
            int width,
            Component head,
            float gap,
            int left,
            int right,
            int top,
            int bottom) {
        float w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1;
        // An upright numeral can have two open bowls that satisfy four ray
        // tests. This fallback is for a horizontal note oval, not a tall glyph.
        if (w < gap * 1.1f || h < gap * .9f || h > w * 1.05f) return false;
        int enclosed = 0, rows = 0, fadedEnclosed = 0, fadedRows = 0, fadedPeak = 0;
        for (int y = Math.max(head.minY + 1, top - 2);
                y <= Math.min(head.maxY - 1, bottom + 2);
                y++) {
            int row = 0, fadedRow = 0;
            for (int x = Math.max(head.minX + 1, left - 1);
                    x <= Math.min(head.maxX - 1, right + 1);
                    x++) {
                int pocket = gray[y * width + x] & 255;
                if (pocket < 185) continue;
                int l = 255, r = 255, a = 255, b = 255;
                for (int xx = head.minX; xx < x; xx++) l = Math.min(l, gray[y * width + xx] & 255);
                for (int xx = x + 1; xx <= head.maxX; xx++)
                    r = Math.min(r, gray[y * width + xx] & 255);
                for (int yy = head.minY; yy < y; yy++) a = Math.min(a, gray[yy * width + x] & 255);
                for (int yy = y + 1; yy <= head.maxY; yy++)
                    b = Math.min(b, gray[yy * width + x] & 255);
                int wall = Math.max(Math.max(l, r), Math.max(a, b));
                if (wall <= 200 && pocket >= wall + 30) {
                    if (wall <= 180) row++;
                    if (wall > 135) {
                        fadedRow++;
                        fadedPeak = Math.max(fadedPeak, pocket);
                    }
                }
            }
            enclosed += row;
            if (row >= 2) rows++;
            fadedEnclosed += fadedRow;
            if (fadedRow >= 2) fadedRows++;
        }
        return rows >= 3 && enclosed >= Math.max(8, Math.round(w * h * .055f))
                || fadedPeak >= 200
                        && fadedRows >= 3
                        && fadedEnclosed >= Math.max(8, Math.round(w * h * .025f));
    }

    /** Reconnect narrow cuts in an accidental's semantic mask using the printed ink. */
    private static List<AccidentalCandidate> joinLocalAccidentalFragments(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            List<Staff> staffs) {
        List<AccidentalCandidate> sameLabel =
                joinAccidentalFragments(labels, gray, width, height, candidates, staffs, false);
        List<AccidentalCandidate> result = new ArrayList<>();
        // Preserve compact crosses before a greedy union can absorb nearby ledger ink.
        // Only complete four-arm glyphs qualify; partial islands remain ordinary candidates.
        for (int i = 0; i < candidates.size(); i++) {
            Component a = candidates.get(i).component;
            Staff staff = nearestHeadStaff(staffs, a.centerY);
            if (staff == null) continue;
            List<Component> nearby = new ArrayList<>();
            for (int j = i + 1; j < candidates.size(); j++) {
                Component b = candidates.get(j).component;
                if (Math.max(a.maxX, b.maxX) - Math.min(a.minX, b.minX) + 1 <= staff.gap * 1.4f
                        && Math.max(a.maxY, b.maxY) - Math.min(a.minY, b.minY) + 1
                                <= staff.gap * 1.4f) nearby.add(b);
            }
            for (int j = 0; j < nearby.size(); j++)
                for (int k = j; k < nearby.size(); k++) {
                    Component b = nearby.get(j), c = nearby.get(k);
                    int l = Math.min(a.minX, Math.min(b.minX, c.minX)),
                            r = Math.max(a.maxX, Math.max(b.maxX, c.maxX));
                    int t = Math.min(a.minY, Math.min(b.minY, c.minY)),
                            v = Math.max(a.maxY, Math.max(b.maxY, c.maxY));
                    if (gray != null
                            ? DoubleSharpGlyph.matchesRaw(
                                    gray, width, height, l, t, r, v, staff.gap)
                            : DoubleSharpGlyph.matches(
                                    labels, width, height, l, t, r, v, (byte) 0, staff.gap))
                        result.add(
                                new AccidentalCandidate(
                                        new Component(
                                                a.area + b.area + (j == k ? 0 : c.area),
                                                l,
                                                r,
                                                t,
                                                v,
                                                (l + r) / 2f,
                                                (t + v) / 2f),
                                        (byte) 0));
                }
        }
        // Joining a stray staff fragment can spoil a complete sharp or natural.
        // Retain the intact reading ahead of repaired alternatives at the same edge.
        for (AccidentalCandidate candidate : candidates) {
            Staff staff = nearestHeadStaff(staffs, candidate.component.centerY);
            if (staff != null
                    && (isSharpGlyph(labels, width, height, candidate, staff.pitchGap)
                            || isNaturalGlyph(labels, width, height, candidate, staff.pitchGap)
                            || DoubleSharpGlyph.matchesRaw(
                                    gray,
                                    width,
                                    height,
                                    candidate.component.minX,
                                    candidate.component.minY,
                                    candidate.component.maxX,
                                    candidate.component.maxY,
                                    staff.gap))) result.add(candidate);
        }
        result.addAll(sameLabel);
        // Proved sharp crossbars must remain available as a pair after a greedy
        // union of their faint connecting shafts loses the individual seeds.
        for (AccidentalCandidate candidate : candidates)
            if (candidate.label == OmrMeasurePostProcessor.NOTEHEAD) result.add(candidate);
        // Keep the original glyphs: a cross-label union can also include a
        // nearby rule fragment and must not replace an already legible natural.
        for (AccidentalCandidate candidate :
                joinAccidentalFragments(labels, gray, width, height, sameLabel, staffs, true))
            if (candidate.label == 0) result.add(candidate);
        return result;
    }

    private static List<AccidentalCandidate> joinAccidentalFragments(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            List<Staff> staffs,
            boolean mixedLabels) {
        if (gray == null || gray.length != labels.length) return candidates;
        List<AccidentalCandidate> joined = new ArrayList<>(candidates);
        for (int i = 0; i < joined.size(); i++) {
            AccidentalCandidate a = joined.get(i);
            Staff staff = nearestHeadStaff(staffs, a.component.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            for (int j = i + 1; j < joined.size(); j++) {
                AccidentalCandidate b = joined.get(j);
                if (!mixedLabels && a.label != b.label) continue;
                Component ac = a.component, bc = b.component;
                int left = Math.min(ac.minX, bc.minX), right = Math.max(ac.maxX, bc.maxX);
                int top = Math.min(ac.minY, bc.minY), bottom = Math.max(ac.maxY, bc.maxY);
                int reach = Math.max(2, Math.round(gap * .42f));
                if (right - left + 1 > gap * 1.65f
                        || bottom - top + 1 > gap * 3.70f
                        || Math.max(ac.minX, bc.minX) - Math.min(ac.maxX, bc.maxX) > reach
                        || Math.max(ac.minY, bc.minY) - Math.min(ac.maxY, bc.maxY) > reach)
                    continue;
                boolean connected = false;
                for (int y = ac.minY; y <= ac.maxY && !connected; y++)
                    for (int x = ac.minX; x <= ac.maxX && !connected; x++) {
                        if (!a.matches(labels[y * width + x])) continue;
                        for (int by = Math.max(bc.minY, y - reach);
                                by <= Math.min(bc.maxY, y + reach) && !connected;
                                by++)
                            for (int bx = Math.max(bc.minX, x - reach);
                                    bx <= Math.min(bc.maxX, x + reach);
                                    bx++) {
                                if (!b.matches(labels[by * width + bx])) continue;
                                int steps = Math.max(Math.abs(bx - x), Math.abs(by - y));
                                if (steps == 0) continue;
                                boolean ink = true;
                                for (int k = 1; k < steps; k++) {
                                    int px = Math.round(x + (bx - x) * (float) k / steps),
                                            py = Math.round(y + (by - y) * (float) k / steps);
                                    if ((gray[py * width + px] & 255) > 180) {
                                        ink = false;
                                        break;
                                    }
                                }
                                if (ink) {
                                    connected = true;
                                    break;
                                }
                            }
                    }
                if (!connected) continue;
                int area = ac.area + bc.area;
                a =
                        new AccidentalCandidate(
                                new Component(
                                        area,
                                        left,
                                        right,
                                        top,
                                        bottom,
                                        (ac.centerX * ac.area + bc.centerX * bc.area) / area,
                                        (ac.centerY * ac.area + bc.centerY * bc.area) / area),
                                (byte) (a.label == b.label ? a.label : 0));
                joined.set(i, a);
                joined.remove(j);
                j = i;
            }
        }
        return joined;
    }

    /** Restore shared stem/crossbar pixels only inside an accidental-labelled glyph. */
    private static boolean rawSharpFromSeed(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        return rawSharpFromSeedEvidence(gray, width, height, candidates, head, gap, false);
    }

    private static boolean completeRawSharpFromSeed(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        return rawSharpFromSeedEvidence(gray, width, height, candidates, head, gap, true);
    }

    private static boolean rawSharpFromSeedEvidence(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap,
            boolean completeOnly) {
        if (gray == null) return false;
        for (AccidentalCandidate seed : candidates) {
            Component c = seed.component;
            if ((seed.label != OmrMeasurePostProcessor.CLEF_OR_KEY && seed.label != 0)
                    || head.minX - c.maxX > gap * 1.35f
                    || head.minX - c.maxX < gap * .10f
                            && (head.minX - c.maxX < gap * -.15f
                                    || c.centerX > head.minX - gap * .45f)
                    || Math.abs(c.centerY - head.centerY) > gap * .9f
                    || c.maxY - c.minY + 1 < gap * 1.55f
                    || c.maxY - c.minY + 1 > gap * 3.65f
                    || c.maxX - c.minX + 1 < gap * .65f
                    || c.maxX - c.minX + 1 > gap * 1.8f) continue;
            if (seed.label == 0
                    && rawSharpInBounds(
                            gray, width, height, head, gap, c.minX, c.maxX, c.minY, c.maxY, true))
                return true;
            int side = Math.max(1, Math.round(gap * .3f)),
                    end = Math.max(1, Math.round(gap * 1.1f));
            int right =
                    Math.min(
                            width - 1, Math.min(c.maxX + side, Math.round(head.minX - gap * .15f)));
            if (rawSharpInBounds(
                    gray,
                    width,
                    height,
                    head,
                    gap,
                    Math.max(0, c.minX - side),
                    right,
                    Math.max(0, c.minY - end),
                    Math.min(height - 1, c.maxY + end),
                    true)) return true;
            boolean extended = false;
            for (AccidentalCandidate other : candidates) {
                Component whole = other.component;
                if (other.label != 0
                        || whole == c
                        || whole.minX > c.minX
                        || whole.maxX < c.maxX
                        || whole.minY > c.minY
                        || whole.maxY < c.maxY
                        || whole.area <= c.area * 1.05f
                        || whole.maxY - whole.minY <= c.maxY - c.minY + gap * .15f
                        || whole.maxX - whole.minX > c.maxX - c.minX + gap * .25f) continue;
                if (!rawSharpInBounds(
                        gray,
                        width,
                        height,
                        head,
                        gap,
                        whole.minX,
                        whole.maxX,
                        whole.minY,
                        whole.maxY,
                        true)) extended = true;
            }
            if (!completeOnly
                    && head.minX - c.maxX >= gap * .10f
                    && seed.label == OmrMeasurePostProcessor.CLEF_OR_KEY
                    && !extended
                    && rawSharpInBounds(
                            gray, width, height, head, gap, c.minX, c.maxX, c.minY, c.maxY, false))
                return true;
        }
        List<Component> bars = new ArrayList<>();
        for (AccidentalCandidate candidate : candidates) {
            Component c = candidate.component;
            if (c.maxX > head.minX - gap * .10f
                    || head.minX - c.maxX > gap * 1.6f
                    || Math.abs(c.centerY - head.centerY) > gap * 1.3f
                    || c.maxY - c.minY > gap
                    || c.maxX - c.minX < gap * .35f
                    || c.maxX - c.minX > gap * 1.25f) continue;
            bars.add(c);
        }
        // Lost thin spines leave two separate semantic crossbars. Their aligned
        // pair locates a crop; the printed sharp still has to prove its shape.
        for (Component upper : bars)
            for (Component lower : bars) {
                float dy = lower.centerY - upper.centerY;
                if (dy < gap * .5f
                        || dy > gap * 1.5f
                        || Math.abs(upper.centerX - lower.centerX) > gap * .5f) continue;
                int left = Math.max(0, Math.round(Math.min(upper.minX, lower.minX) - gap * .4f));
                int right =
                        Math.min(
                                width - 1,
                                Math.min(
                                        Math.round(head.minX - gap * .15f),
                                        Math.round(Math.max(upper.maxX, lower.maxX) + gap * .4f)));
                int top = Math.max(0, Math.round(upper.minY - gap * 1.1f));
                int bottom = Math.min(height - 1, Math.round(lower.maxY + gap * 1.1f));
                if (right <= left || bottom <= top) continue;
                if (rawSharpInBounds(
                        gray, width, height, head, gap, left, right, top, bottom, true))
                    return true;
            }
        return false;
    }

    private static boolean rawSharpInBounds(
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int left,
            int right,
            int top,
            int bottom,
            boolean checkEdges) {
        int w = right - left + 1, h = bottom - top + 1;
        int reach = Math.max(3, Math.round(gap * .6f)), probe = Math.max(2, Math.round(gap * .2f));
        // Preserve the original exclusive cutoffs for complete semantic seeds.
        for (int threshold : checkEdges ? new int[] {180, 225} : new int[] {179, 224}) {
            byte[] mask = new byte[w * h];
            int area = 0, minX = w, maxX = -1, minY = h, maxY = -1;
            long sx = 0, sy = 0;
            for (int y = top; y <= bottom; y++) {
                int outside = 0, total = 0;
                for (int x = Math.max(0, left - reach);
                        x <= Math.min(width - 1, right + reach);
                        x++)
                    if (x < left || x > right) {
                        total++;
                        if ((gray[y * width + x] & 255) <= threshold) outside++;
                    }
                boolean rule = total > 0 && outside > total * .8f;
                for (int x = left; x <= right; x++) {
                    if ((gray[y * width + x] & 255) > threshold) continue;
                    if (rule
                            && (y < probe
                                    || y + probe >= height
                                    || (gray[(y - probe) * width + x] & 255) > threshold
                                    || (gray[(y + probe) * width + x] & 255) > threshold)) continue;
                    int xx = x - left, yy = y - top;
                    mask[yy * w + xx] = OmrMeasurePostProcessor.CLEF_OR_KEY;
                    area++;
                    sx += xx;
                    sy += yy;
                    minX = Math.min(minX, xx);
                    maxX = Math.max(maxX, xx);
                    minY = Math.min(minY, yy);
                    maxY = Math.max(maxY, yy);
                }
            }
            if (area == 0) continue;
            Component glyph =
                    new Component(
                            area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
            if (checkEdges) {
                Component core =
                        new Component(
                                1,
                                left,
                                right,
                                Math.round(head.centerY - gap * .75f),
                                Math.round(head.centerY + gap * .75f),
                                (left + right) * .5f,
                                head.centerY);
                glyph = retainSeedConnectedInk(mask, w, h, core, left, top);
                if (glyph == null
                        || rawStrokeLeavesCrop(gray, width, height, mask, w, h, left, top, gap))
                    continue;
            }
            float center =
                    sharpPitchCenter(
                            mask,
                            w,
                            h,
                            new AccidentalCandidate(glyph, OmrMeasurePostProcessor.CLEF_OR_KEY),
                            gap);
            if (Float.isFinite(center) && Math.abs(center + top - head.centerY) < gap * .4f)
                return true;
        }
        if (checkEdges) {
            byte[] faint = FaintSharpInk.crop(gray, width, height, left, top, right, bottom, gap);
            if (faint != null) {
                for (Component glyph :
                        findComponents(faint, w, h, OmrMeasurePostProcessor.CLEF_OR_KEY)) {
                    if (glyph.minX == 0
                            || glyph.maxX == w - 1
                            || glyph.minY == 0
                            || glyph.maxY == h - 1) continue;
                    float center =
                            sharpPitchCenter(
                                    faint,
                                    w,
                                    h,
                                    new AccidentalCandidate(
                                            glyph, OmrMeasurePostProcessor.CLEF_OR_KEY),
                                    gap);
                    if (Float.isFinite(center) && Math.abs(center + top - head.centerY) < gap * .4f)
                        return true;
                }
            }
        }
        return false;
    }

    /** A natural's right upper connector can be mislabeled as a small head.
     * Require the complete natural from both its accidental seed and the joined
     * candidate, with an independent full-sized following note as crop anchor. */
    private static List<Component> naturalCrossbarHeads(
            byte[] gray,
            int width,
            int height,
            List<Component> heads,
            List<Staff> staffs,
            List<AccidentalCandidate> candidates) {
        List<Component> result = new ArrayList<>();
        if (gray == null) return result;
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (candidate.area > gap * gap * .5f
                    || candidate.maxX - candidate.minX + 1 > gap * .9f
                    || candidate.maxY - candidate.minY + 1 > gap * .9f) continue;
            boolean owned = false;
            for (AccidentalCandidate accidental : candidates) {
                Component seed = accidental.component;
                if (seed.minX >= candidate.minX
                        || candidate.centerX - seed.centerX > gap * 1.3f
                        || candidate.centerX < seed.centerX
                        || Math.abs(seed.centerY - candidate.centerY) > gap * 1.7f) continue;
                int left = Math.min(seed.minX, candidate.minX),
                        right = Math.max(seed.maxX, candidate.maxX);
                int top = Math.min(seed.minY, candidate.minY),
                        bottom = Math.max(seed.maxY, candidate.maxY);
                if (right - left + 1 > gap * 1.65f || bottom - top + 1 > gap * 3.1f) continue;
                Component joined =
                        new Component(
                                seed.area + candidate.area,
                                left,
                                right,
                                top,
                                bottom,
                                (left + right) * .5f,
                                (top + bottom) * .5f);
                for (Component following : heads) {
                    if (following == candidate
                            || nearestHeadStaff(staffs, following.centerY) != staff
                            || following.minX - right < gap * .1f
                            || following.centerX - candidate.centerX < gap * .8f
                            || following.centerX - candidate.centerX > gap * 4
                            || Math.abs(following.centerY - candidate.centerY) > gap * 1.5f
                            || following.maxX - following.minX + 1 < gap * .85f
                            || following.maxY - following.minY + 1 < gap * .5f) continue;
                    if (rawNaturalAtSeed(gray, width, height, seed, following, gap)
                            && rawNaturalAtSeed(gray, width, height, joined, following, gap)) {
                        owned = true;
                        break;
                    }
                }
                if (owned) break;
            }
            if (owned) result.add(candidate);
        }
        return result;
    }

    /** Two surviving crossbars can locate a faded natural whose thin spines were
     * labelled as stems. Confirm the offset endpoints in the original pixels. */
    private static boolean rawNaturalFromCrossbars(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        return rawNaturalFromCrossbars(gray, width, height, candidates, head, gap, -1);
    }

    private static int nearestFlatRightEdge(
            byte[] labels,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        int right = -1;
        for (AccidentalCandidate candidate : candidates)
            if (candidate.component.maxX < head.minX
                    && isFlatGlyph(labels, width, height, candidate, gap)
                    && Math.abs(flatPitchCenter(labels, width, candidate, gap) - head.centerY)
                            < gap * .45f) right = Math.max(right, candidate.component.maxX);
        return right;
    }

    /** A cancellation natural printed before a new flat must not override that flat. */
    private static boolean rawNaturalFromCrossbars(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap,
            int minimumRightEdge) {
        if (gray == null) return false;
        for (AccidentalCandidate seed : candidates) {
            Component glyph = seed.component;
            if (glyph.maxX < minimumRightEdge
                    || head.minX - glyph.maxX < 0
                    || head.minX - glyph.maxX > gap * 1.35f
                    || Math.abs(glyph.centerY - head.centerY) > gap * .9f
                    || glyph.maxY - glyph.minY < gap * .55f
                    || glyph.maxX - glyph.minX < gap * .35f
                    || glyph.maxX - glyph.minX > gap * 1.5f) continue;
            if (rawNaturalAtSeed(gray, width, height, glyph, head, gap)) return true;
        }
        List<Component> bars = new ArrayList<>();
        for (var c : candidates) {
            Component g = c.component;
            if (g.maxX < minimumRightEdge
                    || g.maxX > head.minX + gap * .1f
                    || head.minX - g.maxX > gap * 1.6f
                    || Math.abs(g.centerY - head.centerY) > gap * 1.3f
                    || g.maxY - g.minY > gap * .6f
                    || g.maxX - g.minX < gap * .35f
                    || g.maxX - g.minX > gap * 1.25f) continue;
            bars.add(g);
        }
        for (Component a : bars)
            for (Component b : bars) {
                float dy = b.centerY - a.centerY;
                if (dy < gap * .65f
                        || dy > gap * 1.5f
                        || Math.abs(a.centerX - b.centerX) > gap * .5f) continue;
                int left = Math.max(0, Math.round(Math.min(a.minX, b.minX) - gap * .2f));
                int right =
                        Math.min(
                                Math.round(head.minX - gap * .15f),
                                Math.round(Math.max(a.maxX, b.maxX)));
                int top = Math.max(0, Math.round(a.minY - gap * .8f)),
                        bottom = Math.min(height - 1, Math.round(b.maxY + gap * .8f));
                int w = right - left + 1, h = bottom - top + 1;
                if (w <= 0 || h <= 0) continue;
                byte[] mask = new byte[w * h];
                int area = 0, minX = w, maxX = -1, minY = h, maxY = -1;
                long sx = 0, sy = 0;
                for (int y = top; y <= bottom; y++) {
                    int outside = 0, samples = 0;
                    for (int x = Math.max(0, left - Math.round(gap));
                            x <= Math.min(width - 1, right + Math.round(gap));
                            x++)
                        if (x < left || x > right) {
                            samples++;
                            if ((gray[y * width + x] & 255) < 225) outside++;
                        }
                    if (samples > 0 && outside > samples * .80f) continue;
                    for (int x = left; x <= right; x++)
                        if ((gray[y * width + x] & 255) < 225) {
                            int xx = x - left, yy = y - top;
                            mask[yy * w + xx] = OmrMeasurePostProcessor.SYMBOL;
                            area++;
                            sx += xx;
                            sy += yy;
                            minX = Math.min(minX, xx);
                            maxX = Math.max(maxX, xx);
                            minY = Math.min(minY, yy);
                            maxY = Math.max(maxY, yy);
                        }
                }
                if (area < 3
                        || rawStrokeLeavesCrop(gray, width, height, mask, w, h, left, top, gap))
                    continue;
                Component g =
                        new Component(
                                area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
                if (isNaturalGlyph(
                        mask,
                        w,
                        h,
                        new AccidentalCandidate(g, OmrMeasurePostProcessor.SYMBOL),
                        gap)) return true;
            }
        return false;
    }

    /** Extend only the missing upper-left spine of an otherwise semantic glyph.
     * The printed extension must end inside the crop and the completed mask must
     * still prove both natural endpoints and both separated connectors. */
    private static boolean naturalFromUpperSpine(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        if (gray == null || gray.length != width * height) return false;
        for (AccidentalCandidate candidate : candidates) {
            Component c = candidate.component;
            int w = c.maxX - c.minX + 1, gh = c.maxY - c.minY + 1;
            if (candidate.label != OmrMeasurePostProcessor.CLEF_OR_KEY
                    || head.minX - c.maxX < gap * .1f
                    || head.minX - c.maxX > gap * 1.35f
                    || Math.abs(c.centerY - head.centerY) > gap * .9f
                    || w < gap * .48f
                    || w > gap * 1.55f
                    || gh < gap * 1.4f
                    || gh > gap * 3.2f
                    || isSharpGlyph(labels, width, height, candidate, gap)
                    || isFlatGlyph(labels, width, height, candidate, gap)) continue;
            int[] columns = new int[w];
            for (int y = c.minY; y <= c.maxY; y++)
                for (int x = c.minX; x <= c.maxX; x++)
                    if (candidate.matches(labels[y * width + x])) columns[x - c.minX]++;
            for (int spine = 0; spine < w / 2; spine++) {
                if (columns[spine] < gh * .45f) continue;
                int top = Math.max(0, Math.round(c.minY - gap * .85f)), h = c.maxY - top + 1;
                byte[] ink = new byte[w * h];
                for (int y = c.minY; y <= c.maxY; y++)
                    for (int x = c.minX; x <= c.maxX; x++)
                        if (candidate.matches(labels[y * width + x]))
                            ink[(y - top) * w + x - c.minX] = OmrMeasurePostProcessor.SYMBOL;
                for (int y = top; y < c.minY; y++)
                    if ((gray[y * width + c.minX + spine] & 255) <= 225)
                        ink[(y - top) * w + spine] = OmrMeasurePostProcessor.SYMBOL;
                Component connected = retainSeedConnectedInk(ink, w, h, c, c.minX, top);
                if (connected == null
                        || connected.minY == 0
                        || c.minY - top - connected.minY < gap * .3f) continue;
                if (isNaturalGlyph(
                        ink,
                        w,
                        h,
                        new AccidentalCandidate(connected, OmrMeasurePostProcessor.SYMBOL),
                        gap)) return true;
            }
        }
        return false;
    }

    /** Thin natural spines can be assigned the stem label while their two
     * connectors retain the accidental label. Reconstruct only that narrow
     * printed column and require the natural's asymmetric spine endpoints. */
    private static boolean rawNaturalAtSeed(
            byte[] gray, int width, int height, Component seed, Component head, float gap) {
        return rawNaturalAtSeed(gray, width, height, seed, head, gap, 0)
                || rawNaturalAtSeed(
                        gray, width, height, seed, head, gap, Math.max(1, Math.round(gap * .16f)))
                || BeamOccludedNatural.matches(
                        gray, width, height, seed.minX, seed.maxX, head.centerY, gap);
    }

    private static boolean rawNaturalAtSeed(
            byte[] gray,
            int width,
            int height,
            Component seed,
            Component head,
            float gap,
            int margin) {
        // Gray antialiasing can fill the counter at the permissive threshold.
        // A darker core must still prove both offset spines and open crossbars.
        return rawNaturalAtSeed(gray, width, height, seed, head, gap, margin, 225)
                || rawNaturalAtSeed(gray, width, height, seed, head, gap, margin, 180);
    }

    private static boolean rawNaturalAtSeed(
            byte[] gray,
            int width,
            int height,
            Component seed,
            Component head,
            float gap,
            int margin,
            int inkThreshold) {
        int left = Math.max(0, seed.minX - margin);
        // When a natural touches the following head, its semantic box can
        // include that note's down-going stem. Leave a slightly wider paper
        // gap at this edge so the foreign stem cannot extend the natural crop.
        float headGap = head.minX - seed.maxX < gap * .10f ? .20f : .15f;
        int right =
                Math.min(
                        Math.min(width - 1, seed.maxX + margin),
                        Math.round(head.minX - gap * headGap));
        int top = Math.max(0, Math.round(head.centerY - gap * 1.8f));
        int bottom = Math.min(height - 1, Math.round(head.centerY + gap * 1.8f));
        int w = right - left + 1, h = bottom - top + 1;
        byte[] ink = new byte[w * h];
        int area = 0, minX = w, maxX = -1, minY = h, maxY = -1;
        long sx = 0, sy = 0;
        int reach = Math.max(3, Math.round(gap * .6f));
        int verticalProbe = Math.max(2, Math.round(gap * .2f));
        for (int y = top; y <= bottom; y++) {
            int outside = 0, dark = 0;
            for (int x = Math.max(0, left - reach); x <= Math.min(width - 1, right + reach); x++)
                if (x < left || x > right) {
                    outside++;
                    if ((gray[y * width + x] & 255) <= inkThreshold) dark++;
                }
            boolean rule = outside > 0 && dark >= outside * .8f;
            for (int x = left; x <= right; x++) {
                if ((gray[y * width + x] & 255) > inkThreshold) continue;
                // Preserve a vertical spine where a rule crosses it.
                if (rule
                        && (y < verticalProbe
                                || y + verticalProbe >= height
                                || (gray[(y - verticalProbe) * width + x] & 255) > inkThreshold
                                || (gray[(y + verticalProbe) * width + x] & 255) > inkThreshold))
                    continue;
                int xx = x - left, yy = y - top;
                ink[yy * w + xx] = OmrMeasurePostProcessor.SYMBOL;
                area++;
                sx += xx;
                sy += yy;
                minX = Math.min(minX, xx);
                maxX = Math.max(maxX, xx);
                minY = Math.min(minY, yy);
                maxY = Math.max(maxY, yy);
            }
        }
        if (area == 0) return false;
        // Disconnected staff or slur ink cannot extend either natural spine.
        // Filter to the seeded accidental before checking its endpoint shape.
        for (Component part : findComponents(ink, w, h, OmrMeasurePostProcessor.SYMBOL)) {
            if (part.area < Math.max(gap * gap * .3f, seed.area * .5f)
                    || Math.abs(top + (part.minY + part.maxY) * .5f - head.centerY) > gap * .45f
                    || left + part.maxX < seed.minX
                    || left + part.minX > seed.maxX) continue;
            byte[] separate = retainExactComponent(ink, w, h, part, OmrMeasurePostProcessor.SYMBOL);
            if (!rawStrokeLeavesCrop(gray, width, height, separate, w, h, left, top, gap)
                    && isNaturalGlyphCore(
                            separate,
                            w,
                            h,
                            new AccidentalCandidate(part, OmrMeasurePostProcessor.SYMBOL),
                            gap)) return true;
        }
        Component glyph = retainSeedConnectedInk(ink, w, h, seed, left, top);
        if (glyph == null) return false;
        // A crop through an annotation can give a flat a false lower-right spine.
        // Recovered natural endpoints must finish inside the inspected column.
        if (rawStrokeLeavesCrop(gray, width, height, ink, w, h, left, top, gap)) return false;
        if (isNaturalGlyph(
                ink, w, h, new AccidentalCandidate(glyph, OmrMeasurePostProcessor.SYMBOL), gap))
            return true;
        // An interrupted staff rule can survive the raw crop at both spines,
        // giving them identical false endpoints. Recheck the same glyph with
        // long horizontal rows removed; the ink is never changed in place.
        byte[] trimmed = ink.clone();
        int radius = Math.max(3, Math.round(gap * .6f));
        for (int y = 0; y < h; y++) {
            int outside = 0, dark = 0;
            for (int x = Math.max(0, left - radius); x <= Math.min(width - 1, right + radius); x++)
                if (x < left || x > right) {
                    outside++;
                    if ((gray[(top + y) * width + x] & 255) <= inkThreshold) dark++;
                }
            if (outside > 0 && dark >= outside * .65f)
                for (int x = 0; x < w; x++) trimmed[y * w + x] = 0;
        }
        int ca = 0, cminX = w, cmaxX = -1, cminY = h, cmaxY = -1;
        long csx = 0, csy = 0;
        for (int at = 0; at < trimmed.length; at++)
            if (trimmed[at] != 0) {
                int x = at % w, y = at / w;
                ca++;
                csx += x;
                csy += y;
                cminX = Math.min(cminX, x);
                cmaxX = Math.max(cmaxX, x);
                cminY = Math.min(cminY, y);
                cmaxY = Math.max(cmaxY, y);
            }
        if (ca == 0) return false;
        Component clean =
                new Component(ca, cminX, cmaxX, cminY, cmaxY, csx / (float) ca, csy / (float) ca);
        return isNaturalGlyph(
                trimmed, w, h, new AccidentalCandidate(clean, OmrMeasurePostProcessor.SYMBOL), gap);
    }

    /** A nearby disconnected slur must not become an endpoint of the seeded accidental. */
    private static Component retainSeedConnectedInk(
            byte[] ink, int width, int height, Component seed, int left, int top) {
        boolean[] kept = new boolean[ink.length];
        int[] queue = new int[ink.length];
        int tail = 0;
        for (int y = Math.max(0, seed.minY - top); y <= Math.min(height - 1, seed.maxY - top); y++)
            for (int x = Math.max(0, seed.minX - left);
                    x <= Math.min(width - 1, seed.maxX - left);
                    x++) {
                int at = y * width + x;
                if (ink[at] != 0) {
                    kept[at] = true;
                    queue[tail++] = at;
                }
            }
        for (int head = 0; head < tail; head++) {
            int at = queue[head], x = at % width, y = at / width;
            for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
                for (int xx = Math.max(0, x - 1); xx <= Math.min(width - 1, x + 1); xx++) {
                    int next = yy * width + xx;
                    if (ink[next] != 0 && !kept[next]) {
                        kept[next] = true;
                        queue[tail++] = next;
                    }
                }
        }
        int area = 0, minX = width, maxX = -1, minY = height, maxY = -1;
        long sx = 0, sy = 0;
        for (int at = 0; at < ink.length; at++) {
            if (!kept[at]) {
                ink[at] = 0;
                continue;
            }
            int x = at % width, y = at / width;
            area++;
            sx += x;
            sy += y;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        return area == 0
                ? null
                : new Component(area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
    }

    private static boolean rawStrokeLeavesCrop(
            byte[] gray,
            int width,
            int height,
            byte[] ink,
            int w,
            int h,
            int left,
            int top,
            float gap) {
        return rawStrokeLeavesCropAtThreshold(gray, width, height, ink, w, h, left, top, gap, 224);
    }

    private static boolean rawStrokeLeavesCropAtThreshold(
            byte[] gray,
            int width,
            int height,
            byte[] ink,
            int w,
            int h,
            int left,
            int top,
            float gap,
            int threshold) {
        int reach = Math.max(2, Math.round(gap * .16f));
        for (int side : new int[] {-1, 1}) {
            int edge = side < 0 ? 0 : h - 1;
            for (int x = 0; x < w; x++) {
                if (ink[edge * w + x] == 0) continue;
                boolean continues = true;
                for (int d = 1; d <= reach; d++) {
                    int y = top + edge + side * d;
                    if (y < 0 || y >= height || (gray[y * width + left + x] & 255) > threshold) {
                        continues = false;
                        break;
                    }
                }
                if (continues) return true;
            }
        }
        return false;
    }

    /** A complete sharp already has stronger shape evidence than a cropped raw
     * bowl. Do not reinterpret it as a flat after pitch alignment rejected it. */
    private static List<AccidentalCandidate> withoutRecognizedSharps(
            byte[] labels, int width, int height, List<AccidentalCandidate> candidates, float gap) {
        List<AccidentalCandidate> result = new ArrayList<>();
        for (AccidentalCandidate candidate : candidates)
            if (!isSharpGlyph(labels, width, height, candidate, gap)) result.add(candidate);
        return result;
    }

    /** A flat's bowl can keep its accidental label while its tall spine is labelled as a stem. */
    private static boolean rawFlatFromBowl(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        return recoverFlatFromBowl(gray, width, height, candidates, head, gap, 180)
                || recoverFlatFromBowl(gray, width, height, candidates, head, gap, 225)
                || recoverFlatFromBowl(gray, width, height, candidates, head, gap, 235);
    }

    /** A semantic flat bowl can share its upper raw stroke with the stem of a beamed note above. */
    private static boolean flatBowlUnderBeamedStem(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        if (gray == null || gray.length != width * height) return false;
        for (AccidentalCandidate seed : candidates) {
            if (seed.label != OmrMeasurePostProcessor.CLEF_OR_KEY) continue;
            Component c = seed.component;
            int bowlHeight = c.maxY - c.minY + 1, bowlWidth = c.maxX - c.minX + 1;
            if (c.maxX >= head.minX
                    || head.minX - c.maxX > gap * .45f
                    || bowlWidth < gap * .7f
                    || bowlWidth > gap * 1.1f
                    || bowlHeight < Math.round(gap * 1.1f)
                    || bowlHeight > Math.round(gap * 1.5f)
                    || Math.abs(c.centerY - head.centerY) > gap * .25f) continue;
            int stemInk = 0, beamInk = 0;
            int xLeft = Math.max(0, c.minX),
                    xRight = Math.min(width - 1, c.minX + Math.max(2, Math.round(gap * .2f)));
            int top = Math.max(0, Math.round(c.minY - gap * 3.6f));
            for (int y = top; y < c.minY - Math.round(gap * .5f); y++)
                for (int x = xLeft; x <= xRight; x++) {
                    byte label = labels[y * width + x];
                    if (label == OmrMeasurePostProcessor.STEM_OR_REST) stemInk++;
                    if (label == OmrMeasurePostProcessor.SYMBOL) beamInk++;
                }
            if (stemInk < gap * .8f || beamInk < 2) continue;
            int margin = Math.max(1, Math.round(gap * .16f));
            int left = Math.max(0, c.minX - margin),
                    right = Math.min(width - 1, Math.min(c.maxX + margin, head.minX - 2));
            int cropTop = Math.max(0, Math.round(head.centerY - gap * 1.9f));
            int bottom = Math.min(height - 1, Math.round(head.centerY + gap * .8f));
            int w = right - left + 1, h = bottom - cropTop + 1;
            if (w <= 0 || h <= 0) continue;
            byte[] ink =
                    flatInkAtThreshold(gray, width, height, left, right, cropTop, bottom, gap, 180);
            clearCrossingBeamInk(ink, labels, width, left, cropTop, w, h, c, gap, candidates);
            Component connected = retainSeedConnectedInk(ink, w, h, c, left, cropTop);
            if (connected == null) continue;
            var glyph = new AccidentalCandidate(connected, OmrMeasurePostProcessor.SYMBOL);
            if (isNaturalGlyph(ink, w, h, glyph, gap)
                    || isSharpGlyph(ink, w, h, glyph, gap)
                    || !isFlatGlyph(ink, w, h, glyph, gap)
                    || Math.abs(cropTop + flatPitchCenter(ink, w, glyph, gap) - head.centerY)
                            > gap * .45f) continue;
            byte[] fullInk =
                    flatInkAtThreshold(gray, width, height, left, right, cropTop, bottom, gap, 235);
            clearCrossingBeamInk(fullInk, labels, width, left, cropTop, w, h, c, gap, candidates);
            Component full = retainSeedConnectedInk(fullInk, w, h, c, left, cropTop);
            if (full == null) continue;
            var fullGlyph = new AccidentalCandidate(full, OmrMeasurePostProcessor.SYMBOL);
            if (isNaturalGlyph(fullInk, w, h, fullGlyph, gap)
                    || isSharpGlyph(fullInk, w, h, fullGlyph, gap)
                    || separatedUpperFlatStrokes(fullInk, w, full, gap)) continue;
            return true;
        }
        return false;
    }

    /** Recover faded spines only with a semantic bowl and the complete flat shape.
     * Disconnected neighboring marks cannot supply strokes or clip the accidental. */
    private static boolean recoverFlatFromBowl(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap,
            int threshold) {
        if (gray == null || gray.length != width * height) return false;
        for (AccidentalCandidate seed : candidates) {
            if (seed.label != OmrMeasurePostProcessor.CLEF_OR_KEY && seed.label != 0) continue;
            Component c = seed.component;
            if (c.maxX >= head.minX
                    || head.minX - c.maxX > gap * 1.35f
                    || c.maxX - c.minX + 1 < gap * .24f
                    || c.maxX - c.minX + 1 > gap * 1.25f
                    || Math.abs(c.centerY - head.centerY) > gap * .8f) continue;
            int margin = Math.max(1, Math.round(gap * .16f));
            int left = Math.max(0, c.minX - margin),
                    right = Math.min(width - 1, Math.min(c.maxX + margin, head.minX - 2));
            int top = Math.max(0, Math.round(head.centerY - gap * 2.7f)),
                    bottom = Math.min(height - 1, Math.round(head.centerY + gap * .8f));
            int w = right - left + 1, h = bottom - top + 1;
            if (w <= 0 || h <= 0) continue;
            byte[] ink =
                    flatInkAtThreshold(
                            gray, width, height, left, right, top, bottom, gap, threshold);
            // An arpeggio arrow or another tall mark can resemble a flat when clipped.
            // Its printed stroke must finish inside the inspected column.
            Component connected = retainSeedConnectedInk(ink, w, h, c, left, top);
            if (connected == null
                    || rawStrokeLeavesCropAtThreshold(
                            gray,
                            width,
                            height,
                            ink,
                            w,
                            h,
                            left,
                            top,
                            gap,
                            Math.max(224, threshold))) continue;
            var glyph = new AccidentalCandidate(connected, OmrMeasurePostProcessor.SYMBOL);
            if (!isNaturalGlyph(ink, w, h, glyph, gap)
                    && !isSharpGlyph(ink, w, h, glyph, gap)
                    && isFlatGlyph(ink, w, h, glyph, gap)
                    && Math.abs(top + flatPitchCenter(ink, w, glyph, gap) - head.centerY)
                            <= gap * .45f) {
                // An intermediate cutoff must not hide a faint second spine or
                // a continued stroke that disproves the complete flat shape.
                if (threshold == 225) {
                    byte[] fullInk =
                            flatInkAtThreshold(
                                    gray, width, height, left, right, top, bottom, gap, 235);
                    Component full = retainSeedConnectedInk(fullInk, w, h, c, left, top);
                    if (full == null
                            || rawStrokeLeavesCropAtThreshold(
                                    gray, width, height, fullInk, w, h, left, top, gap, 235))
                        continue;
                    var fullGlyph = new AccidentalCandidate(full, OmrMeasurePostProcessor.SYMBOL);
                    if (isNaturalGlyph(fullInk, w, h, fullGlyph, gap)
                            || isSharpGlyph(fullInk, w, h, fullGlyph, gap)
                            || separatedUpperFlatStrokes(fullInk, w, full, gap)) continue;
                }
                return true;
            }
        }
        return false;
    }

    /** A light second upright must not disappear when recovering a flat's darker spine. */
    private static boolean separatedUpperFlatStrokes(
            byte[] ink, int width, Component glyph, float gap) {
        int end = glyph.minY + Math.max(2, Math.round((glyph.maxY - glyph.minY + 1) * .45f));
        int required = Math.max(2, (int) Math.ceil((end - glyph.minY) * .68f));
        int last = -1;
        for (int x = glyph.minX; x <= glyph.maxX; x++) {
            int count = 0;
            for (int y = glyph.minY; y < end; y++) if (ink[y * width + x] != 0) count++;
            if (count < required) continue;
            if (last >= 0 && x - last > Math.max(2, Math.round(gap * .2f))) return true;
            last = x;
        }
        return false;
    }

    private static byte[] flatInkAtThreshold(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom,
            float gap,
            int threshold) {
        int w = right - left + 1, h = bottom - top + 1;
        byte[] ink = new byte[w * h];
        int reach = Math.max(3, Math.round(gap * .6f)), probe = Math.max(2, Math.round(gap * .2f));
        for (int y = top; y <= bottom; y++) {
            int outside = 0, dark = 0;
            for (int x = Math.max(0, left - reach); x <= Math.min(width - 1, right + reach); x++)
                if (x < left || x > right) {
                    outside++;
                    if ((gray[y * width + x] & 255) <= threshold) dark++;
                }
            boolean rule = outside > 0 && dark >= outside * .8f;
            for (int x = left; x <= right; x++) {
                if ((gray[y * width + x] & 255) > threshold) continue;
                if (rule
                        && (y < probe
                                || y + probe >= height
                                || (gray[(y - probe) * width + x] & 255) > threshold
                                || (gray[(y + probe) * width + x] & 255) > threshold)) continue;
                ink[(y - top) * w + x - left] = OmrMeasurePostProcessor.SYMBOL;
            }
        }
        return ink;
    }

    /** Raster rounding can make a beamed grace group nearly one staff space tall.
     * Only admit these larger candidates with shortened stems; the caller also
     * requires a group and a substantially larger principal for every member. */
    private static boolean roundedBeamedGraceHead(
            DetectedNote n, byte[] gray, int width, int height) {
        if (n.head.maxX - n.head.minX + 1 > Math.round(n.staffGap * 1.10f)
                || n.head.maxY - n.head.minY + 1 > Math.round(n.staffGap)
                || n.head.area > n.staffGap * n.staffGap * .80f
                || n.event.augmentationDots() != 0
                || n.event.beamCount() < 1
                || n.event.unbeamedDurationBeats() >= ScoreNoteEvent.DURATION_HALF) return false;
        int[] stem = attachedRawStem(gray, width, height, n.head, n.staffGap * .65f);
        return stem != null && Math.abs(stem[1] - n.head.centerY) <= n.staffGap * 3.1f;
    }

    /** Returns a local accidental immediately left of this head, or key-signature fallback. */
    /** Separate touching chord accidentals only when both pieces explain distinct chord heads. */
    private static List<AccidentalCandidate> splitTouchingChordAccidentals(
            byte[] labels,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            List<Component> heads,
            List<Staff> staffs) {
        List<AccidentalCandidate> result = new ArrayList<>(candidates);
        for (AccidentalCandidate candidate : candidates) {
            Component box = candidate.component;
            Staff staff = nearestHeadStaff(staffs, box.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            int glyphWidth = box.maxX - box.minX + 1, glyphHeight = box.maxY - box.minY + 1;
            if (glyphWidth < gap * 1.65f
                    || glyphWidth > gap * 3.8f
                    || glyphHeight < gap * 2f
                    || glyphHeight > gap * 5.5f
                    || isNaturalGlyph(labels, width, height, candidate, gap)
                    || isSharpGlyph(labels, width, height, candidate, gap)
                    || isFlatGlyph(labels, width, height, candidate, gap)) continue;
            AccidentalCandidate bestLeft = null, bestRight = null;
            int bestBridges = Integer.MAX_VALUE, bestArea = 0;
            int margin = Math.max(2, (int) Math.ceil(gap * .45f));
            for (int cut = box.minX + margin; cut < box.maxX - margin; cut++) {
                AccidentalCandidate left = signatureSlice(labels, width, candidate, box.minX, cut);
                AccidentalCandidate right =
                        signatureSlice(labels, width, candidate, cut + 1, box.maxX);
                if (left == null || right == null) continue;
                int retained = left.component.area + right.component.area;
                if (retained < box.area * .90f) continue;
                Component leftHead =
                        chordAccidentalHead(labels, width, height, left, heads, staffs, staff);
                if (leftHead == null) continue;
                Component rightHead =
                        chordAccidentalHead(labels, width, height, right, heads, staffs, staff);
                if (rightHead == null
                        || leftHead == rightHead
                        || Math.abs(leftHead.centerX - rightHead.centerX) > gap * .70f
                        || Math.abs(leftHead.centerY - rightHead.centerY) < gap * .45f) continue;
                int bridges = 0;
                for (int y = box.minY; y <= box.maxY; y++)
                    if (candidate.matches(labels[y * width + cut])
                            && candidate.matches(labels[y * width + cut + 1])) bridges++;
                if (bridges < bestBridges || (bridges == bestBridges && retained > bestArea)) {
                    bestBridges = bridges;
                    bestArea = retained;
                    bestLeft = left;
                    bestRight = right;
                }
            }
            if (bestLeft != null) {
                result.add(bestLeft);
                result.add(bestRight);
            }
        }
        return result;
    }

    private static Component chordAccidentalHead(
            byte[] labels,
            int width,
            int height,
            AccidentalCandidate candidate,
            List<Component> heads,
            List<Staff> staffs,
            Staff staff) {
        Component best = null;
        float distance = Float.MAX_VALUE;
        for (Component head : heads) {
            if (nearestHeadStaff(staffs, head.centerY) != staff) continue;
            if (detectWrittenAccidental(labels, width, height, List.of(candidate), head, staff.gap)
                    == ScoreNoteEvent.ACCIDENTAL_FROM_KEY) continue;
            float dx = head.centerX - candidate.component.centerX;
            if (dx < distance) {
                distance = dx;
                best = head;
            }
        }
        return best;
    }

    private static boolean rawCompactDoubleSharp(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        for (AccidentalCandidate candidate : candidates) {
            Component c = candidate.component;
            if (head.minX - c.maxX < gap * .1f
                    || head.minX - c.maxX > gap * 1.35f
                    || Math.abs((c.minY + c.maxY) / 2f - head.centerY) > gap * .35f) continue;
            if (DoubleSharpGlyph.matchesRaw(
                    gray, width, height, c.minX, c.minY, c.maxX, c.maxY, gap)) return true;
        }
        return false;
    }

    /** A beam may cross the flat's spine, but cannot supply its bowl or upright. */
    private static void clearCrossingBeamInk(
            byte[] ink,
            byte[] labels,
            int width,
            int left,
            int top,
            int cropWidth,
            int cropHeight,
            Component bowl,
            float gap,
            List<AccidentalCandidate> candidates) {
        int spineRight = bowl.minX + Math.max(2, Math.round(gap * .2f));
        for (AccidentalCandidate candidate : candidates) {
            if (candidate.label != OmrMeasurePostProcessor.SYMBOL) continue;
            Component beam = candidate.component;
            if (beam.maxX - beam.minX < gap * 2 || beam.minY >= bowl.minY) continue;
            for (int y = Math.max(top, beam.minY);
                    y < Math.min(bowl.minY, Math.min(top + cropHeight, beam.maxY + 1));
                    y++)
                for (int x = Math.max(left, spineRight + 1); x < left + cropWidth; x++)
                    if (x >= beam.minX
                            && x <= beam.maxX
                            && labels[y * width + x] == OmrMeasurePostProcessor.SYMBOL)
                        ink[(y - top) * cropWidth + x - left] = 0;
        }
    }

    /** A wide double-sharp box can also contain a neighboring stem and staff
     * rules. Its two white notches still flank a dark waist. */
    private static boolean rawWideDoubleSharp(
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        if (gray == null) return false;
        int minArm = Math.max(3, (int) Math.ceil(gap * .35f));
        int flank = Math.max(2, Math.round(gap * .5f));
        for (AccidentalCandidate candidate : candidates) {
            Component glyph = candidate.component;
            int gw = glyph.maxX - glyph.minX + 1, gh = glyph.maxY - glyph.minY + 1;
            if (candidate.label != OmrMeasurePostProcessor.SYMBOL
                    || gw <= gap * 1.4f
                    || gw > gap * 1.9f
                    || gh < gap * .9f
                    || gh > gap * 2f
                    || head.minX - glyph.maxX < 0
                    || head.minX - glyph.maxX > gap * 1.3f
                    || Math.abs(glyph.centerY - head.centerY) > gap * .7f) continue;
            List<int[]> notches = new ArrayList<>();
            int top = Math.max(1, Math.max(glyph.minY, Math.round(head.centerY - gap * .75f)));
            int bottom =
                    Math.min(
                            height - 2,
                            Math.min(glyph.maxY, Math.round(head.centerY + gap * .75f)));
            for (int y = top; y <= bottom; y++)
                for (int x = glyph.minX + minArm; x <= glyph.maxX - minArm; x++) {
                    if (x <= 0 || x >= width - 1 || (gray[y * width + x] & 255) < 180) continue;
                    int l = x, r = x;
                    while (l > glyph.minX && (gray[y * width + l - 1] & 255) >= 180) l--;
                    while (r < glyph.maxX && (gray[y * width + r + 1] & 255) >= 180) r++;
                    if (r - l + 1 > 3 || x != l) continue;
                    int left = 0, right = 0;
                    while (l - left - 1 >= glyph.minX
                            && left < gap * .8f
                            && (gray[y * width + l - left - 1] & 255) < 180) left++;
                    while (r + right + 1 <= glyph.maxX
                            && right < gap * .8f
                            && (gray[y * width + r + right + 1] & 255) < 180) right++;
                    if (left >= minArm && right >= minArm) notches.add(new int[] {y, (l + r) / 2});
                }
            for (int i = 0; i < notches.size(); i++)
                for (int j = i + 1; j < notches.size(); j++) {
                    int[] upper = notches.get(i), lower = notches.get(j);
                    int dy = lower[0] - upper[0];
                    if (dy < gap * .4f
                            || dy > gap * .9f
                            || Math.abs(upper[1] - lower[1]) > gap * .12f) continue;
                    int center = (upper[1] + lower[1]) / 2, waist = 0;
                    if (center - flank < 0 || center + flank >= width) continue;
                    for (int y = upper[0] + 1; y < lower[0]; y++)
                        if ((gray[y * width + center] & 255) < 180
                                && (gray[y * width + center - flank] & 255) >= 180
                                && (gray[y * width + center + flank] & 255) >= 180) waist++;
                    if (waist >= 2) return true;
                }
        }
        return false;
    }

    private static int detectWrittenAccidental(
            byte[] labels,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap) {
        return detectWrittenAccidental(labels, width, height, candidates, head, gap, null);
    }

    private static int detectWrittenAccidental(
            byte[] labels,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap,
            List<Component> heads) {
        return detectWrittenAccidental(labels, width, height, candidates, head, gap, heads, null);
    }

    private static int detectWrittenAccidental(
            byte[] labels,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component head,
            float gap,
            List<Component> heads,
            byte[] gray) {
        AccidentalCandidate best = null;
        int bestAccidental = ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
        float bestDistance = Float.MAX_VALUE;
        for (AccidentalCandidate candidate : candidates) {
            Component glyph = candidate.component;
            float horizontal = head.minX - glyph.maxX;
            if (horizontal < -gap * .35f
                    || horizontal > gap * (heads == null ? 1.35f : 4.2f)
                    || head.centerX - glyph.centerX < gap * .65f) continue;
            if (Math.abs(glyph.centerY - head.centerY) > gap * 1.8f) continue;
            float doubleFlatCenter = doubleFlatPitchCenter(labels, width, height, candidate, gap);
            float sharpCenter = sharpPitchCenter(labels, width, height, candidate, gap);
            int offsetSpine = offsetSpineAccidental(labels, width, height, candidate, gap);
            int accidental =
                    Float.isFinite(doubleFlatCenter)
                            ? ScoreNoteEvent.ACCIDENTAL_DOUBLE_FLAT
                            : DoubleSharpGlyph.matches(
                                            labels,
                                            width,
                                            height,
                                            glyph.minX,
                                            glyph.minY,
                                            glyph.maxX,
                                            glyph.maxY,
                                            (byte) 0,
                                            gap)
                                    ? ScoreNoteEvent.ACCIDENTAL_DOUBLE_SHARP
                                    : offsetSpine != ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                                            ? offsetSpine
                                            : isNaturalGlyph(labels, width, height, candidate, gap)
                                                    ? ScoreNoteEvent.ACCIDENTAL_NATURAL
                                                    : Float.isFinite(sharpCenter)
                                                            ? ScoreNoteEvent.ACCIDENTAL_SHARP
                                                            : isFlatGlyph(
                                                                            labels, width, height,
                                                                            candidate, gap)
                                                                    ? ScoreNoteEvent.ACCIDENTAL_FLAT
                                                                    : ScoreNoteEvent
                                                                            .ACCIDENTAL_FROM_KEY;
            if (accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY) continue;
            if (accidental == ScoreNoteEvent.ACCIDENTAL_FLAT
                    && (containsCompleteNatural(labels, width, height, candidate, candidates, gap)
                            || heads != null
                                    && containsPrintedNatural(
                                            gray,
                                            width,
                                            height,
                                            candidate,
                                            candidates,
                                            heads,
                                            gap))) continue;
            if (accidental == ScoreNoteEvent.ACCIDENTAL_FLAT
                    && glyph.maxX > head.minX
                    && glyph.minX > head.minX - gap * .55f
                    && glyph.maxY <= head.maxY) continue;
            // A sharp belongs at the centre of its crossbars. Extra staff ink can
            // shift its pixel centroid toward another head in the same chord.
            float pitchCenter =
                    accidental == ScoreNoteEvent.ACCIDENTAL_DOUBLE_FLAT
                            ? doubleFlatCenter
                            : accidental == ScoreNoteEvent.ACCIDENTAL_SHARP
                                            && Float.isFinite(sharpCenter)
                                    ? sharpCenter
                                    : accidental == ScoreNoteEvent.ACCIDENTAL_FLAT
                                            ? flatPitchCenter(labels, width, candidate, gap)
                                            : glyph.centerY;
            // A sharp's measured crossbar centre must not reach the next staff
            // position. Keep subpixel/font tolerance below the half-gap step.
            float tolerance =
                    accidental == ScoreNoteEvent.ACCIDENTAL_NATURAL
                            ? .90f
                            : accidental == ScoreNoteEvent.ACCIDENTAL_SHARP ? .45f : .65f;
            if (Math.abs(pitchCenter - head.centerY) > gap * tolerance) continue;
            if (heads != null) {
                boolean otherOwner = false;
                for (Component other : heads) {
                    if (other == head) continue;
                    if (Math.abs(other.centerX - head.centerX) < gap * .7f
                            && Math.abs(other.centerY - pitchCenter) + gap * .12f
                                    < Math.abs(head.centerY - pitchCenter)) otherOwner = true;
                    if (horizontal > gap * 1.35f
                            && !sharedAccidentalChordShaft(labels, width, height, head, other, gap)
                            && other.centerX > glyph.maxX + gap * .2f
                            && other.centerX < head.minX - gap * .3f
                            && Math.abs(other.centerY - head.centerY) < gap * 2) otherOwner = true;
                }
                if (otherOwner) continue;
                if (horizontal > gap * 1.35f
                        && (Math.abs(pitchCenter - head.centerY) > gap * .45f
                                || accidental != ScoreNoteEvent.ACCIDENTAL_NATURAL
                                        && accidental != ScoreNoteEvent.ACCIDENTAL_SHARP
                                        && accidental != ScoreNoteEvent.ACCIDENTAL_FLAT)) continue;
                if (horizontal > gap * 1.35f) {
                    int chordHeads = 0;
                    boolean displacedPartner = false, headInsideGlyph = false;
                    for (Component other : heads) {
                        if ((Math.abs(other.centerX - head.centerX) < gap * .7f
                                        || sharedAccidentalChordShaft(
                                                labels, width, height, head, other, gap))
                                && Math.abs(other.centerY - head.centerY) < gap * 4) chordHeads++;
                        if (other.centerX >= glyph.minX - gap * .2f
                                && other.centerX <= glyph.maxX + gap * .2f
                                && other.centerY >= glyph.minY
                                && other.centerY <= glyph.maxY) headInsideGlyph = true;
                    }
                    for (AccidentalCandidate partner : candidates) {
                        Component p = partner.component;
                        if (Math.abs(p.centerX - glyph.centerX) <= gap * .4f
                                || p.centerX > head.minX - gap * .2f
                                || Math.abs(p.centerX - glyph.centerX) > gap * 2.5f
                                || Math.abs(p.centerY - pitchCenter) > gap * 4) continue;
                        if (isNaturalGlyph(labels, width, height, partner, gap)
                                || isSharpGlyph(labels, width, height, partner, gap)
                                || isFlatGlyph(labels, width, height, partner, gap))
                            displacedPartner = true;
                    }
                    if (chordHeads < 2 || headInsideGlyph || !displacedPartner) continue;
                }
            }
            if (horizontal < bestDistance) {
                best = candidate;
                bestAccidental = accidental;
                bestDistance = horizontal;
            }
        }
        return best == null ? ScoreNoteEvent.ACCIDENTAL_FROM_KEY : bestAccidental;
    }

    /** Recover the glyph against the intact merged head, then assign it to one tone.
     * A displaced right-hand head is too far from the glyph for ordinary crop recovery. */
    private static int displacedSecondAccidental(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<AccidentalCandidate> candidates,
            Component merged,
            Component part,
            List<Component> seconds,
            float gap,
            List<Component> heads) {
        int result = ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
        float best = Float.POSITIVE_INFINITY;
        for (AccidentalCandidate candidate : candidates) {
            List<AccidentalCandidate> single = List.of(candidate);
            int accidental =
                    detectWrittenAccidental(
                            labels, width, height, single, merged, gap, heads, gray);
            if (accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                    && rawSharpFromSeed(gray, width, height, single, merged, gap))
                accidental = ScoreNoteEvent.ACCIDENTAL_SHARP;
            if (accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                    && rawFlatFromBowl(gray, width, height, single, merged, gap))
                accidental = ScoreNoteEvent.ACCIDENTAL_FLAT;
            if ((accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                            || accidental == ScoreNoteEvent.ACCIDENTAL_FLAT)
                    && rawNaturalFromCrossbars(gray, width, height, single, merged, gap))
                accidental = ScoreNoteEvent.ACCIDENTAL_NATURAL;
            if (accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY) continue;
            float pitch =
                    accidental == ScoreNoteEvent.ACCIDENTAL_FLAT
                            ? flatPitchCenter(labels, width, candidate, gap)
                            : accidental == ScoreNoteEvent.ACCIDENTAL_SHARP
                                    ? sharpPitchCenter(labels, width, height, candidate, gap)
                                    : candidate.component.centerY;
            float raw =
                    displacedAccidentalPitch(
                            gray, width, height, candidate, merged, gap, accidental);
            if (Float.isFinite(raw)) pitch = raw;
            if (!Float.isFinite(pitch)) continue;
            float distance = Math.abs(part.centerY - pitch);
            Component owner = seconds.get(0);
            for (Component other : seconds)
                if (Math.abs(other.centerY - pitch) < Math.abs(owner.centerY - pitch))
                    owner = other;
            if (owner == part && distance <= gap * .6f && distance < best) {
                best = distance;
                result = accidental;
            }
        }
        return result;
    }

    private static float displacedAccidentalPitch(
            byte[] gray,
            int width,
            int height,
            AccidentalCandidate candidate,
            Component merged,
            float gap,
            int accidental) {
        if (gray == null) return Float.NaN;
        Component seed = candidate.component;
        int margin = Math.max(1, Math.round(gap * .16f));
        int left = Math.max(0, seed.minX - margin),
                right = Math.min(width - 1, Math.min(seed.maxX + margin, merged.minX - 2));
        int top = Math.max(0, Math.round(merged.centerY - gap * 3)),
                bottom = Math.min(height - 1, Math.round(merged.centerY + gap * 3));
        int w = right - left + 1, h = bottom - top + 1;
        if (w <= 0 || h <= 0) return Float.NaN;
        byte[] ink = flatInkAtThreshold(gray, width, height, left, right, top, bottom, gap, 180);
        Component glyph = retainSeedConnectedInk(ink, w, h, seed, left, top);
        if (glyph == null || rawStrokeLeavesCrop(gray, width, height, ink, w, h, left, top, gap))
            return Float.NaN;
        AccidentalCandidate isolated =
                new AccidentalCandidate(glyph, OmrMeasurePostProcessor.SYMBOL);
        if (accidental == ScoreNoteEvent.ACCIDENTAL_NATURAL
                && isNaturalGlyph(ink, w, h, isolated, gap))
            return top + (glyph.minY + glyph.maxY) * .5f;
        if (accidental == ScoreNoteEvent.ACCIDENTAL_FLAT && isFlatGlyph(ink, w, h, isolated, gap))
            return top + flatPitchCenter(ink, w, isolated, gap);
        if (accidental == ScoreNoteEvent.ACCIDENTAL_SHARP)
            return top + sharpPitchCenter(ink, w, h, isolated, gap);
        return Float.NaN;
    }

    /** A repaired union cannot turn an intact natural plus stray rule ink into a flat. */
    private static boolean containsPrintedNatural(
            byte[] gray,
            int width,
            int height,
            AccidentalCandidate candidate,
            List<AccidentalCandidate> originals,
            List<Component> heads,
            float gap) {
        if (gray == null || candidate.label != 0) return false;
        Component union = candidate.component;
        // A clipped semantic sharp may resemble a flat while its complete printed
        // crossbars still establish a sharp for another chord tone.
        for (Component head : heads)
            if (rawSharpFromSeed(gray, width, height, List.of(candidate), head, gap)) return false;
        for (AccidentalCandidate original : originals) {
            Component core = original.component;
            if (original.label != OmrMeasurePostProcessor.CLEF_OR_KEY
                    || core.area < union.area * .65f
                    || core.minX < union.minX
                    || core.maxX > union.maxX
                    || core.minY < union.minY
                    || core.maxY > union.maxY) continue;
            for (Component head : heads) {
                if (head.minX - core.maxX < gap * .1f
                        || head.minX - core.maxX > gap * 1.35f
                        || Math.abs(head.centerY - core.centerY) > gap * .9f) continue;
                if (rawNaturalAtSeed(gray, width, height, core, head, gap)) return true;
            }
        }
        return false;
    }

    private static boolean containsCompleteNatural(
            byte[] labels,
            int width,
            int height,
            AccidentalCandidate candidate,
            List<AccidentalCandidate> originals,
            float gap) {
        if (candidate.label != 0) return false;
        Component union = candidate.component;
        for (AccidentalCandidate original : originals) {
            Component core = original.component;
            if (original.label != OmrMeasurePostProcessor.CLEF_OR_KEY
                    || core.area < union.area * .65f
                    || core.minX < union.minX
                    || core.maxX > union.maxX
                    || core.minY < union.minY
                    || core.maxY > union.maxY) continue;
            if (isNaturalGlyph(labels, width, height, original, gap)) return true;
        }
        return false;
    }

    private static boolean sharedAccidentalChordShaft(
            byte[] labels, int width, int height, Component a, Component b, float gap) {
        if (a == b
                || Math.abs(a.centerX - b.centerX) > gap * 1.6f
                || Math.abs(a.centerY - b.centerY) > gap * 3.5f) return false;
        int top = Math.round(Math.min(a.centerY, b.centerY)),
                bottom = Math.round(Math.max(a.centerY, b.centerY));
        for (int x = Math.max(0, Math.max(a.minX, b.minX) - Math.round(gap * .2f));
                x <= Math.min(width - 1, Math.min(a.maxX, b.maxX) + Math.round(gap * .2f));
                x++) {
            if (Math.min(Math.abs(x - a.minX), Math.abs(x - a.maxX)) > gap * .2f
                    || Math.min(Math.abs(x - b.minX), Math.abs(x - b.maxX)) > gap * .2f) continue;
            int ink = 0;
            for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST
                        || labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) ink++;
            if (ink < (bottom - top + 1) * .75f) continue;
            for (int direction : new int[] {-1, 1}) {
                int start = direction < 0 ? Math.min(a.minY, b.minY) : Math.max(a.maxY, b.maxY),
                        stem = 0,
                        total = 0;
                for (int d = 1; d <= Math.round(gap * 1.8f); d++) {
                    int y = start + direction * d;
                    if (y < 0 || y >= height) break;
                    total++;
                    if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST) stem++;
                }
                if (total >= gap * 1.6f && stem >= total * .7f) return true;
            }
        }
        return false;
    }

    /** Two adjacent complete flats must have aligned bowls and independent tall
     * spines. Splitting a sharp/natural into strips cannot satisfy both bowls. */
    private static float doubleFlatPitchCenter(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        Component g = candidate.component;
        if (g.maxX - g.minX < gap * .85f || g.maxX - g.minX > gap * 2.1f) return Float.NaN;
        for (int cut = g.minX + Math.round(gap * .35f);
                cut <= g.maxX - Math.round(gap * .35f);
                cut++) {
            AccidentalCandidate[] parts = new AccidentalCandidate[2];
            for (int side = 0; side < 2; side++) {
                int l = side == 0 ? g.minX : cut + 1, r = side == 0 ? cut : g.maxX;
                int minX = r + 1, maxX = l - 1, minY = g.maxY + 1, maxY = g.minY - 1, area = 0;
                long sx = 0, sy = 0;
                for (int y = Math.max(0, g.minY); y <= Math.min(height - 1, g.maxY); y++)
                    for (int x = Math.max(0, l); x <= Math.min(width - 1, r); x++)
                        if (candidate.matches(labels[y * width + x])) {
                            minX = Math.min(minX, x);
                            maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y);
                            area++;
                            sx += x;
                            sy += y;
                        }
                if (area > 0)
                    parts[side] =
                            new AccidentalCandidate(
                                    new Component(
                                            area,
                                            minX,
                                            maxX,
                                            minY,
                                            maxY,
                                            sx / (float) area,
                                            sy / (float) area),
                                    candidate.label);
            }
            if (parts[0] == null
                    || parts[1] == null
                    || !isFlatGlyph(labels, width, height, parts[0], gap)
                    || !isFlatGlyph(labels, width, height, parts[1], gap)) continue;
            Component a = parts[0].component, b = parts[1].component;
            boolean clearUpperBowls = true;
            for (AccidentalCandidate part : parts) {
                Component c = part.component;
                int upperRight = 0;
                for (int y = c.minY; y < c.minY + (c.maxY - c.minY + 1) * .45f; y++)
                    for (int x = c.minX + Math.max(2, Math.round(gap * .25f)); x <= c.maxX; x++)
                        if (part.matches(labels[y * width + x])) upperRight++;
                if (upperRight > Math.max(2, c.area * .04f)) clearUpperBowls = false;
            }
            if (!clearUpperBowls) continue;
            float ac = flatPitchCenter(labels, width, parts[0], gap),
                    bc = flatPitchCenter(labels, width, parts[1], gap);
            if (Math.abs(a.minY - b.minY) <= gap * .25f
                    && Math.abs(a.maxY - b.maxY) <= gap * .25f
                    && Math.abs(ac - bc) <= gap * .2f) return (ac + bc) * .5f;
        }
        return Float.NaN;
    }

    /**
     * A flat has one tall left spine and a lower-right bowl. Naturals have a second upper-right
     * stem, while sharps are much wider; comparing the glyph's own semantic pixels avoids staff
     * lines and artwork texture in the grayscale page.
     */
    private static boolean isFlatGlyph(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        AccidentalInk isolated = isolatedAccidentalInk(labels, width, height, candidate, gap);
        return isolated == null
                ? isFlatGlyphCore(labels, width, height, candidate, gap)
                : isFlatGlyphCore(
                        isolated.labels, isolated.width, isolated.height, isolated.candidate, gap);
    }

    private static boolean isFlatGlyphCore(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        Component glyph = candidate.component;
        int glyphWidth = glyph.maxX - glyph.minX + 1;
        int glyphHeight = glyph.maxY - glyph.minY + 1;
        if (glyphHeight < gap * 1.20f
                || glyphHeight > gap * 3.35f
                || glyphWidth < gap * .24f
                || glyphWidth > gap * 1.75f
                || glyphHeight < glyphWidth * 1.28f
                || glyph.area < gap * gap * .16f
                || glyph.area > gap * gap * 1.65f) return false;
        int[] columns = new int[glyphWidth];
        int spine = 0;
        for (int y = Math.max(0, glyph.minY); y <= Math.min(height - 1, glyph.maxY); y++)
            for (int x = Math.max(0, glyph.minX); x <= Math.min(width - 1, glyph.maxX); x++)
                if (candidate.matches(labels[y * width + x])) columns[x - glyph.minX]++;
        for (int column = 1; column < columns.length; column++)
            if (columns[column] > columns[spine]) spine = column;
        // A slur tail or grace flag bends rightward without a sustained left spine.
        if (spine > Math.round((glyphWidth - 1) * .48f) || columns[spine] < glyphHeight * .68f)
            return false;
        int splitY = glyph.minY + Math.round(glyphHeight * .45f);
        int upperEnd = glyph.minY + Math.max(1, Math.round(glyphHeight * .25f));
        int[] upperReach = new int[upperEnd - glyph.minY];
        for (int y = glyph.minY; y < upperEnd; y++)
            for (int x = glyph.minX + spine; x <= glyph.maxX; x++)
                if (candidate.matches(labels[y * width + x])) upperReach[y - glyph.minY] = x;
        java.util.Arrays.sort(upperReach);
        int upperEdge = upperReach[upperReach.length / 2], expandedRows = 0;
        for (int y = splitY; y <= glyph.maxY; y++)
            for (int x = Math.max(glyph.minX, upperEdge + Math.max(2, Math.round(gap * .2f)));
                    x <= glyph.maxX;
                    x++)
                if (candidate.matches(labels[y * width + x])) {
                    expandedRows++;
                    break;
                }
        // A cut note stem can widen by a pixel at a staff crossing. A flat has
        // a distinct bowl projecting beyond the width of its upper stem.
        if (expandedRows < Math.max(2, Math.round(glyphHeight * .1f))) return false;
        int rightStart = glyph.minX + spine + Math.max(1, Math.round(glyphWidth * .16f));
        int upperRight = 0, lowerRight = 0, wideLowerRows = 0, bowlRows = 0;
        for (int y = glyph.minY; y <= glyph.maxY; y++) {
            int rowMin = glyph.maxX + 1, rowMax = glyph.minX - 1;
            for (int x = Math.max(glyph.minX, rightStart); x <= glyph.maxX; x++) {
                if (x < 0
                        || x >= width
                        || y < 0
                        || y >= height
                        || !candidate.matches(labels[y * width + x])) continue;
                if (y < splitY) upperRight++;
                else lowerRight++;
                rowMin = Math.min(rowMin, x);
                rowMax = Math.max(rowMax, x);
            }
            if (y >= splitY && rowMax >= rowMin && rowMax - rowMin + 1 >= glyphWidth * .42f)
                wideLowerRows++;
            // Narrow engraved flats have a hollow bowl: its curved outer edge can be just
            // one pixel wide on a row. Measure its reach from the spine as well as its ink.
            if (y >= splitY
                    && rowMax - (glyph.minX + spine) >= Math.max(gap * .30f, glyphWidth * .55f))
                bowlRows++;
        }
        return lowerRight >= upperRight + Math.max(2, Math.round(gap * .16f))
                && lowerRight >= glyph.area * .13f
                && (wideLowerRows >= Math.max(2, Math.round(glyphHeight * .10f))
                        || bowlRows >= Math.max(3, Math.round(glyphHeight * .16f)));
    }

    /** A flat changes the note beside its bowl, not the note beside its tall spine. */
    private static float flatPitchCenter(
            byte[] labels, int width, AccidentalCandidate candidate, float gap) {
        AccidentalInk isolated =
                isolatedAccidentalInk(labels, width, labels.length / width, candidate, gap);
        return isolated == null
                ? flatPitchCenterCore(labels, width, candidate, gap)
                : candidate.component.minY
                        + flatPitchCenterCore(
                                isolated.labels, isolated.width, isolated.candidate, gap);
    }

    private static float flatPitchCenterCore(
            byte[] labels, int width, AccidentalCandidate candidate, float gap) {
        Component glyph = candidate.component;
        int glyphWidth = glyph.maxX - glyph.minX + 1, glyphHeight = glyph.maxY - glyph.minY + 1;
        int[] columns = new int[glyphWidth];
        for (int y = glyph.minY; y <= glyph.maxY; y++)
            for (int x = glyph.minX; x <= glyph.maxX; x++)
                if (candidate.matches(labels[y * width + x])) columns[x - glyph.minX]++;
        int spine = 0;
        for (int x = 1; x < glyphWidth; x++) if (columns[x] > columns[spine]) spine = x;
        int start = glyph.minX + spine + (int) Math.ceil(Math.max(gap * .30f, glyphWidth * .55f));
        int first = -1, last = -1;
        for (int y = glyph.minY + Math.round(glyphHeight * .45f); y <= glyph.maxY; y++)
            for (int x = start; x <= glyph.maxX; x++)
                if (candidate.matches(labels[y * width + x])) {
                    if (first < 0) first = y;
                    last = y;
                    break;
                }
        return first < 0 ? glyph.minY + glyphHeight * .75f : (first + last) * .5f;
    }

    /**
     * A natural has two offset vertical spines: the left one extends above the right, while the
     * right one extends below the left. Two separated connectors join them. Checking those
     * asymmetric endpoints before flat/sharp classification keeps a natural from looking like a
     * flat's lower bowl or a sharp with unusually thin crossbars.
     */
    private static boolean isNaturalGlyph(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        AccidentalInk isolated = isolatedAccidentalInk(labels, width, height, candidate, gap);
        return isolated == null
                ? isNaturalGlyphCore(labels, width, height, candidate, gap)
                : isNaturalGlyphCore(
                        isolated.labels, isolated.width, isolated.height, isolated.candidate, gap);
    }

    private record AccidentalInk(
            byte[] labels, int width, int height, AccidentalCandidate candidate) {}

    private static AccidentalInk isolatedAccidentalInk(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        Component glyph = candidate.component;
        int w = glyph.maxX - glyph.minX + 1, h = glyph.maxY - glyph.minY + 1;
        if (candidate.label != 0
                && w > 0
                && h > 0
                && w <= gap * 1.80f
                && h <= gap * 3.70f
                && glyph.minX >= 0
                && glyph.minY >= 0
                && glyph.maxX < width
                && glyph.maxY < height) {
            byte[] local = new byte[w * h];
            int count = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    if (candidate.matches(labels[(glyph.minY + y) * width + glyph.minX + x])) {
                        local[y * w + x] = candidate.label;
                        count++;
                    }
            // Staggered naturals can have overlapping boxes while remaining separate
            // connected components. Do not let the neighbor extend either spine.
            if (count > glyph.area)
                for (Component original : findComponents(local, w, h, candidate.label)) {
                    if (original.area != glyph.area
                            || original.minX != 0
                            || original.maxX != w - 1
                            || original.minY != 0
                            || original.maxY != h - 1) continue;
                    byte[] isolated = retainExactComponent(local, w, h, original, candidate.label);
                    return new AccidentalInk(
                            isolated, w, h, new AccidentalCandidate(original, candidate.label));
                }
        }
        return null;
    }

    private static byte[] retainExactComponent(
            byte[] labels, int width, int height, Component target, byte label) {
        byte[] result = new byte[labels.length];
        boolean[] seen = new boolean[labels.length];
        int[] queue = new int[labels.length];
        for (int seed = 0; seed < labels.length; seed++) {
            if (seen[seed] || labels[seed] != label) continue;
            int start = 0, end = 1;
            queue[0] = seed;
            seen[seed] = true;
            int minX = width, maxX = -1, minY = height, maxY = -1;
            while (start < end) {
                int at = queue[start++], x = at % width, y = at / width;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!seen[next] && labels[next] == label) {
                            seen[next] = true;
                            queue[end++] = next;
                        }
                    }
            }
            if (end == target.area
                    && minX == target.minX
                    && maxX == target.maxX
                    && minY == target.minY
                    && maxY == target.maxY) {
                for (int i = 0; i < end; i++) result[queue[i]] = label;
                return result;
            }
        }
        return labels;
    }

    /** Reuses natural-sign topology to distinguish note accidentals from stacked meter digits. */
    static boolean provesNaturalMeterInk(
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float noteY,
            float gap,
            int firstLine) {
        if (gray == null
                || gray.length != width * height
                || gap < 6
                || !Float.isFinite(gap)
                || right < left
                || right - left > gap * 1.8f) return false;
        left = Math.max(0, left);
        right = Math.min(width - 1, right);
        int top = Math.max(0, Math.round(noteY - gap * 1.9f));
        int bottom = Math.min(height - 1, Math.round(noteY + gap * 1.9f));
        int w = right - left + 1, h = bottom - top + 1;
        if (w <= 0 || h <= 0) return false;
        // Staff suppression leaves spine endpoints and the two separated connectors.
        // Retain antialiased glyph edges as a second independent raw-ink reading.
        for (int threshold : new int[] {155, 200}) {
            byte[] ink = new byte[w * h];
            int area = 0, minX = w, maxX = -1, minY = h, maxY = -1;
            long sx = 0, sy = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    boolean rule = false;
                    for (int row = 0; row < 5; row++)
                        if (Math.abs(top + y - (firstLine + row * gap))
                                <= Math.max(1, Math.round(gap * .12f))) rule = true;
                    if (rule || (gray[(top + y) * width + left + x] & 255) >= threshold) continue;
                    ink[y * w + x] = OmrMeasurePostProcessor.SYMBOL;
                    area++;
                    sx += x;
                    sy += y;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            if (area == 0) continue;
            var glyph =
                    new Component(
                            area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
            if (isNaturalGlyphCore(
                    ink, w, h, new AccidentalCandidate(glyph, OmrMeasurePostProcessor.SYMBOL), gap))
                return true;
        }
        return false;
    }

    private static boolean isNaturalGlyphCore(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        Component glyph = candidate.component;
        int glyphWidth = glyph.maxX - glyph.minX + 1;
        int glyphHeight = glyph.maxY - glyph.minY + 1;
        if (glyphHeight < Math.floor(gap * 1.40f)
                || glyphHeight > gap * 3.70f
                || glyphWidth < gap * .48f
                || glyphWidth > gap * 1.55f
                || glyphHeight < glyphWidth * 1.45f
                || glyphHeight > glyphWidth * 4.50f
                || glyph.area < gap * gap * .30f
                || glyph.area > gap * gap * 2.30f) return false;

        int[] columns = new int[glyphWidth];
        for (int y = Math.max(0, glyph.minY); y <= Math.min(height - 1, glyph.maxY); y++)
            for (int x = Math.max(0, glyph.minX); x <= Math.min(width - 1, glyph.maxX); x++)
                if (candidate.matches(labels[y * width + x])) {
                    int localX = x - glyph.minX;
                    columns[localX]++;
                }

        int leftSpine = 0;
        int rightSpine = Math.max(0, glyphWidth / 2);
        for (int column = 0; column < Math.max(1, glyphWidth / 2); column++)
            if (columns[column] > columns[leftSpine]) leftSpine = column;
        for (int column = Math.max(0, glyphWidth / 2); column < glyphWidth; column++)
            if (columns[column] > columns[rightSpine]) rightSpine = column;
        if (rightSpine - leftSpine < glyphWidth * .28f
                || columns[leftSpine] < glyphHeight * .38f
                || columns[rightSpine] < glyphHeight * .38f) return false;

        int radius = Math.max(0, Math.round(glyphWidth * .09f));
        // Natural connectors stay between the spines. A glyph whose ink
        // protrudes beyond both vertical sides is not proved natural merely
        // because one sharp spine has a short extension.
        boolean overhangs = leftSpine > radius && glyphWidth - 1 - rightSpine > radius;
        int leftTop = glyphHeight, leftBottom = -1, rightTop = glyphHeight, rightBottom = -1;
        int leftRows = 0, rightRows = 0;
        for (int row = 0; row < glyphHeight; row++) {
            boolean leftInk = false, rightInk = false;
            for (int column = Math.max(0, leftSpine - radius);
                    column <= Math.min(glyphWidth - 1, leftSpine + radius);
                    column++)
                if (candidate.matches(labels[(glyph.minY + row) * width + glyph.minX + column])) {
                    leftInk = true;
                    leftTop = Math.min(leftTop, row);
                    leftBottom = Math.max(leftBottom, row);
                }
            for (int column = Math.max(0, rightSpine - radius);
                    column <= Math.min(glyphWidth - 1, rightSpine + radius);
                    column++)
                if (candidate.matches(labels[(glyph.minY + row) * width + glyph.minX + column])) {
                    rightInk = true;
                    rightTop = Math.min(rightTop, row);
                    rightBottom = Math.max(rightBottom, row);
                }
            if (leftInk) leftRows++;
            if (rightInk) rightRows++;
        }
        // Separate fragments cannot lengthen a spine across mostly empty space.
        // Allow short scan breaks while requiring ink along each offset stem.
        if (leftRows < (leftBottom - leftTop + 1) * .65f
                || rightRows < (rightBottom - rightTop + 1) * .65f) return false;
        if (overhangs
                && (leftSpine > radius + 1
                        || glyphWidth - 1 - rightSpine > radius + 1
                        || Math.min(rightTop - leftTop, rightBottom - leftBottom) < gap * .45f))
            return false;
        int endpointOffset = Math.max(1, Math.round(glyphHeight * .05f));
        if (rightTop - leftTop < endpointOffset || rightBottom - leftBottom < endpointOffset)
            return false;

        // A flag can have two offset sides, but only one diagonal connector.
        // Require two actual ink bridges separated by an open counter.
        int innerLeft = leftSpine + radius + 1, innerRight = rightSpine - radius - 1;
        if (innerLeft > innerRight) return false;
        int span = innerRight - innerLeft + 1;
        for (float slope : new float[] {0, -.3f, -.6f, .3f, .6f}) {
            int firstConnector = -1, openRows = 0;
            for (int row = 0; row < glyphHeight; row++) {
                int bridge = 0;
                for (int col = innerLeft; col <= innerRight; col++) {
                    int y = row + Math.round((col - leftSpine) * slope);
                    if (y >= 0
                            && y < glyphHeight
                            && candidate.matches(
                                    labels[(glyph.minY + y) * width + glyph.minX + col])) bridge++;
                }
                boolean connected = bridge >= Math.max(1, (int) Math.ceil(span * .75f));
                if (connected) {
                    if (firstConnector < 0) firstConnector = row;
                    if (openRows >= Math.max(2, Math.round(gap * .15f))
                            && row - firstConnector >= gap * .45f) {
                        // Small endpoint offsets also occur on printed sharps. A natural
                        // must have a spine ending near a connector; two spines extending
                        // well beyond both bridges still describe a sharp.
                        int junctionTolerance = Math.max(2, Math.round(gap * .3f));
                        int upperAtRight =
                                firstConnector + Math.round((rightSpine - leftSpine) * slope);
                        if (upperAtRight - rightTop <= junctionTolerance
                                || leftBottom - row <= junctionTolerance) return true;
                    }
                    openRows = 0;
                } else if (firstConnector >= 0) openRows++;
            }
        }
        return false;
    }

    /** Offset spine endpoints survive partially erased natural and sharp crossbars. */
    private static int offsetSpineAccidental(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        Component glyph = candidate.component;
        int w = glyph.maxX - glyph.minX + 1, h = glyph.maxY - glyph.minY + 1;
        if (w < gap * .6f
                || w > gap * 1.25f
                || h < gap * 2.2f
                || h > gap * 3.2f
                || glyph.area < gap * gap * .65f
                || glyph.area > gap * gap * 1.5f) return ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
        int[] counts = new int[w], first = new int[w], last = new int[w];
        java.util.Arrays.fill(first, height);
        for (int y = glyph.minY; y <= glyph.maxY; y++)
            for (int x = glyph.minX; x <= glyph.maxX; x++)
                if (candidate.matches(labels[y * width + x])) {
                    int i = x - glyph.minX;
                    counts[i]++;
                    first[i] = Math.min(first[i], y);
                    last[i] = y;
                }
        int left = 0, right = w / 2;
        for (int i = 1; i < w / 2; i++) if (counts[i] > counts[left]) left = i;
        for (int i = w / 2 + 1; i < w; i++) if (counts[i] > counts[right]) right = i;
        if (right - left < gap * .25f || counts[left] < h * .6f || counts[right] < h * .6f)
            return ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
        int topShift = first[right] - first[left], bottomShift = last[right] - last[left];
        if (topShift >= gap * .3f && bottomShift >= gap * .3f)
            return ScoreNoteEvent.ACCIDENTAL_NATURAL;
        if (topShift <= -gap * .18f && bottomShift <= -gap * .18f)
            return ScoreNoteEvent.ACCIDENTAL_SHARP;
        return ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
    }

    /** A sharp has two full-height vertical spines crossed by two separated wide strokes. */
    private static boolean isSharpGlyph(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        return Float.isFinite(sharpPitchCenter(labels, width, height, candidate, gap));
    }

    private static float sharpPitchCenter(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        float upright = uprightSharpPitchCenter(labels, width, height, candidate, gap);
        if (Float.isFinite(upright)) return upright;
        Component glyph = candidate.component;
        int gw = glyph.maxX - glyph.minX + 1, gh = glyph.maxY - glyph.minY + 1;
        if (!Float.isFinite(gap)
                || gap < 3
                || gh < gap * 1.55f
                || gh > gap * 3.65f
                || gw < gap * .65f
                || gw > gap * 2.8f
                || glyph.area < gap * gap * .42f
                || glyph.area > gap * gap * 2.45f
                || glyph.minX <= 0
                || glyph.maxX >= width - 1
                || glyph.minY <= 0
                || glyph.maxY >= height - 1) return Float.NaN;
        int pad = Math.round(gh * .3f) + 2, w = gw + 2 * pad;
        float pitchSum = 0, firstPitch = Float.NaN;
        int accepted = 0;
        // Shift each row without dropping ink. A shared lean must preserve both
        // shaft extensions, two bridged crossbars and the upright shape limits.
        for (int step : new int[] {-6, -5, -4, -3, -2, 2, 3, 4, 5, 6}) {
            float lean = step * .05f;
            byte[] projected = new byte[w * gh];
            int area = 0, minX = w, maxX = -1, minY = gh, maxY = -1;
            long sx = 0, sy = 0;
            for (int y = 0; y < gh; y++) {
                int shift = Math.round((y - (gh - 1) * .5f) * lean);
                for (int x = 0; x < gw; x++) {
                    if (!candidate.matches(labels[(glyph.minY + y) * width + glyph.minX + x]))
                        continue;
                    int xx = x - shift + pad;
                    projected[y * w + xx] = OmrMeasurePostProcessor.SYMBOL;
                    area++;
                    sx += xx;
                    sy += y;
                    minX = Math.min(minX, xx);
                    maxX = Math.max(maxX, xx);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
            if (area != glyph.area) return Float.NaN;
            Component shifted =
                    new Component(
                            area, minX, maxX, minY, maxY, sx / (float) area, sy / (float) area);
            float pitch =
                    uprightSharpPitchCenter(
                            projected,
                            w,
                            gh,
                            new AccidentalCandidate(shifted, OmrMeasurePostProcessor.SYMBOL),
                            gap);
            if (!Float.isFinite(pitch)) continue;
            if (Float.isFinite(firstPitch) && Math.abs(pitch - firstPitch) > gap * .12f)
                return Float.NaN;
            if (!Float.isFinite(firstPitch)) firstPitch = pitch;
            pitchSum += pitch;
            accepted++;
        }
        return accepted >= 2 ? glyph.minY + pitchSum / accepted : Float.NaN;
    }

    private static float uprightSharpPitchCenter(
            byte[] labels, int width, int height, AccidentalCandidate candidate, float gap) {
        Component glyph = candidate.component;
        int glyphWidth = glyph.maxX - glyph.minX + 1;
        int glyphHeight = glyph.maxY - glyph.minY + 1;
        if (glyphHeight < gap * 1.55f
                || glyphHeight > gap * 3.65f
                || glyphWidth < gap * .65f
                || glyphWidth > gap * 1.80f
                || glyphHeight < glyphWidth * 1.45f
                || glyphHeight > glyphWidth * 4.50f
                || glyph.area < gap * gap * .42f
                || glyph.area > gap * gap * 2.45f) return Float.NaN;

        int[] columns = new int[glyphWidth];
        int[] rows = new int[glyphHeight];
        for (int y = Math.max(0, glyph.minY); y <= Math.min(height - 1, glyph.maxY); y++)
            for (int x = Math.max(0, glyph.minX); x <= Math.min(width - 1, glyph.maxX); x++)
                if (candidate.matches(labels[y * width + x])) {
                    columns[x - glyph.minX]++;
                    rows[y - glyph.minY]++;
                }

        int leftSpine = 0;
        int rightSpine = Math.max(0, glyphWidth / 2);
        for (int column = 0; column < Math.max(1, glyphWidth / 2); column++)
            if (columns[column] > columns[leftSpine]) leftSpine = column;
        for (int column = Math.max(0, glyphWidth / 2); column < glyphWidth; column++)
            if (columns[column] > columns[rightSpine]) rightSpine = column;
        int coreLeft = 0, coreRight = glyphWidth - 1;
        while (coreLeft < coreRight && columns[coreLeft] < glyphHeight * .30f) coreLeft++;
        while (coreRight > coreLeft && columns[coreRight] < glyphHeight * .30f) coreRight--;
        if (rightSpine - leftSpine < (coreRight - coreLeft + 1) * .28f
                || columns[leftSpine] < glyphHeight * .46f
                || columns[rightSpine] < glyphHeight * .46f) return Float.NaN;

        // Two thick but disconnected spines are not a crossbar.
        // Require ink to bridge their inner gap before considering row width.
        for (int row = 0; row < glyphHeight; row++) {
            int bridge = 0;
            for (int col = leftSpine; col <= rightSpine; col++)
                if (candidate.matches(labels[(glyph.minY + row) * width + glyph.minX + col]))
                    bridge++;
            if (bridge < (rightSpine - leftSpine + 1) * .70f) rows[row] = 0;
        }
        // Measure crossbars against the two spines, not stray ink at the glyph edge.
        int wideThreshold = Math.max(2, Math.round((rightSpine - leftSpine + 1) * 1.15f));
        List<int[]> crossbars = new ArrayList<>();
        int terminalMargin = Math.max(2, Math.round(glyphHeight * .10f));
        for (int row = terminalMargin; row < rows.length - terminalMargin; ) {
            if (rows[row] < wideThreshold) {
                row++;
                continue;
            }
            int first = row;
            while (row < rows.length - terminalMargin && rows[row] >= wideThreshold) row++;
            // A painted staff stripe at a spine tip is not a sharp crossbar.
            if (row - first < Math.max(2, Math.round(gap * .12f))) continue;
            crossbars.add(new int[] {first, row - 1});
        }
        float bestCenter = Float.NaN;
        int bestSupport = -1;
        for (int i = 0; i < crossbars.size(); i++)
            pair:
            for (int j = i + 1; j < crossbars.size(); j++) {
                int[] firstBand = crossbars.get(i), lastBand = crossbars.get(j);
                int firstCrossbar = firstBand[0], lastCrossbar = lastBand[1];
                if (lastCrossbar - firstCrossbar < glyphHeight * .18f) continue;
                // Both sharp spines protrude through both crossbars. A flat's bowl can
                // supply a long second column, but cannot supply its upper extension.
                int radius = Math.max(1, Math.round(glyphWidth * .09f));
                int required = Math.max(1, Math.round(glyphHeight * .05f));
                int firstTop = -1, firstBottom = -1;
                for (int spine : new int[] {leftSpine, rightSpine}) {
                    int above = 0, below = 0, top = -1, bottom = -1;
                    for (int row = 0; row < glyphHeight; row++) {
                        boolean ink = false;
                        for (int col = Math.max(0, spine - radius);
                                col <= Math.min(glyphWidth - 1, spine + radius);
                                col++)
                            if (candidate.matches(
                                    labels[(glyph.minY + row) * width + glyph.minX + col]))
                                ink = true;
                        if (ink) {
                            if (top < 0) top = row;
                            bottom = row;
                        }
                        if (ink && row < firstCrossbar) above++;
                        if (ink && row > lastCrossbar) below++;
                    }
                    if (above < required || below < required) continue pair;
                    if (firstTop < 0) {
                        firstTop = top;
                        firstBottom = bottom;
                    } else if (Math.abs(top - firstTop) > glyphHeight * .22f
                            || Math.abs(bottom - firstBottom) > glyphHeight * .22f) continue pair;
                }
                // A hairpin or staff stripe can add a third short band near a tip.
                // Prefer the two substantial crossbars with support on both spines.
                int firstSize = firstBand[1] - firstBand[0] + 1,
                        lastSize = lastBand[1] - lastBand[0] + 1;
                int support = Math.min(firstSize, lastSize) * 100 + firstSize + lastSize;
                if (support > bestSupport) {
                    bestSupport = support;
                    bestCenter = glyph.minY + (firstCrossbar + lastCrossbar) * .5f;
                }
            }
        return bestCenter;
    }

    /** Uses the staff line beside the note instead of the page-wide average on skewed scans. */
    /** Two neighboring printed lines can correct subpixel error accumulated above/below a staff. */
    private static float printedLedgerBottom(
            byte[] gray, int width, int height, Component head, float bottom, float gap) {
        if (gray == null || gap < 8) return bottom;
        float step = (bottom - head.centerY) / (gap * .5f);
        if (step < 9.5f && step > -1.5f) return bottom;
        int ledger = Math.round(step * .5f) * 2;
        if (ledger > 0 && ledger < 10 || ledger < 0 && ledger > -2) return bottom;
        int adjacent = ledger + (ledger > 0 ? -2 : 2);
        float first =
                printedLedgerLine(
                        gray, width, height, head, bottom - ledger * gap * .5f, gap, true);
        float second =
                printedLedgerLine(
                        gray,
                        width,
                        height,
                        head,
                        bottom - adjacent * gap * .5f,
                        gap,
                        adjacent > 8 || adjacent < 0);
        if (!Float.isFinite(first)
                || !Float.isFinite(second)
                || Math.abs(Math.abs(first - second) - gap) > gap * .12f) return bottom;
        float a = first + ledger * gap * .5f, b = second + adjacent * gap * .5f;
        if (Math.abs(a - bottom) > gap * .2f || Math.abs(b - bottom) > gap * .2f) return bottom;
        return (a + b) * .5f;
    }

    private static float printedLedgerLine(
            byte[] gray,
            int width,
            int height,
            Component head,
            float expected,
            float gap,
            boolean bounded) {
        int reach = Math.max(3, Math.round(gap * .55f)),
                range = Math.max(2, Math.round(gap * .25f));
        int flank = Math.max(2, Math.round(gap * .3f));
        var candidates = new ArrayList<List<Float>>();
        for (int side = 0; side < 2; side++) {
            int left = side == 0 ? head.minX - reach : head.maxX + 1,
                    right = side == 0 ? head.minX - 1 : head.maxX + reach;
            if (left < 0 || right >= width) return Float.NaN;
            int first = Math.max(flank, Math.round(expected) - range),
                    last = Math.min(height - 1 - flank, Math.round(expected) + range);
            int peak = 0;
            int[] strengths = new int[Math.max(0, last - first + 1)];
            for (int y = first; y <= last; y++) {
                int support = 0;
                for (int x = left; x <= right; x++) {
                    int ink = gray[y * width + x] & 255;
                    if (ink < 180
                            && (gray[(y - flank) * width + x] & 255) > ink + 20
                            && (gray[(y + flank) * width + x] & 255) > ink + 20) support++;
                }
                strengths[y - first] = support;
                peak = Math.max(peak, support);
            }
            if (peak < Math.max(1, Math.round(gap * .12f))) return Float.NaN;
            var centers = new ArrayList<Float>();
            for (int y = first; y <= last; y++) {
                int support = strengths[y - first];
                if (support < Math.max(1, Math.round(gap * .12f))
                        || y > first && support < strengths[y - first - 1]
                        || y < last && support < strengths[y - first + 1]) continue;
                double weighted = 0, weight = 0;
                for (int yy = first; yy <= last; yy++)
                    if (Math.abs(yy - y) <= gap * .15f && strengths[yy - first] >= support * .65f) {
                        weighted += yy * strengths[yy - first];
                        weight += strengths[yy - first];
                    }
                if (weight > 0) centers.add((float) (weighted / weight));
            }
            if (centers.isEmpty()) return Float.NaN;
            candidates.add(centers);
        }
        // A returning curve can be stronger than the ledger on one flank.
        // Select matching local peaks on both sides, preserving the phase limit.
        float center = Float.NaN, best = Float.POSITIVE_INFINITY;
        for (float left : candidates.get(0))
            for (float right : candidates.get(1)) {
                if (Math.abs(left - right) > gap * .1f) continue;
                float mean = (left + right) * .5f, distance = Math.abs(mean - expected);
                if (distance < best) {
                    best = distance;
                    center = mean;
                }
            }
        if (!Float.isFinite(center)) return Float.NaN;
        // A ledger ends near its head; an extended beam cannot establish this reference.
        if (bounded)
            for (int x : new int[] {head.minX - Math.round(gap), head.maxX + Math.round(gap)}) {
                int continued = 0;
                for (int xx = Math.max(0, x - 2); xx <= Math.min(width - 1, x + 2); xx++) {
                    boolean ink = false;
                    for (int y = Math.max(flank, Math.round(center) - 1);
                            y <= Math.min(height - 1 - flank, Math.round(center) + 1);
                            y++) {
                        int value = gray[y * width + xx] & 255;
                        if (value < 180
                                && (gray[(y - flank) * width + xx] & 255) > value + 20
                                && (gray[(y + flank) * width + xx] & 255) > value + 20) ink = true;
                    }
                    if (ink) continued++;
                }
                if (continued >= 2) return Float.NaN;
            }
        return center;
    }

    /** Near a line/space boundary, measure a compact filled head in the printed image.
     * Thin staff/stem ink is removed before centering; three thresholds must agree. */
    private static int printedPitchStep(
            byte[] gray, int width, int height, Component head, float bottom, float gap) {
        float position = (bottom - head.centerY) / (gap * .5f);
        int original = Math.round(position);
        if (gray != null
                && gap >= 8
                && original % 2 != 0
                && (original <= -3 || original >= 11)
                && head.maxX - head.minX + 1 >= gap * .7f
                && head.maxX - head.minX + 1 <= gap * 1.8f
                && head.maxY - head.minY + 1 <= gap * 1.25f) {
            int lineStep = original + (original < 0 ? 1 : -1);
            if (Math.abs(position - lineStep) <= .72f) {
                float line =
                        printedLedgerLine(
                                gray,
                                width,
                                height,
                                head,
                                bottom - lineStep * gap * .5f,
                                gap,
                                true);
                if (Float.isFinite(line)
                        && HollowLedgerCenter.straddles(
                                gray, width, height, head.centerX, line, gap)) return lineStep;
            }
        }
        if (gray != null && gap >= 8 && original % 2 == 0 && (original <= -2 || original >= 10)) {
            int inward = original < 0 ? 1 : -1;
            float outer =
                    printedLedgerLine(
                            gray, width, height, head, bottom - original * gap * .5f, gap, false);
            float first =
                    printedLedgerLine(
                            gray,
                            width,
                            height,
                            head,
                            bottom - (original + inward * 2) * gap * .5f,
                            gap,
                            true);
            float second =
                    printedLedgerLine(
                            gray,
                            width,
                            height,
                            head,
                            bottom - (original + inward * 4) * gap * .5f,
                            gap,
                            false);
            int secondStep = original + inward * 4;
            // A tie can continue at the end of the nearest ledger. The actual
            // staff rule one space inward independently fixes its phase.
            if (!Float.isFinite(first)
                    && secondStep >= 0
                    && secondStep <= 8
                    && Float.isFinite(second))
                first =
                        printedLedgerLine(
                                gray,
                                width,
                                height,
                                head,
                                bottom - (original + inward * 2) * gap * .5f,
                                gap,
                                false);
            int space = LedgerSpacePhase.resolve(position, head.centerY, gap, outer, first, second);
            if (space != original) return space;
        }
        if (gray == null
                || Math.abs(position - (float) Math.floor(position) - .5f) > .12f
                || gap < 8) return original;
        int cx = Math.round(head.centerX), cy = Math.round(head.centerY);
        if ((gray[cy * width + cx] & 255) > 140) return original;
        int radius = Math.round(gap * 1.15f),
                left = cx - radius,
                top = cy - radius,
                size = radius * 2 + 1;
        if (left < 0 || top < 0 || left + size > width || top + size > height) return original;
        int rx = Math.max(2, Math.round(gap * .20f)), ry = Math.max(1, Math.round(gap * .125f));
        List<int[]> kernel = new ArrayList<>();
        for (int dy = -ry; dy <= ry; dy++) {
            int extent = Math.round(rx * (float) Math.sqrt(1 - dy * dy / (float) (ry * ry)));
            for (int dx = -extent; dx <= extent; dx++) kernel.add(new int[] {dx, dy});
        }
        int agreed = Integer.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int threshold : new int[] {60, 100, 140}) {
            boolean[] opened = new boolean[size * size];
            for (int y = ry; y < size - ry; y++)
                for (int x = rx; x < size - rx; x++) {
                    boolean solid = true;
                    for (int[] k : kernel)
                        if ((gray[(top + y + k[1]) * width + left + x + k[0]] & 255) >= threshold) {
                            solid = false;
                            break;
                        }
                    if (solid) for (int[] k : kernel) opened[(y + k[1]) * size + x + k[0]] = true;
                }
            int seed = radius * size + radius;
            if (!opened[seed]) return original;
            int[] queue = new int[size * size];
            int count = 1, read = 0;
            queue[0] = seed;
            opened[seed] = false;
            long sumY = 0, sumX = 0;
            int minX = size, maxX = 0, minRow = size, maxRow = 0;
            while (read < count) {
                int at = queue[read++], y = at / size, x = at % size;
                sumY += y;
                sumX += x;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minRow = Math.min(minRow, y);
                maxRow = Math.max(maxRow, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || xx >= size || yy < 0 || yy >= size) continue;
                        int next = yy * size + xx;
                        if (opened[next]) {
                            opened[next] = false;
                            queue[count++] = next;
                        }
                    }
            }
            float y = top + sumY / (float) count, x = left + sumX / (float) count;
            if (count < gap * gap * .35f
                    || count > gap * gap * 1.7f
                    || maxX - minX < gap * .65f
                    || maxX - minX > gap * 1.8f
                    || maxRow - minRow < gap * .4f
                    || maxRow - minRow > gap * 1.3f
                    || Math.abs(x - head.centerX) > gap * .2f
                    || Math.abs(y - head.centerY) > gap * .15f) return original;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            int pitch = Math.round((bottom - y) / (gap * .5f));
            if (pitch == original) return original;
            if (agreed != Integer.MIN_VALUE && agreed != pitch) return original;
            agreed = pitch;
        }
        return maxY - minY <= gap * .04f ? agreed : original;
    }

    private static float[] localStaffPitch(
            byte[] labels, byte[] gray, int width, int height, Staff staff, Component head) {
        float gap = staff.pitchGap,
                referenceBottom =
                        staff.pitchBottom + staff.pitchSlope * (head.centerX - width * .5f);
        boolean curved = staff.pitchTrack != null && staff.pitchTrack.activeAt(head.centerX);
        if (curved) {
            float[] local = staff.pitchTrack.at(head.centerX);
            referenceBottom = local[0];
            gap = local[1];
        }
        boolean shaded =
                StaffPitchTrack.needsContrast(
                        gray, width, height, head.centerX, referenceBottom, gap);
        float[] complete =
                shaded
                        ? StaffPitchTrack.localRules(
                                labels,
                                gray,
                                width,
                                height,
                                head.centerX,
                                head.minX,
                                head.maxX,
                                referenceBottom,
                                gap,
                                curved)
                        : StaffPitchTrack.localPrintedRules(
                                labels,
                                gray,
                                width,
                                height,
                                head.centerX,
                                head.minX,
                                head.maxX,
                                referenceBottom,
                                gap);
        // A complete faded five-rule group is stronger evidence than a fit
        // whose obscured rule is inferred from neighboring observations.
        if (complete == null)
            complete =
                    StaffPitchTrack.localFadedRules(
                            labels,
                            gray,
                            width,
                            height,
                            head.centerX,
                            head.minX,
                            head.maxX,
                            referenceBottom,
                            gap);
        if (complete == null) {
            complete =
                    StaffPitchTrack.localOccludedRules(
                            labels,
                            gray,
                            width,
                            height,
                            head.centerX,
                            head.minX,
                            head.maxX,
                            referenceBottom,
                            gap);
            if (complete != null
                    && staff.printedPhase
                    && !curved
                    && Math.abs(complete[1] - gap) > gap * .035f)
                return new float[] {referenceBottom, gap};
        }
        if (complete == null)
            complete =
                    NeighboringStaffPhase.resolve(
                            labels, gray, width, height, head.centerX, referenceBottom, gap);
        if (complete == null && shaded)
            complete =
                    StaffPitchTrack.localCurledEdgeRules(
                            gray,
                            width,
                            height,
                            head.centerX,
                            head.minX,
                            head.maxX,
                            referenceBottom,
                            gap);
        if (complete == null && shaded)
            complete =
                    NeighboringStaffPhase.rawBracket(
                            gray, width, height, head.centerX, referenceBottom, gap);
        if (complete == null && shaded)
            complete =
                    BeamOccludedStaffPhase.resolve(
                            labels,
                            gray,
                            width,
                            height,
                            head.centerX,
                            head.minX,
                            head.maxX,
                            referenceBottom,
                            gap);
        if (complete == null && shaded)
            complete =
                    ClosedStaffBarPhase.resolve(
                            gray,
                            width,
                            height,
                            head.centerX,
                            head.centerY,
                            head.minX,
                            head.maxX,
                            referenceBottom,
                            gap);
        if (complete != null
                && !curved
                && Math.abs(Math.abs(complete[0] - referenceBottom) - gap) < gap * .2f) {
            // A slur plus four rules is not a new staff phase when all five
            // original straight rules independently survive across the page.
            float[] broad =
                    StaffPitchTrack.broadStraightPitch(
                            gray, width, height, referenceBottom, gap, false);
            if (broad != null) return broad;
        }
        if (complete != null && Math.abs(complete[1] - gap) > gap * .04f && !curved) {
            float[] broad =
                    StaffPitchTrack.broadStraightPitch(
                            gray, width, height, referenceBottom, gap, false);
            if (broad != null && Math.abs(complete[1] - broad[1]) > gap * .035f) return broad;
        }
        if (complete != null
                && (!staff.printedPhase || Math.abs(complete[0] - referenceBottom) < gap * .5f))
            return complete;
        int radius = Math.max(4, Math.round(gap * 3.5f));
        int left = Math.max(0, Math.round(head.centerX) - radius);
        int right = Math.min(width - 1, Math.round(head.centerX) + radius);
        int top = Math.max(0, Math.round(referenceBottom - gap * .58f));
        int bottom = Math.min(height - 1, Math.round(referenceBottom + gap * .58f));
        int exclusion = Math.max(1, Math.round(gap * .45f));
        if (gray != null) {
            // A beam can merge with one rule and leave another thin edge half a
            // space away. Require agreement from several of the five printed
            // rules before using that edge as a local pitch reference.
            // If dark ink is too sparse, require four matching light-ink rules.
            for (float support : new float[] {.70f, .40f}) {
                List<Float> offsets = new ArrayList<>();
                List<float[]> rules = new ArrayList<>();
                for (int line = 0; line < 5; line++) {
                    float reference = referenceBottom - line * gap;
                    int first = Math.max(0, Math.round(reference - gap * .58f));
                    int last = Math.min(height - 1, Math.round(reference + gap * .58f));
                    float closest = Float.NaN, distance = Float.MAX_VALUE;
                    int start = -1;
                    for (int y = first; y <= last + 1; y++) {
                        int dark = 0, samples = 0;
                        if (y <= last)
                            for (int x = left; x <= right; x++) {
                                if (x >= head.minX - exclusion && x <= head.maxX + exclusion)
                                    continue;
                                samples++;
                                int ink = gray[y * width + x] & 255,
                                        flank = Math.max(2, Math.round(gap * .32f));
                                if (ink <= (support < .7f ? 205 : 165)
                                        && (!shaded
                                                || (y >= flank
                                                        && y + flank < height
                                                        && (gray[(y - flank) * width + x] & 255)
                                                                >= ink + 12
                                                        && (gray[(y + flank) * width + x] & 255)
                                                                >= ink + 12))) dark++;
                            }
                        boolean rule = samples >= 8 && dark >= samples * support;
                        if (rule && start < 0) start = y;
                        if (!rule && start >= 0) {
                            float offset = (start + y - 1) * .5f - reference;
                            if (y - start <= Math.max(3, gap * .38f)
                                    && Math.abs(offset) < distance) {
                                closest = offset;
                                distance = Math.abs(offset);
                            }
                            start = -1;
                        }
                    }
                    if (Float.isFinite(closest)) {
                        offsets.add(closest);
                        rules.add(new float[] {line, reference + closest});
                    }
                }
                // Derive spacing locally as well as the bottom rule. Semantic
                // stripes can contract on a skewed scan; using that contracted gap
                // still moves ledger pitches even after the bottom rule is corrected.
                if (rules.size() >= 4) {
                    List<Float> slopes = new ArrayList<>();
                    for (float[] a : rules)
                        for (float[] b : rules)
                            if (b[0] > a[0]) slopes.add((a[1] - b[1]) / (b[0] - a[0]));
                    slopes.sort(Float::compare);
                    float localGap = slopes.get(slopes.size() / 2);
                    List<Float> bottoms = new ArrayList<>();
                    for (float[] rule : rules) bottoms.add(rule[1] + rule[0] * localGap);
                    bottoms.sort(Float::compare);
                    float localBottom = bottoms.get(bottoms.size() / 2);
                    int consistent = 0;
                    for (float value : bottoms)
                        if (Math.abs(value - localBottom) <= gap * .12f) consistent++;
                    if (consistent >= 4 && localGap >= gap * .88f && localGap <= gap * 1.12f)
                        return new float[] {localBottom, localGap};
                }
                List<Float> agreed = List.of();
                float bestDistance = Float.MAX_VALUE;
                for (float candidate : offsets) {
                    List<Float> cluster = new ArrayList<>();
                    for (float offset : offsets)
                        if (Math.abs(offset - candidate) <= gap * .18f) cluster.add(offset);
                    float distance = Math.abs(candidate);
                    if (cluster.size() > agreed.size()
                            || cluster.size() == agreed.size() && distance < bestDistance) {
                        agreed = cluster;
                        bestDistance = distance;
                    }
                }
                if (agreed.size() >= (support < .7f ? 4 : 3)) {
                    agreed.sort(Float::compare);
                    return new float[] {referenceBottom + agreed.get(agreed.size() / 2), gap};
                }
            }
            // Faded rules may not support a local correction. A single beam
            // edge or semantic smear is weaker evidence than the page's five-
            // rule reference, especially for ledger notes. Keep that reference.
            return new float[] {referenceBottom, gap};
        }
        long weightedY = 0;
        int pixels = 0;
        for (int x = left; x <= right; x++) {
            if (x >= head.minX - exclusion && x <= head.maxX + exclusion) continue;
            for (int y = top; y <= bottom; y++) {
                if (labels[y * width + x] != OmrMeasurePostProcessor.STAFF) continue;
                weightedY += y;
                pixels++;
            }
        }
        return new float[] {
            pixels >= Math.max(4, radius / 3) ? weightedY / (float) pixels : referenceBottom, gap
        };
    }

    /** Augmentation dots are small, aligned components to the right of a real notehead. */
    /** The round bulbs of a recognized rest belong to that rest, even if a staff
     * crossing separates their darkest pixels into a dot-shaped island. */
    private static int dotsOutsideRests(
            List<Component> candidates,
            DetectedNote note,
            List<ScoreRestEvent> rests,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height,
            List<Component> accidentalInk,
            Component dotAnchor,
            List<Component> neighboringHeads) {
        return dotsOutsideRests(
                candidates,
                note,
                rests,
                measures,
                gray,
                width,
                height,
                accidentalInk,
                dotAnchor,
                neighboringHeads,
                note.staffGap);
    }

    private static int dotsOutsideRests(
            List<Component> candidates,
            DetectedNote note,
            List<ScoreRestEvent> rests,
            List<MeasureRegion> measures,
            byte[] gray,
            int width,
            int height,
            List<Component> accidentalInk,
            Component dotAnchor,
            List<Component> neighboringHeads,
            float dotGap) {
        if (note.event.augmentationDots() == 0 || rests.isEmpty())
            return note.event.augmentationDots();
        List<Component> excluded = new ArrayList<>(accidentalInk);
        for (ScoreRestEvent rest : rests)
            if (rest.measureIndex() == note.event.measureIndex()
                    && rest.staffIndex() == note.event.staffIndex()
                    && rest.staffCount() == note.event.staffCount()) {
                MeasureRegion bar = measures.get(rest.measureIndex());
                float x =
                        (bar.left() + rest.positionInMeasure() * (bar.right() - bar.left()))
                                * width;
                float y = rest.pageY() * height, half = rest.pageHeight() * height * .5f;
                excluded.add(
                        new Component(
                                1,
                                Math.round(x - note.staffGap * .65f),
                                Math.round(x + note.staffGap * .65f),
                                Math.round(y - half),
                                Math.round(y + half),
                                x,
                                y));
            }
        return excluded.isEmpty() && dotAnchor == note.head
                ? note.event.augmentationDots()
                : countAugmentationDots(
                        candidates,
                        dotAnchor,
                        dotGap,
                        gray,
                        width,
                        height,
                        note.event.unbeamedDurationBeats() >= ScoreNoteEvent.DURATION_HALF,
                        excluded,
                        neighboringHeads);
    }

    /** Far-displaced rests belong to the opposing voice; central rests remain shared. */
    /** A rest sharing a note's column belongs to another voice, even for quarter notes. */
    static boolean restIsSeparateAttack(ScoreRestEvent rest, ScoreNoteEvent note) {
        return Math.abs(rest.positionInMeasure() - note.positionInMeasure()) > .018f;
    }

    /** An attack in the same beamed voice occupies its column, even beside another voice's rest. */
    static boolean restIsSeparateAttack(
            ScoreRestEvent rest,
            ScoreNoteEvent note,
            List<ScoreNoteEvent> notes,
            MeasureRegion region,
            byte[] gray,
            int width,
            int height,
            float gap) {
        if (!restIsSeparateAttack(rest, note)) return false;
        if (rest.durationBeats() < 1
                || rest.durationBeats() > 1.75
                || note.beamCount() < 1
                || note.beamCount() > 2
                || note.crossStaffBeam()
                || (note.articulations() & NoteOrnament.GRACE) != 0) return true;
        int direction = restVoiceDirection(note, region, gray, width, height, gap);
        if (direction == 0
                || !restAttachedBeam(note, notes, region, gray, width, height, gap, direction))
            return true;
        for (var other : notes) {
            if (other == note
                    || other.measureIndex() != note.measureIndex()
                    || other.staffIndex() != note.staffIndex()
                    || other.staffCount() != note.staffCount()
                    || other.beamCount() != note.beamCount()
                    || other.crossStaffBeam()
                    || (other.articulations() & NoteOrnament.GRACE) != 0
                    || Math.abs(other.positionInMeasure() - rest.positionInMeasure()) > .018f
                    || Math.abs(other.pageY() - rest.pageY()) * height <= gap * .65f) continue;
            if (restVoiceDirection(other, region, gray, width, height, gap) == direction
                    && restAttachedBeam(other, notes, region, gray, width, height, gap, direction))
                return false;
        }
        return true;
    }

    private static int restVoiceDirection(
            ScoreNoteEvent note,
            MeasureRegion region,
            byte[] gray,
            int width,
            int height,
            float gap) {
        if (note.stemDirection() != 0) return note.stemDirection();
        float x =
                (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                        * width;
        return PrintedStemDirection.detect(gray, width, height, x, note.pageY() * height, gap);
    }

    private static boolean restAttachedBeam(
            ScoreNoteEvent note,
            List<ScoreNoteEvent> notes,
            MeasureRegion region,
            byte[] gray,
            int width,
            int height,
            float gap,
            int direction) {
        float x =
                (region.left() + note.positionInMeasure() * (region.right() - region.left()))
                        * width;
        for (var other : notes) {
            if (other == note
                    || other.measureIndex() != note.measureIndex()
                    || other.staffIndex() != note.staffIndex()
                    || other.staffCount() != note.staffCount()
                    || other.beamCount() != note.beamCount()
                    || other.crossStaffBeam()
                    || (other.articulations() & NoteOrnament.GRACE) != 0
                    || restVoiceDirection(other, region, gray, width, height, gap) != direction)
                continue;
            float ox =
                    (region.left() + other.positionInMeasure() * (region.right() - region.left()))
                            * width;
            if (Math.abs(ox - x) < gap * .8f || Math.abs(ox - x) > gap * 26) continue;
            if (PrintedTupletBeamOwner.connectedHeads(
                    gray,
                    width,
                    height,
                    Math.min(x, ox),
                    (x < ox ? note.pageY() : other.pageY()) * height,
                    Math.max(x, ox),
                    (x < ox ? other.pageY() : note.pageY()) * height,
                    gap,
                    direction)) return true;
        }
        return false;
    }

    static boolean restSharesStemVoice(
            float restY, float staffMiddle, float gap, int stemDirection) {
        if (stemDirection < 0 && restY > staffMiddle + gap * 1.25f) return false;
        if (stemDirection > 0 && restY < staffMiddle - gap * 1.25f) return false;
        return true;
    }

    private static int countAugmentationDots(
            List<Component> candidates,
            Component head,
            float gap,
            byte[] gray,
            int width,
            int height,
            boolean hollowHead) {
        return countAugmentationDots(
                candidates, head, gap, gray, width, height, hollowHead, List.of());
    }

    private static int countAugmentationDots(
            List<Component> candidates,
            Component head,
            float gap,
            byte[] gray,
            int width,
            int height,
            boolean hollowHead,
            List<Component> excluded) {
        return countAugmentationDots(
                candidates, head, gap, gray, width, height, hollowHead, excluded, List.of());
    }

    private static int countAugmentationDots(
            List<Component> candidates,
            Component head,
            float gap,
            byte[] gray,
            int width,
            int height,
            boolean hollowHead,
            List<Component> excluded,
            List<Component> neighboringHeads) {
        // Semantic boundaries can cut a tiny round island out of a slur, stem
        // or ledger line. When the page is available, require an isolated raw
        // ink component; the model's artificial boundary is not a printed dot.
        List<Component> combined =
                gray != null && gray.length == width * height
                        ? new ArrayList<>(findDarkDotComponents(gray, width, height, head, gap))
                        : new ArrayList<>(candidates);
        // Antialiasing can join a round dot to a nearby tie. Its dark core remains
        // separate; retain the same bounds, shape and engraving-slot checks below.
        if (gray != null && gray.length == width * height)
            for (Component core : findDarkDotComponents(gray, width, height, head, gap, 70))
                if (core.area >= gap * gap * .06f
                        && core.maxX - core.minX + 1 >= gap * .22f
                        && core.maxY - core.minY + 1 >= gap * .22f) combined.add(core);
        if (gray != null && gray.length == width * height)
            combined.addAll(fadedAugmentationDots(gray, width, height, head, gap));
        if (gray != null && gray.length == width * height)
            combined.addAll(tieTouchingDotCores(gray, width, height, head, gap, neighboringHeads));
        List<Component> aligned = new ArrayList<>();
        for (Component dot : combined) {
            if (excluded.stream()
                    .anyMatch(
                            c ->
                                    dot.centerX >= c.minX
                                            && dot.centerX <= c.maxX
                                            && dot.centerY >= c.minY
                                            && dot.centerY <= c.maxY)) continue;
            float dotWidth = dot.maxX - dot.minX + 1f;
            float dotHeight = dot.maxY - dot.minY + 1f;
            float horizontal = dot.centerX - head.maxX;
            if (horizontal < gap * .12f || horizontal > gap * 2.2f) continue;
            // Raw antialiasing often leaves a 2-4 px island just beyond the semantic oval.
            // A compact engraving slot can fall just inside one staff gap. Require a
            // separated round body there; tiny edge islands remain part of the head.
            if (dot.centerX - head.centerX < gap
                    && (gray == null
                            || dot.centerX - head.centerX < gap * .85f
                            || dot.minX - head.maxX < gap * .18f
                            || Math.min(dotWidth, dotHeight) < gap * .22f
                            || dot.area < gap * gap * .045f
                            || dot.area >= dotWidth * dotHeight)) continue;
            // Augmentation dots sit beside the head (with at most the usual line-to-space
            // engraving offset). A detached bowing/staccato mark near the next note is not a dot.
            if (Math.abs(dot.centerY - head.centerY) > gap * .65f) continue;
            if (staccatoAtNextHead(dot, head, neighboringHeads, gap)) continue;
            if (dotWidth < Math.max(1f, gap * .10f)
                    || dotHeight < Math.max(1f, gap * .10f)
                    || dotWidth > gap * .68f
                    || dotHeight > gap * .68f
                    // Preserve faint hollow-head cores; a resolved filled-note
                    // dot needs slightly more ink than a tiny scan-texture island.
                    || dot.area < Math.max(1, Math.round(gap * gap * (hollowHead ? .018f : .02f)))
                    // Run's dotted half has a round 8x8, 52-pixel dot at a 13.75-pixel staff gap.
                    // Allow that slightly heavier ink only beside a verified hollow head.
                    || dot.area > gap * gap * (hollowHead ? .34f : .26f)) continue;
            if (gray != null && !augmentationDotContrast(gray, width, height, dot, gap)) continue;
            if (gray != null && fadedRuleFragment(gray, width, height, dot, gap)) continue;
            if (gray != null && fadedStemFragment(gray, width, height, dot, gap)) continue;
            if (gray != null
                    && AccidentalDotInk.matches(
                            gray, width, height, dot.minX, dot.minY, dot.maxX, dot.maxY, gap))
                continue;
            float dotFill = dot.area / Math.max(1f, dotWidth * dotHeight);
            if (Math.max(dotWidth, dotHeight) / Math.max(1f, Math.min(dotWidth, dotHeight)) > 1.5f
                    || dotFill < .44f) continue;
            boolean duplicate = false;
            for (Component existing : aligned)
                if (Math.abs(existing.centerX - dot.centerX) <= gap * .28f
                        && Math.abs(existing.centerY - dot.centerY) <= gap * .28f) {
                    duplicate = true;
                    break;
                }
            if (!duplicate) aligned.add(dot);
        }
        aligned.sort(Comparator.comparingDouble(Component::centerX));
        if (aligned.isEmpty()) return 0;
        Component first = aligned.get(0);
        if (first.centerX - head.maxX > gap * 1.55f) return 0;
        if (aligned.size() == 1) return 1;
        Component second = aligned.get(1);
        float spacing = second.centerX - first.centerX;
        return spacing >= gap * .18f
                        && spacing <= gap * 1.45f
                        && Math.abs(second.centerY - first.centerY) <= gap * .40f
                ? 2
                : 1;
    }

    /** A tiny threshold island in shaded paper needs an independently contrasting printed core. */
    private static boolean augmentationDotContrast(
            byte[] gray, int width, int height, Component dot, float gap) {
        int radius = Math.max(4, Math.round(gap)),
                cx = Math.round(dot.centerX),
                cy = Math.round(dot.centerY);
        int[] tones = new int[256];
        int count = 0;
        for (int y = Math.max(0, cy - radius); y <= Math.min(height - 1, cy + radius); y++)
            for (int x = Math.max(0, cx - radius); x <= Math.min(width - 1, cx + radius); x++) {
                tones[gray[y * width + x] & 255]++;
                count++;
            }
        int target = (count * 3 + 3) / 4, paper = 0, seen = tones[0];
        while (seen < target && paper < 255) seen += tones[++paper];
        int core = 0;
        for (int y = dot.minY; y <= dot.maxY; y++)
            for (int x = dot.minX; x <= dot.maxX; x++)
                if ((gray[y * width + x] & 255) <= paper - 30) core++;
        return core >= Math.max(1, Math.round(dot.area * .20f));
    }

    private static boolean staccatoAtNextHead(
            Component dot, Component head, List<Component> neighboringHeads, float gap) {
        for (Component next : neighboringHeads)
            if (next != head
                    && next.maxX > head.maxX
                    && next.centerX - head.centerX > gap * .8f
                    && next.centerX - head.centerX < gap * 2.4f
                    && Math.abs(dot.centerX - next.centerX) < gap * .35f) {
                boolean below =
                        Math.abs(next.centerY - head.centerY) < gap * .6f
                                && dot.centerY - next.centerY > gap * .5f
                                && dot.centerY - next.centerY < gap * 1.3f;
                // A descending interval can put the next head's upper staccato
                // beside the previous head. Its own vertical column owns the mark.
                boolean above =
                        Math.abs(next.centerY - head.centerY) > gap * .7f
                                && Math.abs(next.centerY - head.centerY) < gap * 1.5f
                                && next.maxX - next.minX + 1 >= gap * .8f
                                && next.centerY - dot.centerY > gap * .8f
                                && next.centerY - dot.centerY < gap * 2.5f;
                if (below || above) return true;
            }
        return false;
    }

    /** Faint dots may have no substantial pixels at the normal ink cutoff.
     * Require an isolated rounded body with a smaller, centered dark core at
     * a second cutoff; all normal engraving and line-fragment guards still apply. */
    private static List<Component> fadedAugmentationDots(
            byte[] gray, int width, int height, Component head, float gap) {
        List<Component> result = new ArrayList<>();
        for (int bodyThreshold : new int[] {175, 180, 195, 205}) {
            List<Component> cores =
                    findDarkDotComponents(gray, width, height, head, gap, bodyThreshold - 25);
            for (Component body :
                    findDarkDotComponents(gray, width, height, head, gap, bodyThreshold)) {
                float w = body.maxX - body.minX + 1, h = body.maxY - body.minY + 1;
                if (Math.min(w, h) < gap * .28f
                        || Math.max(w, h) > gap * .68f
                        || Math.max(w, h) > Math.min(w, h) * 1.5f
                        || body.area < gap * gap * .055f
                        || body.area < w * h * .55f
                        || body.area >= w * h) continue;
                for (Component core : cores) {
                    float cw = core.maxX - core.minX + 1, ch = core.maxY - core.minY + 1;
                    if (core.minX < body.minX
                            || core.maxX > body.maxX
                            || core.minY < body.minY
                            || core.maxY > body.maxY
                            || Math.min(cw, ch) < gap * .18f
                            || Math.max(cw, ch) > Math.min(cw, ch) * 1.5f + .5f
                            || core.area < gap * gap * .025f
                            || core.area < body.area * .25f
                            || core.area > body.area * .85f
                            || Math.abs(core.centerX - body.centerX) > gap * .15f
                            || Math.abs(core.centerY - body.centerY) > gap * .15f) continue;
                    result.add(body);
                    break;
                }
            }
        }
        return result;
    }

    /** Thresholding can isolate the darker crossing of a shaded stem and rule. */
    private static boolean fadedStemFragment(
            byte[] gray, int width, int height, Component dot, float gap) {
        int cx = Math.round(dot.centerX), cy = Math.round(dot.centerY);
        int band = Math.max(1, Math.round(gap * .12f)), flank = Math.max(2, Math.round(gap * .45f));
        int first = Math.max(Math.round(gap * .4f), (dot.maxY - dot.minY + 1) / 2 + 2);
        int last = Math.round(gap * 1.3f);
        if (last - first < 4
                || cx - band - flank < 0
                || cx + band + flank >= width
                || cy - last < 0
                || cy + last >= height) return false;
        int longSides = 0, shortSides = 0;
        int nearFirst = Math.max(2, (dot.maxY - dot.minY + 1) / 2 + 1),
                nearLast = Math.round(gap * .65f);
        for (int direction : new int[] {-1, 1}) {
            int support = 0;
            for (int distance = first; distance <= last; distance++) {
                int y = cy + direction * distance;
                for (int x = cx - band; x <= cx + band; x++) {
                    int ink = gray[y * width + x] & 255;
                    int paper =
                            Math.max(
                                    gray[y * width + x - flank] & 255,
                                    gray[y * width + x + flank] & 255);
                    if (ink <= 210 && paper >= ink + 12) {
                        support++;
                        break;
                    }
                }
            }
            if (support >= (last - first + 1) * .8f) longSides++;
            int nearSupport = 0;
            for (int distance = nearFirst; distance <= nearLast; distance++) {
                int y = cy + direction * distance;
                for (int x = cx - band; x <= cx + band; x++) {
                    int ink = gray[y * width + x] & 255;
                    int paper =
                            Math.max(
                                    gray[y * width + x - flank] & 255,
                                    gray[y * width + x + flank] & 255);
                    if (ink <= 210 && paper >= ink + 12) {
                        nearSupport++;
                        break;
                    }
                }
            }
            if (nearLast - nearFirst >= 3 && nearSupport >= (nearLast - nearFirst + 1) * .8f)
                shortSides++;
        }
        // A crossing near a stem tip has a shorter continuation on one side.
        // Require a long stem plus close, continuous support on both sides of the dot core.
        return longSides == 2 || (longSides >= 1 && shortSides == 2);
    }

    /** Thresholding can isolate a darker fleck along a faded staff rule. */
    private static boolean fadedRuleFragment(
            byte[] gray, int width, int height, Component dot, float gap) {
        if (dot.maxY - dot.minY + 1 > gap * .25f) return false;
        int row = Math.round(dot.centerY),
                flank = Math.max(2, Math.round(gap * .3f)),
                band = Math.max(1, Math.round(gap * .16f));
        if (row < flank || row + flank >= height) return false;
        int matching = 0;
        for (int line = -4; line <= 4; line++) {
            if (line == 0) continue;
            int center = Math.round(row + line * gap), supported = 0, samples = 0;
            if (center < flank + band || center + flank + band >= height) continue;
            for (int x = Math.max(0, Math.round(dot.centerX - gap * 1.5f));
                    x <= Math.min(width - 1, Math.round(dot.centerX + gap * 1.5f));
                    x++) {
                samples++;
                boolean ink = false;
                for (int y = center - band; y <= center + band; y++) {
                    int value = gray[y * width + x] & 255;
                    if (value <= 240
                            && (gray[(y - flank) * width + x] & 255) >= value + 12
                            && (gray[(y + flank) * width + x] & 255) >= value + 12) ink = true;
                }
                if (ink) supported++;
            }
            if (samples >= 12 && supported >= samples * .55f) matching++;
        }
        return matching >= 3;
    }

    private static List<Component> findDarkDotComponents(
            byte[] gray, int width, int height, Component head, float gap) {
        return findDarkDotComponents(gray, width, height, head, gap, 135);
    }

    private static List<Component> findDarkDotComponents(
            byte[] gray, int width, int height, Component head, float gap, int threshold) {
        int left = Math.max(0, Math.round(head.maxX + gap * .08f));
        int right = Math.min(width - 1, Math.round(head.maxX + gap * 2.3f));
        // Dots beside line notes sit in the next space (half a staff gap away). Include the
        // entire dot there; clipping its edge made the bounded-component guard discard it.
        int top = Math.max(0, Math.round(head.centerY - gap * .92f));
        int bottom = Math.min(height - 1, Math.round(head.centerY + gap * .92f));
        int localWidth = right - left + 1, localHeight = bottom - top + 1;
        if (localWidth <= 0 || localHeight <= 0) return List.of();
        // A real dot can touch a thin staff rule after downsampling. Remove only
        // rows supported across the entire search width, never a rounded local mark.
        boolean[] ruleRows = new boolean[localHeight];
        for (int y = 0; y < localHeight; ) {
            int start = y;
            while (y < localHeight) {
                int ink = 0;
                for (int x = left; x <= right; x++)
                    if ((gray[(top + y) * width + x] & 255) <= threshold) ink++;
                if (ink < localWidth * .90f) break;
                y++;
            }
            if (threshold <= 70 && y > start && y - start <= Math.max(2, Math.round(gap * .23f)))
                java.util.Arrays.fill(ruleRows, start, y, true);
            if (y == start) y++;
        }
        boolean[] visited = new boolean[localWidth * localHeight];
        int[] stack = new int[visited.length];
        List<Component> result = new ArrayList<>();
        for (int localOrigin = 0; localOrigin < visited.length; localOrigin++) {
            int originX = localOrigin % localWidth, originY = localOrigin / localWidth;
            if (visited[localOrigin]
                    || ruleRows[originY]
                    || (gray[(top + originY) * width + left + originX] & 0xff) > threshold)
                continue;
            int stackSize = 0;
            stack[stackSize++] = localOrigin;
            visited[localOrigin] = true;
            int area = 0, minX = right, maxX = left, minY = bottom, maxY = top;
            long sumX = 0, sumY = 0;
            while (stackSize > 0) {
                int current = stack[--stackSize];
                int lx = current % localWidth, ly = current / localWidth;
                int x = left + lx, y = top + ly;
                area++;
                sumX += x;
                sumY += y;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = lx + dx, ny = ly + dy;
                        if ((dx == 0 && dy == 0)
                                || nx < 0
                                || nx >= localWidth
                                || ny < 0
                                || ny >= localHeight) continue;
                        int next = ny * localWidth + nx;
                        if (!visited[next]
                                && !ruleRows[ny]
                                && (gray[(top + ny) * width + left + nx] & 0xff) <= threshold) {
                            visited[next] = true;
                            stack[stackSize++] = next;
                        }
                    }
            }
            // Removing a rule can expose the tiny end of a curved flag. A newly
            // separated mark needs a substantial round core to count as a dot.
            boolean ruleCut =
                    minY > top && ruleRows[minY - top - 1]
                            || maxY < bottom && ruleRows[maxY - top + 1];
            if (ruleCut && area < gap * gap * .06f) continue;
            // A component cut by the search window is not an isolated dot. In particular the
            // protruding end of a whole note's ledger line used to become a false dot here.
            if (minX > left && maxX < right && minY > top && maxY < bottom)
                result.add(
                        new Component(
                                area,
                                minX,
                                maxX,
                                minY,
                                maxY,
                                sumX / (float) area,
                                sumY / (float) area));
        }
        return result;
    }

    /** Three parallel slashes can sit above or below a stemless whole note. */
    private static int[] detachedTremolo(
            byte[] gray, int width, int height, Component head, float gap) {
        if (gray == null) return null;
        int radius = Math.max(3, Math.round(gap * .4f));
        for (int bandThreshold : new int[] {55, 170}) {
            int edgeThreshold = bandThreshold == 55 ? 135 : 180;
            for (int direction : new int[] {1, -1})
                for (float shift : new float[] {0, -.15f, .15f})
                    for (float slope : new float[] {-.5f, -.35f, -.65f, -.8f}) {
                        int cx = Math.round(head.centerX + shift * gap),
                                edge = direction > 0 ? head.maxY : head.minY;
                        int a = edge + direction * Math.round(gap * .35f),
                                b = edge + direction * Math.round(gap * 3.4f);
                        int first = Math.max(radius + 1, Math.min(a, b)),
                                last = Math.min(height - radius - 2, Math.max(a, b));
                        if (cx - Math.round(gap * 1.2f) < 0 || cx + Math.round(gap * 1.2f) >= width)
                            continue;
                        List<int[]> bands = new ArrayList<>();
                        int run = 0;
                        for (int y = first; y <= last + 1; y++) {
                            int shade = 0;
                            if (y <= last)
                                for (int dx = -radius; dx <= radius; dx++)
                                    shade +=
                                            gray[(y + Math.round(slope * dx)) * width + cx + dx]
                                                    & 255;
                            // A light photocopy can preserve all three slanted strokes without
                            // any nearly-black pixels. Shape, spacing and bounded wings still
                            // have to prove the complete group; intensity alone is not a mark.
                            boolean ink = y <= last && shade < (radius * 2 + 1) * bandThreshold;
                            if (ink) run++;
                            else {
                                if (run >= 2 && run <= gap * .75f)
                                    bands.add(new int[] {y - run, y - 1});
                                run = 0;
                            }
                        }
                        for (int i = 0; i + 2 < bands.size(); i++) {
                            int[] one = bands.get(i),
                                    two = bands.get(i + 1),
                                    three = bands.get(i + 2);
                            float c1 = (one[0] + one[1]) * .5f,
                                    c2 = (two[0] + two[1]) * .5f,
                                    c3 = (three[0] + three[1]) * .5f;
                            if (c2 - c1 < gap * .45f
                                    || c2 - c1 > gap * 1.05f
                                    || c3 - c2 < gap * .45f
                                    || c3 - c2 > gap * 1.05f
                                    || Math.abs(c3 - 2 * c2 + c1) > gap * .3f
                                    || Math.abs((direction > 0 ? c1 : c3) - edge) > gap * 1.5f)
                                continue;
                            int diagonal = 0;
                            for (float center : new float[] {c1, c2, c3}) {
                                float left =
                                        verticalInkCenter(
                                                gray,
                                                width,
                                                height,
                                                cx - radius,
                                                Math.round(center - slope * radius),
                                                gap,
                                                edgeThreshold);
                                float right =
                                        verticalInkCenter(
                                                gray,
                                                width,
                                                height,
                                                cx + radius,
                                                Math.round(center + slope * radius),
                                                gap,
                                                edgeThreshold);
                                // Pixel-run centres are quantized to half pixels on small staves.
                                if (Float.isFinite(left)
                                        && Float.isFinite(right)
                                        && left - right >= Math.max(1, Math.floor(gap * .15f))
                                        && left - right <= gap * 1.2f) diagonal++;
                            }
                            if (diagonal < 2) continue;
                            // Horizontal rules and broad beams do not end near the note's centre.
                            boolean bounded = true;
                            for (float center : new float[] {c1, c2, c3})
                                for (int sign : new int[] {-1, 1}) {
                                    int x = cx + sign * Math.round(gap * 1.05f), dark = 0;
                                    int y = Math.round(center + slope * (x - cx));
                                    if (y < 2 || y >= height - 2) {
                                        bounded = false;
                                        continue;
                                    }
                                    for (int yy = Math.max(0, y - radius);
                                            yy <= Math.min(height - 1, y + radius);
                                            yy++) {
                                        dark =
                                                (gray[yy * width + x] & 255) < edgeThreshold
                                                        ? dark + 1
                                                        : 0;
                                        if (dark >= Math.max(4, Math.round(gap * .3f)))
                                            bounded = false;
                                    }
                                }
                            if (bounded)
                                return new int[] {
                                    cx - Math.round(gap * .95f),
                                    cx + Math.round(gap * .95f),
                                    one[0] - Math.round(gap * .45f),
                                    three[1] + Math.round(gap * .45f)
                                };
                        }
                    }
        }
        return null;
    }

    private static float verticalInkCenter(
            byte[] gray, int width, int height, int x, int y, float gap, int threshold) {
        if (y < 0 || y >= height || (gray[y * width + x] & 255) >= threshold) return Float.NaN;
        int top = y, bottom = y, limit = Math.round(gap * 1.2f);
        while (top > 0 && y - top < limit && (gray[(top - 1) * width + x] & 255) < threshold) top--;
        while (bottom < height - 1
                && bottom - y < limit
                && (gray[(bottom + 1) * width + x] & 255) < threshold) bottom++;
        if (y - top == limit || bottom - y == limit) return Float.NaN;
        return (top + bottom) * .5f;
    }

    /** Isolated thick strokes cross BOTH sides of their own stem and end before
     * neighboring stems. Rhythmic beams and one-sided beam hooks fail those bounds. */
    private static int[] tremoloStrokeCounts(
            byte[] gray, int width, int height, Component head, float gap, List<Component> heads) {
        int[] empty = {0, 0};
        if (gray == null || head.maxX - head.minX + 1 < gap * .95f) return empty;
        // Shaded paper beyond the actual shaft must not connect nearby lettering.
        int threshold =
                BeamInkThreshold.at(
                                gray,
                                width,
                                height,
                                Math.round(head.centerX),
                                Math.round(head.centerY - gap * 5),
                                Math.round(head.centerY + gap * 5),
                                gap)
                        + 5;
        int[] stem =
                attachedRawStem(
                        gray,
                        width,
                        height,
                        head,
                        gap,
                        Math.max(1, Math.round(gap * .16f)),
                        threshold);
        if (stem == null) return empty;
        int side = Math.max(2, Math.round(gap * .38f)),
                far = Math.max(side + 2, Math.round(gap * 1.4f));
        if (stem[0] - far < 0 || stem[0] + far >= width) return empty;
        int edge = stem[2] > 0 ? head.maxY : head.minY;
        int a = edge + stem[2] * Math.round(gap * .65f),
                b = stem[1] + stem[2] * Math.round(gap * .15f);
        int first = Math.max(1, Math.min(a, b)), last = Math.min(height - 2, Math.max(a, b));
        int strokeThreshold =
                BeamInkThreshold.at(gray, width, height, stem[0], first, last, gap) + 5;
        java.util.Set<Long> strokeKeys = new java.util.HashSet<>(),
                nearKeys = new java.util.HashSet<>();
        int run = 0;
        for (int y = first; y <= last + 1; y++) {
            boolean shortStroke =
                    y <= last
                            && (gray[y * width + stem[0] - side] & 255) < strokeThreshold
                            && (gray[y * width + stem[0] + side] & 255) < strokeThreshold;
            if (shortStroke) run++;
            else {
                if (run >= Math.max(2, Math.round(gap * .18f)) && run <= gap * .85f) {
                    float center = y - (run + 1) * .5f;
                    boolean otherHead = false;
                    for (Component other : heads)
                        if (other != head
                                && Math.abs(other.centerX - stem[0]) < gap * 1.5f
                                && center >= other.minY - gap * .25f
                                && center <= other.maxY + gap * .25f) {
                            otherHead = true;
                            break;
                        }
                    int leftKey =
                            otherHead
                                    ? -1
                                    : strokeWingKey(
                                            gray,
                                            width,
                                            height,
                                            stem[0],
                                            center,
                                            gap,
                                            -1,
                                            strokeThreshold);
                    int rightKey =
                            otherHead
                                    ? -1
                                    : strokeWingKey(
                                            gray,
                                            width,
                                            height,
                                            stem[0],
                                            center,
                                            gap,
                                            1,
                                            strokeThreshold);
                    if (leftKey >= 0
                            && rightKey >= 0
                            && !thinStaffRuleThroughStroke(
                                    gray, width, height, stem[0], center, gap, strokeThreshold)) {
                        // A nearby staff rule can seed this same pair of wings twice.
                        long key = ((long) leftKey << 32) | (rightKey & 0xffffffffL);
                        strokeKeys.add(key);
                        if ((stem[1] - center) * stem[2] >= -gap * .2f
                                && (stem[1] - center) * stem[2]
                                        <= Math.max(4, Math.round(gap * 1.85f))) nearKeys.add(key);
                    }
                }
                run = 0;
            }
        }
        return strokeKeys.size() > 3 ? empty : new int[] {strokeKeys.size(), nearKeys.size()};
    }

    /** A finite dark patch in a thin continuous staff rule is not a tremolo wing. */
    private static boolean thinStaffRuleThroughStroke(
            byte[] gray, int width, int height, int x, float center, float gap, int threshold) {
        int side = Math.max(2, Math.round(gap * .38f)), y = Math.round(center);
        if (thickStrokeInk(gray, width, height, x - side, y, gap, threshold)
                && thickStrokeInk(gray, width, height, x + side, y, gap, threshold)) return false;
        int near = Math.round(gap * 2),
                far = Math.round(gap * 4),
                level = Math.min(220, threshold + 20);
        for (int direction : new int[] {-1, 1}) {
            int hits = 0, total = 0;
            for (int d = near; d <= far; d++) {
                int xx = x + direction * d;
                if (xx < 0 || xx >= width) return false;
                total++;
                boolean ink = false;
                for (int yy = Math.max(0, y - 1); yy <= Math.min(height - 1, y + 1); yy++)
                    if ((gray[yy * width + xx] & 255) < level) ink = true;
                if (ink) hits++;
            }
            if (total == 0 || hits < total * .85f) return false;
        }
        return true;
    }

    private static boolean boundedStrokeWing(
            byte[] gray, int width, int height, int stemX, float center, float gap, int side) {
        return strokeWingKey(gray, width, height, stemX, center, gap, side) >= 0;
    }

    private static int strokeWingKey(
            byte[] gray, int width, int height, int stemX, float center, float gap, int side) {
        return strokeWingKey(gray, width, height, stemX, center, gap, side, 170);
    }

    private static int strokeWingKey(
            byte[] gray,
            int width,
            int height,
            int stemX,
            float center,
            float gap,
            int side,
            int threshold) {
        int inner = Math.max(2, Math.round(gap * .32f)), outer = Math.round(gap * 1.5f);
        int top = Math.max(0, Math.round(center - gap)),
                bottom = Math.min(height - 1, Math.round(center + gap));
        int w = outer - inner + 1, h = bottom - top + 1;
        if (w < 2 || h < 2) return -1;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        int size = 0, take = 0;
        int seedX = Math.max(inner, Math.round(gap * .5f)) - inner;
        for (int y = Math.max(top, Math.round(center) - 2);
                y <= Math.min(bottom, Math.round(center) + 2);
                y++) {
            int at = (y - top) * w + seedX;
            if (thickStrokeInk(
                    gray, width, height, stemX + side * (inner + seedX), y, gap, threshold)) {
                seen[at] = true;
                queue[size++] = at;
            }
        }
        if (size == 0) return -1;
        int key = Integer.MAX_VALUE;
        while (take < size) {
            int at = queue[take++], x = at % w, y = at / w;
            if (x == w - 1 || y == 0 || y == h - 1) return -1;
            key = Math.min(key, (top + y) * width + stemX + side * (inner + x));
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int next = ny * w + nx;
                    if (!seen[next]
                            && thickStrokeInk(
                                    gray,
                                    width,
                                    height,
                                    stemX + side * (inner + nx),
                                    top + ny,
                                    gap,
                                    threshold)) {
                        seen[next] = true;
                        queue[size++] = next;
                    }
                }
        }
        return key;
    }

    private static boolean thickStrokeInk(
            byte[] gray, int width, int height, int x, int y, float gap) {
        return thickStrokeInk(gray, width, height, x, y, gap, 170);
    }

    private static boolean thickStrokeInk(
            byte[] gray, int width, int height, int x, int y, float gap, int threshold) {
        if ((gray[y * width + x] & 255) >= threshold) return false;
        int count = 1, radius = Math.max(3, Math.round(gap * .45f));
        for (int dy = 1;
                dy <= radius && y - dy >= 0 && (gray[(y - dy) * width + x] & 255) < threshold;
                dy++) count++;
        for (int dy = 1;
                dy <= radius && y + dy < height && (gray[(y + dy) * width + x] & 255) < threshold;
                dy++) count++;
        return count >= Math.max(3, Math.ceil(gap * .30f));
    }

    /** A numeral/head prediction painted over a stroke can hide that stroke from
     * the original band count. Confirm the actual long beams outside its wings. */
    private static int beamsBeyondTremolo(
            byte[] gray, byte[] labels, int width, int height, Component head, Staff staff) {
        float gap = staff.gap;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        if (stem == null) return 0;
        int a = stem[1] - stem[2] * Math.round(gap * 2.3f),
                b = stem[1] + stem[2] * Math.round(gap * .6f);
        int top = Math.max(0, Math.min(a, b)),
                bottom = Math.min(height - 1, Math.max(a, b)),
                beams = 0;
        for (float offset : new float[] {-1.45f, -1.7f, 1.45f, 1.7f})
            beams =
                    Math.max(
                            beams,
                            thickNonHeadBands(
                                    gray,
                                    labels,
                                    width,
                                    height,
                                    stem[0] + Math.round(gap * offset),
                                    top,
                                    bottom,
                                    staff));
        return Math.min(3, beams);
    }

    /** Reuse the proved local five-rule frame without changing the stored staff or pitch. */
    private static Staff beamStaffFrame(Staff staff, float[] localPitch, int width) {
        if (staff.pitchTrack == null
                && staff.printedPhase
                && Float.isFinite(localPitch[0])
                && Float.isFinite(localPitch[1])
                && Math.abs(localPitch[1] - staff.pitchGap) <= staff.pitchGap * .035f
                && Math.abs(staff.pitchGap - staff.gap) >= staff.gap * .25f) {
            Staff printed =
                    new Staff(
                            staff.pitchBottom - 4 * staff.pitchGap,
                            staff.pitchBottom,
                            staff.pitchGap);
            printed.pitchTrack =
                    StaffPitchTrack.linear(
                            width, staff.pitchBottom, staff.pitchGap, staff.pitchSlope);
            return printed;
        }
        if (staff.pitchTrack != null
                || !Float.isFinite(localPitch[0])
                || !Float.isFinite(localPitch[1])
                || Math.abs(localPitch[0] - staff.bottom) <= staff.gap * .25f
                || Math.abs(localPitch[1] - staff.gap) >= staff.gap * .25f) return staff;
        Staff local = new Staff(localPitch[0] - 4 * localPitch[1], localPitch[0], localPitch[1]);
        local.pitchTrack = StaffPitchTrack.linear(width, localPitch[0], localPitch[1], 0);
        return local;
    }

    private static int detectBeamCount(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            List<Component> heads) {
        int[] voiceStem = opposingVoiceStem(gray, width, height, head, staff.gap, heads);
        if (voiceStem != null)
            return detectBeamCount(
                    labels, gray, width, height, head, staff, false, false, voiceStem);
        int count = detectBeamCount(labels, gray, width, height, head, staff);
        if (count == 0)
            count =
                    CreaseBeamAttachment.count(
                            gray,
                            width,
                            height,
                            staff.gap,
                            staff.top,
                            staff.bottom,
                            head.minX,
                            head.maxX,
                            head.minY,
                            head.maxY,
                            head.centerY);
        if (count == 0 && gray != null && head.maxX - head.minX + 1 > staff.gap * 1.05f)
            count = detectBeamCount(labels, gray, width, height, head, staff, false, true);
        if (count < 2 && gray != null && head.maxX - head.minX + 1 > staff.gap * 1.05f) {
            int[] pale = paleStemToDoubleBeam(labels, gray, width, height, head, staff);
            if (pale != null
                    && fadedDoubleBeamJunction(labels, gray, width, height, head, staff, pale))
                count = 2;
            else if (count == 0
                    && pale != null
                    && rootedPaleFlag(
                            labels,
                            gray,
                            width,
                            height,
                            head,
                            staff.gap,
                            pale[0],
                            pale[1],
                            pale[2] < 0)) count = 1;
        }
        // A reduced neighboring mask does not invalidate three complete printed rails.
        if (count == 2 && pairedPrintedRails(gray, width, height, head, staff, heads, 3)) return 3;
        if (count == 2
                && gray != null
                && head.maxX - head.minX + 1 > staff.gap * 1.05f
                && singleBeamAtPaleEndpoint(labels, gray, width, height, head, staff)) count = 1;
        if (count > 0
                && gray != null
                && head.maxX - head.minX + 1 > staff.gap * 1.05f
                && barePaleStemEndpoint(labels, gray, width, height, head, staff)) count = 0;
        if (gray != null && count < 4 && head.maxX - head.minX + 1 > staff.gap * 1.05f) {
            int threshold =
                    BeamInkThreshold.at(
                                    gray,
                                    width,
                                    height,
                                    Math.round(head.centerX),
                                    Math.round(head.centerY - staff.gap * 5),
                                    Math.round(head.centerY + staff.gap * 5),
                                    staff.gap)
                            + 5;
            int[] own =
                    attachedRawStem(
                            gray,
                            width,
                            height,
                            head,
                            staff.gap,
                            Math.max(1, Math.round(staff.gap * .16f)),
                            threshold);
            if (own != null && Math.abs(own[1] - head.centerY) >= staff.gap * 5.5f) {
                int[] compact =
                        attachedRawStem(
                                gray,
                                width,
                                height,
                                head,
                                staff.gap,
                                Math.max(1, Math.round(staff.gap * .16f)),
                                Math.max(60, Math.round(threshold * .8f)));
                if (compact != null
                        && compact[2] == own[2]
                        && Math.abs(compact[1] - head.centerY) < staff.gap * 5.5f
                        && (own[1] - compact[1]) * own[2] >= staff.gap * .65f) own = compact;
            }
            if (count > 0
                    && OutlinedBeamInk.count(gray, width, height, own, head.centerY, staff.gap) > 0)
                return count;
            if (own != null && Math.abs(own[1] - head.centerY) < staff.gap * 8.5f)
                for (Component other : heads) {
                    if (other == head
                            || Math.abs(other.centerX - head.centerX) < staff.gap * .95f
                            || Math.abs(other.centerX - head.centerX) > staff.gap * 4
                            || Math.abs(other.centerY - head.centerY) > staff.gap * 2
                            || other.maxX - other.minX + 1 <= staff.gap * 1.05f) continue;
                    int[] pair =
                            attachedRawStem(
                                    gray,
                                    width,
                                    height,
                                    other,
                                    staff.gap,
                                    Math.max(1, Math.round(staff.gap * .16f)),
                                    threshold);
                    if (pair == null || Math.abs(pair[1] - other.centerY) > staff.gap * 8.5f)
                        continue;
                    if (WideTripleBeamInk.countFour(gray, width, height, own, pair, staff.gap) == 4)
                        return 4;
                    if (count < 2 && Math.abs(other.centerX - head.centerX) > staff.gap * 3)
                        continue;
                    int paired =
                            Math.abs(other.centerX - head.centerX) > staff.gap * 3
                                    ? WideTripleBeamInk.count(
                                            gray, width, height, own, pair, staff.gap)
                                    : PairedGraceBeamInk.countFullSize(
                                            gray, width, height, own, pair, staff.gap);
                    // Longer shafts need all three independently aligned beam cores.
                    // Keep the ordinary two-beam recovery within its original stem bound.
                    boolean longShaft =
                            Math.abs(own[1] - head.centerY) >= staff.gap * 5.5f
                                    || Math.abs(pair[1] - other.centerY) > staff.gap * 5.5f;
                    if (paired > count && (!longShaft || paired == 3)) return paired;
                }
        }
        if (gray != null
                && head.maxX - head.minX + 1 <= staff.gap * 1.05f
                && head.maxY - head.minY + 1 <= staff.gap * 1.2f) {
            int threshold =
                    BeamInkThreshold.at(
                                    gray,
                                    width,
                                    height,
                                    Math.round(head.centerX),
                                    Math.round(head.centerY - staff.gap * 5),
                                    Math.round(head.centerY + staff.gap * 5),
                                    staff.gap)
                            + 5;
            int[] own =
                    attachedRawStem(
                            gray,
                            width,
                            height,
                            head,
                            staff.gap * .75f,
                            Math.max(1, Math.round(staff.gap * .16f)),
                            threshold);
            if (count != 1 && (own == null || head.centerY - own[1] >= staff.gap * 3.3f)) {
                int localThreshold =
                        BeamInkThreshold.at(
                                        gray,
                                        width,
                                        height,
                                        Math.round(head.centerX),
                                        Math.round(head.centerY - staff.gap * 2.8f),
                                        Math.round(head.centerY - staff.gap * .45f),
                                        staff.gap)
                                + 5;
                if (localThreshold < threshold) {
                    int[] local =
                            attachedRawStem(
                                    gray,
                                    width,
                                    height,
                                    head,
                                    staff.gap * .75f,
                                    Math.max(1, Math.round(staff.gap * .16f)),
                                    localThreshold);
                    if (local != null && local[2] < 0 && head.centerY - local[1] < staff.gap * 3.3f)
                        own = local;
                }
            }
            // Reduced segmentation ovals also occur on long metrical shafts.
            // Recover a third rail only from two attached, complete shafts and
            // the existing five-column proof of three continuous beam cores.
            if (count == 2) {
                int ownLimit =
                        head.centerY < staff.top - staff.gap * 2.5f
                                        || head.centerY > staff.bottom + staff.gap * 2.5f
                                ? 12
                                : 9;
                int[] fullOwn =
                        attachedRawStem(
                                gray,
                                width,
                                height,
                                head,
                                staff.gap,
                                Math.max(1, Math.round(staff.gap * .16f)),
                                threshold,
                                ownLimit);
                if (fullOwn != null && Math.abs(fullOwn[1] - head.centerY) >= staff.gap * 4.8f)
                    for (Component other : heads) {
                        if (other == head
                                || Math.abs(other.centerX - head.centerX) < staff.gap * .95f
                                || Math.abs(other.centerX - head.centerX) > staff.gap * 3
                                || Math.abs(other.centerY - head.centerY) > staff.gap * 2.5f)
                            continue;
                        int pairLimit =
                                other.centerY < staff.top - staff.gap * 2.5f
                                                || other.centerY > staff.bottom + staff.gap * 2.5f
                                        ? 12
                                        : 9;
                        int[] fullPair =
                                attachedRawStem(
                                        gray,
                                        width,
                                        height,
                                        other,
                                        staff.gap,
                                        Math.max(1, Math.round(staff.gap * .16f)),
                                        threshold,
                                        pairLimit);
                        if (PairedGraceBeamInk.countFullSize(
                                        gray, width, height, fullOwn, fullPair, staff.gap)
                                == 3) return 3;
                    }
            }
            boolean pairedGrace = false;
            if (own != null && Math.abs(own[1] - head.centerY) < staff.gap * 4.8f)
                for (Component other : heads) {
                    if (other == head
                            || Math.abs(other.centerX - head.centerX) < staff.gap * .95f
                            || Math.abs(other.centerX - head.centerX) > staff.gap * 3
                            || Math.abs(other.centerY - head.centerY) > staff.gap * 1.3f
                            || other.maxX - other.minX + 1 > staff.gap * 1.05f
                            || other.maxY - other.minY + 1 > staff.gap * 1.2f) continue;
                    // A faint companion stem may fail tracing. Its small oval still
                    // rules out treating a shared beam corner as a solitary slash.
                    pairedGrace = true;
                    int[] pair =
                            attachedRawStem(
                                    gray,
                                    width,
                                    height,
                                    other,
                                    staff.gap * .75f,
                                    Math.max(1, Math.round(staff.gap * .16f)),
                                    threshold);
                    if (pair == null || Math.abs(pair[1] - other.centerY) > staff.gap * 4.8f)
                        continue;
                    int paired =
                            PairedGraceBeamInk.count(gray, width, height, own, pair, staff.gap);
                    // A compact head mask can belong to an ordinary long-stemmed run.
                    // Three full-size cores at five columns outrank a compact two-core fit.
                    if (paired > 0 && count >= 2) {
                        int ownLimit =
                                head.centerY < staff.top - staff.gap * 2.5f
                                                || head.centerY > staff.bottom + staff.gap * 2.5f
                                        ? 12
                                        : 9;
                        int pairLimit =
                                other.centerY < staff.top - staff.gap * 2.5f
                                                || other.centerY > staff.bottom + staff.gap * 2.5f
                                        ? 12
                                        : 9;
                        int[] fullOwn =
                                attachedRawStem(
                                        gray,
                                        width,
                                        height,
                                        head,
                                        staff.gap,
                                        Math.max(1, Math.round(staff.gap * .16f)),
                                        threshold,
                                        ownLimit);
                        int[] fullPair =
                                attachedRawStem(
                                        gray,
                                        width,
                                        height,
                                        other,
                                        staff.gap,
                                        Math.max(1, Math.round(staff.gap * .16f)),
                                        threshold,
                                        pairLimit);
                        if (PairedGraceBeamInk.countFullSize(
                                        gray, width, height, fullOwn, fullPair, staff.gap)
                                == 3) return 3;
                    }
                    if (paired > 0) return paired;
                }
            if (!pairedGrace
                    && own != null
                    && own[2] < 0
                    && head.centerY - own[1] < staff.gap * 3.3f
                    && SlashedGraceFlagInk.count(
                                    gray,
                                    width,
                                    height,
                                    head.centerX,
                                    head.centerY,
                                    own[0],
                                    staff.gap)
                            == 1) {
                // A thick straight rail can imitate both miniature flag diagonals.
                // Preserve an established double beam only with the existing complete
                // attached-rail proof and its independent curved-flag rejection.
                int[] doubleBeam =
                        count >= 2
                                ? paleStemToDoubleBeam(labels, gray, width, height, head, staff)
                                : null;
                boolean straightPair =
                        count == 2
                                && doubleBeam == null
                                && pairedPrintedRails(gray, width, height, head, staff, heads, 2);
                if (!straightPair
                        && (doubleBeam == null
                                || rootedPaleFlag(
                                        labels,
                                        gray,
                                        width,
                                        height,
                                        head,
                                        staff.gap,
                                        doubleBeam[0],
                                        doubleBeam[1],
                                        doubleBeam[2] < 0))) return 1;
            }
        }
        if (gray != null && !hasOpenCenter(labels, gray, width, height, head, staff.gap)) {
            int[] own = attachedRawStem(gray, width, height, head, staff.gap);
            if (own != null
                    && !thinShaftRun(
                            gray, width, height, head, own[0], own[1], own[2], staff.gap, 170))
                for (Component other : heads) {
                    if (other == head
                            || Math.abs(other.centerX - head.centerX) > staff.gap * .35f
                            || Math.abs(other.centerY - head.centerY) < staff.gap * .7f
                            || Math.abs(other.centerY - head.centerY) > staff.gap * 2.5f
                            || hasOpenCenter(labels, gray, width, height, other, staff.gap))
                        continue;
                    int[] shared = attachedRawStem(gray, width, height, other, staff.gap);
                    if (shared == null
                            || shared[2] == own[2]
                            || shared[0] < head.minX - staff.gap * .2f
                            || shared[0] > head.maxX + staff.gap * .2f
                            || (shared[1] - head.centerY) * shared[2] < staff.gap * 2.3f
                            || !thinShaftRun(
                                    gray, width, height, head, shared[0], shared[1], shared[2],
                                    staff.gap, 170)) continue;
                    int common = detectBeamCount(labels, gray, width, height, other, staff);
                    if (common != count) return common;
                }
        }
        // Uneven paper may hide one flank of a faint shaft. Established
        // counts and complete paired rails take priority over this recovery.
        if (count == 0 && gray != null && head.maxX - head.minX + 1 > staff.gap * 1.05f) {
            int[] gradient =
                    paleStemToSupportedBeamAtThreshold(
                            labels, gray, width, height, head, staff, false, 245, true);
            if (gradient != null
                    && fadedDoubleBeamJunction(labels, gray, width, height, head, staff, gradient))
                return 2;
        }
        if (count < 2 || gray == null) return count;
        int[] stem = attachedRawStem(gray, width, height, head, staff.gap);
        if (stem == null) return count;
        for (var other : heads) {
            float distance = (head.centerY - other.centerY) * stem[2];
            if (other == head
                    || distance < staff.gap
                    || distance > staff.gap * 6
                    || Math.abs(other.centerX - head.centerX) > staff.gap * .5f) continue;
            int[] anchor = attachedRawStem(gray, width, height, other, staff.gap);
            if (anchor == null
                    || anchor[2] != stem[2]
                    || Math.abs(anchor[0] - stem[0]) > staff.gap * .2f
                    || Math.abs(anchor[1] - stem[1]) > staff.gap * .3f) continue;
            int common = detectBeamCount(labels, gray, width, height, other, staff);
            if (common == count - 1
                    && detectBeamCount(labels, gray, width, height, head, staff, true) == common)
                return common;
        }
        return count;
    }

    /** Five-column full-size rail evidence between two independently attached shafts. */
    private static boolean pairedPrintedRails(
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            List<Component> heads,
            int expected) {
        if (gray == null || heads == null) return false;
        float gap = staff.gap;
        int threshold =
                BeamInkThreshold.at(
                                gray,
                                width,
                                height,
                                Math.round(head.centerX),
                                Math.round(head.centerY - gap * 5),
                                Math.round(head.centerY + gap * 5),
                                gap)
                        + 5;
        int ownLimit =
                head.centerY < staff.top - gap * 2.5f || head.centerY > staff.bottom + gap * 2.5f
                        ? 12
                        : 9;
        int[] own =
                attachedRawStem(
                        gray,
                        width,
                        height,
                        head,
                        gap,
                        Math.max(1, Math.round(gap * .16f)),
                        threshold,
                        ownLimit);
        if (own == null) return false;
        for (Component other : heads) {
            if (other == head
                    || Math.abs(other.centerX - head.centerX) < gap * .95f
                    || Math.abs(other.centerX - head.centerX) > gap * 5
                    || Math.abs(other.centerY - head.centerY) > gap * 5
                    || expected == 2 && other.maxX - other.minX + 1 <= gap * 1.05f) continue;
            int pairLimit =
                    other.centerY < staff.top - gap * 2.5f
                                    || other.centerY > staff.bottom + gap * 2.5f
                            ? 12
                            : 9;
            int[] pair =
                    attachedRawStem(
                            gray,
                            width,
                            height,
                            other,
                            gap,
                            Math.max(1, Math.round(gap * .16f)),
                            threshold,
                            pairLimit);
            if (PairedGraceBeamInk.countPrintedSize(gray, width, height, own, pair, gap)
                    == expected) return true;
        }
        return false;
    }

    private static int detectBeamCount(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        return detectBeamCount(labels, gray, width, height, head, staff, false);
    }

    private static int detectBeamCount(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            boolean corroborate) {
        return detectBeamCount(labels, gray, width, height, head, staff, corroborate, false);
    }

    private static int detectBeamCount(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            boolean corroborate,
            boolean allowSinglePale) {
        return detectBeamCount(
                labels, gray, width, height, head, staff, corroborate, allowSinglePale, null);
    }

    private static int detectBeamCount(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            boolean corroborate,
            boolean allowSinglePale,
            int[] voiceStem) {
        float gap = staff.gap;
        // A stem attaches to this oval's edge. A wider window can borrow the preceding
        // triplet's stem and assign its beams to the following ordinary quarter note.
        boolean smallHead =
                head.maxX - head.minX + 1 < gap * .95f || head.maxY - head.minY + 1 < gap * .70f;
        float stemSearchPadding = smallHead ? .8f : .28f;
        int searchLeft = Math.max(0, Math.round(head.minX - gap * stemSearchPadding));
        int searchRight = Math.min(width - 1, Math.round(head.maxX + gap * stemSearchPadding));
        int aboveTop = Math.max(0, Math.round(head.centerY - gap * 4.8f));
        int aboveBottom = Math.max(0, Math.round(head.centerY - gap * .15f));
        int belowTop = Math.min(height - 1, Math.round(head.centerY + gap * .15f));
        int belowBottom = Math.min(height - 1, Math.round(head.centerY + gap * 4.8f));
        int bestX = Math.round(head.centerX), bestAbove = 0, bestBelow = 0;
        for (int x = searchLeft; x <= searchRight; x++) {
            int above = countVertical(labels, width, x, aboveTop, aboveBottom);
            int below = countVertical(labels, width, x, belowTop, belowBottom);
            if (Math.max(above, below) > Math.max(bestAbove, bestBelow)) {
                bestX = x;
                bestAbove = above;
                bestBelow = below;
            }
        }
        boolean upward = bestAbove >= bestBelow;
        int[] faintStem = null;
        if (Math.max(bestAbove, bestBelow) < Math.max(3, Math.round(gap * .75f))) {
            if (!smallHead)
                faintStem = fadedStemToDarkBeam(labels, gray, width, height, head, staff);
            if (!smallHead && faintStem == null)
                faintStem =
                        paleStemToSupportedBeam(
                                labels, gray, width, height, head, staff, allowSinglePale);
            if (faintStem == null) return 0;
        }
        int stemEnd =
                findStemEnd(
                        labels,
                        width,
                        bestX,
                        upward,
                        upward ? aboveTop : belowTop,
                        upward ? aboveBottom : belowBottom);
        // Trace the attached ink, not a fixed-height semantic window. In a wide chord the
        // upper head lies inside that window and used to masquerade as the lower head's beam.
        int stemThreshold =
                smallHead
                        ? 170
                        : BeamInkThreshold.at(
                                        gray,
                                        width,
                                        height,
                                        Math.round(head.centerX),
                                        Math.round(head.centerY - gap * 5),
                                        Math.round(head.centerY + gap * 5),
                                        gap)
                                + 5;
        int[] attached =
                faintStem != null
                        ? faintStem
                        : attachedRawStem(
                                gray,
                                width,
                                height,
                                head,
                                gap,
                                Math.max(1, Math.round(gap * .16f)),
                                stemThreshold,
                                head.centerY < staff.top - gap * 2.5f
                                                || head.centerY > staff.bottom + gap * 2.5f
                                        ? 12
                                        : 9);
        if (attached == null && !smallHead)
            attached = fadedStemToDarkBeam(labels, gray, width, height, head, staff);
        if (attached == null && !smallHead)
            attached =
                    paleStemToSupportedBeam(
                            labels, gray, width, height, head, staff, allowSinglePale);
        if (!smallHead)
            attached =
                    CappedStemBeam.extend(
                            gray, width, height, attached, head.centerY, gap, stemThreshold);
        attached = stemBelowDetachedBow(gray, width, height, head, gap, attached);
        attached = stemBeforePaperTail(labels, gray, width, height, head, gap, attached);
        attached = stemToReturningFlag(labels, gray, width, height, head, gap, attached);
        if (voiceStem != null) attached = voiceStem;
        if (attached != null) {
            bestX = attached[0];
            stemEnd = attached[1];
            upward = attached[2] < 0;
        }
        // Secondary beams sit just inside the stem endpoint. Searching symmetrically toward
        // the notehead reaches ordinary staff lines and makes every note look beamed.
        int outside = Math.max(2, Math.round(gap * .50f));
        int inside = Math.max(4, Math.round(gap * 1.85f));
        int top = Math.max(0, stemEnd - (upward ? outside : inside));
        int bottom = Math.min(height - 1, stemEnd + (upward ? inside : outside));
        int horizontalLeft = Math.max(0, Math.round(bestX - gap * 4.2f));
        int horizontalRight = Math.min(width - 1, Math.round(bestX + gap * 4.2f));
        // A beam or flag must extend far enough away from its stem to establish horizontal
        // topology. Short dark corners left where one thick beam crosses a semantic staff row
        // otherwise look like an extra beam after that staff row is suppressed.
        int minimumHorizontal = Math.max(4, (int) Math.ceil(gap * .85f));
        // HOMR often leaves a narrow background seam where a beam meets its stem. Allow that
        // local seam, but stay well below the roughly 3-4 staff-gap distance to the next stem.
        int stemTolerance = Math.max(2, Math.round(gap * 1.10f));
        int allowedRunGap = Math.max(1, Math.round(gap * .22f));
        int beams = 0;
        boolean inBand = false;
        for (int y = top; y <= bottom; y++) {
            // A beam belongs to this note only when its horizontal run reaches this note's
            // own stem. Counting all ink in a wide window assigns a neighbour's partial
            // 16th/32nd beams to the wrong note (the Boulevard regression).
            boolean staffLine =
                    gray != null
                            && rowLabelCount(
                                            labels,
                                            width,
                                            y,
                                            horizontalLeft,
                                            horizontalRight,
                                            OmrMeasurePostProcessor.STAFF)
                                    >= minimumHorizontal;
            int run =
                    gray == null
                            ? horizontalRunAtStem(
                                    labels,
                                    width,
                                    y,
                                    horizontalLeft,
                                    horizontalRight,
                                    bestX,
                                    stemTolerance,
                                    allowedRunGap)
                            : darkRunAtStem(
                                    gray,
                                    width,
                                    y,
                                    horizontalLeft,
                                    horizontalRight,
                                    bestX,
                                    Math.max(2, Math.round(gap * .28f)),
                                    1);
            // A staff-labelled row can still be white between two genuine beams. Only bridge
            // the semantic occlusion when raw ink really spans it; otherwise end the band.
            if (staffLine && !smallHead && run >= minimumHorizontal) continue;
            if (staffLine) run = 0;
            boolean band = run >= minimumHorizontal;
            if (band && !inBand) {
                beams++;
                inBand = true;
            } else if (!band) inBand = false;
        }
        // At this render resolution a thick 32nd beam can be segmented into four dark bands.
        // Four-band optical results are therefore not safe evidence of a true 64th note.
        if (beams == 0
                && !smallHead
                && gray != null
                && hasCurvedFlag(labels, gray, width, height, head, gap, bestX, stemEnd, upward))
            beams = 1;
        // A thin staff line through the white gap can fuse two sloped beams at the stem.
        // Recover only when thick ink bands on BOTH sides independently prove two beams;
        // this cannot borrow a neighbour's one-sided partial beam.
        if (beams == 1 && gray != null && !smallHead) {
            boolean leftProof = false, rightProof = false;
            for (float distance : new float[] {.65f, 1f, 1.35f}) {
                int offset = Math.max(3, Math.round(gap * distance));
                leftProof |=
                        thickBeamBands(gray, width, height, bestX - offset, top, bottom, gap) == 2;
                rightProof |=
                        thickBeamBands(gray, width, height, bestX + offset, top, bottom, gap) == 2;
            }
            if (leftProof && rightProof) beams = 2;
        }
        if (attached != null && gray != null) {
            int thick = 0, innerThick = 0, outerLeft = 0, outerRight = 0;
            for (float distance : new float[] {-.65f, -.4f, .4f, .65f}) {
                int x = bestX + Math.round(distance * gap);
                int near = Math.max(0, stemEnd - (upward ? Math.round(gap * .2f) : inside));
                int far = Math.min(height - 1, stemEnd + (upward ? inside : Math.round(gap * .2f)));
                // Very long stems can stop at the inner beam edge. Complete that
                // clipped band, but never reach across
                // white space into a separate slur or flag return.
                if (Math.abs(stemEnd - head.centerY) > gap * 7) {
                    if (upward)
                        near =
                                completeBeamEdge(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        near,
                                        -1,
                                        Math.max(0, stemEnd - outside));
                    else
                        far =
                                completeBeamEdge(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        far,
                                        1,
                                        Math.min(height - 1, stemEnd + outside));
                }
                // Do not count any head on the chord's attached stem as a beam.
                int innerX =
                        bestX
                                + Math.round(
                                        Math.copySign(
                                                        Math.abs(distance) < .5f ? .2f : .4f,
                                                        distance)
                                                * gap);
                int count =
                        corroborate
                                ? supportedBeamBands(
                                        gray,
                                        labels,
                                        width,
                                        height,
                                        x,
                                        near,
                                        far,
                                        staff,
                                        innerX,
                                        !smallHead)
                                : thickNonHeadBands(
                                        gray,
                                        labels,
                                        width,
                                        height,
                                        x,
                                        near,
                                        far,
                                        staff,
                                        innerX,
                                        !smallHead);
                if (count > 1
                        && Math.abs(stemEnd - head.centerY) < gap * 7
                        && !hasCurvedFlag(
                                labels, gray, width, height, head, gap, bestX, stemEnd, upward))
                    count =
                            Math.min(
                                    count,
                                    thickNonHeadBands(
                                            gray,
                                            labels,
                                            width,
                                            height,
                                            x,
                                            near,
                                            far,
                                            staff,
                                            bestX + (distance < 0 ? -2 : 2),
                                            !smallHead));
                thick = Math.max(thick, count);
                if (Math.abs(distance) < .5f) innerThick = Math.max(innerThick, count);
                else if (distance < 0) outerLeft = count;
                else outerRight = count;
            }
            // A detached accent is not an additional beam; retain independently curved flags.
            if (upward
                    && outerLeft == 1
                    && outerRight == 1
                    && thick > 1
                    && caretAboveBeam(gray, width, height, head.centerX, stemEnd, gap)
                    && !hasCurvedFlag(
                            labels, gray, width, height, head, gap, bestX, stemEnd, upward))
                thick = 1;
            if (thick == 1 && innerThick == 0) thick = 0;
            if (thick == 0
                    && hasCurvedFlag(
                            labels, gray, width, height, head, gap, bestX, stemEnd, upward))
                thick = 1;
            // The returning edge of one curved flag can intersect an outer column twice.
            // Multiple flags also need separate thick roots close to their shared stem.
            // Very long traces can follow dark paper beyond the actual stem.
            // They do not establish a trustworthy endpoint for counting flag roots.
            if (thick > 1
                    && Math.abs(stemEnd - head.centerY) < gap * 7
                    && hasCurvedFlag(
                            labels, gray, width, height, head, gap, bestX, stemEnd, upward)) {
                int near = Math.max(0, stemEnd - (upward ? Math.round(gap * .2f) : inside));
                int far = Math.min(height - 1, stemEnd + (upward ? inside : Math.round(gap * .2f)));
                int roots =
                        thickNonHeadBands(
                                gray,
                                labels,
                                width,
                                height,
                                bestX + Math.round(gap * .4f),
                                near,
                                far,
                                staff,
                                bestX + Math.round(gap * .4f),
                                !smallHead);
                if (roots == 1) thick = 1;
            }
            if (thick == 1
                    && Math.abs(stemEnd - head.centerY) < gap * 7
                    && hasCurvedFlag(
                            labels, gray, width, height, head, gap, bestX, stemEnd, upward)) {
                int rootSpan = Math.max(inside, Math.round(gap * 2.1f));
                int near = Math.max(0, stemEnd - (upward ? Math.round(gap * .2f) : rootSpan));
                int far =
                        Math.min(height - 1, stemEnd + (upward ? rootSpan : Math.round(gap * .2f)));
                // Narrow curved flags can return before the usual outer sample.
                // Two distinct thick roots at both inner columns prove a pair;
                // one returning flag has only one root next to the shaft.
                if (adjacentFlagRoots(
                        gray, labels, width, height, bestX, near, far, staff, !smallHead))
                    thick = 2;
                else if (!smallHead
                        && recenteredFlagRoots(
                                gray, labels, width, height, head, staff, bestX, stemEnd, upward,
                                near, far)) thick = 2;
            }
            // Three curved flags extend farther toward the head than the two-beam
            // window. Require three thick roots beside the shaft in adjacent columns;
            // a returning eighth flag or a detached curve cannot establish this count.
            if (thick <= 2
                    && Math.abs(stemEnd - head.centerY) < gap * 7
                    && hasCurvedFlag(
                            labels, gray, width, height, head, gap, bestX, stemEnd, upward)) {
                int span = Math.round(gap * 3.2f);
                int near = Math.max(0, stemEnd - (upward ? Math.round(gap * .2f) : span));
                int far = Math.min(height - 1, stemEnd + (upward ? span : Math.round(gap * .2f)));
                if (adjacentTripleFlagRoots(
                        gray, labels, width, height, bestX, near, far, staff, !smallHead))
                    thick = 3;
            }
            if (thick == 1
                    && ParallelBeamTip.matches(
                            gray,
                            width,
                            height,
                            head.centerX,
                            stemEnd + (upward ? 1 : -1) * gap * .55f,
                            gap)) thick = 2;
            if (thick <= 1 && !smallHead) {
                int[] pale =
                        thick == 0
                                ? fadedStemToDarkBeam(labels, gray, width, height, head, staff)
                                : null;
                if (pale == null)
                    pale =
                            paleStemToSupportedBeam(
                                    labels, gray, width, height, head, staff, allowSinglePale);
                if (pale != null
                        && !PaleBeamRecovery.crossesBlankCap(
                                gray, width, height, attached, pale, gap)
                        && pale[2] == attached[2]
                        && (pale[1] - attached[1]) * pale[2] >= -gap * .2f
                        && (thick == 0
                                || Math.abs(pale[0] - attached[0]) <= gap * .3f
                                        && (pale[1] - attached[1]) * pale[2] > gap * .2f
                                        && (pale[1] - attached[1]) * pale[2] <= gap * 1.2f
                                        && !hasCurvedFlag(
                                                labels, gray, width, height, head, gap, bestX,
                                                stemEnd, upward))) {
                    boolean paleUp = pale[2] < 0;
                    int a = Math.max(0, pale[1] - Math.round(gap * (paleUp ? .2f : 1.85f)));
                    int b =
                            Math.min(
                                    height - 1, pale[1] + Math.round(gap * (paleUp ? 1.85f : .2f)));
                    for (int side : new int[] {-1, 1}) {
                        int inner = pale[0] + side * Math.round(gap * .4f),
                                outer = pale[0] + side * Math.round(gap * .65f);
                        thick =
                                Math.max(
                                        thick,
                                        Math.min(
                                                thickNonHeadBands(
                                                        gray, labels, width, height, inner, a, b,
                                                        staff),
                                                thickNonHeadBands(
                                                        gray, labels, width, height, outer, a, b,
                                                        staff, inner)));
                    }
                    if (thick == 0
                            && hasCurvedFlag(
                                    labels, gray, width, height, head, gap, pale[0], pale[1],
                                    paleUp)) thick = 1;
                }
            }
            if (!smallHead) {
                int outlined =
                        OutlinedBeamInk.count(gray, width, height, attached, head.centerY, gap);
                if (outlined > 0) return outlined;
            }
            return Math.min(3, thick);
        }
        return Math.min(3, beams);
    }

    private static boolean recenteredFlagRoots(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            Component head,
            Staff staff,
            int stemX,
            int end,
            boolean upward,
            int near,
            int far) {
        float gap = staff.gap;
        if (thinShaftRun(gray, width, height, head, stemX, end, upward ? -1 : 1, gap, 170))
            return false;
        for (int shift = 1; shift <= Math.max(1, Math.round(gap * .22f)); shift++)
            for (int side : new int[] {-1, 1}) {
                int axis = stemX + shift * side, edge = upward ? head.maxX : head.minX;
                if (Math.abs(axis - edge) > gap * .25f
                        || !thinShaftRun(
                                gray, width, height, head, axis, end, upward ? -1 : 1, gap, 170)
                        || !hasCurvedFlag(
                                labels, gray, width, height, head, gap, axis, end, upward))
                    continue;
                if (adjacentFlagRoots(gray, labels, width, height, axis, near, far, staff, true))
                    return true;
            }
        return false;
    }

    private static boolean adjacentFlagRoots(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int stemX,
            int near,
            int far,
            Staff staff,
            boolean adaptive) {
        int a = stemX + Math.max(2, Math.round(staff.gap * .12f));
        int b = stemX + Math.max(3, Math.round(staff.gap * .28f));
        int consecutive = 0;
        for (int column = a; column <= b; column++) {
            if (thickNonHeadBands(
                            gray, labels, width, height, column, near, far, staff, column, adaptive)
                    == 2) consecutive++;
            else consecutive = 0;
            if (consecutive >= 2) return true;
        }
        return false;
    }

    private static boolean adjacentTripleFlagRoots(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int stemX,
            int near,
            int far,
            Staff staff,
            boolean adaptive) {
        int a = stemX + Math.max(2, Math.round(staff.gap * .12f));
        int b = stemX + Math.max(3, Math.round(staff.gap * .28f));
        int consecutive = 0;
        for (int column = a; column <= b; column++) {
            if (thickNonHeadBands(
                            gray, labels, width, height, column, near, far, staff, column, adaptive)
                    == 3) consecutive++;
            else consecutive = 0;
            if (consecutive >= 2) return true;
        }
        return false;
    }

    // Retain the already-published displaced whole-note and accent safeguards.
    private static List<Component> splitWholeSeconds(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        if (gray == null || staff == null) return List.of();
        float gap = staff.pitchGap, w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1;
        if (w < gap * 3.1f || w > gap * 4.2f || h < gap * 1.15f || h > gap * 1.9f) return List.of();
        int middle = (head.minX + head.maxX) / 2;
        Component a = horizontalHeadSlice(labels, width, head, head.minX, middle);
        Component b = horizontalHeadSlice(labels, width, head, middle + 1, head.maxX);
        // A few pixels from the neighbouring oval can cross the horizontal cut.
        // Trim only that thin tail around each half's independently weighted centre.
        if (a != null && a.maxY - a.minY + 1 > gap * 1.3f)
            a =
                    componentSlice(
                            labels,
                            width,
                            a,
                            Math.max(a.minY, Math.round(a.centerY - gap * .6f)),
                            Math.min(a.maxY, Math.round(a.centerY + gap * .6f)));
        if (b != null && b.maxY - b.minY + 1 > gap * 1.3f)
            b =
                    componentSlice(
                            labels,
                            width,
                            b,
                            Math.max(b.minY, Math.round(b.centerY - gap * .6f)),
                            Math.min(b.maxY, Math.round(b.centerY + gap * .6f)));
        if (a == null
                || b == null
                || Math.abs(a.centerY - b.centerY) < gap * .3f
                || Math.abs(a.centerY - b.centerY) > gap * .75f) return List.of();
        for (Component part : List.of(a, b))
            if (!plausibleHead(part, gap)
                    || part.maxY - part.minY + 1 < gap * .65f
                    || part.maxY - part.minY + 1 > gap * 1.4f
                    || !hasOpenCenter(labels, gray, width, height, part, gap)
                    || hasAttachedStem(labels, width, height, part, gap)) return List.of();
        return List.of(a, b);
    }

    private static Component halfChordDotAnchor(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Component> heads,
            float gap) {
        if (gray == null) return head;
        int edge = head.maxX;
        for (Component other : heads) {
            float dx = other.centerX - head.centerX, dy = Math.abs(other.centerY - head.centerY);
            if (dx < gap * .9f
                    || dx > gap * 1.8f
                    || dy < gap * .3f
                    || dy > gap * .75f
                    || !hasOpenCenter(labels, gray, width, height, other, gap)) continue;
            int margin = Math.max(1, Math.round(gap * .15f));
            int left = Math.max(head.minX, other.minX) - margin,
                    right = Math.min(head.maxX, other.maxX) + margin;
            boolean shared = false;
            for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++)
                for (int direction : new int[] {-1, 1}) {
                    int yEdge =
                            direction < 0
                                    ? Math.min(head.minY, other.minY)
                                    : Math.max(head.maxY, other.maxY);
                    int ink = 0, samples = 0;
                    for (int k = 1; k <= Math.round(gap * 1.8f); k++) {
                        int y = yEdge + direction * k;
                        if (y < 0 || y >= height) break;
                        samples++;
                        if ((gray[y * width + x] & 255) < 165) ink++;
                    }
                    if (samples >= gap * 1.6f && ink >= samples * .92f) shared = true;
                }
            if (shared) edge = Math.max(edge, other.maxX);
        }
        return edge == head.maxX
                ? head
                : new Component(
                        head.area,
                        head.minX,
                        edge,
                        head.minY,
                        head.maxY,
                        head.centerX,
                        head.centerY);
    }

    private static Component wholeChordDotAnchor(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            List<Component> heads,
            float gap) {
        if (gray == null) return head;
        int edge = head.maxX;
        for (Component column : heads) {
            if (Math.abs(column.centerX - head.centerX) > gap * .3f
                    || Math.abs(column.centerY - head.centerY) > gap * 3.2f
                    || !hasOpenCenter(labels, gray, width, height, column, gap)) continue;
            for (Component other : heads) {
                float dx = other.centerX - column.centerX,
                        dy = Math.abs(other.centerY - column.centerY);
                if (dx < gap * 1.3f
                        || dx > gap * 2.2f
                        || dy < gap * .3f
                        || dy > gap * .75f
                        || !hasOpenCenter(labels, gray, width, height, other, gap)
                        || hasAttachedStem(labels, width, height, other, gap)) continue;
                edge = Math.max(edge, other.maxX);
            }
        }
        return edge == head.maxX
                ? head
                : new Component(
                        head.area,
                        head.minX,
                        edge,
                        head.minY,
                        head.maxY,
                        head.centerX,
                        head.centerY);
    }

    private static boolean caretAboveBeam(
            byte[] gray, int width, int height, float center, int end, float gap) {
        for (int dx = -2; dx <= 2; dx++)
            for (float rise : new float[] {.6f, .8f, 1f})
                for (float slope : new float[] {.45f, .6f, .75f}) {
                    int cx = Math.round(center) + dx,
                            top = Math.round(end - gap * rise),
                            hits = 0,
                            open = 0;
                    for (int step = 2; step <= 8; step++) {
                        int y = top + Math.round(gap * rise * step / 8f),
                                offset = Math.max(2, Math.round((y - top) * slope));
                        if (y < 0 || y >= height || cx - offset < 1 || cx + offset >= width - 1)
                            continue;
                        int left = 255, right = 255;
                        for (int pad = -1; pad <= 1; pad++) {
                            left = Math.min(left, gray[y * width + cx - offset + pad] & 255);
                            right = Math.min(right, gray[y * width + cx + offset + pad] & 255);
                        }
                        if (left < 165 && right < 165) {
                            hits++;
                            if (offset >= 3
                                    && (gray[y * width + cx] & 255) > Math.max(left, right) + 35)
                                open++;
                        }
                    }
                    if (hits >= 6 && open >= 3) return true;
                }
        return false;
    }

    /** A thin tie may touch a dot without erasing its independently round body.
     * Require a dark inner disk and clear outer ring except for at most two
     * narrow connections. A bare slur cannot supply the complete inner disk. */
    private static List<Component> tieTouchingDotCores(
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            List<Component> neighboringHeads) {
        List<Component> result = new ArrayList<>();
        if (head.area < gap * gap * .5f || head.maxX - head.minX + 1 < gap * .85f) return result;
        // Round the independently proved disk radius; flooring fractional spacing
        // can let a thick diagonal slur pass all eight inner probes. Keep the
        // crop inside its fractional bound so the connected tail does not widen it.
        int inner = Math.max(2, Math.round(gap * .18f));
        int outer = Math.max(inner + 2, Math.round(gap * .43f));
        int box = Math.max(inner + 1, (int) Math.floor(gap * .28f));
        for (int cy = Math.max(outer, Math.round(head.centerY - gap * .6f));
                cy <= Math.min(height - 1 - outer, Math.round(head.centerY + gap * .6f));
                cy++) {
            for (int cx = Math.max(outer, head.maxX + Math.round(gap * .25f));
                    cx <= Math.min(width - 1 - outer, head.maxX + Math.round(gap * 1.55f));
                    cx++) {
                if ((gray[cy * width + cx] & 255) >= 70) continue;
                boolean onHead = false;
                for (Component other : neighboringHeads)
                    if (other != head
                            && cx >= other.minX - gap * .15f
                            && cx <= other.maxX + gap * .15f
                            && cy >= other.minY - gap * .15f
                            && cy <= other.maxY + gap * .15f) {
                        onHead = true;
                        break;
                    }
                if (onHead) continue;
                int darkInner = 0, clearOuter = 0;
                for (int direction = 0; direction < 8; direction++) {
                    double angle = direction * Math.PI / 4;
                    int ix = cx + (int) Math.round(Math.cos(angle) * inner),
                            iy = cy + (int) Math.round(Math.sin(angle) * inner);
                    int ox = cx + (int) Math.round(Math.cos(angle) * outer),
                            oy = cy + (int) Math.round(Math.sin(angle) * outer);
                    if ((gray[iy * width + ix] & 255) < 70) darkInner++;
                    if ((gray[oy * width + ox] & 255) > 200) clearOuter++;
                }
                if (darkInner != 8
                        || clearOuter < 6
                        || !shallowDotConnection(gray, width, height, cx, cy, gap, inner)
                        || steepDotConnection(gray, width, height, cx, cy, gap, inner)) continue;
                int area = 0, minX = cx + box, maxX = cx - box, minY = cy + box, maxY = cy - box;
                long sx = 0, sy = 0;
                for (int y = cy - box; y <= cy + box; y++)
                    for (int x = cx - box; x <= cx + box; x++)
                        if ((gray[y * width + x] & 255) < 70) {
                            area++;
                            sx += x;
                            sy += y;
                            minX = Math.min(minX, x);
                            maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y);
                        }
                if (area > 0)
                    result.add(
                            new Component(
                                    area,
                                    minX,
                                    maxX,
                                    minY,
                                    maxY,
                                    sx / (float) area,
                                    sy / (float) area));
            }
        }
        return result;
    }

    /** A bulb with a connected vertical tail belongs to a rest or flag. */
    private static boolean steepDotConnection(
            byte[] gray, int width, int height, int cx, int cy, float gap, int inner) {
        int distance = Math.round(gap * 1.15f),
                left = cx - inner,
                right = cx + Math.round(gap * .85f);
        int top = cy - distance, bottom = cy + distance;
        if (left < 0 || right >= width || top < 0 || bottom >= height) return false;
        int localWidth = right - left + 1, size = localWidth * (bottom - top + 1);
        boolean[] seen = new boolean[size];
        int[] queue = new int[size];
        int take = 0, count = 1;
        queue[0] = (cy - top) * localWidth + cx - left;
        seen[queue[0]] = true;
        while (take < count) {
            int at = queue[take++], x = left + at % localWidth, y = top + at / localWidth;
            if (y == top || y == bottom) return true;
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < left || nx > right || ny < top || ny > bottom) continue;
                    int next = (ny - top) * localWidth + nx - left;
                    if (!seen[next] && (gray[ny * width + nx] & 255) < 135) {
                        seen[next] = true;
                        queue[count++] = next;
                    }
                }
        }
        return false;
    }

    /** A tie leaves the disk sideways. A rest bulb's steep tail is not a tie. */
    private static boolean shallowDotConnection(
            byte[] gray, int width, int height, int cx, int cy, float gap, int inner) {
        int radius = Math.max(inner, Math.round(gap * .45f)), distance = Math.round(gap * 1.15f);
        if (cy - radius < 0 || cy + radius >= height) return false;
        for (int direction : new int[] {-1, 1}) {
            if (cx + direction * distance < 0 || cx + direction * distance >= width) continue;
            boolean[] reachable = new boolean[radius * 2 + 1];
            for (int dy = -inner; dy <= inner; dy++) reachable[radius + dy] = true;
            boolean complete = true;
            int narrow = 0, samples = 0;
            for (int step = inner; step <= distance; step++) {
                boolean[] next = new boolean[reachable.length];
                boolean any = false;
                int x = cx + direction * step;
                for (int i = 0; i < reachable.length; i++)
                    if ((reachable[i]
                                    || i > 0 && reachable[i - 1]
                                    || i + 1 < reachable.length && reachable[i + 1])
                            && (gray[(cy + i - radius) * width + x] & 255) < 135) {
                        next[i] = true;
                        any = true;
                    }
                if (!any) {
                    complete = false;
                    break;
                }
                if (step >= radius) {
                    int first = -1, last = -1;
                    for (int row = 0; row < next.length; row++)
                        if (next[row]) {
                            if (first < 0) first = row;
                            last = row;
                        }
                    samples++;
                    if (last - first + 1 <= Math.max(2, Math.round(gap * .18f))) narrow++;
                }
                reachable = next;
            }
            if (complete && samples >= 3 && narrow >= samples * .6f) return true;
        }
        return false;
    }

    private static int completeBeamEdge(
            byte[] gray, int width, int height, int x, int y, int direction, int limit) {
        if (x < 1 || x >= width - 1) return y;
        int edge = y;
        for (int row = y; direction * (limit - row) >= 0; row += direction) {
            if (row < 0
                    || row >= height
                    || (gray[row * width + x - 1] & 255) >= 165
                    || (gray[row * width + x] & 255) >= 165
                    || (gray[row * width + x + 1] & 255) >= 165) break;
            edge = row;
        }
        return edge;
    }

    /** A tolerant stem trace can bridge a white gap into lettering or page artwork.
     * Shorten only at a printed beam, with no semantic stem continuing through
     * the supposed tail. Shorter traces also exclude a genuine outer beam. */
    private static int[] stemBeforePaperTail(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int[] original) {
        if (original == null || Math.abs(original[1] - head.centerY) <= gap * 3.5f) return original;
        boolean shortTrace = Math.abs(original[1] - head.centerY) <= gap * 7;
        int end = Math.round(head.centerY), blank = 0;
        for (int y = end; (original[1] - y) * original[2] >= 0; y += original[2]) {
            if ((gray[y * width + original[0]] & 255) < 170) {
                end = y;
                blank = 0;
            } else if (++blank > (shortTrace ? 0 : 1)) break;
        }
        int[] strict = {original[0], end, original[2]};
        if (shortTrace) {
            int gapY = end + original[2];
            if (gapY < 0 || gapY >= height) return original;
            for (int x = Math.max(0, original[0] - 1);
                    x <= Math.min(width - 1, original[0] + 1);
                    x++) if ((gray[gapY * width + x] & 255) < 205) return original;
        }
        if (Math.abs(end - head.centerY) < gap * 2.3f
                || (original[1] - end) * original[2] < gap * .75f) return original;
        int start = strict[1] + original[2] * Math.max(2, Math.round(gap * .4f)), ink = 0;
        for (int y = start; (original[1] - y) * original[2] >= 0; y += original[2])
            for (int x = Math.max(0, original[0] - 1);
                    x <= Math.min(width - 1, original[0] + 1);
                    x++) if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST) ink++;
        if (ink > gap * .3f) return original;
        int left = Math.max(0, strict[0] - Math.round(gap * 3)),
                right = Math.min(width - 1, strict[0] + Math.round(gap * 3));
        if (shortTrace) {
            // Do not stop at an inner beam when a faint stem joins another real
            // beam beyond the gap. Lettering lacks this long, thick horizontal run.
            int longRows = 0;
            for (int y = start; (original[1] - y) * original[2] >= 0; y += original[2]) {
                if (darkRunAtStem(
                                gray,
                                width,
                                y,
                                left,
                                right,
                                strict[0],
                                Math.max(2, Math.round(gap * .28f)),
                                1)
                        >= gap * 1.5f) longRows++;
                else longRows = 0;
                if (longRows >= Math.max(3, Math.ceil(gap * .3f))) return original;
            }
        }
        int margin = Math.max(2, Math.round(gap * (shortTrace ? .6f : .35f))),
                run = 0,
                thick = 0,
                stemInk = 0;
        for (int y = Math.max(0, strict[1] - margin);
                y <= Math.min(height - 1, strict[1] + margin);
                y++) {
            if (darkRunAtStem(gray, width, y, left, right, strict[0], margin, 1) >= gap * 1.5f)
                run++;
            else run = 0;
            thick = Math.max(thick, run);
        }
        for (int d = margin; d <= Math.round(gap); d++) {
            int y = strict[1] - original[2] * d;
            if (y < 0 || y >= height) continue;
            for (int x = Math.max(0, strict[0] - 1); x <= Math.min(width - 1, strict[0] + 1); x++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST) stemInk++;
        }
        return thick >= Math.max(3, Math.ceil(gap * .3f)) && stemInk >= gap * .6f
                ? strict
                : original;
    }

    /** A small white gap tolerated in a stem trace can lead into a separate
     * down-bow square. Its cap is not the beam endpoint. Require the complete
     * detached cap and two legs, followed by a thick beam attached to this stem. */
    private static int[] stemBelowDetachedBow(
            byte[] gray, int width, int height, Component head, float gap, int[] stem) {
        if (gray == null || stem == null || stem[2] >= 0) return stem;
        int left = Math.max(0, stem[0] - Math.round(gap * 2)),
                right = Math.min(width - 1, stem[0] + Math.round(gap * 2));
        int top = Math.max(0, stem[1] - Math.round(gap * .5f)),
                bottom = Math.min(height - 1, stem[1] + Math.round(gap * 2));
        int w = right - left + 1, h = bottom - top + 1, seed = (stem[1] - top) * w + stem[0] - left;
        if ((gray[stem[1] * width + stem[0]] & 255) > 165) return stem;
        boolean[] seen = new boolean[w * h];
        int[] queue = new int[w * h];
        queue[0] = seed;
        seen[seed] = true;
        int take = 0, size = 1, minX = width, maxX = -1, minY = height, maxY = -1;
        while (take < size) {
            int at = queue[take++], x = at % w, y = at / w;
            minX = Math.min(minX, left + x);
            maxX = Math.max(maxX, left + x);
            minY = Math.min(minY, top + y);
            maxY = Math.max(maxY, top + y);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int next = ny * w + nx;
                    if (!seen[next] && (gray[(top + ny) * width + left + nx] & 255) <= 165) {
                        seen[next] = true;
                        queue[size++] = next;
                    }
                }
        }
        int gw = maxX - minX + 1, gh = maxY - minY + 1;
        if (minX <= left
                || maxX >= right
                || minY <= top
                || maxY >= bottom
                || gw < gap * .65f
                || gw > gap * 1.65f
                || gh < gap * .65f
                || gh > gap * 1.65f
                || stem[1] > minY + gap * .3f) return stem;
        int capRows = 0, legRows = 0, legTotal = 0;
        for (int y = minY; y <= maxY; y++) {
            int row = 0, a = 0, b = 0, middle = 0;
            for (int x = minX; x <= maxX; x++)
                if (seen[(y - top) * w + x - left]) {
                    row++;
                    if (x < minX + gw * .25f) a++;
                    else if (x > maxX - gw * .25f) b++;
                    else middle++;
                }
            if (y < minY + gh * .4f && row >= gw * .75f) capRows++;
            if (y >= minY + gh * .45f) {
                legTotal++;
                if (a > 0 && b > 0 && middle <= gw * .1f) legRows++;
            }
        }
        if (capRows < Math.max(2, Math.round(gh * .18f)) || legRows < legTotal * .9f) return stem;
        int first = maxY + 1, last = Math.min(height - 1, maxY + Math.round(gap * .55f));
        while (first <= last && (gray[first * width + stem[0]] & 255) > 165) first++;
        if (first > last || first <= maxY + 1 || first >= head.centerY - gap) return stem;
        int thick = Math.max(3, (int) Math.ceil(gap * .3f));
        for (int y = first; y < first + thick; y++) {
            if (y >= height
                    || darkRunAtStem(
                                    gray,
                                    width,
                                    y,
                                    Math.max(0, stem[0] - Math.round(gap * 2.5f)),
                                    Math.min(width - 1, stem[0] + Math.round(gap * 2.5f)),
                                    stem[0],
                                    1,
                                    1)
                            < gap * 2) return stem;
        }
        return new int[] {stem[0], first, stem[2]};
    }

    /** A faded stem can still connect its head to an independently dark beam.
     * Require narrow printed contrast along the stem and thick connected beam
     * ink at two distances on the same side before trusting the lighter trace. */
    private static int[] fadedStemToDarkBeam(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        float gap = staff.gap;
        int[] stem =
                attachedRawStem(
                        gray, width, height, head, gap, Math.max(1, Math.round(gap * .16f)), 235);
        if (stem == null || Math.abs(stem[1] - head.centerY) > gap * 5.5f) return null;
        int flank = Math.max(3, Math.round(gap * .45f)), x = stem[0];
        if (x - flank < 0 || x + flank >= width) return null;
        int first = Math.round(head.centerY) + stem[2] * Math.round(gap * .85f);
        int last = stem[1] - stem[2] * Math.round(gap * .8f), samples = 0, support = 0;
        for (int y = first; (last - y) * stem[2] >= 0; y += stem[2]) {
            if (y < 0 || y >= height) return null;
            int ink = gray[y * width + x] & 255;
            samples++;
            if (ink < 235
                    && (gray[y * width + x - flank] & 255) >= ink + 12
                    && (gray[y * width + x + flank] & 255) >= ink + 12) support++;
        }
        if (samples < gap || support < samples * .75f) return null;
        boolean up = stem[2] < 0;
        if (hasCurvedFlag(labels, gray, width, height, head, gap, stem[0], stem[1], up)) {
            int[] connected =
                    attachedRawStem(
                            gray,
                            width,
                            height,
                            head,
                            gap,
                            Math.max(1, (int) Math.floor(gap * .16f)),
                            235);
            return connected != null
                            && connected[2] == stem[2]
                            && (connected[1] - stem[1]) * stem[2] >= -gap * .15f
                    ? connected
                    : null;
        }
        int top = Math.max(0, stem[1] - Math.round(gap * (up ? .2f : 1.85f)));
        int bottom = Math.min(height - 1, stem[1] + Math.round(gap * (up ? 1.85f : .2f)));
        for (int side : new int[] {-1, 1}) {
            int inner = x + side * Math.round(gap * .4f), outer = x + side * Math.round(gap * .65f);
            if (thickNonHeadBands(gray, labels, width, height, inner, top, bottom, staff) > 0
                    && thickNonHeadBands(
                                    gray, labels, width, height, outer, top, bottom, staff, inner)
                            > 0) return stem;
        }
        return null;
    }

    /** Correct a semantic two-band endpoint only with stable, complete single-beam evidence. */
    private static boolean singleBeamAtPaleEndpoint(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        float gap = staff.gap;
        int adaptive =
                BeamInkThreshold.at(
                        gray,
                        width,
                        height,
                        Math.round(head.centerX),
                        Math.round(head.centerY - gap * 5),
                        Math.round(head.centerY + gap * 5),
                        gap);
        if (adaptive != 165
                || attachedRawStem(
                                gray,
                                width,
                                height,
                                head,
                                gap,
                                Math.max(1, Math.round(gap * .16f)),
                                adaptive + 5)
                        != null) return false;
        int[] pale = paleStemToSupportedBeam(labels, gray, width, height, head, staff, true);
        int[] mid =
                attachedRawStem(
                        gray, width, height, head, gap, Math.max(1, Math.round(gap * .16f)), 205);
        if (pale == null
                || mid == null
                || pale[2] != mid[2]
                || Math.abs(pale[0] - mid[0]) > gap * .3f
                || Math.abs(pale[1] - mid[1]) > gap * .25f
                || !PaleSingleBeamInk.supports(gray, width, height, pale, gap)) return false;
        boolean up = pale[2] < 0;
        int top = Math.max(0, pale[1] - Math.round(gap * (up ? .2f : 1.85f)));
        int bottom = Math.min(height - 1, pale[1] + Math.round(gap * (up ? 1.85f : .2f)));
        for (float distance : new float[] {-.65f, -.4f, .4f, .65f}) {
            int x = pale[0] + Math.round(gap * distance),
                    inner = pale[0] + Math.round(Math.copySign(.4f, distance) * gap);
            if (thickNonHeadBands(gray, labels, width, height, x, top, bottom, staff, inner, true)
                    != 1) return false;
            // A lighter second rooted band makes the single-beam override ambiguous.
            for (int threshold : new int[] {175, 185})
                if (thickNonHeadBandsAtThreshold(
                                gray, labels, width, height, x, top, bottom, staff, threshold,
                                inner)
                        > 1) return false;
        }
        return true;
    }

    /** Reject a false semantic endpoint only when the longer raw shaft is bare. */
    private static boolean barePaleStemEndpoint(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        float gap = staff.gap;
        int threshold =
                BeamInkThreshold.at(
                                gray,
                                width,
                                height,
                                Math.round(head.centerX),
                                Math.round(head.centerY - gap * 5),
                                Math.round(head.centerY + gap * 5),
                                gap)
                        + 5;
        if (attachedRawStem(
                        gray,
                        width,
                        height,
                        head,
                        gap,
                        Math.max(1, Math.round(gap * .16f)),
                        threshold)
                != null) return false;
        int[] stem = paleStemEndpoint(labels, gray, width, height, head, staff);
        if (stem == null) return false;
        boolean up = stem[2] < 0;
        int[] flagged = paleStemToDoubleBeam(labels, gray, width, height, head, staff);
        if (flagged != null
                && rootedPaleFlag(
                        labels,
                        gray,
                        width,
                        height,
                        head,
                        gap,
                        flagged[0],
                        flagged[1],
                        flagged[2] < 0)) return false;
        if (hasCurvedFlag(labels, gray, width, height, head, gap, stem[0], stem[1], up)
                || rootedPaleFlag(labels, gray, width, height, head, gap, stem[0], stem[1], up)
                || OutlinedBeamInk.count(gray, width, height, stem, head.centerY, gap) > 0)
            return false;
        int top = Math.max(0, stem[1] - Math.round(gap * (up ? .2f : 1.85f)));
        int bottom = Math.min(height - 1, stem[1] + Math.round(gap * (up ? 1.85f : .2f)));
        for (float offset : new float[] {-.65f, -.4f, .4f, .65f}) {
            int x = stem[0] + Math.round(offset * gap);
            int inner = stem[0] + Math.round(Math.copySign(.4f, offset) * gap);
            if (thickNonHeadBands(gray, labels, width, height, x, top, bottom, staff, inner, true)
                    > 0) return false;
        }
        return true;
    }

    private static int[] paleStemEndpoint(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        if (gray == null) return null;
        float gap = staff.gap;
        int[] trace =
                attachedRawStem(
                        gray, width, height, head, gap, Math.max(1, Math.round(gap * .16f)), 245);
        if (trace == null || Math.abs(trace[1] - head.centerY) > gap * 5.5f) return null;
        int flank = Math.max(3, Math.round(gap * .45f)), direction = trace[2];
        int centerRadius = Math.max(2, Math.round(gap * .3f));
        for (int offset = -centerRadius; offset <= centerRadius; offset++) {
            int x = trace[0] + offset;
            int edge = direction < 0 ? head.maxX : head.minX;
            if (x - flank < 0 || x + flank >= width || Math.abs(x - edge) > Math.round(gap * .3f))
                continue;
            int blanks = 0;
            boolean connected = true;
            for (int y = Math.round(head.centerY);
                    (trace[1] - y) * direction >= 0;
                    y += direction) {
                if ((gray[y * width + x] & 255) < 245) blanks = 0;
                else if (++blanks > Math.max(1, Math.round(gap * .16f))) {
                    connected = false;
                    break;
                }
            }
            if (!connected) continue;
            float localGap = staff.pitchGap,
                    localBottom = staff.pitchBottom + staff.pitchSlope * (x - width * .5f);
            if (staff.pitchTrack != null) {
                float[] local = staff.pitchTrack.at(x);
                localBottom = local[0];
                localGap = local[1];
            }
            int first = Math.round(head.centerY) + direction * Math.round(gap * .85f);
            int last = trace[1] - direction * Math.round(gap * 1.35f), samples = 0, support = 0;
            for (int y = first; (last - y) * direction >= 0; y += direction) {
                float rule = localBottom + Math.round((y - localBottom) / localGap) * localGap;
                if (Math.abs(y - rule) <= localGap * .2f) continue;
                int ink = gray[y * width + x] & 255;
                samples++;
                if (ink < 245
                        && (gray[y * width + x - flank] & 255) >= ink + 8
                        && (gray[y * width + x + flank] & 255) >= ink + 8) support++;
            }
            if (samples < Math.max(8, Math.round(gap * .6f)) || support < samples * .75f) continue;
            return new int[] {x, trace[1], direction};
        }
        return null;
    }

    /** A very pale shaft needs two dark beams and a continuous centered ink path.
     * Staff crossings cannot supply its contrast; inspect the clear spaces instead. */
    private static int[] paleStemToDoubleBeam(
            byte[] labels, byte[] gray, int width, int height, Component head, Staff staff) {
        return paleStemToSupportedBeam(labels, gray, width, height, head, staff, false);
    }

    private static int[] paleStemToSupportedBeam(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            boolean allowSinglePale) {
        int[] original =
                paleStemToSupportedBeamAtThreshold(
                        labels, gray, width, height, head, staff, allowSinglePale, 245);
        return original != null
                ? original
                : paleStemToSupportedBeamAtThreshold(
                        labels, gray, width, height, head, staff, allowSinglePale, 225);
    }

    private static int[] paleStemToSupportedBeamAtThreshold(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            boolean allowSinglePale,
            int inkThreshold) {
        return paleStemToSupportedBeamAtThreshold(
                labels, gray, width, height, head, staff, allowSinglePale, inkThreshold, false);
    }

    private static int[] paleStemToSupportedBeamAtThreshold(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            boolean allowSinglePale,
            int inkThreshold,
            boolean gradientPaper) {
        if (gray == null) return null;
        float gap = staff.gap;
        int[] trace =
                attachedRawStem(
                        gray,
                        width,
                        height,
                        head,
                        gap,
                        Math.max(1, Math.round(gap * .16f)),
                        inkThreshold);
        if (trace == null
                || Math.abs(trace[1] - head.centerY) > gap * (allowSinglePale ? 10f : 7.5f))
            return null;
        int flank = Math.max(3, Math.round(gap * .45f)), direction = trace[2];
        int centerRadius = Math.max(2, Math.round(gap * .3f));
        if (gradientPaper)
            centerRadius += Math.abs(trace[0] - (direction < 0 ? head.maxX : head.minX));
        for (int step = -centerRadius; step <= centerRadius; step++) {
            int ordinal = step + centerRadius;
            int offset = (ordinal + 1) / 2 * (ordinal % 2 == 1 ? -1 : 1);
            int x = trace[0] + offset;
            int edge = direction < 0 ? head.maxX : head.minX;
            if (x - flank < 0 || x + flank >= width || Math.abs(x - edge) > Math.round(gap * .3f))
                continue;
            int blanks = 0;
            boolean connected = true;
            for (int y = Math.round(head.centerY);
                    (trace[1] - y) * direction >= 0;
                    y += direction) {
                if ((gray[y * width + x] & 255) < inkThreshold) blanks = 0;
                else if (++blanks > Math.max(1, Math.round(gap * .16f))) {
                    connected = false;
                    break;
                }
            }
            if (!connected) continue;
            boolean boundedFlag =
                    inkThreshold < 245
                            && Math.abs(trace[1] - head.centerY) <= gap * 5.5f
                            && rootedPaleFlag(
                                    labels,
                                    gray,
                                    width,
                                    height,
                                    head,
                                    gap,
                                    x,
                                    trace[1],
                                    direction < 0);
            if (inkThreshold < 245 && !boundedFlag) continue;
            float localGap = staff.pitchGap,
                    localBottom = staff.pitchBottom + staff.pitchSlope * (x - width * .5f);
            if (staff.pitchTrack != null) {
                float[] local = staff.pitchTrack.at(x);
                localBottom = local[0];
                localGap = local[1];
            }
            int first = Math.round(head.centerY) + direction * Math.round(gap * .85f);
            int last = trace[1] - direction * Math.round(gap * 1.35f),
                    samples = 0,
                    support = 0,
                    gradientSupport = 0,
                    agreedPaper = 0;
            for (int y = first; (last - y) * direction >= 0; y += direction) {
                float rule = localBottom + Math.round((y - localBottom) / localGap) * localGap;
                if (Math.abs(y - rule) <= localGap * .2f) continue;
                if (boundedFlag
                        && labels[y * width + x - flank] == OmrMeasurePostProcessor.NOTEHEAD)
                    continue;
                int ink = gray[y * width + x] & 255;
                samples++;
                int contrast = inkThreshold == 245 ? 8 : 25;
                if (gradientPaper && x - 2 * flank >= 0 && x + 2 * flank < width) {
                    int leftPaper = gray[y * width + x - flank] & 255;
                    int rightPaper = gray[y * width + x + flank] & 255;
                    if (ink < inkThreshold && leftPaper + rightPaper >= 2 * (ink + contrast)) {
                        gradientSupport++;
                        int leftCenter = 2 * leftPaper - (gray[y * width + x - 2 * flank] & 255);
                        int rightCenter = 2 * rightPaper - (gray[y * width + x + 2 * flank] & 255);
                        if (Math.abs(leftCenter - rightCenter) <= 2 * contrast) agreedPaper++;
                    }
                }
                if (ink < inkThreshold
                        && (gray[y * width + x - flank] & 255) >= ink + contrast
                        && (boundedFlag || (gray[y * width + x + flank] & 255) >= ink + contrast))
                    support++;
            }
            int minimumSamples = Math.max(8, Math.round(gap * .6f));
            if (samples < minimumSamples) continue;
            if (gradientPaper) {
                if (gradientSupport < samples * .75f
                        || agreedPaper < Math.max(minimumSamples, samples * .5f)) continue;
            } else if (support < samples * .75f) continue;
            if (!gradientPaper
                    && Math.abs(trace[1] - head.centerY) <= gap * 5.5f
                    && rootedPaleFlag(
                            labels, gray, width, height, head, gap, x, trace[1], direction < 0))
                return new int[] {x, trace[1], direction};
            int top = Math.max(0, trace[1] - Math.round(gap * (direction < 0 ? .2f : 1.85f)));
            int bottom =
                    Math.min(
                            height - 1, trace[1] + Math.round(gap * (direction < 0 ? 1.85f : .2f)));
            for (int side : new int[] {-1, 1}) {
                int inner = x + side * Math.round(gap * .4f),
                        outer = x + side * Math.round(gap * .65f);
                int innerBeams =
                        thickNonHeadBands(gray, labels, width, height, inner, top, bottom, staff);
                int outerBeams =
                        thickNonHeadBands(
                                gray, labels, width, height, outer, top, bottom, staff, inner);
                if (innerBeams == outerBeams
                        && (innerBeams == 2
                                || allowSinglePale
                                        && innerBeams == 1
                                        && PaleSingleBeamInk.supports(
                                                gray,
                                                width,
                                                height,
                                                new int[] {x, trace[1], direction},
                                                gap))) return new int[] {x, trace[1], direction};
            }
        }
        return null;
    }

    private static boolean rootedPaleFlag(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int x,
            int end,
            boolean upward) {
        if (!hasCurvedFlag(labels, gray, width, height, head, gap, x, end, upward, true))
            return false;
        int direction = upward ? 1 : -1, rows = 0, span = Math.max(3, Math.round(gap * .3f));
        int threshold =
                BeamInkThreshold.at(
                        gray,
                        width,
                        height,
                        x,
                        Math.max(0, end - Math.round(gap)),
                        Math.min(height - 1, end + Math.round(gap)),
                        gap);
        if (threshold == 165) threshold = 205;
        if (x < 0 || x + span >= width) return false;
        for (int d = 0; d <= Math.round(gap * 1.15f); d++) {
            int y = end + direction * d;
            if (y < 0 || y >= height) continue;
            if (rawRuleBeyondFlag(gray, width, y, x, gap, threshold)) continue;
            int ink = 0;
            for (int xx = x; xx <= x + span; xx++)
                if ((gray[y * width + xx] & 255) < threshold) ink++;
            if (ink >= (span + 1) * .85f) rows++;
        }
        return rows >= Math.max(2, Math.round(gap * .16f));
    }

    /** A slightly leaning stem can leave every fixed column before its flag root.
     * Follow only connected ink in a narrow corridor, and require the existing
     * returning-hook proof at the recovered endpoint before changing the trace. */
    private static int[] stemToReturningFlag(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int[] stem) {
        if (stem == null
                || gray == null
                || hasCurvedFlag(
                        labels, gray, width, height, head, gap, stem[0], stem[1], stem[2] < 0))
            return stem;
        // A continuation must contrast with local paper before it can join a hook.
        int threshold =
                BeamInkThreshold.at(
                                gray,
                                width,
                                height,
                                stem[0],
                                Math.round(head.centerY - gap * 5),
                                Math.round(head.centerY + gap * 5),
                                gap)
                        + 5;
        int radius = Math.max(1, Math.round(gap * .22f)), x = stem[0], end = stem[1];
        for (int distance = 1; distance <= Math.round(gap * 1.8f); distance++) {
            int y = stem[1] + stem[2] * distance;
            if (y < 0 || y >= height || Math.abs(y - head.centerY) > gap * 5) break;
            int next = -1;
            for (int offset : new int[] {0, -1, 1}) {
                int candidate = x + offset;
                if (candidate < 1
                        || candidate >= width - 1
                        || Math.abs(candidate - stem[0]) > radius) continue;
                if ((gray[y * width + candidate] & 255) < threshold
                        && ((gray[y * width + candidate - 1] & 255) < threshold + 35
                                || (gray[y * width + candidate + 1] & 255) < threshold + 35)) {
                    next = candidate;
                    break;
                }
            }
            if (next < 0) break;
            x = next;
            end = y;
        }
        if (Math.abs(end - stem[1]) < gap * .5f) return stem;
        return hasCurvedFlag(labels, gray, width, height, head, gap, x, end, stem[2] < 0)
                ? new int[] {x, end, stem[2]}
                : stem;
    }

    private static int[] attachedRawStem(
            byte[] gray, int width, int height, Component head, float gap) {
        return attachedRawStem(gray, width, height, head, gap, Math.max(1, Math.round(gap * .16f)));
    }

    private static int[] attachedRawStem(
            byte[] gray, int width, int height, Component head, float gap, int maxBlank) {
        return attachedRawStem(gray, width, height, head, gap, maxBlank, 170);
    }

    private static int[] attachedRawStem(
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int maxBlank,
            int inkThreshold) {
        return attachedRawStem(gray, width, height, head, gap, maxBlank, inkThreshold, 9);
    }

    private static int[] attachedRawStem(
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int maxBlank,
            int inkThreshold,
            int maxStemGaps) {
        if (gray == null) return null;
        int bestLength = 0;
        int[] best = null;
        for (int direction : new int[] {-1, 1}) {
            int edge = direction < 0 ? head.maxX : head.minX;
            for (int x = Math.max(1, edge - Math.round(gap * .3f));
                    x <= Math.min(width - 2, edge + Math.round(gap * .3f));
                    x++) {
                int blank = 0, end = Math.round(head.centerY);
                for (int d = 0; d < Math.round(gap * maxStemGaps); d++) {
                    int y = Math.round(head.centerY) + direction * d;
                    if (y < 0 || y >= height) break;
                    boolean ink = (gray[y * width + x] & 255) < inkThreshold;
                    if (ink) {
                        end = y;
                        blank = 0;
                    } else if (++blank > maxBlank) break;
                }
                int length = Math.abs(end - Math.round(head.centerY));
                if (length > bestLength) {
                    bestLength = length;
                    best = new int[] {x, end, direction};
                }
            }
        }
        // Touching seconds occupy opposite sides of their shared stem, so
        // the stem lies inside the fused head component rather than at its edge.
        if (bestLength < gap * 2.3f
                && head.maxX - head.minX + 1 >= gap * 1.8f
                && head.maxX - head.minX + 1 <= gap * 3.2f
                && head.maxY - head.minY + 1 >= gap * .95f
                && head.maxY - head.minY + 1 <= gap * 2.1f) {
            int directions = 0;
            for (int direction : new int[] {-1, 1})
                for (int x = Math.max(1, head.minX + Math.round(gap * .55f));
                        x <= Math.min(width - 2, head.maxX - Math.round(gap * .55f));
                        x++) {
                    int blank = 0, end = Math.round(head.centerY);
                    for (int d = 0; d < Math.round(gap * maxStemGaps); d++) {
                        int y = Math.round(head.centerY) + direction * d;
                        if (y < 0 || y >= height) break;
                        if ((gray[y * width + x] & 255) < inkThreshold) {
                            end = y;
                            blank = 0;
                        } else if (++blank > maxBlank) break;
                    }
                    int outside = direction < 0 ? head.minY - end : end - head.maxY;
                    int length = Math.abs(end - Math.round(head.centerY));
                    if (outside >= gap * 2) {
                        directions |= direction < 0 ? 1 : 2;
                        if (length > bestLength) {
                            bestLength = length;
                            best = new int[] {x, end, direction};
                        }
                    }
                }
            // A shaft can pass through other chord heads in the opposite direction.
            // Do not replace established beam evidence with an ambiguous interior trace.
            if (directions == 3) return null;
        }
        return bestLength >= gap * 2.3f ? best : null;
    }

    /** A filled chord on one independently proved shaft has one beam count.
     * Read the rhythm beside its outer head, not by voting on interior heads
     * whose ovals and ledger lines can obscure or resemble extra beam bands. */
    private static List<DetectedNote> reconcileSingleShaftChords(
            List<DetectedNote> source, byte[] labels, byte[] gray, int width, int height) {
        if (gray == null) return source;
        List<DetectedNote> result = new ArrayList<>(source);
        boolean[] visited = new boolean[source.size()];
        for (int i = 0; i < source.size(); i++) {
            if (visited[i]) continue;
            DetectedNote seed = source.get(i);
            float gap = seed.staffGap;
            List<Integer> group = new ArrayList<>();
            for (int j = i; j < source.size(); j++) {
                DetectedNote n = source.get(j);
                if (n.event.measureIndex() == seed.event.measureIndex()
                        && n.event.staffIndex() == seed.event.staffIndex()
                        && n.event.staffCount() == seed.event.staffCount()
                        && Math.abs(n.event.positionInMeasure() - seed.event.positionInMeasure())
                                < .002f
                        && Math.abs(n.head.centerX - seed.head.centerX) < gap * 1.8f) group.add(j);
            }
            int seedDirection =
                    PrintedStemDirection.detect(
                            gray, width, height, seed.head.centerX, seed.head.centerY, gap);
            boolean opposing = false;
            if (seedDirection != 0)
                for (int j : group) {
                    var n = source.get(j);
                    int d =
                            PrintedStemDirection.detect(
                                    gray, width, height, n.head.centerX, n.head.centerY, gap);
                    opposing |= d != 0 && d != seedDirection;
                }
            if (opposing)
                opposing = provedOpposingChordVoices(group, source, gray, width, height, gap);
            if (opposing)
                group.removeIf(
                        j -> {
                            var n = source.get(j);
                            int d =
                                    PrintedStemDirection.detect(
                                            gray,
                                            width,
                                            height,
                                            n.head.centerX,
                                            n.head.centerY,
                                            gap);
                            return d != 0 && d != seedDirection;
                        });
            if (group.size() < 2 || group.size() > 6) continue;
            boolean filled = true;
            int left = width, right = 0, top = height, bottom = 0;
            int[] counts = new int[4];
            int dots = 0;
            boolean dotsDiffer = false;
            for (int j : group) {
                DetectedNote n = source.get(j);
                Component h = n.head;
                filled &=
                        n.event.unbeamedDurationBeats() < 2
                                && n.event.beamCount() <= 3
                                && !hasOpenCenter(labels, gray, width, height, h, gap)
                                && (n.event.articulations() & NoteOrnament.GRACE) == 0;
                left = Math.min(left, h.minX);
                right = Math.max(right, h.maxX);
                top = Math.min(top, h.minY);
                bottom = Math.max(bottom, h.maxY);
                if (n.event.beamCount() <= 3) counts[n.event.beamCount()]++;
                dots = Math.max(dots, n.event.augmentationDots());
                dotsDiffer |= n.event.augmentationDots() != seed.event.augmentationDots();
            }
            int common = 0;
            for (int b = 1; b < 4; b++) if (counts[b] > counts[common]) common = b;
            if (!filled
                    || (counts[common] == group.size() && !dotsDiffer)
                    || bottom - top > gap * 6) continue;
            int directions = 0;
            int[] shaft = null;
            int shaftLength = 0;
            for (int x = Math.max(1, left); x <= Math.min(width - 2, right); x++) {
                boolean edge = true;
                for (int j : group) {
                    Component h = source.get(j).head;
                    if (Math.min(Math.abs(x - h.minX), Math.abs(x - h.maxX)) > gap * .25f) {
                        edge = false;
                        break;
                    }
                }
                if (!edge) continue;
                // A shaft meets an outer oval near its centre, not at the
                // oval's top/bottom tip. Those rounded tips are not gaps in
                // the common shaft between the chord's attacks.
                int shaftTop = top + Math.round(gap * .4f),
                        shaftBottom = bottom - Math.round(gap * .4f);
                int ink = 0;
                for (int y = shaftTop; y <= shaftBottom; y++)
                    if ((gray[y * width + x] & 255) < 170) ink++;
                if (ink < (shaftBottom - shaftTop + 1) * .88f) continue;
                Component bounds =
                        new Component(
                                0,
                                left,
                                right,
                                top,
                                bottom,
                                (left + right) * .5f,
                                (top + bottom) * .5f);
                for (int direction : new int[] {-1, 1}) {
                    int start = direction < 0 ? top : bottom, end = start, blank = 0;
                    for (int d = 1; d <= gap * 4; d++) {
                        int y = start + direction * d;
                        if (y < 0 || y >= height) break;
                        if ((gray[y * width + x] & 255) < 170) {
                            end = y;
                            blank = 0;
                        } else if (++blank > Math.max(1, Math.round(gap * .15f))) break;
                    }
                    if (Math.abs(end - start) >= gap * (opposing ? 1.35f : 1.5f)
                            && thinShaftRun(
                                    gray, width, height, bounds, x, end, direction, gap, 170)) {
                        directions |= direction < 0 ? 1 : 2;
                        if (Math.abs(end - start) > shaftLength) {
                            shaftLength = Math.abs(end - start);
                            shaft = new int[] {x, end, direction};
                        }
                    }
                }
            }
            if ((directions != 1 && directions != 2) || shaft == null) continue;
            DetectedNote outer = seed;
            for (int j : group) {
                DetectedNote n = source.get(j);
                if ((n.head.centerY - outer.head.centerY) * shaft[2] > 0) outer = n;
            }
            float staffBottom = outer.event.pageY() * height + outer.event.staffStep() * gap * .5f;
            Staff printed = new Staff(staffBottom - gap * 4, staffBottom, gap);
            if (counts[common] != group.size())
                common =
                        detectBeamCount(
                                labels,
                                gray,
                                width,
                                height,
                                outer.head,
                                printed,
                                false,
                                false,
                                shaft);
            if (common < 0 || common > 3) continue;
            for (int j : group) {
                visited[j] = true;
                DetectedNote n = source.get(j);
                ScoreNoteEvent e = n.event;
                if (e.beamCount() == common && e.augmentationDots() == dots) continue;
                var corrected =
                        new ScoreNoteEvent(
                                        e.measureIndex(),
                                        e.positionInMeasure(),
                                        e.staffStep(),
                                        e.staffIndex(),
                                        e.staffCount(),
                                        e.pageY(),
                                        e.tiedFromPrevious(),
                                        dots,
                                        common,
                                        e.writtenAccidental(),
                                        common > 0 ? 0 : 1,
                                        e.tupletDivisor(),
                                        e.followingRestBeats(),
                                        e.articulations(),
                                        e.clefBottomDiatonic(),
                                        e.crossStaffBeam(),
                                        e.leadingRestBeats(),
                                        e.compactOpening(),
                                        e.octaveShift(),
                                        e.boundaryTies(),
                                        e.tupletNormalNotes())
                                .withStemDirection(e.stemDirection())
                                .withKind(e.kind());
                result.set(j, new DetectedNote(corrected, n.head, n.staffGap));
            }
        }
        return result;
    }

    /** Opposite apparent directions inside one chord are not separate voices.
     * Both thin shafts must extend beyond all ovals on distinct sides. */
    private static boolean provedOpposingChordVoices(
            List<Integer> group,
            List<DetectedNote> source,
            byte[] gray,
            int width,
            int height,
            float gap) {
        int top = height, bottom = 0;
        int[] up = null, down = null;
        for (int j : group) {
            var h = source.get(j).head;
            top = Math.min(top, h.minY);
            bottom = Math.max(bottom, h.maxY);
        }
        for (int j : group) {
            var h = source.get(j).head;
            int[] a = directionalVoiceShaft(gray, width, height, h, gap, -1);
            int[] b = directionalVoiceShaft(gray, width, height, h, gap, 1);
            if (a != null && a[1] < top - gap * .9f) up = a;
            if (b != null && b[1] > bottom + gap * .9f) down = b;
        }
        return up != null && down != null && up[0] - down[0] > gap * .6f;
    }

    /** Aligned close heads can belong to independent voices. Prove both outer
     * shafts before assigning the upper head upward and the lower downward. */
    private static int[] opposingVoiceStem(
            byte[] gray, int width, int height, Component head, float gap, List<Component> heads) {
        if (gray == null) return null;
        for (Component other : heads) {
            float dy = other.centerY - head.centerY;
            if (other == head
                    || Math.abs(dy) < gap * .75f
                    || Math.abs(dy) > gap * 1.3f
                    || Math.abs(other.centerX - head.centerX) > gap * .3f) continue;
            Component upper = dy > 0 ? head : other, lower = dy > 0 ? other : head;
            int[] up = directionalVoiceShaft(gray, width, height, upper, gap, -1);
            int[] down = directionalVoiceShaft(gray, width, height, lower, gap, 1);
            if (up != null && down != null && up[0] - down[0] > gap * .7f)
                return dy > 0 ? up : down;
        }
        return null;
    }

    private static int[] directionalVoiceShaft(
            byte[] gray, int width, int height, Component head, float gap, int direction) {
        int edge = direction < 0 ? head.maxX : head.minX, best = 0;
        int[] result = null;
        for (int x = Math.max(1, edge - Math.round(gap * .2f));
                x <= Math.min(width - 2, edge + Math.round(gap * .2f));
                x++) {
            int end = Math.round(head.centerY), blank = 0;
            for (int d = 0; d < Math.round(gap * 9); d++) {
                int y = Math.round(head.centerY) + direction * d;
                if (y < 0 || y >= height) break;
                if ((gray[y * width + x] & 255) < 170) {
                    end = y;
                    blank = 0;
                } else if (++blank > Math.max(1, Math.round(gap * .16f))) break;
            }
            int length = Math.abs(end - Math.round(head.centerY));
            if (length > best
                    && length >= gap * 2.3f
                    && thinShaftRun(gray, width, height, head, x, end, direction, gap, 170)) {
                best = length;
                result = new int[] {x, end, direction};
            }
        }
        return result;
    }

    /** A chain through touching filled ovals is not a stem. Require a short
     * genuinely narrow shaft outside this head, allowing staff-rule crossings. */
    private static boolean thinShaftRun(
            byte[] gray,
            int width,
            int height,
            Component head,
            int x,
            int end,
            int direction,
            float gap,
            int threshold) {
        int offset = Math.max(2, Math.round(gap * .35f));
        if (x - offset < 0 || x + offset >= width) return false;
        int first = (direction < 0 ? head.minY : head.maxY) + direction, run = 0, best = 0;
        for (int y = first; direction < 0 ? y >= end : y <= end; y += direction) {
            if (y < 0 || y >= height) break;
            boolean narrow =
                    (gray[y * width + x] & 255) < threshold
                            && (gray[y * width + x - offset] & 255) >= threshold
                            && (gray[y * width + x + offset] & 255) >= threshold;
            run = narrow ? run + 1 : 0;
            best = Math.max(best, run);
        }
        return best >= Math.max(2, Math.round(gap * .5f));
    }

    /** An extra beam needs support beyond a single raster column. */
    private static int supportedBeamBands(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int x,
            int top,
            int bottom,
            Staff staff,
            int stemwardX,
            boolean adaptive) {
        int count =
                thickNonHeadBands(
                        gray, labels, width, height, x, top, bottom, staff, stemwardX, adaptive);
        if (count < 2) return count;
        int left =
                thickNonHeadBands(
                        gray, labels, width, height, x - 1, top, bottom, staff, stemwardX,
                        adaptive);
        if (left >= count) return count;
        int right =
                thickNonHeadBands(
                        gray, labels, width, height, x + 1, top, bottom, staff, stemwardX,
                        adaptive);
        return right >= count ? count : Math.max(1, Math.max(left, right));
    }

    private static int thickNonHeadBands(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int x,
            int top,
            int bottom,
            Staff staff) {
        return thickNonHeadBands(gray, labels, width, height, x, top, bottom, staff, x);
    }

    private static int thickNonHeadBands(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int x,
            int top,
            int bottom,
            Staff staff,
            int stemwardX) {
        return thickNonHeadBands(
                gray, labels, width, height, x, top, bottom, staff, stemwardX, true);
    }

    private static int thickNonHeadBands(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int x,
            int top,
            int bottom,
            Staff staff,
            int stemwardX,
            boolean adaptive) {
        int threshold =
                adaptive
                        ? BeamInkThreshold.at(gray, width, height, x, top, bottom, staff.gap)
                        : 165;
        int normal =
                thickNonHeadBandsAtThreshold(
                        gray, labels, width, height, x, top, bottom, staff, threshold, stemwardX);
        if (normal != 1 || x < 1 || x >= width - 1) return normal;
        // A lighter antialiased staff rule can fill the gap between two dark beams.
        // Require two full-thickness dark cores before splitting that connected ink;
        // a single beam crossed by a rule still has only one core.
        int darkest = threshold;
        for (int y = top; y <= bottom; y++) darkest = Math.min(darkest, gray[y * width + x] & 255);
        for (float fraction : new float[] {.75f, .5f, .25f}) {
            int coreThreshold = darkest + Math.round((threshold - darkest) * fraction);
            int cores =
                    thickNonHeadBandsAtThreshold(
                            gray,
                            labels,
                            width,
                            height,
                            x,
                            top,
                            bottom,
                            staff,
                            coreThreshold,
                            stemwardX);
            if (cores == 2
                    && separateBeamCores(gray, labels, width, x, top, bottom, staff, coreThreshold))
                return 2;
        }
        return normal;
    }

    private static boolean separateBeamCores(
            byte[] gray,
            byte[] labels,
            int width,
            int x,
            int top,
            int bottom,
            Staff staff,
            int threshold) {
        int start = -1, previousEnd = -1, previousStart = -1;
        int minimum = Math.max(3, (int) Math.ceil(staff.gap * .30f));
        for (int y = top; y <= bottom + 1; y++) {
            boolean ink =
                    y <= bottom
                            && (gray[y * width + x - 1] & 255) < threshold
                            && (gray[y * width + x] & 255) < threshold
                            && (gray[y * width + x + 1] & 255) < threshold
                            && labels[y * width + x] != OmrMeasurePostProcessor.NOTEHEAD;
            if (ink && start < 0) start = y;
            if (!ink && start >= 0) {
                if (y - start >= minimum) {
                    if (previousEnd >= 0) {
                        int first = previousEnd - previousStart + 1, second = y - start;
                        float separation = (start + y - 1 - previousStart - previousEnd) * .5f;
                        if (Math.max(first, second) <= Math.min(first, second) * 1.8f
                                && separation >= staff.gap * .4f
                                && separation <= staff.gap * 1.1f) return true;
                    }
                    previousEnd = y - 1;
                    previousStart = start;
                }
                start = -1;
            }
        }
        return false;
    }

    /** Two dark bodies may meet a pale shaft through a locally faded junction.
     * Keep the bodies' dark threshold and require a continuous thick path to the
     * shaft. A returning flag or an unattached outer stroke cannot supply it. */
    private static boolean fadedDoubleBeamJunction(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            Staff staff,
            int[] pale) {
        float gap = staff.gap;
        boolean up = pale[2] < 0;
        if (rootedPaleFlag(labels, gray, width, height, head, gap, pale[0], pale[1], up)
                || hasCurvedFlag(labels, gray, width, height, head, gap, pale[0], pale[1], up))
            return false;
        int top = Math.max(0, pale[1] - Math.round(gap * (up ? .2f : 1.85f)));
        int bottom = Math.min(height - 1, pale[1] + Math.round(gap * (up ? 1.85f : .2f)));
        for (int side : new int[] {-1, 1}) {
            int inner = pale[0] + side * Math.round(gap * .4f);
            int outer = pale[0] + side * Math.round(gap * .65f);
            int near = pale[0] + side * 2;
            int threshold = BeamInkThreshold.at(gray, width, height, inner, top, bottom, gap);
            int rootThreshold = threshold < 165 ? threshold + 16 : 205;
            if (thickNonHeadBands(gray, labels, width, height, inner, top, bottom, staff) == 2
                    && thickNonHeadBands(
                                    gray, labels, width, height, outer, top, bottom, staff, inner)
                            == 2
                    && thickNonHeadBandsAtThreshold(
                                    gray,
                                    labels,
                                    width,
                                    height,
                                    inner,
                                    top,
                                    bottom,
                                    staff,
                                    threshold,
                                    near,
                                    rootThreshold)
                            == 2
                    && thickNonHeadBandsAtThreshold(
                                    gray,
                                    labels,
                                    width,
                                    height,
                                    outer,
                                    top,
                                    bottom,
                                    staff,
                                    threshold,
                                    near,
                                    rootThreshold)
                            == 2) return true;
        }
        return false;
    }

    private static int thickNonHeadBandsAtThreshold(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int x,
            int top,
            int bottom,
            Staff staff,
            int threshold,
            int stemwardX) {
        return thickNonHeadBandsAtThreshold(
                gray, labels, width, height, x, top, bottom, staff, threshold, stemwardX,
                threshold);
    }

    private static int thickNonHeadBandsAtThreshold(
            byte[] gray,
            byte[] labels,
            int width,
            int height,
            int x,
            int top,
            int bottom,
            Staff staff,
            int threshold,
            int stemwardX,
            int rootThreshold) {
        float gap = staff.gap, lineTop = staff.top;
        if (x < 1 || x >= width - 1) return 0;
        float[] track = staff.pitchTrack == null ? null : staff.pitchTrack.at(x);
        if (track != null) {
            gap = track[1];
            lineTop = track[0] - 4 * gap;
        }
        int span = Math.round(gap * 2), firstOffset = Math.round(gap);
        int[] leftShift = new int[span], rightShift = new int[span];
        if (track != null)
            for (int i = 0; i < span; i++) {
                int dx = firstOffset + i;
                leftShift[i] = Math.round(staff.pitchTrack.at(x - dx)[0] - track[0]);
                rightShift[i] = Math.round(staff.pitchTrack.at(x + dx)[0] - track[0]);
            }
        int bands = 0, strongBands = 0, run = 0, staffRows = 0;
        for (int y = top; y <= bottom + 1; y++) {
            boolean ink =
                    y <= bottom
                            && (gray[y * width + x - 1] & 255) < threshold
                            && (gray[y * width + x] & 255) < threshold
                            && (gray[y * width + x + 1] & 255) < threshold
                            && labels[y * width + x] != OmrMeasurePostProcessor.NOTEHEAD;
            if (ink) {
                run++;
                int leftInk = 0, rightInk = 0;
                for (int i = 0; i < span; i++) {
                    int dx = firstOffset + i;
                    if (x - dx >= 0
                            && bandRuleInk(
                                    gray,
                                    width,
                                    height,
                                    x - dx,
                                    y + leftShift[i],
                                    threshold,
                                    track != null)) leftInk++;
                    if (x + dx < width
                            && bandRuleInk(
                                    gray,
                                    width,
                                    height,
                                    x + dx,
                                    y + rightShift[i],
                                    threshold,
                                    track != null)) rightInk++;
                }
                if (leftInk >= span * .85f && rightInk >= span * .85f) staffRows++;
            } else {
                // Thick antialiased staff lines are not beams. Preserve thicker real beams
                // crossing a staff, where most of the band extends beyond the staff ink.
                float bandCenter = y - (run + 1) * .5f;
                boolean onStaff = false;
                for (int line = 0; line < 5; line++)
                    if (Math.abs(bandCenter - (lineTop + line * gap)) <= gap * .3f) onStaff = true;
                // The middle of a long beam also has ink on both sides. Suppress it only
                // where an actual staff line runs; horizontal shape alone loses inner eighths.
                boolean onlyStaff =
                        onStaff
                                && (staffRows == run || (staffRows >= 3 && staffRows + 1 == run))
                                && run <= gap * .55f;
                if (!onlyStaff
                        && onStaff
                        && staffRows >= 3
                        && staffRows + 2 >= run
                        && run <= gap * .45f
                        && thinAtInnerProbe(
                                gray, width, height, stemwardX, y - run, y - 1, gap, threshold))
                    onlyStaff = true;
                boolean locallyAlignedRule = false;
                if (!onlyStaff && onStaff && run <= gap * .55f) {
                    Float slope =
                            BeamRuleLocalSlope.find(
                                    gray, width, height, x, lineTop, gap, threshold, bandCenter);
                    if (slope != null && Math.abs(slope) * span >= 1) {
                        int alignedRows = 0;
                        for (int row = y - run; row < y; row++) {
                            int leftInk = 0, rightInk = 0;
                            for (int i = 0; i < span; i++) {
                                int dx = firstOffset + i;
                                if (bandRuleInk(
                                        gray,
                                        width,
                                        height,
                                        x - dx,
                                        row - Math.round(slope * dx),
                                        threshold,
                                        true)) leftInk++;
                                if (bandRuleInk(
                                        gray,
                                        width,
                                        height,
                                        x + dx,
                                        row + Math.round(slope * dx),
                                        threshold,
                                        true)) rightInk++;
                            }
                            if (leftInk >= span * .85f && rightInk >= span * .85f) alignedRows++;
                        }
                        locallyAlignedRule =
                                alignedRows == run || (alignedRows >= 3 && alignedRows + 1 == run);
                        if (locallyAlignedRule) onlyStaff = true;
                    }
                }
                if (onlyStaff
                        && (locallyAlignedRule || run >= Math.ceil(gap * .4f))
                        && finiteBeamOverRule(
                                gray, width, height, x, y - run, y - 1, staff, threshold))
                    onlyStaff = false;
                boolean rooted =
                        run >= 3
                                && bandReachesInnerProbe(
                                        gray,
                                        width,
                                        height,
                                        x,
                                        stemwardX,
                                        y - run,
                                        y - 1,
                                        rootThreshold);
                if (run >= Math.max(3, Math.round(gap * .30f)) && !onlyStaff && rooted) bands++;
                if (run >= Math.max(3, Math.ceil(gap * .30f)) && !onlyStaff && rooted)
                    strongBands++;
                run = 0;
                staffRows = 0;
            }
        }
        // Preserve a single narrow flag. Adding a second beam needs the full
        // thickness threshold so a thinner slur terminal cannot shorten the note.
        return bands > 1 ? Math.max(1, strongBands) : bands;
    }

    /** A blurred rule may gain two fringe rows at the outer beam probe, while
     * remaining only a thin staff line beside the actual stem. */
    private static boolean thinAtInnerProbe(
            byte[] gray,
            int width,
            int height,
            int x,
            int first,
            int last,
            float gap,
            int threshold) {
        if (x < 0 || x >= width) return false;
        int best = 0;
        for (int shift = -2; shift <= 2; shift++) {
            int ink = 0;
            for (int y = Math.max(0, first + shift); y <= Math.min(height - 1, last + shift); y++)
                if ((gray[y * width + x] & 255) < threshold) ink++;
            best = Math.max(best, ink);
        }
        return best < Math.max(3, (int) Math.ceil(gap * .30f));
    }

    /** Both local side probes can lie inside a beam printed over a staff rule.
     * Keep that band only when its thick body ends on both sides and a thinner
     * printed rule continues beyond each end. An unbroken thick rule fails. */
    private static boolean finiteBeamOverRule(
            byte[] gray,
            int width,
            int height,
            int x,
            int first,
            int last,
            Staff staff,
            int threshold) {
        float gap = staff.gap;
        float[] origin = staff.pitchTrack == null ? null : staff.pitchTrack.at(x);
        if (origin != null) gap = origin[1];
        int thickMinimum = Math.max(3, Math.min(last - first + 1, (int) Math.ceil(gap * .3f)));
        int thinMaximum = Math.max(1, (int) Math.floor(gap * .24f));
        int witnessLength = Math.max(4, Math.round(gap * .75f));
        for (int direction : new int[] {-1, 1}) {
            int thickColumns = 0,
                    thinColumns = 0,
                    taperColumns = 0,
                    beamShift = 0,
                    tailInterruption = 0;
            boolean proven = false, tailStarted = false;
            // A written beam can span most of a system. Keep its two-end proof bounded
            // by actual page columns rather than truncating a long finite body.
            for (int distance = 1; distance < width; distance++) {
                int column = x + direction * distance;
                if (column < 1 || column >= width - 1) break;
                int shift =
                        origin == null ? 0 : Math.round(staff.pitchTrack.at(column)[0] - origin[0]);
                int ink = 0, bestShift = beamShift;
                for (int candidate : new int[] {beamShift, beamShift - 1, beamShift + 1}) {
                    if (Math.abs(candidate) > Math.round(gap * .3f)) continue;
                    int count = 0;
                    for (int y = first + shift + candidate; y <= last + shift + candidate; y++) {
                        if (y < 0 || y >= height) return false;
                        if ((gray[y * width + column] & 255) < threshold) count++;
                    }
                    if (count > ink) {
                        ink = count;
                        bestShift = candidate;
                    }
                }
                beamShift = bestShift;
                shift += beamShift;
                // The continuing rule can be lighter than the beam's dark core.
                // Require a narrow local core, never blank paper, beyond the body.
                if (ink == 0 && thickColumns >= Math.round(gap * 2)) {
                    // A beam's dark core can stop just before the lighter rule's center.
                    // Include its antialiased fringe, still requiring a narrow local core.
                    int fringe = Math.max(1, Math.round(gap * .15f));
                    int ruleFirst = first + shift - beamShift - fringe,
                            ruleLast = last + shift - beamShift + fringe;
                    if (ruleFirst < 0 || ruleLast >= height) return false;
                    int minimum = 255;
                    for (int y = ruleFirst; y <= ruleLast; y++)
                        minimum = Math.min(minimum, gray[y * width + column] & 255);
                    int paper = 255;
                    if (minimum >= 220) {
                        int[] histogram = new int[256];
                        int total = 0;
                        int margin = Math.max(3, Math.round(gap * .6f));
                        for (int y = Math.max(0, ruleFirst - margin);
                                y <= Math.min(height - 1, ruleLast + margin);
                                y++) {
                            histogram[gray[y * width + column] & 255]++;
                            total++;
                        }
                        int target = (total * 3 + 3) / 4, seen = 0;
                        for (int shade = 0; shade < 256; shade++) {
                            seen += histogram[shade];
                            if (seen >= target) {
                                paper = shade;
                                break;
                            }
                        }
                    }
                    if (minimum < 220 || minimum <= paper - 24) {
                        int cutoff =
                                minimum < 220
                                        ? Math.min(220, minimum + 6)
                                        : Math.min(paper - 20, minimum + 6);
                        for (int y = ruleFirst; y <= ruleLast; y++)
                            if ((gray[y * width + column] & 255) < cutoff) ink++;
                    }
                }
                if (ink >= thickMinimum) {
                    // A small articulation can cross the continuing rule just beyond a
                    // beam end. Require a fresh uninterrupted thin witness after it;
                    // never bridge another long beam or accept an entirely thick rule.
                    if (tailStarted) {
                        thinColumns = 0;
                        if (++tailInterruption > Math.round(gap * .85f)) break;
                        continue;
                    }
                    taperColumns = 0;
                    thickColumns++;
                } else if (ink > 0 && ink <= thinMaximum && thickColumns >= Math.round(gap * 2)) {
                    tailStarted = true;
                    tailInterruption = 0;
                    if (++thinColumns >= witnessLength) {
                        proven = true;
                        break;
                    }
                } else if (tailStarted && ink > thinMaximum) {
                    thinColumns = 0;
                    if (++tailInterruption > Math.round(gap * .85f)) break;
                } else if (ink > thinMaximum
                        && ink < thickMinimum
                        && thinColumns == 0
                        && thickColumns >= Math.round(gap * 2)
                        && ++taperColumns <= Math.max(1, Math.round(gap * .2f))) {
                    // Antialiasing can soften the last few columns of a finite beam.
                } else break;
            }
            if (!proven) return false;
        }
        return true;
    }

    /** Follow a thick ink path toward the stem. Two beams can merge at the
     * inner probe; a nearby slur cannot supply another unattached outer band. */
    private static boolean bandReachesInnerProbe(
            byte[] gray,
            int width,
            int height,
            int fromX,
            int toX,
            int firstY,
            int lastY,
            int threshold) {
        if (fromX == toX) return true;
        int distance = Math.abs(toX - fromX), direction = Integer.signum(toX - fromX);
        int top = Math.max(1, firstY - distance), bottom = Math.min(height - 2, lastY + distance);
        boolean[] reachable = new boolean[bottom - top + 1];
        for (int y = Math.max(top, firstY + 1); y <= Math.min(bottom, lastY - 1); y++)
            reachable[y - top] = true;
        for (int step = 1; step <= distance; step++) {
            int x = fromX + direction * step;
            if (x < 1 || x >= width - 1) return false;
            boolean[] next = new boolean[reachable.length];
            boolean any = false;
            for (int y = top; y <= bottom; y++) {
                int i = y - top;
                if (!reachable[i]
                        && (i == 0 || !reachable[i - 1])
                        && (i + 1 == reachable.length || !reachable[i + 1])) continue;
                boolean solid = true;
                for (int dy = -1; dy <= 1; dy++)
                    for (int dx = -1; dx <= 1; dx++)
                        if ((gray[(y + dy) * width + x + dx] & 255) >= threshold) solid = false;
                if (!solid) continue;
                next[i] = true;
                any = true;
            }
            if (!any) return false;
            reachable = next;
        }
        return true;
    }

    /** Follow a printed staff's slope when testing whether a short band is only rule ink. */
    private static boolean bandRuleInk(
            byte[] gray, int width, int height, int x, int y, int threshold, boolean curved) {
        for (int yy = Math.max(0, y - (curved ? 1 : 0));
                yy <= Math.min(height - 1, y + (curved ? 1 : 0));
                yy++) if ((gray[yy * width + x] & 255) < threshold) return true;
        return false;
    }

    private static int thickBeamBands(
            byte[] gray, int width, int height, int x, int top, int bottom, float gap) {
        if (x < 1 || x >= width - 1) return 0;
        int bands = 0, run = 0;
        for (int y = top; y <= Math.min(height, bottom + 1); y++) {
            boolean ink =
                    y <= bottom
                            && (gray[y * width + x - 1] & 255) < 165
                            && (gray[y * width + x] & 255) < 165
                            && (gray[y * width + x + 1] & 255) < 165;
            if (ink) run++;
            else {
                if (run >= Math.max(3, Math.round(gap * .30f))) bands++;
                run = 0;
            }
        }
        return bands;
    }

    /** A single flag is a narrow curved hook, not a horizontal beam-width run. */
    private static boolean hasCurvedFlag(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int stemX,
            int end,
            boolean upward) {
        return hasCurvedFlag(labels, gray, width, height, head, gap, stemX, end, upward, false);
    }

    private static boolean hasCurvedFlag(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            Component head,
            float gap,
            int stemX,
            int end,
            boolean upward,
            boolean pale) {
        int left = Math.max(0, Math.round(stemX + gap * .25f));
        int right = Math.min(width - 1, Math.round(stemX + gap * 1.5f));
        int top = Math.max(0, Math.round(upward ? end : end - gap * 2.3f));
        int bottom = Math.min(height - 1, Math.round(upward ? end + gap * 2.3f : end));
        int flagThreshold = BeamInkThreshold.at(gray, width, height, stemX, top, bottom, gap);
        if (pale && flagThreshold == 165) flagThreshold = 205;
        // A slur can return beside the shaft but continues around its free
        // endpoint to the other side. A flag is rooted at that endpoint.
        if (curvePastStemEnd(gray, width, height, stemX, end, gap, upward, flagThreshold))
            return false;
        int rows = 0, nearEnd = 0, bulge = 0, exterior = 0, rootRows = 0;
        for (int y = top; y <= bottom; y++) {
            if (Math.abs(y - head.centerY) < gap * .65f) continue;
            // Do not count staff/ledger strokes as the hook's side wall.
            if (rowLabelCount(
                            labels,
                            width,
                            y,
                            Math.max(0, stemX - Math.round(gap)),
                            Math.min(width - 1, stemX + Math.round(gap * 3)),
                            OmrMeasurePostProcessor.STAFF)
                    > gap) continue;
            // A staff rule can be painted as a generic symbol where a flag crosses
            // it. Its long continuation on both sides is still printed evidence.
            if (rawRuleBeyondFlag(gray, width, y, stemX, gap, flagThreshold)) continue;
            int minX = right + 1, maxX = left - 1;
            for (int x = left; x <= right; x++) {
                if ((gray[y * width + x] & 0xff) > flagThreshold
                        || labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) continue;
                // A nearby rest can enter this window beyond the curved hook.
                // Measure the nearest ink run, not the disconnected rest beside it.
                if (maxX >= left && x - maxX > 2) break;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
            }
            if (maxX < minX) continue;
            // The dot of a neighbouring eighth rest may occupy the window before
            // the hook leaves its stem. It cannot be the root of this flag.
            if (Math.abs(y - end) < gap * .5f && minX - stemX > gap * .6f) {
                continue;
            }
            if (Math.abs(y - end) <= gap * 1.15f && minX - stemX <= gap * .5f) rootRows++;
            rows++;
            if (Math.abs(y - end) <= gap * 1.15f) nearEnd++;
            if (maxX - stemX >= gap * .55f) bulge++;
            if (maxX >= right - 1) exterior++;
        }
        // Every hook needs a root beside the shaft, even when nearby slur ink
        // enters only after the narrow detached-ink exclusion window.
        return rootRows >= Math.max(2, Math.round(gap * .16f))
                && rows >= Math.max(4, Math.round(gap * .65f))
                && nearEnd >= Math.max(2, Math.round(gap * .16f))
                && bulge >= Math.max(3, Math.round(gap * .30f))
                && exterior <= Math.max(1, Math.round(rows * .15f));
    }

    private static boolean curvePastStemEnd(
            byte[] gray,
            int width,
            int height,
            int stemX,
            int end,
            float gap,
            boolean upward,
            int threshold) {
        int direction = upward ? -1 : 1, previous = -1, first = -1, columns = 0;
        for (int dx = Math.max(3, Math.round(gap * .3f)); dx <= Math.round(gap * 1.2f); dx++) {
            int x = stemX - dx;
            if (x < 0) return false;
            int found = -1;
            for (int d = 2; d <= Math.round(gap * 1.8f); d++) {
                int y = end + direction * d;
                if (y < 0 || y >= height) continue;
                if ((gray[y * width + x] & 255) <= threshold
                        && (previous < 0 || Math.abs(d - previous) <= 2)) {
                    found = d;
                    break;
                }
            }
            if (found < 0) return false;
            if (first < 0) first = found;
            previous = found;
            columns++;
        }
        return columns >= Math.max(5, Math.round(gap * .8f)) && previous - first >= gap * .25f;
    }

    private static boolean rawRuleBeyondFlag(byte[] gray, int width, int y, int stemX, float gap) {
        return rawRuleBeyondFlag(gray, width, y, stemX, gap, 165);
    }

    private static boolean rawRuleBeyondFlag(
            byte[] gray, int width, int y, int stemX, float gap, int threshold) {
        for (int direction : new int[] {-1, 1}) {
            int samples = 0, ink = 0;
            for (int dx = Math.round(gap * 2); dx <= Math.round(gap * 4); dx++) {
                int x = stemX + direction * dx;
                if (x < 0 || x >= width) continue;
                samples++;
                if ((gray[y * width + x] & 255) <= threshold) ink++;
            }
            if (samples < Math.max(6, Math.round(gap)) || ink < samples * .85f) return false;
        }
        return true;
    }

    private static int rowLabelCount(
            byte[] labels, int width, int y, int left, int right, byte wanted) {
        int count = 0;
        for (int x = left; x <= right; x++) if (labels[y * width + x] == wanted) count++;
        return count;
    }

    private static int findStemEnd(
            byte[] labels, int width, int stemX, boolean upward, int top, int bottom) {
        int end = upward ? bottom : top;
        for (int y = top; y <= bottom; y++) {
            boolean stem = false;
            for (int dx = -2; dx <= 2; dx++) {
                int x = stemX + dx;
                if (x >= 0
                        && x < width
                        && labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST) {
                    stem = true;
                    break;
                }
            }
            if (stem && (upward ? y < end : y > end)) end = y;
        }
        return end;
    }

    private static int darkRunAtStem(
            byte[] gray,
            int width,
            int y,
            int left,
            int right,
            int stemX,
            int tolerance,
            int allowedGap) {
        int best = 0;
        int clusterLeft = -1, clusterRight = -1, previousInk = -1;
        for (int x = left; x <= right; x++) {
            if ((gray[y * width + x] & 0xff) > 165) continue;
            if (clusterLeft < 0 || x - previousInk > allowedGap + 1) {
                if (clusterLeft >= 0
                        && clusterLeft <= stemX + tolerance
                        && clusterRight >= stemX - tolerance)
                    best = Math.max(best, clusterRight - clusterLeft + 1);
                clusterLeft = x;
            }
            clusterRight = x;
            previousInk = x;
        }
        if (clusterLeft >= 0
                && clusterLeft <= stemX + tolerance
                && clusterRight >= stemX - tolerance)
            best = Math.max(best, clusterRight - clusterLeft + 1);
        return best;
    }

    private static int horizontalRunAtStem(
            byte[] labels,
            int width,
            int y,
            int left,
            int right,
            int stemX,
            int tolerance,
            int allowedGap) {
        int best = 0;
        int clusterLeft = -1, clusterRight = -1, previousInk = -1;
        for (int x = left; x <= right; x++) {
            if (labels[y * width + x] != OmrMeasurePostProcessor.STEM_OR_REST) continue;
            if (clusterLeft < 0 || x - previousInk > allowedGap + 1) {
                if (clusterLeft >= 0
                        && clusterLeft <= stemX + tolerance
                        && clusterRight >= stemX - tolerance)
                    best = Math.max(best, clusterRight - clusterLeft + 1);
                clusterLeft = x;
            }
            clusterRight = x;
            previousInk = x;
        }
        if (clusterLeft >= 0
                && clusterLeft <= stemX + tolerance
                && clusterRight >= stemX - tolerance)
            best = Math.max(best, clusterRight - clusterLeft + 1);
        return best;
    }

    private static int countVertical(byte[] labels, int width, int x, int top, int bottom) {
        int count = 0;
        for (int y = top; y <= bottom; y++) {
            boolean ink = false;
            for (int dx = -1; dx <= 1; dx++) {
                int check = x + dx;
                if (check >= 0
                        && check < width
                        && labels[y * width + check] == OmrMeasurePostProcessor.STEM_OR_REST) {
                    ink = true;
                    break;
                }
            }
            if (ink) count++;
        }
        return count;
    }

    private static int containingMeasure(
            List<MeasureRegion> measures, float x, float y, float verticalTolerance) {
        int result = -1;
        float smallest = Float.MAX_VALUE;
        for (int index = 0; index < measures.size(); index++) {
            MeasureRegion measure = measures.get(index);
            if (x < measure.left() - .004f
                    || x > measure.right() + .004f
                    || y < measure.top() - verticalTolerance
                    || y > measure.bottom() + verticalTolerance) continue;
            float area = (measure.right() - measure.left()) * (measure.bottom() - measure.top());
            if (area < smallest) {
                smallest = area;
                result = index;
            }
        }
        return result;
    }

    /**
     * Keeps a ledger-line note with the measure row that owns its staff. A high violin head can
     * sit outside the measure rectangle and inside the preceding row's padded rectangle; choosing
     * by page Y alone then turns an upper-register note into a low note on the row above. The
     * staff center is unambiguous even when the head itself is not.
     */
    private static int containingMeasureForStaff(
            List<MeasureRegion> measures, float x, float y, Staff staff, int pageHeight) {
        float staffCenter = (staff.top + staff.bottom) * .5f / pageHeight;
        float outside =
                y * pageHeight < staff.top
                        ? staff.top - y * pageHeight
                        : y * pageHeight > staff.bottom ? y * pageHeight - staff.bottom : 0f;
        float verticalTolerance =
                Math.max(staff.gap * 3.5f, outside + staff.gap * .75f) / pageHeight;
        int result = -1;
        float smallest = Float.MAX_VALUE;
        for (int index = 0; index < measures.size(); index++) {
            MeasureRegion measure = measures.get(index);
            if (x < measure.left() - .004f
                    || x > measure.right() + .004f
                    || staffCenter < measure.top() - staff.gap / pageHeight
                    || staffCenter > measure.bottom() + staff.gap / pageHeight
                    || y < measure.top() - verticalTolerance
                    || y > measure.bottom() + verticalTolerance) continue;
            float area = (measure.right() - measure.left()) * (measure.bottom() - measure.top());
            if (area < smallest) {
                smallest = area;
                result = index;
            }
        }
        return result;
    }

    /** A small, stemmed prefix may precede the first full-sized attack used by
     * the measure detector. Never bridge an internal barline or another row. */
    private static int openingGraceMeasure(
            List<MeasureRegion> measures, float x, Staff staff, int width, int height) {
        float cy = (staff.top + staff.bottom) * .5f / height;
        int first = -1;
        for (int i = 0; i < measures.size(); i++) {
            MeasureRegion m = measures.get(i);
            if (cy < m.top() || cy > m.bottom()) continue;
            if (first < 0 || m.left() < measures.get(first).left()) first = i;
        }
        if (first < 0) return -1;
        float distance = measures.get(first).left() * width - x;
        return distance > 0 && distance <= staff.pitchGap * 3 ? first : -1;
    }

    private static boolean openingGraceFlag(
            byte[] gray, int width, int height, Component head, float gap) {
        int[] stem = attachedRawStem(gray, width, height, head, gap * .65f);
        return stem != null
                && stem[2] < 0
                && head.centerY - stem[1] < gap * 3.5f
                && SlashedGraceFlagInk.count(
                                gray, width, height, head.centerX, head.centerY, stem[0], gap)
                        > 0;
    }

    /** Printed accidentals carry to the same written pitch until the next barline. */
    private static List<DetectedNote> applyAccidentalState(List<DetectedNote> source) {
        Map<AccidentalStateKey, Integer> measureState = new HashMap<>();
        Map<PitchKey, Integer> lastResolved = new HashMap<>();
        List<DetectedNote> result = new ArrayList<>(source.size());
        for (DetectedNote note : source) {
            ScoreNoteEvent event = note.event;
            if (event.kind() != ScoreNoteEvent.Kind.PITCHED) {
                result.add(note);
                continue;
            }
            AccidentalStateKey stateKey =
                    new AccidentalStateKey(
                            event.measureIndex(),
                            event.staffIndex(),
                            event.staffCount(),
                            event.diatonicPitchIdentity());
            PitchKey pitchKey =
                    new PitchKey(
                            event.staffIndex(), event.staffCount(), event.diatonicPitchIdentity());
            int accidental = event.writtenAccidental();
            if (accidental != ScoreNoteEvent.ACCIDENTAL_FROM_KEY)
                measureState.put(stateKey, accidental);
            else if (measureState.containsKey(stateKey)) accidental = measureState.get(stateKey);
            else if (event.tiedFromPrevious() && lastResolved.containsKey(pitchKey))
                accidental = lastResolved.get(pitchKey);
            lastResolved.put(pitchKey, accidental);
            if (accidental != event.writtenAccidental()) {
                event =
                        new ScoreNoteEvent(
                                        event.measureIndex(),
                                        event.positionInMeasure(),
                                        event.staffStep(),
                                        event.staffIndex(),
                                        event.staffCount(),
                                        event.pageY(),
                                        event.tiedFromPrevious(),
                                        event.augmentationDots(),
                                        event.beamCount(),
                                        accidental,
                                        event.unbeamedDurationBeats(),
                                        event.tupletDivisor(),
                                        event.followingRestBeats(),
                                        event.articulations(),
                                        event.clefBottomDiatonic(),
                                        event.crossStaffBeam(),
                                        event.leadingRestBeats(),
                                        event.compactOpening(),
                                        event.octaveShift(),
                                        event.boundaryTies(),
                                        event.tupletNormalNotes())
                                .withStemDirection(event.stemDirection())
                                .withTupletRatio(event.tupletDivisor(), event.tupletNormalNotes())
                                .withKind(event.kind());
                note = new DetectedNote(event, note.head, note.staffGap);
            }
            result.add(note);
        }
        return result;
    }

    private static List<DetectedNote> removeSplitDuplicates(List<DetectedNote> source) {
        List<DetectedNote> result = new ArrayList<>();
        for (DetectedNote detected : source) {
            ScoreNoteEvent event = detected.event;
            boolean duplicate = false;
            for (int index = result.size() - 1; index >= 0; index--) {
                ScoreNoteEvent previous = result.get(index).event;
                if (previous.measureIndex() != event.measureIndex()
                        || event.positionInMeasure() - previous.positionInMeasure() > .018f) break;
                if (previous.kind() == event.kind()
                        && previous.staffIndex() == event.staffIndex()
                        && previous.staffStep() == event.staffStep()) {
                    Component priorHead = result.get(index).head;
                    boolean separateUnison =
                            ScoreNoteTiming.hasIndependentSustain(previous)
                                            != ScoreNoteTiming.hasIndependentSustain(event)
                                    && (priorHead.maxX < detected.head.minX
                                            || detected.head.maxX < priorHead.minX)
                                    && Math.abs(priorHead.centerY - detected.head.centerY)
                                            < detected.staffGap * .3f;
                    if (!separateUnison) {
                        duplicate = true;
                        break;
                    }
                }
            }
            if (!duplicate) result.add(detected);
        }
        return result;
    }

    private static List<DetectedNote> markTieContinuations(
            byte[] labels, byte[] gray, int width, int height, List<DetectedNote> source) {
        return markTieContinuations(labels, gray, width, height, source, List.of());
    }

    private static List<DetectedNote> markTieContinuations(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<DetectedNote> source,
            List<Staff> staffs) {
        List<DetectedNote> result = new ArrayList<>(source);
        List<DetectedNote> printedGeometry = null;
        for (int currentIndex = 1; currentIndex < result.size(); currentIndex++) {
            DetectedNote current = result.get(currentIndex);
            if (current.event.kind() != ScoreNoteEvent.Kind.PITCHED) continue;
            int previousIndex =
                    previousSamePitch(result, currentIndex, width, labels, gray, height);
            boolean tied =
                    previousIndex >= 0
                            && hasTieArc(
                                    labels,
                                    gray,
                                    width,
                                    height,
                                    result.get(previousIndex),
                                    current);
            // Semantic staff fragments can compress one system's scale. A
            // separately proved local five-rule track may restore only a
            // two-ended system-break tie; ordinary candidates keep their frame.
            if (!tied && !staffs.isEmpty() && current.head.centerX < width * .35f) {
                if (printedGeometry == null)
                    printedGeometry =
                            printedTieGeometry(labels, gray, width, height, source, staffs);
                int candidate =
                        previousSamePitch(
                                printedGeometry, currentIndex, width, labels, gray, height);
                if (candidate >= 0) {
                    DetectedNote before = printedGeometry.get(candidate),
                            after = printedGeometry.get(currentIndex);
                    tied =
                            systemBreakTieCandidate(before, after, width)
                                    && hasTieArc(labels, gray, width, height, before, after);
                }
            }
            if (!tied) continue;
            ScoreNoteEvent event = current.event;
            result.set(
                    currentIndex,
                    new DetectedNote(
                            new ScoreNoteEvent(
                                            event.measureIndex(),
                                            event.positionInMeasure(),
                                            event.staffStep(),
                                            event.staffIndex(),
                                            event.staffCount(),
                                            event.pageY(),
                                            true,
                                            event.augmentationDots(),
                                            event.beamCount(),
                                            event.writtenAccidental(),
                                            event.unbeamedDurationBeats(),
                                            event.tupletDivisor(),
                                            event.followingRestBeats(),
                                            event.articulations(),
                                            event.clefBottomDiatonic(),
                                            event.crossStaffBeam(),
                                            event.leadingRestBeats(),
                                            event.compactOpening(),
                                            event.octaveShift(),
                                            event.boundaryTies(),
                                            event.tupletNormalNotes())
                                    .withStemDirection(event.stemDirection())
                                    .withTupletRatio(
                                            event.tupletDivisor(), event.tupletNormalNotes())
                                    .withKind(event.kind()),
                            current.head,
                            current.staffGap));
        }
        return result;
    }

    /** Tie-only scale reference; never changes emitted pitch, rhythm or geometry. */
    private static List<DetectedNote> printedTieGeometry(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            List<DetectedNote> source,
            List<Staff> staffs) {
        List<DetectedNote> result = new ArrayList<>(source.size());
        for (DetectedNote note : source) {
            float gap = note.staffGap;
            Staff staff = staffForHead(labels, gray, width, height, staffs, note.head);
            if (staff != null
                    && (staff.pitchTrack != null || staff.printedPhase || staff.printedSlope)) {
                float[] local =
                        staff.pitchTrack != null
                                ? staff.pitchTrack.at(note.head.centerX)
                                : new float[] {
                                    staff.pitchBottom
                                            + staff.pitchSlope * (note.head.centerX - width * .5f),
                                    staff.pitchGap
                                };
                if (Float.isFinite(local[0])
                        && Float.isFinite(local[1])
                        && local[1] >= 5
                        && local[1] >= gap * .65f
                        && local[1] <= gap * 1.55f
                        && Math.round((local[0] - note.head.centerY) * 2 / local[1])
                                == note.event.staffStep()) gap = local[1];
            }
            result.add(new DetectedNote(note.event, note.head, gap));
        }
        return result;
    }

    private static int previousSamePitch(List<DetectedNote> notes, int currentIndex, int width) {
        return previousSamePitch(notes, currentIndex, width, null, null, 0);
    }

    private static int previousSamePitch(
            List<DetectedNote> notes,
            int currentIndex,
            int width,
            byte[] labels,
            byte[] gray,
            int height) {
        DetectedNote current = notes.get(currentIndex);
        if (current.event.kind() != ScoreNoteEvent.Kind.PITCHED) return -1;
        DetectedNote previousOnset = null;
        for (int index = currentIndex - 1; index >= 0; index--) {
            DetectedNote previous = notes.get(index);
            if (!ScoreTiePitchGuard.sameContinuingStaff(previous.event, current.event)) continue;
            if (previous.event.staffCount() != current.event.staffCount()
                    && !systemBreakTieCandidate(previous, current, width)) continue;
            if (current.event.measureIndex() - previous.event.measureIndex() > 1) break;
            if (samePrintedOnset(previous, current)) continue;
            boolean firstOnset = previousOnset == null;
            if (firstOnset) previousOnset = previous;
            // An unpitched attack still occupies this onset; do not borrow an older pitch.
            if (previous.event.kind() != ScoreNoteEvent.Kind.PITCHED) continue;
            // A held voice can bridge a barline while another voice keeps moving. Sustained
            // endpoints or opposing printed shafts prove that independent voice.
            if (!firstOnset
                    && !samePrintedOnset(previous, previousOnset)
                    && !(ScoreNoteTiming.hasIndependentSustain(previous.event)
                            && (previous.event.measureIndex() == current.event.measureIndex()
                                    || ScoreNoteTiming.hasIndependentSustain(current.event)))
                    && !independentTieVoice(notes, index, currentIndex, gray, width, height)) {
                // A closer attack of this pitch supersedes the older held note.
                // Do not stretch that old endpoint across a later melodic slur.
                if (previous.event.diatonicPitchIdentity() == current.event.diatonicPitchIdentity())
                    return -1;
                continue;
            }
            int measureDistance = current.event.measureIndex() - previous.event.measureIndex();
            if (measureDistance > 1) break;
            // An arc is only a tie when its endpoints are the same written pitch. Slurs can have
            // the identical curved shape, so never use the arc itself to bridge staff positions.
            if (previous.event.diatonicPitchIdentity() != current.event.diatonicPitchIdentity())
                continue;
            if (measureDistance == 0
                    && current.event.positionInMeasure() - previous.event.positionInMeasure() > .68f
                    && !ScoreNoteTiming.hasIndependentSustain(previous.event)) continue;
            // A short meter can fill the preceding bar with a quarter or dotted quarter.
            // Its left-edge position does not rule out a tie: the returning printed arc
            // must still connect matching pitches at consecutive voice onsets.
            if (measureDistance == 1 && current.event.positionInMeasure() > .58f) continue;
            float gap = (previous.staffGap + current.staffGap) * .5f;
            if ((samePrintedOnset(previous, previousOnset)
                            || ScoreNoteTiming.hasIndependentSustain(previous.event)
                                    && ScoreNoteTiming.hasIndependentSustain(current.event))
                    && systemBreakTieCandidate(previous, current, width)) return index;
            int horizontal = current.head.minX - previous.head.maxX;
            // Compact engraved ties can start inside the head shoulders. Keep
            // those candidates for the raw returning-arc test, but never relax
            // semantic-only matching or merge overlapping/same-onset heads.
            boolean compactPrintedCandidate =
                    gray != null
                            && labels != null
                            && gray.length == labels.length
                            && horizontal > 2
                            && current.head.centerX - previous.head.centerX >= gap * 1.8f;
            if (horizontal < gap * 1.3f && !compactPrintedCandidate || horizontal > width * .34f)
                continue;
            // Quantization alone can occasionally put two heads near a step boundary in the same
            // bucket. A real repeated pitch remains within less than half a staff-space vertically.
            if (Math.abs(current.head.centerY - previous.head.centerY) > gap * .45f
                    && !LocalTieStaffAlignment.same(
                            labels,
                            gray,
                            width,
                            height,
                            previous.head.centerX,
                            previous.head.centerY,
                            previous.head.minX,
                            previous.head.maxX,
                            previous.staffGap,
                            current.head.centerX,
                            current.head.centerY,
                            current.head.minX,
                            current.head.maxX,
                            current.staffGap,
                            current.event.staffStep())) continue;
            return index;
        }
        return -1;
    }

    /** Opposing printed shafts keep a tied voice independent of other attacks. */
    private static boolean independentTieVoice(
            List<DetectedNote> notes,
            int previousIndex,
            int currentIndex,
            byte[] gray,
            int width,
            int height) {
        if (gray == null || gray.length != (long) width * height) return false;
        DetectedNote before = notes.get(previousIndex), after = notes.get(currentIndex);
        if (before.event.kind() != ScoreNoteEvent.Kind.PITCHED
                || after.event.kind() != ScoreNoteEvent.Kind.PITCHED
                || before.event.diatonicPitchIdentity() != after.event.diatonicPitchIdentity()
                || before.event.followingRestBeats() > 0
                || after.event.leadingRestBeats() > 0) return false;
        int directions =
                tieVoiceStemDirections(gray, width, height, before.head, before.staffGap)
                        & tieVoiceStemDirections(gray, width, height, after.head, after.staffGap);
        if (directions == 0) return false;
        boolean opposing = false;
        for (int i = previousIndex + 1; i < currentIndex; i++) {
            DetectedNote between = notes.get(i);
            if (!ScoreTiePitchGuard.sameContinuingStaff(before.event, between.event)
                    || samePrintedOnset(before, between)
                    || samePrintedOnset(after, between)) continue;
            if (between.event.kind() == ScoreNoteEvent.Kind.PITCHED
                    && between.event.diatonicPitchIdentity()
                            == before.event.diatonicPitchIdentity()) return false;
            int shafts =
                    tieVoiceStemDirections(gray, width, height, between.head, between.staffGap);
            if (shafts != 1 && shafts != 2) return false;
            directions &= ~shafts;
            if (directions == 0) return false;
            opposing = true;
        }
        return opposing;
    }

    /** A shared chord column can have two shafts; the longest one does not own both voices. */
    private static int tieVoiceStemDirections(
            byte[] gray, int width, int height, Component head, float gap) {
        int directions = 0, maxBlank = Math.max(1, Math.round(gap * .16f));
        for (int direction : new int[] {-1, 1}) {
            int edge = direction < 0 ? head.maxX : head.minX;
            for (int x = Math.max(1, edge - Math.round(gap * .3f));
                    x <= Math.min(width - 2, edge + Math.round(gap * .3f));
                    x++) {
                int blank = 0, end = Math.round(head.centerY);
                for (int d = 0; d < Math.round(gap * 4); d++) {
                    int y = Math.round(head.centerY) + direction * d;
                    if (y < 0 || y >= height) break;
                    if ((gray[y * width + x] & 255) < 170) {
                        end = y;
                        blank = 0;
                    } else if (++blank > maxBlank) break;
                }
                // Shaft lengths are measured in integer pixels, including their endpoint.
                if (Math.abs(end - Math.round(head.centerY)) >= Math.round(gap * 2.3f))
                    directions |= direction < 0 ? 1 : 2;
            }
        }
        return directions;
    }

    private static boolean samePrintedOnset(DetectedNote first, DetectedNote second) {
        if (sameOnset(first.event, second.event)) return true;
        float gap = (first.staffGap + second.staffGap) * .5f;
        return first.event.measureIndex() == second.event.measureIndex()
                && first.event.staffIndex() == second.event.staffIndex()
                && first.event.staffCount() == second.event.staffCount()
                && (first.event.kind() == ScoreNoteEvent.Kind.PITCHED
                                && second.event.kind() == ScoreNoteEvent.Kind.PITCHED
                        ? first.event.diatonicPitchIdentity()
                                != second.event.diatonicPitchIdentity()
                        : first.event.staffStep() != second.event.staffStep())
                && (first.head.maxX - first.head.minX + 1 >= gap * .7f
                                && second.head.maxX - second.head.minX + 1 >= gap * .7f
                        || Math.max(
                                                first.head.maxX - first.head.minX + 1,
                                                second.head.maxX - second.head.minX + 1)
                                        >= gap * .7f
                                && Math.min(first.head.maxX, second.head.maxX)
                                                - Math.max(first.head.minX, second.head.minX)
                                                + 1
                                        >= gap * .3f)
                && Math.abs(first.head.centerX - second.head.centerX) <= gap * .7f;
    }

    private static boolean sameOnset(ScoreNoteEvent first, ScoreNoteEvent second) {
        return first.measureIndex() == second.measureIndex()
                && Math.abs(first.positionInMeasure() - second.positionInMeasure()) <= .018f;
    }

    private static boolean hasTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            DetectedNote previous,
            DetectedNote current) {
        if (systemBreakTieCandidate(previous, current, width)) {
            if (gray == null || gray.length != labels.length) return false;
            for (int side : new int[] {-1, 1})
                if (hasSystemEndTieArc(labels, gray, width, height, previous, true, side)
                        && hasSystemEndTieArc(labels, gray, width, height, current, false, side))
                    return true;
            return false;
        }
        int left = Math.max(0, previous.head.maxX + 1);
        int right = Math.min(width - 1, current.head.minX - 1);
        float gap = Math.max(2f, (previous.staffGap + current.staffGap) * .5f);
        if (right <= left
                || right - left + 1 < gap * 1.3f
                        && current.head.centerX - previous.head.centerX < gap * 1.8f) return false;
        float centerY = (previous.head.centerY + current.head.centerY) * .5f;
        if (gray != null && gray.length == labels.length) {
            if (right - left + 1 >= gap * 1.3f
                    && hasPrintedTieArc(labels, gray, width, height, left, right, centerY, gap))
                return true;
            // Engraved ties may begin below the heads, before their horizontal edges.
            // Recover the returning shoulders instead of testing only the flattened middle.
            int overlap = Math.round(gap * .6f);
            int arcLeft = Math.max(Math.round(previous.head.centerX), left - overlap);
            int arcRight = Math.min(Math.round(current.head.centerX), right + overlap);
            if (hasPrintedTieArc(
                    labels, gray, width, height, arcLeft, arcRight, centerY, gap, false))
                return true;
            if (ScoreNoteTiming.hasIndependentSustain(previous.event)
                    && ScoreNoteTiming.hasIndependentSustain(current.event)
                    && hasFlattenedTieArc(
                            labels, gray, width, height, arcLeft, arcRight, centerY, gap))
                return true;
            // Raw pixels must prove a continuous arc; aggregates of staff/beam
            // fragments cannot establish a tie, even after a sustained note.
            return false;
        }
        if (right - left + 1 < gap * 1.3f) return false;
        ArcStats above =
                arcStats(
                        labels,
                        gray,
                        width,
                        height,
                        left,
                        right,
                        Math.round(centerY - gap * 3f),
                        Math.round(centerY - gap * .12f));
        ArcStats below =
                arcStats(
                        labels,
                        gray,
                        width,
                        height,
                        left,
                        right,
                        Math.round(centerY + gap * .12f),
                        Math.round(centerY + gap * 3f));
        return plausibleArc(above, left, right, gap) || plausibleArc(below, left, right, gap);
    }

    private static boolean systemBreakTieCandidate(
            DetectedNote previous, DetectedNote current, int width) {
        float gap = (previous.staffGap + current.staffGap) * .5f;
        return previous.event.kind() == ScoreNoteEvent.Kind.PITCHED
                && current.event.kind() == ScoreNoteEvent.Kind.PITCHED
                && current.event.measureIndex() == previous.event.measureIndex() + 1
                && current.event.diatonicPitchIdentity() == previous.event.diatonicPitchIdentity()
                && previous.head.centerX
                        > width
                                * (ScoreNoteTiming.hasIndependentSustain(previous.event)
                                                && ScoreNoteTiming.hasIndependentSustain(
                                                        current.event)
                                        ? .5f
                                        : .65f)
                && current.head.centerX < width * .35f
                && current.head.centerY - previous.head.centerY > gap * 6
                && current.head.centerY - previous.head.centerY < gap * 40
                && Math.abs(previous.staffGap - current.staffGap) < gap * .2f;
    }

    /** System-end ties retain a returning curve at both printed endpoints.
     * Short strokes and a lone outgoing slur cannot establish continuation. */
    private static boolean hasSystemEndTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            DetectedNote note,
            boolean outgoing,
            int side) {
        float gap = note.staffGap;
        int step = Math.max(2, Math.round(gap * .2f));
        int maximumSpan =
                outgoing && ScoreNoteTiming.hasIndependentSustain(note.event)
                        ? 48
                        : outgoing ? 14 : 7;
        // Returning shoulders may start underneath a head, as with in-system ties.
        // Keep the complete two-ended raw-curve proof on both systems.
        for (int initial : new int[] {step, -Math.round(gap * .65f)}) {
            for (int clearance = initial;
                    clearance <= (initial == step ? gap * 1.8f : 0);
                    clearance += step) {
                for (int span = Math.round(gap * 1.6f); span <= gap * maximumSpan; span += step) {
                    int left =
                            outgoing
                                    ? note.head.maxX + clearance
                                    : note.head.minX - clearance - span;
                    int right = outgoing ? left + span : note.head.minX - clearance;
                    if (left < 0 || right >= width) continue;
                    if (hasContinuousTieArc(
                            labels,
                            gray,
                            width,
                            height,
                            left,
                            right,
                            note.head.centerY,
                            gap,
                            null,
                            205,
                            side)) return true;
                }
            }
        }
        return false;
    }

    /** Small blank clearances can separate an engraved tie from either head. */
    private static boolean hasPrintedTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap) {
        return hasPrintedTieArc(labels, gray, width, height, left, right, centerY, gap, true);
    }

    private static boolean hasPrintedTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap,
            boolean faintShoulders) {
        if (hasContinuousTieArc(labels, gray, width, height, left, right, centerY, gap))
            return true;
        // Compact ties leave small gaps beside the heads. Sample both shoulders
        // finely enough to retain the whole returning curve, even at small sizes.
        // Faint ink is allowed only between heads: under a head it can belong
        // to the tail of an unrelated slur. The faint path still requires a dark core.
        int step = Math.max(1, Math.round(gap * .1f));
        // Under-head shoulders can be asymmetric. Keep the raw, dark curve
        // proof while allowing either end to return before the head center.
        int shoulderCount = faintShoulders ? 4 : (int) Math.ceil(gap * .75f / step);
        for (int first = 0; first <= shoulderCount; first++)
            for (int last = 0; last <= shoulderCount; last++) {
                if (first == 0 && last == 0) continue;
                int a = left + first * step, b = right - last * step;
                if (b - a < gap) continue;
                if (hasContinuousTieArc(labels, gray, width, height, a, b, centerY, gap)
                        || faintShoulders
                                && hasContinuousTieArc(
                                        labels, gray, width, height, a, b, centerY, gap, null, 205))
                    return true;
            }
        // Long ties may leave a full staff-space of clearance beside a dot and fade near the heads.
        // Compact dotted notes also leave that clearance. Keep the whole returning
        // curve inside the head edges and require a substantial remaining span.
        if (faintShoulders && right - left < gap * 5) {
            int clearanceStep = Math.max(1, Math.round(gap * .1f));
            int clearanceCount = Math.round(gap * 1.4f / clearanceStep);
            for (int first = 0; first <= clearanceCount; first++)
                for (int last = 0; last <= clearanceCount; last++) {
                    int a = left + first * clearanceStep, b = right - last * clearanceStep;
                    if (b - a < Math.max(gap * 1.3f, (right - left) * .45f)) continue;
                    if (hasContinuousTieArc(
                            labels, gray, width, height, a, b, centerY, gap, null, 205, 0, true))
                        return true;
                }
        }
        // Keep short-arc limits and require a dark core within the complete curve.
        int longStep = Math.max(1, Math.round(gap * .2f));
        if (right - left >= gap * 5)
            for (int first = 0; first <= 5; first++)
                for (int last = 0; last <= 5; last++) {
                    int a = left + first * longStep, b = right - last * longStep;
                    if (hasContinuousTieArc(
                            labels, gray, width, height, a, b, centerY, gap, null, 205))
                        return true;
                }
        return false;
    }

    /** Follow one returning curve; averaging nearby slurs, stems and ledger lines loses short ties. */
    private static boolean hasFlattenedTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap) {
        if (right - left < gap * 24 || right - left > gap * 48) return false;
        int step = Math.max(1, Math.round(gap * .2f));
        for (int first = 0; first <= 5; first++)
            for (int last = 0; last <= 5; last++)
                if (hasContinuousTieArc(
                        labels,
                        gray,
                        width,
                        height,
                        left + first * step,
                        right - last * step,
                        centerY,
                        gap,
                        null,
                        205,
                        0,
                        true,
                        true)) return true;
        return false;
    }

    /** Follow one returning curve; averaging nearby slurs, stems and ledger lines loses short ties. */
    private static boolean hasContinuousTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap) {
        return hasContinuousTieArc(labels, gray, width, height, left, right, centerY, gap, null);
    }

    private static boolean hasContinuousTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap,
            Component target) {
        return hasContinuousTieArc(
                labels, gray, width, height, left, right, centerY, gap, target, 165);
    }

    private static boolean hasContinuousTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap,
            Component target,
            int inkLimit) {
        return hasContinuousTieArc(
                labels, gray, width, height, left, right, centerY, gap, target, inkLimit, 0);
    }

    private static boolean hasContinuousTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap,
            Component target,
            int inkLimit,
            int requiredSide) {
        return hasContinuousTieArc(
                labels,
                gray,
                width,
                height,
                left,
                right,
                centerY,
                gap,
                target,
                inkLimit,
                requiredSide,
                false);
    }

    private static boolean hasContinuousTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap,
            Component target,
            int inkLimit,
            int requiredSide,
            boolean strictContrast) {
        return hasContinuousTieArc(
                labels,
                gray,
                width,
                height,
                left,
                right,
                centerY,
                gap,
                target,
                inkLimit,
                requiredSide,
                strictContrast,
                false);
    }

    private static boolean hasContinuousTieArc(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            float centerY,
            float gap,
            Component target,
            int inkLimit,
            int requiredSide,
            boolean strictContrast,
            boolean flatProfile) {
        // Boundary arc evidence needs raw pixels; semantic-only decoding remains supported.
        if (gray == null || gray.length != (long) width * height) return false;
        {
            int[] paper = new int[63];
            int count = 0;
            for (int row = -3; row <= 3; row++)
                for (int col = 0; col < 9; col++) {
                    int x = Math.max(0, Math.min(width - 1, left + (right - left) * col / 8));
                    int y =
                            Math.max(
                                    0,
                                    Math.min(height - 1, Math.round(centerY + (row + .37f) * gap)));
                    paper[count++] = gray[y * width + x] & 255;
                }
            java.util.Arrays.sort(paper);
            if (strictContrast || paper[47] < 210)
                inkLimit = Math.min(inkLimit, Math.max(80, paper[47] - 20));
        }
        int radius = Math.max(1, Math.round(gap * .09f));
        int[] contrastOffsets = {
            Math.max(1, Math.round(gap * .18f)),
            Math.max(2, Math.round(gap * .35f)),
            Math.max(3, Math.round(gap * .65f))
        };
        boolean[] straightRows = new boolean[height];
        for (int y = Math.max(0, Math.round(centerY - gap * 3.2f));
                y <= Math.min(height - 1, Math.round(centerY + gap * 3.2f));
                y++) {
            int dark = 0;
            for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) <= inkLimit) dark++;
            straightRows[y] = dark >= (right - left + 1) * .85f;
        }
        // Each candidate is evaluated synchronously; scratch arrays belong to this call.
        int[] bins = new int[5], coveredBins = new int[5];
        float[] centers = new float[50],
                supportedCenters = new float[50],
                strokeCenters = new float[50];
        int reach = Math.max(2, Math.round(gap * .4f));
        // Pixel columns are identical for every candidate curve in this call.
        int[] sampleColumns = new int[50];
        for (int sample = 0; sample < sampleColumns.length; sample++) {
            float t = (sample + .5f) / 50f;
            sampleColumns[sample] = Math.round(left + t * (right - left));
        }
        for (int side : requiredSide == 0 ? new int[] {-1, 1} : new int[] {requiredSide})
            for (float offset = .2f; offset <= 1.15f; offset += .15f)
                candidate:
                for (float bend = -.75f; bend <= 1.8f; bend += .1f) {
                    if (Math.abs(bend) < .24f || offset + bend < .12f) continue;
                    // A compact span cannot own a deep bowl under the heads;
                    // rounded dynamics and rest bulbs can supply unrelated ink.
                    if (right - left < gap * 2.6f && offset + Math.max(0, bend) > 1.6f) continue;
                    if (target != null
                            && Math.abs(centerY + side * gap * (offset + bend) - target.centerY)
                                    > gap * .25f) continue;
                    int hits = 0, obscured = 0, strong = 0;
                    java.util.Arrays.fill(bins, 0);
                    java.util.Arrays.fill(coveredBins, 0);
                    for (int sample = 0; sample < 50; sample++) {
                        // Every acceptance path needs at least 34 visible hits, 43 total
                        // samples, and seven covered samples in every ten-sample bin.
                        // Remaining samples cannot repair a failed bound; skip only then.
                        int remaining = 50 - sample;
                        int bin = sample / 10;
                        if (hits + remaining < 34
                                || hits + obscured + remaining < 43
                                || (inkLimit > 165 && strong + remaining < 30)
                                || coveredBins[bin] + 10 - sample % 10 < 7
                                || (sample % 10 == 0 && bin > 0 && coveredBins[bin - 1] < 7))
                            continue candidate;
                        // Only completed candidates reach curvature checks. Initialize each
                        // visited sample; abandoned candidates never read their stale tail.
                        centers[sample] =
                                supportedCenters[sample] = strokeCenters[sample] = Float.NaN;
                        int x = sampleColumns[sample];
                        int y =
                                Math.round(
                                        centerY
                                                + side
                                                        * gap
                                                        * (offset
                                                                + bend
                                                                        * (flatProfile
                                                                                ? FLAT_TIE_PROFILE[
                                                                                        sample]
                                                                                : PARABOLIC_TIE_PROFILE[
                                                                                        sample])));
                        boolean ink = false;
                        int obscuredY = -1;
                        for (int search = 0; search <= radius * 2; search++) {
                            int dy = (search + 1) / 2 * (search % 2 == 0 ? 1 : -1);
                            int yy = y + dy;
                            if (yy < 0 || yy >= height) continue;
                            int at = yy * width + x;
                            if ((gray[at] & 255) > inkLimit
                                    || labels[at] == OmrMeasurePostProcessor.NOTEHEAD) continue;
                            int shade = gray[at] & 255;
                            if (strictContrast && shade > 125) {
                                int paper = 0;
                                for (int distance : contrastOffsets) {
                                    if (yy >= distance)
                                        paper =
                                                Math.max(
                                                        paper,
                                                        gray[(yy - distance) * width + x] & 255);
                                    if (yy + distance < height)
                                        paper =
                                                Math.max(
                                                        paper,
                                                        gray[(yy + distance) * width + x] & 255);
                                }
                                if (paper < shade + 20) continue;
                            }
                            // Semantic staff bands can cover a curved tie crest. Raw
                            // straight rules stay occluded; the remaining thin stroke
                            // must independently pass the returning-curve checks.
                            if (straightRows[yy]) {
                                if (obscuredY < 0) obscuredY = yy;
                            } else {
                                // Measure the printed stroke center, not whichever edge
                                // best fits the requested curve. Alternating between the
                                // top and bottom edges can bend a thick straight rule.
                                int inkTop = yy, inkBottom = yy;
                                while (inkTop > Math.max(0, yy - reach)
                                        && (gray[(inkTop - 1) * width + x] & 255) <= inkLimit)
                                    inkTop--;
                                while (inkBottom < Math.min(height - 1, yy + reach)
                                        && (gray[(inkBottom + 1) * width + x] & 255) <= inkLimit)
                                    inkBottom++;
                                // Follow the actual thin stroke, not the convenient
                                // edge of a thick accidental, rest bulb or letter.
                                if (inkBottom - inkTop > gap * .6f) continue;
                                float strokeCenter = (inkTop + inkBottom) * .5f;
                                if (Math.abs(strokeCenter - y) > radius + Math.max(1f, gap * .06f))
                                    continue;
                                strokeCenters[sample] = strokeCenter;
                                ink = true;
                                centers[sample] = yy;
                                supportedCenters[sample] = yy;
                                if ((gray[at] & 255) <= 165) strong++;
                                break;
                            }
                        }
                        if (ink) {
                            hits++;
                            bins[sample / 10]++;
                            coveredBins[sample / 10]++;
                        } else if (obscuredY >= 0) {
                            obscured++;
                            coveredBins[sample / 10]++;
                            supportedCenters[sample] = obscuredY;
                        }
                    }
                    if (inkLimit > 165 && strong < 30) continue;
                    // A changing stroke width can supply two curved boundaries while
                    // its printed center remains straight. Do not let the search
                    // switch boundaries to manufacture a returning arc.
                    if (hits >= 34 && hasFlatTieStrokeCenter(strokeCenters, gap)) continue;
                    if (hits >= 43
                            && bins[0] >= 7
                            && bins[1] >= 7
                            && bins[2] >= 7
                            && bins[3] >= 7
                            && bins[4] >= 7
                            && arcCurvature(strokeCenters, 0, 0, 49) >= Math.max(.8f, gap * .12f)) {
                        if (!TieArcBranchInk.outwardStems(
                                gray,
                                width,
                                height,
                                left,
                                right,
                                strokeCenters,
                                side,
                                gap,
                                inkLimit)) return true;
                    }
                    // A short returning arc can cross a staff rule at one end. Treat a
                    // few such pixels as occluded only when the remaining curve and
                    // both endpoints are independently visible away from the rule.
                    if (hits >= 40
                            && obscured > 0
                            && obscured <= 6
                            && hits + obscured >= 43
                            && bins[0] >= 5
                            && bins[4] >= 5
                            && coveredBins[0] >= 7
                            && coveredBins[1] >= 7
                            && coveredBins[2] >= 7
                            && coveredBins[3] >= 7
                            && coveredBins[4] >= 7
                            && arcCurvature(supportedCenters, 0, 0, 49)
                                    >= Math.max(1.2f, gap * .15f)) {
                        if (!TieArcBranchInk.outwardStems(
                                gray,
                                width,
                                height,
                                left,
                                right,
                                strokeCenters,
                                side,
                                gap,
                                inkLimit)) return true;
                    }
                    // Both ends of a short tie can merge into the same thick staff rule.
                    // Require an almost complete curve and an independently visible middle
                    // and returning shoulders; a straight rule or one-sided beam cannot pass.
                    if (hits >= 36
                            && obscured > 0
                            && obscured <= 14
                            && hits + obscured >= 48
                            && bins[0] >= 3
                            && bins[4] >= 3
                            && bins[1] >= 9
                            && bins[2] >= 9
                            && bins[3] >= 9
                            && coveredBins[0] >= 9
                            && coveredBins[4] >= 9
                            && arcCurvature(strokeCenters, 0, 0, 49)
                                    >= Math.max(1.2f, gap * .15f)) {
                        if (!TieArcBranchInk.outwardStems(
                                gray,
                                width,
                                height,
                                left,
                                right,
                                strokeCenters,
                                side,
                                gap,
                                inkLimit)) return true;
                    }
                    // A long, deeply bowed tie can cross several distinct staff rules.
                    // Require every sampled bin to be fully covered, both returning
                    // shoulders and a dark core; unrelated straight fragments cannot qualify.
                    if (right - left >= gap * 8
                            && right - left <= gap * (flatProfile ? 48 : 24)
                            && hits >= 36
                            && strong >= 36
                            && obscured > 0
                            && obscured <= 14
                            && hits + obscured == 50
                            && bins[0] >= 5
                            && bins[4] >= 5
                            && bins[1] >= 8
                            && bins[2] >= 8
                            && bins[3] >= 8
                            && arcCurvature(supportedCenters, 0, 0, 49) >= gap * .45f
                            && (strictContrast
                                    || hasContinuousTieArc(
                                            labels,
                                            gray,
                                            width,
                                            height,
                                            left,
                                            right,
                                            centerY,
                                            gap,
                                            target,
                                            inkLimit,
                                            requiredSide,
                                            true))) {
                        if (!TieArcBranchInk.outwardStems(
                                gray,
                                width,
                                height,
                                left,
                                right,
                                strokeCenters,
                                side,
                                gap,
                                inkLimit)) return true;
                    }
                    // Antialiased shoulders can reduce the dark count while staff rules
                    // obscure a returning curve. Require nearly complete coverage in
                    // every bin, both shoulders, an independently dark core and contrast.
                    if (right - left >= gap * 8
                            && right - left <= gap * (flatProfile ? 48 : 24)
                            && hits >= 36
                            && strong >= 30
                            && obscured > 0
                            && obscured <= 14
                            && hits + obscured >= 48
                            && bins[0] >= 4
                            && bins[4] >= 4
                            && bins[0] + bins[4] >= 10
                            && bins[1] >= 8
                            && bins[2] >= 8
                            && bins[3] >= 8
                            && coveredBins[0] >= 8
                            && coveredBins[1] >= 8
                            && coveredBins[2] >= 8
                            && coveredBins[3] >= 8
                            && coveredBins[4] >= 8
                            && arcCurvature(supportedCenters, 0, 0, 49) >= gap * .45f
                            && (strictContrast
                                    || hasContinuousTieArc(
                                            labels,
                                            gray,
                                            width,
                                            height,
                                            left,
                                            right,
                                            centerY,
                                            gap,
                                            target,
                                            inkLimit,
                                            requiredSide,
                                            true))) {
                        if (!TieArcBranchInk.outwardStems(
                                gray,
                                width,
                                height,
                                left,
                                right,
                                strokeCenters,
                                side,
                                gap,
                                inkLimit)) return true;
                    }
                    // A compact tie can touch a staff rule at its crest instead of
                    // at the endpoints. Both returning shoulders must remain visible;
                    // the rule only supplies the small occluded central section.
                    if (right - left <= gap * 5
                            && hits >= 34
                            && obscured > 0
                            && obscured <= 16
                            && hits + obscured >= 48
                            && bins[0] >= 8
                            && bins[1] >= 7
                            && bins[3] >= 7
                            && bins[4] >= 8
                            && coveredBins[0] >= 9
                            && coveredBins[1] >= 9
                            && coveredBins[2] >= 9
                            && coveredBins[3] >= 9
                            && coveredBins[4] >= 9
                            && arcCurvature(supportedCenters, 0, 0, 49)
                                    >= Math.max(1.5f, gap * .20f)
                            && (strictContrast
                                    || hasContinuousTieArc(
                                            labels,
                                            gray,
                                            width,
                                            height,
                                            left,
                                            right,
                                            centerY,
                                            gap,
                                            target,
                                            inkLimit,
                                            requiredSide,
                                            true))) {
                        if (!TieArcBranchInk.outwardStems(
                                gray,
                                width,
                                height,
                                left,
                                right,
                                strokeCenters,
                                side,
                                gap,
                                inkLimit)) return true;
                    }
                }
        return false;
    }

    private static boolean hasFlatTieStrokeCenter(float[] centers, float gap) {
        float[] finite = new float[centers.length];
        int count = 0;
        double sumX = 0, sumY = 0, sumXX = 0, sumXY = 0;
        for (int i = 0; i < centers.length; i++)
            if (Float.isFinite(centers[i])) {
                count++;
                sumX += i;
                sumY += centers[i];
                sumXX += (double) i * i;
                sumXY += (double) i * centers[i];
            }
        if (count < 34) return false;
        // A photographed straight rule can incline. Compare its actual stroke
        // center with a fitted line so changing edges cannot manufacture a bow.
        double divisor = count * sumXX - sumX * sumX;
        if (divisor <= 0) return false;
        double slope = (count * sumXY - sumX * sumY) / divisor,
                intercept = (sumY - slope * sumX) / count;
        int at = 0;
        for (int i = 0; i < centers.length; i++)
            if (Float.isFinite(centers[i]))
                finite[at++] = (float) (centers[i] - intercept - slope * i);
        java.util.Arrays.sort(finite, 0, count);
        return finite[count - 1 - count / 10] - finite[count / 10] <= Math.max(1.5f, gap * .13f);
    }

    private static final float[] PARABOLIC_TIE_PROFILE = parabolicTieProfile();

    private static float[] parabolicTieProfile() {
        float[] result = new float[50];
        for (int sample = 0; sample < result.length; sample++) {
            float t = (sample + .5f) / 50f;
            result[sample] = 4 * t * (1 - t);
        }
        return result;
    }

    private static final float[] FLAT_TIE_PROFILE = flatTieProfile();

    /** Long engraved ties have steep shoulders and a broad crest, rather than a parabola. */
    private static float[] flatTieProfile() {
        float[] result = new float[50];
        for (int i = 0; i < result.length; i++) {
            double t = (i + .5) / result.length, low = 0, high = 1;
            for (int step = 0; step < 16; step++) {
                double u = (low + high) * .5,
                        v = 1 - u,
                        x = 3 * .05 * v * v * u + 3 * .95 * v * u * u + u * u * u;
                if (x < t) low = u;
                else high = u;
            }
            double u = (low + high) * .5;
            result[i] = (float) (4 * u * (1 - u));
        }
        return result;
    }

    private static ArcStats arcStats(
            byte[] labels,
            byte[] gray,
            int width,
            int height,
            int left,
            int right,
            int top,
            int bottom) {
        int safeTop = Math.max(0, top), safeBottom = Math.min(height - 1, bottom);
        int pixels = 0, columns = 0, minX = right + 1, maxX = left - 1;
        int minY = safeBottom + 1, maxY = safeTop - 1;
        boolean rawAvailable = gray != null && gray.length == labels.length;
        boolean[] horizontalRows = new boolean[Math.max(0, safeBottom - safeTop + 1)];
        if (rawAvailable)
            for (int y = safeTop; y <= safeBottom; y++) {
                int dark = 0;
                for (int x = left; x <= right; x++) if ((gray[y * width + x] & 0xff) <= 165) dark++;
                horizontalRows[y - safeTop] = dark >= (right - left + 1) * .85f;
            }
        float[] columnCenters = new float[right - left + 1];
        java.util.Arrays.fill(columnCenters, Float.NaN);
        for (int x = left; x <= right; x++) {
            int columnPixels = 0, columnY = 0;
            for (int y = safeTop; y <= safeBottom; y++) {
                byte label = labels[y * width + x];
                boolean semanticArc =
                        label == OmrMeasurePostProcessor.SYMBOL
                                || label == OmrMeasurePostProcessor.STEM_OR_REST
                                || label == OmrMeasurePostProcessor.CLEF_OR_KEY;
                boolean rawArc =
                        rawAvailable
                                && (gray[y * width + x] & 0xff) <= 165
                                && !horizontalRows[y - safeTop]
                                && label != OmrMeasurePostProcessor.STAFF
                                && label != OmrMeasurePostProcessor.NOTEHEAD;
                if (rawAvailable ? !rawArc : !semanticArc) continue;
                columnPixels++;
                columnY += y;
                pixels++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            if (columnPixels > 0) {
                columns++;
                columnCenters[x - left] = columnY / (float) columnPixels;
            }
        }
        float curvature = arcCurvature(columnCenters, left, minX, maxX);
        return new ArcStats(pixels, columns, minX, maxX, minY, maxY, curvature);
    }

    private static float arcCurvature(float[] centers, int left, int minX, int maxX) {
        if (maxX <= minX) return 0f;
        float span = maxX - minX;
        float leftSum = 0f, rightSum = 0f, middleSum = 0f;
        int leftCount = 0, rightCount = 0, middleCount = 0;
        for (int x = minX; x <= maxX; x++) {
            float y = centers[x - left];
            if (!Float.isFinite(y)) continue;
            float position = (x - minX) / span;
            if (position <= .28f) {
                leftSum += y;
                leftCount++;
            } else if (position >= .72f) {
                rightSum += y;
                rightCount++;
            } else if (position >= .38f && position <= .62f) {
                middleSum += y;
                middleCount++;
            }
        }
        if (leftCount == 0 || rightCount == 0 || middleCount == 0) return 0f;
        float middle = middleSum / middleCount;
        float fromLeft = middle - leftSum / leftCount;
        float fromRight = middle - rightSum / rightCount;
        // A real tie bows away from BOTH ends, then returns. Averaging the two endpoints
        // accepted a one-sided step where a sixteenth's extra beam begins halfway across
        // an eighth/sixteenth pair (Feliz). Straight/sloped beams and steps are not arcs.
        if (fromLeft * fromRight <= 0f) return 0f;
        return Math.min(Math.abs(fromLeft), Math.abs(fromRight));
    }

    private static boolean plausibleArc(ArcStats arc, int left, int right, float gap) {
        if (arc.pixels < Math.max(6, Math.round(gap * .8f)) || arc.columns <= 0) return false;
        int span = right - left + 1;
        int inkSpan = arc.maxX - arc.minX + 1;
        int verticalSpan = arc.maxY - arc.minY + 1;
        int endpointTolerance = Math.max(Math.round(gap * 2.2f), Math.round(span * .28f));
        return inkSpan >= Math.max(gap * 1.8f, span * .48f)
                && arc.columns >= Math.max(gap * 1.3f, span * .24f)
                && arc.minX - left <= endpointTolerance
                && right - arc.maxX <= endpointTolerance
                && verticalSpan >= Math.max(2, Math.round(gap * .12f))
                && verticalSpan <= gap * 3f
                && arc.curvature >= Math.max(.65f, gap * .07f);
    }

    private static float median(float[] values) {
        float[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return (sorted[(sorted.length - 1) / 2] + sorted[sorted.length / 2]) * .5f;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static final class Staff {
        final float top, bottom, gap;
        float pitchBottom, pitchGap, pitchSlope;
        boolean printedPhase, printedSlope;
        StaffPitchTrack pitchTrack;
        int index, count = 1;

        Staff(float top, float bottom, float gap) {
            this.top = top;
            this.bottom = bottom;
            this.gap = gap;
            this.pitchBottom = bottom;
            this.pitchGap = gap;
        }
    }

    record Analysis(
            List<ScoreNoteEvent> notes,
            List<ScoreKeyChange> keyChanges,
            List<ScoreRestEvent> rests) {
        Analysis(List<ScoreNoteEvent> notes, List<ScoreKeyChange> keyChanges) {
            this(notes, keyChanges, List.of());
        }

        Analysis {
            notes = notes == null ? List.of() : List.copyOf(notes);
            keyChanges = keyChanges == null ? List.of() : List.copyOf(keyChanges);
            rests = rests == null ? List.of() : List.copyOf(rests);
        }
    }

    private record SignatureGlyph(float x, int accidental, float pitchY) {
        SignatureGlyph(float x, int accidental) {
            this(x, accidental, Float.NaN);
        }
    }

    private record Component(
            int area, int minX, int maxX, int minY, int maxY, float centerX, float centerY) {}

    private record AccidentalCandidate(Component component, byte label) {
        boolean matches(byte value) {
            return label == 0
                    ? value == OmrMeasurePostProcessor.SYMBOL
                            || value == OmrMeasurePostProcessor.CLEF_OR_KEY
                    : label == value;
        }
    }

    private record AccidentalStateKey(
            int measureIndex, int staffIndex, int staffCount, int staffStep) {}

    private record PitchKey(int staffIndex, int staffCount, int staffStep) {}

    private record CrossHeadIdentity(int x, int y, int staffIndex, int staffCount) {}

    private record DetectedNote(ScoreNoteEvent event, Component head, float staffGap) {}

    private record ArcStats(
            int pixels, int columns, int minX, int maxX, int minY, int maxY, float curvature) {}
}
