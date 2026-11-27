// Important instructions:
// - Show the glossy remote overlay ONLY when enabled AND the selected target app is in the foreground.
// - Provide a persistent notification with an instant ON/OFF toggle action in the system notification shade.
// - Execute spatial focus moves, click (OK), long-click (Menu), and Back actions on the target app window.

package shree.remote.sim

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        private const val CHANNEL_ID = "tv_remote_sim_channel"
        private const val NOTIF_ID = 7071
        private const val ACTION_TOGGLE_FROM_NOTIF = "shree.remote.sim.ACTION_TOGGLE_FROM_NOTIF"
    }

    private lateinit var prefs: RemotePrefs
    private lateinit var overlay: GlossyRemoteOverlay
    private var currentForegroundPkg: String = ""

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TOGGLE_FROM_NOTIF) {
                prefs.isSimulatorEnabled = !prefs.isSimulatorEnabled
            }
            updateNotification()
            evaluateOverlayVisibility(forceRebuild = true)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = RemotePrefs(this)
        overlay = GlossyRemoteOverlay(
            ctx = this,
            prefs = prefs,
            onAction = ::performRemoteAction,
            onToggleMode = {
                prefs.overlayMode = if (prefs.overlayMode == RemotePrefs.MODE_DPAD) RemotePrefs.MODE_GESTURE else RemotePrefs.MODE_DPAD
            },
            onHideOverlay = { prefs.isSimulatorEnabled = false }
        )

        val filter = IntentFilter().apply {
            addAction(RemotePrefs.ACTION_STATE_CHANGED)
            addAction(ACTION_TOGGLE_FROM_NOTIF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }

        updateNotification()
        evaluateOverlayVisibility(forceRebuild = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (pkg != currentForegroundPkg) {
            currentForegroundPkg = pkg
            evaluateOverlayVisibility(forceRebuild = false)
        }
    }

    override fun onInterrupt() {
        overlay.hide()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        overlay.hide()
    }

    private fun evaluateOverlayVisibility(forceRebuild: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            overlay.hide()
            return
        }
        val activePkg = currentForegroundPkg.ifEmpty { rootInActiveWindow?.packageName?.toString() ?: "" }
        val shouldShow = prefs.isSimulatorEnabled && activePkg == prefs.targetPackage
        if (shouldShow) {
            if (forceRebuild || !overlay.isShowing()) overlay.show()
        } else {
            overlay.hide()
        }
    }
    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "TV Remote Simulator", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }

        val toggleIntent = Intent(ACTION_TOGGLE_FROM_NOTIF).setPackage(packageName)
        val togglePi = PendingIntent.getBroadcast(
            this, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAppPi = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val enabled = prefs.isSimulatorEnabled
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notif = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(if (enabled) "TV Remote Simulator: ACTIVE" else "TV Remote Simulator: PAUSED")
            .setContentText("Target: ${prefs.targetPackage} • Tap action to toggle")
            .setContentIntent(openAppPi)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, if (enabled) "Turn OFF" else "Turn ON", togglePi).build())
            .build()

        nm.notify(NOTIF_ID, notif)
    }

    private fun performRemoteAction(action: GlossyRemoteOverlay.RemoteAction) {
        if (action == GlossyRemoteOverlay.RemoteAction.BACK) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        when (action) {
            GlossyRemoteOverlay.RemoteAction.OK -> focused?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            GlossyRemoteOverlay.RemoteAction.LONG_OK -> focused?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            GlossyRemoteOverlay.RemoteAction.LEFT -> moveSpatialFocus(root, focused, View.FOCUS_LEFT)
            GlossyRemoteOverlay.RemoteAction.RIGHT -> moveSpatialFocus(root, focused, View.FOCUS_RIGHT)
            GlossyRemoteOverlay.RemoteAction.UP -> moveSpatialFocus(root, focused, View.FOCUS_UP)
            GlossyRemoteOverlay.RemoteAction.DOWN -> moveSpatialFocus(root, focused, View.FOCUS_DOWN)
            else -> {}
        }
    }

    private fun moveSpatialFocus(root: AccessibilityNodeInfo, current: AccessibilityNodeInfo?, direction: Int) {
        val candidates = ArrayList<AccessibilityNodeInfo>()
        collectFocusableNodes(root, candidates)
        if (candidates.isEmpty()) return

        if (current == null) {
            candidates.first().performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            return
        }

        val curRect = Rect()
        current.getBoundsInScreen(curRect)
        val curCx = curRect.centerX()
        val curCy = curRect.centerY()

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = Float.MAX_VALUE
        val candRect = Rect()

        for (node in candidates) {
            if (node == current) continue
            node.getBoundsInScreen(candRect)
            if (candRect.isEmpty) continue

            val cx = candRect.centerX()
            val cy = candRect.centerY()
            val dx = cx - curCx
            val dy = cy - curCy

            val validDirection = when (direction) {
                View.FOCUS_LEFT -> dx < -8
                View.FOCUS_RIGHT -> dx > 8
                View.FOCUS_UP -> dy < -8
                View.FOCUS_DOWN -> dy > 8
                else -> false
            }
            if (!validDirection) continue

            val primaryDist = if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) abs(dx) else abs(dy)
            val crossDist = if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) abs(dy) else abs(dx)
            val score = primaryDist + (crossDist * 2.4f)
            if (score < bestScore) {
                bestScore = score
                bestNode = node
            }
        }

        bestNode?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun collectFocusableNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null || !node.isVisibleToUser) return
        if (node.isFocusable && node.isEnabled) out.add(node)
        for (i in 0 until node.childCount) {
            collectFocusableNodes(node.getChild(i), out)
        }
    }
}
