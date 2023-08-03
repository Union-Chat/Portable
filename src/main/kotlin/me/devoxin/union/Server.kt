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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONObject
import me.devoxin.union.entities.User
import me.devoxin.union.enums.OpCode
import me.devoxin.union.enums.Table
import java.io.File
import java.net.URLDecoder
import java.nio.charset.Charset
import java.time.Instant

object Server {
     private val contexts = hashSetOf<SocketContext>()

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

                static("/img") {
                    files("assets/img")
                }

                static("/ugc") {
                    files("assets/ugc")
                }

                webSocket("/gateway") {
                    val user = authenticate(call)
                        ?: return@webSocket close(CloseReason(4001, "Invalid credentials"))

                    println("Creating websocket context for user \"${user.username}\"")
                    createContext(this, user)
                }

                get("/") {
                    call.respondFile(File("index.html"))
                }

                post("/api/create") {
                    val body = call.receiveJson() ?: return@post

                    if (!body.has("username") || !body.has("password")) {
                        return@post call.respondError(HttpStatusCode.BadRequest, "Missing 'username' and/or 'password' fields.")
                    }

                    if (body.getString("username").isEmpty() || body.getString("password").isEmpty()) {
                        return@post call.respondError(HttpStatusCode.BadRequest, "'Username' and/or 'password' must not be empty!")
                    }

                    try {
                        val user = Database.createUser(body)
                        call.respondText(user)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, e.localizedMessage)
                    }
                }

                authenticate {
                    post("/api/invites/{code}") {
                        val code = call.parameters["code"]
                            ?: return@post call.respondError(HttpStatusCode.BadRequest, "code must be specified")

                        val acceptor = call.principal<User>()!!

                        val guild = Database.getGuildByInvite(code)
                            ?: return@post call.respondError(HttpStatusCode.NotFound, "invalid code")

                        if (guild.id in acceptor.guildIds) {
                            return@post call.respondError(HttpStatusCode.BadRequest, "you are already in this server")
                        }

                        acceptor.guildIds.add(guild.id)
                        acceptor.save()

                        dispatchToUser(acceptor.id, OpCode.SERVER_JOIN, guild.toJson())
                    }

                    post("/api/server") {
                        val payload = call.receiveJson() ?: return@post
                        val owner = call.principal<User>()!!

                        val guildInfo = Database.createGuild(owner.id, payload)
                            ?: return@post call.respondError(HttpStatusCode.BadRequest, "server not found")

                        dispatchToUser(owner.id, OpCode.SERVER_JOIN, guildInfo.toJson())
                        // Perhaps we should respond with the guild info here...?
                        call.respond(HttpStatusCode.NoContent)
                    }

                    post("/api/server/{id}/invites") {
                        val guildId = call.parameters["id"]?.toLongOrNull()
                            ?: return@post call.respondError(HttpStatusCode.BadRequest, "id must be a snowflake")

                        val creator = call.principal<User>()!!

                        if (!Database.exists(Table.GUILDS, guildId)) {
                            return@post call.respondError(HttpStatusCode.NotFound, "server not found")
                        }

                        val inviteCode = Database.createGuildInvite(guildId, creator.id)
                        call.respond(inviteCode)
                    }

                    post("/api/server/{id}/messages") {
                        val guildId = call.parameters["id"]?.toLongOrNull()
                            ?: return@post call.respondError(HttpStatusCode.BadRequest, "id must be a snowflake")

                        if (!Database.exists(Table.GUILDS, guildId)) {
                            return@post call.respondError(HttpStatusCode.NotFound, "server not found")
                        }

                        val author = call.principal<User>()!!

                        if (!author.guildIds.contains(guildId)) {
                            return@post call.respondError(HttpStatusCode.Forbidden, "You do not have access to the requested server.")
                        }

                        val payload = call.receiveJson() ?: return@post

                        val message = JSONObject(
                            mapOf(
                                "content" to payload.getString("content"),
                                "server" to call.parameters["id"],
                                "author" to author.toJson(),
                                "createdAt" to Instant.now().toEpochMilli()
                            )
                        )

                        for (context in contexts) {
                            if (context.user.guildIds.contains(guildId)) {
                                context.sendJson(
                                    "op" to OpCode.MESSAGE.ordinal,
                                    "d" to message
                                )
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
            Database.authenticate(header, skipTypeCheck = call.request.header("authorization") == null)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun createContext(session: DefaultWebSocketSession, user: User) {
        val context = SocketContext(this, session, user.id)
        session.outgoing.invokeOnClose { destroyContext(context) }

        contexts.add(context)
        context.setup()
    }

    private fun destroyContext(context: SocketContext) {
        println("Destroying socket context")
        contexts.remove(context)
    }

    suspend fun dispatchToUser(userId: Long, op: OpCode, data: Any) {
        contexts.find { it.userId == userId }?.sendJson(
            "op" to op.ordinal,
            "d" to data
        )
    }

    suspend fun dispatch(clients: Set<SocketContext>, op: OpCode, data: Any) {
        clients.forEach {
            it.sendJson(
                "op" to op.ordinal,
                "d" to data
            )
        }
    }

    suspend fun broadcast(op: OpCode, data: Any) {
        dispatch(contexts, op, data)
    }
}
