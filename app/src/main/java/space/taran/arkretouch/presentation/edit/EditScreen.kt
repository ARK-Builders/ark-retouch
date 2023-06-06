@file:OptIn(ExperimentalComposeUiApi::class)

package space.taran.arkretouch.presentation.edit

import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.TextButton
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.viewmodel.compose.viewModel
import space.taran.arkretouch.R
import space.taran.arkretouch.di.DIManager
import space.taran.arkretouch.presentation.drawing.EditCanvas
import space.taran.arkretouch.presentation.edit.crop.CropAspectRatiosMenu
import space.taran.arkretouch.presentation.edit.resize.Hint
import space.taran.arkretouch.presentation.edit.resize.ResizeInput
import space.taran.arkretouch.presentation.edit.resize.delayHidingHint
import space.taran.arkretouch.presentation.picker.toPx
import space.taran.arkretouch.presentation.theme.Gray
import space.taran.arkretouch.presentation.utils.askWritePermissions
import space.taran.arkretouch.presentation.utils.getActivity
import space.taran.arkretouch.presentation.utils.isWritePermGranted
import java.nio.file.Path

@Composable
fun EditScreen(
    imagePath: Path?,
    imageUri: String?,
    fragmentManager: FragmentManager,
    navigateBack: () -> Unit,
    launchedFromIntent: Boolean
) {
    val primaryColor = MaterialTheme.colors.primary.value.toLong()
    val viewModel: EditViewModel =
        viewModel(
            factory = DIManager
                .component
                .editVMFactory()
                .create(
                    primaryColor,
                    launchedFromIntent,
                    imagePath,
                    imageUri,
                )
        )
    val context = LocalContext.current

    ExitDialog(
        viewModel = viewModel,
        navigateBack = { navigateBack() },
        launchedFromIntent = launchedFromIntent,
    )

    BackHandler {
        val editManager = viewModel.editManager
        if (editManager.isRotateMode.value) {
            editManager.toggleRotateMode()
            editManager.cancelRotateMode()
            viewModel.menusVisible = true
            return@BackHandler
        }
        if (editManager.isCropMode.value) {
            editManager.toggleCropMode()
            editManager.cancelCropMode()
            viewModel.menusVisible = true
            return@BackHandler
        }
        if (editManager.isResizeMode.value) {
            editManager.toggleResizeMode()
            editManager.cancelResizeMode()
            viewModel.menusVisible = true
            return@BackHandler
        }
        if (editManager.isEyeDropperMode.value) {
            viewModel.toggleEyeDropper()
            viewModel.cancelEyeDropper()
            viewModel.menusVisible = true
            return@BackHandler
        }
        if (editManager.canUndo.value) {
            editManager.undo()
            return@BackHandler
        }

        if (viewModel.exitConfirmed) {
            if (launchedFromIntent)
                context.getActivity()?.finish()
            else
                navigateBack()
        } else {
            Toast.makeText(context, "Tap back again to exit", Toast.LENGTH_SHORT)
                .show()
            viewModel.confirmExit()
        }
    }

    HandleImageSavedEffect(viewModel, launchedFromIntent, navigateBack)

    DrawContainer(
        viewModel
    )

    Menus(
        imagePath,
        fragmentManager,
        viewModel,
        launchedFromIntent,
        navigateBack
    )

    if (viewModel.isSavingImage) {
        SaveProgress()
    }

    if (viewModel.showEyeDropperHint) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Hint(stringResource(R.string.pick_color)) {
                delayHidingHint(it) {
                    viewModel.showEyeDropperHint = false
                }
                viewModel.showEyeDropperHint
            }
        }
    }
}

