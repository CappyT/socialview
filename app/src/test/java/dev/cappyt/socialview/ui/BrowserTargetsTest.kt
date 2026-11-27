package dev.cappyt.socialview.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserTargetsTest {
    private val browser = BrowserHandler("com.android.chrome", "com.android.chrome.Main")
    private val otherBrowser = BrowserHandler("org.mozilla.firefox", "org.mozilla.firefox.App")
    private val socialView = BrowserHandler("dev.cappyt.socialview", "dev.cappyt.socialview.MainActivity")

    @Test
    fun `a single external browser is targeted directly`() {
        val choice = chooseBrowserDestination(
            handlers = listOf(socialView, browser),
            ownPackage = "dev.cappyt.socialview",
        )

        assertEquals(BrowserDestination.Direct("com.android.chrome"), choice)
    }

    @Test
    fun `SocialView is excluded from the chooser`() {
        val choice = chooseBrowserDestination(
            handlers = listOf(socialView, browser, otherBrowser),
            ownPackage = "dev.cappyt.socialview",
        )

        assertEquals(BrowserDestination.Chooser(excluded = listOf(socialView)), choice)
    }

    @Test
    fun `duplicate handlers do not create a chooser`() {
        val choice = chooseBrowserDestination(
            handlers = listOf(socialView, browser, browser),
            ownPackage = "dev.cappyt.socialview",
        )

        assertEquals(BrowserDestination.Direct("com.android.chrome"), choice)
    }

    @Test
    fun `only SocialView means no external destination`() {
        val choice = chooseBrowserDestination(
            handlers = listOf(socialView),
            ownPackage = "dev.cappyt.socialview",
        )

        assertEquals(BrowserDestination.None, choice)
    }

    @Test
    fun `chooser exclusions drop SocialView and host specific apps`() {
        val instagram = BrowserHandler("com.instagram.android", "com.instagram.android.activity.MainTabActivity")

        val excluded = chooserExclusions(
            actualHandlers = listOf(socialView, browser, instagram),
            browserPackages = setOf(browser.packageName),
            ownPackage = "dev.cappyt.socialview",
        )

        assertEquals(listOf(socialView, instagram), excluded)
    }

    @Test
    fun `chooser exclusions keep only SocialView when no browser is known`() {
        val instagram = BrowserHandler("com.instagram.android", "com.instagram.android.activity.MainTabActivity")

        val excluded = chooserExclusions(
            actualHandlers = listOf(socialView, instagram),
            browserPackages = emptySet(),
            ownPackage = "dev.cappyt.socialview",
        )

        assertEquals(listOf(socialView), excluded)
    }
}