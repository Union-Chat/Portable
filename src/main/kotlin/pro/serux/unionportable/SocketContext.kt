package pro.serux.unionportable

import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.mapNotNull
import kotlinx.coroutines.isActive
import org.json.JSONObject

class SocketContext(
    private val server: Server,
    private val socketSession: DefaultWebSocketSession
) {

    public var isOpen = true
        private set

    suspend fun setup() {
        socketSession.outgoing.invokeOnClose {
            isOpen = false
            server.destroyContext(this)
        }

        startListening()
    }

    suspend fun send(obj: JSONObject) {
        send(obj.toString())
    }

    suspend fun send(data: String) {
        socketSession.outgoing.send(Frame.Text(data))
    }

    private suspend fun startListening() {
        socketSession.incoming.mapNotNull { it as? Frame.Text }.consumeEach {
            handleMessage(it.readText())
        }
    }

    private suspend fun handleMessage(d: String) {
        println(d)
        val j = JSONObject(d)

        if (!j.has("op")) {
            println("Invalid payload - No op key")
            return
        }

        val op = j.getString("op")

        if (op == "fuck") {
            server.dispatch(j.getString("d"))
        }
    }

}