package com.medicineboxnotes.android.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.util.UUID

data class StoredImage(val imagePath: String, val thumbnailPath: String)

class ImageStore(private val context: Context) {
    fun import(uri: Uri): StoredImage {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ -> decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE }
        else context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
        val normalized = bitmap.scaledTo(1800)
        val thumb = bitmap.scaledTo(360)
        val id = UUID.randomUUID().toString(); val relativeDir = "images"; val dir = context.filesDir.resolve(relativeDir).apply { mkdirs() }
        val original = dir.resolve("$id-original.jpg"); val thumbnail = dir.resolve("$id-thumb.jpg")
        original.outputStream().use { normalized.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        thumbnail.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 78, it) }
        if (normalized !== bitmap) normalized.recycle(); if (thumb !== bitmap) thumb.recycle(); bitmap.recycle()
        return StoredImage("$relativeDir/${original.name}", "$relativeDir/${thumbnail.name}")
    }

    private fun Bitmap.scaledTo(max: Int): Bitmap {
        if (width <= max && height <= max) return this
        val ratio = max.toFloat() / maxOf(width, height); return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }
}
