# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original typed-source controls through actual Java clocks and Python backends."""
import copy
import unittest
from unittest.mock import patch
import numpy as np
from sheet_interpreter.boundary_ties import resolve_boundary_ties
from sheet_interpreter.navigation import project_navigation
from sheet_interpreter.performance import perform_expressions
from sheet_interpreter.midi import performance_events, UnpitchedMidiPreview
from sheet_interpreter.audio import (_sample_events, _pcm_blocks, SAMPLE_RATE, write_mp3,
    UnpitchedAudioPreview, UnpitchedNoiseAttack)
from sheet_interpreter.typed_performance import unpitched_intervals
from test_expressive_performance import document as expressive_document, fermata, expression


def unpitched(start=0, duration=1, **fields):
    return dict(kind='UNPITCHED', displayStep='E', displayOctave=4,
                startBeat=start, durationBeats=duration, tiedFromPrevious=False,
                staffIndex=0, staffCount=1, **fields)


def pitched(start=0, duration=1):
    return dict(midi=64, startBeat=start, durationBeats=duration,
                tiedFromPrevious=False, staffIndex=0, staffCount=1)


def score(notes=(), beats=(4,), directions=(), tempos=()):
    return dict(pages=[dict(events=list(notes), measureBeats=list(beats), totalBeats=sum(beats),
        score=dict(playbackDirections=list(directions), tempoChanges=list(tempos)))])


def repeat(beats=1):
    return [dict(measureBoundary=0, kind=4), dict(measureBoundary=beats, kind=5)]


def audio(document):
    events, end = _sample_events(document, 120, unpitched_preview=UnpitchedAudioPreview())
    return events, end, np.frombuffer(b''.join(_pcm_blocks(events, end)), dtype='<f4')


