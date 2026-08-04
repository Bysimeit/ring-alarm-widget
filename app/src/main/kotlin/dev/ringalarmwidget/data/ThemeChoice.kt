package dev.ringalarmwidget.data

enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStored(raw: String?): ThemeChoice =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}
