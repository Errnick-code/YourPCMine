package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class MinimizePayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<MinimizePayload> = TYPE
    companion object {
        val INSTANCE = MinimizePayload()
        val TYPE = CustomPacketPayload.Type<MinimizePayload>(
            Identifier.fromNamespaceAndPath("ypm", "minimize")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, MinimizePayload> =
            StreamCodec.unit(INSTANCE)
    }
}
