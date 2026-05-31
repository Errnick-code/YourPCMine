package dev.errnicraft.ypm
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
@Environment(EnvType.CLIENT)
object VisualEffectRenderer {
    private var registered = false
    @Volatile private var invertActive = false
    @Volatile private var invertExpire = 0L
    private val INVERT_ID = Identifier.withDefaultNamespace("invert")
    fun register() {
        if (registered) return
        registered = true
        HudRenderCallback.EVENT.register { _, _ -> tick() }
    }
    fun activate(effect: String, durationMs: Long) {
        if (effect != "invert") return
        invertActive = true
        invertExpire = System.currentTimeMillis() + durationMs
        applyEffect()
    }
    private fun tick() {
        if (!invertActive) return
        if (System.currentTimeMillis() >= invertExpire) {
            invertActive = false
            clearEffect()
            return
        }
        val gr = Minecraft.getInstance().gameRenderer
        if (gr.postEffectId != INVERT_ID || !gr.effectActive) {
            applyEffect()
        }
    }
    private fun applyEffect() {
        val client = Minecraft.getInstance()
        client.execute {
            client.gameRenderer.postEffectId = INVERT_ID
            client.gameRenderer.effectActive = true
        }
    }
    private fun clearEffect() {
        val client = Minecraft.getInstance()
        client.execute {
            client.gameRenderer.postEffectId = null
            client.gameRenderer.effectActive = false
        }
    }
    fun close() {
        invertActive = false
        clearEffect()
    }
}