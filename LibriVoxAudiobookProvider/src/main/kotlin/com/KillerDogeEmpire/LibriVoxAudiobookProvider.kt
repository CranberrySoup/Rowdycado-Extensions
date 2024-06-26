package com.KillerDogeEmpire

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

// .\gradlew.bat PelisPlus4KProvider:deployWithAdb
// adb logcat | find "TAG"
// com.lagradost.cloudstream3.prerelease

class LibriVoxAudiobookProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://librivox.org"
    override var name = "Librivox Audiobook"

    // override val hasMainPage = true
    override val hasMainPage = true

    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Others)

    // val reqlink = "https://librivox.org/api/feed/audiobooks/title/%5EAr?format=json"
    // val jason = app.get(reqlink)?.parsed<BookList>()
    // val log = jason?.books

    // override val mainPage = mainPageOf(
    //    "/search?primary_key=0&search_category=title&search_page=" to "Latest Audiobooks",
    //    )

    override val mainPage =
            listOf(
                    MainPageData(
                            "Latest Audiobook",
                            "https://librivox.org/api/feed/audiobooks/title/?format=json"
                    )
            )

    // taken from AllAnime lag, I have no idea wtf I am doing

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val reqlink = request.data

        val home =
                when (request.name) {
                    "Latest Audiobook" -> {
                        val jason = app.get(reqlink).parsed<BookList>()

                        jason.books.mapNotNull {
                            // No null saftey goddamit
                            newAnimeSearchResponse(it.title!!, it.url!!, TvType.Anime) {
                                // this.posterUrl = it.thumbnail
                            }
                        }
                    }
                    else -> emptyList()
                }

        return HomePageResponse(
                listOf(HomePageList(request.name, home)),
                hasNext = home.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val reqlink = "https://librivox.org/api/feed/audiobooks/title/%5E$query?format=json"

        val jason = app.get(reqlink).parsed<BookList>()

        return jason.books.map {
            // No null saftey goddamit
            newAnimeSearchResponse(it.title!!, it.url!!, TvType.Anime) {
                // this.posterUrl = it.thumbnail
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {

        val document = app.get(url).document

        val title = document.selectFirst("div.content-wrap h1")?.text()?.trim() ?: return null

        val poster =
                document.selectFirst("div.book-page-book-cover img")?.attr("src")
                        ?: "https://librivox.org/images/librivox-logo.png"

        val tvType = TvType.TvSeries

        return if (tvType == TvType.TvSeries) {

            val episodes =
                    document.select("a.chapter-name").mapNotNull {
                        val href = fixUrl(it?.attr("href") ?: return null)
                        val name = it?.text()?.trim() ?: return null

                        Episode(
                                href,
                                name,
                        )
                    }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster.toString()
                // this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster.toString()
                // this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {

        val name = "Librivox Audiobook"

        callback.invoke(
                ExtractorLink(
                        source = name,
                        name = "Librivox Audiobook",
                        url = data,
                        referer = "$mainUrl/",
                        quality = Qualities.P360.value
                )
        )

        return true
    }

    data class BookList(@JsonProperty("books") val books: ArrayList<Book> = arrayListOf())

    data class Book(
            @JsonProperty("id") val id: String? = null, // 119
            @JsonProperty("title") val title: String? = null, // Art of War
            // @JsonProperty("description") val description:  String? = null,//Bla bla bla
            @JsonProperty("url_librivox") val url: String? = null, // main url of book
    )
}
