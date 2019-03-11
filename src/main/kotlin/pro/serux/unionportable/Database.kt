package pro.serux.unionportable

import at.favre.lib.crypto.bcrypt.BCrypt
import com.zaxxer.hikari.HikariDataSource
import org.json.JSONObject
import pro.serux.unionportable.entities.User
import java.sql.Connection

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

    public fun checkAuthentication(username: String, discriminator: String, password: String): Boolean {
        val user = getUser(username, discriminator) ?: return false
        val result = verifier.verify(password.toCharArray(), user.password.toCharArray())

        return result.verified
    }

    public fun createUser(json: JSONObject): Boolean {
        val username = json.getString("username")
        val password = json.getString("password")
        val encryptedPassword = hasher.hashToString(10, password.toCharArray())

        // TODO: Gen ID+Discrim, check for existing users.

        pool.connection.use {
            val stmt = it.prepareStatement("INSERT INTO users VALUES (?, ?, ?, ?)")
            stmt.setInt(1, 123456789)
            stmt.setString(2, username)
            stmt.setString(3, "0001")
            stmt.setString(4, encryptedPassword)
            val succeeded = stmt.execute()
            println(succeeded)
            return@use succeeded
        }

        return false
    }

    public fun addServer(userId: Long, serverId: Long) {
        val u = getUser(userId) ?: return // TODO: Throw
        u.serverIds.add(serverId)
        u.save(this)
    }
}