package works.mees.jiib.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.state.PrintState

class ScreenAwakePolicyTest {

    @Test
    fun keepScreenOnOff_neverHolds() {
        PrintState.entries.forEach { state ->
            assertEquals(false, shouldHoldScreenOn(keepScreenOn = false, onlyWhilePrinting = false, printState = state))
            assertEquals(false, shouldHoldScreenOn(keepScreenOn = false, onlyWhilePrinting = true, printState = state))
        }
    }

    @Test
    fun keepScreenOnAlways_holdsInEveryState() {
        PrintState.entries.forEach { state ->
            assertEquals(true, shouldHoldScreenOn(keepScreenOn = true, onlyWhilePrinting = false, printState = state))
        }
    }

    @Test
    fun onlyWhilePrinting_holdsOnlyWhilePrintingOrPaused() {
        val expected = mapOf(
            PrintState.Standby to false,
            PrintState.Printing to true,
            PrintState.Paused to true,
            PrintState.Complete to false,
            PrintState.Error to false,
            PrintState.Cancelled to false,
        )
        assertEquals(PrintState.entries.toSet(), expected.keys)
        expected.forEach { (state, hold) ->
            assertEquals("$state", hold, shouldHoldScreenOn(keepScreenOn = true, onlyWhilePrinting = true, printState = state))
        }
    }

    @Test
    fun wakeForPrints_needsBothToggles() {
        assertEquals(false, wakeForPrints(keepScreenOn = false, onlyWhilePrinting = false))
        assertEquals(false, wakeForPrints(keepScreenOn = false, onlyWhilePrinting = true))
        assertEquals(false, wakeForPrints(keepScreenOn = true, onlyWhilePrinting = false))
        assertEquals(true, wakeForPrints(keepScreenOn = true, onlyWhilePrinting = true))
    }
}
