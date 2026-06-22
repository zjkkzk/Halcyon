package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.listmodel.SortDirection
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SortDropdownItem(
    val text: String,
    val selected: Boolean,
    val summary: String? = null,
    val onClick: () -> Unit
)

internal data class DirectionalSortField<T>(
    val field: T,
    val text: String,
    val defaultDirection: SortDirection = SortDirection.Ascending,
    val supportsAscending: Boolean = true,
    val supportsDescending: Boolean = true
)

internal fun <T> directionalSortDropdownItems(
    fields: List<DirectionalSortField<T>>,
    selectedField: T,
    selectedDirection: SortDirection,
    ascendingSummary: String,
    descendingSummary: String,
    onSelect: (field: T, direction: SortDirection) -> Unit
): List<SortDropdownItem> =
    fields.map { option ->
        val selected = option.field == selectedField
        SortDropdownItem(
            text = option.text,
            selected = selected,
            summary = if (selected) {
                if (selectedDirection == SortDirection.Descending) descendingSummary else ascendingSummary
            } else {
                null
            },
            onClick = {
                val nextDirection = when {
                    !selected -> option.defaultDirection
                    selectedDirection == SortDirection.Ascending && option.supportsDescending -> SortDirection.Descending
                    selectedDirection == SortDirection.Descending && option.supportsAscending -> SortDirection.Ascending
                    else -> option.defaultDirection
                }
                onSelect(option.field, nextDirection)
            }
        )
    }

@Composable
fun SortDropdownMenu(
    items: List<SortDropdownItem>,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.onSurface,
    contentDescription: String = stringResource(R.string.common_sort)
) {
    SortDropdownMenuContent(items = items) {
        Icon(
            imageVector = MiuixIcons.Regular.Sort,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(24.dp)
        )
    }
}

@Composable
fun SortDropdownMenuContent(
    items: List<SortDropdownItem>,
    content: @Composable () -> Unit
) {
    WindowIconDropdownMenu(
        entries = listOf(
            DropdownEntry(
                items = items.map { item ->
                    DropdownItem(
                        text = item.text,
                        selected = item.selected,
                        summary = item.summary,
                        onClick = item.onClick
                    )
                }
            )
        )
    ) {
        content()
    }
}
