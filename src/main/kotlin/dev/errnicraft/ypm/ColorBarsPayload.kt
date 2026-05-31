package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


data class ColorBarsPayload(
    val durationMs:  Long,
    val tone:        Boolean,
    val barType:     String,
    val labelText:   String,
    val labelCorner: String,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ColorBarsPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ColorBarsPayload>(
            Identifier.fromNamespaceAndPath("ypm", "color_bars")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, ColorBarsPayload> =
            StreamCodec.composite(
                ByteBufCodecs.VAR_LONG,    ColorBarsPayload::durationMs,
                ByteBufCodecs.BOOL,        ColorBarsPayload::tone,
                ByteBufCodecs.STRING_UTF8, ColorBarsPayload::barType,
                ByteBufCodecs.STRING_UTF8, ColorBarsPayload::labelText,
                ByteBufCodecs.STRING_UTF8, ColorBarsPayload::labelCorner,
                ::ColorBarsPayload
            )
    }
}
