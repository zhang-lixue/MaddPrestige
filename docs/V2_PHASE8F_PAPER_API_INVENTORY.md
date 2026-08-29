# Phase 8F final Paper-specific Stable event API inventory

Generated mechanically from public `@Stable` classes in `maddprestige-platform-paper` and their compiled class files on 2026-08-24 by `tools/Generate-Phase8FApiInventory.ps1`. This is surface B: the Paper-specific event API. It is intentionally separate from the Bukkit-free SDK inventory.

- Public Paper-specific Stable top-level types: **6**.
- Permitted platform exposure: `org.bukkit.event.Event`, `Cancellable`, and `HandlerList` only.
- Permitted MaddPrestige exposure: immutable Stable SDK event snapshots only.
- Persistence, journal/recovery, mutable core implementation, provider registry generation/token, optional-vendor, and unrelated platform internals are forbidden by `StablePaperEventSurfaceTest`.
- The six events and their accepted Bukkit/Stable-SDK boundary form Paper surface B of baseline `2.x-stable-1`.

## Stable Paper event types

| Public type | Purpose | Delivery/ownership contract | Compatibility risk |
|---|---|---|---|
| `net.maddkraft.maddprestige.platform.paper.event.ConfigAppliedEvent` | Canonical configuration publication notification. | Synchronous Paper-server-thread event; dispatch owns the event object and immutable snapshot values remain safe after return. | HIGH: superclass, interfaces, constructors, or accessor signature changes are binary/source incompatible. |
| `net.maddkraft.maddprestige.platform.paper.event.PostPrestigeEvent` | Durable terminal operation notification. | Synchronous Paper-server-thread event; dispatch owns the event object and immutable snapshot values remain safe after return. | HIGH: superclass, interfaces, constructors, or accessor signature changes are binary/source incompatible. |
| `net.maddkraft.maddprestige.platform.paper.event.PostRankUpEvent` | Durable terminal operation notification. | Synchronous Paper-server-thread event; dispatch owns the event object and immutable snapshot values remain safe after return. | HIGH: superclass, interfaces, constructors, or accessor signature changes are binary/source incompatible. |
| `net.maddkraft.maddprestige.platform.paper.event.PrePrestigeEvent` | Cancellable pre-operation gate. | Synchronous Paper-server-thread event; dispatch owns the event object and immutable snapshot values remain safe after return. | HIGH: superclass, interfaces, constructors, or accessor signature changes are binary/source incompatible. |
| `net.maddkraft.maddprestige.platform.paper.event.PreRankUpEvent` | Cancellable pre-operation gate. | Synchronous Paper-server-thread event; dispatch owns the event object and immutable snapshot values remain safe after return. | HIGH: superclass, interfaces, constructors, or accessor signature changes are binary/source incompatible. |
| `net.maddkraft.maddprestige.platform.paper.event.ProviderHealthChangedEvent` | Cached provider-health transition notification. | Synchronous Paper-server-thread event; dispatch owns the event object and immutable snapshot values remain safe after return. | HIGH: superclass, interfaces, constructors, or accessor signature changes are binary/source incompatible. |

## Exact public members by type

### `net.maddkraft.maddprestige.platform.paper.event.ConfigAppliedEvent` - PAPER STABLE 2.x BASELINE

```text
Compiled from "ConfigAppliedEvent.java"
public final class net.maddkraft.maddprestige.platform.paper.event.ConfigAppliedEvent extends org.bukkit.event.Event {
  public net.maddkraft.maddprestige.platform.paper.event.ConfigAppliedEvent(net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot);
  public net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot snapshot();
  public org.bukkit.event.HandlerList getHandlers();
  public static org.bukkit.event.HandlerList getHandlerList();
}
```

### `net.maddkraft.maddprestige.platform.paper.event.PostPrestigeEvent` - PAPER STABLE 2.x BASELINE

```text
Compiled from "PostPrestigeEvent.java"
public final class net.maddkraft.maddprestige.platform.paper.event.PostPrestigeEvent extends org.bukkit.event.Event {
  public net.maddkraft.maddprestige.platform.paper.event.PostPrestigeEvent(net.maddkraft.maddprestige.api.event.OperationEventSnapshot);
  public net.maddkraft.maddprestige.api.event.OperationEventSnapshot snapshot();
  public org.bukkit.event.HandlerList getHandlers();
  public static org.bukkit.event.HandlerList getHandlerList();
}
```

### `net.maddkraft.maddprestige.platform.paper.event.PostRankUpEvent` - PAPER STABLE 2.x BASELINE

```text
Compiled from "PostRankUpEvent.java"
public final class net.maddkraft.maddprestige.platform.paper.event.PostRankUpEvent extends org.bukkit.event.Event {
  public net.maddkraft.maddprestige.platform.paper.event.PostRankUpEvent(net.maddkraft.maddprestige.api.event.OperationEventSnapshot);
  public net.maddkraft.maddprestige.api.event.OperationEventSnapshot snapshot();
  public org.bukkit.event.HandlerList getHandlers();
  public static org.bukkit.event.HandlerList getHandlerList();
}
```

### `net.maddkraft.maddprestige.platform.paper.event.PrePrestigeEvent` - PAPER STABLE 2.x BASELINE

```text
Compiled from "PrePrestigeEvent.java"
public final class net.maddkraft.maddprestige.platform.paper.event.PrePrestigeEvent extends org.bukkit.event.Event implements org.bukkit.event.Cancellable {
  public net.maddkraft.maddprestige.platform.paper.event.PrePrestigeEvent(net.maddkraft.maddprestige.api.event.OperationEventSnapshot);
  public net.maddkraft.maddprestige.api.event.OperationEventSnapshot snapshot();
  public boolean isCancelled();
  public void setCancelled(boolean);
  public org.bukkit.event.HandlerList getHandlers();
  public static org.bukkit.event.HandlerList getHandlerList();
}
```

### `net.maddkraft.maddprestige.platform.paper.event.PreRankUpEvent` - PAPER STABLE 2.x BASELINE

```text
Compiled from "PreRankUpEvent.java"
public final class net.maddkraft.maddprestige.platform.paper.event.PreRankUpEvent extends org.bukkit.event.Event implements org.bukkit.event.Cancellable {
  public net.maddkraft.maddprestige.platform.paper.event.PreRankUpEvent(net.maddkraft.maddprestige.api.event.OperationEventSnapshot);
  public net.maddkraft.maddprestige.api.event.OperationEventSnapshot snapshot();
  public boolean isCancelled();
  public void setCancelled(boolean);
  public org.bukkit.event.HandlerList getHandlers();
  public static org.bukkit.event.HandlerList getHandlerList();
}
```

### `net.maddkraft.maddprestige.platform.paper.event.ProviderHealthChangedEvent` - PAPER STABLE 2.x BASELINE

```text
Compiled from "ProviderHealthChangedEvent.java"
public final class net.maddkraft.maddprestige.platform.paper.event.ProviderHealthChangedEvent extends org.bukkit.event.Event {
  public net.maddkraft.maddprestige.platform.paper.event.ProviderHealthChangedEvent(net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot);
  public net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot snapshot();
  public org.bukkit.event.HandlerList getHandlers();
  public static org.bukkit.event.HandlerList getHandlerList();
}
```
