package me.devoxin.union.interfaces

import org.json.JSONObject
import me.devoxin.union.Database

interface ISerializable {

    fun save()

    fun toJson(): JSONObject

}