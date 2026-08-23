# Phase 8D A76 blind fresh-admin protocol

**Acceptance status:** OWNER-DEFERRED — A76 remains Partial until the final Phase 8 release candidate is frozen  
**Purpose:** repeatable independent usability qualification  
**Target:** no more than 20 minutes active administrative setup time, excluding downloads, server startup and the
play-time demonstration

## Independence rule

By owner decision, this protocol is not executed against the intermediate Phase 8D snapshot. It must run after the
final Phase 8 release candidate is frozen so it qualifies the actual setup flow, commands, documentation, packaging,
defaults, integrations and release behavior. Deferral is not a pass, failure, waiver or replacement.

The tester is a technically competent Minecraft server administrator who has not read MaddPrestige source,
architecture/traceability documents, Codex context or qualification-harness source. No developer may answer procedural
questions during the run. A person who implemented or reviewed Phase 8D is not independent.

The tester receives only:

- the final frozen Phase 8 release-candidate MaddPrestige JAR and SHA-256;
- Paper 26.1.2 build 74 and LuckPerms 5.5.71 artifacts/checksums;
- Java 25 support statement;
- public `README.md`, `docs/INSTALLATION_V2.md` and `docs/QUICK_START.md`;
- a new empty server directory;
- this action-record form, without the expected internal outcomes below.

Do not supply architecture docs, internal file manifests, database tools, preset runtime data or a prepared
configuration revision.

## Pass protocol

The tester records the wall clock and active-time clock, then performs only actions described in the supplied public
guides:

1. Install the exact Paper, LuckPerms and MaddPrestige artifacts and record every copied file/checksum.
2. Accept the Paper EULA, configure the fresh local qualification server as documented and start it.
3. Record every startup warning/error and whether MaddPrestige reaches safely dormant setup-ready state.
4. Create external LuckPerms groups `Member`, `Adventurer` and `Veteran` using the documented commands.
5. Grant the documented MaddPrestige player/admin permissions to the tester identities.
6. Follow Quick Start verbatim: discover/start setup, select LuckPerms, add the exact ordered stages, select Member,
   attach 60/180-second current-Prestige Paper play-time requirements and configure Veteran-only Prestige reset.
7. Preview, record the reported risks, request/use the server-issued acknowledgement and apply.
8. Run Doctor and record its complete status/remediation output.
9. Join as a new player and confirm Member projection.
10. Attempt Adventurer early; record the command response and Why deficit. Confirm no group/stage change.
11. Accumulate the documented demonstration play time, advance to Adventurer, then repeat for Veteran.
12. Prestige once and confirm count 1, Member and preserved underlying play time with current-Prestige progress reset.
13. Stop cleanly, restart the directory unchanged and confirm stage/count/projection plus healthy Doctor.
14. Stop cleanly and end the active-time clock.

Any value the tester must guess, any undocumented correction, or any developer hint is recorded immediately as a
deviation; the tester must not silently repair the documentation during the run.

## Mandatory raw action record

Copy this table into the evidence file and add rows as needed.

| Seq | Wall time | Active minutes | Actor | Exact command/action | File copied/edited | Observed result | Warning/error | Documentation section | Deviation |
|---:|---|---:|---|---|---|---|---|---|---|
| 1 | | | | | | | | | |

Also record:

| Field | Raw value |
|---|---|
| Tester name or owner-approved pseudonym | |
| Independence attestation | |
| Start/end date and timezone | |
| Total active administrative setup minutes | |
| Excluded download/startup minutes | |
| Excluded play-time demonstration minutes | |
| Java output | |
| Paper version/build/hash | |
| LuckPerms version/hash | |
| MaddPrestige version/hash | |
| Fresh directory identity | |
| First-boot result | |
| Progression/Prestige result | |
| Unchanged-restart result | |
| Every warning/error | |
| Every deviation from public docs | |

## Automatic failure conditions

A76 fails if any required step uses an undocumented command, permission, file location, restart or value; source or
architecture inspection; manual SQLite editing; a prepared hidden file; developer intervention; or guessing. It also
fails if the active setup target exceeds 20 minutes. Raw evidence is retained even when the result fails.

Ordinary Paper/LuckPerms warnings are not silently ignored: the tester records them and the owner decides whether they
are relevant. Downloads, process boot time and the play-time demonstration are excluded only when their raw durations
are separately recorded.

## Evidence handling and acceptance

The tester returns the complete action record, relevant console logs, final public command output and artifact hashes.
The owner compares commands/permissions/files to the public docs and signs one outcome: PASS, FAIL or INVALID/REPEAT.
Only a valid independent PASS may move A76 to Satisfied.

Phase 8D currently provides docs-as-tests plus a fresh two-boot automated proxy in
`PHASE8D_A76_PROCESS_EVIDENCE.txt`. That evidence proves executability, not blind-human usability. No independent result
or elapsed human setup time has been fabricated.
