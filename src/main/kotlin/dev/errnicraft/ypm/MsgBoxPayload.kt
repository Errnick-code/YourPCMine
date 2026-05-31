package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


data class MsgBoxPayload(
    val title: String,
    val text: String,
    val buttons: String,
    val icon: String,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<MsgBoxPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MsgBoxPayload>(
            Identifier.fromNamespaceAndPath("ypm", "msgbox")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, MsgBoxPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, MsgBoxPayload::title,
                ByteBufCodecs.STRING_UTF8, MsgBoxPayload::text,
                ByteBufCodecs.STRING_UTF8, MsgBoxPayload::buttons,
                ByteBufCodecs.STRING_UTF8, MsgBoxPayload::icon,
                ::MsgBoxPayload
            )
    }
}
