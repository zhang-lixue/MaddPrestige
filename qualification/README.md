# Phase 8B disposable Paper qualification

`phase8b-paper-harness` is a separate first-party Paper plugin that exercises the actual shaded
`MaddPrestigeV2Plugin`; it does not construct a test-only MaddPrestige runtime.

## Qualified environment

- Java 25
- Paper 26.1.2 build 74
- LuckPerms 5.5.58
- PlaceholderAPI 2.12.2
- first-party `MaddPrestige-2.0.0-SNAPSHOT.jar`
- first-party `Phase8B-Paper-Harness.jar`

Paper, LuckPerms and PlaceholderAPI artifacts are supplied externally to an ignored disposable `target` directory.
They are never copied into source control or the owner-review bundle.

## Build and run

1. Run `mvnw.cmd --no-transfer-progress -DskipTests package` from the repository root.
2. Run `mvnw.cmd --no-transfer-progress -f qualification/phase8b-paper-harness/pom.xml clean package`.
3. Create a fresh short disposable directory under `target`, add `paper.jar`, the two external dependency JARs,
   `maddprestige-distribution/target/MaddPrestige-2.0.0-SNAPSHOT.jar` and
   `qualification/phase8b-paper-harness/target/Phase8B-Paper-Harness.jar`.
4. Copy `qualification/eula.txt` and `qualification/paper-server.properties` as `eula.txt` and
   `server.properties`.
5. Run `java -Xms512M -Xmx1G -jar paper.jar --nogui --nojline` twice without changing the disposable data.

The first boot starts dormant and executes canonical setup, public service, provider, event, operation, Placeholder and
configuration-reconciliation assertions before recording its exact active revision. It also proves one request UUID
across PRE/POST/result, a distinct durable operation UUID, successful PRE followed by authority invalidation with zero
unknown-player materialization, and exact two-key bridge output by deliberately omitting one key. The second boot checks
the same revision and durable player state, late provider rebind, Placeholder publication and clean shutdown. Either
assertion failure is logged as `PHASE8B-Q FAIL` and stops the server. The sanitized successful run is retained at
`PHASE8B_PAPER_QUALIFICATION.log`.
