package com.ottoman.empire1800.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.Difficulty
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    loadingSaved: Boolean,
    savedGame: GameState?,
    snackbarMessage: String?,
    onConsumeSnackbar: () -> Unit,
    onContinueSaved: () -> Unit,
    onStartNewGame: (difficulty: Int, ppy: Int) -> Unit,
    onOpenSettings: () -> Unit,
    onExportSave: () -> String?,
    onImportSave: (String) -> Unit,
    onNotify: (String) -> Unit,
) {
    var difficulty by remember { mutableIntStateOf(1) }
    var ppy by remember { mutableIntStateOf(2) }
    var showNewConfirm by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onConsumeSnackbar()
        }
    }

    Scaffold(
        containerColor = AppColors.Navy,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("الدولة العثمانية 1800") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "الذكاء الاصطناعي",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Navy,
                    titleContentColor = AppColors.White,
                    actionIconContentColor = AppColors.White,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = AppColors.Gold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "السلطان",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Gold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "من 1800 إلى ${GameEngine.END_YEAR}. ثلاثة قرارات في كل جولة، يكتبها الذكاء الاصطناعي وتحسب اللعبة نتائجها.",
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp,
                    fontSize = 16.sp,
                    color = AppColors.Muted,
                )
                Spacer(modifier = Modifier.height(28.dp))

                if (loadingSaved) {
                    CircularProgressIndicator(color = AppColors.Gold)
                } else {
                    Text("الصعوبة (للعبة جديدة)", color = AppColors.Muted)
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        Difficulty.names.forEachIndexed { index, name ->
                            SegmentedButton(
                                selected = difficulty == index,
                                onClick = { difficulty = index },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = Difficulty.names.size,
                                ),
                            ) {
                                Text(name)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = Difficulty.descriptions[difficulty],
                        textAlign = TextAlign.Center,
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("إيقاع المحاكاة (للعبة جديدة)", color = AppColors.Muted)
                    Spacer(modifier = Modifier.height(8.dp))
                    val paces = listOf(2 to "كل 6 أشهر", 4 to "كل 3 أشهر")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        paces.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = ppy == value,
                                onClick = { ppy = value },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = paces.size,
                                ),
                            ) {
                                Text(label)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (ppy == 4) {
                            "أدق وأبطأ: 440 جولة بنحو ضعف استهلاك التوكنات، وفي كل جولة قرارات أخف أثرا."
                        } else {
                            "متوازن وأوفر: 220 جولة، وقرارات أكبر أثرا في كل جولة."
                        },
                        textAlign = TextAlign.Center,
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    if (savedGame != null) {
                        Button(
                            onClick = onContinueSaved,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("متابعة - ${GameEngine.periodLabel(savedGame.round, savedGame.ppy)}")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showNewConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("لعبة جديدة")
                        }
                    } else {
                        Button(
                            onClick = { onStartNewGame(difficulty, ppy) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ابدأ اللعبة")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (savedGame != null) {
                            TextButton(
                                onClick = {
                                    val json = onExportSave()
                                    if (json != null) {
                                        clipboard.setText(AnnotatedString(json))
                                        onNotify("نُسخ الحفظ إلى الحافظة. الصقه في ملاحظة لتحتفظ به.")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نسخ الحفظ")
                            }
                        }
                        TextButton(
                            onClick = {
                                val text = clipboard.getText()?.text ?: ""
                                onImportSave(text)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Filled.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استيراد حفظ")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { showHelp = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("كيف تلعب؟")
                    }
                }
            }
        }
    }

    if (showNewConfirm) {
        AlertDialog(
            onDismissRequest = { showNewConfirm = false },
            containerColor = AppColors.Card,
            title = { Text("لعبة جديدة؟") },
            text = { Text("سيتم حذف اللعبة المحفوظة والبدء من جديد.") },
            confirmButton = {
                Button(
                    onClick = {
                        showNewConfirm = false
                        onStartNewGame(difficulty, ppy)
                    },
                ) {
                    Text("ابدأ من جديد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewConfirm = false }) {
                    Text("تراجع")
                }
            },
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            containerColor = AppColors.Card,
            title = { Text("كيف تلعب؟", color = AppColors.Gold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "• أنت السلطان. تبدأ في كانون الثاني 1800 وتحكم حتى نهاية ${GameEngine.END_YEAR}، في جولات كل واحدة ستة أشهر أو ثلاثة بحسب اختيارك.\n\n" +
                            "• في كل جولة يكتب الذكاء الاصطناعي الوضع الراهن وثلاثة قرارات: داخلي واقتصادي وخارجي، ولكل قرار ثلاثة خيارات. لا شيء من الأحداث مكتوب سلفا في اللعبة.\n\n" +
                            "• اللعبة تعاير أرقام الذكاء الاصطناعي بحجم ميزانية الدولة، وتحسب الإيراد والنفقات والفوائد بالقروش، وتؤجل الآثار الكبرى كما يحدث في الواقع.\n\n" +
                            "• من «الديوان» تدير القوى (الانكشارية والعلماء والأعيان...)، والسفارات والجواسيس والمعاهدات، وتراقب الولايات والأسعار والسكان. هذه الأنظمة تحسبها اللعبة بقواعد ثابتة، فلا تستهلك توكنات.\n\n" +
                            "• يمكنك سداد جزء من الدين من فائض الخزينة قبل تنفيذ الجولة. وللدين وجه خارجي يبدأ من 1854 وقد ينتهي بعجز عن السداد وإدارة ديون عامة.\n\n" +
                            "• المشاريع القومية (طرق وري وسكك حديد ومدارس...): مشروع واحد في كل وقت، تموله بأقساط وتختار مدته، وفوائده عند الاكتمال.\n\n" +
                            "• الحروب تحسبها اللعبة بقوة الجيش والاقتصاد والعلاقات، وقد تنتهي بتعويض أو بخسارة ولاية. والولايات قد تنفصل إن غضبت وضعفت الدولة، ومن الديوان ترى الرسوم البيانية لمسارك.\n\n" +
                            "• تخسر إذا نزلت الشرعية إلى 15 أو الاستقرار إلى 12، أو هبط ثلاثة من أبعاد الاستقرار الستة إلى الحضيض، أو بلغ الدين ست أمثال الإيراد السنوي، أو انهارت قوة الجيش والمكانة الدولية معا.\n\n" +
                            "• اللعبة تحفظ نفسها بعد كل اختيار، بما فيها الجولة التي كتبها الذكاء الاصطناعي.",
                        lineHeight = 25.sp,
                        color = AppColors.White,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showHelp = false }) {
                    Text("فهمت")
                }
            },
        )
    }
}
