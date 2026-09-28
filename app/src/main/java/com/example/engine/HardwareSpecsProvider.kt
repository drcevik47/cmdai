package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

data class DeviceHardwareInfo(
    val cpuCores: Int,
    val totalRamMb: Long,
    val freeRamMb: Long,
    val availableStorageMb: Long,
    val gpuType: String,
    val isVulkanSupported: Boolean,
    val isNnapiSupported: Boolean,
    val batteryPct: Int,
    val isCharging: Boolean,
    val thermalStatus: String,
    val deviceModel: String
)

class HardwareSpecsProvider(private val context: Context) {

    fun getHardwareInfo(): DeviceHardwareInfo {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        
        // RAM info
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val freeRamMb = memInfo.availMem / (1024 * 1024)

        // Storage info
        val statFs = StatFs(context.filesDir.path)
        val availableStorageMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)

        // Vulkan / GPU support
        val pm = context.packageManager
        val hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val hasNnapi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

        // Detect GPU Architecture approximation
        val gpuType = when {
            Build.HARDWARE.contains("qcom", ignoreCase = true) -> "Qualcomm Adreno GPU (Vulkan/OpenCL)"
            Build.HARDWARE.contains("exynos", ignoreCase = true) || Build.HARDWARE.contains("mali", ignoreCase = true) -> "ARM Mali GPU (Vulkan/OpenCL)"
            Build.HARDWARE.contains("mt", ignoreCase = true) -> "MediaTek Immortalis / Mali GPU"
            Build.HARDWARE.contains("tensor", ignoreCase = true) -> "Google Tensor TPU / Mali GPU"
            else -> "${Build.HARDWARE.uppercase()} Hardware Accelerator"
        }

        // Battery
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            when (powerManager?.currentThermalStatus) {
                android.os.PowerManager.THERMAL_STATUS_NONE -> "Nominal (Cool)"
                android.os.PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                android.os.PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttle"
                android.os.PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttle"
                else -> "Nominal"
            }
        } else {
            "Nominal"
        }

        return DeviceHardwareInfo(
            cpuCores = cpuCores,
            totalRamMb = totalRamMb,
            freeRamMb = freeRamMb,
            availableStorageMb = availableStorageMb,
            gpuType = gpuType,
            isVulkanSupported = hasVulkan,
            isNnapiSupported = hasNnapi,
            batteryPct = batteryPct,
            isCharging = isCharging,
            thermalStatus = thermalStatus,
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        )
    }
}
