package dev.errnicraft.ypm

import net.fabricmc.loader.api.FabricLoader
import java.util.Properties
import java.io.File

object YpmPlayerConfig {

    private val configFile: File by lazy {
        FabricLoader.getInstance().configDir.resolve("ypm_player.properties").toFile()
    }

    private val props = Properties()

    init {
        if (configFile.exists()) {
            configFile.inputStream().use { props.load(it) }
        }
    }

    var blockWeb: Boolean
        get() = props.getProperty("block_web", "false") == "true"
        set(v) { props["block_web"] = v.toString(); save() }

    var blockShutdown: Boolean
        get() = props.getProperty("block_shutdown", "false") == "true"
        set(v) { props["block_shutdown"] = v.toString(); save() }

    var safeMode: Boolean
        get() = props.getProperty("safe_mode", "false") == "true"
        set(v) { props["safe_mode"] = v.toString(); save() }

    private fun save() {
        configFile.parentFile?.mkdirs()
        configFile.outputStream().use { props.store(it, "YPM Player Config") }
    }
}
