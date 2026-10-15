package com.attiqtherehman.kharcha.feature.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.attiqtherehman.kharcha.R
import com.attiqtherehman.kharcha.core.common.HasReferencesException
import com.attiqtherehman.kharcha.core.common.Ids
import com.attiqtherehman.kharcha.core.datetime.Clock
import com.attiqtherehman.kharcha.data.seed.DefaultData
import com.attiqtherehman.kharcha.domain.model.Category
import com.attiqtherehman.kharcha.domain.model.CategoryKind
import com.attiqtherehman.kharcha.domain.repository.CategoryRepository
import com.attiqtherehman.kharcha.feature.accounts.ColorPaletteRow
import com.attiqtherehman.kharcha.feature.accounts.IconPickerRow
import com.attiqtherehman.kharcha.ui.common.CategoryPickerSheet
import com.attiqtherehman.kharcha.ui.common.ConfirmDialog
import com.attiqtherehman.kharcha.ui.common.IconBadge
import com.attiqtherehman.kharcha.ui.common.KharchaIcons
import com.attiqtherehman.kharcha.ui.common.KharchaTopBar
import com.attiqtherehman.kharcha.ui.common.LoadingState
import com.attiqtherehman.kharcha.ui.common.toColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val loading: Boolean = true,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val active: List<Category> = emptyList(),
    val archived: List<Category> = emptyList(),
)

sealed interface CategoriesEvent {
    data class DeleteBlocked(val category: Category, val count: Int) : CategoriesEvent
    data class Message(val text: String) : CategoriesEvent
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val kind = MutableStateFlow(CategoryKind.EXPENSE)
    private val _events = MutableSharedFlow<CategoriesEvent>()
    val events = _events.asSharedFlow()

