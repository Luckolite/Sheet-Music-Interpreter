# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Original transport framing and preflight-before-destination checks."""
from pathlib import Path
import struct
import tempfile
import unittest
from sheet_interpreter import beam_source, guide_writer
class BeamTransportPreflightTest(unittest.TestCase):
    def test_empty_and_detached_frames_preserve_current_body(self):
        score=dict(measures=[],notes=[],firstMeasureNumber=0,keyChanges=[],tempoChanges=[],meterChanges=[],rests=[],techniqueChanges=[],dynamicChanges=[],playbackDirections=[],expressiveEvents=[])
        known=dict(groups=[],detachedColumns=[dict(members=[dict(pageIndex=0,noteIndex=0)])],noteOrigins=[])
        old=guide_writer.encode(score,296)
        for source in [None,known]:
            frame=beam_source.encode_source(source,0)
            self.assertEqual(beam_source.checked(source,0),beam_source.decode_source(frame,0))
            self.assertEqual(struct.pack('>i',297)+old[4:]+frame,guide_writer.encode(dict(score,beamSource=source),297))
        with tempfile.TemporaryDirectory()as folder:
            root=Path(folder);dest=root/'must-not-exist'/'new.guide'
            with self.assertRaises(ValueError):guide_writer.write_new(dest,dict(score,beamSource=known),296)
            self.assertFalse(dest.parent.exists())
            dest=root/'existing.guide';dest.write_bytes(b'original destination')
            with self.assertRaises(FileExistsError):guide_writer.write_new(dest,dict(score,beamSource=known),296)
            self.assertEqual(b'original destination',dest.read_bytes())
if __name__=='__main__':unittest.main()
