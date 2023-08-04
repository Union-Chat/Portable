package me.devoxin.union

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.future.await
import org.json.JSONObject
import me.devoxin.union.entities.User
import me.devoxin.union.enums.OpCode
import org.json.JSONArray
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class SocketContext(
    private val server: Server,
    private val socketSession: DefaultWebSocketSession,
    val userId: Long
) {
     val user: User
        get() = Database.getUser(userId)!!

    private var lastHeartbeat = Instant.now().toEpochMilli()
    private lateinit var heartbeatTask: Job
    private var heartbeatWaiter: CompletableFuture<Long>? = null

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

        heartbeatTask = coroutineScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL)

                val waiter = CompletableFuture<Long>().also { heartbeatWaiter = it }
                val ackTime = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(10)) {
                    heartbeat()
                    waiter.await()
                }

                if (ackTime == null) {
                    close(4000, "Heartbeat missed")
                    break
                }

                lastHeartbeat = ackTime
            }
        }

        startListening()
    }

    private suspend fun heartbeat() {
        sendJson("op" to OpCode.HEARTBEAT.ordinal)
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
            ?: return close(4001, "Invalid payload; not a valid JSON object.")

        if (!json.has("op")) {
            return close(4001, "Invalid payload; missing op key.")
        }

        when (json.getInt("op")) {
            OpCode.HEARTBEAT_ACK.ordinal -> heartbeatWaiter?.complete(Instant.now().toEpochMilli())
            //"send" -> server.dispatch(json.getString("d"))
        }
    }

    companion object {
        private val coroutineScope = CoroutineScope(Dispatchers.Default)

        private val HEARTBEAT_INTERVAL = TimeUnit.SECONDS.toMillis(30)
    }
}
