# Dense interrupted barlines

The raw barline gate required one long dark run before checking the complete
fixed-axis staff-space proofs. A few scan-row interruptions near a middle staff
rule could reject a densely observed real bar before those stronger checks ran.

The additional path requires both endpoints and at least 90 percent aggregate
coverage. It then applies the existing counter, fixed-axis, every-space, branch,
oscillation, note-owner and rest-owner guards. Horizontal rules cannot supply a
missing staff space, and independent dark pixels cannot be followed along a
different axis on each row. Every originally successful path retains its original
work and return behavior. No raw threshold, model or record layout changes.

The new branch allocates no new production arrays. Only a dense candidate that
previously failed the long-run test reaches the existing downstream checks.
Those checks retain at most two axis slopes and five origins, each traversing the
bounded staff span. Existing glyph and raster bounds remain unchanged; this is
not a claim of unchanged worst-case work or a new whole-process resource bound.

Five original generated regressions use procedural rules and scan interruptions
at three scales, including gray paper, a missing-space negative, rules alone and
an ordinary unbroken bar. The interrupted positives fail the previous helper;
all five pass the candidate. No commercial score, library or model capture is
included. The saved geometry qualification is separate from complete recognition,
note timing, conversion, native packaging and physical-device validation.
