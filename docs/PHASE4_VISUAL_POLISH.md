# Phase 4 — Visual polish (part 1)

## Font (Vazirmatn)
- **Do this once:** copy `Vazirmatn-Regular/Medium/SemiBold/Bold.ttf` into `android/app/src/main/assets/fonts/` (https://github.com/rastikerdar/vazirmatn → fonts/ttf). The app loads them automatically and works offline. Without them it falls back to the old Google-Fonts provider (needs Play Services + Google access, unreliable in Iran), then the system font.
- Bug fixed: only 4 of the 15 text styles had the custom font, so the heart/letters/memories/mood screens rendered in Roboto. Now all 15 do (and all respect the font-size setting).

## Theme
- New **ROSE** accent, the default for fresh installs (existing installs keep their choice; pick it in Settings → Appearance).
- The scheme is built around the accent: background, surfaces, received bubbles and chips pick up a faint tint; text on the accent is chosen by luminance (readable on every accent, light and dark).

## Chat
- Bubbles: bigger corners with a tight "tail" corner (RTL-aware); your bubbles use a soft gradient of the accent; text colour inside bubbles is now set explicitly (before, light mode could show dark text on the accent).
- Soft accent-tinted gradient behind the conversation.
- Jalali date separators: «امروز» / «دیروز» / «۱۲ مهر» (year added when not the current year).

## Not yet
Profile photos, app icon/splash, wallpaper picker, swipe-to-reply, scroll-to-bottom button with unread count, typing dots, voice waveform, link previews.
