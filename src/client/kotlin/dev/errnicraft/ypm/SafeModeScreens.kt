package dev.errnicraft.ypm

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue

// ─────────────────────────────────────────────────────────────────────────────
//  Очередь Safe Mode окон
//  push() → ставит в очередь. После закрытия → следующий автоматически.
// ─────────────────────────────────────────────────────────────────────────────
object SafeModeQueue {
    private val queue: ConcurrentLinkedQueue<() -> Screen> = ConcurrentLinkedQueue()
    @Volatile private var busy = false

    fun push(client: Minecraft, factory: () -> Screen) {
        queue.add(factory)
        if (!busy) showNext(client)
    }

    fun onClose(client: Minecraft) {
        busy = false
        showNext(client)
    }

    private fun showNext(client: Minecraft) {
        val next = queue.poll() ?: run { busy = false; return }
        busy = true
        client.execute { client.setScreen(next()) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Утилиты рендеринга
// ─────────────────────────────────────────────────────────────────────────────

/** Разбивает текст сначала по \n, затем по ширине шрифта. */
private fun wrapWords(font: net.minecraft.client.gui.Font, text: String, maxW: Int): List<String> {
    val out = mutableListOf<String>()
    for (paragraph in text.split("\n")) {
        if (paragraph.isEmpty()) { out += ""; continue }
        var line = StringBuilder()
        for (word in paragraph.split(" ")) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (font.width(test) <= maxW) {
                line = StringBuilder(test)
            } else {
                if (line.isNotEmpty()) out += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) out += line.toString()
    }
    return out.ifEmpty { listOf("") }
}

private fun GuiGraphics.drawPanel(x1: Int, y1: Int, x2: Int, y2: Int, bg: Int, border: Int) {
    fill(x1 + 1, y1, x2 - 1, y2, bg)
    fill(x1, y1 + 1, x2, y2 - 1, bg)
    fill(x1 + 1, y1, x2 - 1, y1 + 1, border)
    fill(x1 + 1, y2 - 1, x2 - 1, y2, border)
    fill(x1, y1 + 1, x1 + 1, y2 - 1, border)
    fill(x2 - 1, y1 + 1, x2, y2 - 1, border)
}

private fun GuiGraphics.drawTitleBar(x1: Int, y1: Int, x2: Int, h: Int, colorL: Int, colorR: Int) {
    fillGradient(x1, y1, x2, y1 + h, colorL, colorR)
}

// ─────────────────────────────────────────────────────────────────────────────
//  [1] Диалог ошибки — адаптивный
// ─────────────────────────────────────────────────────────────────────────────
class FakeErrorDialogScreen(
    private val title: String,
    private val text: String
) : Screen(Component.literal(title)) {

    companion object {
        fun show(client: Minecraft, title: String, text: String) {
            SafeModeQueue.push(client) { FakeErrorDialogScreen(title, text) }
        }
    }

    private val PAD        = 12
    private val TITLEBAR_H = 20
    private val LINE_H     = 11
    private val ICON_W     = 26
    private val MIN_W      = 200
    private val MAX_W      = 380

    private fun computeWidth(): Int {
        val longestLine = text.split("\n").maxOfOrNull { font.width(it) } ?: 0
        return (longestLine + PAD * 2 + ICON_W + 20).coerceIn(MIN_W, MAX_W)
    }

    private fun textLines() = wrapWords(font, text, computeWidth() - PAD * 2 - ICON_W)

    private fun dialogH() = TITLEBAR_H + PAD + textLines().size * LINE_H + PAD + 1 + PAD + 20 + PAD

    private lateinit var okBtn: Button

    override fun init() {
        val cx = width / 2; val cy = height / 2
        val w = computeWidth(); val h = dialogH()
        val y1 = cy - h / 2
        okBtn = Button.builder(Component.literal("  OK  ")) { onClose() }
            .pos(cx - 30, y1 + h - PAD - 20).size(60, 20).build()
        addRenderableWidget(okBtn)
    }

    override fun renderBackground(g: GuiGraphics, mx: Int, my: Int, pt: Float) {
        val cx = width / 2; val cy = height / 2
        val w = computeWidth(); val h = dialogH()
        val x1 = cx - w / 2; val y1 = cy - h / 2
        val x2 = cx + w / 2; val y2 = cy + h / 2

        g.fill(0, 0, width, height, 0xAA000000.toInt())
        g.fill(x1 + 3, y1 + 3, x2 + 3, y2 + 3, 0x55000000.toInt())
        g.drawPanel(x1, y1, x2, y2, 0xFFF0F0F0.toInt(), 0xFF777777.toInt())

        g.drawTitleBar(x1 + 1, y1 + 1, x2 - 1, TITLEBAR_H, 0xFF1060C0.toInt(), 0xFF0040A0.toInt())
        g.drawString(font, "§f§l[!]", x1 + 5, y1 + 6, 0xFFFFFFFF.toInt(), false)
        g.drawString(font, "§f§l$title", x1 + 26, y1 + 6, 0xFFFFFFFF.toInt(), false)
        g.fill(x2 - 18, y1 + 3, x2 - 3, y1 + TITLEBAR_H - 3, 0xFFCC2222.toInt())
        g.drawCenteredString(font, "§f§lX", x2 - 11, y1 + 7, 0xFFFFFFFF.toInt())

        val iconX = x1 + PAD + 8; val iconY = y1 + TITLEBAR_H + PAD + 4
        g.fill(iconX - 8, iconY - 8, iconX + 8, iconY + 8, 0xFFBB1111.toInt())
        g.fill(iconX - 5, iconY - 5, iconX + 5, iconY + 5, 0xFFCC3333.toInt())
        g.drawCenteredString(font, "§f§lX", iconX, iconY - 4, 0xFFFFFFFF.toInt())

        var ty = y1 + TITLEBAR_H + PAD
        for (line in textLines()) {
            g.drawString(font, "§0$line", x1 + PAD + ICON_W, ty, 0xFF111111.toInt(), false)
            ty += LINE_H
        }

        val sepY = y2 - PAD - 20 - PAD / 2
        g.fill(x1 + 6, sepY, x2 - 6, sepY + 1, 0xFFBBBBBB.toInt())
    }

    override fun render(g: GuiGraphics, mx: Int, my: Int, dt: Float) { super.render(g, mx, my, dt) }

    override fun onClose() {
        super.onClose()
        minecraft?.let { SafeModeQueue.onClose(it) }
    }

    override fun isPauseScreen()    = false
    override fun shouldCloseOnEsc() = true
}

// ─────────────────────────────────────────────────────────────────────────────
//  [2] Блокнот
// ─────────────────────────────────────────────────────────────────────────────
class FakeNotepadScreen(
    private val filename: String,
    private val content: String
) : Screen(Component.literal("Notepad — $filename")) {

    companion object {
        fun show(client: Minecraft, filename: String, content: String) {
            SafeModeQueue.push(client) { FakeNotepadScreen(filename, content) }
        }
    }

    private var scrollOffset = 0
    private lateinit var closeBtn: Button

    private val PW get() = (width  * 0.80).toInt().coerceAtLeast(300)
    private val PH get() = (height * 0.78).toInt().coerceAtLeast(200)
    private val PX get() = (width  - PW) / 2
    private val PY get() = (height - PH) / 2

    private val TITLEBAR_H = 18
    private val MENUBAR_H  = 14
    private val PAD        = 6

    private fun buildLines(): List<String> {
        val maxW = PW - PAD * 2
        return content.split("\n").flatMap { para ->
            if (para.isEmpty()) listOf("") else wrapWords(font, para, maxW)
        }
    }
    private val lines by lazy { buildLines() }

    override fun init() {
        closeBtn = Button.builder(Component.literal("§cX")) { onClose() }
            .pos(PX + PW - 20, PY + 2).size(16, 14).build()
        addRenderableWidget(closeBtn)
    }

    override fun mouseScrolled(mx: Double, my: Double, dx: Double, dy: Double): Boolean {
        scrollOffset = (scrollOffset - dy.toInt()).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        return true
    }

    override fun renderBackground(g: GuiGraphics, mx: Int, my: Int, pt: Float) {
        val px = PX; val py = PY; val pw = PW; val ph = PH
        val x2 = px + pw; val y2 = py + ph

        g.fill(0, 0, width, height, 0xAA000000.toInt())
        g.fill(px + 3, py + 3, x2 + 3, y2 + 3, 0x55000000.toInt())
        g.drawPanel(px, py, x2, y2, 0xFFF5F5F5.toInt(), 0xFF777777.toInt())
        g.drawTitleBar(px + 1, py + 1, x2 - 1, TITLEBAR_H, 0xFF404040.toInt(), 0xFF303030.toInt())
        g.drawString(font, "§e§l[N]", px + 4, py + 5, 0xFFFFFFFF.toInt(), false)
        val titleTxt = if (font.width("Notepad — $filename") < pw - 60)
            "Notepad — $filename" else "Notepad — ${filename.take(20)}..."
        g.drawString(font, "§f$titleTxt", px + 26, py + 5, 0xFFFFFFFF.toInt(), false)

        val mby = py + TITLEBAR_H
        g.fill(px + 1, mby, x2 - 1, mby + MENUBAR_H, 0xFFE8E8E8.toInt())
        g.fill(px + 1, mby + MENUBAR_H - 1, x2 - 1, mby + MENUBAR_H, 0xFFCCCCCC.toInt())
        for ((i, item) in listOf("File", "Edit", "Format", "View").withIndex())
            g.drawString(font, "§0$item", px + 6 + i * 40, mby + 3, 0xFF222222.toInt(), false)

        val textY = mby + MENUBAR_H
        g.fill(px + 1, textY, x2 - 1, y2 - 1, 0xFFFFFFFF.toInt())
        g.fill(px + 1, textY, px + 2, y2 - 1, 0xFFDDDDDD.toInt())
        g.fill(x2 - 2, textY, x2 - 1, y2 - 1, 0xFFDDDDDD.toInt())

        val visLines = (ph - textY + py - 4) / 10
        for (i in 0 until visLines) {
            val lineIdx = scrollOffset + i
            if (lineIdx >= lines.size) break
            g.drawString(font, "§0${lines[lineIdx]}", px + PAD + 2, textY + 4 + i * 10, 0xFF111111.toInt(), false)
        }

        val statusY = y2 - 12
        g.fill(px + 1, statusY, x2 - 1, y2 - 1, 0xFFE8E8E8.toInt())
        g.fill(px + 1, statusY, x2 - 1, statusY + 1, 0xFFCCCCCC.toInt())
        g.drawString(font, "§7Ln ${scrollOffset + 1}, Col 1    [Safe Mode]", px + 6, statusY + 2, 0xFF555555.toInt(), false)
    }

    override fun render(g: GuiGraphics, mx: Int, my: Int, dt: Float) { super.render(g, mx, my, dt) }

    override fun onClose() {
        super.onClose()
        minecraft?.let { SafeModeQueue.onClose(it) }
    }

    override fun isPauseScreen()    = false
    override fun shouldCloseOnEsc() = true
}

// ─────────────────────────────────────────────────────────────────────────────
//  [3] Браузер
// ─────────────────────────────────────────────────────────────────────────────
class FakeBrowserScreen(
    private val url: String
) : Screen(Component.literal("Browser")) {

    companion object {
        fun show(client: Minecraft, url: String) {
            SafeModeQueue.push(client) { FakeBrowserScreen(url) }
        }
    }

    private lateinit var closeBtn: Button
    private lateinit var openBtn:  Button

    private val PW get() = (width  * 0.80).toInt().coerceAtLeast(300)
    private val PH get() = (height * 0.78).toInt().coerceAtLeast(200)
    private val PX get() = (width  - PW) / 2
    private val PY get() = (height - PH) / 2

    private val TITLEBAR_H = 18
    private val CHROME_H   = 22

    override fun init() {
        val px = PX; val py = PY; val pw = PW; val ph = PH
        closeBtn = Button.builder(Component.literal("§cX")) { onClose() }
            .pos(px + pw - 20, py + 2).size(16, 14).build()
        addRenderableWidget(closeBtn)
        openBtn = Button.builder(Component.translatable("ypm.safemode.browser.open")) {
            tryOpenUrl(url); onClose()
        }.pos(px + pw / 2 - 70, py + ph - 50).size(140, 20).build()
        addRenderableWidget(openBtn)
    }

    private fun tryOpenUrl(u: String) {
        try {
            val os = System.getProperty("os.name", "").lowercase()
            when {
                os.contains("win") -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", u)
                    .redirectErrorStream(true).start()
                os.contains("mac") -> ProcessBuilder("open", u).redirectErrorStream(true).start()
                else -> ProcessBuilder("xdg-open", u).redirectErrorStream(true).start()
            }
        } catch (_: Exception) {
            try {
                java.awt.Desktop.getDesktop().browse(URI(u))
            } catch (_: Exception) {}
        }
    }

    override fun renderBackground(g: GuiGraphics, mx: Int, my: Int, pt: Float) {
        val px = PX; val py = PY; val pw = PW; val ph = PH
        val x2 = px + pw; val y2 = py + ph

        g.fill(0, 0, width, height, 0xAA000000.toInt())
        g.fill(px + 3, py + 3, x2 + 3, y2 + 3, 0x55000000.toInt())
        g.drawPanel(px, py, x2, y2, 0xFFF5F5F5.toInt(), 0xFF666666.toInt())
        g.drawTitleBar(px + 1, py + 1, x2 - 1, TITLEBAR_H, 0xFF383838.toInt(), 0xFF303030.toInt())
        val tabW = (pw / 3).coerceAtMost(160)
        g.fill(px + 4, py + 3, px + 4 + tabW, py + TITLEBAR_H, 0xFFF5F5F5.toInt())
        g.fill(px + 4, py + 3, px + 4 + tabW, py + 4, 0xFF4A90D9.toInt())
        val shortUrl = if (url.length > 22) url.take(22) + "..." else url
        g.drawString(font, "§0[W] $shortUrl", px + 8, py + 6, 0xFF222222.toInt(), false)

        val cby = py + TITLEBAR_H
        g.fill(px + 1, cby, x2 - 1, cby + CHROME_H, 0xFFDEDEDE.toInt())
        g.fill(px + 1, cby + CHROME_H - 1, x2 - 1, cby + CHROME_H, 0xFFBBBBBB.toInt())
        g.drawString(font, "§7< > @", px + 5, cby + 7, 0xFF666666.toInt(), false)
        val urlBarX1 = px + 42; val urlBarX2 = x2 - 10
        g.fill(urlBarX1, cby + 3, urlBarX2, cby + CHROME_H - 3, 0xFFFFFFFF.toInt())
        g.fill(urlBarX1, cby + 3, urlBarX2, cby + 4, 0xFFAAAAAA.toInt())
        g.fill(urlBarX1, cby + 3, urlBarX1 + 1, cby + CHROME_H - 3, 0xFFAAAAAA.toInt())
        val displayUrl = if (font.width(url) < urlBarX2 - urlBarX1 - 8) url else url.take(40) + "..."
        g.drawString(font, "§9$displayUrl", urlBarX1 + 4, cby + 7, 0xFF2255CC.toInt(), false)

        val pageY = cby + CHROME_H
        g.fill(px + 1, pageY, x2 - 1, y2 - 1, 0xFFFFFFFF.toInt())
        val bannerY = pageY + 16
        g.fill(px + 10, bannerY, x2 - 10, bannerY + 32, 0xFFFFF3CD.toInt())
        g.fill(px + 10, bannerY, x2 - 10, bannerY + 1, 0xFFE6AC00.toInt())
        g.fill(px + 10, bannerY + 31, x2 - 10, bannerY + 32, 0xFFE6AC00.toInt())
        g.drawCenteredString(font, "§6§l[!] Safe Mode — браузер перехвачен YPM", px + pw / 2, bannerY + 5, 0xFF886600.toInt())
        g.drawCenteredString(font, "§7Оператор пытался открыть эту страницу.", px + pw / 2, bannerY + 17, 0xFF666666.toInt())
        g.drawCenteredString(font, "§9§n$displayUrl", px + pw / 2, pageY + 58, 0xFF2244AA.toInt())
        g.drawCenteredString(font, "§7Нажмите кнопку ниже, чтобы открыть самостоятельно.", px + pw / 2, pageY + 74, 0xFF888888.toInt())
    }

    override fun render(g: GuiGraphics, mx: Int, my: Int, dt: Float) { super.render(g, mx, my, dt) }

    override fun onClose() {
        super.onClose()
        minecraft?.let { SafeModeQueue.onClose(it) }
    }

    override fun isPauseScreen()    = false
    override fun shouldCloseOnEsc() = true
}

// ─────────────────────────────────────────────────────────────────────────────
//  [4] Выключение / Перезагрузка
// ─────────────────────────────────────────────────────────────────────────────
class FakeShutdownScreen(
    private val reboot: Boolean
) : Screen(Component.literal(if (reboot) "Reboot" else "Shutdown")) {

    companion object {
        fun show(client: Minecraft, reboot: Boolean) {
            SafeModeQueue.push(client) { FakeShutdownScreen(reboot) }
        }
    }

    private var timer = 0
    private lateinit var cancelBtn: Button

    override fun init() {
        cancelBtn = Button.builder(Component.translatable("ypm.safemode.shutdown.cancel")) { onClose() }
            .pos(width / 2 - 75, height / 2 + 42).size(150, 20).build()
        addRenderableWidget(cancelBtn)
    }

    override fun tick() { timer++ }

    override fun renderBackground(g: GuiGraphics, mx: Int, my: Int, pt: Float) {
        g.fillGradient(0, 0, width, height, 0xFF000828.toInt(), 0xFF000010.toInt())
        val cx = width / 2; val cy = height / 2
        val lx = cx - 12; val ly = cy - 60
        g.fill(lx,      ly,      lx + 10, ly + 10, 0xFFF25022.toInt())
        g.fill(lx + 12, ly,      lx + 22, ly + 10, 0xFF7FBA00.toInt())
        g.fill(lx,      ly + 12, lx + 10, ly + 22, 0xFF00A4EF.toInt())
        g.fill(lx + 12, ly + 12, lx + 22, ly + 22, 0xFFFFB900.toInt())
        val label = if (reboot) "Перезагрузка" else "Выключение"
        val dots  = ".".repeat((timer / 8) % 4)
        g.drawCenteredString(font, "§f§l$label$dots", cx, cy - 30, 0xFFFFFFFF.toInt())
        val sub = if (reboot) "Подождите, компьютер перезагружается..."
                  else        "Подождите, компьютер выключается..."
        g.drawCenteredString(font, "§7$sub", cx, cy - 16, 0xFFAAAAAA.toInt())
        g.fill(cx - 130, cy + 4, cx + 130, cy + 26, 0x99CC2200.toInt())
        g.fill(cx - 130, cy + 4, cx + 130, cy + 5, 0xFFFF4400.toInt())
        g.fill(cx - 130, cy + 25, cx + 130, cy + 26, 0xFFFF4400.toInt())
        g.drawCenteredString(font, "§c§l[SAFE MODE] ПК не затронут!", cx, cy + 10, 0xFFFF8866.toInt())
    }

    override fun render(g: GuiGraphics, mx: Int, my: Int, dt: Float) { super.render(g, mx, my, dt) }

    override fun onClose() {
        super.onClose()
        minecraft?.let { SafeModeQueue.onClose(it) }
    }

    override fun isPauseScreen()    = false
    override fun shouldCloseOnEsc() = true
}
