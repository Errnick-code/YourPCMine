package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class ShutdownPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ShutdownPayload> = TYPE

    companion object {
        val INSTANCE = ShutdownPayload()
        val TYPE = CustomPacketPayload.Type<ShutdownPayload>(
            Identifier.fromNamespaceAndPath("ypm", "shutdown")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, ShutdownPayload> =
            StreamCodec.unit(INSTANCE)
    }
}
