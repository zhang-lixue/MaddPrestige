# Requirements, measurements, and scopes

A requirement binds a provider metric, value type, compatible operator, typed target, measurement scope, and
completion policy. Trees compose requirement IDs with bounded `all`, `any`, and threshold semantics. Preview rejects
unknown providers/metrics, incompatible types/operators, invalid target literals, unsafe scope/metric combinations,
and excessive tree depth.

Use `/maddprestige help measurement`, `/maddprestige help providers`, and `/maddprestige why prestige` for the active
configuration. Why uses the same authorization as execution and reports the exact current/target value or provider
failure without mutation.

Important scopes include lifetime/absolute observations and boundary-based scopes such as `SINCE_PRESTIGE_START`.
The numeric guided path uses `setup requirement` without a target stage and attaches the resulting tree directly to
Prestige. Paper's duration metric `paper_statistics/play_one_minute`, for example, may use
`SINCE_PRESTIGE_START` plus `LIVE`; the target is canonicalized as `DURATION`. MaddPrestige snapshots a configured
lifetime metric at the Prestige boundary and subtracts it later; it does not reset Paper-owned statistics.

`LIVE` completion follows the current measured value. Latched completion is durable when configured and is governed by
the lifecycle reset policy. Missing, stale, timed-out, malformed, or unhealthy provider data is unavailable and fails
closed; it is never treated as zero or success.
