# Avoid repeated impossible staff-track searches

When the local strip width equals the complete image width, all13 clamped strip
left coordinates are exactly zero. Every possible sample therefore has the same
floating-point x coordinate. A track with too few samples already returns null;
otherwise its zero span cannot meet the existing positive width coverage. The
detector now returns null before performing those repeated searches, after its
original straight-rule checks and only for valid finite in-page input geometry.
Invalid inputs retain their old refusal/exception path. No numerical threshold,
pitch, staff phase, ownership, API, record format or model changes.

188 original differential requests in four modes cover boundary widths, curved
non-null controls, malformed rasters, NaN/Infinity, exact numerical/exception
outputs and raw immutability. Two new JUnit controls and30 retained staff/quarter
controls pass on old and optimized source. A separate original143x200 counter
fixture observes13 raw staff searches before and0 after with the same null result;
this uses no timing threshold. Fatal VM allocation failures are not asserted to
remain identical when unnecessary allocations are removed.

Private current-closure saved pure-Omr qualification keeps all five pages' complete
note/rest/key/tremolo DTOs exact, including the existing quarter and dot repairs.
That is a numerical parity scope, not a fresh model/full native-page or deployed
runtime claim. No additional missing-note/bar/rest correction is credited here.
All portable fixtures are original Apache-2.0 arrays; no commercial score pixels,
library data, model output, device logs or assets are included. The desktop worker
already selects StaffPitchTrack; cumulative matched app/worker packaging remains
deferred during the active audit.
