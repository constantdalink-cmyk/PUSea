package org.example.project

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==============================================================================
// UI：Key 子画布
// ==============================================================================

@Composable
fun SubCanvasKey(
    visible: Boolean,
    onClose: () -> Unit,
    pixelFont: FontFamily
) {
    val savedKeys by KeyConfigStore.configs.collectAsState()
    val activeIds by KeyConfigStore.activeIds.collectAsState()

    var showModal by remember { mutableStateOf(false) }
    var modalStep by remember { mutableStateOf(1) }
    var editingItem by remember { mutableStateOf<SavedKeyConfig?>(null) }

    var selectedProvider by remember { mutableStateOf("OpenAI") }
    var nameField by remember { mutableStateOf("") }
    var openAiKey by remember { mutableStateOf("") }
    var openAiBaseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var openAiModel by remember { mutableStateOf("gpt-4o-mini") }
    var claudeKey by remember { mutableStateOf("") }
    var claudeBaseUrl by remember { mutableStateOf("https://api.anthropic.com") }
    var claudeModel by remember { mutableStateOf("claude-3-5-sonnet-20240620") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun resetFormForCreate() {
        editingItem = null
        selectedProvider = "OpenAI"
        nameField = ""
        openAiKey = ""
        openAiBaseUrl = "https://api.openai.com/v1"
        openAiModel = "gpt-4o-mini"
        claudeKey = ""
        claudeBaseUrl = "https://api.anthropic.com"
        claudeModel = "claude-3-5-sonnet-20240620"
        validationError = null
        modalStep = 1
    }

    fun fillFormForEdit(item: SavedKeyConfig) {
        editingItem = item
        selectedProvider = item.provider
        nameField = item.name
        if (item.provider == "OpenAI") {
            openAiKey = item.apiKey
            openAiBaseUrl = normalizeBaseUrl(item.baseUrl)
            openAiModel = item.model
        } else {
            claudeKey = item.apiKey
            claudeBaseUrl = normalizeBaseUrl(item.baseUrl)
            claudeModel = item.model
        }
        validationError = null
        modalStep = 2
    }

    SlideDownContainer(
        visible = visible,
        title = "Key",
        onClose = onClose,
        pixelFont = pixelFont,
        showDefaultTitle = false
    ) {
        Text(
            text = uiText(UiText.Key),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp),
            color = Color.Black,
            fontSize = 20.sp,
            fontFamily = pixelFont,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 44.dp)
                .height(1.dp)
                .background(Color(0xFFBDBDBD))
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 12.dp, end = 12.dp, top = 56.dp, bottom = 100.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AddButton(pixelFont = pixelFont) {
                resetFormForCreate()
                showModal = true
            }

            Spacer(modifier = Modifier.height(12.dp))

            savedKeys.forEach { item ->
                KeyConfigCard(
                    item = item,
                    isActive = item.id in activeIds,
                    pixelFont = pixelFont,
                    onEdit = {
                        fillFormForEdit(item)
                        showModal = true
                    },
                    onDelete = { KeyConfigStore.delete(item.id) },
                    onSetActive = { KeyConfigStore.toggleActive(item.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (showModal) {
            KeyModal(
                pixelFont = pixelFont,
                modalStep = modalStep,
                selectedProvider = selectedProvider,
                onProviderChange = { selectedProvider = it },
                nameField = nameField,
                onNameChange = { nameField = it },
                openAiKey = openAiKey,
                onOpenAiKeyChange = { openAiKey = it },
                openAiBaseUrl = openAiBaseUrl,
                onOpenAiBaseUrlChange = { openAiBaseUrl = it },
                openAiModel = openAiModel,
                onOpenAiModelChange = { openAiModel = it },
                claudeKey = claudeKey,
                onClaudeKeyChange = { claudeKey = it },
                claudeBaseUrl = claudeBaseUrl,
                onClaudeBaseUrlChange = { claudeBaseUrl = it },
                claudeModel = claudeModel,
                onClaudeModelChange = { claudeModel = it },
                validationError = validationError,
                isEditing = editingItem != null,
                onDismiss = { showModal = false },
                onStepChange = { modalStep = it },
                onConfirm = {
                    val key = if (selectedProvider == "OpenAI") openAiKey else claudeKey
                    val base = normalizeBaseUrl(if (selectedProvider == "OpenAI") openAiBaseUrl else claudeBaseUrl)
                    val model = if (selectedProvider == "OpenAI") openAiModel else claudeModel

                    when {
                        key.isBlank() -> validationError = uiText(UiText.ApiKeyRequired)
                        base.isBlank() -> validationError = uiText(UiText.BaseUrlRequired)
                        model.isBlank() -> validationError = uiText(UiText.ModelRequired)
                        else -> {
                            val editing = editingItem
                            if (editing != null) {
                                KeyConfigStore.update(editing.copy(
                                    name = nameField.ifBlank { selectedProvider + " " + editing.id },
                                    provider = selectedProvider,
                                    apiKey = key,
                                    baseUrl = base,
                                    model = model
                                ))
                            } else {
                                KeyConfigStore.add(
                                    KeyConfigStore.createNewConfig(
                                        name = nameField,
                                        provider = selectedProvider,
                                        apiKey = key,
                                        baseUrl = base,
                                        model = model
                                    )
                                )
                            }
                            validationError = null
                            showModal = false
                        }
                    }
                }
            )
        }
    }
}

// ==============================================================================
// UI：加号按钮
// ==============================================================================

@Composable
private fun AddButton(pixelFont: FontFamily, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color.Black.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 12.dp)) {
            val strokePx = 1.6.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(Color.Black.copy(alpha = 0.65f), Offset(0f, cy), Offset(size.width, cy), strokePx)
            drawLine(Color.Black.copy(alpha = 0.65f), Offset(cx, 0f), Offset(cx, size.height), strokePx)
        }
    }
}

// ==============================================================================
// UI：单个配置卡片
// ==============================================================================

@Composable
private fun KeyConfigCard(
    item: SavedKeyConfig,
    isActive: Boolean,
    pixelFont: FontFamily,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit
) {
    var showFull by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable { onEdit() }
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isActive) Color(0xFF3DDC84) else Color.Transparent,
                                shape = RoundedCornerShape(5.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isActive) Color(0xFF3DDC84) else Color.Black.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(5.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSetActive() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = pixelFont,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.provider,
                        fontSize = 11.sp,
                        fontFamily = pixelFont,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    EyeIcon(
                        revealed = showFull,
                        onClick = { showFull = !showFull }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(11.dp)) {
                            val c = Color.Black.copy(alpha = 0.5f)
                            val s = 1.6.dp.toPx()
                            drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = s)
                            drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = s)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val shown = if (showFull) item.apiKey else "••••••••"

            Text(
                text = uiText(UiText.KeyPrefix) + shown,
                fontSize = 11.sp,
                fontFamily = pixelFont,
                color = Color.Black.copy(alpha = 0.75f)
            )

            Text(
                text = uiText(UiText.UrlPrefix) + item.baseUrl,
                fontSize = 10.sp,
                fontFamily = pixelFont,
                color = Color.Black.copy(alpha = 0.5f)
            )
            Text(
                text = uiText(UiText.ModelPrefix) + item.model,
                fontSize = 10.sp,
                fontFamily = pixelFont,
                color = Color.Black.copy(alpha = 0.5f)
            )
        }
    }
}

