package pro.serux.unionportable.entities

import org.json.JSONObject
import pro.serux.unionportable.Database
import pro.serux.unionportable.interfaces.ISerializable
import java.sql.ResultSet

data class User(
    public val id: Long,
    public val username: String,
    public val discriminator: String,
    public val password: String,
    public val serverIds: MutableSet<Long>
) : ISerializable {

    override fun save(database: Database) {
        database.connection.use {
            val stmt = it.prepareStatement("UPDATE users SET serverIds = ? WHERE id = ?")
            stmt.setString(1, serverIds.joinToString(","))
            stmt.setLong(2, id)

            stmt.execute()
        }
    }

    override fun toJson(): JSONObject {
        return JSONObject(
            mapOf(
                "id" to id,
                "username" to username,
                "discriminator" to discriminator
            )
        )
    }

    companion object {

        fun from(resultSet: ResultSet): User {
            val id = resultSet.getLong("id")
            val name = resultSet.getString("username")
            val discrim = resultSet.getString("discriminator")
            val password = resultSet.getString("password")
            val serverIds = resultSet.getString("serverIds")
                .split(",")
                .filterNot { it.isEmpty() }
                .map { it.toLong() }
                .toMutableSet()

            return User(id, name, discrim, password, serverIds)
        }

    }

}