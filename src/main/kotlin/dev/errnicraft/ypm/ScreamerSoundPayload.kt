package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Скример-звук из квадратной волны (square wave).
 *
 * @param volume    громкость 0.0–1.0
 * @param randomize рандомизировать высоту (частоту) звука
 * @param durationMs длительность звука в мс
 */
data class ScreamerSoundPayload(
    val volume: Float,
    val randomize: Boolean,
    val durationMs: Long,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<ScreamerSoundPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ScreamerSoundPayload>(
            Identifier.fromNamespaceAndPath("ypm", "screamer_sound")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, ScreamerSoundPayload> =
            StreamCodec.composite(
                ByteBufCodecs.FLOAT,    ScreamerSoundPayload::volume,
                ByteBufCodecs.BOOL,     ScreamerSoundPayload::randomize,
                ByteBufCodecs.VAR_LONG, ScreamerSoundPayload::durationMs,
                ::ScreamerSoundPayload
            )
    }
}
