// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Authored independent numeric opening-bar scenarios; no score assets or private paths. */
public class OpeningPickupContentTest {
    @Test
    public void literal_four_four_complete() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void literal_cut_time_complete() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 2, 2))));
    }

    @Test
    public void literal_six_eight_complete() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        3d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 6, 8))));
    }

    @Test
    public void full_kind_three_four_nominal() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.FULL_MEASURE)),
                        List.of(new ScoreMeterChange(0, 3, 4))));
    }

    @Test
    public void literal_three_four_complete() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        3d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 3, 4))));
    }

    @Test
    public void two_known_lanes_complete() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0, .5f, 0.2f, 0.01f, 0, 2, 4d, ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        1,
                                        2,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void mixed_full_and_literal_complete() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        2,
                                        4d,
                                        ScoreRestEvent.Kind.FULL_MEASURE),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        1,
                                        2,
                                        3d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 3, 4))));
    }

    @Test
    public void sixteen_lanes_complete() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        1,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        2,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        3,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        4,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        5,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        6,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        7,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        8,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        9,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        10,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        11,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        12,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        13,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        14,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        15,
                                        16,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void number_proof_still_required() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void short_silent_pickup() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        1d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void dotted_short_silent_pickup() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        1.5d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 6, 8))));
    }

    @Test
    public void pitched_pickup() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(
                                new ScoreNoteEvent(
                                        0, .4f, 4, 0, 1, .2f, false, 0, 0, 2, 1f, 1, 0f, 0, 30,
                                        false, 0f, false, 0, 0, 1, 0)),
                        List.of(),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void pitched_plus_full_rest_conflict() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(
                                new ScoreNoteEvent(
                                        0, .4f, 4, 0, 1, .2f, false, 0, 0, 2, 1f, 1, 0f, 0, 30,
                                        false, 0f, false, 0, 0, 1, 0)),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void overfull_literal_ambiguous() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        5d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void zero_duration_ambiguous() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        0d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void negative_duration_ambiguous() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        -4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void missing_meter() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of()));
    }

    @Test
    public void later_meter_is_not_opening_meter() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(1, 4, 4))));
    }

    @Test
    public void duplicate_equal_meter() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4), new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void conflicting_meter() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4), new ScoreMeterChange(0, 3, 4))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalid_zero_denominator() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 0))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalid_zero_numerator() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 0, 4))));
    }

    @Test
    public void missing_staff_lane() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        2,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void duplicate_staff_lane() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0, .5f, 0.2f, 0.01f, 0, 2, 4d, ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        2,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void conflicting_staff_count() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0, .5f, 0.2f, 0.01f, 0, 2, 4d, ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        1,
                                        3,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void out_of_range_lane() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        1,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void zero_staff_count() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        0,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void over_limit_staff_count() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        17,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void two_rests_same_lane_not_single_complete_glyph() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0, .5f, 0.2f, 0.01f, 0, 1, 2d, ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        2d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void empty_opening() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(), List.of(), List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void later_rest_does_not_fill_opening() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        1,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void later_notes_do_not_invalidate_full_opening() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(
                                new ScoreNoteEvent(
                                        1, .4f, 4, 0, 1, .2f, false, 0, 0, 2, 1f, 1, 0f, 0, 30,
                                        false, 0f, false, 0, 0, 1, 0)),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void later_rest_does_not_duplicate_opening_lane() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0, .5f, 0.2f, 0.01f, 0, 1, 4d, ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        1,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void full_kind_zero_duration_abstains() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        0d,
                                        ScoreRestEvent.Kind.FULL_MEASURE)),
                        List.of(new ScoreMeterChange(0, 3, 4))));
    }

    @Test
    public void near_nominal_is_not_equal() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.2f,
                                        0.01f,
                                        0,
                                        1,
                                        3.9999999999999996d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void later_note_greater_known_lane_count() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(
                                new ScoreNoteEvent(
                                        2, .4f, 4, 0, 2, .2f, false, 0, 0, 2, 1f, 1, 0f, 0, 30,
                                        false, 0f, false, 0, 0, 1, 0)),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.25f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void later_rest_greater_known_lane_count() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.25f,
                                        0.01f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        2,
                                        .5f,
                                        0.5f,
                                        0.01f,
                                        0,
                                        2,
                                        1d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void later_smaller_known_count_does_not_invent_missing_lane() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(
                                new ScoreNoteEvent(
                                        2, .4f, 4, 0, 1, .2f, false, 0, 0, 2, 1f, 1, 0f, 0, 30,
                                        false, 0f, false, 0, 0, 1, 0)),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.25f,
                                        0.01f,
                                        0,
                                        2,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL),
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.5f,
                                        0.01f,
                                        1,
                                        2,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void normalized_page_height_greater_than_one() {
        assertEquals(
                true,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.25f,
                                        1.25f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }

    @Test
    public void normalized_page_height_equal_one_is_not_greater() {
        assertEquals(
                false,
                OpeningPickupContent.keepsNumberedPickup(
                        List.of(),
                        List.of(
                                new ScoreRestEvent(
                                        0,
                                        .5f,
                                        0.25f,
                                        1.0f,
                                        0,
                                        1,
                                        4d,
                                        ScoreRestEvent.Kind.LITERAL)),
                        List.of(new ScoreMeterChange(0, 4, 4))));
    }
}
