package space.taran.arkretouch.presentation.drawing

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.IntSize
import space.taran.arkretouch.presentation.edit.rotate.RotateGrid
import timber.log.Timber
import java.util.Stack

class EditManager {
    private val drawPaint: MutableState<Paint> = mutableStateOf(defaultPaint())
    private val erasePaint: Paint = Paint().apply {
        shader = null
        color = Color.Transparent
        style = PaintingStyle.Stroke
        blendMode = BlendMode.SrcOut
    }
    val currentPaint: Paint
        get() = if (isEraseMode.value) {
            erasePaint
        } else {
            drawPaint.value
        }

    val drawPaths = Stack<DrawPath>()
    private val redoPaths = Stack<DrawPath>()

    var backgroundImage = mutableStateOf<ImageBitmap?>(null)
    var backgroundImage2 = mutableStateOf<ImageBitmap?>(null)
    private val originalBackgroundImage = mutableStateOf<ImageBitmap?>(null)

    lateinit var bitmapToRotate: Bitmap
    val rotatePreviewBitmap: Bitmap
        get() = bitmapToRotate

    lateinit var rotationGrid: RotateGrid
    val rotateMatrix = Matrix()

    var drawAreaSize = mutableStateOf(IntSize.Zero)

    var invalidatorTick = mutableStateOf(0)

    private val _currentPaintColor: MutableState<Color> =
        mutableStateOf(drawPaint.value.color)
    val currentPaintColor: State<Color> = _currentPaintColor

    private val _isEraseMode: MutableState<Boolean> = mutableStateOf(false)
    val isEraseMode: State<Boolean> = _isEraseMode

    private val _canUndo: MutableState<Boolean> = mutableStateOf(false)
    val canUndo: State<Boolean> = _canUndo

    private val _canRedo: MutableState<Boolean> = mutableStateOf(false)
    val canRedo: State<Boolean> = _canRedo

    private val _isRotateMode = mutableStateOf(false)
    val isRotateMode = _isRotateMode

    val rotationAngle = mutableStateOf(0F)

    private val rotations = Stack<ImageBitmap>()
    private val redoRotations = Stack<ImageBitmap>()
    private val rotatedStack = Stack<Stack<DrawPath>>()

    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()

    internal fun clearRedoPath() {
        redoPaths.clear()
    }

    fun updateRevised() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun undoRotate() {
        if (rotations.isNotEmpty()) {
            redoRotations.push(backgroundImage.value)
            backgroundImage.value = rotations.pop()
            redrawRotatedPaths()
        }
    }

    private fun redoRotate() {
        if (redoRotations.isNotEmpty()) {
            rotations.push(backgroundImage.value)
            backgroundImage.value = redoRotations.pop()
            keepRotatedPaths()
        }
    }

    fun addRotation() {
        if (canRedo.value) clearRedo()
        rotations.add(backgroundImage2.value)
        undoStack.add(ROTATE)
        resetRotation()
        keepRotatedPaths()
        updateRevised()
    }

    fun rotateGrid(angle: Float = 0f) {
        rotationAngle.value += angle
    }

    private fun keepRotatedPaths() {
        val stack = Stack<DrawPath>()
        if (drawPaths.isNotEmpty()) {
            val size = drawPaths.size
            for (i in 1..size) {
                stack.push(drawPaths.pop())
            }
        }
        rotatedStack.add(stack)
    }

    private fun redrawRotatedPaths() {
        if (rotatedStack.isNotEmpty()) {
            val paths = rotatedStack.pop()
            if (paths.isNotEmpty()) {
                val size = paths.size
                for (i in 1..size) {
                    drawPaths.push(paths.pop())
                }
            }
        }
    }

    fun cancelRotateMode() {
        backgroundImage.value = backgroundImage2.value
        rotationAngle.value = 0f
    }

    private fun resetRotation() {
        rotationAngle.value = 0f
    }

    private fun clearRotations() {
        rotations.clear()
        redoRotations.clear()
    }

    private fun undoDraw() {
        if (drawPaths.isNotEmpty()) {
            redoPaths.push(drawPaths.pop())
        }
    }

    private fun redoDraw() {
        if (redoPaths.isNotEmpty()) {
            drawPaths.push(redoPaths.pop())
        }
    }

    fun undo() {
        if (canUndo.value) {
            val undoTask = undoStack.pop()
            redoStack.push(undoTask)
            Timber.tag("edit-manager").d("undoing $undoTask")
            if (undoTask == ROTATE)
                undoRotate()
            if (undoTask == DRAW)
                undoDraw()
        }
        invalidatorTick.value++
        updateRevised()
    }

    fun redo() {
        if (canRedo.value) {
            val redoTask = redoStack.pop()
            undoStack.push(redoTask)
            Timber.tag("edit-manager").d("redoing $redoTask")
            if (redoTask == ROTATE) {
                redoRotate()
            }
            if (redoTask == DRAW)
                redoDraw()
        }
        invalidatorTick.value++
        updateRevised()
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

    fun setBackgroundImage2() {
        backgroundImage2.value = backgroundImage.value
    }

    fun setOriginalBackgroundImage(imgBitmap: ImageBitmap?) {
        originalBackgroundImage.value = imgBitmap
    }

    private fun restoreOriginalBackgroundImage() {
        backgroundImage.value = originalBackgroundImage.value
    }

    fun setPaintColor(color: Color) {
        drawPaint.value.color = color
        _currentPaintColor.value = color
    }

    private fun clearPaths() {
        drawPaths.clear()
        redoPaths.clear()
        invalidatorTick.value++
        updateRevised()
    }

    fun clearEdits() {
        clearPaths()
        clearRotations()
        undoStack.clear()
        redoStack.clear()
        restoreOriginalBackgroundImage()
        updateRevised()
    }

    private fun clearRedo() {
        redoPaths.clear()
        redoRotations.clear()
        redoStack.clear()
        updateRevised()
    }

    fun toggleEraseMode() {
        _isEraseMode.value = !isEraseMode.value
    }

    fun toggleRotateMode() {
        _isRotateMode.value = !isRotateMode.value
    }

    fun setPaintStrokeWidth(strokeWidth: Float) {
        drawPaint.value.strokeWidth = strokeWidth
    }

    fun calcImageOffset(): Offset {
        val drawArea = drawAreaSize.value
        val bitmap = backgroundImage.value!!
        val xOffset = (drawArea.width - bitmap.width) / 2f
        val yOffset = (drawArea.height - bitmap.height) / 2f
        return Offset(xOffset, yOffset)
    }

    companion object {
        private const val DRAW = "draw"
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
