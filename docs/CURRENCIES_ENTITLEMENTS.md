# Currencies and entitlements

V2 currencies use stable IDs and exact-decimal durable balances. A definition controls scope, precision, minimum,
maximum, and reset behavior; mutations are ledgered with operation/configuration identity. Do not model currency by
parsing display text or floating-point values.

Entitlements are declarative provider-owned capabilities with explicit grant/revoke and merge policy. They are separate
from progression stages and paid/store ranks. Optional integration absence makes a referenced entitlement unavailable;
it must not corrupt unrelated progression.

The numeric example uses `currencies: {}` and `entitlements: {}`. Currency ID, display name, symbol, precision, limits,
and scope are administrator configuration; core hardcodes no server-specific currency identity and imports no V1
currency balance.
The feature may remain disabled. By owner product decision, a first-party Prestige Shop is deferred beyond the V2
launch pending live/beta player feedback and a defined Prestige-specific reward catalog. There are no first-party shop
entries, prices, purchases, discounts, permissions, commands, configuration, persistence, or GUI to configure. The
absence of that optional future feature does not block Phase 9F completion, final acceptance, release qualification, or
the V2 launch. See [the Phase 9F-C3 decision](V2_PHASE9F_C3_PRODUCT_DECISION.md).
