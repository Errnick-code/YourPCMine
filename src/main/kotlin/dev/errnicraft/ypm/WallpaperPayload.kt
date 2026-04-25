package dev.errnicraft.ypm

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class WallpaperPayload(val url: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<WallpaperPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<WallpaperPayload>(
            Identifier.fromNamespaceAndPath("ypm", "wallpaper")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, WallpaperPayload> =
            ByteBufCodecs.STRING_UTF8.map(::WallpaperPayload, WallpaperPayload::url)
                .cast()
    }
}
