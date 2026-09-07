# Qualification infrastructure

The retained qualification projects exercise deployment boundaries that are intentionally outside the normal unit and
integration-test reactor. Generated servers, plugin JARs, worlds, logs, databases, player data, and harness output must
remain outside source control.

## Normal verification

`mvn clean verify` covers the production modules, configuration and persistence compatibility, provider contracts,
Paper adapters, V1 characterization, package integrity, and the release artifact. It does not start a real Paper server
or third-party plugins.

## Migration and recovery

`migration-recovery/` builds a disposable Paper plugin that validates populated SQLite migration, operation recovery,
backup evidence, and exact state across a controlled two-boot sequence.

```powershell
mvn -f qualification/migration-recovery/pom.xml clean package
```

Execution is opt-in. It requires a disposable Paper server, the current MaddPrestige candidate, a sanitized historical
SQLite fixture, and the generated `Migration-Recovery-Qualification.jar`. The fixture downgrader records only synthetic
qualification identities and must never target production data.

## Runtime, provider, and fault qualification

`runtime-provider-fault/` contains one coupled qualification suite:

- `provider-alpha/` and `provider-beta/` provide independently owned late-registration fixtures;
- `economy-provider/` provides a controlled Vault economy service;
- `provider-support/` contains shared provider-fixture mechanics;
- `paper-harness/` coordinates provider discovery, dependency absence, fault, setup-audit, recovery, and performance
  scenarios through public Paper and stable MaddPrestige boundaries.

Build the opt-in components explicitly:

```powershell
mvn -f qualification/runtime-provider-fault/provider-alpha/pom.xml clean package
mvn -f qualification/runtime-provider-fault/provider-beta/pom.xml clean package
mvn -f qualification/runtime-provider-fault/economy-provider/pom.xml clean package
mvn -f qualification/runtime-provider-fault/paper-harness/pom.xml clean package
```

Execution requires a disposable Paper server and the exact third-party dependencies declared by the harness POMs.
Select the coordinator scenario with `-Dmaddprestige.qualification.runtime.mode=performance`, `fault`, `absence`, or
`setup-audit`. The suite must run with production credentials removed and with any required external isolation applied.

`eula.txt` and `paper-server.properties` are reusable inputs for an ignored disposable Paper directory. They are not a
production server configuration.
