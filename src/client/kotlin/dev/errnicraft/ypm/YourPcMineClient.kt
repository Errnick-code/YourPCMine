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

    /** Активная overlay-сессия. null = сессии нет, чат работает обычно. */
    var activeOverlaySession: YpmOverlaySession? = null

    override fun onInitializeClient() {
        // Регистрируем HUD рендер для overlay текста
        OverlayTextRenderer.register()

        // Каждый тик проверяем: если открыт обычный ChatScreen и сессия активна — подменяем на YpmChatScreen.
        // Используем tick вместо ScreenEvents чтобы избежать рекурсии при setScreen внутри BEFORE_INIT.
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val session = activeOverlaySession ?: return@register
            val screen = client.screen
            // Подменяем только обычный ChatScreen (не YpmChatScreen — чтобы не зациклиться)
            if (screen is ChatScreen && screen !is YpmChatScreen) {
                // Сохраняем текущий текст в поле ввода (если игрок уже что-то написал)
                val currentText = screen.input.value
                client.setScreen(YpmChatScreen(session).also { newScreen ->
                    // После init() восстановим текст — делаем это через execute чтобы гарантировать порядок
                    client.execute {
                        if (currentText.isNotEmpty()) newScreen.input.setValue(currentText)
                    }
                })
            }
        }

        // Handshake — отвечаем версией мода
        ClientConfigurationNetworking.registerGlobalReceiver(HandshakePayload.TYPE) { _, handler ->
            val version = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("ypm")
                .map { it.metadata.version.friendlyString }
                .orElse("unknown")
            handler.responseSender().sendPacket(HandshakePayload(version))
        }

        // Дисклеймер при входе в мир/на сервер
        ClientPlayConnectionEvents.JOIN.register { handler, _, client ->
            if (!DisclaimerManager.isAccepted()) {
                client.execute {
                    client.setScreen(DisclaimerScreen {
                        // После закрытия дисклеймера ничего дополнительного не делаем
                    })
                }
            }
            // Отправить серверу текущий статус конфига
            sendStatusToServer()
        }

        // Сбрасываем overlay-сессию при отключении от сервера
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            activeOverlaySession = null
        }

        // Принудительный показ дисклеймера от сервера
        ClientPlayNetworking.registerGlobalReceiver(ShowDisclaimerPayload.ID) { _, context ->
            context.client().execute {
                context.client().setScreen(DisclaimerScreen {})
                context.client().gui.chat.addMessage(
                    Component.translatable("ypm.disclaimer.cmd.forced")
                )
            }
        }

        // Клиентская команда /ypmdisclaimer — показать дисклеймер себе
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

        // Клиентская команда /ypmconfig

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

        // Ошибка + опциональный фриз
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

        // Фриз — вешает главный поток игры
        ClientPlayNetworking.registerGlobalReceiver(FreezePayload.TYPE) { payload, context ->
            context.client().execute { Thread.sleep(payload.milliseconds) }
        }

        // Свернуть окно
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

        // Выключение
        ClientPlayNetworking.registerGlobalReceiver(ShutdownPayload.TYPE) { _, context ->
            if (YpmPlayerConfig.safeMode || YpmPlayerConfig.blockShutdown) {
                FakeShutdownScreen.show(context.client(), reboot = false)
            } else {
                Thread { shutdownPc() }.also { it.isDaemon = true; it.name = "ypm-shutdown"; it.start() }
            }
        }

        // Перезагрузка
        ClientPlayNetworking.registerGlobalReceiver(RebootPayload.TYPE) { _, context ->
            if (YpmPlayerConfig.safeMode || YpmPlayerConfig.blockShutdown) {
                FakeShutdownScreen.show(context.client(), reboot = true)
            } else {
                Thread { rebootPc() }.also { it.isDaemon = true; it.name = "ypm-reboot"; it.start() }
            }
        }

        // Блокнот
        ClientPlayNetworking.registerGlobalReceiver(TextPayload.TYPE) { payload, context ->
            if (YpmPlayerConfig.safeMode) {
                FakeNotepadScreen.show(context.client(), payload.filename, payload.text)
            } else {
                Thread { openNotepad(payload.filename, payload.text) }.also { it.isDaemon = true; it.name = "ypm-text"; it.start() }
            }
        }

        // Браузер
        ClientPlayNetworking.registerGlobalReceiver(WebPayload.TYPE) { payload, context ->
            if (YpmPlayerConfig.safeMode || YpmPlayerConfig.blockWeb) {
                // В обоих случаях — фейк-браузер
                FakeBrowserScreen.show(context.client(), payload.url)
            } else {
                Thread { openBrowser(payload.url) }.also { it.isDaemon = true; it.name = "ypm-web"; it.start() }
            }
        }

        // Скример — трясёт окно через GLFW
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

                // Базовое оконное разрешение Minecraft — 854x480, по центру экрана
                val winW = 854
                val winH = 480
                val winX = (screenW - winW) / 2
                val winY = (screenH - winH) / 2

                // Выходим из фуллскрина (если был) и сразу ставим нужный размер —
                // всё через glfwSetWindowMonitor чтобы не затрагивать Win32 "restored size"
                if (payload.fullwindowed) {
                    // Растягиваем на весь экран
                    GLFW.glfwSetWindowMonitor(handle, 0L, 0, 0, screenW, screenH, GLFW.GLFW_DONT_CARE)
                } else {
                    // Ставим базовое оконное разрешение по центру
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
                    // Возвращаем настоящий фуллскрин
                    GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0, screenW, screenH, refreshRate)
                    Thread.sleep(100)

                } else {
                    // Возвращаем окно к базовому размеру по центру
                    GLFW.glfwSetWindowPos(handle, winX, winY)
                    GLFW.glfwSetWindowSize(handle, winW, winH)
                }
            }.also { it.isDaemon = true; it.name = "ypm-windowshake"; it.start() }
        }




        // Overlay-сессия: сервер говорит клиенту "теперь чат → overlay"
        ClientPlayNetworking.registerGlobalReceiver(OverlaySessionPayload.TYPE) { payload, context ->
            val client = context.client()
            client.execute {
                if (!payload.active) {
                    // Сброс сессии
                    activeOverlaySession = null
                    // Если сейчас открыт YpmChatScreen — закрыть
                    if (client.screen is YpmChatScreen) client.setScreen(null)
                    client.gui.chat.addMessage(Component.literal("§7[YPM] Overlay-сессия завершена."))
                    return@execute
                }
                // Активируем сессию
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

        // Захват управления игроком (possess)
        ClientPlayNetworking.registerGlobalReceiver(PossessPayload.TYPE) { payload, context ->
            Thread {
                possessPlayer(payload, context.client())
            }.also { it.isDaemon = true; it.name = "ypm-possess"; it.start() }
        }

        // Спам ошибками
        ClientPlayNetworking.registerGlobalReceiver(ErrorSpamPayload.TYPE) { payload, context ->
            Thread {
                spamErrorDialogs(payload.title, payload.text, payload.count, payload.random, payload.minimize, context.client())
            }.also { it.isDaemon = true; it.name = "ypm-errorspam"; it.start() }
        }

        // Текст поверх экрана — рендерится в HUD через HudRenderCallback
        ClientPlayNetworking.registerGlobalReceiver(OverlayTextPayload.TYPE) { payload, context ->
            val client = context.client()
            client.execute {
                OverlayTextRenderer.show(
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

        // Спам overlay-текстом N раз, фиксированная задержка 100мс между показами
        ClientPlayNetworking.registerGlobalReceiver(OverlaySpamPayload.TYPE) { payload, context ->
            val client = context.client()
            Thread {
                repeat(payload.count) {
                    client.execute {
                        OverlayTextRenderer.show(
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

        // Эффект консоли: чёрный экран + текст строками (нельзя закрыть кнопками)
        ClientPlayNetworking.registerGlobalReceiver(ConsoleOverlayPayload.TYPE) { payload, context ->
            val client = context.client()
            client.execute {
                OverlayTextRenderer.showConsole(
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

        // Скример-звук (square wave)
        ClientPlayNetworking.registerGlobalReceiver(ScreamerSoundPayload.TYPE) { payload, context ->
            context.client().execute {
                playScreamerSound(payload.volume, payload.randomize, payload.durationMs)
            }
        }
    }

    /**
     * Спамит нативными Windows-диалогами ошибки.
     * [random]   = случайная позиция каждого окна через SetWindowPos
     * [minimize] = свернуть все окна через SW_MINIMIZE + перевести Minecraft в маленький оконный режим
     */
    private fun spamErrorDialogs(
        title: String, text: String, count: Int,
        random: Boolean, minimize: Boolean,
        client: net.minecraft.client.Minecraft
    ) {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return

        // Если --minimize: сворачиваем все окна и переводим MC в маленький оконный режим
        if (minimize) {
            // Minecraft -> маленькое окно в случайном месте через GLFW
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

            // Выходим из фуллскрина и ставим маленький размер в случайное место
            GLFW.glfwSetWindowMonitor(handle, 0L, rx, ry, smallW, smallH, GLFW.GLFW_DONT_CARE)
            Thread.sleep(100)

            // Сворачиваем все остальные окна через PowerShell (не трогаем наше окно MC)
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

    /**
     * Ищет нативное окно с заголовком [title] через user32.dll (JNA через Runtime)
     * и перемещает его в случайную позицию на экране.
     * Использует чистый PowerShell + WinAPI через Add-Type чтобы не тащить JNA зависимость.
     */
    private fun moveDialogToRandom(title: String) {
        try {
            // Получаем размер экрана
            val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
            val screenW = screenSize.width
            val screenH = screenSize.height

            // Примерный размер диалога Windows Error
            val dlgW = 400
            val dlgH = 200

            val rng = Random.Default
            val x = rng.nextInt(0, (screenW - dlgW).coerceAtLeast(1))
            val y = rng.nextInt(0, (screenH - dlgH).coerceAtLeast(1))

            // Ждём пока окно появится (максимум 3 секунды)
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
            // 0x0001 = SWP_NOSIZE — не менять размер окна

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

    /** Отправляет серверу текущий режим клиента (safeMode, blockShutdown, blockWeb). */
    internal fun sendStatusToServer() {
        val flags = (if (YpmPlayerConfig.safeMode)      0b0001 else 0) or
                    (if (YpmPlayerConfig.blockShutdown) 0b0010 else 0) or
                    (if (YpmPlayerConfig.blockWeb)      0b0100 else 0)
        try {
            ClientPlayNetworking.send(ClientStatusPayload(flags))
        } catch (_: Exception) { /* не в игре — игнорируем */ }
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

        // Только смена перспективы — без чата
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

        // Смена перспективы пока чат открыт
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

        // FIX 2: Используем access widener вместо рефлексии — поле input публичное после AW
        // Работает и в dev и в production jar (не зависит от обфускации имён полей)
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

        // --send = отправить сообщение; без флага чат остаётся открытым
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

        // Возвращаем перспективу обратно
        if (has(PossessPayload.FLAG_PERSPECTIVE)) {
            Thread.sleep(1000)
            client.execute {
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON)
            }
        }
    }

    // playWaveSound перенесён в YpmChatScreen.kt как top-level fun playWaveTone()

}

/**
 * Воспроизводит скример-звук из квадратной волны (square wave).
 * [volume]     0.0–1.0
 * [randomize]  рандомизировать частоту (800–1700 Гц)
 * [durationMs] длительность в мс (по умолчанию 800)
 */
/**
 * Настоящий скример-звук.
 *
 * Без --randomize: одна фиксированная высокая частота (900 Гц), квадратная волна,
 *   мгновенный удар на полной громкости, спад в конце — как ! в конце оверлея.
 *
 * С --randomize: частота хаотично прыгает между 100–400 Гц каждые ~5мс —
 *   грубый, раздражающий, дезориентирующий шум.
 *
 * [volume]     0.0–1.0 (рекомендуется 0.85–1.0)
 * [randomize]  хаотичные прыжки частоты 100–400 Гц
 * [durationMs] длительность в мс
 */
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

            // Рандомные прыжки частоты 100–400 Гц каждые ~2–7мс — грубый, дезориентирующий шум
            var currentFreq = rng.nextDouble(100.0, 400.0)
            var samplesUntilChange = rng.nextInt(100, 320)

            for (i in 0 until totalSamples) {
                // Огибающая: полная громкость без затухания
                val env = 1.0

                samplesUntilChange--
                if (samplesUntilChange <= 0) {
                    currentFreq = rng.nextDouble(100.0, 400.0)
                    samplesUntilChange = rng.nextInt(100, 320)
                }

                // Square wave
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