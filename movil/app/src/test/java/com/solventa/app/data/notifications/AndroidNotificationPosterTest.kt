package com.solventa.app.data.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.messaging.RemoteMessage
import com.solventa.app.R
import com.solventa.app.SolventaApplication
import com.solventa.app.domain.policies.Notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

// Avisos del canal de Solventa: privados en la pantalla bloqueada y borrados al cerrar sesión (HU-MOV-11: AC-03, AC-05, AC-11).
@RunWith(RobolectricTestRunner::class)
class AndroidNotificationPosterTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val notice = Notice("claim:SIN-2026-00001", "Su siniestro cambió", "Ahora está en evaluación.", claimNumber = "SIN-2026-00001")

    @Test
    fun `sin permiso de avisos no muestra nada`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        AndroidNotificationPoster(app).post(notice)

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `con permiso publica el aviso privado que abre el siniestro`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        AndroidNotificationPoster(app).post(notice)

        val posted = shadowOf(manager).getNotification(notice.key.hashCode())
        assertNotNull(posted)
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
        assertEquals("Su siniestro cambió", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Ahora está en evaluación.", posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(app.getString(R.string.notice_public_title), posted.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(AndroidNotificationPoster.CHANNEL_ID, posted.channelId)
        val intent = shadowOf(posted.contentIntent).savedIntent
        assertEquals("SIN-2026-00001", intent.getStringExtra(AndroidNotificationPoster.EXTRA_CLAIM_NUMBER))
        assertEquals(app.getString(R.string.notice_channel_name), manager.getNotificationChannel(AndroidNotificationPoster.CHANNEL_ID).name)
    }

    @Test
    fun `un aviso sin siniestro no lleva el numero y cerrar sesion los borra`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val renewal = Notice("renewal:POL-2026-00001", "Su póliza vence pronto", "Renuévela desde la app.")

        AndroidNotificationPoster(app).post(renewal)
        val posted = shadowOf(manager).getNotification(renewal.key.hashCode())
        assertEquals(null, shadowOf(posted.contentIntent).savedIntent.getStringExtra(AndroidNotificationPoster.EXTRA_CLAIM_NUMBER))

        AndroidNotificationPoster.clear(app)

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `el servicio descarta avisos desconocidos o sin sesion y no registra el token sin sesion`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val service = Robolectric.buildService(SolventaMessagingService::class.java).create().get()
        assertTrue(service.application is SolventaApplication)

        service.onMessageReceived(message("type" to "OTRO", "title" to "t", "body" to "b"))
        service.onMessageReceived(message("type" to "CLAIM_STATUS", "claim_number" to "SIN-1", "title" to "t", "body" to "b"))
        service.onNewToken("fcm-token-de-prueba")

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `sin Firebase configurado no pide el token`() {
        var registered: String? = null

        SolventaMessagingService.registerCurrentToken(app) { registered = it }

        assertEquals(null, registered)
    }

    @Test
    fun `el aviso de renovacion necesita el numero de poliza`() {
        val renewal = SolventaMessagingService.pushNotice(mapOf("type" to "RENEWAL_REMINDER", "policy_number" to "POL-1", "title" to "t", "body" to "b"))
        assertEquals("renewal-push:POL-1", renewal?.key)
        assertEquals(null, SolventaMessagingService.pushNotice(mapOf("type" to "CLAIM_STATUS", "claim_number" to "SIN-1", "body" to "b")))
        assertEquals(null, SolventaMessagingService.pushNotice(mapOf("type" to "CLAIM_STATUS", "claim_number" to "SIN-1", "title" to "t")))
    }

    private fun message(vararg data: Pair<String, String>) = RemoteMessage(Bundle().apply { data.forEach { (k, v) -> putString(k, v) } })
}
