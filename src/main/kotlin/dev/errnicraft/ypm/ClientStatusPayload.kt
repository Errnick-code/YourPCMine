package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


class ClientStatusPayload(val flags: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ClientStatusPayload> = TYPE

    val safeMode         get() = (flags and 0b0001) != 0
    val blockShutdown    get() = (flags and 0b0010) != 0
    val blockWeb         get() = (flags and 0b0100) != 0
    val useNativeWindows get() = (flags and 0b1000) != 0

    companion object {
        val TYPE = CustomPacketPayload.Type<ClientStatusPayload>(
            Identifier.fromNamespaceAndPath("ypm", "client_status")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, ClientStatusPayload> =
            ByteBufCodecs.INT
                .map(::ClientStatusPayload, ClientStatusPayload::flags)
                .cast()
    }
}
