# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Standard MIDI preview writer; no synthesizer, sound bank or external dependency."""
import math
import struct
from pathlib import Path


def variable_length(value):
    if not 0 <= value <= 0x0FFFFFFF:
        raise ValueError("MIDI delta time exceeds four bytes")
    out = [value & 127]
    value >>= 7
    while value:
        out.insert(0, (value & 127) | 128)
        value >>= 7
    return bytes(out)


def write_midi(document, path, bpm=120):
    if not math.isfinite(bpm) or not 15 <= bpm <= 400:
        raise ValueError("Initial BPM must be 15..400 quarter notes per minute")
    ppq = 480
    tempo = round(60_000_000 / bpm)
    events = [(0, 0, b"\xff\x51\x03" + tempo.to_bytes(3, "big"))]
    offset = 0.0
    tones = []
    previous = {}
    for page in document["pages"]:
        starts = [0.0]
        for beats in page["measureBeats"]:
            starts.append(starts[-1] + beats)
        for change in page["score"]["tempoChanges"]:
            bar = change["measureIndex"]
            beat = offset + starts[bar] + page["measureBeats"][bar] * change["positionInMeasure"]
            micros = round(60_000_000 / change["bpm"])
            events.append((round(beat * ppq), 0, b"\xff\x51\x03" + micros.to_bytes(3, "big")))
        for note in sorted(page["events"], key=lambda n: n["startBeat"]):
            pitch = note["midi"]
            if not 0 <= pitch <= 127:
                continue
            start = max(0, round((offset + note["startBeat"]) * ppq))
            end = max(start + 1, round((offset + note["startBeat"] + note["durationBeats"]) * ppq))
            lane = (note["staffCount"], note["staffIndex"], pitch)
            subdivision = note.get("tremoloBeats", 0)
            if subdivision not in (0, .5, .25, .125, .0625):
                raise ValueError("Unsupported tremolo subdivision")
            step = round(subdivision * ppq)
            previous_tone = previous.get(lane)
            if (note["tiedFromPrevious"] and previous_tone is not None
                    and note.get("guitarEffect", {}) == previous_tone[4]
                    and (step == 0 or previous_tone[3] == step)
                    and abs(previous_tone[1] - start) <= ppq // 8):
                previous_tone[1] = max(previous_tone[1], end)
            else:
                tone = [start, end, pitch, step, note.get("guitarEffect", {})]
                tones.append(tone)
                previous[lane] = tone
        offset += page["totalBeats"]
    active = {}
    performed = []
    for start, end, pitch, step, effect in tones:
        if step:
            performed.extend((tick, min(end, tick + step), pitch, effect) for tick in range(start, end, step))
        else:
            performed.append((start, end, pitch, effect))
    for start, end, pitch, effect in sorted(performed, key=lambda n: (n[0], n[2])):
        kind = effect.get("type", "none")
        delta = effect.get("semitones", 0)
        if kind not in ("none", "slide", "hammer_on", "pull_off", "bend", "bend_release", "dead", "harmonic") or not isinstance(delta, (int, float)) or not math.isfinite(delta) or abs(delta) > 24:
            raise ValueError("Unsupported guitar performance effect")
        expressive = kind in ("slide", "bend", "bend_release") or effect.get("vibrato", False)
        # Pitch bend is channel-wide. Isolate it from every overlapping note, including other pitches.
        channel = next((c for c in range(16) if c != 9 and all(
            stop <= start or (not expressive and not bent and other != pitch)
            for stop, other, bent in active.get(c, []))), None)
        if channel is None:
            raise ValueError("MIDI channel capacity exceeded by overlapping voices/effects")
        active[channel] = [(stop, other, bent) for stop, other, bent in active.get(channel, []) if stop > start]
        active[channel].append((end, pitch, expressive))
        velocity = 20 if kind == "dead" else 62 if kind in ("hammer_on", "pull_off") else 80
        sounding_end = start + max(1, round((end-start) * (.12 if kind == "dead" else .45 if effect.get("palmMute") else 1)))
        if expressive:
            # RPN 0: +/-24 semitones, confined to this note's exclusive channel.
            for controller, value in ((101, 0), (100, 0), (6, 24), (38, 0), (101, 127), (100, 127)):
                events.append((start, 1, bytes([0xB0 | channel, controller, value])))
            count = max(2, min(256, (end-start)//15))
            for i in range(count+1):
                t = i/count
                smooth = lambda x: x*x*(3-2*x)
                offset = delta*(1-smooth(min(1, t/.3))) if kind == "slide" else delta*smooth(min(1,t/.4)) if kind == "bend" else delta*(smooth(t/.5) if t<.5 else 1-smooth((t-.5)/.5)) if kind == "bend_release" else 0
                if effect.get("vibrato"):
                    age = t*(end-start)/ppq*60/bpm
                    offset += .18*math.sin(age*math.pi*10)*min(1,t*8)
                bend = max(0,min(16383,round(8192+offset/24*8192)))
                tick = start+round((end-start)*t)
                if tick < sounding_end:
                    events.append((tick, 2, bytes([0xE0 | channel, bend & 127, bend >> 7])))
            events.append((end, 1, bytes([0xE0 | channel, 0, 64])))
        events.extend([(start, 3, bytes([0x90 | channel, pitch, velocity])),
                       (sounding_end, 0, bytes([0x80 | channel, pitch, 0]))])
    track = bytearray()
    last = 0
    for tick, _, message in sorted(events, key=lambda x: (x[0], x[1])):
        track.extend(variable_length(tick - last))
        track.extend(message)
        last = tick
    track.extend(b"\x00\xff\x2f\x00")
    Path(path).write_bytes(b"MThd" + struct.pack(">IHHH", 6, 0, 1, ppq) + b"MTrk" + struct.pack(">I", len(track)) + track)
