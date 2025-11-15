package straightway.mosa.client.ui

import kotlin.js.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import straightway.mosa.identity.FictionalName

class IdentityCarouselNavigationPresenterTest {
    @Test
    fun `disables previous navigation while busy`() {
        val presenter =
            presenter {
                isBusy = true
                canNavigatePrevious = true
            }

        val state = presenter.previousButton()

        assertTrue(state.isDisabled)
    }

    @Test
    fun `enables previous navigation when idle and movement allowed`() {
        val presenter =
            presenter {
                isBusy = false
                canNavigatePrevious = true
            }

        val state = presenter.previousButton()

        assertFalse(state.isDisabled)
    }

    @Test
    fun `uses add icon when last identity visible`() {
        val presenter =
            presenter {
                isShowingLastIdentity = true
                canNavigateNext = true
            }

        val state = presenter.nextButton()

        assertEquals(IdentityNavIcon.Add, state.icon)
    }

    @Test
    fun `returns null when progress interaction blocked by busy state`() {
        val presenter =
            presenter {
                isBusy = true
                identityTotal = 4
            }

        val selection = presenter.progressSelection(totalWidth = 200.0, relativePosition = 80.0)

        assertNull(selection)
    }

    @Test
    fun `returns null when no identities available`() {
        val presenter =
            presenter {
                identityTotal = 0
            }

        val selection = presenter.progressSelection(totalWidth = 200.0, relativePosition = 80.0)

        assertNull(selection)
    }

    @Test
    fun `maps progress click to bounded identity index`() {
        val presenter =
            presenter {
                identityTotal = 5
            }

        val selection = presenter.progressSelection(totalWidth = 200.0, relativePosition = 210.0)

        assertEquals(4, selection)
    }

    @Test
    fun `identifies active segment`() {
        val presenter = presenter(activeSegment = 2)

        assertFalse(presenter.isSegmentActive(1))
        assertTrue(presenter.isSegmentActive(2))
    }

    private fun presenter(
        activeSegment: Int = 0,
        configure: IdentityProfileViewProps.() -> Unit = {},
    ): IdentityCarouselNavigationPresenter =
        IdentityCarouselNavigationPresenter(
            props = identityProps(configure),
            activeSegment = activeSegment,
        )

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    private fun identityProps(
        configure: IdentityProfileViewProps.() -> Unit = {},
    ): IdentityProfileViewProps {
        val props = json().unsafeCast<IdentityProfileViewProps>()
        props.identityNameParts = FictionalName()
        props.identityAvatar = null
        props.isBusy = false
        props.errorText = null
        props.canDelete = false
        props.canNavigatePrevious = false
        props.canNavigateNext = false
        props.isShowingLastIdentity = false
        props.identityIndex = 0
        props.identityTotal = 1
        props.canConnect = false
        props.canDownload = false
        props.canUpload = false
        props.onConnect = {}
        props.onPrevious = {}
        props.onNext = {}
        props.onDelete = {}
        props.onSelect = {}
        props.onDownload = {}
        props.onUpload = {}
        configure(props)
        return props
    }
}
