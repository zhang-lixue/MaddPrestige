# Phase 8F Owner Review 3 corrected-candidate release installation qualification

Date: 2026-08-28

Qualification type: automated technical release qualification; **not owner-operated A76 acceptance**

## Exact environment

- candidate: `MaddPrestige-2.0.0-rc.1.jar`
- candidate size: 16,507,914 bytes
- candidate SHA-256: `0D961DB73D6C44CF18986CA42B7950914CEA5A1598BA7D41D0E919BFB76C6513`
- Java: Eclipse Adoptium Temurin `25.0.3+9-LTS`
- Paper: `26.1.2-74`, commit `e4e17fc`, 52,893,229 bytes,
  SHA-256 `1D70B1DAB9CF4A6DE615209A536F3A45A2186240253C428213CE2188AB95E5F7`
- LuckPerms: `5.5.71`, 1,501,521 bytes,
  SHA-256 `49CECB66FA1FD22A133039A490E9C1E5095A238E7CD66EB9D2A16FE6C897550D`
- first-party coordinator: isolated `Phase8D-Paper-Harness.jar`, 28,569 bytes,
  SHA-256 `896DD00DA7DE6FD6A6989C69FD891AD53E6FAD5F4944371EDD57C2012C72F132`
- disposable server: offline, dedicated port, four-player maximum, newly created empty directory

Paper was downloaded from the official PaperMC object URL returned by its build-74 API and matched the existing
repository-recorded identity. The runtime began with only Paper, EULA/minimal server properties, the three exact plugin
JARs, and no MaddPrestige config, database, world, generated file or earlier qualification state. Runtime artifacts,
libraries, worlds and databases remain in the ignored disposable directory and are excluded from review archives.

## Fresh boot — 30/30

The exact corrected distribution loaded as `MaddPrestige 2.0.0-rc.1`, generated fresh files and published dormant
SQLite plus built-in providers. Before configuration, the harness called the real MaddPrestige player-join listener for
a synthetic online player over six one-second intervals. It observed zero Placeholder initialization warnings and zero
rows for that player in `mp_player_stage_state` and `mp_player_prestige_state`. The harness then created external
`Member`, `Adventurer` and `Veteran` LuckPerms groups and
executed the public guided flow without repeating the setup-session UUID:

- provider `luckperms`;
- ordered Member, Adventurer and Veteran stages with Member baseline;
- `setup playtime adventurer PT1M` and `setup playtime veteran PT3M`;
- `setup prestige enabled veteran member`;
- preview, server-issued acknowledgement and confirm.

Preview independently reported the exact generated `adventurer_playtime` and `veteran_playtime` profile VALID, and the
canonical configuration applied as `r_c7b7fca1d62945c39b1d2603c51e73a0`. Doctor reported HEALTHY. The first post-activation
progress read for the same dormant player established durable Member state and real LuckPerms Member projection without
restart or stale dormant state. Remaining assertions proved idempotent establishment, exact blocked Adventurer/Why with zero effects,
successful Adventurer and Veteran transitions, one Prestige, Member reset/projection, preserved Paper lifetime
statistic, reset current-Prestige baseline, exact durable history and no duplicate terminal effect.

## Unchanged restart — 3/3

Boot two used the identical directory and plugin bytes. It recovered Member, current/lifetime Prestige 1, the exact
configuration revision and history counts, Paper statistic and LuckPerms projection; retained the new
current-Prestige baseline and blocked premature Adventurer; and reported Doctor HEALTHY. Both boots stopped cleanly.
The runtime candidate hash after both boots still equaled the clean-build hash above.

## Evidence and boundary

- `docs/evidence/phase8f/PHASE8F_A76_CORRECTION_OR3_PAPER_FIRST_BOOT_SANITIZED.log`
- `docs/evidence/phase8f/PHASE8F_A76_CORRECTION_OR3_PAPER_RESTART_SANITIZED.log`

The coordinator uses known assertions and therefore proves the corrected engineering workflow, not owner-operated
public-only discoverability. Both earlier owner runs remain blocker evidence. Targeted Owner Review 3 accepted this
exact artifact, and the separate real-player owner-operated run recorded in `V2_PHASE8F_A76_OWNER_ACCEPTANCE.md` passed;
A76 is Satisfied without misrepresenting this harness as the acceptance run.
