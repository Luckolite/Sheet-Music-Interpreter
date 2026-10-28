# Staff-rule exposure and repeated direction readings

A fixed darkness cutoff could reject five visible staff rules after a
nonclipping exposure shift, even though their local contrast and geometry were
unchanged. The faint-rule path now uses its existing contrast requirement on
both vertical flanks without that absolute brightness veto. It still requires
all five rules, bilateral horizontal support, bounded line thickness, consistent
spacing and phase, agreement across accepted views, and the existing written
head and direction ownership checks. Contrast, geometry and search limits are
unchanged. Bright blank paper, weak contrast, broad bands, missing rules,
one-sided evidence and wrong phase remain insufficient.

Restoring a direction can expose repeated OCR readings whose slightly different
coordinates select different members of one written chord. Printed directions
now coalesce only when the existing column resolver selects exactly the same
nonempty ordered note roster and measure/staff lane, with the same kind,
strength and qualifier. Unresolved words must also have matching normalized
text. Rest targets, empty columns, foreign evidence, distinct semantics and
overlapping but unequal note rosters remain separate. An existing printed event
keeps its identity. No new positional tolerance or beat inference is introduced.

The original regressions cover exposure invariance, source preservation, tempo
attachment, overlapping column membership, existing event identity and negative
ownership controls. The focused app suite passes 236 tests and the standalone
suite passes 224. A fresh five-page native comparison restores three absent
supported tempo words and a character label, preserving every previous
expression and all notes, rests, written rhythms, beams, keys, dynamics and
slurs. Additional meter observations change proof metadata without changing
semantic meter transitions.

The restored tempo grammar remains unresolved where the written clock is not
proved. Separate word-to-column defects for sudden-dynamic modifiers remain
open, including duplicate and shifted bindings; they receive no repair credit.
Models, weights and serialized layouts are unchanged.
Private score images and application data are not distributed. Source/native
acceptance does not establish installed playback or a deployed Hub/worker pair.
