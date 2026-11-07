# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Export validation must run before recognition or source/output mutation."""
import contextlib
import io
import unittest
from unittest.mock import patch

from sheet_music_recognition.cli import main


class CliAudioTest(unittest.TestCase):
    def reject(self, args, expected):
        errors = io.StringIO()
        with patch('sys.argv', ['sheet-music-recognition', *args]), \
             patch('sheet_music_recognition.cli.Interpreter') as engine, \
             contextlib.redirect_stderr(errors):
            with self.assertRaises(SystemExit) as exit_code:
                main()
            self.assertEqual(1, exit_code.exception.code)
            engine.assert_not_called()
        self.assertIn(expected, errors.getvalue())

    def test_mp3_cannot_overwrite_json(self):
        self.reject(['score.pdf', '-o', 'out.json', '--mp3', 'out.json'], 'different files')

    def test_mp3_cannot_overwrite_score(self):
        self.reject(['score.pdf', '-o', 'out.json', '--mp3', 'score.pdf'], 'input score')

    def test_encoder_requires_audio_output(self):
        self.reject(['score.pdf', '-o', 'out.json', '--ffmpeg', 'ffmpeg'], 'requires --mp3')

    def test_encoder_checked_before_inference(self):
        with patch('sheet_music_recognition.audio.find_ffmpeg', side_effect=RuntimeError('Encoder unavailable')):
            self.reject(['score.pdf', '-o', 'out.json', '--mp3', 'out.mp3'], 'Encoder unavailable')
