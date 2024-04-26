package com.wuyr.jdwp_injector_test.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.widget.Toast
import com.wuyr.jdwp_injector.adb.AdbClient
import com.wuyr.jdwp_injector.adb.AdbWirelessPairing
import com.wuyr.jdwp_injector.adb.AdbWirelessPortResolver
import com.wuyr.jdwp_injector.adb.AdbWirelessPortResolver.Companion.resolveAdbConnectPort
import com.wuyr.jdwp_injector.adb.AdbWirelessPortResolver.Companion.resolveAdbPairingPort
import com.wuyr.jdwp_injector_test.R
import com.wuyr.jdwp_injector_test.log.logE
import javax.net.ssl.SSLHandshakeException
import kotlin.concurrent.thread

/**
 * @author wuyr
 * @github https://github.com/wuyr/jdwp-injector-for-android
 * @since 2024-04-14 3:38 PM
 */
class WirelessPairingService : Service() {

    companion object {
        private const val CHANNEL_ID = "Adb wireless pairing"

        const val ACTION_PAIRING = "action.PAIRING"
        private const val ACTION_STOP = "action.STOP"
        private const val ACTION_ENTER_PAIRING_CODE = "action.ENTER_PAIRING_CODE"

        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_PARING_CODE = "paring code"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onTimeout(startId: Int) {
        Toast.makeText(this, R.string.wireless_debugging_pairing_timeout_please_restart_the_pairing_service, Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private var wirelessConnectPortResolver: AdbWirelessPortResolver? = null
    private var wirelessPairingPortResolver: AdbWirelessPortResolver? = null

    private fun stopResolver() {
        wirelessConnectPortResolver?.stop()
        wirelessConnectPortResolver = null
        wirelessPairingPortResolver?.stop()
        wirelessPairingPortResolver = null
    }

    private var processing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAIRING -> startResolvePairingPort()
            ACTION_STOP -> stopSelf()
            ACTION_ENTER_PAIRING_CODE -> doPairing(intent)
            else -> startResolveConnectPort()
        }
        return START_REDELIVER_INTENT
    }

    private fun startResolveConnectPort() {
        notifyConnectStarted()
        wirelessConnectPortResolver?.stop()
        wirelessConnectPortResolver = resolveAdbConnectPort(onLost = {
            notifyFailed(R.string.wireless_debugging_not_connected, R.string.wireless_debugging_has_been_closed_please_reopen_it)
        }) { host, port ->
            if (!processing) {
                processing = true
                thread {
                    runCatching {
                        if (AdbClient.openShell(host, port).use { it.sendShellCommand("ls") }.isEmpty()) {
                            notifyFailed(R.string.wireless_debugging_not_connected, R.string.communication_failed_please_retry)
                        } else {
                            notifyConnected()
                            Thread.sleep(3000)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }.onFailure {
                        if (it is SSLHandshakeException) {
                            processing = false
                            Toast.makeText(this, R.string.wireless_debugging_unpaired, Toast.LENGTH_SHORT).show()
                            wirelessConnectPortResolver?.stop()
                            startResolvePairingPort()
                        } else {
                            notifyFailed(R.string.wireless_debugging_not_connected, R.string.please_connect_a_wifi_and_reopen_wireless_debugging)
                        }
                    }
                    processing = false
                }
            }
        }
    }

    private fun startResolvePairingPort() {
        notifyPairingStarted()
        wirelessPairingPortResolver?.stop()
        wirelessPairingPortResolver = resolveAdbPairingPort(onLost = {
            if (processing) {
                notifyFailed(R.string.wireless_debugging_unpaired, R.string.the_pairing_code_has_been_closed_please_reopen_it)
                processing = false
            }
        }) { host, port ->
            if (!processing) {
                processing = true
                notifyEnterPairingCode(host, port)
            }
        }
    }

    private fun doPairing(intent: Intent) {
        thread {
            val pairingCode = RemoteInput.getResultsFromIntent(intent)?.getString(KEY_PARING_CODE) ?: ""
            val host = intent.getStringExtra(KEY_HOST) ?: "127.0.0.1"
            val port = intent.getIntExtra(KEY_PORT, -1)
            runCatching {
                AdbWirelessPairing(host, port, pairingCode).use {
                    it.start()
                    processing = false
                    notifyPaired()
                }
                Thread.sleep(3000)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }.onFailure {
                it.stackTraceToString().logE()
                notifyFailed(R.string.wireless_debugging_unpaired, R.string.entered_an_incorrect_pairing_code)
                processing = false
            }
        }
    }

    private fun createNotificationChannel() = getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            setShowBadge(false)
            setAllowBubbles(false)
        })

    private fun notifyConnectStarted() = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.connection_service_started))
        .setContentText(getString(R.string.please_open_the_wireless_debugging))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .addAction(
            Notification.Action.Builder(
                null, getString(R.string.stop), PendingIntent.getService(
                    this, 1, Intent(this, this::class.java).setAction(ACTION_STOP),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
                )
            ).build()
        ).build().notify()

    private fun notifyPairingStarted() = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.pairing_service_started))
        .setContentText(getString(R.string.please_show_the_pairing_code))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .addAction(
            Notification.Action.Builder(
                null, getString(R.string.stop), PendingIntent.getService(
                    this, 1, Intent(this, this::class.java).setAction(ACTION_STOP),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
                )
            ).build()
        ).build().notify()

    private fun notifyEnterPairingCode(host: String, port: Int) = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.pairing_code_found))
        .setContentText(getString(R.string.please_enter_it_in_the_notification))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .addAction(
            Notification.Action.Builder(
                null, getString(R.string.enter_pairing_code), PendingIntent.getService(
                    this, 1, Intent(this, this::class.java).setAction(ACTION_ENTER_PAIRING_CODE).putExtra(KEY_HOST, host).putExtra(KEY_PORT, port),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
                )
            ).addRemoteInput(RemoteInput.Builder(KEY_PARING_CODE).run {
                setLabel(getString(R.string.paring_code))
                build()
            }).build()
        ).build().notify()

    private fun notifyConnected() = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.wireless_debugging_connect_successful))
        .setSmallIcon(R.drawable.ic_launcher_foreground).build().notify()

    private fun notifyPaired() = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.wireless_debugging_pairing_successful))
        .setSmallIcon(R.drawable.ic_launcher_foreground).build().notify()

    private fun notifyFailed(title: Int, reason: Int) = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(title))
        .setContentText(getString(reason))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .addAction(
            Notification.Action.Builder(
                null, getString(R.string.stop), PendingIntent.getService(
                    this, 1, Intent(this, this::class.java).setAction(ACTION_STOP),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
                )
            ).build()
        ).build().notify()

    private fun Notification.notify() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(1, this, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
    } else {
        startForeground(1, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopResolver()
    }

    override fun onBind(intent: Intent?) = null
}