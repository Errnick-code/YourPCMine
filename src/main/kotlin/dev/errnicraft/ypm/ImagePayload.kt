package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data class ImagePayload(
    val url: String,
    val durationMs: Long,
    val fadeOutMs: Long
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ImagePayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<ImagePayload>(Identifier.fromNamespaceAndPath("ypm", "image"))
        val CODEC: StreamCodec<FriendlyByteBuf, ImagePayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ImagePayload::url,
                ByteBufCodecs.VAR_LONG, ImagePayload::durationMs,
                ByteBufCodecs.VAR_LONG, ImagePayload::fadeOutMs,
                ::ImagePayload
            )
    }
}
