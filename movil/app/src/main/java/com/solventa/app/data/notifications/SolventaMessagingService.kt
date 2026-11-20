package com.solventa.app.data.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.solventa.app.SolventaApplication
import com.solventa.app.domain.policies.Notice
import com.solventa.app.domain.policies.NoticeCategory
import com.solventa.app.domain.policies.PushPreferences

/**
 * Avisos del servidor por Firebase Cloud Messaging (HU-MOV-11). Sin google-services.json Firebase no se inicia
 * y los avisos salen solo con la sincronización (RC-15).
 */
class SolventaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        (application as SolventaApplication).registerPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as SolventaApplication
        val opened = app.sessionRepository.openSession ?: return
        // Aunque el aviso se descarte, el cambio llega a la billetera con la sincronización.
        app.policySync.schedule()
        pushNotice(message.data, app.notificationPreferences.read(opened.email))?.let(app.notificationPoster::post)
    }

    companion object {
        /** Solo los tipos conocidos; cualquier otro dato se descarta. Una categoría apagada por el asegurado tampoco se muestra (FE-MOV-04). */
        fun pushNotice(data: Map<String, String>, preferences: PushPreferences = PushPreferences()): Notice? {
            val title = data["title"] ?: return null
            val body = data["body"] ?: return null
            val category = NoticeCategory.fromWire(data["category"])
            if (category != null && !preferences.enabled(category)) return null
            return when (data["type"]) {
                "CLAIM_STATUS" -> data["claim_number"]?.let { Notice("claim:$it", title, body, claimNumber = it) }
                "RENEWAL_REMINDER" -> data["policy_number"]?.let { Notice("renewal-push:$it", title, body) }
                else -> null
            }
        }

        /** Pide el token y lo registra para la cuenta con sesión; no hace nada si Firebase no está configurado. */
        fun registerCurrentToken(context: Context, register: (String) -> Unit) {
            if (FirebaseApp.getApps(context).isEmpty()) return
            runCatching { FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> token?.let(register) } }
        }
    }
}
