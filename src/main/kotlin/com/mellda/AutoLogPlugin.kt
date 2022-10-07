package com.mellda

import com.lambda.client.plugin.api.Plugin
import com.mellda.commands.AutoLogPlusCommand
import com.mellda.commands.DiscordCommand
import com.mellda.modules.AutoLogPlus

internal object AutoLogPlugin : Plugin() {

    override fun onLoad() {
        modules.add(AutoLogPlus)
        commands.add(AutoLogPlusCommand)
        commands.add(DiscordCommand)
    }
}