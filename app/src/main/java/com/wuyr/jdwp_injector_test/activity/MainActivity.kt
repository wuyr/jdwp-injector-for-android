package com.wuyr.jdwp_injector_test.activity

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
import com.wuyr.jdwp_injector.adb.AdbWirelessPairing
import com.wuyr.jdwp_injector.adb.AdbWirelessPortResolver
import com.wuyr.jdwp_injector.adb.AdbWirelessPortResolver.Companion.resolveAdbPairingPort
import com.wuyr.jdwp_injector.adb.AdbWirelessPortResolver.Companion.resolveAdbTcpConnectPort
import com.wuyr.jdwp_injector.adb.AdbWirelessPortResolver.Companion.resolveAdbWirelessConnectPort
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
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import javax.net.ssl.SSLHandshakeException
import kotlin.concurrent.thread

/**
 * @author wuyr
 * @github https://github.com/wuyr/jdwp-injector-for-android
 * @since 2024-04-13 3:33 PM
 */
class MainActivity(override val viewBindingClass: Class<ActivityMainBinding> = ActivityMainBinding::class.java) : BaseActivity<ActivityMainBinding>() {

    private var tcpConnectPortResolver: AdbWirelessPortResolver? = null
    private var wirelessConnectPortResolver: AdbWirelessPortResolver? = null
    private var wirelessPairingPortResolver: AdbWirelessPortResolver? = null
    private val handle = Handler(Looper.getMainLooper())
    private var globalDebuggable = false
    private var connectHost = ""
    private var connectPort = 0
    private var pairingHost = ""
    private var pairingPort = 0

    override fun onCreate() {
        binding.apply {
            connectButton.setOnClickListener { tryConnect() }
            performPairingButton.setOnClickListener { doPairing() }
        }
    }

    private fun tryConnect() {
        if (Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, if (Build.TYPE == "eng") 1 else 0) == 0) {
            Toast.makeText(this, R.string.development_settings_has_been_disabled_please_reopen_it, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_SETTINGS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            return
        }
        setupConnectPortResolver()
        handle.removeCallbacks(resolveTimeoutTask)
        handle.postDelayed(resolveTimeoutTask, 2000L)
    }

    private fun setupConnectPortResolver() {
        tcpConnectPortResolver?.stop()
        tcpConnectPortResolver = resolveAdbTcpConnectPort { host, port ->
            onConnectPortDetected(host, port)
        }
        wirelessConnectPortResolver?.stop()
        wirelessConnectPortResolver = resolveAdbWirelessConnectPort(onLost = {
            handle.removeCallbacks(resolveTimeoutTask)
            runOnUiThread {
                Toast.makeText(this, R.string.wireless_debugging_not_available, Toast.LENGTH_LONG).show()
            }
        }) { host, port ->
            onConnectPortDetected(host, port)
        }
    }

    private val resolveTimeoutTask = Runnable {
        Toast.makeText(this, R.string.wireless_debugging_not_available_have_you_opened_it, Toast.LENGTH_LONG).show()
        goToDevelopmentSettings(false)
    }

    private val connectFailedTask = Runnable { goToDevelopmentSettings(handshakeFailed) }

    private var handshakeFailed = false
    private var triedConnectPort = HashSet<Int>()
    private var connected = false

    private fun onConnectPortDetected(host: String, port: Int) {
        if (connected) return
        if (!triedConnectPort.add(port)) return
        handle.removeCallbacks(resolveTimeoutTask)
        handle.removeCallbacks(connectFailedTask)
        runCatching {
            if (AdbClient.openShell(host, port).use { it.sendShellCommand("getprop ro.debuggable") }.also { response ->
                    globalDebuggable = response.split("\r\n").filterNot { it.isEmpty() }.run { if (size > 1) this[1] else "" } == "1"
                }.isNotEmpty()) {
                connected = true
                handle.removeCallbacks(connectFailedTask)
                stopPortResolver()
                connectHost = host
                connectPort = port
                runOnUiThread { loadDebuggableAppList() }
            }
        }.onFailure {
            handle.removeCallbacks(connectFailedTask)
            handle.postDelayed(connectFailedTask, 1000L)
            handshakeFailed = it is SSLHandshakeException
        }
    }

    private fun doPairing() {
        thread {
            val pairingCode = binding.pairingCodeView.text.toString()
            if (pairingCode.isEmpty()) {
                return@thread
            }
            runCatching {
                AdbWirelessPairing(pairingHost, pairingPort, pairingCode).use {
                    it.start()
                    runOnUiThread {
                        binding.apply {
                            connectHintView.setText(R.string.wireless_debugging_pairing_successful)
                            pairingCodeView.visibility = View.GONE
                            performPairingButton.visibility = View.GONE
                        }
                    }
                    triedConnectPort.clear()
                    tryConnect()
                }
            }.onFailure {
                it.stackTraceToString().logE()
                runOnUiThread {
                    binding.apply {
                        connectHintView.setText(R.string.entered_an_incorrect_pairing_code)
                        pairingCodeView.setText("")
                    }
                }
            }
        }
    }

    private var developmentSettingsStarted = false

    private fun goToDevelopmentSettings(pairingNeeded: Boolean) {
        binding.apply {
            connectButton.visibility = View.GONE
            wirelessPairingRoot.visibility = View.VISIBLE
            if (pairingNeeded) {
                setupPairingPortResolver()
            } else {
                connectHintView.setText(R.string.please_open_the_wireless_debugging)
            }
        }
        if (developmentSettingsStarted) return
        developmentSettingsStarted = true
        startActivity(runCatching {
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }.getOrDefault(Intent(Settings.ACTION_SETTINGS)).apply {
            flags = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }, Bundle().apply { putInt("android.activity.windowingMode", 3) })
    }

    private fun setupPairingPortResolver() {
        binding.apply {
            connectHintView.setText(R.string.please_show_the_pairing_code)
            wirelessPairingPortResolver?.stop()
            wirelessPairingPortResolver = resolveAdbPairingPort(onLost = {
                runOnUiThread {
                    connectHintView.setText(R.string.the_pairing_code_has_been_closed_please_reopen_it)
                    pairingCodeView.visibility = View.GONE
                    performPairingButton.visibility = View.GONE
                }
            }) { host, port ->
                pairingHost = host
                pairingPort = port
                runOnUiThread {
                    connectHintView.setText(R.string.please_enter_pairing_code)
                    pairingCodeView.visibility = View.VISIBLE
                    performPairingButton.visibility = View.VISIBLE
                }
            }
        }
    }

    private lateinit var adapter: AppListAdapter
    private val threadPool = Executors.newSingleThreadExecutor()

    private fun loadDebuggableAppList() {
        binding.connectButton.visibility = View.GONE
        binding.wirelessPairingRoot.visibility = View.GONE
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
                AdbClient.openShell(connectHost, connectPort).use { adb ->
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
                JdwpInjector.start(connectHost, connectPort, packageName, packageName, packageResourcePath, Drug::class.java.name, injectMethodName)
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

    override fun onDestroy() {
        super.onDestroy()
        stopPortResolver()
    }

    private fun stopPortResolver() {
        tcpConnectPortResolver?.stop()
        wirelessConnectPortResolver?.stop()
        wirelessPairingPortResolver?.stop()
    }
}
