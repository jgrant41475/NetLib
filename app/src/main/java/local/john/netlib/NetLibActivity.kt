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
import android.widget.*
import kotlinx.android.synthetic.main.activity_netlib.*
import kotlinx.android.synthetic.main.app_bar_net_lib.*
import kotlinx.android.synthetic.main.content_net_lib.*
import kotlinx.android.synthetic.main.netlib_movie_layout.view.*
import kotlinx.android.synthetic.main.netlib_show_layout.view.*
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
        var sharedPref: SharedPreferences?                             = null
        val library: MutableList<Pair<CATEGORY, List<NetLibCommon>>>   = mutableListOf()

        var IP_ADDRESS                                                 = "192.168.0.5"
        var PORT                                                       = "5051"
        var ROOT                                                       = "C:\\Media\\"
        var DEFAULT_CATEGORY                                           = CATEGORY.MOVIE
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
                        val cat = (nav_recycler.adapter as CategoryAdapter).categories[position]

                        updateContent(CATEGORY.from(cat.name))

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
                            CATEGORY.MOVIE -> { sendPlay((item as mMovie).file) }
                            CATEGORY.TV -> { sendPlay((item as mShow).file) }
                            else -> {  }
                        }
                    }

                }))

        // Load app settings and data
        reset()
    }

    private fun refreshCategories() = (nav_recycler.adapter as CategoryAdapter)
            .refreshCategories(library.map { (cat, _) -> mCategory(cat.name, cat.id) })


    private fun reset() {
        sharedPref      = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        IP_ADDRESS          = sharedPref?.getString("ip_address", IP_ADDRESS) ?: IP_ADDRESS
        PORT                = sharedPref?.getString("port", PORT) ?: PORT
        ROOT                = sharedPref?.getString("root", ROOT) ?: ROOT
        DEFAULT_CATEGORY    = CATEGORY.from(sharedPref!!.getString("default_cat", DEFAULT_CATEGORY.toString()))

        updateLibrary()
    }

    private fun updateLibrary() {
        async(UI) {
            val response: Deferred<List<Pair<CATEGORY, List<NetLibCommon>>>> = bg {
                Socket(IP_ADDRESS, PORT.toInt()).use {
                    PrintWriter(it.getOutputStream(), true).println("list")
                    val reader = it.getInputStream().bufferedReader()
                    var line = reader.readLine()

                    val temp = mutableListOf<Pair<CATEGORY, List<NetLibCommon>>>()

                    while (line != null) {
                        val (cat, list) = line.split("@")

                        temp.add(parseCategory(cat, list.split("|")))

                        line = reader.readLine()
                    }

                    temp.toList()
                }
            }

            val temp = response.await()

            library.clear()
            library.addAll(temp)

            refreshCategories()

            // Check that the list is not empty and that the default category is contained
            if(library.size > 0 && library.any{ (cat, _) -> cat == DEFAULT_CATEGORY })
                updateContent(DEFAULT_CATEGORY)
        }
    }

    private fun sendPlay(file: String) {
        async(UI) { bg { Socket(IP_ADDRESS, PORT.toInt()).use {
            PrintWriter(it.getOutputStream(), true).println("play file=${file.replace(" ", "`")}") } }.await()
        }
    }

    @Suppress("unchecked_cast")
    private fun updateContent(category: CATEGORY) {
        val adapter = content_recycler.adapter
        supportActionBar?.title = category.getName()

        when(category) {
            CATEGORY.MOVIE -> {
                (adapter as ContentAdapter<mMovie>).content = MovieContainer(library.firstOrNull {
                    (cat, _) -> cat.name == CATEGORY.MOVIE.toString() }?.second as MutableList<mMovie>)
            }
            CATEGORY.TV -> {
                (adapter as ContentAdapter<mShow>).content = ShowContainer(library.firstOrNull { (cat, _) ->
                    cat.name == CATEGORY.TV.toString() }?.second as MutableList<mShow>)
            }

            else -> {  }
        }

        adapter.notifyDataSetChanged()
    }

    private fun parseCategory(category: String, list: List<String>): Pair<CATEGORY, List<NetLibCommon>> {
        return when (category) {
            "Movies" -> {
                CATEGORY.MOVIE to list.map { mMovie(it.replace(ROOT, "").split("\\")[1], it, CATEGORY.MOVIE) }
            }
            "TV" -> {
                CATEGORY.TV to list.map {
                    val (series, season, episode) = it.replace(ROOT + "TV\\", "").split("\\")
                    mShow(episode.substring(0, episode.lastIndexOf(".")), season, series, it)
                }
            }
            else -> { CATEGORY.NONE to emptyList() }
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
        when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            R.id.action_refresh -> {
                reset()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    internal class CategoryAdapter(val categories: MutableList<mCategory>)
        : RecyclerView.Adapter<CategoryAdapter.Holder>() {

        internal fun refreshCategories(newList: List<mCategory>) {
            categories.clear()
            categories.addAll(newList)

            notifyDataSetChanged()
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val cat = categories[position]

            holder.name.text = CATEGORY.from(cat.type).getName()
            holder.type.text = cat.type.toString()
        }

        override fun getItemCount() = categories.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                Holder(LayoutInflater.from(parent.context).inflate(R.layout.nav_category_layout, parent, false))

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.find(R.id.nav_cat_name)
            val type: TextView = view.find(R.id.nav_cat_type)
        }
    }

    internal class ContentAdapter<T: NetLibCommon>(var content: BaseContainer<T>)
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

                else -> {  }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            when(CATEGORY.from(viewType)) {
                CATEGORY.MOVIE -> { return MovieHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.netlib_movie_layout, parent, false)) }

                CATEGORY.TV -> { return ShowHolder(LayoutInflater.from(parent.context)
                        .inflate(R.layout.netlib_show_layout, parent, false)) }

                else -> { return ViewHolder(LayoutInflater.from(parent.context)
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
    }
}
