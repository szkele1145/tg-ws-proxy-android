package com.amurcanov.tgwsproxy

import android.content.Context
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

    fun isAlive(): Boolean = try {
        Shizuku.isAlive()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission()
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(code: Int) {
        try {
            Shizuku.requestPermission(code)
        } catch (e: Throwable) {
            // Shizuku 未运行等情况
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
     * 用 shell 权限应用系统级保活豁免。返回 "OK n/n" 或 "ERR ..."。
     * 每条命令失败不影响其他命令执行。
     */
    fun applyElevation(pkg: String): String {
        if (!isAlive()) return "ERR shizuku not alive"
        if (!hasPermission()) return "ERR no permission"
        val cmds = listOf(
            // Doze 白名单（等效"忽略电池优化"，但是强制写入）
            "dumpsys deviceidle whitelist +$pkg",
            // 后台运行豁免
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_IN_FOREGROUND allow",
            // 前台服务与精确闹钟
            "cmd appops set $pkg START_FOREGROUND allow",
            "cmd appops set $pkg SCHEDULE_EXACT_ALARM allow",
            // 取消待机，退出 App 池
            "am set-inactive $pkg false"
        )
        var ok = 0
        val fails = StringBuilder()
        for (c in cmds) {
            if (runShell(c) == 0) {
                ok++
            } else {
                if (fails.isNotEmpty()) fails.append("; ")
                fails.append(c.substringAfter(' ').take(24))
            }
        }
        return if (fails.isEmpty()) "OK $ok/$ok" else "OK $ok/$($cmds.size), skipped: $fails"
    }

    private fun runShell(cmd: String): Int {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val code = process.waitFor()
            try { process.inputStream.close() } catch (_: Throwable) {}
            try { process.errorStream.close() } catch (_: Throwable) {}
            code
        } catch (_: Throwable) {
            -1
        }
    }
}