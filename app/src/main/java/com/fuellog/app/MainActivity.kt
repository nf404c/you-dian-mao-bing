package com.fuellog.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fuellog.app.domain.Consumption
import com.fuellog.app.data.*
import com.fuellog.app.domain.RecordWithConsumption
import com.fuellog.app.domain.FuelField
import com.fuellog.app.domain.FuelInputs
import com.fuellog.app.domain.FuelLinking
import com.fuellog.app.domain.datePickerStartMillis
import com.fuellog.app.domain.daysSinceDate
import com.fuellog.app.domain.timestampWithSelectedDate
import com.fuellog.app.domain.toFuelField
import com.fuellog.app.domain.calculateVehicleStatistics
import com.fuellog.app.domain.FuelPriceTrendPoint
import com.fuellog.app.domain.averagePriceByGrade
import com.fuellog.app.domain.RecordEnergyType
import com.fuellog.app.domain.consumptionUnit
import com.fuellog.app.domain.defaultRecordEnergyType
import com.fuellog.app.domain.displayName
import com.fuellog.app.domain.priceDisplayUnit
import com.fuellog.app.domain.priceUnit
import com.fuellog.app.domain.quantityUnit
import com.fuellog.app.domain.canChangeVehicleEnergyType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val Ink = Color(0xFF20211F)
private val Soft = Color(0xFFF2F1EC)
private val Accent = Color(0xFF2E624B)
private val HistoryCardShape = RoundedCornerShape(18.dp)
internal const val SwipeTriggerFraction = 0.82f
internal const val NavigationSwipeTriggerFraction = SwipeTriggerFraction * 0.8f
private object Routes {
    const val HOME = "home"
    const val ADD = "add"
    const val HISTORY = "history"
    const val VEHICLES = "vehicles"
    const val STATISTICS = "statistics"
}

private enum class HomeSwipe {
    HISTORY,
    ADD,
    STATISTICS,
    VEHICLES,
    CLOSED
}

private enum class HomeSwipeAxis { HORIZONTAL, VERTICAL }

private enum class SidePageSwipe { CLOSED, RETURN_HOME }

internal fun hasReachedHomeSwipeThreshold(offsetPx: Float, pageWidthPx: Float): Boolean =
    pageWidthPx > 0f && abs(offsetPx) >= pageWidthPx * NavigationSwipeTriggerFraction

internal fun hasReachedHomeVehicleSwipeThreshold(offsetPx: Float, pageWidthPx: Float): Boolean =
    hasReachedHomeSwipeThreshold(offsetPx, pageWidthPx)

/** Uses the same physical pixel distance as left/right home navigation, not screen height. */
internal fun homeVerticalEntryThresholdPx(pageWidthPx: Float): Float =
    pageWidthPx * NavigationSwipeTriggerFraction

internal fun hasReachedHomeVerticalSwipeThreshold(offsetPx: Float, pageWidthPx: Float): Boolean =
    pageWidthPx > 0f && abs(offsetPx) >= homeVerticalEntryThresholdPx(pageWidthPx)

/** Add-record return uses the same physical distance as home navigation. */
internal fun verticalPageReturnThresholdPx(pageWidthPx: Float): Float =
    pageWidthPx * NavigationSwipeTriggerFraction

/** The page must travel its full visible container height before route removal. */
internal fun verticalPageFullExitOffsetPx(pageHeightPx: Float, direction: Float): Float =
    pageHeightPx * direction

internal fun isHistoryListAtTop(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    canScrollBackward: Boolean
): Boolean = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0 && !canScrollBackward

internal fun hasReachedSidePageReturnSwipeThreshold(offsetPx: Float, pageWidthPx: Float, returnDirection: Float): Boolean =
    pageWidthPx > 0f && offsetPx * returnDirection >= pageWidthPx * NavigationSwipeTriggerFraction

internal fun navigationSwipeProgress(offsetPx: Float, pageWidthPx: Float): Float =
    if (pageWidthPx > 0f) {
        (abs(offsetPx) / (pageWidthPx * NavigationSwipeTriggerFraction * 2f)).coerceIn(0f, 1f)
    } else 0f

internal fun navigationHintAlpha(progress: Float): Float {
    val fadeIn = ((progress - 0.08f) / 0.22f).coerceIn(0f, 1f)
    val fadeOut = ((1f - progress) / 0.25f).coerceIn(0f, 1f)
    return minOf(fadeIn, fadeOut)
}

/** Home's vertical-page hints fade over a slightly longer final segment. */
internal fun verticalNavigationHintAlpha(progress: Float): Float {
    val fadeIn = ((progress - 0.08f) / 0.22f).coerceIn(0f, 1f)
    val fadeOut = ((1f - progress) / 0.34f).coerceIn(0f, 1f)
    return minOf(fadeIn, fadeOut)
}

/** Return-page hints use the full settle distance, keeping the late fade visible. */
internal fun verticalReturnHintAlpha(progress: Float): Float {
    val fadeIn = ((progress - 0.06f) / 0.16f).coerceIn(0f, 1f)
    val fadeOut = ((1f - progress) / 0.44f).coerceIn(0f, 1f)
    return minOf(fadeIn, fadeOut)
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FuelLogTheme { FuelLogApp(viewModel) } }
    }
}

@Composable private fun FuelLogTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9AD3B4)) else lightColorScheme(
            primary = Accent, background = Color(0xFFFAFAF7), surface = Color(0xFFFAFAF7),
            surfaceVariant = Soft, onBackground = Ink, onSurface = Ink
        ),
        typography = Typography(bodyLarge = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)),
        content = content
    )
}

@Composable private fun FuelLogApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val pageSlide = with(LocalDensity.current) { 24.dp.roundToPx() }
    Surface(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                val homeToVerticalPage = initialState.destination.route == Routes.HOME && targetState.destination.route in setOf(Routes.HISTORY, Routes.ADD)
                val homeToSidePage = initialState.destination.route == Routes.HOME && targetState.destination.route in setOf(Routes.VEHICLES, Routes.STATISTICS)
                val toVehicles = targetState.destination.route == Routes.VEHICLES
                when {
                    homeToVerticalPage -> fadeIn(tween(160, easing = LinearOutSlowInEasing)) + slideInVertically(tween(210, easing = FastOutSlowInEasing)) { if (targetState.destination.route == Routes.HISTORY) it else -it }
                    else -> fadeIn(tween(160, easing = LinearOutSlowInEasing)) + slideInHorizontally(tween(210, easing = FastOutSlowInEasing)) { if (homeToSidePage && toVehicles) -pageSlide else pageSlide }
                }
            },
            exitTransition = {
                val homeToVerticalPage = initialState.destination.route == Routes.HOME && targetState.destination.route in setOf(Routes.HISTORY, Routes.ADD)
                val homeToSidePage = initialState.destination.route == Routes.HOME && targetState.destination.route in setOf(Routes.VEHICLES, Routes.STATISTICS)
                val toVehicles = targetState.destination.route == Routes.VEHICLES
                when {
                    homeToVerticalPage -> fadeOut(tween(140)) + slideOutVertically(tween(190, easing = FastOutSlowInEasing)) { if (targetState.destination.route == Routes.HISTORY) -pageSlide / 2 else pageSlide / 2 }
                    else -> fadeOut(tween(140)) + slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { if (homeToSidePage && toVehicles) pageSlide / 2 else -pageSlide / 2 }
                }
            },
            popEnterTransition = {
                when (initialState.destination.route) {
                    Routes.ADD, Routes.HISTORY -> EnterTransition.None
                    Routes.VEHICLES -> fadeIn(tween(160, easing = LinearOutSlowInEasing)) + slideInHorizontally(tween(210, easing = FastOutSlowInEasing)) { pageSlide }
                    else -> fadeIn(tween(160, easing = LinearOutSlowInEasing)) + slideInHorizontally(tween(210, easing = FastOutSlowInEasing)) { -pageSlide }
                }
            },
            popExitTransition = {
                when (initialState.destination.route) {
                    Routes.ADD, Routes.HISTORY -> ExitTransition.None
                    Routes.VEHICLES -> fadeOut(tween(140)) + slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { -pageSlide / 2 }
                    else -> fadeOut(tween(140)) + slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { pageSlide / 2 }
                }
            }
        ) {
            composable(Routes.HOME) {
                Home(
                    state,
                    { navController.navigateOnce(Routes.ADD) },
                    { navController.navigateOnce(Routes.HISTORY) },
                    { navController.navigateOnce(Routes.VEHICLES) },
                    { navController.navigateOnce(Routes.STATISTICS) },
                    vm
                )
            }
            composable(Routes.ADD) { AddRecord(state, vm) { navController.popBackStack() } }
            composable(Routes.HISTORY) { History(state, { navController.popBackStack() }, vm) }
            composable(Routes.VEHICLES) { Vehicles(state, vm) { navController.popBackStack() } }
            composable(Routes.STATISTICS) { VehicleStatisticsPage(state) { navController.popBackStack() } }
        }
    }
}

