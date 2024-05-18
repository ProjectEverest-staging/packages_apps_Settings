/*
 * Copyright (C) 2024 EverestOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import android.graphics.Point
import android.util.DisplayMetrics
import com.android.internal.os.PowerProfile
import com.android.internal.util.MemInfoReader
import com.android.settings.R
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.roundToInt

object EverestSpecUtils {
    private const val DEVICE_NAME_MODEL_PROPERTY = "ro.product.system.model"
    private const val EVEREST_CPU_MODEL_PROPERTY = "ro.everest.cpu"
    private const val FALLBACK_CPU_MODEL_PROPERTY = "ro.board.platform"
    private const val POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile"
    private val GB2MB = BigDecimal(1024)

    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as String
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun getTotalInternalMemorySize(): String {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val totalStorageBytes = stat.totalBytes
        val totalStorageGB = totalStorageBytes / (1024.0 * 1024.0 * 1024.0)
        val roundedStorageGB = roundToNearestKnownStorageSize(totalStorageGB)
        return if (roundedStorageGB >= 1024) {
            "${roundedStorageGB / 1024} TB"
        } else {
            "$roundedStorageGB GB"
        }
    }

    private fun roundToNearestKnownStorageSize(storageGB: Double): Int {
        val knownSizes = arrayOf(16, 32, 64, 128, 256, 512, 1024)
        if (storageGB <= 8) return ceil(storageGB).toInt()
        for (size in knownSizes) {
            if (storageGB <= size) return size
        }
        return ceil(storageGB).toInt()
    }

    fun getTotalRAM(): String {
        val memInfoReader = MemInfoReader()
        memInfoReader.readMemInfo()
        val totalMemoryBytes = memInfoReader.totalSize
        val totalMemoryGB = totalMemoryBytes / (1024.0 * 1024.0 * 1024.0)
        val roundedMemoryGB = roundToNearestKnownRamSize(totalMemoryGB)
        return "$roundedMemoryGB GB"
    }

    private fun roundToNearestKnownRamSize(memoryGB: Double): Int {
        val knownSizes = arrayOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 32, 48, 64)
        if (memoryGB <= 0) return 1
        for (size in knownSizes) {
            if (memoryGB <= size) return size
        }
        return knownSizes.last()
    }

    fun getDeviceName(): String {
        val deviceModel = getSystemProperty(DEVICE_NAME_MODEL_PROPERTY)
        return if (deviceModel.isNotEmpty()) deviceModel else "unknown"
    }

    fun getProcessorModel(context: Context): String {
        val customProcessorModel = context.getString(R.string.CUSTOM_CPU_MODEL)
        if (customProcessorModel.isNotEmpty()) {
            return customProcessorModel
        }

        val cpuModelEverest = getSystemProperty(EVEREST_CPU_MODEL_PROPERTY)
        val cpuModelFallback = getSystemProperty(FALLBACK_CPU_MODEL_PROPERTY)
        return when {
            cpuModelEverest.isNotEmpty() -> cpuModelEverest
            cpuModelFallback.isNotEmpty() -> cpuModelFallback
            else -> "unknown"
        }
    }

    fun getScreenRes(context: Context): String {
        val customScreenRes = context.getString(R.string.CUSTOM_SCREEN_RESOLUTION)
        if (customScreenRes.isNotEmpty()) {
            return customScreenRes
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x
        val height = size.y + getNavigationBarHeight(windowManager)
        return "$width x $height"
    }

    private fun getNavigationBarHeight(wm: WindowManager): Int {
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        val usableHeight = metrics.heightPixels
        wm.defaultDisplay.getRealMetrics(metrics)
        val realHeight = metrics.heightPixels
        return if (realHeight > usableHeight) realHeight - usableHeight else 0
    }

    fun getBatteryInfo(context: Context): String {
        val customBatteryInfo = context.getString(R.string.CUSTOM_BATTERY_INFO)
        if (customBatteryInfo.isNotEmpty()) {
            return customBatteryInfo
        }

        return "${getBatteryCapacity(context)} mAh"
    }

    private fun getBatteryCapacity(context: Context): Int {
        var powerProfile: Any? = null
        var batteryCapacity = 0.0
        try {
            powerProfile = Class.forName(POWER_PROFILE_CLASS)
                .getConstructor(Context::class.java).newInstance(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            batteryCapacity = Class.forName(POWER_PROFILE_CLASS)
                .getMethod("getAveragePower", String::class.java)
                .invoke(powerProfile, "battery.capacity") as Double
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return batteryCapacity.roundToInt()
    }

    fun getStorageInfo(context: Context): String {
        val customStorageInfo = context.getString(R.string.CUSTOM_STORAGE_INFO)
        if (customStorageInfo.isNotEmpty()) {
            return customStorageInfo
        }

        return getTotalInternalMemorySize()
    }

    fun getRAMInfo(context: Context): String {
        val customRAMInfo = context.getString(R.string.CUSTOM_RAM_INFO)
        if (customRAMInfo.isNotEmpty()) {
            return customRAMInfo
        }

        return getTotalRAM()
    }

    fun getStorageAndRAMInfo(context: Context): String {
        val storage = getStorageInfo(context)
        val ram = getRAMInfo(context)
        return "$ram | $storage"
    }
}
