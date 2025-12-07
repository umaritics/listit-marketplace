package com.example.listit

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class AdAdapter(
    private var adList: ArrayList<Ad>,
    private val onFavClick: ((Ad, Int) -> Unit)? = null // Made nullable for versatility
) : RecyclerView.Adapter<AdAdapter.AdViewHolder>() {

    class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.post_image)
        val title: TextView = itemView.findViewById(R.id.post_title)
        val price: TextView = itemView.findViewById(R.id.post_price)
        val location: TextView = itemView.findViewById(R.id.post_location)
        val time: TextView = itemView.findViewById(R.id.time)
        val favIcon: ImageView = itemView.findViewById(R.id.fav_icon)
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
        holder.time.text = ad.date.take(10)

        // Image Logic
        val path = ad.imagePath ?: ""
        if (path.startsWith("/")) {
            val imgFile = File(path)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                holder.image.setImageBitmap(bitmap)
            }
        } else if (path.isNotEmpty()) {
            val fullUrl = if (path.startsWith("http")) path else Constants.BASE_URL + path
            Glide.with(holder.itemView.context).load(fullUrl).placeholder(R.drawable.sample_image).into(holder.image)
        } else {
            holder.image.setImageResource(R.drawable.sample_image)
        }

        // --- HEART ICON LOGIC (Updated) ---
        // 1. Keep the same icon drawable
        holder.favIcon.setImageResource(R.drawable.heart_home)

        if (ad.isSaved) {
            // SAVED: Orange Background, White Icon
            holder.favIcon.background.setTint(Color.parseColor("#FF6F00")) // Orange
            holder.favIcon.setColorFilter(Color.WHITE)
        } else {
            // NOT SAVED: White Background, Orange Icon
            holder.favIcon.background.setTint(Color.WHITE)
            holder.favIcon.setColorFilter(Color.parseColor("#FF6F00")) // Orange
        }

        // Click Listener
        holder.favIcon.setOnClickListener {
            onFavClick?.invoke(ad, position)
        }

        // Open Detail View
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, ViewAd::class.java)
            intent.putExtra("AD_ID", ad.id)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = adList.size

    fun updateList(newList: ArrayList<Ad>) {
        adList = newList
        notifyDataSetChanged()
    }
}