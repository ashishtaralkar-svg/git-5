package com.hdfc.docupload.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Search : Screen("search")

    data object Upload : Screen("upload/{appNo}") {
        fun createRoute(appNo: String) = "upload/$appNo"
    }

    data object Uploaded : Screen("uploaded/{appNo}") {
        fun createRoute(appNo: String) = "uploaded/$appNo"
    }

    companion object {
        const val ARG_APP_NO = "appNo"
    }
}
