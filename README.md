# Kharcha — personal finance for Android

Kharcha is a native Android app for tracking income, daily expenses, savings, budgets, multiple
accounts, a cash wallet, transfers, ATM withdrawals, upcoming bills and payment reminders. It is
offline-first, has no login, no server and **no Internet permission**: every record lives in a
local SQLite database on your phone, and you move data between phones with a backup file.

Default currency is PKR (Pakistani Rupee). Dates and times follow the phone's locale and zone.

## Features

| Area | What you get |
|---|---|
| Quick entry | Floating + button and a dedicated quick-expense screen: big amount keypad (with `+` running sum), favourite and recent categories, last-used account, date/time prefilled, *Save* and *Save & add another*. |
| Accounts | Cash wallet, bank, savings, digital wallet, credit card, other. Opening balances (never counted as income), archive instead of delete when history exists, "Adjust balance" and "Fix opening balance" that leave an auditable ADJUSTMENT row. |
| Transfers / ATM | One atomic transfer row touches both accounts; an ATM fee is a separate linked expense. Savings transfers and card repayments are transfers, never expenses. |
| Credit cards | Purchases are expenses on the card (balance goes negative = owed); repayments are bank→card transfers, so nothing is counted twice. Available credit = limit + balance. |
| Refunds | REFUND rows linked to the original expense, capped at what is left of it; they reduce category spend, never count as income. |
| Budgets | Monthly overall budget, per-category budgets, optional savings target, 75/90/100 % colouring and one-time notifications, copy from the previous month. |
| Savings goals | Target, target date, optional linked savings account (progress = account balance) or logged contributions. |
| Bills & reminders | Any number of reminders with amount, category, account, due date, time, repeat (daily/weekly/monthly/yearly × N), advance notices (7/3/1/0 days), end date, pause, skip, mark paid (optionally recording the expense), full payment history. 31st-of-month bills clamp to short months without drifting. |
| Recurring transactions | Salary, rent, subscriptions: each occurrence is recorded once on its date (unique index + cursor), with backfill preview and catch-up after days off. |
| Daily reminder | 10:30 PM by default, configurable, on by default; skipped when the day already has transactions or was marked reviewed; *Missed days* screen to catch up. |
| Widgets | **Quick expense** (add button, favourite categories, one-tap preset amounts that save immediately and show "Saved ✓") and **Account balances** (configurable accounts, hide amounts). Jetpack Glance, refreshed after every change, no polling. |
| Reports | Income vs expenses (12 months), spending by category with previous-period comparison, monthly trend, budget performance, upcoming obligations, account summary. Same calculation code as the dashboard. |
| Backup / restore | Versioned ZIP (manifest + JSON per table + receipts). Restore validates everything first, then **Replace** (typed confirmation) or **Merge** by ID (skip / newer wins / overwrite) with a per-table report. |
| Export / import | CSV (UTF-8 BOM, RFC 4180, formula-safe) and XLSX (one sheet per data type) of transactions, accounts, budgets, goals, reminders, payments; CSV transaction import with preview and idempotent re-import. |
| App lock | Fingerprint / face / device credential plus a Kharcha PIN whose verifier is an HMAC with an Android Keystore key. The database itself is **not** encrypted. |

## Build and run

