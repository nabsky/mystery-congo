package com.zorindisplays.display.host.db

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object RoomJsonConverters {
    @TypeConverter
    fun fromJsonString(value: String?): JsonObject? =
        value?.let { Json.parseToJsonElement(it) as? JsonObject }

    @TypeConverter
    fun toJsonString(obj: JsonObject?): String? =
        obj?.let { Json.encodeToString(JsonObject.serializer(), it) }
}

