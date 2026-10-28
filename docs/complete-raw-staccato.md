# Complete raw staccato components

The strict geometric classifier can reject a compact dot at a fractional area
boundary, while the existing raster classifier correctly accepts the complete
component. Applying that raster check only after dark-core extraction loses
literal antialiased boundary cells and can make the same dot disappear.

For an original complete raw component, the detector now tries the existing
compact raster staccato law before extracting its dark core. Already recognized
kinds retain priority. Joined partitions and additional normalized components
do not enter this path. Contrast, text, duration-dot, note-body, staff and
ownership guards remain in place. Candidate-prefix identity avoids a quadratic
membership search or an additional set allocation.

The original regression uses a four-by-four, thirteen-pixel dot with a dark
two-by-two center and a literal gray boundary. Further controls cover area
boundaries, punctuation, augmentation dots, semantic note bodies, partial and
normalized components, source immutability and candidate-prefix equivalence.
No score pixels, model weights, thresholds, palettes, resource caps or binary
record layouts are changed.
