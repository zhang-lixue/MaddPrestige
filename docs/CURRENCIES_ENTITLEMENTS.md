# Currencies and entitlements

V2 currencies use stable IDs and exact-decimal durable balances. A definition controls scope, precision, minimum,
maximum, and reset behavior; mutations are ledgered with operation/configuration identity. Do not model currency by
parsing display text or floating-point values.

Entitlements are declarative provider-owned capabilities with explicit grant/revoke and merge policy. They are separate
from progression stages and paid/store ranks. Optional integration absence makes a referenced entitlement unavailable;
it must not corrupt unrelated progression.

The Member/Adventurer/Veteran example uses `currencies: {}` and `entitlements: {}`. Add either only through a validated
draft after reading schema-owned help and provider health. Store/supporter fulfillment and MaddKraft-specific
entitlements are outside this generic Quick Start.
