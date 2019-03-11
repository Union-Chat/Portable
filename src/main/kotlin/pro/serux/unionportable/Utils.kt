package pro.serux.unionportable

import org.json.JSONException
import org.json.JSONObject


/**
 * Attempts to convert a string into a JSON object, or returns null if an exception occurred
 * while parsing.
 */
fun parseJson(data: String): JSONObject? {
    return try {
        JSONObject(data)
    } catch (e: JSONException) {
        null
    }
}