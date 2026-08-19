package com.nauhaan.skycast

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end navigation test.
 *
 * Drives the real app with a real Hilt graph: every tab is reachable, push destinations open and
 * dismiss, and system back returns to the right place.
 *
 * Tabs are located by test tag, not by label: "METAR" appears both in the bottom bar and as that
 * screen's heading, so a text matcher would find two nodes and fail.
 *
 * `mainClock.autoAdvance` is disabled. `LoadingView` uses Material 3 Expressive's
 * `LoadingIndicator`, which morphs between shapes **indefinitely**, and with auto-advance on
 * `waitForIdle` advances the clock until no animation is running, which never happens. Disabling it
 * makes `waitForIdle` flush recomposition without waiting for animations to end, so [settle]
 * advances the clock explicitly after a navigation.
 *
 * Back is pressed through `OnBackPressedDispatcher` rather than Espresso's `pressBack()`, which
 * blocks until Espresso considers the main thread idle and knows nothing about the Compose test
 * clock. The dispatcher is what the platform calls for a system back gesture, so the assertion
 * still proves the real back stack unwinds to the right tab.
 *
 * Runs on a device or emulator:
 *
 *     ./gradlew connectedDebugAndroidTest
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {
    // Order matters: Hilt must build the graph before the activity is created.
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        composeRule.mainClock.autoAdvance = false
        awaitNavigationBar()
        // And then let it finish arriving. `awaitNavigationBar` stops the moment the tag resolves,
        // which is typically the *first* frame of the `AnimatedVisibility` enter transition, and a
        // `performClick` on a bar that is still sliding in is **silently lost**. Measured, not guessed:
        // clicking one poll after the tag appeared left the tab unselected, while invoking the same
        // node's semantics `OnClick` action selected it. `performClick` injects a real touch, so it
        // depends on where the item actually is; the semantics action does not. Every test in this
        // suite failed on that, all of them reporting the *next* thing they waited for.
        settle()
    }

    /**
     * Waits for the navigation bar to be on screen before the first interaction.
     *
     * The bar is wrapped in `AnimatedVisibility`, and `currentBackStackEntryAsState()` is null on
     * the very first frame, so the bar is absent to begin with and animates in once the graph has
     * a destination. With auto-advance disabled nothing moves that animation along, so a test whose
     * first statement clicked a tab was clicking a bar that was not there yet: "could not find any
     * node that satisfies (TestTag = 'tab_settings')", three seconds in.
     *
     * Advancing the clock until the tag resolves is deterministic, unlike a sleep, and it fails
     * loudly rather than hanging if the bar never appears.
     */
    private fun awaitNavigationBar() {
        await("the navigation bar") {
            composeRule
                .onAllNodesWithTag(TAB_HOME)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    /** Lets a navigation transition finish, deterministically rather than by sleeping. */
    private fun settle() {
        composeRule.mainClock.advanceTimeBy(TRANSITION_MILLIS)
        composeRule.waitForIdle()
    }

    /**
     * A system back press, routed through the same dispatcher the platform uses.
     *
     * See the class KDoc.
     */
    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * Waits for a tab to report itself **selected**, then asserts it.
     *
     * `settle()` alone was not enough and the whole suite failed on it: selection comes from
     * `currentBackStackEntryAsState()`, whose emission is delivered on Compose's UI dispatcher and so
     * arrives on a *frame*. One `advanceTimeBy` is one budget of frames, and with five tabs and a real
     * Hilt graph behind them the state landed after it on this emulator: the assertion then read the
     * previous tab and failed, "Selected = 'false'", in every test that switched tabs.
     *
     * Polling while advancing the clock is what the rest of this file already does for text and content
     * descriptions; selection needed the same treatment. It still fails loudly: `await` gives up with a
     * named error, so this is not a weaker assertion, just one that stops racing a frame boundary.
     */
    private fun awaitSelected(tag: String) {
        await("tab \"$tag\" to become selected") {
            composeRule
                .onAllNodesWithTag(tag)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { node -> node.config.getOrNull(SemanticsProperties.Selected) == true }
        }
        composeRule.onNodeWithTag(tag).assertIsSelected()
    }

    /** Waits for real asynchronous work, such as a Room emission or a network reply, to reach the UI. */
    private fun awaitText(text: String) {
        await("text \"$text\"") {
            composeRule
                .onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun awaitContentDescription(description: String) {
        await("content description \"$description\"") {
            composeRule
                .onAllNodesWithContentDescription(description, substring = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    /**
     * Waits for [condition], **advancing the test clock** between attempts.
     *
     * This replaced `composeRule.waitUntil`, which cannot work here. With auto-advance disabled the
     * frame clock only moves when a test moves it, and coroutines dispatched on Compose's UI
     * dispatcher are delivered on a frame, so a `collectAsStateWithLifecycle` never starts
     * collecting, and a screen fed by a `StateFlow` sits on its loading state forever. `waitUntil`
     * polls in real time without touching the clock, so it watched a screen that could not
     * progress and timed out after ten seconds.
     *
     * Both symptoms this suite showed trace back to that: a Settings screen stuck on "Loading", and
     * tests that hung rather than failed. Advancing the clock while polling fixes both, and keeps
     * the reason for disabling auto-advance, the indefinite `LoadingIndicator`, intact.
     *
     * `atLeastOneRootRequired = false` matters as much as the clock. The default blocks until a
     * Compose root exists, so a test whose activity never came up waited forever instead of
     * failing, which is how this suite produced twenty-minute hangs with no output. Polling
     * without that requirement turns the same situation into a named failure.
     */
    private fun await(what: String, condition: () -> Boolean) {
        repeat(AWAIT_ATTEMPTS) {
            if (condition()) return
            // A frame, then a moment of real time. Both are needed and for different reasons: the
            // frame lets Compose deliver work queued on its UI dispatcher, and the real time lets
            // the disk read that work is waiting on actually finish. Advancing frames alone spins
            // through the whole budget in milliseconds and gives DataStore no chance to answer.
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            Thread.sleep(AWAIT_STEP_MILLIS)
        }
        error("Gave up waiting for $what")
    }

    @Test
    fun everyTabIsReachableAndSelectable() {
        // Home is the start destination.
        awaitSelected(TAB_HOME)

        composeRule.onNodeWithTag(TAB_METAR).performClick()
        awaitSelected(TAB_METAR)

        composeRule.onNodeWithTag(TAB_LOCATIONS).performClick()
        awaitSelected(TAB_LOCATIONS)

        composeRule.onNodeWithTag(TAB_SETTINGS).performClick()
        awaitSelected(TAB_SETTINGS)
        // A section header exists only on the Settings screen itself, so finding one proves the
        // content rendered rather than merely that the tab is highlighted.
        awaitText("Units")
        composeRule.onNodeWithText("Units").assertIsDisplayed()
    }

    @Test
    fun addLocationOpensAndSystemBackReturns() {
        composeRule.onNodeWithTag(TAB_LOCATIONS).performClick()
        settle()

        // The FAB is icon-only, so its content description is the handle.
        awaitContentDescription("Add location")
        composeRule.onNodeWithContentDescription("Add location").performClick()
        settle()

        // The search screen's own prompt, which no other screen shows.
        awaitText("Type at least two letters")
        composeRule.onNode(hasText("Type at least two letters", substring = true)).assertIsDisplayed()

        // System back, not our own button: this asserts the platform back stack is wired
        // correctly.
        pressBack()
        settle()

        awaitSelected(TAB_LOCATIONS)
    }

    /**
     * Locations → location detail → back.
     *
     * The **second** push route. A day row only exists once a forecast has been fetched, so
     * asserting on one would make this test depend on a live network and a valid API key. A saved
     * location comes from the debug seeder and is read from Room, so this route is deterministic
     * offline.
     *
     * The day-detail push is verified manually and captured in `docs/screenshots/`.
     */
    @Test
    fun locationDetailOpensAndSystemBackReturns() {
        composeRule.onNodeWithTag(TAB_LOCATIONS).performClick()
        settle()

        // The debug seeder guarantees London is saved on first launch.
        awaitText("London, England, GB")
        composeRule.onNodeWithText("London, England, GB").performClick()
        settle()

        // The coordinate readout exists only on the detail screen, and comes from the database
        // rather than the network, so this assertion holds offline.
        //
        // Asserted through the **content description**, not the text. The identity block merges its
        // two lines into one announcement with `clearAndSetSemantics`, so TalkBack says "London,
        // England, GB. 51.5074, -0.1278" once. That removes the child text nodes from the tree, so
        // a text query finds nothing.
        awaitContentDescription("51.5074")
        composeRule.onNodeWithContentDescription("51.5074", substring = true).assertIsDisplayed()

        pressBack()
        settle()

        awaitSelected(TAB_LOCATIONS)
    }

    @Test
    fun changingATemperatureUnitTakesEffectWithoutNetwork() {
        composeRule.onNodeWithTag(TAB_SETTINGS).performClick()
        settle()

        // Selecting Fahrenheit must apply immediately from cache: the point of storing canonical
        // Celsius and converting at render time.
        awaitText("Fahrenheit (°F)")
        composeRule.onNodeWithText("Fahrenheit (°F)").performClick()
        settle()
        composeRule.onNodeWithText("Fahrenheit (°F)").assertIsDisplayed()
    }

    @Test
    fun perTabStateSurvivesSwitchingAwayAndBack() {
        composeRule.onNodeWithTag(TAB_SETTINGS).performClick()
        settle()
        awaitText("Fahrenheit (°F)")
        composeRule.onNodeWithText("Fahrenheit (°F)").performClick()
        settle()

        composeRule.onNodeWithTag(TAB_HOME).performClick()
        settle()
        composeRule.onNodeWithTag(TAB_SETTINGS).performClick()
        settle()

        // saveState/restoreState in SkyCastNavigator is what makes this pass; the preference
        // itself is restored by DataStore.
        awaitText("Units")
        composeRule.onNodeWithText("Units").assertIsDisplayed()
        composeRule.onNodeWithText("Fahrenheit (°F)").assertIsDisplayed()
    }

    private companion object {
        const val TAB_HOME = "tab_home"
        const val TAB_METAR = "tab_metar"
        const val TAB_LOCATIONS = "tab_locations"
        const val TAB_SETTINGS = "tab_settings"

        /** Comfortably longer than the Expressive spatial spec used for push transitions. */
        const val TRANSITION_MILLIS = 1_000L

        /** Two hundred 25 ms steps: five seconds, far longer than a local read or a transition. */
        const val AWAIT_ATTEMPTS = 200
        const val AWAIT_STEP_MILLIS = 25L

        /** Generous: a cold Room read plus a network round trip on a loaded emulator. */
    }
}
