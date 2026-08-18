/**
 * Stable, Bukkit-free service facade.
 *
 * <p>Parameters, record components, and return values are non-null unless an {@link java.util.Optional}
 * explicitly represents absence. Null arguments fail synchronously with {@link NullPointerException}; values
 * outside a documented grammar or bound fail synchronously with {@link IllegalArgumentException}. Collections
 * crossing this boundary are immutable, defensively copied snapshots that callers may retain indefinitely.</p>
 *
 * <p>Synchronous methods use already-materialized memory only. Methods that may read persistence, call a provider,
 * or mutate progression return immediately with a {@link java.util.concurrent.CompletionStage}. Operational
 * failures are successful future completions containing {@link net.maddkraft.maddprestige.api.service.ServiceError},
 * not implementation exceptions. Cancelling a caller-derived future detaches that caller; it does not guarantee
 * cancellation or rollback of an operation already accepted by MaddPrestige. Callers must not block a Paper server
 * thread waiting for asynchronous completion.</p>
 */
@net.maddkraft.maddprestige.api.annotation.Stable
package net.maddkraft.maddprestige.api.service;
