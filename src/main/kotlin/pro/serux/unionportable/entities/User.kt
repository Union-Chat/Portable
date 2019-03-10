package pro.serux.unionportable.entities

import org.json.JSONObject
import pro.serux.unionportable.interfaces.ISerializable

data class User(
    public val id: Long,
    public val username: String,
    public val discriminator: String,
    public val password: String
) : ISerializable {

    override fun toJson(): JSONObject {
        return JSONObject(mapOf(
            "id" to id,
            "username" to username,
            "discriminator" to discriminator
        ))
    }

}