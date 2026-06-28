package com.highdel4y.flipcardwallpaper

enum class LoopTransitionMode(
    val label: String,
    val description: String,
) {
    Snap(
        label = "Snap",
        description = "Step cleanly from photo to photo.",
    ),
    Smooth(
        label = "Smooth transition",
        description = "Blend continuously through the photo list.",
    );

    companion object {
        fun fromStoredName(name: String?): LoopTransitionMode =
            entries.firstOrNull { it.name == name } ?: Snap
    }
}
