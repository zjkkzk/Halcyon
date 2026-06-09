package com.ella.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

data class EllaMiuixAction(
    val text: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val weight: Float = 1f
)

@Composable
fun EllaMiuixBottomSheet(
    show: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    endAction: @Composable (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    enableNestedScroll: Boolean = true,
    content: @Composable () -> Unit
) {
    WindowBottomSheet(
        show = show,
        title = title,
        endAction = endAction,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        enableNestedScroll = enableNestedScroll,
        cornerRadius = 28.dp,
        insideMargin = DpSize(20.dp, 18.dp),
        backgroundColor = MiuixTheme.colorScheme.background.copy(alpha = 0.98f),
        modifier = modifier,
        content = content
    )
}

@Composable
fun EllaMiuixDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    WindowDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismissRequest,
        backgroundColor = MiuixTheme.colorScheme.background.copy(alpha = 0.98f),
        insideMargin = DpSize(22.dp, 20.dp),
        modifier = modifier,
        content = content
    )
}

@Composable
fun EllaMiuixDialogActions(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    EllaMiuixActionRow(
        actions = listOf(
            EllaMiuixAction(text = cancelText, onClick = onCancel),
            EllaMiuixAction(text = confirmText, onClick = onConfirm, primary = true)
        ),
        modifier = modifier
    )
}

@Composable
fun EllaMiuixTripleDialogActions(
    firstText: String,
    secondText: String,
    confirmText: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    EllaMiuixActionRow(
        actions = listOf(
            EllaMiuixAction(text = firstText, onClick = onFirst),
            EllaMiuixAction(text = secondText, onClick = onSecond),
            EllaMiuixAction(text = confirmText, onClick = onConfirm, primary = true)
        ),
        modifier = modifier,
        spacing = 8.dp
    )
}

@Composable
fun EllaMiuixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    focusRequester: FocusRequester? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = TextStyle(
        color = MiuixTheme.colorScheme.onSurface,
        fontSize = 15.sp
    )
) {
    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = singleLine,
        insideMargin = DpSize(14.dp, 11.dp),
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f),
        ),
        cornerRadius = 14.dp,
        textStyle = textStyle,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .fillMaxWidth()
            .then(focusModifier)
    )
}

@Composable
fun EllaMiuixSheetActions(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    EllaMiuixActionRow(
        actions = listOf(
            EllaMiuixAction(text = cancelText, onClick = onCancel),
            EllaMiuixAction(text = confirmText, onClick = onConfirm, primary = true)
        ),
        modifier = modifier
    )
}

@Composable
fun EllaMiuixActionRow(
    actions: List<EllaMiuixAction>,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp
) {
    if (actions.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        actions.forEach { action ->
            val buttonModifier = Modifier.weight(action.weight)
            if (action.primary) {
                TextButton(
                    text = action.text,
                    onClick = action.onClick,
                    modifier = buttonModifier,
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            } else {
                TextButton(
                    text = action.text,
                    onClick = action.onClick,
                    modifier = buttonModifier
                )
            }
        }
    }
}
