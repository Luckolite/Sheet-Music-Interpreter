# Original source-beam regression controls

The finite beam, shaft, five-rule and isolated-flag controls use original
procedural geometry. They contain no captured score pixels, fonts, model outputs
or private libraries. All controls and generators are Apache-2.0; see
[the original fixture provenance](original-test-provenance.json).

The standalone sources live in
`java/src/test/java/io/github/luckolite/interpreter`. The normal Java test runner
discovers these three JUnit4 entry points, containing seventeen test methods:

- `PrintedSourceBeamOriginalsTest`
- `PrintedSourceBeamIntakeOwnershipTest`
- `PrintedBeamIntakeTest`

Run the standalone Java suite from the repository root with
`python scripts/test_java.py`. The tests use the existing JUnit/Hamcrest test
dependencies and pure-JDK support; no general JSON parser or new production
dependency is required. Android source counterparts use the normal app unit-test
source set and the same original controls.

Fixtures are under `java/src/test/resources/source-beams`. An explicit
`FILES.txt` allowlist materializes classpath resources into a JUnit temporary
folder, supporting ordinary class directories and test JARs. Original generators
are retained under `tests/fixtures/source-beams/generators`; their hashes appear
in the provenance record. Generator outputs are written beside their respective
scripts and do not replace the checked-in test expectations automatically.

The controls cover sixty generated beam plates, bounded core and terminal
geometry, unknown-carrier refusals, literal shaft paths, five-rule witnesses,
isolated flag ownership, competing continuations, mixed endpoint scales and
cross-staff rivals. The ownership harness preserves forty-two assertions across
five original scenes; the raw glyph wrapper preserves thirty-three assertions.
The test adapter also checks malformed, missing and duplicate fixed report
fields. These counts describe regression controls and do not establish general
recognition accuracy.

The eight production helper mappings and exact source hashes are recorded in
`docs/source-provenance.json`. The helpers preserve existing scalar rhythm fields
and refuse incomplete or competing raw ownership evidence.
