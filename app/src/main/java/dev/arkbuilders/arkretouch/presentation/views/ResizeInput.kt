package dev.arkbuilders.arkretouch.presentation.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import dev.arkbuilders.arkretouch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ResizeInput(isVisible: Boolean, imageSize: IntSize, onResizeDown: (Int, Int) -> IntSize) {
    if (isVisible) {
        var width by rememberSaveable {
            mutableStateOf(
                imageSize.width.toString()
            )
        }

        var height by rememberSaveable {
            mutableStateOf(
                imageSize.height.toString()
            )
        }

        val widthHint = stringResource(
            R.string.width_too_large,
            imageSize.width
        )
        val digitsHint = stringResource(R.string.digits_only)
        val heightHint = stringResource(
            R.string.height_too_large,
            imageSize.height
        )
        var hint by remember {
            mutableStateOf("")
        }
        var showHint by remember {
            mutableStateOf(false)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Hint(
                hint,
                isVisible = {
                    delayHidingHint(it) {
                        showHint = false
                    }
                    showHint
                }
            )
            Row {
                TextField(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    value = width,
                    onValueChange = {
                        if (
                            it.isNotEmpty() &&
                            it.isDigitsOnly() &&
                            it.toInt() > imageSize.width
                        ) {
                            hint = widthHint
                            showHint = true
                            return@TextField
                        }
                        if (it.isNotEmpty() && !it.isDigitsOnly()) {
                            hint = digitsHint
                            showHint = true
                            return@TextField
                        }
                        width = it
                        showHint = false
                        if (width.isEmpty()) height = width
                        if (width.isNotEmpty() && width.isDigitsOnly()) {
                            height = onResizeDown(width.toInt(), 0)
                                .height.toString()
                        }
                    },
                    label = {
                        Text(
                            stringResource(R.string.width),
                            modifier = Modifier
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    },
                    textStyle = TextStyle(
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = height,
                    onValueChange = {
                        if (
                            it.isNotEmpty() &&
                            it.isDigitsOnly() &&
                            it.toInt() > imageSize.height
                        ) {
                            hint = heightHint
                            showHint = true
                            return@TextField
                        }
                        if (it.isNotEmpty() && !it.isDigitsOnly()) {
                            hint = digitsHint
                            showHint = true
                            return@TextField
                        }
                        height = it
                        showHint = false
                        if (height.isEmpty()) width = height
                        if (height.isNotEmpty() && height.isDigitsOnly()) {
                            width = onResizeDown(0, height.toInt())
                                .width.toString()
                        }
                    },
                    label = {
                        Text(
                            stringResource(R.string.height),
                            modifier = Modifier
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    },
                    textStyle = TextStyle(
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }
        }
    }
}

fun delayHidingHint(scope: CoroutineScope, hide: () -> Unit) {
    scope.launch {
        delay(1000)
        hide()
    }
}

@Composable
fun Hint(text: String, isVisible: (CoroutineScope) -> Boolean) {
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = isVisible(scope),
        enter = fadeIn(),
        exit = fadeOut(tween(durationMillis = 500, delayMillis = 1000)),
        modifier = Modifier
            .wrapContentSize()
            .background(Color.LightGray, RoundedCornerShape(10))
    ) {
        Text(
            text,
            Modifier
                .padding(12.dp)
        )
    }
}