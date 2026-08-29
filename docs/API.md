# Developer API

> **Historical V1 document.** This is retained as accepted 1.x evidence, not current V2 installation, API, compatibility, or operator guidance. Start with [the V2 README](../README.md).

MaddPrestige exposes `MaddPrestigeApi` through Bukkit's service manager. Calls must be made on the server thread.

```java
RegisteredServiceProvider<MaddPrestigeApi> registration =
        Bukkit.getServicesManager().getRegistration(MaddPrestigeApi.class);
MaddPrestigeApi api = registration.getProvider();

api.completeRabbitHole(player.getUniqueId(), 1);
api.completeDecreeObjective(player.getUniqueId(), 1);
api.defeatBoss(player.getUniqueId(), 1);
```

An event bridge is also available for loosely coupled plugins:

```java
Bukkit.getPluginManager().callEvent(new MaddPrestigeProgressEvent(
        player,
        ProgressType.RABBIT_HOLE,
        1,
        "maddkraft-rabbit-holes"
));
```

Server earnings should only represent newly generated/earned economy value. Do not report balance transfers at full value or a player can recycle the same money. MaddPrestige counts EconomyShopGUI server sales at `1.0` and QuickShop player-to-player receipts at the conservative default weight `0.25`.

External plugins can consume successful MaddPrestige lifecycle changes by listening for `MaddPrestigeActionEvent`. Its action names and values match the triggers documented in [`RELATIONSHIPS.md`](RELATIONSHIPS.md).

```java
@EventHandler
public void onPrestigeAction(MaddPrestigeActionEvent event) {
    if (!event.getAction().equals("prestige") || event.getPlayer() == null) return;
    String newLevel = event.getValue("completed_prestige");
    // Apply the external plugin's feature here.
}
```