class TypedConsumerTests(unittest.TestCase):
    def test_linear_unpitched_navigation_has_no_pitch_query(self):
        doc = score([unpitched(), pitched()])
        self.assertEqual(doc, project_navigation(doc))
        self.assertNotIn('midi', doc['pages'][0]['events'][0])

    def test_repeat_visits_have_owned_independent_attacks(self):
        doc = score([unpitched(duration=.5)], beats=(1,), directions=repeat())
        played = project_navigation(doc)
        events = played['pages'][0]['events']
        self.assertEqual([0, 1], [n['startBeat'] for n in events])
        self.assertEqual(2, len({n['_unpitchedAttackId'] for n in events}))
        self.assertTrue(all(not n['tiedFromPrevious'] and 'midi' not in n for n in events))
        messages, end, _ = audio(doc)
        self.assertEqual([0, SAMPLE_RATE//2], [at for at, message in messages if isinstance(message, UnpitchedNoiseAttack)])
        self.assertEqual(SAMPLE_RATE, end)

    def test_same_display_unpitched_heads_never_merge(self):
        doc = score([unpitched(), unpitched(), pitched()])
        messages, _, pcm = audio(doc)
        attacks = [m for _, m in messages if isinstance(m, UnpitchedNoiseAttack)]
        self.assertEqual(2, len(attacks))
        self.assertEqual(2, len({m.source_id for m in attacks}))
        self.assertEqual(1, sum(isinstance(m, bytes) and m[0]&0xf0 == 0x90 for _, m in messages))
        self.assertTrue(np.isfinite(pcm).all())

    def test_contiguous_bar_fragments_share_only_their_source_attack(self):
        doc = score([unpitched(duration=2)], beats=(1, 1), directions=repeat(2))
        played = project_navigation(doc)
        notes = played['pages'][0]['events']
        self.assertEqual([False, True, False, True], [n['_unpitchedContinuation'] for n in notes])
        intervals = unpitched_intervals(played)
        self.assertEqual([(0, 2), (2, 4)], [(n['startBeat'], n['endBeat']) for n in intervals])
        messages, _, _ = audio(doc)
        self.assertEqual(2, sum(isinstance(m, UnpitchedNoiseAttack) for _, m in messages))

    def test_owned_continuation_cannot_alias_another_raw_head(self):
        doc = score([unpitched(duration=2), unpitched(duration=2)], beats=(1, 1), directions=repeat(2))
        played = project_navigation(doc)
        self.assertEqual(4, len(unpitched_intervals(played)))
        notes = played['pages'][0]['events']
        notes[2]['sourceEventId'] = 'different-source'
        with self.assertRaisesRegex(ValueError, 'same owned source'):
            unpitched_intervals(played)

    def test_real_repeat_jump_clips_the_owned_sustain_into_a_new_visit(self):
        doc = score([unpitched(duration=12)], beats=(4, 4, 4),
            directions=[dict(measureBoundary=1, kind=4), dict(measureBoundary=3, kind=5)])
        played = project_navigation(doc)
        intervals = unpitched_intervals(played)
        self.assertEqual([(0, 12), (12, 20)], [(n['startBeat'], n['endBeat']) for n in intervals])
        self.assertTrue(intervals[1]['note']['clippedEntry'])
        events, end, _ = audio(doc)
        self.assertEqual([0, 6*SAMPLE_RATE], [at for at, m in events if isinstance(m, UnpitchedNoiseAttack)])
        self.assertEqual(10*SAMPLE_RATE, end)

    def test_explicit_clipped_entry_keeps_finite_preview_without_tie(self):
        doc = score([unpitched(duration=2)], beats=(2,))
        note = doc['pages'][0]['events'][0]
        note.update(startBeat=1, durationBeats=.25, clippedEntry=True)
        events, _, pcm = audio(doc)
        self.assertEqual([SAMPLE_RATE//2], [at for at, m in events if isinstance(m, UnpitchedNoiseAttack)])
        self.assertEqual(0, np.max(abs(pcm[:SAMPLE_RATE//2])))
        self.assertEqual(0, np.max(abs(pcm[SAMPLE_RATE//2+882:])))

    def test_neutral_dead_gain_and_gate_follow_approved_policy(self):
        ordinary = score([unpitched(duration=.02)], beats=(1,))
        dead = copy.deepcopy(ordinary)
        dead['pages'][0]['events'][0]['guitarEffect'] = dict(type='dead', semitones=0, vibrato=False)
        normal_events, _, normal_pcm = audio(ordinary)
        dead_events, _, dead_pcm = audio(dead)
        attack = next(m for _, m in dead_events if isinstance(m, UnpitchedNoiseAttack))
        self.assertAlmostEqual(.25, attack.gain)
        self.assertLess(attack.end_sample, next(m.end_sample for _, m in normal_events if isinstance(m, UnpitchedNoiseAttack)))
        self.assertLess(abs(dead_pcm[0]), abs(normal_pcm[0]))
        self.assertEqual(0, np.max(abs(dead_pcm[attack.end_sample:])))

    def test_aperiodic_burst_is_deterministic_and_bounded(self):
        doc = score([unpitched(duration=2)])
        first, _, pcm = audio(doc)
        _, _, second = audio(doc)
        self.assertEqual(pcm.tobytes(), second.tobytes())
        self.assertGreater(np.max(abs(pcm[:882])), 0)
        self.assertEqual(0, np.max(abs(pcm[882:])))
        self.assertTrue(np.isfinite(pcm).all())
        self.assertTrue(all(isinstance(m, UnpitchedNoiseAttack) for _, m in first))

    def test_tempo_changes_use_the_existing_performed_clock(self):
        doc = score([unpitched(2, duration=1)], tempos=[dict(measureIndex=0, positionInMeasure=.5, bpm=60)])
        events, end, _ = audio(doc)
        self.assertEqual(44100, next(at for at, m in events if isinstance(m, UnpitchedNoiseAttack)))
        self.assertEqual(132300, end)

    def test_source_hold_does_not_extend_noise_or_erase_pitched_attack(self):
        doc = expressive_document([fermata()])
        doc['pages'][0]['events'][0] = unpitched(duration=2, durationFallback=False)
        played = perform_expressions(doc, 120)
        self.assertGreater(played['pages'][0]['totalBeats'], 4)
        self.assertNotIn('midi', played['pages'][0]['events'][0])
        events, end, pcm = audio(doc)
        self.assertGreater(end, 2*SAMPLE_RATE)
        self.assertEqual(1, sum(isinstance(m, UnpitchedNoiseAttack) for _, m in events))
        self.assertEqual(0, np.max(abs(pcm[882:SAMPLE_RATE])))

    def test_expressive_same_display_heads_keep_distinct_source_indices(self):
        doc = expressive_document([expression('RITARDANDO', 0, 4)])
        doc['pages'][0]['events'] = [unpitched(duration=2), unpitched(duration=2), pitched(duration=2)]
        doc['pages'][0]['score']['notes'].append(dict(measureIndex=0, positionInMeasure=.1, staffIndex=0, staffCount=1))
        played = perform_expressions(doc, 120)
        events = played['pages'][0]['events']
        self.assertEqual(3, len(events))
        self.assertEqual(2, len({n['performanceId'] for n in events if n.get('kind') == 'UNPITCHED'}))

    def test_pedal_never_releases_or_extends_noise(self):
        events, end = _sample_events(score([unpitched(duration=2)]), 120, unpitched_preview=UnpitchedAudioPreview())
        events.extend([(0, bytes([0xb0, 64, 127])), (SAMPLE_RATE, bytes([0xb0, 64, 0]))])
        events.sort(key=lambda e: e[0])
        pcm = np.frombuffer(b''.join(_pcm_blocks(events, end)), dtype='<f4')
        self.assertEqual(0, np.max(abs(pcm[882:])))

    def test_missing_backend_policy_is_explicit(self):
        doc = score([unpitched()])
        with self.assertRaisesRegex(ValueError, 'UnpitchedAudioPreview'):
            _sample_events(doc, 120)
        with self.assertRaisesRegex(ValueError, 'UnpitchedMidiPreview'):
            performance_events(doc)
        with patch('sheet_interpreter.audio.find_ffmpeg') as encoder:
            with self.assertRaisesRegex(ValueError, 'UnpitchedAudioPreview'):
                write_mp3(doc, 'must-not-exist.mp3')
            encoder.assert_not_called()

    def test_midi_route_is_explicit_and_repeats_preserve_time(self):
        doc = score([unpitched(duration=.5)], beats=(1,), directions=repeat())
        ppq, events, end = performance_events(doc, unpitched_preview=UnpitchedMidiPreview(37, 80, .1))
        self.assertEqual([(0, 37), (480, 37)], [(at, msg[1]) for at, _, msg in events if msg[0] == 0x99])
        self.assertEqual(960, end)
        self.assertEqual(480, ppq)

    def test_duplicate_heads_need_midi_multi_address_policy(self):
        with self.assertRaisesRegex(ValueError, 'multi-address'):
            performance_events(score([unpitched(), unpitched()]), unpitched_preview=UnpitchedMidiPreview(37, 80, 1))

    def test_midi_neutral_dead_with_low_positive_velocity_still_attacks(self):
        n = unpitched(guitarEffect=dict(type='dead',semitones=0))
        _, events, _ = performance_events(score([n]), unpitched_preview=UnpitchedMidiPreview(37,1,1))
        self.assertEqual([1], [m[2] for _, _, m in events if m[0] == 0x99])

    def test_midi_gain_envelope_stays_on_explicit_percussion_channel(self):
        n = unpitched(performanceAttack=dict(gain=2.,settledGain=1.,seconds=.1))
        _, events, _ = performance_events(score([n]), unpitched_preview=UnpitchedMidiPreview(37,40,1))
        gains = [m[2] for _, _, m in events if m[0] == 0xb9 and m[1] == 11]
        self.assertEqual(127, gains[0])
        self.assertEqual(64, gains[-1])
        self.assertTrue(all(m[0]&15 == 9 for _, _, m in events if m[0]&0xf0 in (0x90,0x80,0xb0)))

    def test_later_independent_percussion_attack_resets_prior_gain_envelope(self):
        first = unpitched(duration=.25,performanceAttack=dict(gain=2.,settledGain=1.,seconds=.1))
        _, events, _ = performance_events(score([first,unpitched(.5)]), unpitched_preview=UnpitchedMidiPreview(37,40,.25))
        reset = [(at,m[2]) for at,_,m in events if m[0] == 0xb9 and m[1] == 11]
        self.assertIn((240,127), reset)

    def test_muted_gain_keeps_full_time_and_zero_pcm(self):
        n = unpitched(performanceAttack=dict(gain=0.,settledGain=0.,seconds=.1))
        _, end, pcm = audio(score([n]))
        self.assertEqual(2*SAMPLE_RATE, end)
        self.assertEqual(0, np.max(abs(pcm)))

    def test_finished_noise_does_not_change_the_remaining_tonal_pcm(self):
        doc = score([pitched(duration=2)])
        baseline_events, end = _sample_events(doc,120)
        baseline = np.frombuffer(b''.join(_pcm_blocks(baseline_events,end)),dtype='<f4')
        mixed = score([pitched(duration=2),unpitched(duration=2)])
        _, _, pcm = audio(mixed)
        self.assertEqual(baseline[882:].tobytes(),pcm[882:].tobytes())

    def test_each_tonal_request_rejects_before_rendering(self):
        for fields in [dict(midi=64), dict(upperMidi=65), dict(frequency=440), dict(boundaryPitch=30), dict(boundaryTies=1), dict(tiedFromPrevious=True),
                       dict(tremoloBeats=.25), dict(octaveShift=12), dict(glissando={'targetMidi':64}),
                       dict(guitarEffect=dict(type='dead', semitones=2)), dict(guitarEffect=dict(type='bend', semitones=2))]:
            with self.subTest(fields=fields):
                n = unpitched()
                n.update(fields)
                doc = score([n])
                for consume in [resolve_boundary_ties, project_navigation, lambda d: perform_expressions(d, 120),
                                lambda d: _sample_events(d, 120, unpitched_preview=UnpitchedAudioPreview())]:
                    with self.assertRaises(ValueError):
                        consume(doc)

    def test_boundary_ties_never_graft_pitched_identity_onto_unpitched(self):
        first = score([pitched(duration=4)])['pages'][0]
        first['events'][0].update(boundaryTies=4, boundaryPitch=30)
        after = score([unpitched()])['pages'][0]
        output = resolve_boundary_ties(dict(pages=[first, after]))
        self.assertNotIn('midi', output['pages'][1]['events'][0])
        self.assertFalse(output['pages'][1]['events'][0]['tiedFromPrevious'])

    def test_unknown_kind_and_nonfinite_intervals_reject(self):
        for fields in [dict(kind='UNKNOWN'), dict(startBeat=float('nan')), dict(durationBeats=float('inf')), dict(durationBeats=0)]:
            n = unpitched()
            n.update(fields)
            with self.assertRaises(ValueError):
                audio(score([n]))

    def test_invalid_direct_noise_message_cannot_escape_twenty_ms(self):
        with self.assertRaisesRegex(ValueError, 'at most20ms'):
            list(_pcm_blocks([(0, UnpitchedNoiseAttack('owner', 10000, 1., None))], 20000))

    def test_mixed_backend_has_one_shared_voice_capacity(self):
        events = [(0, UnpitchedNoiseAttack('owner'+str(i), 882, 1., None)) for i in range(256)]
        events.append((0, bytes([0x90, 64, 80])))
        with self.assertRaisesRegex(ValueError, '256 simultaneous'):
            list(_pcm_blocks(events, 22050))

    def test_nonfinite_expressive_attack_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'finite'):
            audio(score([unpitched(performanceAttack=dict(gain=float('nan'),settledGain=1,seconds=.1))]))

    def test_unowned_continuation_is_rejected(self):
        n = unpitched(_unpitchedContinuation=True)
        with self.assertRaisesRegex(ValueError, 'owned source'):
            audio(score([n]))


if __name__ == '__main__':
    unittest.main()
