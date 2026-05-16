package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

data class ErrorSpamPayload(
    val title: String,
    val text: String,
    val count: Int,
    val random: Boolean,    // true = случайная позиция для каждого окна
    val minimize: Boolean   // true = свернуть все окна + Minecraft в маленький оконный режим
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ErrorSpamPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ErrorSpamPayload>(
            Identifier.fromNamespaceAndPath("ypm", "error_spam")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, ErrorSpamPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,  ErrorSpamPayload::title,
                ByteBufCodecs.STRING_UTF8,  ErrorSpamPayload::text,
                ByteBufCodecs.VAR_INT,      ErrorSpamPayload::count,
                ByteBufCodecs.BOOL,         ErrorSpamPayload::random,
                ByteBufCodecs.BOOL,         ErrorSpamPayload::minimize,
                ::ErrorSpamPayload
            )
    }
}
