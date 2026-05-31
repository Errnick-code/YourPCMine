package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


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
