# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original decoded logical notes, testing the actual Java route and emitted MIDI."""
import copy
import tempfile
import unittest
from pathlib import Path
from sheet_music_recognition.navigation import project_navigation
from sheet_music_recognition.midi import write_midi


def note(beat, pitch, duration=1, tied=False):
    return dict(startBeat=beat, midi=pitch, durationBeats=duration, tiedFromPrevious=tied,
                staffIndex=0, staffCount=1)


def document(beats, notes, directions, tempos=()):
    return dict(pages=[dict(measureBeats=beats, totalBeats=sum(beats), events=notes,
        score=dict(playbackDirections=directions, tempoChanges=list(tempos)))])


def direction(boundary, kind):
    return dict(measureBoundary=boundary, kind=kind)


def final_tick(data):
    cursor,tick=22,0  # MThd + format0 MTrk header; writer emits explicit statuses.
    def variable():
        nonlocal cursor
        value=0
        while True:
            byte=data[cursor];cursor+=1;value=(value<<7)|(byte&127)
            if byte<128:return value
    while cursor<len(data):
        tick+=variable();status=data[cursor];cursor+=1
        if status==255:
            kind=data[cursor];cursor+=1;length=variable();cursor+=length
            if kind==47:return tick
        else:cursor+=1 if status&240 in (192,208) else 2
    raise AssertionError('Missing MIDI end of track')


def note_intervals(data):
    cursor,tick=22,0
    active,intervals={},[]
    def variable():
        nonlocal cursor
        value=0
        while True:
            byte=data[cursor];cursor+=1;value=(value<<7)|(byte&127)
            if byte<128:return value
    while cursor<len(data):
        tick+=variable();status=data[cursor];cursor+=1
        if status==255:
            kind=data[cursor];cursor+=1;length=variable();cursor+=length
            if kind==47:break
            continue
        kind=status&240;length=1 if kind in (192,208) else 2
        payload=data[cursor:cursor+length];cursor+=length
        if kind not in (128,144):continue
        key=(status&15,payload[0])
        if kind==144 and payload[1]:
            if key in active:raise AssertionError('Overlapping MIDI note on one channel')
            active[key]=tick
        else:
            if key not in active:raise AssertionError('Release without MIDI attack')
            intervals.append((payload[0],active.pop(key),tick))
    if active:raise AssertionError('Unreleased MIDI notes')
    return sorted(intervals,key=lambda row:(row[1],row[0]))


