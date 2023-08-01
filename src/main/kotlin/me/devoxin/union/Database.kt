package me.devoxin.union

import at.favre.lib.crypto.bcrypt.BCrypt
import com.zaxxer.hikari.HikariDataSource
import org.json.JSONObject
import me.devoxin.union.entities.User
import xyz.downgoon.snowflake.Snowflake
import java.lang.IllegalStateException
import java.sql.Connection
import java.util.*

object Database {
    private val pool = HikariDataSource()

    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()
    private val snowflake = Snowflake(0, 0)

    val connection: Connection
        get() = pool.connection

    init {
        if (!pool.isRunning) {
            pool.jdbcUrl = "jdbc:sqlite:union.db"
            initDatabase()
        }
    }

    fun initDatabase() {
        connection.use {
            val stmt = it.createStatement()

            stmt.addBatch("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, username VARCHAR(32), hashed_password TEXT, server_ids TEXT DEFAULT '', UNIQUE(name), check(length(username) <= 32))")
            stmt.addBatch("CREATE TABLE IF NOT EXISTS guilds (id INTEGER PRIMARY KEY, owner_id INTEGER)")
            stmt.addBatch("CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)")


            stmt.executeBatch()
        }
    }

    fun authenticate(auth: String): User? {
        val parts = auth.split(" ")

        if (parts.size < 2 || parts[0] != "Basic") {
            return null
        }

        val decoded = String(Base64.getDecoder().decode(parts[1])).split(':')

        if (decoded.size != 2) {
            return null // USERNAME:PASSWORD
        }

        val username = decoded[0]

        if (checkAuthentication(username, decoded[1])) {
            return getUser(username)
        }

        return null
    }

    fun checkAuthentication(username: String, password: String): Boolean {
        val user = getUser(username) ?: return false
        val result = verifier.verify(password.toCharArray(), user.password.toCharArray())

        return result.verified
    }

    fun getUser(id: Long): User? {
        connection.use {
            val stmt = it.prepareStatement("SELECT id, username, hashed_password, server_ids" +
                    " FROM users WHERE id = ?")
            stmt.setLong(1, id)

            val result = stmt.executeQuery()

            if (!result.next()) {
                return null
            }

            return User.from(result)
        }
    }

    fun getUser(username: String): User? {
        connection.use {
            val stmt = it.prepareStatement("SELECT id, username, hashed_password, server_ids" +
                    " FROM users WHERE username = ?")
            stmt.setString(1, username)

            val result = stmt.executeQuery()

            if (!result.next()) {
                return null
            }

            return User.from(result)
        }
    }

    fun createUser(json: JSONObject): String {
        val username = json.getString("username")
        val hashedPassword = hasher.hashToString(10, json.getString("password").toCharArray())
        val id = generateUniqueUserId()
        // TODO: check for existing users.

        pool.connection.use {
            val stmt = it.prepareStatement("INSERT INTO users(id, username, hashed_password) VALUES (?, ?, ?)")
            stmt.setLong(1, id)
            stmt.setString(2, username)
            stmt.setString(3, hashedPassword)
            stmt.execute()
        }

        return username
    }

    fun createServer(json: JSONObject): Boolean {
        TODO()
    }

    fun addServer(userId: Long, serverId: Long) {
        val u = getUser(userId) ?: throw IllegalStateException("Cannot add non-existent user to server!")

        //validate server exists?
        //possibly optimise by making this one call, also.
        u.serverIds.add(serverId)
        u.save(this)
    }

    private fun generateUniqueUserId(): Long {
        var userId = snowflake.nextId()

        connection.use {
            while (true) {
                val stmt = it.prepareStatement("SELECT (COUNT(id) > 0) FROM users WHERE id = ?")
                stmt.setLong(1, userId)

                val result = stmt.executeQuery()
                val shouldRegenerate = !result.next() || result.getBoolean(1)

                if (!shouldRegenerate) {
                    break
                }

                userId = snowflake.nextId() // so regenerate
            }
        }

        return userId
    }
}