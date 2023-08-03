package me.devoxin.union.entities

import io.ktor.auth.Principal
import org.json.JSONObject
import me.devoxin.union.Database
import me.devoxin.union.enums.Table
import me.devoxin.union.interfaces.ISerializable
import java.sql.ResultSet

data class User(
     val id: Long,
     val username: String,
     val hashedPassword: String,
     val avatarHash: String,
     val guildIds: MutableSet<Long>
) : ISerializable, Principal {
    val guilds: Set<Guild>
        get() = Database.getGuilds(guildIds)

    override fun save() {
        Database.connection.use {
            it.prepareStatement("UPDATE ${Table.USERS.real} SET avatar_hash = ?, guild_ids = ? WHERE id = ?").apply {
                setString(1, guildIds.joinToString(","))
                setLong(2, id)
            }.execute()
        }
    }

    override fun toJson(): JSONObject {
        return JSONObject(
            mapOf(
                "id" to id.toString(),
                "username" to username,
                "avatar_hash" to avatarHash
            )
        )
    }

    companion object {
        fun from(resultSet: ResultSet): User {
            val id = resultSet.getLong("id")
            val name = resultSet.getString("username")
            val password = resultSet.getString("hashed_password")
            val avatarHash = resultSet.getString("avatar_hash")
            val serverIds = resultSet.getString("guild_ids")
                .split(",")
                .asSequence()
                .filterNot { it.isEmpty() }
                .map { it.toLong() }
                .toMutableSet()

            return User(id, name, password, avatarHash, serverIds)
        }
    }
}
