package dev.errnicraft.ypm
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.resources.Identifier
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
@Environment(EnvType.CLIENT)
class OverlayTextRenderer {
    private val entries = mutableListOf<OverlayEntry>()
    private val lock = Any()
    private var registered = false
    private val YPM_FONT = FontDescription.Resource(Identifier.fromNamespaceAndPath("ypm", "ypm_sans"))
    @Volatile private var consoleActive = false
    private val consoleLines   = mutableListOf<String>()
    private val consoleVisible = mutableListOf<String>()
    private val consoleLock    = Any()
    @Volatile private var consoleColor   = 0xFF00FF00.toInt()
    @Volatile private var consoleExpire  = 0L
    @Volatile private var consoleMcText  = false
    @Volatile private var consoleTyperThread: Thread? = null
    @Volatile var colorBarsActive = false
    @Volatile var colorBarsExpire = 0L
    @Volatile var colorBarsType   = "smpte"
    @Volatile var colorBarsLabel  = ""
    @Volatile var colorBarsCorner = ""
    private val CODE_COLORS = mapOf(
        '0' to 0xFF000000.toInt(),
        '1' to 0xFF0000AA.toInt(),
        '2' to 0xFF00AA00.toInt(),
        '3' to 0xFF00AAAA.toInt(),
        '4' to 0xFFAA0000.toInt(),
        '5' to 0xFFAA00AA.toInt(),
        '6' to 0xFFFFAA00.toInt(),
        '7' to 0xFFAAAAAA.toInt(),
        '8' to 0xFF555555.toInt(),
        '9' to 0xFF5555FF.toInt(),
        'a' to 0xFF55FF55.toInt(),
        'b' to 0xFF55FFFF.toInt(),
        'c' to 0xFFFF5555.toInt(),
        'd' to 0xFFFF55FF.toInt(),
        'e' to 0xFFFFFF55.toInt(),
        'f' to 0xFFFFFFFF.toInt(),
    )
    private val FORMAT_CODES = setOf('l', 'o', 'n', 'm', 'k', 'r')
    data class OverlayEntry(
        val lines: List<Component>,
        val argb: Int,
        val size: Int,
        val scaleX: Float,
        val scaleY: Float,
        val x: Int,
        val y: Int,
        val expireAt: Long,
        val useMcFont: Boolean,
    )
    fun register() {
        if (registered) return
        registered = true
        HudRenderCallback.EVENT.register { guiGraphics, _ ->
            render(guiGraphics)
        }
    }
    fun extractColorCode(text: String): Int? {
        val i = text.indexOfFirst { it == '&' || it == '§' }
        if (i < 0 || i + 1 >= text.length) return null
        return CODE_COLORS[text[i + 1].lowercaseChar()]
    }
    fun stripCodes(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if ((text[i] == '&' || text[i] == '§') && i + 1 < text.length) {
                val c = text[i + 1].lowercaseChar()
                if (CODE_COLORS.containsKey(c) || FORMAT_CODES.contains(c)) {
                    i += 2; continue
                }
            }
            sb.append(text[i]); i++
        }
        return sb.toString()
    }
    private fun buildLineComponent(rawLine: String, useMcFont: Boolean, baseArgb: Int? = null): Component {
        val converted = buildString {
            var i = 0
            while (i < rawLine.length) {
                if (rawLine[i] == '&' && i + 1 < rawLine.length) {
                    val c = rawLine[i + 1].lowercaseChar()
                    if (CODE_COLORS.containsKey(c) || FORMAT_CODES.contains(c)) {
                        append('§'); append(rawLine[i + 1]); i += 2; continue
                    }
                }
                append(rawLine[i]); i++
            }
        }
        val comp = Component.literal(converted)
        val hasOwnColor = converted.length >= 2 && converted[0] == '§'
                && (CODE_COLORS.containsKey(converted[1].lowercaseChar()))
        val styled = if (!hasOwnColor && baseArgb != null) {
            comp.withStyle { it.withColor(baseArgb and 0xFFFFFF) }
        } else comp
        return if (useMcFont) styled
        else styled.withStyle { it.withFont(YPM_FONT) }
    }
    fun show(
        text: String,
        color: String,
        size: Int,
        scaleX: Float,
        scaleY: Float,
        durationMs: Long,
        random: Boolean,
        randomScale: Boolean,
        useMcFont: Boolean,
        client: Minecraft,
    ) {
        val baseArgb = parseColor(color)
        val rawLines = text.split("\n")
        val font = client.font
        val scrW = client.window.guiScaledWidth
        val scrH = client.window.guiScaledHeight
        val baseScale = sizeToScale(size)
        val rng = Random.Default
        val finalScaleX: Float
        val finalScaleY: Float
        if (randomScale) {
            val squeezed  = rng.nextFloat() * 0.45f + 0.4f
            val stretched = rng.nextFloat() * 1.2f  + 1.2f
            if (rng.nextBoolean()) { finalScaleX = squeezed;  finalScaleY = stretched }
            else                   { finalScaleX = stretched; finalScaleY = squeezed  }
        } else {
            finalScaleX = scaleX
            finalScaleY = scaleY
        }
        val lines = rawLines.map { buildLineComponent(it, useMcFont, baseArgb) }
        val rawLineW = (lines.maxOfOrNull { font.width(it) } ?: 50)
        val rawH     = 10 * lines.size
        val maxAllowedScaleX = if (rawLineW > 0) (scrW.toFloat() / (rawLineW * baseScale)).coerceAtMost(finalScaleX) else finalScaleX
        val maxAllowedScaleY = if (rawH > 0)     (scrH.toFloat() / (rawH     * baseScale)).coerceAtMost(finalScaleY) else finalScaleY
        val csx = maxAllowedScaleX.coerceAtLeast(0.1f)
        val csy = maxAllowedScaleY.coerceAtLeast(0.1f)
        val maxLineW = (rawLineW * baseScale * csx).toInt().coerceAtLeast(1)
        val totalH   = (rawH     * baseScale * csy).toInt().coerceAtLeast(1)
        val x: Int
        val y: Int
        if (random) {
            x = rng.nextInt(0, (scrW - maxLineW).coerceAtLeast(1))
            y = rng.nextInt(0, (scrH - totalH).coerceAtLeast(1))
        } else {
            x = (scrW / 2 - maxLineW / 2).coerceIn(0, (scrW - maxLineW).coerceAtLeast(0))
            y = (scrH / 2 - totalH   / 2).coerceIn(0, (scrH - totalH  ).coerceAtLeast(0))
        }
        synchronized(lock) {
            entries.add(OverlayEntry(lines, baseArgb, size, csx, csy, x, y,
                System.currentTimeMillis() + durationMs, useMcFont))
        }
    }
    fun showConsole(
        text: String,
        color: String,
        durationMs: Long,
        mcText: Boolean = false,
    ) {
        val argb  = parseColor(color)
        val lines = text.split("|").map { it.trim() }
        consoleTyperThread?.interrupt()
        synchronized(consoleLock) {
            consoleLines.clear()
            consoleLines.addAll(lines)
            consoleVisible.clear()
            consoleColor  = argb
            consoleExpire = if (durationMs > 0) System.currentTimeMillis() + durationMs else 0L
            consoleMcText = mcText
            consoleActive = true
        }
        val t = Thread {
            try {
                while (true) {
                    val snapLines: List<String>
                    val expire: Long
                    synchronized(consoleLock) {
                        if (!consoleActive) return@Thread
                        snapLines = consoleLines.toList()
                        expire    = consoleExpire
                    }
                    if (expire > 0 && System.currentTimeMillis() >= expire) {
                        Thread.sleep(500L)
                        synchronized(consoleLock) { consoleActive = false }
                        return@Thread
                    }
                    for (line in snapLines) {
                        if (Thread.interrupted()) return@Thread
                        val nowExpire: Long
                        synchronized(consoleLock) {
                            if (!consoleActive) return@Thread
                            consoleVisible.add(line)
                            if (consoleVisible.size > 200) consoleVisible.removeAt(0)
                            nowExpire = consoleExpire
                        }
                        if (nowExpire > 0 && System.currentTimeMillis() >= nowExpire) {
                            Thread.sleep(500L)
                            synchronized(consoleLock) { consoleActive = false }
                            return@Thread
                        }
                        Thread.sleep(200L)
                    }
                }
            } catch (_: InterruptedException) { }
        }
        t.isDaemon = true
        t.name = "ypm-console-typer"
        consoleTyperThread = t
        t.start()
    }
    fun clearConsole() {
        consoleTyperThread?.interrupt()
        consoleTyperThread = null
        synchronized(consoleLock) {
            consoleActive = false
            consoleLines.clear()
            consoleVisible.clear()
        }
    }
    private fun render(guiGraphics: GuiGraphics) {
        val now = System.currentTimeMillis()
        if (colorBarsActive) {
            if (colorBarsExpire > 0 && now >= colorBarsExpire) {
                colorBarsActive = false
            } else {
                when (colorBarsType) {
                    "hd"     -> renderColorBarsHd(guiGraphics)
                    "ebu"    -> renderColorBarsEbu(guiGraphics)
                    "pluge"  -> renderColorBarsPluge(guiGraphics)
                    "mono"   -> renderColorBarsMono(guiGraphics)
                    "rgb"    -> renderColorBarsRgb(guiGraphics)
                    else     -> renderColorBars(guiGraphics)
                }
                if (colorBarsLabel.isNotEmpty()) {
                    renderColorBarsLabel(guiGraphics, colorBarsLabel, colorBarsCorner)
                }
                return
            }
        }
        synchronized(consoleLock) {
            if (consoleActive) {
                if (consoleExpire > 0 && now >= consoleExpire) {
                    consoleActive = false
                } else {
                    renderConsole(guiGraphics, now)
                }
            }
        }
        val active: List<OverlayEntry>
        synchronized(lock) {
            entries.removeAll { it.expireAt <= now }
            active = entries.toList()
        }
        if (active.isEmpty()) return
        val client = Minecraft.getInstance()
        for (entry in active) {
            val baseScale = sizeToScale(entry.size)
            val pose = guiGraphics.pose()
            pose.pushMatrix()
            pose.translate(entry.x.toFloat(), entry.y.toFloat())
            pose.scale(baseScale * entry.scaleX, baseScale * entry.scaleY)
            entry.lines.forEachIndexed { i, line ->
                guiGraphics.drawString(client.font, line, 0, i * 10, entry.argb, true)
            }
            pose.popMatrix()
        }
    }
    private fun renderColorBars(guiGraphics: GuiGraphics) {
        val client = Minecraft.getInstance()
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val topH = h * 2 / 3
        val topColors = intArrayOf(
            0xFFBEBEBE.toInt(),
            0xFFBEBE00.toInt(),
            0xFF00BEBE.toInt(),
            0xFF00BE00.toInt(),
            0xFFBE00BE.toInt(),
            0xFFBE0000.toInt(),
            0xFF0000BE.toInt(),
        )
        val barW = w.toFloat() / topColors.size
        topColors.forEachIndexed { i, color ->
            guiGraphics.fill((i * barW).toInt(), 0, ((i + 1) * barW).toInt(), topH, color)
        }
        val midY0 = topH
        val midY1 = h * 3 / 4
        val midColors = intArrayOf(
            0xFF0000BE.toInt(), 0xFF131313.toInt(), 0xFFBE00BE.toInt(),
            0xFF131313.toInt(), 0xFF00BEBE.toInt(), 0xFF131313.toInt(), 0xFF131313.toInt(),
        )
        midColors.forEachIndexed { i, color ->
            guiGraphics.fill((i * barW).toInt(), midY0, ((i + 1) * barW).toInt(), midY1, color)
        }
        val botY0 = midY1
        val botY1 = h
        val leftW = w * 3 / 4
        val subW  = leftW.toFloat() / 4
        val subColors = intArrayOf(
            0xFF131B4C.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF2C1048.toInt(),
            0xFF0D0D0D.toInt(),
        )
        subColors.forEachIndexed { i, color ->
            guiGraphics.fill((i * subW).toInt(), botY0, ((i + 1) * subW).toInt(), botY1, color)
        }
        val plugeW = (w - leftW).toFloat() / 3
        val plugeColors = intArrayOf(0xFF040404.toInt(), 0xFF0D0D0D.toInt(), 0xFF1C1C1C.toInt())
        plugeColors.forEachIndexed { i, color ->
            guiGraphics.fill(leftW + (i * plugeW).toInt(), botY0, leftW + ((i + 1) * plugeW).toInt(), botY1, color)
        }
    }
    private fun fillGradientH(
        guiGraphics: GuiGraphics,
        x0: Int, y0: Int, x1: Int, y1: Int,
        colorLeft: Int, colorRight: Int,
        steps: Int = 64
    ) {
        val rL = (colorLeft  shr 16 and 0xFF).toFloat()
        val gL = (colorLeft  shr  8 and 0xFF).toFloat()
        val bL = (colorLeft         and 0xFF).toFloat()
        val rR = (colorRight shr 16 and 0xFF).toFloat()
        val gR = (colorRight shr  8 and 0xFF).toFloat()
        val bR = (colorRight        and 0xFF).toFloat()
        val totalW = (x1 - x0).toFloat()
        for (s in 0 until steps) {
            val t  = (s + 0.5f) / steps
            val px0 = x0 + (s.toFloat()       / steps * totalW).toInt()
            val px1 = x0 + ((s + 1).toFloat() / steps * totalW).toInt()
            val r  = (rL + (rR - rL) * t).toInt().coerceIn(0, 255)
            val g  = (gL + (gR - gL) * t).toInt().coerceIn(0, 255)
            val b  = (bL + (bR - bL) * t).toInt().coerceIn(0, 255)
            guiGraphics.fill(px0, y0, px1, y1, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        }
    }
    private fun renderColorBarsHd(guiGraphics: GuiGraphics) {
        val client = Minecraft.getInstance()
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val row1H = h * 7 / 12
        val row2H = h * 8 / 12
        val row3H = h * 9 / 12
        val sideW  = w / 7
        val midW   = w - sideW * 2
        val cBarW  = midW.toFloat() / 7
        guiGraphics.fill(0,         0, sideW,    row1H, 0xFF666666.toInt())
        guiGraphics.fill(w - sideW, 0, w,        row1H, 0xFF666666.toInt())
        val centerColors = intArrayOf(
            0xFFBEBEBE.toInt(), 0xFFBEBE00.toInt(), 0xFF00BEBE.toInt(),
            0xFF00BE00.toInt(), 0xFFBE00BE.toInt(), 0xFFBE0000.toInt(), 0xFF0000BE.toInt(),
        )
        centerColors.forEachIndexed { i, color ->
            val x0 = sideW + (i * cBarW).toInt()
            val x1 = sideW + ((i + 1) * cBarW).toInt()
            guiGraphics.fill(x0, 0, x1, row1H, color)
        }
        guiGraphics.fill(0,            row1H, sideW,         row2H, 0xFF00FFFF.toInt())
        guiGraphics.fill(sideW,        row1H, sideW * 2,     row2H, 0xFF666666.toInt())
        val gx0 = sideW * 2; val gx1 = w - sideW
        val gMid = (gx0 + gx1) / 2
        fillGradientH(guiGraphics, gx0, row1H, gMid, row2H, 0xFF303030.toInt(), 0xFFBEBEBE.toInt())
        fillGradientH(guiGraphics, gMid, row1H, gx1, row2H, 0xFFBEBEBE.toInt(), 0xFF303030.toInt())
        guiGraphics.fill(w - sideW,    row1H, w,             row2H, 0xFF0000FF.toInt())
        guiGraphics.fill(0,         row2H, sideW,     row3H, 0xFFFFFF00.toInt())
        fillGradientH(guiGraphics, sideW, row2H, w - sideW, row3H, 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        guiGraphics.fill(w - sideW, row2H, w,         row3H, 0xFFFF0000.toInt())
        val botGrayW = w / 7
        val whiteW   = w / 4
        val whiteX0  = (w - whiteW) / 2
        guiGraphics.fill(0,           row3H, w,              h, 0xFF0D0D0D.toInt())
        guiGraphics.fill(0,           row3H, botGrayW,       h, 0xFF262626.toInt())
        guiGraphics.fill(w-botGrayW,  row3H, w,              h, 0xFF262626.toInt())
        guiGraphics.fill(whiteX0,     row3H, whiteX0+whiteW, h, 0xFFFFFFFF.toInt())
    }
    private fun renderColorBarsEbu(guiGraphics: GuiGraphics) {
        val client = Minecraft.getInstance()
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val topH = h * 3 / 4
        val topColors = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFFFFF00.toInt(),
            0xFF00FFFF.toInt(),
            0xFF00FF00.toInt(),
            0xFFFF00FF.toInt(),
            0xFFFF0000.toInt(),
            0xFF0000FF.toInt(),
            0xFF000000.toInt(),
        )
        val barW = w.toFloat() / topColors.size
        topColors.forEachIndexed { i, color ->
            guiGraphics.fill((i * barW).toInt(), 0, ((i + 1) * barW).toInt(), topH, color)
        }
        val botY0 = topH
        val botColors = intArrayOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF000000.toInt(),
            0xFF00FFFF.toInt(),
            0xFF000000.toInt(),
            0xFFFF0000.toInt(),
            0xFF000000.toInt(),
            0xFF000000.toInt(),
        )
        botColors.forEachIndexed { i, color ->
            guiGraphics.fill((i * barW).toInt(), botY0, ((i + 1) * barW).toInt(), h, color)
        }
    }
    private fun renderColorBarsPluge(guiGraphics: GuiGraphics) {
        val client = Minecraft.getInstance()
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        guiGraphics.fill(0, 0, w, h, 0xFF808080.toInt())
        val greyBars = 8
        val greyW = w * 2 / 3
        val gBarW = greyW.toFloat() / greyBars
        for (i in 0 until greyBars) {
            val lum = (i * 255 / (greyBars - 1)).coerceIn(0, 255)
            val color = (0xFF shl 24) or (lum shl 16) or (lum shl 8) or lum
            guiGraphics.fill((i * gBarW).toInt(), 0, ((i + 1) * gBarW).toInt(), h, color)
        }
        val plugeX = greyW
        val plugeW = (w - greyW).toFloat() / 5
        val plugeColors = intArrayOf(
            0xFF050505.toInt(),
            0xFF101010.toInt(),
            0xFF1A1A1A.toInt(),
            0xFFE0E0E0.toInt(),
            0xFFFFFFFF.toInt(),
        )
        plugeColors.forEachIndexed { i, color ->
            val x0 = plugeX + (i * plugeW).toInt()
            val x1 = plugeX + ((i + 1) * plugeW).toInt()
            guiGraphics.fill(x0, 0, x1, h, color)
        }
    }
    private fun renderColorBarsMono(guiGraphics: GuiGraphics) {
        val client = Minecraft.getInstance()
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val steps = 10
        val barW = w.toFloat() / steps
        for (i in 0 until steps) {
            val lum = (i * 255 / (steps - 1)).coerceIn(0, 255)
            val color = (0xFF shl 24) or (lum shl 16) or (lum shl 8) or lum
            guiGraphics.fill((i * barW).toInt(), 0, ((i + 1) * barW).toInt(), h, color)
        }
    }
    private fun renderColorBarsRgb(guiGraphics: GuiGraphics) {
        val client = Minecraft.getInstance()
        val w = client.window.guiScaledWidth
        val h = client.window.guiScaledHeight
        val colors = intArrayOf(
            0xFF000000.toInt(),
            0xFFFF0000.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt(),
            0xFFFFFF00.toInt(),
            0xFF00FFFF.toInt(),
            0xFFFF00FF.toInt(),
            0xFFFFFFFF.toInt(),
        )
        val barW = w.toFloat() / colors.size
        colors.forEachIndexed { i, color ->
            guiGraphics.fill((i * barW).toInt(), 0, ((i + 1) * barW).toInt(), h, color)
        }
    }
    private fun renderColorBarsLabel(guiGraphics: GuiGraphics, rawText: String, corner: String) {
        val client = Minecraft.getInstance()
        val font   = client.font
        val w      = client.window.guiScaledWidth
        val h      = client.window.guiScaledHeight
        val converted = buildString {
            var idx = 0
            while (idx < rawText.length) {
                if (rawText[idx] == '&' && idx + 1 < rawText.length) {
                    val c = rawText[idx + 1].lowercaseChar()
                    if (c in '0'..'9' || c in 'a'..'f' || c == 'r' || c == 'l' || c == 'o' || c == 'n' || c == 'k') {
                        append('§'); append(rawText[idx + 1]); idx += 2; continue
                    }
                }
                append(rawText[idx]); idx++
            }
        }
        val component = Component.literal(converted)
        val scale  = 3f
        val textW  = (font.width(component) * scale).toInt()
        val textH  = (font.lineHeight * scale).toInt()
        val pad    = 8
        val anchorX = when {
            corner.endsWith("r") -> w - pad - textW
            else                  -> pad
        }
        val anchorY = when {
            corner.startsWith("b") -> h - pad - textH
            else                   -> pad
        }
        val pose = guiGraphics.pose()
        pose.pushMatrix()
        pose.translate(anchorX.toFloat(), anchorY.toFloat())
        pose.scale(scale, scale)
        guiGraphics.drawString(font, component, 0, 0, 0xFFFFFFFF.toInt(), false)
        pose.popMatrix()
    }
    private fun renderConsole(guiGraphics: GuiGraphics, now: Long) {
        val client  = Minecraft.getInstance()
        val scrW    = client.window.guiScaledWidth
        val scrH    = client.window.guiScaledHeight
        val mcText  = consoleMcText
        val font    = client.font
        guiGraphics.fill(0, 0, scrW, scrH, 0xFF000000.toInt())
        val lineH   = font.lineHeight + 2
        val padX    = 6
        val padY    = 6
        val maxLine = ((scrH - padY * 2) / lineH).coerceAtLeast(1)
        val displayLines = synchronized(consoleLock) {
            if (consoleVisible.size > maxLine) consoleVisible.takeLast(maxLine)
            else consoleVisible.toList()
        }
        displayLines.forEachIndexed { i, rawLine ->
            val converted = buildString {
                var idx = 0
                while (idx < rawLine.length) {
                    if (rawLine[idx] == '&' && idx + 1 < rawLine.length) {
                        val c = rawLine[idx + 1].lowercaseChar()
                        if (CODE_COLORS.containsKey(c) || FORMAT_CODES.contains(c)) {
                            append('§'); append(rawLine[idx + 1]); idx += 2; continue
                        }
                    }
                    append(rawLine[idx]); idx++
                }
            }
            val comp = Component.literal(converted)
            if (mcText) {
                guiGraphics.drawString(font, comp, padX, padY + i * lineH, consoleColor, false)
            } else {
                val styledComp = buildLineComponent(rawLine, useMcFont = false)
                guiGraphics.drawString(font, styledComp, padX, padY + i * lineH, consoleColor, false)
            }
        }
    }
    private fun sizeToScale(size: Int): Float = when (size) {
        1 -> 0.5f
        2 -> 0.75f
        3 -> 1.0f
        4 -> 1.5f
        5 -> 2.5f
        else -> 1.0f
    }
    fun parseColor(color: String): Int {
        val rgb = when (color.lowercase().trim()) {
            "red"          -> 0xFF5555
            "green"        -> 0x55FF55
            "blue"         -> 0x5555FF
            "white"        -> 0xFFFFFF
            "yellow"       -> 0xFFFF55
            "cyan"         -> 0x55FFFF
            "magenta"      -> 0xFF55FF
            "orange"       -> 0xFFAA00
            "black"        -> 0x000000
            "pink"         -> 0xFF69B4
            "gray", "grey" -> 0xAAAAAA
            "darkred"      -> 0xAA0000
            "darkgreen"    -> 0x00AA00
            "darkblue"     -> 0x0000AA
            "gold"         -> 0xFFAA00
            "purple"       -> 0xAA00AA
            else -> try {
                val hex = color.trim().removePrefix("#")
                hex.toLong(16).toInt()
            } catch (_: Exception) { 0xFFFFFF }
        }
        return (0xFF shl 24) or (rgb and 0xFFFFFF)
    }
}