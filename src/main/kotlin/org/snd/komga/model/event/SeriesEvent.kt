package org.snd.komga.model.event

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SeriesEvent(
    val seriesId: String,
    val libraryId: String,
)
