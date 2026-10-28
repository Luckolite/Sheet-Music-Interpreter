# Bow height in pixel coordinates

Staff spacing and raw component positions use pixel-center coordinates. The
upper bow height bound therefore compares `bottom - top` with `3.5 * gap`.
The inclusive sample count `bottom - top + 1` still sizes arrays and indexes
samples; it is not the distance between the endpoint coordinates.

For example, a complete component from y=78 through y=127 has extent 49 and
50 samples. At a staff gap of 14, its extent equals the bound. Doubling all
coordinates and the gap produces extent 98 and 99 samples. Comparing sample
counts with a coordinate distance incorrectly rejects both boundary cases.

The correction changes one upper-bound comparison. Complete shape and arm
proof, text and contrast rejection, exact attack ownership, work limits,
record layouts, models and dependencies are unchanged. Recognition revision
125 invalidates earlier derived interpretations; Guide299 and audio101 remain.

`BowCoordinateExtentTest` uses original closed-radius segment renderings at
integer and half-pixel positions, two scales, over-bound refusals, a third arm,
competing attacks and a simultaneous chord. It also retains two original
steep, thick V counterexamples that disprove column-run-center projection as
a substitute for physical height. All examples are Apache-2.0 procedural
originals; no score scans or private libraries are included.
