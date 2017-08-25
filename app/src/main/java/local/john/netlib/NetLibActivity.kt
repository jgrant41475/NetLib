package local.john.netlib

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_netlib.*
import kotlinx.android.synthetic.main.activity_netlib_appbar.*
import kotlinx.android.synthetic.main.activity_netlib_content.*
import kotlinx.android.synthetic.main.container_movie.view.*
import kotlinx.android.synthetic.main.container_show.view.*
import kotlinx.android.synthetic.main.container_song.view.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import local.john.netlib.Util.*
import org.jetbrains.anko.coroutines.experimental.bg
import org.jetbrains.anko.find
import java.io.PrintWriter
import java.net.Socket

class NetLibActivity : AppCompatActivity() {

    internal companion object {
        var sharedPref: SharedPreferences?                                  =   null
        val library: MutableList<Pair<CATEGORY, List<NetLibCommon>>>        =   mutableListOf()

        var IP_ADDRESS                                                      =   "192.168.0.5"
        var PORT                                                            =   "5051"
        var ROOT                                                            =   "C:\\Media"
        var DEFAULT_CATEGORY                                                =   CATEGORY.MOVIE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_netlib)
        setSupportActionBar(toolbar)

        // Setup UI and bind navigation drawer to an adapter
        val toggle = ActionBarDrawerToggle(this, drawer_layout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close)

        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_recycler.setHasFixedSize(true)
        nav_recycler.layoutManager = LinearLayoutManager(this)
        nav_recycler.adapter = CategoryAdapter(mutableListOf())
        nav_recycler.addOnItemTouchListener(RecyclerItemClickListener(this, nav_recycler,
                object: RecyclerItemClickListener.OnItemClickListener {
                    override fun onItemClick(view: View, position: Int) {
                        updateContent(CATEGORY.from((nav_recycler.adapter as CategoryAdapter).categories[position].name))
                        drawer_layout.closeDrawer(GravityCompat.START)
                    }

                    override fun onLongItemClick(view: View, position: Int) {  }
                }))

        content_recycler.setHasFixedSize(true)
        content_recycler.layoutManager = LinearLayoutManager(this)
        content_recycler.adapter = ContentAdapter(MovieContainer(mutableListOf()))
        content_recycler.addOnItemTouchListener(RecyclerItemClickListener(this, content_recycler,
                object: RecyclerItemClickListener.OnItemClickListener {
                    override fun onItemClick(view: View, position: Int) { }

                    override fun onLongItemClick(view: View, position: Int) {
                        @Suppress("unchecked_cast")
                        val adapter = content_recycler.adapter as ContentAdapter<NetLibCommon>
                        val item = adapter.content.content[position]

                        when(item.type) {
                            CATEGORY.MOVIE, CATEGORY.TV, CATEGORY.SONG -> { sendPlay(item.file) }
                            else -> {  }
                        }
                    }
                }))

        // Load app settings and data
        sharedPref              = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        if(sharedPref?.getString("ip_address", "NONE") == "NONE")
            firstRun(sharedPref!!)

