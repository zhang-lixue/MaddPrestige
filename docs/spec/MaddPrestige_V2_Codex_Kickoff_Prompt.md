# Codex Kickoff Prompt — MaddPrestige V2

You are working on **MaddPrestige V2**, a production-grade Paper progression/prestige plugin intended to be reusable by unrelated Minecraft servers, with MaddKraft as the flagship deployment.

The authoritative product/engineering requirements are in:

`MaddPrestige_V2_Master_Spec.md`

Read that file completely before modifying code.

## Your first assignment: Phase 0 only

Do **not** immediately rewrite the plugin or attempt the entire V2 implementation in one pass.

Perform the Phase 0 repository audit defined in the master specification.

### Required Phase 0 work

1. Inspect the entire existing MaddPrestige repository relevant to architecture, including:
   - build files and Java/toolchain target;
   - plugin metadata;
   - source packages/classes;
   - commands;
   - GUIs;
   - current configuration handling;
   - current database/storage implementation;
   - current migrations, if any;
   - current integration hooks;
   - current tests;
   - bundled/default resources;
   - any README/docs.

2. Determine what existing implementation is worth preserving versus replacing/generalizing.

3. Audit current third-party dependencies and integrations. Do not guess APIs or versions. Use the actual repository/build environment and official/public documentation/source where needed.

4. Identify all MaddKraft-specific assumptions currently hardcoded in Java or resources.

5. Identify all obsolete/stale concepts that conflict with the master specification, including old progression/patron groups, hardcoded MaddHatter behavior, auction-listing terminology, stale integrations, and any code that can recreate missing LuckPerms groups.

6. Audit data-loss/migration risks. Treat any existing `maddprestige.db` / old config semantics as potentially valuable production data. Do not write a destructive migration yet.

7. Propose the concrete V2 package/module/service architecture that best fits the existing repository while satisfying the master specification.

8. Propose the canonical configuration schema strategy, including comment-preserving YAML and shared schema metadata for YAML/commands/GUI.

9. Propose the persistence/migration strategy for SQLite and later MySQL/MariaDB.

10. Propose the provider/capability architecture and how optional integrations will be isolated from core class loading.

11. Propose the transaction/recovery model, explicitly distinguishing native/idempotent provider actions from generic external command side effects.

12. Propose the test strategy, including what can be unit-tested, what needs DB integration tests, and what requires a real Paper + real dependency test environment.

13. Create/update:
   - `STATUS.md` — current phase, repository state, tests/build status, known risks, next action;
   - `DECISIONS.md` — architectural decisions and any proposed deviations from the specification;
   - a Phase 0 audit report, e.g. `docs/V2_PHASE0_AUDIT.md`.

14. Run the existing build/tests before changes if possible and record the result. Do not hide pre-existing failures.

15. If small non-behavioral changes are necessary to make the audit/test harness possible, keep them minimal and clearly document them. Do not begin the full V2 rewrite yet.

## Phase 0 deliverable

At the end of this task, report:

- current architecture summary;
- build/test result;
- code/components to preserve;
- code/components to retire/refactor;
- migration risks;
- dependency/API findings;
- proposed V2 architecture;
- proposed implementation sequence mapped to the master spec phases;
- unresolved questions that truly require owner input;
- exact files created/changed.

## Non-negotiable rules

- Never create missing LuckPerms groups as a side effect of loading/migrating configuration.
- Never alter LuckPerms hierarchy/weights/prefixes during Phase 0.
- Never delete or overwrite existing player data during Phase 0.
- Do not assume MaddKraft rank names are core domain concepts.
- Do not silently omit difficult requirements from the master spec.
- Prefer supported public APIs over reflection/private internals.
- Do not build the optional web UI.
- Do not claim an integration is supported until its actual API/version assumptions are verified.
- Do not mark Phase 0 complete without recording current build/test status and migration risks.

Once Phase 0 is complete, stop and present the audit for review before beginning Phase 1.
