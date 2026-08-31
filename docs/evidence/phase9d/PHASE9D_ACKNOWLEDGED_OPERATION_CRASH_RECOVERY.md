# Phase 9D acknowledged-operation crash/recovery qualification

Status: **PASS — REAL-CLIENT STATE_COMMITTED CRASH/RECOVERY AND CLEAN-RESTART PERSISTENCE**

## Accepted preceding gate

The owner-observed real-client login-session confirmation invalidation is PASS. Confirmation
`4324e69f-6d13-41c6-990e-a302c2445ab1` was invalidated by logout, rejected after reconnect in explicit form with
`confirmation.unknown`, and absent from shorthand with `confirmation.none_pending`. No mutation occurred.

## Exact pre-operation state

- Player: `tmydwc` / `d7551bf9-6358-3218-89c4-06c9c57dc879`
- Current/lifetime Prestige: `2 / 2`
- Vault balance: `12`
- mcMMO `total_level`: `1`
- LuckPerms direct parents: `default`, `owner`
- Active revision: `r_d30689cab75d406bb08e0edee970d33a`
- Server: disposable clone; Minecraft endpoint `127.0.0.1:25569`
- Runtime: clone-specific firewall-isolated JDK only
- Deployed MaddPrestige JAR: `16,578,420` bytes;
  SHA-256 `4341EEEACFF8392A171015AD006FA0D3DBC236FF5A78AE34B6054E2FE46D7B0F`

Console/provider reads at `19:35–19:36` reconfirmed Vault `12`, PlaceholderAPI current/lifetime `2 / 2`, mcMMO
power level `1`, LuckPerms parents `default`, `owner`, and active revision
`r_d30689cab75d406bb08e0edee970d33a`.

## Qualification profile

The already-active low-value profile is reused; no new configuration mutation is required:

- requirement mode: `ALL`
- Vault requirement: effective `6`; current `12` — satisfied
- mcMMO requirement: `total_level >= 1`; current `1` — satisfied, check-only
- Vault cost: `5`
- Vault reward: `1`
- expected transition if confirmed: current/lifetime `2 / 2 → 3 / 3`
- expected final Vault balance: `12 - 5 + 1 = 8`
- expected final mcMMO: `1`
- expected final LuckPerms parents: `default`, `owner`

The production simulation for this exact revision previously returned `ELIGIBLE`, blockers `0`, and the values
above. Merely retaining/applying the profile caused no provider write and created no confirmation.

## Deterministic crash boundary

The selected boundary is **after every consequential action is durably verified, before terminal completion**:

1. Vault cost action is `VERIFIED`.
2. Atomic internal Prestige/state/history commit has occurred and its journal action is `VERIFIED`.
3. Operation state is `STATE_COMMITTED`.
4. Vault reward action is `VERIFIED`.
5. The process stops before the operation transition `STATE_COMMITTED → COMPLETED`.

This is the narrow boundary at source line `PrestigeOperationExecutor.java:372`. The exact deployed class retains
debug symbols mapping line `372` to bytecode offset `1086`, before the completion transition at offset `1100`.

Earlier Vault boundaries are deliberately rejected: Vault is an external, non-idempotent provider, so interruption
while a cost or reward is merely `STARTED` would correctly become uncertain and would not prove automatic exactly-once
recovery.

## Qualification-only fault method

No production source hook is added. Paper will receive one clean qualification restart using only:

`<clone>\\_phase9d-jdk\\bin\\java.exe`

with a localhost-only JDWP listener on `127.0.0.1:25570`. The copied JDK's `jdb.exe` will set a suspend-all
breakpoint on `net.maddkraft.maddprestige.persistence.plan.PrestigeOperationExecutor:372`. When the owner later
confirms, execution must stop before the terminal journal transition; Codex will inspect evidence and terminate only
that suspended clone JVM. Recovery will start without JDWP.

