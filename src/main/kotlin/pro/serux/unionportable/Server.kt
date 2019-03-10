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
import io.ktor.response.respondFile
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import org.json.JSONObject
import java.io.File

class Server {

    private val Database = Database()
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
                    if (!authenticate(call)) {
                        close(CloseReason(4001, "Invalid credentials"))
                        return@webSocket
                    }
                    println("Setting up context")
                    createContext(this)
                }

                get("/") {
                    call.respondFile(File("index.html"))
                    //call.respondText("ur mum gay")
                }

                post("/api/servers/{id}/messages") {
                    val serverId = call.parameters["id"]
                    println(serverId)
                }

                post("/api/create") {
                    val body = call.receiveText()
                    val created = Database.createUser(JSONObject(body))

                    if (created) {
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

    private fun authenticate(call: ApplicationCall): Boolean {
        val header = call.request.header("authorization")
        return header != null && header == "test"
    }

    suspend fun createContext(session: DefaultWebSocketSession) {
        val context = SocketContext(this, session)
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
