@file:OptIn(ExperimentalComposeUiApi::class)

package dev.arkbuilders.arkretouch.presentation.screens

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentManager
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.widget.Toast
import dev.arkbuilders.arkretouch.R
import dev.arkbuilders.arkretouch.data.model.EditingState
import dev.arkbuilders.arkretouch.data.model.Resolution
import dev.arkbuilders.arkretouch.presentation.canvas.EditCanvasScreen
import dev.arkbuilders.arkretouch.presentation.dialogs.ColorPickerDialog
import dev.arkbuilders.arkretouch.presentation.dialogs.ConfirmClearDialog
import dev.arkbuilders.arkretouch.presentation.dialogs.NewImageOptionsDialog
import dev.arkbuilders.arkretouch.presentation.dialogs.SavePathDialog
import dev.arkbuilders.arkretouch.presentation.dialogs.SaveProgress
import dev.arkbuilders.arkretouch.presentation.picker.toPx
import dev.arkbuilders.arkretouch.presentation.popups.BlurIntensityPopup
import dev.arkbuilders.arkretouch.presentation.popups.MoreOptionsPopup
import dev.arkbuilders.arkretouch.presentation.theme.Gray
import dev.arkbuilders.arkretouch.presentation.viewmodels.EditViewModel
import dev.arkbuilders.arkretouch.presentation.views.CropAspectRatiosMenu
import dev.arkbuilders.arkretouch.presentation.views.Hint
import dev.arkbuilders.arkretouch.presentation.views.ResizeInput
import dev.arkbuilders.arkretouch.presentation.views.delayHidingHint
import dev.arkbuilders.arkretouch.utils.getActivity
import dev.arkbuilders.arkretouch.utils.permission.isWritePermissionGranted
import dev.arkbuilders.arkretouch.utils.permission.requestWritePermissions
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.nio.file.Path

