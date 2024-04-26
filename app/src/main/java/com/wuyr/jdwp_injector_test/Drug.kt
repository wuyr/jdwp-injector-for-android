package com.wuyr.jdwp_injector_test

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.ArrayMap
import android.widget.Toast
import com.wuyr.jdwp_injector_test.log.logE
import com.wuyr.jdwp_injector_test.log.logI
import kotlin.concurrent.thread


/**
 * 注入成功后运行的代码
 *
 * @author wuyr
 * @github https://github.com/wuyr/jdwp-injector-for-android
 * @since 2024-01-12 下午12:27
 */
object Drug {

    /**
     * 获取全局的application实例
     */
    @JvmStatic
    private val application by lazy {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Application
    }

    /**
     * 获取当前正在运行的所有activity
     */
    @JvmStatic
    private val runningActivities: List<Activity>
        get() {
            val activityThread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null) ?: return emptyList()
            return activityThread::class.java.getDeclaredField("mActivities").run {
                isAccessible = true
                (get(activityThread) as ArrayMap<*, *>).values.mapNotNull { record ->
                    record::class.java.getDeclaredField("activity").run {
                        isAccessible = true
                        get(record) as? Activity
                    }
                }
            }
        }

    @JvmStatic
    fun showDialog() {
        "======dex已注入========".logI()
        // 建议在子线程里操作，避免阻塞debugger
        thread {
            Handler(Looper.getMainLooper()).post {
                runCatching {
                    val activities = runningActivities
                    "======一共有${activities.size}个activity正在运行========".logI()
                    if (activities.isEmpty()) {
                        Toast.makeText(application, "no running activities", Toast.LENGTH_LONG).show()
                    } else {
                        activities.forEach {
                            "=============找到activity: $it===========".logI()
                            if (it.isDestroyed) {
                                "=============activity: $it 已destroy===========".logI()
                            } else {
                                "===========开始show dialog: $application============".logI()
                                AlertDialog.Builder(it).setMessage("Hello Debugger from $application").setPositiveButton("close", null).show()
                            }
                        }
                        Toast.makeText(application, "dialog has been showed", Toast.LENGTH_LONG).show()
                    }
                }.onFailure {
                    it.stackTraceToString().logE()
                }
            }
        }
    }

    /**
     * 局限性:  受高版本(android 14)限制，非前台app show toast不会显示，导致不能及时获知运行结果，建议看日志或者用分屏形式使其重回前台再操作
     */
    @JvmStatic
    fun showToast() {
        "======dex已注入========".logI()
        // 建议在子线程里操作，避免阻塞debugger
        thread {
            Handler(Looper.getMainLooper()).post {
                runCatching {
                    "===========开始show toast: $application============".logI()
                    Toast.makeText(application, "Hello Debugger from $application", Toast.LENGTH_LONG).show()
                }.onFailure {
                    it.stackTraceToString().logE()
                }
            }
        }
    }
}
