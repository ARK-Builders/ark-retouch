package dev.arkbuilders.arkretouch.editing.manager

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import android.graphics.Matrix
import dev.arkbuilders.arkretouch.data.model.DrawPath
import dev.arkbuilders.arkretouch.data.model.ImageDefaults
import dev.arkbuilders.arkretouch.data.model.ImageViewParams
import dev.arkbuilders.arkretouch.data.model.Resolution
import dev.arkbuilders.arkretouch.editing.Operation
import dev.arkbuilders.arkretouch.editing.blur.BlurOperation
import dev.arkbuilders.arkretouch.editing.crop.CropOperation
import dev.arkbuilders.arkretouch.editing.crop.CropWindow
import dev.arkbuilders.arkretouch.editing.draw.DrawOperation
import dev.arkbuilders.arkretouch.editing.resize.ResizeOperation
import dev.arkbuilders.arkretouch.editing.rotate.RotateOperation
import dev.arkbuilders.arkretouch.presentation.viewmodels.fitBackground
import dev.arkbuilders.arkretouch.presentation.viewmodels.fitImage
import timber.log.Timber
import java.util.Stack

// FIXME: This class is overloaded, split into smaller classes/managers
class EditManager {

    private var imageSize: IntSize = IntSize.Zero

    private val _backgroundColor = mutableStateOf(Color.Transparent)

    val blurIntensity = mutableStateOf(12f)

    val cropWindow = CropWindow(this)

    val drawOperation = DrawOperation(this)
    val resizeOperation = ResizeOperation(this)
    val rotateOperation = RotateOperation(this, {})
    val cropOperation = CropOperation(this, {})
    val blurOperation = BlurOperation(this, imageSize)

    val drawPaths = Stack<DrawPath>()

    val redoPaths = Stack<DrawPath>()

    val backgroundImage = mutableStateOf<ImageBitmap?>(null)
    val backgroundImage2 = mutableStateOf<ImageBitmap?>(null)
    val originalBackgroundImage = mutableStateOf<ImageBitmap?>(null)

    val matrix = Matrix()
    val editMatrix = Matrix()
    val backgroundMatrix = Matrix()
    val rectMatrix = Matrix()

    private val matrixScale = mutableStateOf(1f)
    var zoomScale = 1f
    lateinit var bitmapScale: ResizeOperation.Scale
        private set

    /* val imageSize: IntSize
        get() {
            return if (isResizeMode.value)
                backgroundImage2.value?.let {
                    IntSize(it.width, it.height)
                } ?: originalBackgroundImage.value?.let {
                    IntSize(it.width, it.height)
                } ?: resolution.value?.toIntSize()!!
            else
                backgroundImage.value?.let {
                    IntSize(it.width, it.height)
                } ?: resolution.value?.toIntSize() ?: drawAreaSize.value
        }*/

    private val _resolution = mutableStateOf<Resolution?>(null)
    val resolution: State<Resolution?> = _resolution
    var drawAreaSize = mutableStateOf(IntSize.Zero)
    val availableDrawAreaSize = mutableStateOf(IntSize.Zero)

    var invalidatorTick = mutableStateOf(0)

    // TODO: Consider using [EditionMode] instead
    private val _canUndo: MutableState<Boolean> = mutableStateOf(false)
    val canUndo: State<Boolean> = _canUndo

    // TODO: Consider using [EditionMode] instead
    private val _canRedo: MutableState<Boolean> = mutableStateOf(false)
    val canRedo: State<Boolean> = _canRedo

    // TODO: Consider using [EditionMode] instead
    private val _isEyeDropperMode = mutableStateOf(false)
    val isEyeDropperMode: State<Boolean> = _isEyeDropperMode

    // TODO: Consider using [EditionMode] instead
    private val _isBlurMode = mutableStateOf(false)
    val isBlurMode: State<Boolean> = _isBlurMode

    // TODO: Consider using [EditionMode] instead
    private val _isZoomMode = mutableStateOf(false)
    val isZoomMode: State<Boolean> = _isZoomMode

    // TODO: Consider using [EditionMode] instead
    private val _isPanMode = mutableStateOf(false)
    val isPanMode: State<Boolean> = _isPanMode

    val rotationAngle = mutableStateOf(0F)
    var prevRotationAngle = 0f

    private val editedPaths = Stack<Stack<DrawPath>>()

    val redoResize = Stack<ImageBitmap>()
    val resizes = Stack<ImageBitmap>()
    val rotationAngles = Stack<Float>()
    val redoRotationAngles = Stack<Float>()

    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()

    val cropStack = Stack<ImageBitmap>()
    val redoCropStack = Stack<ImageBitmap>()

    private fun undoOperation(operation: Operation) {
        operation.undo()
    }

    private fun redoOperation(operation: Operation) {
        operation.redo()
    }

    fun setImageSize(size: IntSize) {
        if (size != IntSize.Zero) {
            imageSize = size
        }
    }

