package com.example.listit

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import java.io.File

class OtherAdsAdapter(
    private val adList: ArrayList<Ad>,
    private val onViewClick: (Ad) -> Unit
) : RecyclerView.Adapter<OtherAdsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.adImage)
        val title: TextView = itemView.findViewById(R.id.adTitle)
        val price: TextView = itemView.findViewById(R.id.adPrice)
        val location: TextView = itemView.findViewById(R.id.adLocation)
        val btnView: MaterialButton = itemView.findViewById(R.id.btnView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_other_ad, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ad = adList[position]

        holder.title.text = ad.title
        holder.price.text = "Rs ${ad.price}"
        holder.location.text = ad.location

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
        }

        // Click Listeners
        holder.btnView.setOnClickListener { onViewClick(ad) }
        holder.itemView.setOnClickListener { onViewClick(ad) }
    }

    override fun getItemCount(): Int = adList.size
}