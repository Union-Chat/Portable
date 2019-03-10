package pro.serux.unionportable

import at.favre.lib.crypto.bcrypt.BCrypt
import com.zaxxer.hikari.HikariDataSource
import org.json.JSONObject
import pro.serux.unionportable.entities.User

class Database {
    private val pool = HikariDataSource()

    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    init {
        if (!pool.isRunning) {
            pool.jdbcUrl = "jdbc:sqlite:union.db"
            initDatabase()
        }

        println(getUser("Devoxin", "0001"))
    }

    public fun initDatabase() {
        pool.connection.use {
            val stmt = it.createStatement()

            stmt.addBatch("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, username TEXT, discriminator TEXT, password TEXT)")
            stmt.addBatch("CREATE TABLE IF NOT EXISTS guilds (id INTEGER PRIMARY KEY, ownerId INTEGER)")

            stmt.executeBatch()
        }
    }

    public fun getUser(username: String, discriminator: String): User? {
        pool.connection.use {
            val stmt = it.prepareStatement("SELECT * FROM users WHERE username = ? AND discriminator = ?")
            stmt.setString(1, username)
            stmt.setString(2, discriminator)

            val result = stmt.executeQuery()

            if (!result.next()) {
                return null
            }

            val id = result.getLong("id")
            val name = result.getString("username")
            val discrim = result.getString("discriminator")
            val password = result.getString("password")

            return User(id, name, discrim, password)
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
}