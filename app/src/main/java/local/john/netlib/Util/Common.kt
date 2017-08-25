package local.john.netlib.Util

// Base class that all child data models inherit from
internal abstract class NetLibCommon {
    abstract val type: CATEGORY
    open val file: String = ""
}

// Data models for recyclerviews
internal data class mCategory(val name: String, override val type: CATEGORY = CATEGORY.NONE) : NetLibCommon()
internal data class mMovie(val title: String, override val file: String, override val type: CATEGORY = CATEGORY.MOVIE) : NetLibCommon()
internal data class mShow(val episode: String, val season: String, val series: String,
                          override val file: String, override val type: CATEGORY = CATEGORY.TV) : NetLibCommon()
internal data class mSong(val song: String, val artist: String, val album: String,
                          override val file:String, override val type: CATEGORY = CATEGORY.SONG) : NetLibCommon()

// Data containers to exchange categories between main recyclerview
internal open class BaseContainer<T: NetLibCommon>(open val content: MutableList<T>)
internal data class MovieContainer(override val content: MutableList<mMovie>) : BaseContainer<mMovie>(content)
internal data class ShowContainer(override val content: MutableList<mShow>) : BaseContainer<mShow>(content)
internal data class SongContainer(override val content: MutableList<mSong>) : BaseContainer<mSong>(content)

// Category struct
internal enum class CATEGORY(val id: Int) { NONE(0), MOVIE(1), TV(2), SONG(3);
    fun getName() = when(id) {
        0 -> "None"
        1 -> "Movies"
        2 -> "TV Shows"
        3 -> "Songs"
        else -> super.toString()
    }

    companion object {
        fun from(find: Int): CATEGORY = CATEGORY.values().firstOrNull { it.id == find } ?: NONE
        fun from(find: String): CATEGORY = CATEGORY.values().firstOrNull { it.name == find } ?: NONE
    }
}