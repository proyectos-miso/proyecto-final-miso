package com.solventa.app.domain.auth

/** [reportToken] solo sirve para enviar el reporte de siniestro y es lo único que se guarda en disco (HU-MOV-09). */
class SessionTokens(val accessToken: String, val refreshToken: String, val reportToken: String? = null) {

    fun toBytes(): ByteArray = "$accessToken\n$refreshToken".toByteArray(Charsets.UTF_8)

    override fun equals(other: Any?): Boolean =
        other is SessionTokens && other.accessToken == accessToken && other.refreshToken == refreshToken &&
            other.reportToken == reportToken

    override fun hashCode(): Int = 31 * (31 * accessToken.hashCode() + refreshToken.hashCode()) + reportToken.hashCode()

    override fun toString(): String = "SessionTokens(***)"

    companion object {
        fun fromBytes(bytes: ByteArray): SessionTokens {
            val parts = String(bytes, Charsets.UTF_8).split('\n')
            require(parts.size == 2) { "Formato de sesión inválido" }
            return SessionTokens(parts[0], parts[1])
        }
    }
}
