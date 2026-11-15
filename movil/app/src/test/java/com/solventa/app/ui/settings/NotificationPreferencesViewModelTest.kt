package com.solventa.app.ui.settings

import com.solventa.app.data.policies.FakePolicyActions
import com.solventa.app.data.policies.InMemoryNotificationPreferences
import com.solventa.app.domain.policies.NoticeCategory
import com.solventa.app.domain.policies.PreferencesResult
import com.solventa.app.domain.policies.PushPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Preferencias de avisos: carga, guardado al instante, error con reintento y sin conexión (FE-MOV-04).
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPreferencesViewModelTest {

    private val actions = FakePolicyActions()
    private val store = InMemoryNotificationPreferences()
    private lateinit var viewModel: NotificationPreferencesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = NotificationPreferencesViewModel(actions, store)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `al abrir trae las preferencias del servidor y actualiza la copia local`() {
        store.save(EMAIL, PushPreferences(claims = false), pending = false)
        actions.preferences = PreferencesResult.Loaded(PushPreferences(claims = true, renewals = false, payments = true))

        viewModel.open(EMAIL)

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertFalse(state.offline)
        assertFalse(state.saved)
        assertEquals(PushPreferences(claims = true, renewals = false, payments = true), state.preferences)
        assertEquals(PushPreferences(claims = true, renewals = false, payments = true), store.read(EMAIL))
        assertFalse(store.pending(EMAIL))
        assertEquals(1, actions.reads)
    }

    @Test
    fun `cada interruptor guarda al instante con PUT y muestra Guardado`() {
        viewModel.open(EMAIL)

        viewModel.toggle(NoticeCategory.RENEWALS, false)

        val state = viewModel.uiState.value
        assertEquals(listOf(PushPreferences(renewals = false)), actions.saved)
        assertEquals(PushPreferences(renewals = false), state.preferences)
        assertTrue(state.saved)
        assertFalse(state.saving)
        assertNull(state.error)
        assertEquals(PushPreferences(renewals = false), store.read(EMAIL))
        assertFalse(store.pending(EMAIL))

        viewModel.toggle(NoticeCategory.PAYMENTS, false)
        assertEquals(PushPreferences(renewals = false, payments = false), actions.saved.last())
    }

    @Test
    fun `un error del servidor muestra el aviso y Reintentar vuelve a enviar lo mismo`() {
        viewModel.open(EMAIL)
        actions.saveResult = { PreferencesResult.Invalid }

        viewModel.toggle(NoticeCategory.CLAIMS, false)
        assertEquals(PreferencesError.INVALID, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.saved)

        actions.saveResult = { PreferencesResult.Loaded(it) }
        viewModel.retry()

        assertEquals(listOf(PushPreferences(claims = false), PushPreferences(claims = false)), actions.saved)
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.saved)
        assertFalse(store.pending(EMAIL))
    }

    @Test
    fun `con la sesion vencida al guardar se pide volver a ingresar`() {
        viewModel.open(EMAIL)
        actions.saveResult = { PreferencesResult.Unauthorized }

        viewModel.toggle(NoticeCategory.CLAIMS, false)

        assertEquals(PreferencesError.SESSION, viewModel.uiState.value.error)
        assertEquals(PushPreferences(claims = false), viewModel.uiState.value.preferences)
    }

    @Test
    fun `sin red muestra la copia local, deja el cambio pendiente y lo envia al reabrir`() {
        store.save(EMAIL, PushPreferences(payments = false), pending = false)
        actions.preferences = PreferencesResult.Unavailable
        actions.saveResult = { PreferencesResult.Unavailable }

        viewModel.open(EMAIL)
        assertTrue(viewModel.uiState.value.offline)
        assertEquals(PushPreferences(payments = false), viewModel.uiState.value.preferences)

        viewModel.toggle(NoticeCategory.CLAIMS, false)
        assertTrue(viewModel.uiState.value.offline)
        assertEquals(PushPreferences(claims = false, payments = false), viewModel.uiState.value.preferences)
        assertTrue(store.pending(EMAIL))
        assertNull(viewModel.uiState.value.error)

        actions.saveResult = { PreferencesResult.Loaded(it) }
        viewModel.open(EMAIL)

        assertEquals(PushPreferences(claims = false, payments = false), actions.saved.last())
        assertEquals(1, actions.reads)
        assertFalse(viewModel.uiState.value.offline)
        assertFalse(store.pending(EMAIL))
    }

    @Test
    fun `cerrar la pantalla descarta la cuenta y un cambio ya no guarda nada`() {
        viewModel.open(EMAIL)

        viewModel.close()
        viewModel.toggle(NoticeCategory.CLAIMS, false)

        assertTrue(actions.saved.isEmpty())
        assertTrue(viewModel.uiState.value.loading)
    }

    private companion object {
        const val EMAIL = "asegurada@example.com"
    }
}
