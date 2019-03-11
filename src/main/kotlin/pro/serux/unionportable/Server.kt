package pro.serux.unionportable

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.request.header
import io.ktor.request.receiveText
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.consumes
import kotlinx.coroutines.channels.mapNotNull
import org.json.JSONObject
import pro.serux.unionportable.entities.User
import java.io.File
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.CompletableFuture

class Server {

    private val Database = Database(this)
    public val contexts = hashSetOf<SocketContext>()

    fun start() {
        embeddedServer(Netty, 6969) {
            install(WebSockets)

            routing {
                static("/assets") {
                    files("assets")
                }

                webSocket("/gateway") {
                    println("Incoming connection")
                    val user = authenticate(call)

                    if (user == null) {
                        println("Invalid authentication.")
                        close(CloseReason(4001, "Invalid credentials"))
                        return@webSocket
                    }
                    println("Setting up context")
                    createContext(this, user)
                }

                get("/") {
                    call.respondFile(File("index.html"))
                }

                post("/api/servers/{id}/messages") {
                    val serverId = call.parameters["id"]
                    println(serverId)
                }

                post("/api/create") {
                    val body = call.receiveText()

                    if (Database.createUser(JSONObject(body))) {
                        call.respondText("Devoxin#0001")
                    } else {
                        call.respondText(JSONObject(mapOf(
                            "error" to "GAY"
                        )).toString(), status = HttpStatusCode.BadRequest)
                    }
                }
            }
        }.start(wait = true)
    }

    private fun authenticate(call: ApplicationCall): User? {
        val header = call.request.header("authorization") ?: return null
        return authenticate(header)
    }

    private fun authenticate(auth: String): User? {
        val decoded = String(Base64.getDecoder().decode(auth)).split(':')

        if (decoded.size != 2) {
            println(decoded)
            return null // USERNAME:PASSWORD
        }

        val username = decoded[0].split('#')

        if (username.size < 2) {
            println(username)
            return null // USERNAME#DISCRIM
        }

        if (Database.checkAuthentication(username[0], username[1], decoded[1])) {
            return Database.getUser(username[0], username[1])
        }

        println("auth failed yuh")

        return null
    }

    suspend fun createContext(session: DefaultWebSocketSession, user: User) {
        val context = SocketContext(this, session, user)
        contexts.add(context)
        context.setup()
    }

    fun destroyContext(context: SocketContext) {
        println("Destroying socket context")
        contexts.remove(context)
    }

    suspend fun dispatch(data: String) {
        contexts.forEach {
            it.send(JSONObject(mapOf(
                "op" to "broadcast",
                "d" to data
            )))
        }
    }

}
