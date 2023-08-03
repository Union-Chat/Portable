package me.devoxin.union

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveText
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondText
import org.json.JSONException
import org.json.JSONObject


private val CHAR_POOL = ('a'..'z') + ('A'..'Z') + ('0'..'9')

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

fun generateArbitrary(length: Int): String {
    return (0 until length).map { CHAR_POOL.random() }.joinToString("")
}

suspend fun ApplicationCall.respondError(code: HttpStatusCode, message: String) {
    val errorResponse = JSONObject(
        mapOf(
            "error" to message
        )
    )

    this.respondText(errorResponse.toString(), ContentType.Application.Json, code)
}

suspend fun ApplicationCall.receiveJson(): JSONObject? {
    if (this.request.headers["content-type"] != "application/json") {
        this.respondError(HttpStatusCode.BadRequest, "Missing content-type header.")
        return null
    }

    return try {
        JSONObject(this.receiveText())
    } catch (e: JSONException) {
        this.respondError(HttpStatusCode.BadRequest, "The payload is either invalid, or not in the expected format.")
        null
    }
}
