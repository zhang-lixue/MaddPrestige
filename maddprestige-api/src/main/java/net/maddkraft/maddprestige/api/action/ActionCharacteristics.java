package net.maddkraft.maddprestige.api.action;

public record ActionCharacteristics(boolean idempotent, boolean reversible, boolean reconcilable,
                                    boolean externalUncertaintyPossible) {
}
