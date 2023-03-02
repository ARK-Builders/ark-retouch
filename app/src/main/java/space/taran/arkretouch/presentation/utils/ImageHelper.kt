package space.taran.arkretouch.presentation.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import space.taran.arkretouch.presentation.edit.rotate.RotateGrid

fun Bitmap.rotate(
    angle: Float,
    shouldResize: Boolean,
    resize: (Bitmap, Int, Int) -> Bitmap
): Bitmap {
    val matrix = Matrix()
    val aspectRatio = width.toFloat() / height.toFloat()
    var newWidth: Int = width
    var newHeight: Int = height
    matrix.postRotate(angle)
    var source = Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        matrix,
        true
    )
    if (height > width || height == width) {
        newWidth = (3 * source.width) - (2 * width)
        newHeight = (newWidth / aspectRatio).toInt()
    }
    if (width > height) {
        newHeight = (3 * source.height) - (2 * height)
        newWidth = (newHeight * aspectRatio).toInt()
    }
    source = resize(source, newWidth, newHeight)
    /* if (shouldResize) {
        val aspectRatio: Float = newWidth.toFloat() / newHeight.toFloat()
        newHeight = newWidth
        newWidth = (newHeight * aspectRatio).toInt()
        source = resize(this, newWidth, newHeight)
    }*/
    return source
}

fun Bitmap.getOriginalSized(cropParams: RotateGrid.CropParams): Bitmap =
    Bitmap.createBitmap(
        this,
        cropParams.x,
        cropParams.y,
        cropParams.width,
        cropParams.height
    )
