package com.example.listit

data class Ad(
    val id: Int,
    val title: String,
    val price: Double,
    val location: String,
    val category: String,
    val condition: String,
    val imagePath: String?,
    val date: String,
    val isSynced: Int,
    var isSaved: Boolean = false // New Field
)