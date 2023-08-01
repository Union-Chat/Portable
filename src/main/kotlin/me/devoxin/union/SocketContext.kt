package me.devoxin.union

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.mapNotNull
import org.json.JSONObject
import me.devoxin.union.entities.User

class SocketContext(
    private val server: Server,
    private val socketSession: DefaultWebSocketSession,
    private val userId: Long
) {
     val user: User
        get() = Database.getUser(userId)!!

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun setup() {
        socketSession.outgoing.invokeOnClose {
            server.destroyContext(this)
        }

        startListening()
    }

    private suspend fun close(code: Short, reason: String) {
        socketSession.close(CloseReason(code, reason))
    }

    suspend fun send(obj: JSONObject) {
        socketSession.outgoing.send(Frame.Text(obj.toString()))
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
            "send" -> server.dispatch(json.getString("d"))
        }
    }
}