package com.aditya.music.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aditya.music.data.Song
import com.aditya.music.data.formatDuration
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView

sealed interface LibraryEntry {
    data class Track(val song: Song) : LibraryEntry
    data class Collection(
        val title: String,
        val subtitle: String,
        val songIds: Set<Long>,
        val artworkUri: android.net.Uri? = null,
        val canDelete: Boolean = false
    ) : LibraryEntry
}

class LibraryAdapter(
    private val onTrackClick: (Song) -> Unit,
    private val onCollectionClick: (LibraryEntry.Collection) -> Unit,
    private val onMoreClick: (Song, View) -> Unit,
    private val onCollectionMoreClick: (LibraryEntry.Collection, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var entries: List<LibraryEntry> = emptyList()

    fun submit(newEntries: List<LibraryEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (entries[position] is LibraryEntry.Track) TYPE_TRACK else TYPE_COLLECTION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        val card = MaterialCardView(context).apply {
            radius = 18f
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(Color.rgb(29, 31, 39))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = context.resources.displayMetrics.density.times(8).toInt()
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val inset = context.resources.displayMetrics.density.times(10).toInt()
            setPadding(inset, inset, inset, inset)
            minimumHeight = context.resources.displayMetrics.density.times(76).toInt()
        }
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(50, 53, 65))
            layoutParams = LinearLayout.LayoutParams(
                context.resources.displayMetrics.density.times(54).toInt(),
                context.resources.displayMetrics.density.times(54).toInt()
            )
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = context.resources.displayMetrics.density.times(12).toInt()
                marginEnd = context.resources.displayMetrics.density.times(6).toInt()
            }
        }
        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val subtitle = TextView(context).apply {
            setTextColor(Color.rgb(177, 181, 194))
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textColumn.addView(title)
        textColumn.addView(subtitle)
        val more = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_more)
            setColorFilter(Color.rgb(205, 210, 225))
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "More options"
            layoutParams = LinearLayout.LayoutParams(
                context.resources.displayMetrics.density.times(42).toInt(),
                context.resources.displayMetrics.density.times(48).toInt()
            )
        }
        row.addView(image)
        row.addView(textColumn)
        row.addView(more)
        card.addView(row)
        return RowHolder(card, image, title, subtitle, more)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = holder as RowHolder
        when (val entry = entries[position]) {
            is LibraryEntry.Track -> {
                val song = entry.song
                row.title.text = song.title.ifBlank { "Unknown title" }
                row.subtitle.text = "${song.artist.ifBlank { "Unknown artist" }}  •  ${formatDuration(song.durationMs)}"
                row.more.visibility = View.VISIBLE
                row.more.setOnClickListener { onMoreClick(song, it) }
                row.itemView.setOnClickListener { onTrackClick(song) }
                loadArtwork(row.image, song.artworkUri)
            }
            is LibraryEntry.Collection -> {
                row.title.text = entry.title
                row.subtitle.text = entry.subtitle
                row.more.visibility = if (entry.canDelete) View.VISIBLE else View.GONE
                row.more.setOnClickListener { onCollectionMoreClick(entry, it) }
                row.itemView.setOnClickListener { onCollectionClick(entry) }
                loadArtwork(row.image, entry.artworkUri)
            }
        }
    }

    override fun getItemCount(): Int = entries.size

    private fun loadArtwork(image: ImageView, uri: android.net.Uri?) {
        Glide.with(image)
            .load(uri)
            .placeholder(android.R.drawable.ic_media_play)
            .error(android.R.drawable.ic_media_play)
            .centerCrop()
            .into(image)
    }

    private class RowHolder(
        root: View,
        val image: ImageView,
        val title: TextView,
        val subtitle: TextView,
        val more: ImageButton
    ) : RecyclerView.ViewHolder(root)

    companion object {
        private const val TYPE_TRACK = 1
        private const val TYPE_COLLECTION = 2
    }
}
