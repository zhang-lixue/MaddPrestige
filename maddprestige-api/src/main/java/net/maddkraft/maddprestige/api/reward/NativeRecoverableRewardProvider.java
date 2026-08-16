package net.maddkraft.maddprestige.api.reward;

/**
 * Marker for a trusted native provider whose exact persisted action can be replayed after restart because its
 * authoritative effect is operation/action-id idempotent. External providers must not implement this contract.
 */
public interface NativeRecoverableRewardProvider extends RewardProvider {
}
