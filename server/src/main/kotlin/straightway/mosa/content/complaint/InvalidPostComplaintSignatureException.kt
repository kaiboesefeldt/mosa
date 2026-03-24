package straightway.mosa.content.complaint

import straightway.mosa.content.UnprocessableContentException

class InvalidPostComplaintSignatureException : UnprocessableContentException("Post complaint signature is invalid")
