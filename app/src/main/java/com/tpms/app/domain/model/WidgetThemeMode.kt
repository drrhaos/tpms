package com.tpms.app.domain.model

enum class WidgetThemeMode {
    AUTO,
    LIGHT,
    DARK;

    companion object {
        fun fromName(name: String?): WidgetThemeMode =
            entries.find { it.name == name } ?: AUTO
    }
}
