package com.solventa.app.data.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.data.policies.PassphraseVault
import com.solventa.app.data.policies.XorWalletKeys
import com.solventa.app.domain.auth.SessionTokens
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Token de reporte cifrado por cuenta con su llave del Keystore (HU-MOV-09, SR-02, SR-05).
@RunWith(RobolectricTestRunner::class)
class ReportTokenStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keys = XorWalletKeys()
    private val store = ReportTokenStore(context, keys)
    private val prefs = context.getSharedPreferences("solventa_report_tokens", Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        store.delete(MARIA)
        store.delete(PEDRO)
    }

    @Test
    fun `guarda el último token de cada cuenta cifrado y lo lee por su id`() {
        store.save(MARIA, "token-uno")
        store.save(MARIA, "token-dos")
        store.save(PEDRO, "token-pedro")

        assertEquals("token-dos", store.read(PassphraseVault.accountId(MARIA)))
        assertEquals("token-pedro", store.read(PassphraseVault.accountId(PEDRO)))
        assertFalse(prefs.all.values.joinToString().contains("token-dos"))
        assertFalse("el correo no queda en el teléfono", prefs.all.keys.any { it.contains("@") })
    }

    @Test
    fun `al borrar la cuenta se borran el token y su llave sin tocar las demás`() {
        store.save(MARIA, "token-maria")
        store.save(PEDRO, "token-pedro")

        store.delete(MARIA)

        assertNull(store.read(PassphraseVault.accountId(MARIA)))
        assertEquals("token-pedro", store.read(PassphraseVault.accountId(PEDRO)))
        assertEquals(setOf("solventa_report_${PassphraseVault.accountId(PEDRO)}"), keys.aliases)
    }

    @Test
    fun `si la llave no envuelve, el token anterior ya no sirve y se borra`() {
        store.save(MARIA, "token-viejo")
        keys.canWrap = false

        store.save(MARIA, "token-nuevo")

        assertNull(store.read(PassphraseVault.accountId(MARIA)))
    }

    @Test
    fun `una llave no disponible conserva el token y una perdida lo borra`() {
        store.save(MARIA, "token")
        keys.unavailable = true
        assertNull(store.read(PassphraseVault.accountId(MARIA)))
        keys.unavailable = false
        assertEquals("token", store.read(PassphraseVault.accountId(MARIA)))

        keys.aliases.clear()
        assertNull(store.read(PassphraseVault.accountId(MARIA)))
        assertFalse(prefs.all.keys.any { it.startsWith(PassphraseVault.accountId(MARIA)) })
    }

    @Test
    fun `la sesión entrega cada par nuevo para guardar el token de reporte`() {
        val received = mutableListOf<Triple<String, String?, Boolean>>()
        val session = SecureSessionRepository(FakeSessionCrypto(), InMemorySessionStore(), onTokens = { email, tokens, signedIn ->
            received += Triple(email, tokens.reportToken, signedIn)
        }) { 0L }

        val opened = session.start(MARIA, SessionTokens("a1", "r1", "rep1"))
        session.replace(opened, opened.withRenewalUnanswered())
        val current = session.openSession!!
        session.replace(current, current.withTokens(SessionTokens("a2", "r2", "rep2")))

        assertEquals(listOf(Triple(MARIA, "rep1", true), Triple(MARIA, "rep2", false)), received)
    }

    private companion object {
        const val MARIA = "maria@correo.test"
        const val PEDRO = "pedro@correo.test"
    }
}
