package com.shimpyo.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.shimpyo.app.core.designsystem.ShimpyoTheme
import com.shimpyo.app.domain.model.GeoPoint
import com.shimpyo.app.domain.model.RestArea
import com.shimpyo.app.domain.model.RestAreaKind
import com.shimpyo.app.domain.model.RestMode
import com.shimpyo.app.engine.ConditionResult
import com.shimpyo.app.engine.Connections
import com.shimpyo.app.engine.Phase
import com.shimpyo.app.engine.SuggestedStop

// Pleos Connect app area on the 2560 x 1440 (160 dpi) emulator: 1682 x 1210 dp alone, 841 dp wide in a split.
private const val WIDE = 1682
private const val SPLIT = 841
private const val H = 1210
private const val BG = 0xFFF6F8FC

private val sampleArea = RestArea(
    id = "cheonan-samgeori",
    nameKo = "천안삼거리휴게소",
    nameEn = "Cheonan Samgeori Rest Area",
    location = GeoPoint(36.7869, 127.1828),
    kind = RestAreaKind.SERVICE_AREA,
    hasCafe = true,
)

private val base = MainState(
    continuousMinutes = 112,
    minutesToCheckIn = 8,
    connections = Connections(vehicle = true, navi = true, voice = true, location = true),
    caption = "2시간째예요. 졸린 정도는요?",
)

private val proposal = base.copy(
    surface = Surface.DRIVING,
    phase = Phase.Proposing(sampleArea, 15.2),
    suggestedStop = SuggestedStop(sampleArea, 15.2),
    listening = true,
)

@Composable
private fun Frame(state: MainState) = ShimpyoTheme(darkTheme = false) { MainContent(state = state, onIntent = {}) }

@Preview(name = "Home", widthDp = WIDE, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun HomePreview() = Frame(base.copy(surface = Surface.HOME))

@Preview(name = "Home · split", widthDp = SPLIT, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun HomeSplitPreview() = Frame(base.copy(surface = Surface.HOME, demoControls = true))

@Preview(name = "Driving · proposal", widthDp = WIDE, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun DrivingProposalPreview() = Frame(proposal)

@Preview(name = "Driving · proposal · split", widthDp = SPLIT, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun DrivingProposalSplitPreview() = Frame(proposal)

@Preview(name = "Driving · arrived", widthDp = WIDE, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun DrivingArrivedPreview() = Frame(
    base.copy(surface = Surface.DRIVING, phase = Phase.WaitingForPark(sampleArea), suggestedStop = SuggestedStop(sampleArea, 15.2)),
)

@Preview(name = "Rest · choose", widthDp = WIDE, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun ChoosePreview() = Frame(base.copy(surface = Surface.REST, phase = Phase.ChoosingMode))

@Preview(name = "Rest · choose · split", widthDp = SPLIT, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun ChooseSplitPreview() = Frame(base.copy(surface = Surface.REST, phase = Phase.ChoosingMode))

@Preview(name = "Rest · resting", widthDp = WIDE, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun RestingPreview() = Frame(
    base.copy(surface = Surface.REST, phase = Phase.Resting(RestMode.CAR_NAP, 900, 612, waking = false), companionsOnBoard = true),
)

@Preview(name = "Rest · waking", widthDp = WIDE, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun WakingPreview() = Frame(
    base.copy(surface = Surface.REST, phase = Phase.Resting(RestMode.CAR_NAP, 900, 95, waking = true)),
)

@Preview(name = "Rest · condition check", widthDp = WIDE, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable
private fun CheckPreview() = Frame(base.copy(surface = Surface.REST, phase = Phase.ConditionCheck(ConditionResult.GOOD)))
