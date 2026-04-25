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

## Download

Available on [Modrinth](https://modrinth.com/mod/your-pc-mine).

## License

MIT