// ==============================================================================
// UI：弹窗
// ==============================================================================

@Composable
private fun KeyModal(
    pixelFont: FontFamily,
    modalStep: Int,
    selectedProvider: String,
    onProviderChange: (String) -> Unit,
    nameField: String,
    onNameChange: (String) -> Unit,
    openAiKey: String,
    onOpenAiKeyChange: (String) -> Unit,
    openAiBaseUrl: String,
    onOpenAiBaseUrlChange: (String) -> Unit,
    openAiModel: String,
    onOpenAiModelChange: (String) -> Unit,
    claudeKey: String,
    onClaudeKeyChange: (String) -> Unit,
    claudeBaseUrl: String,
    onClaudeBaseUrlChange: (String) -> Unit,
    claudeModel: String,
    onClaudeModelChange: (String) -> Unit,
    validationError: String?,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onStepChange: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var loadingDotCount by remember { mutableStateOf(1) }

    LaunchedEffect(isTestingConnection) {
        loadingDotCount = 1
        while (isTestingConnection) {
            delay(360)
            loadingDotCount = if (loadingDotCount >= 3) 1 else loadingDotCount + 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(2.dp, Color.Black, RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .padding(20.dp)
        ) {
            Crossfade(
                targetState = modalStep,
                animationSpec = tween(300),
                label = "ModalStep"
            ) { step ->
                if (step == 1) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiText(UiText.SelectProvider),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = pixelFont,
                            color = Color.Black
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        ProviderOptionCard(
                            name = "OpenAI",
                            desc = "GPT-4o / GPT-4o-mini / Compatible",
                            isSelected = selectedProvider == "OpenAI",
                            pixelFont = pixelFont
                        ) { onProviderChange("OpenAI") }

                        Spacer(modifier = Modifier.height(10.dp))

                        ProviderOptionCard(
                            name = "Claude",
                            desc = "Anthropic Claude 3.5",
                            isSelected = selectedProvider == "Claude",
                            pixelFont = pixelFont
                        ) { onProviderChange("Claude") }

                        Spacer(modifier = Modifier.height(22.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ModalButton(
                                text = uiText(UiText.Cancel),
                                filled = false,
                                pixelFont = pixelFont,
                                modifier = Modifier.weight(1f),
                                onClick = onDismiss
                            )
                            ModalButton(
                                text = uiText(UiText.Confirm),
                                filled = true,
                                pixelFont = pixelFont,
                                modifier = Modifier.weight(1f),
                                onClick = { onStepChange(2) }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = selectedProvider + " " + uiText(UiText.Configuration),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = pixelFont,
                            color = Color.Black
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        KeyInputField(
                            label = uiText(UiText.Name),
                            value = nameField,
                            onValueChange = onNameChange,
                            placeholder = "My API Key",
                            pixelFont = pixelFont
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (selectedProvider == "OpenAI") {
                            KeyInputField(uiText(UiText.ApiKey), openAiKey, onOpenAiKeyChange, "sk-...", pixelFont, isSecret = true)
                            Spacer(modifier = Modifier.height(10.dp))
                            KeyInputField(uiText(UiText.BaseUrl), openAiBaseUrl, onOpenAiBaseUrlChange, "https://api.openai.com/v1", pixelFont)
                            Spacer(modifier = Modifier.height(10.dp))
                            KeyInputField(uiText(UiText.Model), openAiModel, onOpenAiModelChange, "gpt-4o-mini", pixelFont)
                        } else {
                            KeyInputField(uiText(UiText.ApiKey), claudeKey, onClaudeKeyChange, "sk-ant-...", pixelFont, isSecret = true)
                            Spacer(modifier = Modifier.height(10.dp))
                            KeyInputField(uiText(UiText.BaseUrl), claudeBaseUrl, onClaudeBaseUrlChange, "https://api.anthropic.com", pixelFont)
                            Spacer(modifier = Modifier.height(10.dp))
                            KeyInputField(uiText(UiText.Model), claudeModel, onClaudeModelChange, "claude-3-5-sonnet-20240620", pixelFont)
                        }

                        if (validationError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = validationError,
                                fontSize = 11.sp,
                                fontFamily = pixelFont,
                                color = Color.Red
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ModalButton(
                            text = if (isTestingConnection) uiText(UiText.Testing) + ".".repeat(loadingDotCount) else uiText(UiText.TestConnection),
                            filled = false,
                            pixelFont = pixelFont,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isTestingConnection,
                            onClick = {
                                if (!isTestingConnection) {
                                    val testKey = if (selectedProvider == "OpenAI") openAiKey else claudeKey
                                    val testBase = normalizeBaseUrl(
                                        if (selectedProvider == "OpenAI") openAiBaseUrl else claudeBaseUrl
                                    )
                                    val testModel = if (selectedProvider == "OpenAI") openAiModel else claudeModel

                                    when {
                                        testKey.isBlank() -> {
                                            connectionTestResult = ConnectionTestResult(
                                                isSuccess = false,
                                                message = uiText(UiText.ApiKeyRequired)
                                            )
                                        }
                                        testBase.isBlank() -> {
                                            connectionTestResult = ConnectionTestResult(
                                                isSuccess = false,
                                                message = uiText(UiText.BaseUrlRequired)
                                            )
                                        }
                                        testModel.isBlank() -> {
                                            connectionTestResult = ConnectionTestResult(
                                                isSuccess = false,
                                                message = uiText(UiText.ModelRequired)
                                            )
                                        }
                                        else -> {
                                            isTestingConnection = true
                                            connectionTestResult = ConnectionTestResult(
                                                isSuccess = false,
                                                message = "Testing..."
                                            )

                                            scope.launch {
                                                val result = testKeyConnection(
                                                    SavedKeyConfig(
                                                        id = "test_connection",
                                                        name = nameField.ifBlank { "Test Connection" },
                                                        provider = selectedProvider,
                                                        apiKey = testKey,
                                                        baseUrl = testBase,
                                                        model = testModel
                                                    )
                                                )
                                                connectionTestResult = result
                                                isTestingConnection = false
                                            }
                                        }
                                    }
                                }
                            }
                        )

                        connectionTestResult?.let { result ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = result.message,
                                fontSize = 11.sp,
                                fontFamily = pixelFont,
                                color = if (result.isSuccess) Color(0xFF1B8F4D) else Color.Red
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ModalButton(
                                text = uiText(UiText.Back),
                                filled = false,
                                pixelFont = pixelFont,
                                modifier = Modifier.weight(1f),
                                onClick = { onStepChange(1) }
                            )
                            ModalButton(
                                text = if (isEditing) uiText(UiText.Update) else uiText(UiText.Save),
                                filled = true,
                                pixelFont = pixelFont,
                                modifier = Modifier.weight(1f),
                                onClick = onConfirm
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================================
// UI 组件：通用小控件
// ==============================================================================

@Composable
private fun ProviderOptionCard(
    name: String,
    desc: String,
    isSelected: Boolean,
    pixelFont: FontFamily,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) Color.Black.copy(alpha = 0.08f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.Black else Color.Black.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = name,
                fontSize = 15.sp,
                fontFamily = pixelFont,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 11.sp,
                fontFamily = pixelFont,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ModalButton(
    text: String,
    filled: Boolean,
    pixelFont: FontFamily,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .background(
                color = when {
                    !enabled -> Color.Black.copy(alpha = 0.025f)
                    filled -> Color.Black
                    else -> Color.Black.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = when {
                    !enabled -> Color.Black.copy(alpha = 0.08f)
                    filled -> Color.Black
                    else -> Color.Black.copy(alpha = 0.2f)
                },
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontFamily = pixelFont,
            fontWeight = when {
                !enabled -> FontWeight.Normal
                filled -> FontWeight.Bold
                else -> FontWeight.Normal
            },
            color = when {
                !enabled -> Color.Black.copy(alpha = 0.35f)
                filled -> Color.White
                else -> Color.Black
            }
        )
    }
}

@Composable
private fun KeyInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    pixelFont: FontFamily,
    isSecret: Boolean = false
) {
    var reveal by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = pixelFont,
            fontWeight = FontWeight.Bold,
            color = Color.Black.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 13.sp,
                fontFamily = pixelFont,
                color = Color.Black
            ),
            visualTransformation = if (isSecret && !reveal) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.04f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.Black.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    fontSize = 12.sp,
                                    fontFamily = pixelFont,
                                    color = Color.Black.copy(alpha = 0.35f)
                                )
                            }
                            innerTextField()
                        }

                        if (isSecret) {
                            EyeIcon(
                                revealed = reveal,
                                onClick = { reveal = !reveal }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun EyeIcon(
    revealed: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 18.dp, height = 12.dp)) {
            val c = Color.Black.copy(alpha = 0.6f)
            val s = 1.5.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f

            drawLine(c, Offset(0f, cy), Offset(cx, 0f), strokeWidth = s)
            drawLine(c, Offset(cx, 0f), Offset(size.width, cy), strokeWidth = s)
            drawLine(c, Offset(0f, cy), Offset(cx, size.height), strokeWidth = s)
            drawLine(c, Offset(cx, size.height), Offset(size.width, cy), strokeWidth = s)

            if (!revealed) {
                drawCircle(c, radius = 2.dp.toPx(), center = Offset(cx, cy))
            } else {
                drawLine(
                    c,
                    Offset(1.dp.toPx(), size.height),
                    Offset(size.width - 1.dp.toPx(), 0f),
                    strokeWidth = s
                )
            }
        }
    }
}
