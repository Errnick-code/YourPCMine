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
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
object YourPcMineMod : ModInitializer {
    val clientStatusCache: ConcurrentHashMap<UUID, ClientStatusPayload> = ConcurrentHashMap()
    fun playerModeString(player: ServerPlayer): String {
        val status = clientStatusCache[player.uuid]
        return when {
            status == null -> "§7[?]"
            status.safeMode -> "§b[SafeMode]"
            status.blockShutdown && status.blockWeb -> "§e[Block:SD+Web]"
            status.blockShutdown -> "§e[Block:Shutdown]"
            status.blockWeb -> "§e[Block:Web]"
            else -> "§a[Normal]"
        }
    }
    enum class CmdType { OVERLAY, SHUTDOWN, WEB, GENERAL }
    fun playerGroupSummaryComponent(
        players: Collection<ServerPlayer>,
        cmdType: CmdType = CmdType.GENERAL,
    ): net.minecraft.network.chat.Component {
        val normalNames   = mutableListOf<String>()
        val safeNames     = mutableListOf<String>()
        val blockedNames  = mutableListOf<String>()
        val unknownNames  = mutableListOf<String>()
        for (p in players) {
            val s = clientStatusCache[p.uuid]
            val name = p.gameProfile.name
            val isBlocked = when (cmdType) {
                CmdType.SHUTDOWN -> s != null && (s.safeMode || s.blockShutdown)
                CmdType.WEB      -> s != null && (s.safeMode || s.blockWeb)
                CmdType.OVERLAY  -> false
                CmdType.GENERAL  -> s != null && (s.blockShutdown || s.blockWeb)
            }
            when {
                s == null    -> unknownNames += name
                s.safeMode && cmdType == CmdType.OVERLAY -> normalNames += name
                s.safeMode   -> safeNames += name
                isBlocked    -> blockedNames += name
                else         -> normalNames += name
            }
        }
        fun hoverList(names: List<String>) =
            net.minecraft.network.chat.HoverEvent.ShowText(
                Component.literal(names.joinToString("\n"))
            )
        val result = net.minecraft.network.chat.MutableComponent.create(
            net.minecraft.network.chat.contents.PlainTextContents.EMPTY
        )
        var first = true
        fun sep() { if (!first) result.append(Component.literal(", ")); first = false }
        if (normalNames.isNotEmpty()) {
            sep()
            result.append(
                Component.literal("§aNormal: ${normalNames.size}")
                    .withStyle { it.withHoverEvent(hoverList(normalNames)) }
            )
        }
        if (safeNames.isNotEmpty()) {
            sep()
            result.append(
                Component.literal("§bSafeMode: ${safeNames.size}")
                    .withStyle { it.withHoverEvent(hoverList(safeNames)) }
            )
        }
        if (blockedNames.isNotEmpty()) {
            sep()
            result.append(
                Component.literal("§eBlocked: ${blockedNames.size}")
                    .withStyle { it.withHoverEvent(hoverList(blockedNames)) }
            )
        }
        if (unknownNames.isNotEmpty()) {
            sep()
            result.append(
                Component.literal("§7Unknown: ${unknownNames.size}")
                    .withStyle { it.withHoverEvent(hoverList(unknownNames)) }
            )
        }
        return result
    }
    fun playerGroupSummary(players: Collection<ServerPlayer>, cmdType: CmdType = CmdType.GENERAL): String {
        var normal = 0; var safe = 0; var blockSd = 0; var unknown = 0
        for (p in players) {
            val s = clientStatusCache[p.uuid]
            val isBlocked = when (cmdType) {
                CmdType.SHUTDOWN -> s != null && (s.safeMode || s.blockShutdown)
                CmdType.WEB      -> s != null && (s.safeMode || s.blockWeb)
                CmdType.OVERLAY  -> false
                CmdType.GENERAL  -> s != null && (s.blockShutdown || s.blockWeb)
            }
            when {
                s == null    -> unknown++
                s.safeMode && cmdType == CmdType.OVERLAY -> normal++
                s.safeMode   -> safe++
                isBlocked    -> blockSd++
                else         -> normal++
            }
        }
        val parts = mutableListOf<String>()
        if (normal  > 0) parts += "§aNormal: $normal"
        if (safe    > 0) parts += "§bSafeMode: $safe"
        if (blockSd > 0) parts += "§eBlocked: $blockSd"
        if (unknown > 0) parts += "§7Unknown: $unknown"
        return parts.joinToString(", ")
    }
    const val MOD_ID = "ypm"
    private fun parseSize(input: String): Int {
        val s = input.trim().lowercase()
        if (s == "rdm") return (1..5).random()
        return input.toIntOrNull()?.coerceIn(1, 5) ?: 3
    }
    private fun parseScaleArg(input: String): Float {
        val s = input.trim().lowercase()
        if (s == "rdm") return -1f
        return input.toFloatOrNull()?.coerceIn(0.1f, 10.0f) ?: 1.0f
    }
    private fun sendOverlay(
        ctx: com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>,
        randomPos: Boolean,
        sound: Boolean = false,
        mcText: Boolean = false,
    ): Int {
        val players = EntityArgument.getPlayers(ctx, "who")
        val ms = parseTime(StringArgumentType.getString(ctx, "time"))
            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return 0 }
        val size   = parseSize(StringArgumentType.getString(ctx, "size"))
        val sxRaw  = parseScaleArg(StringArgumentType.getString(ctx, "scaleX"))
        val syRaw  = parseScaleArg(StringArgumentType.getString(ctx, "scaleY"))
        val color  = StringArgumentType.getString(ctx, "color")
        val text   = StringArgumentType.getString(ctx, "text").replace("|", "\n")
        val useRandomScale = sxRaw < 0f || syRaw < 0f
        val sx = if (sxRaw < 0f) 1.0f else sxRaw
        val sy = if (syRaw < 0f) 1.0f else syRaw
        for (player in players) ServerPlayNetworking.send(
            player, OverlayTextPayload(text, color, size, sx, sy, ms, randomPos, useRandomScale, sound, mcText)
        )
        ctx.source.sendSuccess({ Component.literal("OverlayText → ${players.size} | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
        return players.size
    }
    private fun sendOverlaySpam(
        ctx: com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>,
        randomPos: Boolean,
        sound: Boolean,
    ): Int {
        val players = EntityArgument.getPlayers(ctx, "who")
        val count  = StringArgumentType.getString(ctx, "count").toIntOrNull()?.coerceIn(1, 200) ?: 1
        val ms     = parseTime(StringArgumentType.getString(ctx, "time"))
            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return 0 }
        val size   = parseSize(StringArgumentType.getString(ctx, "size"))
        val sxRaw  = parseScaleArg(StringArgumentType.getString(ctx, "scaleX"))
        val syRaw  = parseScaleArg(StringArgumentType.getString(ctx, "scaleY"))
        val color  = StringArgumentType.getString(ctx, "color")
        val text   = StringArgumentType.getString(ctx, "text")
        val useRandomScale = sxRaw < 0f || syRaw < 0f
        val sx = if (sxRaw < 0f) 1.0f else sxRaw
        val sy = if (syRaw < 0f) 1.0f else syRaw
        for (player in players) ServerPlayNetworking.send(
            player, OverlaySpamPayload(text, color, size, sx, sy, ms, count, randomPos, useRandomScale, sound)
        )
        ctx.source.sendSuccess({ Component.literal("OverlaySpam x$count → ${players.size} | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
        return players.size
    }
    private fun sendConsoleOverlay(
        ctx: com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>,
        screamer: Boolean,
        screamerVol: Float,
        screamerMs: Long = 2000L,
        mcText: Boolean = false,
    ): Int {
        val players = EntityArgument.getPlayers(ctx, "who")
        val ms    = parseTime(StringArgumentType.getString(ctx, "time"))
            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return 0 }
        val color = StringArgumentType.getString(ctx, "color")
        val text  = StringArgumentType.getString(ctx, "text")
        for (player in players) ServerPlayNetworking.send(
            player, ConsoleOverlayPayload(text, color, ms, false, screamer, screamerVol, mcText)
        )
        if (screamer) {
            for (player in players) ServerPlayNetworking.send(
                player, ScreamerSoundPayload(screamerVol, false, screamerMs)
            )
        }
        ctx.source.sendSuccess({ Component.literal("Console → ${players.size} | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
        return players.size
    }
    private fun sendScreamerSound(
        ctx: com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>,
        volume: Float,
        durationMs: Long,
        randomize: Boolean,
    ): Int {
        val players = EntityArgument.getPlayers(ctx, "who")
        for (player in players) ServerPlayNetworking.send(
            player, ScreamerSoundPayload(volume, randomize, durationMs)
        )
        ctx.source.sendSuccess({ Component.literal("Screamer → ${players.size} | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
        return players.size
    }
    private fun parseTime(input: String): Long? {
        val lower = input.trim().lowercase()
        return when {
            lower.endsWith("s") -> lower.dropLast(1).toDoubleOrNull()?.let { (it * 1000).toLong() }
            lower.endsWith("m") -> lower.dropLast(1).toDoubleOrNull()?.let { (it * 60_000).toLong() }
            else -> null
        }
    }
    override fun onInitialize() {
        PayloadTypeRegistry.playS2C().register(ErrorDialogPayload.TYPE, ErrorDialogPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(FreezePayload.TYPE, FreezePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ShutdownPayload.TYPE, ShutdownPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(RebootPayload.TYPE, RebootPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(TextPayload.TYPE, TextPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(WebPayload.TYPE, WebPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ScreamerPayload.TYPE, ScreamerPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(MinimizePayload.TYPE, MinimizePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(PossessPayload.TYPE, PossessPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ErrorSpamPayload.TYPE, ErrorSpamPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(OverlayTextPayload.TYPE, OverlayTextPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(OverlaySpamPayload.TYPE, OverlaySpamPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ConsoleOverlayPayload.TYPE, ConsoleOverlayPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ScreamerSoundPayload.TYPE, ScreamerSoundPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ShowDisclaimerPayload.ID, ShowDisclaimerPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ToastPayload.TYPE,    ToastPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(MsgBoxPayload.TYPE,   MsgBoxPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SysSoundPayload.TYPE, SysSoundPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ColorBarsPayload.TYPE, ColorBarsPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VisualEffectPayload.TYPE, VisualEffectPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(OverlaySessionPayload.TYPE, OverlaySessionPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ClientStatusPayload.TYPE, ClientStatusPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(OverlayTextRequestPayload.TYPE, OverlayTextRequestPayload.CODEC)
        ServerPlayNetworking.registerGlobalReceiver(OverlayTextRequestPayload.TYPE) { payload, context ->
            val sender = context.player()
            val server = context.server()
            val targets: List<net.minecraft.server.level.ServerPlayer> = when {
                payload.targetName.isEmpty() -> listOf(sender)
                payload.targetName.startsWith("r") -> {
                    val radius = payload.targetName.substring(1).toDoubleOrNull()
                    if (radius != null && radius > 0) {
                        val senderPos = sender.position()
                        server.playerList.players.filter { player ->
                            player.position().distanceTo(senderPos) <= radius
                        }
                    } else {
                        listOf(sender)
                    }
                }
                else -> server.playerList.players.filter {
                    it.gameProfile.name.equals(payload.targetName, ignoreCase = true)
                }
            }
            for (target in targets) {
                ServerPlayNetworking.send(
                    target,
                    OverlayTextPayload(
                        payload.text, payload.color, payload.size,
                        payload.scaleX, payload.scaleY, payload.durationMs,
                        payload.randomPos, payload.randomScale, payload.sound, payload.mcText
                    )
                )
            }
        }
        ServerPlayNetworking.registerGlobalReceiver(ClientStatusPayload.TYPE) { payload, context ->
            clientStatusCache[context.player().uuid] = payload
        }
        PayloadTypeRegistry.configurationS2C().register(HandshakePayload.TYPE, HandshakePayload.CODEC)
        PayloadTypeRegistry.configurationC2S().register(HandshakePayload.TYPE, HandshakePayload.CODEC)
        ServerConfigurationNetworking.registerGlobalReceiver(HandshakePayload.TYPE) { payload, ctx ->
            val clientVersion = try { payload.version.trim() } catch (_: Exception) { "" }
            val ok = clientVersion == HandshakePayload.MOD_VERSION
            ctx.networkHandler().connection.channel.eventLoop().submit {
                if (ok) {
                    ctx.networkHandler().finishCurrentTask(YpmConfigurationTask.TYPE)
                } else {
                    val reason = if (clientVersion.isEmpty())
                        "§cCould not determine your §eYPM §cmod version.\n§7Please install version §f${HandshakePayload.MOD_VERSION}§7 from Modrinth: §fmodrinth.com/mod/your-pc-mine"
                    else
                        "§cYour §eYPM §cmod version is incompatible with this server.\n§7Server: §f${HandshakePayload.MOD_VERSION}§7, yours: §f$clientVersion\n§7Download the correct version at: §fmodrinth.com/mod/your-pc-mine"
                    ctx.networkHandler().disconnect(Component.literal(reason))
                }
            }
        }
        ServerConfigurationConnectionEvents.CONFIGURE.register { handler, _ ->
            if (ServerConfigurationNetworking.canSend(handler, HandshakePayload.TYPE)) {
                handler.addTask(YpmConfigurationTask())
            } else {
                handler.disconnect(
                    Component.literal("§cThis server requires the §eYPM §cmod.\n§7Download it at: §fmodrinth.com/mod/your-pc-mine")
                )
            }
        }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("ypm")
                    .requires { it.permissions().hasPermission(Permissions.COMMANDS_OWNER) || !it.isPlayer() }
                    .then(Commands.literal("error")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("text", StringArgumentType.string())
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val title = StringArgumentType.getString(ctx, "title").replace("|", "\n")
                                        val text = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                        for (player in players) ServerPlayNetworking.send(player, ErrorDialogPayload(title, text, 0L))
                                        ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.error", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
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
                                            ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.error_freeze", ms/1000, players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                            players.size
                                        }
                                    )
                                )
                            )
                        )
                    )
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
                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.freeze", players.size, ms/1000, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                    players.size
                                }
                            )
                        )
                    )
                    .then(Commands.literal("shutdown")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (player in players) ServerPlayNetworking.send(player, ShutdownPayload.INSTANCE)
                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.shutdown", players.size, playerGroupSummaryComponent(players, CmdType.SHUTDOWN)) }, true)
                                players.size
                            }
                        )
                    )
                    .then(Commands.literal("reboot")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (player in players) ServerPlayNetworking.send(player, RebootPayload.INSTANCE)
                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.reboot", players.size, playerGroupSummaryComponent(players, CmdType.SHUTDOWN)) }, true)
                                players.size
                            }
                        )
                    )
                    .then(Commands.literal("txt")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("filename", StringArgumentType.string())
                                .then(Commands.argument("text", StringArgumentType.string())
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val filename = StringArgumentType.getString(ctx, "filename")
                                        val text = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                        for (player in players) ServerPlayNetworking.send(player, TextPayload(filename, text))
                                        ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.text", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                        players.size
                                    }
                                )
                            )
                        )
                    )
                    .then(Commands.literal("web")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("url", StringArgumentType.string())
                                .executes { ctx ->
                                    val players = EntityArgument.getPlayers(ctx, "who")
                                    val url = StringArgumentType.getString(ctx, "url")
                                    for (player in players) ServerPlayNetworking.send(player, WebPayload(url))
                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.web", players.size, playerGroupSummaryComponent(players, CmdType.WEB)) }, true)
                                    players.size
                                }
                            )
                        )
                    )
                    .then(Commands.literal("windowshake")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("time", StringArgumentType.string())
                                .then(Commands.argument("strength", IntegerArgumentType.integer(1, 10))
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                        val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                        for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, false, false, false))
                                        ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.shake", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                        players.size
                                    }
                                    .then(Commands.literal("--fullwindowed")
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                            val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                            for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, false, false, true))
                                            ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.screamer_fw", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                            players.size
                                        }
                                        .then(Commands.literal("--noise")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                    ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, false, true))
                                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.screamer_fw_noise", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                players.size
                                            }
                                            .then(Commands.literal("--restore")
                                                .executes { ctx ->
                                                    val players = EntityArgument.getPlayers(ctx, "who")
                                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                        ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                    val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                    for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, true, true))
                                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.screamer_fw_noise_restore", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                    players.size
                                                }
                                            )
                                        )
                                        .then(Commands.literal("--restore")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                    ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, false, true, true))
                                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.screamer_fw_restore", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                players.size
                                            }
                                            .then(Commands.literal("--noise")
                                                .executes { ctx ->
                                                    val players = EntityArgument.getPlayers(ctx, "who")
                                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                        ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                    val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                    for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, true, true))
                                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.screamer_fw_restore_noise", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                    players.size
                                                }
                                            )
                                        )
                                    )
                                    .then(Commands.literal("--noise")
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                            val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                            for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, false, false))
                                            ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.shake_noise", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                            players.size
                                        }
                                        .then(Commands.literal("--restore")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                    ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, true, false))
                                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.shake_noise_restore", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                players.size
                                            }
                                        )
                                    )
                                    .then(Commands.literal("--restore")
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                            val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                            for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, false, true, false))
                                            ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.shake_restore", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                            players.size
                                        }
                                        .then(Commands.literal("--noise")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                    ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return@executes 0 }
                                                val strength = IntegerArgumentType.getInteger(ctx, "strength")
                                                for (player in players) ServerPlayNetworking.send(player, ScreamerPayload(ms, strength, true, true, false))
                                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.shake_noise_restore", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                players.size
                                            }
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("minimize")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (player in players) ServerPlayNetworking.send(player, MinimizePayload.INSTANCE)
                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.minimize", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                players.size
                            }
                        )
                    )
                    .then(Commands.literal("errorspam")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .then(Commands.argument("title", StringArgumentType.string())
                                    .then(Commands.argument("text", StringArgumentType.string())
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val count = IntegerArgumentType.getInteger(ctx, "count")
                                            val title = StringArgumentType.getString(ctx, "title").replace("|", "\n")
                                            val text  = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                            for (player in players) ServerPlayNetworking.send(player, ErrorSpamPayload(title, text, count, false, false))
                                            ctx.source.sendSuccess({ Component.literal("ErrorSpam ($count) → ${players.size} | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                            players.size
                                        }
                                        .then(Commands.literal("--random")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val count = IntegerArgumentType.getInteger(ctx, "count")
                                                val title = StringArgumentType.getString(ctx, "title").replace("|", "\n")
                                                val text  = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                                for (player in players) ServerPlayNetworking.send(player, ErrorSpamPayload(title, text, count, true, false))
                                                ctx.source.sendSuccess({ Component.literal("ErrorSpam --random ($count) → ${players.size} | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                players.size
                                            }
                                            .then(Commands.literal("--minimize")
                                                .executes { ctx ->
                                                    val players = EntityArgument.getPlayers(ctx, "who")
                                                    val count = IntegerArgumentType.getInteger(ctx, "count")
                                                    val title = StringArgumentType.getString(ctx, "title").replace("|", "\n")
                                                    val text  = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                                    for (player in players) ServerPlayNetworking.send(player, ErrorSpamPayload(title, text, count, true, true))
                                                    ctx.source.sendSuccess({ Component.literal("ErrorSpam --random --minimize ($count) → ${players.size} | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                    players.size
                                                }
                                            )
                                        )
                                        .then(Commands.literal("--minimize")
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val count = IntegerArgumentType.getInteger(ctx, "count")
                                                val title = StringArgumentType.getString(ctx, "title").replace("|", "\n")
                                                val text  = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                                for (player in players) ServerPlayNetworking.send(player, ErrorSpamPayload(title, text, count, false, true))
                                                ctx.source.sendSuccess({ Component.literal("ErrorSpam --minimize ($count) → ${players.size} | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                players.size
                                            }
                                            .then(Commands.literal("--random")
                                                .executes { ctx ->
                                                    val players = EntityArgument.getPlayers(ctx, "who")
                                                    val count = IntegerArgumentType.getInteger(ctx, "count")
                                                    val title = StringArgumentType.getString(ctx, "title").replace("|", "\n")
                                                    val text  = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                                    for (player in players) ServerPlayNetworking.send(player, ErrorSpamPayload(title, text, count, true, true))
                                                    ctx.source.sendSuccess({ Component.literal("ErrorSpam --minimize --random ($count) → ${players.size} | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                    players.size
                                                }
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("overlaytext")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("time", StringArgumentType.string())
                                .then(Commands.argument("size", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        listOf("1", "2", "3", "4", "5", "rdm").forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .then(Commands.argument("scaleX", StringArgumentType.string())
                                        .suggests { _, builder ->
                                            listOf("rdm", "0.5", "0.75", "1.0", "1.5", "2.0").forEach { builder.suggest(it) }
                                            builder.buildFuture()
                                        }
                                        .then(Commands.argument("scaleY", StringArgumentType.string())
                                            .suggests { _, builder ->
                                                listOf("rdm", "0.5", "0.75", "1.0", "1.5", "2.0").forEach { builder.suggest(it) }
                                                builder.buildFuture()
                                            }
                                            .then(Commands.argument("color", StringArgumentType.string())
                                                .then(Commands.argument("text", StringArgumentType.string())
                                                    .executes { ctx -> sendOverlay(ctx, false, false, false) }
                                                    .then(Commands.literal("--random")
                                                        .executes { ctx -> sendOverlay(ctx, true, false, false) }
                                                        .then(Commands.literal("--sound")
                                                            .executes { ctx -> sendOverlay(ctx, true, true, false) }
                                                            .then(Commands.literal("--mctext")
                                                                .executes { ctx -> sendOverlay(ctx, true, true, true) }
                                                            )
                                                        )
                                                        .then(Commands.literal("--mctext")
                                                            .executes { ctx -> sendOverlay(ctx, true, false, true) }
                                                        )
                                                    )
                                                    .then(Commands.literal("--sound")
                                                        .executes { ctx -> sendOverlay(ctx, false, true, false) }
                                                        .then(Commands.literal("--random")
                                                            .executes { ctx -> sendOverlay(ctx, true, true, false) }
                                                            .then(Commands.literal("--mctext")
                                                                .executes { ctx -> sendOverlay(ctx, true, true, true) }
                                                            )
                                                        )
                                                        .then(Commands.literal("--mctext")
                                                            .executes { ctx -> sendOverlay(ctx, false, true, true) }
                                                        )
                                                    )
                                                    .then(Commands.literal("--mctext")
                                                        .executes { ctx -> sendOverlay(ctx, false, false, true) }
                                                        .then(Commands.literal("--random")
                                                            .executes { ctx -> sendOverlay(ctx, true, false, true) }
                                                        )
                                                        .then(Commands.literal("--sound")
                                                            .executes { ctx -> sendOverlay(ctx, false, true, true) }
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("overlayspam")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("count", StringArgumentType.string())
                                .then(Commands.argument("time", StringArgumentType.string())
                                    .then(Commands.argument("size", StringArgumentType.string())
                                        .suggests { _, builder ->
                                            listOf("1","2","3","4","5","rdm").forEach { builder.suggest(it) }
                                            builder.buildFuture()
                                        }
                                        .then(Commands.argument("scaleX", StringArgumentType.string())
                                            .suggests { _, builder ->
                                                listOf("rdm","0.5","0.75","1.0","1.5","2.0").forEach { builder.suggest(it) }
                                                builder.buildFuture()
                                            }
                                            .then(Commands.argument("scaleY", StringArgumentType.string())
                                                .suggests { _, builder ->
                                                    listOf("rdm","0.5","0.75","1.0","1.5","2.0").forEach { builder.suggest(it) }
                                                    builder.buildFuture()
                                                }
                                                .then(Commands.argument("color", StringArgumentType.string())
                                                    .then(Commands.argument("text", StringArgumentType.string())
                                                        .executes { ctx -> sendOverlaySpam(ctx, false, false) }
                                                        .then(Commands.literal("--random")
                                                            .executes { ctx -> sendOverlaySpam(ctx, true, false) }
                                                            .then(Commands.literal("--sound")
                                                                .executes { ctx -> sendOverlaySpam(ctx, true, true) }
                                                            )
                                                        )
                                                        .then(Commands.literal("--sound")
                                                            .executes { ctx -> sendOverlaySpam(ctx, false, true) }
                                                            .then(Commands.literal("--random")
                                                                .executes { ctx -> sendOverlaySpam(ctx, true, true) }
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("console")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("time", StringArgumentType.string())
                                .then(Commands.argument("color", StringArgumentType.string())
                                    .then(Commands.argument("text", StringArgumentType.string())
                                        .executes { ctx -> sendConsoleOverlay(ctx, screamer = false, screamerVol = 0.85f) }
                                        .then(Commands.literal("--mctext")
                                            .executes { ctx -> sendConsoleOverlay(ctx, screamer = false, screamerVol = 0.85f, mcText = true) }
                                        )
                                        .then(Commands.literal("--screamer")
                                            .executes { ctx -> sendConsoleOverlay(ctx, screamer = true, screamerVol = 0.85f, screamerMs = 2000L) }
                                            .then(Commands.literal("--mctext")
                                                .executes { ctx -> sendConsoleOverlay(ctx, screamer = true, screamerVol = 0.85f, screamerMs = 2000L, mcText = true) }
                                            )
                                            .then(Commands.argument("screamerVol", StringArgumentType.string())
                                                .suggests { _, b -> listOf("0.3","0.5","0.7","0.85","1.0").forEach { b.suggest(it) }; b.buildFuture() }
                                                .executes { ctx ->
                                                    val vol = StringArgumentType.getString(ctx, "screamerVol").toFloatOrNull()?.coerceIn(0f,1f) ?: 0.85f
                                                    sendConsoleOverlay(ctx, screamer = true, screamerVol = vol, screamerMs = 2000L)
                                                }
                                                .then(Commands.literal("--mctext")
                                                    .executes { ctx ->
                                                        val vol = StringArgumentType.getString(ctx, "screamerVol").toFloatOrNull()?.coerceIn(0f,1f) ?: 0.85f
                                                        sendConsoleOverlay(ctx, screamer = true, screamerVol = vol, screamerMs = 2000L, mcText = true)
                                                    }
                                                )
                                                .then(Commands.argument("screamerSec", StringArgumentType.string())
                                                    .suggests { _, b -> listOf("1","2","3","5","10").forEach { b.suggest(it) }; b.buildFuture() }
                                                    .executes { ctx ->
                                                        val vol = StringArgumentType.getString(ctx, "screamerVol").toFloatOrNull()?.coerceIn(0f,1f) ?: 0.85f
                                                        val ms  = (StringArgumentType.getString(ctx, "screamerSec").toDoubleOrNull()?.coerceAtLeast(0.1) ?: 2.0).times(1000).toLong()
                                                        sendConsoleOverlay(ctx, screamer = true, screamerVol = vol, screamerMs = ms)
                                                    }
                                                    .then(Commands.literal("--mctext")
                                                        .executes { ctx ->
                                                            val vol = StringArgumentType.getString(ctx, "screamerVol").toFloatOrNull()?.coerceIn(0f,1f) ?: 0.85f
                                                            val ms  = (StringArgumentType.getString(ctx, "screamerSec").toDoubleOrNull()?.coerceAtLeast(0.1) ?: 2.0).times(1000).toLong()
                                                            sendConsoleOverlay(ctx, screamer = true, screamerVol = vol, screamerMs = ms, mcText = true)
                                                        }
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("screamer")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx -> sendScreamerSound(ctx, 0.85f, 2000L, false) }
                            .then(Commands.argument("volume", StringArgumentType.string())
                                .suggests { _, b -> listOf("0.3","0.5","0.7","0.85","1.0").forEach { b.suggest(it) }; b.buildFuture() }
                                .executes { ctx ->
                                    val vol = StringArgumentType.getString(ctx, "volume").toFloatOrNull()?.coerceIn(0f,1f) ?: 0.85f
                                    sendScreamerSound(ctx, vol, 2000L, false)
                                }
                                .then(Commands.argument("duration", StringArgumentType.string())
                                    .suggests { _, b -> listOf("1s","2s","3s","5s","10s").forEach { b.suggest(it) }; b.buildFuture() }
                                    .executes { ctx ->
                                        val vol = StringArgumentType.getString(ctx, "volume").toFloatOrNull()?.coerceIn(0f,1f) ?: 0.85f
                                        val dur = parseTime(StringArgumentType.getString(ctx, "duration")) ?: 2000L
                                        sendScreamerSound(ctx, vol, dur, false)
                                    }
                                )
                            )
                        )
                    )
                    .then(Commands.literal("chat")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("text", StringArgumentType.string())
                                .executes { ctx ->
                                    val players = EntityArgument.getPlayers(ctx, "who")
                                    val text = StringArgumentType.getString(ctx, "text")
                                    for (player in players) ServerPlayNetworking.send(player, PossessPayload(text, 0))
                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.chat", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                    players.size
                                }
                                .then(Commands.literal("--send")
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val text = StringArgumentType.getString(ctx, "text")
                                        for (player in players) ServerPlayNetworking.send(player, PossessPayload(text, PossessPayload.FLAG_SEND))
                                        ctx.source.sendSuccess({ Component.literal("Chat --send sent to ${players.size} player(s) | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                        players.size
                                    }
                                )
                                .then(Commands.literal("--perspective")
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val text = StringArgumentType.getString(ctx, "text")
                                        for (player in players) ServerPlayNetworking.send(player, PossessPayload(text, PossessPayload.FLAG_PERSPECTIVE))
                                        ctx.source.sendSuccess({ Component.literal("Chat --perspective sent to ${players.size} player(s) | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                        players.size
                                    }
                                    .then(Commands.literal("--send")
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val text = StringArgumentType.getString(ctx, "text")
                                            val flags = PossessPayload.FLAG_PERSPECTIVE or PossessPayload.FLAG_SEND
                                            for (player in players) ServerPlayNetworking.send(player, PossessPayload(text, flags))
                                            ctx.source.sendSuccess({ Component.literal("Chat --perspective --send sent to ${players.size} player(s) | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                            players.size
                                        }
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("perspective")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (player in players) ServerPlayNetworking.send(player, PossessPayload("", PossessPayload.FLAG_PERSPECTIVE))
                                ctx.source.sendSuccess({ Component.literal("Perspective toggled for ${players.size} player(s) | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                players.size
                            }
                        )
                    )
                    .then(Commands.literal("disclaimer")
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (player in players) ServerPlayNetworking.send(player, ShowDisclaimerPayload())
                                ctx.source.sendSuccess({ Component.literal("Disclaimer shown to ${players.size} player(s) | ").append(playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                players.size
                            }
                        )
                    )
                    .then(Commands.literal("toast")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("text", StringArgumentType.string())
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val title   = StringArgumentType.getString(ctx, "title")
                                        val text    = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                        for (player in players) ServerPlayNetworking.send(player, ToastPayload(title, text, "Info", 5000))
                                        ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.toast", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                        players.size
                                    }
                                    .then(Commands.argument("icon", StringArgumentType.word())
                                        .suggests { _, builder ->
                                            listOf("Info", "Warning", "Error", "None")
                                                .forEach { builder.suggest(it) }
                                            builder.buildFuture()
                                        }
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val title   = StringArgumentType.getString(ctx, "title")
                                            val text    = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                            val icon    = StringArgumentType.getString(ctx, "icon")
                                                .replaceFirstChar { it.uppercase() }
                                                .let { if (it in setOf("Info","Warning","Error","None")) it else "Info" }
                                            for (player in players) ServerPlayNetworking.send(player, ToastPayload(title, text, icon, 5000))
                                            ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.toast", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                            players.size
                                        }
                                        .then(Commands.argument("durationMs", IntegerArgumentType.integer(1000, 30000))
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val title   = StringArgumentType.getString(ctx, "title")
                                                val text    = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                                val icon    = StringArgumentType.getString(ctx, "icon")
                                                    .replaceFirstChar { it.uppercase() }
                                                    .let { if (it in setOf("Info","Warning","Error","None")) it else "Info" }
                                                val dur     = IntegerArgumentType.getInteger(ctx, "durationMs")
                                                for (player in players) ServerPlayNetworking.send(player, ToastPayload(title, text, icon, dur))
                                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.toast", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                players.size
                                            }
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("msgbox")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("title", StringArgumentType.string())
                                .then(Commands.argument("text", StringArgumentType.string())
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val title   = StringArgumentType.getString(ctx, "title")
                                        val text    = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                        for (player in players) ServerPlayNetworking.send(player, MsgBoxPayload(title, text, "OK", "Info"))
                                        ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.msgbox", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                        players.size
                                    }
                                    .then(Commands.argument("buttons", StringArgumentType.word())
                                        .suggests { _, builder ->
                                            listOf("OK", "OKCancel", "YesNo", "YesNoCancel", "RetryCancel", "AbortRetryIgnore")
                                                .forEach { builder.suggest(it) }
                                            builder.buildFuture()
                                        }
                                        .executes { ctx ->
                                            val players  = EntityArgument.getPlayers(ctx, "who")
                                            val title    = StringArgumentType.getString(ctx, "title")
                                            val text     = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                            val validBtn = setOf("OK","OKCancel","YesNo","YesNoCancel","RetryCancel","AbortRetryIgnore")
                                            val buttons  = StringArgumentType.getString(ctx, "buttons")
                                                .let { b -> validBtn.firstOrNull { it.equals(b, ignoreCase = true) } ?: "OK" }
                                            for (player in players) ServerPlayNetworking.send(player, MsgBoxPayload(title, text, buttons, "Info"))
                                            ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.msgbox", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                            players.size
                                        }
                                        .then(Commands.argument("icon", StringArgumentType.word())
                                            .suggests { _, builder ->
                                                listOf("Info", "Warning", "Error", "Question", "None")
                                                    .forEach { builder.suggest(it) }
                                                builder.buildFuture()
                                            }
                                            .executes { ctx ->
                                                val players  = EntityArgument.getPlayers(ctx, "who")
                                                val title    = StringArgumentType.getString(ctx, "title")
                                                val text     = StringArgumentType.getString(ctx, "text").replace("|", "\n")
                                                val validBtn = setOf("OK","OKCancel","YesNo","YesNoCancel","RetryCancel","AbortRetryIgnore")
                                                val buttons  = StringArgumentType.getString(ctx, "buttons")
                                                    .let { b -> validBtn.firstOrNull { it.equals(b, ignoreCase = true) } ?: "OK" }
                                                val icon     = StringArgumentType.getString(ctx, "icon")
                                                    .let { i -> setOf("Info","Warning","Error","Question","None").firstOrNull { it.equals(i, ignoreCase = true) } ?: "Info" }
                                                for (player in players) ServerPlayNetworking.send(player, MsgBoxPayload(title, text, buttons, icon))
                                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.msgbox", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                players.size
                                            }
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("colorbars")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("time", StringArgumentType.string())
                                .suggests { _, b ->
                                    listOf("3s", "5s", "10s", "30s", "1m").forEach { b.suggest(it) }
                                    b.buildFuture()
                                }
                                .executes { ctx ->
                                    val players = EntityArgument.getPlayers(ctx, "who")
                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                        ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                    val type = ("smpte").lowercase()
                                    if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                        ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                        return@executes 0
                                    }
                                    for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, false, type, "", ""))
                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                    players.size
                                }
                                .then(Commands.literal("--tone")
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                        val type = ("smpte").lowercase()
                                        if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                            ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                            return@executes 0
                                        }
                                        for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, true, type, "", ""))
                                        ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                        players.size
                                    }
                                    .then(Commands.literal("--label")
                                        .then(Commands.argument("corner", StringArgumentType.word())
                                            .suggests { _, b ->
                                                listOf("tl", "tr", "bl", "br").forEach { b.suggest(it) }
                                                b.buildFuture()
                                            }
                                            .then(Commands.argument("labeltext", StringArgumentType.string())
                                                .executes { ctx ->
                                                    val players = EntityArgument.getPlayers(ctx, "who")
                                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                        ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                                    val type = ("smpte").lowercase()
                                                    if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                                        ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                                        return@executes 0
                                                    }
                                                    for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, true, type, StringArgumentType.getString(ctx, "labeltext"), StringArgumentType.getString(ctx, "corner")))
                                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                    players.size
                                                }
                                            )
                                        )
                                    )
                                )
                                .then(Commands.literal("--label")
                                    .then(Commands.argument("corner", StringArgumentType.word())
                                        .suggests { _, b ->
                                            listOf("tl", "tr", "bl", "br").forEach { b.suggest(it) }
                                            b.buildFuture()
                                        }
                                        .then(Commands.argument("labeltext", StringArgumentType.string())
                                            .executes { ctx ->
                                                val players = EntityArgument.getPlayers(ctx, "who")
                                                val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                    ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                                val type = ("smpte").lowercase()
                                                if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                                    ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                                    return@executes 0
                                                }
                                                for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, false, type, StringArgumentType.getString(ctx, "labeltext"), StringArgumentType.getString(ctx, "corner")))
                                                ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                players.size
                                            }
                                            .then(Commands.literal("--tone")
                                                .executes { ctx ->
                                                    val players = EntityArgument.getPlayers(ctx, "who")
                                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                        ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                                    val type = ("smpte").lowercase()
                                                    if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                                        ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                                        return@executes 0
                                                    }
                                                    for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, true, type, StringArgumentType.getString(ctx, "labeltext"), StringArgumentType.getString(ctx, "corner")))
                                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                    players.size
                                                }
                                            )
                                        )
                                    )
                                )
                                .then(Commands.argument("type", StringArgumentType.word())
                                    .suggests { _, b ->
                                        listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb").forEach { b.suggest(it) }
                                        b.buildFuture()
                                    }
                                    .executes { ctx ->
                                        val players = EntityArgument.getPlayers(ctx, "who")
                                        val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                        val type = (StringArgumentType.getString(ctx, "type")).lowercase()
                                        if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                            ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                            return@executes 0
                                        }
                                        for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, false, type, "", ""))
                                        ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                        players.size
                                    }
                                    .then(Commands.literal("--tone")
                                        .executes { ctx ->
                                            val players = EntityArgument.getPlayers(ctx, "who")
                                            val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                            val type = (StringArgumentType.getString(ctx, "type")).lowercase()
                                            if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                                ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                                return@executes 0
                                            }
                                            for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, true, type, "", ""))
                                            ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                            players.size
                                        }
                                        .then(Commands.literal("--label")
                                            .then(Commands.argument("corner", StringArgumentType.word())
                                                .suggests { _, b ->
                                                    listOf("tl", "tr", "bl", "br").forEach { b.suggest(it) }
                                                    b.buildFuture()
                                                }
                                                .then(Commands.argument("labeltext", StringArgumentType.string())
                                                    .executes { ctx ->
                                                        val players = EntityArgument.getPlayers(ctx, "who")
                                                        val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                                        val type = (StringArgumentType.getString(ctx, "type")).lowercase()
                                                        if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                                            ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                                            return@executes 0
                                                        }
                                                        for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, true, type, StringArgumentType.getString(ctx, "labeltext"), StringArgumentType.getString(ctx, "corner")))
                                                        ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                        players.size
                                                    }
                                                )
                                            )
                                        )
                                    )
                                    .then(Commands.literal("--label")
                                        .then(Commands.argument("corner", StringArgumentType.word())
                                            .suggests { _, b ->
                                                listOf("tl", "tr", "bl", "br").forEach { b.suggest(it) }
                                                b.buildFuture()
                                            }
                                            .then(Commands.argument("labeltext", StringArgumentType.string())
                                                .executes { ctx ->
                                                    val players = EntityArgument.getPlayers(ctx, "who")
                                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                        ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                                    val type = (StringArgumentType.getString(ctx, "type")).lowercase()
                                                    if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                                        ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                                        return@executes 0
                                                    }
                                                    for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, false, type, StringArgumentType.getString(ctx, "labeltext"), StringArgumentType.getString(ctx, "corner")))
                                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                    players.size
                                                }
                                                .then(Commands.literal("--tone")
                                                    .executes { ctx ->
                                                        val players = EntityArgument.getPlayers(ctx, "who")
                                                        val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                                            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s, 0.5s или 2m")); return@executes 0 }
                                                        val type = (StringArgumentType.getString(ctx, "type")).lowercase()
                                                        if (type !in listOf("smpte", "hd", "ebu", "pluge", "mono", "rgb")) {
                                                            ctx.source.sendFailure(Component.literal("Тип полос: smpte, hd, ebu, pluge, mono, rgb, static"))
                                                            return@executes 0
                                                        }
                                                        for (player in players) ServerPlayNetworking.send(player, ColorBarsPayload(ms, true, type, StringArgumentType.getString(ctx, "labeltext"), StringArgumentType.getString(ctx, "corner")))
                                                        ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.colorbars", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                                        players.size
                                                    }
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("invert")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("time", StringArgumentType.string())
                                .suggests { _, b ->
                                    listOf("3s", "5s", "10s", "30s", "1m").forEach { b.suggest(it) }
                                    b.buildFuture()
                                }
                                .executes { ctx ->
                                    val players = EntityArgument.getPlayers(ctx, "who")
                                    val ms = parseTime(StringArgumentType.getString(ctx, "time"))
                                        ?: return@executes ctx.source.sendFailure(Component.literal("Неверный формат времени (пример: 5s, 1m)")).let { 0 }
                                    for (player in players) ServerPlayNetworking.send(player, VisualEffectPayload("invert", ms))
                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.invert", players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                    players.size
                                }
                            )
                        )
                    )
                    .then(Commands.literal("syssound")
                        .then(Commands.argument("who", EntityArgument.players())
                            .then(Commands.argument("sound", StringArgumentType.word())
                                .suggests { _, builder ->
                                    listOf("Hand", "Asterisk", "Beep", "Exclamation", "Question")
                                        .forEach { builder.suggest(it) }
                                    builder.buildFuture()
                                }
                                .executes { ctx ->
                                    val players   = EntityArgument.getPlayers(ctx, "who")
                                    val validSnd  = setOf("Hand","Asterisk","Beep","Exclamation","Question")
                                    val sound     = StringArgumentType.getString(ctx, "sound")
                                        .let { s -> validSnd.firstOrNull { it.equals(s, ignoreCase = true) } ?: "Hand" }
                                    for (player in players) ServerPlayNetworking.send(player, SysSoundPayload(sound))
                                    ctx.source.sendSuccess({ Component.translatable("ypm.cmd.sent.syssound", sound, players.size, playerGroupSummaryComponent(players, CmdType.OVERLAY)) }, true)
                                    players.size
                                }
                            )
                        )
                    )
            )
        }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("ypmutil")
                    .requires { it.permissions().hasPermission(Permissions.COMMANDS_OWNER) || !it.isPlayer() }
                    .then(Commands.literal("overlaytext")
                        .then(Commands.argument("overlay_to", StringArgumentType.word())
                            .suggests { ctx, b ->
                                val server = ctx.source.server
                                server.playerList.players.forEach { b.suggest(it.gameProfile.name) }
                                b.suggest("r10")
                                b.suggest("r20")
                                b.suggest("r50")
                                b.buildFuture()
                            }
                            .then(Commands.argument("time", StringArgumentType.string())
                                .then(Commands.argument("size", StringArgumentType.string())
                                    .suggests { _, b -> listOf("1","2","3","4","5","rdm").forEach { b.suggest(it) }; b.buildFuture() }
                                    .then(Commands.argument("scaleX", StringArgumentType.string())
                                        .suggests { _, b -> listOf("rdm","0.5","0.75","1.0","1.5","2.0").forEach { b.suggest(it) }; b.buildFuture() }
                                        .then(Commands.argument("scaleY", StringArgumentType.string())
                                            .suggests { _, b -> listOf("rdm","0.5","0.75","1.0","1.5","2.0").forEach { b.suggest(it) }; b.buildFuture() }
                                            .then(Commands.argument("color", StringArgumentType.string())
                                                .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = false, sound = false) }
                                                .then(Commands.literal("--random")
                                                    .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = true, sound = false) }
                                                    .then(Commands.literal("--sound")
                                                        .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = true, sound = true) }
                                                        .then(Commands.literal("--mctext")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = true, sound = true, mcText = true) }
                                                        )
                                                    )
                                                    .then(Commands.literal("--mctext")
                                                        .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = true, sound = false, mcText = true) }
                                                        .then(Commands.literal("--sound")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = true, sound = true, mcText = true) }
                                                        )
                                                    )
                                                )
                                                .then(Commands.literal("--sound")
                                                    .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = false, sound = true) }
                                                    .then(Commands.literal("--random")
                                                        .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = true, sound = true) }
                                                        .then(Commands.literal("--mctext")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = true, sound = true, mcText = true) }
                                                        )
                                                    )
                                                    .then(Commands.literal("--mctext")
                                                        .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = false, sound = true, mcText = true) }
                                                        .then(Commands.literal("--random")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = true, sound = true, mcText = true) }
                                                        )
                                                    )
                                                )
                                                .then(Commands.literal("--mctext")
                                                    .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = false, sound = false, mcText = true) }
                                                    .then(Commands.literal("--random")
                                                        .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = true, sound = false, mcText = true) }
                                                    )
                                                    .then(Commands.literal("--sound")
                                                        .executes { ctx -> startOverlaySession(ctx, chatFrom = null, randomPos = false, sound = true, mcText = true) }
                                                    )
                                                )
                                                .then(Commands.argument("chat_from", EntityArgument.players())
                                                    .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = false, sound = false) }
                                                    .then(Commands.literal("--random")
                                                        .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = true, sound = false) }
                                                        .then(Commands.literal("--sound")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = true, sound = true) }
                                                            .then(Commands.literal("--mctext")
                                                                .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = true, sound = true, mcText = true) }
                                                            )
                                                        )
                                                        .then(Commands.literal("--mctext")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = true, sound = false, mcText = true) }
                                                            .then(Commands.literal("--sound")
                                                                .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = true, sound = true, mcText = true) }
                                                            )
                                                        )
                                                    )
                                                    .then(Commands.literal("--sound")
                                                        .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = false, sound = true) }
                                                        .then(Commands.literal("--random")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = true, sound = true) }
                                                            .then(Commands.literal("--mctext")
                                                                .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = true, sound = true, mcText = true) }
                                                            )
                                                        )
                                                        .then(Commands.literal("--mctext")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = false, sound = true, mcText = true) }
                                                            .then(Commands.literal("--random")
                                                                .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = true, sound = true, mcText = true) }
                                                            )
                                                        )
                                                    )
                                                    .then(Commands.literal("--mctext")
                                                        .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = false, sound = false, mcText = true) }
                                                        .then(Commands.literal("--random")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = true, sound = false, mcText = true) }
                                                        )
                                                        .then(Commands.literal("--sound")
                                                            .executes { ctx -> startOverlaySession(ctx, chatFrom = EntityArgument.getPlayers(ctx, "chat_from"), randomPos = false, sound = true, mcText = true) }
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    .then(Commands.literal("stop")
                        .executes { ctx ->
                            val executor = ctx.source.player
                            if (executor == null) {
                                ctx.source.sendFailure(Component.literal("При запуске из командного блока укажи /ypmutil stop <who>"))
                                return@executes 0
                            }
                            ServerPlayNetworking.send(executor, OverlaySessionPayload(active = false))
                            ctx.source.sendSuccess({ Component.literal("§aOverlay-сессия остановлена.") }, false)
                            1
                        }
                        .then(Commands.argument("who", EntityArgument.players())
                            .executes { ctx ->
                                val players = EntityArgument.getPlayers(ctx, "who")
                                for (p in players) ServerPlayNetworking.send(p, OverlaySessionPayload(active = false))
                                ctx.source.sendSuccess({ Component.literal("§aOverlay-сессия остановлена у ${players.size} игрок(ов).") }, false)
                                players.size
                            }
                        )
                    )
            )
        }
    }
    private fun resolveOverlayToNames(
        raw: String,
        executor: ServerPlayer,
    ): String = raw.trim()
    private fun startOverlaySession(
        ctx: com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack>,
        chatFrom: Collection<net.minecraft.server.level.ServerPlayer>?,
        randomPos: Boolean,
        sound: Boolean,
        mcText: Boolean = false,
    ): Int {
        val executor: net.minecraft.server.level.ServerPlayer? = ctx.source.player
        val overlayToRaw = StringArgumentType.getString(ctx, "overlay_to")
        val ms = parseTime(StringArgumentType.getString(ctx, "time"))
            ?: run { ctx.source.sendFailure(Component.literal("Используй 10s или 2m")); return 0 }
        val size  = parseSize(StringArgumentType.getString(ctx, "size"))
        val sxRaw = parseScaleArg(StringArgumentType.getString(ctx, "scaleX"))
        val syRaw = parseScaleArg(StringArgumentType.getString(ctx, "scaleY"))
        val color = StringArgumentType.getString(ctx, "color")
        val useRandomScale = sxRaw < 0f || syRaw < 0f
        val sx = if (sxRaw < 0f) 1.0f else sxRaw
        val sy = if (syRaw < 0f) 1.0f else syRaw
        val overlayToNames = if (executor != null) resolveOverlayToNames(overlayToRaw, executor) else overlayToRaw
        val sessionTargets: Collection<net.minecraft.server.level.ServerPlayer> = when {
            chatFrom != null -> chatFrom
            executor != null -> listOf(executor)
            else -> {
                ctx.source.sendFailure(Component.literal("При запуске из командного блока укажи <chat_from>"))
                return 0
            }
        }
        for (player in sessionTargets) {
            ServerPlayNetworking.send(
                player,
                OverlaySessionPayload(
                    active      = true,
                    targetName  = overlayToNames,
                    durationMs  = ms,
                    size        = size,
                    scaleX      = sx,
                    scaleY      = sy,
                    color       = color,
                    randomPos   = randomPos,
                    randomScale = useRandomScale,
                    sound       = sound,
                    mcText      = mcText,
                )
            )
        }
        val fromDesc = sessionTargets.joinToString(", ") { it.gameProfile.name }
        ctx.source.sendSuccess({
            Component.literal("§aOverlay-сессия: §f$fromDesc §7→ overlay к §f$overlayToNames §7(${StringArgumentType.getString(ctx, "time")}, size=$size, color=$color)")
        }, false)
        return 1
    }
}