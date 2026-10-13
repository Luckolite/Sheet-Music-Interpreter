# Printed slur source ownership

Printed phrasing arcs are distinct from sounding ties. The source model retains
physical page/note endpoints and normalized arc evidence. Explicit edit-owner
maps preserve endpoints through reorder, insertion and deletion. Deleted or
excluded endpoints remain unresolved; no nearby note is substituted. Page joins
deduplicate identical retained arcs and reject conflicting identities.

Unslurred scores use a compact immutable unowned map. Source wire SLT1 stores
the current note count, stored-origin count (zero or the full current count),
optional original page/note pairs (-1/-1 for an unowned slot), and bounded SLP1
page-local spans. Both sections are length-framed and reject malformed identities,
geometry, style IDs, duplicate ownership, truncation and trailing record bytes.

Original procedural tests cover cross-page ownership, edits, unresolved source,
round trips and bounded parsing. This is source-retention infrastructure; it does
not claim pixel slur recognition or complete optical phrasing accuracy. The app's
Android cache adoption, arrangement writer and matching Hub integration are
separate surfaces. No models or recognition weights changed.