@Composable
private fun Menus(
    imagePath: Path?,
    fragmentManager: FragmentManager,
    viewModel: EditViewModel,
    launchedFromIntent: Boolean,
    navigateBack: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            TopMenu(
                imagePath,
                fragmentManager,
                viewModel,
                launchedFromIntent,
                navigateBack
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(IntrinsicSize.Min),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (viewModel.editManager.isRotateMode.value)
                Row {
                    Icon(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                viewModel.editManager.apply {
                                    rotate(-90f)
                                    invalidatorTick.value++
                                }
                            },
                        imageVector = ImageVector
                            .vectorResource(R.drawable.ic_rotate_left),
                        tint = MaterialTheme.colors.primary,
                        contentDescription = null
                    )
                    Icon(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                viewModel.editManager.apply {
                                    rotate(90f)
                                    invalidatorTick.value++
                                }
                            },
                        imageVector = ImageVector
                            .vectorResource(R.drawable.ic_rotate_right),
                        tint = MaterialTheme.colors.primary,
                        contentDescription = null
                    )
                }

            EditMenuContainer(viewModel, navigateBack)
        }
    }
}

@Composable
private fun DrawContainer(
    viewModel: EditViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 32.dp)
            .background(Color.Gray)
            .pointerInteropFilter { event ->
                if (event.action == MotionEvent.ACTION_DOWN)
                    viewModel.strokeSliderExpanded = false
                false
            }
            .onSizeChanged { newSize ->
                if (newSize == IntSize.Zero) return@onSizeChanged
                if (
                    viewModel.editManager.isResizeMode.value ||
                    viewModel.editManager.resizeOperation.isApplied()
                ) {
                    viewModel.editManager.resizeOperation.resetApply()
                    return@onSizeChanged
                }
                if (viewModel.showSavePathDialog) return@onSizeChanged
                viewModel.editManager.drawAreaSize.value = newSize
                viewModel.editManager.updateAvailableDrawArea()
                viewModel.loadImage()
            },
        contentAlignment = Alignment.Center
    ) {
        EditCanvas(viewModel)
    }
}

@Composable
private fun BoxScope.TopMenu(
    imagePath: Path?,
    fragmentManager: FragmentManager,
    viewModel: EditViewModel,
    launchedFromIntent: Boolean,
    navigateBack: () -> Unit
) {
    val context = LocalContext.current

    if (viewModel.showSavePathDialog)
        SavePathDialog(
            initialImagePath = imagePath,
            fragmentManager = fragmentManager,
            onDismissClick = { viewModel.showSavePathDialog = false },
            onPositiveClick = { savePath ->
                viewModel.saveImage(savePath)
                viewModel.showSavePathDialog = false
            }
        )
    if (viewModel.showMoreOptionsPopup)
        MoreOptionsPopup(
            onDismissClick = {
                viewModel.showMoreOptionsPopup = false
            },
            onShareClick = {
                viewModel.shareImage(context)
                viewModel.showMoreOptionsPopup = false
            },
            onSaveClick = {
                if (!context.isWritePermGranted()) {
                    context.askWritePermissions()
                    return@MoreOptionsPopup
                }
                viewModel.showSavePathDialog = true
            }
        )

    if (
        !viewModel.menusVisible &&
        !viewModel.editManager.isRotateMode.value &&
        !viewModel.editManager.isResizeMode.value &&
        !viewModel.editManager.isCropMode.value &&
        !viewModel.editManager.isEyeDropperMode.value
    )
        return
    Icon(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .size(36.dp)
            .clip(CircleShape)
            .clickable {
                viewModel.editManager.apply {
                    if (isRotateMode.value) {
                        toggleRotateMode()
                        cancelRotateMode()
                        viewModel.menusVisible = true
                        return@clickable
                    }
                    if (isCropMode.value) {
                        toggleCropMode()
                        cancelCropMode()
                        viewModel.menusVisible = true
                        return@clickable
                    }
                    if (isResizeMode.value) {
                        toggleResizeMode()
                        cancelResizeMode()
                        viewModel.menusVisible = true
                        return@clickable
                    }
                    if (isEyeDropperMode.value) {
                        viewModel.toggleEyeDropper()
                        viewModel.cancelEyeDropper()
                        viewModel.menusVisible = true
                        return@clickable
                    }
                    if (
                        !viewModel.editManager.canUndo.value
                    ) {
                        if (launchedFromIntent) {
                            context
                                .getActivity()
                                ?.finish()
                        } else {
                            navigateBack()
                        }
                    } else {
                        viewModel.showExitDialog = true
                    }
                }
            },
        imageVector = ImageVector.vectorResource(R.drawable.ic_arrow_back),
        tint = MaterialTheme.colors.primary,
        contentDescription = null
    )

    Row(
        Modifier
            .align(Alignment.TopEnd)
    ) {
        Icon(
            modifier = Modifier
                .padding(8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .clickable {
                    viewModel.editManager.apply {
                        if (
                            isCropMode.value || isRotateMode.value ||
                            isResizeMode.value
                        ) {
                            val operation: Operation =
                                when (true) {
                                    isRotateMode.value -> rotateOperation
                                    isCropMode.value -> cropOperation
                                    else -> resizeOperation
                                }
                            viewModel.applyOperation(operation)
                            viewModel.menusVisible = true
                            return@clickable
                        }
                    }
                    viewModel.showMoreOptionsPopup = true
                },
            imageVector = if (
                viewModel.editManager.isCropMode.value ||
                viewModel.editManager.isRotateMode.value ||
                viewModel.editManager.isResizeMode.value
            )
                ImageVector.vectorResource(R.drawable.ic_check)
            else ImageVector.vectorResource(R.drawable.ic_more_vert),
            tint = MaterialTheme.colors.primary,
            contentDescription = null
        )
    }
}

@Composable
private fun StrokeWidthPopup(
    modifier: Modifier,
    viewModel: EditViewModel
) {
    val editManager = viewModel.editManager
    editManager.setPaintStrokeWidth(viewModel.strokeWidth.dp.toPx())
    if (viewModel.strokeSliderExpanded) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .padding(
                            horizontal = 10.dp,
                            vertical = 5.dp
                        )
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(viewModel.strokeWidth.dp)
                        .clip(RoundedCornerShape(30))
                        .background(editManager.paintColor.value)
                )
            }

            Slider(
                modifier = Modifier
                    .fillMaxWidth(),
                value = viewModel.strokeWidth,
                onValueChange = {
                    viewModel.strokeWidth = it
                },
                valueRange = 0.5f..50f,
            )
        }
    }
}

