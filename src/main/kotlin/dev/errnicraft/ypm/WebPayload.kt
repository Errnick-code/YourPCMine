package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data class WebPayload(val url: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<WebPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<WebPayload>(Identifier.fromNamespaceAndPath("ypm", "web"))
        val CODEC: StreamCodec<FriendlyByteBuf, WebPayload> =
            ByteBufCodecs.STRING_UTF8.map(::WebPayload, WebPayload::url).cast()
    }
}
