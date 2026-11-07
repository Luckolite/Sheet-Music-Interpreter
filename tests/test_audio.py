# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original decoded tones; real encoding is optional outside the audio install."""
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import numpy as np
from sheet_music_recognition.audio import (
    SAMPLE_RATE, _pcm_blocks, _sample_events, find_ffmpeg, write_mp3,
)
from sheet_music_recognition.midi import write_midi


def score(notes=(), beats=4, tempos=()):
    return dict(pages=[dict(measureBeats=[beats], totalBeats=beats,
                           events=list(notes), score=dict(tempoChanges=list(tempos)))])


def note(start=0, pitch=69, duration=1, tied=False, **fields):
    return dict(startBeat=start, midi=pitch, durationBeats=duration,
                tiedFromPrevious=tied, staffIndex=0, staffCount=1, **fields)


class AudioTest(unittest.TestCase):
    def test_clock_integrates_mid_measure_tempo_and_trailing_rest(self):
        events, end = _sample_events(score([note(), note(2, 71)], tempos=[
            dict(measureIndex=0, positionInMeasure=.5, bpm=60)]), 120)
        notes = [(at, msg[0]&0xf0) for at, msg in events if msg[0]&0xf0 in (0x80, 0x90)]
        self.assertEqual([(0, 0x90), (22050, 0x80), (44100, 0x90), (88200, 0x80)], notes)
        self.assertEqual(132300, end)

    def test_ties_do_not_rearticulate_audio(self):
        events, _ = _sample_events(score([note(), note(1, duration=1, tied=True)]), 120)
        self.assertEqual(1, sum(msg[0]&0xf0 == 0x90 for _, msg in events))
        self.assertEqual([44100], [at for at, msg in events if msg[0]&0xf0 == 0x80])

    def test_repeat_navigation_uses_same_clock_as_midi(self):
        doc = score([note()], beats=1)
        doc['pages'][0]['score']['playbackDirections'] = [
            dict(measureBoundary=0, kind=4), dict(measureBoundary=1, kind=5)]
        events, end = _sample_events(doc, 120)
        self.assertEqual([0, 22050], [at for at, msg in events if msg[0]&0xf0 == 0x90])
        self.assertEqual(44100, end)

    def test_bend_and_tremolo_messages_reach_audio_renderer(self):
        events, end = _sample_events(score([note(duration=2, tremoloBeats=.5,
            guitarEffect=dict(type='bend', semitones=2))], beats=2), 120)
        self.assertEqual(4, sum(msg[0]&0xf0 == 0x90 for _, msg in events))
        self.assertTrue(any(msg[0]&0xf0 == 0xe0 for _, msg in events))
        blocks = list(_pcm_blocks(events, end))
        self.assertTrue(np.isfinite(np.frombuffer(b''.join(blocks), dtype='<f4')).all())

    def test_pcm_has_requested_pitch_and_bounded_blocks(self):
        events, end = _sample_events(score([note(duration=2)], beats=2), 120)
        blocks = list(_pcm_blocks(events, end))
        self.assertTrue(all(len(b) <= 8192*4 for b in blocks))
        samples = np.frombuffer(b''.join(blocks), dtype='<f4')
        window = samples[4410:22050]
        peak = np.argmax(abs(np.fft.rfft(window))) * SAMPLE_RATE/len(window)
        self.assertAlmostEqual(440, peak, delta=3)
        self.assertTrue(np.isfinite(samples).all())
        self.assertGreater(np.max(abs(samples)), .05)
        self.assertLessEqual(np.max(abs(samples)), 1)

    def test_silent_score_preserves_time(self):
        events, end = _sample_events(score(beats=2), 120)
        samples = np.frombuffer(b''.join(_pcm_blocks(events, end)), dtype='<f4')
        self.assertEqual(round(1.08*SAMPLE_RATE), len(samples))
        self.assertEqual(0, np.max(abs(samples)))

    def test_bend_cannot_shorten_midi_trailing_rest(self):
        from test_navigation_midi import final_tick
        doc = score([note(guitarEffect=dict(type='bend', semitones=2))], beats=8)
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'bend.mid'
            write_midi(doc, path)
            self.assertEqual(8*480, final_tick(path.read_bytes()))
        _, end = _sample_events(doc, 120)
        self.assertEqual(4*SAMPLE_RATE, end)

    def test_missing_encoder_has_actionable_error(self):
        with patch('sheet_music_recognition.audio.shutil.which', return_value=None):
            with self.assertRaisesRegex(ValueError, 'FFmpeg executable not found'):
                find_ffmpeg('missing-encoder')
            with patch.dict('sys.modules', {'imageio_ffmpeg': None}):
                with self.assertRaisesRegex(RuntimeError, r'\[audio\]'):
                    find_ffmpeg()

    def test_audio_extra_is_used_when_path_has_no_encoder(self):
        from types import SimpleNamespace
        bundled = SimpleNamespace(get_ffmpeg_exe=lambda: '/bundled/ffmpeg')
        with patch('sheet_music_recognition.audio.shutil.which', return_value=None), \
             patch.dict('sys.modules', {'imageio_ffmpeg': bundled}):
            self.assertEqual('/bundled/ffmpeg', find_ffmpeg())

    def test_invalid_bitrate_and_long_export_fail_before_encoding(self):
        with self.assertRaisesRegex(ValueError, 'bitrate'):
            write_mp3(score(), 'unused.mp3', bitrate=13)
        with self.assertRaisesRegex(ValueError, 'one hour'):
            _sample_events(score(beats=10000), 120)

    def test_failed_encoder_preserves_existing_file_and_cleans_temp(self):
        class FailedEncoder:
            returncode = 1
            stdin = None
            def poll(self):
                return self.returncode
            def wait(self, **kwargs):
                return self.returncode
        import io
        process = FailedEncoder()
        process.stdin = io.BytesIO()
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder)/'preview.mp3'
            target.write_bytes(b'previous audio')
            with patch('sheet_music_recognition.audio.find_ffmpeg', return_value='ffmpeg'), \
                 patch('sheet_music_recognition.audio.subprocess.Popen', return_value=process):
                with self.assertRaisesRegex(RuntimeError, 'MP3 encoder failed'):
                    write_mp3(score([note()]), target)
            self.assertEqual(b'previous audio', target.read_bytes())
            self.assertEqual([target], list(Path(folder).iterdir()))

    def test_real_mp3_can_be_decoded_back_to_non_silent_audio(self):
        try:
            encoder = find_ffmpeg()
        except RuntimeError:
            self.skipTest('Install audio extra or FFmpeg for encoding integration')
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)/'space in preview.mp3'
            write_mp3(score([note(duration=2)], beats=2), path, ffmpeg=encoder)
            self.assertGreater(path.stat().st_size, 1000)
            decoded = subprocess.run([encoder, '-v', 'error', '-i', str(path),
                '-f', 'f32le', '-ac', '1', '-ar', str(SAMPLE_RATE), 'pipe:1'],
                capture_output=True, check=True, timeout=30)
            audio = np.frombuffer(decoded.stdout, dtype='<f4')
            self.assertAlmostEqual(1.08, len(audio)/SAMPLE_RATE, delta=.03)
            self.assertGreater(float(np.max(abs(audio))), .04)
            peak = np.argmax(abs(np.fft.rfft(audio[4410:22050]))) * SAMPLE_RATE/17640
            self.assertAlmostEqual(440, peak, delta=3)