        reset()
    }

    private fun reset() {
        IP_ADDRESS              = sharedPref?.getString("ip_address", IP_ADDRESS) ?: IP_ADDRESS
        PORT                    = sharedPref?.getString("port", PORT) ?: PORT
        ROOT                    = sharedPref?.getString("root", ROOT) ?: ROOT
        DEFAULT_CATEGORY        = CATEGORY.from(sharedPref!!.getString("default_cat", DEFAULT_CATEGORY.toString()))

        if(ROOT.last() != '\\')
            ROOT += "\\"

        updateLibrary()
    }

    private fun updateLibrary() {
        async(UI) {
            val response: Deferred<List<Pair<CATEGORY, List<NetLibCommon>>>> = bg {
                Socket(IP_ADDRESS, PORT.toInt()).use {
                    PrintWriter(it.getOutputStream(), true).println("root=\"${ROOT.replace(" ", "`")}\" list")
                    val reader = it.getInputStream().bufferedReader()
                    val temp = mutableListOf<Pair<CATEGORY, List<NetLibCommon>>>()
                    var line = reader.readLine()

                    while (line != null) {
                        val (cat, list) = line.split("@")

                        temp.add(parseCategory(cat, list.split("|")))
                        line = reader.readLine()
                    }

                    temp.toList()
                }
            }

            refreshCategories(response.await())

            // Library is empty, clear caches
            if(library.size < 1) {
                nav_recycler.adapter = CategoryAdapter(mutableListOf())
                content_recycler.adapter = ContentAdapter(MovieContainer(mutableListOf()))
            }

            // Check that the list is not empty and that the default category is contained
            else if(library.any{ (cat, _) -> cat == DEFAULT_CATEGORY })
                updateContent(DEFAULT_CATEGORY)
        }
    }

    private fun sendPlay(file: String) {
        async(UI) { bg { Socket(IP_ADDRESS, PORT.toInt()).use {
            PrintWriter(it.getOutputStream(), true).println("play file=${file.replace(" ", "`")}") } }.await()
        }
    }

    private fun refreshCategories(newCat: List<Pair<CATEGORY, List<NetLibCommon>>>) {
        library.clear()
        library.addAll(newCat)
        (nav_recycler.adapter as CategoryAdapter).refreshCategories(library.map { (cat, _) -> mCategory(cat.name, cat) })
    }

    private fun updateContent(category: CATEGORY) {
        val adapter = content_recycler.adapter
        supportActionBar?.title = category.getName()

        @Suppress("unchecked_cast")
        when(category) {
            CATEGORY.MOVIE -> {
                (adapter as ContentAdapter<mMovie>).content = MovieContainer(library.firstOrNull {
                    (cat, _) -> cat.name == CATEGORY.MOVIE.toString() }?.second as MutableList<mMovie>)
            }
            CATEGORY.TV -> {
                (adapter as ContentAdapter<mShow>).content = ShowContainer(library.firstOrNull { (cat, _) ->
                    cat.name == CATEGORY.TV.toString() }?.second as MutableList<mShow>)
            }
            CATEGORY.SONG -> {
                (adapter as ContentAdapter<mSong>).content = SongContainer(library.firstOrNull { (cat, _) ->
                    cat.name == CATEGORY.SONG.toString() }?.second as MutableList<mSong>)
            }

            else -> {  }
        }

        adapter.notifyDataSetChanged()
    }

    private fun parseCategory(category: String, list: List<String>): Pair<CATEGORY, List<NetLibCommon>> {
        return when (category) {
            "Movies" -> {
                CATEGORY.MOVIE to list.map { mMovie(it.replace(ROOT, "").split("\\")[1], it) }
            }
            "TV" -> {
                CATEGORY.TV to list.map {
                    val (series, season, episode) = it.replace(ROOT + "TV\\", "").split("\\")
                    mShow(episode.substring(0, episode.lastIndexOf(".")), season, series, it)
                }
            }
            "Songs" -> {
                CATEGORY.SONG to list.map {
                    val (artist, album, song) = it.replace(ROOT + "Songs\\", "").split("\\")
                    mSong(song.substring(0, song.lastIndexOf(".")), artist, album, it)
                }
            }
            else -> { CATEGORY.NONE to emptyList() }
        }
    }

    private fun firstRun(pref: SharedPreferences) {
        pref.edit()
                .putString("ip_address", IP_ADDRESS)
                .putString("port", PORT)
                .putString("root", ROOT)
                .putString("default_cat", DEFAULT_CATEGORY.toString())
                .apply()
    }

    private class CategoryAdapter(val categories: MutableList<mCategory>)
        : RecyclerView.Adapter<CategoryAdapter.Holder>() {

        internal fun refreshCategories(newList: List<mCategory>) {
            categories.clear()
            categories.addAll(newList)

            notifyDataSetChanged()
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val cat = categories[position].type

            holder.name.text = cat.getName()
            holder.type.text = cat.toString()
        }

        override fun getItemCount() = categories.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                Holder(LayoutInflater.from(parent.context).inflate(R.layout.container_nav_category, parent, false))

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.find(R.id.nav_cat_name)
            val type: TextView = view.find(R.id.nav_cat_type)
        }
    }

    private class ContentAdapter<T: NetLibCommon>(var content: BaseContainer<T>)
        : RecyclerView.Adapter<ContentAdapter.ViewHolder>() {
        override fun getItemViewType(position: Int) = content.content[position].type.id
        override fun getItemCount() = content.content.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            when(CATEGORY.from(holder.itemViewType)) {
                CATEGORY.MOVIE -> {
                    holder as MovieHolder
                    val movie = (content as MovieContainer).content[position]

                    holder.title.text = movie.title
                    holder.file.text = movie.file
                }

                CATEGORY.TV -> {
                    holder as ShowHolder
                    val show = (content as ShowContainer).content[position]

                    holder.series.text = show.series
                    holder.episode.text = show.episode
                    holder.file.text = show.file
                }

                CATEGORY.SONG -> {
                    holder as SongHolder
                    val show = (content as SongContainer).content[position]

                    holder.artist.text = show.artist
                    holder.album.text = show.album
                    holder.song.text = show.song
                    holder.file.text = show.file
                }

                else -> {  }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return when(CATEGORY.from(viewType)) {
                CATEGORY.MOVIE -> { MovieHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.container_movie, parent, false)) }

                CATEGORY.TV -> { ShowHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.container_show, parent, false)) }

                CATEGORY.SONG -> { SongHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.container_song, parent, false)) }

                else -> { ViewHolder(LayoutInflater.from(parent.context)
                        .inflate(android.R.layout.simple_list_item_1, parent, false)) }
            }
        }

        open class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

        class MovieHolder(view: View) : ViewHolder(view) {
            val title: TextView = view.netlib_movie_name
            val file: TextView = view.netlib_movie_file
        }

        class ShowHolder(view: View) : ViewHolder(view) {
            val series: TextView = view.netlib_show_series
            val episode: TextView = view.netlib_show_episode
            val file: TextView = view.netlib_show_file
        }

        class SongHolder(view: View) : ViewHolder(view) {
            val song: TextView = view.netlib_song_song
            val artist: TextView = view.netlib_song_artist
            val album: TextView = view.netlib_song_album
            val file: TextView = view.netlib_song_file
        }
    }

    override fun onBackPressed() =
            if (drawer_layout.isDrawerOpen(GravityCompat.START)) { drawer_layout.closeDrawer(GravityCompat.START) }
            else { super.onBackPressed() }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.net_lib, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                reset()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
