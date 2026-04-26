package dev.errnicraft.ypm

import net.fabricmc.loader.api.FabricLoader
import java.io.File

object DisclaimerManager {

    private val flagFile: File by lazy {
        FabricLoader.getInstance().configDir.resolve("ypm_accepted.flag").toFile()
    }

    fun isAccepted(): Boolean = flagFile.exists()

    fun setAccepted() {
        flagFile.parentFile?.mkdirs()
        if (!flagFile.exists()) flagFile.createNewFile()
    }
}
