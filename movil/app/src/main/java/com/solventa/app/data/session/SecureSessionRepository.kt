package com.solventa.app.data.session

import android.os.SystemClock
import com.solventa.app.domain.auth.DeviceCredential
import com.solventa.app.domain.auth.SessionTokens
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher

data class BoundAccount(val email: String, val name: String)

/**
 * Sesión abierta, inmutable: cada cambio crea otra instantánea. [id] identifica la sesión y se conserva al
 * renovar o soltar la credencial; [credential] solo existe tras un ingreso sin red (HU-MOV-07).
 */
class OpenSession internal constructor(
    val id: Long,
    val email: String,
    val tokens: SessionTokens?,
    val credential: DeviceCredential?,
    val credentialSince: Long,
    val renewalUnanswered: Boolean,
) {
    fun withTokens(tokens: SessionTokens) = OpenSession(id, email, tokens, null, credentialSince, false)

    fun withRenewalUnanswered() = OpenSession(id, email, tokens, credential, credentialSince, true)

    fun withoutCredential() = OpenSession(id, email, tokens, null, credentialSince, renewalUnanswered)

    override fun toString(): String = "OpenSession($id)"
}

/** Estado de la llave biométrica de una cuenta, sin lanzar (HU-MOV-07). */
enum class KeyState { USABLE, LOST, UNAVAILABLE }

// Los tokens y la credencial del ingreso sin red solo viven en memoria. Lo que se guarda, cifrado con la
// llave que exige huella, es la credencial del teléfono de cada cuenta que activó la huella (HU-MOV-03,
// HU-MOV-12). La sesión cambia con compareAndSet, porque también la cambia la sincronización (HU-MOV-07).
class SecureSessionRepository(
    private val crypto: SessionCrypto,
    private val store: EncryptedSessionStore,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val open = AtomicReference<OpenSession?>()
    private val ids = AtomicLong()

    val openSession: OpenSession? get() = open.get()

    val activeSession: SessionTokens? get() = open.get()?.tokens

    fun start(email: String, tokens: SessionTokens): OpenSession =
        OpenSession(ids.incrementAndGet(), email, tokens, null, 0, false).also(open::set)

    fun startOffline(email: String, credential: DeviceCredential): OpenSession =
        OpenSession(ids.incrementAndGet(), email, null, credential, elapsedRealtime(), false).also(open::set)

    /** Cambia la sesión solo si sigue siendo [from]; una renovación tardía no revive una sesión cerrada. */
    fun replace(from: OpenSession, to: OpenSession): Boolean = open.compareAndSet(from, to)

    fun endIf(from: OpenSession): Boolean = open.compareAndSet(from, null)

    /** Termina la sesión [id] si sigue abierta y devuelve la instantánea que quitó, con sus tokens actuales. */
    fun endSession(id: Long): OpenSession? {
        while (true) {
            val current = open.get()?.takeIf { it.id == id } ?: return null
            if (open.compareAndSet(current, null)) return current
        }
    }

    fun end() {
        open.set(null)
    }

    /** La credencial vale 15 minutos desde la huella; un reloj que retrocede la da por vencida. */
    fun credentialExpired(session: OpenSession): Boolean =
        (elapsedRealtime() - session.credentialSince).let { it < 0 || it >= CREDENTIAL_TTL_MS }

    /** Suelta la credencial del ingreso sin red, de [email] o de cualquier cuenta. */
    fun dropCredential(email: String? = null) {
        while (true) {
            val current = open.get() ?: return
            if (current.credential == null || (email != null && current.email != email)) return
            if (open.compareAndSet(current, current.withoutCredential())) return
        }
    }

    fun keyState(email: String): KeyState = try {
        if (decryptionCipher(email) != null) KeyState.USABLE else KeyState.LOST
    } catch (e: Exception) {
        KeyState.UNAVAILABLE
    }

    fun boundAccounts(): List<BoundAccount> = store.readAll().map { BoundAccount(it.accountEmail, it.accountName) }

    fun hasStoredSession(): Boolean = store.readAll().isNotEmpty()

    fun isBound(email: String): Boolean = find(email) != null

    fun storedDeviceId(email: String): String? = find(email)?.deviceId

    fun encryptionCipher(): Cipher = crypto.encryptionCipher()

    /** Si la llave fue invalidada (huella nueva en el teléfono) se pierden todas las cuentas vinculadas. */
    fun decryptionCipher(email: String): Cipher? {
        val stored = find(email) ?: return null
        return crypto.decryptionCipher(stored.iv) ?: run {
            unbindAll()
            null
        }
    }

    fun save(credential: DeviceCredential, authenticatedCipher: Cipher, accountEmail: String, accountName: String) {
        val plaintext = credential.toBytes()
        try {
            store.write(
                EncryptedSession(
                    ciphertext = authenticatedCipher.doFinal(plaintext),
                    iv = authenticatedCipher.iv,
                    deviceId = credential.deviceId,
                    accountEmail = accountEmail,
                    accountName = accountName,
                )
            )
        } finally {
            plaintext.fill(0)
        }
    }

    fun unlock(email: String, authenticatedCipher: Cipher): DeviceCredential {
        val stored = checkNotNull(find(email)) { "No hay huella vinculada para la cuenta" }
        val plaintext = authenticatedCipher.doFinal(stored.ciphertext)
        try {
            return DeviceCredential.fromBytes(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    fun unbind(email: String) {
        store.remove(email)
        dropCredential(email)
        if (!hasStoredSession()) crypto.deleteKey()
    }

    fun unbindAll() {
        store.clear()
        dropCredential()
        crypto.deleteKey()
    }

    fun clear() {
        end()
        unbindAll()
    }

    private fun find(email: String): EncryptedSession? = store.readAll().firstOrNull { it.accountEmail == email }

    private companion object {
        const val CREDENTIAL_TTL_MS = 15 * 60 * 1000L
    }
}
