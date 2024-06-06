package com.KillerDogeEmpire

import android.util.Log
import com.KillerDogeEmpire.UltimaMediaProvidersUtils.invokeExtractors
import com.KillerDogeEmpire.UltimaUtils.Category
import com.KillerDogeEmpire.UltimaUtils.LinkData
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.CoverImage
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.LikePageInfo
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Media
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.RecommendationConnection
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.SeasonNextAiringEpisode
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Title
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class AniList(val plugin: UltimaPlugin) : MainAPI() {
    override var name = "AniList"
    override var mainUrl = "https://anilist.co"
    override var supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Anilist)
    override val hasMainPage = true
    override val hasQuickSearch = false
    private val api = AccountManager.aniListApi
    private val apiUrl = "https://graphql.anilist.co"
    private val headerJSON =
            mapOf("Accept" to "application/json", "Content-Type" to "application/json")

    protected fun Any.toStringData(): String {
        return mapper.writeValueAsString(this)
    }

    private suspend fun anilistAPICall(query: String): AnilistAPIResponse {
        val data = mapOf("query" to query)
        Log.d("rowdy", query)
        val res =
                app.post(apiUrl, headers = headerJSON, data = data).parsedSafe<AnilistAPIResponse>()
                        ?: throw Exception("Unable to fetch or parse Anilist api response")
        Log.d("rowdy", res.toString())
        return res
    }

    private fun AniListApi.Media.toSearchResponse(): SearchResponse {
        val title = this.title.english ?: this.title.romaji ?: ""
        val url = "$mainUrl/anime/${this.id}"
        val posterUrl = this.coverImage.large
        return newAnimeSearchResponse(title, url, TvType.Anime) { this.posterUrl = posterUrl }
    }

    private fun test(test: AniListApi.SeasonMedia): SearchResponse {
        val title = test.title?.english ?: test.title?.romaji ?: ""
        val url = "$mainUrl/anime/${test.id}"
        val posterUrl = test.coverImage?.large
        return newAnimeSearchResponse(title, url, TvType.Anime) { this.posterUrl = posterUrl }
    }

    private suspend fun MainPageRequest.toSearchResponseList(
            page: Int
    ): Pair<List<SearchResponse>, Boolean> {
        val res = anilistAPICall(this.data.replace("###", "$page"))
        val data =
                res.data.page?.media?.map { it.toSearchResponse() }
                        ?: throw Exception("Unable to read media data")
        val hasNextPage = res.data.page.pageInfo.hasNextPage ?: false
        return data to hasNextPage
    }

    override val mainPage =
            mainPageOf(
                    "query (\$page: Int = ###, \$sort: [MediaSort] = [TRENDING_DESC, POPULARITY_DESC], \$isAdult: Boolean = false) { Page(page: \$page, perPage: 20) { pageInfo { total perPage currentPage lastPage hasNextPage } media(sort: \$sort, isAdult: \$isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }" to
                            "Trending Now",
                    "query (\$page: Int = ###, \$seasonYear: Int = 2024, \$sort: [MediaSort] = [TRENDING_DESC, POPULARITY_DESC], \$isAdult: Boolean = false) { Page(page: \$page, perPage: 20) { pageInfo { total perPage currentPage lastPage hasNextPage } media(sort: \$sort, seasonYear: \$seasonYear, season: SPRING, isAdult: \$isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }" to
                            "Popular This Season",
                    "query (\$page: Int = ###, \$sort: [MediaSort] = [POPULARITY_DESC], \$isAdult: Boolean = false) { Page(page: \$page, perPage: 20) { pageInfo { total perPage currentPage lastPage hasNextPage } media(sort: \$sort, isAdult: \$isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }" to
                            "All Time Popular",
                    "query (\$page: Int = ###, \$sort: [MediaSort] = [SCORE_DESC], \$isAdult: Boolean = false) { Page(page: \$page, perPage: 20) { pageInfo { total perPage currentPage lastPage hasNextPage } media(sort: \$sort, isAdult: \$isAdult, type: ANIME) { id idMal season seasonYear format episodes chapters title { english romaji } coverImage { extraLarge large medium } synonyms nextAiringEpisode { timeUntilAiring episode } } } }" to
                            "Top 100 Anime",
                    "Personal" to "Personal"
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        if (request.name.equals("Personal")) {
            // Reading and manipulating personal library
            api.loginInfo()
                    ?: return newHomePageResponse(
                            "Login required for personal content.",
                            emptyList<SearchResponse>(),
                            false
                    )
            var homePageList =
                    api.getPersonalLibrary().allLibraryLists.mapNotNull {
                        if (it.items.isEmpty()) return@mapNotNull null
                        val libraryName =
                                it.name.asString(plugin.activity ?: return@mapNotNull null)
                        HomePageList("${request.name}: $libraryName", it.items)
                    }
            return newHomePageResponse(homePageList, false)
        } else {
            // Other new sections will be generated if toSearchResponseList() is
            // overridden
            val data = request.toSearchResponseList(page)
            return newHomePageResponse(request.name, data.first, data.second)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.removeSuffix("/").substringAfterLast("/")
        val data =
                anilistAPICall(
                                "query (\$id: Int = $id) {  Media (id: \$id, type: ANIME) { id title { romaji english } seasonYear genres description averageScore bannerImage coverImage { extraLarge large medium } bannerImage episodes nextAiringEpisode { episode } recommendations { edges { node { id mediaRecommendation { id title { romaji english } coverImage { extraLarge large medium } } } } } } }"
                        )
                        .data
                        .media
                        ?: throw Exception("Unable to fetch media details")
        val episodes =
                (1..data.totalEpisodes()).map { i ->
                    val linkData =
                            LinkData(
                                            title = data.getTitle(),
                                            year = data.seasonYear,
                                            season = 1,
                                            episode = i,
                                            isAnime = true
                                    )
                                    .toStringData()
                    Episode(linkData, season = 1, episode = i)
                }
        return newAnimeLoadResponse(data.getTitle(), url, TvType.Anime) {
            addAniListId(id.toInt())
            addEpisodes(DubStatus.Subbed, episodes)
            this.year = data.seasonYear
            this.plot = data.description
            this.backgroundPosterUrl = data.bannerImage
            this.posterUrl = data.getCoverImage()
            this.tags = data.genres
            this.recommendations =
                    data.recommendations?.edges?.map {
                        val recommendation = it.node.mediaRecommendation
                        val title =
                                recommendation.title?.english
                                        ?: recommendation.title?.romaji
                                                ?: throw Exception(
                                                "Unable to load name of recommendation"
                                        )
                        val recommendationUrl = "$mainUrl/anime/${recommendation.id}"
                        newAnimeSearchResponse(title, recommendationUrl, TvType.Anime) {
                            this.posterUrl = recommendation.coverImage?.large
                        }
                    }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaData = AppUtils.parseJson<LinkData>(data)
        invokeExtractors(Category.ANIME, mediaData, subtitleCallback, callback)
        return true
    }

    data class AnilistAPIResponse(
            @JsonProperty("data") val data: AnilistData,
    )

    data class AnilistData(
            @JsonProperty("Page") val page: AnilistPage?,
            @JsonProperty("Media") val media: anilistMedia?,
    )

    data class AnilistPage(
            @JsonProperty("pageInfo") val pageInfo: LikePageInfo,
            @JsonProperty("media") val media: List<Media>,
    )

    data class anilistMedia(
            @JsonProperty("id") val id: Int,
            @JsonProperty("seasonYear") val seasonYear: Int,
            @JsonProperty("episodes") val episodes: Int?,
            @JsonProperty("title") val title: Title,
            @JsonProperty("genres") val genres: List<String>,
            @JsonProperty("description") val description: String?,
            @JsonProperty("coverImage") val coverImage: CoverImage,
            @JsonProperty("bannerImage") val bannerImage: String,
            @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
            @JsonProperty("recommendations") val recommendations: RecommendationConnection?,
    ) {
        fun totalEpisodes(): Int {
            return nextAiringEpisode?.episode?.minus(1)
                    ?: episodes ?: throw Exception("Unable to calculate total episodes")
        }

        fun getTitle(): String {
            return title.english
                    ?: title.romaji ?: throw Exception("Unable to calculate total episodes")
        }

        fun getCoverImage(): String? {
            return coverImage.extraLarge ?: coverImage.large ?: coverImage.medium
        }
    }
}