package com.mellda.commands

import com.lambda.client.command.ClientCommand
import com.lambda.client.util.text.MessageSendHelper
import com.mellda.modules.AutoLogPlus

object DiscordCommand : ClientCommand(
    name = "dicsordhook",
    description = "Add discord Hook."
) {
    init {
        literal("set") {
            string("hookURL") { url ->
                execute("Set DiscordHook") {
                    if (url.value.trim() == "") {
                        AutoLogPlus.hookUrl = url.value.trim()
                        MessageSendHelper.sendChatMessage("Cleared DiscordHook Url.")
                    }
                    else if (url.value.startsWith("https://discord.com/api/webhooks/")) {
                        AutoLogPlus.hookUrl = url.value
                        MessageSendHelper.sendChatMessage("Set DiscordHook Url to ${url.value}.")
                    } else {
                        MessageSendHelper.sendWarningMessage("Invalid Hook Detected.")
                    }
                }
            }
        }
        literal("get") {
            execute("Get WebHook Url") {
                MessageSendHelper.sendChatMessage(AutoLogPlus.hookUrl)
            }
        }
        literal("clear") {
            execute("Clear DiscordHook") {
                AutoLogPlus.hookUrl = ""
                MessageSendHelper.sendChatMessage("Cleared DiscordHook Url.")
            }
        }
    }
}