package me.devoxin.union

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.basic
import io.ktor.auth.principal
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.close
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.request.header
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
import org.json.JSONObject
import me.devoxin.union.entities.User
import java.io.File
import java.net.URLDecoder
import java.nio.charset.Charset

object Server {
     val contexts = hashSetOf<SocketContext>()

    fun start() {
        embeddedServer(Netty, 6969) {
            install(StatusPages) {
                exception<Throwable> {
                    it.printStackTrace()
                }
            }
            install(WebSockets)
            install(Authentication) {
                basic {
                    validate { credentials ->
                        val username = credentials.name

                        if (!Database.checkAuthentication(username, credentials.password)) {
                            return@validate null
                        }

                        return@validate Database.getUser(username)
                    }
                }
            }
            intercept(ApplicationCallPipeline.Call) {
                call.request.header("sec-websocket-protocol")?.let {
                    call.response.header("sec-websocket-protocol", it)
                }

                this.proceed()
            }

            routing {
                static("/assets") {
                    files("assets")
                }

                webSocket("/gateway") {
                    println("Incoming connection")
                    val user = authenticate(call)
                        ?: return@webSocket close(CloseReason(4001, "Invalid credentials"))

                    println("Creating websocket context for ${user.username}")
                    createContext(this, user)
                }

                get("/") {
                    call.respondFile(File("index.html"))
                }

                post("/api/create") {
                    val body = call.receiveJson() ?: return@post

                    try {
                        val user = Database.createUser(JSONObject(body))
                        call.respondText(user)
                    } catch (e: Exception) {
                        call.respondError(500, e.localizedMessage)
                    }
                }

                authenticate {
                    post("/api/server/{id}/messages") {
                        val serverId = call.parameters["id"]?.toLongOrNull()
                            ?: return@post call.respondError(400, "id must be an integer")

                        val author = call.principal<User>()!!

                        if (!author.serverIds.contains(serverId)) {
                            return@post call.respondError(403, "You do not have access to the requested server.")
                        }

                        val payload = call.receiveJson() ?: return@post

                        val message = JSONObject(
                            mapOf(
                                "server" to call.parameters["id"],
                                "author" to author.toJson(),
                                "content" to payload.getString("content")
                            )
                        )

                        for (context in contexts) {
                            if (context.user.serverIds.contains(serverId)) {
                                context.send(message)
                            }
                        }

                        call.respond(HttpStatusCode.NoContent)
                        //println("${author.username}#${author.discriminator} -> $serverId: ${payload.getString("content")}")
                    }
                }
            }
        }.start(wait = true)
    }

    private fun authenticate(call: ApplicationCall): User? {
        val header = call.request.header("authorization")
            ?: URLDecoder.decode(call.request.header("sec-websocket-protocol") ?: "", Charset.defaultCharset())
            ?: return null

        return try {
            Database.authenticate(header)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createContext(session: DefaultWebSocketSession, user: User) {
        val context = SocketContext(this, session, user.id)
        contexts.add(context)
        context.setup()
    }

    fun destroyContext(context: SocketContext) {
        println("Destroying socket context")
        contexts.remove(context)
    }

    suspend fun dispatch(data: String) {
        contexts.forEach {
            it.send(
                JSONObject(
                    mapOf(
                        "op" to "broadcast",
                        "d" to data
                    )
                )
            )
        }
    }

}
