package org.localsend.localsend_app.model

import com.google.gson.annotations.SerializedName

enum class DeviceType {
    @SerializedName("mobile")
    MOBILE,
    @SerializedName("desktop")
    DESKTOP,
    @SerializedName("web")
    WEB,
    @SerializedName("headless")
    HEADLESS,
    @SerializedName("server")
    SERVER
}

data class Device(
    val ip: String? = null,
    val version: String = "1.0",
    val port: Int = 53317,
    val https: Boolean = false,
    val fingerprint: String = "",
    val alias: String = "",
    val deviceModel: String? = null,
    val deviceType: DeviceType = DeviceType.DESKTOP,
    val download: Boolean = false,
    val discoveryMethods: Set<String> = setOf("multicast")
)

data class InfoResponse(
    @SerializedName("alias")
    val alias: String,
    @SerializedName("version")
    val version: String?,
    @SerializedName("deviceModel")
    val deviceModel: String?,
    @SerializedName("deviceType")
    val deviceType: DeviceType?,
    @SerializedName("fingerprint")
    val fingerprint: String?,
    @SerializedName("download")
    val download: Boolean?
)

data class FileInfo(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val thumbnailPath: String? = null
)

data class TransferState(
    val fileId: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long = 0,
    val status: TransferStatus = TransferStatus.PENDING,
    val error: String? = null
)

enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}
