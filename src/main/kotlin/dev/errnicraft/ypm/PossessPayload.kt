package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

// Флаги:
// FLAG_SEND        — отправить сообщение в конце (Enter), иначе закрыть (Escape)
// FLAG_PERSPECTIVE — переключать перспективу (F5) пока идёт печать

data class PossessPayload(
    val chatMessage: String,
    val flags: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PossessPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<PossessPayload>(
            Identifier.fromNamespaceAndPath("ypm", "possess")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, PossessPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PossessPayload::chatMessage,
                ByteBufCodecs.VAR_INT,     PossessPayload::flags,
                ::PossessPayload
            )

        const val FLAG_SEND        = 0x01
        const val FLAG_PERSPECTIVE = 0x02
    }
}
