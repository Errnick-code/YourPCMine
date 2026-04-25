package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data class FreezePayload(
    val milliseconds: Long
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<FreezePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FreezePayload>(
            Identifier.fromNamespaceAndPath("ypm", "freeze")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, FreezePayload> =
            StreamCodec.composite(
                ByteBufCodecs.VAR_LONG,
                FreezePayload::milliseconds,
                ::FreezePayload
            )
    }
}
