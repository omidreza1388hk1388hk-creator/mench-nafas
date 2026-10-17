package com.omidgame.mench.feature.product

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Route names for every Phase 1C destination (public so settings/deep links can reach them). */
object ProductRoutes {
    const val ONBOARDING = "onboarding"
    const val FIND_PEOPLE = "find-people?invite={invite}"
    const val SCAN_QR = "find-people/scan"
    const val BLOCKED = "settings/blocked"
    const val DELETE_ACCOUNT = "settings/delete-account"
    const val HELP = "help"
    const val FEEDBACK = "help/feedback"
    const val LEGAL = "legal"
    const val CHANGELOG = "changelog"
    const val SHARE_PICKER = DeepLinks.SHARE_ROUTE
    const val PLANNER = "planner"
    const val SAVED = "saved"
    const val REFERRALS = "referrals"
    const val PLUS = "plus"
    const val TRANSPARENCY = "transparency"
    const val DATA_USAGE = "settings/data-usage"
    const val ACCOUNTS = "settings/accounts"
    const val CONTACT_PRIVACY = "settings/contact-privacy"
    const val SYNC = "settings/sync"
    const val LINK_DEVICE = "link-device?payload={payload}"

    fun findPeople(inviteCode: String? = null) = if (inviteCode == null) "find-people" else "find-people?invite=$inviteCode"

    /** Settings rows use these string keys so SettingsScreen needs only one callback. */
    fun forSettingsKey(key: String): String? = when (key) {
        "find_people" -> findPeople()
        "blocked" -> BLOCKED
        "delete_account" -> DELETE_ACCOUNT
        "help" -> HELP
        "feedback" -> FEEDBACK
        "legal" -> LEGAL
        "changelog" -> CHANGELOG
        "planner" -> PLANNER
        "saved" -> SAVED
        "referrals" -> REFERRALS
        "plus" -> PLUS
        "transparency" -> TRANSPARENCY
        "data_usage" -> DATA_USAGE
        "accounts" -> ACCOUNTS
        "contact_privacy" -> CONTACT_PRIVACY
        "sync" -> SYNC
        "link_device" -> "link-device"
        else -> null
    }
}

/**
 * Registers all Phase 1C screens. Called once from MenchNavGraph so that file only grows by one line.
 * [onOpenChat] opens a conversation; [onLoggedOut] clears the back stack to the sign-in screen;
 * [onOnboardingFinished] continues to the conversation list.
 */
fun NavGraphBuilder.productRoutes(
    navController: NavController,
    onOpenChat: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onOnboardingFinished: () -> Unit,
) {
    composable(ProductRoutes.ONBOARDING) { OnboardingScreen(onFinished = onOnboardingFinished) }

    composable(
        route = ProductRoutes.FIND_PEOPLE,
        arguments = listOf(navArgument("invite") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        FindPeopleScreen(
            initialInviteCode = entry.arguments?.getString("invite"),
            onBack = { navController.popBackStack() },
            onScanQr = { navController.navigate(ProductRoutes.SCAN_QR) },
            onOpenChat = onOpenChat,
        )
    }
    composable(ProductRoutes.SCAN_QR) {
        QrScanScreen(
            onBack = { navController.popBackStack() },
            onInviteCode = { code ->
                navController.navigate(ProductRoutes.findPeople(code)) { popUpTo(ProductRoutes.SCAN_QR) { inclusive = true } }
            },
        )
    }
    composable(ProductRoutes.BLOCKED) { BlockedUsersScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.DELETE_ACCOUNT) {
        AccountDeletionScreen(
            onBack = { navController.popBackStack() },
            onDeleted = onLoggedOut,
            onExportData = { navController.navigate("settings/data-export") },
        )
    }
    composable(ProductRoutes.HELP) {
        HelpScreen(onBack = { navController.popBackStack() }, onFeedback = { navController.navigate(ProductRoutes.FEEDBACK) })
    }
    composable(ProductRoutes.FEEDBACK) { FeedbackScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.LEGAL) { LegalScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.CHANGELOG) { ChangelogScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.ACCOUNTS) { AccountsScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.SYNC) { SyncSettingsScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.CONTACT_PRIVACY) { ContactPrivacyScreen(onBack = { navController.popBackStack() }) }
    composable(
        route = ProductRoutes.LINK_DEVICE,
        arguments = listOf(navArgument("payload") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        LinkDeviceScreen(onBack = { navController.popBackStack() }, initialPayload = entry.arguments?.getString("payload"))
    }
    composable(ProductRoutes.PLANNER) { PlannerScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.SAVED) { SavedScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.REFERRALS) { ReferralsScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.PLUS) { PlusScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.TRANSPARENCY) { TransparencyScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.DATA_USAGE) { DataUsageScreen(onBack = { navController.popBackStack() }) }
    composable(ProductRoutes.SHARE_PICKER) {
        ShareTargetScreen(
            onBack = { navController.popBackStack() },
            onSent = { id -> navController.popBackStack(); onOpenChat(id) },
        )
    }
}
