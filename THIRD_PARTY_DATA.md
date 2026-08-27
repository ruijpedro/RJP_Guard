# Third-party data used by RJP Guard

## CallShield public spam feed

RJP Guard V1.2 can download the public CallShield legacy spam-number feed directly from its upstream repository:

https://raw.githubusercontent.com/SysAdminDoc/CallShield/master/data/spam_numbers.json

RJP Guard does not bundle the feed in this repository or APK. The app downloads it at runtime, caches exact phone-number records locally in SQLite, and performs incoming-call lookups against that local cache.

Upstream project:
https://github.com/SysAdminDoc/CallShield

The upstream repository documents source provenance, geography, licensing and redistribution policy for its feeds. RJP Guard intentionally ignores range/prefix feeds in V1.2 and imports only exact-number records from the legacy `spam_numbers.json` payload.

## Portuguese directories not imported

Portuguese collaborative directories such as QuemLigou and tellows.pt may contain useful local reports, but RJP Guard V1.2 does not scrape or redistribute their data. A future integration should use an official API, licensed feed, or explicit permission from the provider.