    val state: StateFlow<CategoriesUiState> = combine(categories.observeAll(), kind) { all, k ->
        val ofKind = all.filter { it.kind == k }
        CategoriesUiState(loading = false, kind = k, active = ofKind.filter { !it.isArchived }, archived = ofKind.filter { it.isArchived })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun setKind(k: CategoryKind) = kind.update { k }

    fun save(existing: Category?, name: String, color: Long, icon: String, favorite: Boolean) = viewModelScope.launch {
        val now = clock.nowMillis()
        if (existing == null) {
            categories.create(Category(id = Ids.newId(), name = name.trim(), kind = kind.value, color = color.toInt(), icon = icon, isFavorite = favorite, createdAt = now, updatedAt = now))
        } else {
            categories.update(existing.copy(name = name.trim(), color = color.toInt(), icon = icon, isFavorite = favorite))
        }
    }

    fun setFavorite(c: Category, fav: Boolean) = viewModelScope.launch { categories.setFavorite(c.id, fav) }
    fun setArchived(c: Category, archived: Boolean) = viewModelScope.launch { categories.setArchived(c.id, archived) }

    fun delete(c: Category) = viewModelScope.launch {
        try {
            categories.delete(c.id)
        } catch (e: HasReferencesException) {
            _events.emit(CategoriesEvent.DeleteBlocked(c, e.referenceCount))
        }
    }

    fun mergeInto(from: Category, to: Category) = viewModelScope.launch {
        runCatching { categories.mergeInto(from.id, to.id) }.onFailure { _events.emit(CategoriesEvent.Message(it.message ?: it.toString())) }
    }

    fun move(c: Category, delta: Int) = viewModelScope.launch {
        val list = state.value.active.toMutableList()
        val index = list.indexOfFirst { it.id == c.id }
        val target = index + delta
        if (index < 0 || target < 0 || target >= list.size) return@launch
        list.removeAt(index); list.add(target, c)
        categories.reorder(kind.value, list.map { it.id })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf<Category?>(null) }
    var mergeTarget by remember { mutableStateOf<Pair<Category, Category>?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val blockedTemplate = stringResource(R.string.category_delete_blocked)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is CategoriesEvent.DeleteBlocked -> snackbar.showSnackbar(blockedTemplate.format(e.category.name, e.count))
                is CategoriesEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    Scaffold(
        topBar = {
            KharchaTopBar(title = stringResource(R.string.categories_title), onBack = onBack) {
                IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.categories_add)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.kind.ordinal) {
                Tab(selected = state.kind == CategoryKind.INCOME, onClick = { viewModel.setKind(CategoryKind.INCOME) }, text = { Text(stringResource(R.string.categories_income)) })
                Tab(selected = state.kind == CategoryKind.EXPENSE, onClick = { viewModel.setKind(CategoryKind.EXPENSE) }, text = { Text(stringResource(R.string.categories_expense)) })
            }
            if (state.loading) { LoadingState(); return@Column }
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(state.active, key = { it.id }) { c ->
                    CategoryRow(
                        c, isFirst = state.active.firstOrNull()?.id == c.id, isLast = state.active.lastOrNull()?.id == c.id,
                        onFavorite = { viewModel.setFavorite(c, !c.isFavorite) }, onEdit = { editing = c },
                        onArchive = { viewModel.setArchived(c, true) }, onUnarchive = null, onMerge = { merging = c }, onDelete = { deleting = c },
                        onMoveUp = { viewModel.move(c, -1) }, onMoveDown = { viewModel.move(c, 1) },
                    )
                }
                if (state.archived.isNotEmpty()) {
                    item { Text(stringResource(R.string.category_archived), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)) }
                    items(state.archived, key = { it.id }) { c ->
                        CategoryRow(c, isFirst = true, isLast = true, onFavorite = null, onEdit = { editing = c }, onArchive = null, onUnarchive = { viewModel.setArchived(c, false) }, onMerge = { merging = c }, onDelete = { deleting = c }, onMoveUp = null, onMoveDown = null)
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        CategoryEditDialog(existing = editing, onConfirm = { name, color, icon, fav -> viewModel.save(editing, name, color, icon, fav); creating = false; editing = null }, onDismiss = { creating = false; editing = null })
    }
    merging?.let { from ->
        CategoryPickerSheet(
            categories = (state.active + state.archived).filter { it.id != from.id }, selectedId = null,
            onSelect = { to -> mergeTarget = from to to; merging = null }, onDismiss = { merging = null }, title = stringResource(R.string.category_merge_into),
        )
    }
    mergeTarget?.let { (from, to) ->
        ConfirmDialog(
            title = stringResource(R.string.category_merge_into), message = stringResource(R.string.category_merge_confirm, from.name, to.name),
            confirmLabel = stringResource(R.string.action_ok), destructive = true, onConfirm = { viewModel.mergeInto(from, to); mergeTarget = null }, onDismiss = { mergeTarget = null },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(title = stringResource(R.string.action_delete), message = stringResource(R.string.category_delete_confirm), confirmLabel = stringResource(R.string.action_delete), destructive = true, onConfirm = { viewModel.delete(c); deleting = null }, onDismiss = { deleting = null })
    }
}

@Composable
private fun CategoryRow(
    c: Category, isFirst: Boolean, isLast: Boolean,
    onFavorite: (() -> Unit)?, onEdit: () -> Unit, onArchive: (() -> Unit)?, onUnarchive: (() -> Unit)?, onMerge: () -> Unit, onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(KharchaIcons.category(c.icon), c.color.toColor(), size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = if (c.isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        if (onFavorite != null) IconButton(onClick = onFavorite) { Icon(if (c.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = stringResource(R.string.category_favourite), tint = if (c.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onMoveUp != null) IconButton(onClick = onMoveUp, enabled = !isFirst) { Icon(Icons.Filled.ArrowUpward, contentDescription = null) }
        if (onMoveDown != null) IconButton(onClick = onMoveDown, enabled = !isLast) { Icon(Icons.Filled.ArrowDownward, contentDescription = null) }
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onEdit() })
            if (onArchive != null) DropdownMenuItem(text = { Text(stringResource(R.string.action_archive)) }, onClick = { menu = false; onArchive() })
            if (onUnarchive != null) DropdownMenuItem(text = { Text(stringResource(R.string.action_unarchive)) }, onClick = { menu = false; onUnarchive() })
            DropdownMenuItem(text = { Text(stringResource(R.string.category_merge_into)) }, onClick = { menu = false; onMerge() })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
private fun CategoryEditDialog(existing: Category?, onConfirm: (String, Long, String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.color?.toLong()?.and(0xFFFFFFFFL) ?: DefaultData.palette.first()) }
    var icon by remember { mutableStateOf(existing?.icon ?: "more") }
    var favorite by remember { mutableStateOf(existing?.isFavorite ?: false) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.categories_add else R.string.categories_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; error = false }, label = { Text(stringResource(R.string.category_name)) }, isError = error, singleLine = true, modifier = Modifier.fillMaxWidth())
                ColorPaletteRow(selected = color, onSelect = { color = it })
                IconPickerRow(selected = icon, color = color.toColor(), onSelect = { icon = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.category_favourite), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isBlank()) error = true else onConfirm(name, color, icon, favorite) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