private fun NavHostController.navigateOnce(route: String) {
    navigate(route) { launchSingleTop = true }
}

@Composable private fun Page(
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = rememberScrollState(),
    scrollEnabled: Boolean = true,
    topPadding: Dp = 52.dp,
    content: @Composable ColumnScope.() -> Unit
) = Column(
    modifier.fillMaxSize()
        .then(if (scrollState == null) Modifier else Modifier.verticalScroll(scrollState, enabled = scrollEnabled))
        .padding(horizontal = 24.dp).padding(top = topPadding, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp), content = content
)

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Home(
    state: AppState,
    add: () -> Unit,
    history: () -> Unit,
    vehicles: () -> Unit,
    statistics: () -> Unit,
    vm: MainViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var today by remember { mutableStateOf(java.time.LocalDate.now(java.time.ZoneId.systemDefault())) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val overallConsumption = Consumption.overall(state.records.map { it.record })
    val consumptionTarget = overallConsumption?.toFloat() ?: 0f
    val animatedConsumption by animateFloatAsState(consumptionTarget, tween(420), label = "consumption")
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var navigationStarted by remember { mutableStateOf(false) }
    var renameVisible by rememberSaveable { mutableStateOf(false) }
    var pageOffset by remember { mutableStateOf(Offset.Zero) }
    var gestureAxis by remember { mutableStateOf<HomeSwipeAxis?>(null) }
    val scope = rememberCoroutineScope()
    fun navigate(direction: HomeSwipe) {
        if (navigationStarted) return
        navigationStarted = true
        when (direction) {
            HomeSwipe.VEHICLES -> vehicles()
            HomeSwipe.STATISTICS -> statistics()
            HomeSwipe.HISTORY -> history()
            HomeSwipe.ADD -> add()
            HomeSwipe.CLOSED -> Unit
        }
    }
    fun settleHome() {
        scope.launch {
            val start = pageOffset
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { value, _ ->
                pageOffset = start * (1f - value)
            }
        }
    }
    fun completeHome(direction: HomeSwipe) {
        if (navigationStarted) return
        scope.launch {
            val targetDistance = NavigationSwipeTriggerFraction * 2f
            val target = when (direction) {
                HomeSwipe.VEHICLES -> Offset(pageWidthPx * targetDistance, 0f)
                HomeSwipe.STATISTICS -> Offset(-pageWidthPx * targetDistance, 0f)
                HomeSwipe.HISTORY -> Offset(0f, -pageHeightPx * targetDistance)
                HomeSwipe.ADD -> Offset(0f, pageHeightPx * targetDistance)
                HomeSwipe.CLOSED -> Offset.Zero
            }
            val start = pageOffset
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) { value, _ ->
                pageOffset = androidx.compose.ui.geometry.lerp(start, target, value)
            }
            navigate(direction)
        }
    }
    val horizontalProgress = navigationSwipeProgress(pageOffset.x, pageWidthPx)
    val verticalProgress = navigationSwipeProgress(pageOffset.y, pageHeightPx)
    val horizontalHintAlpha = navigationHintAlpha(horizontalProgress)
    val verticalHintAlpha = verticalNavigationHintAlpha(verticalProgress)
    Box(Modifier.fillMaxSize()) {
        when (gestureAxis) {
            HomeSwipeAxis.HORIZONTAL -> HomeSwipeHint(
                text = if (pageOffset.x >= 0f) "车辆管理" else "车辆统计",
                alignEnd = pageOffset.x < 0f,
                alpha = horizontalHintAlpha,
                pageWidthPx = pageWidthPx,
                revealWidthPx = abs(pageOffset.x)
            )
            HomeSwipeAxis.VERTICAL -> HomeVerticalSwipeHint(
                text = if (pageOffset.y < 0f) "历史记录" else "记一笔",
                alignBottom = pageOffset.y < 0f,
                alpha = verticalHintAlpha,
                pageHeightPx = pageHeightPx,
                revealHeightPx = abs(pageOffset.y)
            )
            null -> Unit
        }
        Page(
            Modifier
                .onSizeChanged { pageWidthPx = it.width.toFloat(); pageHeightPx = it.height.toFloat() }
                .offset { IntOffset(pageOffset.x.roundToInt(), pageOffset.y.roundToInt()) }
                .pointerInput(pageWidthPx, pageHeightPx) {
                    detectDragGestures(
                        onDragStart = { gestureAxis = null },
                        onDragCancel = { settleHome() },
                        onDragEnd = {
                            val direction = when (gestureAxis) {
                                HomeSwipeAxis.HORIZONTAL -> when {
                                    pageOffset.x >= pageWidthPx * NavigationSwipeTriggerFraction -> HomeSwipe.VEHICLES
                                    pageOffset.x <= -pageWidthPx * NavigationSwipeTriggerFraction -> HomeSwipe.STATISTICS
                                    else -> HomeSwipe.CLOSED
                                }
                                HomeSwipeAxis.VERTICAL -> when {
                                    pageOffset.y <= -homeVerticalEntryThresholdPx(pageWidthPx) -> HomeSwipe.HISTORY
                                    pageOffset.y >= homeVerticalEntryThresholdPx(pageWidthPx) -> HomeSwipe.ADD
                                    else -> HomeSwipe.CLOSED
                                }
                                null -> HomeSwipe.CLOSED
                            }
                            if (direction == HomeSwipe.CLOSED) settleHome() else completeHome(direction)
                        },
                        onDrag = { _, amount ->
                            // detectDragGestures has already crossed touch slop before this callback.
                            if (gestureAxis == null) {
                                gestureAxis = if (abs(amount.x) > abs(amount.y)) HomeSwipeAxis.HORIZONTAL else HomeSwipeAxis.VERTICAL
                            }
                            when (gestureAxis) {
                                HomeSwipeAxis.HORIZONTAL -> { pageOffset = Offset(pageOffset.x + amount.x, 0f) }
                                HomeSwipeAxis.VERTICAL -> { pageOffset = Offset(0f, pageOffset.y + amount.y) }
                                null -> Unit
                            }
                        }
                    )
                },
            scrollState = null
        ) {
    Spacer(Modifier.height(48.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AnimatedContent(state.activeVehicle, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) }, label = "vehicleName") { vehicle ->
            TextButton(onClick = { renameVisible = true }, enabled = vehicle != null, contentPadding = PaddingValues(0.dp)) {
                Text(vehicle?.name ?: "还没有车辆", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    Spacer(Modifier.height(36.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val energyType = state.activeVehicle?.energyType ?: EnergyType.FUEL
            Text(if (energyType == EnergyType.FUEL) "平均油耗" else "平均电耗", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (overallConsumption == null) "—" else one(animatedConsumption.toDouble()), fontSize = 64.sp, fontWeight = FontWeight.Light)
            Text(
                if (overallConsumption == null) {
                    if (energyType == EnergyType.FUEL) "等待下一次加油后计算" else "等待下一次充电后计算"
                } else energyType.consumptionUnit(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    val latest = state.records.lastOrNull()
    val last = latest?.record
    val energyType = state.activeVehicle?.energyType ?: EnergyType.FUEL
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (energyType == EnergyType.FUEL) "上次加油" else "上次充电", color = MaterialTheme.colorScheme.onSurfaceVariant)
                last?.let { Text(date(it.timestamp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(last?.odometerKm?.let { "${number(it)} km" } ?: "暂无记录", fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Text(
                    latest?.litersPer100Km?.let { "${two(it)} ${energyType.consumptionUnit()}" }
                        ?: if (energyType == EnergyType.FUEL) "暂无区间油耗" else "暂无区间电耗",
                    fontWeight = FontWeight.Medium,
                    color = if (latest?.litersPer100Km == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    last?.let {
                        val typeName = RecordEnergyType.fromStorageValue(energyType, it.fuelGrade)?.displayName ?: it.fuelGrade
                        val suffix = if (energyType == EnergyType.FUEL) "号" else ""
                        "$typeName$suffix · ¥${two(it.pricePerLiter)}${energyType.priceDisplayUnit()}"
                    } ?: "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    last?.let { record -> daysSinceDate(record.timestamp, today).let { days -> if (days == 0L) "今天" else "距上次 $days 天" } } ?: "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
        }
    }
    state.activeVehicle?.takeIf { renameVisible }?.let { vehicle ->
        VehicleRenameDialog(vehicle, vm) { renameVisible = false }
    }
}

@Composable private fun HomeSwipeHint(
    text: String,
    alignEnd: Boolean,
    alpha: Float,
    pageWidthPx: Float,
    revealWidthPx: Float
) {
    var hintWidthPx by remember { mutableIntStateOf(0) }
    val horizontalCenterPx = if (alignEnd) pageWidthPx - revealWidthPx / 2f else revealWidthPx / 2f
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .onSizeChanged { hintWidthPx = it.width }
                .offset { IntOffset(horizontalCenterPx.roundToInt() - hintWidthPx / 2, 0) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            text.forEach { character ->
                Text(
                    character.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable private fun HomeVerticalSwipeHint(
    text: String,
    alignBottom: Boolean,
    alpha: Float,
    pageHeightPx: Float,
    revealHeightPx: Float
) {
    var hintHeightPx by remember { mutableIntStateOf(0) }
    val verticalCenterPx = if (alignBottom) pageHeightPx - revealHeightPx / 2f else revealHeightPx / 2f
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(
            text,
            modifier = Modifier
                .onSizeChanged { hintHeightPx = it.height }
                .offset { IntOffset(0, verticalCenterPx.roundToInt() - hintHeightPx / 2) },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable private fun EnergyTypeSelector(
    selected: EnergyType,
    enabled: Boolean,
    onSelected: (EnergyType) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EnergyType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                enabled = enabled,
                label = { Text(type.emoji, fontSize = 18.sp) },
                modifier = Modifier.semantics { contentDescription = type.displayName() }
            )
        }
    }
}

@Composable private fun VehicleRenameDialog(vehicle: Vehicle, vm: MainViewModel, onDismiss: () -> Unit) {
    var name by remember(vehicle.id) { mutableStateOf(vehicle.name) }
    var energyType by remember(vehicle.id) { mutableStateOf(vehicle.energyType) }
    var error by remember(vehicle.id) { mutableStateOf<String?>(null) }
    var recordCount by remember(vehicle.id) { mutableStateOf<Int?>(null) }
    var confirmVisible by remember(vehicle.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(vehicle.id) { recordCount = vm.recordCount(vehicle.id) }
    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("确认修改车辆信息？") },
            text = {
                Text(
                    when {
                        vehicle.name != name.trim() && vehicle.energyType != energyType ->
                            "确认将车辆名称修改为「${name.trim()}」，并将车辆类型修改为${energyType.displayName()}？"
                        vehicle.energyType != energyType -> "确认将车辆类型修改为${energyType.displayName()}？"
                        else -> "确认将车辆名称修改为「${name.trim()}」？"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        error = vm.updateVehicle(vehicle, name, energyType)
                        if (error == null) onDismiss() else confirmVisible = false
                    }
                }) { Text("确认修改") }
            },
            dismissButton = { TextButton(onClick = { confirmVisible = false }) { Text("取消") } }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("编辑车辆") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; error = null }, Modifier.fillMaxWidth(), label = { Text("车辆名称") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                EnergyTypeSelector(
                    selected = energyType,
                    enabled = recordCount?.let(::canChangeVehicleEnergyType) == true,
                    onSelected = { energyType = it; error = null }
                )
                if (recordCount != null && recordCount != 0) {
                    Text("已有记录，车辆类型不可修改", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.trim().isEmpty() -> error = "请输入车辆名称。"
                    name.trim() == vehicle.name && energyType == vehicle.energyType -> error = "没有需要保存的修改。"
                    else -> confirmVisible = true
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun SidePageSwipeBack(returnDirection: Float, back: () -> Unit, content: @Composable () -> Unit) {
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    var returnStarted by remember { mutableStateOf(false) }
    val swipeState = remember {
        AnchoredDraggableState(
            initialValue = SidePageSwipe.CLOSED,
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { Float.MAX_VALUE },
            snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            decayAnimationSpec = exponentialDecay()
        )
    }
    val anchors = remember(pageWidthPx, returnDirection) {
        val committedAnchorDistancePx = pageWidthPx * NavigationSwipeTriggerFraction * 2f * returnDirection
        DraggableAnchors {
            SidePageSwipe.CLOSED at 0f
            SidePageSwipe.RETURN_HOME at committedAnchorDistancePx
        }
    }
    SideEffect { if (pageWidthPx > 0f) swipeState.updateAnchors(anchors) }
    LaunchedEffect(swipeState) {
        snapshotFlow { swipeState.settledValue }.collect { value ->
            if (value == SidePageSwipe.RETURN_HOME && !returnStarted) {
                returnStarted = true
                back()
            }
        }
    }
    val offset = swipeState.offset.takeIf { !it.isNaN() } ?: 0f
    Box(Modifier.fillMaxSize().onSizeChanged { pageWidthPx = it.width.toFloat() }) {
        HomeSwipeHint(
            text = "返回主页",
            alignEnd = returnDirection < 0f,
            alpha = navigationHintAlpha(navigationSwipeProgress(offset, pageWidthPx)),
            pageWidthPx = pageWidthPx,
            revealWidthPx = abs(offset)
        )
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .anchoredDraggable(state = swipeState, orientation = Orientation.Horizontal)
        ) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun VerticalPageSwipeBack(
    back: () -> Unit,
    navigationEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var returnStarted by remember { mutableStateOf(false) }
    val swipeState = remember {
        AnchoredDraggableState(
            initialValue = SidePageSwipe.CLOSED,
            positionalThreshold = { verticalPageReturnThresholdPx(pageWidthPx) },
            velocityThreshold = { Float.MAX_VALUE },
            snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            decayAnimationSpec = exponentialDecay()
        )
    }
    val anchors = remember(pageHeightPx) {
        val committedAnchorDistancePx = verticalPageFullExitOffsetPx(pageHeightPx, -1f)
        DraggableAnchors { SidePageSwipe.CLOSED at 0f; SidePageSwipe.RETURN_HOME at committedAnchorDistancePx }
    }
    SideEffect { if (pageHeightPx > 0f) swipeState.updateAnchors(anchors) }
    LaunchedEffect(swipeState) {
        snapshotFlow { swipeState.settledValue }.collect { value ->
            if (value == SidePageSwipe.RETURN_HOME && !returnStarted) { returnStarted = true; back() }
        }
    }
    val offset = swipeState.offset.takeIf { !it.isNaN() } ?: 0f
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { pageWidthPx = it.width.toFloat(); pageHeightPx = it.height.toFloat() }
    ) {
        HomeVerticalSwipeHint("返回主页", alignBottom = true, alpha = verticalReturnHintAlpha(navigationSwipeProgress(offset, pageHeightPx)), pageHeightPx = pageHeightPx, revealHeightPx = abs(offset))
        Box(
            Modifier.fillMaxSize()
                .offset { IntOffset(0, offset.roundToInt()) }
                .then(
                    if (navigationEnabled) Modifier.anchoredDraggable(state = swipeState, orientation = Orientation.Vertical)
                    else Modifier
                )
        ) { content() }
    }
}

@Composable private fun CenteredHeader(title: String) = Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Text(title, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
}

@Composable private fun NumberField(value: String, onChange: (String) -> Unit, label: String, suffix: String) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, suffix = { Text(suffix) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(14.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AddRecord(state: AppState, vm: MainViewModel, back: () -> Unit) {
    val vehicle = state.activeVehicle
    val vehicleId = vehicle?.id ?: -1L
    val energyType = vehicle?.energyType ?: EnergyType.FUEL
    val availableTypes = RecordEnergyType.forVehicle(energyType)
    val pageScrollState = rememberScrollState()
    val contentNeedsScroll = pageScrollState.maxValue > 0
    VerticalPageSwipeBack(back, navigationEnabled = !contentNeedsScroll) {
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(pageScrollState, enabled = contentNeedsScroll)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
    var selectedDateMillis by rememberSaveable(vehicleId) { mutableLongStateOf(datePickerStartMillis(System.currentTimeMillis())) }
    var showDatePicker by rememberSaveable(vehicleId) { mutableStateOf(false) }
    var km by rememberSaveable(vehicleId) { mutableStateOf("") }
    var grade by rememberSaveable(vehicleId) {
        mutableStateOf(
            state.records.lastOrNull()?.record?.fuelGrade
                ?.takeIf { RecordEnergyType.fromStorageValue(energyType, it) != null }
                ?: energyType.defaultRecordEnergyType().storageValue
        )
    }
    var price by rememberSaveable(vehicleId) { mutableStateOf(state.recentPrices[grade]?.let(::plain) ?: "") }
    var amount by rememberSaveable(vehicleId) { mutableStateOf("") }
    var liters by rememberSaveable(vehicleId) { mutableStateOf("") }
    var editOrderText by rememberSaveable(vehicleId) { mutableStateOf("") }
    var error by rememberSaveable(vehicleId) { mutableStateOf<String?>(null) }
    var saved by rememberSaveable(vehicleId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun userEdit(field: FuelField, value: String) {
        val result = FuelLinking.onUserEdit(
            FuelInputs(price, amount, liters, editOrderText.split(',').mapNotNull { it.toFuelField() }),
            field,
            value
        )
        price = result.price
        amount = result.amount
        liters = result.liters
        editOrderText = result.editOrder.joinToString(",") { it.name }
        error = null
    }
    fun selectGrade(newGrade: String) {
        grade = newGrade
        val result = FuelLinking.onSystemPricePrefill(
            FuelInputs(price, amount, liters, editOrderText.split(',').mapNotNull { it.toFuelField() }),
            state.recentPrices[newGrade]?.let(::plain) ?: ""
        )
        price = result.price
        amount = result.amount
        liters = result.liters
        editOrderText = result.editOrder.joinToString(",") { it.name }
        error = null
    }
    val selectedTimestamp = timestampWithSelectedDate(System.currentTimeMillis(), selectedDateMillis)
    OutlinedButton(onClick = { showDatePicker = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Text("${if (energyType == EnergyType.FUEL) "加油" else "充电"}日期  ${date(selectedTimestamp)}")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        availableTypes.forEach { recordType ->
            val value = recordType.storageValue
            val previousPrice = state.recentPrices[value]
            FilterChip(
                selected = grade == value,
                onClick = { selectGrade(value) },
                label = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(recordType.displayName)
                        Spacer(Modifier.weight(1f))
                        Text(
                            previousPrice?.let { "上次 ¥${two(it)}" } ?: "暂无记录",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
    NumberField(km, { km = it; error = null }, "当前总里程", "km")
    NumberField(price, { userEdit(FuelField.PRICE, it) }, if (energyType == EnergyType.FUEL) "当前油价" else "当前电价", energyType.priceUnit())
    NumberField(amount, { userEdit(FuelField.AMOUNT, it) }, if (energyType == EnergyType.FUEL) "本次加油金额" else "本次充电金额", "元")
    NumberField(liters, { userEdit(FuelField.LITERS, it) }, if (energyType == EnergyType.FUEL) "本次加油升数" else "本次充电电量", energyType.quantityUnit())
    Spacer(Modifier.height(12.dp))
    val currentKm = km.toDoubleOrNull()
    val currentLiters = liters.toDoubleOrNull()
    val previousKm = state.records.lastOrNull()?.record?.odometerKm
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (previousKm == null) Text("首次记录，将作为后续${if (energyType == EnergyType.FUEL) "油耗" else "电耗"}计算基准。")
            else if (currentKm != null && currentLiters != null && currentKm > previousKm) {
                val distance = currentKm - previousKm
                Text("本次行驶  ${number(distance)} km")
                Text(
                    "预计平均${if (energyType == EnergyType.FUEL) "油耗" else "电耗"}  ${two(currentLiters / distance * 100)} ${energyType.consumptionUnit()}",
                    fontWeight = FontWeight.SemiBold
                )
            } else Text(
                "输入里程和${if (energyType == EnergyType.FUEL) "升数" else "电量"}后显示预计${if (energyType == EnergyType.FUEL) "油耗" else "电耗"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        if (energyType == EnergyType.FUEL) "默认加至跳枪，跳枪后不补油" else "请按本次实际充电电量记录",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(12.dp))
    Button(onClick = {
        val values = listOf(km, price, amount, liters).map { it.toDoubleOrNull() }
        if (values.any { it == null }) error = "请完整填写所有字段。"
        else scope.launch {
            error = vm.saveRecord(values[0]!!, grade, values[1]!!, values[2]!!, values[3]!!, selectedTimestamp)
            if (error == null) {
                saved = true
                delay(180)
                back()
            }
        }
    }, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp), enabled = !saved) {
        AnimatedContent(saved, label = "saveState") { done -> Text(if (done) "已保存 ✓" else "保存记录") }
    }
    if (showDatePicker) {
        val todayPickerMillis = datePickerStartMillis(System.currentTimeMillis())
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayPickerMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = { pickerState.selectedDateMillis?.let { selectedDateMillis = it }; showDatePicker = false },
                    enabled = pickerState.selectedDateMillis != null
                ) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = pickerState) }
    }
            }
        }
    }
}

private enum class SwipeReveal {
    EDIT_COMMITTED,
    CLOSED,
    DELETE_COMMITTED
}

private data class RecordEditRequest(val original: FuelRecord, val edited: FuelRecord)

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun HistoryPageSwipeBack(
    listState: LazyListState,
    back: () -> Unit,
    content: @Composable () -> Unit
) {
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    var pageHeightPx by remember { mutableFloatStateOf(0f) }
    var returnStarted by remember { mutableStateOf(false) }
    var returnGestureArmed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val swipeState = remember {
        AnchoredDraggableState(
            initialValue = SidePageSwipe.CLOSED,
            positionalThreshold = { verticalPageReturnThresholdPx(pageWidthPx) },
            velocityThreshold = { Float.MAX_VALUE },
            snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
            decayAnimationSpec = exponentialDecay()
        )
    }
    val anchors = remember(pageHeightPx) {
        DraggableAnchors {
            SidePageSwipe.CLOSED at 0f
            SidePageSwipe.RETURN_HOME at verticalPageFullExitOffsetPx(pageHeightPx, 1f)
        }
    }
    SideEffect { if (pageHeightPx > 0f) swipeState.updateAnchors(anchors) }
    LaunchedEffect(swipeState) {
        snapshotFlow { swipeState.settledValue }.collect { value ->
            if (value == SidePageSwipe.RETURN_HOME && !returnStarted) {
                returnStarted = true
                back()
            }
        }
    }
    fun listIsAtTop() = isHistoryListAtTop(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        listState.canScrollBackward
    )
    val historyReturnConnection = remember(swipeState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && returnGestureArmed && listIsAtTop() && available.y > 0f) {
                    Offset(0f, swipeState.dispatchRawDelta(available.y))
                } else {
                    Offset.Zero
                }
        }
    }
    val offset = swipeState.offset.takeIf { !it.isNaN() } ?: 0f
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { pageWidthPx = it.width.toFloat(); pageHeightPx = it.height.toFloat() }
            .nestedScroll(historyReturnConnection)
            .pointerInput(listState, pageWidthPx) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed && !it.previousPressed }) {
                            returnGestureArmed = listIsAtTop()
                        }
                        if (event.changes.none { it.pressed } && returnGestureArmed) {
                            returnGestureArmed = false
                            scope.launch {
                                swipeState.settle(velocity = 0f)
                            }
                        }
                    }
                }
            }
    ) {
        HomeVerticalSwipeHint(
            "返回主页",
            alignBottom = false,
            alpha = verticalReturnHintAlpha(navigationSwipeProgress(offset, pageHeightPx)),
            pageHeightPx = pageHeightPx,
            revealHeightPx = abs(offset)
        )
        Box(Modifier.fillMaxSize().offset { IntOffset(0, offset.roundToInt()) }) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun History(state: AppState, back: () -> Unit, vm: MainViewModel) {
    val records = state.records
    val energyType = state.activeVehicle?.energyType ?: EnergyType.FUEL
    var deleteTarget by remember { mutableStateOf<RecordWithConsumption?>(null) }
    var deleteVisible by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<RecordWithConsumption?>(null) }
    var editVisible by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<RecordEditRequest?>(null) }
    var editError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(editVisible) {
        if (!editVisible) {
            delay(190)
            if (!editVisible) editTarget = null
        }
    }
    LaunchedEffect(deleteVisible) {
        if (!deleteVisible) {
            delay(190)
            if (!deleteVisible) deleteTarget = null
        }
    }
    fun closeEdit() {
        if (pendingEdit == null) {
            editVisible = false
            editError = null
        }
    }
    fun closeDelete() { deleteVisible = false }
    BackHandler(enabled = editVisible && pendingEdit == null) { closeEdit() }
    BackHandler(enabled = deleteVisible) { closeDelete() }
    HistoryPageSwipeBack(listState, back) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (records.isEmpty()) item(key = "empty") {
                Text(if (energyType == EnergyType.FUEL) "还没有加油记录。" else "还没有充电记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else items(records.asReversed(), key = { it.record.id }) { item ->
                SwipeHistoryRecord(
                    item = item,
                    energyType = energyType,
                    onEdit = { editTarget = item; editVisible = true; editError = null },
                    onDelete = { deleteTarget = item; deleteVisible = true },
                    resetSwipe = !editVisible && !deleteVisible,
                    modifier = Modifier.animateItem()
                )
            }
        }
        AnimatedVisibility(
            visible = editVisible,
            enter = slideInHorizontally(tween(210, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(140))
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                editTarget?.let { item ->
                    RecordEditorSheet(
                        item = item,
                        energyType = energyType,
                        externalError = editError,
                        onClearError = { editError = null },
                        onDismiss = ::closeEdit,
                        onSave = { edited ->
                            val validation = vm.validateRecordUpdate(edited)
                            if (validation == null) pendingEdit = RecordEditRequest(item.record, edited) else editError = validation
                        }
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = deleteVisible,
            enter = slideInHorizontally(tween(210, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(190, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(140))
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                deleteTarget?.let { item ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Top
                        ) {
                            Text("删除这条${if (energyType == EnergyType.FUEL) "加油" else "充电"}记录？", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(18.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                Column(
                                    Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(date(item.record.timestamp), fontSize = 15.sp)
                                    Text(
                                        "${number(item.record.odometerKm)} km · ${recordTypeDisplayName(energyType, item.record.fuelGrade)}${if (energyType == EnergyType.FUEL) "号" else ""}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "¥${two(item.record.pricePerLiter)}${energyType.priceDisplayUnit()} · ${two(item.record.liters)} ${energyType.quantityUnit()}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text("此操作无法撤销。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(Modifier.height(24.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = ::closeDelete,
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("取消", fontSize = 17.sp) }
                                Button(
                                    onClick = {
                                        vm.deleteRecord(item.record.id)
                                        closeDelete()
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("删除", fontSize = 17.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingEdit?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingEdit = null },
            title = { Text("确认修改这条${if (energyType == EnergyType.FUEL) "加油" else "充电"}记录？") },
            text = { Text(recordChangeSummary(request.original, request.edited, energyType)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = vm.updateRecord(request.edited)
                        if (result == null) {
                            pendingEdit = null
                            editVisible = false
                            editError = null
                        } else {
                            pendingEdit = null
                            editError = result
                        }
                    }
                }) { Text("确认修改") }
            },
            dismissButton = { TextButton(onClick = { pendingEdit = null }) { Text("取消") } }
        )
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun SwipeHistoryRecord(
    item: RecordWithConsumption,
    energyType: EnergyType,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    resetSwipe: Boolean,
    modifier: Modifier = Modifier
) {
    // Only a drag across 82% of this card's actual width can trigger an action.
    // A finite fling velocity never bypasses the distance requirement.
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    val swipeState = remember {
        AnchoredDraggableState(
            initialValue = SwipeReveal.CLOSED,
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { Float.MAX_VALUE },
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            decayAnimationSpec = exponentialDecay()
        )
    }
    val anchors = remember(cardWidthPx) {
        // The commit anchors are intentionally twice as far as the physical trigger.
        // With a 50% positional threshold, the commit decision occurs at exactly 82%.
        val committedAnchorDistancePx = cardWidthPx * SwipeTriggerFraction * 2f
        DraggableAnchors {
            SwipeReveal.EDIT_COMMITTED at committedAnchorDistancePx
            SwipeReveal.CLOSED at 0f
            SwipeReveal.DELETE_COMMITTED at -committedAnchorDistancePx
        }
    }
    SideEffect { if (cardWidthPx > 0f) swipeState.updateAnchors(anchors) }
    LaunchedEffect(resetSwipe) {
        if (resetSwipe && swipeState.currentValue != SwipeReveal.CLOSED) {
            swipeState.snapTo(SwipeReveal.CLOSED)
        }
    }
    LaunchedEffect(swipeState) {
        snapshotFlow { swipeState.settledValue }.collect { settledValue ->
            when (settledValue) {
                SwipeReveal.EDIT_COMMITTED -> {
                    onEdit()
                }
                SwipeReveal.DELETE_COMMITTED -> {
                    onDelete()
                }
                SwipeReveal.CLOSED -> Unit
            }
        }
    }
    val offset = swipeState.offset.takeIf { !it.isNaN() } ?: 0f
    val progress = if (cardWidthPx > 0f) (abs(offset) / cardWidthPx).coerceIn(0f, 1f) else 0f
    val isEditing = offset > 0f
    val isDeleting = offset < 0f
    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { cardWidthPx = it.width.toFloat() }
            .clip(HistoryCardShape)
            .background(
                when {
                    isEditing -> MaterialTheme.colorScheme.primaryContainer
                    isDeleting -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isEditing || isDeleting) {
            Text(
                if (isEditing) "编辑" else "删除",
                fontWeight = FontWeight.SemiBold,
                fontSize = if (progress >= SwipeTriggerFraction) 15.sp else 14.sp,
                color = if (isEditing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Card(
            Modifier.fillMaxWidth()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .anchoredDraggable(state = swipeState, orientation = Orientation.Horizontal),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = HistoryCardShape
        ) { RecordContent(item, energyType) }
    }
}

@Composable private fun RecordContent(item: RecordWithConsumption, energyType: EnergyType) = Column(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(date(item.record.timestamp), fontWeight = FontWeight.SemiBold)
        Text(item.daysSincePrevious?.let { if (it == 0L) "当天" else "距上次 ${it} 天" } ?: "首次记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("${number(item.record.odometerKm)} km")
        Text(item.litersPer100Km?.let { "${two(it)} ${energyType.consumptionUnit()}" } ?: "基准记录", fontWeight = FontWeight.Medium, color = if (item.litersPer100Km == null) Accent else MaterialTheme.colorScheme.onSurface)
    }
    Text(
        "${recordTypeDisplayName(energyType, item.record.fuelGrade)}${if (energyType == EnergyType.FUEL) "号" else ""} · ¥${two(item.record.pricePerLiter)}${energyType.priceDisplayUnit()} · ${two(item.record.liters)} ${energyType.quantityUnit()} · ¥${two(item.record.amountPaid)}",
        modifier = Modifier.fillMaxWidth(),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RecordEditorSheet(
    item: RecordWithConsumption,
    energyType: EnergyType,
    externalError: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (FuelRecord) -> Unit
) {
    val original = item.record
    var selectedDateMillis by rememberSaveable(original.id) { mutableLongStateOf(datePickerStartMillis(original.timestamp)) }
    var grade by rememberSaveable(original.id) { mutableStateOf(original.fuelGrade) }
    var odometer by rememberSaveable(original.id) { mutableStateOf(plain(original.odometerKm)) }
    var price by rememberSaveable(original.id) { mutableStateOf(plain(original.pricePerLiter)) }
    var amount by rememberSaveable(original.id) { mutableStateOf(plain(original.amountPaid)) }
    var liters by rememberSaveable(original.id) { mutableStateOf(plain(original.liters)) }
    var editOrderText by rememberSaveable(original.id) { mutableStateOf("") }
    var showDatePicker by rememberSaveable(original.id) { mutableStateOf(false) }
    var localError by rememberSaveable(original.id) { mutableStateOf<String?>(null) }
    fun clearError() { localError = null; onClearError() }
    fun userEdit(field: FuelField, value: String) {
        val result = FuelLinking.onUserEdit(
            FuelInputs(price, amount, liters, editOrderText.split(',').mapNotNull { it.toFuelField() }),
            field,
            value
        )
        price = result.price
        amount = result.amount
        liters = result.liters
        editOrderText = result.editOrder.joinToString(",") { it.name }
        clearError()
    }
    val editedDateTimestamp = timestampWithSelectedDate(original.timestamp, selectedDateMillis)
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回历史记录")
            }
            Text("编辑${if (energyType == EnergyType.FUEL) "加油" else "充电"}记录", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = { showDatePicker = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("日期  ${date(editedDateTimestamp)}")
        }
        NumberField(odometer, { odometer = it; clearError() }, "当前总里程", "km")
        Text(if (energyType == EnergyType.FUEL) "汽油标号" else "充电类型", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecordEnergyType.forVehicle(energyType).forEach { recordType ->
                FilterChip(grade == recordType.storageValue, { grade = recordType.storageValue; clearError() }, { Text(recordType.displayName) }, Modifier.weight(1f))
            }
        }
        NumberField(price, { userEdit(FuelField.PRICE, it) }, if (energyType == EnergyType.FUEL) "当前油价" else "当前电价", energyType.priceUnit())
        NumberField(amount, { userEdit(FuelField.AMOUNT, it) }, if (energyType == EnergyType.FUEL) "本次加油金额" else "本次充电金额", "元")
        NumberField(liters, { userEdit(FuelField.LITERS, it) }, if (energyType == EnergyType.FUEL) "本次加油升数" else "本次充电电量", energyType.quantityUnit())
        (localError ?: externalError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            val parsedOdometer = odometer.toDoubleOrNull()
            val parsedPrice = price.toDoubleOrNull()
            val parsedAmount = amount.toDoubleOrNull()
            val parsedLiters = liters.toDoubleOrNull()
            if (listOf(parsedOdometer, parsedPrice, parsedAmount, parsedLiters).any { it == null }) {
                localError = "请完整填写所有字段。"
            } else {
                val edited = original.copy(
                    timestamp = editedDateTimestamp,
                    odometerKm = parsedOdometer!!,
                    fuelGrade = grade,
                    pricePerLiter = parsedPrice!!,
                    amountPaid = parsedAmount!!,
                    liters = parsedLiters!!
                )
                if (edited == original) localError = "没有需要保存的修改。" else onSave(edited)
            }
        }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("保存") }
        TextButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("取消") }
    }
    if (showDatePicker) {
        val todayPickerMillis = datePickerStartMillis(System.currentTimeMillis())
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayPickerMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = { pickerState.selectedDateMillis?.let { selectedDateMillis = it }; showDatePicker = false },
                    enabled = pickerState.selectedDateMillis != null
                ) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = pickerState) }
    }
}

private fun recordChangeSummary(original: FuelRecord, edited: FuelRecord, energyType: EnergyType): String {
    val changes = buildList {
        if (date(original.timestamp) != date(edited.timestamp)) add("日期：${date(original.timestamp)} → ${date(edited.timestamp)}")
        if (original.odometerKm != edited.odometerKm) add("里程：${number(original.odometerKm)} → ${number(edited.odometerKm)} km")
        if (original.fuelGrade != edited.fuelGrade) add(
            "${if (energyType == EnergyType.FUEL) "油号" else "充电类型"}：${recordTypeDisplayName(energyType, original.fuelGrade)} → ${recordTypeDisplayName(energyType, edited.fuelGrade)}"
        )
        if (original.pricePerLiter != edited.pricePerLiter) add("${if (energyType == EnergyType.FUEL) "油价" else "电价"}：¥${two(original.pricePerLiter)} → ¥${two(edited.pricePerLiter)}${energyType.priceDisplayUnit()}")
        if (original.amountPaid != edited.amountPaid) add("金额：¥${two(original.amountPaid)} → ¥${two(edited.amountPaid)}")
        if (original.liters != edited.liters) add("${if (energyType == EnergyType.FUEL) "升数" else "电量"}：${two(original.liters)} → ${two(edited.liters)} ${energyType.quantityUnit()}")
    }
    return changes.ifEmpty { listOf("未检测到字段变化。") }.joinToString("\n")
}

@Composable private fun VehicleStatisticsPage(state: AppState, back: () -> Unit) {
    val energyType = state.activeVehicle?.energyType ?: EnergyType.FUEL
    BackHandler(onBack = back)
    SidePageSwipeBack(returnDirection = 1f, back = back) {
        val records = state.records.map { it.record }
        val statistics = remember(records) { calculateVehicleStatistics(records) }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().height(maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                if (state.activeVehicle == null) {
                    Text("还没有车辆。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (statistics.recordCount == 0) {
                    Text(if (energyType == EnergyType.FUEL) "暂无加油记录" else "暂无充电记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatisticsGrid(statistics, energyType)
                        FuelPriceTrend(statistics.recentPriceSeries, energyType)
                    }
                }
            }
        }
    }
}

@Composable private fun StatisticsGrid(statistics: com.fuellog.app.domain.VehicleStatistics, energyType: EnergyType) {
    val cards = listOf(
        "累计记录里程" to (statistics.recordedDistanceKm?.let { "${number(it)} km" } ?: "—"),
        (if (energyType == EnergyType.FUEL) "累计加油量" else "累计充电量") to "${two(statistics.totalLiters)} ${energyType.quantityUnit()}",
        "累计花费" to "¥${two(statistics.totalAmount)}",
        (if (energyType == EnergyType.FUEL) "累计加油次数" else "累计充电次数") to "${statistics.recordCount} 次",
        "平均间隔天数" to (statistics.averageIntervalDays?.let { "$it 天" } ?: "--"),
        "平均间隔里程" to (statistics.averageIntervalKm?.let { "$it km" } ?: "--")
    )
    cards.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (label, value) -> StatisticCard(label, value, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable private fun StatisticCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable private fun FuelPriceTrend(series: Map<String, List<FuelPriceTrendPoint>>, energyType: EnergyType) {
    val recordTypes = RecordEnergyType.forVehicle(energyType)
    val relevantSeries = series.filterKeys { key -> recordTypes.any { it.storageValue == key } }
    val points = relevantSeries.values.flatten()
    if (points.isEmpty()) return
    val colors = mapOf(
        recordTypes[0].storageValue to MaterialTheme.colorScheme.primary,
        recordTypes[1].storageValue to MaterialTheme.colorScheme.tertiary
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (energyType == EnergyType.FUEL) "油价趋势 · ¥/L" else "电价趋势 · ¥/kWh",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(recordTypes.joinToString(" · ") { it.displayName }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PriceTrendCanvas(relevantSeries, colors, averagePriceByGrade(relevantSeries))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(date(points.minBy { it.index }.record.timestamp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date(points.maxBy { it.index }.record.timestamp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun PriceTrendCanvas(
    series: Map<String, List<FuelPriceTrendPoint>>,
    colors: Map<String, Color>,
    averages: Map<String, Double>
) {
    val all = series.values.flatten()
    val values = all.map { it.record.pricePerLiter } + averages.values
    val min = values.min()
    val max = values.max()
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val pointLabelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    Canvas(Modifier.fillMaxWidth().height(166.dp)) {
        val horizontalPadding = 10.dp.toPx()
        val verticalPadding = 24.dp.toPx()
        val priceRange = (max - min).takeIf { it > 0.0 } ?: 1.0
        val lastIndex = all.maxOf { it.index }.coerceAtLeast(1)
        fun point(item: FuelPriceTrendPoint): Offset = Offset(
            x = horizontalPadding + (size.width - horizontalPadding * 2) * item.index / lastIndex,
            y = verticalPadding + (size.height - verticalPadding * 2) * ((max - item.record.pricePerLiter) / priceRange).toFloat()
        )
        fun priceY(price: Double) = verticalPadding + (size.height - verticalPadding * 2) * ((max - price) / priceRange).toFloat()
        averages.entries.sortedBy { it.key }.forEachIndexed { index, (grade, average) ->
            val color = colors.getValue(grade).copy(alpha = 0.55f)
            val y = priceY(average)
            drawLine(
                color = color,
                start = Offset(horizontalPadding, y),
                end = Offset(size.width - horizontalPadding, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            )
            val labelY = (y + if (index % 2 == 0) -18.dp.toPx() else 4.dp.toPx()).coerceIn(0f, size.height - 16.dp.toPx())
            drawText(textMeasurer, AnnotatedString(two(average)), Offset(horizontalPadding, labelY), style = pointLabelStyle.copy(color = color))
        }
        series.forEach { (grade, items) ->
            val color = colors.getValue(grade)
            items.zipWithNext().forEach { (from, to) -> drawLine(color, point(from), point(to), strokeWidth = 2.dp.toPx()) }
            items.forEachIndexed { itemIndex, item ->
                val point = point(item)
                drawCircle(color, radius = 4.dp.toPx(), center = point)
                val neighborPoints = listOfNotNull(items.getOrNull(itemIndex - 1), items.getOrNull(itemIndex + 1)).map(::point)
                val hasLineAbove = neighborPoints.any { it.y < point.y - 4.dp.toPx() }
                val hasLineBelow = neighborPoints.any { it.y > point.y + 4.dp.toPx() }
                val above = when {
                    hasLineAbove && !hasLineBelow -> false
                    hasLineBelow && !hasLineAbove -> true
                    else -> (itemIndex + series.keys.sorted().indexOf(grade).coerceAtLeast(0)) % 2 == 0
                }
                val labelY = (point.y + if (above) -30.dp.toPx() else 14.dp.toPx()).coerceIn(0f, size.height - 16.dp.toPx())
                drawText(textMeasurer, AnnotatedString(two(item.record.pricePerLiter)), Offset(point.x - 13.dp.toPx(), labelY), style = pointLabelStyle)
            }
        }
    }
}

@Composable private fun Vehicles(state: AppState, vm: MainViewModel, back: () -> Unit) {
    var deleteTarget by remember { mutableStateOf<Vehicle?>(null) }
    var deleteRecordCount by remember { mutableIntStateOf(0) }
    var versionHistoryVisible by rememberSaveable { mutableStateOf(false) }
    var versionTapState by remember { mutableStateOf(VersionTapState()) }
    var name by remember { mutableStateOf("") }
    var energyType by remember { mutableStateOf(EnergyType.FUEL) }
    val scope = rememberCoroutineScope()
    SidePageSwipeBack(returnDirection = -1f, back = back) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val estimatedContentHeight = 126.dp + 78.dp * state.vehicles.size
                val topPadding = ((maxHeight - estimatedContentHeight) / 2).coerceAtLeast(0.dp)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, top = topPadding, end = 24.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "addVehicle") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("车辆名称") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                                EnergyTypeSelector(energyType, enabled = true) { energyType = it }
                            }
                            Button({ vm.addVehicle(name, energyType); name = ""; energyType = EnergyType.FUEL }, Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("添加车辆")
                            }
                        }
                    }
                items(state.vehicles, key = { it.id }) { vehicle ->
                    Card(Modifier.fillMaxWidth().clickable { vm.selectVehicle(vehicle.id); back() }, colors = CardDefaults.cardColors(
                        containerColor = if (vehicle.id == state.activeVehicle?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(vehicle.displayNameWithEnergy(), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            if (vehicle.id == state.activeVehicle?.id) Text("当前", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = {
                                scope.launch {
                                    deleteRecordCount = vm.recordCount(vehicle.id)
                                    deleteTarget = vehicle
                                }
                            }) { Icon(Icons.Rounded.DeleteOutline, "删除${vehicle.name}", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                }
            }
            Text(
                "油电猫饼 · v${BuildConfig.VERSION_NAME}",
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 14.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        versionTapState = nextVersionTapState(versionTapState, SystemClock.uptimeMillis())
                        if (versionTapState.reachedVersionHistory()) {
                            versionHistoryVisible = true
                            versionTapState = VersionTapState()
                        }
                    },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }
    }
    deleteTarget?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确定删除「${vehicle.name}」？") },
            text = {
                Text(
                    if (deleteRecordCount > 0) "这将同时删除该车辆的 ${deleteRecordCount} 条${if (vehicle.energyType == EnergyType.FUEL) "加油" else "充电"}记录。\n\n此操作无法撤销。"
                    else "此车辆没有${if (vehicle.energyType == EnergyType.FUEL) "加油" else "充电"}记录。\n\n此操作无法撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteVehicle(vehicle)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
    if (versionHistoryVisible) {
        VersionHistoryDialog { versionHistoryVisible = false }
    }
}

private data class VersionHistory(val version: String, val changes: List<String>)

private val versionHistory = listOf(
    VersionHistory("v2.0.3", listOf("优化平均补能间隔统计，降低偶发漏记对结果的影响")),
    VersionHistory("v2.0.2", listOf("品牌名称更新为油电猫饼，并启用全新应用图标")),
    VersionHistory("v2.0.1", listOf("主页当前车辆名称改为纯文本显示")),
    VersionHistory("v2.0.0", listOf("新增电动车电耗记录与统计，支持车辆能源类型")),
    VersionHistory("v1.4.3", listOf("优化纵向页面返回动画与切换衔接")),
    VersionHistory("v1.4.2", listOf("优化记一笔与历史记录布局及纵向动画")),
    VersionHistory("v1.4.1", listOf("优化版本历史彩蛋与页面布局", "修复历史记录下拉返回和纵向返回动画")),
    VersionHistory("v1.4.0", listOf("全新四方向主页导航", "优化页面滑动进入与返回逻辑", "历史记录支持顶部下拉返回", "精简页面标题与导航界面", "新增加油入口更名为“记一笔”")),
    VersionHistory("v1.3.9", listOf("优化纵向页面导航手势与提示动画")),
    VersionHistory("v1.3.8", listOf("优化主页纵向导航触发距离")),
    VersionHistory("v1.3.7", listOf("优化主页纵向导航手感", "调整车辆统计与车辆管理内容布局")),
    VersionHistory("v1.3.6", listOf("精简主页入口并增加上下滑导航", "优化记录加油与历史记录页面布局")),
    VersionHistory("v1.3.5", listOf("优化侧页滑动提示动画与返回方向")),
    VersionHistory("v1.3.4", listOf("优化页面滑动提示与返回交互", "调整车辆管理和统计页面布局", "优化油价图数字显示")),
    VersionHistory("v1.3.3", listOf("优化主页与侧页滑动交互", "完善车辆统计布局和指标", "优化油价趋势展示")),
    VersionHistory("v1.3.2", listOf("优化主页左右滑动提示", "完善车辆统计指标", "优化最近油价趋势显示")),
    VersionHistory("v1.3.1", listOf("车辆统计新增平均加油间隔", "移除重复的平均油耗统计")),
    VersionHistory("v1.3.0", listOf("新增当前车辆统计与最近油价趋势", "主页左右滑动增加车辆统计和渐进提示", "车辆名称改为弹窗快速编辑")),
    VersionHistory("v1.2.0", listOf("新增主页右滑进入车辆管理", "主页车辆名称支持直接修改当前车辆信息", "优化车辆管理页面入口与导航")),
    VersionHistory("v1.1.3", listOf("优化删除确认页面布局", "将记录信息和操作按钮整合为统一的居中确认模块", "改善页面视觉重心与整体一致性")),
    VersionHistory("v1.1.2", listOf("优化删除确认页面视觉", "删除页面现在跟随 App 明暗主题", "减少删除操作界面与整体 UI 的割裂感")),
    VersionHistory("v1.1.1", listOf("精简加油记录页面，移除多余的“汽油标号”标题", "减少页面纵向占用，优化录入界面显示空间")),
    VersionHistory("v1.1.0", listOf("新增加油记录时可直接选择加油日期", "新增版本历史彩蛋", "保持既有油耗记录和历史计算逻辑")),
    VersionHistory("v1.0.1", listOf("修正首页“距上次”计算", "首页“距上次”改为当前日期距离最近一次加油的天数", "新增车辆页面增加版本号显示")),
    VersionHistory("v1.0.0", listOf("首个正式版本", "支持车辆管理、加油记录和历史记录", "支持全箱法油耗计算与 92/95 历史油价", "支持油价、金额、升数三向换算", "支持记录编辑与删除"))
)

@Composable private fun VersionHistoryDialog(onDismiss: () -> Unit) {
    var allowOutsideDismiss by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(VersionHistoryDismissCooldownMillis)
        allowOutsideDismiss = true
    }
    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = allowOutsideDismiss
        ),
        title = { Text("油电猫饼 · 版本记录") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                versionHistory.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(entry.version, fontWeight = FontWeight.SemiBold)
                        entry.changes.forEach { Text("• $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

private fun number(value: Double) = DecimalFormat("#,##0.##").format(value)
private fun plain(value: Double) = DecimalFormat("0.##").format(value)
private fun one(value: Double) = DecimalFormat("0.0").format(value)
private fun two(value: Double) = DecimalFormat("0.00").format(value)
private fun date(timestamp: Long) = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timestamp))
private fun Vehicle.displayNameWithEnergy() = "$name ${energyType.emoji}"

private fun recordTypeDisplayName(energyType: EnergyType, storageValue: String): String =
    RecordEnergyType.fromStorageValue(energyType, storageValue)?.displayName ?: storageValue
