package com.ella.music.ui.online

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import com.ella.music.ui.components.EllaMiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OnlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    EllaMiuixTextField(
        value = value,
        onValueChange = onValueChange,
        label = placeholder,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        textStyle = TextStyle(
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 16.sp
        ),
        modifier = modifier.fillMaxWidth()
    )
}
