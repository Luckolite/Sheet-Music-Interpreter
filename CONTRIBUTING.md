# Contributing

Contributions are welcome under Apache-2.0. By intentionally submitting code,
documentation, test data or weights for inclusion, you offer your contribution
under this project's license and confirm you have the necessary rights.

Please include a short explanation of the defect, a reproducer and a test when
appropriate. Recognition fixes should use general geometry or musical evidence;
do not add production exceptions keyed to a song title, filename or fingerprint.

Use original, public-domain or appropriately licensed examples. Include the source,
license and expected pitches/rhythms. Do not attach commercial score scans without
permission, personal libraries, credentials, signing keys or device logs containing
private data. A small synthetic reproducer is often enough.

Run the commands in the README before opening a pull request. Describe separately
what was tested on generated examples and what was tested on real scores. Avoid
turning segmentation IoU or a handful of passing passages into a general note-accuracy
claim. Do not replace released weights without updating hashes, lineage and evaluation.

Closed-source users have no obligation to submit their changes. Bug reports and
improvements are appreciated when you can share them.

For maintainers carrying fixes from Music Sheets, port applicable interpreting
changes and shareable regressions in the same task,
verify them here, and publish the public commit. Check mapped source drift with
`python scripts/check_app_drift.py --app <active-app-worktree>`; review new helpers,
adapters and model changes separately.
