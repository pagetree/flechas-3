package com.asrys.arrowgame

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.core.content.edit
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import android.util.Patterns
import androidx.compose.runtime.rememberCoroutineScope
import retrofit2.HttpException

private val AppBg = Color(0xFF050A1F)
private const val ONBOARDING_PREFS_NAME = "arrow_onboarding_prefs"
private const val ONBOARDING_DONE_KEY = "onboarding_done"
private const val PLAYER_EMAIL_KEY = "player_email"
private const val PLAYER_NAME_KEY = "player_name"
private const val SHOW_ONBOARDING_EVERY_LOAD_FOR_WORKING = false
private val appTypography = Typography()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = AppBg,
                    surface = AppBg
                ),
                typography = appTypography
            ) {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot(vm: GameViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(ONBOARDING_PREFS_NAME, Context.MODE_PRIVATE) }
    var onboardingStep by rememberSaveable {
        mutableIntStateOf(
            if (SHOW_ONBOARDING_EVERY_LOAD_FOR_WORKING) 1
            else if (prefs.getBoolean(ONBOARDING_DONE_KEY, false)) 0
            else 1
        )
    }

    fun finishOnboarding() {
        if (!SHOW_ONBOARDING_EVERY_LOAD_FOR_WORKING) {
            prefs.edit { putBoolean(ONBOARDING_DONE_KEY, true) }
        }
        onboardingStep = 0
    }

    if (onboardingStep != 0) {
        OnboardingFlow(
            step = onboardingStep,
            onAgreeContinue = { onboardingStep = 2 },
            onJustPlay = { finishOnboarding() },
            onOpenPrivacyPolicy = { onboardingStep = 3 },
            onBackFromPrivacyPolicy = { onboardingStep = 1 },
            onOpenPlayerSignup = { onboardingStep = 4 },
            onBackFromPlayerSignup = { onboardingStep = 2 }
        )
        return
    }

    var inGame by rememberSaveable { mutableStateOf(false) }
    var showMenuSignup by rememberSaveable { mutableStateOf(false) }
    val state by vm.state.collectAsState()
    var displayedLevelInMenu by rememberSaveable { mutableStateOf(state.puzzleNumber) }

    if (showMenuSignup) {
        PlayerSignupScreen(
            onBack = { showMenuSignup = false },
            onJustPlay = { showMenuSignup = false },
            onSignupSuccess = { showMenuSignup = false }
        )
        return
    }

    if (!inGame) {
        MainMenu(
            levelNumber = if (displayedLevelInMenu == 1) state.puzzleNumber else displayedLevelInMenu,
            isLoading = state.isLoadingProgress,
            onPlay = {
                vm.startRandomPuzzle()
                inGame = true
            },
            onCreatePlayerId = { showMenuSignup = true }
        )

        LaunchedEffect(state.isLoadingProgress) {
            if (!state.isLoadingProgress) {
                displayedLevelInMenu = state.puzzleNumber
            }
        }

        LaunchedEffect(state.puzzleNumber) {
            // Only perform the delayed slide animation if we're NOT in the initial loading phase
            if (!state.isLoadingProgress && displayedLevelInMenu != state.puzzleNumber) {
                delay(800L) 
                displayedLevelInMenu = state.puzzleNumber
            }
        }
        return
    }
    GameScreen(vm = vm, onReturnToMenu = { inGame = false })
}

@Composable
private fun OnboardingFlow(
    step: Int,
    onAgreeContinue: () -> Unit,
    onJustPlay: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onBackFromPrivacyPolicy: () -> Unit,
    onOpenPlayerSignup: () -> Unit,
    onBackFromPlayerSignup: () -> Unit
) {
    when (step) {
        1 -> OnboardingWelcomeScreen(
            onAgreeContinue = onAgreeContinue,
            onPrivacyClick = onOpenPrivacyPolicy
        )
        2 -> OnboardingPlayerIdScreen(
            onJustPlay = onJustPlay,
            onCreatePlayerId = onOpenPlayerSignup
        )
        3 -> PrivacyPolicyScreen(onBack = onBackFromPrivacyPolicy)
        4 -> PlayerSignupScreen(
            onJustPlay = onJustPlay,
            onSignupSuccess = onJustPlay,
            onBack = onBackFromPlayerSignup
        )
    }
}

@Composable
private fun OnboardingWelcomeScreen(
    onAgreeContinue: () -> Unit,
    onPrivacyClick: () -> Unit
) {
    OnboardingScreen(
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_welcome_description),
        primaryButtonText = stringResource(R.string.onboarding_agree_continue),
        linkText = stringResource(R.string.onboarding_read_privacy_policy),
        onPrimaryButtonClick = onAgreeContinue,
        onLinkClick = onPrivacyClick
    )
}

