// lyrico-audiotag/src/main/java/com/lonx/audiotag/model/AudioPictureListExt.kt
package com.lonx.audiotag.model

fun List<AudioPicture>.frontCoverOrFallback(): AudioPicture? {
    return firstOrNull { it.type == AudioPictureType.FrontCover }
        ?: firstOrNull { it.type == AudioPictureType.Other }
        ?: firstOrNull()
}

fun List<AudioPicture>.replacePicture(
    picture: AudioPicture,
    type: AudioPictureType = AudioPictureType.FrontCover
): List<AudioPicture> {
    val normalizedPicture = picture.copy(pictureType = type.tagLibName)
    return listOf(normalizedPicture) + filterNot { it.type == type }
}

fun List<AudioPicture>.removePictureType(
    type: AudioPictureType
): List<AudioPicture> {
    return filterNot { it.type == type }
}