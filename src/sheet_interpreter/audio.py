# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Stream a synthesized performance preview to an optional local MP3 encoder."""
import math
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

from .midi import performance_events, performed_document, _performance_events_prepared
from .typed_events import event_kind, validate_unpitched
from .typed_performance import unpitched_intervals
from dataclasses import dataclass


SAMPLE_RATE = 44100
RELEASE_SECONDS = .08


def find_ffmpeg(executable=None):
    """Prefer caller/PATH tools; the audio extra supplies an offline fallback."""
    if executable is not None:
        found = shutil.which(str(executable))
        if not found:
            raise ValueError("FFmpeg executable not found: " + str(executable))
        return found
    found = shutil.which("ffmpeg")
    if found:
        return found
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except (ImportError, RuntimeError) as error:
        raise RuntimeError(
            "MP3 export needs FFmpeg on PATH, --ffmpeg /path/to/ffmpeg, "
            "or pip install 'music-sheets-interpreter[audio]'"
        ) from error


@dataclass(frozen=True)
class UnpitchedAudioPreview:
    """Explicit caller choice of the finite aperiodic preview; no pitch/timbre mapping."""


@dataclass(frozen=True)
class UnpitchedNoiseAttack:
    source_id: str
    end_sample: int
    gain: float
    attack: object


def _sample_events(document, bpm, *, unpitched_preview=None):
    has_unpitched = any(event_kind(note) == 'UNPITCHED' for page in document['pages'] for note in page['events'])
    if has_unpitched:
        if not isinstance(unpitched_preview, UnpitchedAudioPreview):
            raise ValueError('Unpitched audio needs an explicit UnpitchedAudioPreview policy')
        for page in document['pages']:
            for note in page['events']:
                validate_unpitched(note)
        document, bpm, expressive_document = performed_document(document, bpm)
        pitched = dict(document, pages=[dict(page, events=[note for note in page['events']
            if event_kind(note) == 'PITCHED']) for page in document['pages']])
    else:
        pitched = document
    if has_unpitched:
        ppq, events, end_tick = _performance_events_prepared(pitched, bpm, expressive_document)
    else:
        ppq, events, end_tick = performance_events(pitched, bpm)
    if len(events) > 1_000_000:
        raise ValueError("Audio preview exceeds one million performance messages")
    seconds, last_tick, micros = 0.0, 0, round(60_000_000 / bpm)
    tempo_points = [(0, micros)]
    result = []
    for tick, _, message in events:
        seconds += (tick-last_tick) * micros / (ppq * 1_000_000)
        last_tick = tick
        if message.startswith(b"\xff\x51\x03"):
            micros = int.from_bytes(message[3:], "big")
            tempo_points.append((tick, micros))
            if micros <= 0:
                raise ValueError("Invalid audio tempo")
        else:
            result.append((round(seconds*SAMPLE_RATE), message))
    seconds += (end_tick-last_tick) * micros / (ppq * 1_000_000)
    if not math.isfinite(seconds) or not 0 <= seconds <= 3600:
        raise ValueError("Audio preview must be at most one hour")
    if has_unpitched:
        def sample_at(beat):
            target = round(beat*ppq)
            elapsed, prior, tempo = 0., 0, round(60_000_000/bpm)
            for tick, value in tempo_points:
                if tick > target:
                    break
                elapsed += (tick-prior)*tempo/(ppq*1_000_000)
                prior, tempo = tick, value
            return round((elapsed+(target-prior)*tempo/(ppq*1_000_000))*SAMPLE_RATE)
        for interval in unpitched_intervals(document):
            note = interval['note']
            start = sample_at(interval['startBeat'])
            dead = (note.get('guitarEffect') or {}).get('type') == 'dead'
            end = sample_at(interval['startBeat']+(interval['endBeat']-interval['startBeat'])*(.12 if dead else 1))
            end = min(max(start+1, end), start+round(.020*SAMPLE_RATE))
            result.append((start, UnpitchedNoiseAttack(interval['sourceId'], end,
                          .25 if dead else 1., note.get('performanceAttack'))))
        result.sort(key=lambda item: item[0])
    return result, round(seconds*SAMPLE_RATE)


