# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Standard MIDI preview writer; no synthesizer, sound bank or external dependency."""
import math
import struct
from dataclasses import dataclass
from pathlib import Path
from .boundary_ties import resolve_boundary_ties
from .navigation import project_navigation
from .typed_events import event_kind, validate_unpitched
from .typed_performance import unpitched_intervals


@dataclass(frozen=True)
class UnpitchedMidiPreview:
    """Explicit caller choice of percussion address, velocity and maximum gate in quarter beats.

    Channel 10 is reserved for this preview. Its note number is a percussion
    address, not the source note's pitch or a recovered instrument identity.
    No default mapping is provided. Written notation and score time stay intact.
    """
    percussion_note: int
    velocity: int
    gate_beats: float

    def __post_init__(self):
        if type(self.percussion_note) is not int or not 0 <= self.percussion_note <= 127:
            raise ValueError('Percussion preview address must be an integer 0..127')
        if type(self.velocity) is not int or not 1 <= self.velocity <= 127:
            raise ValueError('Percussion preview velocity must be an integer 1..127')
        if (type(self.gate_beats) not in (int, float) or not math.isfinite(self.gate_beats)
                or not 0 < self.gate_beats <= 128):
            raise ValueError('Percussion preview gate must be finite and positive, at most 128 quarter beats')


def _check_unpitched_clock(document, policy):
    unpitched = False
    for page in document['pages']:
        for note in page['events']:
            event_kind(note)
            validate_unpitched(note)
            if event_kind(note) != 'UNPITCHED':
                continue
            unpitched = True
            start, duration, extent = note['startBeat'], note['durationBeats'], page['totalBeats']
            if (any(type(value) not in (int, float) or not math.isfinite(value)
                    for value in (start, duration, extent)) or start < 0 or duration <= 0
                    or start + duration > extent + 1e-6):
                raise ValueError('An unpitched MIDI preview needs a finite span inside its page timeline')
    if unpitched:
        if not isinstance(policy, UnpitchedMidiPreview):
            raise ValueError('Unpitched MIDI export needs an explicit UnpitchedMidiPreview policy')
        for page in document['pages']:
            beats, extent = page['measureBeats'], page['totalBeats']
            if (any(type(value) not in (int, float) or not math.isfinite(value)
                    or not 0 < value <= 128 for value in beats)
                    or type(extent) not in (int, float) or not math.isfinite(extent)
                    or not math.isclose(sum(beats), extent, abs_tol=1e-6)):
                raise ValueError('Unpitched MIDI page extent must agree with finite written measures')
    return unpitched


def variable_length(value):
    if not 0 <= value <= 0x0FFFFFFF:
        raise ValueError("MIDI delta time exceeds four bytes")
    out = [value & 127]
    value >>= 7
    while value:
        out.insert(0, (value & 127) | 128)
        value >>= 7
    return bytes(out)


def performed_document(document, bpm=120):
    """Resolve the existing source/Java clocks without assigning a backend identity."""
    unpitched_intervals(document)
    document = resolve_boundary_ties(document)
    if not math.isfinite(bpm) or not 15 <= bpm <= 400:
        raise ValueError("Initial BPM must be 15..400 quarter notes per minute")
    from .performance import perform_expressions
    expressive_document = perform_expressions(document, bpm)
    if expressive_document is None:
        document = project_navigation(document, bpm)
    else:
        document, bpm = expressive_document, 120
    return document, bpm, expressive_document is not None


def performance_events(document, bpm=120, *, unpitched_preview=None):
    """Shared MIDI-performance clock/messages for notation and audio previews."""
    _check_unpitched_clock(document, unpitched_preview)
    document, bpm, expressive_document = performed_document(document, bpm)
    return _performance_events_prepared(document, bpm, expressive_document, unpitched_preview)


