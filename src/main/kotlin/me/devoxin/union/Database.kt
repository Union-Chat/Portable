package me.devoxin.union

import at.favre.lib.crypto.bcrypt.BCrypt
import com.zaxxer.hikari.HikariDataSource
import me.devoxin.union.entities.Guild
import org.json.JSONObject
import me.devoxin.union.entities.User
import me.devoxin.union.enums.Table
import xyz.downgoon.snowflake.Snowflake
import java.lang.IllegalStateException
import java.sql.Connection
import java.sql.ResultSet
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

    private fun initDatabase() {
        connection.use {
            it.createStatement().apply {
                addBatch("PRAGMA foreign_keys = ON")

                addBatch("""
                    CREATE TABLE IF NOT EXISTS ${Table.USERS.real} (
                        id INTEGER PRIMARY KEY,
                        username VARCHAR(32),
                        hashed_password TEXT,
                        guild_ids TEXT DEFAULT '',
                        UNIQUE(username),
                        check(length(username) <= 32)
                    )""".trimIndent())
                addBatch("""
                    CREATE TABLE IF NOT EXISTS ${Table.GUILDS.real} (
                        id INTEGER PRIMARY KEY,
                        name VARCHAR(32),
                        owner_id INTEGER,
                        UNIQUE(name, owner_id),
                        check(length(name) <= 32)
                    )""")
                addBatch("""
                    CREATE TABLE IF NOT EXISTS ${Table.GUILD_INVITES.real} (
                        id VARCHAR(7) PRIMARY KEY,
                        guild_id INTEGER,
                        inviter INTEGER,
                        FOREIGN KEY(guild_id) REFERENCES ${Table.GUILDS.real}(id) ON DELETE CASCADE,
                        FOREIGN KEY(inviter) REFERENCES ${Table.USERS.real}(id) ON DELETE CASCADE,
                        check(length(id) >= 1 and length(id) <= 7)
                    )""")
                addBatch("CREATE INDEX IF NOT EXISTS idx_${Table.USERS.real}_username ON ${Table.USERS.real}(username)")
                addBatch("CREATE INDEX IF NOT EXISTS idx_${Table.GUILDS.real}_name ON ${Table.GUILDS.real}(name)")
                addBatch("CREATE INDEX IF NOT EXISTS idx_${Table.GUILDS.real}_ownerid ON ${Table.GUILDS.real}(owner_id)")
                addBatch("CREATE INDEX IF NOT EXISTS idx_${Table.GUILD_INVITES.real}_guildid ON ${Table.GUILD_INVITES.real}(guild_id)")
            }.executeBatch()
        }
    }

    fun exists(table: Table, entityId: Any): Boolean {
        return connection.use {
            it.prepareStatement("SELECT (COUNT(id) > 0) FROM ${table.real} WHERE id = ?").apply {
                setObject(1, entityId)
            }.executeQuery().let { rs -> rs.next() && rs.getBoolean(1) }
        }
    }

    fun authenticate(encoded: String, skipTypeCheck: Boolean = false): User? {
        val parts = encoded.split(" ")

        if (!skipTypeCheck && (parts.size < 2 || parts[0] != "Basic")) {
            return null
        }

        val auth = parts.lastOrNull() ?: return null
        val decoded = String(Base64.getDecoder().decode(auth)).split(':')

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
            val result = it.prepareStatement("SELECT id, username, hashed_password, guild_ids FROM ${Table.USERS.real} WHERE id = ?").apply {
                setLong(1, id)
            }.executeQuery()

            if (!result.next()) {
                return null
            }

            return User.from(result)
        }
    }

    fun getUser(username: String): User? {
        connection.use {
            val result = it.prepareStatement("SELECT id, username, hashed_password, guild_ids FROM ${Table.USERS.real} WHERE username = ?").apply {
                setString(1, username)
            }.executeQuery()

            if (!result.next()) {
                return null
            }

            return User.from(result)
        }
    }

    fun getGuild(id: Long): Guild? {
        connection.use {
            val result = it.prepareStatement("SELECT id, name, owner_id FROM ${Table.GUILDS.real} WHERE id = ?").apply {
                setLong(1, id)
            }.executeQuery()

            if (!result.next()) {
                return null
            }

            return Guild.from(result)
        }
    }

    fun getGuildByInvite(code: String): Guild? {
        val guildId = connection.use {
            val result = it.prepareStatement("SELECT guild_id FROM ${Table.GUILD_INVITES.real} WHERE id = ?").apply {
                setString(1, code)
            }.executeQuery()

            return@use result.takeIf(ResultSet::next)?.getLong("guild_id")
        } ?: return null

        connection.use {
            val result = it.prepareStatement("SELECT id, name, owner_id FROM ${Table.GUILDS.real} WHERE id = ?").apply {
                setLong(1, guildId)
            }.executeQuery()

            return result.takeIf(ResultSet::next)?.let(Guild::from)
        }
    }

    fun getGuilds(guildIds: Set<Long>): Set<Guild> {
        val guilds = hashSetOf<Guild>()

        for (id in guildIds) {
            // TODO: Could probably make this one call
            connection.use {
                val result = it.prepareStatement("SELECT id, name, owner_id FROM ${Table.GUILDS.real} WHERE id = ?").apply {
                    setLong(1, id)
                }.executeQuery()

                while (result.next()) {
                    guilds.add(Guild.from(result))
                }
            }
        }

        return guilds
    }

    fun createUser(json: JSONObject): String {
        val username = json.getString("username")
        val hashedPassword = hasher.hashToString(10, json.getString("password").toCharArray())

        val id = generateUniqueId(Table.USERS)
        // TODO: check for existing users.

        connection.use {
            it.prepareStatement("INSERT INTO ${Table.USERS.real}(id, username, hashed_password) VALUES (?, ?, ?)").apply {
                setLong(1, id)
                setString(2, username)
                setString(3, hashedPassword)
            }.execute()
        }

        return username
    }

    fun createGuild(ownerId: Long, json: JSONObject): Guild? {
        val name = json.getString("name")
        val id = generateUniqueId(Table.GUILDS)

        connection.use {
            it.prepareStatement("INSERT INTO ${Table.GUILDS.real}(id, name, owner_id) VALUES (?, ?, ?)").apply {
                setLong(1, id)
                setString(2, name)
                setLong(3, ownerId)
            }.execute()
        }

        return addGuild(ownerId, id)
    }

    fun createGuildInvite(guildId: Long, creatorId: Long): String {
        var code: String

        do {
            code = generateArbitrary(7)
        } while (exists(Table.GUILD_INVITES, code))

        connection.use {
            it.prepareStatement("INSERT INTO ${Table.GUILD_INVITES.real}(id, guild_id, inviter) VALUES (?, ?, ?)").apply {
                setString(1, code)
                setLong(2, guildId)
                setLong(3, creatorId)
            }.execute()
        }

        return code
    }

    fun addGuild(userId: Long, guildId: Long): Guild {
        val u = getUser(userId) ?: throw IllegalStateException("Cannot add non-existent user to guild!")
        val g = getGuild(guildId) ?: throw IllegalStateException("Cannot add user to non-existent guild!")

        //TODO: possibly optimise by making this one call, also.
        u.guildIds.add(guildId)
        u.save()

        return g
    }

    private fun generateUniqueId(table: Table): Long {
        var userId: Long

        do {
            userId = snowflake.nextId()
        } while (exists(table, userId))

        return userId
    }
}