@Composable
fun EditScreen(
    imagePath: Path?,
    imageUri: String?,
    fragmentManager: FragmentManager,
    navigateBack: () -> Unit,
    launchedFromIntent: Boolean,
    maxResolution: Resolution
) {
    val primaryColor = MaterialTheme.colors.primary.value.toLong()
    val viewModel: EditViewModel = koinViewModel {
        parametersOf(primaryColor, launchedFromIntent, imagePath, imageUri, maxResolution)
    }

    val editingState = viewModel.editingState

    val context = LocalContext.current
    val showDefaultsDialog = remember {
        mutableStateOf(
            imagePath == null && imageUri == null && !editingState.isLoaded
        )
    }

    if (showDefaultsDialog.value) {
        viewModel.editManager.apply {
            resolution.value?.let {
                NewImageOptionsDialog(
                    it,
                    maxResolution,
                    viewModel.drawingState.backgroundPaint.color,
                    navigateBack,
                    viewModel,
                    persistDefaults = { color, resolution ->
                        viewModel.persistDefaults(color, resolution)
                    },
                    onConfirm = {
                        showDefaultsDialog.value = false
                    }
                )
            }
        }
    }
    ExitDialog(
        viewModel = viewModel,
        navigateBack = {
            navigateBack()
            viewModel.setIsLoaded(false)
        },
        launchedFromIntent = launchedFromIntent,
    )

    BackHandler {
        val editManager = viewModel.editManager
        if (
            viewModel.isCropping() || viewModel.isRotating() ||
            viewModel.isResizing() || editManager.isEyeDropperMode.value ||
            editManager.isBlurMode.value
        ) {
            viewModel.cancelOperation()
            return@BackHandler
        }
        if (editManager.isZoomMode.value) {
            editManager.toggleZoomMode()
            return@BackHandler
        }
        if (editManager.isPanMode.value) {
            editManager.togglePanMode()
            return@BackHandler
        }
        if (editManager.canUndo.value) {
            editManager.undo()
            return@BackHandler
        }
        if (viewModel.editingState.exitConfirmed) {
            if (launchedFromIntent)
                context.getActivity()?.finish()
            else
                navigateBack()
            return@BackHandler
        }
        if (!viewModel.editingState.exitConfirmed) {
            Toast.makeText(context, "Tap back again to exit", Toast.LENGTH_SHORT)
                .show()
            viewModel.confirmExit()
            return@BackHandler
        }
    }

    HandleImageSavedEffect(viewModel, launchedFromIntent, navigateBack)

    if (!showDefaultsDialog.value)
        DrawContainer(viewModel, context)

    Menus(
        imagePath,
        fragmentManager,
        viewModel,
        launchedFromIntent,
        editingState,
        navigateBack
    )

    if (viewModel.editingState.isSavingImage) {
        SaveProgress()
    }

    if (viewModel.editingState.showEyeDropperHint) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Hint(stringResource(R.string.pick_color)) {
                delayHidingHint(it) {
                    viewModel.showEyeDropperHint(false)
                }
                viewModel.editingState.showEyeDropperHint
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
    editingState: EditingState,
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
            if (viewModel.isRotating())
                Row {
                    Icon(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                viewModel.editManager.apply {
                                    viewModel.onRotate(-90f)
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
                                    viewModel.onRotate(90f)
                                    invalidatorTick.value++
                                }
                            },
                        imageVector = ImageVector
                            .vectorResource(R.drawable.ic_rotate_right),
                        tint = MaterialTheme.colors.primary,
                        contentDescription = null
                    )
                }

            EditMenuContainer(viewModel, editingState, navigateBack)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DrawContainer(
    viewModel: EditViewModel,
    context: Context
) {
    Box(
        modifier = Modifier
            .padding(bottom = 32.dp)
            .fillMaxSize()
            .background(
                if (viewModel.isCropping()) {
                    Color.White
                } else {
                    Color.Gray
                }
            )
            .pointerInteropFilter { event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    viewModel.setStrokeSliderExpanded(isExpanded = false)
                }

                return@pointerInteropFilter false
            }
            .onSizeChanged { newSize ->
                viewModel.onDrawContainerSizeChanged(newSize, context)
            },
        contentAlignment = Alignment.Center
    ) {
        EditCanvasScreen(viewModel)
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

    if (viewModel.editingState.showSavePathDialog)
        SavePathDialog(
            initialImagePath = imagePath,
            fragmentManager = fragmentManager,
            onDismissClick = { viewModel.showSavePathDialog(false) },
            onPositiveClick = { savePath ->
                viewModel.saveImage(context, savePath)
                viewModel.showSavePathDialog(false)
            }
        )
    if (viewModel.editingState.showMoreOptionsPopup)
        MoreOptionsPopup(
            onDismissClick = {
                viewModel.showMoreOptions(false)
            },
            onShareClick = {
                viewModel.shareImage(
                    context.cacheDir.toPath(),
                    provideUri = { file ->
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    },
                    startShare = { intent ->
                        context.apply {
                            startActivity(
                                Intent.createChooser(
                                    intent,
                                    getString(R.string.share)
                                )
                            )
                        }
                    }
                )
                viewModel.showMoreOptions(false)
            },
            onSaveClick = {
                if (!context.isWritePermissionGranted()) {
                    context.requestWritePermissions()
                    return@MoreOptionsPopup
                }
                viewModel.showSavePathDialog(true)
            },
            onClearEdits = {
                viewModel.showConfirmClearDialog(true)
                viewModel.showMoreOptions(false)
            }
        )

    ConfirmClearDialog(
        viewModel.editingState.showConfirmClearDialog,
        onConfirm = {
            viewModel.editManager.apply {
                if (
                    !viewModel.isRotating() &&
                    !viewModel.isResizing() &&
                    !isEyeDropperMode.value
                ) clearEdits()
            }
        },
        onDismiss = {
            viewModel.showConfirmClearDialog(false)
        }
    )

    if (
        !viewModel.editingState.menusVisible &&
        !viewModel.isRotating() &&
        !viewModel.isResizing() &&
        !viewModel.isCropping() &&
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
                    if (
                        viewModel.isCropping() || viewModel.isRotating() ||
                        viewModel.isResizing() || isEyeDropperMode.value ||
                        isBlurMode.value
                    ) {
                        viewModel.cancelOperation()
                        return@clickable
                    }
                    if (isZoomMode.value) {
                        toggleZoomMode()
                        return@clickable
                    }
                    if (isPanMode.value) {
                        togglePanMode()
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
                        viewModel.showExitDialog(true)
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
                            viewModel.isCropping() || viewModel.isRotating() ||
                            viewModel.isResizing() || isBlurMode.value
                        ) {
                            viewModel.applyOperation()
                            return@clickable
                        }
                    }
                    viewModel.showMoreOptions(true)
                },
            imageVector = if (
                viewModel.isCropping() ||
                viewModel.isRotating() ||
                viewModel.isResizing() ||
                viewModel.editManager.isBlurMode.value
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
    viewModel: EditViewModel,
    editionState: EditingState
) {
    val editManager = viewModel.editManager
    viewModel.onSetPaintStrokeWidth(viewModel.editingState.strokeWidth.dp.toPx())
    if (editionState.strokeSliderExpanded) {
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
                        .height(viewModel.editingState.strokeWidth.dp)
                        .clip(RoundedCornerShape(30))
                        .background(viewModel.drawingState.drawPaint.color)
                )
            }

            Slider(
                modifier = Modifier
                    .fillMaxWidth(),
                value = viewModel.editingState.strokeWidth,
                onValueChange = {
                    viewModel.setStrokeWidth(it)
                },
                valueRange = 0.5f..50f,
            )
        }
    }
}

