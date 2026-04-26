# YPM — Your PC Mine
A Fabric mod that lets you mess with your friends directly from a Minecraft server.
Built for ARGs, horror maps, or just chaotic fun — you type a command, and something happens on their screen. Or their desktop. Or their PC.
> ⚠️ **Must be installed on both server and client.** Players without the mod will be kicked on join.
## Requirements
- Minecraft 1.21.11
- [Fabric Loader](https://fabricmc.net/use/installer/) `>=0.18.0`
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
## Commands
All commands require operator permissions.
| Command | Description |
|---|---|
| `/ypm error <player> "Title" "Text" [freeze]` | Shows a fake Windows error dialog. Optional freeze before showing. Use `\|` for line breaks. |
| `/ypm freeze <player> <time>` | Freezes their game completely. Example: `10s`, `2m` |
| `/ypm windowshake <player> <time> <1-10> [--noise] [--fullwindowed] [--restore]` | Shakes their Minecraft window |
| `/ypm image <player> <url> <time> [fadeout]` | Shows a fullscreen image over their game |
| `/ypm wallpaper <player> <url>` | Changes their actual desktop wallpaper |
| `/ypm web <player> <url>` | Opens a URL in their browser |
| `/ypm txt <player> "filename" "text"` | Opens a .txt file in Notepad with your text. Use `\|` for line breaks. |
| `/ypm minimize <player>` | Minimizes their Minecraft window |
| `/ypm shutdown <player>` | Shuts down their PC |
| `/ypm reboot <player>` | Reboots their PC |
| `/ypm chat <player> "text" [--send] [--perspective]` | Types in their chat, optionally sends it |
| `/ypm perspective <player>` | Toggles their camera perspective |
| `/ypm disclaimer <player>` | Forces the disclaimer screen to appear for a player |

## Client Commands
| Command | Description |
|---|---|
| `/ypmdisclaimer` | Opens the disclaimer screen for yourself |

## Player Protection — `/ypmconfig` `(1.0.3)`
Players can restrict what the operator is allowed to do on their machine. These are client-side commands.

| Command | Description |
|---|---|
| `/ypmconfig canopenweb <true\|false>` | Block browser from being opened. When blocked, a fake browser screen appears inside the game instead. |
| `/ypmconfig canshutdown <true\|false>` | Block shutdown/reboot. When blocked, a fake shutdown screen appears inside the game — PC is not touched. |
| `/ypmconfig enablesafemode <true\|false>` | **Safe Mode** — all out-of-game actions (browser, Notepad, wallpaper, shutdown) are fully simulated inside Minecraft. Multiple commands queue up correctly. |

## Operator Feedback `(1.0.3)`
When you run any `/ypm` command, the server reports each player's current mode:
```
[YPM] Error sent to 3 player(s) | Normal: 2, SafeMode: 1
```

## Download
Available on [Modrinth](https://modrinth.com/mod/your-pc-mine).

## License
MIT
