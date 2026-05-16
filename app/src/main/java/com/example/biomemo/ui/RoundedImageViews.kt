package com.example.biomemo.ui

import android.content.Context
import android.widget.ImageView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel

fun roundedImageView(context: Context, cornerRadiusPx: Float): ShapeableImageView {
    return ShapeableImageView(context).apply {
        applyRoundedCorners(cornerRadiusPx)
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
}

fun ImageView.applyRoundedCorners(cornerRadiusPx: Float) {
    if (this is ShapeableImageView) {
        shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornerRadiusPx)
            .build()
    }
}
