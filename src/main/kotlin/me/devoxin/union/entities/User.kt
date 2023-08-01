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

    override fun save(database: Database) {
        database.connection.use {
            val stmt = it.prepareStatement("UPDATE users SET server_ids = ? WHERE id = ?")
            stmt.setString(1, serverIds.joinToString(","))
            stmt.setLong(2, id)

            stmt.execute()
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
            val password = resultSet.getString("password")
            val serverIds = resultSet.getString("serverIds")
                .split(",")
                .filterNot { it.isEmpty() }
                .map { it.toLong() }
                .toMutableSet()

            return User(id, name, password, serverIds)
        }
    }
}
