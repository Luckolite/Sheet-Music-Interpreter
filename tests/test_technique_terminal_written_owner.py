# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original written-bar ownership for a terminal after-grace instruction."""
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from sheet_interpreter.musicxml import write_musicxml, DIVISIONS

def document(change_measure=0):
    notes=[dict(measureIndex=m,positionInMeasure=p,staffIndex=0,staffCount=1,staffStep=0,
                articulations=32768 if i==1 else 0)
           for i,(m,p) in enumerate([(0,.12),(0,.9),(1,.2)])]
    events=[]
    for i,(m,start,duration) in enumerate([(0,0,4),(0,3.75,.25),(1,4,4)]):
        events.append(dict(measureIndex=m,staffIndex=0,staffCount=1,sourceNoteIndex=i,
            midi=60,startBeat=start,durationBeats=duration,durationFallback=False,
            writtenPlacement=dict(onsetBeats=4 if i==1 else 0,durationBeats=.25 if i==1 else 4,
                principalIndex=0 if i==1 else -1,ordinal=0 if i==1 else -1,
                afterGrace=i==1,stealPercent=6.25 if i==1 else 0)))
    return dict(initialMeter=[4,4],initialKeyFifths=0,pages=[dict(measureBeats=[4,4],events=events,
        score=dict(notes=notes,techniqueChanges=[dict(measureIndex=change_measure,
            positionInMeasure=.85 if change_measure==0 else .1,staffIndex=0,staffCount=1,technique=0)]))])

class TechniqueTerminalWrittenOwnerOriginalTests(unittest.TestCase):
    def directions(self, doc):
        with tempfile.TemporaryDirectory(prefix='technique-terminal-owner-') as directory:
            path=Path(directory)/'score.musicxml';write_musicxml(doc,path);root=ET.parse(path).getroot()
        return [(m.get('number'),d.findtext('direction-type/words'),int(d.findtext('offset','0')))
                for m in root.findall('.//measure')for d in m.findall('direction')
                if d.find('direction-type/words')is not None]
    def test_after_grace_keeps_nonfinal_written_measure_at_terminal_offset(self):
        self.assertEqual([('1','arco',4*DIVISIONS)],self.directions(document()))
    def test_next_measure_ordinary_instruction_still_starts_in_next_measure(self):
        self.assertEqual([('2','arco',0)],self.directions(document(1)))

if __name__=='__main__':unittest.main()
