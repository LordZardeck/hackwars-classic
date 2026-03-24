package com.hackwars.rewrite.gamecore

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

object RewriteGameJson {
    val codec: Json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray {
        return codec.encodeToString(serializer, value).encodeToByteArray()
    }

    fun <T> decode(serializer: KSerializer<T>, payload: ByteArray): T {
        return codec.decodeFromString(serializer, payload.decodeToString())
    }
}