The launch also sets the exact H2-supported property `-Dh2.bindAddress=127.0.0.1`. This confines VortexStacker's H2
auto-server, which the current boot exposed as an all-interface random listener, to loopback. Minecraft remains on
`127.0.0.1:25569`; the existing owner-managed clone-JDK outbound firewall rules remain unchanged.

## Expected restart recovery

`PendingOperationRecoveryService` handles a `STATE_COMMITTED` operation whose reward actions are all `VERIFIED` by
transitioning it to `COMPLETED` and recording the terminal result. It does not invoke the external reward provider.
Expected coherent recovered outcome:

- operation: `COMPLETED`
- current/lifetime Prestige: `3 / 3`, exactly once
- Vault: `8`, exactly one cost and one reward
- mcMMO `total_level`: `1`
- LuckPerms parents: `default`, `owner`; no group creation/hierarchy mutation
- no `NEEDS_RECONCILIATION`

The focused `PhaseFourLifecycleTest` recovery suite passes `22 / 22` with zero failures, errors, or skips.

## Non-mutating preflight debugger interruption

The owner created confirmation `7c7f013f-f3c3-4a2e-9006-efbcee197344` in the genuine `tmydwc` login session. A
read-only debugger inspection proved that the confirmation map contained exactly one entry and that a lookup of this
exact identifier was non-null. The current player session was also present. The server log contains the preview
command and no confirmation command.

The inspection suspended all server threads for too long, so Paper's watchdog initiated shutdown before the owner
was told to click Confirm. This was **not** the intended acknowledged-operation crash boundary:

- the line-372 operation breakpoint was never reached;
- no confirmation was consumed;
- no cost, reward, Prestige, mcMMO, or LuckPerms mutation ran;
- the operation journal contained no incomplete operation for startup recovery;
- startup recovery inspected `0` operations.

The retained database before and after this shutdown is byte-for-byte identical: `507,904` bytes, SHA-256
`782CBF1571E82B9532DE0911BD077E0BE43B97CE61B98FF83A12568A497664B8`. Preserved evidence is under
`<clone>\\_phase9d-evidence\\ACKNOWLEDGED-CRASH-r_d30689cab75d406bb08e0edee970d33a-pre-operation`, including
`latest-preflight-watchdog-shutdown.log` (SHA-256
`2BA28F561A1BEDEC80CE25ADDFCA242200678B0D10E625B6B7A300C05DDC9BDE`) and the post-shutdown database snapshot.

Because confirmations are intentionally login-session/server-instance bound, the shutdown invalidated
`7c7f013f-f3c3-4a2e-9006-efbcee197344`. It must not be submitted after restart. The exact breakpoint has been
re-armed on the replacement process without repeating any thread-suspending heap inspection.

## Owner checkpoint

Following the non-mutating watchdog shutdown described above, the qualification boot is live as PID `35108` with
the exact clone-specific Java executable. Startup reported the active revision above, inspected `0` incomplete
operations, and loaded all `42` plugins. All listeners and debugger connections are loopback:

- Minecraft: `127.0.0.1:25569`
- JDWP: `127.0.0.1:25570`, attached only to clone JDK `jdb.exe` PID `29260`
- VortexStacker H2 auto-server: `127.0.0.1:52117`

The owner-managed outbound-block rules remain enabled and scoped to only the clone's `java.exe` and `javaw.exe`.
The debugger reports this exact active breakpoint:

```text
breakpoint net.maddkraft.maddprestige.persistence.plan.PrestigeOperationExecutor:372
```

The debugger's generic uncaught-`Throwable` stop was explicitly removed; `catch` reports no exception stops. Only
the exact MaddPrestige line breakpoint remains armed, so unrelated plugin exceptions cannot trigger the test crash.

## Executed acknowledged-operation crash

The owner generated fresh session-bound confirmation `73c88bdb-99aa-4e37-9c59-5ac527ed8b54` and clicked its
`[Confirm]` control. The preserved Paper log records the exact player command at `20:05:23`:

