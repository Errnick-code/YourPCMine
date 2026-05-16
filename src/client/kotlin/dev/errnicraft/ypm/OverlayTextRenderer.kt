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

/**
 * Рендерит overlay-текст и эффект консоли поверх всего HUD.
 *
 * Поддерживает:
 *  - &-коды цвета (&4 = тёмно-красный, &c = красный и т.д.) + §-коды
 *  - --mctext: рендер стандартным шрифтом Minecraft вместо ypm_sans
 *  - случайную позицию, масштаб, цвет
 *  - эффект консоли: чёрный экран + текст строками (нельзя закрыть)
 */
@Environment(EnvType.CLIENT)
object OverlayTextRenderer {

    private val entries = mutableListOf<OverlayEntry>()
    private val lock = Any()
    private var registered = false

    private val YPM_FONT = FontDescription.Resource(Identifier.fromNamespaceAndPath("ypm", "ypm_sans"))

    // ── Эффект консоли ────────────────────────────────────────────────────────
    @Volatile private var consoleActive = false
    private val consoleLines   = mutableListOf<String>()   // все строки текущего сообщения
    private val consoleVisible = mutableListOf<String>()   // строки которые сейчас видны
    private val consoleLock    = Any()
    @Volatile private var consoleColor   = 0xFF00FF00.toInt()
    @Volatile private var consoleExpire  = 0L   // 0 = бесконечно (весь блок)
    @Volatile private var consoleMcText  = false
    // Поток-тайпер для построчного показа консоли
    @Volatile private var consoleTyperThread: Thread? = null

    // ── Mapping &-кодов → ARGB цвет ──────────────────────────────────────────
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

    // Коды форматирования (не цвет) — конвертируются &→§ но не трактуются как цвет
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

    // ── Парсинг &-кодов и §-кодов ────────────────────────────────────────────

    /**
     * Извлекает первый &X или §X цветовой код из текста.
     * Если найден — возвращает соответствующий ARGB, иначе null.
     */
    fun extractColorCode(text: String): Int? {
        val i = text.indexOfFirst { it == '&' || it == '§' }
        if (i < 0 || i + 1 >= text.length) return null
        return CODE_COLORS[text[i + 1].lowercaseChar()]
    }

    /**
     * Убирает все &X / §X коды (цвет и форматирование) из строки и возвращает чистый текст.
     * Используется для измерения ширины.
     */
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

    /**
     * Конвертирует &-коды (цвет + форматирование) → §-коды чтобы Minecraft их понял в Component.
     * Затем оборачивает в Component.literal с нужным шрифтом.
     * Базовый цвет из color-аргумента передаётся отдельно через [baseArgb] и применяется
     * только если строка не начинается с собственного цветового кода.
     */
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
        // Применяем базовый цвет через Style только если строка не начинает своего цветового кода.
        // Если строка начинается с §X — Minecraft сам подхватит, не перебиваем.
        val hasOwnColor = converted.length >= 2 && converted[0] == '§'
                && (CODE_COLORS.containsKey(converted[1].lowercaseChar()))
        val styled = if (!hasOwnColor && baseArgb != null) {
            comp.withStyle { it.withColor(baseArgb and 0xFFFFFF) }
        } else comp
        return if (useMcFont) styled
        else styled.withStyle { it.withFont(YPM_FONT) }
    }

    // ── Показ overlay-текста ──────────────────────────────────────────────────

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

        // Базовый цвет из color-аргумента. Каждая строка получает его как фоновый,
        // но если строка начинается с собственного &X — он перебивает базовый.
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

    // ── Эффект консоли ───────────────────────────────────────────────────────

    fun showConsole(
        text: String,
        color: String,
        durationMs: Long,
        mcText: Boolean = false,
    ) {
        val argb  = parseColor(color)
        // | = разделитель строк. Каждая строка появляется через 100мс одна за другой,
        // потом весь цикл повторяется снова (экран очищается и заново).
        val lines = text.split("|").map { it.trim() }

        // Останавливаем предыдущий тайпер если был
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

        // Запускаем тайпер: строки добавляются сверху вниз как в реальном терминале.
        // Когда экран заполнен — продолжаем добавлять (старые уходят вверх за экран).
        // При истечении времени — стоп, пауза 500мс, закрыть консоль.
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
                        // Время вышло — стоп, пауза 500мс, закрыть
                        Thread.sleep(500L)
                        synchronized(consoleLock) { consoleActive = false }
                        return@Thread
                    }
                    // Добавляем следующую строку снизу, старые уходят вверх
                    for (line in snapLines) {
                        if (Thread.interrupted()) return@Thread
                        val nowExpire: Long
                        synchronized(consoleLock) {
                            if (!consoleActive) return@Thread
                            consoleVisible.add(line)
                            // Ограничиваем буфер — держим последние 200 строк чтобы не течь памятью
                            if (consoleVisible.size > 200) consoleVisible.removeAt(0)
                            nowExpire = consoleExpire
                        }
                        if (nowExpire > 0 && System.currentTimeMillis() >= nowExpire) {
                            // Время вышло посреди строки — стоп, пауза 500мс, закрыть
                            Thread.sleep(500L)
                            synchronized(consoleLock) { consoleActive = false }
                            return@Thread
                        }
                        Thread.sleep(200L)  // скорость вывода строк: 0.2 сек
                    }
                    // Цикл заново — продолжаем добавлять строки бесконечно
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

    // ── Рендер ───────────────────────────────────────────────────────────────

    private fun render(guiGraphics: GuiGraphics) {
        val now = System.currentTimeMillis()

        // 1. Консоль — рендерится первой (под текстом, но выше игры)
        synchronized(consoleLock) {
            if (consoleActive) {
                if (consoleExpire > 0 && now >= consoleExpire) {
                    consoleActive = false
                } else {
                    renderConsole(guiGraphics, now)
                }
            }
        }

        // 2. Обычные overlay-записи
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

    private fun renderConsole(guiGraphics: GuiGraphics, now: Long) {
        val client  = Minecraft.getInstance()
        val scrW    = client.window.guiScaledWidth
        val scrH    = client.window.guiScaledHeight
        val mcText  = consoleMcText
        val font    = client.font

        // Чёрный фон на весь экран (непрозрачный)
        guiGraphics.fill(0, 0, scrW, scrH, 0xFF000000.toInt())

        // Текст: рендерим строки сверху вниз с небольшим отступом
        val lineH   = font.lineHeight + 2
        val padX    = 6
        val padY    = 6
        val maxLine = ((scrH - padY * 2) / lineH).coerceAtLeast(1)

        // Показываем последние maxLine строк
        val displayLines = synchronized(consoleLock) {
            if (consoleVisible.size > maxLine) consoleVisible.takeLast(maxLine)
            else consoleVisible.toList()
        }

        displayLines.forEachIndexed { i, rawLine ->
            // &-коды → §-коды для Minecraft Component
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
                // Ванильный шрифт Minecraft
                guiGraphics.drawString(font, comp, padX, padY + i * lineH, consoleColor, false)
            } else {
                // ypm_sans через buildLineComponent
                val styledComp = buildLineComponent(rawLine, useMcFont = false)
                guiGraphics.drawString(font, styledComp, padX, padY + i * lineH, consoleColor, false)
            }
        }
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

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
