package com.appsci.panda.sdk.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.LocalTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter

/**
 * Room converter.
 *
 **/
class Converters {

    @TypeConverter
    fun intListToString(listOfInts: List<Int>): String = Json.encodeToString(listOfInts)

    @TypeConverter
    fun stringToIntList(string: String): List<Int> {
        return Json.decodeFromString(string)
    }

    @TypeConverter
    fun mapToString(map: Map<String, String>?): String = Json.encodeToString(map)

    @TypeConverter
    fun stringToMap(string: String?): Map<String, String>? {
        if (string == null) return null
        return Json.decodeFromString(string)
    }

    @TypeConverter
    fun longsListToString(listOfLongs: List<Long>): String = Json.encodeToString(listOfLongs)

    @TypeConverter
    fun stringToLongList(string: String): List<Long> {
        return Json.decodeFromString(string)
    }

    @TypeConverter
    fun localDateTimeToString(localDateTime: LocalDateTime?): String? =
        localDateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    @TypeConverter
    fun stringToLocalDateTime(string: String?): LocalDateTime? =
        if (string == null) null
        else LocalDateTime.parse(string, DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    @TypeConverter
    fun localDateToString(localDate: LocalDate?): String? =
        localDate?.format(DateTimeFormatter.ISO_DATE)

    @TypeConverter
    fun stringToLocalDate(string: String?): LocalDate? =
        string?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }

    @TypeConverter
    fun offsetDateTimeToString(dateTime: OffsetDateTime?): String? =
        dateTime?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    @TypeConverter
    fun stringToOffsetDateTime(string: String?): OffsetDateTime? =
        if (string == null) null
        else OffsetDateTime.parse(string, DateTimeFormatter.ISO_OFFSET_DATE_TIME)


    @TypeConverter
    fun localTimeToString(localTime: LocalTime): String =
        localTime.format(DateTimeFormatter.ISO_LOCAL_TIME)

    @TypeConverter
    fun stringToLocalTime(string: String): LocalTime =
        LocalTime.parse(string, DateTimeFormatter.ISO_LOCAL_TIME)

}
