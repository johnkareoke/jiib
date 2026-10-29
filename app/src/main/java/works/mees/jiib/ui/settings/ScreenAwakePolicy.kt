package works.mees.jiib.ui.settings

import works.mees.jiib.state.PrintState

/** A print is "active" for screen purposes while it is running or paused (the job still owns the bed). */
fun isActivePrint(state: PrintState): Boolean =
    state == PrintState.Printing || state == PrintState.Paused

/**
 * Whether the shell should hold the screen awake: never when [keepScreenOn] is off; always when it is
 * on; only during an [isActivePrint] when [onlyWhilePrinting] narrows it.
 */
fun shouldHoldScreenOn(keepScreenOn: Boolean, onlyWhilePrinting: Boolean, printState: PrintState): Boolean =
    keepScreenOn && (!onlyWhilePrinting || isActivePrint(printState))

/**
 * Whether the service should keep the connection alive with the screen off and wake the screen when a
 * print starts — the "only while printing" mode.
 */
fun wakeForPrints(keepScreenOn: Boolean, onlyWhilePrinting: Boolean): Boolean =
    keepScreenOn && onlyWhilePrinting
