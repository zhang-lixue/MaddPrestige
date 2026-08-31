# A63 populated pre-Phase-9B V2 upgrade evidence

Date: 2026-08-30. Network and production access: none.

## Source integrity

A genuine retained, populated pre-Phase-9B V2 SQLite database was found under the owner-local Phase 9D source
quarantine. The absolute owner filesystem prefix is deliberately not committed. Source file:
`_phase9d_source_quarantine/prior-maddprestige-data/maddprestige-v2.sqlite`.

- source size: 442,368 bytes
- source SHA-256 before and after: `7E137C5C3D2B09AD8E7AB5347ADA61468602A46437EB00B25F40FC4694A760C3`
- source schema: 11, migrations 1-11 checked/applied
- source active revision: `r_7c783d729af8437196ba7414798288bd`
- populated state: one stage row, one Prestige row, one Prestige-history row, four operations, seven operation
  actions, four requirement baselines, three stage-history rows, one active configuration revision

The source was first opened read-only, then copied to a disposable Phase 9E directory. Only the disposable copy was
migrated.

## Backup-first migration and preservation

The production `MigrationRunner`, `SqliteBackupService`, migration catalog, and validator upgraded the disposable copy
from schema 11 to 12.

- migration result: changed, migration 12 APPLIED, 12 checked history records
- backup size: 442,368 bytes
- backup SHA-256: `B2E03CB5F81D10871B11ED11D0E95CC8E033C516F08286DF201312C0CF3742C7`
- manifest SHA-256: `061123FE5F3E1A556246D38FF155DC0386D5C33EB1F37E41BD52EC4ABB122869`
- backup validation and restore rehearsal: PASS
- journal/integrity/foreign keys: WAL, PASS, no foreign-key findings
- active revision preserved exactly

After migration, active numeric state was safely initialized to `0:0:0:numeric-p0`. The former stage row was no
longer active authority; stage-era history/operation details were marked `LEGACY_STAGE`. The former numeric row was
archived as `1:1:1`; operation/action, baseline, stage-history, configuration, and audit records remained present.
No stage position was mapped into active numeric Prestige and no provider state was projected.

An unchanged second migration run reported no change and the same 12 checked records. A separate restore from the
accepted backup validated under the schema-11 catalog with the original active revision and populated row counts.
This closes A63 as PASS for a real retained local populated pre-Phase-9B V2 artifact. It is not authorization to mutate
or deploy production.
