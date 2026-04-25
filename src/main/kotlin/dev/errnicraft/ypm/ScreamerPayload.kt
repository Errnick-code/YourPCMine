package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data class ScreamerPayload(
    val durationMs: Long,
    val strength: Int,
    val noise: Boolean,
    val restoreFullscreen: Boolean,
    val fullwindowed: Boolean  // трясти окно без выхода из фуллскрина (оконный режим на весь экран)
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ScreamerPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<ScreamerPayload>(Identifier.fromNamespaceAndPath("ypm", "windowshake"))
        val CODEC: StreamCodec<FriendlyByteBuf, ScreamerPayload> =
            StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, ScreamerPayload::durationMs,
                ByteBufCodecs.VAR_INT, ScreamerPayload::strength,
                ByteBufCodecs.BOOL, ScreamerPayload::noise,
                ByteBufCodecs.BOOL, ScreamerPayload::restoreFullscreen,
                ByteBufCodecs.BOOL, ScreamerPayload::fullwindowed,
                ::ScreamerPayload
            )
    }
}
