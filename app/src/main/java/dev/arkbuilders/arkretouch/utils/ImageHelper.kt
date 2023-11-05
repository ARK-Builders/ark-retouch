package dev.arkbuilders.arkretouch.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import dev.arkbuilders.arkretouch.edit.ui.crop.CropWindow
import dev.arkbuilders.arkretouch.presentation.edit.resize.ResizeOperation
import dev.arkbuilders.arkretouch.presentation.edit.rotate.RotateOperation

fun Bitmap.crop(cropParams: CropWindow.CropParams): Bitmap = Bitmap.createBitmap(
    this,
    cropParams.x,
    cropParams.y,
    cropParams.width,
    cropParams.height
)

fun Bitmap.resize(scale: ResizeOperation.Scale): Bitmap {
    val matrix = Matrix()
    matrix.postScale(scale.x, scale.y)
    return Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        matrix,
        true
    )
}

fun Matrix.rotate(angle: Float, center: RotateOperation.Center) {
    this.postRotate(angle, center.x, center.y)
}