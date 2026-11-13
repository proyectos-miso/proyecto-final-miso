package com.solventa.app.data.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.solventa.app.SolventaApplication
import com.solventa.app.domain.policies.Notice

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
        val notice = pushNotice(message.data) ?: return
        if (app.sessionRepository.openSession == null) return
        app.notificationPoster.post(notice)
        app.policySync.schedule()
    }

    companion object {
        /** Solo los tipos conocidos; cualquier otro dato se descarta. */
        fun pushNotice(data: Map<String, String>): Notice? {
            val title = data["title"] ?: return null
            val body = data["body"] ?: return null
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
