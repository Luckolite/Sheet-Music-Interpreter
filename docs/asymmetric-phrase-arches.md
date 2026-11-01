# Asymmetric printed arch fit

The existing quadratic and elliptical fits retain precedence. If their unchanged fit-quality gate refuses a contour, one additional least-squares family can represent unequal returning ends:

`f(t) = a + b*t + (c + d*t)*sqrt(1 - t*t)`, for `-1 <= t <= 1`.

The same observed columns define t. There is no offset, scale, threshold or iterative parameter search. Four coefficients require one fixed4-by-4 solve.

A whole-arch curvature certificate is mandatory. The second derivative is proportional to `-[c + d*(3*t - 2*t^3)]`. Since the maximum absolute value of `3*t - 2*t^3` is `sqrt(2)`, requiring `abs(c) > sqrt(2)*abs(d)` excludes inflections throughout the arch.

The existing bend range applies to actual maximum height relative to the endpoint chord, rather than coefficient c. The peak solves `2*d*t*t + c*t - d = 0`; a stable root is `2*d/(c + copySign(sqrt(c*c + 8*d*d), c))`, with t=0 when d=0. Height is `abs((c+d*t)*sqrt(1-t*t))` at that root.

The existing RMS,95th-percentile error, sharp-hinge comparison, returning outer-quarter slopes, ink/connectivity, staff and resource guards remain. A successful raw fit supplies geometry only. It does not establish pitch, timing, a slur endpoint, a voice, a tie, or a beam role.

A transient branch flag gives newly accepted asymmetric arcs their own source-ID counter. Existing admitted arcs retain their original counter and IDs. The flag is not added to score wire/cache records; the old bow constructor remains available.

Original regression ink connects analytic samples with an ordinary integer stroke. Merely plotting one pixel at each integer column leaves isolated pixels near steep endpoint tangents and is not a faithful raster stroke. The original assertions retain exact printed endpoints and reject rails, hinges and inflected impostors.

No machine-learning weights or inference models change.
