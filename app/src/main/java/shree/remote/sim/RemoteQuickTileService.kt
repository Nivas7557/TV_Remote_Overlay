// Important instructions:
// - Persist simulator toggle state, overlay mode (dpad vs gesture), target app package, opacity, and position.

package shree.remote.sim

import android.content.Context
import android.content.Intent

class RemotePrefs(private val ctx: Context) {

    companion object {
        private const val PREF_NAME = "tv_remote_sim_prefs"
        const val MODE_DPAD = "dpad"
        const val MODE_GESTURE = "gesture"
        const val ACTION_STATE_CHANGED = "shree.remote.sim.ACTION_STATE_CHANGED"
    }

    private val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isSimulatorEnabled: Boolean
        get() = prefs.getBoolean("sim_enabled", true)
        set(value) {
            prefs.edit().putBoolean("sim_enabled", value).apply()
            notifyStateChanged()
        }

    var overlayMode: String
        get() = prefs.getString("overlay_mode", MODE_DPAD) ?: MODE_DPAD
        set(value) {
            prefs.edit().putString("overlay_mode", value).apply()
            notifyStateChanged()
        }

    var targetPackage: String
        get() = prefs.getString("target_pkg", "shree.tvhome") ?: "shree.tvhome"
        set(value) {
            prefs.edit().putString("target_pkg", value).apply()
            notifyStateChanged()
        }

    var overlayOpacity: Int
        get() = prefs.getInt("overlay_opacity", 100).coerceIn(35, 100)
        set(value) {
            prefs.edit().putInt("overlay_opacity", value.coerceIn(35, 100)).apply()
            notifyStateChanged()
        }

    var overlayX: Int
        get() = prefs.getInt("overlay_x", 60)
        set(value) = prefs.edit().putInt("overlay_x", value).apply()

    var overlayY: Int
        get() = prefs.getInt("overlay_y", 220)
        set(value) = prefs.edit().putInt("overlay_y", value).apply()

    private fun notifyStateChanged() {
        ctx.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(ctx.packageName))
    }
}
