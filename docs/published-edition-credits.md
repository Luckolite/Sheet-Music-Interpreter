# Printed edition credits

An exact printed publisher number, copyright footer, prominent matching title
and explicit credit role can corroborate a damaged final name glyph. Given-name
tokens must agree after case/accent normalization. Only one final surname glyph
may be replaced or completed. Known intact people, internal surname changes,
ambiguous candidates, service transcriptions and owned review rows remain protected.
Repeated rows retain each edition witness while canonical role names are deduplicated.
The edition never supplies an absent credit or promotes a title to an artist.

The optional Hal Leonard provider verifies the exact product inventory, primary
title and explicit linked composer/arranger roles. HTTPS uses normal certificate
verification, bounded responses and timeouts. Requests contain the public edition
number only; PDF bytes, OCR text and account credentials are not sent.

Android uses this only during explicit autofill review. A separate private cache
holds at most 64 public-fact records, with a 30-day expiry and checksums. Its actual
binary layout is version 1: version integer, fetch timestamp, length-prefixed
UTF-8 product/title, counted composer/arranger lists, URL, response SHA-256, and
a SHA-256 trailer. The payload limit is 32768 bytes and storage uses Base64.
Cache failures retain valid fresh facts; unavailable lookups retain printed text.
No library or PDF metadata is automatically saved.

Consume a selection's `after` result. Literal evidence pages do not encode public
work ownership; a complete replay must revalidate each title and credit selection.
`PublishedEditionCreditRecovery.carry` preserves an independently owned title and
review while applying only source-bound credit changes.

All regression names, titles, publication HTML and geometry are fictional. No
publisher response body, commercial score, private library or model is bundled.
Native decoder and worker record layouts are unchanged; the standalone JAR still
requires paired packaging and deployment after source changes.
