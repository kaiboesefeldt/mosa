package straightway.mosa.legal

import io.github.oshai.kotlinlogging.KotlinLogging

class LegalStartupStatus(
    checkProfiles: () -> Unit,
    checkImprint: () -> Unit,
) {
    val complete: Boolean

    init {
        val profilesValid = check("privacy policy and terms", checkProfiles)
        val imprintValid = check("imprint", checkImprint)
        complete = profilesValid && imprintValid
        if (!complete) {
            logger.error {
                "Legal information is missing or invalid. This Mosa pod cannot be operated publicly in legal compliance until it is corrected."
            }
        }
    }

    private fun check(
        name: String,
        validate: () -> Unit,
    ): Boolean =
        try {
            validate()
            true
        } catch (exception: Exception) {
            logger.error(exception) { "Invalid $name: ${exception.message}" }
            false
        }
}

private val logger = KotlinLogging.logger {}
