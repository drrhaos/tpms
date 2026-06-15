package com.tpms.app.domain.model

enum class SettingsUiMode {
    USER,
    ADVANCED;

    companion object {
        fun fromName(name: String?): SettingsUiMode =
            entries.find { it.name == name } ?: USER
    }
}
