package com.amurcanov.tgwsproxy.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/**
 * 后台保活辅助：引导用户把本应用加入系统的电池优化白名单，
 * 并尝试跳转到各厂商的自启动 / 后台管理页面。
 *
 * 说明：
 * - 标准 Android 只提供「忽略电池优化」这一种官方机制（Doze 白名单）。
 * - 国产 ROM（vivo/iQOO 的 OriginOS、小米 MIUI、华为 EMUI 等）另有一套
 *   私有的自启动 / 后台高耗电管理，没有公开 API，只能靠枚举候选组件尝试跳转，
 *   失败时退回本应用的详情页。
 */
object BatteryHelper {

    /** 是否已经在电池优化白名单中（即「不优化」）。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 弹出系统的「忽略电池优化」授权框。
     * 需要清单里声明 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS。
     * 部分 ROM 会直接打开列表页而不是弹框，属于正常现象。
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            // 有些 ROM 不支持带包名的请求，退回打开电池优化列表页
            openBatteryOptimizationSettings(context)
        }
    }

    /** 打开系统的电池优化列表页。 */
    fun openBatteryOptimizationSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Throwable) {
                // 继续尝试下一个
            }
        }
        toast(context, "无法打开系统设置")
    }

    /**
     * 尝试跳转到厂商的「自启动管理」页面。
     * 逐个尝试已知的组件名，成功一个就返回；全部失败则打开应用详情页。
     */
    fun openAutoStartSettings(context: Context) {
        val components = listOf(
            // vivo / iQOO (OriginOS / Funtouch)
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.PurviewTabActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            "com.iqoo.secure" to "com.iqoo.secure.MainActivity",
            // 小米 / 红米
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            // 华为 / 荣耀
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            // OPPO / 一加 / realme
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            // 三星
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            // 魅族
            "com.meizu.safe" to "com.meizu.safe.security.SHOW_APPSEC"
        )
        for ((pkg, cls) in components) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(pkg, cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Throwable) {
                // 该组件不存在，试下一个
            }
        }
        // 全都不行：退回应用详情页，让用户手动找
        openAppDetails(context)
    }

    /** 打开本应用的系统详情页（权限、电池等入口都在这）。 */
    fun openAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            toast(context, "无法打开应用详情页")
        }
    }

    /** 判断是否是 vivo / iQOO 机型（用于给出针对性的提示文案）。 */
    fun isVivoFamily(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return brand.contains("vivo") || brand.contains("iqoo") ||
                manufacturer.contains("vivo") || manufacturer.contains("iqoo")
    }

    private fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
