package com.example.listit

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import java.io.File

class MyAdsAdapter(
    private var adList: ArrayList<Ad>,
    private val isFavTab: Boolean,
    private val onActionClick: (Ad) -> Unit,
    private val onEditClick: (Ad) -> Unit,
    private val onItemClick: (Ad) -> Unit // New callback for image click
) : RecyclerView.Adapter<MyAdsAdapter.MyAdViewHolder>() {

    class MyAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.adImage)
        val title: TextView = itemView.findViewById(R.id.adTitle)
        val price: TextView = itemView.findViewById(R.id.adPrice)
        val location: TextView = itemView.findViewById(R.id.adLocation)
        val status: TextView = itemView.findViewById(R.id.adStatus)
        val btnEdit: MaterialButton = itemView.findViewById(R.id.btn_edit)
        val btnAction: MaterialButton = itemView.findViewById(R.id.btn_action)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyAdViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_ad, parent, false)
        return MyAdViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyAdViewHolder, position: Int) {
        val ad = adList[position]

        holder.title.text = ad.title
        holder.price.text = "Rs ${ad.price}"
        holder.location.text = ad.location

        // Image Loading
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

        // --- IMAGE CLICK LISTENER ---
        holder.image.setOnClickListener {
            onItemClick(ad)
        }

        // Tab & Status Logic
        if (isFavTab) {
            holder.status.visibility = View.GONE
            holder.btnEdit.visibility = View.GONE
            holder.btnAction.text = "Remove"
            holder.btnAction.setBackgroundColor(Color.parseColor("#FF0000"))
        } else {
            holder.btnEdit.visibility = View.VISIBLE
            holder.status.visibility = View.VISIBLE

            if (ad.isDeleted == 1) {
                holder.status.text = "Removed"
                holder.status.setTextColor(Color.RED)
                holder.btnAction.text = "Republish"
                holder.btnAction.setBackgroundColor(Color.parseColor("#4CAF50")) // Green
            } else {
                holder.status.text = "Active"
                holder.status.setTextColor(Color.parseColor("#4CAF50"))
                holder.btnAction.text = "Remove"
                holder.btnAction.setBackgroundColor(Color.parseColor("#FF6F00")) // Orange
            }
        }

        holder.btnAction.setOnClickListener { onActionClick(ad) }
        holder.btnEdit.setOnClickListener { onEditClick(ad) }
    }

    override fun getItemCount(): Int = adList.size

    fun updateList(newList: ArrayList<Ad>) {
        adList = newList
        notifyDataSetChanged()
    }
}