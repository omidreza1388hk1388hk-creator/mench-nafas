# نصب اپ MENCH از GitHub

## ۱. ساخت مخزن و آپلود
1. در GitHub یک مخزن جدید بساز (ترجیحاً **Private**).
2. محتوای همین پوشه را در ریشهٔ مخزن بگذار، طوری که پوشه‌های `android`، `backend`، `.github` و `docs` مستقیم در ریشه باشند.
   - با گیت:
     ```
     git init
     git add .
     git commit -m "MENCH with settings phases 1 and 2"
     git branch -M main
     git remote add origin https://github.com/<نام‌کاربری>/<نام‌مخزن>.git
     git push -u origin main
     ```
   - یا در GitHub: Add file → Upload files (پوشه‌ها را بکش و رها کن).

## ۲. ساخت APK
1. تب **Actions** → workflow با نام **Build APK**.
2. با push روی `main` خودکار اجرا می‌شود؛ دستی هم با **Run workflow**.
3. بعد از پایان (چند دقیقه)، پایین صفحهٔ اجرا در **Artifacts** فایل `mench-debug-apk` را دانلود کن.
4. زیپ را باز کن و `app-debug.apk` را روی گوشی نصب کن (اجازهٔ «نصب از منبع ناشناس» را بده).

## ۳. اگر ساخت خطا داد
این پروژه روی سیستم من کامپایل نشده (کامپایلر نبود)، پس احتمال خطای کامپایل هست.
1. روی اجرای قرمز کلیک کن → مرحلهٔ **Build debug APK** → متن خطا (خط‌هایی که با `e:` شروع می‌شوند).
2. همان متن را برایم بفرست تا اصلاح کنم.
3. workflow دیگر به نام **CI** تست‌های واحد (از جمله برابری رشته‌های فارسی/انگلیسی) را اجرا می‌کند؛ اگر قرمز شد گزارش تست‌ها در Artifacts است.

## نکته‌ها
- فایل `google-services.json` (برای اعلان FCM) داخل پروژه نیست؛ بدون آن هم اپ ساخته می‌شود ولی اعلان پوش گوگل کار نمی‌کند (اتصال زندهٔ خود برنامه کار می‌کند). اگر داری، در `android/app/` بگذار.
- نسخهٔ Debug است و برای انتشار نیست. برای نسخهٔ Release امضاشده، `docs/` و workflow مربوط را ببین.
- بک‌اند (NestJS) جداست و باید روی سرور خودت اجرا شود؛ تغییر جدید بک‌اند فقط دو مسیر دستگاه‌هاست (`GET /devices` و `DELETE /devices/others`) و migration ندارد.
- مستندات تنظیمات: `docs/SETTINGS_PHASE_1.md` و `docs/SETTINGS_PHASE_2.md`.
