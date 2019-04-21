package pro.serux.unionportable

import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.header
import io.ktor.response.respond
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

suspend fun ApplicationCall.respondError(code: Int, message: String) {
    val errorResponse = JSONObject(
        mapOf(
            "error" to message
        )
    )

    this.response.header("Content-Type", "application/json")
    this.respond(HttpStatusCode.fromValue(code), errorResponse.toString())
}

suspend fun ApplicationCall.receiveJson(): JSONObject? {
    if (this.request.headers["content-type"] != "application/json") {
        this.respondError(400, "Missing content-type header.")
        return null
    }

    return try {
        JSONObject(this.receiveText())
    } catch (e: JSONException) {
        this.respondError(400, "The payload is either invalid, or not in the expected format.")
        null
    }
}