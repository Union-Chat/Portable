package pro.serux.unionportable

import at.favre.lib.crypto.bcrypt.BCrypt
import com.zaxxer.hikari.HikariDataSource
import org.json.JSONObject
import pro.serux.unionportable.entities.User
import java.sql.Connection
import java.util.*

class Database(private val server: Server) {
    private val pool = HikariDataSource()

    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    public val connection: Connection
        get() = pool.connection

    init {
        if (!pool.isRunning) {
            pool.jdbcUrl = "jdbc:sqlite:union.db"
            initDatabase()
        }

        println(getUser("Devoxin", "0001"))
    }

    public fun initDatabase() {
        connection.use {
            val stmt = it.createStatement()

            stmt.addBatch("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, username TEXT, discriminator TEXT, password TEXT, serverIds TEXT)")
            stmt.addBatch("CREATE TABLE IF NOT EXISTS guilds (id INTEGER PRIMARY KEY, ownerId INTEGER)")

            stmt.executeBatch()
        }
    }

    public fun authenticate(auth: String): User? {
        val parts = auth.split(" ")

        if (parts.size < 2 || parts[0] != "Basic") {
            return null
        }

        val decoded = String(Base64.getDecoder().decode(parts[1])).split(':')

        if (decoded.size != 2) {
            //println(decoded)
            return null // USERNAME:PASSWORD
        }

        val username = decoded[0].split('#')

        if (username.size < 2) {
            //println(username)
            return null // USERNAME#DISCRIM
        }

        if (checkAuthentication(username[0], username[1], decoded[1])) {
            return getUser(username[0], username[1])
        }

        return null
    }

    public fun checkAuthentication(username: String, discriminator: String, password: String): Boolean {
        val user = getUser(username, discriminator) ?: return false
        val result = verifier.verify(password.toCharArray(), user.password.toCharArray())

        println(result.verified)
        return result.verified
    }

    public fun getUser(id: Long): User? {
        connection.use {
            val stmt = it.prepareStatement("SELECT * FROM users WHERE id = ?")
            stmt.setLong(1, id)

            val result = stmt.executeQuery()

            if (!result.next()) {
                return null
            }

            return User.from(result)
        }
    }

    public fun getUser(username: String, discriminator: String): User? {
        connection.use {
            val stmt = it.prepareStatement("SELECT * FROM users WHERE username = ? AND discriminator = ?")
            stmt.setString(1, username)
            stmt.setString(2, discriminator)

            val result = stmt.executeQuery()

            if (!result.next()) {
                return null
            }

            return User.from(result)
        }
    }

    public fun createUser(json: JSONObject): String {
        val username = json.getString("username")
        val password = json.getString("password")
        val encryptedPassword = hasher.hashToString(10, password.toCharArray())

        // TODO: check for existing users.

        pool.connection.use {
            val stmt = it.prepareStatement("INSERT INTO users VALUES (?, ?, ?, ?, ?)")
            stmt.setInt(1, 123456789) // TODO
            stmt.setString(2, username)
            stmt.setString(3, "0001") // TODO
            stmt.setString(4, encryptedPassword)
            stmt.setString(5, "")
            stmt.execute()
        }

        return "$username#0001" // TODO
    }

    public fun addServer(userId: Long, serverId: Long) {
        val u = getUser(userId) ?: return // TODO: Throw
        u.serverIds.add(serverId)
        u.save(this)
    }
}