def _pcm_blocks(events, end_sample):
    """Bounded mono blocks; keep phase continuous across bends and tempo changes."""
    import numpy as np
    bends, ranges = [0.0]*16, [2.0]*16
    expression = [1.0]*16
    pedal = [False]*16
    rpn = [[127, 127] for _ in range(16)]
    voices, noise_voices, cursor = [], [], 0

    def render(stop):
        nonlocal cursor, voices, noise_voices
        while cursor < stop:
            count = min(8192, stop-cursor)
            indices = np.arange(count, dtype=np.float64)
            mix = np.zeros(count, dtype=np.float64)
            for voice in voices:
                channel, pitch, velocity, start, released, phase, release_expression, key_released = voice
                age = (cursor-start+indices)/SAMPLE_RATE
                frequency = 440 * 2**((pitch-69+bends[channel])/12)
                angles = phase + indices*(2*math.pi*frequency/SAMPLE_RATE)
                envelope = np.minimum(1, age/.005) * (.3+.7*np.exp(-age*1.8))
                if released is not None:
                    envelope *= np.clip(1-(cursor-released+indices)/(SAMPLE_RATE*RELEASE_SECONDS), 0, 1)
                # Original additive tone, not sampled instruments or a bundled soundfont.
                tone = np.sin(angles)
                for harmonic, level in ((2, .3), (3, .12)):
                    if frequency*harmonic < SAMPLE_RATE/2:
                        tone += level*np.sin(angles*harmonic)
                mix += tone*envelope*(.16*velocity/127)*(expression[channel] if release_expression is None else release_expression)
                voice[5] = (phase+count*2*math.pi*frequency/SAMPLE_RATE) % (2*math.pi)
            for start, samples in noise_voices:
                a, b = max(0, cursor-start), min(len(samples), cursor-start+count)
                if b > a:
                    mix[max(0,start-cursor):max(0,start-cursor)+b-a] += samples[a:b]
            cursor += count
            noise_voices = [(start, samples) for start, samples in noise_voices if cursor < start+len(samples)]
            voices = [v for v in voices if v[4] is None or cursor-v[4] < SAMPLE_RATE*RELEASE_SECONDS]
            yield np.tanh(mix).astype('<f4').tobytes()

    for at, message in events:
        yield from render(at)
        if isinstance(message, UnpitchedNoiseAttack):
            if (type(message.end_sample) is not int or not 0 < message.end_sample-at <= round(.020*SAMPLE_RATE)
                    or not isinstance(message.source_id, str) or not message.source_id
                    or not math.isfinite(message.gain) or message.gain < 0):
                raise ValueError('Unpitched noise attack must be finite, owned and at most20ms')
            if len(voices)+len(noise_voices) >= 256:
                raise ValueError('Audio preview exceeds 256 simultaneous voices')
            state, mask = 0x6a09e667f3bcc909, (1<<64)-1
            samples = []
            for frame in range(message.end_sample-at):
                state ^= (state<<13)&mask
                state ^= state>>7
                state ^= (state<<17)&mask
                state &= mask
                age = frame/SAMPLE_RATE
                gain = message.gain
                if message.attack:
                    attack = message.attack
                    fraction = min(1., age/attack['seconds']) if attack['seconds'] else 1.
                    gain *= attack['gain']+(attack['settledGain']-attack['gain'])*fraction
                samples.append(((state>>11)*2**-53*2-1)*.12*math.exp(-450*age)*gain)
            noise_voices.append((at, np.asarray(samples, dtype=np.float64)))
            continue
        status, channel = message[0]&0xf0, message[0]&15
        if status == 0x90 and message[2]:
            if len(voices)+len(noise_voices) >= 256:
                raise ValueError("Audio preview exceeds 256 simultaneous voices")
            voices.append([channel, message[1], message[2], at, None, 0.0, None, False])
        elif status == 0x80 or status == 0x90:
            for voice in voices:
                if voice[0] == channel and voice[1] == message[1] and not voice[7]:
                    voice[7] = True
                    if not pedal[channel]:
                        voice[4] = at
                    voice[6] = expression[channel]
        elif status == 0xb0:
            controller, value = message[1:]
            if controller == 64:
                pedal[channel] = value >= 64
                if not pedal[channel]:
                    for voice in voices:
                        if voice[0] == channel and voice[7] and voice[4] is None:
                            voice[4] = at
            elif controller == 11:
                expression[channel] = value/127
            elif controller == 101:
                rpn[channel][0] = value
            elif controller == 100:
                rpn[channel][1] = value
            elif controller == 6 and rpn[channel] == [0, 0]:
                ranges[channel] = value
        elif status == 0xe0:
            bends[channel] = (((message[2]<<7)|message[1])-8192)/8192*ranges[channel]
    yield from render(end_sample+round(RELEASE_SECONDS*SAMPLE_RATE))


def write_mp3(document, path, bpm=120, *, ffmpeg=None, bitrate=192, unpitched_preview=None):
    """Export a basic synthesized MP3 preview, preserving the MIDI performance.

    Requires local FFmpeg with libmp3lame. No inference, network or soundfont is
    needed for an already decoded document. Existing output survives failures.
    """
    if bitrate not in (64, 96, 128, 160, 192, 256, 320):
        raise ValueError("MP3 bitrate must be 64, 96, 128, 160, 192, 256 or 320 kbps")
    if (any(event_kind(note) == 'UNPITCHED' for page in document['pages'] for note in page['events'])
            and not isinstance(unpitched_preview, UnpitchedAudioPreview)):
        raise ValueError('Unpitched audio needs an explicit UnpitchedAudioPreview policy')
    encoder = find_ffmpeg(ffmpeg)
    events, end_sample = _sample_events(document, bpm, unpitched_preview=unpitched_preview)
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix=".mp3-export-", suffix=".mp3", dir=target.parent, delete=False) as output:
        temporary = Path(output.name)
    process = None
    try:
        # File-backed stderr avoids pipe deadlocks and keeps diagnostics off stdout.
        with tempfile.TemporaryFile() as errors:
            process = subprocess.Popen(
                [encoder, '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
                 '-f', 'f32le', '-ar', str(SAMPLE_RATE), '-ac', '1', '-i', 'pipe:0',
                 '-c:a', 'libmp3lame', '-b:a', str(bitrate)+'k', '-f', 'mp3', str(temporary)],
                stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=errors,
                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0),
            )
            broken = False
            try:
                for block in _pcm_blocks(events, end_sample):
                    process.stdin.write(block)
            except BrokenPipeError:
                broken = True
            finally:
                try:
                    process.stdin.close()
                except BrokenPipeError:
                    broken = True
            code = process.wait(timeout=60)
            if code or broken or temporary.stat().st_size == 0:
                errors.seek(0, os.SEEK_END)
                errors.seek(max(0, errors.tell()-2000))
                detail = errors.read().decode('utf-8', errors='replace').strip()
                raise RuntimeError("MP3 encoder failed" + (": " + detail if detail else ""))
        os.replace(temporary, target)
    except subprocess.TimeoutExpired as error:
        raise RuntimeError("MP3 encoder did not finish within 60 seconds") from error
    finally:
        if process is not None and process.poll() is None:
            process.kill()
            process.wait()
        temporary.unlink(missing_ok=True)
