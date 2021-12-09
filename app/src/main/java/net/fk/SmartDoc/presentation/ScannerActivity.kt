package net.fk.SmartDoc.presentation

import android.graphics.Bitmap
import android.widget.Toast
import net.fk.SmartDoc.R
import net.fk.SmartDoc.exceptions.NullCorners

class ScannerActivity : BaseScannerActivity() {
    override fun onError(throwable: Throwable) {
        when (throwable) {
            is NullCorners -> Toast.makeText(
                this,
                R.string.null_corners,
                Toast.LENGTH_LONG
            ).show()
            else -> Toast.makeText(this,
                throwable.message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDocumentAccepted(bitmap: Bitmap) {
    }

    override fun onClose() {
        finish()
    }

}