@Composable
private fun OnboardingPlayerIdScreen(
    onJustPlay: () -> Unit,
    onCreatePlayerId: () -> Unit
) {
    OnboardingScreen(
        title = stringResource(R.string.onboarding_playerid_title),
        description = stringResource(R.string.onboarding_playerid_description),
        primaryButtonText = stringResource(R.string.onboarding_create_playerid),
        linkText = stringResource(R.string.onboarding_just_play),
        onPrimaryButtonClick = onCreatePlayerId,
        onLinkClick = onJustPlay
    )
}

@Composable
private fun PlayerSignupScreen(
    onBack: () -> Unit,
    onJustPlay: () -> Unit,
    onSignupSuccess: () -> Unit
) {
    val api = remember { GameApi.create() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val verticalPadding = 56.dp
    val muted = Color.White.copy(alpha = 0.78f)

    var email by rememberSaveable { mutableStateOf("") }
    var playerName by rememberSaveable { mutableStateOf("") }

    var isLoading by rememberSaveable { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var playerNameError by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    fun sanitizePlayerName(input: String): String {
        // Letters + numbers only, max 12 chars.
        val filtered = input.replace(Regex("[^A-Za-z0-9]"), "")
        return if (filtered.length > 12) filtered.substring(0, 12) else filtered
    }

    fun isEmailValid(value: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(value).matches()
    }

    fun isPlayerNameValid(value: String): Boolean {
        return Regex("^[A-Za-z0-9]{1,12}$").matches(value)
    }

    fun submit() {
        emailError = null
        playerNameError = null
        submitError = null

        val trimmedEmail = email.trim()
        val trimmedName = playerName.trim()

        if (!isEmailValid(trimmedEmail)) {
            emailError = "onboarding_error_invalid_email"
            return
        }

        if (!isPlayerNameValid(trimmedName)) {
            playerNameError = "onboarding_error_invalid_player_name"
            return
        }

        isLoading = true

        scope.launch {
            try {
                val check = api.checkPlayerEmail(CheckPlayerEmailRequest(trimmedEmail))
                if (check.exists) {
                    emailError = "onboarding_error_email_exists"
                    return@launch
                }

                val deviceId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: ""
                val created = api.createPlayer(
                    CreatePlayerRequest(
                        email = trimmedEmail,
                        player_name = trimmedName,
                        device_id = deviceId.ifBlank { null }
                    )
                )
                if (created.success && !created.exists) {
                    context.getSharedPreferences(ONBOARDING_PREFS_NAME, Context.MODE_PRIVATE).edit {
                        putString(PLAYER_EMAIL_KEY, trimmedEmail.lowercase())
                        putString(PLAYER_NAME_KEY, trimmedName)
                    }
                    onSignupSuccess()
                } else if (created.exists) {
                    emailError = "onboarding_error_email_exists"
                } else {
                    submitError = "onboarding_error_create_player_failed"
                }
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> emailError = "onboarding_error_email_exists"
                    400 -> submitError = "onboarding_error_create_player_failed"
                    else -> submitError = "onboarding_error_network"
                }
            } catch (e: Exception) {
                submitError = "onboarding_error_network"
            } finally {
                isLoading = false
            }
        }
    }

    fun closeWithAnimation() {
        scope.launch {
            showSheet = false
            delay(260L)
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        showSheet = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
        // Logo centered at the top.
        Image(
            painter = painterResource(id = R.drawable.arrows_logo),
            contentDescription = stringResource(R.string.onboarding_logo_content_description),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = verticalPadding)
                .fillMaxWidth(0.45f)
        )

        // Bottom sheet style: slides up, keeps top gap.
        AnimatedVisibility(
            visible = showSheet,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 350),
                initialOffsetY = { fullHeight -> fullHeight }
            ) + fadeIn(animationSpec = tween(durationMillis = 350)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 260),
                targetOffsetY = { fullHeight -> fullHeight }
            ) + fadeOut(animationSpec = tween(durationMillis = 220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C1635), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.onboarding_close),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.End)
                    .size(28.dp)
                    .clickable(onClick = ::closeWithAnimation)
            )

            Text(
                text = stringResource(R.string.onboarding_signup_body),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 10.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            TextField(
                value = email,
                onValueChange = { email = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        stringResource(R.string.onboarding_email_label),
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                isError = emailError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                colors = TextFieldDefaults.colors(
                    focusedLabelColor = Color.LightGray,
                    unfocusedLabelColor = Color.LightGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color(0xFF2E5BFF),
                    unfocusedIndicatorColor = Color.LightGray.copy(alpha = 0.4f),
                    errorIndicatorColor = Color(0xFFFF5A5F),
                    errorCursorColor = Color(0xFFFF5A5F)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                placeholder = { Text(stringResource(R.string.onboarding_email_placeholder)) }
            )

            if (emailError != null) {
                Text(
                    text = stringResource(
                        when (emailError) {
                            "onboarding_error_invalid_email" -> R.string.onboarding_error_invalid_email
                            "onboarding_error_email_exists" -> R.string.onboarding_error_email_exists
                            else -> R.string.onboarding_error_network
                        }
                    ),
                    color = Color(0xFFFF5A5F),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start).padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            TextField(
                value = playerName,
                onValueChange = { playerName = sanitizePlayerName(it) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        stringResource(R.string.onboarding_player_name_label),
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                isError = playerNameError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                colors = TextFieldDefaults.colors(
                    focusedLabelColor = Color.LightGray,
                    unfocusedLabelColor = Color.LightGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color(0xFF2E5BFF),
                    unfocusedIndicatorColor = Color.LightGray.copy(alpha = 0.4f),
                    errorIndicatorColor = Color(0xFFFF5A5F),
                    errorCursorColor = Color(0xFFFF5A5F)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                placeholder = { Text(stringResource(R.string.onboarding_player_name_placeholder)) }
            )

            if (playerNameError != null) {
                Text(
                    text = stringResource(R.string.onboarding_error_invalid_player_name),
                    color = Color(0xFFFF5A5F),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start).padding(top = 6.dp)
                )
            }

            if (submitError != null) {
                Text(
                    text = stringResource(
                        when (submitError) {
                            "onboarding_error_create_player_failed" -> R.string.onboarding_error_create_player_failed
                            else -> R.string.onboarding_error_network
                        }
                    ),
                    color = Color(0xFFFF5A5F),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start).padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = { if (!isLoading) submit() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E5BFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(64.dp),
                enabled = !isLoading
            ) {
                Text(
                    text = if (isLoading) stringResource(R.string.onboarding_creating_playerid) else stringResource(R.string.onboarding_create_playerid),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val muted = Color.White.copy(alpha = 0.78f)
    val collectBullets = stringArrayResource(R.array.privacy_collect_bullets).toList()
    val useBullets = stringArrayResource(R.array.privacy_use_bullets).toList()
    val choicesBullets = stringArrayResource(R.array.privacy_choices_bullets).toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2A52)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(text = stringResource(R.string.privacy_back), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.privacy_title),
                color = Color.White,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.privacy_subtitle),
                color = muted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(42.dp))

        PrivacySection(
            title = stringResource(R.string.privacy_collect_title),
            body = stringResource(R.string.privacy_collect_body),
            bullets = collectBullets,
            muted = muted
        )

        Spacer(modifier = Modifier.height(42.dp))

        PrivacySection(
            title = stringResource(R.string.privacy_use_title),
            body = stringResource(R.string.privacy_use_body),
            bullets = useBullets,
            muted = muted
        )

        Spacer(modifier = Modifier.height(42.dp))

        PrivacySection(
            title = stringResource(R.string.privacy_choices_title),
            body = stringResource(R.string.privacy_choices_body),
            bullets = choicesBullets,
            muted = muted
        )

        Spacer(modifier = Modifier.height(52.dp))
    }
}

@Composable
private fun PrivacySection(
    title: String,
    body: String,
    bullets: List<String>,
    muted: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = body,
            color = muted,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 14.dp)
        )

        Column(modifier = Modifier.padding(top = 22.dp)) {
            bullets.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = item,
                        color = muted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyTable(
    cardBorder: Color,
    muted: Color
) {
    val headerBg = Color(0xFF16224A)
    val border = cardBorder
    val c0 = 0.36f
    val c1 = 0.32f
    val c2 = 0.32f

    @Composable
    fun TableRow(
        bg: Color,
        cells: List<String>,
        isHeader: Boolean
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(horizontal = 12.dp, vertical = if (isHeader) 10.dp else 12.dp)
        ) {
            Text(
                text = cells[0],
                color = muted,
                fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(c0)
            )
            Text(
                text = cells[1],
                color = muted,
                fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(c1)
            )
            Text(
                text = cells[2],
                color = muted,
                fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(c2)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent, RoundedCornerShape(18.dp))
            .border(1.dp, border, RoundedCornerShape(18.dp))
    ) {
        TableRow(
            bg = headerBg,
            cells = listOf(
                stringResource(R.string.privacy_table_header_data_category),
                stringResource(R.string.privacy_table_header_example),
                stringResource(R.string.privacy_table_header_purpose)
            ),
            isHeader = true
        )
        TableRow(
            bg = Color.Transparent,
            cells = listOf(
                stringResource(R.string.privacy_table_row_progress),
                stringResource(R.string.privacy_table_row_progress_example),
                stringResource(R.string.privacy_table_row_progress_purpose)
            ),
            isHeader = false
        )
        TableRow(
            bg = Color.Transparent,
            cells = listOf(
                stringResource(R.string.privacy_table_row_device),
                stringResource(R.string.privacy_table_row_device_example),
                stringResource(R.string.privacy_table_row_device_purpose)
            ),
            isHeader = false
        )
        TableRow(
            bg = Color.Transparent,
            cells = listOf(
                stringResource(R.string.privacy_table_row_stats),
                stringResource(R.string.privacy_table_row_stats_example),
                stringResource(R.string.privacy_table_row_stats_purpose)
            ),
            isHeader = false
        )
    }
}

@Composable
private fun OnboardingScreen(
    title: String,
    description: String,
    primaryButtonText: String,
    linkText: String,
    onPrimaryButtonClick: () -> Unit,
    onLinkClick: () -> Unit
) {
    val verticalPadding = 56.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
    ) {
        // Logo stays lower than before (same top margin as the bottom block margin).
        Image(
            painter = painterResource(id = R.drawable.arrows_logo),
            contentDescription = stringResource(R.string.onboarding_logo_content_description),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = verticalPadding)
                .fillMaxWidth(0.45f)
        )

        // Button + link are always pinned to the bottom with a bottom margin.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 36.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )

            Button(
                onClick = onPrimaryButtonClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E5BFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(top = 22.dp)
                    .fillMaxWidth(0.8f)
                    .height(64.dp)
            ) {
                Text(
                    primaryButtonText,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = linkText,
                color = Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                        .padding(top = 22.dp)
                    .clickable(onClick = onLinkClick)
            )
        }
    }
}

@Composable
private fun MainMenu(
    levelNumber: Int,
    isLoading: Boolean,
    onPlay: () -> Unit,
    onCreatePlayerId: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(ONBOARDING_PREFS_NAME, Context.MODE_PRIVATE) }
    val email = prefs.getString(PLAYER_EMAIL_KEY, null)
    val playerName = prefs.getString(PLAYER_NAME_KEY, "")
    val hasPlayerId = !email.isNullOrBlank() && !playerName.isNullOrBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(20.dp)
    ) {
        if (hasPlayerId) {
            Text(
                text = buildAnnotatedString {
                    val fullText = stringResource(R.string.menu_greeting, playerName)
                    val startIndex = fullText.indexOf(playerName)
                    append(fullText)
                    if (startIndex >= 0) {
                        addStyle(
                            style = SpanStyle(fontWeight = FontWeight.ExtraBold),
                            start = startIndex,
                            end = startIndex + playerName.length
                        )
                    }
                },
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
            )
        }
        
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.arrows_logo),
                contentDescription = stringResource(R.string.menu_title),
                modifier = Modifier.fillMaxWidth(0.78f)
            )
            val levelText = stringResource(R.string.level_label, levelNumber)
            val parts = levelText.split(levelNumber.toString())
            val prefix = parts.getOrNull(0) ?: ""
            val suffix = parts.getOrNull(1) ?: ""

            if (isLoading) {
                val infiniteTransition = rememberInfiniteTransition(label = "loading")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                Text(
                    text = stringResource(R.string.loading_level),
                    color = Color.LightGray.copy(alpha = alpha),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (prefix.isNotEmpty()) {
                        Text(
                            text = prefix,
                            color = Color.LightGray,
                            fontSize = 18.sp
                        )
                    }
                    AnimatedContent(
                        targetState = levelNumber,
                        transitionSpec = {
                            (slideInVertically { height -> -height } + fadeIn())
                                .togetherWith(slideOutVertically { height -> height } + fadeOut())
                        },
                        label = "LevelNumberChange"
                    ) { targetLevel ->
                        Text(
                            text = targetLevel.toString(),
                            color = Color.LightGray,
                            fontSize = 18.sp
                        )
                    }
                    if (suffix.isNotEmpty()) {
                        Text(
                            text = suffix,
                            color = Color.LightGray,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onPlay,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E5BFF),
                    disabledContainerColor = Color(0xFF2E5BFF).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(64.dp)
            ) {
                Text(stringResource(R.string.play_button), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            if (!hasPlayerId) {
                Text(
                    text = stringResource(R.string.onboarding_create_playerid),
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .clickable(onClick = onCreatePlayerId)
                )
            }
        }
    }
}

data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Color,
    val radius: Float
)

