package dev.errnicraft.ypm

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.network.chat.Component

class DisclaimerScreen(private val onAccept: () -> Unit) : Screen(Component.translatable("ypm.disclaimer.title")) {

    private lateinit var checkbox: Checkbox
    private lateinit var okButton: Button

    private val featureKeys = listOf(
        "ypm.disclaimer.feature.images"    to false,
        "ypm.disclaimer.feature.browser"   to false,
        "ypm.disclaimer.feature.chat"      to false,
        "ypm.disclaimer.feature.camera"    to false,
        "ypm.disclaimer.feature.shake"     to false,
        "ypm.disclaimer.feature.wallpaper" to false,
        "ypm.disclaimer.feature.shutdown"  to true,
    )

    companion object {
        private const val LINE_H  = 11
        private const val PAD     = 14
        private const val PANEL_W = 450
    }

    private fun subtitleLines(): List<String> =
        Component.translatable("ypm.disclaimer.subtitle").string.split("\n")

    private fun wrapLine(text: String, maxW: Int): List<String> {
        val out = mutableListOf<String>(); var line = StringBuilder()
        for (word in text.split(" ")) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (font.width(test) <= maxW) line = StringBuilder(test)
            else { if (line.isNotEmpty()) out += line.toString(); line = StringBuilder(word) }
        }
        if (line.isNotEmpty()) out += line.toString()
        return out.ifEmpty { listOf("") }
    }

    private fun contentMaxW() = PANEL_W - PAD * 2

    private fun computePanelH(): Int {
        val subH  = subtitleLines().size * LINE_H
        val featH = featureKeys.size * LINE_H
        val cmdH  = listOf("ypm.disclaimer.cmd.web", "ypm.disclaimer.cmd.shutdown", "ypm.disclaimer.cmd.safemode")
            .sumOf { wrapLine(Component.translatable(it).string, contentMaxW()).size } * LINE_H
        val safeModeH = LINE_H * 2 + 6  // блок про safe mode
        return PAD + 11 + 6 + 1 + 8 + subH + 4 + featH + 8 + 1 + 7 + 11 + (LINE_H + 2) + cmdH + 8 + 1 + 8 + safeModeH + PAD + 20 + 6 + 20 + PAD
    }

    override fun init() {
        val cx = width / 2; val cy = height / 2
        val panelH = computePanelH()
        val panelBot = cy + panelH / 2

        val buttonY   = panelBot - PAD - 20
        val checkboxY = buttonY - 6 - 20

        val checkboxLabel = Component.translatable("ypm.disclaimer.checkbox")
        val checkboxW     = 20 + 4 + font.width(checkboxLabel)
        checkbox = Checkbox.builder(checkboxLabel, font)
            .pos(cx - checkboxW / 2, checkboxY)
            .build()
        addRenderableWidget(checkbox)

        okButton = Button.builder(Component.translatable("ypm.disclaimer.button")) {
            if (checkbox.selected()) DisclaimerManager.setAccepted()
            onClose(); onAccept()
        }
            .pos(cx - 60, buttonY)
            .size(120, 20)
            .build()
        addRenderableWidget(okButton)
    }

    override fun renderBackground(g: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val cx = width / 2; val cy = height / 2
        val panelH = computePanelH()
        val x1 = cx - PANEL_W / 2; val y1 = cy - panelH / 2
        val x2 = cx + PANEL_W / 2; val y2 = cy + panelH / 2

        // Затемнение фона
        g.fill(0, 0, width, height, 0xAA000000.toInt())

        // Тень панели
        g.fill(x1 + 3, y1 + 3, x2 + 3, y2 + 3, 0x44000000.toInt())

        // Фон панели с градиентом
        g.fillGradient(x1, y1, x2, y2, 0xF0111111.toInt(), 0xF00A0A0A.toInt())

        // Рамка — двойная, внешняя тонкая
        g.fill(x1,     y1,     x2,     y1 + 1, 0xFF444444.toInt())
        g.fill(x1,     y2 - 1, x2,     y2,     0xFF444444.toInt())
        g.fill(x1,     y1,     x1 + 1, y2,     0xFF444444.toInt())
        g.fill(x2 - 1, y1,     x2,     y2,     0xFF444444.toInt())
        // Внутренняя рамка
        g.fill(x1 + 1,     y1 + 1,     x2 - 1,     y1 + 2, 0xFF222222.toInt())
        g.fill(x1 + 1,     y2 - 2,     x2 - 1,     y2 - 1, 0xFF222222.toInt())
        g.fill(x1 + 1,     y1 + 1,     x1 + 2,     y2 - 1, 0xFF222222.toInt())
        g.fill(x2 - 2,     y1 + 1,     x2 - 1,     y2 - 1, 0xFF222222.toInt())

        var y = y1 + PAD

        // Заголовок с градиентным подсвечиванием
        val titleStr = Component.translatable("ypm.disclaimer.title").string
        // Подсветка за заголовком
        g.fillGradient(x1 + 1, y - 2, x2 - 1, y + 16, 0x22FF0000.toInt(), 0x00FF0000.toInt())
        g.drawCenteredString(font, "§c§l>>> $titleStr <<<", cx, y, 0xFFFFFFFF.toInt())
        y += 13

        // Разделитель — градиентный
        g.fillGradient(x1 + 20, y, cx, y + 1, 0xFF111111.toInt(), 0xFF555555.toInt())
        g.fillGradient(cx, y, x2 - 20, y + 1, 0xFF555555.toInt(), 0xFF111111.toInt())
        y += 8

        // Subtitle
        for (line in subtitleLines()) {
            g.drawCenteredString(font, line, cx, y, 0xFFAAAAFF.toInt())
            y += LINE_H
        }
        y += 4

        // Список фич
        for ((key, red) in featureKeys) {
            val bullet = if (red) "§c* " else "§e- "
            val color  = if (red) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt()
            g.drawCenteredString(font, "$bullet§f${Component.translatable(key).string}", cx, y, color)
            y += LINE_H
        }

        y += 8
        // Разделитель
        g.fillGradient(x1 + 20, y, cx, y + 1, 0xFF111111.toInt(), 0xFF333333.toInt())
        g.fillGradient(cx, y, x2 - 20, y + 1, 0xFF333333.toInt(), 0xFF111111.toInt())
        y += 7

        // Footer
        g.drawCenteredString(font, Component.translatable("ypm.disclaimer.footer"), cx, y, 0xFF888888.toInt())
        y += LINE_H + 2

        // Команды конфига
        val maxW = contentMaxW()
        for (key in listOf("ypm.disclaimer.cmd.web", "ypm.disclaimer.cmd.shutdown")) {
            for (wrapped in wrapLine(Component.translatable(key).string, maxW)) {
                g.drawCenteredString(font, "§7$wrapped", cx, y, 0xFF666666.toInt())
                y += LINE_H
            }
        }

        y += 8
        // Разделитель перед Safe Mode блоком
        g.fillGradient(x1 + 20, y, cx, y + 1, 0xFF111111.toInt(), 0xFF2A2A00.toInt())
        g.fillGradient(cx, y, x2 - 20, y + 1, 0xFF2A2A00.toInt(), 0xFF111111.toInt())
        y += 8

        // Safe Mode блок — жёлтая тематика
        g.fillGradient(x1 + 20, y - 2, x2 - 20, y + LINE_H * 2 + 8, 0x1AFFAA00.toInt(), 0x00FFAA00.toInt())
        g.drawCenteredString(font, Component.translatable("ypm.disclaimer.safemode.title").string, cx, y, 0xFFFFCC00.toInt())
        y += LINE_H + 2
        for (wrapped in wrapLine(Component.translatable("ypm.disclaimer.safemode.desc").string, maxW)) {
            g.drawCenteredString(font, "§7$wrapped", cx, y, 0xFF999966.toInt())
            y += LINE_H
        }
        y += 4
        for (wrapped in wrapLine(Component.translatable("ypm.disclaimer.cmd.safemode").string, maxW)) {
            g.drawCenteredString(font, "§7$wrapped", cx, y, 0xFF666633.toInt())
            y += LINE_H
        }
    }

    override fun render(g: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(g, mouseX, mouseY, delta)
    }

    override fun isPauseScreen()    = false
    override fun shouldCloseOnEsc() = false
}
