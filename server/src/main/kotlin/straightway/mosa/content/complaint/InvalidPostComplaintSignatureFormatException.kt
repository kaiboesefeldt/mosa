package straightway.mosa.content.complaint

import io.ktor.server.plugins.BadRequestException

class InvalidPostComplaintSignatureFormatException : BadRequestException("Post complaint signature is not valid base64url")
