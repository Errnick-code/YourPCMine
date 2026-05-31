package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


data class OverlaySpamPayload(
    val text: String,
    val color: String,
    val size: Int,
    val scaleX: Float,
    val scaleY: Float,
    val durationMs: Long,
    val count: Int,
    val random: Boolean,
    val randomScale: Boolean,
    val sound: Boolean,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OverlaySpamPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OverlaySpamPayload>(
            Identifier.fromNamespaceAndPath("ypm", "overlay_spam")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, OverlaySpamPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,  OverlaySpamPayload::text,
                ByteBufCodecs.STRING_UTF8,  OverlaySpamPayload::color,
                ByteBufCodecs.VAR_INT,      OverlaySpamPayload::size,
                ByteBufCodecs.FLOAT,        OverlaySpamPayload::scaleX,
                ByteBufCodecs.FLOAT,        OverlaySpamPayload::scaleY,
                ByteBufCodecs.VAR_LONG,     OverlaySpamPayload::durationMs,
                ByteBufCodecs.VAR_INT,      OverlaySpamPayload::count,
                ByteBufCodecs.BOOL,         OverlaySpamPayload::random,
                ByteBufCodecs.BOOL,         OverlaySpamPayload::randomScale,
                ByteBufCodecs.BOOL,         OverlaySpamPayload::sound,
                ::OverlaySpamPayload
            )
    }
}
