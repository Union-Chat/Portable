package me.devoxin.union.entities

import io.ktor.auth.Principal
import org.json.JSONObject
import me.devoxin.union.Database
import me.devoxin.union.interfaces.ISerializable
import java.sql.ResultSet

data class User(
     val id: Long,
     val username: String,
     val password: String,
     val serverIds: MutableSet<Long>
) : ISerializable, Principal {
    val guilds: Set<Guild>
        get() = Database.getGuilds(serverIds)

    override fun save(database: Database) {
        database.connection.use {
            it.prepareStatement("UPDATE users SET server_ids = ? WHERE id = ?").apply {
                setString(1, serverIds.joinToString(","))
                setLong(2, id)
            }.execute()
        }
    }

    override fun toJson(): JSONObject {
        return JSONObject(
            mapOf(
                "id" to id.toString(),
                "username" to username
            )
        )
    }

    companion object {
        fun from(resultSet: ResultSet): User {
            val id = resultSet.getLong("id")
            val name = resultSet.getString("username")
            val password = resultSet.getString("hashed_password")
            val serverIds = resultSet.getString("server_ids")
                .split(",")
                .asSequence()
                .filterNot { it.isEmpty() }
                .map { it.toLong() }
                .toMutableSet()

            return User(id, name, password, serverIds)
        }
    }
}