Requirements: JDK 17+ (21 recommended), Android SDK with platform 37 (Android 17) and build-tools
36.0.0, Gradle 9.6 (wrapper included). Android Studio is optional.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM + Robolectric tests
./gradlew installDebug           # install on a connected phone (USB debugging on)
```

GitHub Actions builds every push (`.github/workflows/android.yml`) and uploads the debug APK as an
artifact, so you can also sideload the latest build from the Actions tab.

### Notes for this development machine (Windows, no admin)

The toolchain was installed portably under `C:\Users\<user>\Android` (JDK, SDK command-line
tools, platform 37, build-tools 36, platform-tools) and `C:\Users\<user>\Android\gradle`.
Three machine-specific workarounds are in place and are *not* part of the repository:

- `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\<user>\tmp` (user environment variable):
  this machine blocks Unix-domain socket files under `AppData\Local\Temp`, which breaks the Gradle
  daemon's internal pipes with "Unable to establish loopback connection".
- `%USERPROFILE%\.gradle\gradle.properties` sets `org.gradle.java.home` to the portable JDK.
- The network throttles each connection, so dependencies are served from a local Maven-layout
  mirror (`C:\Users\<user>\Android\m2mirror`, filled with a parallel downloader) that a user-level
  init script (`%USERPROFILE%\.gradle\init.d\local-mirror.init.gradle.kts`) prepends to every
  repository list. Robolectric's `android-all-instrumented` jar for SDK 36 was pre-placed in
  `%USERPROFILE%\.m2\repository` for the same reason. None of this affects CI or other machines.

## Architecture

Single Gradle module, Kotlin, Jetpack Compose + Material 3, MVVM with use cases, Hilt, Room,
Coroutines/Flow, Navigation Compose (type-safe routes), DataStore, WorkManager, Glance.

```
core/        money (Long minor units, formatter, parser), datetime (Clock, DateRange), common
domain/      models, LedgerRules (the sign table), FinanceCalculator, RecurrenceMath, use cases
data/        Room entities/DAOs/database, repositories, mappers, DataStore settings, backup, export
feature/     one package per screen group (dashboard, quickentry, transactions, accounts, budgets, …)
widget/      Glance widgets, config activity, snapshot repository, updater
notifications/ single next-wake alarm scheduler, evaluator, receivers, notifier
applock/     lock manager, PIN verifier (Keystore HMAC), lock screen
```

Key design decisions:

- **Money is `Long` minor units**; `BigDecimal` only for parsing and formatting; extra precision is
  rejected, never rounded.
- **Balances are never stored.** `balance = opening + Σ deltas` from the SQL view `v_ledger_effects`,
  which is the exact twin of `LedgerRules.effects` in Kotlin; a parity test feeds both the same
  random ledger.
- **Transfers are one row** with `accountId` → `toAccountId`, so a transfer can never be half
  deleted or half merged, and the sum of all balances is invariant under transfers.
- **Recurrence is anchor-indexed** (`anchor + n·interval`), so Jan 31 → Feb 28 → Mar 31 → Apr 30.
- **One alarm for everything.** `ReminderScheduler` arms a single `PendingIntent` for the earliest
  upcoming instant and re-evaluates at fire time against a watermark, so late, repeated or missed
  wakes never double-notify.
- **No cloud, no auto-backup.** `android:allowBackup="false"`; the manual ZIP is the migration path.

## Database schema (Room v1)

`accounts`, `categories`, `transactions` (type INCOME | EXPENSE | TRANSFER | REFUND | ADJUSTMENT,
`amountMinor`, `accountId`, `toAccountId?`, `categoryId?`, `localDate`, `localTime`, payee,
description, notes, tags, `linkedTransactionId?`, `recurringTransactionId?`, `occurrenceKey?`,
attachment), `recurring_transactions`, `monthly_budgets`, `category_budgets`, `savings_goals`,
`goal_contributions`, `reminders`, `bill_payments`, `day_reviews`, plus the view
`v_ledger_effects`. All ids are UUID strings; dates are ISO text; enums are stored by name.
Foreign keys use RESTRICT for history (archive instead of delete), SET NULL for links and CASCADE
for dependent rows. The exported schema lives in `app/schemas` for migration tests.

## Permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` (Android 13+) | daily reminder, bill notices, budget warnings; asked once with an explanation |
| `SCHEDULE_EXACT_ALARM` (Android 12+) | exact reminder timing; if denied the app uses inexact alarms and says so in Settings |
| `RECEIVE_BOOT_COMPLETED` | re-arm reminders after a restart |
| `USE_BIOMETRIC` | optional app lock |

Settings → Reminders shows the live status of notifications, exact alarms and battery
optimisation with buttons that open the right system screen. On phones with aggressive battery
management (OPPO/ColorOS, Xiaomi, etc.) you may also need to allow Kharcha to auto-start.

