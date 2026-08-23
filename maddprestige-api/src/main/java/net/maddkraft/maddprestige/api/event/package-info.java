/**
 * Immutable, Bukkit-free payloads carried by the stable Paper lifecycle events.
 *
 * <p>Components are non-null unless an {@link java.util.Optional} represents absence. Collections are bounded,
 * defensively copied, and immutable. Constructors reject null with {@link NullPointerException} and reject invalid
 * cross-field states or bounds with {@link IllegalArgumentException}. Snapshots are thread-safe values that listeners
 * may retain indefinitely; they do not remain connected to mutable runtime state.</p>
 *
 * <p>The enclosing Paper events are delivered synchronously on the server thread. PRE snapshots have a correlation
 * identity but no durable operation identity and may be cancelled by the event. POST snapshots are observational,
 * include the durable journal identity, and cannot alter the completed outcome.</p>
 */
@net.maddkraft.maddprestige.api.annotation.Stable
package net.maddkraft.maddprestige.api.event;
