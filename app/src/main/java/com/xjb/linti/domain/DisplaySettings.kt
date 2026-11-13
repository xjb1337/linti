package com.xjb.linti.domain

enum class ReadingMode { DAY, NIGHT, EYE_CARE }

enum class FontScale(val factor: Float) {
    SMALL(0.875f),
    STANDARD(1.0f),
    LARGE(1.125f),
    EXTRA_LARGE(1.25f),
}