@Composable
private fun SuccessScreen(timeSeconds: Int, onAnimationEnd: () -> Unit) {
    val words = stringArrayResource(R.array.success_words).toList()
    val word = remember { words.random() }
    
    var particles by remember { mutableStateOf<List<ConfettiParticle>>(emptyList()) }
    
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(AppBg)
    ) {
        val density = LocalDensity.current.density
        val screenWidth = this.maxWidth.value * density
        val screenHeight = this.maxHeight.value * density
        
        LaunchedEffect(Unit) {
            val colors = listOf(
                Color(0xFF90A4AE), // Muted Blue-Grey
                Color(0xFFAED581), // Muted Light Green
                Color(0xFF7986CB), // Muted Indigo
                Color(0xFFFFB74D), // Muted Orange
                Color(0xFFBA68C8), // Muted Purple
                Color(0xFF4DD0E1)  // Muted Cyan
            )
            var currentParticles = List(150) {
                ConfettiParticle(
                    x = screenWidth * kotlin.random.Random.nextFloat(),
                    y = screenHeight,
                    vx = kotlin.random.Random.nextFloat() * 600 - 300,
                    vy = -(kotlin.random.Random.nextFloat() * screenHeight * 0.45f + screenHeight * 1.0f),
                    color = colors.random(),
                    radius = kotlin.random.Random.nextFloat() * 15f + 10f
                )
            }
            particles = currentParticles
            
            val startTime = System.nanoTime()
            var lastTime = startTime
            while (true) {
                androidx.compose.runtime.withFrameNanos { time ->
                    val dt = (time - lastTime) / 1_000_000_000f
                    lastTime = time
                    
                    currentParticles = currentParticles.map { p ->
                        p.copy(
                            x = p.x + p.vx * dt,
                            y = p.y + p.vy * dt,
                            vy = p.vy + screenHeight * 1.5f * dt
                        )
                    }
                    particles = currentParticles
                }
                if (System.nanoTime() - startTime > 2_500_000_000L) {
                    break
                }
            }
            onAnimationEnd()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { p ->
                drawCircle(
                    color = p.color,
                    radius = p.radius,
                    center = Offset(p.x, p.y)
                )
            }
        }
        
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = word,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatTime(timeSeconds),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun FailScreen(onRetry: () -> Unit) {
    val words = stringArrayResource(R.array.failure_words).toList()
    val word = remember { words.random() }
    var timeLeft by remember { mutableIntStateOf(10) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000L)
            timeLeft--
        }
        onRetry()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(AppBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = word,
                color = Color.Red,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.out_of_lives),
                color = Color.Red.copy(alpha = 0.7f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = stringResource(R.string.try_again_in),
                color = Color.LightGray,
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "$timeLeft",
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun GameScreen(vm: GameViewModel, onReturnToMenu: () -> Unit) {
    val state by vm.state.collectAsState()
    val level = state.puzzle ?: return

    var showSuccessScreen by remember(level.id) { mutableStateOf(false) }
    var waveProgress by remember(level.id) { mutableFloatStateOf(0f) }
    var timerSeconds by remember(level.id) { mutableIntStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    val soundIds = remember(soundPool) {
        listOf(
            soundPool.load(context, R.raw.tap1, 1),
            soundPool.load(context, R.raw.tap2, 1),
            soundPool.load(context, R.raw.tap3, 1),
            soundPool.load(context, R.raw.tap4, 1),
            soundPool.load(context, R.raw.tap5, 1)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            soundPool.release()
        }
    }

    LaunchedEffect(level.id, state.isLevelComplete, state.isGameOver) {
        if (!state.isLevelComplete && !state.isGameOver) {
            while (true) {
                delay(1000L)
                timerSeconds++
            }
        }
    }

    var collisionGlowAlpha by remember(level.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(state.collisionTrigger) {
        if (state.collisionTrigger > 0) {
            androidx.compose.animation.core.Animatable(0.6f).animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 400)
            ) {
                collisionGlowAlpha = this.value
            }
        }
    }

    if (showSuccessScreen) {
        SuccessScreen(
            timeSeconds = timerSeconds,
            onAnimationEnd = {
                vm.submitStats(timerSeconds)
                vm.nextPuzzle()
                onReturnToMenu()
            }
        )
        return
    }

    if (state.isGameOver) {
        FailScreen(onRetry = { vm.resumeWithOneLife() })
        return
    }

    var scale by remember(level.id) { mutableFloatStateOf(1.0f) }
    var offset by remember(level.id) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(state.isLevelComplete) {
        if (state.isLevelComplete) {
            val startScale = scale
            val startOffset = offset
            
            if (startScale > 1.0f || startOffset != Offset.Zero) {
                androidx.compose.animation.core.Animatable(0f).animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 500)
                ) {
                    val t = this.value
                    scale = startScale + (1.0f - startScale) * t
                    offset = androidx.compose.ui.geometry.lerp(startOffset, Offset.Zero, t)
                }
            }

            androidx.compose.animation.core.Animatable(0f).animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000)
            ) {
                waveProgress = this.value
            }
            delay(100L)
            showSuccessScreen = true
        }
    }



    LaunchedEffect(level.id) {
        // Zoom in on puzzle load — more zoom for bigger boards
        val targetScale = when {
            level.width >= 22 -> 2.2f  // Nightmare
            level.width >= 20 -> 1.9f  // Hard
            else              -> 1.6f  // Normal / Easy
        }
        scale = 1.0f
        offset = Offset.Zero
        androidx.compose.animation.core.Animatable(1.0f).animateTo(
            targetValue = targetScale,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 1200)
        ) {
            scale = this.value
        }
    }

    // Minimum zoom = 80% of the current scale so the player can't zoom out to a tiny board
    val minScale = when {
        level.width >= 22 -> 1.6f
        level.width >= 20 -> 1.3f
        else              -> 1.0f
    }

    val density = LocalDensity.current
    val paddingPx = with(density) { 24.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBg)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, 5.0f)
                        // The box width is screen width minus left and right padding
                        val boxSize = size.width.toFloat() - paddingPx * 2f
                        // By calculating maxOffset from the box size, the visual edge of the scaled 
                        // content will perfectly align with the padding boundary when fully panned!
                        val maxOffsetX = (boxSize * newScale - boxSize) / 2f
                        val maxOffsetY = (boxSize * newScale - boxSize) / 2f
                        
                        scale = newScale
                        val limitX = maxOf(0f, maxOffsetX)
                        val limitY = maxOf(0f, maxOffsetY)
                        
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-limitX, limitX),
                            y = (offset.y + pan.y).coerceIn(-limitY, limitY)
                        )
                    }
                }
        ) {
            // Lives Hearts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 64.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color(0xFF1E2A52),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${state.remaining.size}",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(3) { index ->
                            val isAlive = index < state.lives
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = if (isAlive) Color.Red else Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp).padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // Reserved for future HUD content.
                }
            }

            Text(
                text = formatTime(timerSeconds),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                ) {
                    ArrowBoard(
                        level = level,
                        state = state,
                        waveProgress = waveProgress,
                        onArrowTap = { id -> 
                            if (soundIds.isNotEmpty()) {
                                val sid = soundIds.random()
                                soundPool.play(sid, 1f, 1f, 1, 0, 1f)
                            }
                            vm.onArrowTap(id, scale) 
                        }
                    )
                }
            }
        }

        // Edge glows for collision
        if (collisionGlowAlpha > 0f) {
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Red.copy(alpha = collisionGlowAlpha), Color.Transparent))))
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Red.copy(alpha = collisionGlowAlpha)))))
            Box(modifier = Modifier.fillMaxHeight().width(40.dp).align(Alignment.CenterStart)
                .background(Brush.horizontalGradient(listOf(Color.Red.copy(alpha = collisionGlowAlpha), Color.Transparent))))
            Box(modifier = Modifier.fillMaxHeight().width(40.dp).align(Alignment.CenterEnd)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Red.copy(alpha = collisionGlowAlpha)))))
        }
    }
}

