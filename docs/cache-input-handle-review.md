# Rejected Android cache inputs

The Android cache adapter rejected invalid normalized measure or note geometry
while its input stream was still open. Deleting the rejected file at that point
fails on Windows, leaving an unusable disposable cache behind.

Both geometry branches now throw into the existing rejection handler. Java
closes the input stream before entering that handler, so deletion happens after
the handle is released. Retained conversion sources still use the non-disposable
reader and remain byte-for-byte available for recovery.

The adapter's existing measure-geometry and retained-source recovery tests cover
the original failure. An additional original synthetic regression corrupts a
note's normalized horizontal position and checks both disposable deletion and
retained-byte preservation. Saved-state JVM tests now use a real test-only JSON
implementation instead of Android's stub methods.

This is an Android file-handle lifecycle correction. It does not change accepted
record layouts, geometry thresholds, musical interpretation, decoder sources,
model weights, or standalone interpreter behavior. The adapter remains outside
the standalone API; its reviewed source identity is recorded separately from
historical adapter reviews.
