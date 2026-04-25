package dev.errnicraft.ypm

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking
import net.minecraft.network.protocol.Packet
import net.minecraft.server.network.ConfigurationTask
import java.util.function.Consumer

class YpmConfigurationTask : ConfigurationTask {

    companion object {
        val TYPE = ConfigurationTask.Type("ypm:handshake")

        fun getVersion(): String = HandshakePayload.MOD_VERSION
    }

    override fun start(sender: Consumer<Packet<*>>) {
        sender.accept(ServerConfigurationNetworking.createS2CPacket(HandshakePayload.INSTANCE))
    }

    override fun type(): ConfigurationTask.Type = TYPE
}
