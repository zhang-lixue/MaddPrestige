# Optional V1 compatibility inspection tool

`ReadOnlySqliteInventory.java` is a historical forensic helper for inspecting the schema and aggregate contents of a
V1 `maddprestige.db`. It is not part of the Maven reactor, product runtime, migration pipeline, or release build.

The Xerial driver used during the original audit did not honor the attempted read-only URI form. Run the helper only
against a temporary copy of a database, never an original fixture or production database.

The helper reports integrity and foreign-key checks, table definitions and counts, schema version, aggregate rank and
transaction states, pending tiers, and seasons. It does not display player UUIDs.
