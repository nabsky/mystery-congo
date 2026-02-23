package com.zorindisplays.display.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorindisplays.display.ui.theme.DefaultBackground
import com.zorindisplays.display.ui.theme.DefaultTextStyle
import com.zorindisplays.display.ui.theme.PrimaryTextColor
import com.zorindisplays.display.util.StringEncryption
import java.util.UUID

private const val PREFS = "app_prefs"
private const val KEY_INSTALLATION_ID = "InstallationId"
private const val KEY_REGISTRATION_ID = "RegistrationId"

@Composable
fun RegistrationGate(
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var installationId by remember { mutableStateOf("") }
    var expectedCode by remember { mutableStateOf<String?>(null) }
    var registered by remember { mutableStateOf(false) }

    fun recalcState() {
        var id = prefs.getString(KEY_INSTALLATION_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
        }
        installationId = id

        expectedCode = StringEncryption.sha1(id.take(6))?.takeLast(6)

        val saved = prefs.getString(KEY_REGISTRATION_ID, null)
        registered = expectedCode != null && expectedCode.equals(saved, ignoreCase = true)
    }

    LaunchedEffect(Unit) { recalcState() }

    Box(Modifier.fillMaxSize()) {
        content()

        if (!registered) {
            RegistrationOverlay(
                installationId = installationId,
                onSubmit = { code ->
                    prefs.edit().putString(KEY_REGISTRATION_ID, code).apply()
                    recalcState()
                }
            )
        }
    }
}

@Composable
private fun RegistrationOverlay(
    installationId: String,
    onSubmit: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var hasFocusedOnce by remember { mutableStateOf(false) }

    var value by remember {
        mutableStateOf(TextFieldValue(text = ""))
    }

    fun sanitize(s: String): String =
        s.uppercase().filter { it.isLetterOrDigit() }.take(6)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DefaultBackground)
    ) {
        BasicText(
            text = installationId,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, end = 20.dp),
            style = DefaultTextStyle.copy(
                fontSize = 24.sp,
                color = PrimaryTextColor
            )
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1280.dp)
                .fillMaxHeight()
        ) {

            BasicText(
                text = "ENTER REGISTRATION CODE:",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 200.dp)
                    .width(640.dp),
                style = DefaultTextStyle.copy(
                    fontSize = 36.sp
                )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 260.dp)
                    .width(640.dp)
                    .height(160.dp)
                    .border(2.dp, PrimaryTextColor.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { incoming ->
                        val cleaned = sanitize(incoming.text)
                        val newSel = incoming.selection.end.coerceIn(0, cleaned.length)

                        value = incoming.copy(
                            text = cleaned,
                            selection = TextRange(newSel)
                        )
                    },
                    singleLine = true,
                    textStyle = DefaultTextStyle.copy(
                        fontSize = 120.sp,
                        color = PrimaryTextColor
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    cursorBrush = SolidColor(PrimaryTextColor),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                if (!hasFocusedOnce) {
                                    hasFocusedOnce = true
                                    value = value.copy(
                                        selection = TextRange(0, value.text.length)
                                    )
                                }
                            }
                        },
                    decorationBox = { inner ->
                        if (value.text.isEmpty()) {
                            BasicText(
                                text = "",
                                modifier = Modifier.alpha(0.25f),
                                style = DefaultTextStyle.copy(
                                    fontSize = 120.sp,
                                    color = PrimaryTextColor
                                )
                            )
                        }
                        inner()
                    }
                )
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            // Кнопка SUBMIT
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 460.dp)
                    .height(100.dp)
                    .wrapContentWidth()
                    .border(2.dp, PrimaryTextColor.copy(alpha = 0.9f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "SUBMIT",
                    modifier = Modifier.clickable {
                        val code = sanitize(value.text)
                        if (code.length == 6) onSubmit(code)
                    },
                    style = DefaultTextStyle.copy(
                        fontSize = 72.sp,
                        color = PrimaryTextColor
                    )
                )
            }
        }
    }
}
