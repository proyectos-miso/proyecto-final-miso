package com.solventa.app.domain.claims

import java.security.MessageDigest

/** Huella SHA-256 en hexadecimal de los bytes JPEG que se envían; HU-MOV-09 la verifica al recibir (CRY-04). */
fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
