package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


data class OverlaySessionPayload(
    val active: Boolean,

    val targetName: String = "",
    val durationMs: Long = 5000L,
    val size: Int = 3,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val color: String = "white",
    val randomPos: Boolean = false,
    val randomScale: Boolean = false,
    val sound: Boolean = false,
    val mcText: Boolean = false,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OverlaySessionPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OverlaySessionPayload>(
            Identifier.fromNamespaceAndPath("ypm", "overlay_session")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, OverlaySessionPayload> =
            StreamCodec.composite(
                ByteBufCodecs.BOOL,         OverlaySessionPayload::active,
                ByteBufCodecs.STRING_UTF8,  OverlaySessionPayload::targetName,
                ByteBufCodecs.VAR_LONG,     OverlaySessionPayload::durationMs,
                ByteBufCodecs.VAR_INT,      OverlaySessionPayload::size,
                ByteBufCodecs.FLOAT,        OverlaySessionPayload::scaleX,
                ByteBufCodecs.FLOAT,        OverlaySessionPayload::scaleY,
                ByteBufCodecs.STRING_UTF8,  OverlaySessionPayload::color,
                ByteBufCodecs.BOOL,         OverlaySessionPayload::randomPos,
                ByteBufCodecs.BOOL,         OverlaySessionPayload::randomScale,
                ByteBufCodecs.BOOL,         OverlaySessionPayload::sound,
                ByteBufCodecs.BOOL,         OverlaySessionPayload::mcText,
                ::OverlaySessionPayload
            )
    }
}
