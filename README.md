# OsirisUpgrader

Paper 1.21.8 plugin. Place a physical roulette and bet items to try for a prize. Chance comes from the bet value versus the prize value. The server decides the result before the needle moves.

The source in this repository is **6.0.0**. Older jars are on [Releases](https://github.com/FlamingIzanagi/OsirisUpgrader/releases).

## Requirements

- Java 21
- Paper 1.21.8
- Nexo, only if you use Nexo items
- Floodgate or Geyser, only if Bedrock players join

## Install

Copy `OsirisUpgrader-6.0.0.jar` into `plugins` and restart the server. On first start it creates `plugins/OsirisUpgrader/` with the default files. If you already had an older version, the plugin adds new keys and leaves the values you already changed. It does not wipe wheels, rewards, history, or pending spins.

## Build

```powershell
.\gradlew.bat test build
```

The jar is written to `build/libs/`.

## How to play

1. `/osirisupgrader spawner` gives a chest. Place it to spawn the wheel.
2. Right-click opens the bet menu.
3. Left-click without sneaking opens the base-value catalog, highest value first.
4. Sneak and left-click picks the wheel up, if you have break permission.

In the menu:

- The left slot is the bet. It uses the item's base value (`vanilla-items.yml` or `custom-items.yml`), not the prize value.
- The prize is chosen from the catalog. Its value comes from `rewards.yml`.
- `+1`, `+15`, and `+32` change the amount. `x2`, `x4`, and `x8` multiply the prize and lower the chance.
- The sunflower in the center spins the wheel, consumes the bet, and closes the menu.

Several players can have the menu open at once. Only one can spin that wheel until the spin ends. There is a cooldown between spins, 24 hours by default.

If the server stops mid-spin, `pending-spins.yml` stores the result and delivers it when the player joins again.

## Chance

```text
chance = (bet value / prize value) * 100 * chance-factor
```

The result is then clamped between `min-chance` and `max-chance`. With `chance-factor: 1.0` the ratio is used as-is.

## Rewards

`/osirisupgrader rewards` opens the editor.

- **Prize value:** used for chance. It does not change the base value used when betting.
- **Prize limit:** cap per bet. Stock does not run out.
- **Limited prize:** server-wide stock. You can only win 1, with no multiplier. The catalog shows how many are left. At 0, the prize is removed from the list.
- **Enchant glow:** glow on the item that is given.

The "Limited Prize" text in menus is `gui-texts.limited-badge` in `messages.yml`. It accepts `&`, hex, and MiniMessage. It is not written onto the item the player receives.

`/osirisupgrader base` registers custom items in `custom-items.yml`. Vanilla materials are edited in `vanilla-items.yml`.

## The wheel

The disc, needle, and gold center are display entities. At rest it shows a catalog prize and its name. While spinning, slices turn green or red from the chance.

If Floodgate or Geyser is installed, Bedrock players see a block ring in the same place, with the prize and its name. Java players do not see that ring. Bedrock does not render display entities.

The disc closes the same way facing north, south, east, or west.

## History

Each spin is written to `plugins/OsirisUpgrader/history.log`: player, bet, multiplier, chance, result, and prize.

- `/osirisupgrader history` shows the last 10.
- `/osirisupgrader historyclear` deletes the file.

When a spin ends, win or loss, you can broadcast it. Turn that on with `broadcast-daily-spin`. The text is `messages.daily-spin`. `daily-spin-prefix` drops the prefix on that message only.

## Commands

Everything uses `/osirisupgrader`. Operators can run them without the permission. Full notes are in `comandos.txt`.

| Command | Permission | What it does |
| --- | --- | --- |
| `/osirisupgrader` and `/osirisupgrader help` | none | Shows help |
| `/osirisupgrader reload` | `osirisupgrader.reload` | Reloads config |
| `/osirisupgrader giveitem [player]` | `osirisupgrader.give` | Gives the item that opens the menu |
| `/osirisupgrader spawner [player]` | `osirisupgrader.spawner` | Gives the chest that places the wheel |
| `/osirisupgrader clear` | `osirisupgrader.clear` | Removes every wheel on the server |
| `/osirisupgrader clearnear` | `osirisupgrader.clear` | Removes wheels within 10 blocks |
| `/osirisupgrader rewards` | `osirisupgrader.rewards` | Reward editor |
| `/osirisupgrader base` | `osirisupgrader.base` | Base-value editor for custom items |
| `/osirisupgrader history` | `osirisupgrader.history` | Last 10 spins |
| `/osirisupgrader historyclear` | `osirisupgrader.historyclear` | Deletes `history.log` |

Other permissions, without their own command: `osirisupgrader.use` (everyone by default), `osirisupgrader.break`, `osirisupgrader.bypass-cooldown`, `osirisupgrader.precise-chance`, and `osirisupgrader.admin`.

## Config files

| File | Purpose |
| --- | --- |
| `config.yml` | Chance, cooldown, broadcast, sounds, and wheel |
| `vanilla-items.yml` | Base value of vanilla materials |
| `custom-items.yml` | Base value of custom items, created by `/osirisupgrader base` |
| `rewards.yml` | Prizes, prize value, per-bet cap, limited stock, and glow |
| `messages.yml` | Chat and menu text |
| `menus.yml` | Menu slots and look |
| `ruletas.yml` | Placed wheels |
| `pending-spins.yml` | Spins that have not been delivered yet |
| `history.log` | Spin log |

## Nexo

The hook is optional and uses reflection. It accepts `dev.lone.nexo.API.NexoItems` or `com.nexomc.nexo.api.NexoItems`. Nexo prizes are not shipped by default. Add them with `/osirisupgrader rewards`; the full item is stored.
