package com.RowdyAvocado

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.Vidplay
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class CineZoneExtractor : ExtractorApi() {
    override val mainUrl = CineZone.mainUrl
    override val name = CineZone.name
    override val requiresReferer = false

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val serverName = referer
        if (url.isNotEmpty()) {
            try {
                when (serverName) {
                    "filemoon" -> FileMoon().getUrl(url, null, subtitleCallback, callback)
                    "vidplay" -> Vidplay2().getUrl(url, referer, subtitleCallback, callback)
                    else -> loadExtractor(url, subtitleCallback, callback)
                }
            } catch (e: Exception) {}
        }
    }
}

open class Vidplay2 : Vidplay() {
    override val name = "VidPlay"
    override val mainUrl = "https://vid41c.site"
    override val requiresReferer = true
}
