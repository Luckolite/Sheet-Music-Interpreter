# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original owned grace groups with separate written and playback clocks."""
import copy
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml, DIVISIONS


def document(after=False):
    heads=[dict(measureIndex=0,staffIndex=0,staffCount=1,articulations=32768) for _ in range(2)]
    heads.append(dict(measureIndex=0,staffIndex=0,staffCount=1,articulations=0))
    events=[]
    for i,h in enumerate(heads):
        grace=i<2
        p=dict(onsetBeats=(4 if after else 0) if grace else 0,durationBeats=.125 if grace else 4,
               principalIndex=2 if grace else -1,ordinal=i if grace else -1,
               afterGrace=after if grace else False,stealPercent=3.125 if grace else 0)
        events.append(dict(h,midi=60+i,startBeat=(3.75+i*.125 if after else i*.125) if grace else (0 if after else .25),
                           durationBeats=.125 if grace else 3.75,writtenPlacement=p))
    return dict(pages=[dict(measureBeats=[4],events=events,score=dict(notes=heads))])


class MusicXmlGraceTests(unittest.TestCase):
    def export(self, doc):
        before=copy.deepcopy(doc)
        with tempfile.TemporaryDirectory(prefix='original-grace-export-') as folder:
            path=Path(folder)/'score.musicxml';write_musicxml(doc,path);root=ET.parse(path).getroot()
        self.assertEqual(before,doc)
        return root

    def test_prefix_graces_are_zero_clock_and_principal_keeps_full_whole(self):
        notes=self.export(document()).findall('.//note[pitch]')
        self.assertEqual([True,True,False],[n.find('grace') is not None for n in notes])
        self.assertEqual(['C','C','D'],[n.findtext('pitch/step') for n in notes])
        self.assertEqual([None,None,str(4*DIVISIONS)],[n.findtext('duration') for n in notes])
        self.assertEqual(['32nd','32nd','whole'],[n.findtext('type') for n in notes])
        self.assertEqual(['3.125','3.125'],[n.find('grace').get('steal-time-following') for n in notes[:2]])

    def test_after_graces_follow_written_release_in_original_ordinal_order(self):
        notes=self.export(document(True)).findall('.//note[pitch]')
        self.assertEqual([False,True,True],[n.find('grace') is not None for n in notes])
        self.assertEqual(['D','C','C'],[n.findtext('pitch/step') for n in notes])
        self.assertEqual(['3.125','3.125'],[n.find('grace').get('steal-time-previous') for n in notes[1:]])

    def test_playback_clocks_are_never_overwritten(self):
        doc=document();before=copy.deepcopy(doc['pages'][0]['events']);self.export(doc)
        self.assertEqual(before,doc['pages'][0]['events'])

    def test_unowned_or_legacy_grace_reports_missing_metadata(self):
        doc=document();doc['pages'][0]['events'][0].pop('writtenPlacement')
        with self.assertRaisesRegex(ValueError,'retained written placement'):self.export(doc)

    def test_wrong_owner_context_and_partial_ordinals_are_refused(self):
        for change in ('principal','ordinal','onset','staff','budget'):
            doc=document();e=doc['pages'][0]['events'][0];p=e['writtenPlacement']
            if change=='principal':p['principalIndex']=0
            elif change=='ordinal':p['ordinal']=1
            elif change=='onset':p['onsetBeats']=1
            elif change=='staff':e['staffIndex']=1
            else:p['stealPercent']=99
            with self.assertRaises(ValueError):self.export(doc)

    def test_removed_source_head_cannot_be_borrowed(self):
        doc=document();doc['pages'][0]['events'].pop(0)
        with self.assertRaisesRegex(ValueError,'one event per retained'):self.export(doc)

    def test_grace_to_principal_slur_uses_written_grace_order(self):
        doc=document();doc['pages'][0]['score']['slurSource']=dict(
            spans=[dict(id='original',start=dict(pageIndex=0,noteIndex=0),end=dict(pageIndex=0,noteIndex=2),
                        placement='ABOVE',lineType='SOLID',evidence=[dict(pageIndex=0,startX=.1,startY=.2,endX=.3,endY=.2)])],candidates=[],
            noteOrigins=[dict(pageIndex=0,noteIndex=i) for i in range(3)])
        notes=self.export(doc).findall('.//note[pitch]')
        self.assertEqual('start',notes[0].find('notations/slur').get('type'))
        self.assertEqual('stop',notes[2].find('notations/slur').get('type'))

    def test_grace_does_not_gain_ties_or_gap_rests(self):
        root=self.export(document())
        self.assertEqual([],root.findall('.//tie'));self.assertEqual([],root.findall('.//rest'))
        self.assertEqual({'1'},{n.findtext('voice') for n in root.findall('.//note')})

    def test_cross_bar_principal_keeps_prefix_only_on_first_fragment(self):
        doc=document();page=doc['pages'][0];page['measureBeats']=[4,4]
        page['events'][2]['writtenPlacement']['durationBeats']=6.5
        page['events'][2]['durationBeats']=6.25
        root=self.export(doc)
        self.assertEqual(2,len(root.findall('part/measure[1]/note/grace')))
        self.assertEqual([],root.findall('part/measure[2]/note/grace'))
        self.assertEqual(4,len(root.findall('.//tied')))

    def test_cross_bar_after_graces_follow_final_tied_fragment(self):
        doc=document(True);page=doc['pages'][0];page['measureBeats']=[4,4]
        page['events'][2]['writtenPlacement']['durationBeats']=6.5
        page['events'][2]['durationBeats']=6.25
        for i in (0,1):page['events'][i]['writtenPlacement']['onsetBeats']=6.5
        root=self.export(doc)
        self.assertEqual([],root.findall('part/measure[1]/note/grace'))
        self.assertEqual(2,len(root.findall('part/measure[2]/note/grace')))
        self.assertEqual(4,len(root.findall('.//tied')))

    def test_unpitched_grace_is_not_given_a_tonal_realization(self):
        doc=document();e=doc['pages'][0]['events'][0];e.pop('midi');e.update(kind='UNPITCHED',displayStep='C',displayOctave=5)
        with self.assertRaisesRegex(ValueError,'Unpitched grace'):self.export(doc)

    def test_clef_change_precedes_owned_prefix_graces(self):
        doc=document();page=doc['pages'][0]
        for i in range(3):
            page['score']['notes'][i]['clefBottomDiatonic']=18
            page['events'][i]['writtenPlacement']['onsetBeats']=1
        page['events'][2]['writtenPlacement']['durationBeats']=3
        page['events'][2].update(startBeat=1.25,durationBeats=2.75)
        head=dict(measureIndex=0,staffIndex=0,staffCount=1,articulations=0,clefBottomDiatonic=30)
        page['score']['notes'].append(head)
        page['events'].append(dict(head,midi=65,startBeat=0,durationBeats=1,
            writtenPlacement=dict(onsetBeats=0,durationBeats=1,principalIndex=-1,ordinal=-1,afterGrace=False,stealPercent=0)))
        measure=self.export(doc).find('part/measure');current=None
        for node in measure:
            if node.tag=='attributes' and node.find('clef') is not None:current=node.findtext('clef/sign')
            elif node.tag=='note' and node.find('grace') is not None:self.assertEqual('F',current)
