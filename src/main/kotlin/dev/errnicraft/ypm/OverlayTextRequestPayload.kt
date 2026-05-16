package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Клиент → сервер: владелец вводит текст в overlay-режиме.
 * Сервер найдёт игрока по [targetName] и отправит ему OverlayTextPayload.
 * Если [targetName] пуст — overlay получает сам отправитель.
 */
data class OverlayTextRequestPayload(
    val targetName: String,
    val text: String,
    val color: String,
    val size: Int,
    val scaleX: Float,
    val scaleY: Float,
    val durationMs: Long,
    val randomPos: Boolean,
    val randomScale: Boolean,
    val sound: Boolean,
    val mcText: Boolean = false,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OverlayTextRequestPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OverlayTextRequestPayload>(
            Identifier.fromNamespaceAndPath("ypm", "overlay_text_request")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, OverlayTextRequestPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,  OverlayTextRequestPayload::targetName,
                ByteBufCodecs.STRING_UTF8,  OverlayTextRequestPayload::text,
                ByteBufCodecs.STRING_UTF8,  OverlayTextRequestPayload::color,
                ByteBufCodecs.VAR_INT,      OverlayTextRequestPayload::size,
                ByteBufCodecs.FLOAT,        OverlayTextRequestPayload::scaleX,
                ByteBufCodecs.FLOAT,        OverlayTextRequestPayload::scaleY,
                ByteBufCodecs.VAR_LONG,     OverlayTextRequestPayload::durationMs,
                ByteBufCodecs.BOOL,         OverlayTextRequestPayload::randomPos,
                ByteBufCodecs.BOOL,         OverlayTextRequestPayload::randomScale,
                ByteBufCodecs.BOOL,         OverlayTextRequestPayload::sound,
                ByteBufCodecs.BOOL,         OverlayTextRequestPayload::mcText,
                ::OverlayTextRequestPayload
            )
    }
}
