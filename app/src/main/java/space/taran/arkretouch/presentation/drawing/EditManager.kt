package space.taran.arkretouch.presentation.drawing

import android.graphics.Matrix
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.IntSize
import space.taran.arkretouch.data.ImageDefaults
import space.taran.arkretouch.data.Resolution
import space.taran.arkretouch.presentation.edit.ImageViewParams
import space.taran.arkretouch.presentation.edit.Operation
import space.taran.arkretouch.presentation.edit.crop.CropOperation
import timber.log.Timber
import space.taran.arkretouch.presentation.edit.crop.CropWindow
import space.taran.arkretouch.presentation.edit.draw.DrawOperation
import space.taran.arkretouch.presentation.edit.fitBackground
import space.taran.arkretouch.presentation.edit.fitImage
import space.taran.arkretouch.presentation.edit.resize.ResizeOperation
import space.taran.arkretouch.presentation.edit.rotate.RotateOperation
import java.util.Stack

class EditManager {
    private val drawPaint: MutableState<Paint> = mutableStateOf(defaultPaint())

    private val _paintColor: MutableState<Color> =
        mutableStateOf(drawPaint.value.color)
    val paintColor: State<Color> = _paintColor

    private val erasePaint: Paint = Paint().apply {
        shader = null
        color = Color.Transparent
        style = PaintingStyle.Stroke
        blendMode = BlendMode.SrcOut
    }

    val cropWindow = CropWindow(this)

    val drawOperation = DrawOperation(this)
    val resizeOperation = ResizeOperation(this)
    val rotateOperation = RotateOperation(this)
    val cropOperation = CropOperation(this)

    private val currentPaint: Paint
        get() = if (isEraseMode.value) {
            erasePaint
        } else {
            drawPaint.value
        }

    val drawPaths = Stack<DrawPath>()
    val redoPaths = Stack<DrawPath>()

    val backgroundImage = mutableStateOf<ImageBitmap?>(null)
    val _backgroundImage: State<ImageBitmap?> = backgroundImage
    private val _backgroundColor = mutableStateOf(Color.White)
    val backgroundColor: State<Color> = _backgroundColor
    private val backgroundImage2 = mutableStateOf<ImageBitmap?>(null)
    private val originalBackgroundImage = mutableStateOf<ImageBitmap?>(null)

    val matrix = Matrix()
    val editMatrix = Matrix()
    lateinit var matrixScale: ResizeOperation.Scale
        private set
    lateinit var bitmapScale: ResizeOperation.Scale
        private set

