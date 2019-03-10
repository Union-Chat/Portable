package pro.serux.unionportable

import at.favre.lib.crypto.bcrypt.BCrypt
import com.zaxxer.hikari.HikariDataSource
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
    }

    public fun initDatabase() {
        pool.connection.use {
            val stmt = it.createStatement()

            stmt.addBatch("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, username TEXT, discriminator INTEGER, password TEXT)")
            stmt.addBatch("CREATE TABLE IF NOT EXISTS guilds (id INTEGER PRIMARY KEY, ownerId INTEGER)")

            stmt.executeBatch()
        }
    }

    public fun getUser(username: String, discriminator: String): User? {
        pool.connection.use {
            val stmt = it.prepareStatement("SELECT * FROM users WHERE username = ? AND discriminator = ?")
            stmt.setString(1, username)
            stmt.setShort(2, discriminator.toShort())

            val result = stmt.executeQuery()

            if (!result.next()) {
                return null
            }

            val name = result.getString("username")
            val discrim = result.getShort("discriminator")
            val password = result.getString("password")

            return User(name, discrim, password)
        }
    }

    public fun checkAuthentication(username: String, discriminator: String, password: String): Boolean {
        val user = getUser(username, discriminator) ?: return false
        val result = verifier.verify(password.toCharArray(), user.password.toCharArray())

        return result.verified
    }
}