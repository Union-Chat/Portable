package me.devoxin.union.entities

import io.ktor.auth.*
import me.devoxin.union.Database
import me.devoxin.union.enums.Table
import me.devoxin.union.interfaces.ISerializable
import org.json.JSONObject
import java.sql.ResultSet

data class Guild(
    val id: Long,
    val name: String,
    val ownerId: Long
) : ISerializable, Principal {
    override fun save() {
        Database.connection.use {
            it.prepareStatement("UPDATE ${Table.GUILDS.real} SET name = ? WHERE id = ?").apply {
                setString(1, name)
                setLong(2, id)
            }.execute()
        }
    }

    override fun toJson(): JSONObject {
        return JSONObject(
            mapOf(
                "id" to id.toString(),
                "name" to name,
                "owner_id" to ownerId.toString()
            )
        )
    }

    companion object {
        fun from(resultSet: ResultSet): Guild {
            val id = resultSet.getLong("id")
            val name = resultSet.getString("name")
            val ownerId = resultSet.getLong("owner_id")
            return Guild(id, name, ownerId)
        }
    }
}