```text
tmydwc issued server command: /maddprestige confirm 73c88bdb-99aa-4e37-9c59-5ac527ed8b54
```

The command was received and consumed. The operation thread reached only the armed breakpoint:

```text
Breakpoint hit: thread=maddprestige-operation-5
PrestigeOperationExecutor.executeJournaled(), line=372, bci=1086
```

The operation identifier was `cd00d672-9734-4da2-99e5-0edf971da455`. Direct read-only durable inspection while
Paper was suspended proved:

- operation state: `STATE_COMMITTED`;
- `cost-0`: `VERIFIED`;
- `prestige-state-commit`: `VERIFIED`;
- `reward-0`: `VERIFIED`;
- current/lifetime Prestige: `3 / 3`, state revision `3`;
- exactly one Prestige-history row: `2 / 2 -> 3 / 3`, result `STATE_COMMITTED`;
- Essentials persisted Vault balance: `8.0`.

The client received no response because the operation thread was deliberately suspended before
`STATE_COMMITTED -> COMPLETED` and before command response completion. The durable boundary snapshot is retained
under
`<clone>\\_phase9d-evidence\\ACKNOWLEDGED-CRASH-cd00d672-9734-4da2-99e5-0edf971da455-STATE_COMMITTED`.
Only disposable Paper PID `35108`, whose executable was the clone-specific isolated `java.exe`, was then
force-terminated. No other Java process was terminated.

## Recovery result

Paper restarted using only `<clone>\\_phase9d-jdk\\bin\\java.exe`, without JDWP. MaddPrestige startup recovery
inspected exactly `1` operation and produced recovery event
`091e25be-632f-4adb-97f4-8a6730a08025`:

- previous state: `STATE_COMMITTED`;
- resulting state: `COMPLETED`;
- decision: `verified-completion`;
- detail: `Committed internal evidence and verified actions allowed safe completion`.

Post-recovery durable/provider state:

- operation: `COMPLETED`;
- current/lifetime Prestige: `3 / 3`;
- Prestige history rows for this operation: exactly `1`;
- Vault balance: `8`;
- mcMMO persisted state / `total_level`: `1`;
- LuckPerms parents: `default`, `owner`;
- original cost, state-commit, and reward action timestamps unchanged;
- duplicate cost: `0`;
- duplicate reward: `0`;
- duplicate Prestige increment: `0`;
- `NEEDS_RECONCILIATION` or other ambiguity: `0`.

The recovery database snapshot is `507,904` bytes with SHA-256
`7DF9D5B47B766DAB08DDD0B15EB633F52CF13921044122B2C59ADED14F63FAEF`.

## Additional clean-restart persistence

After `save-all flush`, Paper shut down normally and restarted again using only the isolated clone JDK. The clean
boot loaded all `42` plugins, reported startup recovery inspected `0` operations, and retained the exact completed
state. The live process is PID `30620`; its listeners are loopback-only at Minecraft `127.0.0.1:25569` and the
VortexStacker H2 auto-server `127.0.0.1:64676`.

The post-clean-restart database is byte-for-byte identical to the recovery snapshot, including SHA-256
`7DF9D5B47B766DAB08DDD0B15EB633F52CF13921044122B2C59ADED14F63FAEF`. Provider persistence also remains unchanged:

- Essentials player file SHA-256
  `C8924CF7D95C5689D989B0818F2EC90CE5E1EF8233C6A2EA7414A85180B06F03`, balance `8.0`;
- mcMMO users file SHA-256
  `C7D7299A1CE315C69B69377DBBC2FE400E546E2CBBB91F5E219F371FD87C9A58`, player total `1`;
- LuckPerms console read: direct parents `default`, `owner`.

The owner-managed clone-JDK firewall rules remain active. All observed external connection attempts were denied.
Production was untouched. No commit, push, PR, merge, production-balance selection, GUI/shop work, or Phase 10 work
occurred.
