package com.gikoko.giplayerpro

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import java.util.Locale

class MainActivity : AppCompatActivity() {

    data class VideoItem(
        val uri: String,
        val name: String,
        val folder: String,
        val duration: Long,
        val size: Long,
        val modified: Long
    )

    private lateinit var rvVideos: RecyclerView
    private lateinit var rvFolders: RecyclerView
    private lateinit var rvStreams: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var tabVideos: TextView
    private lateinit var tabFolders: TextView
    private lateinit var tabStreams: TextView

    private val videos = mutableListOf<VideoItem>()
    private val folders = mutableListOf<Pair<String, List<VideoItem>>>()
    private val streams = mutableListOf<String>()
    private val prefs by lazy { getSharedPreferences("gi_player", MODE_PRIVATE) }
    private var currentTab = 0
    private var sortMode = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[videoPermission()] == true) {
            refreshLibrary()
        } else {
            tvEmpty.text = getString(R.string.no_permission)
            tvEmpty.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rvVideos = findViewById(R.id.rvVideos)
        rvFolders = findViewById(R.id.rvFolders)
        rvStreams = findViewById(R.id.rvStreams)
        tvEmpty = findViewById(R.id.tvEmpty)
        fab = findViewById(R.id.fabAddStream)
        tabVideos = findViewById(R.id.tabVideos)
        tabFolders = findViewById(R.id.tabFolders)
        tabStreams = findViewById(R.id.tabStreams)

        rvVideos.layoutManager = LinearLayoutManager(this)
        rvFolders.layoutManager = LinearLayoutManager(this)
        rvStreams.layoutManager = LinearLayoutManager(this)
        rvVideos.adapter = VideoAdapter()
        rvFolders.adapter = FolderAdapter()
        rvStreams.adapter = StreamAdapter()

        tabVideos.setOnClickListener { selectTab(0) }
        tabFolders.setOnClickListener { selectTab(1) }
        tabStreams.setOnClickListener { selectTab(2) }
        findViewById<TextView>(R.id.tvMenu).setOnClickListener { showMenu() }
        fab.setOnClickListener { showAddStreamDialog() }

        loadStreams()
        selectTab(0)
        requestPermissionsNow()
    }

    private fun videoPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun requestPermissionsNow() {
        val wanted = mutableListOf(videoPermission())
        if (Build.VERSION.SDK_INT >= 33) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val toAsk = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toAsk.isEmpty()) refreshLibrary()
        else permissionLauncher.launch(toAsk.toTypedArray())
    }

    private fun refreshLibrary() {
        loadVideos()
        rebuildFolders()
        rvVideos.adapter?.notifyDataSetChanged()
        rvFolders.adapter?.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun loadVideos() {
        videos.clear()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val modCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    ).toString()
                    videos.add(
                        VideoItem(
                            uri,
                            c.getString(nameCol) ?: "Unknown",
                            c.getString(bucketCol) ?: "Unknown",
                            c.getLong(durCol),
                            c.getLong(sizeCol),
                            c.getLong(modCol)
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        sortVideos()
    }

    private fun sortVideos() {
        when (sortMode) {
            1 -> videos.sortBy { it.name.lowercase(Locale.getDefault()) }
            2 -> videos.sortByDescending { it.size }
            else -> videos.sortByDescending { it.modified }
        }
    }

    private fun rebuildFolders() {
        folders.clear()
        videos.groupBy { it.folder }.forEach { (name, list) -> folders.add(name to list) }
        folders.sortBy { it.first.lowercase(Locale.getDefault()) }
    }

    private fun selectTab(index: Int) {
        currentTab = index
        val tabs = listOf(tabVideos, tabFolders, tabStreams)
        tabs.forEachIndexed { i, t ->
            if (i == index) {
                t.setBackgroundResource(R.drawable.bg_tab_active)
                t.setTextColor(ContextCompat.getColor(this, R.color.cyber_cyan))
            } else {
                t.background = null
                t.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
        rvVideos.visibility = if (index == 0) View.VISIBLE else View.GONE
        rvFolders.visibility = if (index == 1) View.VISIBLE else View.GONE
        rvStreams.visibility = if (index == 2) View.VISIBLE else View.GONE
        fab.visibility = if (index == 2) View.VISIBLE else View.GONE
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val empty = when (currentTab) {
            0 -> videos.isEmpty()
            1 -> folders.isEmpty()
            else -> streams.isEmpty()
        }
        tvEmpty.text = when (currentTab) {
            0 -> getString(R.string.no_videos)
            1 -> getString(R.string.no_folders)
            else -> getString(R.string.no_streams)
        }
        tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun showMenu() {
        val anchor = findViewById<TextView>(R.id.tvMenu)
        val popup = PopupMenu(this, anchor)
        popup.menu.add(getString(R.string.refresh))
        popup.menu.add(getString(R.string.sort))
        popup.menu.add(getString(R.string.about))
        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                getString(R.string.refresh) -> refreshLibrary()
                getString(R.string.sort) -> showSortDialog()
                getString(R.string.about) -> showAbout()
            }
            true
        }
        popup.show()
    }

    private fun showSortDialog() {
        val modes = arrayOf(
            "အချိန် အသစ်ဆုံး",
            "နာမည် အလိုက်",
            "အရွယ်အစား အကြီးဆုံး"
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sort))
            .setItems(modes) { _, which ->
                sortMode = which
                sortVideos()
                rebuildFolders()
                rvVideos.adapter?.notifyDataSetChanged()
                rvFolders.adapter?.notifyDataSetChanged()
            }
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("GI PLAYER PRO v1.0.0")
            .setMessage(
                "⚡ Modern Cyber Theme Video Player\n\n" +
                    "▶ MX-level features + PRO toolkit\n\n" +
                    "🏆 POWERED BY I KO KO"
            )
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showAddStreamDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_stream, null)
        val et = view.findViewById<EditText>(R.id.etUrl)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_stream))
            .setView(view)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val url = et.text.toString().trim()
                if (url.startsWith("http")) {
                    streams.add(url)
                    saveStreams()
                    rvStreams.adapter?.notifyDataSetChanged()
                    updateEmptyState()
                } else {
                    Toast.makeText(this, "URL မှားနေပါသည်", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadStreams() {
        streams.clear()
        try {
            val arr = JSONArray(prefs.getString("streams", "[]") ?: "[]")
            for (i in 0 until arr.length()) streams.add(arr.getString(i))
        } catch (_: Exception) {
        }
    }

    private fun saveStreams() {
        prefs.edit().putString("streams", JSONArray(streams).toString()).apply()
    }

    private fun openPlayer(
        uris: ArrayList<String>,
        titles: ArrayList<String>,
        index: Int,
        stream: Boolean
    ) {
        if (uris.isEmpty()) return
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putStringArrayListExtra(PlayerActivity.EXTRA_URIS, uris)
        intent.putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, titles)
        intent.putExtra(PlayerActivity.EXTRA_INDEX, index)
        intent.putExtra(PlayerActivity.EXTRA_IS_STREAM, stream)
        startActivity(intent)
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    // ---------------- Adapters ----------------

    inner class VideoAdapter : RecyclerView.Adapter<VideoAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val thumb: ImageView = v.findViewById(R.id.ivThumb)
            val title: TextView = v.findViewById(R.id.tvTitle)
            val sub: TextView = v.findViewById(R.id.tvSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

        override fun getItemCount(): Int = videos.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val v = videos[position]
            holder.title.text = v.name
            holder.sub.text = "${formatDuration(v.duration)}  •  ${v.folder}  •  " +
                Formatter.formatFileSize(this@MainActivity, v.size)
            Glide.with(holder.thumb).load(Uri.parse(v.uri)).centerCrop()
                .placeholder(R.drawable.bg_thumb).into(holder.thumb)
            holder.itemView.setOnClickListener {
                openPlayer(
                    ArrayList(videos.map { it.uri }),
                    ArrayList(videos.map { it.name }),
                    position, false
                )
            }
        }
    }

    inner class FolderAdapter : RecyclerView.Adapter<FolderAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvName)
            val count: TextView = v.findViewById(R.id.tvCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false))

        override fun getItemCount(): Int = folders.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = folders[position]
            holder.name.text = f.first
            holder.count.text = "${f.second.size} ဗီဒီယို"
            holder.itemView.setOnClickListener {
                openPlayer(
                    ArrayList(f.second.map { it.uri }),
                    ArrayList(f.second.map { it.name }),
                    0, false
                )
            }
        }
    }

    inner class StreamAdapter : RecyclerView.Adapter<StreamAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val url: TextView = v.findViewById(R.id.tvUrl)
            val del: TextView = v.findViewById(R.id.tvDel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_stream, parent, false))

        override fun getItemCount(): Int = streams.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = streams[position]
            holder.title.text = s.substringAfterLast('/')
            holder.url.text = s
            holder.itemView.setOnClickListener {
                openPlayer(arrayListOf(s), arrayListOf("Stream"), 0, true)
            }
            holder.del.setOnClickListener {
                val p = holder.bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) {
                    streams.removeAt(p)
                    saveStreams()
                    notifyItemRemoved(p)
                    updateEmptyState()
                }
            }
        }
    }
}
