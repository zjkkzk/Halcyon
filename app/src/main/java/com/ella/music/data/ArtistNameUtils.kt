package com.ella.music.data

object NameSplitConfigStore {
    @Volatile
    var artistCustomSeparators: List<String> = emptyList()

    @Volatile
    var artistProtectedNames: List<String> = emptyList()

    @Volatile
    var genreCustomSeparators: List<String> = emptyList()

    @Volatile
    var genreProtectedNames: List<String> = emptyList()

    @Volatile
    var tagIgnoreCase: Boolean = false
}

private val defaultArtistSeparatorPatterns = listOf(
    "/",
    "&",
    "、",
    ";",
    "；",
    ",",
    "，",
    "\\+",
    "×",
    " x ",
    " X ",
    "feat\\.?",
    "ft\\.?",
    "with"
)

private val defaultGenreSeparatorPatterns = listOf(
    "/",
    "\\|",
    "、",
    ";",
    "；",
    ",",
    "，"
)

fun splitArtistNames(value: String): List<String> {
    return splitNames(
        value = value,
        defaultSeparatorPatterns = defaultArtistSeparatorPatterns,
        customSeparators = NameSplitConfigStore.artistCustomSeparators,
        protectedNames = NameSplitConfigStore.artistProtectedNames,
        unknownValues = setOf("unknown", "unknown artist")
    )
}

fun splitGenreNames(value: String): List<String> {
    return splitNames(
        value = value,
        defaultSeparatorPatterns = defaultGenreSeparatorPatterns,
        customSeparators = NameSplitConfigStore.genreCustomSeparators,
        protectedNames = NameSplitConfigStore.genreProtectedNames,
        unknownValues = setOf("unknown", "unknown genre")
    )
}

fun String.matchesArtistName(artistName: String): Boolean {
    val target = artistName.trim()
    if (target.isBlank()) return false
    return splitArtistNames(this).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) }
}

fun String.matchesGenreName(genreName: String): Boolean {
    val target = genreName.trim()
    if (target.isBlank()) return false
    return splitGenreNames(this).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) }
}

fun String.tagIdentityKey(): String =
    if (NameSplitConfigStore.tagIgnoreCase) trim().lowercase() else trim()

fun parseNameSplitSetting(value: String): List<String> {
    return value
        .lines()
        .flatMap { line -> line.split('\t') }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
}

private fun splitNames(
    value: String,
    defaultSeparatorPatterns: List<String>,
    customSeparators: List<String>,
    protectedNames: List<String>,
    unknownValues: Set<String>
): List<String> {
    val normalized = value
        .replace("（", "(")
        .replace("）", ")")
        .trim()
    if (normalized.isBlank()) return emptyList()

    val protectedMap = linkedMapOf<String, String>()
    var protectedText = normalized
    protectedNames
        .filter { it.isNotBlank() }
        .sortedByDescending { it.length }
        .forEachIndexed { index, name ->
            val token = "\uE000${index}\uE000"
            val regex = Regex(Regex.escape(name), RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(protectedText)) {
                protectedMap[token] = name
                protectedText = regex.replace(protectedText, token)
            }
        }

    val separatorPattern = (defaultSeparatorPatterns + customSeparators.map { Regex.escape(it) })
        .filter { it.isNotBlank() }
        .joinToString("|")
    if (separatorPattern.isBlank()) return listOf(normalized)

    return protectedText
        .split(Regex("""\s*(?:$separatorPattern)\s*""", RegexOption.IGNORE_CASE))
        .map { raw ->
            protectedMap.entries.fold(raw.trim()) { current, (token, name) ->
                current.replace(token, name)
            }.trim()
        }
        .filter { item ->
            item.isNotBlank() && item.lowercase() !in unknownValues
        }
        .distinctBy { it.tagIdentityKey() }
}
