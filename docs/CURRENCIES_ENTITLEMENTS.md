# Currencies and entitlements

V2 currencies use stable IDs and exact-decimal durable balances. A definition controls scope, precision, minimum,
maximum, and reset behavior; mutations are ledgered with operation/configuration identity. Do not model currency by
parsing display text or floating-point values.

Entitlements are declarative provider-owned capabilities with explicit grant/revoke and merge policy. They are separate
from progression stages and paid/store ranks. Optional integration absence makes a referenced entitlement unavailable;
it must not corrupt unrelated progression.

The numeric example uses `currencies: {}` and `entitlements: {}`. Currency display name, precision, limits, and scope
are administrator configuration; the feature may remain disabled. A configurable first-party Prestige shop is
authorized later Phase 9 work but is not implemented, so there are currently no shop entries, prices, purchases,
discounts, or shop GUI to configure.
