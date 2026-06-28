package com.highdel4y.flipcardwallpaper

enum class TransitionEffect(
    val label: String,
    val description: String,
) {
    Crossfade(
        label = "Crossfade",
        description = "Classic lenticular opacity blend.",
    ),
    Slide(
        label = "Slide",
        description = "Photos travel across the display as tilt increases.",
    ),
    Wipe(
        label = "Wipe",
        description = "The next photo is revealed from the left edge.",
    ),
    SwipeFade(
        label = "Swipe fade",
        description = "A short swipe with a soft opacity blend.",
    ),
    ZoomFade(
        label = "Zoom fade",
        description = "The next photo settles forward while fading in.",
    ),
    Depth(
        label = "Depth",
        description = "A soft push-in parallax style transition.",
    );

    companion object {
        fun fromStoredName(name: String?): TransitionEffect =
            entries.firstOrNull { it.name == name } ?: Crossfade
    }
}
