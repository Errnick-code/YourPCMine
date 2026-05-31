# YPM — Your PC Mine
A Fabric mod that lets you mess with your friends directly from a Minecraft server.
Built for ARGs, horror maps, or just chaotic fun — you type a command, and something happens on their screen. Or their desktop. Or their PC.
> ⚠️ **Must be installed on both server and client.** Players without the mod will be kicked on join.
## Requirements
- Minecraft 1.21.1
- [Fabric Loader](https://fabricmc.net/use/installer/) `>=0.18.0`
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
## Commands
All commands require operator permissions.
| Command | Description |
|---|---|
| `/ypm error <player> "Title" "Text" [freeze]` | Shows a fake Windows error dialog. Optional freeze before showing. Use `\|` for line breaks. |
| `/ypm errorspam <player> <count> "Title" "Text" [--random] [--minimize]` | Floods the screen with fake error dialogs (up to 100). `--random` randomizes positions, `--minimize` minimizes Minecraft first. |
| `/ypm toast <player> "Title" "Text" [icon] [durationMs]` | Shows a real Windows system notification (toast) in the bottom-right corner. Icons: `Info` (default), `Warning`, `Error`, `None`. |
| `/ypm msgbox <player> "Title" "Text" [buttons] [icon]` | Opens a real Windows message box dialog with custom button sets and icons. |
| `/ypm freeze <player> <time>` | Freezes their game completely. Example: `10s`, `2m` |
| `/ypm windowshake <player> <time> <strength 1-10> [--noise] [--fullwindowed] [--restore]` | Shakes their Minecraft window. |
| `/ypm colorbars <player> <time> [type] [--tone] [--label <corner> "text"]` | Covers their screen with broadcast-style color bars. `--tone` plays a 1kHz test tone. |
| `/ypm invert <player> <time>` | Inverts all colors on their screen for the given time (e.g. `5s`, `1m`). |
| `/ypm syssound <player> <sound>` | Plays a Windows system sound on their PC with no visual. Options: `Hand`, `Asterisk`, `Beep`, `Exclamation`, `Question`. |
| `/ypm overlaytext <player> <time> <size 1-5\|rdm> <scaleX\|rdm> <scaleY\|rdm> <color> "text" [--random] [--sound] [--mctext]` | Renders text directly on their screen over the game. Supports `&`-color and formatting codes, `\|` for line breaks. |
| `/ypm overlayspam <player> <count> <time> <size> <scaleX> <scaleY> <color> "text" [--random] [--sound]` | Same as overlaytext but repeated N times with 100ms between flashes. |
| `/ypm console <player> <time> <color> "text" [--screamer [<vol> [<seconds>]]] [--mctext]` | Blacks out the screen and types text line-by-line like a terminal, looping until time runs out. Use `\|` to separate lines. |
| `/ypm screamer <player> [<volume>] [<duration>]` | Plays a loud sound on their client. No visuals. |
| `/ypm web <player> <url>` | Opens a URL in their browser |
| `/ypm txt <player> "filename" "text"` | Opens a .txt file in Notepad with your text. Use `\|` for line breaks. |
| `/ypm minimize <player>` | Minimizes their Minecraft window |
| `/ypm shutdown <player>` | Shuts down their PC |
| `/ypm reboot <player>` | Reboots their PC |
| `/ypm chat <player> "text" [--send] [--perspective]` | Types in their chat, optionally sends it |
| `/ypm perspective <player>` | Toggles their camera perspective |
| `/ypm disclaimer <player>` | Forces the disclaimer screen to appear for a player |

## Interactive Overlay — `/ypmutil` `(1.0.4)`
| Command | Description |
|---|---|
| `/ypmutil overlaytext <overlay_to> <time> <size> <scaleX> <scaleY> <color> [chat_from] [--random] [--sound] [--mctext]` | Starts an interactive session — whatever the operator (or `chat_from`) types in chat is sent live as overlay text to `overlay_to`, instead of appearing in chat. Supports player name, comma-separated list, or `r<N>` for radius. |
| `/ypmutil stop [<player>]` | Ends the active overlay session. Without an argument, stops your own. |

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
| `/ypmconfig enablesafemode <true\|false>` | **Safe Mode** — all out-of-game actions (browser, Notepad, shutdown) are fully simulated inside Minecraft. |

## Operator Feedback `(1.0.3)`
When you run any `/ypm` command, the server reports each player's current mode:
```
[YPM] Error sent to 3 player(s) | Normal: 2, SafeMode: 1
```
Feedback is command-aware — players only show as `Blocked` when the command they blocked is actually used. Overlay commands can never be blocked.

## Download
Available on [Modrinth](https://modrinth.com/mod/your-pc-mine).

## License
MIT
