package net.maddkraft.maddprestige.core.scaling;

/** Defines how a segment obtains its value at the first level in its range. */
public enum SegmentTransition {
    /** The first value equals the preceding segment's final value. */
    CONTINUE,
    /** The first value is the segment's explicitly configured base. */
    EXPLICIT_BASE
}
