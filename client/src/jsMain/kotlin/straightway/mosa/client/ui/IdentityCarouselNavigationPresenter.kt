package straightway.mosa.client.ui

internal class IdentityCarouselNavigationPresenter(
    private val props: IdentityProfileViewProps,
    private val activeSegment: Int,
) {
    fun previousButton(): IdentityNavButton =
        IdentityNavButton(
            icon = IdentityNavIcon.Previous,
            isDisabled = props.isBusy || !props.canNavigatePrevious,
            onClick = props.onPrevious,
        )

    fun nextButton(): IdentityNavButton =
        IdentityNavButton(
            icon = if (props.isShowingLastIdentity) IdentityNavIcon.Add else IdentityNavIcon.Next,
            isDisabled = props.isBusy || !props.canNavigateNext,
            onClick = props.onNext,
        )

    fun progressSelection(totalWidth: Double, relativePosition: Double): Int? {
        val segments = props.identityTotal
        if (props.isBusy || segments <= 0 || totalWidth <= 0.0) return null
        val clamped = relativePosition.coerceIn(0.0, totalWidth)
        val rawIndex = ((clamped / totalWidth) * segments).toInt()
        return rawIndex.coerceIn(0, segments - 1)
    }

    fun isSegmentActive(index: Int): Boolean = index == activeSegment
}

internal data class IdentityNavButton(
    val icon: IdentityNavIcon,
    val isDisabled: Boolean,
    val onClick: () -> Unit,
)

internal enum class IdentityNavIcon(val cssClass: String) {
    Previous("fa fa-solid fa-chevron-left"),
    Next("fa fa-solid fa-chevron-right"),
    Add("fa fa-solid fa-user-plus"),
}
