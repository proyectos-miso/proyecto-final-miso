package com.solventa.app.data.session

import com.solventa.app.domain.auth.DeviceCredential
import com.solventa.app.domain.auth.SessionTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher

// TC-S1-11: custodia de credenciales en el almacén protegido (HU-MOV-03)
class SecureSessionRepositoryTest {

    private val maria = DeviceCredential(deviceId = "device-maria", secret = "secreto-maria")
    private val pedro = DeviceCredential(deviceId = "device-pedro", secret = "secreto-pedro")
    private val tokens = SessionTokens(accessToken = "access.jwt.token", refreshToken = "refresh-token-123")
    private val newTokens = SessionTokens(accessToken = "access.nuevo", refreshToken = "refresh-nuevo")
    private var now = 1_000L

    private lateinit var crypto: FakeSessionCrypto
    private lateinit var store: InMemorySessionStore
    private lateinit var repository: SecureSessionRepository

    @Before
    fun setUp() {
        crypto = FakeSessionCrypto()
        store = InMemorySessionStore()
        repository = SecureSessionRepository(crypto, store) { now }
    }

    private fun bind(credential: DeviceCredential, email: String, name: String) =
        repository.save(credential, repository.encryptionCipher(), email, name)

    @Test
    fun `TC-S1-11 sin huella vinculada no hay nada que desbloquear`() {
        assertFalse(repository.hasStoredSession())
        assertNull(repository.decryptionCipher(MARIA))
        assertNull(repository.activeSession)
    }

    @Test
    fun `TC-S1-11 guarda la credencial cifrada y nunca el secreto en texto plano`() {
        bind(maria, MARIA, "María Ruiz")

        val stored = store.sessions.single()
        assertFalse(String(stored.ciphertext, Charsets.ISO_8859_1).contains(maria.secret))
        assertEquals(12, stored.iv.size)
        assertEquals("device-maria", repository.storedDeviceId(MARIA))
        assertEquals(listOf(BoundAccount(MARIA, "María Ruiz")), repository.boundAccounts())
    }

    @Test
    fun `TC-S1-11 cada cuenta desbloquea su propia credencial`() {
        bind(maria, MARIA, "María Ruiz")
        bind(pedro, PEDRO, "Pedro Gómez")
        val reopened = SecureSessionRepository(crypto, store)

        assertEquals(maria, reopened.unlock(MARIA, checkNotNull(reopened.decryptionCipher(MARIA))))
        assertEquals(pedro, reopened.unlock(PEDRO, checkNotNull(reopened.decryptionCipher(PEDRO))))
    }

    @Test
    fun `TC-S1-11 volver a activar la huella reemplaza la credencial de esa cuenta`() {
        bind(maria, MARIA, "María Ruiz")
        bind(DeviceCredential("device-nuevo", "otro"), MARIA, "María Ruiz")

        assertEquals(1, repository.boundAccounts().size)
        assertEquals("device-nuevo", repository.storedDeviceId(MARIA))
    }

    @Test
    fun `TC-S1-11 llave invalidada por cambio de huellas descarta todas las cuentas`() {
        bind(maria, MARIA, "María Ruiz")
        bind(pedro, PEDRO, "Pedro Gómez")
        crypto.invalidated = true

        assertNull(repository.decryptionCipher(MARIA))
        assertFalse(repository.hasStoredSession())
    }

    @Test
    fun `TC-S1-11 credencial alterada no se puede descifrar`() {
        bind(maria, MARIA, "María Ruiz")
        val stored = store.sessions.single()
        stored.ciphertext[0] = (stored.ciphertext[0].toInt() xor 0x01).toByte()

        assertThrows(AEADBadTagException::class.java) {
            repository.unlock(MARIA, checkNotNull(repository.decryptionCipher(MARIA)))
        }
    }

    @Test
    fun `TC-S1-12 terminar la sesion conserva las huellas vinculadas`() {
        repository.start(MARIA, tokens)
        bind(maria, MARIA, "María Ruiz")

        repository.end()

        assertNull(repository.activeSession)
        assertTrue(repository.isBound(MARIA))
    }

    @Test
    fun `desvincular una cuenta conserva las demas y la llave`() {
        bind(maria, MARIA, "María Ruiz")
        bind(pedro, PEDRO, "Pedro Gómez")

        repository.unbind(MARIA)

        assertFalse(repository.isBound(MARIA))
        assertTrue(repository.isBound(PEDRO))
        assertNotNull(crypto.key)
    }

    @Test
    fun `desvincular la ultima cuenta borra la llave`() {
        bind(maria, MARIA, "María Ruiz")

        repository.unbind(MARIA)

        assertFalse(repository.hasStoredSession())
        assertNull(crypto.key)
    }

    @Test
    fun `cuenta sin huella no tiene dispositivo ni se puede desbloquear`() {
        assertNull(repository.storedDeviceId(MARIA))
        assertThrows(IllegalStateException::class.java) {
            repository.unlock(MARIA, repository.encryptionCipher())
        }
    }

    @Test
    fun `limpiar todo borra sesion, cuentas y llave`() {
        repository.start(MARIA, tokens)
        bind(maria, MARIA, "María Ruiz")

        repository.clear()

        assertNull(repository.activeSession)
        assertFalse(repository.hasStoredSession())
        assertNull(crypto.key)
    }

