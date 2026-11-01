package com.ajv009.gbvoice.ui.speech

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ajv009.gbvoice.App
import com.ajv009.gbvoice.data.Replacement
import com.ajv009.gbvoice.ui.Hint

/** Fix words the engine keeps getting wrong ("al fonts" -> "Alphons"); applied before anything is sent. */
@Composable
fun ReplacementsEditor() {
    val app = App.get(LocalContext.current)
    val s by app.settings.state.collectAsStateWithLifecycle()
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }

    Hint("Whole words, any capitalisation. Applied to the transcript before it goes to the watch or the webhook.")
    s.replacements.forEach { rule ->
        ListItem(
            headlineContent = { Text("${rule.from}  →  ${rule.to}") },
            trailingContent = {
                IconButton(onClick = { app.settings.update { it.copy(replacements = it.replacements - rule) } }) {
                    Icon(Icons.Filled.Delete, "Remove")
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(from, { from = it }, label = { Text("Heard") }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(to, { to = it }, label = { Text("Replace with") }, singleLine = true, modifier = Modifier.weight(1f))
    }
    TextButton(
        onClick = {
            app.settings.update { it.copy(replacements = it.replacements + Replacement(from.trim(), to)) }
            from = ""
            to = ""
        },
        enabled = from.isNotBlank(),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) { Text("Add replacement") }
}
