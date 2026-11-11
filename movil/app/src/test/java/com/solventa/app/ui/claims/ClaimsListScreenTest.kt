package com.solventa.app.ui.claims

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.solventa.app.PHONE
import com.solventa.app.R
import com.solventa.app.data.policies.sampleClaim
import com.solventa.app.domain.claims.ClaimTimelineStep
import com.solventa.app.domain.claims.InsuredClaim
import com.solventa.app.ui.theme.SolventaTheme
import com.solventa.app.ui.wallet.formatDate
import com.solventa.app.ui.wallet.formatDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

// Mis siniestros y el historial de cada uno (HU-MOV-11: AC-01, AC-02, AC-04, AC-06, AC-07).
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE)
class ClaimsListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private val updatedAt = Instant.parse("2026-11-08T09:00:00Z")

    private fun showList(state: ClaimsUiState, onBack: () -> Unit = {}, onOpen: (String) -> Unit = {}, onReport: (() -> Unit)? = null) {
        compose.setContent {
            SolventaTheme { ClaimsListScreen(state = state, onBack = onBack, onOpen = onOpen, onReport = onReport) }
        }
    }

    private fun showDetail(claim: InsuredClaim?, onBack: () -> Unit = {}) {
        compose.setContent {
            SolventaTheme { ClaimDetailScreen(claim = claim, onBack = onBack) }
        }
    }

    private fun rowDescription(claim: InsuredClaim) =
        s(R.string.claims_row_description, claim.claimNumber, claim.policyNumber, formatDate(claim.reportedAt), claim.statusLabel)

    @Test
    fun `AC-01 lista los siniestros con su estado y abre el elegido`() {
        val claims = listOf(
            sampleClaim("SIN-2026-00003", status = "PAID", label = "Pagado"),
            sampleClaim("SIN-2026-00002", status = "REJECTED", label = "Rechazado"),
            sampleClaim("SIN-2026-00001"),
        )
        var opened: String? = null
        showList(ClaimsUiState(loading = false, claims = claims, lastUpdate = updatedAt), onOpen = { opened = it })

        compose.onNodeWithText(s(R.string.claims_title)).assertIsDisplayed()
        claims.forEach { compose.onNodeWithContentDescription(rowDescription(it)).assertIsDisplayed() }
        compose.onNodeWithText(s(R.string.claims_offline)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.claims_last_update, formatDateTime(updatedAt))).assertIsDisplayed()

        compose.onNodeWithContentDescription(rowDescription(claims[1])).performClick()

        assertEquals("SIN-2026-00002", opened)
    }

    @Test
    fun `AC-04 sin conexion muestra el aviso y la ultima actualizacion`() {
        showList(ClaimsUiState(loading = false, claims = listOf(sampleClaim()), offline = true, lastUpdate = updatedAt))

        compose.onNodeWithText(s(R.string.claims_offline)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claims_last_update, formatDateTime(updatedAt))).assertIsDisplayed()
    }

    @Test
    fun `sin conexion ni copia pide conexion sin el aviso de copia`() {
        showList(ClaimsUiState(loading = false, offline = true, needsConnection = true))

        compose.onNodeWithText(s(R.string.claims_needs_connection)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claims_offline)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.claims_empty)).assertDoesNotExist()
    }

    @Test
    fun `AC-06 sin siniestros lo indica y ofrece reportar uno`() {
        var reported = false
        showList(ClaimsUiState(loading = false), onReport = { reported = true })

        compose.onNodeWithText(s(R.string.claims_empty)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claim_report_button)).performClick()

        assertTrue(reported)
    }

    @Test
    fun `AC-06 sin siniestros ni poliza vigente no ofrece reportar`() {
        showList(ClaimsUiState(loading = false))

        compose.onNodeWithText(s(R.string.claims_empty)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claim_report_button)).assertDoesNotExist()
    }

    @Test
    fun `mientras carga lo anuncia y volver cierra la lista`() {
        var back = false
        showList(ClaimsUiState(), onBack = { back = true })

        compose.onNodeWithContentDescription(s(R.string.claims_loading)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.claim_back)).performClick()

        assertTrue(back)
    }

    @Test
    fun `AC-02 el detalle muestra la linea de tiempo con fecha y explicacion`() {
        val received = Instant.parse("2026-10-01T10:00:00Z")
        val review = Instant.parse("2026-10-03T15:00:00Z")
        val claim = sampleClaim().copy(
            lastChangeAt = review,
            timeline = listOf(
                ClaimTimelineStep("RECEIVED", "Recibido", "Recibimos su reporte.", received),
                ClaimTimelineStep("IN_REVIEW", "En evaluación", "Un analista revisa la evidencia.", review),
            ),
        )
        var back = false
        showDetail(claim) { back = true }

        compose.onNodeWithText("SIN-2026-00001").assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claims_detail_subtitle, "POL-2026-00001")).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claims_timeline_title), ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.claims_step_description, "Recibido", formatDateTime(received), "Recibimos su reporte."))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            s(R.string.claims_step_description, "En evaluación", formatDateTime(review), "Un analista revisa la evidencia."),
        ).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claims_rejection_title)).assertDoesNotExist()

        compose.onNodeWithContentDescription(s(R.string.claim_back)).performClick()
        assertTrue(back)
    }

    @Test
    fun `AC-07 un rechazo muestra el motivo y como pedir revision`() {
        val claim = sampleClaim(status = "REJECTED", label = "Rechazado").copy(
            rejectionReason = "La póliza no cubre este evento.",
            reviewChannel = "Escriba a revisiones@example.com.",
        )
        showDetail(claim)

        compose.onNodeWithText(s(R.string.claims_rejection_title)).assertIsDisplayed()
        compose.onNodeWithText("La póliza no cubre este evento.").assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claims_review_title)).assertIsDisplayed()
        compose.onNodeWithText("Escriba a revisiones@example.com.").assertIsDisplayed()
    }

    @Test
    fun `un rechazo sin motivo ni canal no muestra esos titulos`() {
        showDetail(sampleClaim(status = "REJECTED", label = "Rechazado"))

        compose.onAllNodesWithText(s(R.string.claims_rejection_title)).assertCountEquals(0)
        compose.onAllNodesWithText(s(R.string.claims_review_title)).assertCountEquals(0)
    }

    @Test
    fun `un siniestro que ya no esta lo indica`() {
        showDetail(null)

        compose.onNodeWithText(s(R.string.claims_not_found)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.claims_title)).assertIsDisplayed()
    }
}
