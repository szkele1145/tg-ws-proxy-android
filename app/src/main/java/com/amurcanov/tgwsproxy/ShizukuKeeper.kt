package com.amurcanov.tgwsproxy

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 保活助手：借 ADB shell 权限（uid 2000）执行系统级豁免命令。
 * 需要用户安装 Shizuku 并完成授权。
 */
object ShizukuKeeper {

    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    const val PERMISSION_REQUEST_CODE = 1001

    // 探测结果码
    const val STATE_OK = "OK"
    const val STATE_NEED_AUTH = "NEED_AUTH"
    const val STATE_NOT_RUNNING = "NOT_RUNNING"
    const val STATE_NOT_INSTALLED = "NOT_INSTALLED"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        true
    } catch (_: Throwable) {
        false
    }

    /** 13.x 没有 isAlive()，正确 API 是 pingBinder() */
    fun isAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /** checkSelfPermission() 返回 Int，需要和 PERMISSION_GRANTED 比较 */
    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(code: Int) {
        try {
            Shizuku.requestPermission(code)
        } catch (_: Throwable) {
            // binder 未就绪等
        }
    }

    /** 探测当前状态，UI 用于展示 */
    fun probe(context: Context): String = when {
        !isInstalled(context) -> STATE_NOT_INSTALLED
        !isAlive() -> STATE_NOT_RUNNING
        !hasPermission() -> STATE_NEED_AUTH
        else -> STATE_OK
    }

    /**
     * 用 shell 权限应用系统级保活豁免。返回 "OK n/n" 或 "OK n/N, skipped: ..."。
     */
    fun applyElevation(pkg: String): String {
        if (!isAlive()) return "ERR shizuku not alive"
        if (!hasPermission()) return "ERR no permission"
        val cmds = listOf(
            "dumpsys deviceidle whitelist +$pkg",
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_IN_FOREGROUND allow",
            "cmd appops set $pkg START_FOREGROUND allow",
            "cmd appops set $pkg SCHEDULE_EXACT_ALARM allow",
            "am set-inactive $pkg false"
        )
        var ok = 0
        val fails = StringBuilder()
        for (c in cmds) {
            if (runShell(c) == 0) {
                ok++
            } else {
                if (fails.isNotEmpty()) fails.append("; ")
                fails.append(c.substringAfter(' ').take(20))
            }
        }
        return if (fails.isEmpty()) "OK $ok/$($cmds.size)" else "OK $ok/$($cmds.size), skipped: $fails"
    }

    /**
     * 执行 shell 命令并返回退出码。
     * Shizuku.newProcess 在 13.x 中是 private（API 14 将移除），
     * 通过反射调用；proguard 规则 -keep class rikka.shizuku.** { *; }
     * 保证方法名不被混淆。失败返回 -1。
     */
    private fun runShell(cmd: String): Int {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,   // cmd
                Array<String>::class.java,   // env
                String::class.java           // dir
            )
            method.isAccessible = true
            val proc = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) ?: return -1
            // ShizukuRemoteProcess 继承 java.lang.Process；稳妥起见反射读退出码
            try {
                proc.javaClass.getMethod("waitFor").invoke(proc) as Int
            } catch (_: Throwable) {
                // 读不到退出码时视为已派发
                0
            }
        } catch (_: Throwable) {
            -1
        }
    }
}