// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package com.musicsheets.app.pdf;

import java.util.*;

/** Original caller-wiring scenarios, without score assets or reimplemented ingress predicates. */
public final class PickupIngressOriginalFixture {
    private record Scenario(String id, List<ScoreNoteEvent> notes, List<ScoreRestEvent> rests,
            List<ScoreMeterChange> meters, List<MeasureNumberReconciler.NumberToken> numbers,
            boolean numberProof, boolean expected) {}

    private static List<MeasureRegion> measures() {
        var out = new ArrayList<MeasureRegion>();
        int[] counts = {3, 4, 4, 3};
        for (int row = 0; row < counts.length; row++)
            for (int i = 0; i < counts[row]; i++)
                out.add(new MeasureRegion(.1f + .8f*i/counts[row],
                        .1f + .8f*(i+1)/counts[row] - .004f,
                        .1f + row*.12f, .18f + row*.12f));
        return List.copyOf(out);
    }

    private static MeasureNumberReconciler.NumberToken number(int value, int row) {
        float y = .1f + row*.12f;
        return new MeasureNumberReconciler.NumberToken(value,.09f,y-.005f,.11f,y+.012f);
    }

    private static List<MeasureNumberReconciler.NumberToken> pickupNumbers() {
        return List.of(number(3,1),number(7,2));
    }

    private static ScoreRestEvent rest(int lane, int lanes, double beats,
            ScoreRestEvent.Kind kind) {
        return new ScoreRestEvent(0,.5f,.14f,.01f,lane,lanes,beats,kind);
    }

    private static ScoreNoteEvent pitch() {
        return new ScoreNoteEvent(0,.4f,4,0,1,.14f,false,0,0,2,1f,1,0f,0,30,
                false,0f,false,0,0,1,0);
    }

    private static List<Scenario> scenarios() {
        var n = pickupNumbers();
        var four = List.of(new ScoreMeterChange(0,4,4));
        var full = List.of(rest(0,1,4,ScoreRestEvent.Kind.LITERAL));
        return List.of(
            new Scenario("full44_literal",List.of(),full,four,n,true,false),
            new Scenario("full22_literal",List.of(),full,List.of(new ScoreMeterChange(0,2,2)),n,true,false),
            new Scenario("full34_nominal_kind",List.of(),List.of(rest(0,1,4,ScoreRestEvent.Kind.FULL_MEASURE)),List.of(new ScoreMeterChange(0,3,4)),n,true,false),
            new Scenario("full44_two_lanes",List.of(),List.of(rest(0,2,4,ScoreRestEvent.Kind.LITERAL),rest(1,2,4,ScoreRestEvent.Kind.LITERAL)),four,n,true,false),
            new Scenario("short_literal",List.of(),List.of(rest(0,1,1,ScoreRestEvent.Kind.LITERAL)),four,n,true,true),
            new Scenario("missing_meter",List.of(),full,List.of(),n,true,true),
            new Scenario("later_meter_only",List.of(),full,List.of(new ScoreMeterChange(1,4,4)),n,true,true),
            new Scenario("conflicting_opening_meters",List.of(),full,List.of(new ScoreMeterChange(0,4,4),new ScoreMeterChange(0,3,4)),n,true,true),
            new Scenario("pitched_opening",List.of(pitch()),List.of(),four,n,true,true),
            new Scenario("pitched_plus_full_rest_conflict",List.of(pitch()),full,four,n,true,true),
            new Scenario("incomplete_two_lanes",List.of(),List.of(rest(0,2,4,ScoreRestEvent.Kind.LITERAL)),four,n,true,true),
            new Scenario("ordinary_numbering",List.of(),full,four,List.of(number(4,1),number(8,2)),false,false),
            new Scenario("continuation_numbering",List.of(),full,four,List.of(number(13,1),number(17,2)),false,false),
            new Scenario("one_number_not_independent",List.of(),full,four,List.of(number(3,1)),false,false),
            new Scenario("contradictory_numbering",List.of(),full,four,List.of(number(3,1),number(7,2),number(12,3)),false,false));
    }

    public static void main(String[] args) {
        String entry = args[0];
        int mismatch = 0, total = 0;
        for (Scenario fixture : scenarios()) {
            var regions = measures();
            boolean numberProof = MeasureNumberReconciler.provesOpeningPickup(regions,fixture.numbers());
            if (numberProof != fixture.numberProof())
                throw new IllegalStateException("FIXTURE_PREREQUISITE_FAILED "+fixture.id());
            int first = MeasureNumberReconciler.firstMeasureNumber(regions,fixture.numbers());
            var score = new ScorePageInterpretation(regions,fixture.notes(),first,List.of(),
                    List.of(),fixture.meters(),fixture.rests(),List.of(),List.of());
            var numbers = fixture.numbers();
            var result = switch (entry) {
                case "android" -> PickupIngressActualSlices.android(score,first,numbers,numbers,numbers);
                case "native" -> PickupIngressActualSlices.nativeEntry(score,first,numbers,numbers,numbers);
                case "public" -> PickupIngressActualSlices.publicEntry(score,first,numbers,numbers,numbers);
                default -> throw new IllegalArgumentException("Unknown entry "+entry);
            };
            if (!score.withOpeningPickup(false).equals(result.withOpeningPickup(false)))
                throw new IllegalStateException("UNRELATED_SCORE_MUTATION "+fixture.id());
            boolean okay = result.openingPickup() == fixture.expected();
            total++; if (!okay) mismatch++;
            System.out.println("CASE\t"+entry+"\t"+fixture.id()+"\t"+fixture.expected()+"\t"
                    +result.openingPickup()+"\t"+numberProof+"\t"+first+"\t"+(okay?"PASS":"BEHAVIOR_RED"));
        }
        System.out.println("SUMMARY\t"+entry+"\t"+total+"\t"+mismatch);
        if (mismatch > 0) throw new AssertionError("BEHAVIOR_RED "+mismatch+"/"+total+" "+entry);
    }
}
