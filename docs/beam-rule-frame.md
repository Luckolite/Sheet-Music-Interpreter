# Beam frames from observed rule bands

A complete five-rule witness can have a slightly different spacing from the rounded staff seed. The fallback reader previously intersected phases at that fixed seed gap, rejecting valid observed bands. It now solves the common spacing interval supported by both five-band witnesses and selects the closest supported spacing. A supported nominal frame remains unchanged. The existing two-column interpolation, lane uniqueness, head residual and slope checks still apply.

Each rule band supplies a half-pixel interval. Pairwise lower/upper phase inequalities bound the common gap; both phase intervals must remain nonempty. The fit stays within the existing spacing domain and twelve-percent witness tolerance, and prepays 128 bounded work units. The original raw witness search and ink thresholds are unchanged.

`PrintedBeamRuleFrameTest` uses original procedural bands and an actual fallback binding call. It covers a rounded seed that previously failed, all ten band constraints, unchanged supported spacing, missing or incompatible witnesses, wrong pitch-step binding, competing lanes and unchanged pixels. Existing ordinary, cue, grace, body and shaft-rival tests remain applicable. These controls do not establish general recognition accuracy or packaged-runtime behavior.
