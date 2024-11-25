package snd.komf.providers.bangumi

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import snd.komf.model.Image
import snd.komf.providers.bangumi.model.BangumiSubject
import snd.komf.providers.bangumi.model.SearchSubjectsResponse
import snd.komf.providers.bangumi.model.SubjectRelation
import snd.komf.providers.bangumi.model.SubjectSearchData
import snd.komf.providers.bangumi.model.SubjectType
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min

class BangumiClient(
    private val ktor: HttpClient,
) {
    private val apiV0Url = "https://api.bgm.tv/v0"

    suspend fun searchSeries(
        keyword: String,
        rating: Collection<String> = listOf(">0.0"), // Use min rating to improve result quality
        rank: Collection<String> = listOf(">=0"), // Use ranked items to improve result quality
    ): SearchSubjectsResponse {
        val rep1 =  searchSeries1(keyword,rating,rank)
        val rep1Items = rep1.data.toMutableList()
        val rep1Ids = rep1Items.map { it.id }.toSet()
        searchSeries2(keyword).data.forEachIndexed { index, subjectSearchData ->
            if(!rep1Ids.contains(subjectSearchData.id)) {
                if(index<5) rep1Items.add(min(index,rep1Items.size),subjectSearchData)
                else rep1Items.add(subjectSearchData)
            }
        }
        return rep1.copy(total = rep1Items.size,limit = rep1Items.size, data=rep1Items)
    }

    suspend fun searchSeries1(
        keyword: String,
        rating: Collection<String> = listOf(">0.0"), // Use min rating to improve result quality
        rank: Collection<String> = listOf(">=0"), // Use ranked items to improve result quality
    ): SearchSubjectsResponse {
        return ktor.post("$apiV0Url/search/subjects?limit=15") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("keyword", keyword)
                    put("filter", buildJsonObject {
                        putJsonArray("type") { add(SubjectType.BOOK.value) }
//                        putJsonArray("rating") { rating.forEach { add(it) } }
//                        putJsonArray("rank") { rank.forEach { add(it) } }
                        put("nsfw", true) // include NSFW content
                    })
                }
            )

        }.body()
    }

    @Serializable
    class Rep2 {
        var results:Int = 0
        var list = ArrayList<Item>()
        @Serializable
        class Item {
            var id:Long=0
            var url:String = ""
            var name:String = ""
            @SerialName("name_cn")
            var nameCn:String = ""
            var summary:String = ""
            @SerialName("air_date")
            var airDate:String?=null
            var images: ItemImage? = null
            var type: Int? = null

        }
        @Serializable
        class ItemImage {
            var common: String = ""
            var large:String = ""
        }
    }
    suspend fun searchSeries2(
        keyword: String
    ): SearchSubjectsResponse {
        return ktor.get("https://api.bgm.tv/search/subject/${URLEncoder.encode(keyword)}?type=1&responseGroup=large").body<Rep2>().let {rep->
            SearchSubjectsResponse(total = rep.results, data = rep.list.map {
                SubjectSearchData(id = it.id, date = it.airDate, image = it.images!!.common, summary = it.summary,
                    name = it.name, nameCn = it.nameCn, tags = emptyList(), type = SubjectType.fromValue(it.type!!))
            })
        }
    }

    suspend fun getSubject(subjectId: Long): BangumiSubject {
        return ktor.get("$apiV0Url/subjects/$subjectId") {
        }.body()
    }

    suspend fun getSubjectRelations(subjectId: Long): Collection<SubjectRelation> {
        return ktor.get("$apiV0Url/subjects/$subjectId/subjects") {
        }.body()
    }

    suspend fun getThumbnail(subject: BangumiSubject): Image? {
        return (subject.images.common ?: subject.images.medium)?.let {
            val bytes: ByteArray = ktor.get(it) {
            }.body()
            Image(bytes)
        }
    }
}
