package com.wuyr.jdwp_injector_test.activity

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import com.wuyr.jdwp_injector.adb.AdbClient
import com.wuyr.jdwp_injector.adb.AdbWirelessPortResolver
import com.wuyr.jdwp_injector.adb.AdbWirelessPortResolver.Companion.resolveAdbConnectPort
import com.wuyr.jdwp_injector.debugger.JdwpInjector
import com.wuyr.jdwp_injector.exception.AdbCommunicationException
import com.wuyr.jdwp_injector.exception.ProcessNotFoundException
import com.wuyr.jdwp_injector_test.BuildConfig
import com.wuyr.jdwp_injector_test.Drug
import com.wuyr.jdwp_injector_test.R
import com.wuyr.jdwp_injector_test.adapter.AppItem
import com.wuyr.jdwp_injector_test.adapter.AppListAdapter
import com.wuyr.jdwp_injector_test.databinding.ActivityMainBinding
import com.wuyr.jdwp_injector_test.log.logE
import com.wuyr.jdwp_injector_test.service.WirelessPairingService
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import javax.net.ssl.SSLHandshakeException

/**
 * @author wuyr
 * @github https://github.com/wuyr/jdwp-injector-for-android
 * @since 2024-04-13 3:33 PM
 */
class MainActivity(override val viewBindingClass: Class<ActivityMainBinding> = ActivityMainBinding::class.java) : BaseActivity<ActivityMainBinding>() {

    override fun onCreate() {
        binding.connectButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                verifyPermissions({ granted ->
                    if (granted) {
                        tryConnect()
                    } else {
                        Toast.makeText(this, R.string.you_have_denied_notification_permission, Toast.LENGTH_LONG).show()
                    }
                }, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                getSystemService(NotificationManager::class.java).let { notificationManager ->
                    if (notificationManager.areNotificationsEnabled()) {
                        tryConnect()
                    } else {
                        Toast.makeText(this, R.string.please_grant_notification_permission, Toast.LENGTH_LONG).show()
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                    }
                }
            }
        }
    }

    private val handle = Handler(Looper.getMainLooper())
    private val resolveTimeoutTask = Runnable {
        Toast.makeText(this, R.string.wireless_debugging_not_available_have_you_opened_it, Toast.LENGTH_LONG).show()
        startForegroundService(Intent(this, WirelessPairingService::class.java))
        goToDevelopmentSettings()
    }

    private var wirelessPortResolver: AdbWirelessPortResolver? = null
    private var globalDebuggable = false
    private var adbHost = ""
    private var adbPort = 0

    private fun tryConnect() {
        if (Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, if (Build.TYPE == "eng") 1 else 0) == 0) {
            Toast.makeText(this, R.string.development_settings_has_been_disabled_please_reopen_it, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_SETTINGS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            return
        }
        wirelessPortResolver?.stop()
        wirelessPortResolver = resolveAdbConnectPort(onLost = {
            handle.removeCallbacks(resolveTimeoutTask)
            Toast.makeText(this, R.string.wireless_debugging_not_available, Toast.LENGTH_LONG).show()
        }) { host, port ->
            if (handle.hasCallbacks(resolveTimeoutTask)) {
                adbHost = host
                adbPort = port
                handle.removeCallbacks(resolveTimeoutTask)
                runCatching {
                    if (AdbClient.openShell(host, port).use { it.sendShellCommand("getprop ro.debuggable") }.also { response ->
                            globalDebuggable = response.split("\r\n").filterNot { it.isEmpty() }.run { if (size > 1) this[1] else "" } == "1"
                        }.isNotEmpty()) {
                        runOnUiThread { loadDebuggableAppList() }
                    }
                }.onFailure {
                    if (it is SSLHandshakeException) {
                        Toast.makeText(this, R.string.wireless_debugging_unpaired_now_starting_pairing_service, Toast.LENGTH_LONG).show()
                        startForegroundService(Intent(this, WirelessPairingService::class.java).setAction(WirelessPairingService.ACTION_PAIRING))
                    } else {
                        Toast.makeText(this, R.string.wireless_debugging_not_available_now_please_connect_a_wifi_and_reopen_it, Toast.LENGTH_LONG).show()
                        startForegroundService(Intent(this, WirelessPairingService::class.java))
                    }
                    goToDevelopmentSettings()
                }
            }
        }
        handle.removeCallbacks(resolveTimeoutTask)
        handle.postDelayed(resolveTimeoutTask, 2000L)
    }

    private lateinit var adapter: AppListAdapter
    private val threadPool = Executors.newSingleThreadExecutor()

    private fun loadDebuggableAppList() {
        binding.connectButton.visibility = View.GONE
        binding.appList.visibility = View.VISIBLE
        binding.appList.adapter = AppListAdapter(this).apply {
            adapter = this
            onOpenButtonClick = { item -> openApplication(item.packageName, item.activityClassName) }
            onShowDialogButtonClick = { item -> doInject(item.packageName, "showDialog") }
            onShowToastButtonClick = { item -> doInject(item.packageName, "showToast") }
        }
        loadList()
    }

    private fun loadList() {
        adapter.items = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.GET_META_DATA
        ).mapNotNull { resolveInfo ->
            val applicationInfo = (resolveInfo.activityInfo ?: resolveInfo.serviceInfo ?: resolveInfo.providerInfo)?.applicationInfo
            if (applicationInfo != null && applicationInfo.packageName != BuildConfig.APPLICATION_ID &&
                (globalDebuggable || (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            ) {
                AppItem(
                    resolveInfo.loadIcon(packageManager).apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) },
                    "${resolveInfo.loadLabel(packageManager)}\n(${resolveInfo.activityInfo.packageName})",
                    resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name
                )
            } else null
        }.sortedBy { it.appName }.toMutableList()
    }

    private fun openApplication(packageName: String, activityClassName: String) {
        threadPool.execute {
            runCatching {
                AdbClient.openShell(adbHost, adbPort).use { adb ->
                    adb.sendShellCommand("am force-stop $packageName")
                    Thread.sleep(250)
                    startActivity(Intent().apply {
                        component = ComponentName(packageName, activityClassName)
                        flags = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }, Bundle().apply { putInt("android.activity.windowingMode", 3) })
                }
            }
        }
    }

    private fun doInject(packageName: String, injectMethodName: String) {
        threadPool.execute {
            runCatching {
                JdwpInjector.start(adbHost, adbPort, packageName, packageName, packageResourcePath, Drug::class.java.name, injectMethodName)
            }.onFailure {
                val message = getString(
                    when (it) {
                        is ProcessNotFoundException -> R.string.target_process_not_found_please_open_it_first
                        is SocketTimeoutException -> R.string.time_out_to_connect_to_target_process
                        is AdbCommunicationException -> R.string.adb_communication_failure
                        else -> {
                            it.stackTraceToString().logE()
                            R.string.unknown_error
                        }
                    }
                )
                runOnUiThread {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goToDevelopmentSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wirelessPortResolver?.stop()
        wirelessPortResolver = null
    }
}
