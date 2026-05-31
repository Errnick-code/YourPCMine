package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


data class SysSoundPayload(
    val sound: String,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<SysSoundPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<SysSoundPayload>(
            Identifier.fromNamespaceAndPath("ypm", "sys_sound")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, SysSoundPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SysSoundPayload::sound,
                ::SysSoundPayload
            )
    }
}
