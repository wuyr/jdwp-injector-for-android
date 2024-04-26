package com.wuyr.jdwp_injector_test.activity

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.viewbinding.ViewBinding

/**
 * @author wuyr
 * @github https://github.com/wuyr/jdwp-injector-for-android
 * @since 2024-04-13 2:49 PM
 */
abstract class BaseActivity<VIEW_BINDING : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VIEW_BINDING
    protected abstract val viewBindingClass: Class<VIEW_BINDING>

    private val inflateMethod by lazy { viewBindingClass.getMethod("inflate", LayoutInflater::class.java) }

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView((inflateMethod.invoke(null, layoutInflater) as VIEW_BINDING).apply { binding = this }.root)
        onCreate()
    }

    abstract fun onCreate()

    private var requestPermissionCallback: ((successful: Boolean) -> Unit)? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        requestPermissionCallback?.invoke(permissions.size == grantResults.size && grantResults.none { it == PackageManager.PERMISSION_DENIED })
        requestPermissionCallback = null
    }

    open fun verifyPermissions(callback: (successful: Boolean) -> Unit, vararg permissions: String) {
        if (permissions.none { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED }) {
            callback(true)
        } else {
            requestPermissionCallback = callback
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
    }
}