def _performance_events_prepared(document, bpm, expressive_document, unpitched_preview=None):
    ppq = 480
    tempo = round(60_000_000 / bpm)
    events = [(0, 0, b"\xff\x51\x03" + tempo.to_bytes(3, "big"))]
    offset = 0.0
    tones = []
    percussion = []
    for interval in unpitched_intervals(document):
        note = interval['note']
        start = max(0, round(interval['startBeat']*ppq))
        written_end = max(start+1, round(interval['endBeat']*ppq))
        dead = (note.get('guitarEffect') or {}).get('type') == 'dead'
        gate = min(unpitched_preview.gate_beats, (interval['endBeat']-interval['startBeat'])*(.12 if dead else 1))
        end = min(written_end, start+max(1, round(gate*ppq)))
        attack = note.get('performanceAttack')
        reference = max(attack['gain'], attack['settledGain']) if attack else 1.
        gain = reference*(.25 if dead else 1)
        velocity = 0 if gain == 0 else max(1, min(127, round(unpitched_preview.velocity*gain)))
        if velocity:
            percussion.append((start, end, unpitched_preview.percussion_note, velocity, attack, reference))
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
            if event_kind(note) == 'UNPITCHED':
                continue
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
            gliss = note.get("glissando")
            if gliss is not None:
                if (not isinstance(gliss, dict) or gliss.get("style") != "white_keys"
                        or type(gliss.get("targetMidi")) is not int or not 0 <= gliss["targetMidi"] <= 127):
                    raise ValueError("Unsupported glissando performance")
                if step or note.get("guitarEffect"):
                    raise ValueError("Conflicting glissando performance")
            previous_tone = previous.get(lane)
            # Recognition has already verified both endpoints of this tie.
            # A hidden/restored lower staff must not force a second top-staff attack.
            if note["tiedFromPrevious"] and lane[1] == 0 and lane[0] in (1, 2):
                alternate = previous.get((3 - lane[0], 0, pitch))
                if (alternate is not None and abs(alternate[1] - start) <= round(ppq * .04)
                        and (previous_tone is None or previous_tone[1] < alternate[1])):
                    previous_tone = alternate
            if (note["tiedFromPrevious"] and previous_tone is not None
                    and gliss is None and previous_tone[5] is None
                    and note.get("guitarEffect", {}) == previous_tone[4]
                    and (step == 0 or previous_tone[3] == step)
                    and abs(previous_tone[1] - start) <= ppq // 8):
                previous_tone[1] = max(previous_tone[1], end)
                previous[lane] = previous_tone
            else:
                tone = [start, end, pitch, step, note.get("guitarEffect", {}), gliss, note.get("performanceAttack"), note['staffCount']]
                tones.append(tone)
                previous[lane] = tone
        offset += page["totalBeats"]
    active = {}
    channel_parts = {}
    pedal_controls = []
    pedal_offset = 0.
    for page in document['pages']:
        pedal_controls.extend(dict(c, tick=round((pedal_offset+c['beat'])*ppq))
                              for c in page.get('pedalControls', []))
        pedal_offset += page['totalBeats']
    has_pedal = bool(pedal_controls)
    pedal_spans = []
    held_parts = {}
    for control in sorted(pedal_controls, key=lambda c: (c['tick'], c['value'])):
        part = control['staffCount']
        if control['value']:
            held_parts[part] = control['tick']
        elif part in held_parts:
            pedal_spans.append((part, held_parts.pop(part), control['tick']))
    performed = []
    for start, end, pitch, step, effect, gliss, attack, part in tones:
        if gliss is not None:
            performed.extend((a, b, midi, effect, attack if a == start else None, part) for a, b, midi in
                             white_key_gliss(start, end, pitch, gliss["targetMidi"]))
        elif step:
            performed.extend((tick, min(end, tick + step), pitch, effect, attack if tick == start else None, part) for tick in range(start, end, step))
        else:
            performed.append((start, end, pitch, effect, attack, part))
    for start, end, pitch, effect, attack, part in sorted(performed, key=lambda n: (n[0], n[2])):
        kind = effect.get("type", "none")
        delta = effect.get("semitones", 0)
        if kind not in ("none", "slide", "hammer_on", "pull_off", "bend", "bend_release", "dead", "harmonic", "tap") or not isinstance(delta, (int, float)) or not math.isfinite(delta) or abs(delta) > 24:
            raise ValueError("Unsupported guitar performance effect")
        expressive = kind in ("slide", "bend", "bend_release") or effect.get("vibrato", False) or attack is not None
        # Pitch bend is channel-wide. Isolate it from every overlapping note, including other pitches.
        channel = next((c for c in range(16) if c != 9 and (not has_pedal or channel_parts.get(c, part) == part) and all(
            stop <= start or (not expressive and not bent and other != pitch)
            for stop, other, bent in active.get(c, []))), None)
        if channel is None:
            raise ValueError("MIDI channel capacity exceeded by overlapping voices/effects")
        if has_pedal:
            channel_parts[channel] = part
        active[channel] = [(stop, other, bent) for stop, other, bent in active.get(channel, []) if stop > start]
        release = max([end]+[up for count, down, up in pedal_spans if count == part and down < end < up])
        active[channel].append((release, pitch, expressive))
        velocity = 20 if kind == "dead" else 62 if kind in ("hammer_on", "pull_off", "tap") else 80
        if attack is not None:
            velocity = max(1, min(127, round(velocity*attack['gain'])))
            ramp = max(1, round(attack['seconds']*ppq*bpm/60))
            for index in range(7):
                tick = start+round(ramp*index/6)
                if tick >= end:
                    break
                gain = attack['gain']+(attack['settledGain']-attack['gain'])*index/6
                expression = max(0, min(127, round(127*gain/attack['gain'])))
                events.append((tick, 2, bytes([0xB0 | channel, 11, expression])))
        elif expressive_document:
            # Exclusive attack channels may be reused after their release.
            events.append((start, 2, bytes([0xB0 | channel, 11, 127])))
        sounding_end = start + max(1, round((end-start) * (.12 if kind == "dead" else .45 if effect.get("palmMute") else 1)))
        if expressive:
            # RPN 0: +/-24 semitones, confined to this note's exclusive channel.
            for controller, value in ((101, 0), (100, 0), (6, 24), (38, 0), (101, 127), (100, 127)):
                events.append((start, 1, bytes([0xB0 | channel, controller, value])))
            count = max(2, min(256, (end-start)//15))
            for i in range(count+1):
                t = i/count
                smooth = lambda x: x*x*(3-2*x)
                pitch_offset = delta*(1-smooth(min(1, t/.3))) if kind == "slide" else delta*smooth(min(1,t/.4)) if kind == "bend" else delta*(smooth(t/.5) if t<.5 else 1-smooth((t-.5)/.5)) if kind == "bend_release" else 0
                if effect.get("vibrato"):
                    age = t*(end-start)/ppq*60/bpm
                    pitch_offset += .18*math.sin(age*math.pi*10)*min(1,t*8)
                bend = max(0,min(16383,round(8192+pitch_offset/24*8192)))
                tick = start+round((end-start)*t)
                if tick < sounding_end:
                    events.append((tick, 2, bytes([0xE0 | channel, bend & 127, bend >> 7])))
            events.append((release, 1, bytes([0xE0 | channel, 0, 64])))
        events.extend([(start, 3, bytes([0x90 | channel, pitch, velocity])),
                       (sounding_end, 0, bytes([0x80 | channel, pitch, 0]))])
    # Pedal is channel-wide too. Keep unrelated parts isolated and release before redepress.
    for part in sorted({c['staffCount'] for c in pedal_controls}):
        if part not in channel_parts.values():
            channel = next((c for c in range(16) if c != 9 and c not in channel_parts), None)
            if channel is None:
                raise ValueError('MIDI channel capacity exceeded by pedal parts')
            channel_parts[channel] = part
    for control in sorted(pedal_controls, key=lambda c: (c['tick'], c['value'])):
        for channel, part in channel_parts.items():
            if part == control['staffCount']:
                events.append((control['tick'], 1 if control['value'] == 0 else 2,
                               bytes([0xb0 | channel, 64, control['value']])))
    # The selected percussion address is fixed. Overlap cannot retain independent
    # attacks reliably on one MIDI channel/address, so require a routing decision.
    previous_end = -1
    percussion_expression = 127
    for start, end, address, velocity, attack, reference in sorted(percussion, key=lambda value: value[:4]):
        if start < previous_end:
            raise ValueError('Overlapping unpitched attacks require an explicit multi-address percussion routing policy')
        previous_end = end
        if attack is None:
            if percussion_expression != 127:
                events.append((start, 2, bytes([0xb9, 11, 127])))
                percussion_expression = 127
        else:
            ramp = max(1, round(attack['seconds']*ppq*bpm/60))
            for index in range(7):
                tick = start+round(ramp*index/6)
                if tick >= end:
                    break
                gain = attack['gain']+(attack['settledGain']-attack['gain'])*index/6
                percussion_expression = max(0,min(127,round(127*gain/reference)))
                events.append((tick, 2, bytes([0xb9, 11, percussion_expression])))
        events.extend([(start, 3, bytes([0x99, address, velocity])),
                       (end, 0, bytes([0x89, address, 0]))])
    ordered = sorted(events, key=lambda x: (x[0], x[1]))
    return ppq, ordered, max(ordered[-1][0], round(offset * ppq))


def white_key_gliss(start, end, source, target):
    """Hold two thirds, then play intervening white keys; target stays a written note."""
    direction = 1 if target > source else -1
    pitches = [midi for midi in range(source + direction, target, direction)
               if midi % 12 in (0, 2, 4, 5, 7, 9, 11)]
    if not pitches:
        return [(start, end, source)]
    hold = start + round((end-start)*2/3)
    result = [(start, hold, source)] if hold > start else []
    for index, midi in enumerate(pitches):
        a = hold + round((end-hold)*index/len(pitches))
        b = hold + round((end-hold)*(index+1)/len(pitches))
        if b > a:
            result.append((a, b, midi))
    return result


def write_midi(document, path, bpm=120, *, unpitched_preview=None):
    ppq, events, end_tick = performance_events(document, bpm, unpitched_preview=unpitched_preview)
    track = bytearray()
    last = 0
    for tick, _, message in events:
        track.extend(variable_length(tick - last))
        track.extend(message)
        last = tick
    # Rests and skipped voices still occupy performed score time.
    track.extend(variable_length(end_tick - last))
    track.extend(b"\xff\x2f\x00")
    Path(path).write_bytes(b"MThd" + struct.pack(">IHHH", 6, 0, 1, ppq) + b"MTrk" + struct.pack(">I", len(track)) + track)
