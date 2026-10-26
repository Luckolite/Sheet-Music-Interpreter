# Beam frames from observed rule bands

A complete five-rule witness can have a slightly different spacing from the rounded staff seed. The fallback reader previously intersected phases at that fixed seed gap, rejecting valid observed bands. It retains the exact previous float-offset calculation when that nominal spacing is supported, including its acceptance boundary and work charges. Only an unsupported nominal spacing enters the common-gap fit over both five-band witnesses. The existing two-column interpolation, lane uniqueness, head residual and slope checks still apply.

Each rule band supplies a half-pixel interval. Pairwise lower/upper phase inequalities bound the common gap; both phase intervals must remain nonempty. The fit stays within the existing spacing domain and twelve-percent witness tolerance, and prepays 128 bounded work units only when that fallback fit is needed. The original raw witness search and ink thresholds are unchanged.

`PrintedBeamRuleFrameTest` uses original procedural bands and an actual fallback binding call. It covers a rounded seed that previously failed, all ten band constraints, unchanged supported spacing, missing or incompatible witnesses, wrong pitch-step binding, competing lanes and unchanged pixels. Existing ordinary, cue, grace, body and shaft-rival tests remain applicable. These controls do not establish general recognition accuracy or packaged-runtime behavior.

A synthetic float-boundary regression verifies actual binding acceptance and exact bottom bits, not just mathematical interval containment. The previous float multiplication must occur before widening its offset to double; changing that order can move a head across the existing residual threshold.
