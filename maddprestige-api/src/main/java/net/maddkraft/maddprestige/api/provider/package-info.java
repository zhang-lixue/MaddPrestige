/**
 * Owner-attested, generation-safe external requirement-provider contracts.
 *
 * <p>Stable provider parameters, components, and return values are non-null unless an
 * {@link java.util.Optional} represents absence. Collections are immutable defensive copies. Constructors reject
 * null with {@link NullPointerException} and invalid grammar, bounds, duplication, or cross-field state with
 * {@link IllegalArgumentException}. Machine codes and localization keys are not rendered display text.</p>
 *
 * <p>MaddPrestige invokes callbacks outside registry locks on bounded workers. Callback arguments and registration
 * handles are owned by MaddPrestige; providers may retain immutable metadata values but must not use a callback
 * context or its cancellation capability after the callback completes. Providers must complete before the supplied
 * deadline, observe cancellation, avoid Paper thread assumptions, and return structured unavailable results for
 * operational failures. Uncaught callback exceptions are contained by MaddPrestige and fail closed.</p>
 */
package net.maddkraft.maddprestige.api.provider;
