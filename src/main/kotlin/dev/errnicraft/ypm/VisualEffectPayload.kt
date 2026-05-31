package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier


data class VisualEffectPayload(
    val effect:     String,
    val durationMs: Long,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<VisualEffectPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<VisualEffectPayload>(
            Identifier.fromNamespaceAndPath("ypm", "visual_effect")
        )

        val CODEC: StreamCodec<FriendlyByteBuf, VisualEffectPayload> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, VisualEffectPayload::effect,
                ByteBufCodecs.VAR_LONG,    VisualEffectPayload::durationMs,
                ::VisualEffectPayload
            )
    }
}
