package pro.serux.unionportable.interfaces

import org.json.JSONObject

interface ISerializable {

    fun toJson(): JSONObject

}