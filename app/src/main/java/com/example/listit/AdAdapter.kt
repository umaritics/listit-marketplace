package com.example.listit

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class AdAdapter(private val adList: ArrayList<Ad>) : RecyclerView.Adapter<AdAdapter.AdViewHolder>() {

    class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.post_image)
        val title: TextView = itemView.findViewById(R.id.post_title)
        val price: TextView = itemView.findViewById(R.id.post_price)
        val location: TextView = itemView.findViewById(R.id.post_location)
        val time: TextView = itemView.findViewById(R.id.time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ad, parent, false)
        return AdViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdViewHolder, position: Int) {
        val ad = adList[position]

        holder.title.text = ad.title
        holder.price.text = "Rs ${ad.price}"
        holder.location.text = ad.location
        holder.time.text = ad.date.take(10) // Show only date part

        // --- SMART IMAGE LOADING ---
        val path = ad.imagePath ?: ""

        if (path.startsWith("/")) {
            // It's a Local File (Offline created)
            val imgFile = File(path)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                holder.image.setImageBitmap(bitmap)
            }
        } else if (path.isNotEmpty()) {
            // It's a Server URL (Synced)
            // If path is "uploads/...", append Base URL
            val fullUrl = if (path.startsWith("http")) path else Constants.BASE_URL + path

            // Use Glide for efficient networking
            Glide.with(holder.itemView.context)
                .load(fullUrl)
                .placeholder(R.drawable.sample_image)
                .into(holder.image)
        } else {
            holder.image.setImageResource(R.drawable.sample_image)
        }
    }

    override fun getItemCount(): Int = adList.size
}