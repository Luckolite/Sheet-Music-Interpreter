// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Converts the segmentation model's staff/notehead mask into a deliberately transparent diagnostic melody. */
final class OmrScoreInterpreter {
    /** Two staves in one grand staff are close; consecutive compact violin systems are not. */
    private static final float MAX_STAFFS_IN_SYSTEM_SEPARATION_GAPS = 6.75f;
    /** D7/E7 in treble clef reaches roughly six to six-and-a-half staff gaps above the stave. */
    private static final float MAX_HEAD_LEDGER_GAPS = 6.75f;

    private OmrScoreInterpreter() { }

    static List<PlayingTechniqueDetector.Staff> techniqueStaffs(byte[] labels,byte[] gray,int width,int height,
                                                               List<MeasureRegion> measures) {
        List<PlayingTechniqueDetector.Staff> result=new ArrayList<>();
        for(Staff staff:findStaffs(labels,gray,width,height,measures))result.add(new PlayingTechniqueDetector.Staff(
                staff.top,staff.bottom,staff.gap,staff.index,staff.count));
        return result;
    }

    static List<ScoreNoteEvent> extract(byte[] labels, int width, int height,
                                        List<MeasureRegion> measures) {
        return extract(labels, null, width, height, measures);
    }

    static List<ScoreNoteEvent> extract(byte[] labels, byte[] gray, int width, int height,
                                        List<MeasureRegion> measures) {
        return analyze(labels, gray, width, height, measures).notes();
    }

    static Analysis analyze(byte[] labels, byte[] gray, int width, int height,
                            List<MeasureRegion> measures) {
        if (labels == null || labels.length != width * height || width <= 0 || height <= 0
                || measures == null || measures.isEmpty()) return new Analysis(List.of(), List.of());
        List<Staff> staffs = findStaffs(labels, gray, width, height, measures);
        if (staffs.isEmpty()) return new Analysis(List.of(), List.of());
        List<Component> rawHeadComponents = findComponents(labels, width, height,
                OmrMeasurePostProcessor.NOTEHEAD);
        List<Component> clefOrKeyComponents = findComponents(labels, width, height,
                OmrMeasurePostProcessor.CLEF_OR_KEY);
        List<Component> symbolComponents = findComponents(labels, width, height,
                OmrMeasurePostProcessor.SYMBOL);
        rawHeadComponents.removeIf(head->isRoundedHeaderMeter(labels,gray,width,height,
                head,staffs,clefOrKeyComponents));
        rawHeadComponents.removeIf(head -> commonTimeGlyphBounds(labels, gray, width, height,
                head, staffs, clefOrKeyComponents) != null);
        rawHeadComponents.removeIf(head -> isTempoUnitHead(gray, width, height, head, staffs));
        rawHeadComponents.removeIf(head -> isHeavyRestBarFragment(gray, width, height, head, staffs));
        rawHeadComponents.removeIf(head -> isWholeMeasureRestHead(gray,width,height,head,staffs));
        rawHeadComponents.removeIf(head -> isThickBarlineHead(gray,width,height,head,staffs));
        rawHeadComponents.removeIf(head -> isHeaderFlatHead(labels,gray,width,height,head,staffs,clefOrKeyComponents));
        rawHeadComponents.removeIf(head -> isOwnedHeaderCrossbar(labels,gray,width,height,head,staffs,rawHeadComponents,clefOrKeyComponents,symbolComponents));
        rawHeadComponents.removeIf(head -> isForteHookHead(gray,width,height,head,staffs,symbolComponents));
        rawHeadComponents.removeIf(head -> isZigzagOrnamentHead(labels,gray,width,height,head,staffs,rawHeadComponents));
        rawHeadComponents.removeIf(head -> isTrebleTailHead(labels,width,height,head,staffs,clefOrKeyComponents));
        List<Component> headComponents = splitStackedHeads(labels, gray, width, height,
                rawHeadComponents, staffs);
        List<Component> heads = new ArrayList<>();
        List<Component> rejectedSlurHeads = new ArrayList<>();
        for (Component head : headComponents) {
            Staff staff = staffForHead(labels, gray, width, height, staffs, head);
            if (staff != null && plausibleHead(head, staff.gap)) {
                if(flatStemlessFragment(gray,width,height,head,staff.gap))rejectedSlurHeads.add(head);
                else heads.add(head);
            }
        }
        List<Component> rejectedBeamHeads=beamJunctionHeads(gray,width,height,heads,staffs);
        rejectedBeamHeads.addAll(mergedBeamInteriorHeads(gray,width,height,heads,staffs));
        rejectedBeamHeads.addAll(singleBeamInteriorHeads(gray,width,height,heads,staffs));
        heads.removeAll(rejectedBeamHeads);
        byte[] beamLabels=withoutBeamHeadIslands(labels,width,rejectedBeamHeads);
        heads.removeAll(entranceStrokeFragments(gray,width,height,heads,staffs));
        List<Component> shortTies=shortTieBowlHeads(labels,gray,width,height,heads,staffs);
        heads.removeAll(shortTies);
        rejectedSlurHeads.addAll(shortTies);
        // A bright paper halo can enlarge a printed augmentation dot enough for the model to label it
        // as a second plausible notehead. Demote only small, stemless components immediately to
        // the right of a substantially larger head; grace notes retain their attached stem.
        List<Component> demotedDotHeads = augmentationDotHeads(labels, gray, width, height, heads, staffs);
        demotedDotHeads.addAll(articulationDotHeads(labels, gray, width, height, heads, staffs));
        heads.removeAll(demotedDotHeads);
        heads.removeAll(stemSlashFragments(gray, width, height, heads, staffs));
        Map<Component,int[]> detachedTremolos=new HashMap<>();
        for(Component head:heads) {
            Staff staff=nearestHeadStaff(staffs,head.centerY);
            if(staff==null||head.maxX-head.minX+1<staff.gap*1.4f
                    ||!hasOpenCenter(labels,gray,width,height,head,staff.gap)
                    ||attachedRawStem(gray,width,height,head,staff.gap)!=null)continue;
            int[] mark=detachedTremolo(gray,width,height,head,staff.gap);
            if(mark!=null)detachedTremolos.put(head,mark);
        }
        heads.removeIf(head->detachedTremolos.entrySet().stream().anyMatch(entry->{
            int[] mark=entry.getValue();return head!=entry.getKey()
                    &&head.minX>=mark[0]&&head.maxX<=mark[1]
                    &&head.minY>=mark[2]&&head.maxY<=mark[3];
        }));
        heads.removeIf(head -> isHeaderMeterDigit(labels,gray,width,height,head,staffs,clefOrKeyComponents));
        List<Component> dotCandidates = new ArrayList<>(symbolComponents);
        for (Component component : headComponents) if (!heads.contains(component))
            dotCandidates.add(component);
        List<AccidentalCandidate> accidentalCandidates = new ArrayList<>();
        for (Component component : clefOrKeyComponents)
            accidentalCandidates.add(new AccidentalCandidate(component,
                    OmrMeasurePostProcessor.CLEF_OR_KEY));
        for (Component component : symbolComponents)
            accidentalCandidates.add(new AccidentalCandidate(component,
                    OmrMeasurePostProcessor.SYMBOL));
        // A repeated key signature may sit close enough to the first note to look local.
        // Keep its recognized glyphs out of both semantic and raw accidental recovery.
        List<Component> headerAccidentals = new ArrayList<>();
        detectKeyChangesWithHeaders(labels,gray,width,height,measures,staffs,
                accidentalCandidates,heads,headerAccidentals);
        List<AccidentalCandidate> localAccidentals = new ArrayList<>(joinLocalAccidentalFragments(
                labels, gray, width, height, accidentalCandidates, staffs));
        localAccidentals.removeIf(candidate->headerAccidentals.stream().anyMatch(header->
                candidate.component.minX>=header.minX && candidate.component.maxX<=header.maxX
                && candidate.component.minY>=header.minY && candidate.component.maxY<=header.maxY));
        localAccidentals.removeIf(candidate->attachedGraceFlag(labels,gray,width,height,candidate,heads,staffs));
        localAccidentals.removeAll(noteParentheses(gray,width,height,localAccidentals,heads,staffs));
        localAccidentals=splitTouchingChordAccidentals(labels,width,height,localAccidentals,heads,staffs);
        List<Component> accidentalInk = new ArrayList<>();
        for (AccidentalCandidate candidate : localAccidentals) {
            Staff staff = nearestHeadStaff(staffs, candidate.component.centerY);
            if (staff != null && (isFlatGlyph(labels,width,height,candidate,staff.gap)
                    || isNaturalGlyph(labels,width,height,candidate,staff.gap)
                    || isSharpGlyph(labels,width,height,candidate,staff.gap)))
                accidentalInk.add(candidate.component);
        }
        List<Component> roundedLedgerGraces=roundedLedgerGraceHeads(labels,gray,width,height,heads,staffs);
        List<Component> bowMarks=new ArrayList<>();
        for(Component candidate:heads) {
            Staff staff=nearestHeadStaff(staffs,candidate.centerY);
            if(staff==null||candidate.area>staff.gap*staff.gap*1.1f)continue;
            for(Component other:heads) {
                float dy=other.centerY-candidate.centerY;
                if(other==candidate||other.area<candidate.area*1.35f||other.maxX-other.minX+1<staff.gap*.9f
                        ||nearestHeadStaff(staffs,other.centerY)!=staff
                        ||Math.abs(other.centerX-candidate.centerX)>staff.gap*.7f
                        ||dy<staff.gap*1.2f||dy>staff.gap*4f)continue;
                if(NoteArticulationDetector.upBowAtHead(gray,width,height,candidate.minX,candidate.minY,
                        candidate.maxX,candidate.maxY,staff.gap)) {bowMarks.add(candidate);break;}
            }
        }
        // Bow direction changes technique, not the pitch or accent of the following note.
        heads.removeAll(bowMarks);
        Map<Component,Integer> recoveredArticulations=new HashMap<>();
        List<Component> angularMarks=new ArrayList<>();
        for(Component candidate:heads) {
            Staff staff=nearestHeadStaff(staffs,candidate.centerY);
            if(staff==null||candidate.area>staff.gap*staff.gap*.65f)continue;
            Component owner=null;float distance=Float.MAX_VALUE;
            for(Component other:heads) {
                float dy=Math.abs(other.centerY-candidate.centerY);
                if(other==candidate||other.area<candidate.area*2||other.maxX-other.minX+1<staff.gap*.9f
                        ||Math.abs(other.centerX-candidate.centerX)>staff.gap*.7f
                        ||dy<staff.gap*1.2f||dy>staff.gap*6f||dy>=distance)continue;
                if(NoteArticulationDetector.marcatoAtHead(gray,width,height,candidate.minX,candidate.minY,
                        candidate.maxX,candidate.maxY,staff.gap,candidate.centerY<other.centerY)) {owner=other;distance=dy;}
            }
            if(owner!=null) {
                angularMarks.add(candidate);Staff ownerStaff=nearestHeadStaff(staffs,owner.centerY);
                for(Component chord:heads)if(Math.abs(chord.centerX-owner.centerX)<staff.gap*.45f
                        &&nearestHeadStaff(staffs,chord.centerY)==ownerStaff)
                    recoveredArticulations.merge(chord,NoteArticulation.MARCATO,(a,b)->a|b);
            }
        }
        heads.removeAll(angularMarks);
        List<DetectedNote> detected = new ArrayList<>();
        for (Component head : heads) {
            Staff staff = staffForHead(labels, gray, width, height, staffs, head);
            if (staff == null) continue;
            if (isHeavyRestCount(gray,width,height,head,staff)) continue;
            if (isUnpitchedCrossHead(gray,width,height,head,staff)) continue;
            // Heads far outside a staff require printed ledger lines. A nearby
            // text stroke can look like a stem, so a semantic stem alone cannot
            // promote a tempo digit or other text into an extreme pitch.
            // Cross-staff stems may assign a note to the other voice. Validate
            // its printed position against the nearest physical staff instead.
            Staff physicalStaff = printedLedgerOwner(gray,width,height,staffs,head);
            if(physicalStaff==null)physicalStaff=nearestHeadStaff(staffs,head.centerY);
            if (gray != null && physicalStaff != null
                    && (head.centerY < physicalStaff.top-physicalStaff.gap*1.8f
                    || head.centerY > physicalStaff.bottom+physicalStaff.gap*1.8f)
                    && !hasHeadLedgerSupport(labels,gray,width,height,head,physicalStaff,roundedLedgerGraces.contains(head))) continue;
            float normalizedX = head.centerX() / width;
            float normalizedY = head.centerY() / height;
            int measureIndex = containingMeasureForStaff(measures, normalizedX, normalizedY,
                    staff, height);
            if (measureIndex < 0) continue;
            MeasureRegion measure = measures.get(measureIndex);
            // Repeated geometry represents an expanded multi-measure rest. Its printed count
            // can look like a hollow notehead, but every logical bar in that span is silent.
            if ((measureIndex > 0 && measure.equals(measures.get(measureIndex - 1)))
                    || (measureIndex + 1 < measures.size() && measure.equals(measures.get(measureIndex + 1)))) continue;
            float position = (normalizedX - measure.left()) / Math.max(0.0001f,
                    measure.right() - measure.left());
            float[] localPitch = localStaffPitch(labels, gray, width, height, staff, head);
            float localGap=localPitch[1];
            float localBottom=printedLedgerBottom(gray,width,height,head,localPitch[0],localGap);
            int step = printedPitchStep(gray,width,height,head,localBottom,localGap);
            int beamCount = detectBeamCount(beamLabels, gray, width, height, head, staff);
            int[] tremolo = tremoloStrokeCounts(gray,width,height,head,staff.gap,heads);
            beamCount = Math.max(0,beamCount-tremolo[1]);
            if(tremolo[0]>0)beamCount=Math.max(beamCount,
                    beamsBeyondTremolo(gray,beamLabels,width,height,head,staff));
            float unbeamedDuration = detectUnbeamedDuration(labels, gray, width, height,
                    head, staff.gap, beamCount);
            if(detachedTremolos.containsKey(head))unbeamedDuration=ScoreNoteEvent.DURATION_WHOLE;
            // Beamed notes cannot have open heads. If a staff line, slur, or artwork edge near
            // an open half/whole head looked like a beam, retain the notehead's stronger direct
            // evidence instead of collapsing the sustained passage into eighths/sixteenths.
            if (unbeamedDuration >= ScoreNoteEvent.DURATION_HALF) beamCount = 0;
            int augmentationDots = countAugmentationDots(dotCandidates, head, staff.gap,
                    gray, width, height,unbeamedDuration>=ScoreNoteEvent.DURATION_HALF,accidentalInk);
            if(augmentationDots>0&&beamCount>0&&hasHollowUnisonToRight(labels,gray,width,height,head,heads,staff.gap))
                augmentationDots=0;
            int writtenAccidental = detectWrittenAccidental(labels, width, height,
                    localAccidentals, head, localPitch[1]);
            if(writtenAccidental==ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                    &&rawSharpFromSeed(gray,width,height,localAccidentals,head,localPitch[1]))
                writtenAccidental=ScoreNoteEvent.ACCIDENTAL_SHARP;
            if(writtenAccidental==ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                    &&rawFlatFromBowl(gray,width,height,withoutRecognizedSharps(labels,width,height,localAccidentals,localPitch[1]),head,localPitch[1]))
                writtenAccidental=ScoreNoteEvent.ACCIDENTAL_FLAT;
            if((writtenAccidental==ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                    ||writtenAccidental==ScoreNoteEvent.ACCIDENTAL_FLAT||writtenAccidental==ScoreNoteEvent.ACCIDENTAL_SHARP)
                    &&rawNaturalFromCrossbars(gray,width,height,localAccidentals,head,localPitch[1]))
                writtenAccidental=ScoreNoteEvent.ACCIDENTAL_NATURAL;
            if(writtenAccidental==ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                    &&naturalFromUpperSpine(labels,gray,width,height,localAccidentals,head,localPitch[1]))
                writtenAccidental=ScoreNoteEvent.ACCIDENTAL_NATURAL;
            ScoreNoteEvent event = new ScoreNoteEvent(measureIndex, clamp(position),
                    Math.max(-32, Math.min(32, step)), staff.index, staff.count,
                    clamp(normalizedY), false, augmentationDots, beamCount,
                    writtenAccidental, unbeamedDuration);
            if(tremolo[0]>0)event=event.withArticulations(NoteOrnament.withTremolo(
                    event.articulations(),beamCount+tremolo[0]));
            if(detachedTremolos.containsKey(head))event=event.withArticulations(
                    NoteOrnament.withTremolo(event.articulations(),3));
            event=event.withArticulations(event.articulations()|recoveredArticulations.getOrDefault(head,0));
            List<Component> unison=sideBySideUnison(labels,gray,width,height,head,staff.gap);
            if(!unison.isEmpty()) {
                // Opposite stems share a printed pitch/attack but have separate durations.
                for(int partIndex=0;partIndex<unison.size();partIndex++) {
                    Component part=unison.get(partIndex);
                    int partBeams=partIndex==0?detectBeamCount(beamLabels,gray,width,height,part,staff):0;
                    int[] partTremolo=tremoloStrokeCounts(gray,width,height,part,staff.gap,heads);
                    if(partIndex==0&&partTremolo[0]>0)partBeams=Math.max(
                            Math.max(0,partBeams-partTremolo[1]),
                            beamsBeyondTremolo(gray,beamLabels,width,height,part,staff));
                    float partDuration=partIndex==0?(partBeams>0?0:1):2;
                    int partDots=partIndex==0?0:countAugmentationDots(dotCandidates,part,staff.gap,gray,width,height,true);
                    var separate=new ScoreNoteEvent(measureIndex,clamp(position),step,staff.index,staff.count,
                            clamp(normalizedY),false,partDots,partBeams,writtenAccidental,partDuration);
                    if(partTremolo[0]>0)separate=separate.withArticulations(
                            NoteOrnament.withTremolo(0,partBeams+partTremolo[0]));
                    detected.add(new DetectedNote(separate,part,staff.gap));
                }
                continue;
            }
            List<Component> seconds=sideBySideSeconds(labels,width,head,staff.gap);
            if(seconds.isEmpty())detected.add(new DetectedNote(event, head, staff.gap));
            else for(Component part:seconds) {
                int partStep=printedPitchStep(gray,width,height,part,localBottom,localGap);
                // Displaced seconds share the stem/attack, despite their two horizontal centres.
                var chord=new ScoreNoteEvent(measureIndex,clamp(position),partStep,staff.index,staff.count,
                        clamp(part.centerY/height),false,augmentationDots,beamCount,writtenAccidental,unbeamedDuration)
                        .withArticulations(event.articulations());
                detected.add(new DetectedNote(chord,part,staff.gap));
            }
        }
        detected = alignDisplacedSeconds(detected,gray,width,height);
        detected = applyPrintedClefs(detected, staffs, clefOrKeyComponents, labels, gray, width, height);
        detected.sort(Comparator.comparingInt((DetectedNote note) -> note.event.measureIndex())
                .thenComparingDouble(note -> note.event.positionInMeasure())
                .thenComparingInt(note -> note.event.staffIndex())
                .thenComparingInt(note -> note.event.staffStep()));
        List<DetectedNote> joined = applyAccidentalState(markTieContinuations(tieLabelsWithoutSlurHeads(labels,width,rejectedSlurHeads,heads), gray,
                width, height, removeSplitDuplicates(detected)));
        logHeadCoverage(staffs, rawHeadComponents, headComponents, heads, demotedDotHeads, joined);
        List<ScoreNoteEvent> result = new ArrayList<>(joined.size());
        for (DetectedNote note : joined) result.add(note.event);
        List<ScoreNoteEvent> notation=TripletRhythmDetector.withoutNumeralHeads(result,measures,gray,width,height);
        if(notation.size()!=result.size()) {
            for(DetectedNote note:joined)if(!notation.contains(note.event))heads.remove(note.head);
            joined.removeIf(note->!notation.contains(note.event));
            result.clear();result.addAll(notation);
        }
        List<SixteenthRestDetector.Staff> restStaffs = new ArrayList<>();
        for (Staff staff : staffs) restStaffs.add(new SixteenthRestDetector.Staff(
                staff.pitchBottom - staff.pitchGap * 4, staff.pitchBottom, staff.pitchGap,
                staff.index, staff.count, staff.pitchTrack));
        List<ScoreRestEvent> rests = SixteenthRestDetector.detect(gray, width, height, measures, restStaffs, result);
        // Small stemless model heads can be augmentation dots of an independently
        // recognized rest. Re-read those dots without letting the mistaken head
        // claim ownership, but require the same rest to have survived the first pass.
        List<DetectedNote> compactDots=new ArrayList<>();
        List<DetectedNote> restBodyHeads=new ArrayList<>();
        for(DetectedNote note:joined) {
            Component h=note.head;float gap=note.staffGap;
            if(gray==null)continue;
            int[] stem=attachedRawStem(gray,width,height,h,gap);
            if(stem==null&&h.maxX-h.minX+1<=gap*.7f&&h.maxY-h.minY+1<=gap*.7f
                    &&h.area<=gap*gap*.32f)compactDots.add(note);
            // The zigzag body can generate a larger prediction than an
            // augmentation dot. Its complete raw shape supplies separate
            // evidence, so do not make it pass the tiny-dot size gate.
            // A quarter-rest zigzag can resemble a stem when several blank
            // rows are bridged. Preserve continuous stems, but let complete
            // rest recognition adjudicate a compact head on that broken path.
            if(h.maxX-h.minX+1<=gap*1.05f&&h.maxY-h.minY+1<=gap*1.05f
                    &&h.area<=gap*gap*.65f
                    &&(stem==null||attachedRawStem(gray,width,height,h,gap,1)==null))restBodyHeads.add(note);
        }
        if(!compactDots.isEmpty()||!restBodyHeads.isEmpty()) {
            List<ScoreNoteEvent> owners=new ArrayList<>(result);
            for(DetectedNote note:compactDots)owners.remove(note.event);
            for(DetectedNote note:restBodyHeads)owners.remove(note.event);
            var evidence=SixteenthRestDetector.detectWithDots(gray,width,height,measures,restStaffs,owners);
            List<DetectedNote> removed=new ArrayList<>();
            List<ScoreRestEvent> verifiedBodies=new ArrayList<>();
            // An independently recognized complete quarter-rest body can
            // establish ownership even when its false head blocked the first
            // pass. Continuous stems remain excluded from the body candidates.
            for(DetectedNote note:restBodyHeads)for(ScoreRestEvent rest:evidence.rests()) {
                if(rest.durationBeats()<1||rest.durationBeats()>1.75
                        ||rest.measureIndex()!=note.event.measureIndex()
                        ||rest.staffIndex()!=note.event.staffIndex()
                        ||rest.staffCount()!=note.event.staffCount())continue;
                MeasureRegion region=measures.get(rest.measureIndex());
                float x=(region.left()+rest.positionInMeasure()*(region.right()-region.left()))*width;
                if(Math.abs(x-note.head.centerX)<=note.staffGap*.4f
                        &&Math.abs(rest.pageY()*height-note.head.centerY)<=rest.pageHeight()*height*.5f) {
                    removed.add(note);verifiedBodies.add(rest);break;
                }
            }
            for(DetectedNote note:compactDots)for(var dot:evidence.dots()) {
                ScoreRestEvent parent=dot.rest();
                if(parent.measureIndex()!=note.event.measureIndex()||parent.staffIndex()!=note.event.staffIndex()
                        ||parent.staffCount()!=note.event.staffCount()
                        ||Math.abs(dot.x()-note.head.centerX)>note.staffGap*.3f
                        ||Math.abs(dot.y()-note.head.centerY)>note.staffGap*.3f)continue;
                boolean verified=verifiedBodies.contains(parent)||rests.stream().anyMatch(rest->rest.measureIndex()==parent.measureIndex()
                        &&rest.staffIndex()==parent.staffIndex()&&rest.staffCount()==parent.staffCount()
                        &&Math.abs(rest.positionInMeasure()-parent.positionInMeasure())<.005f
                        &&Math.abs(rest.pageY()-parent.pageY())<.005f);
                if(verified){removed.add(note);break;}
            }
            if(!removed.isEmpty()) {
                joined.removeAll(removed);
                for(DetectedNote note:removed){result.remove(note.event);heads.remove(note.head);}
                rests=SixteenthRestDetector.detect(gray,width,height,measures,restStaffs,result);
            }
        }
        List<ScoreKeyChange> keyChanges = detectKeyChanges(labels, gray, width, height, measures,
                staffs, accidentalCandidates, joined.stream().map(n -> n.head).collect(java.util.stream.Collectors.toList()));
        List<ScoreNoteEvent> withRests = new ArrayList<>();
        for (DetectedNote note : joined) {
            ScoreNoteEvent event = note.event;
            float next = 1.01f;
            for (ScoreNoteEvent other : result) if (other.measureIndex() == event.measureIndex()
                    && other.staffIndex() == event.staffIndex() && other.staffCount() == event.staffCount()
                    && other.positionInMeasure() > event.positionInMeasure() + .018f)
                next = Math.min(next, other.positionInMeasure());
            float silence = 0;
            boolean hasSixteenthRest=false;
            for (ScoreRestEvent rest : rests) if (rest.measureIndex() == event.measureIndex()
                    && rest.staffIndex() == event.staffIndex() && rest.staffCount() == event.staffCount()
                    && rest.positionInMeasure() > event.positionInMeasure() && rest.positionInMeasure() < next
                    &&(!ScoreNoteTiming.hasIndependentSustain(event)
                    ||rest.positionInMeasure()>event.positionInMeasure()+.018f)) {
                silence += (float)rest.durationBeats();
                hasSixteenthRest|=rest.durationBeats()==.25;
            }
            float leading = 0;
            boolean first = result.stream().noneMatch(other->other.measureIndex()==event.measureIndex()
                    &&other.staffIndex()==event.staffIndex()&&other.staffCount()==event.staffCount()
                    &&other.positionInMeasure()<event.positionInMeasure()-.018f);
            if(first)for(ScoreRestEvent rest:rests)if(rest.measureIndex()==event.measureIndex()
                    &&rest.staffIndex()==event.staffIndex()&&rest.staffCount()==event.staffCount()
                    &&rest.positionInMeasure()<event.positionInMeasure()
                    &&(!ScoreNoteTiming.hasIndependentSustain(event)
                    ||rest.positionInMeasure()<event.positionInMeasure()-.018f))leading+=(float)rest.durationBeats();
            // The first moving attack can follow a printed rest while another voice
            // already holds a half note in that rest's column.
            if(!first&&!ScoreNoteTiming.hasIndependentSustain(event)
                    &&result.stream().noneMatch(other->other.measureIndex()==event.measureIndex()
                    &&other.staffIndex()==event.staffIndex()&&other.staffCount()==event.staffCount()
                    &&other.positionInMeasure()<event.positionInMeasure()-.018f
                    &&!ScoreNoteTiming.hasIndependentSustain(other))) {
                for(ScoreRestEvent rest:rests)if(rest.measureIndex()==event.measureIndex()
                        &&rest.staffIndex()==event.staffIndex()&&rest.staffCount()==event.staffCount()
                        &&rest.positionInMeasure()<event.positionInMeasure()-.018f
                        &&result.stream().anyMatch(other->other.measureIndex()==event.measureIndex()
                        &&other.staffIndex()==event.staffIndex()&&other.staffCount()==event.staffCount()
                        &&ScoreNoteTiming.hasIndependentSustain(other)
                        &&Math.abs(other.positionInMeasure()-rest.positionInMeasure())<=.018f))
                    leading+=(float)rest.durationBeats();
            }
            int beams = event.beamCount();
            if (hasSixteenthRest && beams <= 2 && !ScoreNoteTiming.hasIndependentSustain(event)) {
                int flags = rawDetachedFlags(gray, labels, width, height, note.head, note.staffGap);
                if (flags > 0) beams = flags;
            }
            withRests.add(new ScoreNoteEvent(event.measureIndex(), event.positionInMeasure(), event.staffStep(),
                    event.staffIndex(), event.staffCount(), event.pageY(), event.tiedFromPrevious(),
                    dotsOutsideRests(dotCandidates, note, rests, measures, gray, width, height, accidentalInk),
                    beams, event.writtenAccidental(),
                    beams != event.beamCount() ? 0 : event.unbeamedDurationBeats(), event.tupletDivisor(), silence,
                    event.articulations(), event.clefBottomDiatonic()).withLeadingRest(leading));
        }
        List<NoteArticulationDetector.Anchor> anchors = new ArrayList<>();
        for (DetectedNote note : joined) anchors.add(new NoteArticulationDetector.Anchor(
                note.head.centerX, note.head.centerY, note.staffGap,
                staffs.indexOf(staffForHead(labels,gray,width,height,staffs,note.head))));
        int[] marks = NoteArticulationDetector.detect(labels,gray,width,height,anchors);
        for (int i=0;i<withRests.size();i++) withRests.set(i,withRests.get(i).withArticulations(
                withRests.get(i).articulations()|marks[i]));
        markGraceHeads(labels, gray, width, height, joined, withRests);
        for (int i=1;i<joined.size();i++) {
            int prior=i-1;
            while(prior>=0&&ScoreNoteTiming.hasIndependentSustain(joined.get(prior).event))prior--;
            if(prior<0)continue;
            DetectedNote a=joined.get(prior),b=joined.get(i);
            if(a.event.measureIndex()!=b.event.measureIndex()||a.event.staffCount()!=2
                    ||b.event.staffCount()!=2||a.event.staffIndex()==b.event.staffIndex()
                    ||ScoreNoteTiming.hasIndependentSustain(a.event)||ScoreNoteTiming.hasIndependentSustain(b.event))continue;
            // Prove the printed bridge even when another voice overlaps later in the bar.
            // The rhythm reader separately requires a complete shared attack clock.
            boolean interrupted=false;
            for(ScoreNoteEvent n:withRests) if(n.measureIndex()==a.event.measureIndex()) {
                if(ScoreNoteTiming.hasIndependentSustain(n))continue;
                if(n.followingRestBeats()>0||n.leadingRestBeats()>0){interrupted=true;break;}
            }
            if(interrupted)continue;
            if(CrossStaffBeamDetector.connected(gray,width,height,a.head.centerX,a.head.centerY,
                    b.head.centerX,b.head.centerY,(a.staffGap+b.staffGap)/2)) {
                withRests.set(prior,withRests.get(prior).withCrossStaffBeam());
                withRests.set(i,withRests.get(i).withCrossStaffBeam());
            }
        }
        return new Analysis(OpeningMeasureLayout.mark(withRests,rests,measures), keyChanges, rests);
    }

    /** Excludes proven non-note header ink before OCR rest reconciliation, without modifying input masks. */
    static byte[] normalizeHeaderSymbols(byte[] labels, byte[] gray, int width, int height,
                                         List<MeasureRegion> measures) {
        if (labels == null || gray == null || width <= 0 || height <= 0
                || labels.length != (long) width * height || gray.length != labels.length
                || measures == null || measures.isEmpty()) return labels;
        List<Staff> staffs = findStaffs(labels, gray, width, height, measures);
        List<Component> heads = findComponents(labels, width, height, OmrMeasurePostProcessor.NOTEHEAD);
        List<Component> glyphs = findComponents(labels, width, height, OmrMeasurePostProcessor.CLEF_OR_KEY);
        List<Component> symbols = findComponents(labels, width, height, OmrMeasurePostProcessor.SYMBOL);
        byte[] result = labels;
        for (Component head : heads) {
            int[] bounds = commonTimeGlyphBounds(labels, gray, width, height, head, staffs, glyphs);
            if (bounds == null && isTempoUnitHead(gray, width, height, head, staffs))
                bounds = new int[]{head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null && isRoundedHeaderMeter(labels, gray, width, height, head, staffs, glyphs))
                bounds = new int[]{head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null && isHeavyRestBarFragment(gray, width, height, head, staffs))
                bounds = new int[]{head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null && isHeaderFlatHead(labels,gray,width,height,head,staffs,glyphs))
                bounds = new int[]{head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null && isOwnedHeaderCrossbar(labels,gray,width,height,head,staffs,heads,glyphs,symbols))
                bounds = new int[]{head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null && isForteHookHead(gray,width,height,head,staffs,symbols))
                bounds = new int[]{head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null && isZigzagOrnamentHead(labels,gray,width,height,head,staffs,heads))
                bounds = new int[]{head.minX, head.maxX, head.minY, head.maxY};
            if (bounds == null) continue;
            for (int y = bounds[2]; y <= bounds[3]; y++) for (int x = bounds[0]; x <= bounds[1]; x++) {
                int at = y * width + x;
                if (result[at] == OmrMeasurePostProcessor.NOTEHEAD) {
                    if (result == labels) result = labels.clone();
                    // Keep source ink for OCR; do not promote removed fragments to accidental candidates.
                    result[at] = 0;
                }
            }
        }
        return normalizeDoubleBarSharps(result,gray,width,height,measures,staffs);
    }

    /** Recover a mixed-label sharp only in a separated slot after a full double bar. */
    private static byte[] normalizeDoubleBarSharps(byte[] labels,byte[] gray,int width,int height,
            List<MeasureRegion> measures,List<Staff> staffs) {
        byte[] result=labels;
        List<Component> noteHeads=findComponents(labels,width,height,(byte)2); List<Component> seeds=new ArrayList<>();
        for(byte label:new byte[]{2,3,5})seeds.addAll(findComponents(labels,width,height,label));
        for(Staff staff:staffs)for(MeasureRegion measure:measures) {
            float cy=(staff.top+staff.bottom)*.5f,gap=staff.pitchGap;
            if(cy<measure.top()*height||cy>measure.bottom()*height)continue;
            float boundary=measure.left()*width;
            if(!hasDoubleBar(labels,gray,width,height,boundary,staff))continue;
            for(Component seed:seeds) {
                if(seed.centerX<boundary+gap*.2f||seed.centerX>boundary+gap*2.1f
                        ||seed.centerY<staff.top-gap*1.7f||seed.centerY>staff.bottom+gap*1.7f)continue;
                int left=Math.max(0,Math.round(seed.centerX-gap*.7f)),right=Math.min(width-1,Math.round(seed.centerX+gap*.7f));
                int top=Math.max(0,Math.round(seed.centerY-gap*2.5f)),bottom=Math.min(height-1,Math.round(seed.centerY+gap*2.5f));
                if(left<boundary+gap*.05f)continue;
                int w=right-left+1,h=bottom-top+1;byte[] ink=new byte[w*h];
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    byte v=labels[(top+y)*width+left+x];
                    if(v!=0&&v!=4)ink[y*w+x]=3;
                }
                // Keep only the largest eight-connected glyph; nearby isolated marks stay unchanged.
                byte[] remaining=ink.clone();int[] queue=new int[ink.length],largest=new int[ink.length];int largestSize=0;
                for(int origin=0;origin<remaining.length;origin++) {
                    if(remaining[origin]==0)continue;
                    int tail=1;queue[0]=origin;remaining[origin]=0;
                    for(int head=0;head<tail;head++) {
                        int at=queue[head],xx=at%w,yy=at/w;
                        for(int ny=Math.max(0,yy-1);ny<=Math.min(h-1,yy+1);ny++)
                            for(int nx=Math.max(0,xx-1);nx<=Math.min(w-1,xx+1);nx++) {
                                int next=ny*w+nx;
                                if(remaining[next]!=0){remaining[next]=0;queue[tail++]=next;}
                            }
                    }
                    if(tail>largestSize){largestSize=tail;System.arraycopy(queue,0,largest,0,tail);}
                }
                java.util.Arrays.fill(ink,(byte)0);
                for(int i=0;i<largestSize;i++)ink[largest[i]]=3;
                int area=0,l=w,r=-1,t=h,b=-1;
                for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(ink[y*w+x]==3){area++;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);}
                if(area==0||l==0||r==w-1||t==0||b==h-1)continue;
                Component glyph=null; for(Component piece:findComponents(ink,w,h,(byte)3))if(glyph==null||piece.area>glyph.area)glyph=piece; if(glyph==null)continue;
                if(!isSharpGlyph(ink,w,h,new AccidentalCandidate(glyph,(byte)3),gap))continue;
                // A first played chord must not be swallowed as a signature.
                float firstHead=Float.POSITIVE_INFINITY;
                for(Component head:noteHeads)if(head.centerX>right&&Math.abs(head.centerY-cy)<gap*5
                        &&head.minY<=staff.bottom+gap*3&&head.maxY>=staff.top-gap*3)
                    firstHead=Math.min(firstHead,head.minX);
                if(!Float.isFinite(firstHead)||firstHead-(left+glyph.centerX)<gap*1.35f)continue;
                if(result==labels)result=labels.clone();
                for(int y=t;y<=b;y++)for(int x=l;x<=r;x++)if(ink[y*w+x]==3)result[(top+y)*width+left+x]=3;
            }
        }
        return result;
    }

    /** A treble clef's round lower tip may be split into the notehead class. */
    private static boolean isTrebleTailHead(byte[] labels,int width,int height,
            Component head,List<Staff> staffs,List<Component> glyphs) {
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.pitchGap;
        if(head.centerY<staff.pitchBottom+gap*.25f||head.centerY>staff.pitchBottom+gap*1.6f
                ||head.maxX-head.minX+1>gap*1.2f||head.maxY-head.minY+1>gap*1.2f)return false;
        for(Component original:glyphs) {
            Component glyph=joinTrebleCurl(joinSmallTrebleFragments(original,glyphs,staff),glyphs,staff);
            float gh=glyph.maxY-glyph.minY+1,gw=glyph.maxX-glyph.minX+1;
            if(gh<gap*4.8f||gh>gap*8.8f||gw<gap*1.25f||gw>gap*3.4f
                    ||glyph.area<gap*gap*1.65f||glyph.minY>=staff.top-gap*.35f
                    ||glyph.maxY<=staff.bottom+gap*.2f
                    ||Math.abs(glyph.centerY-(staff.top+staff.bottom)*.5f)>=gap*1.1f
                    ||head.minX<glyph.minX||head.maxX>glyph.maxX
                    ||head.maxY>glyph.maxY+gap*.3f)continue;
            // Require direct contact with clef ink, not merely a nearby note below it.
            for(int y=head.minY;y<=head.maxY;y++)for(int x=head.minX;x<=head.maxX;x++) {
                if(labels[y*width+x]!=OmrMeasurePostProcessor.NOTEHEAD)continue;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int xx=x+dx,yy=y+dy;
                    if(xx<glyph.minX||xx>glyph.maxX||yy<glyph.minY||yy>glyph.maxY
                            ||xx<0||xx>=width||yy<0||yy>=height)continue;
                    if(labels[yy*width+xx]==OmrMeasurePostProcessor.CLEF_OR_KEY)return true;
                }
            }
        }
        return false;
    }

    /** Small connected header crossbars belong to a symbolic glyph, not a chord.
     * This establishes ownership only; it does not infer an accidental value. */
    private static boolean isOwnedHeaderCrossbar(byte[] labels,byte[] gray,int width,int height,
            Component head,List<Staff> staffs,List<Component> heads,List<Component> clefs,List<Component> symbols) {
        if(gray==null)return false;
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.pitchGap,top=staff.pitchBottom-gap*4;
        if(head.area>gap*gap*.3f||head.maxX-head.minX+1>gap*.8f||head.maxY-head.minY+1>gap*.65f
                ||head.centerX>width*.25f||Math.abs(head.centerY-top)>gap*.8f
                ||attachedRawStem(gray,width,height,head,gap*.65f,2,180)!=null
                ||hasOpenCenter(labels,gray,width,height,head,gap))return false;
        boolean clef=false;
        List<Component> parts=new ArrayList<>(clefs);parts.addAll(symbols);
        for(Component seed:clefs) {
            if(seed.area<gap*gap*.6f||seed.maxX>=head.minX-gap*.6f||head.minX-seed.maxX>gap*4
                    ||Math.abs(seed.centerY-(top+gap*2))>gap*2)continue;
            Component joined=seed;
            for(int pass=0;pass<3;pass++)for(Component part:parts) {
                if(part.minX>=joined.minX&&part.maxX<=joined.maxX&&part.minY>=joined.minY&&part.maxY<=joined.maxY)continue;
                if(part.area<gap*gap*.025f||part.minX<seed.minX-gap*.8f||part.maxX>head.minX-gap*.6f
                        ||part.maxX>seed.maxX+gap*1.2f||part.minY<top-gap*3||part.maxY>staff.pitchBottom+gap*1.8f
                        ||part.minY>joined.maxY+gap*1.2f||part.maxY<joined.minY-gap*1.2f)continue;
                int area=joined.area+part.area;
                joined=new Component(area,Math.min(joined.minX,part.minX),Math.max(joined.maxX,part.maxX),
                        Math.min(joined.minY,part.minY),Math.max(joined.maxY,part.maxY),
                        (joined.centerX*joined.area+part.centerX*part.area)/area,
                        (joined.centerY*joined.area+part.centerY*part.area)/area);
            }
            if(joined.maxY-joined.minY>=gap*5&&joined.maxY-joined.minY<=gap*8.8f
                    &&joined.maxX-joined.minX>=gap*1.25f&&joined.maxX-joined.minX<=gap*3.4f
                    &&joined.area>=gap*gap*3&&joined.minY<top-gap*.6f
                    &&joined.maxY>staff.pitchBottom+gap*.2f){clef=true;break;}
        }
        if(!clef)return false;
        for(Component other:heads) {
            if(other==head||other.area>gap*gap*.3f||other.maxX-other.minX+1>gap*.8f
                    ||other.maxY-other.minY+1>gap*.65f||Math.abs(other.centerX-head.centerX)>gap*.3f
                    ||Math.abs(other.centerY-head.centerY)<gap*.8f||Math.abs(other.centerY-head.centerY)>gap*1.25f
                    ||Math.abs((other.centerY+head.centerY)*.5f-top)>gap*.25f
                    ||attachedRawStem(gray,width,height,other,gap*.65f,2,180)!=null
                    ||hasOpenCenter(labels,gray,width,height,other,gap))continue;
            boolean following=false;
            for(Component main:heads)if(main.area>gap*gap*.65f&&main.minX>Math.max(head.maxX,other.maxX)+gap
                    &&main.minX<Math.max(head.maxX,other.maxX)+gap*6&&nearestHeadStaff(staffs,main.centerY)==staff){following=true;break;}
            if(!following)continue;
            int left=Math.round(Math.min(head.minX,other.minX)-gap*.6f),right=Math.round(Math.max(head.maxX,other.maxX)+gap*.6f);
            int first=Math.min(head.minY,other.minY),last=Math.max(head.maxY,other.maxY);
            int y0=Math.round(first-gap*.8f),y1=Math.round(last+gap*.8f);
            int reach=Math.round(gap*.6f),probe=Math.max(2,Math.round(gap*.16f));
            if(left<reach||right+reach>=width||y0<probe||y1+probe>=height)continue;
            int w=right-left+1,h=y1-y0+1;byte[] ink=new byte[w*h];
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((gray[(y0+y)*width+left+x]&255)<235
                    &&(headerInkContrast(gray,width,left+x,y0+y,reach)>=12
                        ||(gray[(y0+y-probe)*width+left+x]&255)<235&&(gray[(y0+y+probe)*width+left+x]&255)<235
                            &&headerInkContrast(gray,width,left+x,y0+y-probe,reach)>=12
                            &&headerInkContrast(gray,width,left+x,y0+y+probe,reach)>=12))ink[y*w+x]=5;
            Component glyph=retainSeedConnectedInk(ink,w,h,head,left,y0);if(glyph==null)continue;
            if(glyph.minX==0||glyph.maxX==w-1||glyph.minY==0||glyph.maxY==h-1
                    ||glyph.maxY-glyph.minY<gap*2||glyph.maxY-glyph.minY>gap*3.8f
                    ||glyph.maxX-glyph.minX>gap*1.8f||y0+glyph.minY>first-gap*.3f||y0+glyph.maxY<last+gap*.3f)continue;
            int shared=0;
            for(int y=other.minY;y<=other.maxY;y++)for(int x=other.minX;x<=other.maxX;x++)
                if(ink[(y-y0)*w+x-left]!=0)shared++;
            if(shared>=other.area*.5f)return true;
        }
        return false;
    }

    /** A key flat can be split between an accidental spine and a note-labelled bowl.
     * Require a nearby printed clef, semantic spine evidence and the complete raw flat. */
    private static boolean isHeaderFlatHead(byte[] labels,byte[] gray,int width,int height,
            Component head,List<Staff> staffs,List<Component> glyphs) {
        if(gray==null||gray.length!=width*height)return false;
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.pitchGap;
        if(head.maxX-head.minX+1>gap||head.maxY-head.minY+1>gap
                ||head.area>gap*gap*.55f||head.centerY<staff.top-gap||head.centerY>staff.bottom+gap)return false;
        boolean header=false;
        for(Component original:glyphs) {
            Component c=joinTrebleCurl(joinSmallTrebleFragments(original,glyphs,staff),glyphs,staff);
            c=joinHeaderClefFragments(c,glyphs,staff);
            if(c.maxX>=head.minX||head.minX-c.maxX>gap*4)continue;
            float gh=c.maxY-c.minY+1,gw=c.maxX-c.minX+1;
            boolean treble=gh>=gap*4.8f&&gh<=gap*8.8f&&gw>=gap*1.25f&&gw<=gap*3.4f
                    &&c.area>=gap*gap*1.65f&&c.minY<staff.top-gap*.35f
                    &&c.maxY>staff.bottom+gap*.2f&&Math.abs(c.centerY-(staff.top+staff.bottom)*.5f)<gap*1.1f;
            if(treble||rawBassClef(c,gray,width,height,staff)){header=true;break;}
        }
        if(!header)return false;
        for(Component original:glyphs) {
            Component seed=joinHeaderFlatSpine(original,glyphs,head,gap);
            if(seed.minX>=head.minX||seed.maxX<head.minX-gap*.6f
                    ||seed.maxX-seed.minX+1>gap*.85f||seed.maxY-seed.minY+1<gap*.8f
                    ||seed.minY>head.centerY-gap*.9f||seed.maxY<head.minY-gap*.25f)continue;
            for(int threshold:new int[]{180,235}) {
                int margin=Math.max(1,Math.round(gap*.16f));
                int left=Math.max(0,Math.min(seed.minX,head.minX)-margin);
                int right=Math.min(width-1,Math.max(seed.maxX,head.maxX)+(threshold==180?margin:Math.round(gap*.5f)));
                int top=Math.max(0,Math.round(head.centerY-gap*2.7f));
                int bottom=Math.min(height-1,Math.round(head.centerY+gap*.8f));
                int w=right-left+1,h=bottom-top+1;if(w<=0||h<=0)continue;
                byte[] ink=new byte[w*h];int area=0,minX=w,maxX=-1,minY=h,maxY=-1;long sx=0,sy=0;
                int reach=Math.max(3,Math.round(gap*.6f)),probe=Math.max(2,Math.round(gap*.2f));
                for(int y=top;y<=bottom;y++) {
                    int outside=0,dark=0;
                    for(int x=Math.max(0,left-reach);x<=Math.min(width-1,right+reach);x++)
                        if(x<left||x>right){outside++;if((gray[y*width+x]&255)<=threshold)dark++;}
                    boolean rule=outside>0&&dark>=outside*.8f;
                    for(int x=left;x<=right;x++) {
                        if((gray[y*width+x]&255)>threshold)continue;
                        if(rule&&(y<probe||y+probe>=height||(gray[(y-probe)*width+x]&255)>threshold
                                ||(gray[(y+probe)*width+x]&255)>threshold))continue;
                        if(threshold>180) {
                            int contrastReach=Math.round(gap*.6f);
                            if(x>=contrastReach&&x+contrastReach<width
                                    &&headerInkContrast(gray,width,x,y,contrastReach)<12
                                    &&(!rule||headerInkContrast(gray,width,x,y-probe,contrastReach)<12
                                        ||headerInkContrast(gray,width,x,y+probe,contrastReach)<12))continue;
                        }
                        int xx=x-left,yy=y-top;ink[yy*w+xx]=OmrMeasurePostProcessor.SYMBOL;
                        area++;sx+=xx;sy+=yy;minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);
                        minY=Math.min(minY,yy);maxY=Math.max(maxY,yy);
                    }
                }
                if(threshold>180) {
                    Component connected=retainSeedConnectedInk(ink,w,h,head,left,top);
                    if(connected==null)continue;
                    area=connected.area;minX=connected.minX;maxX=connected.maxX;
                    minY=connected.minY;maxY=connected.maxY;
                    sx=Math.round(connected.centerX*area);sy=Math.round(connected.centerY*area);
                }
                if(area==0)continue;
                var flat=new AccidentalCandidate(new Component(area,minX,maxX,minY,maxY,sx/(float)area,sy/(float)area),OmrMeasurePostProcessor.SYMBOL);
                // A fragmented note label may cover only the lower tip of a
                // proven flat bowl, away from the accidental's pitch center.
                if(!isNaturalGlyph(ink,w,h,flat,gap)&&!isSharpGlyph(ink,w,h,flat,gap)
                        &&isFlatGlyph(ink,w,h,flat,gap)
                        &&(threshold==180?Math.abs(top+flatPitchCenter(ink,w,flat,gap)-head.centerY)<=gap*.5f
                            :head.centerY>=top+minY+(maxY-minY+1)*.55f&&head.centerY<=top+maxY))return true;
            }
        }
        return false;
    }

    private static float headerInkContrast(byte[] gray,int width,int x,int y,int reach) {
        return ((gray[y*width+x-reach]&255)+(gray[y*width+x+reach]&255))*.5f-(gray[y*width+x]&255);
    }

    /** Reconnect upper and lower clef pieces only around an already large body.
     * The complete header-clef checks still decide whether this is a clef. */
    private static Component joinHeaderClefFragments(Component body,List<Component> glyphs,Staff staff) {
        float gap=staff.gap;
        if(body.maxY-body.minY<gap*3||body.maxY-body.minY>gap*7
                ||body.maxX-body.minX<gap*1.3f||body.maxX-body.minX>gap*2.5f
                ||body.area<gap*gap*1.65f||body.minY>staff.top+gap*.5f
                ||body.minY<staff.top-gap*3)return body;
        Component joined=body;
        for(Component part:glyphs) {
            if(part.minX>=joined.minX&&part.maxX<=joined.maxX
                    &&part.minY>=joined.minY&&part.maxY<=joined.maxY)continue;
            if(part==body||part.minX<body.minX-gap*.2f||part.maxX>body.maxX+gap*.6f
                    ||part.minY<body.minY-gap*2.5f||part.maxY>body.maxY+gap*2
                    ||part.minY>joined.maxY+gap*1.2f||part.maxY<joined.minY-gap*1.2f)continue;
            int area=joined.area+part.area;
            joined=new Component(area,Math.min(joined.minX,part.minX),Math.max(joined.maxX,part.maxX),
                    Math.min(joined.minY,part.minY),Math.max(joined.maxY,part.maxY),
                    (joined.centerX*joined.area+part.centerX*part.area)/area,
                    (joined.centerY*joined.area+part.centerY*part.area)/area);
        }
        return joined;
    }

    /** Staff labels can interrupt a flat spine into several narrow pieces. */
    private static Component joinHeaderFlatSpine(Component seed,List<Component> glyphs,Component head,float gap) {
        if(seed.maxX-seed.minX+1>gap*.85f)return seed;
        Component joined=seed;
        for(Component part:glyphs) {
            if(part.minX>=joined.minX&&part.maxX<=joined.maxX
                    &&part.minY>=joined.minY&&part.maxY<=joined.maxY)continue;
            if(part==seed||part.minX<seed.minX-gap*.2f||part.maxX>seed.maxX+gap*.2f
                    ||part.minY<head.centerY-gap*2.7f||part.maxY>head.centerY+gap*.65f
                    ||part.minY>joined.maxY+gap*.65f||part.maxY<joined.minY-gap*.65f
                    ||Math.max(joined.maxX,part.maxX)-Math.min(joined.minX,part.minX)+1>gap*.85f)continue;
            int area=joined.area+part.area;
            joined=new Component(area,Math.min(joined.minX,part.minX),Math.max(joined.maxX,part.maxX),
                    Math.min(joined.minY,part.minY),Math.max(joined.maxY,part.maxY),
                    (joined.centerX*joined.area+part.centerX*part.area)/area,
                    (joined.centerY*joined.area+part.centerY*part.area)/area);
        }
        return joined;
    }

    /** A tiny upper hook can belong to an italic forte rather than a note.
     * Require the descending symbol body, separate cross-stroke and lower hook. */
    private static boolean isForteHookHead(byte[] gray,int width,int height,Component head,
            List<Staff> staffs,List<Component> symbols) {
        if(gray==null||gray.length!=width*height)return false;
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.pitchGap;
        if(head.minY<staff.pitchBottom+gap*.75f||head.centerY>staff.pitchBottom+gap*3.5f
                ||head.maxX-head.minX+1>gap||head.maxY-head.minY+1>gap*.8f
                ||head.area>gap*gap*.55f||attachedRawStem(gray,width,height,head,gap)!=null)return false;
        for(Component body:symbols) {
            if(body.area<head.area*2||body.maxY<head.maxY+gap*1.2f
                    ||body.minY>head.maxY+gap*.3f||body.maxY>head.maxY+gap*3
                    ||body.minX>head.minX-gap*.6f||body.maxX<head.minX
                    ||body.maxX>head.maxX+gap*.5f||body.maxX-body.minX<gap)continue;
            int margin=Math.max(2,Math.round(gap*.2f));
            int left=Math.max(0,Math.min(body.minX,head.minX)-margin);
            int right=Math.min(width-1,Math.max(body.maxX,head.maxX)+margin);
            int top=Math.max(0,Math.min(body.minY,head.minY)-margin);
            int bottom=Math.min(height-1,Math.max(body.maxY,head.maxY)+margin);
            int w=right-left+1,h=bottom-top+1;
            if(w>gap*3.2f||h>gap*4.2f)continue;
            byte[] ink=new byte[w*h];
            for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)
                if((gray[y*width+x]&255)<=205)ink[(y-top)*w+x-left]=5;
            Component glyph=retainSeedConnectedInk(ink,w,h,head,left,top);
            if(glyph==null||rawStrokeLeavesCrop(gray,width,height,ink,w,h,left,top,gap))continue;
            int gh=glyph.maxY-glyph.minY+1,gw=glyph.maxX-glyph.minX+1;
            if(gh<gap*1.8f||gh>gap*3.8f||gw<gap||gw>gap*3
                    ||head.centerY>top+glyph.minY+gh*.3f)continue;
            int[] spans=new int[gh],centers=new int[gh];
            for(int y=0;y<gh;y++) {
                int lo=w,hi=-1;
                for(int x=glyph.minX;x<=glyph.maxX;x++)if(ink[(y+glyph.minY)*w+x]!=0){lo=Math.min(lo,x);hi=x;}
                if(hi>=lo){spans[y]=hi-lo+1;centers[y]=lo+hi;}
            }
            int upper=0,cross=Math.round(gh*.2f),lower=Math.round(gh*.75f);
            for(int y=1;y<gh*.25f;y++)if(spans[y]>spans[upper])upper=y;
            for(int y=cross+1;y<gh*.45f;y++)if(spans[y]>spans[cross])cross=y;
            for(int y=lower+1;y<gh;y++)if(spans[y]>spans[lower])lower=y;
            int[] middle=java.util.Arrays.copyOfRange(spans,Math.round(gh*.45f),Math.round(gh*.75f));
            java.util.Arrays.sort(middle);int stem=middle[middle.length/2];
            if(stem<2||spans[upper]<stem*1.4f||spans[cross]<stem*1.65f||spans[lower]<stem*1.5f
                    ||cross-upper<gap*.3f||centers[upper]-centers[cross]<gap*.4f
                    ||centers[cross]-centers[lower]<gap*1.2f)continue;
            int valley=Integer.MAX_VALUE;
            for(int y=upper+1;y<cross;y++)valley=Math.min(valley,spans[y]);
            if(valley>Math.min(spans[upper],spans[cross])*.65f)continue;
            return true;
        }
        return false;
    }

    /** A bounded zigzag above a larger note can contain a false semantic oval.
     * Its dark center must fall, rise, and fall again across four distinct lobes. */
    private static boolean isZigzagOrnamentHead(byte[] labels,byte[] gray,int width,int height,
            Component head,List<Staff> staffs,List<Component> heads) {
        if(gray==null)return false;
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.pitchGap;
        if(head.maxY>staff.pitchBottom-gap*4.5f||head.centerY<staff.pitchBottom-gap*8
                ||head.area>gap*gap*.9f||head.maxX-head.minX+1>gap*1.3f
                ||head.maxY-head.minY+1>gap||attachedRawStem(gray,width,height,head,gap*.65f,2,180)!=null)return false;
        boolean owner=false;
        for(Component main:heads)if(main!=head&&main.area>=head.area*1.4f
                &&main.maxX-main.minX+1>=gap&&Math.abs(main.centerX-head.centerX)<gap
                &&main.centerY-head.centerY>=gap*2&&main.centerY-head.centerY<=gap*7
                &&nearestHeadStaff(staffs,main.centerY)==staff){owner=true;break;}
        if(!owner)return false;
        int left=Math.round(head.minX-gap*1.4f),right=Math.round(head.maxX+gap*.8f);
        int top=Math.round(head.minY-gap*.3f),bottom=Math.round(head.maxY+gap*.3f);
        if(left<0||right>=width||top<0||bottom>=height)return false;
        int w=right-left+1,h=bottom-top+1;byte[] ink=new byte[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((gray[(top+y)*width+left+x]&255)<205)ink[y*w+x]=5;
        Component glyph=retainSeedConnectedInk(ink,w,h,head,left,top);if(glyph==null)return false;
        int span=glyph.maxX-glyph.minX+1,rise=glyph.maxY-glyph.minY+1,symbol=0;
        if(glyph.minX==0||glyph.maxX==w-1||glyph.minY==0||glyph.maxY==h-1
                ||span<gap*1.8f||span>gap*3.2f||rise<gap*.6f||rise>gap*1.3f)return false;
        float[] centers=new float[w];java.util.Arrays.fill(centers,Float.NaN);
        int first=w,last=-1;
        for(int x=glyph.minX;x<=glyph.maxX;x++) {
            int count=0,sum=0,runs=0;boolean previous=false;
            for(int y=glyph.minY;y<=glyph.maxY;y++) {
                if(ink[y*w+x]!=0&&labels[(top+y)*width+left+x]==5)symbol++;
                boolean dark=ink[y*w+x]!=0&&(gray[(top+y)*width+left+x]&255)<165;
                if(dark){count++;sum+=y;if(!previous)runs++;}previous=dark;
            }
            if(runs>1)return false;
            if(count>0){centers[x]=sum/(float)count;first=Math.min(first,x);last=x;}
        }
        if(symbol<glyph.area*.2f||last-first+1<gap*1.4f)return false;
        float[] extremes={Float.MAX_VALUE,-Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE};int[] at=new int[4];
        for(int x=first+1;x<last;x++) {
            if(!Float.isFinite(centers[x-1]+centers[x]+centers[x+1]))return false;
            float value=(centers[x-1]+centers[x]+centers[x+1])/3;
            int bin=Math.min(3,(x-first)*4/(last-first+1));
            if(bin%2==0?value<extremes[bin]:value>extremes[bin]){extremes[bin]=value;at[bin]=x;}
        }
        for(int i=1;i<4;i++)if(at[i]-at[i-1]<gap*.3f)return false;
        return extremes[1]-extremes[0]>=gap*.12f&&extremes[1]-extremes[2]>=gap*.12f
                &&extremes[3]-extremes[2]>=gap*.12f;
    }

    /** A tall detached count above a fully capped multimeasure-rest bar. */
    private static boolean isHeavyRestCount(byte[] gray,int width,int height,
            Component head,Staff staff) {
        if(gray==null)return false;
        float gap=staff.pitchGap,top=staff.pitchBottom-gap*4;
        if(head.maxY>=top-gap*.1f||head.centerY<top-gap*3.5f)return false;
        boolean countShape=false;
        for(TempoInk glyph:tempoInk(gray,width,height,Math.round(head.centerX-gap*2.5f),
                Math.round(head.centerX+gap*2.5f),Math.round(top-gap*4),Math.round(top))) {
            if(head.centerX<glyph.left||head.centerX>glyph.right
                    ||head.centerY<glyph.top||head.centerY>glyph.bottom)continue;
            if(glyph.height()>gap*1.3f&&glyph.height()<gap*3.3f
                    &&glyph.width()>gap*.4f&&glyph.width()<glyph.height()*.95f)countShape=true;
        }
        if(!countShape)return false;
        int[] stem=attachedRawStem(gray,width,height,head,gap);
        if(stem!=null&&Math.abs(stem[1]-head.centerY)>gap*2)return false;
        for(int y=Math.max(0,Math.round(staff.pitchBottom-gap*2.7f));
                y<=Math.min(height-1,Math.round(staff.pitchBottom-gap*1.3f));y++)
            if(heavyRestBarAtRow(gray,width,height,Math.round(head.centerX),y,gap))return true;
        return false;
    }

    /** A small semantic oval can lie entirely inside a thick ending barline. */
    private static boolean isThickBarlineHead(byte[] gray,int width,int height,
            Component head,List<Staff> staffs) {
        if(gray==null)return false;
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.pitchGap,top=staff.pitchBottom-gap*4,bottom=staff.pitchBottom;
        if(head.minY<top||head.maxY>bottom||head.maxX-head.minX+1>gap*.85f)return false;
        int cx=Math.round(head.centerX),first=Math.round(top+gap*.2f),last=Math.round(bottom-gap*.2f);
        if(cx<0||cx>=width||first<0||last>=height)return false;
        int minLeft=width,maxLeft=-1,minRight=width,maxRight=-1,rows=0;
        for(int y=first;y<=last;y++) {
            float ruleDistance=Math.abs((y-top)/gap-Math.round((y-top)/gap))*gap;
            if(ruleDistance<gap*.18f)continue;
            if((gray[y*width+cx]&255)>=165)return false;
            int left=cx,right=cx;
            while(left>0&&(gray[y*width+left-1]&255)<165)left--;
            while(right+1<width&&(gray[y*width+right+1]&255)<165)right++;
            int span=right-left+1;
            if(span<gap*.25f||span>gap*.85f)return false;
            minLeft=Math.min(minLeft,left);maxLeft=Math.max(maxLeft,left);
            minRight=Math.min(minRight,right);maxRight=Math.max(maxRight,right);rows++;
        }
        int tolerance=Math.max(1,Math.round(gap*.12f));
        if(rows<gap||maxLeft-minLeft>tolerance||maxRight-minRight>tolerance
                ||head.minX<minLeft-1||head.maxX>maxRight+1)return false;
        // A true head protrudes from its stem; a plain bar ends at the outer staff rules.
        for(int y:new int[]{Math.round(top-gap*.3f),Math.round(bottom+gap*.3f)}) {
            if(y<0||y>=height||(gray[y*width+cx]&255)<165)return false;
        }
        return true;
    }

    /** A whole-measure rest hangs as a filled rectangle below the second rule. */
    private static boolean isWholeMeasureRestHead(byte[] gray,int width,int height,
            Component head,List<Staff> staffs) {
        if(gray==null)return false;
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.pitchGap,rule=staff.pitchBottom-gap*3;
        if(head.centerY<rule+gap*.12f||head.centerY>rule+gap*.7f
                ||attachedRawStem(gray,width,height,head,gap)!=null)return false;
        int cx=Math.round(head.centerX),start=Math.max(0,Math.round(rule+gap*.15f));
        int end=Math.min(height-1,Math.round(rule+gap*.8f));
        int minX=width,maxX=-1,first=-1,last=-1;
        int[] lefts=new int[end-start+1],rights=new int[end-start+1];
        java.util.Arrays.fill(lefts,-1);
        for(int y=start;y<=end;y++) {
            if((gray[y*width+cx]&255)>=180)continue;
            int left=cx,right=cx;
            while(left>0&&(gray[y*width+left-1]&255)<180)left--;
            while(right+1<width&&(gray[y*width+right+1]&255)<180)right++;
            if(right-left+1>gap*1.8f)return false;
            lefts[y-start]=left;rights[y-start]=right;
            minX=Math.min(minX,left);maxX=Math.max(maxX,right);
            if(first<0)first=y;last=y;
        }
        int w=maxX-minX+1,h=last-first+1;
        if(first<0||first>rule+gap*.25f||last>=end||w<gap*.9f
                ||h<gap*.3f||h>gap*.65f)return false;
        int tolerance=Math.max(1,Math.round(gap*.1f));
        for(int y=first;y<=last;y++) {
            int left=lefts[y-start],right=rights[y-start];
            if(left<0||left-minX>tolerance||maxX-right>tolerance)return false;
        }
        Component block=new Component(w*h,minX,maxX,first,last,(minX+maxX)*.5f,(first+last)*.5f);
        if(attachedRawStem(gray,width,height,block,gap)!=null)return false;
        // The supporting staff rule must continue on both sides of the block.
        int lineY=Math.round(rule),support=0;
        for(int side:new int[]{-1,1}) {
            int x=Math.round((side<0?minX:maxX)+side*gap*.5f);
            if(x<0||x>=width)continue;
            for(int y=Math.max(0,lineY-2);y<=Math.min(height-1,lineY+2);y++)
                if((gray[y*width+x]&255)<180){support++;break;}
        }
        return support==2;
    }

    /** A compact prediction inside the thick centre of a two-capped multimeasure rest.
     * Both end caps must extend beyond both edges of the horizontal band. */
    private static boolean isHeavyRestBarFragment(byte[] gray,int width,int height,
            Component head,List<Staff> staffs) {
        if(gray==null)return false;
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.pitchGap;
        if(Math.abs(head.centerY-(staff.pitchBottom-gap*2))>gap*.7f)return false;
        boolean compact=head.maxX-head.minX+1<=gap*.8f&&head.maxY-head.minY+1<=gap*1.25f
                &&head.area<=gap*gap*.4f;
        for(int y=head.minY;y<=head.maxY;y++)
            if(compact&&heavyRestBarAtRow(gray,width,height,Math.round(head.centerX),y,gap)
                    ||countedRestBodyAtRow(gray,width,height,head,y,staff))return true;
        return false;
    }

    /** A readable count corroborates shorter H-rests and larger mask islands in their band. */
    private static boolean countedRestBodyAtRow(byte[] gray,int width,int height,
            Component head,int y,Staff staff) {
        float gap=staff.pitchGap;int x=Math.round(head.centerX);
        if(head.maxY-head.minY+1>gap*1.2f||(gray[y*width+x]&255)>=165)return false;
        int left=x,right=x;
        while(left>0&&(gray[y*width+left-1]&255)<165)left--;
        while(right+1<width&&(gray[y*width+right+1]&255)<165)right++;
        if(right-left<gap*4||right-left>gap*60)return false;
        int top=y,bottom=y;
        while(top>0&&restBandRow(gray,width,left,right,top-1))top--;
        while(bottom+1<height&&restBandRow(gray,width,left,right,bottom+1))bottom++;
        if(bottom-top+1<gap*.35f||bottom-top+1>gap*1.2f
                ||head.minX<left||head.maxX>right
                ||head.minY<top-gap*.2f||head.maxY>bottom+gap*.2f
                ||!restEndCap(gray,width,height,left,top,bottom,gap)
                ||!restEndCap(gray,width,height,right,top,bottom,gap))return false;
        float staffTop=staff.pitchBottom-gap*4;
        MeasureRegion region=new MeasureRegion(Math.max(0,(left-gap)/width),
                Math.min(1,(right+gap)/width),Math.max(0,(staffTop-gap*2)/height),
                Math.min(1,(staff.pitchBottom+gap*2)/height));
        var count=MultiMeasureRestDetector.standaloneCount(gray,width,height,
                new MultiMeasureRestDetector.RestBarCandidate(0,region));
        return count!=null&&count.bottom()*height<staffTop
                &&Math.abs((count.left()+count.right())*.5f*width-(left+right)*.5f)<gap;
    }

    private static boolean heavyRestBarAtRow(byte[] gray,int width,int height,int x,int y,float gap) {
        // A thin staff rule can run through the centre of the thick rest. Inspect
        // the predicted fragment's rows so that rule cannot hide the end caps.
        if((gray[y*width+x]&255)>=165)return false;
        int left=x,right=x;
        while(left>0&&(gray[y*width+left-1]&255)<165)left--;
        while(right+1<width&&(gray[y*width+right+1]&255)<165)right++;
        if(right-left<gap*6||right-left>gap*60)return false;
        int top=y,bottom=y;
        while(top>0&&restBandRow(gray,width,left,right,top-1))top--;
        while(bottom+1<height&&restBandRow(gray,width,left,right,bottom+1))bottom++;
        int thick=bottom-top+1;
        if(thick<gap*.35f||thick>gap*1.2f)return false;
        return restEndCap(gray,width,height,left,top,bottom,gap)
                &&restEndCap(gray,width,height,right,top,bottom,gap);
    }

    private static boolean restBandRow(byte[] gray,int width,int left,int right,int y) {
        for(int i=1;i<=5;i++)if((gray[y*width+left+(right-left)*i/6]&255)>=165)return false;
        return true;
    }

    private static boolean restEndCap(byte[] gray,int width,int height,int edge,
            int top,int bottom,float gap) {
        int margin=Math.max(2,Math.round(gap*.4f));
        if(top-margin<0||bottom+margin>=height)return false;
        for(int x=Math.max(0,Math.round(edge-gap*.25f));x<=Math.min(width-1,Math.round(edge+gap*.25f));x++) {
            boolean solid=true;
            for(int y=top-margin;y<=bottom+margin;y++)if((gray[y*width+x]&255)>=165){solid=false;break;}
            if(solid)return true;
        }
        return false;
    }

    /** A note followed by an equals sign and text above the staff is a tempo beat unit. */
    private static boolean isTempoUnitHead(byte[] gray, int width, int height,
                                           Component head, List<Staff> staffs) {
        if (gray == null) return false;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return false;
        float gap = staff.pitchGap, top = staff.pitchBottom - gap * 4;
        float y = head.centerY, x = head.maxX;
        if (head.maxY > top + gap * .15f || y < top - gap * 3.5f) return false;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        if (stem == null || stem[1] > y - gap * 1.5f) return false;
        List<TempoInk> glyphs = tempoInk(gray, width, height,
                Math.round(x + gap * .1f), Math.round(x + gap * 5.5f),
                Math.round(y - gap * 2), Math.round(y + gap * .3f));
        for (TempoInk upper : glyphs) for (TempoInk lower : glyphs) {
            if (upper.top >= lower.top || upper.width() < gap * .5f || upper.width() > gap * 1.6f
                    || lower.width() < gap * .5f || lower.width() > gap * 1.6f
                    || upper.height() > gap * .25f || lower.height() > gap * .25f
                    || upper.area < upper.width() * upper.height() * .7f
                    || lower.area < lower.width() * lower.height() * .7f
                    || Math.abs(upper.left - lower.left) > gap * .15f
                    || Math.abs(upper.right - lower.right) > gap * .2f
                    || lower.top - upper.bottom < gap * .08f
                    || lower.top - upper.bottom > gap * .5f
                    || lower.bottom - upper.top > gap * .8f
                    || upper.left - x > gap * 2.5f || lower.bottom > y + gap * .1f) continue;
            // This is shape evidence for following text, not OCR of the BPM value.
            // Ordinary ledger rules are farther apart; connected beams are not two isolated strokes.
            for (TempoInk text : glyphs) {
                if (text.left > lower.right + gap * .15f && text.left < lower.right + gap * 1.5f
                        && text.height() > gap * .75f && text.height() < gap * 2
                        && text.area > gap * gap * .18f
                        && text.top < upper.top && text.bottom > lower.bottom) return true;
            }
        }
        return false;
    }

    private record TempoInk(int left, int right, int top, int bottom, int area) {
        int width() { return right - left + 1; }
        int height() { return bottom - top + 1; }
    }

    /** Complete printed components only; clipping a letter must not manufacture an equals stroke. */
    private static List<TempoInk> tempoInk(byte[] gray, int width, int height,
                                          int left, int right, int top, int bottom) {
        left = Math.max(0, left); right = Math.min(width - 1, right);
        top = Math.max(0, top); bottom = Math.min(height - 1, bottom);
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
            queue[0] = start; seen[start] = true;
            while (take < size) {
                int pos = queue[take++], x = pos % rw, y = pos / rw;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                edge |= x == 0 || y == 0 || x == rw - 1 || y == rh - 1;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= rw || ny < 0 || ny >= rh) continue;
                    int next = ny * rw + nx;
                    if (!seen[next] && (gray[(top + ny) * width + left + nx] & 255) < 155) {
                        seen[next] = true; queue[size++] = next;
                    }
                }
            }
            if (!edge) result.add(new TempoInk(left + minX, left + maxX,
                    top + minY, top + maxY, size));
        }
        return result;
    }

    /** The tall open-right C can contribute tiny false heads at its curved terminals. */
    private static int[] commonTimeGlyphBounds(byte[] labels, byte[] gray, int width, int height,
                                              Component head, List<Staff> staffs, List<Component> glyphs) {
        if (gray == null) return null;
        Staff staff = nearestHeadStaff(staffs, head.centerY);
        if (staff == null) return null;
        float gap = staff.pitchGap, bottom = staff.pitchBottom, top = bottom - gap * 4;
        float mid = (top + bottom) * .5f;
        if (head.area > gap * gap * .5f || head.maxX - head.minX > gap * .8f
                || head.maxY - head.minY > gap * .8f || Math.abs(head.centerY - mid) > gap * 1.15f)
            return null;
        boolean header = false;
        for (Component glyph : glyphs) {
            if (glyph.maxX < head.minX && head.minX - glyph.maxX < gap * 9
                    && glyph.maxY - glyph.minY > gap * 4.5f && glyph.maxX - glyph.minX > gap * 1.1f
                    && Math.abs(glyph.centerY - mid) < gap * 3) header = true;
        }
        if (!header) return null;
        int[] stem = attachedRawStem(gray, width, height, head, gap);
        if (stem != null && (stem[1] < top - gap * .25f || stem[1] > bottom + gap * .25f)) return null;

        // Follow the printed glyph across narrow antialiasing gaps, ignoring the five staff rules.
        int searchLeft = Math.max(0, Math.round(head.minX - gap * 2.5f));
        int searchRight = Math.min(width - 1, Math.round(head.maxX + gap * 2.5f));
        int[] columns = new int[searchRight - searchLeft + 1];
        int scanTop = Math.max(0, Math.round(top)), scanBottom = Math.min(height - 1, Math.round(bottom));
        for (int x = searchLeft; x <= searchRight; x++) for (int y = scanTop; y <= scanBottom; y++) {
            if (offHeaderStaffLine(y, top, gap) && (gray[y * width + x] & 255) < 155)
                columns[x - searchLeft]++;
        }
        int left = head.minX, right = head.maxX, blank = 0;
        int maxBlank = Math.max(1, Math.round(gap * .15f));
        for (int x = left - 1; x >= searchLeft; x--) {
            if (columns[x - searchLeft] > 0) { left = x; blank = 0; }
            else if (++blank > maxBlank) break;
        }
        blank = 0;
        for (int x = right + 1; x <= searchRight; x++) {
            if (columns[x - searchLeft] > 0) { right = x; blank = 0; }
            else if (++blank > maxBlank) break;
        }
        if (left == searchLeft || right == searchRight || right - left < gap * .9f
                || right - left > gap * 2.5f) return null;
        int noteInk = 0, minY = height, maxY = -1;
        for (int y = scanTop; y <= scanBottom; y++) for (int x = left; x <= right; x++) {
            if (labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) noteInk++;
            if (offHeaderStaffLine(y, top, gap) && (gray[y * width + x] & 255) < 155) {
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
        }
        if (noteInk > gap * gap * .55f || maxY - minY < gap * 2.2f || maxY - minY > gap * 3.8f)
            return null;
        int span = right - left;
        int[] open = headerInkBand(gray, width, height, Math.round(left + span * .65f), right,
                Math.round(mid - gap * .25f), Math.round(mid + gap * .25f), top, gap);
        int[] upper = headerInkBand(gray, width, height, Math.round(left + span * .60f), right,
                Math.round(mid - gap * 1.05f), Math.round(mid - gap * .35f), top, gap);
        int[] lower = headerInkBand(gray, width, height, Math.round(left + span * .60f), right,
                Math.round(mid + gap * .35f), Math.round(mid + gap * 1.05f), top, gap);
        int[] leftUp = headerInkBand(gray, width, height, left, Math.round(left + span * .35f),
                Math.round(mid - gap * .65f), Math.round(mid - gap * .15f), top, gap);
        int[] leftDown = headerInkBand(gray, width, height, left, Math.round(left + span * .35f),
                Math.round(mid + gap * .15f), Math.round(mid + gap * .65f), top, gap);
        // Require both curved terminals and the left arc, with a genuinely open middle at the right.
        if (open[1] == 0 || open[0] > open[1] * .2f || upper[0] < gap * .7f || lower[0] < gap * .7f
                || leftUp[0] < gap * .7f || leftDown[0] < gap * .7f) return null;
        return new int[]{left, right, scanTop, scanBottom};
    }

    private static boolean offHeaderStaffLine(int y, float top, float gap) {
        return Math.abs((y - top) / gap - Math.round((y - top) / gap)) * gap > Math.max(1, gap * .15f);
    }

    private static int[] headerInkBand(byte[] gray, int width, int height, int left, int right,
                                      int top, int bottom, float staffTop, float gap) {
        int ink = 0, total = 0;
        for (int y = Math.max(0, top); y <= Math.min(height - 1, bottom); y++) {
            if (!offHeaderStaffLine(y, staffTop, gap)) continue;
            for (int x = Math.max(0, left); x <= Math.min(width - 1, right); x++) {
                total++;
                if ((gray[y * width + x] & 255) < 155) ink++;
            }
        }
        return new int[]{ink, total};
    }


    /** Stacked rounded meter digits can arrive as one tall semantic head blob.
     * Inspect their printed counters before splitting that blob into chord tones. */
    private static boolean isRoundedHeaderMeter(byte[] labels,byte[] gray,int width,int height,
            Component head,List<Staff> staffs,List<Component> glyphs) {
        if(gray==null)return false;
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.gap;
        boolean whole=head.maxY-head.minY>=gap*3&&head.maxY-head.minY<=gap*4.3f
                &&head.maxX-head.minX<=gap*1.9f&&head.maxX-head.minX>=gap*.65f
                &&head.minY>=staff.top-gap*.2f&&head.maxY<=staff.bottom+gap*.3f;
        boolean denominator=head.maxY-head.minY>=gap*1.45f&&head.maxY-head.minY<=gap*2.65f
                &&head.maxX-head.minX<=gap*2.2f&&head.maxX-head.minX>=gap*.65f
                &&head.minY+1>=Math.floor(staff.top+gap*1.75f)&&head.centerY>=staff.top+gap*2.2f
                &&head.maxY<=staff.bottom+gap*.3f;
        if(!whole&&!denominator)return false;
        boolean header=false;
        for(Component glyph:glyphs)if(glyph.maxX<head.minX&&head.minX-glyph.maxX<gap*7
                &&glyph.maxY-glyph.minY>gap*4.5f&&glyph.maxX-glyph.minX>gap*1.1f
                &&glyph.centerY>staff.top-gap&&glyph.centerY<staff.bottom+gap)header=true;
        // Meter changes also occur after a barline within or at the end of a system.
        // Require a complete printed rule, not a short note stem or numeral stroke.
        if(!header)for(int x=Math.max(0,Math.round(head.minX-gap*3.8f));
                x<Math.min(width,Math.round(head.minX-gap*.3f));x++) {
            int ink=0,total=0;
            for(int y=Math.max(0,Math.round(staff.top));y<=Math.min(height-1,Math.round(staff.bottom));y++) {
                total++;if((gray[y*width+x]&255)<155)ink++;
            }
            if(total>=gap*3.8f&&ink>=total*.93f) {header=true;break;}
        }
        if(!header)return false;
        int[] stem=attachedRawStem(gray,width,height,head,gap);
        if(stem!=null&&(stem[1]<staff.top-gap*.25f||stem[1]>staff.bottom+gap*.25f))return false;
        int left=Math.max(0,Math.round(head.minX-gap*.3f));
        int right=Math.min(width-1,Math.round(head.maxX+gap*.3f));
        int top=Math.max(0,Math.round(staff.top-gap*.2f));
        int bottom=Math.min(height-1,Math.round(staff.bottom+gap*.2f));
        int w=right-left+1,h=bottom-top+1,upper=0,lower=0,tallLower=0;
        boolean[] visited=new boolean[w*h];int[] queue=new int[w*h];
        for(int seed=0;seed<w*h;seed++) {
            if(visited[seed]||(gray[(top+seed/w)*width+left+seed%w]&255)<=155)continue;
            int take=0,size=1,minX=w,maxX=-1,minY=h,maxY=-1;boolean edge=false;
            queue[0]=seed;visited[seed]=true;
            while(take<size) {
                int index=queue[take++],x=index%w,y=index/w;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                edge|=x==0||y==0||x==w-1||y==h-1;
                for(int direction=0;direction<4;direction++) {
                    int nx=x+(direction==0?-1:direction==1?1:0);
                    int ny=y+(direction==2?-1:direction==3?1:0);
                    if(nx<0||nx>=w||ny<0||ny>=h)continue;
                    int next=ny*w+nx;
                    if(!visited[next]&&(gray[(top+ny)*width+left+nx]&255)>155) {
                        visited[next]=true;queue[size++]=next;
                    }
                }
            }
            int cw=maxX-minX+1,ch=maxY-minY+1;
            // Full stacked glyphs need upright counters. A separate denominator
            // can have wider bowls; it also requires printed numerator evidence.
            // Broad shallow hollow-note counters remain excluded.
            if(edge||size<gap*gap*.07f||cw<gap*.2f||cw>gap*1.1f
                    ||ch<gap*.35f||ch>gap*1.1f||ch<cw*(whole?.75f:.5f))continue;
            float cy=top+(minY+maxY)*.5f;
            if(cy<staff.top+gap*2)upper++;else {
                lower++;
                if(ch>=cw*.6f)tallLower++;
            }
        }
        if(whole)return upper>=1&&lower>=2;
        if(lower<2||tallLower<1)return false;
        // An 8 denominator may be the only part labelled as a head. Its printed
        // numerator must span the upper half, without an actual note/chord there.
        int ink=0,heads=0,minY=height,maxY=-1;
        for(int y=Math.max(0,Math.round(staff.top));
                y<=Math.min(head.minY-1,Math.min(height-1,Math.round(staff.top+gap*1.85f)));y++)
            for(int x=Math.max(0,Math.round(head.minX-gap*.8f));
                    x<=Math.min(width-1,Math.round(head.maxX+gap*.25f));x++) {
                if(labels[y*width+x]==OmrMeasurePostProcessor.NOTEHEAD)heads++;
                float lineDistance=Math.abs((y-staff.top)/gap-Math.round((y-staff.top)/gap))*gap;
                if(lineDistance<=Math.max(1,gap*.15f))continue;
                if((gray[y*width+x]&255)<155) {ink++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
            }
        return heads<gap*gap*.15f&&ink>gap*3&&maxY-minY>gap*1.1f;
    }

    /** A tall, rounded lower meter digit can be painted as a hollow notehead. */
    /** A small head prediction can cover just one corner of a repeated meter digit.
     * Require two tall matching printed glyphs after a full barline, with no upper
     * note prediction or attached stem extending beyond the staff. */
    private static boolean isRepeatedMeterFragment(byte[] labels,byte[] gray,int width,int height,
            Component head,Staff staff) {
        float gap=staff.gap;
        if(head.area>gap*gap*.55f||head.maxX-head.minX>gap||head.maxY-head.minY>gap)return false;
        float[] pitch=localStaffPitch(labels,gray,width,height,staff,head);
        gap=pitch[1];float staffBottom=pitch[0],staffTop=staffBottom-gap*4;
        if(head.area>gap*gap*.55f||head.maxX-head.minX>gap||head.maxY-head.minY>gap
                ||head.centerY<staffTop+gap*2.25f||head.centerY>staffBottom)return false;
        boolean boundary=false;
        for(int x=Math.max(0,Math.round(head.minX-gap*4));x<head.minX-gap*.7f;x++) {
            int ink=0,total=0;
            for(int y=Math.max(0,Math.round(staffTop));y<=Math.min(height-1,Math.round(staffBottom));y++) {
                total++;if((gray[y*width+x]&255)<155)ink++;
            }
            if(total>=gap*3.8f&&ink>=total*.94f){boundary=true;break;}
        }
        if(!boundary)return false;
        int[] stem=attachedRawStem(gray,width,height,head,gap);
        if(stem!=null&&(stem[1]<staffTop-gap*.25f||stem[1]>staffBottom+gap*.25f))return false;
        int left=Math.max(0,Math.round(head.centerX-gap*1.25f));
        int right=Math.min(width-1,Math.round(head.centerX+gap*1.25f));
        int top=Math.max(0,Math.round(staffTop)),bottom=Math.min(height-1,Math.round(staffTop+gap*1.95f));
        int upperHeads=0;
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)
            if(labels[y*width+x]==OmrMeasurePostProcessor.NOTEHEAD)upperHeads++;
        if(upperHeads>gap*gap*.08f)return false;
        for(int dy=Math.round(gap*1.75f);dy<=Math.round(gap*2.15f);dy++)
            for(int dx=-Math.round(gap*.2f);dx<=Math.round(gap*.2f);dx++) {
                int intersection=0,union=0,inkA=0,inkB=0,minX=width,maxX=-1,minY=height,maxY=-1;
                for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++) {
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||xx>=width||yy<0||yy>=height||yy>staffBottom+gap*.15f)continue;
                    float aDistance=Math.abs((y-staffTop)/gap-Math.round((y-staffTop)/gap))*gap;
                    float bDistance=Math.abs((yy-staffTop)/gap-Math.round((yy-staffTop)/gap))*gap;
                    if(aDistance<gap*.16f||bDistance<gap*.16f)continue;
                    boolean a=(gray[y*width+x]&255)<155,b=(gray[yy*width+xx]&255)<155;
                    if(a){inkA++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
                    if(b)inkB++;
                    if(a||b)union++;
                    if(a&&b)intersection++;
                }
                if(inkA>gap*gap*.45f&&inkB>gap*gap*.45f&&maxX-minX>gap*.75f
                        &&maxY-minY>gap*1.2f&&intersection>union*.72f)return true;
            }
        return false;
    }

    private static boolean isHeaderMeterDigit(byte[] labels,byte[] gray,int width,int height,
            Component head,List<Staff> staffs,List<Component> glyphs) {
        if(gray==null)return false;
        Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)return false;
        float gap=staff.gap;
        if(isRepeatedMeterFragment(labels,gray,width,height,head,staff))return true;
        if(head.centerY<staff.top+gap*2.2f||head.centerY>staff.bottom+gap*.2f
                ||head.maxX-head.minX>gap*2.2f||head.maxY-head.minY<gap*1.5f
                ||head.maxY-head.minY>gap*2.6f
                ||attachedRawStem(gray,width,height,head,gap)!=null)return false;
        boolean header=false;
        for(Component glyph:glyphs)if(glyph.maxX<head.minX&&head.minX-glyph.maxX<gap*7
                &&glyph.maxY-glyph.minY>gap*4.5f&&glyph.maxX-glyph.minX>gap*1.1f
                &&glyph.centerY>staff.top-gap&&glyph.centerY<staff.bottom+gap)header=true;
        if(!header)return false;
        int left=Math.max(0,Math.round(head.minX-gap*.55f)),right=Math.min(width-1,Math.round(head.maxX+gap*.25f));
        int top=Math.max(0,Math.round(staff.top)),bottom=Math.min(height-1,Math.round(staff.top+gap*1.85f));
        int ink=0,heads=0,minY=height,maxY=-1;
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++) {
            if(labels[y*width+x]==OmrMeasurePostProcessor.NOTEHEAD)heads++;
            float lineDistance=Math.abs((y-staff.top)/gap-Math.round((y-staff.top)/gap))*gap;
            if(lineDistance<=Math.max(1,gap*.15f))continue;
            if((gray[y*width+x]&255)<=155){ink++;minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        }
        // Another genuine note above this head is a chord, not a stacked numeral pair.
        return heads<gap*gap*.15f&&ink>gap*3&&maxY-minY>gap*1.1f;
    }

    /** Adjacent chord pitches can occupy opposite sides of one shared stem even
     * when segmentation returns two separate components. Their horizontal offset
     * is engraving, not an extra attack between the surrounding eighth notes. */
    private static List<DetectedNote> alignDisplacedSeconds(List<DetectedNote> source,byte[] gray,int width,int height) {
        if(gray==null)return source;
        List<DetectedNote> result=new ArrayList<>(source);
        for(int i=0;i<result.size();i++)for(int j=i+1;j<result.size();j++) {
            DetectedNote a=result.get(i),b=result.get(j);
            if(a.event.measureIndex()!=b.event.measureIndex()||a.event.staffIndex()!=b.event.staffIndex()
                    ||a.event.staffCount()!=b.event.staffCount()||Math.abs(a.event.staffStep()-b.event.staffStep())!=1)continue;
            float gap=(a.staffGap+b.staffGap)*.5f,dx=Math.abs(a.head.centerX-b.head.centerX);
            if(dx<gap*.9f||dx>gap*1.8f||Math.abs(a.head.centerY-b.head.centerY)>gap*.75f)continue;
            int left=Math.max(a.head.minX,b.head.minX)-1,right=Math.min(a.head.maxX,b.head.maxX)+1;
            if(left>right)continue;
            boolean shared=false;
            for(int x=Math.max(0,left);x<=Math.min(width-1,right);x++)for(int direction:new int[]{-1,1}) {
                int edge=direction<0?Math.min(a.head.minY,b.head.minY):Math.max(a.head.maxY,b.head.maxY);
                int count=0,samples=0;
                for(int k=1;k<=Math.round(gap*1.8f);k++) {
                    int y=edge+direction*k;if(y<0||y>=height)break;
                    samples++;if((gray[y*width+x]&255)<165)count++;
                }
                if(samples>=gap*1.6f&&count>=samples*.92f)shared=true;
            }
            if(!shared)continue;
            float position=Math.min(a.event.positionInMeasure(),b.event.positionInMeasure());
            // A displaced second may share its original column with further chord tones.
            // Move that whole attack together, preserving every tone's independent duration.
            List<Integer> chord=new ArrayList<>();
            for(int index=0;index<result.size();index++) {
                DetectedNote n=result.get(index);ScoreNoteEvent e=n.event;
                if(e.measureIndex()!=a.event.measureIndex()||e.staffIndex()!=a.event.staffIndex()
                        ||e.staffCount()!=a.event.staffCount())continue;
                if(index!=i&&index!=j&&Math.abs(n.head.centerX-a.head.centerX)>gap*.3f
                        &&Math.abs(n.head.centerX-b.head.centerX)>gap*.3f)continue;
                chord.add(index);
                position=Math.min(position,e.positionInMeasure());
            }
            for(int index:chord) {
                DetectedNote n=result.get(index);ScoreNoteEvent e=n.event;
                result.set(index,new DetectedNote(new ScoreNoteEvent(e.measureIndex(),position,e.staffStep(),
                        e.staffIndex(),e.staffCount(),e.pageY(),e.tiedFromPrevious(),e.augmentationDots(),e.beamCount(),
                        e.writtenAccidental(),e.unbeamedDurationBeats(),e.tupletDivisor(),e.followingRestBeats(),
                        e.articulations(),e.clefBottomDiatonic(),e.crossStaffBeam(),e.leadingRestBeats(),e.compactOpening()),n.head,n.staffGap));
            }
        }
        return result;
    }

    /** Each printed stave has its own clef stream, including small in-row changes. */
    private static List<DetectedNote> applyPrintedClefs(List<DetectedNote> notes, List<Staff> staffs,
                                                       List<Component> glyphs, byte[] labels, byte[] gray, int width, int height) {
        List<DetectedNote> result=new ArrayList<>(notes);
        Map<Integer,Integer> inherited=new HashMap<>();
        for(Staff staff:staffs) {
            List<ClefGlyph> clefs=new ArrayList<>();
            float gap=staff.gap;
            for(Component original:glyphs) {
                Component glyph=joinTrebleCurl(joinSmallTrebleFragments(original,glyphs,staff),glyphs,staff);
                float gh=glyph.maxY-glyph.minY+1,gw=glyph.maxX-glyph.minX+1;
                int clef=ScoreNoteEvent.CLEF_UNKNOWN;
                // A treble clef crosses the whole stave and projects beyond both outer lines.
                // This excludes tall accidentals and the thin bracket joining two staves.
                if(gh>=gap*4.8f&&gh<=gap*8.8f&&gw>=gap*1.25f&&gw<=gap*3.4f
                        &&glyph.area>=gap*gap*1.65f&&glyph.minY<staff.top-gap*.35f
                        &&glyph.maxY>staff.bottom+gap*.20f
                        &&Math.abs(glyph.centerY-(staff.top+staff.bottom)*.5f)<gap*1.1f)
                    clef=ScoreNoteEvent.CLEF_TREBLE;
                // Bass clef: curved body plus the two distinct dots straddling the F line.
                if(gh>=gap*2.3f&&gh<=gap*3.7f&&gw>=gap*1.35f&&gw<=gap*2.8f
                        &&Math.abs(glyph.minY-staff.top)<gap*.65f) {
                    boolean above=false,below=false;
                    for(Component dot:glyphs) {
                        float dh=dot.maxY-dot.minY+1,dw=dot.maxX-dot.minX+1;
                        if(dot.centerX<glyph.maxX+gap*.1f||dot.centerX>glyph.maxX+gap*1.1f
                                ||dw<gap*.2f||dw>gap*.7f||dh<gap*.2f||dh>gap*.7f)continue;
                        above|=Math.abs(dot.centerY-(staff.top+gap*.5f))<gap*.3f;
                        below|=Math.abs(dot.centerY-(staff.top+gap*1.5f))<gap*.3f;
                    }
                    if(above&&below)clef=ScoreNoteEvent.CLEF_BASS;
                }
                if(clef==ScoreNoteEvent.CLEF_UNKNOWN&&rawBassClef(original,labels,gray,width,height,staff))
                    clef=ScoreNoteEvent.CLEF_BASS;
                if(clef!=ScoreNoteEvent.CLEF_UNKNOWN)clefs.add(new ClefGlyph(glyph.maxX,clef));
            }
            clefs.sort(Comparator.comparingDouble(ClefGlyph::x));
            int voice=staff.count*16+staff.index;
            int initial=inherited.getOrDefault(voice,ScoreNoteEvent.CLEF_UNKNOWN);
            for(int i=0;i<result.size();i++) {
                DetectedNote n=result.get(i);
                if(staffForHead(labels,gray,width,height,staffs,n.head)!=staff)continue;
                int active=initial;
                for(ClefGlyph clef:clefs)if(clef.x<n.head.centerX)active=clef.clef;
                result.set(i,new DetectedNote(n.event.withClef(active),n.head,n.staffGap));
            }
            if(!clefs.isEmpty())inherited.put(voice,clefs.get(clefs.size()-1).clef);
        }
        return result;
    }
    private record ClefGlyph(float x,int clef) { }

    private static boolean rawBassClef(Component body,byte[] labels,byte[] gray,int width,int height,Staff staff) {
        // A key accidental beside rounded meter terminals can mimic a bass clef.
        // Preserve a sharp's crossbars or a flat's left spine and lower bowl.
        AccidentalCandidate candidate=new AccidentalCandidate(body,OmrMeasurePostProcessor.CLEF_OR_KEY);
        return !isSharpGlyph(labels,width,height,candidate,staff.gap)
                &&!isFlatGlyph(labels,width,height,candidate,staff.gap)
                &&rawBassClef(body,gray,width,height,staff);
    }

    /** A small bass-clef tail and one dot may be painted as generic symbols.
     * Confirm its two round dots and descending body in the printed pixels. */
    private static boolean rawBassClef(Component body,byte[] gray,int width,int height,Staff staff) {
        if(gray==null)return false;
        float gap=staff.pitchGap,top=staff.pitchBottom-gap*4;
        float gh=body.maxY-body.minY+1,gw=body.maxX-body.minX+1;
        // Segmentation may lose the left curl of a small bass clef. A narrower
        // seed still needs both printed dots and the descending tail below them.
        if(gh<gap*1.3f||gh>gap*3.7f||gw<gap*1.0f||gw>gap*2.8f
                ||body.area<gap*gap*.5f||Math.abs(body.minY-top)>gap*.65f)return false;
        int left=Math.max(0,Math.round(body.maxX+gap*.10f));
        int right=Math.min(width-1,Math.round(body.maxX+gap*1.15f));
        int first=Math.max(0,Math.round(top+gap*.1f)),last=Math.min(height-1,Math.round(top+gap*1.95f));
        int w=right-left+1,h=last-first+1;if(w<1||h<1)return false;
        byte[] ink=new byte[w*h];
        for(int y=first;y<=last;y++) {
            float distance=Math.abs((y-top)/gap-Math.round((y-top)/gap))*gap;
            if(distance<gap*.12f)continue;
            for(int x=left;x<=right;x++)if((gray[y*width+x]&255)<165)ink[(y-first)*w+x-left]=1;
        }
        List<Component> dots=new ArrayList<>();
        for(Component dot:findComponents(ink,w,h,(byte)1)) {
            int dw=dot.maxX-dot.minX+1,dh=dot.maxY-dot.minY+1;
            if(dw>=gap*.18f&&dw<=gap*.7f&&dh>=gap*.18f&&dh<=gap*.7f
                    &&dot.area>=dw*dh*.45f)dots.add(dot);
        }
        boolean paired=false;
        for(Component a:dots)for(Component b:dots)if(b.centerY>a.centerY
                &&Math.abs(a.centerX-b.centerX)<gap*.25f
                &&b.centerY-a.centerY>=gap*.55f&&b.centerY-a.centerY<=gap*1.4f
                &&Math.abs((a.centerY+b.centerY)*.5f+first-(top+gap))<gap*.3f)paired=true;
        if(!paired)return false;
        // A two-dot punctuation mark beside another glyph is insufficient: the
        // clef must also have a printed tail reaching below its lower dot.
        for(int y=Math.max(0,Math.round(top+gap*2.35f));y<=Math.min(height-1,Math.round(top+gap*3.4f));y++) {
            float distance=Math.abs((y-top)/gap-Math.round((y-top)/gap))*gap;
            if(distance<gap*.15f)continue;
            int count=0;
            for(int x=Math.max(0,body.minX);x<=Math.min(width-1,body.maxX);x++)
                if((gray[y*width+x]&255)<165)count++;
            if(count>=gap*.25f&&count<gw*.8f)return true;
        }
        return false;
    }

    private static Component joinSmallTrebleFragments(Component body,List<Component> glyphs,Staff staff) {
        float gap=staff.gap;
        if(body.maxY-body.minY<gap*3||body.maxY-body.minY>gap*4.8f
                ||body.maxX-body.minX<gap*1.3f||body.maxX-body.minX>gap*2.5f
                ||body.minY>staff.top+gap*.3f||body.minY<staff.top-gap
                ||body.maxY<staff.bottom-gap||body.maxY>staff.bottom+gap*.2f)return body;
        Component joined=body;
        for(Component part:glyphs)if(part!=body&&part.minX>=body.minX-gap*.4f&&part.maxX<=body.maxX+gap*.4f
                &&part.minY>=staff.top-gap&&part.maxY<=staff.bottom+gap*1.4f) {
            int area=joined.area+part.area;
            joined=new Component(area,Math.min(joined.minX,part.minX),Math.max(joined.maxX,part.maxX),
                    Math.min(joined.minY,part.minY),Math.max(joined.maxY,part.maxY),
                    (joined.centerX*joined.area+part.centerX*part.area)/area,
                    (joined.centerY*joined.area+part.centerY*part.area)/area);
        }
        return joined;
    }

    /** Staff-line predictions can sever the bottom curl from an otherwise complete treble clef. */
    private static Component joinTrebleCurl(Component body,List<Component> glyphs,Staff staff) {
        float gap=staff.gap;
        float gh=body.maxY-body.minY+1,gw=body.maxX-body.minX+1;
        // Require the large, wide, above-staff body first: never construct a clef from accidentals,
        // bass-clef dots, a bracket, or arbitrary small symbols near the bottom of a stave.
        if(gh<gap*4.8f||gh>gap*8.8f||gw<gap*1.25f||gw>gap*3.4f
                ||body.area<gap*gap*1.65f||body.minY>=staff.top-gap*.35f
                ||body.maxY<staff.bottom-gap||body.maxY>staff.bottom+gap*.20f
                ||Math.abs(body.centerY-(staff.top+staff.bottom)*.5f)>=gap*1.1f)return body;
        Component joined=body;
        // Compare integer row coordinates with a distance rounded to the same raster grid.
        for(Component tail:glyphs) {
            if(tail==body||tail.minX<body.minX-gap*.2f||tail.maxX>body.maxX+gap*.2f
                    ||tail.minY<staff.bottom-gap||tail.minY>body.maxY+Math.round(gap*.45f)
                    ||tail.maxY<=staff.bottom+gap*.2f||tail.maxY>staff.bottom+gap*2.2f
                    ||tail.area<gap*gap*.15f||tail.area>body.area*.75f)continue;
            int area=joined.area+tail.area;
            joined=new Component(area,Math.min(joined.minX,tail.minX),Math.max(joined.maxX,tail.maxX),
                    Math.min(joined.minY,tail.minY),Math.max(joined.maxY,tail.maxY),
                    (joined.centerX*joined.area+tail.centerX*tail.area)/area,
                    (joined.centerY*joined.area+tail.centerY*tail.area)/area);
        }
        return joined;
    }

    /** Count separated flag attachments close to the stem, only beside a proven short rest. */
    private static int rawDetachedFlags(byte[] gray, byte[] labels, int width, int height,
                                         Component head, float gap) {
        if (gray == null) return 0;
        int stemX = head.maxX, best = 0, direction = -1;
        int top = Math.max(0, Math.round(head.centerY - gap * 4.8f));
        int bottom = Math.max(0, Math.round(head.centerY - gap * .65f));
        int belowTop = Math.min(height-1, Math.round(head.centerY + gap * .65f));
        int belowBottom = Math.min(height-1, Math.round(head.centerY + gap * 4.8f));
        for (int x = Math.max(0, head.minX - 3); x <= Math.min(width - 1, head.maxX + 4); x++) {
            int count = countVertical(labels, width, x, top, bottom);
            if (count > best) { best = count; stemX = x; direction = -1; }
            count = countVertical(labels, width, x, belowTop, belowBottom);
            if (count > best) { best = count; stemX = x; direction = 1; }
        }
        if (best < gap) return 0;
        int limit = direction < 0 ? top : belowBottom;
        bottom = direction < 0 ? bottom : belowTop;
        int end = findStemEnd(labels, width, stemX, direction < 0,
                direction < 0 ? top : belowTop, direction < 0 ? bottom : belowBottom);
        // A staff crossing can hide the first flag's stem segment in the semantic mask.
        // Follow the attached raw stem outward from the head, allowing only tiny ink gaps.
        int blank = 0, rawEnd = Math.round(head.centerY);
        for (int y = Math.round(head.centerY + direction * gap * .3f);
                direction < 0 ? y >= limit : y <= limit; y += direction) {
            boolean dark = false;
            for (int x = Math.max(0,stemX-1); x <= Math.min(width-1,stemX+1); x++)
                if ((gray[y*width+x]&255)<170) dark = true;
            if (dark) { rawEnd=y; blank=0; }
            else if (++blank > Math.max(2,Math.round(gap*.2f))) break;
        }
        if (Math.abs(rawEnd-head.centerY)>gap*1.5f && Math.abs(rawEnd-end)<gap*1.8f) end=rawEnd;
        int left = Math.min(width - 1, Math.round(stemX + gap * .3f));
        int right = Math.min(width - 1, Math.round(stemX + gap * .55f));
        int groups = 0, run = 0, first = -1, last = -1;
        int span = Math.abs(bottom-end);
        for (int offset = 0; offset <= span + 1; offset++) {
            int y = end - direction * offset;
            int rowLeft = Math.max(0, stemX - Math.round(gap * 2));
            int rowRight = Math.min(width - 1, stemX + Math.round(gap * 2));
            int rowInk = 0;
            if (offset <= span) for (int x = rowLeft; x <= rowRight; x++)
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
            if (offset <= span) for (int x = left; x <= right; x++)
                if ((gray[y * width + x] & 255) < 170) ink++;
            if (ink >= 2) run++;
            else {
                if (run >= Math.max(2, gap * .12f)) {
                    groups++; if (first < 0) first = offset; last = offset;
                }
                run = 0;
            }
        }
        return groups == 2 && last - first >= gap * .75f && last - first <= gap * 2f ? 2
                : groups == 1 && first <= gap * 1.8f ? 1 : 0;
    }

    /**
     * Finds a compact run of signature accidentals immediately after a measure boundary and
     * before that stave's first note. Requiring at least two same-kind glyphs deliberately avoids
     * treating an ordinary local accidental as a modulation; one-flat/one-sharp changes remain
     * eligible only when the raw score also shows a double bar at the boundary.
     */
    private static List<ScoreKeyChange> detectKeyChanges(byte[] labels, byte[] gray, int width, int height,
                                                          List<MeasureRegion> measures,
                                                          List<Staff> staffs,
                                                          List<AccidentalCandidate> candidates,
                                                          List<Component> heads) {
        return detectKeyChangesWithHeaders(labels,gray,width,height,measures,staffs,candidates,heads,null);
    }

    private static List<ScoreKeyChange> detectKeyChangesWithHeaders(byte[] labels,byte[] gray,int width,int height,
            List<MeasureRegion> measures,List<Staff> staffs,List<AccidentalCandidate> candidates,
            List<Component> heads,List<Component> headerAccidentals) {
        List<ScoreKeyChange> result = new ArrayList<>();
        candidates = joinSignatureFragments(labels, width, candidates, staffs);
        candidates = splitSignatureSharps(labels, width, height, candidates, staffs);
        for (int measureIndex = 0; measureIndex < measures.size(); measureIndex++) {
            MeasureRegion measure = measures.get(measureIndex);
            Map<Integer, Integer> votes = new HashMap<>();
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
                    if (other.left() < measure.left() && staffCenter >= other.top() - tolerance
                            && staffCenter <= other.bottom() + tolerance) firstInRow = false;
                Component clef = null;
                if (firstInRow) for (AccidentalCandidate candidate : candidates) {
                    Component c = candidate.component;
                    if (candidate.label == OmrMeasurePostProcessor.CLEF_OR_KEY
                            && c.maxX < boundary && c.maxX > boundary - staff.gap * 10
                            && c.maxY - c.minY > staff.gap * 4.5f
                            && c.maxX - c.minX > staff.gap * 1.1f
                            && c.centerY > staff.top - staff.gap && c.centerY < staff.bottom + staff.gap
                            && (clef == null || c.maxX > clef.maxX)) clef = c;
                }
                if (clef != null) left = clef.maxX + staff.gap * .2f;
                float right = Math.min(measure.right() * width, left + staff.gap * 10.5f);
                float firstHead = Float.MAX_VALUE;
                for (Component head : heads) {
                    if (head.centerX < left - staff.gap * .2f || head.centerX > right) continue;
                    Staff owner = nearestHeadStaff(staffs, head.centerY);
                    if (owner == staff) firstHead = Math.min(firstHead, head.minX);
                }
                if (Float.isFinite(firstHead)) right = Math.min(right, firstHead - staff.gap * .28f);
                if (right <= left) continue;

                List<SignatureGlyph> glyphs = new ArrayList<>();
                Map<AccidentalCandidate,Float> recognized = new HashMap<>();
                for (AccidentalCandidate candidate : candidates) {
                    Component glyph = candidate.component;
                    if (glyph.centerX < left - staff.gap * .12f || glyph.centerX > right
                            || glyph.centerY < staff.top - staff.gap * 2.25f
                            || glyph.centerY > staff.bottom + staff.gap * 2.25f) continue;
                    // A fragmented semantic double bar may resemble a flat bowl.
                    // Its source column still crosses the complete staff.
                    if (glyph.maxX - glyph.minX + 1 <= Math.round(staff.gap * .65f)
                            && fullStaffRule(gray, width, height, Math.round(glyph.centerX), staff)) continue;
                    int accidental = isNaturalGlyph(labels, width, height, candidate, staff.gap)
                            ? ScoreNoteEvent.ACCIDENTAL_NATURAL
                            : isSharpGlyph(labels, width, height, candidate, staff.gap)
                            ? ScoreNoteEvent.ACCIDENTAL_SHARP
                            : isFlatGlyph(labels, width, height, candidate, staff.gap)
                            ? ScoreNoteEvent.ACCIDENTAL_FLAT
                            : ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
                    // Recover a substantial fragment in a verified header from the printed
                    // sharp. Tiny semantic specks can also occur on nearby meter digits.
                    float signatureX=glyph.centerX;
                    if(accidental==ScoreNoteEvent.ACCIDENTAL_FLAT
                            || accidental==ScoreNoteEvent.ACCIDENTAL_FROM_KEY && clef!=null
                            && glyph.area>=staff.gap*staff.gap*.18f) {
                        float printedX=printedSignatureSharpCenter(gray,width,height,candidate,staff.gap);
                        if(Float.isFinite(printedX)) {
                            accidental=ScoreNoteEvent.ACCIDENTAL_SHARP;
                            // A nearby rule fragment may recover the same printed sharp.
                            // Position it at that glyph so it cannot count as a second sharp.
                            signatureX=printedX;
                        }
                    }
                    if (accidental != ScoreNoteEvent.ACCIDENTAL_FROM_KEY) {
                        glyphs.add(new SignatureGlyph(signatureX, accidental));
                        recognized.put(candidate,signatureX);
                    }
                }
                glyphs.sort(Comparator.comparingDouble(SignatureGlyph::x));
                List<SignatureGlyph> run = densestSignatureRun(glyphs, staff.gap);
                if (run.isEmpty()) continue;
                int flats = 0, sharps = 0, naturals = 0;
                for (SignatureGlyph glyph : run) {
                    if (glyph.accidental == ScoreNoteEvent.ACCIDENTAL_FLAT) flats++;
                    else if (glyph.accidental == ScoreNoteEvent.ACCIDENTAL_SHARP) sharps++;
                    else if (glyph.accidental == ScoreNoteEvent.ACCIDENTAL_NATURAL) naturals++;
                }
                boolean doubleBar = hasDoubleBar(labels, gray, width, height, boundary, staff);
                boolean signatureHeader = clef != null && run.get(0).x < clef.maxX + staff.gap * 2.2f
                        && firstHead - run.get(run.size() - 1).x >= staff.gap * 1.35f;
                // Staff lines can cut a flat's bowl away from its spine, producing two semantic
                // components that are individually too incomplete for the local-accidental
                // classifier. Once at least one flat establishes the run's glyph family, count
                // the repeated tall left spines in the same pre-note slot.
                if (flats > 0 && sharps == 0 && naturals == 0) {
                    int spines = countFlatSpines(labels, gray, width, height, left, right, staff, doubleBar);
                    // One damaged sharp can satisfy the flat-bowl test; its two spines are
                    // not two separate flats. Do not change the inherited key on this evidence.
                    if (signatureHeader && run.size() == 1 && spines > flats) continue;
                    flats = Math.max(flats, spines);
                }
                int strongest = Math.max(naturals, Math.max(flats, sharps));
                if (strongest > 7 || (flats > 0 ? sharps + naturals > 0
                        : sharps > 0 ? naturals > 0 : false)) continue;
                if (strongest < 2 && !doubleBar && !signatureHeader) continue;
                // A single glyph pressed against the first head is a local accidental even
                // when its own spines confused the nearby double-bar test.
                if(strongest==1&&!signatureHeader&&firstHead-run.get(run.size()-1).x<staff.gap*1.35f)continue;
                int fifths = naturals > 0 ? 0 : flats > 0 ? -flats : sharps;
                if (signatureHeader && !result.isEmpty() && fifths > 0
                        && result.get(result.size() - 1).fifths() > fifths
                        && unfinishedSharpTail(labels, width, height, candidates, run, staff, firstHead))
                    continue;
                if (signatureHeader && headerAccidentals != null
                        && orderedSignaturePitches(labels,width,height,run,recognized,staff))
                    for (var entry : recognized.entrySet())
                        if (run.stream().anyMatch(glyph->Math.abs(glyph.x-entry.getValue())<staff.gap*.1f))
                            headerAccidentals.add(entry.getKey().component);
                votes.put(fifths, votes.getOrDefault(fifths, 0) + 1);
            }
            int bestFifths = 0, bestVotes = 0;
            for (Map.Entry<Integer, Integer> vote : votes.entrySet())
                if (vote.getValue() > bestVotes) {
                    bestFifths = vote.getKey(); bestVotes = vote.getValue();
                }
            // A damaged repeated header on one stave must not outvote the intact matching
            // header on another. Preserve the established key when support is tied.
            if(!result.isEmpty()) {
                int inherited=result.get(result.size()-1).fifths();
                if(votes.getOrDefault(inherited,0)==bestVotes&&bestVotes>0)bestFifths=inherited;
            }
            if (bestVotes > 0 && (result.isEmpty() || result.get(result.size() - 1).fifths() != bestFifths))
                result.add(new ScoreKeyChange(measureIndex, bestFifths));
        }
        return List.copyOf(result);
    }

    /** Adjacent key symbols follow fourths/fifths; a nearby note accidental need not. */
    private static boolean orderedSignaturePitches(byte[] labels,int width,int height,
            List<SignatureGlyph> run,Map<AccidentalCandidate,Float> recognized,Staff staff) {
        if(run.size()<2)return false;
        int family=run.get(0).accidental;
        float previous=0;
        if(family!=ScoreNoteEvent.ACCIDENTAL_FLAT&&family!=ScoreNoteEvent.ACCIDENTAL_SHARP)return false;
        for(int i=0;i<run.size();i++) {
            SignatureGlyph symbol=run.get(i);
            if(symbol.accidental!=family)return false;
            AccidentalCandidate owner=null;
            for(var entry:recognized.entrySet())
                if(Math.abs(entry.getValue()-symbol.x)<staff.gap*.1f){owner=entry.getKey();break;}
            if(owner==null)return false;
            float center=family==ScoreNoteEvent.ACCIDENTAL_FLAT?flatPitchCenter(labels,width,owner,staff.pitchGap):
                    sharpPitchCenter(labels,width,height,owner,staff.pitchGap);
            if(!Float.isFinite(center))return false;
            int interval=Math.round((previous-center)*2/staff.pitchGap);
            if(i>0&&Math.floorMod(interval,7)!=(family==ScoreNoteEvent.ACCIDENTAL_FLAT?3:4))return false;
            previous=center;
        }
        return true;
    }

    /** A staff stripe can leave only one lobe of a sharp in the semantic mask.
     * Recover its narrow printed column only when both complete spines and both
     * crossbars prove a sharp; a flat's bowl alone cannot supply that evidence. */
    private static float printedSignatureSharpCenter(byte[] gray,int width,int height,
            AccidentalCandidate candidate,float gap) {
        if(gray==null||gap<3)return Float.NaN;
        Component seed=candidate.component;
        if(seed.maxX-seed.minX+1>gap*1.6f||seed.maxY-seed.minY+1>gap*3.65f)return Float.NaN;
        int margin=Math.max(1,Math.round(gap*.3f));
        int left=Math.max(0,seed.minX-margin),right=Math.min(width-1,seed.maxX+margin);
        int top=Math.max(0,Math.round(seed.centerY-gap*2.5f));
        int bottom=Math.min(height-1,Math.round(seed.centerY+gap*2.5f));
        int w=right-left+1,h=bottom-top+1,reach=Math.max(3,Math.round(gap*.7f));
        int probe=Math.max(2,Math.round(gap*.2f));
        for(int threshold:new int[]{180,205}) {
            byte[] ink=new byte[w*h];
            for(int y=top;y<=bottom;y++) {
                int outside=0,dark=0;
                for(int x=Math.max(0,left-reach);x<=Math.min(width-1,right+reach);x++)
                    if(x<left||x>right){outside++;if((gray[y*width+x]&255)<=threshold)dark++;}
                boolean rule=outside>0&&dark>=outside*.8f;
                for(int x=left;x<=right;x++) {
                    if((gray[y*width+x]&255)>threshold)continue;
                    if(rule&&(y<probe||y+probe>=height||(gray[(y-probe)*width+x]&255)>threshold
                            ||(gray[(y+probe)*width+x]&255)>threshold))continue;
                    ink[(y-top)*w+x-left]=OmrMeasurePostProcessor.SYMBOL;
                }
            }
            Component glyph=retainSeedConnectedInk(ink,w,h,seed,left,top);
            if(glyph==null||rawStrokeLeavesCrop(gray,width,height,ink,w,h,left,top,gap))continue;
            if(isSharpGlyph(ink,w,h,new AccidentalCandidate(glyph,OmrMeasurePostProcessor.SYMBOL),gap))return left+glyph.centerX;
        }
        return Float.NaN;
    }

    /** A visibly unfinished extra sharp cannot prove that a repeated key has fewer sharps. */
    private static boolean unfinishedSharpTail(byte[] labels, int width, int height,
            List<AccidentalCandidate> candidates, List<SignatureGlyph> run, Staff staff, float firstHead) {
        float lastX = run.get(run.size() - 1).x, lastPitch = Float.NaN;
        for (AccidentalCandidate candidate : candidates)
            if (Math.abs(candidate.component.centerX - lastX) < staff.gap * .35f
                    && candidate.component.centerY >= staff.top - staff.gap * 2.25f
                    && candidate.component.centerY <= staff.bottom + staff.gap * 2.25f) {
                float pitch = sharpPitchCenter(labels, width, height, candidate, staff.gap);
                if (Float.isFinite(pitch)) lastPitch = pitch;
            }
        if (!Float.isFinite(lastPitch)) return false;
        for (AccidentalCandidate candidate : candidates) {
            Component c = candidate.component;
            float dx = c.centerX - lastX;
            if (candidate.label != OmrMeasurePostProcessor.CLEF_OR_KEY
                    || c.centerY < staff.top - staff.gap * 2.25f
                    || c.centerY > staff.bottom + staff.gap * 2.25f
                    || dx < staff.gap * .65f || dx > staff.gap * 1.85f
                    || firstHead - c.centerX < staff.gap * 1.35f
                    || c.maxX-c.minX+1 < staff.gap*.5f || c.maxX-c.minX+1 > staff.gap*1.8f
                    || c.maxY-c.minY+1 < staff.gap*1.2f || c.maxY-c.minY+1 > staff.gap*3.65f
                    || c.area < staff.gap*staff.gap*.3f
                    || isNaturalGlyph(labels,width,height,candidate,staff.gap)
                    || isFlatGlyph(labels,width,height,candidate,staff.gap)
                    || isSharpGlyph(labels,width,height,candidate,staff.gap)) continue;
            for (float expected : new float[]{lastPitch + staff.gap*1.5f, lastPitch - staff.gap*2f})
                if (expected >= c.minY - staff.gap*.2f && expected <= c.maxY + staff.gap*.2f) return true;
        }
        return false;
    }

    /** Resolve joined signature sharps only when each slice has a complete sharp shape. */
    private static List<AccidentalCandidate> splitSignatureSharps(byte[] labels, int width, int height,
            List<AccidentalCandidate> candidates, List<Staff> staffs) {
        List<AccidentalCandidate> result = new ArrayList<>();
        for (AccidentalCandidate candidate : candidates) {
            Staff staff = nearestHeadStaff(staffs, candidate.component.centerY);
            List<AccidentalCandidate> parts = candidate.label == OmrMeasurePostProcessor.CLEF_OR_KEY
                    && staff != null ? splitSignatureSharpRun(labels, width, height, candidate, staff.gap, 7)
                    : List.of();
            if (parts.size() >= 2) result.addAll(parts); else result.add(candidate);
        }
        return result;
    }

    private static List<AccidentalCandidate> splitSignatureSharpRun(byte[] labels, int width, int height,
            AccidentalCandidate candidate, float gap, int remaining) {
        Component box = candidate.component;
        if (isSharpGlyph(labels, width, height, candidate, gap)) return List.of(candidate);
        if (remaining < 2 || box.maxX - box.minX + 1 < gap * 1.65f
                || box.maxX - box.minX + 1 > gap * remaining * 1.8f
                || box.maxY - box.minY + 1 > gap * 7f) return List.of();
        int first = box.minX + Math.max(3, Math.round(gap * .65f));
        int last = Math.min(box.maxX - Math.max(3, Math.round(gap * .65f)),
                box.minX + Math.round(gap * 1.8f));
        for (int cut = first; cut <= last; cut++) {
            AccidentalCandidate left = signatureSlice(labels, width, candidate, box.minX, cut);
            if (left == null) continue;
            float leftPitch = sharpPitchCenter(labels, width, height, left, gap);
            if (!Float.isFinite(leftPitch)) continue;
            AccidentalCandidate right = signatureSlice(labels, width, candidate, cut + 1, box.maxX);
            if (right == null) continue;
            List<AccidentalCandidate> rest = splitSignatureSharpRun(labels, width, height, right, gap, remaining - 1);
            if (rest.isEmpty()) continue;
            float delta = sharpPitchCenter(labels, width, height, rest.get(0), gap) - leftPitch;
            // Consecutive signature sharps alternate a fourth down or a fifth up.
            if (Math.abs(delta - gap * 1.5f) > gap * .55f
                    && Math.abs(delta + gap * 2f) > gap * .55f) continue;
            List<AccidentalCandidate> result = new ArrayList<>();
            result.add(left); result.addAll(rest); return result;
        }
        return List.of();
    }

    private static AccidentalCandidate signatureSlice(byte[] labels, int width,
            AccidentalCandidate candidate, int left, int right) {
        Component box = candidate.component;
        int cropWidth = right - left + 1, cropHeight = box.maxY - box.minY + 1;
        byte[] crop = new byte[cropWidth * cropHeight];
        for (int y = box.minY; y <= box.maxY; y++) for (int x = left; x <= right; x++)
            if (candidate.matches(labels[y * width + x])) crop[(y - box.minY) * cropWidth + x - left] = 3;
        Component best = null;
        for (Component piece : findComponents(crop, cropWidth, cropHeight, (byte) 3))
            if (best == null || piece.area > best.area) best = piece;
        return best == null ? null : new AccidentalCandidate(new Component(best.area,
                best.minX + left, best.maxX + left, best.minY + box.minY, best.maxY + box.minY,
                best.centerX + left, best.centerY + box.minY), candidate.label);
    }

    /** Reconnect a signature glyph cut horizontally by a staff-labelled scan line. */
    private static List<AccidentalCandidate> joinSignatureFragments(byte[] labels, int width,
            List<AccidentalCandidate> candidates, List<Staff> staffs) {
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
                int left = Math.max(upper.minX, lower.minX), right = Math.min(upper.maxX, lower.maxX);
                if (separation < 0 || separation > gap * .35f
                        // A staff line can separate a flat's thin upper stem from its wider
                        // lower bowl. Require overlap of the narrower fragment; requiring
                        // the bowl's width leaves a truncated glyph that can resemble a sharp.
                        || right - left + 1 < Math.min(upper.maxX-upper.minX+1, lower.maxX-lower.minX+1) * .70f
                        || lower.maxY-upper.minY+1 > gap*3.65f
                        || Math.max(upper.maxX,lower.maxX)-Math.min(upper.minX,lower.minX)+1 > gap*1.65f) continue;
                boolean staffCut = false;
                for (int y=upper.maxY;y<=lower.minY;y++)
                    if (rowLabelCount(labels,width,y,left,right,OmrMeasurePostProcessor.STAFF) >= (right-left+1)*.7f) staffCut=true;
                if (!staffCut) continue;
                int area=upper.area+lower.area;
                Component merged=new Component(area,Math.min(upper.minX,lower.minX),Math.max(upper.maxX,lower.maxX),
                        upper.minY,lower.maxY,(upper.centerX*upper.area+lower.centerX*lower.area)/area,
                        (upper.centerY*upper.area+lower.centerY*lower.area)/area);
                a=new AccidentalCandidate(merged,a.label); joined.set(i,a);joined.remove(j--);
            }
        }
        return joined;
    }

    private static int countFlatSpines(byte[] labels, byte[] gray, int width, int height,
                                       float boundary, float right, Staff staff,
                                       boolean doubleBar) {
        int leftX=Math.max(0,Math.round(boundary+(doubleBar?staff.gap*.42f:0f)));
        int rightX=Math.min(width-1,Math.round(right));
        int top=Math.max(0,Math.round(staff.top-staff.gap*2.25f));
        int bottom=Math.min(height-1,Math.round(staff.bottom+staff.gap*2.25f));
        int threshold=Math.max(3,Math.round(staff.gap*1.55f));
        List<float[]> spines=new ArrayList<>();
        float[] active=null;
        for(int x=leftX;x<=rightX;x++) {
            int pixels=0,run=0,longest=0,blanks=0,start=top,bestTop=top,bestBottom=top;
            for(int y=top;y<=bottom;y++) {
                byte label=labels[y*width+x];
                boolean symbol=label==OmrMeasurePostProcessor.CLEF_OR_KEY||label==OmrMeasurePostProcessor.SYMBOL;
                if(symbol)pixels++;
                boolean ink=gray==null?symbol:(gray[y*width+x]&255)<=205;
                if(ink) {
                    if(run==0)start=y;
                    run++;blanks=0;
                    if(run>longest){longest=run;bestTop=start;bestBottom=y;}
                } else if(++blanks>1)run=0;
            }
            boolean strong=pixels>=threshold&&longest>=threshold
                    &&!fullStaffRule(gray,width,height,x,staff);
            if(strong) {
                if(active==null)active=new float[]{x,(bestTop+bestBottom)*.5f,longest};
                else if(longest>active[2]){active[0]=x;active[1]=(bestTop+bestBottom)*.5f;active[2]=longest;}
            } else if(active!=null){spines.add(active);active=null;}
        }
        if(active!=null)spines.add(active);
        if(spines.isEmpty())return 0;
        int groups=1;float[] previous=spines.get(0);
        for(int i=1;i<spines.size();i++) {
            float[] next=spines.get(i);float dx=next[0]-previous[0];
            // A bowl edge is not another accidental. Keys have separate, closely
            // spaced spines, alternating a fourth up or a fifth down on the page.
            if(dx<staff.gap*.5f)continue;
            if(dx>staff.gap*1.85f)break;
            float dy=next[1]-previous[1];
            if(Math.abs(dy+staff.gap*1.5f)>staff.gap*.55f
                    &&Math.abs(dy-staff.gap*2f)>staff.gap*.55f)continue;
            groups++;previous=next;
        }
        return groups;
    }

    private static boolean fullStaffRule(byte[] gray, int width, int height, int x, Staff staff) {
        return fullStaffRule(gray,width,height,x,staff,205);
    }

    private static boolean fullStaffRule(byte[] gray, int width, int height, int x, Staff staff,int threshold) {
        if (gray == null) return false;
        int covered = 0, sampled = 0;
        int margin = Math.max(1, Math.round(staff.gap * .14f));
        for (int line = 0; line < 4; line++) {
            int first = Math.max(0, Math.round(staff.top + line * staff.gap) + margin + 1);
            int last = Math.min(height - 1, Math.round(staff.top + (line + 1) * staff.gap) - margin - 1);
            int space = 0;
            for (int y = first; y <= last; y++) if ((gray[y * width + x] & 255) <= threshold) space++;
            int count = Math.max(0, last - first + 1);
            if (space < count * .55f) return false;
            covered += space;
            sampled += count;
        }
        return sampled > 0 && covered >= sampled * .90f;
    }

    private static List<SignatureGlyph> densestSignatureRun(List<SignatureGlyph> glyphs, float gap) {
        List<SignatureGlyph> best = List.of();
        for (int start = 0; start < glyphs.size(); start++) {
            List<SignatureGlyph> current = new ArrayList<>();
            current.add(glyphs.get(start));
            for (int index = start + 1; index < glyphs.size(); index++) {
                SignatureGlyph previous = current.get(current.size() - 1);
                SignatureGlyph next = glyphs.get(index);
                // Stacked local accidentals belong to chord pitches, not to a
                // horizontally ordered key signature.
                if(next.x-previous.x<gap*.5f)continue;
                if (next.x - previous.x > gap * 1.85f
                        || next.x - current.get(0).x > gap * 7.8f) break;
                current.add(next);
            }
            if (current.size() > best.size()) best = current;
        }
        return best;
    }

    private static boolean hasDoubleBar(byte[] labels, byte[] gray, int width, int height,
                                        float boundaryX, Staff staff) {
        int left = Math.max(0, Math.round(boundaryX - staff.gap * 1.1f));
        int right = Math.min(width - 1, Math.round(boundaryX + staff.gap * 1.1f));
        int top = Math.max(0, Math.round(staff.top - staff.gap * .3f));
        int bottom = Math.min(height - 1, Math.round(staff.bottom + staff.gap * .3f));
        int groups = 0;
        boolean previous = false;
        for (int x = left; x <= right; x++) {
            int ink = 0;
            for (int y = top; y <= bottom; y++)
                if (labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST
                        || gray != null && (gray[y * width + x] & 0xff) < 180) ink++;
            boolean strong = ink >= (bottom - top + 1) * .72f;
            if (strong && !previous) groups++;
            previous = strong;
        }
        if(groups>=2)return true;
        // Thin faded bars can lose their semantic stem labels. Require two
        // separate raw columns crossing every staff space, not just the rules
        // or the shorter parallel spines of a sharp.
        for(int threshold:new int[]{205,225}) {
            groups=0;previous=false;
            for(int x=left;x<=right;x++) {
                boolean strong=fullStaffRule(gray,width,height,x,staff,threshold);
                if(strong&&!previous)groups++;
                previous=strong;
            }
            if(groups>=2)return true;
        }
        return false;
    }

    private static void logHeadCoverage(List<Staff> staffs, List<Component> rawComponents,
                                         List<Component> splitComponents,
                                         List<Component> accepted, List<Component> demoted,
                                         List<DetectedNote> emitted) {
        int[] rawByStaff = new int[staffs.size()];
        int[] splitByStaff = new int[staffs.size()];
        int[] acceptedByStaff = new int[staffs.size()];
        int[] demotedByStaff = new int[staffs.size()];
        int[] emittedByStaff = new int[staffs.size()];
        int[] tallByStaff = new int[staffs.size()];
        float[] maxHeightByStaff = new float[staffs.size()];
        for (Component component : rawComponents) incrementNearest(staffs, rawByStaff, component.centerY);
        for (Component component : splitComponents) incrementNearest(staffs, splitByStaff, component.centerY);
        for (Component component : rawComponents) {
            Staff nearest = nearestStaff(staffs, component.centerY);
            int index = nearest == null ? -1 : staffs.indexOf(nearest);
            if (index < 0) continue;
            float ratio = (component.maxY - component.minY + 1f) / nearest.gap;
            if (ratio >= .90f) tallByStaff[index]++;
            maxHeightByStaff[index] = Math.max(maxHeightByStaff[index], ratio);
        }
        for (Component component : accepted) incrementNearest(staffs, acceptedByStaff, component.centerY);
        for (Component component : demoted) incrementNearest(staffs, demotedByStaff, component.centerY);
        for (DetectedNote note : emitted) incrementNearest(staffs, emittedByStaff, note.head.centerY);
        try {
            Diagnostics.log("Head coverage raw="
                    + java.util.Arrays.toString(rawByStaff) + " split="
                    + java.util.Arrays.toString(splitByStaff) + " accepted="
                    + java.util.Arrays.toString(acceptedByStaff) + " demotedDots="
                    + java.util.Arrays.toString(demotedByStaff) + " emitted="
                    + java.util.Arrays.toString(emittedByStaff) + " tall="
                    + java.util.Arrays.toString(tallByStaff) + " maxHeight="
                    + java.util.Arrays.toString(maxHeightByStaff));
        } catch (RuntimeException ignored) {
            // Diagnostics are optional and must not fail recognition.
        }
    }

    private static void incrementNearest(List<Staff> staffs, int[] counts, float centerY) {
        Staff nearest = nearestStaff(staffs, centerY);
        int index = nearest == null ? -1 : staffs.indexOf(nearest);
        if (index >= 0) counts[index]++;
    }

    /** the model occasionally joins the two vertically aligned ovals of a printed dyad into one
     * NOTEHEAD component. Split only an abnormally tall component with two strong ink lobes and
     * a real valley between them; ordinary single heads, dots, and solid artifacts stay intact. */
    private static List<Component> splitStackedHeads(byte[] labels, byte[] gray, int width, int height,
                                                      List<Component> source,
                                                      List<Staff> staffs) {
        List<Component> result = new ArrayList<>();
        for (Component component : source) {
            Staff staff = nearestHeadStaff(staffs, component.centerY);
            float componentHeight = component.maxY - component.minY + 1f;
            List<Component> run=splitRepeatedHeadRun(labels,gray,width,height,component,staff);
            if(!run.isEmpty()){result.addAll(run);continue;}
            List<Component> filled = splitTouchingFilledVoices(labels, gray, width, height,
                    component, staff);
            if (!filled.isEmpty()) { result.addAll(filled); continue; }
            List<Component> mixed=splitMixedUnisonStack(labels,gray,width,height,component,staff);
            if(!mixed.isEmpty()){result.addAll(mixed);continue;}
            List<Component> regular = splitRegularStack(labels, width, component, staff);
            if (!regular.isEmpty()) {
                result.addAll(regular);
                continue;
            }
            List<Component> hollow = splitHollowStack(labels, gray, width, height, component, staff);
            if (!hollow.isEmpty()) {
                result.addAll(hollow);
                continue;
            }
            if (staff == null || componentHeight < staff.gap * 1.45f
                    || componentHeight > staff.gap * 3.2f) {
                result.add(component);
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
                if (count < valley) { valley = count; split = y; }
            }
            if (split >= 0) {
                for (int y = component.minY; y < split; y++)
                    upperPeak = Math.max(upperPeak, rowInk[y - component.minY]);
                for (int y = split + 1; y <= component.maxY; y++)
                    lowerPeak = Math.max(lowerPeak, rowInk[y - component.minY]);
            }
            Component upper = split < 0 ? null : componentSlice(labels, width, component,
                    component.minY, split);
            Component lower = split < 0 ? null : componentSlice(labels, width, component,
                    split + 1, component.maxY);
            boolean twoLobes = upper != null && lower != null
                    && upperPeak >= Math.max(2, Math.round(staff.gap * .24f))
                    && lowerPeak >= Math.max(2, Math.round(staff.gap * .24f))
                    && valley <= Math.min(upperPeak, lowerPeak) * .82f
                    && plausibleHead(upper, staff.gap) && plausibleHead(lower, staff.gap)
                    && lower.centerY - upper.centerY >= staff.gap * .58f
                    && lower.centerY - upper.centerY <= staff.gap * 2.25f
                    && Math.abs(lower.centerX - upper.centerX) <= staff.gap * 1.45f;
            if(!twoLobes&&gray!=null&&componentHeight>=staff.gap*1.7f
                    &&componentHeight<=staff.gap*2.6f&&component.maxX-component.minX+1<=staff.gap*1.7f) {
                // A semantic bridge can fill the neck between two solid heads.
                // The printed silhouette must independently contain two lobes.
                // A darker core can reveal the neck when pale edge ink joins it.
                for(int inkThreshold:new int[]{165,120}) {
                    int[] rawRows=new int[rowInk.length];
                    for(int y=component.minY;y<=component.maxY;y++)
                        for(int x=component.minX;x<=component.maxX;x++)
                            if((gray[y*width+x]&255)<=inkThreshold)rawRows[y-component.minY]++;
                    int neck=-1,minimum=Integer.MAX_VALUE;
                    for(int y=firstSplit;y<=lastSplit;y++)if(rawRows[y-component.minY]<minimum) {
                        minimum=rawRows[y-component.minY];neck=y;
                    }
                    int peakAbove=0,peakBelow=0;
                    for(int y=component.minY;y<neck;y++)peakAbove=Math.max(peakAbove,rawRows[y-component.minY]);
                    for(int y=neck+1;y<=component.maxY;y++)peakBelow=Math.max(peakBelow,rawRows[y-component.minY]);
                    if(inkThreshold<165) {
                        int upperCore=0,lowerCore=0,coreRun=0;
                        for(int y=component.minY;y<neck;y++) {
                            coreRun=rawRows[y-component.minY]>=peakAbove*.6f?coreRun+1:0;upperCore=Math.max(upperCore,coreRun);
                        }
                        coreRun=0;
                        for(int y=neck+1;y<=component.maxY;y++) {
                            coreRun=rawRows[y-component.minY]>=peakBelow*.6f?coreRun+1:0;lowerCore=Math.max(lowerCore,coreRun);
                        }
                        // Isolated dark rules or strokes cannot supply filled oval cores.
                        if(upperCore<staff.gap*.35f||lowerCore<staff.gap*.35f)continue;
                    }
                    Component a=neck<0?null:componentSlice(labels,width,component,component.minY,neck);
                    Component b=neck<0?null:componentSlice(labels,width,component,neck+1,component.maxY);
                    if(a!=null&&b!=null&&minimum<=Math.min(peakAbove,peakBelow)*.78f
                            &&peakAbove>=staff.gap*.65f&&peakBelow>=staff.gap*.65f
                            &&plausibleHead(a,staff.gap)&&plausibleHead(b,staff.gap)
                            &&!hasOpenCenter(labels,gray,width,height,a,staff.gap)
                            &&!hasOpenCenter(labels,gray,width,height,b,staff.gap)
                            &&b.centerY-a.centerY>=staff.gap*.58f&&b.centerY-a.centerY<=staff.gap*2.25f
                            &&Math.abs(a.centerX-b.centerX)<=staff.gap*1.45f) {
                        upper=a;lower=b;twoLobes=true;break;
                    }
                }
            }
            if (!twoLobes && gray != null && componentHeight>=staff.gap*1.7f
                    && componentHeight<=staff.gap*2.6f
                    && component.maxX-component.minX+1<=staff.gap*1.8f) {
                // The model can fill both hollow ovals and their connecting ledger line into
                // one solid mask. Two separate white centres in the original engraving are
                // stronger evidence than a missing valley in that semantic mask.
                int middle=(component.minY+component.maxY)/2;
                Component a=componentSlice(labels,width,component,component.minY,middle);
                Component b=componentSlice(labels,width,component,middle+1,component.maxY);
                if(a!=null&&b!=null&&plausibleHead(a,staff.gap)&&plausibleHead(b,staff.gap)
                        &&b.centerY-a.centerY>=staff.gap*.75f
                        &&hasOpenCenter(labels,gray,width,height,a,staff.gap)
                        &&hasOpenCenter(labels,gray,width,height,b,staff.gap)) {
                    upper=a;lower=b;twoLobes=true;
                }
            }
            if (twoLobes) {
                result.add(upper);
                result.add(lower);
            } else result.add(component);
        }
        return List.copyOf(result);
    }

    /** Thin semantic bridges can join a tightly engraved repeated run. Require
     * distinct raw oval lobes and an attached stem for every recovered attack. */
    private static List<Component> splitRepeatedHeadRun(byte[] labels,byte[] gray,int width,int height,
                                                       Component head,Staff staff) {
        if(gray==null||staff==null)return List.of();
        float gap=staff.gap;int w=head.maxX-head.minX+1,h=head.maxY-head.minY+1;
        if(w<gap*3.2f||h<gap*.65f||h>gap*1.4f)return List.of();
        int[] columns=new int[w];
        for(int x=0;x<w;x++)for(int y=head.minY;y<=head.maxY;y++)
            if((gray[y*width+head.minX+x]&255)<=165)columns[x]++;
        List<Integer> cuts=new ArrayList<>();
        for(int x=0;x<w;) {
            if(columns[x]>h*.35f){x++;continue;}
            int start=x;while(x<w&&columns[x]<=h*.35f)x++;
            if(start>0&&x<w)cuts.add(head.minX+(start+x-1)/2);
        }
        if(cuts.size()<2)return List.of();
        cuts.add(head.maxX);List<Component> result=new ArrayList<>();int left=head.minX;
        for(int right:cuts) {
            Component part=horizontalHeadSlice(labels,width,head,left,right);left=right+1;
            if(part==null||!plausibleHead(part,gap)||part.maxX-part.minX+1<gap*.8f
                    ||part.maxX-part.minX+1>gap*1.9f||part.area<gap*gap*.4f
                    ||Math.abs(part.centerY-head.centerY)>gap*.25f
                    ||hasOpenCenter(labels,gray,width,height,part,gap)
                    ||attachedRawStem(gray,width,height,part,gap)==null)return List.of();
            int peak=0;for(int x=part.minX;x<=part.maxX;x++)peak=Math.max(peak,columns[x-head.minX]);
            if(peak<h*.7f)return List.of();
            result.add(part);
        }
        return List.copyOf(result);
    }

    /** A narrow raw-ink neck and opposing stems identify two touching filled voices.
     * Split before event decoding so each head retains its own position and duration. */
    private static List<Component> splitTouchingFilledVoices(byte[] labels, byte[] gray,
            int width, int height, Component head, Staff staff) {
        if (gray == null || staff == null) return List.of();
        float gap = staff.pitchGap;
        int w = head.maxX - head.minX + 1, h = head.maxY - head.minY + 1;
        if (w < gap * 1.8f || w > gap * 3.1f || h < gap * .65f || h > gap * 1.4f)
            return List.of();
        int middle = (head.minX + head.maxX) / 2;
        Component left = horizontalHeadSlice(labels, width, head, head.minX, middle);
        Component right = horizontalHeadSlice(labels, width, head, middle + 1, head.maxX);
        if (left == null || right == null || !plausibleHead(left, gap) || !plausibleHead(right, gap)
                || Math.abs(left.centerY - right.centerY) > gap * .3f
                || left.area < gap * gap * .35f || right.area < gap * gap * .35f
                || hasOpenCenter(labels, gray, width, height, left, gap)
                || hasOpenCenter(labels, gray, width, height, right, gap)) return List.of();
        int leftUp = attachedStemReach(labels, width, height, left, gap, true);
        int leftDown = attachedStemReach(labels, width, height, left, gap, false);
        int rightUp = attachedStemReach(labels, width, height, right, gap, true);
        int rightDown = attachedStemReach(labels, width, height, right, gap, false);
        int minimum = Math.max(3, Math.round(gap * .72f));
        boolean opposing = (leftDown >= minimum && rightUp >= minimum
                && leftUp < minimum && rightDown < minimum)
                || (leftUp >= minimum && rightDown >= minimum
                && leftDown < minimum && rightUp < minimum);
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
    private static List<Component> splitMixedUnisonStack(byte[] labels,byte[] gray,int width,int height,
                                                         Component source,Staff staff) {
        if(gray==null||staff==null)return List.of();
        float gap=staff.gap;int w=source.maxX-source.minX+1,h=source.maxY-source.minY+1;
        if(w<gap*2.6f||w>gap*3.6f||h<gap*1.8f||h>gap*2.7f)return List.of();
        int middle=source.minX+Math.round(gap*1.45f);
        Component left=horizontalHeadSlice(labels,width,source,source.minX,middle);
        Component right=horizontalHeadSlice(labels,width,source,middle+1,source.maxX);
        if(left==null||right==null||!plausibleHead(left,gap)||left.maxY-left.minY>gap*1.4f
                ||hasOpenCenter(labels,gray,width,height,left,gap)
                ||!hasAttachedStem(labels,width,height,left,gap))return List.of();
        List<Component> parts=splitRegularStack(labels,width,right,staff);
        if(parts.size()!=2)return List.of();
        for(Component part:parts)if(!hasOpenCenter(labels,gray,width,height,part,gap))return List.of();
        if(parts.stream().noneMatch(part->Math.abs(part.centerY-left.centerY)<gap*.3f))return List.of();
        return List.of(left,parts.get(0),parts.get(1));
    }

    /** Follow every broad oval lobe, rather than choosing the single darkest neck.
     * A filled-in ledger bridge can move that neck inside a hollow head, and a
     * triad has two necks. Staff-spaced broad lobes establish the actual centres. */
    private static List<Component> splitRegularStack(byte[] labels, int width,
                                                      Component component, Staff staff) {
        if (staff == null) return List.of();
        float gap = staff.gap;
        int height = component.maxY - component.minY + 1;
        if (height < gap * 1.7f || height > gap * 4.4f
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
        for (int y = 0; y < height;) {
            if (rows[y] < peak * .85f) { y++; continue; }
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
            int bottom = i + 1 == centers.size() ? component.maxY
                    : Math.round((centers.get(i) + centers.get(i + 1)) * .5f);
            Component part = componentSlice(labels, width, component, top, bottom);
            if (part == null || !plausibleHead(part, gap)
                    || Math.abs(part.centerY - centers.get(i)) > gap * .3f) return List.of();
            parts.add(part);
            top = bottom + 1;
        }
        return parts;
    }

    /** Ledger paint can hide a middle lobe of a hollow triad in the mask. Accept
     * equal slices only when every slice contains an independently printed open oval. */
    private static List<Component> splitHollowStack(byte[] labels, byte[] gray, int width, int height,
                                                    Component head, Staff staff) {
        if (gray == null || staff == null) return List.of();
        float gap = staff.pitchGap;
        int span = head.maxY - head.minY + 1;
        int count = Math.round(span / gap);
        if (count < 3 || count > 4 || span < gap * (count - .25f)
                || span > gap * (count + .4f)
                || head.maxX - head.minX + 1 > gap * 1.8f) return List.of();
        List<Component> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int top = head.minY + Math.round(i * span / (float)count);
            int bottom = head.minY + Math.round((i + 1) * span / (float)count) - 1;
            Component part = componentSlice(labels, width, head, top, bottom);
            if (part == null || !plausibleHead(part, gap)
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
    private static boolean hasHollowUnisonToRight(byte[] labels,byte[] gray,int width,int height,
                                                Component head,List<Component> heads,float gap) {
        if(gray==null)return false;
        for(Component other:heads)if(other.centerX-head.centerX>=gap&&other.centerX-head.centerX<=gap*2
                &&Math.abs(other.centerY-head.centerY)<gap*.3f
                &&hasAttachedStem(labels,width,height,other,gap)
                &&hasOpenCenter(labels,gray,width,height,other,gap))return true;
        return false;
    }

    private static List<Component> sideBySideUnison(byte[] labels,byte[] gray,int width,int height,
                                                   Component head,float gap) {
        float w=head.maxX-head.minX+1,h=head.maxY-head.minY+1;
        // Compare component bounds on their integer-pixel grid when staff spacing is fractional.
        if(gray==null||w<gap*1.8f||w>Math.round(gap*3.1f)||h<gap*.65f||h>gap*1.4f)return List.of();
        int middle=(head.minX+head.maxX)/2;
        Component left=horizontalHeadSlice(labels,width,head,head.minX,middle);
        Component right=horizontalHeadSlice(labels,width,head,middle+1,head.maxX);
        if(left==null||right==null||Math.abs(left.centerY-right.centerY)>gap*.3f
                ||left.area<gap*gap*.35f||right.area<gap*gap*.3f
                ||hasOpenCenter(labels,gray,width,height,left,gap)
                ||!hasOpenCenter(labels,gray,width,height,right,gap)
                ||!hasAttachedStem(labels,width,height,left,gap)
                ||!hasAttachedStem(labels,width,height,right,gap))return List.of();
        return List.of(left,right);
    }

    private static List<Component> sideBySideSeconds(byte[] labels,int width,Component head,float gap) {
        float w=head.maxX-head.minX+1,h=head.maxY-head.minY+1;
        if(w<gap*2.1f||w>gap*3.1f||h<gap*1.05f||h>gap*1.9f)return List.of();
        int middle=(head.minX+head.maxX)/2;
        Component a=horizontalHeadSlice(labels,width,head,head.minX,middle);
        Component b=horizontalHeadSlice(labels,width,head,middle+1,head.maxX);
        if(a==null||b==null||a.area<gap*gap*.4f||b.area<gap*gap*.4f
                ||Math.abs(a.centerY-b.centerY)<gap*.3f||Math.abs(a.centerY-b.centerY)>gap*.8f
                ||a.maxY-a.minY<gap*.65f||b.maxY-b.minY<gap*.65f)return List.of();
        return List.of(a,b);
    }

    private static Component horizontalHeadSlice(byte[] labels,int width,Component head,int left,int right) {
        int area=0,minY=head.maxY,maxY=head.minY;long sumX=0,sumY=0;
        for(int y=head.minY;y<=head.maxY;y++)for(int x=left;x<=right;x++)
            if(labels[y*width+x]==OmrMeasurePostProcessor.NOTEHEAD){area++;sumX+=x;sumY+=y;minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        return area==0?null:new Component(area,left,right,minY,maxY,sumX/(float)area,sumY/(float)area);
    }

    private static Component componentSlice(byte[] labels, int width, Component source,
                                             int top, int bottom) {
        int area = 0, minX = source.maxX + 1, maxX = source.minX - 1;
        int minY = bottom + 1, maxY = top - 1;
        long sumX = 0, sumY = 0;
        for (int y = top; y <= bottom; y++) for (int x = source.minX; x <= source.maxX; x++) {
            if (labels[y * width + x] != OmrMeasurePostProcessor.NOTEHEAD) continue;
            area++; sumX += x; sumY += y;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        return area < 3 ? null : new Component(area, minX, maxX, minY, maxY,
                sumX / (float) area, sumY / (float) area);
    }

    private static List<Staff> findStaffs(byte[] labels, byte[] gray, int width, int height,
                                          List<MeasureRegion> measures) {
        int[] projection = new int[height];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++)
                if (labels[row + x] == OmrMeasurePostProcessor.STAFF) projection[y]++;
        }
        int threshold = Math.max(8, Math.round(width * 0.055f));
        List<Staff> staffs = new ArrayList<>();
        for (RawStaffLineDetector.StaffLines semantic :
                RawStaffLineDetector.detectFromStrength(projection, threshold, height))
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
        for (RawStaffLineDetector.StaffLines weak : RawStaffLineDetector.detectFromStrength(
                projection, Math.max(10,width/80),height)) {
            Staff recovered = new Staff(weak.top(),weak.bottom(),weak.gap());
            if (alignedWithMeasureRow(recovered,measures,height)
                    && compatibleStaffScale(recovered,staffs)
                    && isolatedMissingSystem(recovered,staffs)
                    && !representedStaff(recovered,staffs,height)) staffs.add(recovered);
        }
        // The measure reader already deskews semantic rules. Use that same
        // evidence for missing pitch staffs so an accepted row cannot go silent.
        float semanticSlope=OmrMeasurePostProcessor.estimateStaffSlope(labels,width,height);
        if(Math.abs(semanticSlope)>.001f) {
            int[] deskewed=new int[height];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(labels[y*width+x]==OmrMeasurePostProcessor.STAFF) {
                int row=Math.round(y-semanticSlope*(x-width*.5f));
                if(row>=0&&row<height)deskewed[row]++;
            }
            for(var lines:RawStaffLineDetector.detectFromStrength(deskewed,Math.max(10,width/80),height)) {
                Staff recovered=new Staff(lines.top(),lines.bottom(),lines.gap());
                recovered.pitchSlope=semanticSlope;
                // A tilted rule can produce two peaks in the uncorrected row
                // projection, inventing a half-spacing staff. A complete,
                // strong deskewed five-rule group with no intervening rules
                // resolves that alias before the page-scale gate can reject it.
                boolean printed=completePrintedDeskewedStaff(gray,width,height,lines,semanticSlope);
                boolean replaced=false;
                if(alignedWithMeasureRow(recovered,measures,height)
                        &&(printed||unambiguousDeskewedRules(deskewed,lines,width))) {
                    for(int i=staffs.size()-1;i>=0;i--) {
                        Staff prior=staffs.get(i);
                        float center=(prior.top+prior.bottom)*.5f;
                        if(recovered.gap>=prior.gap*(printed?1.18f:1.8f)&&recovered.gap<=prior.gap*(printed?2.4f:2.2f)
                                &&Math.abs(center-(recovered.top+recovered.bottom)*.5f)<=recovered.gap*(printed?1.75f:1f)
                                &&prior.top>=recovered.top-recovered.gap*(printed?.8f:.25f)
                                &&prior.bottom<=recovered.bottom+recovered.gap*(printed?.8f:.25f)) {
                            staffs.remove(i);replaced=true;
                        }
                    }
                }
                if(replaced||alignedWithMeasureRow(recovered,measures,height)&&(printed||compatibleStaffScale(recovered,staffs))
                        &&(printed&&staffs.isEmpty()||isolatedMissingSystem(recovered,staffs))
                        &&!representedStaff(recovered,staffs,height))staffs.add(recovered);
            }
        }
        for (RawStaffLineDetector.StaffLines raw : RawStaffLineDetector.detect(gray, width, height)) {
            Staff recovered = new Staff(raw.top(), raw.bottom(), raw.gap());
            float[] pitch=printedStaffPitch(gray,width,height,raw);
            // Semantic paint can shorten the outer-line spacing by several pixels. That
            // error accumulates on ledger notes and moves them to a different printed pitch.
            // Keep semantic geometry for symbol ownership, but calibrate pitch to a matching
            // complete five-line staff measured directly from the page.
            // A missing semantic outer rule can admit a ledger line and shift the
            // entire five-line group by one gap. A complete printed staff also
            // calibrates that case; the nearby group must still overlap four rules.
            for (Staff staff : staffs) if (pitch!=null && Math.abs(staff.top-raw.top()) <= staff.gap*1.2f
                    && Math.abs(staff.bottom-raw.bottom()) <= staff.gap*1.2f
                    && raw.gap() >= staff.gap*.85f && raw.gap() <= staff.gap*1.18f) {
                staff.pitchGap=pitch[1];staff.pitchBottom=pitch[0];
            }
            if(pitch!=null){recovered.pitchGap=pitch[1];recovered.pitchBottom=pitch[0];}
            if (!representedStaff(recovered, staffs, height)) staffs.add(recovered);
        }
        staffs.sort(Comparator.comparingDouble(staff -> staff.top));
        // Recover staff geometry from five continuous printed rules on a tilted page.
        // Match nearby groups and require page-scale support for compressed aliases.
        if(gray!=null&&Math.abs(semanticSlope)>.001f) {
            int[] printedRows=new int[height];
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((gray[y*width+x]&255)<170) {
                int row=Math.round(y-semanticSlope*(x-width*.5f));
                if(row>=0&&row<height)printedRows[row]++;
            }
            int[] interruptedRows=printedRows.clone();
            for(int row=0;row<height;row++)if(printedRows[row]>=width*.25f) {
                int run=0,longest=0;
                for(int x=0;x<width;x++) {
                    int y=Math.round(row+semanticSlope*(x-width*.5f));boolean ink=false;
                    for(int yy=Math.max(0,y-1);yy<=Math.min(height-1,y+1);yy++)
                        if((gray[yy*width+x]&255)<180){ink=true;break;}
                    if(ink)longest=Math.max(longest,++run);else run=0;
                }
                if(longest<width*.25f)printedRows[row]=0;
            }
            for(var raw:RawStaffLineDetector.detectFromStrength(printedRows,Math.max(24,Math.round(width*.25f)),height)) {
                if(!completePrintedDeskewedStaff(gray,width,height,raw,semanticSlope))continue;
                for(int i=0;i<staffs.size();i++) {
                    Staff staff=staffs.get(i);
                    Staff printed=new Staff(raw.top(),raw.bottom(),raw.gap());
                    // Multiple peaks within each tilted rule can invent a tiny staff.
                    // Five continuous printed rules, clear spaces, measure alignment and
                    // the other staffs' scale resolve it without a fixed alias ratio.
                    if(raw.gap()>staff.gap*1.18f
                            &&staff.top>=raw.top()-raw.gap()*.8f
                            &&staff.bottom<=raw.bottom()+raw.gap()*.8f
                            &&Math.abs((staff.top+staff.bottom-raw.top()-raw.bottom())*.5f)<=raw.gap()*1.75f
                            &&alignedWithMeasureRow(printed,measures,height)) {
                        List<Staff> others=new ArrayList<>(staffs);others.remove(i);
                        if(compatibleStaffScale(printed,others)) {
                            printed.pitchSlope=semanticSlope;printed.printedPhase=true;
                            staffs.set(i,printed);continue;
                        }
                    }
                    if(raw.gap()<staff.gap*.85f||raw.gap()>staff.gap*1.18f
                            ||Math.abs(staff.top-raw.top())>raw.gap()*1.2f
                            ||Math.abs(staff.bottom-raw.bottom())>raw.gap()*1.2f)continue;
                    // A close bottom rule does not guarantee the correct scale on ledger notes.
                    float error=Math.max(Math.abs(staff.pitchBottom-raw.bottom()),Math.abs(staff.bottom-raw.bottom()));
                    error=Math.max(error,Math.abs(staff.pitchGap-raw.gap())*6);
                    if(error<raw.gap()*.45f) {
                        // A quarter-gap edge displacement reaches half a diatonic step:
                        // even a centered seed can then round an edge note to the next pitch.
                        // Correct that seed without locking the local reader to a new staff phase.
                        if(!staff.printedPhase&&Math.abs(staff.pitchSlope-semanticSlope)*width*.5f>=raw.gap()*.25f) {
                            staff.pitchBottom=raw.bottom();staff.pitchGap=raw.gap();
                            staff.pitchSlope=semanticSlope;staff.printedSlope=true;
                        }
                        continue;
                    }
                    staff.pitchBottom=raw.bottom();staff.pitchGap=raw.gap();staff.pitchSlope=semanticSlope;
                    staff.printedPhase=true;
                }
                Staff missing=new Staff(raw.top(),raw.bottom(),raw.gap());
                if(alignedWithMeasureRow(missing,measures,height)
                        &&compatibleStaffScale(missing,staffs)&&!representedStaff(missing,staffs,height)) {
                    missing.pitchSlope=semanticSlope;missing.printedPhase=true;staffs.add(missing);
                }
            }
            calibrateInterruptedStaffs(gray,width,height,staffs,interruptedRows,semanticSlope);
        }
        recoverFadedStaffAliases(gray,width,height,staffs,measures,semanticSlope);
        calibrateContrastedFadedStaffs(gray,width,height,staffs,semanticSlope);
        staffs.sort(Comparator.comparingDouble(staff -> staff.top));
        for(Staff staff:staffs) {
            if(!staff.printedPhase&&!staff.printedSlope) {
                float[] straight=StaffPitchTrack.straightPitch(labels,gray,width,height,staff.pitchBottom,staff.pitchGap);
                if(straight!=null) {
                    staff.pitchBottom=straight[0];staff.pitchGap=straight[1];staff.pitchSlope=0;staff.printedPhase=true;
                }
            }
            staff.pitchTrack=StaffPitchTrack.detect(gray,width,height,
                staff.printedPhase||staff.printedSlope?staff.pitchBottom-staff.pitchGap*4:staff.top,
                staff.printedPhase||staff.printedSlope?staff.pitchBottom:staff.bottom,staff.pitchGap);
            if(staff.printedPhase&&staff.pitchTrack!=null
                    &&Math.abs(staff.pitchTrack.at(width*.5f)[0]-staff.pitchBottom)>staff.pitchGap*.5f)
                staff.pitchTrack=null;
        }
        assignSystemPositions(staffs, measures, height);
        return staffs;
    }

    /** Short breaks in otherwise broad rules must not leave an admitted staff
     * on the wrong pitch phase. This never admits or removes a staff. */
    private static void calibrateInterruptedStaffs(byte[] gray,int width,int height,
            List<Staff> staffs,int[] rows,float slope) {
        if(staffs.size()<4)return;
        for(var raw:RawStaffLineDetector.detectFromStrength(rows,Math.max(24,Math.round(width*.25f)),height)) {
            if(!completeInterruptedStaff(gray,width,height,raw,slope))continue;
            for(Staff staff:staffs) {
                if(staff.printedPhase||staff.printedSlope
                        ||raw.gap()<staff.pitchGap*.85f||raw.gap()>staff.pitchGap*1.18f
                        ||Math.abs(staff.top-raw.top())>raw.gap()*1.2f
                        ||Math.abs(staff.bottom-raw.bottom())>raw.gap()*1.2f)continue;
                int corroboration=0;for(Staff other:staffs)if(other!=staff
                        &&Math.abs(other.pitchGap-raw.gap())<=raw.gap()*.08f)corroboration++;
                if(corroboration<3)continue;
                float error=Math.max(Math.abs(staff.pitchBottom-raw.bottom()),Math.abs(staff.pitchGap-raw.gap())*6);
                error=Math.max(error,Math.abs(staff.pitchSlope-slope)*width*.5f);
                if(error<raw.gap()*.45f)continue;
                staff.pitchBottom=raw.bottom();staff.pitchGap=raw.gap();staff.pitchSlope=slope;staff.printedPhase=true;
            }
        }
    }

    private static boolean completeInterruptedStaff(byte[] gray,int width,int height,
            RawStaffLineDetector.StaffLines staff,float slope) {
        int step=Math.max(1,width/512),radius=Math.max(1,Math.round(staff.gap()*.15f));
        int flank=Math.max(2,Math.round(staff.gap()*.32f));
        int partialRules=0;
        for(int i=0;i<9;i++) {
            float row=i<5?staff.rows()[i]:(staff.rows()[i-5]+staff.rows()[i-4])*.5f;
            int supported=0,samples=0;
            for(int x=0;x<width;x+=step) {
                int y=Math.round(row+slope*(x-width*.5f));samples++;
                for(int yy=Math.max(flank,y-radius);yy<=Math.min(height-1-flank,y+radius);yy++) {
                    int ink=gray[yy*width+x]&255;
                    if(i<5?ink<=205&&(gray[(yy-flank)*width+x]&255)>=ink+12
                            &&(gray[(yy+flank)*width+x]&255)>=ink+12:ink<=180) {supported++;break;}
                }
            }
            // One rule can cross denser notation, but the other four must
            // retain the stronger page-wide thin-ink support.
            if(i<5) {
                if(supported<samples*.70f||supported<samples*.75f&&++partialRules>1)return false;
            } else if(supported>=samples*.4f)return false;
        }
        return true;
    }

    /** Pale rules can leave a compressed semantic group. Require three other
     * systems to confirm its true scale and broad printed support for every rule. */
    private static void recoverFadedStaffAliases(byte[] gray,int width,int height,
            List<Staff> staffs,List<MeasureRegion> measures,float slope) {
        if(gray==null||staffs.size()<4)return;
        List<Float> gaps=new ArrayList<>();for(Staff s:staffs)gaps.add(s.pitchGap);
        gaps.sort(Float::compare);float typical=gaps.get(gaps.size()/2);
        if(staffs.stream().noneMatch(s->s.pitchGap<typical*.8f))return;
        int[] rows=new int[height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((gray[y*width+x]&255)<205) {
            int row=Math.round(y-slope*(x-width*.5f));if(row>=0&&row<height)rows[row]++;
        }
        for(var raw:RawStaffLineDetector.detectFromStrength(rows,Math.max(24,Math.round(width*.25f)),height)) {
            if(Math.abs(raw.gap()-typical)>typical*.08f)continue;
            int corroboration=0;for(Staff s:staffs)if(Math.abs(s.pitchGap-raw.gap())<=raw.gap()*.08f)corroboration++;
            if(corroboration<3||!completeFadedStaff(gray,width,height,raw,slope))continue;
            for(int i=0;i<staffs.size();i++) {
                Staff prior=staffs.get(i);
                if(prior.pitchGap>=raw.gap()*.8f||prior.pitchGap<raw.gap()*.45f
                        ||prior.top<raw.top()-raw.gap()*.8f||prior.bottom>raw.bottom()+raw.gap()*.8f
                        ||Math.abs((prior.top+prior.bottom-raw.top()-raw.bottom())*.5f)>raw.gap()*1.75f)continue;
                Staff recovered=new Staff(raw.top(),raw.bottom(),raw.gap());
                if(!alignedWithMeasureRow(recovered,measures,height))continue;
                recovered.pitchSlope=slope;recovered.printedPhase=true;staffs.set(i,recovered);
            }
        }
    }

    /** Thin pale rules can establish scale despite a moderately compressed mask.
     * Three independent systems must corroborate the complete printed group. */
    private static void calibrateContrastedFadedStaffs(byte[] gray,int width,int height,
            List<Staff> staffs,float slope) {
        if(gray==null||staffs.size()<4)return;
        List<Float> gaps=new ArrayList<>();for(Staff staff:staffs)gaps.add(staff.pitchGap);
        gaps.sort(Float::compare);float typical=gaps.get(gaps.size()/2);
        int flank=Math.max(2,Math.round(typical*.22f));
        int[] rows=new int[height];
        for(int y=flank;y<height-flank;y++)for(int x=0;x<width;x++) {
            int ink=gray[y*width+x]&255;
            if(ink>225||(gray[(y-flank)*width+x]&255)<ink+12
                    ||(gray[(y+flank)*width+x]&255)<ink+12)continue;
            int row=Math.round(y-slope*(x-width*.5f));if(row>=0&&row<height)rows[row]++;
        }
        for(var raw:RawStaffLineDetector.detectFromStrength(rows,Math.max(24,Math.round(width*.25f)),height)) {
            if(Math.abs(raw.gap()-typical)>typical*.08f)continue;
            int corroboration=0;for(float originalGap:gaps)
                if(Math.abs(originalGap-raw.gap())<=raw.gap()*.08f)corroboration++;
            if(corroboration<3||!completeContrastedFadedStaff(gray,width,height,raw,slope))continue;
            for(Staff staff:staffs) {
                int independent=corroboration-(Math.abs(staff.pitchGap-raw.gap())<=raw.gap()*.08f?1:0);
                if(independent<3)continue;
                if(staff.printedPhase||staff.printedSlope||staff.pitchTrack!=null
                        ||staff.pitchGap<raw.gap()*.8f||staff.pitchGap>raw.gap()*1.2f
                        ||Math.abs(staff.pitchBottom-raw.bottom())>raw.gap()*.45f
                        ||Math.abs(staff.pitchGap-raw.gap())<raw.gap()*.035f)continue;
                staff.pitchBottom=raw.bottom();staff.pitchGap=raw.gap();
                staff.pitchSlope=slope;staff.printedPhase=true;
            }
        }
    }

    private static boolean completeContrastedFadedStaff(byte[] gray,int width,int height,
            RawStaffLineDetector.StaffLines staff,float slope) {
        int radius=Math.max(1,Math.round(staff.gap()*.15f)),flank=Math.max(2,Math.round(staff.gap()*.22f));
        for(int i=-2;i<=10;i++) {
            float row=staff.top()+i*staff.gap()*.5f;int supported=0,samples=0;
            for(int x=Math.round(width*.1f);x<width*.94f;x+=Math.max(1,width/512)) {
                int center=Math.round(row+slope*(x-width*.5f));samples++;
                for(int y=Math.max(flank,center-radius);y<=Math.min(height-1-flank,center+radius);y++) {
                    int ink=gray[y*width+x]&255;
                    if(ink<=225&&(gray[(y-flank)*width+x]&255)>=ink+12
                            &&(gray[(y+flank)*width+x]&255)>=ink+12){supported++;break;}
                }
            }
            boolean rule=i>=0&&i<=8&&i%2==0;
            if(samples<24||(rule?supported<samples*.75f:supported>=samples*.4f))return false;
        }
        return true;
    }

    private static boolean completeFadedStaff(byte[] gray,int width,int height,
            RawStaffLineDetector.StaffLines staff,float slope) {
        int step=Math.max(1,width/512),radius=Math.max(1,Math.round(staff.gap()*.15f));
        for(int i=0;i<9;i++) {
            float row=i<5?staff.rows()[i]:(staff.rows()[i-5]+staff.rows()[i-4])*.5f;
            int dark=0,samples=0;
            for(int x=0;x<width;x+=step) {
                int y=Math.round(row+slope*(x-width*.5f));samples++;
                for(int yy=Math.max(0,y-radius);yy<=Math.min(height-1,y+radius);yy++)
                    if((gray[yy*width+x]&255)<205){dark++;break;}
            }
            if(i<5?dark<samples*.75f:dark>=samples*.4f)return false;
        }
        return true;
    }

    /** Verify all five sloped rules in the printed page, with clear spaces between them. */
    private static boolean completePrintedDeskewedStaff(byte[] gray,int width,int height,
            RawStaffLineDetector.StaffLines staff,float slope) {
        if(gray==null||gray.length!=width*height)return false;
        int step=Math.max(1,width/512),radius=Math.max(1,Math.round(staff.gap()*.15f));
        for(int i=0;i<9;i++) {
            float row=i<5?staff.rows()[i]:(staff.rows()[i-5]+staff.rows()[i-4])*.5f;
            int dark=0,samples=0;
            for(int x=0;x<width;x+=step) {
                int y=Math.round(row+slope*(x-width*.5f));samples++;
                for(int yy=Math.max(0,y-radius);yy<=Math.min(height-1,y+radius);yy++)
                    if((gray[yy*width+x]&255)<180){dark++;break;}
            }
            if(i<5?dark<samples*.55f:dark>=samples*.5f)
                return completePrintedDeskewedSpan(gray,width,height,staff,slope);
        }
        return true;
    }

    /** Short systems must prove five printed rules across their own extent. */
    private static boolean completePrintedDeskewedSpan(byte[] gray,int width,int height,
            RawStaffLineDetector.StaffLines staff,float slope) {
        int step=Math.max(1,width/512),radius=Math.max(1,Math.round(staff.gap()*.15f));
        int first=-1,last=-1;
        for(int x=0;x<width;x+=step) {
            int lines=0,spaces=0;
            for(int i=0;i<9;i++) {
                float row=i<5?staff.rows()[i]:(staff.rows()[i-5]+staff.rows()[i-4])*.5f;
                int y=Math.round(row+slope*(x-width*.5f));boolean dark=false;
                for(int yy=Math.max(0,y-radius);yy<=Math.min(height-1,y+radius);yy++)
                    if((gray[yy*width+x]&255)<180){dark=true;break;}
                if(dark){if(i<5)lines++;else spaces++;}
            }
            if(lines>=4&&spaces<=1){if(first<0)first=x;last=x;}
        }
        if(first<0||last-first<Math.max(staff.gap()*12,width*.18f))return false;
        for(int i=0;i<9;i++) {
            float row=i<5?staff.rows()[i]:(staff.rows()[i-5]+staff.rows()[i-4])*.5f;
            int dark=0,samples=0;
            for(int x=first;x<=last;x+=step) {
                int y=Math.round(row+slope*(x-width*.5f));samples++;
                for(int yy=Math.max(0,y-radius);yy<=Math.min(height-1,y+radius);yy++)
                    if((gray[yy*width+x]&255)<180){dark++;break;}
            }
            if(i<5?dark<samples*.75f:dark>=samples*.35f)return false;
        }
        return true;
    }

    private static boolean unambiguousDeskewedRules(int[] strength,
            RawStaffLineDetector.StaffLines staff,int width) {
        int radius=Math.max(1,Math.round(staff.gap()*.15f));
        for(int row:staff.rows()) {
            int peak=0;
            for(int y=Math.max(0,row-radius);y<=Math.min(strength.length-1,row+radius);y++)peak=Math.max(peak,strength[y]);
            if(peak<width*.25f)return false;
        }
        for(int i=0;i<4;i++) {
            int middle=Math.round((staff.rows()[i]+staff.rows()[i+1])*.5f);
            for(int y=Math.max(0,middle-radius);y<=Math.min(strength.length-1,middle+radius);y++)
                if(strength[y]>=width*.20f)return false;
        }
        return true;
    }

    /** Center complete printed rule bands instead of using whichever edge pixel wins a peak. */
    private static float[] printedStaffPitch(byte[] gray,int width,int height,
                                             RawStaffLineDetector.StaffLines raw) {
        float[] centers=new float[5];int radius=Math.max(2,Math.round(raw.gap()*.28f));
        for(int line=0;line<5;line++) {
            int first=Math.max(0,raw.rows()[line]-radius),last=Math.min(height-1,raw.rows()[line]+radius);
            int[] strength=new int[last-first+1];int strongest=0,peak=0;
            for(int y=first;y<=last;y++) {
                int n=0;for(int x=0;x<width;x++)if((gray[y*width+x]&255)<=170)n++;
                strength[y-first]=n;if(n>strongest){strongest=n;peak=y-first;}
            }
            if(strongest<width*.25f)return null;
            int start=peak,end=peak;
            while(start>0&&strength[start-1]>=strongest*.85f)start--;
            while(end+1<strength.length&&strength[end+1]>=strongest*.85f)end++;
            if(end-start+1>Math.max(3,raw.gap()*.4f))return null;
            centers[line]=first+(start+end)*.5f;
            // A dense row of beams can outscore an outer staff rule in the
            // page projection. A printed rule stays thin in individual columns,
            // even on a skewed scan; beams do not. Do not let such a candidate
            // move the pitch reference by an entire staff gap.
            int thinColumns=0,maxThickness=Math.max(3,Math.round(raw.gap()*.4f));
            for(int x=0;x<width;x++) {
                for(int y=first;y<=last;y++) {
                    if((gray[y*width+x]&255)>170)continue;
                    int a=y,b=y;
                    while(a>0&&y-a<=maxThickness&&(gray[(a-1)*width+x]&255)<=170)a--;
                    while(b+1<height&&b-a<maxThickness&&(gray[(b+1)*width+x]&255)<=170)b++;
                    if(b-a+1<=maxThickness){thinColumns++;break;}
                    y=b;
                }
            }
            if(thinColumns<width*.25f)return null;
        }
        float gap=(centers[4]-centers[0])/4;
        for(int line=1;line<5;line++)if(Math.abs(centers[line]-centers[line-1]-gap)>
                Math.max(.75f,gap*.12f))return null;
        return new float[]{centers[4],gap};
    }

    /** Original contiguous-band reader retained as a conservative fallback on known score rows. */
    private static List<Staff> legacySemanticStaffs(int[] projection, int threshold, int height) {
        List<Float> lines = new ArrayList<>();
        for (int row = 0; row < height;) {
            if (projection[row] < threshold) { row++; continue; }
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
        for (int start = 0; start + 4 < lines.size();) {
            float[] gaps = new float[4];
            for (int index = 0; index < gaps.length; index++)
                gaps[index] = lines.get(start + index + 1) - lines.get(start + index);
            float gap = median(gaps);
            boolean regular = gap >= 2f && gap <= height * .045f;
            for (float candidate : gaps)
                regular &= Math.abs(candidate - gap) <= Math.max(1.5f, gap * .34f);
            if (!regular) { start++; continue; }
            result.add(new Staff(lines.get(start), lines.get(start + 4), gap));
            start += 5;
        }
        return result;
    }

    private static boolean alignedWithMeasureRow(Staff staff, List<MeasureRegion> measures,
                                                  int pageHeight) {
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
            if (Math.abs(other - center) <= Math.max(candidate.gap, staff.gap)
                    * MAX_STAFFS_IN_SYSTEM_SEPARATION_GAPS)
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
    private static void assignSystemPositions(List<Staff> staffs, List<MeasureRegion> measures,
                                              int pageHeight) {
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

    private static boolean shareMeasureSystem(Staff first, Staff second,
                                              List<MeasureRegion> measures, int pageHeight) {
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

    private static List<Component> findComponents(byte[] labels, int width, int height, byte target) {
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
                area++; sumX += x; sumY += y;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
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
            if (area >= 3) result.add(new Component(area, minX, maxX, minY, maxY,
                    sumX / (float) area, sumY / (float) area));
        }
        return result;
    }

    private static Staff nearestStaff(List<Staff> staffs, float y) {
        return nearestStaff(staffs, y, 5f);
    }

    private static Staff nearestHeadStaff(List<Staff> staffs, float y) {
        return nearestStaff(staffs, y, MAX_HEAD_LEDGER_GAPS);
    }

    /**
     * Ledger notes between two adjacent staves can be a fraction of a pixel closer to the
     * wrong stave. Their attached stem normally points back toward the owning stave, so use that
     * topology only for genuinely ambiguous adjacent staves and retain nearest-staff everywhere
     * else. This prevents a high piano note from joining and lengthening a simultaneous violin
     * note in mixed violin/piano scores.
     */
    private static Staff staffForHead(byte[] labels, byte[] gray, int width, int height,
                                      List<Staff> staffs, Component head) {
        Staff ledgerOwner = printedLedgerOwner(gray,width,height,staffs,head);
        if (ledgerOwner != null) return ledgerOwner;
        Staff nearest = nearestHeadStaff(staffs, head.centerY);
        if (nearest == null) return null;
        Staff upper = null, lower = null;
        for (Staff staff : staffs) {
            if (staff.bottom < head.centerY
                    && (upper == null || staff.bottom > upper.bottom)) upper = staff;
            if (staff.top > head.centerY
                    && (lower == null || staff.top < lower.top)) lower = staff;
        }
        if (upper == null || lower == null) return nearest;
        // A head within a third staff is not between these two candidates.
        if (nearest != upper && nearest != lower) return nearest;
        boolean adjacentParts=upper.count==lower.count&&upper.index+1==lower.index;
        boolean adjacentSoloRows=upper.count==1&&lower.count==1;
        if (!adjacentParts&&!adjacentSoloRows) return nearest;
        float upperDistance = head.centerY - upper.bottom;
        float lowerDistance = lower.top - head.centerY;
        float smaller = Math.max(.001f, Math.min(upperDistance, lowerDistance));
        if (Math.max(upperDistance, lowerDistance) > smaller * 1.55f) return nearest;

        // Beamed voices may keep an upward stem even above the bass staff.
        // The ledger chain, when visible, identifies the printed pitch staff
        // more reliably than that stem direction.
        int upperLedgers=innerLedgerCount(gray,width,height,head,upper);
        int lowerLedgers=innerLedgerCount(gray,width,height,head,lower);
        if(upperLedgers>0&&lowerLedgers==0)return upper;
        if(lowerLedgers>0&&upperLedgers==0)return lower;
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
    private static Staff printedLedgerOwner(byte[] gray,int width,int height,
                                            List<Staff> staffs,Component head) {
        if(gray==null)return null;
        Staff upper=null,lower=null;
        for(Staff staff:staffs) {
            if(head.centerY>=staff.top&&head.centerY<=staff.bottom)return null;
            if(staff.bottom<head.centerY&&(upper==null||staff.bottom>upper.bottom))upper=staff;
            if(staff.top>head.centerY&&(lower==null||staff.top<lower.top))lower=staff;
        }
        if(upper==null||lower==null)return null;
        // A nearby staff outweighs ledgers belonging to another tone of the same chord.
        if(head.centerY-upper.bottom<=upper.gap*1.5f||lower.top-head.centerY<=lower.gap*1.5f)return null;
        int above=innerLedgerCount(gray,width,height,head,upper);
        int below=innerLedgerCount(gray,width,height,head,lower);
        // Two inner rules can establish ownership; the rule beside/through the head is excluded.
        if(above>=2&&above>=below+2&&head.centerY-upper.bottom<=upper.gap*MAX_HEAD_LEDGER_GAPS)return upper;
        if(below>=2&&below>=above+2&&lower.top-head.centerY<=lower.gap*MAX_HEAD_LEDGER_GAPS)return lower;
        return null;
    }

    private static int innerLedgerCount(byte[] gray,int width,int height,Component head,Staff staff) {
        if(gray==null)return 0;
        float gap=staff.pitchGap;
        boolean above=head.centerY<staff.pitchBottom-gap*4;
        float outer=above?staff.pitchBottom-gap*4:staff.pitchBottom;
        float direction=above?-1:1;
        int left=Math.max(0,Math.round(head.minX-gap*.3f));
        int right=Math.min(width-1,Math.round(head.maxX+gap*.3f));
        int count=0;
        for(float line=outer+direction*gap;direction*(head.centerY-line)>gap*.65f;line+=direction*gap) {
            boolean found=false;
            for(int y=Math.max(0,Math.round(line-gap*.18f));y<=Math.min(height-1,Math.round(line+gap*.18f));y++) {
                int ink=0,leftInk=0,rightInk=0;
                for(int x=left;x<=right;x++)if((gray[y*width+x]&255)<165) {
                    ink++;if(x<head.minX)leftInk++;if(x>head.maxX)rightInk++;
                }
                int margin=Math.max(1,Math.round(gap*.18f));
                if(ink>=(right-left+1)*.85f&&leftInk>=margin&&rightInk>=margin
                        &&shortLedgerRule(gray,width,y,head,gap))found=true;
            }
            if(found)count++;
        }
        return count;
    }

    /** Ending brackets and long beams do not identify a ledger pitch. */
    private static boolean shortLedgerRule(byte[] gray,int width,int y,Component head,float gap) {
        int center=Math.max(0,Math.min(width-1,Math.round(head.centerX)));
        if((gray[y*width+center]&255)>=165)return false;
        int left=center,right=center,limit=Math.max(4,Math.round(gap*4.5f));
        while(left>0&&center-left<=limit&&(gray[y*width+left-1]&255)<165)left--;
        while(right+1<width&&right-center<=limit&&(gray[y*width+right+1]&255)<165)right++;
        return right-left+1<=limit;
    }

    private static int attachedStemReach(byte[] labels, int width, int height,
                                         Component head, float gap, boolean upward) {
        int left = Math.max(0, Math.round(head.minX - gap * .42f));
        int right = Math.min(width - 1, Math.round(head.maxX + gap * .42f));
        int first = upward ? head.minY - 1 : head.maxY + 1;
        int limit = upward
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
            if (stem) { reach++; blanks = 0; }
            else if (++blanks > 1) break;
        }
        return reach;
    }

    private static Staff nearestStaff(List<Staff> staffs, float y, float maximumGapDistance) {
        Staff best = null;
        float distance = Float.MAX_VALUE;
        for (Staff staff : staffs) {
            float candidate = y < staff.top ? staff.top - y : y > staff.bottom ? y - staff.bottom : 0;
            if (candidate < distance) { distance = candidate; best = staff; }
        }
        return best != null && distance <= best.gap * maximumGapDistance ? best : null;
    }

    /** A displaced second shares a stem between two ovals. Its combined bounds
     * are not one stemless head: require ledger evidence for both actual tones. */
    private static boolean hasHeadLedgerSupport(byte[] labels,byte[] gray,int width,int height,
            Component head,Staff staff,boolean roundedGrace) {
        List<Component> parts=sideBySideSeconds(labels,width,head,staff.gap);
        if(parts.isEmpty())parts=List.of(head);
        for(Component part:parts)
            if(!hasLedgerInk(gray,width,height,part,staff.gap,roundedGrace)
                    ||!hasInnerLedgerInk(gray,width,height,part,staff,roundedGrace))return false;
        return true;
    }

    private static int[] ledgerInkLimits(byte[] gray,int width,int height,Component head,float gap) {
        int[] normal={160,185};
        if(gray==null)return normal;
        int[] ink=new int[256],paper=new int[256];int ni=0,np=0;
        for(int y=Math.max(0,head.minY);y<=Math.min(height-1,head.maxY);y++)
            for(int x=Math.max(0,head.minX);x<=Math.min(width-1,head.maxX);x++) {
                ink[gray[y*width+x]&255]++;ni++;
            }
        int radius=Math.max(8,Math.round(gap*2));
        for(int y=Math.max(0,head.minY-radius);y<=Math.min(height-1,head.maxY+radius);y+=2)
            for(int x=Math.max(0,head.minX-radius);x<=Math.min(width-1,head.maxX+radius);x+=2) {
                paper[gray[y*width+x]&255]++;np++;
            }
        if(ni<6||np<12)return normal;
        int dark=0,light=0,sum=0;
        for(int i=0;i<256;i++){sum+=ink[i];if(sum>=Math.max(1,ni/4)){dark=i;break;}}
        sum=0;for(int i=0;i<256;i++){sum+=paper[i];if(sum>=np*.9f){light=i;break;}}
        if(dark<70||dark>205||light-dark<45)return normal;
        // A pale antialiased curve on an otherwise crisp scan is not faded ink.
        // Bound the head estimate by the surrounding printed strokes.
        sum=0;for(int i=0;i<256;i++){sum+=paper[i];if(sum>=np*.05f){dark=Math.min(dark,i);break;}}
        if(dark<70||dark>205||light-dark<45)return normal;
        return new int[]{Math.min(225,Math.max(160,Math.round(dark+(light-dark)*160f/255))),
                Math.min(235,Math.max(185,Math.round(dark+(light-dark)*185f/255)))};
    }

    private static boolean hasLedgerInk(byte[] gray,int width,int height,Component head,float gap,boolean roundedGrace) {
        int[] limits=ledgerInkLimits(gray,width,height,head,gap);
        // Use the same local contrast for a ledger note's stem and horizontal rules.
        int stemInk=Math.min(limits[1],limits[0]+10);
        boolean stemless=attachedRawStem(gray,width,height,head,gap,
                Math.max(1,Math.round(gap*.16f)),stemInk)==null;
        boolean reduced=roundedGrace||reducedLedgerHead(gray,width,height,head,gap);
        float minimum=ledgerRunMinimum(head,gap,reduced);
        int left=Math.max(0,Math.round(head.centerX-gap*1.2f));
        int right=Math.min(width-1,Math.round(head.centerX+gap*1.2f));
        for(int y=Math.max(0,Math.round(head.centerY-gap*.65f));
                y<=Math.min(height-1,Math.round(head.centerY+gap*.65f));y++) {
            int run=0,strongInRun=0;
            for(int x=left;x<=right;x++) {
                boolean strong=(gray[y*width+x]&255)<limits[0];
                if(!strong&&y>0&&y+1<height)strong=(gray[(y-1)*width+x]&255)<limits[0]
                        ||(gray[(y+1)*width+x]&255)<limits[0];
                boolean dark=strong||(gray[y*width+x]&255)<limits[1];
                if(!dark&&y>0&&y+1<height)dark=(gray[(y-1)*width+x]&255)<limits[1]
                        ||(gray[(y+1)*width+x]&255)<limits[1];
                run=dark?run+1:0;
                strongInRun=dark?strongInRun+(strong?1:0):0;
                // A horizontal instruction arrow also ends in a head-like blob.
                // A stemless ledger note has rule ink on both sides of its oval.
                if(run>=minimum&&strongInRun>=minimum*.6f
                        &&(!stemless&&!reduced||(x-run+1<head.minX&&x>head.maxX)))return true;
            }
        }
        return false;
    }

    /** Rounded grace heads must survive ledger validation before ornament grouping.
     * Require a short, beamed prefix and a substantially larger following principal. */
    private static List<Component> roundedLedgerGraceHeads(byte[] labels,byte[] gray,int width,int height,
            List<Component> heads,List<Staff> staffs) {
        List<Component> result=new ArrayList<>();
        if(gray==null)return result;
        List<Component> ordered=new ArrayList<>(heads);ordered.sort(Comparator.comparingDouble(h->h.centerX));
        for(int i=0;i<ordered.size();i++) {
            Component first=ordered.get(i);Staff staff=nearestHeadStaff(staffs,first.centerY);
            if(staff==null||!roundedRawGraceHead(labels,gray,width,height,first,staff))continue;
            List<Component> prefix=new ArrayList<>();prefix.add(first);Component previous=first;
            for(int j=i+1;j<ordered.size();j++) {
                Component next=ordered.get(j);
                if(nearestHeadStaff(staffs,next.centerY)!=staff)continue;
                float dx=next.centerX-previous.centerX;
                if(dx<staff.gap*.65f||dx>staff.gap*2.8f||Math.abs(next.centerY-previous.centerY)>staff.gap*2.5f)break;
                if(roundedRawGraceHead(labels,gray,width,height,next,staff)
                        &&graceStemsShareBeam(labels,gray,width,height,previous,next,staff.gap)) {
                    prefix.add(next);previous=next;continue;
                }
                if(prefix.size()>=2&&next.maxX-next.minX+1>staff.gap*1.05f
                        &&prefix.stream().allMatch(head->next.area>head.area*1.65f))result.addAll(prefix);
                break;
            }
        }
        return result;
    }

    private static boolean graceStemsShareBeam(byte[] labels,byte[] gray,int width,int height,
            Component first,Component next,float gap) {
        int[] a=attachedRawStem(gray,width,height,first,gap*.65f),b=attachedRawStem(gray,width,height,next,gap*.65f);
        if(a==null||b==null||a[2]!=b[2]||b[0]-a[0]<gap*.6f)return false;
        for(int offset=0;offset<=Math.round(gap*.8f);offset++) {
            int hits=0,clear=0,samples=0;
            for(int x=a[0]+2;x<=b[0]-2;x++) {
                float t=(x-a[0])/(float)(b[0]-a[0]);
                int y=Math.round(a[1]+t*(b[1]-a[1])-a[2]*offset);
                boolean ink=false,nonStaff=false;
                for(int dy=-1;dy<=1;dy++)if(y+dy>=0&&y+dy<height) {
                    int at=(y+dy)*width+x;
                    if((gray[at]&255)<=165) {
                        ink=true;
                        if(labels[at]!=OmrMeasurePostProcessor.STAFF&&labels[at]!=OmrMeasurePostProcessor.NOTEHEAD)nonStaff=true;
                    }
                }
                samples++;if(ink)hits++;if(nonStaff)clear++;
            }
            if(samples>=6&&hits>=samples*.85f&&clear>=samples*.65f)return true;
        }
        return false;
    }

    private static boolean roundedRawGraceHead(byte[] labels,byte[] gray,int width,int height,
            Component head,Staff staff) {
        if(head.maxX-head.minX+1>Math.round(staff.gap*1.10f)
                ||head.maxY-head.minY+1>Math.round(staff.gap)
                ||head.area>staff.gap*staff.gap*.80f)return false;
        int[] stem=attachedRawStem(gray,width,height,head,staff.gap*.65f);
        if(stem==null||Math.abs(stem[1]-head.centerY)>staff.gap*3.1f)return false;
        int beams=detectBeamCount(labels,gray,width,height,head,staff);
        return beams>0&&detectUnbeamedDuration(labels,gray,width,height,head,staff.gap,beams)<ScoreNoteEvent.DURATION_HALF;
    }

    /** Grace-sized heads use shorter ledger rules but retain the normal staff spacing. */
    private static boolean reducedLedgerHead(byte[] gray,int width,int height,Component head,float gap) {
        return head.maxX-head.minX+1<=Math.round(gap*.95f)&&head.maxY-head.minY+1<=Math.round(gap*.78f)
                &&head.area<=gap*gap*.60f
                &&attachedRawStem(gray,width,height,head,gap*.65f)!=null;
    }

    private static float ledgerRunMinimum(Component head,float gap,boolean reduced) {
        return reduced?Math.max(gap,head.maxX-head.minX+1+2*Math.max(1,Math.round(gap*.1f))):gap*1.5f;
    }

    private static boolean hasInnerLedgerInk(byte[] gray,int width,int height,Component head,Staff staff,boolean roundedGrace) {
        // Beyond two staff spaces, real notation needs another ledger toward
        // the staff. One instruction arrow or underline is insufficient.
        int[] limits=ledgerInkLimits(gray,width,height,head,staff.gap);
        boolean reduced=roundedGrace||reducedLedgerHead(gray,width,height,head,staff.gap);
        float minimum=ledgerRunMinimum(head,staff.gap,reduced);
        float direction=head.centerY<staff.top?1:-1;
        float distance=head.centerY<staff.top?staff.top-head.centerY:head.centerY-staff.bottom;
        // A remote head needs more than a pair of nearby horizontal strokes.
        // Keep tolerance for local staff curvature and partly obscured rules;
        // require a third ledger only beyond three and a half staff spaces.
        int required=distance>staff.gap*3.5f?2:1;
        int left=Math.max(0,Math.round(head.centerX-staff.gap*1.2f));
        int right=Math.min(width-1,Math.round(head.centerX+staff.gap*1.2f));
        float totalStrongSupport=0;
        for(int inner=1;inner<=required;inner++) {
            float center=head.centerY+direction*staff.gap*inner;
            float bestSupport=0;
            for(int y=Math.max(1,Math.round(center-staff.gap*.55f));
                    y<=Math.min(height-2,Math.round(center+staff.gap*.55f));y++) {
                int run=0,strongInRun=0;
                for(int x=left;x<=right;x++) {
                    boolean strong=(gray[y*width+x]&255)<limits[0]||(gray[(y-1)*width+x]&255)<limits[0]
                            ||(gray[(y+1)*width+x]&255)<limits[0];
                    boolean dark=strong||(gray[y*width+x]&255)<limits[1]||(gray[(y-1)*width+x]&255)<limits[1]
                            ||(gray[(y+1)*width+x]&255)<limits[1];
                    run=dark?run+1:0;
                    strongInRun=dark?strongInRun+(strong?1:0):0;
                    // Faded portions may complete a printed rule, but pale underlines alone
                    // cannot supply the additional ledger required for a remote note.
                    if(run>=minimum&&(!reduced||(x-run+1<head.minX&&x>head.maxX)))
                        bestSupport=Math.max(bestSupport,Math.min(1,strongInRun/minimum));
                }
            }
            // A faded middle rule can be supported by the next complete rule,
            // but every required rule must retain a continuous visible span
            // and its own dark core. One strong underline cannot replace it.
            if(bestSupport<(inner<required&&!reduced?.4f:.6f))return false;
            totalStrongSupport+=bestSupport;
        }
        return totalStrongSupport>=required*.6f;
    }

    /** Rejected slur islands must not continue masking a real tie's curve.
     * Keep the caller's segmentation and every retained head unchanged. */
    private static byte[] tieLabelsWithoutSlurHeads(byte[] labels,int width,
            List<Component> rejected,List<Component> retained) {
        if(rejected.isEmpty())return labels;
        byte[] result=labels.clone();
        for(Component head:rejected)for(int y=head.minY;y<=head.maxY;y++)for(int x=head.minX;x<=head.maxX;x++) {
            if(result[y*width+x]!=OmrMeasurePostProcessor.NOTEHEAD)continue;
            boolean protectedHead=false;
            for(Component other:retained)if(x>=other.minX&&x<=other.maxX&&y>=other.minY&&y<=other.maxY) {
                protectedHead=true;break;
            }
            if(!protectedHead)result[y*width+x]=OmrMeasurePostProcessor.SYMBOL;
        }
        return result;
    }

    /** A thin slur fragment joined to a staff line can form a false semantic oval.
     * Require a stem for this unusually flat shape; normal whole notes are taller. */

    /** A short tie clipped by a staff rule can look like a complete small oval.
     * Require two larger stemmed endpoints and a continuous arc through it. */
    private static List<Component> shortTieBowlHeads(byte[] labels,byte[] gray,int width,int height,
            List<Component> heads,List<Staff> staffs) {
        List<Component> result=new ArrayList<>();
        if(gray==null)return result;
        for(Component head:heads) {
            Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)continue;
            float gap=staff.gap;
            if(head.maxX-head.minX+1>gap*1.4f||head.maxY-head.minY+1>gap*.7f
                    ||head.area>gap*gap*.6f||attachedRawStem(gray,width,height,head,gap*.65f)!=null)continue;
            Component left=null,right=null;
            for(Component other:heads) {
                float dx=other.centerX-head.centerX,dy=Math.abs(other.centerY-head.centerY);
                if(other==head||Math.abs(dx)<gap*.9f||Math.abs(dx)>gap*3.3f
                        ||dy<gap*.4f||dy>gap*1.4f||other.area<head.area*1.8f
                        ||other.maxX-other.minX+1<gap*1.1f
                        ||attachedRawStem(gray,width,height,other,gap*.65f)==null)continue;
                if(dx<0&&(left==null||other.centerX>left.centerX))left=other;
                if(dx>0&&(right==null||other.centerX<right.centerX))right=other;
            }
            if(left==null||right==null||Math.abs(left.centerY-right.centerY)>gap*.2f
                    ||head.minX<=left.maxX||head.maxX>=right.minX)continue;
            int a=left.maxX+1,b=right.minX-1;
            if(b-a+1<gap*1.3f||b-a+1>gap*4.5f)continue;
            List<Component> retained=new ArrayList<>(heads);retained.remove(head);
            byte[] arcLabels=tieLabelsWithoutSlurHeads(labels,width,List.of(head),retained);
            if(hasContinuousTieArc(arcLabels,gray,width,height,a,b,
                    (left.centerY+right.centerY)*.5f,gap,head))result.add(head);
        }
        return result;
    }

    /** Small semantic islands at a beam endpoint can borrow the actual note's stem.
     * Reject only a thin beam tip on a stem already attached to a larger head. */
    private static List<Component> beamJunctionHeads(byte[] gray,int width,int height,
            List<Component> heads,List<Staff> staffs) {
        List<Component> rejected=new ArrayList<>();
        if(gray==null)return rejected;
        for(Component head:heads) {
            Staff staff=nearestHeadStaff(staffs,head.centerY);
            if(staff==null)continue;
            float gap=staff.gap;
            if(head.maxX-head.minX+1>gap*.85f||head.maxY-head.minY+1>gap*.60f
                    ||head.area>gap*gap*.36f)continue;
            for(Component main:heads) {
                if(main==head||main.area<head.area*3f||nearestHeadStaff(staffs,main.centerY)!=staff
                        ||Math.abs(main.centerX-head.centerX)>gap
                        ||Math.abs(main.centerY-head.centerY)<gap*2
                        ||Math.abs(main.centerY-head.centerY)>gap*6)continue;
                int[] stem=attachedRawStem(gray,width,height,main,gap);
                if(stem==null||stem[0]<head.minX-gap*.2f||stem[0]>head.maxX+gap*.2f
                        ||Math.abs(stem[1]-head.centerY)>gap*.45f)continue;
                if(narrowBeamTip(gray,width,height,stem[0],head.centerY,gap)) {
                    rejected.add(head);break;
                }
            }
        }
        return rejected;
    }

    /** A small stemless mask fragment can sit inside a single printed beam.
     * Two larger heads must independently anchor both ends of a thin beam. */
    private static List<Component> singleBeamInteriorHeads(byte[] gray,int width,int height,
            List<Component> heads,List<Staff> staffs) {
        List<Component> rejected=new ArrayList<>();if(gray==null)return rejected;
        for(Component head:heads) {
            Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)continue;
            float gap=staff.gap;
            if(head.area>gap*gap*.36f||head.maxX-head.minX+1>gap*.85f
                    ||head.maxY-head.minY+1>gap*.65f
                    ||attachedRawStem(gray,width,height,head,gap)!=null)continue;
            List<int[]> stems=new ArrayList<>();
            for(Component main:heads) {
                if(main==head||main.area<head.area*3||nearestHeadStaff(staffs,main.centerY)!=staff
                        ||Math.abs(main.centerX-head.centerX)>gap*6
                        ||Math.abs(main.centerY-head.centerY)<gap*2
                        ||Math.abs(main.centerY-head.centerY)>gap*6)continue;
                int[] stem=attachedRawStem(gray,width,height,main,gap);
                if(stem!=null&&Math.abs(stem[1]-head.centerY)<gap*1.5f)stems.add(stem);
            }
            boolean found=false;
            for(int[] left:stems)for(int[] right:stems) {
                if(found||left[2]!=right[2]||left[0]>=head.minX-gap*.2f
                        ||right[0]<=head.maxX+gap*.2f||right[0]-left[0]<gap*2
                        ||right[0]-left[0]>gap*6)continue;
                if(thinBeamBetween(gray,width,height,left[0],left[1],right[0],right[1],
                        head.centerX,head.centerY,gap)){rejected.add(head);found=true;}
            }
        }
        return rejected;
    }

    static boolean thinBeamBetween(byte[] gray,int width,int height,int left,int leftY,
            int right,int rightY,float headX,float headY,float gap) {
        if(gray==null||gap<8||right-left<gap*2)return false;
        float slope=(rightY-leftY)/(float)(right-left);if(Math.abs(slope)>.6f)return false;
        int margin=Math.max(3,Math.round(gap*.3f)),core=Math.max(1,Math.round(gap*.08f));
        int search=Math.max(2,Math.round(gap*.45f)),radius=Math.round(gap);
        for(int offset=-search;offset<=search;offset++) {
            if(Math.abs(leftY+offset+slope*(headX-left)-headY)>gap*.25f)continue;
            int valid=0,total=0,near=0,nearValid=0;
            for(int x=left+margin;x<=right-margin;x++) {
                int y=Math.round(leftY+offset+slope*(x-left));
                if(x<0||x>=width||y-radius<0||y+radius>=height)return false;
                total++;boolean ink=true;
                for(int d=-core;d<=core;d++)if((gray[(y+d)*width+x]&255)>=165)ink=false;
                int top=y,bottom=y;
                while(top>y-radius&&(gray[(top-1)*width+x]&255)<165)top--;
                while(bottom<y+radius&&(gray[(bottom+1)*width+x]&255)<165)bottom++;
                int span=bottom-top+1;boolean thin=ink&&span>=gap*.25f&&span<=gap*.8f;
                if(thin)valid++;
                if(Math.abs(x-headX)<=gap*.4f){near++;if(thin)nearValid++;}
            }
            if(total>=gap&&valid>=total*.93f&&near>=3&&nearValid==near)return true;
        }
        return false;
    }

    private static byte[] withoutBeamHeadIslands(byte[] labels,int width,List<Component> rejected) {
        if(rejected.isEmpty())return labels;
        byte[] result=labels.clone();
        for(Component head:rejected)for(int y=head.minY;y<=head.maxY;y++)for(int x=head.minX;x<=head.maxX;x++)
            if(result[y*width+x]==OmrMeasurePostProcessor.NOTEHEAD)result[y*width+x]=0;
        return result;
    }

    /** Blurred parallel beams can merge into one broad strip with small mask islands inside. */
    private static List<Component> mergedBeamInteriorHeads(byte[] gray,int width,int height,
            List<Component> heads,List<Staff> staffs) {
        List<Component> rejected=new ArrayList<>();
        if(gray==null)return rejected;
        for(Component head:heads) {
            Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)continue;
            float gap=staff.gap;
            if(head.maxX-head.minX+1>gap*2||head.maxY-head.minY+1>gap*.85f
                    ||head.area>gap*gap)continue;
            for(Component main:heads) {
                if(main==head||main.area<Math.max(head.area*1.7f,gap*gap*1.2f)
                        ||nearestHeadStaff(staffs,main.centerY)!=staff
                        ||Math.abs(main.centerX-head.centerX)>gap*1.5f
                        ||Math.abs(main.centerY-head.centerY)<gap*2
                        ||Math.abs(main.centerY-head.centerY)>gap*6)continue;
                int[] stem=attachedRawStem(gray,width,height,main,gap);
                if(stem==null||stem[0]<head.minX-gap*.4f||stem[0]>head.maxX+gap*.4f
                        ||Math.abs(stem[1]-head.centerY)>gap*1.85f)continue;
                if(mergedBeamStrip(gray,width,height,head.centerX,head.centerY,gap)) {
                    rejected.add(head);break;
                }
            }
        }
        return rejected;
    }

    static boolean mergedBeamStrip(byte[] gray,int width,int height,float centerX,float centerY,float gap) {
        if(gray==null||gap<8)return false;
        int cy=Math.round(centerY),radius=Math.round(gap*1.8f);
        if(cy-radius<0||cy+radius>=height)return false;
        for(int direction:new int[]{-1,1}) {
            int valid=0,total=0;List<Integer> tops=new ArrayList<>(),bottoms=new ArrayList<>();
            for(int dx=0;dx<=Math.round(gap*3);dx++) {
                int x=Math.round(centerX)+direction*dx;if(x<0||x>=width)break;
                total++;if((gray[cy*width+x]&255)>=165)continue;
                int top=cy,bottom=cy;
                while(top>cy-radius&&(gray[(top-1)*width+x]&255)<165)top--;
                while(bottom<cy+radius&&(gray[(bottom+1)*width+x]&255)<165)bottom++;
                int span=bottom-top+1;
                if(span<gap*.85f||span>gap*1.8f)continue;
                valid++;tops.add(top);bottoms.add(bottom);
            }
            if(total<Math.round(gap*3)||valid<total*.9f)continue;
            var sortedTops=new ArrayList<>(tops);var sortedBottoms=new ArrayList<>(bottoms);
            sortedTops.sort(Integer::compare);sortedBottoms.sort(Integer::compare);
            int a=sortedTops.get(tops.size()/2),b=sortedBottoms.get(bottoms.size()/2),aligned=0;
            for(int i=0;i<tops.size();i++)if(Math.abs(tops.get(i)-a)<=gap*.15f
                    &&Math.abs(bottoms.get(i)-b)<=gap*.15f)aligned++;
            if(aligned>=valid*.9f)return true;
        }
        return false;
    }

    /** A beam has an extended straight core and no rounded head bulge at its tip. */
    static boolean narrowBeamTip(byte[] gray,int width,int height,float stemX,float centerY,float gap) {
        if(gray==null||gap<8)return false;
        int core=Math.max(1,Math.round(gap*.07f));
        int flank=Math.max(core+2,Math.round(gap*.28f));
        int first=Math.max(3,Math.round(gap*.35f)),last=Math.round(gap*2.2f);
        int search=Math.max(2,Math.round(gap*.3f));
        for(int side:new int[]{-1,1})for(int offset=-search;offset<=search;offset++) {
            float origin=centerY+offset;
            for(int angle=-12;angle<=12;angle++) {
                float slope=angle*.05f;int supported=0,total=0,tip=0,tipTotal=0;
                for(int distance=first;distance<=last;distance++) {
                    int x=Math.round(stemX)+side*distance,y=Math.round(origin+slope*distance);
                    if(x<0||x>=width||y-flank<0||y+flank>=height)break;
                    boolean ink=true;
                    for(int dy=-core;dy<=core;dy++)if((gray[(y+dy)*width+x]&255)>=165)ink=false;
                    boolean thin=(gray[(y-flank)*width+x]&255)>=165
                            &&(gray[(y+flank)*width+x]&255)>=165;
                    total++;if(ink&&thin)supported++;
                    if(distance<=gap*.85f){tipTotal++;if(ink&&thin)tip++;}
                }
                if(total==last-first+1&&supported>=total*.88f&&tipTotal>=3&&tip>=tipTotal*.85f)return true;
            }
        }
        return false;
    }

    /** A small mask island on a straight or curved entrance stroke is not a separate attack. */
    private static List<Component> entranceStrokeFragments(byte[] gray,int width,int height,
            List<Component> heads,List<Staff> staffs) {
        List<Component> rejected=new ArrayList<>();
        if(gray==null)return rejected;
        for(Component head:heads) {
            Staff staff=nearestHeadStaff(staffs,head.centerY);if(staff==null)continue;
            float gap=staff.gap;
            if(head.area>gap*gap*.3f||head.maxX-head.minX+1>gap*.85f
                    ||head.maxY-head.minY+1>gap*.9f
                    ||attachedRawStem(gray,width,height,head,gap*.65f)!=null)continue;
            for(Component main:heads) {
                float dx=main.centerX-head.centerX,dy=head.centerY-main.centerY;
                if(main==head||nearestHeadStaff(staffs,main.centerY)!=staff
                        ||main.area<head.area*3||main.maxX-main.minX+1<gap
                        ||dx<gap||dx>gap*3||dy<gap*.5f||dy>gap*2.5f)continue;
                boolean stem=attachedRawStem(gray,width,height,main,gap*.65f)!=null;
                boolean ordinary=stem&&(head.maxY-head.minY+1<=gap*.65f
                        &&rawStraightEntrance(gray,width,height,head,main,gap)
                        ||rawCurvedEntrance(gray,width,height,head,main,gap));
                boolean pale=!ordinary&&attachedRawStem(gray,width,height,head,gap*.65f,2,180)==null
                        &&attachedRawStem(gray,width,height,main,gap,2,180)!=null
                        &&rawCurvedEntrance(gray,width,height,head,main,gap,180,true);
                if(ordinary||pale){rejected.add(head);break;}
            }
        }
        return rejected;
    }

    /** A scoop has one thin rising curve ending beside the destination, not an oval attack. */
    private static boolean rawCurvedEntrance(byte[] gray,int width,int height,Component head,
            Component main,float gap) {
        return rawCurvedEntrance(gray,width,height,head,main,gap,165,false);
    }

    private static boolean rawCurvedEntrance(byte[] gray,int width,int height,Component head,
            Component main,float gap,int inkThreshold,boolean joined) {
        int left=head.minX-Math.round(gap*(joined?1.8f:1)),right=main.minX-Math.max(2,Math.round(gap*.2f));
        int top=Math.round(main.centerY-gap*.5f),bottom=head.maxY+Math.round(gap*.65f);
        int ruleLeft=left-Math.round(gap*2),ruleRight=right+Math.round(gap*2);
        if(ruleLeft<0||ruleRight>=width||top<1||bottom>=height-1||right-left<gap)return false;
        int w=right-left+1,h=bottom-top+1;byte[] ink=new byte[w*h];boolean[] rules=new boolean[h];
        for(int y=top;y<=bottom;y++) {
            int dark=0;for(int x=ruleLeft;x<=ruleRight;x++)if((gray[y*width+x]&255)<inkThreshold)dark++;
            rules[y-top]=dark>=(ruleRight-ruleLeft+1)*.9f;
        }
        for(int y=0;y<h;) {
            int start=y;while(y<h&&rules[y])y++;
            if(y-start>Math.max(3,Math.round(gap*.3f)))java.util.Arrays.fill(rules,start,y,false);
            if(y==start)y++;
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            if(!rules[y]&&(gray[(top+y)*width+left+x]&255)<inkThreshold)ink[y*w+x]=OmrMeasurePostProcessor.SYMBOL;
        // Restore only crossings supported on both sides of a narrow staff stripe.
        for(int y=0;y<h;) {
            if(!rules[y]){y++;continue;}
            int first=y;while(y<h&&rules[y])y++;
            if(first==0||y==h)continue;
            for(int x=1;x<w-1;x++) {
                if(ink[(first-1)*w+x]!=0&&ink[y*w+x]!=0)
                    for(int yy=first;yy<y;yy++)ink[yy*w+x]=OmrMeasurePostProcessor.SYMBOL;
            }
        }
        Component curve=retainSeedConnectedInk(ink,w,h,head,left,top);
        if(curve==null)return false;
        int span=curve.maxX-curve.minX+1,rise=curve.maxY-curve.minY+1;
        if(curve.minX==0||curve.maxX==w-1&&!joined||curve.minY==0||curve.maxY==h-1
                ||span<gap*1.2f||span>gap*2.5f||rise<gap*.9f||rise>gap*2.2f
                ||span<(head.maxX-head.minX+1)*1.6f||rise<(head.maxY-head.minY+1)*1.5f
                ||curve.area>span*rise*.6f||main.minX-(left+curve.maxX)>gap*.65f
                ||Math.abs(top+curve.minY-main.centerY)>gap*(joined?.8f:.6f))return false;
        if(joined&&!entranceJoinsHead(gray,width,height,ink,w,h,left,top,main,inkThreshold))return false;
        float[] centers=new float[3],columnCenters=new float[w];int[] bins=new int[3],columnCounts=new int[w];
        for(int x=curve.minX;x<=curve.maxX;x++) {
            int count=0,sum=0,runs=0;boolean previous=false;
            for(int y=curve.minY;y<=curve.maxY;y++) {
                boolean dark=ink[y*w+x]!=0;if(dark){count++;sum+=y;if(!previous)runs++;}previous=dark;
            }
            if(count==0||runs>1)return false;
            columnCenters[x]=sum/(float)count;columnCounts[x]=count;
            int bin=Math.min(2,(x-curve.minX)*3/span);centers[bin]+=columnCenters[x];bins[bin]++;
        }
        int checked=0,thin=0,strongColumns=0;
        for(int x=curve.minX+2;x<=curve.maxX-2;x++) {
            float slope=(columnCenters[x+2]-columnCenters[x-2])*.25f;
            int core=columnCounts[x];
            if(joined){core=0;for(int y=curve.minY;y<=curve.maxY;y++)
                if(ink[y*w+x]!=0&&(gray[(top+y)*width+left+x]&255)<165)core++;}
            if(core>0)strongColumns++;
            checked++;if(core/Math.sqrt(1+slope*slope)<=Math.max(3,gap*.35f))thin++;
        }
        if(checked<gap*.7f||thin<checked*.85f||joined&&strongColumns<checked*.8f)return false;
        for(int i=0;i<3;i++){if(bins[i]==0)return false;centers[i]/=bins[i];}
        return centers[0]-centers[1]>=gap*.08f&&centers[1]-centers[2]>=gap*.25f
                &&centers[0]-centers[2]>=gap*.6f
                &&centers[1]-(centers[0]+centers[2])*.5f>=gap*.08f;
    }

    /** A clipped scoop is accepted only when its ascending ink actually reaches
     * the destination head within the short omitted clearance. */
    private static boolean entranceJoinsHead(byte[] gray,int width,int height,byte[] ink,int w,int h,
            int left,int top,Component main,int threshold) {
        boolean[] previous=new boolean[h];for(int y=0;y<h;y++)previous[y]=ink[y*w+w-1]!=0;
        for(int x=left+w;x<=main.minX+2;x++) {
            if(x<0||x>=width)return false;boolean[] next=new boolean[h];
            for(int y=0;y<h;y++)if(top+y>=0&&top+y<height&&(gray[(top+y)*width+x]&255)<threshold)
                for(int dy=-1;dy<=1;dy++)if(y+dy>=0&&y+dy<h&&previous[y+dy]){next[y]=true;break;}
            previous=next;
        }
        for(int y=Math.max(0,main.minY-top);y<=Math.min(h-1,main.maxY-top);y++)if(previous[y])return true;
        return false;
    }

    private static boolean rawStraightEntrance(byte[] gray,int width,int height,Component head,
            Component main,float gap) {
        int left=head.minX-Math.round(gap*.5f),right=main.minX-Math.max(2,Math.round(gap*.25f));
        int top=Math.round(head.centerY-gap*2.2f),bottom=Math.round(head.centerY+gap*2.2f);
        int ruleLeft=left-Math.round(gap*2),ruleRight=right+Math.round(gap*2);
        if(ruleLeft<0||ruleRight>=width||top<0||bottom>=height||right-left<gap*1.3f)return false;
        boolean[] rules=new boolean[bottom-top+1];
        for(int y=top;y<=bottom;y++) {
            int n=0;for(int x=ruleLeft;x<=ruleRight;x++)if((gray[y*width+x]&255)<165)n++;
            rules[y-top]=n>=(ruleRight-ruleLeft+1)*.9f;
        }
        for(int y=0;y<rules.length;) {
            int start=y;while(y<rules.length&&rules[y])y++;
            if(y-start>Math.max(3,Math.round(gap*.3f)))java.util.Arrays.fill(rules,start,y,false);
            if(y==start)y++;
        }
        for(float slope=.65f;slope<=1.56f;slope+=.1f)for(int offset=-1;offset<=1;offset++) {
            int visible=0,supported=0,thin=0;
            for(int x=left;x<=right;x++) {
                float cy=head.centerY+offset-slope*(x-head.centerX);
                int y0=Math.round(cy),radius=Math.max(3,Math.round(gap*.6f));
                if(y0-radius<top||y0+radius>bottom||rules[y0-top])continue;
                visible++;boolean ink=false,wide=false;
                for(int y=y0-radius;y<=y0+radius;y++) {
                    if(rules[y-top]||(gray[y*width+x]&255)>=165)continue;
                    float distance=Math.abs(y-cy);
                    if(distance<=Math.max(1.5f,gap*.18f))ink=true;
                    if(distance>Math.max(3,gap*.3f))wide=true;
                }
                if(ink)supported++;
                if(ink&&!wide)thin++;
            }
            if(visible>=gap&&supported>=visible*.9f&&thin>=visible*.85f)return true;
        }
        return false;
    }

    private static int slurInkThreshold(byte[] gray,int width,int height,Component head,float gap) {
        int[] values=new int[256];int count=0;
        int left=Math.max(0,Math.round(head.centerX-gap*2)),right=Math.min(width-1,Math.round(head.centerX+gap*2));
        int top=Math.max(0,Math.round(head.centerY-gap*1.2f)),bottom=Math.min(height-1,Math.round(head.centerY+gap*1.2f));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++){values[gray[y*width+x]&255]++;count++;}
        int cumulative=0,paper=255;
        for(int i=0;i<256;i++){cumulative+=values[i];if(cumulative>=count*.85f){paper=i;break;}}
        return Math.min(165,Math.max(40,paper-30));
    }

    private static boolean flatStemlessFragment(byte[] gray, int width, int height,
                                                Component head, float gap) {
        if(gray==null)return false;
        int inkThreshold=slurInkThreshold(gray,width,height,head,gap);
        float w = head.maxX - head.minX + 1f, h = head.maxY - head.minY + 1f;
        if(h < gap*.65f && w > h*2.2f
                && attachedRawStem(gray,width,height,head,gap,Math.max(1,Math.round(gap*.16f)),inkThreshold)==null)return true;
        if(w>gap*1.2f || h>gap*.95f || head.area>gap*gap*.5f
                || attachedRawStem(gray,width,height,head,gap*.65f,Math.max(1,Math.round(gap*.65f*.16f)),inkThreshold)!=null)return false;
        return rawSlurBowl(gray,width,height,head,gap);
    }

    /** A segmentation island can cover only the roundest part of a longer slur.
     * Inspect its complete raw component after removing thin, continuous staff rules. */
    private static boolean rawSlurBowl(byte[] gray,int width,int height,Component head,float gap) {
        int inkThreshold=slurInkThreshold(gray,width,height,head,gap);
        int left=Math.max(0,Math.round(head.centerX-gap*2)),right=Math.min(width-1,Math.round(head.centerX+gap*2));
        int top=Math.max(0,Math.round(head.centerY-gap*1.2f)),bottom=Math.min(height-1,Math.round(head.centerY+gap*1.2f));
        int w=right-left+1,h=bottom-top+1;
        boolean[] rules=new boolean[h];
        for(int y=0;y<h;y++) {
            int count=0;for(int x=left;x<=right;x++)if((gray[(top+y)*width+x]&255)<=inkThreshold)count++;
            rules[y]=count>=w*.9f;
        }
        for(int y=0;y<h;) {
            int start=y;while(y<h&&rules[y])y++;
            if(y-start>Math.max(2,Math.round(gap*.3f)))java.util.Arrays.fill(rules,start,y,false);
            if(y==start)y++;
        }
        boolean[] visited=new boolean[w*h];int[] stack=new int[w*h];
        for(int origin=0;origin<visited.length;origin++) {
            int ox=origin%w,oy=origin/w;
            if(visited[origin]||rules[oy]||(gray[(top+oy)*width+left+ox]&255)>inkThreshold)continue;
            int size=0;stack[size++]=origin;visited[origin]=true;
            int minX=w,maxX=-1,minY=h,maxY=-1,overlap=0;
            int[] counts=new int[w],sums=new int[w];
            while(size>0) {
                int at=stack[--size],x=at%w,y=at/w;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                counts[x]++;sums[x]+=y;
                if(x+left>=head.minX&&x+left<=head.maxX&&y+top>=head.minY&&y+top<=head.maxY)overlap++;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int xx=x+dx,yy=y+dy;
                    if(xx<0||xx>=w||yy<0||yy>=h||rules[yy])continue;
                    int next=yy*w+xx;
                    if(!visited[next]&&(gray[(top+yy)*width+left+xx]&255)<=inkThreshold) {
                        visited[next]=true;stack[size++]=next;
                    }
                }
            }
            int span=maxX-minX+1,rise=maxY-minY+1;
            if(minX==0||maxX==w-1||minY==0||maxY==h-1||overlap<head.area*.55f
                    ||span<gap*1.25f||span<(head.maxX-head.minX+1)*1.5f||rise>gap*1.2f||span<rise*1.6f)continue;
            float[] centers=new float[3];int[] bins=new int[3];
            for(int x=minX;x<=maxX;x++)if(counts[x]>0) {
                int bin=Math.min(2,(x-minX)*3/span);centers[bin]+=sums[x]/(float)counts[x];bins[bin]++;
            }
            if(bins[0]==0||bins[1]==0||bins[2]==0)continue;
            for(int i=0;i<3;i++)centers[i]/=bins[i];
            // Straight ledger extensions and small intact ovals lack this returning bend.
            // Compact grace slurs can be deeper than a shallow tie. Require a stronger
            // returning bend and closer endpoint heights when admitting that geometry.
            boolean deep=rise>gap*.85f||span<rise*2.4f;
            float shallowBend=span<gap*1.4f&&Math.abs(centers[0]-centers[2])<gap*.1f?.09f:.1f;
            if(Math.abs(centers[1]-(centers[0]+centers[2])*.5f)>=Math.max(1.25f,gap*(deep?.25f:shallowBend))
                    &&Math.abs(centers[0]-centers[2])<=gap*(deep?.4f:.65f))return true;
        }
        return false;
    }

    /** Tremolo strokes cross both sides of a stem. A fragmented mask may join
     * two strokes into a plausible small head; the repeated short raw bands and
     * the larger head owning that stem distinguish them from a chord. */
    private static List<Component> stemSlashFragments(byte[] gray, int width, int height,
                                                      List<Component> heads, List<Staff> staffs) {
        List<Component> fragments = new ArrayList<>();
        if (gray == null) return fragments;
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            if (candidate.area > gap * gap * .65f
                    || candidate.maxX-candidate.minX+1 > gap * 1.2f) continue;
            for (Component main : heads) {
                float distance = Math.abs(main.centerY-candidate.centerY);
                if (main == candidate || main.area < candidate.area * 2.2f
                        || main.maxX-main.minX+1 < gap
                        || distance < gap * 1.2f || distance > gap * 7f
                        || Math.abs(main.centerX-candidate.centerX) > gap*1.5f) continue;
                int[] stem = attachedRawStem(gray, width, height, main, gap);
                if (stem == null || (candidate.centerY-main.centerY)*stem[2] <= 0
                        || (candidate.centerY-stem[1])*stem[2] > gap * .3f
                        || Math.abs(candidate.centerX-stem[0]) > gap * .9f) continue;
                int edge = stem[2] > 0 ? main.maxY : main.minY;
                // A staff rule through the stroke can hide both of its far ends.
                // Follow only thick ink on either side of the stem in that case.
                boolean isolatedStroke=false;
                int radius=Math.max(2,Math.round(gap*.35f)),wing=Math.max(2,Math.round(gap*.5f));
                for(int y=Math.max(0,Math.round(candidate.centerY)-radius);
                        y<=Math.min(height-1,Math.round(candidate.centerY)+radius);y++) {
                    if(stem[0]-Math.round(gap*1.5f)<0||stem[0]+Math.round(gap*1.5f)>=width)break;
                    if(thickStrokeInk(gray,width,height,stem[0]-wing,y,gap)
                            &&thickStrokeInk(gray,width,height,stem[0]+wing,y,gap)
                            &&boundedStrokeWing(gray,width,height,stem[0],y,gap,-1)
                            &&boundedStrokeWing(gray,width,height,stem[0],y,gap,1)) {isolatedStroke=true;break;}
                }
                if(isolatedStroke) {fragments.add(candidate);break;}
                int first = Math.max(1, Math.min(edge + stem[2]*Math.round(gap*.25f), stem[1]));
                int last = Math.min(height-2, Math.max(edge + stem[2]*Math.round(gap*.25f), stem[1]));
                int side = Math.max(2, Math.round(gap*.42f)), far = Math.round(gap*2f);
                if (stem[0]-far < 0 || stem[0]+far >= width) continue;
                int run = 0, bands = 0; boolean touches = false, provenCrossStroke=false;
                for (int y=first; y<=last+1; y++) {
                    boolean shortStroke = y<=last
                            && rawColumnInk(gray,width,stem[0]-side,y)
                            && rawColumnInk(gray,width,stem[0]+side,y)
                            && !(rawColumnInk(gray,width,stem[0]-far,y)
                            && rawColumnInk(gray,width,stem[0]+far,y));
                    if (shortStroke) run++;
                    else {
                        if (run >= Math.max(2, Math.round(gap*.18f)) && run <= gap*.85f) {
                            bands++;
                            float center = y-(run+1)*.5f;
                            if (Math.abs(center-candidate.centerY) < gap*.75f) {
                                touches=true;
                                provenCrossStroke|=boundedStrokeWing(gray,width,height,stem[0],center,gap,-1)
                                        &&boundedStrokeWing(gray,width,height,stem[0],center,gap,1);
                            }
                        }
                        run=0;
                    }
                }
                if (touches&&(bands>=2||provenCrossStroke)) { fragments.add(candidate); break; }
            }
        }
        return fragments;
    }

    /** Reduced heads in a close, stemmed prefix are ornaments, not extra metrical beats. */
    private static void markGraceHeads(byte[] labels, byte[] gray, int width, int height,
            List<DetectedNote> detected, List<ScoreNoteEvent> events) {
        for (int i=0;i<detected.size();i++) {
            DetectedNote first=detected.get(i);
            if (!(smallGraceHead(first)||roundedBeamedGraceHead(first,gray,width,height)) || (gray != null
                    ? attachedRawStem(gray,width,height,first.head,first.staffGap*.65f)==null
                    : !hasAttachedStem(labels,width,height,first.head,first.staffGap))) continue;
            List<Integer> prefix=new ArrayList<>(); prefix.add(i);
            DetectedNote previous=first;
            for(int j=i+1;j<detected.size();j++) {
                DetectedNote next=detected.get(j);
                if(next.event.measureIndex()!=first.event.measureIndex())break;
                if(next.event.staffIndex()!=first.event.staffIndex()
                        ||next.event.staffCount()!=first.event.staffCount())continue;
                float dx=next.head.centerX-previous.head.centerX;
                if(dx<first.staffGap*.65f || dx>first.staffGap*2.8f
                        ||Math.abs(next.head.centerY-previous.head.centerY)>first.staffGap*2.5f)break;
                if(smallGraceHead(next)||roundedBeamedGraceHead(next,gray,width,height)) {
                    if(gray!=null
                            ? attachedRawStem(gray,width,height,next.head,next.staffGap*.65f)==null
                            : !hasAttachedStem(labels,width,height,next.head,next.staffGap))break;
                    prefix.add(j);previous=next;continue;
                }
                if(next.head.area>first.head.area*1.65f
                        &&next.head.maxX-next.head.minX+1>first.staffGap*1.05f
                        &&(prefix.stream().allMatch(index->smallGraceHead(detected.get(index)))
                        ||prefix.size()>=2&&prefix.stream().allMatch(index->
                                next.head.area>detected.get(index).head.area*1.65f)))
                    for(int index:prefix)events.set(index,events.get(index).withArticulations(
                            events.get(index).articulations()|NoteOrnament.GRACE));
                break;
            }
        }
    }

    private static boolean smallGraceHead(DetectedNote n) {
        return n.head.maxX-n.head.minX+1<=Math.round(n.staffGap*.95f)
                &&n.head.maxY-n.head.minY+1<=Math.round(n.staffGap*.78f)
                &&n.head.area<=n.staffGap*n.staffGap*.60f
                &&n.event.augmentationDots()==0
                &&n.event.unbeamedDurationBeats()<ScoreNoteEvent.DURATION_HALF;
    }

    private static boolean rawColumnInk(byte[] gray, int width, int x, int y) {
        return (gray[(y-1)*width+x]&255)<170 || (gray[y*width+x]&255)<170
                || (gray[(y+1)*width+x]&255)<170;
    }


    /** Cross heads denote an unpitched attack. A partial semantic mask can keep
     * only one corner, so inspect the two converging diagonals in the raw ink.
     * Hollow oval sides diverge toward their centre, the opposite topology. */
    private static boolean isUnpitchedCrossHead(byte[] gray,int width,int height,
                                               Component head,Staff staff) {
        if(gray==null||staff.gap<9||head.maxX-head.minX>staff.gap*1.7f
                ||head.maxY-head.minY>staff.gap*1.2f)return false;
        float gap=staff.gap;
        int inner=Math.max(2,Math.round(gap*.22f)),outer=Math.max(inner+2,Math.round(gap*.38f));
        int radius=Math.round(gap),searchX=Math.round(gap*.8f),searchY=Math.round(gap*.65f);
        for(int cy=Math.max(outer,Math.round(head.centerY)-searchY);
                cy<=Math.min(height-outer-1,Math.round(head.centerY)+searchY);cy++) {
            boolean rule=false;
            int ruleLeft=Math.max(0,Math.round(head.centerX-gap*3)),ruleRight=Math.min(width-1,Math.round(head.centerX+gap*3));
            for(int dy:new int[]{-outer,-inner,inner,outer}) {
                int ink=0;
                for(int x=ruleLeft;x<=ruleRight;x++)if((gray[(cy+dy)*width+x]&255)<165)ink++;
                if(ink>(ruleRight-ruleLeft+1)*.8f)rule=true;
            }
            if(rule)continue;
            for(int cx=Math.max(radius,Math.round(head.centerX)-searchX);
                    cx<=Math.min(width-radius-1,Math.round(head.centerX)+searchX);cx++) {
                int[] upper=crossHeadSides(gray,width,cx,cy-outer,radius,gap);
                if(upper==null)continue;
                int[] lower=crossHeadSides(gray,width,cx,cy+outer,radius,gap);
                if(lower==null)continue;
                int[] upperInner=crossHeadSides(gray,width,cx,cy-inner,radius,gap);
                int[] lowerInner=crossHeadSides(gray,width,cx,cy+inner,radius,gap);
                if(upperInner==null||lowerInner==null)continue;
                boolean converges=true;
                for(int[] pair:new int[][]{upper,lower}) {
                    int[] near=pair==upper?upperInner:lowerInner;
                    if(near[1]-pair[1]<gap*.1f||pair[2]-near[2]<gap*.1f
                            ||pair[2]-pair[1]<gap*.45f)converges=false;
                }
                if(!converges||Math.abs(upper[1]-lower[1])>gap*.25f
                        ||Math.abs(upper[2]-lower[2])>gap*.25f)continue;
                if(head.centerX<Math.min(upper[0],lower[0])-1
                        ||head.centerX>Math.max(upper[3],lower[3])+1
                        ||Math.abs(head.centerY-cy)>outer+1)continue;
                return true;
            }
        }
        return false;
    }

    private static int[] crossHeadSides(byte[] gray,int width,int cx,int y,int radius,float gap) {
        if((gray[y*width+cx]&255)<165)return null;
        int left=cx-1,right=cx+1;
        while(left>=cx-radius&&(gray[y*width+left]&255)>=165)left--;
        while(right<=cx+radius&&(gray[y*width+right]&255)>=165)right++;
        if(left<cx-radius||right>cx+radius)return null;
        int begin=left,end=right;
        while(begin>cx-radius&&(gray[y*width+begin-1]&255)<165)begin--;
        while(end<cx+radius&&(gray[y*width+end+1]&255)<165)end++;
        if(left-begin+1<2||end-right+1<2||left-begin+1>gap*.55f||end-right+1>gap*.55f
                ||end-begin<gap*.85f||end-begin>gap*1.9f)return null;
        return new int[]{begin,left,right,end};
    }

    private static boolean plausibleHead(Component head, float gap) {
        float width = head.maxX - head.minX + 1f, height = head.maxY - head.minY + 1f;
        return width >= Math.max(2f, gap * .38f) && height >= Math.max(2f, gap * .30f)
                && width <= gap * 3.2f && height <= gap * 3.2f
                && head.area >= Math.max(4, Math.round(gap * gap * .11f));
    }

    private static List<Component> augmentationDotHeads(byte[] labels, byte[] gray, int width, int height,
                                                         List<Component> heads,
                                                         List<Staff> staffs) {
        List<Component> dots = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            float candidateWidth = candidate.maxX - candidate.minX + 1f;
            float candidateHeight = candidate.maxY - candidate.minY + 1f;
            if (candidateWidth > gap * .82f || candidateHeight > gap * .82f
                    || candidate.area > gap * gap * .48f
                    || hasAttachedStem(labels, width, height, candidate, gap)
                    || attachedRawStem(gray,width,height,candidate,gap*.65f)!=null) continue;
            for (Component main : heads) {
                if (main == candidate || nearestHeadStaff(staffs, main.centerY) != staff) continue;
                float mainWidth = main.maxX - main.minX + 1f;
                float mainHeight = main.maxY - main.minY + 1f;
                float horizontal = candidate.minX - main.maxX;
                if (horizontal < gap * .10f || horizontal > gap * 2.20f
                        || Math.abs(candidate.centerY - main.centerY) > gap * .78f) continue;
                if (mainWidth < gap * .62f || mainHeight < gap * .44f
                        || main.area < candidate.area * 1.55f) continue;
                dots.add(candidate);
                break;
            }
        }
        return dots;
    }

    /** A nearby bow/rest is not a duration stem unless it reaches the head. Raw ink can
     * independently bridge a model-label gap; do not require every stem pixel to be labelled. */
    private static boolean hasStemAtHead(byte[] labels,int width,int height,Component head,float gap) {
        int pad=Math.max(1,Math.round(gap*.3f));
        for(int y=Math.max(0,head.minY-pad);y<=Math.min(height-1,head.maxY+pad);y++)
            for(int x=Math.max(0,head.minX-pad);x<=Math.min(width-1,head.maxX+pad);x++)
                if(labels[y*width+x]==OmrMeasurePostProcessor.STEM_OR_REST)return true;
        return false;
    }

    private static boolean hasAttachedStem(byte[] labels, int width, int height,
                                            Component head, float gap) {
        int left = Math.max(0, Math.round(head.minX - gap * .36f));
        int right = Math.min(width - 1, Math.round(head.maxX + gap * .36f));
        int top = Math.max(0, Math.round(head.minY - gap * 2.8f));
        int bottom = Math.min(height - 1, Math.round(head.maxY + gap * 2.8f));
        int rows = 0, above = 0, below = 0;
        for (int y = top; y <= bottom; y++) {
            boolean stem = false;
            for (int x = left; x <= right; x++) if (labels[y * width + x]
                    == OmrMeasurePostProcessor.STEM_OR_REST) { stem = true; break; }
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
    private static boolean rawRoundArticulationDot(byte[] labels,byte[] gray,int width,int height,
            Component head,float gap) {
        if(gray==null)return false;
        int padding=Math.max(2,Math.round(gap*.30f));
        int left=Math.max(0,head.minX-padding),right=Math.min(width-1,head.maxX+padding);
        int top=Math.max(0,head.minY-padding),bottom=Math.min(height-1,head.maxY+padding);
        int w=right-left+1,h=bottom-top+1;
        boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];
        for(int start=0;start<w*h;start++) {
            if(seen[start]||(gray[(top+start/w)*width+left+start%w]&255)>=165)continue;
            int read=0,count=1;queue[0]=start;seen[start]=true;
            int minX=w,maxX=-1,minY=h,maxY=-1,overlap=0;
            while(read<count) {
                int point=queue[read++],x=point%w,y=point/w;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                int xx=left+x,yy=top+y;
                if(xx>=head.minX&&xx<=head.maxX&&yy>=head.minY&&yy<=head.maxY
                        &&labels[yy*width+xx]==OmrMeasurePostProcessor.NOTEHEAD)overlap++;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int nx=x+dx,ny=y+dy;if(nx<0||ny<0||nx>=w||ny>=h)continue;
                    int next=ny*w+nx;
                    if(!seen[next]&&(gray[(top+ny)*width+left+nx]&255)<165) {
                        seen[next]=true;queue[count++]=next;
                    }
                }
            }
            if(minX==0||maxX==w-1||minY==0||maxY==h-1||overlap<head.area*.5f)continue;
            float rw=maxX-minX+1,rh=maxY-minY+1;
            if(Math.max(rw,rh)<=gap*.72f&&Math.max(rw,rh)<=Math.min(rw,rh)*1.45f
                    &&count>=Math.max(4,gap*gap*.04f)&&count>=rw*rh*.55f)return true;
        }
        return false;
    }

    /** Mirrored printed parentheses enclosing a note do not alter the next pitch. */
    private static List<AccidentalCandidate> noteParentheses(byte[] gray,int width,int height,
            List<AccidentalCandidate> candidates,List<Component> heads,List<Staff> staffs) {
        List<AccidentalCandidate> result=new ArrayList<>();
        if(gray==null)return result;
        for(Component head:heads) {
            Staff staff=nearestHeadStaff(staffs,head.centerY);
            if(staff==null)continue;
            float gap=staff.gap;
            if(head.maxX-head.minX+1>gap*2.2f||head.maxY-head.minY+1>gap*1.35f)continue;
            for(AccidentalCandidate left:candidates) {
                Component a=left.component;
                if(a.maxX>=head.minX||head.minX-a.maxX>gap
                        ||a.minY>head.minY||a.maxY<head.maxY
                        ||Math.abs((a.minY+a.maxY)*.5f-head.centerY)>gap*.35f
                        ||!printedParenthesis(gray,width,height,a,gap,true))continue;
                for(AccidentalCandidate right:candidates) {
                    Component b=right.component;
                    if(b.minX<=head.maxX||b.minX-head.maxX>gap
                            ||b.minY>head.minY||b.maxY<head.maxY
                            ||Math.abs(a.minY-b.minY)>gap*.3f||Math.abs(a.maxY-b.maxY)>gap*.3f
                            ||Math.abs((a.maxX+b.minX)*.5f-head.centerX)>gap*.4f
                            ||!printedParenthesis(gray,width,height,b,gap,false))continue;
                    result.add(left);result.add(right);
                }
            }
        }
        return result;
    }

    private static boolean printedParenthesis(byte[] gray,int width,int height,Component glyph,
            float gap,boolean opening) {
        int w=glyph.maxX-glyph.minX+1,h=glyph.maxY-glyph.minY+1;
        if(w<gap*.24f||w>gap*.8f||h<gap*1.3f||h>gap*2.8f
                ||glyph.minX<0||glyph.maxX>=width||glyph.minY<0||glyph.maxY>=height)return false;
        float[] centers=new float[h];java.util.Arrays.fill(centers,Float.NaN);
        for(int y=glyph.minY;y<=glyph.maxY;y++) {
            int n=0,sum=0;
            for(int x=glyph.minX;x<=glyph.maxX;x++)if((gray[y*width+x]&255)<170){sum+=x-glyph.minX;n++;}
            if(n>0)centers[y-glyph.minY]=sum/(float)n;
        }
        float top=parenthesisBand(centers,0,.15f),middle=parenthesisBand(centers,.35f,.65f),
                bottom=parenthesisBand(centers,.85f,1);
        if(!Float.isFinite(top)||!Float.isFinite(middle)||!Float.isFinite(bottom))return false;
        float bend=Math.max(1,w*.2f),sign=opening?1:-1;
        return sign*(top-middle)>=bend&&sign*(bottom-middle)>=bend
                &&Math.abs(top-bottom)<=gap*.25f;
    }

    private static float parenthesisBand(float[] centers,float from,float to) {
        int first=Math.round(from*(centers.length-1)),last=Math.round(to*(centers.length-1)),n=0;float sum=0;
        for(int i=first;i<=last;i++)if(Float.isFinite(centers[i])){sum+=centers[i];n++;}
        return n>=Math.max(2,(last-first+1)*.5f)?sum/n:Float.NaN;
    }

    /** A flag attached to an accepted grace head cannot flatten the next note. */
    private static boolean attachedGraceFlag(byte[] labels,byte[] gray,int width,int height,
            AccidentalCandidate candidate,List<Component> heads,List<Staff> staffs) {
        if(gray==null)return false;
        Component glyph=candidate.component;
        Staff staff=nearestHeadStaff(staffs,glyph.centerY);
        if(staff==null)return false;
        float gap=staff.gap;
        if(!isFlatGlyph(labels,width,height,candidate,gap))return false;
        int spine=glyph.minX,strongest=0;
        for(int x=glyph.minX;x<=glyph.maxX;x++) {
            int ink=0;
            for(int y=glyph.minY;y<=glyph.maxY;y++)if(candidate.matches(labels[y*width+x]))ink++;
            if(ink>strongest){strongest=ink;spine=x;}
        }
        for(int i=0;i<heads.size();i++) {
            Component original=heads.get(i),head=original;
            if(head.maxX-head.minX+1>gap*1.25f||head.maxY-head.minY+1>gap*.95f||head.area>gap*gap*.85f) {
                head=printedGraceCore(gray,width,height,original,glyph,spine,gap);
                if(head==null)continue;
            }
            if(head.maxX-head.minX+1>gap*1.25f||head.maxY-head.minY+1>gap*.95f
                    ||head.area>gap*gap*.85f||nearestHeadStaff(staffs,head.centerY)!=staff
                    ||head.centerY-glyph.maxY<gap*.35f||head.centerY-glyph.maxY>gap*1.1f
                    ||Math.abs(head.maxX-spine)>gap*.35f)continue;
            int[] stem=attachedRawStem(gray,width,height,head,gap*.65f);
            if(stem==null||stem[2]!=-1||Math.abs(stem[0]-spine)>gap*.22f
                    ||Math.abs(stem[1]-glyph.minY)>gap*.65f)continue;
            if(head!=original)heads.set(i,head);
            return true;
        }
        return false;
    }

    /** A short grace slur can enlarge the semantic head. Recover only a compact
     * printed core beneath the flag, stable across three ink thresholds. */
    private static Component printedGraceCore(byte[] gray,int width,int height,Component source,
            Component glyph,int spine,float gap) {
        if(gap<8||source.maxX-source.minX+1>gap*2.4f||source.maxY-source.minY+1>gap*1.8f
                ||source.area>gap*gap*2f||source.minX>=spine||source.maxX<spine
                ||source.centerY-glyph.maxY<0||source.centerY-glyph.maxY>gap*1.4f)return null;
        int pad=Math.max(2,Math.round(gap*.25f)),left=Math.max(0,source.minX-pad),top=Math.max(0,source.minY-pad);
        int w=Math.min(width,source.maxX+pad+1)-left,h=Math.min(height,source.maxY+pad+1)-top;
        int rx=Math.max(2,Math.round(gap*.2f)),ry=Math.max(2,Math.round(gap*.18f));
        List<int[]> kernel=new ArrayList<>();
        for(int y=-ry;y<=ry;y++)for(int x=-rx;x<=rx;x++)
            if(x*x/(float)(rx*rx)+y*y/(float)(ry*ry)<=1)kernel.add(new int[]{x,y});
        Component reference=null;
        for(int threshold:new int[]{60,100,140}) {
            byte[] opened=new byte[w*h];
            for(int y=ry;y<h-ry;y++)for(int x=rx;x<w-rx;x++) {
                boolean solid=true;
                for(int[] k:kernel)if((gray[(top+y+k[1])*width+left+x+k[0]]&255)>=threshold){solid=false;break;}
                if(solid)for(int[] k:kernel)opened[(y+k[1])*w+x+k[0]]=1;
            }
            Component accepted=null;
            for(Component c:findComponents(opened,w,h,(byte)1)) {
                Component core=new Component(c.area,c.minX+left,c.maxX+left,c.minY+top,c.maxY+top,c.centerX+left,c.centerY+top);
                if(core.area<gap*gap*.25f||core.area>gap*gap*.85f
                        ||core.maxX-core.minX+1<gap*.6f||core.maxX-core.minX+1>gap*1.25f
                        ||core.maxY-core.minY+1<gap*.4f||core.maxY-core.minY+1>Math.round(gap*.95f)
                        ||Math.abs(core.maxX-spine)>gap*.35f||core.centerX>=spine
                        ||core.centerY-glyph.maxY<gap*.35f||core.centerY-glyph.maxY>gap*1.1f)continue;
                if(accepted!=null)return null;
                accepted=core;
            }
            if(accepted==null)return null;
            if(reference==null)reference=accepted;
            else if(Math.abs(reference.centerX-accepted.centerX)>gap*.12f
                    ||Math.abs(reference.centerY-accepted.centerY)>gap*.12f)return null;
        }
        return reference;
    }

    /** A small isolated dot above/below a full head is an articulation, not another pitch. */
    private static List<Component> articulationDotHeads(byte[] labels, byte[] gray,
            int width, int height, List<Component> heads, List<Staff> staffs) {
        List<Component> dots = new ArrayList<>();
        for (Component candidate : heads) {
            Staff staff = nearestHeadStaff(staffs, candidate.centerY);
            if (staff == null) continue;
            float gap = staff.gap;
            float w = candidate.maxX - candidate.minX + 1f;
            float h = candidate.maxY - candidate.minY + 1f;
            if (w > gap * .75f || h > gap * .75f || candidate.area > gap * gap * .36f) continue;
            if (Math.max(w,h) > Math.min(w,h) * 1.6f
                    && !rawRoundArticulationDot(labels,gray,width,height,candidate,gap)) continue;
            // The broad semantic stem window can borrow the main note's stem.
            // Trace contiguous raw ink from this component when pixels exist.
            if (gray != null ? attachedRawStem(gray,width,height,candidate,gap) != null
                    : hasAttachedStem(labels,width,height,candidate,gap)) continue;
            for (Component main : heads) {
                if (main == candidate || nearestHeadStaff(staffs,main.centerY) != staff
                        || main.area < candidate.area * 2.5f) continue;
                float distance = Math.abs(main.centerY-candidate.centerY);
                if (Math.abs(main.centerX-candidate.centerX) <= gap * .90f
                        && distance >= gap * .78f && distance <= gap * 2.5f
                        && main.maxX-main.minX+1 >= gap * .85f) {
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
    private static float detectUnbeamedDuration(byte[] labels, byte[] gray, int width, int height,
                                                 Component head, float gap, int beamCount) {
        boolean open = hasOpenCenter(labels, gray, width, height, head, gap);
        boolean stem = hasAttachedStem(labels, width, height, head, gap);
        if (open && stem && gray != null && !hasStemAtHead(labels,width,height,head,gap)
                && attachedRawStem(gray,width,height,head,gap)==null) stem=false;
        if (open) return stem ? ScoreNoteEvent.DURATION_HALF : ScoreNoteEvent.DURATION_WHOLE;
        if (beamCount > 0) return ScoreNoteEvent.DURATION_UNKNOWN;
        // A filled head is a quarter even when a thin stem was missed by segmentation. Treating it
        // as unknown would send the renderer back to noisy horizontal spacing.
        return ScoreNoteEvent.DURATION_QUARTER;
    }

    private static boolean hasOpenCenter(byte[] labels, byte[] gray, int width, int height,
                                         Component head, float gap) {
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
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
            samples++;
            int index = y * width + x;
            // The segmentation model sometimes paints the complete oval as NOTEHEAD even when
            // the rendered page has a plainly white center. The raw page pixel is the direct
            // evidence for a hollow half/whole head; requiring a non-NOTEHEAD semantic label here
            // converted those sustained notes to one-beat quarters and left silent measure tails.
            if ((gray[index] & 0xff) >= 185) brightHole++;
        }
        if (samples >= 3 && brightHole >= Math.max(2, Math.round(samples * .34f))) return true;
        // A tiny grace head or a fragmented model component can enclose a few antialiased
        // corner pixels without being a hollow half. Expanded pocket recovery is full-size only.
        if (headWidth < gap * 1.05f || headHeight < gap * .85f) return false;
        // Tinted scans and staff lines leave gray, rather than white, enclosed pockets.
        // Adapt only this four-sided pocket test; the simple center test stays conservative.
        int[] shades=new int[256];int shadeCount=0;
        int margin=Math.max(2,Math.round(gap));
        for(int y=Math.max(0,head.minY-margin);y<=Math.min(height-1,head.maxY+margin);y++)
            for(int x=Math.max(0,head.minX-margin);x<=Math.min(width-1,head.maxX+margin);x++) {
                shades[gray[y*width+x]&255]++;shadeCount++;
            }
        int background=255,seen=0;
        for(int value=0;value<256;value++)if((seen+=shades[value])>=shadeCount*.8){background=value;break;}
        int pocketThreshold=Math.min(185,Math.max(145,background-40));
        // A thick ledger/staff line can cover the middle of a hollow oval. Look for the
        // remaining enclosed white pockets above/below it, not just an average over its centre.
        // Requiring dark ink on both sides rejects the exterior corners of a filled/slanted head.
        int enclosed = 0, pocketRows = 0;
        for (int y = Math.max(head.minY + 1, top - 2);
             y <= Math.min(head.maxY - 1, bottom + 2); y++) {
            int rowHoles = 0;
            for (int x = Math.max(head.minX + 1, left - 1);
                 x <= Math.min(head.maxX - 1, right + 1); x++) {
                if ((gray[y * width + x] & 0xff) < pocketThreshold) continue;
                boolean inkLeft = false, inkRight = false, inkAbove = false, inkBelow = false;
                for (int xx = head.minX; xx < x; xx++)
                    if ((gray[y * width + xx] & 0xff) <= 135) { inkLeft = true; break; }
                for (int xx = x + 1; xx <= head.maxX; xx++)
                    if ((gray[y * width + xx] & 0xff) <= 135) { inkRight = true; break; }
                for (int yy = head.minY; yy < y; yy++)
                    if ((gray[yy * width + x] & 0xff) <= 135) { inkAbove = true; break; }
                for (int yy = y + 1; yy <= head.maxY; yy++)
                    if ((gray[yy * width + x] & 0xff) <= 135) { inkBelow = true; break; }
                if (inkLeft && inkRight && inkAbove && inkBelow) rowHoles++;
            }
            enclosed += rowHoles;
            if (rowHoles >= 2) pocketRows++;
        }
        // Heavy outlines can leave small pockets above and below a staff rule.
        // Require more enclosed rows before accepting their smaller combined area.
        return pocketRows >= 2 && enclosed >= Math.max(4, Math.round(headWidth * headHeight * .055f))
                || pocketRows >= Math.max(4, Math.round(gap * .22f))
                && enclosed >= Math.max(8, Math.round(headWidth * headHeight * .04f));
    }

    /** Reconnect narrow cuts in an accidental's semantic mask using the printed ink. */
    private static List<AccidentalCandidate> joinLocalAccidentalFragments(byte[] labels,
            byte[] gray, int width, int height, List<AccidentalCandidate> candidates,
            List<Staff> staffs) {
        List<AccidentalCandidate> sameLabel = joinAccidentalFragments(labels,gray,width,height,candidates,staffs,false);
        List<AccidentalCandidate> result = new ArrayList<>(sameLabel);
        // Keep the original glyphs: a cross-label union can also include a
        // nearby rule fragment and must not replace an already legible natural.
        for (AccidentalCandidate candidate : joinAccidentalFragments(labels,gray,width,height,sameLabel,staffs,true))
            if (candidate.label == 0) result.add(candidate);
        return result;
    }

    private static List<AccidentalCandidate> joinAccidentalFragments(byte[] labels,
            byte[] gray, int width, int height, List<AccidentalCandidate> candidates,
            List<Staff> staffs, boolean mixedLabels) {
        if (gray == null || gray.length != labels.length) return candidates;
        List<AccidentalCandidate> joined = new ArrayList<>(candidates);
        for (int i=0;i<joined.size();i++) {
            AccidentalCandidate a=joined.get(i);
            Staff staff=nearestHeadStaff(staffs,a.component.centerY);
            if(staff==null)continue;
            float gap=staff.gap;
            for(int j=i+1;j<joined.size();j++) {
                AccidentalCandidate b=joined.get(j);
                if (!mixedLabels && a.label != b.label) continue;
                Component ac=a.component,bc=b.component;
                int left=Math.min(ac.minX,bc.minX),right=Math.max(ac.maxX,bc.maxX);
                int top=Math.min(ac.minY,bc.minY),bottom=Math.max(ac.maxY,bc.maxY);
                int reach=Math.max(2,Math.round(gap*.42f));
                if(right-left+1>gap*1.65f||bottom-top+1>gap*3.70f
                        ||Math.max(ac.minX,bc.minX)-Math.min(ac.maxX,bc.maxX)>reach
                        ||Math.max(ac.minY,bc.minY)-Math.min(ac.maxY,bc.maxY)>reach)continue;
                boolean connected=false;
                for(int y=ac.minY;y<=ac.maxY&&!connected;y++)
                    for(int x=ac.minX;x<=ac.maxX&&!connected;x++) {
                        if(!a.matches(labels[y*width+x]))continue;
                        for(int by=Math.max(bc.minY,y-reach);by<=Math.min(bc.maxY,y+reach)&&!connected;by++)
                            for(int bx=Math.max(bc.minX,x-reach);bx<=Math.min(bc.maxX,x+reach);bx++) {
                                if(!b.matches(labels[by*width+bx]))continue;
                                int steps=Math.max(Math.abs(bx-x),Math.abs(by-y));
                                if(steps==0)continue;
                                boolean ink=true;
                                for(int k=1;k<steps;k++) {
                                    int px=Math.round(x+(bx-x)*(float)k/steps),py=Math.round(y+(by-y)*(float)k/steps);
                                    if((gray[py*width+px]&255)>180){ink=false;break;}
                                }
                                if(ink){connected=true;break;}
                            }
                    }
                if(!connected)continue;
                int area=ac.area+bc.area;
                a=new AccidentalCandidate(new Component(area,left,right,top,bottom,
                        (ac.centerX*ac.area+bc.centerX*bc.area)/area,
                        (ac.centerY*ac.area+bc.centerY*bc.area)/area),(byte)(a.label==b.label?a.label:0));
                joined.set(i,a);joined.remove(j);j=i;
            }
        }
        return joined;
    }

    /** Restore shared stem/crossbar pixels only inside an accidental-labelled glyph. */
    private static boolean rawSharpFromSeed(byte[] gray,int width,int height,
            List<AccidentalCandidate> candidates,Component head,float gap) {
        if(gray==null)return false;
        for(AccidentalCandidate seed:candidates) {
            Component c=seed.component;
            if(seed.label!=OmrMeasurePostProcessor.CLEF_OR_KEY
                    ||head.minX-c.maxX<gap*.10f||head.minX-c.maxX>gap*1.35f
                    ||Math.abs(c.centerY-head.centerY)>gap*.9f
                    ||c.maxY-c.minY+1<gap*1.55f||c.maxY-c.minY+1>gap*3.65f
                    ||c.maxX-c.minX+1<gap*.65f||c.maxX-c.minX+1>gap*1.8f)continue;
            if(rawSharpInBounds(gray,width,height,head,gap,Math.max(0,c.minX),
                    Math.min(width-1,c.maxX),Math.max(0,c.minY),Math.min(height-1,c.maxY),false))return true;
        }
        List<Component> bars=new ArrayList<>();
        for(AccidentalCandidate candidate:candidates) {
            Component c=candidate.component;
            if(c.maxX>head.minX-gap*.10f||head.minX-c.maxX>gap*1.6f
                    ||Math.abs(c.centerY-head.centerY)>gap*1.3f
                    ||c.maxY-c.minY>gap*.6f||c.maxX-c.minX<gap*.35f
                    ||c.maxX-c.minX>gap*1.25f)continue;
            bars.add(c);
        }
        // Lost thin spines leave two separate semantic crossbars. Their aligned
        // pair locates a crop; the printed sharp still has to prove its shape.
        for(Component upper:bars)for(Component lower:bars) {
            float dy=lower.centerY-upper.centerY;
            if(dy<gap*.65f||dy>gap*1.5f||Math.abs(upper.centerX-lower.centerX)>gap*.5f)continue;
            int left=Math.max(0,Math.round(Math.min(upper.minX,lower.minX)-gap*.10f));
            int right=Math.min(width-1,Math.min(Math.round(head.minX-gap*.15f),
                    Math.round(Math.max(upper.maxX,lower.maxX)+gap*.10f)));
            int top=Math.max(0,Math.round(upper.minY-gap*.8f));
            int bottom=Math.min(height-1,Math.round(lower.maxY+gap*.8f));
            if(right<=left||bottom<=top)continue;
            if(rawSharpInBounds(gray,width,height,head,gap,left,right,top,bottom,true))return true;
        }
        return false;
    }

    private static boolean rawSharpInBounds(byte[] gray,int width,int height,Component head,float gap,
            int left,int right,int top,int bottom,boolean checkEdges) {
        int w=right-left+1,h=bottom-top+1;
        int reach=Math.max(3,Math.round(gap*.6f)),probe=Math.max(2,Math.round(gap*.2f));
        // Preserve the original exclusive cutoffs for complete semantic seeds.
        for(int threshold:checkEdges?new int[]{180,225}:new int[]{179,224}) {
            byte[] mask=new byte[w*h];int area=0,minX=w,maxX=-1,minY=h,maxY=-1;long sx=0,sy=0;
            for(int y=top;y<=bottom;y++) {
                int outside=0,total=0;
                for(int x=Math.max(0,left-reach);x<=Math.min(width-1,right+reach);x++)
                    if(x<left||x>right){total++;if((gray[y*width+x]&255)<=threshold)outside++;}
                boolean rule=total>0&&outside>total*.8f;
                for(int x=left;x<=right;x++) {
                    if((gray[y*width+x]&255)>threshold)continue;
                    if(rule&&(y<probe||y+probe>=height||(gray[(y-probe)*width+x]&255)>threshold
                            ||(gray[(y+probe)*width+x]&255)>threshold))continue;
                    int xx=x-left,yy=y-top;mask[yy*w+xx]=OmrMeasurePostProcessor.CLEF_OR_KEY;
                    area++;sx+=xx;sy+=yy;minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);minY=Math.min(minY,yy);maxY=Math.max(maxY,yy);
                }
            }
            if(area==0||(checkEdges&&rawStrokeLeavesCrop(gray,width,height,mask,w,h,left,top,gap)))continue;
            Component glyph=new Component(area,minX,maxX,minY,maxY,sx/(float)area,sy/(float)area);
            float center=sharpPitchCenter(mask,w,h,new AccidentalCandidate(glyph,OmrMeasurePostProcessor.CLEF_OR_KEY),gap);
            if(Float.isFinite(center)&&Math.abs(center+top-head.centerY)<gap*.4f)return true;
        }
        return false;
    }

    /** Two surviving crossbars can locate a faded natural whose thin spines were
     * labelled as stems. Confirm the offset endpoints in the original pixels. */
    private static boolean rawNaturalFromCrossbars(byte[] gray,int width,int height,
            List<AccidentalCandidate> candidates,Component head,float gap) {
        if(gray==null)return false;
        for(AccidentalCandidate seed:candidates) {
            Component glyph=seed.component;
            if(head.minX-glyph.maxX < gap*.10f || head.minX-glyph.maxX > gap*1.35f
                    || Math.abs(glyph.centerY-head.centerY)>gap*.9f
                    || glyph.maxY-glyph.minY<gap*.65f
                    || glyph.maxX-glyph.minX<gap*.35f
                    || glyph.maxX-glyph.minX>gap*1.5f)continue;
            if(rawNaturalAtSeed(gray,width,height,glyph,head,gap))return true;
        }
        List<Component> bars=new ArrayList<>();
        for(var c:candidates) {
            Component g=c.component;
            if(g.maxX>head.minX+gap*.1f||head.minX-g.maxX>gap*1.6f
                    ||Math.abs(g.centerY-head.centerY)>gap*1.3f
                    ||g.maxY-g.minY>gap*.6f||g.maxX-g.minX<gap*.35f
                    ||g.maxX-g.minX>gap*1.25f)continue;
            bars.add(g);
        }
        for(Component a:bars)for(Component b:bars) {
            float dy=b.centerY-a.centerY;
            if(dy<gap*.65f||dy>gap*1.5f||Math.abs(a.centerX-b.centerX)>gap*.5f)continue;
            int left=Math.max(0,Math.round(Math.min(a.minX,b.minX)-gap*.2f));
            int right=Math.min(Math.round(head.minX-gap*.15f),Math.round(Math.max(a.maxX,b.maxX)));
            int top=Math.max(0,Math.round(a.minY-gap*.8f)),bottom=Math.min(height-1,Math.round(b.maxY+gap*.8f));
            int w=right-left+1,h=bottom-top+1;if(w<=0||h<=0)continue;
            byte[] mask=new byte[w*h];int area=0,minX=w,maxX=-1,minY=h,maxY=-1;long sx=0,sy=0;
            for(int y=top;y<=bottom;y++) {
                int outside=0,samples=0;
                for(int x=Math.max(0,left-Math.round(gap));x<=Math.min(width-1,right+Math.round(gap));x++)
                    if(x<left||x>right){samples++;if((gray[y*width+x]&255)<225)outside++;}
                if(samples>0&&outside>samples*.80f)continue;
                for(int x=left;x<=right;x++)if((gray[y*width+x]&255)<225) {
                    int xx=x-left,yy=y-top;mask[yy*w+xx]=OmrMeasurePostProcessor.SYMBOL;
                    area++;sx+=xx;sy+=yy;minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);minY=Math.min(minY,yy);maxY=Math.max(maxY,yy);
                }
            }
            if(area<3||rawStrokeLeavesCrop(gray,width,height,mask,w,h,left,top,gap))continue;
            Component g=new Component(area,minX,maxX,minY,maxY,sx/(float)area,sy/(float)area);
            if(isNaturalGlyph(mask,w,h,new AccidentalCandidate(g,OmrMeasurePostProcessor.SYMBOL),gap))return true;
        }
        return false;
    }

    /** Extend only the missing upper-left spine of an otherwise semantic glyph.
     * The printed extension must end inside the crop and the completed mask must
     * still prove both natural endpoints and both separated connectors. */
    private static boolean naturalFromUpperSpine(byte[] labels,byte[] gray,int width,int height,
            List<AccidentalCandidate> candidates,Component head,float gap) {
        if(gray==null||gray.length!=width*height)return false;
        for(AccidentalCandidate candidate:candidates) {
            Component c=candidate.component;
            int w=c.maxX-c.minX+1,gh=c.maxY-c.minY+1;
            if(candidate.label!=OmrMeasurePostProcessor.CLEF_OR_KEY
                    ||head.minX-c.maxX<gap*.1f||head.minX-c.maxX>gap*1.35f
                    ||Math.abs(c.centerY-head.centerY)>gap*.9f
                    ||w<gap*.48f||w>gap*1.55f||gh<gap*1.4f||gh>gap*3.2f
                    ||isSharpGlyph(labels,width,height,candidate,gap)
                    ||isFlatGlyph(labels,width,height,candidate,gap))continue;
            int[] columns=new int[w];
            for(int y=c.minY;y<=c.maxY;y++)for(int x=c.minX;x<=c.maxX;x++)
                if(candidate.matches(labels[y*width+x]))columns[x-c.minX]++;
            for(int spine=0;spine<w/2;spine++) {
                if(columns[spine]<gh*.45f)continue;
                int top=Math.max(0,Math.round(c.minY-gap*.85f)),h=c.maxY-top+1;
                byte[] ink=new byte[w*h];
                for(int y=c.minY;y<=c.maxY;y++)for(int x=c.minX;x<=c.maxX;x++)
                    if(candidate.matches(labels[y*width+x]))ink[(y-top)*w+x-c.minX]=OmrMeasurePostProcessor.SYMBOL;
                for(int y=top;y<c.minY;y++)
                    if((gray[y*width+c.minX+spine]&255)<=225)ink[(y-top)*w+spine]=OmrMeasurePostProcessor.SYMBOL;
                Component connected=retainSeedConnectedInk(ink,w,h,c,c.minX,top);
                if(connected==null||connected.minY==0
                        ||c.minY-top-connected.minY<gap*.3f)continue;
                if(isNaturalGlyph(ink,w,h,new AccidentalCandidate(connected,OmrMeasurePostProcessor.SYMBOL),gap))return true;
            }
        }
        return false;
    }

    /** Thin natural spines can be assigned the stem label while their two
     * connectors retain the accidental label. Reconstruct only that narrow
     * printed column and require the natural's asymmetric spine endpoints. */
    private static boolean rawNaturalAtSeed(byte[] gray,int width,int height,
            Component seed,Component head,float gap) {
        return rawNaturalAtSeed(gray,width,height,seed,head,gap,0)
                ||rawNaturalAtSeed(gray,width,height,seed,head,gap,Math.max(1,Math.round(gap*.16f)));
    }

    private static boolean rawNaturalAtSeed(byte[] gray,int width,int height,
            Component seed,Component head,float gap,int margin) {
        // Gray antialiasing can fill the counter at the permissive threshold.
        // A darker core must still prove both offset spines and open crossbars.
        return rawNaturalAtSeed(gray,width,height,seed,head,gap,margin,225)
                ||rawNaturalAtSeed(gray,width,height,seed,head,gap,margin,180);
    }

    private static boolean rawNaturalAtSeed(byte[] gray,int width,int height,
            Component seed,Component head,float gap,int margin,int inkThreshold) {
        int left=Math.max(0,seed.minX-margin);
        int right=Math.min(Math.min(width-1,seed.maxX+margin),Math.round(head.minX-gap*.15f));
        int top=Math.max(0,Math.round(head.centerY-gap*1.8f));
        int bottom=Math.min(height-1,Math.round(head.centerY+gap*1.8f));
        int w=right-left+1,h=bottom-top+1;
        byte[] ink=new byte[w*h];
        int area=0,minX=w,maxX=-1,minY=h,maxY=-1;long sx=0,sy=0;
        int reach=Math.max(3,Math.round(gap*.6f));
        int verticalProbe=Math.max(2,Math.round(gap*.2f));
        for(int y=top;y<=bottom;y++) {
            int outside=0,dark=0;
            for(int x=Math.max(0,left-reach);x<=Math.min(width-1,right+reach);x++)
                if(x<left||x>right){outside++;if((gray[y*width+x]&255)<=inkThreshold)dark++;}
            boolean rule=outside>0&&dark>=outside*.8f;
            for(int x=left;x<=right;x++) {
                if((gray[y*width+x]&255)>inkThreshold)continue;
                // Preserve a vertical spine where a rule crosses it.
                if(rule&&(y<verticalProbe||y+verticalProbe>=height
                        ||(gray[(y-verticalProbe)*width+x]&255)>inkThreshold
                        ||(gray[(y+verticalProbe)*width+x]&255)>inkThreshold))continue;
                int xx=x-left,yy=y-top;ink[yy*w+xx]=OmrMeasurePostProcessor.SYMBOL;
                area++;sx+=xx;sy+=yy;minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);
                minY=Math.min(minY,yy);maxY=Math.max(maxY,yy);
            }
        }
        if(area==0)return false;
        // Disconnected staff or slur ink cannot extend either natural spine.
        // Filter to the seeded accidental before checking its endpoint shape.
        Component glyph=retainSeedConnectedInk(ink,w,h,seed,left,top);
        if(glyph==null)return false;
        // A crop through an annotation can give a flat a false lower-right spine.
        // Recovered natural endpoints must finish inside the inspected column.
        if(rawStrokeLeavesCrop(gray,width,height,ink,w,h,left,top,gap))return false;
        return isNaturalGlyph(ink,w,h,new AccidentalCandidate(glyph,OmrMeasurePostProcessor.SYMBOL),gap);
    }

    /** A nearby disconnected slur must not become an endpoint of the seeded accidental. */
    private static Component retainSeedConnectedInk(byte[] ink,int width,int height,Component seed,int left,int top) {
        boolean[] kept=new boolean[ink.length];int[] queue=new int[ink.length];int tail=0;
        for(int y=Math.max(0,seed.minY-top);y<=Math.min(height-1,seed.maxY-top);y++)
            for(int x=Math.max(0,seed.minX-left);x<=Math.min(width-1,seed.maxX-left);x++) {
                int at=y*width+x;if(ink[at]!=0){kept[at]=true;queue[tail++]=at;}
            }
        for(int head=0;head<tail;head++) {
            int at=queue[head],x=at%width,y=at/width;
            for(int yy=Math.max(0,y-1);yy<=Math.min(height-1,y+1);yy++)
                for(int xx=Math.max(0,x-1);xx<=Math.min(width-1,x+1);xx++) {
                    int next=yy*width+xx;
                    if(ink[next]!=0&&!kept[next]){kept[next]=true;queue[tail++]=next;}
                }
        }
        int area=0,minX=width,maxX=-1,minY=height,maxY=-1;long sx=0,sy=0;
        for(int at=0;at<ink.length;at++) {
            if(!kept[at]){ink[at]=0;continue;}
            int x=at%width,y=at/width;area++;sx+=x;sy+=y;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        return area==0?null:new Component(area,minX,maxX,minY,maxY,sx/(float)area,sy/(float)area);
    }

    private static boolean rawStrokeLeavesCrop(byte[] gray,int width,int height,byte[] ink,
            int w,int h,int left,int top,float gap) {
        return rawStrokeLeavesCropAtThreshold(gray,width,height,ink,w,h,left,top,gap,224);
    }

    private static boolean rawStrokeLeavesCropAtThreshold(byte[] gray,int width,int height,byte[] ink,
            int w,int h,int left,int top,float gap,int threshold) {
        int reach=Math.max(2,Math.round(gap*.16f));
        for(int side:new int[]{-1,1}) {
            int edge=side<0?0:h-1;
            for(int x=0;x<w;x++) {
                if(ink[edge*w+x]==0)continue;
                boolean continues=true;
                for(int d=1;d<=reach;d++) {
                    int y=top+edge+side*d;
                    if(y<0||y>=height||(gray[y*width+left+x]&255)>threshold){continues=false;break;}
                }
                if(continues)return true;
            }
        }
        return false;
    }

    /** A complete sharp already has stronger shape evidence than a cropped raw
     * bowl. Do not reinterpret it as a flat after pitch alignment rejected it. */
    private static List<AccidentalCandidate> withoutRecognizedSharps(byte[] labels,int width,int height,
            List<AccidentalCandidate> candidates,float gap) {
        List<AccidentalCandidate> result=new ArrayList<>();
        for(AccidentalCandidate candidate:candidates)
            if(!isSharpGlyph(labels,width,height,candidate,gap))result.add(candidate);
        return result;
    }

    /** A flat's bowl can keep its accidental label while its tall spine is labelled as a stem. */
    private static boolean rawFlatFromBowl(byte[] gray,int width,int height,
            List<AccidentalCandidate> candidates,Component head,float gap) {
        return recoverFlatFromBowl(gray,width,height,candidates,head,gap,180)
                ||recoverFlatFromBowl(gray,width,height,candidates,head,gap,235);
    }

    /** Recover faded spines only with a semantic bowl and the complete flat shape.
     * Disconnected neighboring marks cannot supply strokes or clip the accidental. */
    private static boolean recoverFlatFromBowl(byte[] gray,int width,int height,
            List<AccidentalCandidate> candidates,Component head,float gap,int threshold) {
        if(gray==null||gray.length!=width*height)return false;
        for(AccidentalCandidate seed:candidates) {
            if(seed.label!=OmrMeasurePostProcessor.CLEF_OR_KEY&&seed.label!=0)continue;
            Component c=seed.component;
            if(c.maxX>=head.minX||head.minX-c.maxX>gap*1.35f
                    ||c.maxX-c.minX+1<gap*.24f||c.maxX-c.minX+1>gap*1.25f
                    ||Math.abs(c.centerY-head.centerY)>gap*.8f)continue;
            int margin=Math.max(1,Math.round(gap*.16f));
            int left=Math.max(0,c.minX-margin),right=Math.min(width-1,Math.min(c.maxX+margin,head.minX-2));
            int top=Math.max(0,Math.round(head.centerY-gap*2.7f)),bottom=Math.min(height-1,Math.round(head.centerY+gap*.8f));
            int w=right-left+1,h=bottom-top+1;if(w<=0||h<=0)continue;
            byte[] ink=new byte[w*h];
            int reach=Math.max(3,Math.round(gap*.6f)),probe=Math.max(2,Math.round(gap*.2f));
            for(int y=top;y<=bottom;y++) {
                int outside=0,dark=0;
                for(int x=Math.max(0,left-reach);x<=Math.min(width-1,right+reach);x++)
                    if(x<left||x>right){outside++;if((gray[y*width+x]&255)<=threshold)dark++;}
                boolean rule=outside>0&&dark>=outside*.8f;
                for(int x=left;x<=right;x++) {
                    if((gray[y*width+x]&255)>threshold)continue;
                    if(rule&&(y<probe||y+probe>=height||(gray[(y-probe)*width+x]&255)>threshold
                            ||(gray[(y+probe)*width+x]&255)>threshold))continue;
                    int xx=x-left,yy=y-top;ink[yy*w+xx]=OmrMeasurePostProcessor.SYMBOL;
                }
            }
            // An arpeggio arrow or another tall mark can resemble a flat when clipped.
            // Its printed stroke must finish inside the inspected column.
            Component connected=retainSeedConnectedInk(ink,w,h,c,left,top);
            if(connected==null||rawStrokeLeavesCropAtThreshold(gray,width,height,ink,w,h,left,top,gap,
                    Math.max(224,threshold)))continue;
            var glyph=new AccidentalCandidate(connected,OmrMeasurePostProcessor.SYMBOL);
            if(!isNaturalGlyph(ink,w,h,glyph,gap)&&!isSharpGlyph(ink,w,h,glyph,gap)
                    &&isFlatGlyph(ink,w,h,glyph,gap)&&Math.abs(top+flatPitchCenter(ink,w,glyph,gap)-head.centerY)<=gap*.45f)
                return true;
        }
        return false;
    }

    /** Raster rounding can make a beamed grace group nearly one staff space tall.
     * Only admit these larger candidates with shortened stems; the caller also
     * requires a group and a substantially larger principal for every member. */
    private static boolean roundedBeamedGraceHead(DetectedNote n,byte[] gray,int width,int height) {
        if(n.head.maxX-n.head.minX+1>Math.round(n.staffGap*1.10f)
                ||n.head.maxY-n.head.minY+1>Math.round(n.staffGap)
                ||n.head.area>n.staffGap*n.staffGap*.80f||n.event.augmentationDots()!=0
                ||n.event.beamCount()<1||n.event.unbeamedDurationBeats()>=ScoreNoteEvent.DURATION_HALF)return false;
        int[] stem=attachedRawStem(gray,width,height,n.head,n.staffGap*.65f);
        return stem!=null&&Math.abs(stem[1]-n.head.centerY)<=n.staffGap*3.1f;
    }

    /** Returns a local accidental immediately left of this head, or key-signature fallback. */
    /** Separate touching chord accidentals only when both pieces explain distinct chord heads. */
    private static List<AccidentalCandidate> splitTouchingChordAccidentals(byte[] labels,
            int width,int height,List<AccidentalCandidate> candidates,List<Component> heads,List<Staff> staffs) {
        List<AccidentalCandidate> result=new ArrayList<>(candidates);
        for(AccidentalCandidate candidate:candidates) {
            Component box=candidate.component;
            Staff staff=nearestHeadStaff(staffs,box.centerY);
            if(staff==null)continue;
            float gap=staff.gap;
            int glyphWidth=box.maxX-box.minX+1,glyphHeight=box.maxY-box.minY+1;
            if(glyphWidth<gap*1.65f||glyphWidth>gap*3.8f
                    ||glyphHeight<gap*2f||glyphHeight>gap*5.5f
                    ||isNaturalGlyph(labels,width,height,candidate,gap)
                    ||isSharpGlyph(labels,width,height,candidate,gap)
                    ||isFlatGlyph(labels,width,height,candidate,gap))continue;
            AccidentalCandidate bestLeft=null,bestRight=null;
            int bestBridges=Integer.MAX_VALUE,bestArea=0;
            int margin=Math.max(2,(int)Math.ceil(gap*.45f));
            for(int cut=box.minX+margin;cut<box.maxX-margin;cut++) {
                AccidentalCandidate left=signatureSlice(labels,width,candidate,box.minX,cut);
                AccidentalCandidate right=signatureSlice(labels,width,candidate,cut+1,box.maxX);
                if(left==null||right==null)continue;
                int retained=left.component.area+right.component.area;
                if(retained<box.area*.90f)continue;
                Component leftHead=chordAccidentalHead(labels,width,height,left,heads,staffs,staff);
                if(leftHead==null)continue;
                Component rightHead=chordAccidentalHead(labels,width,height,right,heads,staffs,staff);
                if(rightHead==null||leftHead==rightHead
                        ||Math.abs(leftHead.centerX-rightHead.centerX)>gap*.70f
                        ||Math.abs(leftHead.centerY-rightHead.centerY)<gap*.45f)continue;
                int bridges=0;
                for(int y=box.minY;y<=box.maxY;y++)
                    if(candidate.matches(labels[y*width+cut])
                            &&candidate.matches(labels[y*width+cut+1]))bridges++;
                if(bridges<bestBridges||(bridges==bestBridges&&retained>bestArea)) {
                    bestBridges=bridges;bestArea=retained;bestLeft=left;bestRight=right;
                }
            }
            if(bestLeft!=null){result.add(bestLeft);result.add(bestRight);}
        }
        return result;
    }

    private static Component chordAccidentalHead(byte[] labels,int width,int height,
            AccidentalCandidate candidate,List<Component> heads,List<Staff> staffs,Staff staff) {
        Component best=null;float distance=Float.MAX_VALUE;
        for(Component head:heads) {
            if(nearestHeadStaff(staffs,head.centerY)!=staff)continue;
            if(detectWrittenAccidental(labels,width,height,List.of(candidate),head,staff.gap)
                    ==ScoreNoteEvent.ACCIDENTAL_FROM_KEY)continue;
            float dx=head.centerX-candidate.component.centerX;
            if(dx<distance){distance=dx;best=head;}
        }
        return best;
    }


    private static int detectWrittenAccidental(byte[] labels, int width, int height,
                                               List<AccidentalCandidate> candidates,
                                               Component head, float gap) {
        AccidentalCandidate best = null;
        int bestAccidental = ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
        float bestDistance = Float.MAX_VALUE;
        for (AccidentalCandidate candidate : candidates) {
            Component glyph = candidate.component;
            float horizontal = head.minX - glyph.maxX;
            if (horizontal < -gap * .35f || horizontal > gap * 1.35f
                    ||head.centerX-glyph.centerX<gap*.65f) continue;
            if (Math.abs(glyph.centerY - head.centerY) > gap * 1.8f) continue;
            float sharpCenter=sharpPitchCenter(labels,width,height,candidate,gap);
            int accidental = isNaturalGlyph(labels, width, height, candidate, gap)
                    ? ScoreNoteEvent.ACCIDENTAL_NATURAL
                    : Float.isFinite(sharpCenter)
                    ? ScoreNoteEvent.ACCIDENTAL_SHARP
                    : isFlatGlyph(labels, width, height, candidate, gap)
                    ? ScoreNoteEvent.ACCIDENTAL_FLAT
                    : ScoreNoteEvent.ACCIDENTAL_FROM_KEY;
            if (accidental == ScoreNoteEvent.ACCIDENTAL_FROM_KEY) continue;
            // A sharp belongs at the centre of its crossbars. Extra staff ink can
            // shift its pixel centroid toward another head in the same chord.
            float pitchCenter=accidental==ScoreNoteEvent.ACCIDENTAL_SHARP?sharpCenter:
                    accidental==ScoreNoteEvent.ACCIDENTAL_FLAT?flatPitchCenter(labels,width,candidate,gap):glyph.centerY;
            // A sharp's measured crossbar centre must not reach the next staff
            // position. Keep subpixel/font tolerance below the half-gap step.
            float tolerance=accidental==ScoreNoteEvent.ACCIDENTAL_NATURAL?.90f:
                    accidental==ScoreNoteEvent.ACCIDENTAL_SHARP?.45f:.65f;
            if(Math.abs(pitchCenter-head.centerY)>gap*tolerance)continue;
            if (horizontal < bestDistance) {
                best = candidate;
                bestAccidental = accidental;
                bestDistance = horizontal;
            }
        }
        return best == null ? ScoreNoteEvent.ACCIDENTAL_FROM_KEY : bestAccidental;
    }

    /**
     * A flat has one tall left spine and a lower-right bowl. Naturals have a second upper-right
     * stem, while sharps are much wider; comparing the glyph's own semantic pixels avoids staff
     * lines and artwork texture in the grayscale page.
     */
    private static boolean isFlatGlyph(byte[] labels, int width, int height,
                                       AccidentalCandidate candidate, float gap) {
        Component glyph = candidate.component;
        int glyphWidth = glyph.maxX - glyph.minX + 1;
        int glyphHeight = glyph.maxY - glyph.minY + 1;
        if (glyphHeight < gap * 1.20f || glyphHeight > gap * 3.35f
                || glyphWidth < gap * .24f || glyphWidth > gap * 1.75f
                || glyphHeight < glyphWidth * 1.28f
                || glyph.area < gap * gap * .16f || glyph.area > gap * gap * 1.65f)
            return false;
        int[] columns = new int[glyphWidth];
        int spine = 0;
        for (int y = Math.max(0, glyph.minY); y <= Math.min(height - 1, glyph.maxY); y++)
            for (int x = Math.max(0, glyph.minX); x <= Math.min(width - 1, glyph.maxX); x++)
                if (candidate.matches(labels[y * width + x])) columns[x - glyph.minX]++;
        for (int column = 1; column < columns.length; column++)
            if (columns[column] > columns[spine]) spine = column;
        // A slur tail or grace flag bends rightward without a sustained left spine.
        if (spine > Math.round((glyphWidth - 1) * .48f)
                || columns[spine] < glyphHeight * .68f) return false;
        int splitY = glyph.minY + Math.round(glyphHeight * .45f);
        int upperEnd=glyph.minY+Math.max(1,Math.round(glyphHeight*.25f));
        int[] upperReach=new int[upperEnd-glyph.minY];
        for(int y=glyph.minY;y<upperEnd;y++)for(int x=glyph.minX+spine;x<=glyph.maxX;x++)
            if(candidate.matches(labels[y*width+x]))upperReach[y-glyph.minY]=x;
        java.util.Arrays.sort(upperReach);
        int upperEdge=upperReach[upperReach.length/2],expandedRows=0;
        for(int y=splitY;y<=glyph.maxY;y++)for(int x=Math.max(glyph.minX,upperEdge+Math.max(2,Math.round(gap*.2f)));x<=glyph.maxX;x++)
            if(candidate.matches(labels[y*width+x])){expandedRows++;break;}
        // A cut note stem can widen by a pixel at a staff crossing. A flat has
        // a distinct bowl projecting beyond the width of its upper stem.
        if(expandedRows<Math.max(2,Math.round(glyphHeight*.1f)))return false;
        int rightStart = glyph.minX + spine + Math.max(1, Math.round(glyphWidth * .16f));
        int upperRight = 0, lowerRight = 0, wideLowerRows = 0, bowlRows = 0;
        for (int y = glyph.minY; y <= glyph.maxY; y++) {
            int rowMin = glyph.maxX + 1, rowMax = glyph.minX - 1;
            for (int x = Math.max(glyph.minX, rightStart); x <= glyph.maxX; x++) {
                if (x < 0 || x >= width || y < 0 || y >= height
                        || !candidate.matches(labels[y * width + x])) continue;
                if (y < splitY) upperRight++; else lowerRight++;
                rowMin = Math.min(rowMin, x); rowMax = Math.max(rowMax, x);
            }
            if (y >= splitY && rowMax >= rowMin
                    && rowMax - rowMin + 1 >= glyphWidth * .42f) wideLowerRows++;
            // Narrow engraved flats have a hollow bowl: its curved outer edge can be just
            // one pixel wide on a row. Measure its reach from the spine as well as its ink.
            if (y >= splitY && rowMax - (glyph.minX + spine)
                    >= Math.max(gap * .30f, glyphWidth * .55f)) bowlRows++;
        }
        return lowerRight >= upperRight + Math.max(2, Math.round(gap * .16f))
                && lowerRight >= glyph.area * .13f
                && (wideLowerRows >= Math.max(2, Math.round(glyphHeight * .10f))
                    || bowlRows >= Math.max(3, Math.round(glyphHeight * .16f)));
    }

    /** A flat changes the note beside its bowl, not the note beside its tall spine. */
    private static float flatPitchCenter(byte[] labels,int width,AccidentalCandidate candidate,float gap) {
        Component glyph=candidate.component;
        int glyphWidth=glyph.maxX-glyph.minX+1,glyphHeight=glyph.maxY-glyph.minY+1;
        int[] columns=new int[glyphWidth];
        for(int y=glyph.minY;y<=glyph.maxY;y++)for(int x=glyph.minX;x<=glyph.maxX;x++)
            if(candidate.matches(labels[y*width+x]))columns[x-glyph.minX]++;
        int spine=0;
        for(int x=1;x<glyphWidth;x++)if(columns[x]>columns[spine])spine=x;
        int start=glyph.minX+spine+(int)Math.ceil(Math.max(gap*.30f,glyphWidth*.55f));
        int first=-1,last=-1;
        for(int y=glyph.minY+Math.round(glyphHeight*.45f);y<=glyph.maxY;y++)
            for(int x=start;x<=glyph.maxX;x++)if(candidate.matches(labels[y*width+x])) {
                if(first<0)first=y;last=y;break;
            }
        return first<0?glyph.minY+glyphHeight*.75f:(first+last)*.5f;
    }

    /**
     * A natural has two offset vertical spines: the left one extends above the right, while the
     * right one extends below the left. Two separated connectors join them. Checking those
     * asymmetric endpoints before flat/sharp classification keeps a natural from looking like a
     * flat's lower bowl or a sharp with unusually thin crossbars.
     */
    private static boolean isNaturalGlyph(byte[] labels, int width, int height,
                                          AccidentalCandidate candidate, float gap) {
        Component glyph = candidate.component;
        int glyphWidth = glyph.maxX - glyph.minX + 1;
        int glyphHeight = glyph.maxY - glyph.minY + 1;
        if (glyphHeight < gap * 1.40f || glyphHeight > gap * 3.70f
                || glyphWidth < gap * .48f || glyphWidth > gap * 1.55f
                || glyphHeight < glyphWidth * 1.45f
                || glyphHeight > glyphWidth * 4.50f
                || glyph.area < gap * gap * .30f || glyph.area > gap * gap * 2.30f)
            return false;

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
        int leftTop = glyphHeight, leftBottom = -1, rightTop = glyphHeight, rightBottom = -1;
        int leftRows=0,rightRows=0;
        for (int row = 0; row < glyphHeight; row++) {
            boolean leftInk=false,rightInk=false;
            for (int column = Math.max(0, leftSpine - radius);
                 column <= Math.min(glyphWidth - 1, leftSpine + radius); column++)
                if (candidate.matches(labels[(glyph.minY + row) * width + glyph.minX + column])) {
                    leftInk=true;leftTop = Math.min(leftTop, row); leftBottom = Math.max(leftBottom, row);
                }
            for (int column = Math.max(0, rightSpine - radius);
                 column <= Math.min(glyphWidth - 1, rightSpine + radius); column++)
                if (candidate.matches(labels[(glyph.minY + row) * width + glyph.minX + column])) {
                    rightInk=true;rightTop = Math.min(rightTop, row); rightBottom = Math.max(rightBottom, row);
                }
            if(leftInk)leftRows++;if(rightInk)rightRows++;
        }
        // Separate fragments cannot lengthen a spine across mostly empty space.
        // Allow short scan breaks while requiring ink along each offset stem.
        if(leftRows<(leftBottom-leftTop+1)*.65f
                ||rightRows<(rightBottom-rightTop+1)*.65f)return false;
        int endpointOffset = Math.max(1, Math.round(glyphHeight * .05f));
        if (rightTop - leftTop < endpointOffset
                || rightBottom - leftBottom < endpointOffset) return false;

        // A flag can have two offset sides, but only one diagonal connector.
        // Require two actual ink bridges separated by an open counter.
        int innerLeft=leftSpine+radius+1,innerRight=rightSpine-radius-1;
        if(innerLeft>innerRight)return false;
        int span=innerRight-innerLeft+1;
        for(float slope:new float[]{0,-.3f,-.6f,.3f,.6f}) {
            int firstConnector=-1,openRows=0;
            for(int row=0;row<glyphHeight;row++) {
                int bridge=0;
                for(int col=innerLeft;col<=innerRight;col++) {
                    int y=row+Math.round((col-leftSpine)*slope);
                    if(y>=0&&y<glyphHeight&&candidate.matches(labels[(glyph.minY+y)*width+glyph.minX+col]))bridge++;
                }
                boolean connected=bridge>=Math.max(1,(int)Math.ceil(span*.75f));
                if(connected) {
                    if(firstConnector<0)firstConnector=row;
                    if(openRows>=Math.max(2,Math.round(gap*.15f))
                            &&row-firstConnector>=gap*.45f)return true;
                    openRows=0;
                } else if(firstConnector>=0)openRows++;
            }
        }
        return false;
    }

    /** A sharp has two full-height vertical spines crossed by two separated wide strokes. */
    private static boolean isSharpGlyph(byte[] labels,int width,int height,
                                        AccidentalCandidate candidate,float gap) {
        return Float.isFinite(sharpPitchCenter(labels,width,height,candidate,gap));
    }

    private static float sharpPitchCenter(byte[] labels, int width, int height,
                                        AccidentalCandidate candidate, float gap) {
        Component glyph = candidate.component;
        int glyphWidth = glyph.maxX - glyph.minX + 1;
        int glyphHeight = glyph.maxY - glyph.minY + 1;
        if (glyphHeight < gap * 1.55f || glyphHeight > gap * 3.65f
                || glyphWidth < gap * .65f || glyphWidth > gap * 1.80f
                || glyphHeight < glyphWidth * 1.45f
                || glyphHeight > glyphWidth * 4.50f
                || glyph.area < gap * gap * .42f || glyph.area > gap * gap * 2.45f)
            return Float.NaN;

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
        int coreLeft=0,coreRight=glyphWidth-1;
        while(coreLeft<coreRight&&columns[coreLeft]<glyphHeight*.30f)coreLeft++;
        while(coreRight>coreLeft&&columns[coreRight]<glyphHeight*.30f)coreRight--;
        if (rightSpine - leftSpine < (coreRight-coreLeft+1) * .28f
                || columns[leftSpine] < glyphHeight * .46f
                || columns[rightSpine] < glyphHeight * .46f) return Float.NaN;

        // Two thick but disconnected spines are not a crossbar.
        // Require ink to bridge their inner gap before considering row width.
        for (int row = 0; row < glyphHeight; row++) {
            int bridge = 0;
            for (int col = leftSpine; col <= rightSpine; col++)
                if (candidate.matches(labels[(glyph.minY + row) * width + glyph.minX + col])) bridge++;
            if (bridge < (rightSpine - leftSpine + 1) * .70f) rows[row] = 0;
        }
        // Measure crossbars against the two spines, not stray ink at the glyph edge.
        int wideThreshold = Math.max(2, Math.round((rightSpine-leftSpine+1) * 1.15f));
        List<int[]> crossbars=new ArrayList<>();
        int terminalMargin=Math.max(2,Math.round(glyphHeight*.10f));
        for (int row = terminalMargin; row < rows.length-terminalMargin;) {
            if(rows[row]<wideThreshold){row++;continue;}
            int first=row;
            while(row<rows.length-terminalMargin&&rows[row]>=wideThreshold)row++;
            // A painted staff stripe at a spine tip is not a sharp crossbar.
            if(row-first<Math.max(2,Math.round(gap*.12f)))continue;
            crossbars.add(new int[]{first,row-1});
        }
        float bestCenter=Float.NaN;int bestSupport=-1;
        for(int i=0;i<crossbars.size();i++)pair:for(int j=i+1;j<crossbars.size();j++) {
        int[] firstBand=crossbars.get(i),lastBand=crossbars.get(j);
        int firstCrossbar=firstBand[0],lastCrossbar=lastBand[1];
        if(lastCrossbar-firstCrossbar<glyphHeight*.18f)continue;
        // Both sharp spines protrude through both crossbars. A flat's bowl can
        // supply a long second column, but cannot supply its upper extension.
        int radius = Math.max(1, Math.round(glyphWidth * .09f));
        int required = Math.max(1, Math.round(glyphHeight * .05f));
        int firstTop = -1, firstBottom = -1;
        for (int spine : new int[]{leftSpine, rightSpine}) {
            int above = 0, below = 0, top = -1, bottom = -1;
            for (int row = 0; row < glyphHeight; row++) {
                boolean ink = false;
                for (int col = Math.max(0, spine - radius); col <= Math.min(glyphWidth - 1, spine + radius); col++)
                    if (candidate.matches(labels[(glyph.minY + row) * width + glyph.minX + col])) ink = true;
                if (ink) { if (top < 0) top = row; bottom = row; }
                if (ink && row < firstCrossbar) above++;
                if (ink && row > lastCrossbar) below++;
            }
            if (above < required || below < required) continue pair;
            if (firstTop < 0) { firstTop = top; firstBottom = bottom; }
            else if (Math.abs(top - firstTop) > glyphHeight * .22f
                    || Math.abs(bottom - firstBottom) > glyphHeight * .22f) continue pair;
        }
        // A hairpin or staff stripe can add a third short band near a tip.
        // Prefer the two substantial crossbars with support on both spines.
        int firstSize=firstBand[1]-firstBand[0]+1,lastSize=lastBand[1]-lastBand[0]+1;
        int support=Math.min(firstSize,lastSize)*100+firstSize+lastSize;
        if(support>bestSupport){bestSupport=support;bestCenter=glyph.minY+(firstCrossbar+lastCrossbar)*.5f;}
        }
        return bestCenter;
    }

    /** Uses the staff line beside the note instead of the page-wide average on skewed scans. */
    /** Two neighboring printed lines can correct subpixel error accumulated above/below a staff. */
    private static float printedLedgerBottom(byte[] gray,int width,int height,Component head,float bottom,float gap) {
        if(gray==null||gap<8)return bottom;
        float step=(bottom-head.centerY)/(gap*.5f);
        if(step<9.5f&&step> -1.5f)return bottom;
        int ledger=Math.round(step*.5f)*2;
        if(ledger>0&&ledger<10||ledger<0&&ledger> -2)return bottom;
        int adjacent=ledger+(ledger>0?-2:2);
        float first=printedLedgerLine(gray,width,height,head,bottom-ledger*gap*.5f,gap,true);
        float second=printedLedgerLine(gray,width,height,head,bottom-adjacent*gap*.5f,gap,adjacent>8||adjacent<0);
        if(!Float.isFinite(first)||!Float.isFinite(second)||Math.abs(Math.abs(first-second)-gap)>gap*.12f)return bottom;
        float a=first+ledger*gap*.5f,b=second+adjacent*gap*.5f;
        if(Math.abs(a-bottom)>gap*.2f||Math.abs(b-bottom)>gap*.2f)return bottom;
        return (a+b)*.5f;
    }

    private static float printedLedgerLine(byte[] gray,int width,int height,Component head,float expected,float gap,boolean bounded) {
        int reach=Math.max(3,Math.round(gap*.55f)),range=Math.max(2,Math.round(gap*.25f));
        int flank=Math.max(2,Math.round(gap*.3f));float[] centers=new float[2];
        for(int side=0;side<2;side++) {
            int left=side==0?head.minX-reach:head.maxX+1,right=side==0?head.minX-1:head.maxX+reach;
            if(left<0||right>=width)return Float.NaN;
            int first=Math.max(flank,Math.round(expected)-range),last=Math.min(height-1-flank,Math.round(expected)+range);
            double weighted=0,weight=0;int peak=0,peakY=-1;
            int[] strengths=new int[Math.max(0,last-first+1)];
            for(int y=first;y<=last;y++) {
                int support=0;
                for(int x=left;x<=right;x++) {
                    int ink=gray[y*width+x]&255;
                    if(ink<180&&(gray[(y-flank)*width+x]&255)>ink+20&&(gray[(y+flank)*width+x]&255)>ink+20)support++;
                }
                strengths[y-first]=support;if(support>peak){peak=support;peakY=y;}
            }
            if(peak<Math.max(2,Math.round(gap*.16f)))return Float.NaN;
            for(int y=first;y<=last;y++)if(Math.abs(y-peakY)<=gap*.15f&&strengths[y-first]>=peak*.65f) {
                weighted+=y*strengths[y-first];weight+=strengths[y-first];
            }
            if(weight==0)return Float.NaN;centers[side]=(float)(weighted/weight);
        }
        if(Math.abs(centers[0]-centers[1])>gap*.1f)return Float.NaN;
        float center=(centers[0]+centers[1])*.5f;
        // A ledger ends near its head; an extended beam cannot establish this reference.
        if(bounded)for(int x:new int[]{head.minX-Math.round(gap),head.maxX+Math.round(gap)}) {
            int continued=0;
            for(int xx=Math.max(0,x-2);xx<=Math.min(width-1,x+2);xx++) {
                boolean ink=false;
                for(int y=Math.max(flank,Math.round(center)-1);y<=Math.min(height-1-flank,Math.round(center)+1);y++) {
                    int value=gray[y*width+xx]&255;
                    if(value<180&&(gray[(y-flank)*width+xx]&255)>value+20&&(gray[(y+flank)*width+xx]&255)>value+20)ink=true;
                }
                if(ink)continued++;
            }
            if(continued>=2)return Float.NaN;
        }
        return center;
    }

    /** Near a line/space boundary, measure a compact filled head in the printed image.
     * Thin staff/stem ink is removed before centering; three thresholds must agree. */
    private static int printedPitchStep(byte[] gray,int width,int height,Component head,float bottom,float gap) {
        float position=(bottom-head.centerY)/(gap*.5f);
        int original=Math.round(position);
        if(gray==null||Math.abs(position-(float)Math.floor(position)-.5f)>.12f||gap<8)return original;
        int cx=Math.round(head.centerX),cy=Math.round(head.centerY);
        if((gray[cy*width+cx]&255)>140)return original;
        int radius=Math.round(gap*1.15f),left=cx-radius,top=cy-radius,size=radius*2+1;
        if(left<0||top<0||left+size>width||top+size>height)return original;
        int rx=Math.max(2,Math.round(gap*.20f)),ry=Math.max(1,Math.round(gap*.125f));
        List<int[]> kernel=new ArrayList<>();
        for(int dy=-ry;dy<=ry;dy++) {
            int extent=Math.round(rx*(float)Math.sqrt(1-dy*dy/(float)(ry*ry)));
            for(int dx=-extent;dx<=extent;dx++)kernel.add(new int[]{dx,dy});
        }
        int agreed=Integer.MIN_VALUE;float minY=Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
        for(int threshold:new int[]{60,100,140}) {
            boolean[] opened=new boolean[size*size];
            for(int y=ry;y<size-ry;y++)for(int x=rx;x<size-rx;x++) {
                boolean solid=true;
                for(int[] k:kernel)if((gray[(top+y+k[1])*width+left+x+k[0]]&255)>=threshold){solid=false;break;}
                if(solid)for(int[] k:kernel)opened[(y+k[1])*size+x+k[0]]=true;
            }
            int seed=radius*size+radius;
            if(!opened[seed])return original;
            int[] queue=new int[size*size];int count=1,read=0;queue[0]=seed;opened[seed]=false;
            long sumY=0,sumX=0;int minX=size,maxX=0,minRow=size,maxRow=0;
            while(read<count) {
                int at=queue[read++],y=at/size,x=at%size;sumY+=y;sumX+=x;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minRow=Math.min(minRow,y);maxRow=Math.max(maxRow,y);
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int xx=x+dx,yy=y+dy;if(xx<0||xx>=size||yy<0||yy>=size)continue;
                    int next=yy*size+xx;if(opened[next]){opened[next]=false;queue[count++]=next;}
                }
            }
            float y=top+sumY/(float)count,x=left+sumX/(float)count;
            if(count<gap*gap*.35f||count>gap*gap*1.7f||maxX-minX<gap*.65f||maxX-minX>gap*1.8f
                    ||maxRow-minRow<gap*.4f||maxRow-minRow>gap*1.3f
                    ||Math.abs(x-head.centerX)>gap*.2f||Math.abs(y-head.centerY)>gap*.15f)return original;
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            int pitch=Math.round((bottom-y)/(gap*.5f));
            if(pitch==original)return original;
            if(agreed!=Integer.MIN_VALUE&&agreed!=pitch)return original;agreed=pitch;
        }
        return maxY-minY<=gap*.04f?agreed:original;
    }

    private static float[] localStaffPitch(byte[] labels, byte[] gray, int width, int height, Staff staff,
                                         Component head) {
        float gap=staff.pitchGap, referenceBottom=staff.pitchBottom+staff.pitchSlope*(head.centerX-width*.5f);
        if(staff.pitchTrack!=null){float[] local=staff.pitchTrack.at(head.centerX);referenceBottom=local[0];gap=local[1];}
        boolean shaded=StaffPitchTrack.needsContrast(gray,width,height,head.centerX,referenceBottom,gap);
        float[] complete=shaded
                ?StaffPitchTrack.localRules(labels,gray,width,height,head.centerX,head.minX,head.maxX,referenceBottom,gap,staff.pitchTrack!=null)
                :StaffPitchTrack.localPrintedRules(labels,gray,width,height,head.centerX,head.minX,head.maxX,referenceBottom,gap);
        if(complete==null)complete=StaffPitchTrack.localOccludedRules(labels,gray,width,height,head.centerX,
                head.minX,head.maxX,referenceBottom,gap);
        if(complete==null)complete=StaffPitchTrack.localFadedRules(labels,gray,width,height,head.centerX,
                head.minX,head.maxX,referenceBottom,gap);
        if(complete!=null&&(!staff.printedPhase||Math.abs(complete[0]-referenceBottom)<gap*.5f))return complete;
        int radius = Math.max(4, Math.round(gap * 3.5f));
        int left = Math.max(0, Math.round(head.centerX) - radius);
        int right = Math.min(width - 1, Math.round(head.centerX) + radius);
        int top = Math.max(0, Math.round(referenceBottom - gap * .58f));
        int bottom = Math.min(height - 1, Math.round(referenceBottom + gap * .58f));
        int exclusion = Math.max(1, Math.round(gap * .45f));
        if(gray!=null) {
            // A beam can merge with one rule and leave another thin edge half a
            // space away. Require agreement from several of the five printed
            // rules before using that edge as a local pitch reference.
            // If dark ink is too sparse, require four matching light-ink rules.
            for(float support:new float[]{.70f,.40f}) {
                List<Float> offsets = new ArrayList<>();
                List<float[]> rules=new ArrayList<>();
                for (int line=0;line<5;line++) {
                    float reference=referenceBottom-line*gap;
                    int first=Math.max(0,Math.round(reference-gap*.58f));
                    int last=Math.min(height-1,Math.round(reference+gap*.58f));
                    float closest=Float.NaN,distance=Float.MAX_VALUE;int start=-1;
                    for(int y=first;y<=last+1;y++) {
                        int dark=0,samples=0;
                        if(y<=last)for(int x=left;x<=right;x++) {
                            if(x>=head.minX-exclusion&&x<=head.maxX+exclusion)continue;
                            samples++;int ink=gray[y*width+x]&255,flank=Math.max(2,Math.round(gap*.32f));
                            if(ink<=(support<.7f?205:165)&&(!shaded||(y>=flank&&y+flank<height
                                    &&(gray[(y-flank)*width+x]&255)>=ink+12
                                    &&(gray[(y+flank)*width+x]&255)>=ink+12)))dark++;
                        }
                        boolean rule=samples>=8&&dark>=samples*support;
                        if(rule&&start<0)start=y;
                        if(!rule&&start>=0) {
                            float offset=(start+y-1)*.5f-reference;
                            if(y-start<=Math.max(3,gap*.38f)&&Math.abs(offset)<distance) {
                                closest=offset;distance=Math.abs(offset);
                            }
                            start=-1;
                        }
                    }
                    if(Float.isFinite(closest)){offsets.add(closest);rules.add(new float[]{line,reference+closest});}
                }
                // Derive spacing locally as well as the bottom rule. Semantic
                // stripes can contract on a skewed scan; using that contracted gap
                // still moves ledger pitches even after the bottom rule is corrected.
                if(rules.size()>=4) {
                    List<Float> slopes=new ArrayList<>();
                    for(float[] a:rules)for(float[] b:rules)if(b[0]>a[0])
                        slopes.add((a[1]-b[1])/(b[0]-a[0]));
                    slopes.sort(Float::compare);float localGap=slopes.get(slopes.size()/2);
                    List<Float> bottoms=new ArrayList<>();
                    for(float[] rule:rules)bottoms.add(rule[1]+rule[0]*localGap);
                    bottoms.sort(Float::compare);float localBottom=bottoms.get(bottoms.size()/2);
                    int consistent=0;for(float value:bottoms)if(Math.abs(value-localBottom)<=gap*.12f)consistent++;
                    if(consistent>=4&&localGap>=gap*.88f&&localGap<=gap*1.12f)
                        return new float[]{localBottom,localGap};
                }
                List<Float> agreed=List.of();float bestDistance=Float.MAX_VALUE;
                for(float candidate:offsets) {
                    List<Float> cluster=new ArrayList<>();
                    for(float offset:offsets)if(Math.abs(offset-candidate)<=gap*.18f)cluster.add(offset);
                    float distance=Math.abs(candidate);
                    if(cluster.size()>agreed.size()||cluster.size()==agreed.size()&&distance<bestDistance) {
                        agreed=cluster;bestDistance=distance;
                    }
                }
                if(agreed.size()>=(support<.7f?4:3)) {
                    agreed.sort(Float::compare);
                    return new float[]{referenceBottom+agreed.get(agreed.size()/2),gap};
                }
            }
            // Faded rules may not support a local correction. A single beam
            // edge or semantic smear is weaker evidence than the page's five-
            // rule reference, especially for ledger notes. Keep that reference.
            return new float[]{referenceBottom,gap};
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
        return new float[]{pixels >= Math.max(4, radius / 3) ? weightedY / (float) pixels : referenceBottom,gap};
    }

    /** Augmentation dots are small, aligned components to the right of a real notehead. */
    /** The round bulbs of a recognized rest belong to that rest, even if a staff
     * crossing separates their darkest pixels into a dot-shaped island. */
    private static int dotsOutsideRests(List<Component> candidates, DetectedNote note,
            List<ScoreRestEvent> rests, List<MeasureRegion> measures, byte[] gray, int width, int height,
            List<Component> accidentalInk) {
        if(note.event.augmentationDots()==0 || rests.isEmpty())return note.event.augmentationDots();
        List<Component> excluded=new ArrayList<>(accidentalInk);
        for(ScoreRestEvent rest:rests)if(rest.measureIndex()==note.event.measureIndex()
                &&rest.staffIndex()==note.event.staffIndex()&&rest.staffCount()==note.event.staffCount()) {
            MeasureRegion bar=measures.get(rest.measureIndex());
            float x=(bar.left()+rest.positionInMeasure()*(bar.right()-bar.left()))*width;
            float y=rest.pageY()*height,half=rest.pageHeight()*height*.5f;
            excluded.add(new Component(1,Math.round(x-note.staffGap*.65f),Math.round(x+note.staffGap*.65f),
                    Math.round(y-half),Math.round(y+half),x,y));
        }
        return excluded.isEmpty()?note.event.augmentationDots():countAugmentationDots(candidates,note.head,
                note.staffGap,gray,width,height,note.event.unbeamedDurationBeats()>=ScoreNoteEvent.DURATION_HALF,excluded);
    }

    private static int countAugmentationDots(List<Component> candidates, Component head, float gap,
                                             byte[] gray, int width, int height,boolean hollowHead) {
        return countAugmentationDots(candidates, head, gap, gray, width, height, hollowHead, List.of());
    }

    private static int countAugmentationDots(List<Component> candidates, Component head, float gap,
                                             byte[] gray, int width, int height,boolean hollowHead, List<Component> excluded) {
        // Semantic boundaries can cut a tiny round island out of a slur, stem
        // or ledger line. When the page is available, require an isolated raw
        // ink component; the model's artificial boundary is not a printed dot.
        List<Component> combined = gray != null && gray.length == width * height
                ? findDarkDotComponents(gray, width, height, head, gap)
                : new ArrayList<>(candidates);
        // Antialiasing can join a round dot to a nearby tie. Its dark core remains
        // separate; retain the same bounds, shape and engraving-slot checks below.
        if (gray != null && gray.length == width * height)
            for (Component core : findDarkDotComponents(gray, width, height, head, gap, 70))
                if (core.area >= gap*gap*.06f && core.maxX-core.minX+1 >= gap*.22f
                        && core.maxY-core.minY+1 >= gap*.22f) combined.add(core);
        List<Component> aligned = new ArrayList<>();
        for (Component dot : combined) {
            if (excluded.stream().anyMatch(c->dot.centerX>=c.minX&&dot.centerX<=c.maxX
                    &&dot.centerY>=c.minY&&dot.centerY<=c.maxY)) continue;
            float dotWidth = dot.maxX - dot.minX + 1f;
            float dotHeight = dot.maxY - dot.minY + 1f;
            float horizontal = dot.centerX - head.maxX;
            if (horizontal < gap * .12f || horizontal > gap * 2.2f) continue;
            // Raw antialiasing often leaves a 2-4 px island just beyond the semantic oval.
            // A compact engraving slot can fall just inside one staff gap. Require a
            // separated round body there; tiny edge islands remain part of the head.
            if (dot.centerX - head.centerX < gap
                    && (gray == null || dot.centerX - head.centerX < gap * .85f
                    || dot.minX - head.maxX < gap * .18f
                    || Math.min(dotWidth, dotHeight) < gap * .22f
                    || dot.area < gap * gap * .045f
                    || dot.area >= dotWidth * dotHeight)) continue;
            // Augmentation dots sit beside the head (with at most the usual line-to-space
            // engraving offset). A detached bowing/staccato mark near the next note is not a dot.
            if (Math.abs(dot.centerY - head.centerY) > gap * .65f) continue;
            if (dotWidth < Math.max(1f, gap * .10f) || dotHeight < Math.max(1f, gap * .10f)
                    || dotWidth > gap * .68f || dotHeight > gap * .68f
                    || dot.area < Math.max(1, Math.round(gap * gap * .018f))
                    // Run's dotted half has a round 8x8, 52-pixel dot at a 13.75-pixel staff gap.
                    // Allow that slightly heavier ink only beside a verified hollow head.
                    || dot.area > gap * gap * (hollowHead?.34f:.26f)) continue;
            if (gray != null && fadedRuleFragment(gray, width, height, dot, gap)) continue;
            if (gray != null && fadedStemFragment(gray, width, height, dot, gap)) continue;
            float dotFill = dot.area / Math.max(1f, dotWidth * dotHeight);
            if (Math.max(dotWidth, dotHeight) / Math.max(1f, Math.min(dotWidth, dotHeight)) > 1.5f
                    || dotFill < .44f) continue;
            boolean duplicate = false;
            for (Component existing : aligned) if (Math.abs(existing.centerX - dot.centerX) <= gap * .28f
                    && Math.abs(existing.centerY - dot.centerY) <= gap * .28f) { duplicate = true; break; }
            if (!duplicate) aligned.add(dot);
        }
        aligned.sort(Comparator.comparingDouble(Component::centerX));
        if (aligned.isEmpty()) return 0;
        Component first = aligned.get(0);
        if (first.centerX - head.maxX > gap * 1.55f) return 0;
        if (aligned.size() == 1) return 1;
        Component second = aligned.get(1);
        float spacing = second.centerX - first.centerX;
        return spacing >= gap * .18f && spacing <= gap * 1.45f
                && Math.abs(second.centerY - first.centerY) <= gap * .40f ? 2 : 1;
    }

    /** Thresholding can isolate the darker crossing of a shaded stem and rule. */
    private static boolean fadedStemFragment(byte[] gray,int width,int height,Component dot,float gap) {
        int cx=Math.round(dot.centerX),cy=Math.round(dot.centerY);
        int band=Math.max(1,Math.round(gap*.12f)),flank=Math.max(2,Math.round(gap*.45f));
        int first=Math.max(Math.round(gap*.4f),(dot.maxY-dot.minY+1)/2+2);
        int last=Math.round(gap*1.3f);
        if(last-first<4||cx-band-flank<0||cx+band+flank>=width||cy-last<0||cy+last>=height)return false;
        int longSides=0,shortSides=0;
        int nearFirst=Math.max(2,(dot.maxY-dot.minY+1)/2+1),nearLast=Math.round(gap*.65f);
        for(int direction:new int[]{-1,1}) {
            int support=0;
            for(int distance=first;distance<=last;distance++) {
                int y=cy+direction*distance;
                for(int x=cx-band;x<=cx+band;x++) {
                    int ink=gray[y*width+x]&255;
                    int paper=Math.max(gray[y*width+x-flank]&255,gray[y*width+x+flank]&255);
                    if(ink<=210&&paper>=ink+12){support++;break;}
                }
            }
            if(support>=(last-first+1)*.8f)longSides++;
            int nearSupport=0;
            for(int distance=nearFirst;distance<=nearLast;distance++) {
                int y=cy+direction*distance;
                for(int x=cx-band;x<=cx+band;x++) {
                    int ink=gray[y*width+x]&255;
                    int paper=Math.max(gray[y*width+x-flank]&255,gray[y*width+x+flank]&255);
                    if(ink<=210&&paper>=ink+12){nearSupport++;break;}
                }
            }
            if(nearLast-nearFirst>=3&&nearSupport>=(nearLast-nearFirst+1)*.8f)shortSides++;
        }
        // A crossing near a stem tip has a shorter continuation on one side.
        // Require a long stem plus close, continuous support on both sides of the dot core.
        return longSides==2||(longSides>=1&&shortSides==2);
    }

    /** Thresholding can isolate a darker fleck along a faded staff rule. */
    private static boolean fadedRuleFragment(byte[] gray,int width,int height,Component dot,float gap) {
        if(dot.maxY-dot.minY+1>gap*.25f)return false;
        int row=Math.round(dot.centerY),flank=Math.max(2,Math.round(gap*.3f)),band=Math.max(1,Math.round(gap*.16f));
        if(row<flank||row+flank>=height)return false;
        int matching=0;
        for(int line=-4;line<=4;line++) {
            if(line==0)continue;
            int center=Math.round(row+line*gap),supported=0,samples=0;
            if(center<flank+band||center+flank+band>=height)continue;
            for(int x=Math.max(0,Math.round(dot.centerX-gap*1.5f));x<=Math.min(width-1,Math.round(dot.centerX+gap*1.5f));x++) {
                samples++;boolean ink=false;
                for(int y=center-band;y<=center+band;y++) {
                    int value=gray[y*width+x]&255;
                    if(value<=240&&(gray[(y-flank)*width+x]&255)>=value+12
                            &&(gray[(y+flank)*width+x]&255)>=value+12)ink=true;
                }
                if(ink)supported++;
            }
            if(samples>=12&&supported>=samples*.55f)matching++;
        }
        return matching>=3;
    }

    private static List<Component> findDarkDotComponents(byte[] gray, int width, int height,
                                                          Component head, float gap) {
        return findDarkDotComponents(gray, width, height, head, gap, 135);
    }

    private static List<Component> findDarkDotComponents(byte[] gray, int width, int height,
                                                          Component head, float gap, int threshold) {
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
        for (int y = 0; y < localHeight;) {
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
            if (visited[localOrigin] || ruleRows[originY] || (gray[(top + originY) * width + left + originX] & 0xff) > threshold) continue;
            int stackSize = 0; stack[stackSize++] = localOrigin; visited[localOrigin] = true;
            int area = 0, minX = right, maxX = left, minY = bottom, maxY = top;
            long sumX = 0, sumY = 0;
            while (stackSize > 0) {
                int current = stack[--stackSize]; int lx = current % localWidth, ly = current / localWidth;
                int x = left + lx, y = top + ly; area++; sumX += x; sumY += y;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = lx + dx, ny = ly + dy;
                    if ((dx == 0 && dy == 0) || nx < 0 || nx >= localWidth || ny < 0 || ny >= localHeight) continue;
                    int next = ny * localWidth + nx;
                    if (!visited[next] && !ruleRows[ny] && (gray[(top + ny) * width + left + nx] & 0xff) <= threshold) {
                        visited[next] = true; stack[stackSize++] = next;
                    }
                }
            }
            // Removing a rule can expose the tiny end of a curved flag. A newly
            // separated mark needs a substantial round core to count as a dot.
            boolean ruleCut = minY > top && ruleRows[minY-top-1]
                    || maxY < bottom && ruleRows[maxY-top+1];
            if (ruleCut && area < gap*gap*.06f) continue;
            // A component cut by the search window is not an isolated dot. In particular the
            // protruding end of a whole note's ledger line used to become a false dot here.
            if (minX > left && maxX < right && minY > top && maxY < bottom)
                result.add(new Component(area, minX, maxX, minY, maxY,
                        sumX / (float) area, sumY / (float) area));
        }
        return result;
    }

    /** Three parallel slashes can sit above or below a stemless whole note. */
    private static int[] detachedTremolo(byte[] gray,int width,int height,Component head,float gap) {
        if(gray==null)return null;
        int radius=Math.max(3,Math.round(gap*.4f));
        for(int direction:new int[]{1,-1})for(float shift:new float[]{0,-.15f,.15f})
                for(float slope:new float[]{-.5f,-.35f,-.65f,-.8f}) {
            int cx=Math.round(head.centerX+shift*gap),edge=direction>0?head.maxY:head.minY;
            int a=edge+direction*Math.round(gap*.35f),b=edge+direction*Math.round(gap*3.4f);
            int first=Math.max(radius+1,Math.min(a,b)),last=Math.min(height-radius-2,Math.max(a,b));
            if(cx-Math.round(gap*1.2f)<0||cx+Math.round(gap*1.2f)>=width)continue;
            List<int[]> bands=new ArrayList<>();int run=0;
            for(int y=first;y<=last+1;y++) {
                int shade=0;
                if(y<=last)for(int dx=-radius;dx<=radius;dx++)
                    shade+=gray[(y+Math.round(slope*dx))*width+cx+dx]&255;
                boolean ink=y<=last&&shade<(radius*2+1)*55;
                if(ink)run++;
                else {if(run>=2&&run<=gap*.75f)bands.add(new int[]{y-run,y-1});run=0;}
            }
            for(int i=0;i+2<bands.size();i++) {
                int[] one=bands.get(i),two=bands.get(i+1),three=bands.get(i+2);
                float c1=(one[0]+one[1])*.5f,c2=(two[0]+two[1])*.5f,c3=(three[0]+three[1])*.5f;
                if(c2-c1<gap*.45f||c2-c1>gap*1.05f||c3-c2<gap*.45f||c3-c2>gap*1.05f
                        ||Math.abs(c3-2*c2+c1)>gap*.3f
                        ||Math.abs((direction>0?c1:c3)-edge)>gap*1.5f)continue;
                int diagonal=0;
                for(float center:new float[]{c1,c2,c3}) {
                    float left=verticalInkCenter(gray,width,height,cx-radius,Math.round(center-slope*radius),gap);
                    float right=verticalInkCenter(gray,width,height,cx+radius,Math.round(center+slope*radius),gap);
                    // Pixel-run centres are quantized to half pixels on small staves.
                    if(Float.isFinite(left)&&Float.isFinite(right)
                            &&left-right>=Math.max(1,Math.floor(gap*.15f))&&left-right<=gap*1.2f)diagonal++;
                }
                if(diagonal<2)continue;
                // Horizontal rules and broad beams do not end near the note's centre.
                boolean bounded=true;
                for(float center:new float[]{c1,c2,c3})for(int sign:new int[]{-1,1}) {
                    int x=cx+sign*Math.round(gap*1.05f),dark=0;
                    int y=Math.round(center+slope*(x-cx));
                    if(y<2||y>=height-2){bounded=false;continue;}
                    for(int yy=Math.max(0,y-radius);yy<=Math.min(height-1,y+radius);yy++) {
                        dark=(gray[yy*width+x]&255)<135?dark+1:0;
                        if(dark>=Math.max(4,Math.round(gap*.3f)))bounded=false;
                    }
                }
                if(bounded)return new int[]{cx-Math.round(gap*.95f),cx+Math.round(gap*.95f),
                        one[0]-Math.round(gap*.45f),three[1]+Math.round(gap*.45f)};
            }
        }
        return null;
    }

    private static float verticalInkCenter(byte[] gray,int width,int height,int x,int y,float gap) {
        if(y<0||y>=height||(gray[y*width+x]&255)>=135)return Float.NaN;
        int top=y,bottom=y,limit=Math.round(gap*1.2f);
        while(top>0&&y-top<limit&&(gray[(top-1)*width+x]&255)<135)top--;
        while(bottom<height-1&&bottom-y<limit&&(gray[(bottom+1)*width+x]&255)<135)bottom++;
        if(y-top==limit||bottom-y==limit)return Float.NaN;
        return (top+bottom)*.5f;
    }

    /** Isolated thick strokes cross BOTH sides of their own stem and end before
     * neighboring stems. Rhythmic beams and one-sided beam hooks fail those bounds. */
    private static int[] tremoloStrokeCounts(byte[] gray,int width,int height,
            Component head,float gap,List<Component> heads) {
        int[] empty={0,0};
        if(gray==null||head.maxX-head.minX+1<gap*.95f)return empty;
        int[] stem=attachedRawStem(gray,width,height,head,gap);
        if(stem==null)return empty;
        int side=Math.max(2,Math.round(gap*.38f)),far=Math.max(side+2,Math.round(gap*1.4f));
        if(stem[0]-far<0||stem[0]+far>=width)return empty;
        int edge=stem[2]>0?head.maxY:head.minY;
        int a=edge+stem[2]*Math.round(gap*.65f),b=stem[1]+stem[2]*Math.round(gap*.15f);
        int first=Math.max(1,Math.min(a,b)),last=Math.min(height-2,Math.max(a,b));
        java.util.Set<Long> strokeKeys=new java.util.HashSet<>(),nearKeys=new java.util.HashSet<>();
        int run=0;
        for(int y=first;y<=last+1;y++) {
            boolean shortStroke=y<=last&&(gray[y*width+stem[0]-side]&255)<170
                    &&(gray[y*width+stem[0]+side]&255)<170;
            if(shortStroke)run++;
            else {
                if(run>=Math.max(2,Math.round(gap*.18f))&&run<=gap*.85f) {
                    float center=y-(run+1)*.5f;boolean otherHead=false;
                    for(Component other:heads)if(other!=head
                            &&Math.abs(other.centerX-stem[0])<gap*1.5f
                            &&center>=other.minY-gap*.25f&&center<=other.maxY+gap*.25f){otherHead=true;break;}
                    int leftKey=otherHead?-1:strokeWingKey(gray,width,height,stem[0],center,gap,-1);
                    int rightKey=otherHead?-1:strokeWingKey(gray,width,height,stem[0],center,gap,1);
                    if(leftKey>=0&&rightKey>=0) {
                        // A nearby staff rule can seed this same pair of wings twice.
                        long key=((long)leftKey<<32)|(rightKey&0xffffffffL);strokeKeys.add(key);
                        if((stem[1]-center)*stem[2]>=-gap*.2f
                                &&(stem[1]-center)*stem[2]<=Math.max(4,Math.round(gap*1.85f)))nearKeys.add(key);
                    }
                }
                run=0;
            }
        }
        return strokeKeys.size()>3?empty:new int[]{strokeKeys.size(),nearKeys.size()};
    }

    private static boolean boundedStrokeWing(byte[] gray,int width,int height,int stemX,
            float center,float gap,int side) {
        return strokeWingKey(gray,width,height,stemX,center,gap,side)>=0;
    }

    private static int strokeWingKey(byte[] gray,int width,int height,int stemX,
            float center,float gap,int side) {
        int inner=Math.max(2,Math.round(gap*.32f)),outer=Math.round(gap*1.5f);
        int top=Math.max(0,Math.round(center-gap)),bottom=Math.min(height-1,Math.round(center+gap));
        int w=outer-inner+1,h=bottom-top+1;
        if(w<2||h<2)return -1;
        boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];int size=0,take=0;
        int seedX=Math.max(inner,Math.round(gap*.5f))-inner;
        for(int y=Math.max(top,Math.round(center)-2);y<=Math.min(bottom,Math.round(center)+2);y++) {
            int at=(y-top)*w+seedX;
            if(thickStrokeInk(gray,width,height,stemX+side*(inner+seedX),y,gap)){seen[at]=true;queue[size++]=at;}
        }
        if(size==0)return -1;
        int key=Integer.MAX_VALUE;
        while(take<size) {
            int at=queue[take++],x=at%w,y=at/w;
            if(x==w-1||y==0||y==h-1)return -1;
            key=Math.min(key,(top+y)*width+stemX+side*(inner+x));
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                int nx=x+dx,ny=y+dy;if(nx<0||nx>=w||ny<0||ny>=h)continue;
                int next=ny*w+nx;
                if(!seen[next]&&thickStrokeInk(gray,width,height,stemX+side*(inner+nx),top+ny,gap)){seen[next]=true;queue[size++]=next;}
            }
        }
        return key;
    }

    private static boolean thickStrokeInk(byte[] gray,int width,int height,int x,int y,float gap) {
        if((gray[y*width+x]&255)>=170)return false;
        int count=1,radius=Math.max(3,Math.round(gap*.45f));
        for(int dy=1;dy<=radius&&y-dy>=0&&(gray[(y-dy)*width+x]&255)<170;dy++)count++;
        for(int dy=1;dy<=radius&&y+dy<height&&(gray[(y+dy)*width+x]&255)<170;dy++)count++;
        return count>=Math.max(3,Math.ceil(gap*.30f));
    }

    /** A numeral/head prediction painted over a stroke can hide that stroke from
     * the original band count. Confirm the actual long beams outside its wings. */
    private static int beamsBeyondTremolo(byte[] gray,byte[] labels,int width,int height,
            Component head,Staff staff) {
        float gap=staff.gap;int[] stem=attachedRawStem(gray,width,height,head,gap);
        if(stem==null)return 0;
        int a=stem[1]-stem[2]*Math.round(gap*2.3f),b=stem[1]+stem[2]*Math.round(gap*.6f);
        int top=Math.max(0,Math.min(a,b)),bottom=Math.min(height-1,Math.max(a,b)),beams=0;
        for(float offset:new float[]{-1.45f,-1.7f,1.45f,1.7f})
            beams=Math.max(beams,thickNonHeadBands(gray,labels,width,height,
                    stem[0]+Math.round(gap*offset),top,bottom,staff));
        return Math.min(3,beams);
    }

    private static int detectBeamCount(byte[] labels, byte[] gray, int width, int height,
                                       Component head, Staff staff) {
        float gap=staff.gap;
        // A stem attaches to this oval's edge. A wider window can borrow the preceding
        // triplet's stem and assign its beams to the following ordinary quarter note.
        boolean smallHead = head.maxX - head.minX + 1 < gap * .95f
                || head.maxY - head.minY + 1 < gap * .70f;
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
                bestX = x; bestAbove = above; bestBelow = below;
            }
        }
        boolean upward = bestAbove >= bestBelow;
        if (Math.max(bestAbove, bestBelow) < Math.max(3, Math.round(gap * .75f))) return 0;
        int stemEnd = findStemEnd(labels, width, bestX, upward,
                upward ? aboveTop : belowTop, upward ? aboveBottom : belowBottom);
        // Trace the attached ink, not a fixed-height semantic window. In a wide chord the
        // upper head lies inside that window and used to masquerade as the lower head's beam.
        int[] attached = attachedRawStem(gray, width, height, head, gap);
        attached = stemBelowDetachedBow(gray,width,height,head,gap,attached);
        attached = stemBeforePaperTail(labels,gray,width,height,head,gap,attached);
        if (attached != null) {
            bestX = attached[0]; stemEnd = attached[1]; upward = attached[2] < 0;
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
        int minimumHorizontal = Math.max(4, Math.round(gap * .85f));
        // the model often leaves a narrow background seam where a beam meets its stem. Allow that
        // local seam, but stay well below the roughly 3-4 staff-gap distance to the next stem.
        int stemTolerance = Math.max(2, Math.round(gap * 1.10f));
        int allowedRunGap = Math.max(1, Math.round(gap * .22f));
        int beams = 0; boolean inBand = false;
        for (int y = top; y <= bottom; y++) {
            // A beam belongs to this note only when its horizontal run reaches this note's
            // own stem. Counting all ink in a wide window assigns a neighbour's partial
            // 16th/32nd beams to the wrong note (the Boulevard regression).
            boolean staffLine = gray != null && rowLabelCount(labels, width, y,
                    horizontalLeft, horizontalRight, OmrMeasurePostProcessor.STAFF)
                    >= minimumHorizontal;
            int run = gray == null
                    ? horizontalRunAtStem(labels, width, y, horizontalLeft,
                            horizontalRight, bestX, stemTolerance, allowedRunGap)
                    : darkRunAtStem(gray, width, y, horizontalLeft, horizontalRight,
                            bestX, Math.max(2, Math.round(gap * .28f)), 1);
            // A staff-labelled row can still be white between two genuine beams. Only bridge
            // the semantic occlusion when raw ink really spans it; otherwise end the band.
            if (staffLine && !smallHead && run >= minimumHorizontal) continue;
            if (staffLine) run = 0;
            boolean band = run >= minimumHorizontal;
            if (band && !inBand) { beams++; inBand = true; }
            else if (!band) inBand = false;
        }
        // At this render resolution a thick 32nd beam can be segmented into four dark bands.
        // Four-band optical results are therefore not safe evidence of a true 64th note.
        if (beams == 0 && !smallHead && gray != null && hasCurvedFlag(labels, gray, width, height,
                head, gap, bestX, stemEnd, upward)) return 1;
        // A thin staff line through the white gap can fuse two sloped beams at the stem.
        // Recover only when thick ink bands on BOTH sides independently prove two beams;
        // this cannot borrow a neighbour's one-sided partial beam.
        if (beams == 1 && gray != null && !smallHead) {
            boolean leftProof=false,rightProof=false;
            for(float distance:new float[]{.65f,1f,1.35f}) {
                int offset=Math.max(3,Math.round(gap*distance));
                leftProof |= thickBeamBands(gray,width,height,bestX-offset,top,bottom,gap)==2;
                rightProof |= thickBeamBands(gray,width,height,bestX+offset,top,bottom,gap)==2;
            }
            if(leftProof&&rightProof)beams=2;
        }
        if (attached != null && gray != null) {
            int thick = 0, innerThick = 0;
            for (float distance : new float[]{-.65f, -.4f, .4f, .65f}) {
                int x = bestX + Math.round(distance * gap);
                int near = Math.max(0, stemEnd - (upward ? Math.round(gap*.2f) : inside));
                int far = Math.min(height-1, stemEnd + (upward ? inside : Math.round(gap*.2f)));
                // Do not count any head on the chord's attached stem as a beam.
                int innerX=bestX+Math.round(Math.copySign(.4f,distance)*gap);
                int count=thickNonHeadBands(gray,labels,width,height,x,near,far,staff,innerX);
                thick=Math.max(thick,count);
                if(Math.abs(distance)<.5f)innerThick=Math.max(innerThick,count);
            }
            if(thick==1&&innerThick==0)thick=0;
            if (thick == 0 && hasCurvedFlag(labels, gray, width, height, head, gap, bestX, stemEnd, upward)) thick = 1;
            // The returning edge of one curved flag can intersect an outer column twice.
            // Multiple flags also need separate thick roots close to their shared stem.
            // Very long traces can follow dark paper beyond the actual stem.
            // They do not establish a trustworthy endpoint for counting flag roots.
            if (thick > 1 && Math.abs(stemEnd-head.centerY) < gap*7
                    && hasCurvedFlag(labels, gray, width, height, head, gap, bestX, stemEnd, upward)) {
                int near = Math.max(0, stemEnd - (upward ? Math.round(gap*.2f) : inside));
                int far = Math.min(height-1, stemEnd + (upward ? inside : Math.round(gap*.2f)));
                int roots = thickNonHeadBands(gray, labels, width, height,
                        bestX + Math.round(gap*.4f), near, far, staff);
                if (roots == 1) thick = 1;
            }
            return Math.min(3, thick);
        }
        return Math.min(3, beams);
    }

    /** A tolerant stem trace can bridge a white gap into dark page artwork.
     * Shorten an unusually long trace only at a printed beam, with no semantic
     * stem continuing through the supposed tail. */
    private static int[] stemBeforePaperTail(byte[] labels,byte[] gray,int width,int height,
            Component head,float gap,int[] original) {
        if(original==null||Math.abs(original[1]-head.centerY)<=gap*7)return original;
        int end=Math.round(head.centerY),blank=0;
        for(int y=end;(original[1]-y)*original[2]>=0;y+=original[2]) {
            if((gray[y*width+original[0]]&255)<170){end=y;blank=0;}
            else if(++blank>1)break;
        }
        int[] strict={original[0],end,original[2]};
        if(Math.abs(end-head.centerY)<gap*2.3f||(original[1]-end)*original[2]<gap*.75f)return original;
        int start=strict[1]+original[2]*Math.max(2,Math.round(gap*.4f)),ink=0;
        for(int y=start;(original[1]-y)*original[2]>=0;y+=original[2])
            for(int x=Math.max(0,original[0]-1);x<=Math.min(width-1,original[0]+1);x++)
                if(labels[y*width+x]==OmrMeasurePostProcessor.STEM_OR_REST)ink++;
        if(ink>gap*.3f)return original;
        int left=Math.max(0,strict[0]-Math.round(gap*3)),right=Math.min(width-1,strict[0]+Math.round(gap*3));
        int margin=Math.max(2,Math.round(gap*.35f)),run=0,thick=0,stemInk=0;
        for(int y=Math.max(0,strict[1]-margin);y<=Math.min(height-1,strict[1]+margin);y++) {
            if(darkRunAtStem(gray,width,y,left,right,strict[0],margin,1)>=gap*1.5f)run++;
            else run=0;
            thick=Math.max(thick,run);
        }
        for(int d=margin;d<=Math.round(gap);d++) {
            int y=strict[1]-original[2]*d;if(y<0||y>=height)continue;
            for(int x=Math.max(0,strict[0]-1);x<=Math.min(width-1,strict[0]+1);x++)
                if(labels[y*width+x]==OmrMeasurePostProcessor.STEM_OR_REST)stemInk++;
        }
        return thick>=Math.max(3,Math.ceil(gap*.3f))&&stemInk>=gap*.6f?strict:original;
    }

    /** A small white gap tolerated in a stem trace can lead into a separate
     * down-bow square. Its cap is not the beam endpoint. Require the complete
     * detached cap and two legs, followed by a thick beam attached to this stem. */
    private static int[] stemBelowDetachedBow(byte[] gray,int width,int height,
                                              Component head,float gap,int[] stem) {
        if(gray==null||stem==null||stem[2]>=0)return stem;
        int left=Math.max(0,stem[0]-Math.round(gap*2)),right=Math.min(width-1,stem[0]+Math.round(gap*2));
        int top=Math.max(0,stem[1]-Math.round(gap*.5f)),bottom=Math.min(height-1,stem[1]+Math.round(gap*2));
        int w=right-left+1,h=bottom-top+1,seed=(stem[1]-top)*w+stem[0]-left;
        if((gray[stem[1]*width+stem[0]]&255)>165)return stem;
        boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];queue[0]=seed;seen[seed]=true;
        int take=0,size=1,minX=width,maxX=-1,minY=height,maxY=-1;
        while(take<size) {
            int at=queue[take++],x=at%w,y=at/w;
            minX=Math.min(minX,left+x);maxX=Math.max(maxX,left+x);minY=Math.min(minY,top+y);maxY=Math.max(maxY,top+y);
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                int nx=x+dx,ny=y+dy;if(nx<0||nx>=w||ny<0||ny>=h)continue;
                int next=ny*w+nx;if(!seen[next]&&(gray[(top+ny)*width+left+nx]&255)<=165){seen[next]=true;queue[size++]=next;}
            }
        }
        int gw=maxX-minX+1,gh=maxY-minY+1;
        if(minX<=left||maxX>=right||minY<=top||maxY>=bottom||gw<gap*.65f||gw>gap*1.65f
                ||gh<gap*.65f||gh>gap*1.65f||stem[1]>minY+gap*.3f)return stem;
        int capRows=0,legRows=0,legTotal=0;
        for(int y=minY;y<=maxY;y++) {
            int row=0,a=0,b=0,middle=0;
            for(int x=minX;x<=maxX;x++)if(seen[(y-top)*w+x-left]) {
                row++;if(x<minX+gw*.25f)a++;else if(x>maxX-gw*.25f)b++;else middle++;
            }
            if(y<minY+gh*.4f&&row>=gw*.75f)capRows++;
            if(y>=minY+gh*.45f){legTotal++;if(a>0&&b>0&&middle<=gw*.1f)legRows++;}
        }
        if(capRows<Math.max(2,Math.round(gh*.18f))||legRows<legTotal*.9f)return stem;
        int first=maxY+1,last=Math.min(height-1,maxY+Math.round(gap*.55f));
        while(first<=last&&(gray[first*width+stem[0]]&255)>165)first++;
        if(first>last||first<=maxY+1||first>=head.centerY-gap)return stem;
        int thick=Math.max(3,(int)Math.ceil(gap*.3f));
        for(int y=first;y<first+thick;y++) {
            if(y>=height||darkRunAtStem(gray,width,y,Math.max(0,stem[0]-Math.round(gap*2.5f)),
                    Math.min(width-1,stem[0]+Math.round(gap*2.5f)),stem[0],1,1)<gap*2)return stem;
        }
        return new int[]{stem[0],first,stem[2]};
    }

    private static int[] attachedRawStem(byte[] gray,int width,int height,Component head,float gap) {
        return attachedRawStem(gray,width,height,head,gap,Math.max(1,Math.round(gap*.16f)));
    }

    private static int[] attachedRawStem(byte[] gray,int width,int height,Component head,float gap,int maxBlank) {
        return attachedRawStem(gray,width,height,head,gap,maxBlank,170);
    }

    private static int[] attachedRawStem(byte[] gray,int width,int height,Component head,float gap,int maxBlank,int inkThreshold) {
        if(gray==null)return null;
        int bestLength=0;int[] best=null;
        for(int direction:new int[]{-1,1}) {
            int edge=direction<0?head.maxX:head.minX;
            for(int x=Math.max(1,edge-Math.round(gap*.3f));x<=Math.min(width-2,edge+Math.round(gap*.3f));x++) {
                int blank=0,end=Math.round(head.centerY);
                for(int d=0;d<Math.round(gap*9);d++) {
                    int y=Math.round(head.centerY)+direction*d;
                    if(y<0||y>=height)break;
                    boolean ink=(gray[y*width+x]&255)<inkThreshold;
                    if(ink){end=y;blank=0;}else if(++blank>maxBlank)break;
                }
                int length=Math.abs(end-Math.round(head.centerY));
                if(length>bestLength){bestLength=length;best=new int[]{x,end,direction};}
            }
        }
        return bestLength>=gap*2.3f?best:null;
    }

    private static int thickNonHeadBands(byte[] gray,byte[] labels,int width,int height,int x,int top,int bottom,Staff staff) {
        return thickNonHeadBands(gray,labels,width,height,x,top,bottom,staff,x);
    }

    private static int thickNonHeadBands(byte[] gray,byte[] labels,int width,int height,int x,int top,int bottom,Staff staff,int stemwardX) {
        int normal=thickNonHeadBandsAtThreshold(gray,labels,width,height,x,top,bottom,staff,165,stemwardX);
        if(normal!=1||x<1||x>=width-1)return normal;
        // A lighter antialiased staff rule can fill the gap between two dark beams.
        // Require two full-thickness dark cores before splitting that connected ink;
        // a single beam crossed by a rule still has only one core.
        int darkest=165;
        for(int y=top;y<=bottom;y++)darkest=Math.min(darkest,gray[y*width+x]&255);
        for(float fraction:new float[]{.5f,.25f}) {
            int coreThreshold=darkest+Math.round((165-darkest)*fraction);
            int cores=thickNonHeadBandsAtThreshold(gray,labels,width,height,x,top,bottom,staff,coreThreshold,stemwardX);
            if(cores==2&&separateBeamCores(gray,labels,width,x,top,bottom,staff,coreThreshold))return 2;
        }
        return normal;
    }

    private static boolean separateBeamCores(byte[] gray,byte[] labels,int width,int x,int top,int bottom,Staff staff,int threshold) {
        int start=-1,previousEnd=-1,previousStart=-1;
        int minimum=Math.max(3,(int)Math.ceil(staff.gap*.30f));
        for(int y=top;y<=bottom+1;y++) {
            boolean ink=y<=bottom&&(gray[y*width+x-1]&255)<threshold
                    &&(gray[y*width+x]&255)<threshold&&(gray[y*width+x+1]&255)<threshold
                    &&labels[y*width+x]!=OmrMeasurePostProcessor.NOTEHEAD;
            if(ink&&start<0)start=y;
            if(!ink&&start>=0) {
                if(y-start>=minimum) {
                    if(previousEnd>=0) {
                        int first=previousEnd-previousStart+1,second=y-start;
                        float separation=(start+y-1-previousStart-previousEnd)*.5f;
                        if(Math.max(first,second)<=Math.min(first,second)*1.8f
                                &&separation>=staff.gap*.4f&&separation<=staff.gap*1.1f)return true;
                    }
                    previousEnd=y-1;previousStart=start;
                }
                start=-1;
            }
        }
        return false;
    }

    private static int thickNonHeadBandsAtThreshold(byte[] gray,byte[] labels,int width,int height,int x,int top,int bottom,Staff staff,int threshold,int stemwardX) {
        float gap=staff.gap,lineTop=staff.top;
        if(x<1||x>=width-1)return 0;
        float[] track=staff.pitchTrack==null?null:staff.pitchTrack.at(x);
        if(track!=null){gap=track[1];lineTop=track[0]-4*gap;}
        int span=Math.round(gap*2),firstOffset=Math.round(gap);
        int[] leftShift=new int[span],rightShift=new int[span];
        if(track!=null)for(int i=0;i<span;i++) {
            int dx=firstOffset+i;
            leftShift[i]=Math.round(staff.pitchTrack.at(x-dx)[0]-track[0]);
            rightShift[i]=Math.round(staff.pitchTrack.at(x+dx)[0]-track[0]);
        }
        int bands=0,strongBands=0,run=0,staffRows=0;
        for(int y=top;y<=bottom+1;y++) {
            boolean ink=y<=bottom && (gray[y*width+x-1]&255)<threshold
                    &&(gray[y*width+x]&255)<threshold&&(gray[y*width+x+1]&255)<threshold
                    &&labels[y*width+x]!=OmrMeasurePostProcessor.NOTEHEAD;
            if(ink) {
                run++;
                int leftInk=0,rightInk=0;
                for(int i=0;i<span;i++) {
                    int dx=firstOffset+i;
                    if(x-dx>=0&&bandRuleInk(gray,width,height,x-dx,y+leftShift[i],threshold,track!=null))leftInk++;
                    if(x+dx<width&&bandRuleInk(gray,width,height,x+dx,y+rightShift[i],threshold,track!=null))rightInk++;
                }
                if(leftInk>=span*.85f&&rightInk>=span*.85f)staffRows++;
            } else {
                // Thick antialiased staff lines are not beams. Preserve thicker real beams
                // crossing a staff, where most of the band extends beyond the staff ink.
                float bandCenter=y-(run+1)*.5f;
                boolean onStaff=false;
                for(int line=0;line<5;line++)if(Math.abs(bandCenter-(lineTop+line*gap))<=gap*.3f)onStaff=true;
                // The middle of a long beam also has ink on both sides. Suppress it only
                // where an actual staff line runs; horizontal shape alone loses inner eighths.
                boolean onlyStaff=onStaff&&(staffRows==run||(staffRows>=3&&staffRows+1==run))
                        &&run<=gap*.55f;
                boolean rooted=run>=3&&bandReachesInnerProbe(gray,width,height,x,stemwardX,y-run,y-1,threshold);
                if(run>=Math.max(3,Math.round(gap*.30f))&&!onlyStaff&&rooted)bands++;
                if(run>=Math.max(3,Math.ceil(gap*.30f))&&!onlyStaff&&rooted)strongBands++;
                run=0;staffRows=0;
            }
        }
        // Preserve a single narrow flag. Adding a second beam needs the full
        // thickness threshold so a thinner slur terminal cannot shorten the note.
        return bands > 1 ? Math.max(1,strongBands) : bands;
    }

    /** Follow a thick ink path toward the stem. Two beams can merge at the
     * inner probe; a nearby slur cannot supply another unattached outer band. */
    private static boolean bandReachesInnerProbe(byte[] gray,int width,int height,
            int fromX,int toX,int firstY,int lastY,int threshold) {
        if(fromX==toX)return true;
        int distance=Math.abs(toX-fromX),direction=Integer.signum(toX-fromX);
        int top=Math.max(1,firstY-distance),bottom=Math.min(height-2,lastY+distance);
        boolean[] reachable=new boolean[bottom-top+1];
        for(int y=Math.max(top,firstY+1);y<=Math.min(bottom,lastY-1);y++)reachable[y-top]=true;
        for(int step=1;step<=distance;step++) {
            int x=fromX+direction*step;if(x<1||x>=width-1)return false;
            boolean[] next=new boolean[reachable.length];boolean any=false;
            for(int y=top;y<=bottom;y++) {
                int i=y-top;
                if(!reachable[i]&&(i==0||!reachable[i-1])&&(i+1==reachable.length||!reachable[i+1]))continue;
                boolean solid=true;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)
                    if((gray[(y+dy)*width+x+dx]&255)>=threshold)solid=false;
                if(!solid)continue;
                next[i]=true;any=true;
            }
            if(!any)return false;reachable=next;
        }
        return true;
    }

    /** Follow a printed staff's slope when testing whether a short band is only rule ink. */
    private static boolean bandRuleInk(byte[] gray,int width,int height,int x,int y,int threshold,boolean curved) {
        for(int yy=Math.max(0,y-(curved?1:0));yy<=Math.min(height-1,y+(curved?1:0));yy++)
            if((gray[yy*width+x]&255)<threshold)return true;
        return false;
    }

    private static int thickBeamBands(byte[] gray,int width,int height,int x,int top,int bottom,float gap) {
        if(x<1||x>=width-1)return 0;
        int bands=0,run=0;
        for(int y=top;y<=Math.min(height,bottom+1);y++) {
            boolean ink=y<=bottom && (gray[y*width+x-1]&255)<165
                    && (gray[y*width+x]&255)<165 && (gray[y*width+x+1]&255)<165;
            if(ink)run++;
            else {if(run>=Math.max(3,Math.round(gap*.30f)))bands++;run=0;}
        }
        return bands;
    }

    /** A single flag is a narrow curved hook, not a horizontal beam-width run. */
    private static boolean hasCurvedFlag(byte[] labels, byte[] gray, int width, int height,
                                          Component head, float gap, int stemX, int end,
                                          boolean upward) {
        int left = Math.max(0, Math.round(stemX + gap * .25f));
        int right = Math.min(width - 1, Math.round(stemX + gap * 1.5f));
        int top = Math.max(0, Math.round(upward ? end : end - gap * 2.3f));
        int bottom = Math.min(height - 1, Math.round(upward ? end + gap * 2.3f : end));
        int rows = 0, nearEnd = 0, bulge = 0, exterior = 0, rootRows = 0;
        boolean skippedDetachedInk = false;
        for (int y = top; y <= bottom; y++) {
            if (Math.abs(y - head.centerY) < gap * .65f) continue;
            // Do not count staff/ledger strokes as the hook's side wall.
            if (rowLabelCount(labels, width, y, Math.max(0, stemX - Math.round(gap)),
                    Math.min(width - 1, stemX + Math.round(gap * 3)),
                    OmrMeasurePostProcessor.STAFF) > gap) continue;
            // A staff rule can be painted as a generic symbol where a flag crosses
            // it. Its long continuation on both sides is still printed evidence.
            if (rawRuleBeyondFlag(gray,width,y,stemX,gap)) continue;
            int minX = right + 1, maxX = left - 1;
            for (int x = left; x <= right; x++) {
                if ((gray[y * width + x] & 0xff) > 165
                        || labels[y * width + x] == OmrMeasurePostProcessor.NOTEHEAD) continue;
                // A nearby rest can enter this window beyond the curved hook.
                // Measure the nearest ink run, not the disconnected rest beside it.
                if (maxX >= left && x - maxX > 2) break;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            }
            if (maxX < minX) continue;
            // The dot of a neighbouring eighth rest may occupy the window before
            // the hook leaves its stem. It cannot be the root of this flag.
            if (Math.abs(y - end) < gap * .5f && minX - stemX > gap * .6f) {
                skippedDetachedInk = true;
                continue;
            }
            if (Math.abs(y - end) <= gap * 1.15f && minX - stemX <= gap * .5f) rootRows++;
            rows++;
            if (Math.abs(y - end) <= gap * 1.15f) nearEnd++;
            if (maxX - stemX >= gap * .55f) bulge++;
            if (maxX >= right - 1) exterior++;
        }
        // Discarding detached ink is safe only when the remaining hook has a
        // root beside the stem. A slur entering from the far edge lacks it.
        return (!skippedDetachedInk || rootRows >= Math.max(2, Math.round(gap * .16f)))
                && rows >= Math.max(4, Math.round(gap * .65f))
                && nearEnd >= Math.max(2, Math.round(gap * .16f))
                && bulge >= Math.max(3, Math.round(gap * .30f))
                && exterior <= Math.max(1, Math.round(rows * .15f));
    }

    private static boolean rawRuleBeyondFlag(byte[] gray,int width,int y,int stemX,float gap) {
        for(int direction:new int[]{-1,1}) {
            int samples=0,ink=0;
            for(int dx=Math.round(gap*2);dx<=Math.round(gap*4);dx++) {
                int x=stemX+direction*dx;if(x<0||x>=width)continue;
                samples++;if((gray[y*width+x]&255)<=165)ink++;
            }
            if(samples<Math.max(6,Math.round(gap))||ink<samples*.85f)return false;
        }
        return true;
    }

    private static int rowLabelCount(byte[] labels, int width, int y, int left, int right,
                                     byte wanted) {
        int count = 0;
        for (int x = left; x <= right; x++) if (labels[y * width + x] == wanted) count++;
        return count;
    }

    private static int findStemEnd(byte[] labels, int width, int stemX, boolean upward,
                                   int top, int bottom) {
        int end = upward ? bottom : top;
        for (int y = top; y <= bottom; y++) {
            boolean stem = false;
            for (int dx = -2; dx <= 2; dx++) {
                int x = stemX + dx;
                if (x >= 0 && x < width
                        && labels[y * width + x] == OmrMeasurePostProcessor.STEM_OR_REST) {
                    stem = true;
                    break;
                }
            }
            if (stem && (upward ? y < end : y > end)) end = y;
        }
        return end;
    }

    private static int darkRunAtStem(byte[] gray, int width, int y, int left, int right,
                                     int stemX, int tolerance, int allowedGap) {
        int best = 0;
        int clusterLeft = -1, clusterRight = -1, previousInk = -1;
        for (int x = left; x <= right; x++) {
            if ((gray[y * width + x] & 0xff) > 165) continue;
            if (clusterLeft < 0 || x - previousInk > allowedGap + 1) {
                if (clusterLeft >= 0 && clusterLeft <= stemX + tolerance
                        && clusterRight >= stemX - tolerance)
                    best = Math.max(best, clusterRight - clusterLeft + 1);
                clusterLeft = x;
            }
            clusterRight = x;
            previousInk = x;
        }
        if (clusterLeft >= 0 && clusterLeft <= stemX + tolerance
                && clusterRight >= stemX - tolerance)
            best = Math.max(best, clusterRight - clusterLeft + 1);
        return best;
    }

    private static int horizontalRunAtStem(byte[] labels, int width, int y, int left, int right,
                                            int stemX, int tolerance, int allowedGap) {
        int best = 0;
        int clusterLeft = -1, clusterRight = -1, previousInk = -1;
        for (int x = left; x <= right; x++) {
            if (labels[y * width + x] != OmrMeasurePostProcessor.STEM_OR_REST) continue;
            if (clusterLeft < 0 || x - previousInk > allowedGap + 1) {
                if (clusterLeft >= 0 && clusterLeft <= stemX + tolerance
                        && clusterRight >= stemX - tolerance)
                    best = Math.max(best, clusterRight - clusterLeft + 1);
                clusterLeft = x;
            }
            clusterRight = x;
            previousInk = x;
        }
        if (clusterLeft >= 0 && clusterLeft <= stemX + tolerance
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
                if (check >= 0 && check < width
                        && labels[y * width + check] == OmrMeasurePostProcessor.STEM_OR_REST) { ink = true; break; }
            }
            if (ink) count++;
        }
        return count;
    }

    private static int containingMeasure(List<MeasureRegion> measures, float x, float y,
                                         float verticalTolerance) {
        int result = -1;
        float smallest = Float.MAX_VALUE;
        for (int index = 0; index < measures.size(); index++) {
            MeasureRegion measure = measures.get(index);
            if (x < measure.left() - .004f || x > measure.right() + .004f
                    || y < measure.top() - verticalTolerance
                    || y > measure.bottom() + verticalTolerance) continue;
            float area = (measure.right() - measure.left()) * (measure.bottom() - measure.top());
            if (area < smallest) { smallest = area; result = index; }
        }
        return result;
    }

    /**
     * Keeps a ledger-line note with the measure row that owns its staff. A high violin head can
     * sit outside the measure rectangle and inside the preceding row's padded rectangle; choosing
     * by page Y alone then turns an upper-register note into a low note on the row above. The
     * staff center is unambiguous even when the head itself is not.
     */
    private static int containingMeasureForStaff(List<MeasureRegion> measures, float x, float y,
                                                  Staff staff, int pageHeight) {
        float staffCenter = (staff.top + staff.bottom) * .5f / pageHeight;
        float outside = y * pageHeight < staff.top ? staff.top - y * pageHeight
                : y * pageHeight > staff.bottom ? y * pageHeight - staff.bottom : 0f;
        float verticalTolerance = Math.max(staff.gap * 3.5f,
                outside + staff.gap * .75f) / pageHeight;
        int result = -1;
        float smallest = Float.MAX_VALUE;
        for (int index = 0; index < measures.size(); index++) {
            MeasureRegion measure = measures.get(index);
            if (x < measure.left() - .004f || x > measure.right() + .004f
                    || staffCenter < measure.top() - staff.gap / pageHeight
                    || staffCenter > measure.bottom() + staff.gap / pageHeight
                    || y < measure.top() - verticalTolerance
                    || y > measure.bottom() + verticalTolerance) continue;
            float area = (measure.right() - measure.left()) * (measure.bottom() - measure.top());
            if (area < smallest) { smallest = area; result = index; }
        }
        return result;
    }

    /** Printed accidentals carry to the same written pitch until the next barline. */
    private static List<DetectedNote> applyAccidentalState(List<DetectedNote> source) {
        Map<AccidentalStateKey, Integer> measureState = new HashMap<>();
        Map<PitchKey, Integer> lastResolved = new HashMap<>();
        List<DetectedNote> result = new ArrayList<>(source.size());
        for (DetectedNote note : source) {
            ScoreNoteEvent event = note.event;
            AccidentalStateKey stateKey = new AccidentalStateKey(event.measureIndex(),
                    event.staffIndex(), event.staffCount(), event.diatonicPitchIdentity());
            PitchKey pitchKey = new PitchKey(event.staffIndex(), event.staffCount(),
                    event.diatonicPitchIdentity());
            int accidental = event.writtenAccidental();
            if (accidental != ScoreNoteEvent.ACCIDENTAL_FROM_KEY)
                measureState.put(stateKey, accidental);
            else if (measureState.containsKey(stateKey)) accidental = measureState.get(stateKey);
            else if (event.tiedFromPrevious() && lastResolved.containsKey(pitchKey))
                accidental = lastResolved.get(pitchKey);
            lastResolved.put(pitchKey, accidental);
            if (accidental != event.writtenAccidental()) {
                event = new ScoreNoteEvent(event.measureIndex(), event.positionInMeasure(),
                        event.staffStep(), event.staffIndex(), event.staffCount(), event.pageY(),
                        event.tiedFromPrevious(), event.augmentationDots(), event.beamCount(),
                        accidental, event.unbeamedDurationBeats(), event.tupletDivisor(),
                        event.followingRestBeats(),event.articulations(),event.clefBottomDiatonic());
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
                if (previous.staffIndex() == event.staffIndex()
                        && previous.staffStep() == event.staffStep()) {
                    Component priorHead=result.get(index).head;
                    boolean separateUnison=ScoreNoteTiming.hasIndependentSustain(previous)
                            !=ScoreNoteTiming.hasIndependentSustain(event)
                            && (priorHead.maxX<detected.head.minX||detected.head.maxX<priorHead.minX)
                            && Math.abs(priorHead.centerY-detected.head.centerY)<detected.staffGap*.3f;
                    if(!separateUnison) { duplicate = true; break; }
                }
            }
            if (!duplicate) result.add(detected);
        }
        return result;
    }

    private static List<DetectedNote> markTieContinuations(byte[] labels, byte[] gray,
                                                            int width, int height,
                                                            List<DetectedNote> source) {
        List<DetectedNote> result = new ArrayList<>(source);
        for (int currentIndex = 1; currentIndex < result.size(); currentIndex++) {
            DetectedNote current = result.get(currentIndex);
            int previousIndex = previousSamePitch(result, currentIndex, width);
            if (previousIndex < 0) continue;
            DetectedNote previous = result.get(previousIndex);
            if (!hasTieArc(labels, gray, width, height, previous, current)) continue;
            ScoreNoteEvent event = current.event;
            result.set(currentIndex, new DetectedNote(new ScoreNoteEvent(event.measureIndex(),
                    event.positionInMeasure(), event.staffStep(), event.staffIndex(),
                    event.staffCount(), event.pageY(), true, event.augmentationDots(),
                    event.beamCount(), event.writtenAccidental(), event.unbeamedDurationBeats(),
                    event.tupletDivisor(), event.followingRestBeats(),event.articulations(),event.clefBottomDiatonic()),
                    current.head, current.staffGap));
        }
        return result;
    }

    private static int previousSamePitch(List<DetectedNote> notes, int currentIndex, int width) {
        DetectedNote current = notes.get(currentIndex);
        ScoreNoteEvent previousOnset = null;
        for (int index = currentIndex - 1; index >= 0; index--) {
            DetectedNote previous = notes.get(index);
            if (previous.event.staffIndex() != current.event.staffIndex()
                    || previous.event.staffCount() != current.event.staffCount()) continue;
            if(current.event.measureIndex()-previous.event.measureIndex()>1)break;
            if (sameOnset(previous.event, current.event)) continue;
            if (previousOnset == null) previousOnset = previous.event;
            // A held voice can bridge a barline while another voice keeps moving. Both
            // endpoints must be sustained to search past those intervening attacks.
            else if (!sameOnset(previous.event, previousOnset)
                    &&!(ScoreNoteTiming.hasIndependentSustain(previous.event)
                    &&(previous.event.measureIndex()==current.event.measureIndex()
                    ||ScoreNoteTiming.hasIndependentSustain(current.event)))) continue;
            int measureDistance = current.event.measureIndex() - previous.event.measureIndex();
            if (measureDistance > 1) break;
            // An arc is only a tie when its endpoints are the same written pitch. Slurs can have
            // the identical curved shape, so never use the arc itself to bridge staff positions.
            if (previous.event.diatonicPitchIdentity() != current.event.diatonicPitchIdentity()) continue;
            if (measureDistance == 0
                    && current.event.positionInMeasure() - previous.event.positionInMeasure() > .68f
                    && !ScoreNoteTiming.hasIndependentSustain(previous.event))
                continue;
            // A short meter can fill the preceding bar with a quarter or dotted quarter.
            // Its left-edge position does not rule out a tie: the returning printed arc
            // must still connect matching pitches at consecutive voice onsets.
            if (measureDistance == 1 && current.event.positionInMeasure() > .58f) continue;
            float gap = (previous.staffGap + current.staffGap) * .5f;
            if(sameOnset(previous.event,previousOnset)&&systemBreakTieCandidate(previous,current,width))return index;
            int horizontal = current.head.minX - previous.head.maxX;
            if (horizontal < gap * 1.3f || horizontal > width * .34f) continue;
            // Quantization alone can occasionally put two heads near a step boundary in the same
            // bucket. A real repeated pitch remains within less than half a staff-space vertically.
            if (Math.abs(current.head.centerY - previous.head.centerY) > gap * .45f) continue;
            return index;
        }
        return -1;
    }

    private static boolean sameOnset(ScoreNoteEvent first, ScoreNoteEvent second) {
        return first.measureIndex() == second.measureIndex()
                && Math.abs(first.positionInMeasure() - second.positionInMeasure()) <= .018f;
    }

    private static boolean hasTieArc(byte[] labels, byte[] gray, int width, int height,
                                     DetectedNote previous, DetectedNote current) {
        if(systemBreakTieCandidate(previous,current,width)) {
            if(gray==null||gray.length!=labels.length)return false;
            for(int side:new int[]{-1,1})
                if(hasSystemEndTieArc(labels,gray,width,height,previous,true,side)
                        &&hasSystemEndTieArc(labels,gray,width,height,current,false,side))return true;
            return false;
        }
        int left = Math.max(0, previous.head.maxX + 1);
        int right = Math.min(width - 1, current.head.minX - 1);
        float gap = Math.max(2f, (previous.staffGap + current.staffGap) * .5f);
        if (right <= left || right - left + 1 < gap * 1.3f) return false;
        float centerY = (previous.head.centerY + current.head.centerY) * .5f;
        if (gray != null && gray.length == labels.length) {
            if (hasPrintedTieArc(labels, gray, width, height, left, right, centerY, gap))return true;
            if (!ScoreNoteTiming.hasIndependentSustain(previous.event))return false;
        }
        ArcStats above = arcStats(labels, gray, width, height, left, right,
                Math.round(centerY - gap * 3f), Math.round(centerY - gap * .12f));
        ArcStats below = arcStats(labels, gray, width, height, left, right,
                Math.round(centerY + gap * .12f), Math.round(centerY + gap * 3f));
        return plausibleArc(above, left, right, gap) || plausibleArc(below, left, right, gap);
    }

    private static boolean systemBreakTieCandidate(DetectedNote previous,DetectedNote current,int width) {
        float gap=(previous.staffGap+current.staffGap)*.5f;
        return current.event.measureIndex()==previous.event.measureIndex()+1
                &&current.event.diatonicPitchIdentity()==previous.event.diatonicPitchIdentity()
                &&previous.head.centerX>width*.65f&&current.head.centerX<width*.35f
                &&current.head.centerY-previous.head.centerY>gap*6
                &&current.head.centerY-previous.head.centerY<gap*40
                &&Math.abs(previous.staffGap-current.staffGap)<gap*.2f;
    }

    /** System-end ties retain a returning curve at both printed endpoints.
     * Short strokes and a lone outgoing slur cannot establish continuation. */
    private static boolean hasSystemEndTieArc(byte[] labels,byte[] gray,int width,int height,
            DetectedNote note,boolean outgoing,int side) {
        float gap=note.staffGap;int step=Math.max(2,Math.round(gap*.2f));
        for(int clearance=step;clearance<=gap*1.8f;clearance+=step)
            for(int span=Math.round(gap*1.6f);span<=gap*7;span+=step) {
                int left=outgoing?note.head.maxX+clearance:note.head.minX-clearance-span;
                int right=outgoing?left+span:note.head.minX-clearance;
                if(left<0||right>=width)continue;
                if(hasContinuousTieArc(labels,gray,width,height,left,right,note.head.centerY,gap,null,205,side))return true;
            }
        return false;
    }

    /** Small blank clearances can separate an engraved tie from either head. */
    private static boolean hasPrintedTieArc(byte[] labels,byte[] gray,int width,int height,
            int left,int right,float centerY,float gap) {
        if(hasContinuousTieArc(labels,gray,width,height,left,right,centerY,gap))return true;
        int step=Math.max(1,Math.round(gap*.2f));
        for(int first=0;first<=2;first++)for(int last=0;last<=2;last++) {
            if(first==0&&last==0)continue;
            int a=left+first*step,b=right-last*step;
            if(b-a<gap*1.3f)continue;
            if(hasContinuousTieArc(labels,gray,width,height,a,b,centerY,gap))return true;
        }
        // Long ties may leave a little more clearance and fade near the heads.
        // Keep short-arc limits and require a dark core within the complete curve.
        if(right-left>=gap*5)for(int first=0;first<=4;first++)for(int last=0;last<=4;last++) {
            int a=left+first*step,b=right-last*step;
            if(hasContinuousTieArc(labels,gray,width,height,a,b,centerY,gap,null,205))return true;
        }
        return false;
    }

    /** Follow one returning curve; averaging nearby slurs, stems and ledger lines loses short ties. */
    private static boolean hasContinuousTieArc(byte[] labels, byte[] gray, int width, int height,
            int left, int right, float centerY, float gap) {
        return hasContinuousTieArc(labels,gray,width,height,left,right,centerY,gap,null);
    }

    private static boolean hasContinuousTieArc(byte[] labels, byte[] gray, int width, int height,
            int left, int right, float centerY, float gap,Component target) {
        return hasContinuousTieArc(labels,gray,width,height,left,right,centerY,gap,target,165);
    }

    private static boolean hasContinuousTieArc(byte[] labels, byte[] gray, int width, int height,
            int left, int right, float centerY, float gap,Component target,int inkLimit) {
        return hasContinuousTieArc(labels,gray,width,height,left,right,centerY,gap,target,inkLimit,0);
    }

    private static boolean hasContinuousTieArc(byte[] labels, byte[] gray, int width, int height,
            int left, int right, float centerY, float gap,Component target,int inkLimit,int requiredSide) {
        int radius=Math.max(1,Math.round(gap*.09f));
        boolean[] straightRows=new boolean[height];
        for(int y=Math.max(0,Math.round(centerY-gap*3.2f));y<=Math.min(height-1,Math.round(centerY+gap*3.2f));y++) {
            int dark=0;
            for(int x=left;x<=right;x++)if((gray[y*width+x]&255)<=inkLimit)dark++;
            straightRows[y]=dark>=(right-left+1)*.85f;
        }
        for(int side:requiredSide==0?new int[]{-1,1}:new int[]{requiredSide}) for(float offset=.2f;offset<=1.15f;offset+=.15f)
            for(float bend=-.75f;bend<=1.8f;bend+=.1f) {
                if(Math.abs(bend)<.24f || offset+bend<.12f)continue;
                if(target!=null&&Math.abs(centerY+side*gap*(offset+bend)-target.centerY)>gap*.25f)continue;
                int hits=0,obscured=0,strong=0;int[] bins=new int[5],coveredBins=new int[5];
                float[] centers=new float[50],supportedCenters=new float[50];
                java.util.Arrays.fill(centers,Float.NaN);
                java.util.Arrays.fill(supportedCenters,Float.NaN);
                for(int sample=0;sample<50;sample++) {
                    float t=(sample+.5f)/50f;
                    int x=Math.round(left+t*(right-left));
                    int y=Math.round(centerY+side*gap*(offset+bend*4*t*(1-t)));
                    boolean ink=false;int obscuredY=-1;
                    for(int search=0;search<=radius*2;search++) {
                        int dy=(search+1)/2*(search%2==0?1:-1);
                        int yy=y+dy;if(yy<0||yy>=height)continue;
                        int at=yy*width+x;
                        if((gray[at]&255)>inkLimit||labels[at]==OmrMeasurePostProcessor.NOTEHEAD)continue;
                        if(straightRows[yy]||labels[at]==OmrMeasurePostProcessor.STAFF) {
                            if(obscuredY<0)obscuredY=yy;
                        } else {ink=true;centers[sample]=yy;supportedCenters[sample]=yy;
                            if((gray[at]&255)<=165)strong++;break;}
                    }
                    if(ink){hits++;bins[sample/10]++;coveredBins[sample/10]++;}
                    else if(obscuredY>=0){obscured++;coveredBins[sample/10]++;supportedCenters[sample]=obscuredY;}
                }
                if(inkLimit>165&&strong<30)continue;
                if(hits>=43&&bins[0]>=7&&bins[1]>=7&&bins[2]>=7&&bins[3]>=7&&bins[4]>=7
                        &&arcCurvature(centers,0,0,49)>=Math.max(.8f,gap*.12f))return true;
                // A short returning arc can cross a staff rule at one end. Treat a
                // few such pixels as occluded only when the remaining curve and
                // both endpoints are independently visible away from the rule.
                if(hits>=40&&obscured>0&&obscured<=6&&hits+obscured>=43
                        &&bins[0]>=5&&bins[4]>=5&&coveredBins[0]>=7&&coveredBins[1]>=7
                        &&coveredBins[2]>=7&&coveredBins[3]>=7&&coveredBins[4]>=7
                        &&arcCurvature(supportedCenters,0,0,49)>=Math.max(1.2f,gap*.15f))return true;
                // Both ends of a short tie can merge into the same thick staff rule.
                // Require an almost complete curve and an independently visible middle
                // and returning shoulders; a straight rule or one-sided beam cannot pass.
                if(hits>=36&&obscured>0&&obscured<=14&&hits+obscured>=48
                        &&bins[0]>=3&&bins[4]>=3&&bins[1]>=9&&bins[2]>=9&&bins[3]>=9
                        &&coveredBins[0]>=9&&coveredBins[4]>=9
                        &&arcCurvature(centers,0,0,49)>=Math.max(1.2f,gap*.15f))return true;
            }
        return false;
    }

    private static ArcStats arcStats(byte[] labels, byte[] gray, int width, int height,
                                     int left, int right, int top, int bottom) {
        int safeTop = Math.max(0, top), safeBottom = Math.min(height - 1, bottom);
        int pixels = 0, columns = 0, minX = right + 1, maxX = left - 1;
        int minY = safeBottom + 1, maxY = safeTop - 1;
        boolean rawAvailable = gray != null && gray.length == labels.length;
        boolean[] horizontalRows = new boolean[Math.max(0,safeBottom-safeTop+1)];
        if(rawAvailable)for(int y=safeTop;y<=safeBottom;y++) {
            int dark=0;
            for(int x=left;x<=right;x++)if((gray[y*width+x]&0xff)<=165)dark++;
            horizontalRows[y-safeTop]=dark>=(right-left+1)*.85f;
        }
        float[] columnCenters = new float[right - left + 1];
        java.util.Arrays.fill(columnCenters, Float.NaN);
        for (int x = left; x <= right; x++) {
            int columnPixels = 0, columnY = 0;
            for (int y = safeTop; y <= safeBottom; y++) {
                byte label = labels[y * width + x];
                boolean semanticArc = label == OmrMeasurePostProcessor.SYMBOL
                        || label == OmrMeasurePostProcessor.STEM_OR_REST
                        || label == OmrMeasurePostProcessor.CLEF_OR_KEY;
                boolean rawArc = rawAvailable
                        && (gray[y * width + x] & 0xff) <= 165
                        && !horizontalRows[y-safeTop]
                        && label != OmrMeasurePostProcessor.STAFF
                        && label != OmrMeasurePostProcessor.NOTEHEAD;
                if (rawAvailable ? !rawArc : !semanticArc) continue;
                columnPixels++; columnY += y; pixels++;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
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
                leftSum += y; leftCount++;
            } else if (position >= .72f) {
                rightSum += y; rightCount++;
            } else if (position >= .38f && position <= .62f) {
                middleSum += y; middleCount++;
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
                && arc.minX - left <= endpointTolerance && right - arc.maxX <= endpointTolerance
                && verticalSpan >= Math.max(2, Math.round(gap * .12f))
                && verticalSpan <= gap * 3f
                && arc.curvature >= Math.max(.65f, gap * .07f);
    }

    private static float median(float[] values) {
        float[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return (sorted[1] + sorted[2]) * .5f;
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    private static final class Staff {
        final float top, bottom, gap;
        float pitchBottom, pitchGap, pitchSlope;
        boolean printedPhase, printedSlope;
        StaffPitchTrack pitchTrack;
        int index, count = 1;
        Staff(float top, float bottom, float gap) {
            this.top = top; this.bottom = bottom; this.gap = gap;
            this.pitchBottom=bottom; this.pitchGap=gap;
        }
    }

    record Analysis(List<ScoreNoteEvent> notes, List<ScoreKeyChange> keyChanges, List<ScoreRestEvent> rests) {
        Analysis(List<ScoreNoteEvent> notes, List<ScoreKeyChange> keyChanges) {
            this(notes, keyChanges, List.of());
        }
        Analysis {
            notes = notes == null ? List.of() : List.copyOf(notes);
            keyChanges = keyChanges == null ? List.of() : List.copyOf(keyChanges);
            rests = rests == null ? List.of() : List.copyOf(rests);
        }
    }
    private record SignatureGlyph(float x, int accidental) { }
    private record Component(int area, int minX, int maxX, int minY, int maxY,
                             float centerX, float centerY) { }
    private record AccidentalCandidate(Component component, byte label) {
        boolean matches(byte value) {
            return label == 0 ? value == OmrMeasurePostProcessor.SYMBOL
                    || value == OmrMeasurePostProcessor.CLEF_OR_KEY : label == value;
        }
    }
    private record AccidentalStateKey(int measureIndex, int staffIndex, int staffCount,
                                      int staffStep) { }
    private record PitchKey(int staffIndex, int staffCount, int staffStep) { }
    private record DetectedNote(ScoreNoteEvent event, Component head, float staffGap) { }
    private record ArcStats(int pixels, int columns, int minX, int maxX, int minY, int maxY,
                            float curvature) { }
}
