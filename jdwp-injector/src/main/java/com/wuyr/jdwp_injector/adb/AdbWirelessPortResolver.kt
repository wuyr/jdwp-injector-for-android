package com.wuyr.jdwp_injector.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.ServiceInfoCallback
import android.net.nsd.NsdServiceInfo
import android.os.Build

/**
 * @author wuyr
 * @github https://github.com/wuyr/jdwp-injector-for-android
 * @since 2024-04-13 6:07 PM
 */
class AdbWirelessPortResolver private constructor(private val onLost: () -> Unit, private val onResolved: (String, Int) -> Unit) : NsdManager.DiscoveryListener {

    private lateinit var nsdManager: NsdManager

    companion object {

        fun Context.resolveAdbWirelessConnectPort(onLost: () -> Unit = {}, onResolved: (String, Int) -> Unit) = resolveAdbPort("_adb-tls-connect._tcp", onLost, onResolved)

        fun Context.resolveAdbTcpConnectPort(onLost: () -> Unit = {}, onResolved: (String, Int) -> Unit) = resolveAdbPort("_adb._tcp", onLost, onResolved)

        fun Context.resolveAdbPairingPort(onLost: () -> Unit = {}, onResolved: (String, Int) -> Unit) = resolveAdbPort("_adb-tls-pairing._tcp", onLost, onResolved)

        private fun Context.resolveAdbPort(serviceType: String, onLost: () -> Unit, onResolved: (String, Int) -> Unit) = AdbWirelessPortResolver(onLost, onResolved).apply {
            nsdManager = getSystemService(NsdManager::class.java).also {
                it.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, this)
            }
        }
    }

    fun stop() {
        if (discoveryStarted) {
            discoveryStarted = false
            nsdManager.stopServiceDiscovery(this)
        }
    }

    private var discoveryStarted = false

    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        discoveryStarted = false

    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        discoveryStarted = false
    }

    override fun onDiscoveryStarted(serviceType: String) {
        discoveryStarted = true
    }

    override fun onDiscoveryStopped(serviceType: String) {
        discoveryStarted = false
    }

    private var foundServiceName = ""

    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        foundServiceName = serviceInfo.serviceName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nsdManager.registerServiceInfoCallback(serviceInfo, { it.run() }, object : ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                }

                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    onResolved(serviceInfo.hostAddresses.firstOrNull()?.hostAddress ?: "127.0.0.1", serviceInfo.port)
                    nsdManager.unregisterServiceInfoCallback(this)
                }

                override fun onServiceLost() {
                    nsdManager.unregisterServiceInfoCallback(this)
                }

                override fun onServiceInfoCallbackUnregistered() {
                }
            })
        } else {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    onResolved(serviceInfo.host.hostAddress ?: "127.0.0.1", serviceInfo.port)
                }
            })
        }
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
        if (discoveryStarted && foundServiceName == serviceInfo.serviceName) {
            onLost()
        }
    }
}