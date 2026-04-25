package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class RebootPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<RebootPayload> = TYPE

    companion object {
        val INSTANCE = RebootPayload()
        val TYPE = CustomPacketPayload.Type<RebootPayload>(
            Identifier.fromNamespaceAndPath("ypm", "reboot")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, RebootPayload> =
            StreamCodec.unit(INSTANCE)
    }
}