@Composable
private fun ArrowBoard(
    level: LevelMask,
    state: GameState,
    waveProgress: Float,
    onArrowTap: (Int) -> Unit
) {
    val currentState by androidx.compose.runtime.rememberUpdatedState(state)
    val currentOnArrowTap by androidx.compose.runtime.rememberUpdatedState(onArrowTap)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(level.id) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val st = currentState
                        if (st.isGameOver || st.isLevelComplete) return@detectTapGestures
                        val cw = size.width / level.width.toFloat()
                        val ch = size.height / level.height.toFloat()
                        val tapCX = tapOffset.x / cw  // tap position in cell-space
                        val tapCY = tapOffset.y / ch
                        val searchRadius = 1.2f       // cells — generous hit area
                        var bestArrow: ArrowPiece? = null
                        var bestDist = Float.MAX_VALUE
                        for (arrow in st.remaining) {
                            val cells = if (arrow.path.isNotEmpty()) arrow.path else listOf(arrow.start)
                            for (cell in cells) {
                                val dx = cell.x + 0.5f - tapCX
                                val dy = cell.y + 0.5f - tapCY
                                val distSq = dx * dx + dy * dy
                                if (distSq < searchRadius * searchRadius && distSq < bestDist) {
                                    bestDist = distSq
                                    bestArrow = arrow
                                }
                            }
                        }
                        bestArrow?.let { currentOnArrowTap(it.id) }
                    }
                )
            }
    ) {
        val cw = size.width / level.width
        val ch = size.height / level.height
        val stroke = min(cw, ch) * 0.17f
        val dotRadius = min(cw, ch) * 0.09f
        val clearedDotColor = Color(0xFF9FB4FF).copy(alpha = 0.16f)
        val movingById = state.movingArrows.associateBy { it.id }
        val allArrowCells = level.arrows.flatMap { it.occupiedCells() }.toSet()
        val remainingCells = state.remaining.flatMap { it.occupiedCells() }.toSet()
        val movingVacatedCells = state.movingArrows.flatMap { moving ->
            val arrow = level.arrows.firstOrNull { it.id == moving.id } ?: return@flatMap emptyList()
            val cells = arrow.occupiedCells()
            val vacatedCount = moving.progressCells.toInt().coerceIn(0, cells.size)
            cells.take(vacatedCount)
        }.toSet()
        val clearedCells = (allArrowCells - remainingCells) + movingVacatedCells

        val waveHeight = size.height * 0.3f
        val waveY = size.height + waveHeight - (size.height + waveHeight * 2) * waveProgress

        for (cell in clearedCells) {
            val cx = cell.x * cw + cw / 2f
            val cy = cell.y * ch + ch / 2f
            
            var currentRadius = dotRadius
            var currentColor = clearedDotColor
            
            if (waveProgress > 0f && waveProgress < 1f) {
                val dist = kotlin.math.abs(cy - waveY)
                if (dist < waveHeight) {
                    val intensity = 1f - (dist / waveHeight)
                    val smoothIntensity = kotlin.math.sin(intensity * kotlin.math.PI / 2.0).toFloat()
                    
                    currentRadius = dotRadius * (1f + 1.5f * smoothIntensity)
                    val alpha = (0.16f + 0.84f * smoothIntensity).coerceIn(0f, 1f)
                    val darkBluePulse = Color(0xFF1E3A8A)
                    currentColor = lerp(clearedDotColor, darkBluePulse, smoothIntensity).copy(alpha = alpha)
                }
            }

            drawCircle(
                color = currentColor,
                radius = currentRadius,
                center = Offset(cx, cy)
            )
        }

        for (arrow in state.remaining) {
            val moving = movingById[arrow.id]
            // Color based on ABSOLUTE cells traveled (not ratio of full exit distance).
            // Arrow hits full bold green after just 2 cells — feels instant and satisfying.
            val cellsTraveled = moving?.progressCells ?: 0f
            val fastRatio = (cellsTraveled / 2.0f).coerceIn(0f, 1f)
            val boldGreen = Color(0xFF00C853)
            val movingColor = lerp(Color.White, boldGreen, fastRatio)
            val color = when {
                state.lastBlockedArrowId == arrow.id -> Color(0xFFE53935)
                moving != null -> {
                    if (moving.isObstructed) Color(0xFFE53935) else movingColor
                }
                else -> Color.White
            }
            drawArrowPath(
                arrow = arrow,
                cellW = cw,
                cellH = ch,
                color = color,
                stroke = stroke,
                progressCells = moving?.progressCells ?: 0f
            )
        }
    }
}

