/**
 * Immutable canonical identities used at stable API boundaries.
 *
 * <p>String-backed identifiers are non-null, thread-safe values that callers may retain indefinitely. Unless a
 * narrower type documents otherwise, their canonical grammar is a lower-case alphanumeric first character followed
 * by lower-case alphanumeric, dot, underscore, or hyphen characters, with a maximum length of 128. Constructors
 * reject null with {@link NullPointerException} and invalid values with {@link IllegalArgumentException}.</p>
 */
package net.maddkraft.maddprestige.api.id;