## Widgets

Long-press the home screen → Widgets → Kharcha, or use *More → Home-screen widgets → Add widget*.
Widgets cannot take typed input, so *Add expense* and the category chips open the quick-entry
screen; preset rows (configured under *More → Home-screen widgets*) save immediately. When the app
lock is on, presets open quick entry instead unless you allow one-tap saving while locked.

## Backup and restore

*More → Backup & restore → Create backup file* saves `kharcha-backup-YYYY-MM-DD.zip` wherever you
choose (Downloads, a USB-connected PC, a sharing app). On the other phone install Kharcha, choose
*Restore from backup file*, review the summary, then pick **Replace everything** or **Merge**.
The archive contains your complete financial history: keep it private.

## Tests

`./gradlew testDebugUnitTest` runs JUnit and Robolectric tests covering money formatting/parsing,
every ledger rule (income, expense, transfer, ATM fee, opening balance, savings transfer, credit
card purchase/repayment, refund limits, adjustments, edits/deletes), SQL↔Kotlin parity on a random
ledger, transactional rollback, recurrence clamping and idempotent materialisation, bill payments,
the reminder scheduler (single alarm, watermark, catch-up), CSV escaping/import and XLSX output,
backup round trip, invalid-archive rejection and merge policies. 70 tests pass locally on JDK 21
with Robolectric SDK 36; `app/build.gradle.kts` adds the `--add-opens` flags Robolectric needs on
Java 17+. Instrumented tests in `app/src/androidTest` (Room migration smoke test, quick-entry
end-to-end) run on a connected device with `./gradlew connectedDebugAndroidTest`.

## Verified on a device

Checked on 15 Oct 2026 on a Samsung Galaxy A55 (Android 16, One UI) over USB with the debug build.
`logcat -b crash` stayed empty for the whole session.

- **Install and first run**: seeded Cash Wallet and Bank Account, empty dashboard, bottom navigation.
- **Quick entry**: a Rs 250 Food & Dining expense typed on the keypad; the dashboard, account balance,
  category donut and daily bar updated immediately.
- **Widgets**: both pinned from More → Home-screen widgets through the system "Add to Home screen"
  dialog. The Quick Expense widget showed the default account, four favourite categories and the
  "Rs 200 · Transport & Fuel" preset; one tap on the preset flashed "Saved ✓" and the Account Balance
  widget went from −Rs 250 to −Rs 450 within a second. "+ Add expense" opened the quick-entry activity.
- **Reminder scheduling**: `dumpsys alarm` showed the single next-wake alarm for 22:30, first as an
  inexact alarm (28-minute window) and, seconds after the "Alarms & reminders" permission was granted,
  as an exact one (window 0). It was re-armed after uninstall/reinstall. Settings showed notifications
  allowed, exact timing allowed, the battery-optimisation warning and the next reminder time.
- **Daily reminder**: with no transaction recorded that day, the notification (channel `reminders_daily`,
  actions "Add expense" and "Mark reviewed") was posted at 22:30:01, one second after the exact alarm,
  and `dumpsys alarm` showed the single wakeup and the next wake re-armed for 22:30 the following day.
- **Backup and restore**: `kharcha-backup-2026-10-15.zip` (format 1, 2 accounts, 25 categories,
  2 transactions) was written through the system file picker. After `adb uninstall` and a fresh install
  the archive validated, "Replace everything" required typing REPLACE, the report listed every row as
  added with none invalid, and the dashboard matched the pre-uninstall state exactly.
- **Detail and delete**: deleting both test transactions recomputed list totals and balances at once.

## Known limitations

- No cloud sync; move data with a backup file.
- The database is not encrypted; the app lock protects the screen only.
- Reminder timing depends on the exact-alarm permission and the phone's battery settings.
- Only CSV import is supported (plus full backup restore); XLSX is export-only.
- Other currencies are display-only per account; there is no exchange-rate conversion.
