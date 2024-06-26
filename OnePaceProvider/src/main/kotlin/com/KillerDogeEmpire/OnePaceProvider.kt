package com.KillerDogeEmpire

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class OnePaceProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://rentry.org/"
    override var name = "One Pace"

    override val hasMainPage = true

    override var lang = "en"
    override val hasDownloadSupport = true

    override val supportedTypes =
            setOf(
                    TvType.Anime,
            )

    override val mainPage =
            mainPageOf(
                    "/onepace/" to "OnePace",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val link = "$mainUrl${request.data}"
        val document = app.get(link).document
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        // val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val href = "https://rentry.org/onepace"
        var title = "One Pace"
        val posterUrl = "https://i.imgur.com/ESe4Smb.jpeg"

        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    // override suspend fun search(query: String): List<AnimeSearchResponse> {
    //     val document = app.get("$mainUrl/?s=$query").document
    //     //return document.select("div.post-filter-image").mapNotNull {
    //     return document.select("ul[data-results] li article").mapNotNull {
    //         it.toSearchResult()
    //     }
    // }

    override suspend fun load(url: String): LoadResponse? {

        val document = app.get(url).document

        var title = "One Pace"
        val poster = "https://i.imgur.com/ESe4Smb.jpeg"
        // val plot  = document?.selectFirst("div.entry-content p")?.text()?.trim() ?: "null"
        // val year  = document?.selectFirst("span.year")?.text()?.trim()?.toInt() ?: 1990

        var episodes = mutableListOf<Episode>()

        var arcName = mutableListOf<String>()

        document.select("h3").mapNotNull { arcName.add(it?.text() ?: "null") }

        var c = 0
        document.select("table tbody").mapNotNull {
            it?.select("tr td a")?.mapNotNull {
                val name = arcName.get(c) + " " + it?.text() ?: "null"

                val tempstring = it?.attr("href") ?: "null"

                episodes.add(Episode(tempstring, name))
            }

            c = c + 1
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster.toString()
            // this.plot = plot
            // this.year = year
            // this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {

        loadExtractor(data, subtitleCallback, callback)
        return true
    }
}
