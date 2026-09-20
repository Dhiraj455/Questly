package com.example.questly.backend.auth

import at.favre.lib.crypto.bcrypt.BCrypt

/** BCrypt password hashing. Cost 12 is a sensible default for a server. */
object Passwords {
    private const val COST = 12

    fun hash(plain: String): String = BCrypt.withDefaults().hashToString(COST, plain.toCharArray())

    fun verify(plain: String, hash: String): Boolean =
        BCrypt.verifyer().verify(plain.toCharArray(), hash).verified
}
