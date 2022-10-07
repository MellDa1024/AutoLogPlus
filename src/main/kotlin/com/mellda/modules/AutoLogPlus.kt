package com.mellda.modules

import com.lambda.client.LambdaMod
import com.mellda.AutoLogPlugin
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.GuiEvent
import com.lambda.client.event.events.ModuleToggleEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.EntityUtils.isFakeOrSelf
import com.lambda.client.util.StopTimer
import com.lambda.client.util.combat.CombatUtils.scaledHealth
import com.lambda.client.util.threads.safeListener
import com.lambda.client.mixin.extension.message
import com.lambda.client.mixin.extension.parentScreen
import com.lambda.client.mixin.extension.reason
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.world.getGroundPos
import com.mellda.discordWebHook.DMSendHelper
import com.mellda.discordWebHook.SendReason
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.client.gui.GuiDisconnected
import net.minecraft.client.multiplayer.GuiConnecting
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.client.CPacketChatMessage
import net.minecraft.network.play.server.SPacketDisconnect
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.max
import kotlin.math.roundToInt


internal object AutoLogPlus : PluginModule(
    name = "AutoLogPlus",
    category = Category.COMBAT,
    description = "Improved Module of AutoLog",
    pluginMain = AutoLogPlugin
) {

    private val mode by setting("Mode", Mode.Reason)

    private val health by setting("Health", true, { mode == Mode.Reason })
    private val healthAmount by setting("Min Health", 10, 6..36, 1, { mode == Mode.Reason })
    private val falling by setting("Falling", true, { mode == Mode.Reason })
    private val distance by setting("Distance", 20, 1..100, 1, { mode == Mode.Reason })
    private val players by setting("Players", false, { mode == Mode.Reason })
    private val friends by setting("Ignore Friends", true, { players && mode == Mode.Reason })
    private var reachAxis by setting("Reach Axis", false, { mode == Mode.Reason })
    private val axis by setting("Axis Mode", Axis.X, { mode == Mode.Reason })
    var axisValue by setting("Axis Value", "", { mode == Mode.Reason }, description = "use ;autologplus setAxis <IntValue>")
    private val highwaytools by setting("HighWayTools", true, { mode == Mode.Reason }, description = "Logs out when HighWayTools are disabled")

    private val disableMode by setting("Disable Mode",  DisableMode.EXCEPT_PLAYER, { mode == Mode.Reconnect})
    private val autoReconnect by setting("Auto Reconnect", true, { disableMode != DisableMode.ALL && mode == Mode.Reconnect})
    private val delay by setting("Reconnect Delay", 10, 1..30, 1, { autoReconnect && disableMode != DisableMode.ALL && mode == Mode.Reconnect}, unit = "m")

    private val send2Discord by setting("Send to Discord", true, { mode == Mode.Discord })
    private val htBehavior by setting("HighWayToolsBehavior", Behavior.OnlyMessage, { highwaytools && send2Discord && ( mode == Mode.Reason || mode == Mode.Discord)}, description = "Behavior when HighWayTools get disabled, OnlyMessage : Only sends Discord Message, Logout : logs out and sends Discord Message")
    private val sendHighwayLog by setting("Send HighWayTools Info", true, { send2Discord && mode == Mode.Discord}, description = "Sends HighWayTools Info/Error Log.")
    private val sendDisconnect by setting("Send Disconnect", true, { send2Discord && mode == Mode.Discord })
    private val sendCoordinate by setting("Send Coordinate", true, { send2Discord && mode == Mode.Discord })
    private val sendCoordinateDelay by setting("Coordinate Delay", 10, 1..120, 5, { send2Discord && sendCoordinate && mode == Mode.Discord }, unit = "m")
    var hookUrl by setting("Hook URL", "", { send2Discord && mode == Mode.Discord }, description = "use ;dicsordhook set <url>")

    private val shittyBlock by setting("Anti URL Leak", true, { mode == Mode.Other }, description = "Shitty message modifier that blocks you to leak Hook URL in chat to prevent hella things.")

    private var prevServerDate: ServerData? = null
    private var coordinate = "null"

    private val coordinateTimer = TickTimer(TimeUnit.MINUTES)

    private enum class Mode {
        Reason, Reconnect, Discord, Other
    }

    @Suppress("Unused")
    private enum class Axis {
        X, Z
    }

    @Suppress("Unused")
    private enum class Behavior {
        OnlyMessage, Logout
    }

    private enum class Reasons {
        HEALTH, PLAYER, FALL, HIGYWAYTOOLS, AXIS
    }

    private enum class DisableMode {
        ALL, EXCEPT_PLAYER, NEVER
    }

    init {
        onEnable {
            coordinateTimer.reset()
            mc.currentServerData?.let { prevServerDate = it }
        }

        listener<ModuleToggleEvent> {
            if (!highwaytools) return@listener
            if (it.module.name != "HighwayTools") return@listener
            if (it.module.isDisabled) return@listener
            runSafe {
                if (htBehavior == Behavior.OnlyMessage && send2Discord) {
                    val response = DMSendHelper.send(SendReason.HighWayTools, logReason = "HighWayTools are disabled(Didn't Logged-out)", hookUrl = hookUrl, username = Companion.mc.session.username, serverAddress = prevServerDate?.serverIP, coordinate = coordinate)
                    if (response != "") { LambdaMod.LOG.error(response) }
                } else{
                    log(Reasons.HIGYWAYTOOLS)
                }
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (isDisabled || it.phase != TickEvent.Phase.END) return@safeListener
            coordinate = "${player.posX.roundToInt()}, ${player.posY.roundToInt()}, ${player.posZ.roundToInt()}"
            if (!sendCoordinate || !send2Discord) return@safeListener
            if (coordinateTimer.tick(sendCoordinateDelay, true)) {
                val response = DMSendHelper.send(SendReason.Coordinate, num = sendCoordinateDelay.toFloat(), hookUrl = hookUrl, username = mc.session.username, serverAddress = prevServerDate?.serverIP, coordinate = coordinate)
                if (response != "") { LambdaMod.LOG.error(response) }
            }
            //val isHWT = BaritoneUtils.primary?.pathingControlManager?.mostRecentInControl()?.orElse(null)?.displayName0()?.lowercase()?.contains("trombone") ?: false
            //Sry ToxicAven but I couldn't use this :(
        }


        safeListener<PacketEvent.Send> {
            if (it.packet !is CPacketChatMessage) return@safeListener
            if (!shittyBlock) return@safeListener
            else {
                val rawMessage = (it.packet as CPacketChatMessage).message
                if (rawMessage.contains("discord.com/api/webhooks")) {
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                    MessageSendHelper.sendErrorMessage("$chatName Cancelled Message because webhook url was detected.")
                    it.cancelled = true
                }
            }
        }

        //AutoReconnect.kt
        listener<GuiEvent.Closed> {
            if (!autoReconnect) return@listener
            if (it.screen is GuiConnecting) prevServerDate = mc.currentServerData
        }

        //AutoReconnect.kt
        listener<GuiEvent.Displayed> {
            if (isDisabled || (prevServerDate == null && mc.currentServerData == null)) return@listener
            (it.screen as? GuiDisconnected)?.let { gui ->
                if (send2Discord && sendDisconnect) {
                    if (!gui.message.unformattedText.startsWith("[AutoLogPlus] ")) {
                        val response = DMSendHelper.send(SendReason.Disconnect, logReason = gui.message.unformattedText, hookUrl = hookUrl, username = mc.session.username, serverAddress = prevServerDate?.serverIP, shouldReconnect = modifiedARData(), coordinate = coordinate)
                        if (response != "") { LambdaMod.LOG.error(response) }
                    }
                }
                coordinate = "null"
                if (autoReconnect) it.screen = LambdaGuiDisconnected(gui)
            }
        }

        //NoFall.kt
        safeListener<TickEvent.ClientTickEvent> {
            if (!falling) return@safeListener
            if (player.isCreative || player.isSpectator || !fallDistChecker()) return@safeListener
            log(Reasons.FALL, num = player.posY.toFloat())
        }

        //AutoLog.kt
        safeListener<TickEvent.ClientTickEvent>(-1000) {
            if (isDisabled || it.phase != TickEvent.Phase.END) return@safeListener
            when {
                health && player.scaledHealth < healthAmount -> log(Reasons.HEALTH, num = player.scaledHealth)
                players && checkPlayers() -> {
                    /* checkPlayer() does log() */
                }
            }
        }
        safeListener<TickEvent.ClientTickEvent> {
            if (isDisabled || it.phase != TickEvent.Phase.END) return@safeListener
            if (!reachAxis) return@safeListener
            if (axisValue.contains(".") || axisValue.toIntOrNull() == null) {
                MessageSendHelper.sendErrorMessage("The Axis Value is not an int. disabling Axis...")
                reachAxis = false
            } else {
                if (axisValue.toInt() == getAxisPos().toInt()) {
                    log(Reasons.AXIS, num = getAxisPos().toFloat())
                }
            }
        }
    }

    private fun SafeClientEvent.getAxisPos() : Double {
        return if (axis == Axis.X) player.posX
        else player.posZ
    }

    //AutoReconnect.kt
    private class LambdaGuiDisconnected(disconnected: GuiDisconnected) : GuiDisconnected(disconnected.parentScreen, disconnected.reason, disconnected.message) {
        private val timer = StopTimer(TimeUnit.SECONDS)

        override fun updateScreen() {
            if (timer.stop() >= (delay.toLong()*60)) {
                if (send2Discord) {
                    val response = DMSendHelper.send(SendReason.AutoReconnect, hookUrl = hookUrl, username = mc.session.username, serverAddress = prevServerDate?.serverIP)
                    if (response != "") { LambdaMod.LOG.error(response) }
                }
                mc.displayGuiScreen(GuiConnecting(parentScreen, mc, mc.currentServerData ?: prevServerDate ?: return))
            }
        }

        override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
            super.drawScreen(mouseX, mouseY, partialTicks)
            val m = max(delay.toLong()*60 - timer.stop(), 0L).toInt()
            val text = if (m > 60) {
                "Reconnecting in ${m / 60}m ${m % 60}s"
            } else {
                "Reconnecting in ${m}s"
            }
            fontRenderer.drawString(text, width / 2f - fontRenderer.getStringWidth(text) / 2f, height - 32f, 0xffffff, true)
        }
    }

    //NoFall.kt
    private fun SafeClientEvent.fallDistChecker() = (player.fallDistance >= distance) || world.getGroundPos(player).y == -69420.0

    private fun SafeClientEvent.checkPlayers(): Boolean {
        val playerList = mutableListOf<EntityPlayer>()
        for (entity in world.loadedEntityList) {
            if (entity !is EntityPlayer) continue
            if (entity.isFakeOrSelf) continue
            if (friends && FriendManager.isFriend(entity.name)) continue
            playerList.add(entity)
        }
        if (playerList.isNotEmpty()) {
            log(Reasons.PLAYER, playerList)
            return true
        }
        return false
    }

    private fun modifiedARData(shouldReconnect : Boolean = true) : Int {
        if (!autoReconnect) return -1
        return if (shouldReconnect) delay
        else -1
    }

    private fun removeColorCode(message: String): String {
        val colorcode = arrayOf("§0","§1","§2","§3","§4","§5","§6","§7","§8","§9","§a","§b","§c","§d","§e","§f","§k","§l","§m","§n","§o","§r")
        var temp = message
        for (i in colorcode) {
            temp = temp.replace(i,"")
        }
        return temp
    }

    @JvmStatic
    fun chatListener(message : ITextComponent) {
        if (!sendHighwayLog || !send2Discord) return
        if (removeColorCode(message.unformattedText).startsWith("[λ] [HighwayTools] [!]")) {
            var log = removeColorCode(message.unformattedText).replaceFirst("[λ] [HighwayTools] [!] ", "")
            if (log.startsWith("\n")) log = log.replaceFirst("\n", "")
            log = "***$log***"
            val response = DMSendHelper.send(SendReason.HighWayTools, logReason = log, hookUrl = hookUrl, username = mc.session.username, serverAddress = prevServerDate?.serverIP, coordinate = coordinate)
            if (response != "") { LambdaMod.LOG.error(response) }
        } else if (removeColorCode(message.unformattedText).startsWith("    > Direction: ")) {
            val log = "***HighWayTools Enabled***\n*" + removeColorCode(message.unformattedText).replaceFirst("    > ", "") + "*"
            val response = DMSendHelper.send(SendReason.HighWayTools, logReason = log, hookUrl = hookUrl, username = mc.session.username, serverAddress = prevServerDate?.serverIP, coordinate = coordinate)
            if (response != "") { LambdaMod.LOG.error(response) }
        }
    }

    //AutoLog.kt
    private fun SafeClientEvent.log(reason: Reasons, additionalInfo: MutableList<EntityPlayer> = mutableListOf(), num: Float = 0.0f) {
        val disconnectMessage = when (reason) {
            Reasons.HEALTH -> "[AutoLogPlus] Logged out due to low health, HP : $num"
            Reasons.PLAYER -> {
                val players = mutableListOf<String>()
                for (player in additionalInfo) { players.add(player.name) }
                if (additionalInfo.size == 1) "[AutoLogPlus] Logged out because there was unknown player : ${players.joinToString(", ")}"
                else "[AutoLogPlus] Logged out because there were unknown players.\n${players.joinToString(", ")}"
            }
            Reasons.FALL -> "[AutoLogPlus] Logged out due to falling detected, PosY : ${num.roundToInt()}"
            Reasons.HIGYWAYTOOLS -> "[AutoLogPlus] Logged out because HighWayTools are disabled."
            Reasons.AXIS -> "[AutoLogPlus] Logged out because player reached ${axis.name} Axis, Pos${axis.name} : ${num.roundToInt()}"
        }
        coordinate = "${player.posX.roundToInt()}, ${player.posY.roundToInt()}, ${player.posZ.roundToInt()}"
        mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
        connection.handleDisconnect(SPacketDisconnect(TextComponentString(disconnectMessage)))
        when (reason) {
            Reasons.HEALTH -> {
                if (send2Discord) {
                    val response = DMSendHelper.send(SendReason.AutoLog, logReason = "Low Health", hookUrl = hookUrl, username = mc.session.username, num = num, serverAddress = prevServerDate?.serverIP, shouldReconnect = modifiedARData(disableMode == DisableMode.NEVER), coordinate = coordinate)
                    if (response != "") { LambdaMod.LOG.error(response) }
                }
                if (disableMode != DisableMode.NEVER) disable()
            }
            Reasons.PLAYER -> {
                if (send2Discord) {
                    val response = DMSendHelper.send(SendReason.AutoLog, logReason = "Unknown Player", hookUrl = hookUrl, username = mc.session.username, players = additionalInfo, serverAddress = prevServerDate?.serverIP, shouldReconnect = modifiedARData(disableMode != DisableMode.ALL), coordinate = coordinate)
                    if (response != "") { LambdaMod.LOG.error(response) }
                }
                if (disableMode == DisableMode.ALL) disable()
            }
            Reasons.FALL -> {
                if (send2Discord) {
                    val response = DMSendHelper.send(SendReason.AutoLog, logReason = "Falling Detected", hookUrl = hookUrl, username = mc.session.username, num = num, serverAddress = prevServerDate?.serverIP, shouldReconnect = modifiedARData(disableMode == DisableMode.NEVER), coordinate = coordinate)
                    if (response != "") { LambdaMod.LOG.error(response) }
                }
                if (disableMode != DisableMode.NEVER) disable()
            }
            Reasons.HIGYWAYTOOLS -> {
                if (send2Discord) {
                    val response = DMSendHelper.send(SendReason.AutoLog, logReason = "HighWayTools Disabled", hookUrl = hookUrl, username = mc.session.username, serverAddress = prevServerDate?.serverIP, shouldReconnect = modifiedARData(disableMode == DisableMode.NEVER), coordinate = coordinate)
                    if (response != "") { LambdaMod.LOG.error(response) }
                }
                if (disableMode != DisableMode.NEVER) disable()
            }
            Reasons.AXIS -> {
                if (send2Discord) {
                    val response = DMSendHelper.send(SendReason.AutoLog, logReason = "Reached ${axis.name} Axis", hookUrl = hookUrl, username = mc.session.username, num = num, serverAddress = prevServerDate?.serverIP, shouldReconnect = modifiedARData(disableMode == DisableMode.NEVER), coordinate = coordinate)
                    if (response != "") { LambdaMod.LOG.error(response) }
                }
                if (disableMode != DisableMode.NEVER) disable()
            }
        }
    }
}