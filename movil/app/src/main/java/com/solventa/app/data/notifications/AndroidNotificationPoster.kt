package com.solventa.app.data.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.solventa.app.MainActivity
import com.solventa.app.R
import com.solventa.app.domain.policies.Notice
import com.solventa.app.domain.policies.NotificationPoster

/**
 * Avisos del canal «Avisos de Solventa» (HU-MOV-11). En la pantalla bloqueada solo se ve que hay un aviso:
 * el estado de un siniestro de vida puede revelar salud o muerte. Sin permiso no se muestra nada.
 */
class AndroidNotificationPoster(private val context: Context) : NotificationPoster {

    override fun post(notice: Notice) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { notice.claimNumber?.let { putExtra(EXTRA_CLAIM_NUMBER, it) } }
        val pending = PendingIntent.getActivity(
            context, notice.key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val public = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.notice_public_title))
            .build()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(Notification.BigTextStyle().bigText(notice.body))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(notice.key.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "solventa_notices"
        const val EXTRA_CLAIM_NUMBER = "claim_number"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notice_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        /** Al cerrar sesión no queda ningún aviso de la cuenta en la barra (HU-MOV-11, AC-11). */
        fun clear(context: Context) {
            context.getSystemService(NotificationManager::class.java)?.cancelAll()
        }
    }
}
