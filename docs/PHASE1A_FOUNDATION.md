# Phase 1A — Foundation and identity

## New files
- core/ui/theme/MenchTokens.kt — MenchSpacing, MenchElevation, MenchRadius, MenchDuration, MenchEasing, rememberReduceMotion()
- core/ui/theme/Digits.kt — MenchDigits (Persian/Latin conversion), LocalPersianDigits, String.localizedDigits()
- core/ui/components/MenchDialog.kt — glass dialog, same signature as AlertDialog
- core/ui/components/MenchSheet.kt — branded bottom sheet + drag handle
- core/ui/components/MenchMenu.kt — MenchMenu + MenchMenuItem (replaces DropdownMenu)
- core/ui/components/MenchSnackbar.kt — MenchSnackbarHost, MenchSnackbar, SnackbarHostState.menchToast()
- core/ui/components/MenchLogo.kt — MenchLogoMark / MenchLogo (vector paths, follows accent)
- scripts/fetch-fonts.sh — downloads Vazirmatn TTFs

## Modified files
- build.gradle.kts — core-splashscreen 1.0.1, versionName 0.10.0-phase1a
- res/values/themes.xml, colors.xml — Theme.Mench.Splash now extends Theme.SplashScreen; values-night/themes.xml removed
- MainActivity.kt — installSplashScreen()
- core/ui/theme/Type.kt — Persian line height (1.55x) and LineHeightStyle
- core/ui/theme/Theme.kt — provides LocalPersianDigits
- 7 files: AlertDialog -> MenchDialog; ConversationListScreen: DropdownMenu -> MenchMenu; ChatScreen: ModalBottomSheet -> MenchSheet

## Manual step
Run `sh scripts/fetch-fonts.sh` once (needs internet) or copy the 4 Vazirmatn TTFs into android/app/src/main/assets/fonts/.
