package net.ankio.bluetooth.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.ankio.bluetooth.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
        .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
        Spacer(Modifier.height(88.dp))
    }
}

/** Miuix window dialog: anchored to the bottom as a drawer on phones, centred on large screens. */
@Composable
fun ChoiceDialog(title: String, close: () -> Unit, showClose: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    WindowDialog(show = true, title = title, onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            if (showClose) Button(onClick = close, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
        }
    }
}

/** Section header + card, matching the grouped settings layout. */
@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SmallTitle(title)
    Card(Modifier.fillMaxWidth(), content = content)
}