@Composable
private fun EditMenuContainer(
    viewModel: EditViewModel,
    editingState: EditingState,
    navigateBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Bottom
    ) {
        CropAspectRatiosMenu(
            isVisible = viewModel.isCropping(),
            viewModel.editManager.cropWindow
        )
        ResizeInput(
            isVisible = viewModel.isResizing(),
            viewModel.imageSize,
            viewModel.editManager
        )

        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(topStartPercent = 30, topEndPercent = 30))
                .background(Gray)
                .clickable {
                    viewModel.toggleMenus()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (viewModel.editingState.menusVisible) Icons.Filled.KeyboardArrowDown
                else Icons.Filled.KeyboardArrowUp,
                contentDescription = "",
                modifier = Modifier.size(32.dp),
            )
        }
        AnimatedVisibility(
            visible = viewModel.editingState.menusVisible,
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            EditMenuContent(viewModel, editingState)
            EditMenuFlowHint(
                viewModel.editingState.bottomButtonsScrollIsAtStart,
                viewModel.editingState.bottomButtonsScrollIsAtEnd
            )
        }
    }
}

@Composable
private fun EditMenuContent(
    viewModel: EditViewModel,
    editingState: EditingState
) {
    val colorDialogExpanded = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val editManager = viewModel.editManager
    Column(
        Modifier
            .fillMaxWidth()
            .background(Gray)
    ) {
        StrokeWidthPopup(Modifier, viewModel, editingState)

        BlurIntensityPopup(editManager)

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
                        if (
                            !viewModel.isRotating() &&
                            !viewModel.isResizing() &&
                            !viewModel.isCropping() &&
                            !editManager.isEyeDropperMode.value &&
                            !editManager.isBlurMode.value
                        ) {
                            editManager.undo()
                        }
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_undo),
                tint = if (
                    editManager.canUndo.value && (
                        !viewModel.isRotating() &&
                            !viewModel.isResizing() &&
                            !viewModel.isCropping() &&
                            !editManager.isEyeDropperMode.value &&
                            !editManager.isBlurMode.value
                        )
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
                            !viewModel.isRotating() &&
                            !viewModel.isResizing() &&
                            !viewModel.isCropping() &&
                            !editManager.isEyeDropperMode.value &&
                            !editManager.isBlurMode.value
                        ) editManager.redo()
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_redo),
                tint = if (
                    editManager.canRedo.value &&
                    (
                        !viewModel.isRotating() &&
                            !viewModel.isResizing() &&
                            !viewModel.isCropping() &&
                            !editManager.isEyeDropperMode.value &&
                            !editManager.isBlurMode.value
                        )
                ) MaterialTheme.colors.primary else Color.Black,
                contentDescription = null
            )
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color = viewModel.drawingState.drawPaint.color)
                    .clickable {
                        if (editManager.isEyeDropperMode.value) {
                            viewModel.toggleEyeDropper()
                            viewModel.cancelEyeDropper()
                            colorDialogExpanded.value = true
                            return@clickable
                        }
                        if (
                            !viewModel.isRotating() &&
                            !viewModel.isResizing() &&
                            !viewModel.isCropping() &&
                            !viewModel.isErasing() &&
                            !editManager.isBlurMode.value
                        )
                            colorDialogExpanded.value = true
                    }
            )
            ColorPickerDialog(
                isVisible = colorDialogExpanded,
                initialColor = viewModel.drawingState.drawPaint.color,
                usedColors = viewModel.editingState.usedColors,
                enableEyeDropper = true,
                onToggleEyeDropper = {
                    viewModel.toggleEyeDropper()
                },
                onColorChanged = {
                    viewModel.onSetPaintColor(it)
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
                            !viewModel.isRotating() &&
                            !viewModel.isCropping() &&
                            !viewModel.isResizing() &&
                            !editManager.isEyeDropperMode.value &&
                            !editManager.isBlurMode.value
                        )
                            viewModel.setStrokeSliderExpanded(isExpanded = !editingState.strokeSliderExpanded)
                    },
                imageVector =
                ImageVector.vectorResource(R.drawable.ic_line_weight),
                tint = if (
                    !viewModel.isRotating() &&
                    !viewModel.isResizing() &&
                    !viewModel.isCropping() &&
                    !editManager.isEyeDropperMode.value &&
                    !editManager.isBlurMode.value
                ) viewModel.drawingState.drawPaint.color
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
                            !viewModel.isRotating() &&
                            !viewModel.isResizing() &&
                            !viewModel.isCropping() &&
                            !editManager.isEyeDropperMode.value &&
                            !editManager.isBlurMode.value
                        )
                            viewModel.toggleErase()
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_eraser),
                tint = if (
                    viewModel.isErasing()
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
                        if (
                            !viewModel.isRotating() &&
                            !viewModel.isResizing() &&
                            !viewModel.isCropping() &&
                            !editManager.isEyeDropperMode.value &&
                            !editManager.isBlurMode.value &&
                            !viewModel.isErasing()
                        )
                            editManager.toggleZoomMode()
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_zoom_in),
                tint = if (
                    editManager.isZoomMode.value
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
                        if (
                            !viewModel.isRotating() &&
                            !viewModel.isResizing() &&
                            !viewModel.isCropping() &&
                            !editManager.isEyeDropperMode.value &&
                            !editManager.isBlurMode.value &&
                            !viewModel.isErasing()
                        )
                            editManager.togglePanMode()
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_pan_tool),
                tint = if (
                    editManager.isPanMode.value
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
                                !viewModel.isRotating() &&
                                !viewModel.isResizing() &&
                                !isEyeDropperMode.value &&
                                !viewModel.isErasing() &&
                                !isBlurMode.value
                            ) {
                                viewModel.toggleCrop()
                                viewModel.showMenus(!viewModel.isCropping())
                                if (viewModel.isCropping()) {
                                    val bitmap = viewModel.getEditedImage()
                                    setBackgroundImage2()
                                    backgroundImage.value = bitmap
                                    viewModel.editManager.cropWindow.init(
                                        bitmap.asAndroidBitmap()
                                    )
                                    return@clickable
                                }
                                editManager.cancelCropMode()
                                editManager.scaleToFit()
                                editManager.cropWindow.close()
                            }
                        }
                    },
                imageVector = ImageVector.vectorResource(R.drawable.ic_crop),
                tint = if (
                    viewModel.isCropping()
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
                                !viewModel.isCropping() &&
                                !viewModel.isResizing() &&
                                !isEyeDropperMode.value &&
                                !viewModel.isErasing() &&
                                !isBlurMode.value
                            ) {
                                viewModel.toggleRotate()
                                if (viewModel.isRotating()) {
                                    setBackgroundImage2()
                                    viewModel.showMenus(!viewModel.isRotating())
                                    scaleToFitOnEdit()
                                    return@clickable
                                }
                                cancelRotateMode()
                                scaleToFit()
                            }
                        }
                    },
                imageVector = ImageVector
                    .vectorResource(R.drawable.ic_rotate_90_degrees_ccw),
                tint = if (viewModel.isRotating())
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
                                !viewModel.isRotating() &&
                                !viewModel.isCropping() &&
                                !isEyeDropperMode.value &&
                                !viewModel.isErasing() &&
                                !isBlurMode.value
                            )
                                viewModel.toggleResize()
                            else return@clickable
                            viewModel.showMenus(!viewModel.isResizing())
                            if (viewModel.isResizing()) {
                                setBackgroundImage2()
                                val imgBitmap = viewModel.getEditedImage()
                                backgroundImage.value = imgBitmap
                                resizeOperation.init(
                                    imgBitmap.asAndroidBitmap()
                                )
                                return@clickable
                            }
                            cancelResizeMode()
                            scaleToFit()
                        }
                    },
                imageVector = ImageVector
                    .vectorResource(R.drawable.ic_aspect_ratio),
                tint = if (viewModel.isResizing())
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
                                !viewModel.isRotating() &&
                                !viewModel.isCropping() &&
                                !isEyeDropperMode.value &&
                                !viewModel.isResizing() &&
                                !viewModel.isErasing() &&
                                !editingState.strokeSliderExpanded
                            ) toggleBlurMode()
                            if (isBlurMode.value) {
                                setBackgroundImage2()
                                backgroundImage.value = viewModel.getEditedImage()
                                blurOperation.init()
                                return@clickable
                            }
                            blurOperation.cancel()
                            scaleToFit()
                        }
                    },
                imageVector = ImageVector
                    .vectorResource(R.drawable.ic_blur_on),
                tint = if (editManager.isBlurMode.value)
                    MaterialTheme.colors.primary
                else
                    Color.Black,
                contentDescription = null
            )
        }
    }
    viewModel.setBottomButtonsScrollIsAtStart(scrollState.value == 0)
    viewModel.setBottomButtonsScrollIsAtEnd(
        scrollState.value == scrollState.maxValue
    )
}

@Composable
fun EditMenuFlowHint(
    scrollIsAtStart: Boolean = true,
    scrollIsAtEnd: Boolean = false
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = scrollIsAtEnd || !scrollIsAtStart,
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
            visible = scrollIsAtStart || !scrollIsAtEnd,
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
    LaunchedEffect(viewModel.editingState.imageSaved) {
        if (!viewModel.editingState.imageSaved)
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
    if (!viewModel.editingState.showExitDialog) return

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            viewModel.showExitDialog(false)
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
                    viewModel.showExitDialog(false)
                    viewModel.showSavePathDialog(true)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.showExitDialog(false)
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