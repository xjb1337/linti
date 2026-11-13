package com.xjb.linti.domain

enum class AppearanceMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class ColorTheme(val label: String) {
    DEFAULT("默认"),
    JADE("青绿"),
    INDIGO("靛蓝"),
    ROSE("玫瑰"),
    AMBER("琥珀"),
    DYNAMIC("动态取色"),
}

enum class FontScale(val factor: Float) {
    SMALL(0.875f),
    STANDARD(1.0f),
    LARGE(1.125f),
    EXTRA_LARGE(1.25f),
}
