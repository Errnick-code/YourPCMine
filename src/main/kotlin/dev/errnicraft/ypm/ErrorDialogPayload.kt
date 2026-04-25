package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data class ErrorDialogPayload(
    val title: String,
    val text: String,
    val freezeMs: Long = 0L  // 0 = не замораживать
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ErrorDialogPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ErrorDialogPayload>(
            Identifier.fromNamespaceAndPath("ypm", "error_dialog")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, ErrorDialogPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                ErrorDialogPayload::title,
                ByteBufCodecs.STRING_UTF8,
                ErrorDialogPayload::text,
                ByteBufCodecs.VAR_LONG,
                ErrorDialogPayload::freezeMs,
                ::ErrorDialogPayload
            )
    }
}
