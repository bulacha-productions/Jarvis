package com.jarvis.assistant.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.data.local.preferences.UserPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Receiver para capturar a reinicialização do dispositivo (BOOT_COMPLETED).
 * 
 * Em conformidade com o Android 12+ (API 31/34/35/36), não tenta iniciar um serviço
 * com tipo 'microphone' diretamente de um BroadcastReceiver em segundo plano,
 * o que causaria ForegroundServiceStartNotAllowedException.
 * 
 * Em vez disso, emite uma notificação interativa convidando o usuário a reativar
 * a escuta com um único toque, iniciando o serviço de forma permitida e oficial.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val BOOT_CHANNEL_ID = "jarvis_boot_reminder_channel"
        const val BOOT_NOTIFICATION_ID = 5050
        const val EXTRA_START_VOICE_SERVICE = "com.jarvis.assistant.EXTRA_START_VOICE_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val preferencesManager = UserPreferencesManager(context.applicationContext)
            
            CoroutineScope(Dispatchers.IO).launch {
                val wasWakeWordEnabled = preferencesManager.isWakeWordEnabled.firstOrNull() ?: false
                if (wasWakeWordEnabled) {
                    notifyUserToReactivate(context.applicationContext)
                }
            }
        }
    }

    private fun notifyUserToReactivate(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BOOT_CHANNEL_ID,
                "Jarvis — Notificações de Inicialização",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lembrete para reativação autorizada da escuta por voz após reinicialização"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_START_VOICE_SERVICE, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            101,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, BOOT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis — Ativação por voz pronta")
            .setContentText("O celular foi reiniciado. Toque para reativar a escuta contínua de "Jarvis".")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "O celular foi reiniciado com a ativação automática habilitada. Toque aqui para autorizar o início do serviço de microfone."
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BOOT_NOTIFICATION_ID, notification)
    }
}