package app.scotty.model

data class AppSettings(
    val alias: String,
    val deviceModel: String,
    val deviceType: String,
    val port: Int = 53317,
    val fingerprint: String = "",
    val theme: String = "system"
)