@Composable
private fun EditMenuContainer(viewModel: EditViewModel, navigateBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Bottom
    ) {
        CropAspectRatiosMenu(
            isVisible = viewModel.editManager.isCropMode.value,
            viewModel.editManager.cropWindow
        )
        ResizeInput(
            isVisible = viewModel.editManager.isResizeMode.value,
            viewModel.editManager
        )

        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(topStartPercent = 30, topEndPercent = 30))
                .background(Gray)
                .clickable {
                    viewModel.menusVisible = !viewModel.menusVisible
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (viewModel.menusVisible) Icons.Filled.KeyboardArrowDown
                else Icons.Filled.KeyboardArrowUp,
                contentDescription = "",
                modifier = Modifier.size(32.dp),
            )
        }
        AnimatedVisibility(
            visible = viewModel.menusVisible,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            EditMenuContent(viewModel, navigateBack)
            EditMenuFlowHint(
                viewModel.bottomButtonsScrollIsAtStart.value,
                viewModel.bottomButtonsScrollIsAtEnd.value
            )
        }
    }
}

@Composable
private fun EditMenuContent(
    viewModel: EditViewModel,
    navigateBack: () -> Unit
) {
    val colorDialogExpanded = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val editManager = viewModel.editManager
    Column(
        Modifier
            .fillMaxWidth()
            .background(Gray)
    ) {
        StrokeWidthPopup(Modifier, viewModel)

        Row(
            Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp)
                .horizontalScroll(scrollState)
        ) {
            Icon(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (!editManager.isRotateMode.value &&
                            !editManager.isResizeMode.value &&
                            !editManager.isCropMode.value &&
                            !editManager.isEyeDropperMode.value
                        ) {
                            editManager.undo()
                        }
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_undo),
                tint = if (
                    editManager.canUndo.value &&
                    !editManager.isRotateMode.value &&
                    !editManager.isResizeMode.value &&
                    !editManager.isCropMode.value &&
                    !editManager.isEyeDropperMode.value
                ) MaterialTheme.colors.primary else Color.Black,
                contentDescription = null
            )
            Icon(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (
                            !editManager.isRotateMode.value &&
                            !editManager.isResizeMode.value &&
                            !editManager.isCropMode.value &&
                            !editManager.isEyeDropperMode.value
                        ) editManager.redo()
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_redo),
                tint = if (
                    editManager.canRedo.value &&
                    (
                        !editManager.isRotateMode.value &&
                            !editManager.isCropMode.value &&
                            !editManager.isResizeMode.value &&
                            !editManager.isEyeDropperMode.value
                        )
                ) MaterialTheme.colors.primary else Color.Black,
                contentDescription = null
            )
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color = editManager.paintColor.value)
                    .clickable {
                        if (editManager.isEyeDropperMode.value) {
                            viewModel.toggleEyeDropper()
                            viewModel.cancelEyeDropper()
                            colorDialogExpanded.value = true
                            return@clickable
                        }
                        if (
                            !editManager.isRotateMode.value &&
                            !editManager.isResizeMode.value &&
                            !editManager.isCropMode.value
                        )
                            colorDialogExpanded.value = true
                    }
            )
            ColorPickerDialog(
                isVisible = colorDialogExpanded,
                initialColor = editManager.paintColor.value,
                usedColors = viewModel.usedColors,
                enableEyeDropper = true,
                onToggleEyeDropper = {
                    viewModel.toggleEyeDropper()
                },
                onColorChanged = {
                    editManager.setPaintColor(it)
                    viewModel.trackColor(it)
                }
            )
            Icon(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (
                            !editManager.isRotateMode.value &&
                            !editManager.isCropMode.value &&
                            !editManager.isResizeMode.value &&
                            !editManager.isEyeDropperMode.value
                        )
                            viewModel.strokeSliderExpanded =
                                !viewModel.strokeSliderExpanded
                    },
                imageVector =
                ImageVector.vectorResource(R.drawable.ic_line_weight),
                tint = if (
                    !editManager.isRotateMode.value &&
                    !editManager.isResizeMode.value &&
                    !editManager.isCropMode.value &&
                    !editManager.isEyeDropperMode.value
                ) editManager.paintColor.value
                else Color.Black,
                contentDescription = null
            )
            Icon(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (
                            !editManager.isRotateMode.value &&
                            !editManager.isResizeMode.value &&
                            !editManager.isCropMode.value &&
                            !editManager.isEyeDropperMode.value
                        )
                            editManager.clearEdits()
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_clear),
                tint = if (
                    !editManager.isRotateMode.value &&
                    !editManager.isResizeMode.value &&
                    !editManager.isCropMode.value &&
                    !editManager.isEyeDropperMode.value
                )
                    MaterialTheme.colors.primary
                else Color.Black,
                contentDescription = null
            )
            Icon(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (
                            !editManager.isRotateMode.value &&
                            !editManager.isResizeMode.value &&
                            !editManager.isCropMode.value &&
                            !editManager.isEyeDropperMode.value
                        )
                            editManager.toggleEraseMode()
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_eraser),
                tint = if (
                    editManager.isEraseMode.value &&
                    !editManager.isRotateMode.value &&
                    !editManager.isResizeMode.value &&
                    !editManager.isEyeDropperMode.value
                )
                    MaterialTheme.colors.primary
                else
                    Color.Black,
                contentDescription = null
            )
            Icon(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        editManager.apply {
                            if (
                                !isRotateMode.value &&
                                !isResizeMode.value &&
                                !isEyeDropperMode.value
                            )
                                toggleCropMode()
                            else return@clickable
                            viewModel.menusVisible =
                                !editManager.isCropMode.value
                            if (isCropMode.value) {
                                val bitmap = viewModel
                                    .getCombinedImageBitmap()
                                    .asAndroidBitmap()
                                setBackgroundImage2()
                                viewModel.editManager.cropWindow.init(
                                    editManager,
                                    bitmap,
                                    fitBitmap = { bitmap1, maxWidth, maxHeight ->
                                        viewModel.fitBitmap(
                                            bitmap1.asImageBitmap(),
                                            maxWidth,
                                            maxHeight
                                        )
                                    }
                                )
                                return@clickable
                            }
                            editManager.cancelCropMode()
                            editManager.cropWindow.close()
                        }
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_crop),
                tint = if (
                    editManager.isCropMode.value
                ) MaterialTheme.colors.primary
                else
                    Color.Black,
                contentDescription = null
            )
            Icon(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        editManager.apply {
                            if (
                                !isCropMode.value &&
                                !isResizeMode.value &&
                                !isEyeDropperMode.value
                            )
                                toggleRotateMode()
                            else return@clickable
                            if (isRotateMode.value) {
                                setBackgroundImage2()
                                viewModel.menusVisible =
                                    !editManager.isRotateMode.value
                                return@clickable
                            }
                            cancelRotateMode()
                        }
                    },
                imageVector = ImageVector
                    .vectorResource(R.drawable.ic_rotate_90_degrees_ccw),
                tint = if (editManager.isRotateMode.value)
                    MaterialTheme.colors.primary
                else
                    Color.Black,
                contentDescription = null
            )
            Icon(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        editManager.apply {
                            if (
                                !isRotateMode.value &&
                                !isCropMode.value &&
                                !isEyeDropperMode.value
                            )
                                toggleResizeMode()
                            else return@clickable
                            viewModel.menusVisible = !isResizeMode.value
                            if (isResizeMode.value) {
                                val imgBitmap =
                                    viewModel.getCombinedImageBitmap()
                                setBackgroundImage2()
                                resizeOperation.init(imgBitmap.asAndroidBitmap())
                                backgroundImage.value = imgBitmap
                                return@clickable
                            }
                            cancelResizeMode()
                        }
                    },
                imageVector = ImageVector
                    .vectorResource(R.drawable.ic_aspect_ratio),
                tint = if (editManager.isResizeMode.value)
                    MaterialTheme.colors.primary
                else
                    Color.Black,
                contentDescription = null
            )
        }
    }
    viewModel.bottomButtonsScrollIsAtStart.value = scrollState.value == 0
    viewModel.bottomButtonsScrollIsAtEnd.value =
        scrollState.value == scrollState.maxValue
}

