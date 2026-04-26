package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class ShowDisclaimerPayload : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ShowDisclaimerPayload>(
            Identifier.fromNamespaceAndPath("ypm", "show_disclaimer")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, ShowDisclaimerPayload> =
            StreamCodec.of({ _, _ -> }, { ShowDisclaimerPayload() })
    }
    override fun type(): CustomPacketPayload.Type<ShowDisclaimerPayload> = ID
}
