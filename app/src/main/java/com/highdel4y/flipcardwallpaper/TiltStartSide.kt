package com.highdel4y.flipcardwallpaper

enum class TiltStartSide(
    val label: String,
    val description: String,
) {
    Right(
        label = "Right",
        description = "Right tilt advances photos when loop is off.",
    ),
    Left(
        label = "Left",
        description = "Left tilt advances photos when loop is off.",
    );

    companion object {
        fun fromStoredName(name: String?): TiltStartSide =
            entries.firstOrNull { it.name == name } ?: Right
    }
}
