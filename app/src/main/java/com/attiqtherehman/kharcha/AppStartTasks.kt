package com.attiqtherehman.kharcha

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.attiqtherehman.kharcha.di.ApplicationScope
import com.attiqtherehman.kharcha.domain.usecase.SeedDefaultsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Work that must run every time the app comes to the foreground and after system events: seed
 * defaults on first launch, materialise recurring transactions, reschedule reminders, refresh
 * widgets. Everything here is idempotent, so running it twice is harmless.
 */
@Singleton
class AppStartTasks @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val seedDefaults: SeedDefaultsUseCase,
    private val hooks: Set<@JvmSuppressWildcards AppStartHook>,
) : DefaultLifecycleObserver {

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { runBlockingHooks() }
    }

    suspend fun runBlockingHooks() {
        runCatching { seedDefaults() }
        hooks.forEach { hook -> runCatching { hook.onAppStart() } }
    }
}

/** Implemented by platform services (reminder scheduler, recurring materializer, widget updater). */
interface AppStartHook {
    suspend fun onAppStart()
}
