# Costs and rewards

Numeric Prestige may reference typed costs and rewards. Requirements answer whether the player may advance; costs are
separate provider mutations and are never inferred from thresholds. Every-Prestige reward IDs and milestone reward IDs
are independently optional. Empty maps/lists are the canonical no-cost/no-reward representation.

Numeric definitions may select independent segmented multiplier profiles per cost or reward. FLAT, LINEAR,
EXPONENTIAL, and MANUAL ranges support explicit/continued transitions, rounding, bounds, and per-level overrides.
`CONTINUE` anchors at the preceding segment's value for the immediately previous Prestige level, including an override
at that level; it does not reuse the preceding segment's unmodified formula base.
All amounts and reward content belong to configuration; no deployment balance is a Java invariant.

Execution pins one configuration revision and provider generation, validates the complete batch before side effects,
and records an operation plan and per-action state. Internal recoverable actions use durable idempotency authority.
External effects that cannot be proven after interruption become uncertain and require reconciliation; MaddPrestige
does not claim universal exactly-once behavior.

Vault is optional and only needed when an active cost/reward references it. Missing or unhealthy cost/reward providers
block before partial state. Command actions are disabled by default and remain bounded by explicit roots, tokens,
templates, count, length, and nesting policy. Peer-to-peer shop turnover grants no progression credit by default.

Preview all changes and run Doctor before enabling consequential providers. See
[Providers and integrations](PROVIDERS_INTEGRATIONS.md) and [Troubleshooting](DIAGNOSTICS_TROUBLESHOOTING.md).
