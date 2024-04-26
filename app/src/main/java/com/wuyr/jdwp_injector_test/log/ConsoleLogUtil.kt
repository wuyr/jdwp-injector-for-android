@file:Suppress("unused")

package com.wuyr.jdwp_injector_test.log

import android.util.Log

/**
 * @author wuyr
 * @github https://github.com/wuyr/jdwp-injector-for-android
 * @since 2019-10-08 下午12:13
 */
const val NONE = 6
const val ERROR = 5
const val WARN = 4
const val INFO = 3
const val DEBUG = 2
const val VERBOSE = 1

var printLevel = VERBOSE

fun Any?.logV() {
    if (printLevel <= VERBOSE) {
        Log.v(logTag, "$logTag ${toString()}")
    }
}

fun Any?.logD() {
    if (printLevel <= DEBUG) {
        Log.d(logTag, "$logTag ${toString()}")
    }
}

fun Any?.logI() {
    if (printLevel <= INFO) {
        Log.i(logTag, "$logTag ${toString()}")
    }
}

fun Any?.logW() {
    if (printLevel <= WARN) {
        Log.w(logTag, "$logTag ${toString()}")
    }
}

fun Any?.logW(t: Throwable) {
    if (printLevel <= WARN) {
        Log.w(logTag, "$logTag ${toString()}", t)
    }
}

fun Any?.logWRuntimeException(msg: Any = "") {
    if (printLevel <= WARN) {
        Log.w(logTag, msg.toString(), RuntimeException(msg.toString()))
    }
}

fun Any?.logE() {
    if (printLevel <= ERROR) {
        Log.e(logTag, "$logTag ${toString()}")
    }
}

fun Any?.logE(t: Throwable) {
    if (printLevel <= ERROR) {
        Log.e(logTag, "$logTag ${toString()}", t)
    }
}

fun Any?.logERuntimeException(msg: Any = "") {
    if (printLevel <= ERROR) {
        Log.e(logTag, msg.toString(), RuntimeException(msg.toString()))
    }
}

private val logTag: String
    get() = Thread.currentThread().stackTrace[4].run {
        "(${fileName}:${lineNumber}) $methodName"
    }