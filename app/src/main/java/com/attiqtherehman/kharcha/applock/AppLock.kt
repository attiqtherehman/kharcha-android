package com.attiqtherehman.kharcha.applock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-lock preferences. Device-bound (the PIN verifier only works with this device's Keystore
 * key), so this store is never included in backups.
 */
data class AppLockSettings(
    val enabled: Boolean = false,
    val timeoutSeconds: Int = 60,
    val biometricEnabled: Boolean = true,
    val hasPin: Boolean = false,
    val allowWidgetQuickSaveWhileLocked: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutUntil: Long = 0L,
)

private val Context.appLockStore: DataStore<Preferences> by preferencesDataStore(name = "app_lock")

@Singleton
class AppLockStore @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val timeout = intPreferencesKey("timeout_seconds")
        val biometric = booleanPreferencesKey("biometric")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinMac = stringPreferencesKey("pin_mac")
        val widgetAllowed = booleanPreferencesKey("widget_quick_save")
        val failed = intPreferencesKey("failed_attempts")
        val lockoutUntil = longPreferencesKey("lockout_until")
    }

    val settings: Flow<AppLockSettings> = context.appLockStore.data.map { p ->
        AppLockSettings(
            enabled = p[Keys.enabled] ?: false, timeoutSeconds = p[Keys.timeout] ?: 60, biometricEnabled = p[Keys.biometric] ?: true,
            hasPin = p[Keys.pinMac] != null, allowWidgetQuickSaveWhileLocked = p[Keys.widgetAllowed] ?: false,
            failedAttempts = p[Keys.failed] ?: 0, lockoutUntil = p[Keys.lockoutUntil] ?: 0L,
        )
    }

    suspend fun current(): AppLockSettings = settings.first()
    suspend fun setEnabled(v: Boolean) = context.appLockStore.edit { it[Keys.enabled] = v }
    suspend fun setTimeout(seconds: Int) = context.appLockStore.edit { it[Keys.timeout] = seconds }
    suspend fun setBiometric(v: Boolean) = context.appLockStore.edit { it[Keys.biometric] = v }
    suspend fun setWidgetAllowed(v: Boolean) = context.appLockStore.edit { it[Keys.widgetAllowed] = v }
    suspend fun setFailures(count: Int, lockoutUntil: Long) = context.appLockStore.edit { it[Keys.failed] = count; it[Keys.lockoutUntil] = lockoutUntil }
    suspend fun pinVerifier(): Pair<String, String>? {
        val p = context.appLockStore.data.first()
        val salt = p[Keys.pinSalt] ?: return null
        val mac = p[Keys.pinMac] ?: return null
        return salt to mac
    }
    suspend fun setPinVerifier(salt: String, mac: String) = context.appLockStore.edit { it[Keys.pinSalt] = salt; it[Keys.pinMac] = mac; it[Keys.failed] = 0; it[Keys.lockoutUntil] = 0L }
    suspend fun clearPin() = context.appLockStore.edit { it.remove(Keys.pinSalt); it.remove(Keys.pinMac) }
}

class PinKeyUnavailableException : IllegalStateException("App lock key is not available on this device")

/**
 * PIN verifier: HMAC-SHA256(salt || pin) with a key that lives only in the Android Keystore.
 * The stored value is useless off-device, and the key never leaves secure hardware where present.
 */
@Singleton
class PinVerifier @Inject constructor(private val store: AppLockStore) {

    private fun key(): javax.crypto.SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    private fun mac(salt: ByteArray, pin: String): ByteArray {
        val key = try { key() } catch (e: Exception) { throw PinKeyUnavailableException() }
        return Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(salt + pin.toByteArray(Charsets.UTF_8))
    }

    suspend fun setPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) { "PIN must be 4 to 8 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val tag = mac(salt, pin)
        store.setPinVerifier(Base64.encodeToString(salt, Base64.NO_WRAP), Base64.encodeToString(tag, Base64.NO_WRAP))
    }

    /** True when the PIN matches. Throws [PinKeyUnavailableException] if the Keystore key is gone. */
    suspend fun verify(pin: String): Boolean {
        val (saltB64, macB64) = store.pinVerifier() ?: return false
        val expected = Base64.decode(macB64, Base64.NO_WRAP)
        val actual = mac(Base64.decode(saltB64, Base64.NO_WRAP), pin)
        return MessageDigest.isEqual(expected, actual)
    }

    companion object {
        private const val KEY_ALIAS = "kharcha_applock_pin_hmac"
    }
}

/** Decides when the UI is locked: on return to the foreground after the configured timeout. */
@Singleton
class AppLockManager @Inject constructor(
    private val store: AppLockStore,
    private val pinVerifier: PinVerifier,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked
    val settings: Flow<AppLockSettings> = store.settings
    private var backgroundedAt: Long = 0L
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch { if (store.current().enabled) _locked.value = true }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = clock.nowMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            val s = store.current()
            if (!s.enabled) { _locked.value = false; return@launch }
            if (backgroundedAt == 0L || clock.nowMillis() - backgroundedAt >= s.timeoutSeconds * 1000L) _locked.value = true
        }
    }

    fun unlock() { _locked.value = false }
    fun lockNow() { scope.launch { if (store.current().enabled) _locked.value = true } }

    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val attemptsLeft: Int) : PinResult
        data class LockedOut(val secondsLeft: Int) : PinResult
        data object KeyUnavailable : PinResult
    }

    suspend fun tryPin(pin: String): PinResult {
        val s = store.current()
        val now = clock.nowMillis()
        if (s.lockoutUntil > now) return PinResult.LockedOut(((s.lockoutUntil - now) / 1000L).toInt().coerceAtLeast(1))
        val ok = try { pinVerifier.verify(pin) } catch (e: PinKeyUnavailableException) { return PinResult.KeyUnavailable }
        return if (ok) {
            store.setFailures(0, 0L)
            unlock()
            PinResult.Ok
        } else {
            val failures = s.failedAttempts + 1
            if (failures >= MAX_ATTEMPTS) {
                val lockoutSeconds = 30L shl ((failures - MAX_ATTEMPTS).coerceAtMost(6))
                store.setFailures(failures, now + lockoutSeconds * 1000L)
                PinResult.LockedOut(lockoutSeconds.toInt())
            } else {
                store.setFailures(failures, 0L)
                PinResult.Wrong(MAX_ATTEMPTS - failures)
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
