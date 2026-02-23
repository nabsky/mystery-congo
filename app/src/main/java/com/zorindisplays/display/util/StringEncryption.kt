package com.zorindisplays.display.util

import java.security.MessageDigest

object StringEncryption {
    fun sha1(text: String): String? = try {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        val digest = md.digest(bytes)
        digest.joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }
}