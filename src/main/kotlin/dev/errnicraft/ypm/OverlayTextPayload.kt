package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Показывает текст поверх экрана в случайном месте (или по центру).
 *
 * @param text      текст для отображения (| = новая строка)
 * @param color     цвет: название ("red","green","white" и т.д.) или hex "#RRGGBB"
 * @param size      1–5 (или случайный на стороне сервера)
 * @param scaleX    горизонтальное растяжение (1.0 = норма, >1 = растянуть, <1 = ужать)
 * @param scaleY    вертикальное растяжение
 * @param durationMs время показа в мс
 * @param random      true = случайная позиция на экране
 * @param randomScale true = случайное растяжение/сжатие (scaleX/scaleY игнорируются, генерируются рандомно)
 * @param sound       true = воспроизвести низкий волновой звук (2-3 ноты с разным pitch)
 */
data class OverlayTextPayload(
    val text: String,
    val color: String,
    val size: Int,
    val scaleX: Float,
    val scaleY: Float,
    val durationMs: Long,
    val random: Boolean,
    val randomScale: Boolean = false,
    val sound: Boolean = false,
    val mcText: Boolean = false,  // true = рендерить стандартным шрифтом Minecraft (--mctext)
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OverlayTextPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<OverlayTextPayload>(
            Identifier.fromNamespaceAndPath("ypm", "overlay_text")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, OverlayTextPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,  OverlayTextPayload::text,
                ByteBufCodecs.STRING_UTF8,  OverlayTextPayload::color,
                ByteBufCodecs.VAR_INT,      OverlayTextPayload::size,
                ByteBufCodecs.FLOAT,        OverlayTextPayload::scaleX,
                ByteBufCodecs.FLOAT,        OverlayTextPayload::scaleY,
                ByteBufCodecs.VAR_LONG,     OverlayTextPayload::durationMs,
                ByteBufCodecs.BOOL,         OverlayTextPayload::random,
                ByteBufCodecs.BOOL,         OverlayTextPayload::randomScale,
                ByteBufCodecs.BOOL,         OverlayTextPayload::sound,
                ByteBufCodecs.BOOL,         OverlayTextPayload::mcText,
                ::OverlayTextPayload
            )
    }
}
