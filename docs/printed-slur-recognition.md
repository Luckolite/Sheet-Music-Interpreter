# Preparatory printed-slur recognition

The pure Java helpers find thin returning bows in verified staff frames and bind
them to explicit printed heads and written attacks. Bows remain independent of
sounding ties. Staff-line occlusions require raw ink; head-aware closure requires
an independently visible head body. Competing central contours, missing endpoints,
unknown pitches and ambiguous same-pitch phrasing stay unresolved.
Different physical-staff ownership needs separate geometry proof. Voice labels
alone cannot erase a phrase with unique raw endpoints and proved written order;
the endpoint resolver neither infers nor changes those labels.

Page capture retains explicit physical-page and note identities. It validates
ownership before scanning, bounds work, and returns no partial source after a
scan limit. Finalized context accepts validated written placements and explicit
voice/pitch maps; it never fills missing timing with a first attack. Grace notes
use their proved principal lane. Existing SLT1 source retention carries the result.

Fifty-three original procedural controls cover bows versus beams/hairpins/glyphs,
elliptical tips, rounded staff rails, absent crossing ink, endpoint ambiguity,
ties, grace order, source-page joins, captured identities and unresolved context.
They do not establish real-score recognition accuracy or complete phrasing recall.

These helpers are preparatory and unhooked. No production recognition revision,
guide284/audio79 layout, model or dependency changes. Production adoption requires
the finalized page/timing/pitch context, paired cache/writer/Hub layout, public drift
review, and packaged/real-score rendering verification. System-crossing and dashed
arcs, chord ownership and same-pitch phrasing remain incomplete.

The preparatory `PrintedSlurFrames` builder uses a dedicated fresh raw staff proof,
retained owner slots and physical phase to assemble scan frames. It rejects stale
or duplicated ownership and ambiguous repeated staff matches without inventing
voices. Existing generic detector flags and pitch references remain unchanged.
This closes the pure source-context preparation gap; production capture, complete
page/key/timing context, paired persistence and final runtime adoption remain open.
