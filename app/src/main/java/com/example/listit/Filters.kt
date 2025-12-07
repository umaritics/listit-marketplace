package com.example.listit

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Filters : AppCompatActivity() {

    private var selectedCategory: String = ""
    private var selectedCondition: String = ""
    private val categoryLayouts = ArrayList<LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filters)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.btn_close).setOnClickListener { finish() }

        // --- Category Logic ---
        // Bind all category layouts to the selection logic
        bindCategory(findViewById(R.id.filter_cat_car), "Car")
        bindCategory(findViewById(R.id.filter_cat_bike), "Bike")
        bindCategory(findViewById(R.id.filter_cat_housing), "Property")
        bindCategory(findViewById(R.id.filter_cat_mobile), "Mobile")
        bindCategory(findViewById(R.id.filter_cat_furniture), "Furniture")
        bindCategory(findViewById(R.id.filter_cat_fashion), "Fashion")

        // --- Condition Logic ---
        val btnNew = findViewById<Button>(R.id.btn_new)
        val btnUsed = findViewById<Button>(R.id.btn_used)

        btnNew.setOnClickListener {
            selectedCondition = "New"
            updateConditionUI(btnNew, btnUsed)
        }
        btnUsed.setOnClickListener {
            selectedCondition = "Used"
            updateConditionUI(btnUsed, btnNew)
        }

        // --- Save Logic ---
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val intent = Intent()

            if (selectedCategory.isNotEmpty()) intent.putExtra("category", selectedCategory)
            if (selectedCondition.isNotEmpty()) intent.putExtra("condition", selectedCondition)

            val minPrice = findViewById<EditText>(R.id.rs_min)?.text.toString()
            val maxPrice = findViewById<EditText>(R.id.rs_max)?.text.toString()
            val location = findViewById<EditText>(R.id.et_location)?.text.toString()

            if (minPrice.isNotEmpty()) intent.putExtra("minPrice", minPrice.toDouble())
            if (maxPrice.isNotEmpty()) intent.putExtra("maxPrice", maxPrice.toDouble())
            if (location.isNotEmpty()) intent.putExtra("location", location)

            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun bindCategory(layout: LinearLayout, category: String) {
        categoryLayouts.add(layout)
        layout.setOnClickListener {
            selectedCategory = category

            // 1. Reset all others to default (Grey BG, Black Text)
            for (l in categoryLayouts) {
                l.setBackgroundResource(R.drawable.filter_button_bg)
                findTextView(l).setTextColor(Color.BLACK)
                findImageView(l).setColorFilter(Color.BLACK)
            }

            // 2. Highlight selected (Orange BG, White Text)
            layout.setBackgroundResource(R.drawable.filter_button_selected_bg)
            findTextView(layout).setTextColor(Color.WHITE)
            findImageView(layout).setColorFilter(Color.WHITE)
        }
    }

    // Helper to find the text view inside the layout (assuming structure doesn't change)
    private fun findTextView(layout: LinearLayout): TextView {
        for (i in 0 until layout.childCount) {
            if (layout.getChildAt(i) is TextView) return layout.getChildAt(i) as TextView
        }
        throw IllegalStateException("No TextView in Category Layout")
    }

    private fun findImageView(layout: LinearLayout): ImageView {
        for (i in 0 until layout.childCount) {
            if (layout.getChildAt(i) is ImageView) return layout.getChildAt(i) as ImageView
        }
        throw IllegalStateException("No ImageView in Category Layout")
    }

    private fun updateConditionUI(selected: Button, unselected: Button) {
        selected.background.setTint(Color.parseColor("#FF913C"))
        selected.setTextColor(Color.WHITE)
        unselected.background.setTint(Color.WHITE)
        unselected.setTextColor(Color.BLACK)
    }
}