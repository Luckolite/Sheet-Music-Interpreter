# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Tab performance semantics, independent of written pitch and duration."""
import math

KINDS = {'none', 'slide', 'hammer_on', 'pull_off', 'bend', 'bend_release',
         'dead', 'harmonic', 'tap', 'whammy', 'whammy_release', 'pre_bend',
         'strum_down', 'strum_up'}


def validate(effect):
    kind = effect.get('type', 'none')
    delta = effect.get('semitones', 0)
    rank = effect.get('strumIndex', 0)
    if (kind not in KINDS or type(delta) not in (int, float)
            or not math.isfinite(delta) or abs(delta) > 24
            or type(rank) is not int or not 0 <= rank <= 6
            or (kind.startswith('strum_') and delta != 0)
            or (not kind.startswith('strum_') and rank != 0)):
        raise ValueError('Unsupported guitar performance effect')
    return kind, delta, rank


def pitch_offset(kind, delta, t):
    smooth = lambda x: x*x*(3-2*x)
    if kind == 'slide':
        return delta*(1-smooth(min(1, t/.3)))
    if kind in ('bend', 'whammy'):
        return delta*smooth(min(1, t/.4))
    if kind in ('bend_release', 'whammy_release'):
        return delta*(smooth(t/.5) if t < .5 else 1-smooth((t-.5)/.5))
    return delta if kind == 'pre_bend' else 0
