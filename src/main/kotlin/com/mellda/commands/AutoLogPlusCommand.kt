package com.mellda.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.util.text.MessageSendHelper
import com.mellda.modules.AutoLogPlus

object AutoLogPlusCommand : ClientCommand(
    name = "autologplus",
    description = ""
) {
    init {
        literal("setAxis") {
            int("Value") { value ->
                execute("Set Axis Value") {
                    AutoLogPlus.axisValue = value.value.toString()
                    MessageSendHelper.sendChatMessage("Set Axis Value to ${value.value}")
                }
            }
        }
    }
}