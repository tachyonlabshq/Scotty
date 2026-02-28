package org.localsend.localsend_app.model

object Constants {
    const val PROTOCOL_VERSION = "2.1"
    const val PEER_PROTOCOL_VERSION = "1.0"
    const val FALLBACK_PROTOCOL_VERSION = "1.0"
    const val DEFAULT_PORT = 53317
    const val DEFAULT_DISCOVERY_TIMEOUT = 500
    const val DEFAULT_MULTICAST_GROUP = "224.0.0.167"
    const val MULTICAST_PORT = 53317
    
    const val BASE_PATH = "/api/localsend"
    
    object ApiRoutes {
        const val INFO_V1 = "$BASE_PATH/v1/info"
        const val REGISTER_V1 = "$BASE_PATH/v1/register"
        const val PREPARE_UPLOAD_V1 = "$BASE_PATH/v1/send-request"
        const val UPLOAD_V1 = "$BASE_PATH/v1/send"
        const val CANCEL_V1 = "$BASE_PATH/v1/cancel"
        const val SHOW_V1 = "$BASE_PATH/v1/show"
        const val PREPARE_DOWNLOAD_V1 = "$BASE_PATH/v1/prepare-download"
        const val DOWNLOAD_V1 = "$BASE_PATH/v1/download"
        
        const val INFO_V2 = "$BASE_PATH/v2/info"
        const val REGISTER_V2 = "$BASE_PATH/v2/register"
        const val PREPARE_UPLOAD_V2 = "$BASE_PATH/v2/prepare-upload"
        const val UPLOAD_V2 = "$BASE_PATH/v2/upload"
        const val CANCEL_V2 = "$BASE_PATH/v2/cancel"
        const val SHOW_V2 = "$BASE_PATH/v2/show"
        const val PREPARE_DOWNLOAD_V2 = "$BASE_PATH/v2/prepare-download"
        const val DOWNLOAD_V2 = "$BASE_PATH/v2/download"
    }
}
