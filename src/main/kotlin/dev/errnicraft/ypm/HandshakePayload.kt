package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class HandshakePayload(val version: String = MOD_VERSION) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<HandshakePayload> = TYPE

    companion object {
        const val MOD_VERSION = "1.0.2"

        val TYPE = CustomPacketPayload.Type<HandshakePayload>(
            Identifier.fromNamespaceAndPath("ypm", "handshake")
        )
        val INSTANCE = HandshakePayload()
        val CODEC: StreamCodec<FriendlyByteBuf, HandshakePayload> =
            ByteBufCodecs.STRING_UTF8
                .map(::HandshakePayload, HandshakePayload::version)
                .cast()
    }
}
