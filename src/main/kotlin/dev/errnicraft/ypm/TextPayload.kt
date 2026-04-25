package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data class TextPayload(
    val filename: String,
    val text: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<TextPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<TextPayload>(Identifier.fromNamespaceAndPath("ypm", "text"))
        val CODEC: StreamCodec<FriendlyByteBuf, TextPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TextPayload::filename,
                ByteBufCodecs.STRING_UTF8, TextPayload::text,
                ::TextPayload
            )
    }
}
