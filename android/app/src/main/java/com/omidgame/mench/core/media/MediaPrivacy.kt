package com.omidgame.mench.core.media

import android.media.ExifInterface
import java.io.File

/** Removes location data from a photo file in place. Re-encoded (compressed) photos carry no EXIF, so this is for originals. */
object MediaPrivacy {
    private val GPS_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
    )

    /** Best effort: files the platform cannot rewrite (png, webp on old Android) are left as they are. Returns true if edited. */
    fun stripLocation(file: File): Boolean = runCatching {
        val exif = ExifInterface(file.absolutePath)
        if (GPS_TAGS.none { exif.getAttribute(it) != null }) return@runCatching false
        GPS_TAGS.forEach { exif.setAttribute(it, null) }
        exif.saveAttributes()
        true
    }.getOrDefault(false)
}
