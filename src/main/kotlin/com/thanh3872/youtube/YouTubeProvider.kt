package com.thanh3872.youtube

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.*
import org.jsoup.nodes.Element
import java.util.Calendar

class YouTubeProvider : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Others)
    override var lang = "en"

    override val mainPage = mainPageOf(
        "FEATURED" to "Featured Videos",
        "TRENDING" to "Trending",
        "MUSIC" to "Music",
        "GAMING" to "Gaming"
    )

    // ========== SEARCH ==========
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/results?search_query=${query.replace(" ", "+")}"
        val document = app.get(searchUrl).document
        
        return document.select("ytd-video-renderer").mapNotNull { element ->
            val titleElement = element.selectFirst("#video-title")
            val title = titleElement?.text() ?: return@mapNotNull null
            val href = titleElement.attr("href")
            val thumbnail = element.selectFirst("img")?.attr("src") ?: ""
            
            MovieSearchResponse(
                title,
                fixUrl(href),
                this.name,
                TvType.Movie,
                thumbnail,
                null
            )
        }.distinctBy { it.url }
    }

    // ========== HOME PAGE ==========
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = when (request.data) {
            "FEATURED" -> mainUrl
            "TRENDING" -> "$mainUrl/feed/trending"
            "MUSIC" -> "$mainUrl/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ"
            "GAMING" -> "$mainUrl/gaming"
            else -> mainUrl
        }
        
        val document = app.get(url).document
        val items = document.select("ytd-rich-item-renderer, ytd-video-renderer")
            .take(20)
            .mapNotNull { element ->
                val titleElement = element.selectFirst("#video-title")
                val title = titleElement?.text() ?: return@mapNotNull null
                val href = titleElement.attr("href")
                val thumbnail = element.selectFirst("img")?.attr("src") ?: ""
                
                MovieSearchResponse(
                    title,
                    fixUrl(href),
                    this.name,
                    TvType.Movie,
                    thumbnail,
                    null
                )
            }
        
        return newHomePageResponse(request.name, items)
    }

    // ========== LOAD VIDEO PAGE ==========
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[name=title]")?.attr("content") ?: "YouTube Video"
        val description = document.selectFirst("meta[name=description]")?.attr("content") ?: ""
        val thumbnail = document.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        
        // Extract video ID
        val videoId = if "v=" in url:
            url.split("v=")[1].split("&")[0]
        else:
            url.split("/")[-1]
        
        val videoLinks = listOf(
            ExtractorLink(
                name,
                name,
                "https://www.youtube.com/watch?v=$videoId",
                "",
                Qualities.Unknown.value,
                false
            )
        )
        
        return newMovieLoadResponse(title, url, TvType.Movie, videoLinks) {
            this.posterUrl = thumbnail
            this.plot = description
            this.year = Calendar.getInstance().get(Calendar.YEAR)
        }
    }

    // ========== LOAD VIDEO LINKS ==========
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback(
            ExtractorLink(
                name,
                name,
                data,
                referer = mainUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = false
            )
        )
        return true
    }
}
