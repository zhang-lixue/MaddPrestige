# Costs and rewards

Stages and Prestige may reference typed costs and rewards. The generic Quick Start intentionally defines none. Empty
maps/lists are the canonical no-op representation; a demonstration reward is not required.

Execution pins one configuration revision and provider generation, validates the complete batch before side effects,
and records an operation plan and per-action state. Internal recoverable actions use durable idempotency authority.
External effects that cannot be proven after interruption become uncertain and require reconciliation; MaddPrestige
does not claim universal exactly-once behavior.

Vault is optional and only needed when an active cost/reward references it. Missing or unhealthy cost/reward providers
block before partial state. Command actions are disabled by default and remain bounded by explicit roots, tokens,
templates, count, length, and nesting policy. Peer-to-peer shop turnover grants no progression credit by default.

Preview all changes and run Doctor before enabling consequential providers. See
[Providers and integrations](PROVIDERS_INTEGRATIONS.md) and [Troubleshooting](DIAGNOSTICS_TROUBLESHOOTING.md).
