package net.fk.SmartDoc.presentation

import android.graphics.Bitmap
import android.graphics.Color
import com.jabistudio.androidjhlabs.filter.SharpenFilter
import com.jabistudio.androidjhlabs.filter.util.AndroidUtils
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat

import android.graphics.drawable.BitmapDrawable
import android.content.res.Resources


object  CustomFilters {

    fun applySepiaToningEffect(
        src: Bitmap,
        depth: Int,
        red: Double,
        green: Double,
        blue: Double
    ): Bitmap? {
        // image size
        val width = src.width
        val height = src.height
        // create output bitmap
        val bmOut = Bitmap.createBitmap(width, height, src.config)
        // constant grayscale
        val GS_RED = 0.3
        val GS_GREEN = 0.59
        val GS_BLUE = 0.11
        // color information
        var A: Int
        var R: Int
        var G: Int
        var B: Int
        var pixel: Int

        // scan through all pixels
        for (x in 0 until width) {
            for (y in 0 until height) {
                // get pixel color
                pixel = src.getPixel(x, y)
                // get color on each channel
                A = Color.alpha(pixel)
                R = Color.red(pixel)
                G = Color.green(pixel)
                B = Color.blue(pixel)
                // apply grayscale sample
                R = (GS_RED * R + GS_GREEN * G + GS_BLUE * B).toInt()
                G = R
                B = G

                // apply intensity level for sepid-toning on each channel
                R += (depth * red).toInt()
                if (R > 255) {
                    R = 255
                }
                G += (depth * green).toInt()
                if (G > 255) {
                    G = 255
                }
                B += (depth * blue).toInt()
                if (B > 255) {
                    B = 255
                }

                // set new pixel color to output image
                bmOut.setPixel(x, y, Color.argb(A, R, G, B))
            }
        }

        // return final image
        return bmOut
    }


     fun applyFilters (bitmap: Bitmap) : Bitmap?{
        //SharpenFilter
        //ContrastFilter
        // SepiaToningEffect
         val bd = BitmapDrawable(Resources.getSystem(),bitmap)
         bd.setAntiAlias(false);
         bd.setFilterBitmap(false);
         val myBitmap: Bitmap = bd.getBitmap()



        val mFliter = SharpenFilter()
        val src2 = AndroidUtils.bitmapToIntArray(bitmap)
        val width: Int = bitmap.getWidth()
        val height: Int = bitmap.getHeight()
        val dest: IntArray = mFliter.filter(src2, width, height)


        val destBitmap = Bitmap.createBitmap(dest, width, height, Bitmap.Config.ARGB_8888)
        return destBitmap
    }

     fun  increaseBrightness(bitmap: Bitmap, value: Int): Bitmap? {
        val src = Mat(bitmap.height, bitmap.width, CvType.CV_8UC1)
        Utils.bitmapToMat(bitmap, src)
        src.convertTo(src, -1, 1.0, value.toDouble())
        val result = Bitmap.createBitmap(src.cols(), src.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(src, result)
        return result
    }
}