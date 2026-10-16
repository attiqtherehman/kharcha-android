package com.attiqtherehman.kharcha.feature.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.data.backup.BackupContents
import com.attiqtherehman.kharcha.data.backup.BackupException
import com.attiqtherehman.kharcha.data.backup.BackupReader
import com.attiqtherehman.kharcha.data.backup.BackupRestorer
import com.attiqtherehman.kharcha.data.backup.BackupWriter
import com.attiqtherehman.kharcha.data.backup.MergePolicy
import com.attiqtherehman.kharcha.data.backup.RestoreMode
import com.attiqtherehman.kharcha.data.backup.RestoreReport
import com.attiqtherehman.kharcha.data.prefs.SettingsDataStore
import com.attiqtherehman.kharcha.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val label: String, val done: Int = 0, val total: Int = 0) : BackupUiState
    data class Validated(val contents: BackupContents) : BackupUiState
    data class Done(val message: String) : BackupUiState
    data class Restored(val report: RestoreReport) : BackupUiState
    data class Failed(val message: String, val newerVersion: Boolean = false) : BackupUiState
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: BackupWriter,
    private val reader: BackupReader,
    private val restorer: BackupRestorer,
    private val settings: SettingsDataStore,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()
    val lastBackupAt = settings.settings
    private var job: Job? = null

    fun suggestedFileName(): String = "kharcha-backup-${clock.today()}.zip"

    fun createBackup(uri: Uri) {
        job?.cancel()
        job = viewModelScope.launch(io) {
            _state.value = BackupUiState.Working("writing")
            try {
                val out = openForWrite(uri)
                out.use { writer.write(it) { p -> _state.value = BackupUiState.Working(p.step, p.done, p.total) } }
                settings.setLastBackupAt(clock.nowMillis())
                _state.value = BackupUiState.Done("saved")
            } catch (e: Exception) {
                runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
                _state.value = BackupUiState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun validate(uri: Uri) {
        job?.cancel()
        job = viewModelScope.launch(io) {
            _state.value = BackupUiState.Working("validating")
            try {
                val file = reader.copyToCache(uri)
                val contents = reader.read(file)
                _state.value = BackupUiState.Validated(contents)
            } catch (e: BackupException) {
                _state.value = BackupUiState.Failed(e.message ?: "invalid", newerVersion = e.message == "newer")
            } catch (e: Exception) {
                _state.value = BackupUiState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun restore(mode: RestoreMode, policy: MergePolicy) {
        val contents = (_state.value as? BackupUiState.Validated)?.contents ?: return
        job?.cancel()
        job = viewModelScope.launch(io) {
            _state.value = BackupUiState.Working("restoring")
            try {
                val report = restorer.restore(contents, mode, policy)
                contents.archiveFile.delete()
                _state.value = BackupUiState.Restored(report)
            } catch (e: Exception) {
                _state.value = BackupUiState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun reset() { job?.cancel(); _state.value = BackupUiState.Idle }

    private suspend fun openForWrite(uri: Uri): OutputStream = withContext(io) {
        val resolver = context.contentResolver
        // "wt" truncates an existing document; providers that reject that mode get the default one.
        val truncating = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
        if (truncating != null) return@withContext truncating
        val fallback = resolver.openOutputStream(uri)
        if (fallback != null) return@withContext fallback
        throw IllegalStateException("cannot write to the selected location")
    }

    suspend fun lastBackup(): Long? = settings.settings.first().lastBackupAt
}