private fun ArrowPiece.occupiedCells(): List<Cell> {
    return path.ifEmpty { listOf(start) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowPath(
    arrow: ArrowPiece,
    cellW: Float,
    cellH: Float,
    color: Color,
    stroke: Float,
    progressCells: Float = 0f
) {
    val base = min(cellW, cellH)
    val headLen = base * 0.38f
    val headHalfWidth = base * 0.24f
    val isMoving = progressCells > 0f

    val cells = arrow.path.ifEmpty { listOf(arrow.start) }
    val points = computeRopeFollowPoints(cells, arrow.direction, progressCells, cellW, cellH)
    if (points.isEmpty()) return

    val fallbackDir = when (arrow.direction) {
        Direction.UP -> 0f to -1f
        Direction.RIGHT -> 1f to 0f
        Direction.DOWN -> 0f to 1f
        Direction.LEFT -> -1f to 0f
    }

    if (points.size == 1) {
        val end = points.last()
        val (dx, dy) = fallbackDir
        val tailStart = Offset(
            x = end.x - dx * (base * 0.42f),
            y = end.y - dy * (base * 0.42f)
        )
        val shaftEnd = Offset(
            x = end.x - dx * (headLen * 0.70f),
            y = end.y - dy * (headLen * 0.70f)
        )
        drawLine(
            color = color,
            start = tailStart,
            end = shaftEnd,
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }

    if (points.size > 1) {
        val bodyPath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.lastIndex) {
                lineTo(points[i].x, points[i].y)
            }
            val beforeHead = points[points.lastIndex - 1]
            val tip = points.last()
            val vx = tip.x - beforeHead.x
            val vy = tip.y - beforeHead.y
            val mag = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(0.0001f)
            val ux = vx / mag
            val uy = vy / mag
            val retreat = min(headLen * 0.70f, mag * 0.45f)
            val shaftEnd = Offset(
                x = tip.x - ux * retreat,
                y = tip.y - uy * retreat
            )
            lineTo(shaftEnd.x, shaftEnd.y)
        }
        drawPath(
            path = bodyPath,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }

    val end = points.last()
    val (dx, dy) = if (isMoving) {
        // Keep head orientation stable while moving.
        fallbackDir
    } else if (points.size > 1) {
        // Use a slightly longer look-back while moving to reduce direction jitter at corners.
        val lookBack = 1
        val prev = points[(points.lastIndex - lookBack).coerceAtLeast(0)]
        val vx = end.x - prev.x
        val vy = end.y - prev.y
        val mag = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(0.0001f)
        (vx / mag) to (vy / mag)
    } else {
        fallbackDir
    }
    val nx = -dy
    val ny = dx
    val left = Offset(
        x = end.x - dx * headLen + nx * headHalfWidth,
        y = end.y - dy * headLen + ny * headHalfWidth
    )
    val right = Offset(
        x = end.x - dx * headLen - nx * headHalfWidth,
        y = end.y - dy * headLen - ny * headHalfWidth
    )
    val triangle = Path().apply {
        moveTo(end.x, end.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path = triangle, color = color, style = Stroke(width = stroke * 0.3f))
    drawPath(path = triangle, color = color)
}

private fun computeRopeFollowPoints(
    cells: List<Cell>,
    direction: Direction,
    progressCells: Float,
    cellW: Float,
    cellH: Float
): List<Offset> {
    if (cells.isEmpty()) return emptyList()
    val tip = cells.last()

    fun trackCell(i: Int): Cell {
        return if (i < cells.size) {
            cells[i]
        } else {
            val extra = i - (cells.size - 1)
            Cell(
                x = tip.x + direction.dx * extra,
                y = tip.y + direction.dy * extra
            )
        }
    }

    fun pointAt(t: Float): Offset {
        val i = kotlin.math.floor(t).toInt()
        val frac = t - i
        val a = trackCell(i)
        val b = trackCell(i + 1)
        val x = (a.x + (b.x - a.x) * frac) * cellW + cellW / 2f
        val y = (a.y + (b.y - a.y) * frac) * cellH + cellH / 2f
        return Offset(x, y)
    }

    val points = mutableListOf<Offset>()
    val tHead = (cells.size - 1).coerceAtLeast(0).toFloat() + progressCells

    points.add(pointAt(progressCells))

    val firstInt = kotlin.math.floor(progressCells).toInt() + 1
    val lastInt = kotlin.math.ceil(tHead).toInt() - 1

    for (i in firstInt..lastInt) {
        val c = trackCell(i)
        val p = Offset(c.x * cellW + cellW / 2f, c.y * cellH + cellH / 2f)
        val lastP = points.last()
        val distSq = (p.x - lastP.x) * (p.x - lastP.x) + (p.y - lastP.y) * (p.y - lastP.y)
        if (distSq > 0.1f) {
            points.add(p)
        }
    }

    if (tHead > progressCells) {
        val pHead = pointAt(tHead)
        val lastP = points.last()
        val distSq = (pHead.x - lastP.x) * (pHead.x - lastP.x) + (pHead.y - lastP.y) * (pHead.y - lastP.y)
        if (distSq > 0.1f) {
            points.add(pHead)
        }
    }

    return points
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}
