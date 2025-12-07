package com.example.listit

data class Ad(
    val id: Int,
    val title: String,
    val price: Double,
    val location: String,
    val category: String,   // Added for filtering
    val condition: String,  // Added for filtering
    val imagePath: String?,
    val date: String,
    val isSynced: Int
)