    fun scaleToFit() {
        val viewParams = backgroundImage.value?.let {
            fitImage(
                it,
                drawAreaSize.value.width,
                drawAreaSize.value.height
            )
        } ?: run {
            fitBackground(
                imageSize,
                drawAreaSize.value.width,
                drawAreaSize.value.height
            )
        }
        matrixScale.value = viewParams.scale.x
        scaleMatrix(viewParams)
        updateAvailableDrawArea(viewParams.drawArea)
        val bitmapXScale =
            imageSize.width.toFloat() / viewParams.drawArea.width.toFloat()
        val bitmapYScale =
            imageSize.height.toFloat() / viewParams.drawArea.height.toFloat()
        bitmapScale = ResizeOperation.Scale(
            bitmapXScale,
            bitmapYScale
        )
    }

    fun scaleToFitOnEdit(
        maxWidth: Int = drawAreaSize.value.width,
        maxHeight: Int = drawAreaSize.value.height,
        isRotating: Boolean = false
    ): ImageViewParams {
        val viewParams = backgroundImage.value?.let {
            fitImage(it, maxWidth, maxHeight)
        } ?: run {
            fitBackground(
                imageSize,
                maxWidth,
                maxHeight
            )
        }
        scaleEditMatrix(viewParams, isRotating)
        updateAvailableDrawArea(viewParams.drawArea)
        return viewParams
    }

    private fun scaleMatrix(viewParams: ImageViewParams) {
        matrix.setScale(viewParams.scale.x, viewParams.scale.y)
        backgroundMatrix.setScale(viewParams.scale.x, viewParams.scale.y)
        if (prevRotationAngle != 0f) {
            val centerX = viewParams.drawArea.width / 2f
            val centerY = viewParams.drawArea.height / 2f
            matrix.postRotate(prevRotationAngle, centerX, centerY)
        }
    }

    private fun scaleEditMatrix(viewParams: ImageViewParams, isRotating: Boolean) {
        editMatrix.setScale(viewParams.scale.x, viewParams.scale.y)
        backgroundMatrix.setScale(viewParams.scale.x, viewParams.scale.y)
        if (rotationAngle.value != 0f && isRotating) {
            val centerX = viewParams.drawArea.width / 2f
            val centerY = viewParams.drawArea.height / 2f
            editMatrix.postRotate(rotationAngle.value, centerX, centerY)
        }
    }

    fun setImageResolution(value: Resolution) {
        _resolution.value = value
    }

    fun initDefaults(defaults: ImageDefaults, maxResolution: Resolution) {
        defaults.resolution?.let {
            _resolution.value = it
        }
        if (resolution.value == null)
            _resolution.value = maxResolution
        _backgroundColor.value = Color(defaults.colorValue)
    }

    fun updateAvailableDrawAreaByMatrix() {
        val drawArea = backgroundImage.value?.let {
            val drawWidth = it.width * matrixScale.value
            val drawHeight = it.height * matrixScale.value
            IntSize(
                drawWidth.toInt(),
                drawHeight.toInt()
            )
        } ?: run {
            val drawWidth = resolution.value?.width!! * matrixScale.value
            val drawHeight = resolution.value?.height!! * matrixScale.value
            IntSize(
                drawWidth.toInt(),
                drawHeight.toInt()
            )
        }
        updateAvailableDrawArea(drawArea)
    }

    fun updateAvailableDrawArea(bitmap: ImageBitmap? = backgroundImage.value) {
        if (bitmap == null) {
            resolution.value?.let {
                availableDrawAreaSize.value = it.toIntSize()
            }
            return
        }
        availableDrawAreaSize.value = IntSize(
            bitmap.width,
            bitmap.height
        )
    }

    fun updateAvailableDrawArea(area: IntSize) {
        availableDrawAreaSize.value = area
    }

    internal fun clearRedoPath() {
        redoPaths.clear()
    }

    fun toggleEyeDropper() {
        _isEyeDropperMode.value = !isEyeDropperMode.value
    }

    fun updateRevised() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun resizeDown(width: Int = 0, height: Int = 0) =
        resizeOperation.resizeDown(width, height) {
            backgroundImage.value = it
        }

    fun RotateOperation.onRotate(angle: Float) {
        val centerX = availableDrawAreaSize.value.width / 2
        val centerY = availableDrawAreaSize.value.height / 2
        rotationAngle.value += angle
        rotate(
            editMatrix,
            angle,
            centerX.toFloat(),
            centerY.toFloat()
        )
    }

    fun addRotation() {
        if (canRedo.value) clearRedo()
        rotationAngles.add(prevRotationAngle)
        undoStack.add(ROTATE)
        prevRotationAngle = rotationAngle.value
        updateRevised()
    }

    private fun addAngle() {
        rotationAngles.add(prevRotationAngle)
    }

    fun addResize() {
        if (canRedo.value) clearRedo()
        resizes.add(backgroundImage2.value)
        undoStack.add(RESIZE)
        keepEditedPaths()
        updateRevised()
    }

