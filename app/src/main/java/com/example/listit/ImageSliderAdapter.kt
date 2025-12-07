package com.example.listit

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class ImageSliderAdapter(private val imagePaths: List<String>) : RecyclerView.Adapter<ImageSliderAdapter.SliderViewHolder>() {

    class SliderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.slider_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SliderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_slider_image, parent, false)
        return SliderViewHolder(view)
    }

    override fun onBindViewHolder(holder: SliderViewHolder, position: Int) {
        val path = imagePaths[position]

        if (path.startsWith("/")) {
            // Local File (Offline created)
            val imgFile = File(path)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                holder.imageView.setImageBitmap(bitmap)
            }
        } else {
            // Server URL
            val fullUrl = if (path.startsWith("http")) path else Constants.BASE_URL + path
            Glide.with(holder.itemView.context).load(fullUrl).into(holder.imageView)
        }
    }

    override fun getItemCount(): Int = imagePaths.size
}