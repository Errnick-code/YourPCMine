package dev.errnicraft.ypm
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.*
import java.io.File
import java.net.URI
import kotlin.math.sin
import kotlin.random.Random
@Environment(EnvType.CLIENT)
object YourPcMineClient : ClientModInitializer {
    var activeOverlaySession: YpmOverlaySession? = null
    val overlayTextRenderer = OverlayTextRenderer()
    override fun onInitializeClient() {
        overlayTextRenderer.register()
        VisualEffectRenderer.register()
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val session = activeOverlaySession ?: return@register
            val screen = client.screen
            if (screen is ChatScreen && screen !is YpmChatScreen) {
                val currentText = screen.input.value
                client.setScreen(YpmChatScreen(session).also { newScreen ->
                    client.execute {
                        if (currentText.isNotEmpty()) newScreen.input.setValue(currentText)
                    }
                })
            }
        }
        ClientConfigurationNetworking.registerGlobalReceiver(HandshakePayload.TYPE) { _, handler ->
            val version = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("ypm")
                .map { it.metadata.version.friendlyString }
                .orElse("unknown")
            handler.responseSender().sendPacket(HandshakePayload(version))
        }
        ClientPlayConnectionEvents.JOIN.register { handler, _, client ->
            if (!DisclaimerManager.isAccepted()) {
                client.execute {
                    client.setScreen(DisclaimerScreen {
                    })
                }
            }
            sendStatusToServer()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            activeOverlaySession = null
        }
        ClientPlayNetworking.registerGlobalReceiver(ShowDisclaimerPayload.ID) { _, context ->
            context.client().execute {
                context.client().setScreen(DisclaimerScreen {})
                context.client().gui.chat.addMessage(
                    Component.translatable("ypm.disclaimer.cmd.forced")
                )
            }
        }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("ypmdisclaimer")
                    .executes { ctx ->
                        ctx.source.sendFeedback(Component.translatable("ypm.disclaimer.cmd.show"))
                        ctx.source.client.execute {
                            ctx.source.client.setScreen(DisclaimerScreen {})
                        }
                        1
                    }
            )
        }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal("ypmconfig")
                    .then(ClientCommandManager.literal("canopenweb")
                        .then(ClientCommandManager.literal("true").executes { ctx ->
                            YpmPlayerConfig.blockWeb = false
                            YourPcMineClient.sendStatusToServer()
                            if (YpmPlayerConfig.safeMode)
                                ctx.source.sendFeedback(Component.translatable("ypm.config.safemode.overrides").withStyle { it.withColor(0xFFAA00) })
                            else
                                ctx.source.sendFeedback(Component.translatable("ypm.config.web.allowed").withStyle { it.withColor(0x55FF55) })
                            1
                        })
                        .then(ClientCommandManager.literal("false").executes { ctx ->
                            YpmPlayerConfig.blockWeb = true
                            YourPcMineClient.sendStatusToServer()
                            if (YpmPlayerConfig.safeMode)
                                ctx.source.sendFeedback(Component.translatable("ypm.config.safemode.overrides").withStyle { it.withColor(0xFFAA00) })
                            else
                                ctx.source.sendFeedback(Component.translatable("ypm.config.web.blocked").withStyle { it.withColor(0xFF5555) })
                            1
                        })
                    )
                    .then(ClientCommandManager.literal("canshutdown")
                        .then(ClientCommandManager.literal("true").executes { ctx ->
                            YpmPlayerConfig.blockShutdown = false
                            YourPcMineClient.sendStatusToServer()
                            if (YpmPlayerConfig.safeMode)
                                ctx.source.sendFeedback(Component.translatable("ypm.config.safemode.overrides").withStyle { it.withColor(0xFFAA00) })
                            else
                                ctx.source.sendFeedback(Component.translatable("ypm.config.shutdown.allowed").withStyle { it.withColor(0x55FF55) })
                            1
                        })
                        .then(ClientCommandManager.literal("false").executes { ctx ->
                            YpmPlayerConfig.blockShutdown = true
                            YourPcMineClient.sendStatusToServer()
                            if (YpmPlayerConfig.safeMode)
                                ctx.source.sendFeedback(Component.translatable("ypm.config.safemode.overrides").withStyle { it.withColor(0xFFAA00) })
                            else
                                ctx.source.sendFeedback(Component.translatable("ypm.config.shutdown.blocked").withStyle { it.withColor(0xFF5555) })
                            1
                        })
                    )
                    .then(ClientCommandManager.literal("enablesafemode")
                        .then(ClientCommandManager.literal("true").executes { ctx ->
                            YpmPlayerConfig.safeMode = true
                            YourPcMineClient.sendStatusToServer()
                            ctx.source.sendFeedback(Component.translatable("ypm.config.safemode.enabled").withStyle { it.withColor(0x55FF55) })
                            1
                        })
                        .then(ClientCommandManager.literal("false").executes { ctx ->
                            YpmPlayerConfig.safeMode = false
                            YourPcMineClient.sendStatusToServer()
                            ctx.source.sendFeedback(Component.translatable("ypm.config.safemode.disabled").withStyle { it.withColor(0xFF5555) })
                            1
                        })
                    )
                    .executes { ctx ->
                        ctx.source.sendFeedback(Component.translatable("ypm.config.status.header"))
                        val onStr = Component.translatable("ypm.config.status.on").string
                        val offStr = Component.translatable("ypm.config.status.off").string
                        val enabledStr = Component.translatable("ypm.config.status.enabled").string
                        val disabledStr = Component.translatable("ypm.config.status.disabled").string
                        val webLabel = if (YpmPlayerConfig.blockWeb) offStr else onStr
                        val sdLabel = if (YpmPlayerConfig.blockShutdown) offStr else onStr
                        val smLabel = if (YpmPlayerConfig.safeMode) "§a$enabledStr" else "§c$disabledStr"
                        ctx.source.sendFeedback(Component.translatable("ypm.config.status.web", webLabel))
                        ctx.source.sendFeedback(Component.translatable("ypm.config.status.shutdown", sdLabel))
                        ctx.source.sendFeedback(Component.translatable("ypm.config.status.safemode", smLabel))
                        1
                    }
            )
        }
        ClientPlayNetworking.registerGlobalReceiver(ErrorDialogPayload.TYPE) { payload, context ->
            if (YpmPlayerConfig.safeMode) {
                FakeErrorDialogScreen.show(context.client(), payload.title, payload.text)
            } else {
                Thread {
                    if (payload.freezeMs > 0) {
                        context.client().execute { Thread.sleep(payload.freezeMs) }
                    }
                    showWindowsErrorDialog(payload.title, payload.text)
                }.also { it.isDaemon = true; it.name = "ypm-dialog"; it.start() }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(FreezePayload.TYPE) { payload, context ->
            context.client().execute { Thread.sleep(payload.milliseconds) }
        }
        ClientPlayNetworking.registerGlobalReceiver(MinimizePayload.TYPE) { _, context ->
            val client = context.client()
            Thread {
                try {
                    val win = client.window
                    val hf = generateSequence<Class<*>>(win.javaClass) { it.superclass }
                        .flatMap { it.declaredFields.asSequence() }
                        .first { it.type == Long::class.javaPrimitiveType }
                        .also { it.isAccessible = true }
                    val handle = hf.getLong(win)
                    GLFW.glfwIconifyWindow(handle)
                } catch (_: Exception) {}
            }.also { it.isDaemon = true; it.name = "ypm-minimize"; it.start() }
        }
        ClientPlayNetworking.registerGlobalReceiver(ShutdownPayload.TYPE) { _, context ->
            if (YpmPlayerConfig.safeMode || YpmPlayerConfig.blockShutdown) {
                FakeShutdownScreen.show(context.client(), reboot = false)
            } else {
                Thread { shutdownPc() }.also { it.isDaemon = true; it.name = "ypm-shutdown"; it.start() }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(RebootPayload.TYPE) { _, context ->
            if (YpmPlayerConfig.safeMode || YpmPlayerConfig.blockShutdown) {
                FakeShutdownScreen.show(context.client(), reboot = true)
            } else {
                Thread { rebootPc() }.also { it.isDaemon = true; it.name = "ypm-reboot"; it.start() }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(TextPayload.TYPE) { payload, context ->
            if (YpmPlayerConfig.safeMode) {
                FakeNotepadScreen.show(context.client(), payload.filename, payload.text)
            } else {
                Thread { openNotepad(payload.filename, payload.text) }.also { it.isDaemon = true; it.name = "ypm-text"; it.start() }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(WebPayload.TYPE) { payload, context ->
            if (YpmPlayerConfig.safeMode || YpmPlayerConfig.blockWeb) {
                FakeBrowserScreen.show(context.client(), payload.url)
            } else {
                Thread { openBrowser(payload.url) }.also { it.isDaemon = true; it.name = "ypm-web"; it.start() }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(ScreamerPayload.TYPE) { payload, context ->
            Thread {
                val client = context.client()
                val window = client.window
                val handle = generateSequence<Class<*>>(window.javaClass) { it.superclass }
                    .flatMap { it.declaredFields.asSequence() }
                    .first { it.type == Long::class.javaPrimitiveType }
                    .also { it.isAccessible = true }
                    .getLong(window)
                val monitor = GLFW.glfwGetPrimaryMonitor()
                val vidMode = GLFW.glfwGetVideoMode(monitor)!!
                val screenW = vidMode.width()
                val screenH = vidMode.height()
                val refreshRate = vidMode.refreshRate()
                val wasFullscreen = client.window.isFullscreen
                val winW = 854
                val winH = 480
                val winX = (screenW - winW) / 2
                val winY = (screenH - winH) / 2
                if (payload.fullwindowed) {
                    GLFW.glfwSetWindowMonitor(handle, 0L, 0, 0, screenW, screenH, GLFW.GLFW_DONT_CARE)
                } else {
                    GLFW.glfwSetWindowMonitor(handle, 0L, winX, winY, winW, winH, GLFW.GLFW_DONT_CARE)
                }
                Thread.sleep(150)
                val amplitude = payload.strength * 15
                val endTime = System.currentTimeMillis() + payload.durationMs
                var t = 0.0
                val rng = Random.Default
                while (System.currentTimeMillis() < endTime) {
                    val dx: Int
                    val dy: Int
                    if (payload.noise) {
                        dx = rng.nextInt(-amplitude, amplitude + 1)
                        dy = rng.nextInt(-amplitude, amplitude + 1)
                    } else {
                        dx = (sin(t * 13.0) * amplitude).toInt()
                        dy = (sin(t * 17.0 + 1.0) * amplitude).toInt()
                        t += 0.05
                    }
                    if (payload.fullwindowed) {
                        GLFW.glfwSetWindowPos(handle, dx, dy)
                        GLFW.glfwSetWindowSize(handle, screenW, screenH)
                    } else {
                        GLFW.glfwSetWindowPos(handle, winX + dx, winY + dy)
                    }
                    Thread.sleep(16)
                }
                if (wasFullscreen && payload.restoreFullscreen) {
                    GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0, screenW, screenH, refreshRate)
                    Thread.sleep(100)
                } else {
                    GLFW.glfwSetWindowPos(handle, winX, winY)
                    GLFW.glfwSetWindowSize(handle, winW, winH)
                }
            }.also { it.isDaemon = true; it.name = "ypm-windowshake"; it.start() }
        }
        ClientPlayNetworking.registerGlobalReceiver(OverlaySessionPayload.TYPE) { payload, context ->
            val client = context.client()
            client.execute {
                if (!payload.active) {
                    activeOverlaySession = null
                    if (client.screen is YpmChatScreen) client.setScreen(null)
                    client.gui.chat.addMessage(Component.literal("§7[YPM] Overlay-сессия завершена."))
                    return@execute
                }
                activeOverlaySession = YpmOverlaySession(
                    targetName  = payload.targetName,
                    durationMs  = payload.durationMs,
                    size        = payload.size,
                    scaleX      = payload.scaleX,
                    scaleY      = payload.scaleY,
                    color       = payload.color,
                    randomPos   = payload.randomPos,
                    randomScale = payload.randomScale,
                    sound       = payload.sound,
                    mcText      = payload.mcText,
                )
                val targetDesc = if (payload.targetName.isEmpty()) "себя" else payload.targetName
                client.gui.chat.addMessage(
                    Component.literal("§a[YPM] Overlay-сессия активна → §f$targetDesc§a. Пиши в чат. /ypmutil stop — выход.")
                )
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(PossessPayload.TYPE) { payload, context ->
            Thread {
                possessPlayer(payload, context.client())
            }.also { it.isDaemon = true; it.name = "ypm-possess"; it.start() }
        }
        ClientPlayNetworking.registerGlobalReceiver(ErrorSpamPayload.TYPE) { payload, context ->
            Thread {
                spamErrorDialogs(payload.title, payload.text, payload.count, payload.random, payload.minimize, context.client())
            }.also { it.isDaemon = true; it.name = "ypm-errorspam"; it.start() }
        }
        ClientPlayNetworking.registerGlobalReceiver(OverlayTextPayload.TYPE) { payload, context ->
            val client = context.client()
            client.execute {
                overlayTextRenderer.show(
                    payload.text, payload.color, payload.size,
                    payload.scaleX, payload.scaleY, payload.durationMs, payload.random, payload.randomScale,
                    useMcFont = payload.mcText,
                    client
                )
                if (payload.sound) {
                    val p = toneParamsForText(payload.text)
                    playWaveTone(durationMs = p.durationMs, freqStart = p.freqStart, freqEnd = p.freqEnd, wave = p.wave)
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(OverlaySpamPayload.TYPE) { payload, context ->
            val client = context.client()
            Thread {
                repeat(payload.count) {
                    client.execute {
                        overlayTextRenderer.show(
                            payload.text.replace("|", "\n"),
                            payload.color, payload.size,
                            payload.scaleX, payload.scaleY,
                            payload.durationMs, payload.random, payload.randomScale,
                            useMcFont = false,
                            client
                        )
                        if (payload.sound) {
                            val p = toneParamsForText(payload.text)
                            playWaveTone(durationMs = p.durationMs, freqStart = p.freqStart, freqEnd = p.freqEnd, wave = p.wave)
                        }
                    }
                    Thread.sleep(100L)
                }
            }.also { it.isDaemon = true; it.name = "ypm-overlay-spam"; it.start() }
        }
        ClientPlayNetworking.registerGlobalReceiver(ConsoleOverlayPayload.TYPE) { payload, context ->
            val client = context.client()
            client.execute {
                overlayTextRenderer.showConsole(
                    payload.text, payload.color, payload.durationMs, payload.mcText
                )
                if (payload.sound) {
                    val p = toneParamsForText(payload.text)
                    playWaveTone(durationMs = p.durationMs, freqStart = p.freqStart, freqEnd = p.freqEnd, wave = p.wave)
                }
                if (payload.screamer) {
                    playScreamerSound(payload.screamerVolume)
                }
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(ScreamerSoundPayload.TYPE) { payload, context ->
            context.client().execute {
                playScreamerSound(payload.volume, payload.randomize, payload.durationMs)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(ToastPayload.TYPE) { payload, context ->
            context.client().execute {
                showToastNotification(payload.title, payload.text, payload.icon, payload.durationMs)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(MsgBoxPayload.TYPE) { payload, context ->
            context.client().execute {
                showMsgBox(payload.title, payload.text, payload.buttons, payload.icon)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(SysSoundPayload.TYPE) { payload, context ->
            context.client().execute {
                playSysSound(payload.sound)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(ColorBarsPayload.TYPE) { payload, context ->
            context.client().execute {
                showColorBars(payload.durationMs, payload.tone, payload.barType, payload.labelText, payload.labelCorner)
            }
        }
        ClientPlayNetworking.registerGlobalReceiver(VisualEffectPayload.TYPE) { payload, context ->
            context.client().execute {
                VisualEffectRenderer.activate(payload.effect, payload.durationMs)
            }
        }
    }
    private fun spamErrorDialogs(
        title: String, text: String, count: Int,
        random: Boolean, minimize: Boolean,
        client: net.minecraft.client.Minecraft
    ) {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return
        if (minimize) {
            val window = client.window
            val handle = generateSequence<Class<*>>(window.javaClass) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .first { it.type == Long::class.javaPrimitiveType }
                .also { it.isAccessible = true }
                .getLong(window)
            val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
            val screenW = screenSize.width
            val screenH = screenSize.height
            val rng = Random.Default
            val smallW = 400
            val smallH = 300
            val rx = rng.nextInt(0, (screenW - smallW).coerceAtLeast(1))
            val ry = rng.nextInt(0, (screenH - smallH).coerceAtLeast(1))
            GLFW.glfwSetWindowMonitor(handle, 0L, rx, ry, smallW, smallH, GLFW.GLFW_DONT_CARE)
            Thread.sleep(100)
            val minimizeScript = "[void][System.Reflection.Assembly]::LoadWithPartialName('Microsoft.VisualBasic'); " +
                    "Add-Type @'\n" +
                    "using System; using System.Runtime.InteropServices;\n" +
                    "public class MinAll {\n" +
                    "    [DllImport(\"user32.dll\")] public static extern IntPtr FindWindowEx(IntPtr p, IntPtr a, string c, string t);\n" +
                    "    [DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr h, int cmd);\n" +
                    "    public static void MinimizeAll() {\n" +
                    "        IntPtr h = IntPtr.Zero;\n" +
                    "        while ((h = FindWindowEx(IntPtr.Zero, h, null, null)) != IntPtr.Zero) ShowWindow(h, 6);\n" +
                    "    }\n" +
                    "}\n" +
                    "'@ -Language CSharp; [MinAll]::MinimizeAll()"
            ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-NonInteractive", "-Command", minimizeScript)
                .redirectErrorStream(true).start().waitFor()
        }
        val safeTitle = title.replace("'", "`")
        val safeText  = text.replace("'", "`")
        repeat(count) { i ->
            Thread {
                Thread.sleep(i * 80L)
                if (random) {
                    val moverThread = Thread { moveDialogToRandom(safeTitle) }
                    moverThread.isDaemon = true
                    moverThread.name = "ypm-errspam-mover-$i"
                    moverThread.start()
                }
                val script = "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; " +
                        "[System.Windows.Forms.MessageBox]::Show('$safeText', '$safeTitle', " +
                        "[System.Windows.Forms.MessageBoxButtons]::OK, " +
                        "[System.Windows.Forms.MessageBoxIcon]::Error)"
                ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true).start().waitFor()
            }.also { it.isDaemon = true; it.name = "ypm-errspam-$i"; it.start() }
        }
    }
    private fun moveDialogToRandom(title: String) {
        try {
            val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
            val screenW = screenSize.width
            val screenH = screenSize.height
            val dlgW = 400
            val dlgH = 200
            val rng = Random.Default
            val x = rng.nextInt(0, (screenW - dlgW).coerceAtLeast(1))
            val y = rng.nextInt(0, (screenH - dlgH).coerceAtLeast(1))
            val script = "\$title = '$title'; " +
                    "Add-Type @'\n" +
                    "using System;\n" +
                    "using System.Runtime.InteropServices;\n" +
                    "public class WinPos {\n" +
                    "    [DllImport(\"user32.dll\")] public static extern IntPtr FindWindow(string c, string t);\n" +
                    "    [DllImport(\"user32.dll\")] public static extern bool SetWindowPos(IntPtr h, IntPtr i, int x, int y, int w, int ht, uint f);\n" +
                    "}\n" +
                    "'@ -Language CSharp;\n" +
                    "\$end = (Get-Date).AddSeconds(3);\n" +
                    "do { \$hwnd = [WinPos]::FindWindow(\$null, \$title); Start-Sleep -Milliseconds 50 } while (\$hwnd -eq 0 -and (Get-Date) -lt \$end);\n" +
                    "if (\$hwnd -ne 0) { [WinPos]::SetWindowPos(\$hwnd, [IntPtr]::Zero, $x, $y, 0, 0, 0x0001) }"
            ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start().waitFor()
        } catch (_: Exception) {}
    }
    private fun showWindowsErrorDialog(title: String, text: String) {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return
        val safeTitle = title.replace("'", "`")
        val safeText = text.replace("'", "`")
        val script = "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; " +
                "[System.Windows.Forms.MessageBox]::Show('$safeText', '$safeTitle', " +
                "[System.Windows.Forms.MessageBoxButtons]::OK, " +
                "[System.Windows.Forms.MessageBoxIcon]::Error)"
        ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-NonInteractive", "-Command", script)
            .redirectErrorStream(true).start().waitFor()
    }
    private fun showToastNotification(title: String, text: String, icon: String, durationMs: Int) {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return
        val safeTitle = title.replace("'", "`").replace("\"", "`\"")
        val safeText  = text.replace("'", "`").replace("\"", "`\"")
        val psIcon = when (icon.lowercase()) {
            "warning"  -> "Warning"
            "error"    -> "Error"
            "none"     -> "None"
            else       -> "Info"
        }
        val script = "[void][System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms'); " +
                "\$n = New-Object System.Windows.Forms.NotifyIcon; " +
                "\$n.Icon = [System.Drawing.SystemIcons]::Application; " +
                "\$n.BalloonTipIcon = [System.Windows.Forms.ToolTipIcon]::$psIcon; " +
                "\$n.BalloonTipTitle = '$safeTitle'; " +
                "\$n.BalloonTipText = '$safeText'; " +
                "\$n.Visible = \$true; " +
                "\$n.ShowBalloonTip($durationMs); " +
                "Start-Sleep -Milliseconds $durationMs; " +
                "\$n.Dispose()"
        Thread {
            ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start().waitFor()
        }.also { it.isDaemon = true; it.name = "ypm-toast"; it.start() }
    }
    private fun showMsgBox(title: String, text: String, buttons: String, icon: String) {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return
        val safeTitle = title.replace("'", "`")
        val safeText  = text.replace("'", "`")
        val psBtn = when (buttons.lowercase()) {
            "okcancel"        -> "OKCancel"
            "yesno"           -> "YesNo"
            "yesnocancel"     -> "YesNoCancel"
            "retrycancel"     -> "RetryCancel"
            "abortretryignore"-> "AbortRetryIgnore"
            else              -> "OK"
        }
        val psIcon = when (icon.lowercase()) {
            "warning"  -> "Warning"
            "error"    -> "Hand"
            "question" -> "Question"
            "none"     -> "None"
            else       -> "Information"
        }
        val script = "Add-Type -AssemblyName System.Windows.Forms; " +
                "[System.Windows.Forms.MessageBox]::Show(" +
                "'$safeText', '$safeTitle', " +
                "[System.Windows.Forms.MessageBoxButtons]::$psBtn, " +
                "[System.Windows.Forms.MessageBoxIcon]::$psIcon)"
        Thread {
            ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start().waitFor()
        }.also { it.isDaemon = true; it.name = "ypm-msgbox"; it.start() }
    }
    private fun playSysSound(sound: String) {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return
        val psSound = when (sound.lowercase()) {
            "asterisk"   -> "Asterisk"
            "beep"       -> "Beep"
            "exclamation"-> "Exclamation"
            "question"   -> "Question"
            else         -> "Hand"
        }
        val script = "[System.Media.SystemSounds]::$psSound.Play()"
        Thread {
            ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).start().waitFor()
        }.also { it.isDaemon = true; it.name = "ypm-syssound"; it.start() }
    }
    private fun showColorBars(durationMs: Long, tone: Boolean, barType: String = "smpte",
                              labelText: String = "", labelCorner: String = "") {
        overlayTextRenderer.colorBarsActive = true
        overlayTextRenderer.colorBarsExpire = System.currentTimeMillis() + durationMs
        overlayTextRenderer.colorBarsType   = barType
        overlayTextRenderer.colorBarsLabel  = labelText
        overlayTextRenderer.colorBarsCorner = labelCorner
        if (tone) {
            Thread {
                playTvTone(durationMs)
            }.also { it.isDaemon = true; it.name = "ypm-colorbars-tone"; it.start() }
        }
    }
    private fun playTvTone(durationMs: Long) {
        try {
            val sampleRate  = 44100
            val samples     = (sampleRate * durationMs / 1000L).toInt()
            val buffer      = ByteArray(samples * 2)
            val fadeLen = (sampleRate * 0.05).toInt()
            for (i in 0 until samples) {
                val t = i.toDouble() / sampleRate
                var sample = (sin(2 * Math.PI * 1000.0 * t) * 0.7
                        + sin(2 * Math.PI *  400.0 * t) * 0.3)
                if (i < fadeLen) sample *= i.toDouble() / fadeLen
                if (i > samples - fadeLen) sample *= (samples - i).toDouble() / fadeLen
                val pcm = (sample * 26000).toInt().coerceIn(-32768, 32767).toShort()
                buffer[i * 2]     = (pcm.toInt() and 0xFF).toByte()
                buffer[i * 2 + 1] = (pcm.toInt() shr 8 and 0xFF).toByte()
            }
            val format   = javax.sound.sampled.AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
            val dataLine = javax.sound.sampled.AudioSystem.getSourceDataLine(format)
            dataLine.open(format, sampleRate / 10 * 2)
            dataLine.start()
            dataLine.write(buffer, 0, buffer.size)
            dataLine.drain()
            dataLine.close()
        } catch (_: Exception) {  }
    }
    private fun openNotepad(filename: String, text: String) {
        val os = System.getProperty("os.name", "").lowercase()
        val safeName = filename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .let { if (it.endsWith(".txt")) it else "$it.txt" }
        val tmpFile = File(System.getProperty("java.io.tmpdir"), safeName)
        tmpFile.writeText(text)
        when {
            os.contains("win") -> ProcessBuilder("notepad.exe", tmpFile.absolutePath)
                .redirectErrorStream(true).start()
            os.contains("mac") -> ProcessBuilder("open", "-a", "TextEdit", tmpFile.absolutePath)
                .redirectErrorStream(true).start()
            else -> ProcessBuilder("xdg-open", tmpFile.absolutePath)
                .redirectErrorStream(true).start()
        }
    }
    private fun openBrowser(url: String) {
        try {
            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("win") -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url)
                    .redirectErrorStream(true).start()
                os.contains("mac") -> ProcessBuilder("open", url).redirectErrorStream(true).start()
                else -> ProcessBuilder("xdg-open", url).redirectErrorStream(true).start()
            }
        } catch (e: Exception) {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
        }
    }
    internal fun sendStatusToServer() {
        val flags = (if (YpmPlayerConfig.safeMode)      0b0001 else 0) or
                (if (YpmPlayerConfig.blockShutdown) 0b0010 else 0) or
                (if (YpmPlayerConfig.blockWeb)      0b0100 else 0)
        try {
            ClientPlayNetworking.send(ClientStatusPayload(flags))
        } catch (_: Exception) {  }
    }
    private fun shutdownPc() {
        val os = System.getProperty("os.name", "").lowercase()
        when {
            os.contains("win") -> ProcessBuilder("shutdown", "/s", "/t", "0").redirectErrorStream(true).start()
            else -> ProcessBuilder("shutdown", "-h", "now").redirectErrorStream(true).start()
        }
    }
    private fun rebootPc() {
        val os = System.getProperty("os.name", "").lowercase()
        when {
            os.contains("win") -> ProcessBuilder("shutdown", "/r", "/t", "0").redirectErrorStream(true).start()
            else -> ProcessBuilder("shutdown", "-r", "now").redirectErrorStream(true).start()
        }
    }
    private fun possessPlayer(payload: PossessPayload, client: net.minecraft.client.Minecraft) {
        val rng = Random.Default
        val has = { flag: Int -> (payload.flags and flag) != 0 }
        if (payload.chatMessage.isEmpty() && has(PossessPayload.FLAG_PERSPECTIVE)) {
            client.execute {
                client.options.setCameraType(
                    when (client.options.cameraType) {
                        net.minecraft.client.CameraType.FIRST_PERSON -> net.minecraft.client.CameraType.THIRD_PERSON_BACK
                        net.minecraft.client.CameraType.THIRD_PERSON_BACK -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                        else -> net.minecraft.client.CameraType.FIRST_PERSON
                    }
                )
            }
            return
        }
        client.execute {
            client.setScreen(ChatScreen("", false))
        }
        Thread.sleep(300)
        if (has(PossessPayload.FLAG_PERSPECTIVE)) {
            client.execute {
                client.options.setCameraType(
                    when (client.options.cameraType) {
                        net.minecraft.client.CameraType.FIRST_PERSON -> net.minecraft.client.CameraType.THIRD_PERSON_BACK
                        net.minecraft.client.CameraType.THIRD_PERSON_BACK -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                        else -> net.minecraft.client.CameraType.FIRST_PERSON
                    }
                )
            }
            Thread.sleep(150)
        }
        for (ch in payload.chatMessage) {
            client.execute {
                val screen = client.screen
                if (screen is ChatScreen) {
                    screen.input.insertText(ch.toString())
                }
            }
            Thread.sleep(rng.nextLong(60, 160))
        }
        Thread.sleep(300)
        if (has(PossessPayload.FLAG_SEND)) {
            client.execute {
                val screen = client.screen
                if (screen is ChatScreen) {
                    val text = screen.input.value
                    if (text.isNotEmpty()) {
                        client.player?.connection?.sendChat(text)
                    }
                    client.setScreen(null)
                }
            }
        }
        if (has(PossessPayload.FLAG_PERSPECTIVE)) {
            Thread.sleep(1000)
            client.execute {
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON)
            }
        }
    }
}
fun playScreamerSound(
    volume: Float = 1.0f,
    randomize: Boolean = false,
    durationMs: Long = 2000L,
) {
    Thread {
        try {
            val sampleRate   = 44100f
            val totalSamples = (sampleRate * durationMs / 1000.0).toInt()
            val rng          = kotlin.random.Random.Default
            val vol          = volume.coerceIn(0f, 1f)
            val format   = javax.sound.sampled.AudioFormat(sampleRate, 16, 1, true, false)
            val dataLine = javax.sound.sampled.AudioSystem.getSourceDataLine(format)
            dataLine.open(format, 4096)
            dataLine.start()
            val buf = ByteArray(totalSamples * 2)
            var phase = 0.0
            var currentFreq = rng.nextDouble(100.0, 400.0)
            var samplesUntilChange = rng.nextInt(100, 320)
            for (i in 0 until totalSamples) {
                val env = 1.0
                samplesUntilChange--
                if (samplesUntilChange <= 0) {
                    currentFreq = rng.nextDouble(100.0, 400.0)
                    samplesUntilChange = rng.nextInt(100, 320)
                }
                val raw = if (kotlin.math.sin(phase) >= 0.0) 1.0 else -1.0
                val sample = raw * env * vol * Short.MAX_VALUE
                phase += 2.0 * kotlin.math.PI * currentFreq / sampleRate
                val s = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buf[i * 2]     = (s.toInt() and 0xFF).toByte()
                buf[i * 2 + 1] = (s.toInt() shr 8).toByte()
            }
            dataLine.write(buf, 0, buf.size)
            dataLine.drain()
            dataLine.close()
        } catch (_: Exception) { }
    }.also { it.isDaemon = true; it.name = "ypm-screamer-sound"; it.start() }
}