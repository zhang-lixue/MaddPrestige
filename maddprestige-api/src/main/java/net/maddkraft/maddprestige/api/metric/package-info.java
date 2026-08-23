/**
 * Exact immutable metric values and bounded evaluation metadata.
 *
 * <p>Stable parameters, components, and returns are non-null. Values and returned collections are immutable and
 * thread-safe. Constructors and factories reject null with {@link NullPointerException}; malformed, out-of-range,
 * incompatible, or lossy values fail synchronously with {@link IllegalArgumentException}. Methods requiring a
 * numeric type fail with {@link IllegalStateException} when invoked on non-numeric values.</p>
 */
package net.maddkraft.maddprestige.api.metric;
