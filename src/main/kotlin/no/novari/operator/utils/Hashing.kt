package no.novari.operator.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.security.MessageDigest

private val mapper =
    ObjectMapper()
        .registerKotlinModule()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

fun createHash(map: Map<*, *>): String {
    val json = mapper.writeValueAsBytes(map)
    return MessageDigest.getInstance("SHA-256").digest(json).joinToString("") { "%02x".format(it) }
}
