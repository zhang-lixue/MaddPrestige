# MaddPrestige

MaddPrestige adds a configurable Prestige system to Paper servers. You decide what players need to Prestige, what it
costs, what they get for doing it, and how progression changes at higher levels.

Players can track everything through a simple GUI. Staff get their own dashboard for managing players, checking
history, and editing common settings.

## Features

- Configure requirements, costs, and rewards for each Prestige
- Scale progression as players reach higher levels
- Let players track progress and Prestige through `/prestige`
- Manage players and common settings from the staff dashboard
- Set or reset a player's Prestige
- Track player history and staff changes
- Integrates with Vault, mcMMO, LuckPerms, PlaceholderAPI, and custom providers
- Use YAML when you need more control

## Requirements

MaddPrestige currently supports:

- Java 25
- Paper 26.1.2 build 74

SQLite is built in, so you don't need a separate database plugin or server.

Optional integrations include:

- Vault and an economy plugin for money requirements, costs, and rewards
- mcMMO for Total Skill Level requirements
- LuckPerms 5.5.71 for permission or group rewards
- PlaceholderAPI 2.12.2 or 2.12.3 for supported placeholders
- CraftEngine, GriefPrevention, WorldGuard, and custom providers

You only need the plugins used by your configuration. Check the
[provider capability matrix](docs/PROVIDER_CAPABILITY_MATRIX.md) for the full list.

## Installation and Quick Start

1. Download MaddPrestige from the
   [latest release](https://github.com/zhang-lixue/MaddPrestige/releases/latest).
2. Put it in your server as `plugins/MaddPrestige.jar`.
3. Start the server.
4. Configure your Prestige progression.
5. Test it with `/prestige`.

The [Getting started](docs/getting-started.md) and [Quick Start](docs/QUICK_START.md) guides walk through the setup.

## For Players

Running `/prestige` opens the Player GUI. Players can see:

- Their current Prestige and next level
- What they still need
- The cost and rewards
- Whether they're ready

Players can preview the next Prestige before confirming it. See the [Player guide](docs/player-guide.md) for more.

## For Staff

Staff can use `/maddprestige admin` to:

- Look up players and check their progress
- View player history and server-wide audit history
- Set a player's Prestige or reset it to 0
- Edit common configuration settings
- Check configuration, storage, and integration status

Each area has its own permissions. See the [Staff guide](docs/staff-guide.md) and
[Commands and permissions](docs/commands-permissions.md).

## Configuration and Integrations

You can configure requirements, costs, rewards, scaling, and per-level overrides. Common settings like money, rewards,
Total Skill Level, and scaling can be changed right from the staff dashboard.

If you need more control, edit the YAML files directly. The GUI leaves complex sections alone when it can't edit them.
Start with the [Configuration guide](docs/configuration.md) or the
[numeric Prestige example](examples/numeric-prestige).

If you're upgrading from an older setup, there's also a
[compatibility example](examples/compatibility/member-adventurer-veteran).

MaddPrestige works with Vault for economy features, mcMMO for Total Skill Level, LuckPerms for permission and group
rewards, and PlaceholderAPI for Prestige placeholders. Other plugins can add their own features through the provider
SDK. See [Integrations](docs/integrations.md) for details.

## Commands and Documentation

| Command | What it does |
|---|---|
| `/prestige` | Opens the Player GUI |
| `/maddprestige admin` | Opens the staff dashboard |

For everything else, use these guides:

- [Commands and permissions](docs/commands-permissions.md)
- [Player guide](docs/player-guide.md) and [Staff guide](docs/staff-guide.md)
- [Configuration](docs/configuration.md) and [Integrations](docs/integrations.md)
- [Deployment](docs/operations/deployment.md), [Upgrading](docs/operations/upgrading.md), and
  [Troubleshooting](docs/operations/troubleshooting.md)

## API and Building

Developers can use the [Java API and provider SDK](docs/api.md), listen for [Paper events](docs/events.md), or start
from the [provider example](examples/provider-sdk).

Build the project with:

```bash
./mvnw --no-transfer-progress clean verify
```

On Windows, use `mvnw.cmd --no-transfer-progress clean verify`. The plugin JAR is written to
`maddprestige-distribution/target/MaddPrestige.jar`.

## Current Release

MaddPrestige 2.0.0 is the current stable release. Test it on a copy of your server before using it in
production. Download `MaddPrestige.jar`; the already-published RC1 asset keeps its historical
`MaddPrestige-2.0.0-rc.1.jar` name.

## Known Limitations

- SQLite is the only storage option for now.
- External resource-world resets are handled outside MaddPrestige, so make sure that behavior is tested separately for your
  server setup.
- The built-in Prestige Shop is being saved for later, once player feedback gives us a better idea of what should
  actually go in it.

## License

MaddPrestige is available under the [MIT License](LICENSE).
