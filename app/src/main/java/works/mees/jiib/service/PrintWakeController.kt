package works.mees.jiib.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import works.mees.jiib.state.PrintState
import works.mees.jiib.ui.settings.isActivePrint

/**
 * The service half of the "only while printing" screen mode
 * ([works.mees.jiib.di.AppContainer.wakeForPrints]). The shell's window only holds the screen while a
 * print is active, so between prints the screen sleeps on the system timeout. While the mode is on this:
 *
 *  - holds a PARTIAL wake lock + a high-perf Wi-Fi lock, so the Moonraker socket survives screen-off
 *    (Fire OS drops Wi-Fi on sleep otherwise and a print start would never be seen). A dedicated
 *    display is expected to be on mains power;
 *  - on a transition INTO an active print, turns the screen on and brings the shell forward, whose
 *    window then holds the screen for the rest of the print.
 *
 * Locks are released when the mode turns off and when [run] is cancelled (service destroyed).
 */
internal class PrintWakeController(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var cpuLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    suspend fun run(enabled: Flow<Boolean>, printState: Flow<PrintState>) {
        try {
            enabled.collectLatest { on ->
                if (!on) {
                    releaseLocks()
                    return@collectLatest
                }
                acquireLocks()
                // null until the first state arrives: enabling the mode mid-print must not wake.
                var wasActive: Boolean? = null
                printState.map(::isActivePrint).distinctUntilChanged().collect { active ->
                    if (active && wasActive == false) wakeScreen()
                    wasActive = active
                }
            }
        } finally {
            releaseLocks()
        }
    }

    // No timeout by design: the locks live exactly as long as the mode is on (released in run()).
    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        if (cpuLock == null) {
            cpuLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jiib:print-watch").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            // WIFI_MODE_FULL_HIGH_PERF is deprecated on API 34+ but still honored; it is the mode that
            // keeps Wi-Fi associated through screen-off on the Fire OS 5 (API 22) target.
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "jiib:print-watch").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        cpuLock?.takeIf { it.isHeld }?.release()
        cpuLock = null
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }

    private fun wakeScreen() {
        Log.i(TAG, "print started — waking screen")
        if (!powerManager.isInteractive) {
            // A screen wake lock with ACQUIRE_CAUSES_WAKEUP is the only way a service can turn the screen
            // on; deprecated in favor of activity flags, which need an already-running activity.
            @Suppress("DEPRECATION")
            powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "jiib:print-wake",
            ).acquire(WAKE_HOLD_MS)
        }
        // Bring the shell forward the way the launcher does (resumes the existing task). Background
        // activity starts are blocked on API 29+; there the wake lock alone lights the screen.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "could not bring jiib forward", it) }
    }

    private companion object {
        const val TAG = "JiibPrintWake"

        // Long enough for the shell to resume and take over via its own keep-screen-on flag.
        const val WAKE_HOLD_MS = 10_000L
    }
}
