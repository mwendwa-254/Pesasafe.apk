package com.example.sampleapp.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class SafePesaAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "SafePesa Accountability Shield is active. Your accountability partner's PIN is required to disable protection."
    }
}