class NavigationMidiTest(unittest.TestCase):
    def test_tied_marker_cannot_bridge_a_whole_silent_source_bar(self):
        doc=document([4,4,4],[note(0,60,4),note(8,60,4,True)],[])
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'silent-bar.mid';write_midi(doc,path)
            self.assertEqual([(60,0,1920),(60,3840,5760)],note_intervals(path.read_bytes()))
            self.assertEqual(5760,final_tick(path.read_bytes()))

    def test_partial_fine_emits_exact_pitch_attack_and_release_oracle(self):
        fine=dict(measureBoundary=0,kind=12,details=dict(eventId='fine',quarterBeatOffset=1.5))
        doc=document([4,4,4],[note(0,60),note(1,62),note(2,64),note(3,65),
                             note(4,67,4),note(8,69,4)],[direction(2,9),fine])
        expected=[(60,0,480),(62,480,960),(64,960,1440),(65,1440,1920),
                  (67,1920,3840),(60,3840,4320),(62,4320,4560)]
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'partial.mid';write_midi(doc,path)
            self.assertEqual(expected,note_intervals(path.read_bytes()))
            self.assertEqual(4560,final_tick(path.read_bytes()))

    def test_jittered_incoming_tie_cannot_attach_to_skipped_predecessor(self):
        doc=document([4,4,4],[note(0,60,4),note(4.02,60,3.98,True),note(8,60,4)],
                     [direction(1,4),direction(3,5)])
        notes=project_navigation(doc)['pages'][0]['events']
        self.assertEqual([False,True,False,False,False],
                         [n['tiedFromPrevious'] for n in notes])
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'incoming.mid';write_midi(doc,path)
            self.assertEqual(4,path.read_bytes().count(b'\x90\x3c\x50'))

    def test_internal_tie_remains_owned_on_each_return(self):
        doc=document([4,4,4],[note(0,60,4),note(4.02,60,1.98),
                             note(6,60,2,True),note(8,60,4)],
                     [direction(1,4),direction(3,5)])
        notes=project_navigation(doc)['pages'][0]['events']
        self.assertEqual([False,False,True,False,False,True,False],
                         [n['tiedFromPrevious'] for n in notes])
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'internal.mid';write_midi(doc,path)
            self.assertEqual(5,path.read_bytes().count(b'\x90\x3c\x50'))

    def test_partial_ending_and_trailing_rests_retain_exact_midi_extent(self):
        fine=dict(measureBoundary=0,kind=12,details=dict(eventId='fine',quarterBeatOffset=1.5))
        docs=[(document([4,4,4],[note(0,60)],[direction(2,9),fine]),4560),
              (document([4],[note(0,60)],[]),1920)]
        with tempfile.TemporaryDirectory() as folder:
            for index,(doc,expected) in enumerate(docs):
                path=Path(folder)/f'extent-{index}.mid';write_midi(doc,path)
                self.assertEqual(expected,final_tick(path.read_bytes()))

    def test_alternate_arrangements_do_not_jump_into_each_other(self):
        first=document([4],[note(0,60)],[direction(1,7)])['pages'][0]
        second=document([4],[note(0,72)],[direction(1,7)])['pages'][0]
        first['score']['firstMeasureNumber']=1;second['score']['firstMeasureNumber']=1
        played=project_navigation(dict(pages=[first,second]))
        self.assertEqual([[60,60],[72,72]],[[n['midi'] for n in page['events']] for page in played['pages']])
        self.assertNotEqual(played['pages'][0]['events'][0]['sourceEventId'],played['pages'][1]['events'][0]['sourceEventId'])

    def test_actual_kernel_repeats_notes_and_midi_attacks_without_inference(self):
        doc = document([4,4,4], [note(0,60),note(4,62),note(8,64)],
                       [direction(0,4),direction(2,5)])
        before = copy.deepcopy(doc)
        played = project_navigation(doc)
        self.assertEqual([60,62,60,62,64], [n['midi'] for n in played['pages'][0]['events']])
        self.assertEqual(20, played['pages'][0]['totalBeats'])
        self.assertEqual(before, doc)
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'original.mid'; write_midi(doc,path)
            self.assertEqual(2,path.read_bytes().count(b'\x90\x3c\x50'))

    def test_partial_fine_clips_sustain_and_does_not_invent_whole_bar(self):
        fine=dict(measureBoundary=0,kind=12,details=dict(eventId='fine',quarterBeatOffset=1.5))
        doc=document([4,4,4],[note(1,60,2)],[direction(2,9),fine])
        played=project_navigation(doc)['pages'][0]
        self.assertEqual(9.5,played['totalBeats'])
        self.assertEqual([2,.5],[n['durationBeats'] for n in played['events']])
        self.assertFalse(played['events'][-1]['tiedFromPrevious'])

    def test_return_into_sustained_note_rearticulates_only_at_actual_jump(self):
        doc=document([4,4],[note(0,60,8)],[direction(0,4),direction(2,5)])
        notes=project_navigation(doc)['pages'][0]['events']
        self.assertEqual([False,True,False,True],[n['tiedFromPrevious'] for n in notes])
        self.assertEqual([0,4,8,12],[n['startBeat'] for n in notes])
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'held.mid';write_midi(doc,path)
            self.assertEqual(2,path.read_bytes().count(b'\x90\x3c\x50'))

    def test_destination_restores_numeric_tempo_without_later_state_leaking_back(self):
        doc=document([4,4,4],[],[direction(0,4),direction(2,5)],
            [dict(measureIndex=1,positionInMeasure=0,bpm=60)])
        tempo=project_navigation(doc,120)['pages'][0]['score']['tempoChanges']
        self.assertEqual([120,60,120,60],[t['bpm'] for t in tempo])

    def test_cross_page_repeat_uses_one_actual_route(self):
        first=document([4],[note(0,60)],[direction(0,4)])['pages'][0]
        second=document([4],[note(0,62)],[direction(1,5)])['pages'][0]
        played=project_navigation(dict(pages=[first,second]))['pages'][0]
        self.assertEqual([60,62,60,62],[n['midi'] for n in played['events']])
        self.assertEqual(16,played['totalBeats'])

    def test_coda_omits_notes_but_keeps_route_time_and_tail_rests(self):
        doc=document([4]*10,[note(i*4,60+i) for i in range(10)],
            [direction(7,11),direction(3,1),direction(8,3)])
        played=project_navigation(doc)['pages'][0]
        self.assertEqual([60,61,62,63,64,65,66,60,61,62,68,69],[n['midi'] for n in played['events']])
        self.assertEqual(48,played['totalBeats'])

    def test_linear_export_does_not_call_java_or_change_input(self):
        doc=document([4],[note(0,60)],[])
        self.assertIs(doc,project_navigation(doc))


if __name__=='__main__': unittest.main()
