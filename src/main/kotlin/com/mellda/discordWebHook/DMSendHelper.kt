package com.mellda.discordWebHook

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.lambda.client.commons.utils.ConnectionUtils
import com.lambda.client.util.EnchantmentUtils
import com.lambda.client.util.items.originalName
import com.lambda.client.util.threads.defaultScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemAir
import net.minecraft.item.ItemStack
import org.apache.commons.io.IOUtils
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.roundToInt


object DMSendHelper {
    //Original Method by DiscordIntegrationModule.kt
    fun send(sendReason: SendReason,
             logReason: String = "",
             hookUrl: String,
             username: String,
             num: Float = 0.0f,
             players: MutableList<EntityPlayer> = mutableListOf(),
             serverAddress: String? = "null",
             shouldReconnect : Int = -1,
             coordinate : String = ""): String {
        if (hookUrl.trim() == "") return "WebHook URL is not defined."
        else {
            var response = ""
            val embedArray = JsonArray()
            val embedFieldArray = JsonArray()
            val embedObject = JsonObject()
            val embedFieldObject = JsonObject()
            val embedFooterObject = JsonObject()
            val embedThumbnailObject = JsonObject()
            embedThumbnailObject.addProperty("url", "https://mc-heads.net/avatar/$username")
            when (sendReason) {
                SendReason.AutoLog -> embedFieldObject.run {
                    addProperty("name", "Logged out due to following reason : $logReason")
                    addProperty("value", getValue(logReason, num, players, serverAddress, shouldReconnect, coordinate))
                }
                SendReason.AutoReconnect -> embedFieldObject.run {
                    addProperty("name", "Reconnecting to the Server")
                    addProperty("value", "Server Address : $serverAddress")
                }
                SendReason.Disconnect -> embedFieldObject.run {
                    addProperty("name", "Disconnected from the Server")
                    addProperty("value", getDisconnectValue(logReason, serverAddress, shouldReconnect, coordinate))
                }
                SendReason.HighWayTools -> embedFieldObject.run {
                    addProperty("name", "[λ] [HighwayTools]")
                    addProperty("value", getHTValue(logReason, serverAddress, coordinate))
                }
                SendReason.Coordinate -> embedFieldObject.run {
                    addProperty("name", "$username's Current Coordinate")
                    addProperty("value", "**Coordinate** : $coordinate\n\nCoordinate Delay : **${num.roundToInt()}m**\nServer Address : $serverAddress")
                }
            }
            embedFieldArray.add(embedFieldObject)
            embedFooterObject.addProperty("text", "Made By Mell_Da, 2K2R ON TOP!")
            embedObject.run {
                addProperty("title", "AutoLogPlus")
                addProperty("color", getColor(sendReason))
                add("fields", embedFieldArray)
                add("footer", embedFooterObject)
                addProperty("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                add("thumbnail", embedThumbnailObject)
            }
            embedArray.add(embedObject)
            if (sendReason == SendReason.AutoLog && logReason == "Unknown Player") {
                for (player in players.slice(IntRange(0, min(8, players.size-1)))) {
                    val playerThumbnailObject = JsonObject()
                    playerThumbnailObject.addProperty("url", "https://mc-heads.net/avatar/${player.name}")
                    val playerJsonObject = JsonObject()
                    playerJsonObject.run{
                        addProperty("title", player.name)
                        addProperty("description", getPlayerData(player))
                        addProperty("color", 6946955)
                        add("footer", embedFooterObject)
                        addProperty("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                        add("thumbnail", playerThumbnailObject)
                    }
                    embedArray.add(playerJsonObject)
                }
            }
            defaultScope.launch(Dispatchers.IO) {
                ConnectionUtils.runConnection(
                    hookUrl,
                    { connection ->
                        val bytes =
                            JsonObject().run {
                                addProperty("username", username)
                                addProperty("avatar_url", "https://mc-heads.net/avatar/$username")
                                add("content", JsonNull.INSTANCE)
                                add("embeds", embedArray)
                                toString().toByteArray(Charsets.UTF_8)
                            }
                        connection.setRequestProperty(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                        )
                        connection.setRequestProperty("Accept", "application/json")
                        connection.setRequestProperty("User-Agent", "")

                        connection.requestMethod = "POST"
                        connection.outputStream.use { it.write(bytes) }

                        response = connection.inputStream.use { IOUtils.toString(it, Charsets.UTF_8) }
                    }
                )
            }
            if (response.isNotEmpty()) {
                return response
            }
            return ""
        }
    }

    private fun getColor(reason: SendReason) : Int {
        return when (reason) {
            SendReason.AutoReconnect -> {
                6029106
            }
            SendReason.HighWayTools -> {
                4718488
            }
            SendReason.Coordinate -> {
                6029106
            }
            else -> {
                16724530
            }
        }
    }

    private fun getHTValue(logReason: String, serverAddress: String?, coordinate: String) : String{
        return "$logReason\n\n**Coordinate : **$coordinate\nServer Address : $serverAddress"
    }

    private fun getDisconnectValue(logReason: String, serverAddress: String?, shouldReconnect: Int, coordinate: String) : String {
        return "***$logReason***\n**Coordinate : **$coordinate\nServer Address : $serverAddress" + getARData(shouldReconnect)
    }

    private fun getValue(logReason: String, num: Float, players: MutableList<EntityPlayer>, serverAddress: String?, shouldReconnect: Int, coordinate: String) : String {
        val value = when (logReason) {
            "Low Health" -> "HP : $num"
            "Unknown Player" -> {
                if (players.size > 10) {
                    val ignoredPlayer = mutableListOf<String>()
                    for (player in players.slice(IntRange(9,players.size-1))) {
                        ignoredPlayer.add(player.name)
                    }
                    ignoredPlayer.joinToString(", ")
                    "Sending Player List, Unfortunately, there are more than 9 players so some of the player will be not sent due to embed limit.\nIgnored Players : $ignoredPlayer\n"
                } else {
                    "Sending Player List...\n"
                }
            }
            "Falling Detected" -> "PosY : $num"
            "HighWayTools Disabled" -> ""
            "Reached X Axis" -> "PosX : $num"
            "Reached Z Axis" -> "PosZ : $num"
            else -> "Unknown Reason"
        }
        return "**Coordinate : **$coordinate\n" + value + getARData(shouldReconnect) + "\nServer Address : $serverAddress"
    }

    private fun getARData(time : Int) : String {
        return if (time == -1) "\nAutoReconnect : **Disabled**"
        else "\nAutoReconnect : **${time}m**"
    }

    private fun getPlayerData(player: EntityPlayer) : String {
        var value = ""
        value += "**Health :** ${player.health}"
        value += "\n\n**Armor :**${getArmor(player)}"
        value+= "\n\n**Coordiante :** ${player.posX.roundToInt()}, ${player.posY.roundToInt()}, ${player.posZ.roundToInt()}\n"
        return value.substring(0, min(value.length, 1024))
    }

    private fun getArmor(player: EntityPlayer) : String {
        val temp = mutableListOf<String>()
        for (armor in player.armorInventoryList.reversed()) {
            if (armor.item !is ItemAir) {
                temp.add("***${armor.originalName}*** (${getEnchantmentText(armor)})")
            }
        }
        return if (temp.isEmpty()) " None"
        else "\n" + temp.joinToString("\n")
    }

    private fun getEnchantmentText(itemStack: ItemStack): String {
        val temp = mutableListOf<String>()
        val enchantmentList = EnchantmentUtils.getAllEnchantments(itemStack)
        for (leveledEnchantment in enchantmentList) {
            temp.add("*${leveledEnchantment.alias}* : ${leveledEnchantment.levelText}")
        }
        return if (temp.isEmpty()) ""
        else temp.joinToString(" / ")
    }
}