package com.willyshare.willykez.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Fully rounded shape for chips and pill-style controls. */
val PillShape = RoundedCornerShape(percent = 50)

/** App-wide corner scale - rounder than Material defaults for a softer, friendlier feel. */
val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
