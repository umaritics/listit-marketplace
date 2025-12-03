package com.example.listit

data class Ad(
    val id: Int,
    val title: String,
    val price: Double,
    val location: String,
    val imagePath: String?, // Can be local path or server URL
    val date: String,
    val isSynced: Int
)