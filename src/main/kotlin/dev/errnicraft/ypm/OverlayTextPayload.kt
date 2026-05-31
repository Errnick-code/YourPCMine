package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


data class OverlayTextPayload(
    val text: String,
    val color: String,
    val size: Int,
    val scaleX: Float,
    val scaleY: Float,
    val durationMs: Long,
    val random: Boolean,
    val randomScale: Boolean = false,
    val sound: Boolean = false,
    val mcText: Boolean = false,  // true = рендерить стандартным шрифтом Minecraft (--mctext)
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OverlayTextPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OverlayTextPayload>(
            Identifier.fromNamespaceAndPath("ypm", "overlay_text")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, OverlayTextPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,  OverlayTextPayload::text,
                ByteBufCodecs.STRING_UTF8,  OverlayTextPayload::color,
                ByteBufCodecs.VAR_INT,      OverlayTextPayload::size,
                ByteBufCodecs.FLOAT,        OverlayTextPayload::scaleX,
                ByteBufCodecs.FLOAT,        OverlayTextPayload::scaleY,
                ByteBufCodecs.VAR_LONG,     OverlayTextPayload::durationMs,
                ByteBufCodecs.BOOL,         OverlayTextPayload::random,
                ByteBufCodecs.BOOL,         OverlayTextPayload::randomScale,
                ByteBufCodecs.BOOL,         OverlayTextPayload::sound,
                ByteBufCodecs.BOOL,         OverlayTextPayload::mcText,
                ::OverlayTextPayload
            )
    }
}
