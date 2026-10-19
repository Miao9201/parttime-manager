package cn.miao.parttime.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.miao.parttime.data.*
import cn.miao.parttime.domain.*
import cn.miao.parttime.export.ReportScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartTimeApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dayId by rememberSaveable { mutableStateOf<Long?>(null) }
    var shiftName by rememberSaveable { mutableStateOf<String?>(null) }
    var dayEditor by rememberSaveable { mutableStateOf(false) }
    var editingDayId by rememberSaveable { mutableStateOf<Long?>(null) }
    var personEditor by rememberSaveable { mutableStateOf(false) }
    var editingPersonId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingDay by rememberSaveable { mutableStateOf(false) }
    var deletingPersonId by rememberSaveable { mutableStateOf<Long?>(null) }
    var screenGuard by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val day = state.days.firstOrNull { it.day.id == dayId }
    val shift = shiftName?.let { ShiftKind.valueOf(it) }
    val busy = state.busy || state.exporting || state.savingReport
    var exportPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val saveDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) viewModel.saveReportTo(uri)
    }
    val navigate: (() -> Unit) -> Unit = { action ->
        if (!busy) {
            if (screenGuard != null) { pendingNavigation = action; screenGuard?.invoke() } else action()
        }
    }
    val finishNavigation: () -> Unit = {
        val action = pendingNavigation
        pendingNavigation = null
        screenGuard = null
        action?.invoke()
    }
    val back: () -> Unit = { navigate { if (shiftName != null) shiftName = null else dayId = null } }
    BackHandler(enabled = dayId != null && !dayEditor && !personEditor) { back() }
    Scaffold(topBar = {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (dayId != null) TextButton(shape = MaterialTheme.shapes.small, onClick = back, enabled = !busy, modifier = Modifier.testTag("navigate_back")) { Text("‹ 返回") }
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(if (dayId == null) "兼职管理器" else day?.day?.locationName ?: "记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (day != null) Text(fullDate(day.day.localDate) + (shift?.let { " · ${it.label}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (day != null && shift == null) TextButton(shape = MaterialTheme.shapes.small, enabled = !busy, modifier = Modifier.testTag("export_day"), onClick = { navigate { exportPicker = true } }) { Text(if (state.exporting) "生成中…" else "导出") }
                    if (day != null && shift == null) TextButton(shape = MaterialTheme.shapes.small, enabled = !busy, modifier = Modifier.testTag("edit_day"), onClick = {
                        navigate { editingDayId = dayId; dayEditor = true }
                    }) { Text("修改") }
                }
                HorizontalDivider()
            }
        }
    }, bottomBar = {
        if (day != null && shift != null) Surface(tonalElevation = 1.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Button(shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_person"), enabled = !busy,
                    onClick = { navigate { editingPersonId = null; personEditor = true } }) { Text("＋ 添加人员") }
            }
        }
    }) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            dayId == null -> HomeScreen(state.days, busy, Modifier.padding(padding),
                onCreate = { editingDayId = null; dayEditor = true }, onOpen = { dayId = it; shiftName = null })
            day == null -> Column(Modifier.padding(padding).padding(24.dp)) { Text("这条记录已不存在"); TextButton(shape = MaterialTheme.shapes.small, onClick = { dayId = null; shiftName = null }) { Text("回到首页") } }
            shift == null -> DayScreen(day, busy, Modifier.padding(padding),
                onSaveFee = { text, done -> viewModel.saveHeadFee(day.day.id, text, done) },
                onShift = { selected ->
                    navigate { shiftName = selected.name }
                }, onDelete = { navigate { deletingDay = true } },
                setGuard = { screenGuard = it }, onDiscarded = finishNavigation,
                onKept = { pendingNavigation = null })
            else -> ShiftScreen(day, shift, busy, Modifier.padding(padding),
                onEdit = { personId -> navigate { editingPersonId = personId; personEditor = true } },
                onDelete = { id -> navigate { deletingPersonId = id } },
                onSaveOvertime = viewModel::saveOvertime, onDeleteOvertime = viewModel::deleteOvertime,
                navigate = navigate, setGuard = { screenGuard = it }, onDiscarded = finishNavigation,
                onKept = { pendingNavigation = null })
        }
    }
    if (dayEditor) DayEditor(state.days.firstOrNull { it.day.id == editingDayId }, state.days, state.busy,
        onClose = { dayEditor = false }, onOpenExisting = { dayId = it; shiftName = null; dayEditor = false },
        onSave = { location, date -> viewModel.saveDay(editingDayId, location, date) { dayId = it; shiftName = null; dayEditor = false } })
    if (personEditor && day != null && shift != null) PersonEditor(day.people.firstOrNull { it.person.id == editingPersonId },
        day.people.filter { it.person.shiftKind == shift.name }, state.busy, onClose = { personEditor = false },
        onSave = { draft, duplicate -> viewModel.savePerson(day.day.id, shift, editingPersonId, draft, duplicate) { personEditor = false } })
    if (deletingDay && day != null) ConfirmDelete("删除这条记录？", "${day.day.locationName} · ${fullDate(day.day.localDate)}\n两班人员和全部加班也会删除。", state.busy,
        onDismiss = { deletingDay = false }, onConfirm = { viewModel.deleteDay(day.day.id) { deletingDay = false; dayId = null; shiftName = null } })
    deletingPersonId?.let { id -> ConfirmDelete("删除这名人员？", "该次排班及其全部加班会一并删除。", state.busy,
        onDismiss = { deletingPersonId = null }, onConfirm = { viewModel.deletePerson(id) { deletingPersonId = null } }) }
    if (exportPicker && day != null) AlertDialog(onDismissRequest = { exportPicker = false }, title = { Text("导出费用明细") },
        text = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportScope.entries.forEach { scope -> OutlinedButton(shape = MaterialTheme.shapes.small,
                enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("export_${scope.name}"),
                onClick = { exportPicker = false; viewModel.exportReport(day.day.id, scope) }) { Text(scope.label) } }
        } }, confirmButton = { TextButton(onClick = { exportPicker = false }) { Text("取消") } })
    state.report?.let { artifact -> ReportPreviewDialog(artifact, state.savingReport,
        onDismiss = viewModel::clearReport,
        onSave = { if (Build.VERSION.SDK_INT >= 29) viewModel.saveReport() else saveDocument.launch(artifact.file.name) },
        onShare = { viewModel.reportShareIntent()?.let { context.startActivity(Intent.createChooser(it, "分享费用明细")) } }) }
    state.notice?.let { message -> AlertDialog(onDismissRequest = viewModel::clearNotice, title = { Text("操作完成") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = viewModel::clearNotice) { Text("知道了") } }) }
    state.error?.let { message -> AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("未能完成操作") }, text = { Text(message) },
        confirmButton = { TextButton(shape = MaterialTheme.shapes.small, onClick = viewModel::clearError) { Text("知道了") } }) }
}

