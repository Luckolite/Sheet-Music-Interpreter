# Retained branch integration

Historical branch and worktree review distinguished missing behavior from fixes
already ported through later commits. Old decoder snapshots must not replace the
newer implementation simply because their branch names remain present.

## Reconciled history

The retained PDF-word adapter commit `889454bf` is now reconciled with main.
Its adapter sources, generated-PDF regressions, documentation, and all six
semantic provenance additions were already present through `052ebc47` and later
reviewed commits. The reconciliation preserves the current source map and has no
implementation changes. Earlier local branch and detached worktree heads were
already ancestral; amended/rebased reflog versions retain equivalent or newer
feature implementations and tests on main.

## Recovered regression packet

Three original procedural assertions from an unfinished flag/tremolo packet
exposed behavior missing from the current decoder:

- One faded returning flag was lost when its raw shaft failed the ordinary dark
  threshold.
- Two faded returning flags lost one written beam under the same shaft-ownership
  failure.
- A broad semantic mask over a complete raw tremolo stroke remained a competing
  head and suppressed the tremolo ornament.

The port retains the modern ownership guards rather than restoring the packet's
older beam/root algorithms. A pale shaft must independently satisfy bounded
head/shaft geometry, semantic shaft ownership, continuous source contrast, and
the existing curved-flag proof. The current returning-root count remains in
charge. Tremolo mask islands use an integer pixel-area ceiling and bounded size
and area-ratio gates. Islands admitted by those expanded gates must straddle the
proven shaft with a centered mask and still satisfy the existing bounded
bilateral raw-stroke checks. Side-attached chord heads retain their veto;
ordinary rhythm beams remain independent of tremolo strokes.

The regressions use original painted notation and independent negative controls.
They do not contain score crops, private diagnostic logs, song-name rules, new
models, or changed record layouts. Recognition-derived cache identity changes
to revision 121 without changing Guide299 or audio101 wire formats. The native
decoder source manifest explicitly includes the new shaft helper. Source
qualification does not claim a new installed app or desktop worker.

## Deliberately excluded historical states

An untested dark-background staff-rejection experiment was not qualified for
integration. It removes staffs from broad dark/varied regions using additional
thresholds, with neither a self-contained regression nor an independently
verified current failure. Its historical worktree remains preserved for review.

An older sharp test expected rejection after erasing only the semantic mask while
leaving the complete printed source glyph. Current tests intentionally distinguish
that valid printed glyph from a true isolated speck that erases both source and
mask. Restoring the old expectation would regress source authority. A renamed
paper-tail test and other loose synthetic tests are already represented by the
current, stronger regression suite.
