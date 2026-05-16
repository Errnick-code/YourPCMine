package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Эффект консоли: весь экран становится чёрным, текст появляется строка за строкой как в терминале.
 *
 * @param text       текст (| = новая строка). Поддерживает &-коды цвета.
 * @param color      цвет текста: название ("green","white" и т.д.) или hex "#RRGGBB"
 * @param durationMs время показа в мс (0 = показывать вечно до следующего пакета)
 * @param sound      воспроизвести низкочастотный тон при появлении
 * @param screamer   воспроизвести скример-звук (square wave) при появлении
 * @param screamerVolume громкость скримера 0.0–1.0
 * @param mcText     использовать ванильный шрифт Minecraft вместо ypm_sans
 */
data class ConsoleOverlayPayload(
    val text: String,
    val color: String,
    val durationMs: Long,
    val sound: Boolean,
    val screamer: Boolean,
    val screamerVolume: Float,
    val mcText: Boolean,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ConsoleOverlayPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ConsoleOverlayPayload>(
            Identifier.fromNamespaceAndPath("ypm", "console_overlay")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, ConsoleOverlayPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,  ConsoleOverlayPayload::text,
                ByteBufCodecs.STRING_UTF8,  ConsoleOverlayPayload::color,
                ByteBufCodecs.VAR_LONG,     ConsoleOverlayPayload::durationMs,
                ByteBufCodecs.BOOL,         ConsoleOverlayPayload::sound,
                ByteBufCodecs.BOOL,         ConsoleOverlayPayload::screamer,
                ByteBufCodecs.FLOAT,        ConsoleOverlayPayload::screamerVolume,
                ByteBufCodecs.BOOL,         ConsoleOverlayPayload::mcText,
                ::ConsoleOverlayPayload
            )
    }
}
