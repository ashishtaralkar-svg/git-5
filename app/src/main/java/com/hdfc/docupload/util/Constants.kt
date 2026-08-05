package com.hdfc.docupload.util

object Constants {
    // Demo credentials (client-side only; replaced by /login in production).
    const val DEMO_USERNAME = "hdfc"
    const val DEMO_PASSWORD = "123"

    const val JPEG_QUALITY = 80
    const val MAX_IMAGE_DIMENSION = 2048 // px, longest edge after compression

    // Dynamsoft license key (client-side keys are expected — usage is enforced
    // by Dynamsoft's license server, not by keeping this secret). Same key
    // used by the web scanner, see web/js/scanner.js.
    const val DYNAMSOFT_LICENSE =
        "DLS2eyJoYW5kc2hha2VDb2RlIjoiMTA2MDc3MTAxLU1UQTJNRGMzTVRBeExYZGxZaTFVY21saGJGQnliMm8iLCJtYWluU2VydmVyVVJMIjoiaHR0cHM6Ly9tZGxzLmR5bmFtc29mdG9ubGluZS5jb20vIiwib3JnYW5pemF0aW9uSUQiOiIxMDYwNzcxMDEiLCJzdGFuZGJ5U2VydmVyVVJMIjoiaHR0cHM6Ly9zZGxzLmR5bmFtc29mdG9ubGluZS5jb20vIiwiY2hlY2tDb2RlIjoxNDI2NTgzNDU1fQ=="

    const val WORK_UPLOAD_RETRY = "upload_retry_work"
    const val KEY_APPLICATION_NUMBER = "key_application_number"

    val SUPPORTED_MIME_TYPES = arrayOf("image/*", "application/pdf")
}
