package dev.errnicraft.ypm
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin
@Environment(EnvType.CLIENT)
class YpmChatScreen(
    private val session: YpmOverlaySession
) : ChatScreen("", false) {
    override fun init() {
        super.init()
        val hint = when {
            session.targetName.isEmpty() -> "→ overlay  §8|§7 r<блоков> — радиус"
            session.targetName.startsWith("r") -> {
                val radius = session.targetName.substring(1).toDoubleOrNull()
                if (radius != null) "→ все в радиусе §f${radius.toInt()}§7 блоков" else "→ ${session.targetName}"
            }
            else -> "→ ${session.targetName}  §8|§7 r<блоков> — радиус"
        }
        input.setHint(Component.literal("§7[$hint]"))
    }
    override fun moveInHistory(dir: Int) {
        super.moveInHistory(dir)
    }
    override fun handleChatInput(msg: String, addToRecent: Boolean) {
        val normalized = normalizeChatMessage(msg)
        if (normalized.isEmpty()) return
        if (normalized.startsWith("/")) {
            if (addToRecent) minecraft.gui.getChat().addRecentChat(normalized)
            minecraft.player?.connection?.sendCommand(normalized.substring(1))
            return
        }
        val overlayText = normalized.replace("|", "\n")
        ClientPlayNetworking.send(
            OverlayTextRequestPayload(
                targetName  = session.targetName,
                text        = overlayText,
                color       = session.color,
                size        = session.size,
                scaleX      = session.scaleX,
                scaleY      = session.scaleY,
                durationMs  = session.durationMs,
                randomPos   = session.randomPos,
                randomScale = session.randomScale,
                sound       = session.sound,
                mcText      = session.mcText,
            )
        )
        if (addToRecent) minecraft.gui.getChat().addRecentChat(normalized)
    }
}
@Environment(EnvType.CLIENT)
data class YpmOverlaySession(
    val targetName: String,
    val durationMs: Long,
    val size: Int,
    val scaleX: Float,
    val scaleY: Float,
    val color: String,
    val randomPos: Boolean,
    val randomScale: Boolean,
    val sound: Boolean,
    val mcText: Boolean = false,
)
@Environment(EnvType.CLIENT)
fun toneParamsForText(text: String): ToneParams {
    val j = kotlin.random.Random.Default.nextDouble(-10.0, 10.0)
    val last = text.trimEnd().lastOrNull() ?: '.'
    return when (last) {
        '?'       -> ToneParams(360.0 + j, 380.0 + j, 900L, "sine")
        '!'       -> ToneParams(100.0 + j, 100.0 + j, 900L, "square")
        ')'       -> ToneParams(320.0 + j, 520.0 + j, 900L, "sine")
        '('       -> ToneParams(390.0 + j, 280.0 + j, 900L, "sine")
        '/', '\\' -> ToneParams(320.0 + j, 360.0 + j, 900L, "sine")
        else      -> ToneParams(330.0 + j, 350.0 + j, 900L, "sine")
    }
}
data class ToneParams(
    val freqStart: Double,
    val freqEnd: Double,
    val durationMs: Long,
    val wave: String,
)
@Environment(EnvType.CLIENT)
fun playWaveTone(
    durationMs: Long = 900L,
    freqStart: Double = 100.0,
    freqEnd: Double = 100.0,
    volume: Float = 0.55f,
    wave: String = "sine",
) {
    Thread {
        try {
            val sampleRate = 44100f
            val totalSamples = (sampleRate * durationMs / 1000.0).toInt()
            val format = AudioFormat(sampleRate, 16, 1, true, false)
            val dataLine = AudioSystem.getSourceDataLine(format)
            dataLine.open(format, 4096)
            dataLine.start()
            val buf = ByteArray(totalSamples * 2)
            var phase = 0.0
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / totalSamples
                val freq = freqStart + (freqEnd - freqStart) * t
                val env = when {
                    t < 0.3 -> t / 0.3
                    else    -> 1.0 - (t - 0.3) / 0.7
                }.coerceIn(0.0, 1.0)
                val raw = when (wave) {
                    "square"   -> if (sin(phase) >= 0.0) 1.0 else -1.0
                    "sawtooth" -> 2.0 * ((phase / (2.0 * PI)) % 1.0) - 1.0
                    "triangle" -> { val p = (phase / (2.0 * PI)) % 1.0; if (p < 0.5) 4.0*p - 1.0 else 3.0 - 4.0*p }
                    else       -> sin(phase)
                }
                val sample = raw * env * volume * Short.MAX_VALUE
                phase += 2.0 * PI * freq / sampleRate
                val s = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buf[i * 2]     = (s.toInt() and 0xFF).toByte()
                buf[i * 2 + 1] = (s.toInt() shr 8).toByte()
            }
            dataLine.write(buf, 0, buf.size)
            dataLine.drain()
            dataLine.close()
        } catch (_: Exception) {  }
    }.also { it.isDaemon = true; it.name = "ypm-wave-tone"; it.start() }
}