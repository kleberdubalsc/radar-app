package com.kleber.radar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Accessibility service reinicia automaticamente pelo sistema
            // aqui podemos iniciar serviços adicionais se necessário
        }
    }
}
