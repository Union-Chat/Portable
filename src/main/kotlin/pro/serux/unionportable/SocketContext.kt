package pro.serux.unionportable

import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.mapNotNull
import org.json.JSONObject
import pro.serux.unionportable.entities.User

class SocketContext(
    private val server: Server,
    private val socketSession: DefaultWebSocketSession,
    private val userId: Long
) {

    public val user: User
        get() = server.Database.getUser(userId)!!

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
        socketSession.outgoing.send(
            Frame.Text(obj.toString())
        )
    }

    private suspend fun startListening() {
        socketSession.incoming.mapNotNull { it as? Frame.Text }.consumeEach {
            //handleMessage(it.readText())
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