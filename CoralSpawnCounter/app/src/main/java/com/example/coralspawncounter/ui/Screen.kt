package com.example.coralspawncounter.ui

sealed class Screen(val route: String) {
    object Record : Screen("record")
    object Review : Screen("review")
}

val screens = listOf(Screen.Record, Screen.Review)