    @Test
    fun `credencial del telefono se compara por valor y rechaza datos mal formados`() {
        assertEquals(maria, DeviceCredential("device-maria", "secreto-maria"))
        assertEquals(maria.hashCode(), DeviceCredential("device-maria", "secreto-maria").hashCode())
        assertFalse(maria == pedro)
        assertFalse(maria.equals("device-maria"))
        assertFalse(maria == DeviceCredential("device-maria", "otro"))
        assertEquals(maria, DeviceCredential.fromBytes(maria.toBytes()))
        assertThrows(IllegalArgumentException::class.java) { DeviceCredential.fromBytes("sin-separador".toByteArray()) }
    }

    @Test
    fun `los tokens y el secreto no se exponen al imprimirlos`() {
        assertEquals("SessionTokens(***)", tokens.toString())
        assertEquals("DeviceCredential(device-maria, ***)", maria.toString())
    }

    // HU-MOV-07 TC-04: una renovación o un cierre tardíos no reviven la sesión ni pisan otra.
    @Test
    fun `la sesion solo cambia desde la instantanea vigente`() {
        val first = repository.start(MARIA, tokens)
        val renewed = first.withTokens(newTokens)

        assertTrue(repository.replace(first, renewed))
        assertFalse(repository.replace(first, first.withTokens(tokens)))
        assertFalse(repository.endIf(first))
        assertEquals(newTokens, repository.activeSession)

        repository.end()
        assertFalse(repository.replace(renewed, renewed.withTokens(tokens)))
        assertNull(repository.openSession)
    }

    // HU-MOV-07 TC-08: al terminar la actividad solo se cierra su sesión, con los tokens que tenga ahora.
    @Test
    fun `endSession termina solo su sesion y devuelve sus tokens vigentes`() {
        val mine = repository.start(MARIA, tokens)
        repository.replace(mine, mine.withTokens(newTokens))

        assertEquals(newTokens, repository.endSession(mine.id)?.tokens)

        val other = repository.start(PEDRO, tokens)
        assertNull(repository.endSession(mine.id))
        assertSame(other, repository.openSession)
    }

    // HU-MOV-07 TC-06: la credencial del ingreso sin red vale 15 minutos y no se extiende atrasando el reloj.
    @Test
    fun `la credencial vence a los 15 minutos o si el reloj retrocede`() {
        val offline = repository.startOffline(MARIA, maria)

        assertNull(offline.tokens)
        assertFalse(repository.credentialExpired(offline))
        now += 15 * 60 * 1000L - 1
        assertFalse(repository.credentialExpired(offline))
        now += 1
        assertTrue(repository.credentialExpired(offline))
        now = 0
        assertTrue(repository.credentialExpired(offline))
    }

    @Test
    fun `soltar la credencial crea otra instantanea con el mismo id e inicio`() {
        val offline = repository.startOffline(MARIA, maria)

        repository.dropCredential(PEDRO)
        assertSame(offline, repository.openSession)

        repository.dropCredential(MARIA)
        val dropped = checkNotNull(repository.openSession)
        assertNull(dropped.credential)
        assertEquals(offline.id, dropped.id)
        assertEquals(offline.credentialSince, dropped.credentialSince)
        assertFalse(repository.replace(offline, offline.withTokens(newTokens)))
    }

    @Test
    fun `desvincular o invalidar la llave suelta la credencial en memoria`() {
        bind(maria, MARIA, "María Ruiz")
        repository.startOffline(MARIA, maria)
        repository.unbind(MARIA)
        assertNull(repository.openSession?.credential)

        bind(maria, MARIA, "María Ruiz")
        repository.startOffline(MARIA, maria)
        crypto.invalidated = true
        assertEquals(KeyState.LOST, repository.keyState(MARIA))
        assertNull(repository.openSession?.credential)
        assertFalse(repository.hasStoredSession())
    }

    @Test
    fun `el estado de la llave no lanza y una falla pasajera no borra las cuentas`() {
        assertEquals(KeyState.LOST, repository.keyState(MARIA))
        bind(maria, MARIA, "María Ruiz")
        assertEquals(KeyState.USABLE, repository.keyState(MARIA))

        val busy = object : SessionCrypto by crypto {
            override fun decryptionCipher(iv: ByteArray): Cipher? = throw ProviderException("Keystore ocupado")
        }
        val failing = SecureSessionRepository(busy, store) { now }

        assertEquals(KeyState.UNAVAILABLE, failing.keyState(MARIA))
        assertTrue(failing.isBound(MARIA))
    }

    // HU-MOV-07 TC-10: la sesión en memoria no se imprime con el correo ni los secretos.
    @Test
    fun `la sesion abierta no expone el correo ni los secretos al imprimirse`() {
        val text = repository.startOffline(MARIA, maria).toString() + repository.start(MARIA, tokens).toString()

        assertFalse(text.contains(MARIA))
        assertFalse(text.contains(maria.secret))
        assertFalse(text.contains(tokens.refreshToken))
    }

    private companion object {
        const val MARIA = "maria.ruiz@correo.co"
        const val PEDRO = "pedro.gomez@correo.co"
    }
}
