package com.hdfc.docupload.util

object Constants {
    // Demo credentials (client-side only; replaced by /login in production).
    const val DEMO_USERNAME = "hdfc"
    const val DEMO_PASSWORD = "123"

    const val JPEG_QUALITY = 80
    const val MAX_IMAGE_DIMENSION = 2048 // px, longest edge after compression

    const val WORK_UPLOAD_RETRY = "upload_retry_work"
    const val KEY_APPLICATION_NUMBER = "key_application_number"

    val SUPPORTED_MIME_TYPES = arrayOf("image/*", "application/pdf")
}