    fun keepEditedPaths() {
        val stack = Stack<DrawPath>()
        if (drawPaths.isNotEmpty()) {
            val size = drawPaths.size
            for (i in 1..size) {
                stack.push(drawPaths.pop())
            }
        }
        editedPaths.add(stack)
    }

    fun redrawEditedPaths() {
        if (editedPaths.isNotEmpty()) {
            val paths = editedPaths.pop()
            if (paths.isNotEmpty()) {
                val size = paths.size
                for (i in 1..size) {
                    drawPaths.push(paths.pop())
                }
            }
        }
    }

    fun addCrop() {
        if (canRedo.value) clearRedo()
        cropStack.add(backgroundImage2.value)
        undoStack.add(CROP)
        updateRevised()
    }

    fun addBlur() {
        if (canRedo.value) clearRedo()
        undoStack.add(BLUR)
        updateRevised()
    }

    private fun operationByTask(task: String) = when (task) {
        ROTATE -> rotateOperation
        RESIZE -> resizeOperation
        CROP -> cropOperation
        BLUR -> blurOperation
        else -> drawOperation
    }

    fun undo() {
        if (canUndo.value) {
            val undoTask = undoStack.pop()
            redoStack.push(undoTask)
            Timber.tag("edit-manager").d("undoing $undoTask")
            undoOperation(operationByTask(undoTask))
        }
        invalidatorTick.value++
        updateRevised()
    }

    fun redo() {
        if (canRedo.value) {
            val redoTask = redoStack.pop()
            undoStack.push(redoTask)
            Timber.tag("edit-manager").d("redoing $redoTask")
            redoOperation(operationByTask(redoTask))
            invalidatorTick.value++
            updateRevised()
        }
    }

    fun saveRotationAfterOtherOperation() {
        addAngle()
        resetRotation()
    }

    fun restoreRotationAfterUndoOtherOperation() {
        if (rotationAngles.isNotEmpty()) {
            prevRotationAngle = rotationAngles.pop()
            rotationAngle.value = prevRotationAngle
        }
    }

    fun addDrawPath(path: DrawPath) {
        drawPaths.add(path)
        if (canRedo.value) clearRedo()
        undoStack.add(DRAW)
    }

    private fun clearPaths() {
        drawPaths.clear()
        redoPaths.clear()
        invalidatorTick.value++
        updateRevised()
    }

    private fun clearResizes() {
        resizes.clear()
        redoResize.clear()
        updateRevised()
    }

    private fun resetRotation() {
        rotationAngle.value = 0f
        prevRotationAngle = 0f
    }

    private fun clearRotations() {
        rotationAngles.clear()
        redoRotationAngles.clear()
        resetRotation()
    }

    fun clearEdits() {
        clearPaths()
        clearResizes()
        clearRotations()
        clearCrop()
        blurOperation.clear()
        undoStack.clear()
        redoStack.clear()
        restoreOriginalBackgroundImage()
        scaleToFit()
        updateRevised()
    }

    private fun clearRedo() {
        redoPaths.clear()
        redoCropStack.clear()
        redoRotationAngles.clear()
        redoResize.clear()
        redoStack.clear()
        updateRevised()
    }

    private fun clearCrop() {
        cropStack.clear()
        redoCropStack.clear()
        updateRevised()
    }

    fun setBackgroundImage2() {
        backgroundImage2.value = backgroundImage.value
    }

    fun redrawBackgroundImage2() {
        backgroundImage.value = backgroundImage2.value
    }

    fun setOriginalBackgroundImage(imgBitmap: ImageBitmap?) {
        originalBackgroundImage.value = imgBitmap
    }

    private fun restoreOriginalBackgroundImage() {
        backgroundImage.value = originalBackgroundImage.value
        updateAvailableDrawArea()
    }

    fun toggleZoomMode() {
        _isZoomMode.value = !isZoomMode.value
    }

    fun togglePanMode() {
        _isPanMode.value = !isPanMode.value
    }

    fun cancelCropMode() {
        backgroundImage.value = backgroundImage2.value
        editMatrix.reset()
    }

    fun cancelRotateMode() {
        rotationAngle.value = prevRotationAngle
        editMatrix.reset()
    }

    fun cancelResizeMode() {
        backgroundImage.value = backgroundImage2.value
        editMatrix.reset()
    }

    fun toggleBlurMode() {
        _isBlurMode.value = !isBlurMode.value
    }

    fun calcImageOffset(): Offset {
        val drawArea = drawAreaSize.value
        val allowedArea = availableDrawAreaSize.value
        val xOffset = ((drawArea.width - allowedArea.width) / 2f)
            .coerceAtLeast(0f)
        val yOffset = ((drawArea.height - allowedArea.height) / 2f)
            .coerceAtLeast(0f)
        return Offset(xOffset, yOffset)
    }

    fun calcCenter() = Offset(
        availableDrawAreaSize.value.width / 2f,
        availableDrawAreaSize.value.height / 2f
    )

    private companion object {
        private const val DRAW = "draw"
        private const val CROP = "crop"
        private const val RESIZE = "resize"
        private const val ROTATE = "rotate"
        private const val BLUR = "blur"
    }
}