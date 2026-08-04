package dev.ringalarmwidget.core.panel

enum class AlarmMode(val wireValue: String) {
    DISARMED("none"),
    HOME("some"),
    AWAY("all");

    companion object {
        fun fromWire(raw: String?): AlarmMode? = entries.firstOrNull { it.wireValue == raw }
    }
}
