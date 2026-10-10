package com.amurcanov.tgwsproxy

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.accessibilityservice.AccessibilityServiceInfo

/**
 * 保活锚点（GKD 同款原理）：
 * 无障碍服务由系统绑定，划掉任务后系统杀不掉绑定，进程死亡时系统还会自动重建。
 * 本服务不做任何无障碍逻辑，只在被系统拉起时检查：代理没跑就重启它。
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        // 被动锚点策略（对齐 GKD 的实际做法）：
        // 系统绑定本服务 → 进程被拉起/保住 → 划掉时进程和前台服务都不死。
        // 刻意【不】在这里启动前台服务：后台启 FGS 的异常路径一旦抛到主线程，
        // 进程崩溃 → 系统重绑重试 → 连环崩溃 → 系统撤销本服务（正是之前"开了就弹回"的根因）。
        // 代理若真被杀，由 onTaskRemoved 快速闹钟和 15 分钟周期闹钟负责复活。
        super.onServiceConnected()
        android.util.Log.w("KeepAliveA11y", "service connected (passive anchor)")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        /** 本服务是否已在系统无障碍列表中启用 */
        fun isEnabled(context: Context): Boolean {
            return try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                        as android.view.accessibility.AccessibilityManager
                val expected = context.packageName + "/" + KeepAliveAccessibilityService::class.java.name
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.id == expected }
            } catch (_: Throwable) {
                false
            }
        }
    }
}