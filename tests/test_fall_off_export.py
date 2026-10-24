# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original exact-note technical-mark examples; no commercial score material."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml
try:
    from sheet_interpreter.fall_off_notation import target, retained
except ImportError:
    import importlib.util
    spec=importlib.util.spec_from_file_location('original_target',Path(__file__).with_name('fall_off_notation.py'))
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    target,retained=module.target,module.retained


def document(owner=1, cross_bar=False):
    notes=[];events=[]
    for i,(step,pitch,start,duration,x,y) in enumerate([(0,60,0,2,.2,.4),(2,64,0,2,.2,.35),(4,67,0,2,.2,.3),(2,64,2,2,.7,.35)]):
        notes.append(dict(measureIndex=0,positionInMeasure=x,pageY=y,staffStep=step,staffIndex=0,staffCount=1))
        events.append(dict(measureIndex=0,staffIndex=0,staffCount=1,sourceNoteIndex=i,startBeat=start,durationBeats=duration,
                           midi=pitch,durationFallback=False,tiedFromPrevious=False))
    if cross_bar:
        notes=notes[1:2];events=events[1:2];events[0].update(sourceNoteIndex=0,startBeat=2,durationBeats=4);owner=0
    mark=dict(eventId='synthetic:falloff',kind='FALL_OFF',scope='NOTE',staffIndex=0,staffCount=1,
              targetEventId=target(notes[owner]),start=dict(measureIndex=0,quarterBeatOffset=events[owner]['startBeat']),
              end=None,strength='UNSPECIFIED',qualifierText='falloff-v1:curved',
              evidence=[dict(sourceId='printed-fall-off',pageIndex=0,visualX=.2,staffIndex=0,staffCount=1,printedText='○')])
    return dict(pages=[dict(events=events,measureBeats=[4,4] if cross_bar else [4],totalBeats=8 if cross_bar else 4,
                           score=dict(firstMeasureNumber=1,notes=notes,measures=[{}]* (2 if cross_bar else 1),
                                      tempoChanges=[],expressiveEvents=[mark]))])


class FallOffExportOriginals(unittest.TestCase):
    def export(self,doc):
        before=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'original.musicxml';write_musicxml(doc,path);root=ET.parse(path).getroot()
        self.assertEqual(before,doc)
        return root

    def test_chord_member_and_repeated_pitch_keep_one_exact_owner(self):
        doc=document();root=self.export(doc)
        notes=[n for n in root.findall('.//note') if n.find('pitch') is not None]
        self.assertEqual(['C','E','G','E'],[n.findtext('pitch/step')for n in notes])
        self.assertEqual([False,True,False,False],[n.find('notations/articulations/falloff')is not None for n in notes])
        self.assertEqual([],root.findall('.//words'))
        bare=document();bare['pages'][0]['score']['expressiveEvents']=[];old=self.export(bare)
        self.assertEqual([n.findtext('duration')for n in old.findall('.//note')],[n.findtext('duration')for n in root.findall('.//note')])

    def test_cross_bar_tied_slices_emit_on_release_only(self):
        root=self.export(document(cross_bar=True));notes=[n for n in root.findall('.//note')if n.find('pitch')is not None]
        self.assertEqual(2,len(notes))
        self.assertEqual([False,True],[n.find('notations/articulations/falloff')is not None for n in notes])
        self.assertIsNotNone(notes[0].find('tie'));self.assertIsNotNone(notes[1].find('tie'))

    def test_unresolved_page_owner_binds_only_after_whole_score_timing(self):
        doc = document()
        mark = doc['pages'][0]['score']['expressiveEvents'][0]
        mark['scope'] = 'UNRESOLVED'
        mark['start'] = None
        root = self.export(doc)
        notes = [n for n in root.findall('.//note') if n.find('pitch') is not None]
        self.assertEqual([False, True, False, False],
                         [n.find('notations/articulations/falloff') is not None for n in notes])
        self.assertEqual([], root.findall('.//words'))

    def test_wrong_or_ambiguous_ownership_never_falls_back_to_words(self):
        mutations=[lambda p,m:m.update(scope='PART'),lambda p,m:m.update(targetEventId='note:unbound'),
                   lambda p,m:m.update(end=dict(measureIndex=0,quarterBeatOffset=2)),
                   lambda p,m:m.update(staffCount=2),lambda p,m:m['evidence'][0].update(sourceId='ocr:zero'),
                   lambda p,m:m['start'].update(quarterBeatOffset=1),
                   lambda p,m:p['events'][1].update(sourceNoteIndex=2),
                   lambda p,m:p['events'][1].update(durationFallback=True),
                   lambda p,m:p['events'][1].pop('sourceNoteIndex'),
                   lambda p,m:p['score']['notes'].__setitem__(2,copy.deepcopy(p['score']['notes'][1]))]
        for change in mutations:
            with self.subTest(change=change):
                doc=document();p=doc['pages'][0];change(p,p['score']['expressiveEvents'][0]);root=self.export(doc)
                self.assertEqual([],root.findall('.//falloff'));self.assertEqual([],root.findall('.//words'))

    def test_distant_similar_notes_do_not_match_float32_identity(self):
        doc=document();page=doc['pages'][0];page['score']['notes'][1]['positionInMeasure']+=.00001
        self.assertEqual(frozenset(),retained(page));self.assertEqual([],self.export(doc).findall('.//falloff'))

    def test_anchor_must_match_exact_final_attack_tick(self):
        doc = document()
        mark = doc['pages'][0]['score']['expressiveEvents'][0]
        mark['start']['quarterBeatOffset'] += 1 / 10080
        root = self.export(doc)
        self.assertEqual([], root.findall('.//falloff'))

    def test_duplicate_physical_owner_blocks_even_if_one_anchor_is_wrong(self):
        doc = document()
        score = doc['pages'][0]['score']
        wrong = copy.deepcopy(score['expressiveEvents'][0])
        wrong['eventId'] = 'synthetic:wrong-tick-duplicate'
        wrong['start']['quarterBeatOffset'] += 1 / 10080
        score['expressiveEvents'].append(wrong)
        root = self.export(doc)
        self.assertEqual([], root.findall('.//falloff'))

    def test_unresolved_owner_must_not_carry_a_start_anchor(self):
        doc = document()
        mark = doc['pages'][0]['score']['expressiveEvents'][0]
        mark['scope'] = 'UNRESOLVED'
        mark['start'] = dict(measureIndex=0, quarterBeatOffset=0)
        root = self.export(doc)
        self.assertEqual([], root.findall('.//falloff'))
    def test_valid_target_requires_one_pitched_note(self):
        p=document()['pages'][0];n=copy.deepcopy(p['score']['notes'][1]);n['kind']='UNPITCHED'
        self.assertIsNone(target(n));n=copy.deepcopy(p['score']['notes'][1]);n['pageY']=float('nan');self.assertIsNone(target(n))


if __name__=='__main__':unittest.main()