@Composable
fun EditMenuFlowHint(
    scrollIsAtStart: Boolean = true,
    scrollIsAtEnd: Boolean = false
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = scrollIsAtEnd || (!scrollIsAtStart && !scrollIsAtEnd),
            enter = fadeIn(tween(durationMillis = 1000)),
            exit = fadeOut((tween(durationMillis = 1000))),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Icon(
                Icons.Filled.KeyboardArrowLeft,
                contentDescription = null,
                Modifier
                    .background(Gray)
                    .padding(top = 16.dp, bottom = 16.dp)
                    .size(32.dp)
            )
        }
        AnimatedVisibility(
            visible = scrollIsAtStart || (!scrollIsAtStart && !scrollIsAtEnd),
            enter = fadeIn(tween(durationMillis = 1000)),
            exit = fadeOut((tween(durationMillis = 1000))),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                Modifier
                    .background(Gray)
                    .padding(top = 16.dp, bottom = 16.dp)
                    .size(32.dp)
            )
        }
    }
}

@Composable
private fun HandleImageSavedEffect(
    viewModel: EditViewModel,
    launchedFromIntent: Boolean,
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel.imageSaved) {
        if (!viewModel.imageSaved)
            return@LaunchedEffect
        if (launchedFromIntent)
            context.getActivity()?.finish()
        else
            navigateBack()
    }
}

@Composable
private fun ExitDialog(
    viewModel: EditViewModel,
    navigateBack: () -> Unit,
    launchedFromIntent: Boolean
) {
    if (!viewModel.showExitDialog) return

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            viewModel.showExitDialog = false
        },
        title = {
            Text(
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                text = "Do you want to save the changes?",
                fontSize = 16.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.showExitDialog = false
                    viewModel.showSavePathDialog = true
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.showExitDialog = false
                    if (launchedFromIntent) {
                        context.getActivity()?.finish()
                    } else {
                        navigateBack()
                    }
                }
            ) {
                Text("Exit")
            }
        }
    )
}
