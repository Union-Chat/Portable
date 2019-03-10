package pro.serux.unionportable

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.mapNotNull
import org.json.JSONObject

class SocketContext(
    private val server: Server,
    private val socketSession: DefaultWebSocketSession
) {

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
            handleMessage(it.readText())
        }
    }

    private suspend fun handleMessage(d: String) {
        val json = parseJson(d)
            ?: return close(4002, "Invalid payload; not a valid JSON object.")

        if (!json.has("op")) {
            return close(4002, "Invalid payload; missing op key.")
        }

        val op = json.getString("op")

        when (op) {
            "send" -> server.dispatch(json.getString("d"))
        }
    }

}