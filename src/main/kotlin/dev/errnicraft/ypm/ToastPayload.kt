package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


data class ToastPayload(
    val title: String,
    val text: String,
    val icon: String,
    val durationMs: Int,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ToastPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ToastPayload>(
            Identifier.fromNamespaceAndPath("ypm", "toast")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, ToastPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ToastPayload::title,
                ByteBufCodecs.STRING_UTF8, ToastPayload::text,
                ByteBufCodecs.STRING_UTF8, ToastPayload::icon,
                ByteBufCodecs.INT,         ToastPayload::durationMs,
                ::ToastPayload
            )
    }
}
