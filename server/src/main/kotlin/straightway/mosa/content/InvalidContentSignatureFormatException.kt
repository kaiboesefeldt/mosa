package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException

class InvalidContentSignatureFormatException : BadRequestException("Content signature is not valid base64url")
