package pro.serux.unionportable.interfaces

import org.json.JSONObject
import pro.serux.unionportable.Database

interface ISerializable {

    fun save(database: Database)

    fun toJson(): JSONObject

}