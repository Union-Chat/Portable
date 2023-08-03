package me.devoxin.union

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.consumeEach
import org.json.JSONObject
import me.devoxin.union.entities.User
import me.devoxin.union.enums.OpCode
import org.json.JSONArray

class SocketContext(
    private val server: Server,
    private val socketSession: DefaultWebSocketSession,
    val userId: Long
) {
     val user: User
        get() = Database.getUser(userId)!!

    suspend fun setup() {
        val guildJson = JSONArray()

        for (guild in user.guilds) {
            guildJson.put(guild.toJson())
        }

        sendJson(
            "op" to OpCode.HELLO.ordinal,
            "d" to mapOf(
                "user" to user.toJson(),
                "servers" to guildJson
            )
        )
        startListening()
    }

    private suspend fun close(code: Short, reason: String) {
        socketSession.close(CloseReason(code, reason))
    }

    suspend fun send(obj: JSONObject) {
        socketSession.outgoing.send(Frame.Text(obj.toString()))
    }

    suspend fun sendJson(vararg data: Pair<String, Any>) {
        val serialized = JSONObject(mapOf(*data))
        send(serialized)
    }

    private suspend fun startListening() {
        socketSession.incoming.consumeEach {
            if (it !is Frame.Text || !it.fin) {
                return@consumeEach
            }

            handleMessage(it.readText())
        }
    }

    private suspend fun handleMessage(d: String) {
        val json = parseJson(d)
            ?: return close(4002, "Invalid payload; not a valid JSON object.")

        if (!json.has("op")) {
            return close(4002, "Invalid payload; missing op key.")
        }

        when (json.getString("op")) {
            //"send" -> server.dispatch(json.getString("d"))
        }
    }
}
