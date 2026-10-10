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
        super.onServiceConnected()
        val app = applicationContext
        Thread {
            try {
                if (!ProxyService.isRunning.value) {
                    android.util.Log.w("KeepAliveA11y", "rebind detected, reviving proxy")
                    kotlinx.coroutines.runBlocking {
                        ProxyController.startFromSavedSettings(app)
                    }
                    android.util.Log.w("KeepAliveA11y", "revive dispatched")
                } else {
                    android.util.Log.w("KeepAliveA11y", "service connected, proxy already running")
                }
            } catch (e: Throwable) {
                android.util.Log.w("KeepAliveA11y", "revive failed: " + e)
            }
        }.start()
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