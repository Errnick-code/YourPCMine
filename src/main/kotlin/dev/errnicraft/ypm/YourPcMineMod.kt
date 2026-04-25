package dev.errnicraft.ypm

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component

object YourPcMineMod : ModInitializer {

    const val MOD_ID = "ypm"

    private fun parseTime(input: String): Long? {
        val lower = input.trim().lowercase()
        return when {
            lower.endsWith("s") -> lower.dropLast(1).toLongOrNull()?.times(1000)
            lower.endsWith("m") -> lower.dropLast(1).toLongOrNull()?.times(60_000)
            else -> null
        }
    }

    override fun onInitialize() {
        PayloadTypeRegistry.playS2C().register(ErrorDialogPayload.TYPE, ErrorDialogPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(FreezePayload.TYPE, FreezePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ShutdownPayload.TYPE, ShutdownPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(RebootPayload.TYPE, RebootPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(WallpaperPayload.TYPE, WallpaperPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(TextPayload.TYPE, TextPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(WebPayload.TYPE, WebPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ScreamerPayload.TYPE, ScreamerPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ImagePayload.TYPE, ImagePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(MinimizePayload.TYPE, MinimizePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(PossessPayload.TYPE, PossessPayload.CODEC)

        PayloadTypeRegistry.configurationS2C().register(HandshakePayload.TYPE, HandshakePayload.CODEC)
        PayloadTypeRegistry.configurationC2S().register(HandshakePayload.TYPE, HandshakePayload.CODEC)

        ServerConfigurationNetworking.registerGlobalReceiver(HandshakePayload.TYPE) { _, ctx ->
            ctx.networkHandler().connection.channel.eventLoop().submit {
                ctx.networkHandler().finishCurrentTask(YpmConfigurationTask.TYPE)
            }
        }

        ServerConfigurationConnectionEvents.CONFIGURE.register { handler, _ ->
            if (ServerConfigurationNetworking.canSend(handler, HandshakePayload.TYPE)) {
                handler.addTask(YpmConfigurationTask())
            } else {
                handler.disconnect(
                    Component.literal("§cДля входа необходим мод §eYPM§c!\n§7Скачай на Modrinth: §fmodrinth.com/mod/ypm")
                )
            }
        }

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("ypm")
                    .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))

                    // /ypm error <who> "title" "text" [freeze: 10s / 2m]
                    .then(Commands.literal("error")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("text", StringArgumentType.string())
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val title = StringArgumentType.getString(ctx, "title").replace("|", "\n")
                                        val text = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                        for (player in players) ServerPlayNetworking.send(player, ErrorDialogPayload(title, text, 0L))
                                        ctx.source.sendSuccess({ Component.literal("Sent error to ${players.size} player(s)") }, true)
                                        players.size
                                    }
                                    .then(Commands.argument("freeze", StringArgumentType.string())
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val title = StringArgumentType.getString(ctx, "title").replace("|", "\n")
                                            val text = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                            val ms = parseTime(StringArgumentType.getString(ctx, "freeze"))
                                                ?: run {
                                                    ctx.source.sendFailure(Component.literal("Неверный формат времени. Используй 10s или 2m"))
                                                    return@executes 0
                                                }
                                            for (player in players) ServerPlayNetworking.send(player, ErrorDialogPayload(title, text, ms))
                                            ctx.source.sendSuccess({ Component.literal("Sent error + freeze ${ms/1000}s to ${players.size} player(s)") }, true)
                                            players.size
                                        }
                                    )
                                )
                            )
                        )
                    )

                    // /ypm freeze <who> <time>
                    .then(Commands.literal("freeze")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("time", StringArgumentType.string())
                                .executes { ctx ->
                                    val players = EntityArgument.getPlayers(ctx, "who")
                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                        ?: run {
                                            ctx.source.sendFailure(Component.literal("Неверный формат. Используй 10s или 2m"))
                                            return@executes 0
                                        }
                                    for (player in players) ServerPlayNetworking.send(player, FreezePayload(ms))
                                    ctx.source.sendSuccess({ Component.literal("Froze ${players.size} player(s) for ${ms/1000}s") }, true)
                                    players.size
                                }
                            )
                        )
                    )

                    // /ypm shutdown <who>
                    .then(Commands.literal("shutdown")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (player in players) ServerPlayNetworking.send(player, ShutdownPayload.INSTANCE)
                                ctx.source.sendSuccess({ Component.literal("Shutdown sent to ${players.size} player(s)") }, true)
                                players.size
                            }
                        )
                    )

                    // /ypm reboot <who>
                    .then(Commands.literal("reboot")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (player in players) ServerPlayNetworking.send(player, RebootPayload.INSTANCE)
                                ctx.source.sendSuccess({ Component.literal("Reboot sent to ${players.size} player(s)") }, true)
                                players.size
                            }
                        )
                    )

                    // /ypm wallpaper <who> <url>
                    .then(Commands.literal("wallpaper")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("url", StringArgumentType.string())
                                .executes { ctx ->
                                    val players = EntityArgument.getPlayers(ctx, "who")
                                    val url = StringArgumentType.getString(ctx, "url")
                                    for (player in players) ServerPlayNetworking.send(player, WallpaperPayload(url))
                                    ctx.source.sendSuccess({ Component.literal("Wallpaper sent to ${players.size} player(s)") }, true)
                                    players.size
                                }
                            )
                        )
                    )

                    // /ypm txt <who> <filename> <text>
                    // FIX: text теперь string() а не greedyString() — greedyString съедал кавычки
                    .then(Commands.literal("txt")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("filename", StringArgumentType.string())
                                .then(Commands.argument("text", StringArgumentType.string())
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val filename = StringArgumentType.getString(ctx, "filename")
                                        val text = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                        for (player in players) ServerPlayNetworking.send(player, TextPayload(filename, text))
                                        ctx.source.sendSuccess({ Component.literal("Text sent to ${players.size} player(s)") }, true)
                                        players.size
                                    }
                                )
                            )
                        )
                    )

                    // /ypm web <who> <url>
                    .then(Commands.literal("web")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("url", StringArgumentType.string())
                                .executes { ctx ->
                                    val players = EntityArgument.getPlayers(ctx, "who")
                                    val url = StringArgumentType.getString(ctx, "url")
                                    for (player in players) ServerPlayNetworking.send(player, WebPayload(url))
                                    ctx.source.sendSuccess({ Component.literal("Web sent to ${players.size} player(s)") }, true)
                                    players.size
                                }
                            )
                        )
                    )

                    // /ypm windowshake <who> <time> <strength 1-10> [--noise] [--restore]
                    // Флаги теперь отдельные литералы — есть подсказки таба
                    .then(Commands.literal("windowshake")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("time", StringArgumentType.string())
                                .then(Commands.argument("strength", IntegerArgumentType.integer(1, 10))
                                    // без флагов
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                        val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                        for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, false, false, false))
                                        ctx.source.sendSuccess({ Component.literal("Windowshake sent to ${players.size} player(s)") }, true)
                                        players.size
                                    }
                                    // --fullwindowed: трясти окно без выхода из фуллскрина
                                    .then(Commands.literal("--fullwindowed")
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                            val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                            for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, false, false, true))
                                            ctx.source.sendSuccess({ Component.literal("Screamer --fullwindowed sent to ${players.size} player(s)") }, true)
                                            players.size
                                        }
                                        // --fullwindowed --noise
                                        .then(Commands.literal("--noise")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                    ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, false, true))
                                                ctx.source.sendSuccess({ Component.literal("Screamer --fullwindowed --noise sent to ${players.size} player(s)") }, true)
                                                players.size
                                            }
                                            // --fullwindowed --noise --restore
                                            .then(Commands.literal("--restore")
                                                .executes { ctx ->
                                                    val players = EntityArgument.getPlayers(ctx, "who")
                                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                        ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                    val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                    for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, true, true))
                                                    ctx.source.sendSuccess({ Component.literal("Screamer --fullwindowed --noise --restore sent to ${players.size} player(s)") }, true)
                                                    players.size
                                                }
                                            )
                                        )
                                        // --fullwindowed --restore
                                        .then(Commands.literal("--restore")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                    ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, false, true, true))
                                                ctx.source.sendSuccess({ Component.literal("Screamer --fullwindowed --restore sent to ${players.size} player(s)") }, true)
                                                players.size
                                            }
                                            // --fullwindowed --restore --noise
                                            .then(Commands.literal("--noise")
                                                .executes { ctx ->
                                                    val players = EntityArgument.getPlayers(ctx, "who")
                                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                        ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                    val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                    for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, true, true))
                                                    ctx.source.sendSuccess({ Component.literal("Screamer --fullwindowed --restore --noise sent to ${players.size} player(s)") }, true)
                                                    players.size
                                                }
                                            )
                                        )
                                    )
                                    // --noise
                                    .then(Commands.literal("--noise")
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                            val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                            for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, false, false))
                                            ctx.source.sendSuccess({ Component.literal("Windowshake sent to ${players.size} player(s) [noise=true]") }, true)
                                            players.size
                                        }
                                        // --noise --restore
                                        .then(Commands.literal("--restore")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                    ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, true, false))
                                                ctx.source.sendSuccess({ Component.literal("Windowshake sent to ${players.size} player(s) [noise=true, restore=true]") }, true)
                                                players.size
                                            }
                                        )
                                    )
                                    // --restore
                                    .then(Commands.literal("--restore")
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                            val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                            for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, false, true, false))
                                            ctx.source.sendSuccess({ Component.literal("Windowshake sent to ${players.size} player(s) [restore=true]") }, true)
                                            players.size
                                        }
                                        // --restore --noise
                                        .then(Commands.literal("--noise")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                    ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, true, false))
                                                ctx.source.sendSuccess({ Component.literal("Windowshake sent to ${players.size} player(s) [noise=true, restore=true]") }, true)
                                                players.size
                                            }
                                        )
                                    )
                                )
                            )
                        )
                    )

                    // /ypm minimize <who>
                    .then(Commands.literal("minimize")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (player in players) ServerPlayNetworking.send(player, MinimizePayload.INSTANCE)
                                ctx.source.sendSuccess({ Component.literal("Minimize sent to ${players.size} player(s)") }, true)
                                players.size
                            }
                        )
                    )

                    // /ypm image <who> <url> <time> [fadeout]
                    .then(Commands.literal("image")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("url", StringArgumentType.string())
                                .then(Commands.argument("time", StringArgumentType.string())
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val url = StringArgumentType.getString(ctx, "url")
                                        val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                        for (player in players) ServerPlayNetworking.send(player, ImagePayload(url, ms, 0L))
                                        ctx.source.sendSuccess({ Component.literal("Image sent to ${players.size} player(s)") }, true)
                                        players.size
                                    }
                                    .then(Commands.argument("fadeout", StringArgumentType.string())
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val url = StringArgumentType.getString(ctx, "url")
                                            val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                            val fade = parseTime(StringArgumentType.getString(ctx, "fadeout"))
                                                ?: run { ctx.source.sendFailure(Component.literal("Используй 2s или 1m")); return@executes 0 }
                                            for (player in players) ServerPlayNetworking.send(player, ImagePayload(url, ms, fade))
                                            ctx.source.sendSuccess({ Component.literal("Image+fadeout sent to ${players.size} player(s)") }, true)
                                            players.size
                                        }
                                    )
                                )
                            )
                        )
                    )

                    // /ypm chat <who> "текст" [--send] [--perspective]
                    .then(Commands.literal("chat")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("text", StringArgumentType.string())
                                // без флагов — открыть чат, напечатать, закрыть
                                .executes { ctx ->
                                    val players = EntityArgument.getPlayers(ctx, "who")
                                    val text = StringArgumentType.getString(ctx, "text")
                                    for (player in players) ServerPlayNetworking.send(player, PossessPayload(text, 0))
                                    ctx.source.sendSuccess({ Component.literal("Chat sent to ${players.size} player(s)") }, true)
                                    players.size
                                }
                                // --send — напечатать и отправить
                                .then(Commands.literal("--send")
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val text = StringArgumentType.getString(ctx, "text")
                                        for (player in players) ServerPlayNetworking.send(player, PossessPayload(text, PossessPayload.FLAG_SEND))
                                        ctx.source.sendSuccess({ Component.literal("Chat --send sent to ${players.size} player(s)") }, true)
                                        players.size
                                    }
                                )
                                // --perspective — менять вид пока печатает
                                .then(Commands.literal("--perspective")
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val text = StringArgumentType.getString(ctx, "text")
                                        for (player in players) ServerPlayNetworking.send(player, PossessPayload(text, PossessPayload.FLAG_PERSPECTIVE))
                                        ctx.source.sendSuccess({ Component.literal("Chat --perspective sent to ${players.size} player(s)") }, true)
                                        players.size
                                    }
                                    // --perspective --send
                                    .then(Commands.literal("--send")
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val text = StringArgumentType.getString(ctx, "text")
                                            val flags = PossessPayload.FLAG_PERSPECTIVE or PossessPayload.FLAG_SEND
                                            for (player in players) ServerPlayNetworking.send(player, PossessPayload(text, flags))
                                            ctx.source.sendSuccess({ Component.literal("Chat --perspective --send sent to ${players.size} player(s)") }, true)
                                            players.size
                                        }
                                    )
                                )
                            )
                        )
                    )

                    // /ypm perspective <who>
                    .then(Commands.literal("perspective")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (player in players) ServerPlayNetworking.send(player, PossessPayload("", PossessPayload.FLAG_PERSPECTIVE))
                                ctx.source.sendSuccess({ Component.literal("Perspective toggled for ${players.size} player(s)") }, true)
                                players.size
                            }
                        )
                    )
            )
        }
    }
}