@Composable
private fun HomeScreen(days: List<DayBundle>, busy: Boolean, modifier: Modifier, onCreate: () -> Unit, onOpen: (Long) -> Unit) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("地点与日期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(shape = MaterialTheme.shapes.small, onClick = onCreate, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("create_day")) { Text("＋ 创建") }
        }
    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("home_list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (days.isEmpty()) item { Panel { Text("还没有记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("点击“创建”，先填地点和日期，\n再登记中午、晚上两班的人员与费用。", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(days, key = { it.day.id }) { bundle ->
            val noon = calculateShift(bundle, ShiftKind.NOON); val evening = calculateShift(bundle, ShiftKind.EVENING); val totals = calculateDay(bundle)
            Card(onClick = { onOpen(bundle.day.id) }, modifier = Modifier.fillMaxWidth().testTag("day_${bundle.day.id}"), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(bundle.day.locationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(fullDate(bundle.day.localDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("中午 ${noon.count}人 · 晚上 ${evening.count}人", style = MaterialTheme.typography.bodyMedium); Text("查看 ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    Text(if (totals.totalCents == null) "当天费用：待设置人头费单价" else "当天费用  ${yuan(totals.totalCents)}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    }
}

@Composable
private fun DayScreen(day: DayBundle, busy: Boolean, modifier: Modifier, onSaveFee: (String, () -> Unit) -> Unit,
    onShift: (ShiftKind) -> Unit, onDelete: () -> Unit, setGuard: ((() -> Unit)?) -> Unit, onDiscarded: () -> Unit, onKept: () -> Unit) {
    var editingFee by rememberSaveable(day.day.id) { mutableStateOf(false) }
    var fee by rememberSaveable(day.day.id) { mutableStateOf(day.day.headFeeCents?.let(Money::format) ?: "") }
    var discard by rememberSaveable { mutableStateOf(false) }
    val original = day.day.headFeeCents?.let(Money::format) ?: ""
    val dirty = editingFee && fee != original
    LaunchedEffect(dirty) { setGuard(if (dirty) ({ discard = true }) else null) }
    DisposableEffect(Unit) { onDispose { setGuard(null) } }
    Column(modifier.fillMaxSize()) {
        Panel(Modifier.padding(16.dp, 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("人头费单价", fontWeight = FontWeight.Bold); Text("中午、晚上共用 · 每人次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (!editingFee) TextButton(shape = MaterialTheme.shapes.small, enabled = !busy, modifier = Modifier.testTag("edit_fee"), onClick = { fee = original; editingFee = true }) { Text("修改") }
            }
            if (editingFee) {
                OutlinedTextField(value = fee, onValueChange = { fee = it }, singleLine = true, enabled = !busy, label = { Text("单价") }, supportingText = { Text("可填 0 表示不收；留空表示未设置") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("head_fee"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(shape = MaterialTheme.shapes.small, enabled = !busy, onClick = { if (dirty) discard = true else editingFee = false }) { Text("取消") }
                    Button(shape = MaterialTheme.shapes.small, enabled = !busy, modifier = Modifier.testTag("save_fee"), onClick = { onSaveFee(fee) { editingFee = false } }) { Text("保存") }
                }
            } else Text(day.day.headFeeCents?.let { "${yuan(it)} / 人次" } ?: "未设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        LazyColumn(Modifier.weight(1f).testTag("day_list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(day.day.locationName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(ShiftKind.entries) { shift ->
                val totals = calculateShift(day, shift)
                Card(onClick = { onShift(shift) }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("shift_${shift.name}"), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(shift.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Text("${totals.count}人  ›", color = MaterialTheme.colorScheme.primary)
                        }
                        Text(totals.totalCents?.let { "本班合计  ${yuan(it)}" } ?: "本班合计：待设置单价", fontWeight = FontWeight.SemiBold)
                        Text("工资 ${yuan(totals.baseCents)} · 加班 ${yuan(totals.overtimeCents)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Summary(calculateDay(day), "当天汇总", "人次") }
            item { TextButton(shape = MaterialTheme.shapes.small, onClick = onDelete, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("delete_day")) { Text("删除这条记录", color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (discard) NavigationDraftDialog(busy, onKeep = { discard = false; onKept() },
        onDiscard = { discard = false; fee = original; editingFee = false; setGuard(null); onDiscarded() },
        onSave = { onSaveFee(fee) { discard = false; editingFee = false; setGuard(null); onDiscarded() } })
}

@Composable
private fun ShiftScreen(day: DayBundle, shift: ShiftKind, busy: Boolean, modifier: Modifier,
    onEdit: (Long) -> Unit, onDelete: (Long) -> Unit,
    onSaveOvertime: (Long, Long?, OvertimeDraft, () -> Unit) -> Unit,
    onDeleteOvertime: (Long, Long, () -> Unit) -> Unit,
    navigate: (() -> Unit) -> Unit, setGuard: ((() -> Unit)?) -> Unit,
    onDiscarded: () -> Unit, onKept: () -> Unit) {
    val people = day.people.filter { it.person.shiftKind == shift.name }.sortedBy { it.person.sortOrder }
    var inlinePersonId by rememberSaveable(day.day.id, shift) { mutableStateOf<Long?>(null) }
    var inlineItemId by rememberSaveable(day.day.id, shift) { mutableStateOf<Long?>(null) }
    var description by rememberSaveable(day.day.id, shift) { mutableStateOf("") }
    var amount by rememberSaveable(day.day.id, shift) { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var deletingItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingItemPersonId by rememberSaveable { mutableStateOf<Long?>(null) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val original = people.firstOrNull { it.person.id == inlinePersonId }?.overtime?.firstOrNull { it.id == inlineItemId }
    val dirty = inlinePersonId != null && (description != (original?.description ?: "") || amount != (original?.amountCents?.let(Money::format) ?: ""))
    val latestDay by rememberUpdatedState(day)
    val openInline: (Long, Long?) -> Unit = { personId, itemId ->
        val item = latestDay.people.firstOrNull { it.person.id == personId }?.overtime?.firstOrNull { it.id == itemId }
        if (itemId == null || item != null) {
            inlinePersonId = personId; inlineItemId = itemId
            description = item?.description ?: ""; amount = item?.amountCents?.let(Money::format) ?: ""
        }
    }
    val closeInline: () -> Unit = { inlinePersonId = null; inlineItemId = null; description = ""; amount = ""; setGuard(null) }
    val saveInline: (() -> Unit) -> Unit = { done ->
        localError = try { require(description.isNotBlank()) { "请填写加班时长" }; Money.parse(amount); null }
            catch (error: IllegalArgumentException) { error.message ?: "请检查加班金额" }
        if (localError == null) inlinePersonId?.let { personId ->
            onSaveOvertime(personId, inlineItemId, OvertimeDraft(description, amount)) { closeInline(); done() }
        }
    }
    LaunchedEffect(dirty) { setGuard(if (dirty) ({ discard = true }) else null) }
    DisposableEffect(Unit) { onDispose { setGuard(null) } }
    LazyColumn(modifier.fillMaxSize().imePadding().testTag("shift_list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Summary(calculateShift(day, shift), "${shift.label}费用", "人") }
        if (people.isEmpty()) item { Panel { Text("本班还没有人员", fontWeight = FontWeight.Bold); Text("点击底部“添加人员”开始登记。", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(people, key = { it.person.id }) { bundle ->
            val person = bundle.person
            Panel(Modifier.testTag("person_${person.id}")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(person.task, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                }
                Text("工资  ${yuan(person.baseAmountCents)}")
                bundle.overtime.sortedBy { it.sortOrder }.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("＋ ${item.description}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(yuan(item.amountCents), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(shape = MaterialTheme.shapes.small, enabled = !busy && !(inlinePersonId == person.id && inlineItemId == item.id), modifier = Modifier.testTag("edit_inline_ot_${item.id}"), onClick = { navigate {
                            openInline(person.id, item.id)
                        } }) { Text("编辑加班") }
                        TextButton(shape = MaterialTheme.shapes.small, enabled = !busy, modifier = Modifier.testTag("delete_inline_ot_${item.id}"), onClick = { navigate {
                            deletingItemPersonId = person.id; deletingItemId = item.id
                        } }) { Text("删除加班", color = MaterialTheme.colorScheme.error) }
                    }
                }
                HorizontalDivider()
                Text("个人小计  ${yuan(person.baseAmountCents + bundle.overtime.sumOf { it.amountCents })}", fontWeight = FontWeight.Bold)
                Text("不含人头费", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("add_inline_ot_${person.id}"), onClick = { navigate {
                        openInline(person.id, null)
                    } }, enabled = !busy && !(inlinePersonId == person.id && inlineItemId == null)) { Text("＋ 加班") }
                    Row {
                        TextButton(shape = MaterialTheme.shapes.small, onClick = { onDelete(person.id) }, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        TextButton(shape = MaterialTheme.shapes.small, onClick = { onEdit(person.id) }, enabled = !busy) { Text("编辑") }
                    }
                }
                if (inlinePersonId == person.id) {
                    HorizontalDivider()
                    Text(if (inlineItemId == null) "添加加班" else "编辑加班", fontWeight = FontWeight.Bold)
                    OvertimeDurationField(description, { description = it }, !busy, "inline_ot_description")
                    OutlinedTextField(amount, { amount = it }, label = { Text("加班金额") }, enabled = !busy, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("inline_ot_amount"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(shape = MaterialTheme.shapes.small, enabled = !busy, modifier = Modifier.testTag("cancel_inline_ot"), onClick = { navigate(closeInline) }) { Text("取消") }
                        Button(shape = MaterialTheme.shapes.small, enabled = !busy, modifier = Modifier.testTag("save_inline_ot"), onClick = { saveInline {} }) { Text("保存加班") }
                    }
                }
            }
        }
    }
    if (discard) NavigationDraftDialog(busy,
        onKeep = { discard = false; onKept() },
        onDiscard = { discard = false; closeInline(); onDiscarded() },
        onSave = { saveInline { discard = false; onDiscarded() } })
    deletingItemId?.let { itemId -> deletingItemPersonId?.let { personId ->
        ConfirmDelete("删除这项加班？", "删除后将重新计算该人员和本班费用。", busy,
            onDismiss = { deletingItemId = null; deletingItemPersonId = null },
            onConfirm = { onDeleteOvertime(personId, itemId) { deletingItemId = null; deletingItemPersonId = null; if (inlineItemId == itemId) closeInline() } })
    } }
    localError?.let { InputError(it) { localError = null } }
}

@Composable private fun OvertimeDurationField(value: String, onValueChange: (String) -> Unit, enabled: Boolean, tag: String) {
    OutlinedTextField(value, onValueChange, label = { Text("加班时长") }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..3).forEach { hours ->
            val duration = "加班${hours}小时"
            OutlinedButton(onClick = { onValueChange(duration) }, enabled = enabled,
                shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("${tag}_shortcut_$hours")) {
                Text(duration, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable private fun Summary(totals: ShiftTotals, title: String, countUnit: String) {
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontWeight = FontWeight.Bold); Text("${totals.count}$countUnit", color = MaterialTheme.colorScheme.primary) }
        AmountRow("工资", yuan(totals.baseCents)); AmountRow("加班金额", yuan(totals.overtimeCents)); AmountRow("人头费", totals.feeCents?.let(::yuan) ?: "待设置单价")
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("合计", fontWeight = FontWeight.Bold); Text(totals.totalCents?.let(::yuan) ?: "待设置单价", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
    }
}
@Composable private fun AmountRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyMedium) }
}
@Composable private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DayEditor(initial: DayBundle?, days: List<DayBundle>, busy: Boolean,
    onClose: () -> Unit, onOpenExisting: (Long) -> Unit, onSave: (String, String) -> Unit) {
    val originalLocation = initial?.day?.locationName ?: ""; val originalDate = initial?.day?.localDate ?: LocalDate.now().toString()
    var location by rememberSaveable(initial?.day?.id) { mutableStateOf(originalLocation) }; var date by rememberSaveable(initial?.day?.id) { mutableStateOf(originalDate) }
    var discard by rememberSaveable { mutableStateOf(false) }; var localError by rememberSaveable { mutableStateOf<String?>(null) }; var existing by rememberSaveable { mutableStateOf<Long?>(null) }
    val dirty = location != originalLocation || date != originalDate
    val close = { if (!busy) { if (dirty) discard = true else onClose() } }
    val context = LocalContext.current; val focus = LocalFocusManager.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { if (it == SheetValue.Hidden && (dirty || busy)) { if (!busy) discard = true; false } else true })
    ModalBottomSheet(onDismissRequest = close, sheetState = sheet, modifier = Modifier.imePadding()) {
        EditorHeader(if (initial == null) "创建记录" else "修改地点与日期", busy, "day", close) {
            focus.clearFocus()
            if (location.isBlank()) localError = "请填写地点" else {
                val match = days.firstOrNull { it.day.id != initial?.day?.id && it.day.locationName == location.trim() && it.day.localDate == date }
                if (match != null) existing = match.day.id else onSave(location, date)
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(location, { location = it; localError = null }, label = { Text("地点") }, placeholder = { Text("自由输入，或选择下方地点") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("day_location"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("香樟", "旺辉").forEach { name -> OutlinedButton(shape = MaterialTheme.shapes.small,
                    enabled = !busy, modifier = Modifier.weight(1f).testTag("location_$name"), onClick = { location = name; localError = null }) { Text(name) } }
            }
            Text("日期", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(shape = MaterialTheme.shapes.small, enabled = !busy, onClick = { date = LocalDate.now().toString() }, modifier = Modifier.weight(1f).testTag("date_today")) { Text("今天") }
                OutlinedButton(shape = MaterialTheme.shapes.small, enabled = !busy, onClick = { date = LocalDate.now().plusDays(1).toString() }, modifier = Modifier.weight(1f).testTag("date_tomorrow")) { Text("明天") }
            }
            OutlinedButton(shape = MaterialTheme.shapes.small, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("day_date"), onClick = {
                val selected = LocalDate.parse(date)
                DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
            }) { Text("${fullDate(date)}  ·  选择日期") }
            Text("先保存地点与日期，再登记两班的人员和费用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (discard) DiscardDialog(onKeep = { discard = false }, onDiscard = onClose)
    localError?.let { InputError(it) { localError = null } }
    existing?.let { id -> AlertDialog(onDismissRequest = { existing = null }, title = { Text("已有相同记录") }, text = { Text("这个地点在所选日期已有记录，进入已有记录继续管理？当前表单内容不会合并。") },
        confirmButton = { TextButton(shape = MaterialTheme.shapes.small, onClick = { onOpenExisting(id) }) { Text("进入已有记录") } }, dismissButton = { TextButton(shape = MaterialTheme.shapes.small, onClick = { existing = null }) { Text("继续修改") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PersonEditor(initial: PersonBundle?, people: List<PersonBundle>, busy: Boolean, onClose: () -> Unit, onSave: (PersonDraft, Boolean) -> Unit) {
    val original = initial?.let { p -> PersonDraft(p.person.name, p.person.phone, p.person.task, Money.format(p.person.baseAmountCents), p.overtime.sortedBy { it.sortOrder }.map { OvertimeDraft(it.description, Money.format(it.amountCents)) }) } ?: PersonDraft()
    var name by rememberSaveable(initial?.person?.id) { mutableStateOf(original.name) }
    var task by rememberSaveable(initial?.person?.id) { mutableStateOf(original.task) }; var amount by rememberSaveable(initial?.person?.id) { mutableStateOf(original.amount) }
    var descriptions by rememberSaveable(initial?.person?.id) { mutableStateOf(ArrayList(original.overtime.map { it.description })) }
    var amounts by rememberSaveable(initial?.person?.id) { mutableStateOf(ArrayList(original.overtime.map { it.amount })) }
    var discard by rememberSaveable { mutableStateOf(false) }; var duplicate by rememberSaveable { mutableStateOf(false) }; var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val draft = PersonDraft(name, original.phone, task, amount, descriptions.indices.map { OvertimeDraft(descriptions[it], amounts[it]) }); val dirty = draft != original
    val close = { if (!busy) { if (dirty) discard = true else onClose() } }; val focus = LocalFocusManager.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { if (it == SheetValue.Hidden && (dirty || busy)) { if (!busy) discard = true; false } else true })
    ModalBottomSheet(onDismissRequest = close, sheetState = sheet, modifier = Modifier.imePadding().testTag("person_sheet")) {
        EditorHeader(if (initial == null) "添加人员" else "编辑人员", busy, "person", close) {
            focus.clearFocus(); localError = validatePerson(draft)
            if (localError == null) {
                duplicate = people.any { it.person.id != initial?.person?.id && it.person.name == name.trim() }
                if (!duplicate) onSave(draft, false)
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("姓名") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("person_name"))
            TaskPicker(task, { task = it }, busy)
            OutlinedTextField(amount, { amount = it }, label = { Text("工资") }, supportingText = { Text("本班工资，最多两位小数，可填 0") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("person_amount"))
            HorizontalDivider(); Text("加班明细", fontWeight = FontWeight.Bold)
            descriptions.indices.forEach { index -> Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("加班 ${index + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(shape = MaterialTheme.shapes.small, enabled = !busy, onClick = { descriptions = ArrayList(descriptions.filterIndexed { i, _ -> i != index }); amounts = ArrayList(amounts.filterIndexed { i, _ -> i != index }) }, modifier = Modifier.testTag("remove_ot_$index")) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                OvertimeDurationField(descriptions[index], { text -> descriptions = ArrayList(descriptions).also { it[index] = text } }, !busy, "ot_description_$index")
                OutlinedTextField(amounts[index], { text -> amounts = ArrayList(amounts).also { it[index] = text } }, label = { Text("加班金额") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("ot_amount_$index"))
            } }
            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { descriptions = ArrayList(descriptions + ""); amounts = ArrayList(amounts + "") }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_overtime")) { Text("＋ 添加加班") }
            Text("个人小计不含人头费；保存后更新统计。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (discard) DiscardDialog(onKeep = { discard = false }, onDiscard = onClose)
    localError?.let { InputError(it) { localError = null } }
    if (duplicate) AlertDialog(onDismissRequest = { duplicate = false }, title = { Text("本班可能重复登记") }, text = { Text("本班已有相同姓名。仍然保存为另一条人员记录？不会合并或覆盖已有人员。") },
        confirmButton = { TextButton(shape = MaterialTheme.shapes.small, enabled = !busy, onClick = { duplicate = false; onSave(draft, true) }) { Text("仍然保存") } }, dismissButton = { TextButton(shape = MaterialTheme.shapes.small, onClick = { duplicate = false }) { Text("返回检查") } })
}

@Composable private fun TaskPicker(task: String, onTask: (String) -> Unit, busy: Boolean) {
    val presets = listOf("服务员", "传菜", "洗碗", "打荷")
    var expanded by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(task.isNotBlank() && task !in presets) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("任务", style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(shape = MaterialTheme.shapes.small, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("person_task"),
                onClick = { expanded = true }) { Text(if (custom) "自定义 ▾" else task.ifBlank { "选择任务" } + " ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (presets + "自定义").forEach { choice -> DropdownMenuItem(text = { Text(choice) },
                    modifier = Modifier.testTag("task_option_$choice"), onClick = {
                        expanded = false
                        custom = choice == "自定义"
                        onTask(if (custom) { if (task in presets) "" else task } else choice)
                    }) }
            }
        }
        if (custom) OutlinedTextField(task, onTask, enabled = !busy, singleLine = true, label = { Text("自定义任务") },
            modifier = Modifier.fillMaxWidth().testTag("person_custom_task"))
    }
}

@Composable private fun NavigationDraftDialog(busy: Boolean, onKeep: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onKeep() }, title = { Text("修改尚未保存") }, text = { Text("保存后继续，或放弃这次修改。") },
        confirmButton = { TextButton(enabled = !busy, onClick = onSave) { Text("保存并继续") } },
        dismissButton = { Row {
            TextButton(enabled = !busy, onClick = onDiscard) { Text("放弃修改") }
            TextButton(enabled = !busy, onClick = onKeep) { Text("继续填写") }
        } })
}

@Composable private fun EditorHeader(title: String, busy: Boolean, tag: String, onClose: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(shape = MaterialTheme.shapes.small, onClick = onClose, enabled = !busy, modifier = Modifier.testTag("cancel_$tag")) { Text("取消") }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Button(shape = MaterialTheme.shapes.small, onClick = onSave, enabled = !busy, modifier = Modifier.testTag("save_$tag").heightIn(min = 48.dp)) { Text(if (busy) "保存中…" else "保存") }
    }
}
@Composable private fun DiscardDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(onDismissRequest = onKeep, title = { Text("放弃未保存的修改？") }, text = { Text("修改尚未保存。放弃后将保留原来的数据。") }, confirmButton = { TextButton(shape = MaterialTheme.shapes.small, onClick = onDiscard) { Text("放弃修改") } }, dismissButton = { TextButton(shape = MaterialTheme.shapes.small, onClick = onKeep) { Text("继续填写") } })
}
@Composable private fun InputError(message: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("请检查填写内容") }, text = { Text(message) }, confirmButton = { TextButton(shape = MaterialTheme.shapes.small, onClick = onDismiss) { Text("知道了") } })
}
@Composable private fun ConfirmDelete(title: String, message: String, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(shape = MaterialTheme.shapes.small, onClick = onConfirm, enabled = !busy) { Text("确认删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(shape = MaterialTheme.shapes.small, onClick = onDismiss, enabled = !busy) { Text("取消") } })
}
private fun validatePerson(draft: PersonDraft): String? = try {
    require(draft.name.isNotBlank()) { "请填写姓名" }; require(draft.task.isNotBlank()) { "请选择或填写任务" }; Money.parse(draft.amount)
    draft.overtime.forEachIndexed { index, item -> require(item.description.isNotBlank()) { "请填写加班 ${index + 1} 的时长" }; Money.parse(item.amount) }; null
} catch (e: IllegalArgumentException) { e.message ?: "请检查输入内容" }
private fun yuan(cents: Long): String = Money.format(cents)
private fun fullDate(date: String): String = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
