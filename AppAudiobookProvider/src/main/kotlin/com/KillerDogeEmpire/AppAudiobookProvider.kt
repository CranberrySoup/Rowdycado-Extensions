package com.KillerDogeEmpire

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

// .\gradlew.bat PelisPlus4KProvider:deployWithAdb
// adb logcat | find "TAG"
// com.lagradost.cloudstream3.prerelease

class AppAudiobookProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://appaudiobooks.com/"
    override var name = "App Audiobook"

    // override val hasMainPage = true
    override val hasMainPage = true

    override var lang = "en"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(TvType.Others)

    override val mainPage = mainPageOf("/" to "Latest")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl${request.data}/page/$page/").document
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrl(this.selectFirst("h2 a")?.attr("href") ?: return null)
        // val href = "https://www.google.com"
        val title = this.selectFirst("h2 a")?.text() ?: return null
        val posterUrl =
                fixUrlNull(
                        this.selectFirst("div.entry.clearfix div img[data-lazy-src]")
                                ?.attr("data-lazy-src")
                )
        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    private fun Element.toManualSearchResult(): AnimeSearchResponse? {
        val href = fixUrl(this.selectFirst("h2 a")?.attr("href") ?: return null)
        // val href = "https://www.google.com"
        val title = this.selectFirst("h2 a")?.text() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.entry.clearfix div img")?.attr("src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        // return document.select("div.post-filter-image").mapNotNull {
        return document.select("article").mapNotNull { it.toManualSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {

        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null

        val poster =
                document.selectFirst("div[style] img[data-lazy-src]")?.attr("data-lazy-src")
                        ?: "https://librivox.org/images/librivox-logo.png"

        val tvType = TvType.TvSeries

        return if (tvType == TvType.TvSeries) {

            var c = 1

            var episodes = mutableListOf<Episode>()

            document.select("audio a").mapNotNull {
                val href = fixUrl(it?.attr("href") ?: return null)
                val name = c.toString()

                c = c + 1
                episodes.add(Episode(href, name))
            }

            document.select("div.page-links a").mapNotNull {
                val newdoc = app.get(it.attr("href")!!).document

                newdoc.select("audio a").mapNotNull {
                    val href = fixUrl(it?.attr("href") ?: return null)
                    val name = c.toString()

                    c = c + 1
                    episodes.add(Episode(href, name))
                }
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

        val name = "App Audiobook"

        callback.invoke(
                ExtractorLink(
                        source = name,
                        name = "App Audiobook",
                        url = data,
                        referer = "$mainUrl/",
                        quality = Qualities.P360.value
                )
        )

        return true
    }
}
