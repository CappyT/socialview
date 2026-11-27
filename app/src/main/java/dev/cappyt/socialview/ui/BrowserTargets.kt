package dev.cappyt.socialview.ui

internal data class BrowserHandler(
    val packageName: String,
    val activityName: String,
)

internal sealed interface BrowserDestination {
    data class Direct(val packageName: String) : BrowserDestination

    data class Chooser(val excluded: List<BrowserHandler>) : BrowserDestination

    data object None : BrowserDestination
}

internal fun chooseBrowserDestination(
    handlers: List<BrowserHandler>,
    ownPackage: String,
): BrowserDestination {
    val unique = handlers.distinct()
    val own = unique.filter { it.packageName == ownPackage }
    val others = unique.filter { it.packageName != ownPackage }
    return when {
        others.size == 1 -> BrowserDestination.Direct(others.single().packageName)
        others.size > 1 -> BrowserDestination.Chooser(excluded = own)
        else -> BrowserDestination.None
    }
}

internal fun chooserExclusions(
    actualHandlers: List<BrowserHandler>,
    browserPackages: Set<String>,
    ownPackage: String,
): List<BrowserHandler> {
    val unique = actualHandlers.distinct()
    val own = unique.filter { it.packageName == ownPackage }
    if (browserPackages.isEmpty()) return own
    return unique.filter {
        it.packageName == ownPackage || it.packageName !in browserPackages
    }
}