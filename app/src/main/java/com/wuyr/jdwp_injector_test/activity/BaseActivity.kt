package com.wuyr.jdwp_injector_test.activity

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
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
}