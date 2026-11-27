// Important instructions:
// - Provide a glossy settings screen to grant Overlay & Accessibility permissions, toggle ON/OFF,
//   adjust overlay opacity (35%..100%), switch D-Pad vs Gesture mode, and pick the Target App.

package shree.remote.sim

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: RemotePrefs
    private lateinit var overlayStatusTv: TextView
    private lateinit var accessStatusTv: TextView
    private lateinit var simToggleTv: TextView
    private lateinit var modeValueTv: TextView
    private lateinit var targetAppTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = RemotePrefs(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        buildGlossySettingsUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * resources.displayMetrics.density

    private fun buildGlossySettingsUi() {
        val scroll = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(12, 18, 38), Color.rgb(6, 10, 22))
            )
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "TV Remote Simulator"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Glossy Floating D-Pad & Gesture Trackpad for TV App Testing"
            textSize = 12f
            setTextColor(Color.rgb(155, 195, 245))
            setPadding(0, dp(4), 0, dp(18))
        })

        simToggleTv = TextView(this).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        root.addView(makeGlossyCard("MASTER SIMULATOR SWITCH", simToggleTv, "Also controllable from Notification Panel & Quick Tile") {
            prefs.isSimulatorEnabled = !prefs.isSimulatorEnabled
            refreshStatus()
        })

        targetAppTv = TextView(this).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(92, 215, 255)) }
        root.addView(makeGlossyCard("TARGET APP LOCK", targetAppTv, "Overlay appears ONLY when this app is open") {
            showTargetAppPicker()
        })

        modeValueTv = TextView(this).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(185, 145, 255)) }
        root.addView(makeGlossyCard("SIMULATION MODE", modeValueTv, "Switch between tactile D-Pad keys or Swipe Gesture pad") {
            prefs.overlayMode = if (prefs.overlayMode == RemotePrefs.MODE_DPAD) RemotePrefs.MODE_GESTURE else RemotePrefs.MODE_DPAD
            refreshStatus()
        })

        val opacityValueTv = TextView(this).apply {
            text = "${prefs.overlayOpacity}%"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(92, 245, 215))
        }
        val opacitySeek = SeekBar(this).apply {
            max = 65
            progress = (prefs.overlayOpacity - 35).coerceIn(0, 65)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val pct = 35 + p
                    opacityValueTv.text = "$pct%"
                    if (fromUser) prefs.overlayOpacity = pct
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(28, 42, 82), Color.rgb(16, 24, 52))
            ).apply {
                cornerRadius = dpF(18f)
                setStroke(dp(1), Color.argb(130, 120, 195, 255))
            }
            addView(TextView(this@MainActivity).apply {
                text = "OVERLAY OPACITY (35% - 100%)"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(165, 190, 230))
            })
            addView(opacityValueTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(6) })
            addView(opacitySeek, LinearLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        overlayStatusTv = TextView(this).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        root.addView(makeGlossyCard("1. DISPLAY OVER OTHER APPS", overlayStatusTv, "Required to float the glossy remote above your target app") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        accessStatusTv = TextView(this).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        root.addView(makeGlossyCard("2. ACCESSIBILITY D-PAD ENGINE", accessStatusTv, "Required to move TV focus, click OK, and watch Target App") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun makeGlossyCard(title: String, valueView: TextView, subtitle: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(28, 42, 82), Color.rgb(16, 24, 52))
            ).apply {
                cornerRadius = dpF(18f)
                setStroke(dp(1), Color.argb(130, 120, 195, 255))
            }
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(165, 190, 230))
            })
            addView(valueView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 11f
                setTextColor(Color.argb(180, 210, 225, 245))
            })
            setOnClickListener { onClick() }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
    }

    private fun refreshStatus() {
        val canOverlay = Settings.canDrawOverlays(this)
        overlayStatusTv.text = if (canOverlay) "✓ Granted" else "Tap to Grant Overlay Permission"
        overlayStatusTv.setTextColor(if (canOverlay) Color.rgb(88, 245, 165) else Color.rgb(255, 130, 130))

        val accessEnabled = isAccessibilityEnabled()
        accessStatusTv.text = if (accessEnabled) "✓ Active" else "Tap to Enable TV Remote D-Pad Engine"
        accessStatusTv.setTextColor(if (accessEnabled) Color.rgb(88, 245, 165) else Color.rgb(255, 195, 95))

        val enabled = prefs.isSimulatorEnabled
        simToggleTv.text = if (enabled) "● ON (Active for Target App)" else "○ OFF (Paused)"
        simToggleTv.setTextColor(if (enabled) Color.rgb(88, 245, 165) else Color.rgb(255, 165, 110))

        modeValueTv.text = if (prefs.overlayMode == RemotePrefs.MODE_DPAD) {
            "Glossy D-Pad Buttons (Up / Down / Left / Right / OK)"
        } else {
            "Glossy Gesture Trackpad (Swipe / Tap OK / Hold Menu)"
        }

        val pkg = prefs.targetPackage
        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
        targetAppTv.text = "$appLabel ($pkg)"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${RemoteAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabledServices.contains(expected)
    }

    private fun showTargetAppPicker() {
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(mainIntent, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }

        val labels = apps.map { "${it.first}\n(${it.second})" }.toTypedArray()
        AlertDialog.Builder(this)
          .setTitle("Select Target App for TV Remote")
          .setItems(labels) { _, which ->
          prefs.targetPackage = apps[which].second
          refreshStatus()
      }
      .show()
    }
}