    val imageSize: IntSize
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
                } ?: resolution.value?.toIntSize()!!
        }

    private val _resolution = mutableStateOf<Resolution?>(null)
    val resolution: State<Resolution?> = _resolution
    var drawAreaSize = mutableStateOf(IntSize.Zero)
    val availableDrawAreaSize = mutableStateOf(IntSize.Zero)

    var invalidatorTick = mutableStateOf(0)

    private val _isEraseMode: MutableState<Boolean> = mutableStateOf(false)
    val isEraseMode: State<Boolean> = _isEraseMode

    private val _canUndo: MutableState<Boolean> = mutableStateOf(false)
    val canUndo: State<Boolean> = _canUndo

    private val _canRedo: MutableState<Boolean> = mutableStateOf(false)
    val canRedo: State<Boolean> = _canRedo

    private val _isRotateMode = mutableStateOf(false)
    val isRotateMode = _isRotateMode

    private val _isResizeMode = mutableStateOf(false)
    val isResizeMode = _isResizeMode

    private val _isEyeDropperMode = mutableStateOf(false)
    val isEyeDropperMode = _isEyeDropperMode

    val rotationAngle = mutableStateOf(0F)
    var prevRotationAngle = 0f

    private val editedPaths = Stack<Stack<DrawPath>>()

    val redoResize = Stack<ImageBitmap>()
    val resizes = Stack<ImageBitmap>()
    val rotationAngles = Stack<Float>()
    val redoRotationAngles = Stack<Float>()

    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()

    private val _isCropMode = mutableStateOf(false)
    val isCropMode = _isCropMode

    val cropStack = Stack<ImageBitmap>()
    val redoCropStack = Stack<ImageBitmap>()

    fun applyOperation(operation: Operation) {
        operation.apply()
    }

    private fun undoOperation(operation: Operation) {
        operation.undo()
    }

    private fun redoOperation(operation: Operation) {
        operation.redo()
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
                resolution.value!!,
                drawAreaSize.value.width,
                drawAreaSize.value.height
            )
        }
        matrixScale = viewParams.scale
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
        maxHeight: Int = drawAreaSize.value.height
    ): ImageViewParams {
        val viewParams = backgroundImage.value?.let {
             fitImage(it, maxWidth, maxHeight)
        } ?: run {
            fitBackground(
                resolution.value!!,
                maxWidth,
                maxHeight
            )
        }
        scaleEditMatrix(viewParams)
        updateAvailableDrawArea(viewParams.drawArea)
        return viewParams
    }

    private fun scaleMatrix(viewParams: ImageViewParams) {
        matrix.setScale(viewParams.scale.x, viewParams.scale.y)
        if (prevRotationAngle != 0f) {
            val centerX = viewParams.drawArea.width / 2f
            val centerY = viewParams.drawArea.height / 2f
            matrix.postRotate(prevRotationAngle, centerX, centerY)
        }
    }

    private fun scaleEditMatrix(viewParams: ImageViewParams) {
        editMatrix.setScale(viewParams.scale.x, viewParams.scale.y)
        if (prevRotationAngle != 0f && isRotateMode.value) {
            val centerX = viewParams.drawArea.width / 2f
            val centerY = viewParams.drawArea.height / 2f
            editMatrix.postRotate(prevRotationAngle, centerX, centerY)
        }
    }

    fun setBackgroundColor(color: Color) {
        _backgroundColor.value = color
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
            val drawWidth = it.width * matrixScale.x
            val drawHeight = it.height * matrixScale.y
            IntSize(
                drawWidth.toInt(),
                drawHeight.toInt()
            )
        } ?: run {
            val drawWidth = resolution.value?.width!! * matrixScale.x
            val drawHeight = resolution.value?.height!! * matrixScale.y
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

    fun rotate(angle: Float) {
        val centerX = availableDrawAreaSize.value.width / 2
        val centerY = availableDrawAreaSize.value.height / 2
        if (isRotateMode.value) {
            rotationAngle.value += angle
            rotateOperation.rotate(
                editMatrix,
                angle,
                centerX.toFloat(),
                centerY.toFloat()
            )
            return
        }
        rotateOperation.rotate(
            matrix,
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

    fun operationByTask(task: String) = when (task) {
        ROTATE -> rotateOperation
        RESIZE -> resizeOperation
        CROP -> cropOperation
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
            // rotate(prevRotationAngle)
        }
    }

    fun addDrawPath(path: Path) {
        drawPaths.add(
            DrawPath(
                path,
                currentPaint.copy().apply {
                    strokeWidth = drawPaint.value.strokeWidth
                }
            )
        )
        if (canRedo.value) clearRedo()
        undoStack.add(DRAW)
    }

    fun setPaintColor(color: Color) {
        drawPaint.value.color = color
        _paintColor.value = color
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

    fun setOriginalBackgroundImage(imgBitmap: ImageBitmap?) {
        originalBackgroundImage.value = imgBitmap
    }

    private fun restoreOriginalBackgroundImage() {
        backgroundImage.value = originalBackgroundImage.value
        updateAvailableDrawArea()
    }

    fun toggleEraseMode() {
        _isEraseMode.value = !isEraseMode.value
    }

    fun toggleRotateMode() {
        _isRotateMode.value = !isRotateMode.value
        if (isRotateMode.value) editMatrix.set(matrix)
    }

    fun toggleCropMode() {
        _isCropMode.value = !isCropMode.value
        if (!isCropMode.value) cropWindow.close()
    }

    fun cancelCropMode() {
        backgroundImage.value = backgroundImage2.value
        editMatrix.reset()
    }

    fun cancelRotateMode() {
        rotationAngle.value = prevRotationAngle
        editMatrix.reset()
    }

    fun toggleResizeMode() {
        _isResizeMode.value = !isResizeMode.value
    }

    fun cancelResizeMode() {
        backgroundImage.value = backgroundImage2.value
        editMatrix.reset()
    }

    fun setPaintStrokeWidth(strokeWidth: Float) {
        drawPaint.value.strokeWidth = strokeWidth
    }

    fun calcImageOffset(): Offset {
        val drawArea = drawAreaSize.value
        var offset = Offset.Zero
        backgroundImage.value?.let { bitmap ->
            val xOffset = ((drawArea.width - bitmap.width) / 2).coerceAtLeast(0)
            val yOffset = ((drawArea.height - bitmap.height) / 2).coerceAtLeast(0)
            offset = Offset(xOffset.toFloat(), yOffset.toFloat())
        }
        return offset
    }

    private companion object {
        private const val DRAW = "draw"
        private const val CROP = "crop"
        private const val RESIZE = "resize"
        private const val ROTATE = "rotate"
    }
}

class DrawPath(
    val path: Path,
    val paint: Paint
)

fun Paint.copy(): Paint {
    val from = this
    return Paint().apply {
        alpha = from.alpha
        isAntiAlias = from.isAntiAlias
        color = from.color
        blendMode = from.blendMode
        style = from.style
        strokeWidth = from.strokeWidth
        strokeCap = from.strokeCap
        strokeJoin = from.strokeJoin
        strokeMiterLimit = from.strokeMiterLimit
        filterQuality = from.filterQuality
        shader = from.shader
        colorFilter = from.colorFilter
        pathEffect = from.pathEffect
    }
}

fun defaultPaint(): Paint {
    return Paint().apply {
        color = Color.White
        strokeWidth = 14f
        isAntiAlias = true
        style = PaintingStyle.Stroke
        strokeJoin = StrokeJoin.Round
        strokeCap = StrokeCap.Round
    }
}
