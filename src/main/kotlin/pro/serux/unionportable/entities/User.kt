package pro.serux.unionportable.entities

import org.json.JSONObject
import pro.serux.unionportable.interfaces.ISerializable

data class User(
    public val username: String,
    public val discriminator: Short,
    public val password: String
) : ISerializable {

    override fun toJson(): JSONObject {
        return JSONObject(mapOf(
            "username" to username,
            "discriminator" to discriminator
        ))
    }

}