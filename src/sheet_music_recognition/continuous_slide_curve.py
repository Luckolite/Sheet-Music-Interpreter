# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Continuous approach bends on the final emitted MIDI tempo clock."""
import bisect
import math


def _clock(tempo_events, ppq):
    changes = [(0, 500000)]
    for tick, _, data in sorted(tempo_events, key=lambda item: (item[0], item[1])):
        if data[:3] != b'\xff\x51\x03':
            continue
        if type(tick) is not int or tick < 0 or len(data) != 6:
            raise ValueError('Malformed final MIDI tempo event')
        micros = int.from_bytes(data[3:], 'big')
        if micros <= 0:
            raise ValueError('Nonpositive final MIDI tempo')
        if changes[-1][0] == tick:
            changes[-1] = (tick, micros)
        else:
            changes.append((tick, micros))
    ticks, times = [], []
    seconds = 0.
    for i, (tick, micros) in enumerate(changes):
        if i:
            seconds += (tick-changes[i-1][0])*changes[i-1][1]/(1_000_000*ppq)
        ticks.append(tick)
        times.append(seconds)

    def seconds_at(tick):
        i = bisect.bisect_right(ticks, tick)-1
        return times[i]+(tick-ticks[i])*changes[i][1]/(1_000_000*ppq)

    def tick_at(seconds):
        i = bisect.bisect_right(times, seconds)-1
        return ticks[i]+(seconds-times[i])*(1_000_000*ppq)/changes[i][1]

    return seconds_at, tick_at, ticks


def gated_end(start, end, gate, tempo_events, ppq=480):
    """APP gate scales elapsed frame/time duration, not written beat duration."""
    if (type(start) is not int or type(end) is not int or start < 0 or end <= start
            or type(ppq) is not int or ppq <= 0
            or type(gate) not in (int, float) or gate not in (.28, .5, .75, 1.)):
        raise ValueError('Invalid continuous slide articulation gate')
    if gate == 1:
        return end
    seconds_at, tick_at, _ = _clock(tempo_events, ppq)
    begin = seconds_at(start)
    release = tick_at(begin+(seconds_at(end)-begin)*gate)
    # Positive half-up rounding matches the APP frame gate, to MIDI tick resolution.
    return min(end, max(start+1, math.floor(release+.5)))


def curve(start, end, source_midi, target_midi, tempo_events, ppq=480):
    """Return RPN semitone range and (tick, 14-bit bend) samples, excluding note end.

    Tempo events use the MIDI writer's exact (tick, priority, bytes) representation.
    Equal-tick tempos retain stable writer order; the last controls following time.
    """
    if (type(start) is not int or type(end) is not int or start < 0 or end <= start
            or type(ppq) is not int or ppq <= 0
            or any(type(p) is not int or not 0 <= p <= 127 for p in (source_midi, target_midi))):
        raise ValueError('Continuous slide requires finite MIDI endpoints and ticks')
    seconds_at, tick_at, ticks = _clock(tempo_events, ppq)

    begin = seconds_at(start)
    duration = seconds_at(end)-begin
    ramp = max(.001, min(.16, .3*duration))
    delta = source_midi-target_midi
    semitones = max(1, abs(delta))
    finish = min(end-1, math.ceil(tick_at(begin+ramp)))
    sample_ticks = {start, finish}
    sample_ticks.update(min(finish, max(start, round(tick_at(begin+ramp*i/64)))) for i in range(65))
    sample_ticks.update(tick for tick in ticks if start < tick <= finish)
    samples = []
    for tick in sorted(sample_ticks):
        age = seconds_at(tick)-begin
        t = min(1., max(0., age/ramp))
        offset = delta*(1-t*t*(3-2*t))
        scale = 8191 if offset >= 0 else 8192
        bend = round(8192+offset/semitones*scale)
        if not 0 <= bend <= 16383 or not math.isfinite(offset):
            raise ValueError('Continuous slide bend exceeds declared RPN range')
        samples.append((tick, bend))
        if t == 1:
            break
    return semitones, samples
