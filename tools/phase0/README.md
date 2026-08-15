# Phase 0 SQLite Inventory Helper

`ReadOnlySqliteInventory.java` contains only schema/aggregate `SELECT` and `PRAGMA` queries. Because the Xerial driver in this environment rejected the attempted read-only URI form, run it only against a temporary copy of `maddprestige.db`, never the original or production database.

It reports integrity/foreign-key checks, table DDL/counts, schema version, rank/transaction/pending-tier aggregates, and seasons. It does not display player UUIDs.

This is an audit helper, not MaddPrestige V2 implementation or a migration tool.

`ConfigEntitlementProbe.java` loads current YAML through the same Bukkit parser and `PluginSettings` code as V1, then prints only patron-entitlement key/value structure. It was used to verify the dotted-key parsing defect without changing configuration.
