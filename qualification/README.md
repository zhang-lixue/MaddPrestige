# Optional Paper qualification harnesses

This directory contains standalone, disposable Paper harness source retained for migration, recovery, provider,
fault, and performance qualification. These projects are not modules in the normal Maven reactor and require an
explicit qualification environment with their declared Paper and plugin dependencies.

Retained harnesses:

- `phase8c-paper-harness` — SQLite migration and recovery qualification;
- `phase8e-paper-harness` — real-Paper provider, dependency, fault, recovery, and performance coordination;
- `phase8e-alpha-provider`, `phase8e-beta-provider`, and `phase8e-economy-provider` — controlled provider plugins
  used by the Phase 8E harness;
- `phase8e-provider-support` — shared source used by the Phase 8E provider plugins.

The Phase 8B and Phase 8D standalone harnesses were retired from the current tree after their accepted results were
preserved in Git, merged pull requests, CI, and release history. Their removal does not remove current production or
reactor test coverage.

`eula.txt` and `paper-server.properties` are reusable inputs for an ignored disposable Paper directory. Server JARs,
plugin JARs, worlds, logs, databases, player data, and generated harness output must remain outside source control.
