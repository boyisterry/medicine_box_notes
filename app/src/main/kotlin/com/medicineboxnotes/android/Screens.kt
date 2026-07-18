package com.medicineboxnotes.android

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import com.medicineboxnotes.android.platform.PdfExporter
import com.medicineboxnotes.android.platform.ModelDownloadState
import com.medicineboxnotes.designsystem.*
import com.medicineboxnotes.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        Text(subtitle.uppercase(), color = MBColor.Ink3, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, onRecords: () -> Unit, onMedicines: () -> Unit, onMember: (String) -> Unit) {
    val todayItems by vm.today.collectAsState()
    val medicines by vm.medicines.collectAsState()
    val records by vm.records.collectAsState()
    val members by vm.members.collectAsState()
    val now = remember { LocalDate.now() }
    var showMemberEditor by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<FamilyMember?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 38.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(now.format(DateTimeFormatter.ofPattern("MMMM d · EEEE", Locale.getDefault())), color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
            Text(greeting(), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(14.dp))
        }
        item { SectionLabel(stringResource(R.string.today_medication), "Today") }
        item {
            PaperCard(Modifier.fillParentMaxWidth()) {
                if (todayItems.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MBColor.Primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.no_medication_today), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.schedule_hint), color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
                }
                else todayItems.forEach { item -> TodoCheckRow(item.medicine.name, item.medicine.dosage.ifBlank { item.medicine.frequency }, item.scheduledTime.toString(), item.taken) { vm.setTaken(item, it) } }
            }
        }
        item { SectionLabel(stringResource(R.string.low_stock), "Low stock") { TextButton(onClick = onMedicines) { Text(stringResource(R.string.tab_medicines), color = MBColor.Primary) } } }
        item {
            PaperCard(Modifier.fillParentMaxWidth()) {
                val low = medicines.filter { it.stock <= 5 }
                if (low.isEmpty()) Text(stringResource(R.string.stock_sufficient), color = MBColor.Ink3)
                low.take(4).forEachIndexed { index, medicine ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MBColor.Hairline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ownerIndex = members.indexOfFirst { it.id == medicine.sourceMemberId }.coerceAtLeast(0)
                        MemberDot(medicine.sourceMemberName.ifBlank { medicine.name }, ownerIndex)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(medicine.name.ifBlank { stringResource(R.string.pending_medicine) }, style = MaterialTheme.typography.titleLarge)
                            Text(medicine.dosage, color = MBColor.Ink3)
                        }
                        StatusBadge("${stringResource(R.string.stock)} ${medicine.stock}", BadgeKind.Warning)
                    }
                }
            }
        }
        item {
            SectionLabel(stringResource(R.string.family), "Family") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onRecords) { Text(stringResource(R.string.view_records), color = MBColor.Primary) }
                    IconButton({ editingMember = null; showMemberEditor = true }) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.add_member), tint = MBColor.Primary)
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                members.forEachIndexed { index, member ->
                    Surface(
                        modifier = Modifier.width(112.dp).height(104.dp).combinedClickable(
                            onClick = { onMember(member.id) },
                            onLongClick = {
                                editingMember = member
                                showMemberEditor = true
                            },
                            onLongClickLabel = stringResource(R.string.edit_member),
                        ),
                        shape = RoundedCornerShape(20.dp), color = MBColor.Surface,
                        border = androidx.compose.foundation.BorderStroke(.5.dp, MBColor.Hairline),
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            MemberDot(member.name, index)
                            Spacer(Modifier.height(6.dp))
                            Text(member.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(stringResource(R.string.record_count, records.count { it.memberId == member.id }), color = MBColor.Ink3, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Surface(
                    modifier = Modifier.width(88.dp).height(104.dp).clickable {
                        editingMember = null
                        showMemberEditor = true
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = MBColor.Surface,
                    border = androidx.compose.foundation.BorderStroke(.5.dp, MBColor.Hairline),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.add_member), tint = MBColor.Primary, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
    if (showMemberEditor) {
        MemberEditorDialog(
            initial = editingMember,
            dismiss = { showMemberEditor = false },
            delete = { member -> vm.deleteMember(member); showMemberEditor = false },
            save = { member -> vm.saveMember(member); showMemberEditor = false },
        )
    }
}

@Composable
fun MemberDetailScreen(vm: MainViewModel, memberId: String, onBack: () -> Unit, onRecord: (String) -> Unit) {
    val members by vm.members.collectAsState()
    val records by vm.records.collectAsState()
    val medicines by vm.medicines.collectAsState()
    val member = members.firstOrNull { it.id == memberId }
    val memberRecords = records.filter { it.memberId == memberId }
    val memberMedicines = medicines.filter { it.sourceMemberId == memberId }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(member?.name.orEmpty(), style = MaterialTheme.typography.displaySmall)
                    Text(member?.relationship.orEmpty(), color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            PaperCard(Modifier.fillParentMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemberDot(member?.name.orEmpty().ifBlank { "?" }, members.indexOfFirst { it.id == memberId }.coerceAtLeast(0), Modifier.size(56.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(member?.name.orEmpty(), style = MaterialTheme.typography.titleLarge)
                        Text(member?.relationship.orEmpty(), color = MBColor.Ink3)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.record_count, memberRecords.size), color = MBColor.Primary)
                        Text("${memberMedicines.size} ${stringResource(R.string.tab_medicines)}", color = MBColor.Ink3, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { SectionLabel(stringResource(R.string.records_title), "Records") }
        if (memberRecords.isEmpty()) {
            item { PaperCard(Modifier.fillParentMaxWidth()) { Text(stringResource(R.string.no_records), color = MBColor.Ink3) } }
        } else {
            items(memberRecords, key = { it.id }) { record -> RecordListCard(vm, record, members, onRecord) }
        }
    }
}

@Composable private fun greeting(): String = stringResource(when (java.time.LocalTime.now().hour) { in 0..5 -> R.string.greeting_late; in 6..10 -> R.string.greeting_morning; in 11..13 -> R.string.greeting_noon; in 14..17 -> R.string.greeting_afternoon; else -> R.string.greeting_evening })

@Composable
fun RecordsScreen(vm: MainViewModel, onRecord: (String) -> Unit) {
    val records by vm.records.collectAsState(); val members by vm.members.collectAsState()
    var selectedMember by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val visible = records.filter { selectedMember == null || it.memberId == selectedMember }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Spacer(Modifier.height(24.dp)) }
            item {
                Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selectedMember == null, { selectedMember = null }, { Text(stringResource(R.string.all)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MBColor.Ink, selectedLabelColor = MBColor.Surface))
                    members.forEachIndexed { index, member ->
                        FilterChip(selectedMember == member.id, { selectedMember = member.id }, {
                            Row(verticalAlignment = Alignment.CenterVertically) { MemberDot(member.name, index, Modifier.size(26.dp)); Spacer(Modifier.width(6.dp)); Text(member.name) }
                        }, colors = FilterChipDefaults.filterChipColors(containerColor = MBColor.Surface, selectedContainerColor = MBColor.Surface))
                    }
                }
            }
            if (visible.isEmpty()) item { EmptyState(Icons.Rounded.Description, stringResource(R.string.no_records), stringResource(R.string.no_records_hint)) }
            visible.groupBy { LocalDate.ofEpochDay(it.visitDateEpochDay).year }.forEach { (year, yearRecords) ->
                item { Text(stringResource(R.string.year_format, year), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge) }
                items(yearRecords, key = { it.id }) { record ->
                    RecordListCard(vm, record, members, onRecord)
                }
            }
        }
        BottomCenterAddButton(
            contentDescription = stringResource(R.string.add),
            onClick = { showEditor = true },
        )
    }
    if (showEditor) RecordEditorDialog(null, emptyList(), members, { showEditor = false }) { record, prescriptions -> vm.saveRecord(record, prescriptions); showEditor = false }
}

@Composable
private fun LazyItemScope.RecordListCard(vm: MainViewModel, record: MedicalRecord, members: List<FamilyMember>, onRecord: (String) -> Unit) {
    val bundle by remember(record.id) { vm.observeRecordBundle(record.id) }.collectAsState(initial = null)
    val member = members.firstOrNull { it.id == record.memberId }
    val memberIndex = members.indexOf(member).coerceAtLeast(0)
    PaperCard(Modifier.fillParentMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clickable { onRecord(record.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberDot(member?.name.orEmpty().ifBlank { "?" }, memberIndex, Modifier.size(34.dp))
            Spacer(Modifier.width(8.dp))
            Text(member?.name.orEmpty(), color = MBColor.Ink2, modifier = Modifier.weight(1f))
            Text(LocalDate.ofEpochDay(record.visitDateEpochDay).format(DateTimeFormatter.ofPattern("yyyy/M/d")), color = MBColor.Ink3)
        }
        Spacer(Modifier.height(8.dp))
        Text(listOf(record.hospitalName, record.department).filter(String::isNotBlank).joinToString(" · ").ifBlank { stringResource(R.string.hospital_missing) }, style = MaterialTheme.typography.titleLarge)
        Text(record.diagnosis.ifBlank { stringResource(R.string.details_missing) }, color = MBColor.Ink3, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (record.followUpDateEpochDay != null) StatusBadge(stringResource(R.string.follow_up), BadgeKind.Info)
            bundle?.prescriptions?.takeIf { it.isNotEmpty() }?.let { StatusBadge("${stringResource(R.string.prescription)} ${it.size}", BadgeKind.Success) }
            bundle?.attachments?.takeIf { it.isNotEmpty() }?.let { StatusBadge("${stringResource(R.string.attachments)} ${it.size}", BadgeKind.AI) }
        }
    }
}

@Composable
private fun RecordEditorDialog(initial: MedicalRecord?, initialPrescriptions: List<Prescription>, members: List<FamilyMember>, dismiss: () -> Unit, save: (MedicalRecord, List<Prescription>) -> Unit) {
    var hospital by remember { mutableStateOf(initial?.hospitalName.orEmpty()) }; var department by remember { mutableStateOf(initial?.department.orEmpty()) }
    var doctor by remember { mutableStateOf(initial?.doctorName.orEmpty()) }; var complaint by remember { mutableStateOf(initial?.chiefComplaint.orEmpty()) }
    var diagnosis by remember { mutableStateOf(initial?.diagnosis.orEmpty()) }; var advice by remember { mutableStateOf(initial?.doctorAdvice.orEmpty()) }
    var memberId by remember { mutableStateOf(initial?.memberId ?: members.firstOrNull()?.id) }
    val initialPrescription = initialPrescriptions.firstOrNull()
    var medicineName by remember { mutableStateOf(initialPrescription?.medicineName.orEmpty()) }; var dosage by remember { mutableStateOf(initialPrescription?.dosage.orEmpty()) }; var frequency by remember { mutableStateOf(initialPrescription?.frequency.orEmpty()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(if (initial == null) R.string.add_record else R.string.edit_record)) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.member_owner), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { members.forEach { FilterChip(memberId == it.id, { memberId = it.id }, { Text(it.name) }) } }
            Field(hospital, { hospital = it }, stringResource(R.string.hospital)); Field(department, { department = it }, stringResource(R.string.department)); Field(doctor, { doctor = it }, stringResource(R.string.doctor))
            Field(complaint, { complaint = it }, stringResource(R.string.complaint), multiline = true); Field(diagnosis, { diagnosis = it }, stringResource(R.string.diagnosis), multiline = true); Field(advice, { advice = it }, stringResource(R.string.advice), multiline = true)
            HorizontalDivider(); Text(stringResource(R.string.prescription_optional), style = MaterialTheme.typography.labelLarge)
            Field(medicineName, { medicineName = it }, stringResource(R.string.medicine_name)); Field(dosage, { dosage = it }, stringResource(R.string.dosage)); Field(frequency, { frequency = it }, stringResource(R.string.frequency))
        }
    }, confirmButton = { Button(onClick = {
        if (hospital.isNotBlank() || diagnosis.isNotBlank()) {
            val record = (initial ?: MedicalRecord(memberId = memberId)).copy(memberId = memberId, hospitalName = hospital.trim(), department = department.trim(), doctorName = doctor.trim(), chiefComplaint = complaint.trim(), diagnosis = diagnosis.trim(), doctorAdvice = advice.trim())
            val prescriptions = if (medicineName.isBlank()) emptyList() else listOf((initialPrescription ?: Prescription(recordId = record.id, medicineName = medicineName)).copy(recordId = record.id, medicineName = medicineName.trim(), dosage = dosage.trim(), frequency = frequency.trim()))
            save(record, prescriptions)
        }
    }) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    val bundleFlow = remember(id) { vm.observeRecordBundle(id) }
    val bundle by bundleFlow.collectAsState(initial = null)
    val mediaStates by vm.mediaStates.collectAsState()
    val mediaState = mediaStates[id]
    val context = LocalContext.current
    var showCamera by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    val members by vm.members.collectAsState()
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.addRecordAttachment(id, it) } }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.record_details), fontWeight = FontWeight.SemiBold) },
            navigationIcon = { HeaderPill(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } },
            actions = { HeaderPill({ showEditor = true }, enabled = bundle != null) { Text(stringResource(R.string.edit)) } },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MBColor.Paper),
        )
    }, containerColor = MBColor.Paper) { padding ->
        val value = bundle
        if (value == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PaperCard(Modifier.fillParentMaxWidth()) {
                    Text(stringResource(R.string.basic_information), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    LabeledValue(stringResource(R.string.member_owner), value.member?.name.orEmpty())
                    LabeledValue(stringResource(R.string.date), LocalDate.ofEpochDay(value.record.visitDateEpochDay).format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
                    LabeledValue(stringResource(R.string.hospital), value.record.hospitalName.ifBlank { stringResource(R.string.not_entered) })
                    LabeledValue(stringResource(R.string.department), value.record.department.ifBlank { stringResource(R.string.not_entered) })
                    LabeledValue(stringResource(R.string.doctor), value.record.doctorName.ifBlank { stringResource(R.string.not_entered) })
                }
            }
            item {
                PaperCard(Modifier.fillParentMaxWidth()) {
                    Text(stringResource(R.string.diagnosis_information), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    LabeledValue(stringResource(R.string.complaint), value.record.chiefComplaint.ifBlank { stringResource(R.string.not_entered) }, stacked = true)
                    LabeledValue(stringResource(R.string.diagnosis), value.record.diagnosis.ifBlank { stringResource(R.string.not_entered) }, stacked = true)
                    LabeledValue(stringResource(R.string.advice), value.record.doctorAdvice.ifBlank { stringResource(R.string.not_entered) }, stacked = true)
                }
            }
            item { Text(stringResource(R.string.prescription), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
            if (value.prescriptions.isEmpty()) item { PaperCard(Modifier.fillParentMaxWidth()) { Text(stringResource(R.string.no_prescription), color = MBColor.Ink3) } }
            items(value.prescriptions) { prescription ->
                PaperCard(Modifier.fillParentMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Medication, null, tint = MBColor.Primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(prescription.medicineName, style = MaterialTheme.typography.titleLarge)
                            Text(listOf(prescription.dosage, prescription.frequency, prescription.durationDays.takeIf { it > 0 }?.let { "$it" }).filterNotNull().filter(String::isNotBlank).joinToString(" · "), color = MBColor.Ink3)
                            if (prescription.note.isNotBlank()) Text(prescription.note, color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
                        }
                        FilledTonalButton({ vm.syncPrescription(value.record, value.member, prescription) }) { Text(stringResource(R.string.add_to_box)) }
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.attachments), "Attachments") { Row { IconButton({ showCamera = true }) { Icon(Icons.Rounded.CameraAlt, stringResource(R.string.capture)) }; IconButton({ attachmentPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Rounded.PhotoLibrary, stringResource(R.string.gallery)) } } } }
            mediaState?.let { state -> item { MediaProcessNotice(state) } }
            if (value.attachments.isEmpty()) item { PaperCard(Modifier.fillParentMaxWidth()) { Text(stringResource(R.string.attachment_hint), color = MBColor.Ink3) } }
            items(value.attachments, key = { it.id }) { attachment ->
                PaperCard(Modifier.fillParentMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        StoredThumbnail(attachment.thumbnailPath ?: attachment.imagePath)
                        Column(Modifier.weight(1f)) {
                            StatusBadge(attachment.type.name, BadgeKind.Info)
                            Spacer(Modifier.height(6.dp))
                            Text(attachment.aiSummary.ifBlank { attachment.ocrText.take(220).ifBlank { stringResource(R.string.no_ocr) } }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = {
                val uri = PdfExporter(context).export(value)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, context.getString(R.string.share_pdf)))
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_pdf)) } }
            item { Button(onClick = { vm.deleteRecord(value.record); onBack() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_record)) } }
        }
    }
    if (showCamera) CameraCaptureDialog({ showCamera = false }) { vm.addRecordAttachment(id, it); showCamera = false }
    if (showEditor && bundle != null) RecordEditorDialog(bundle!!.record, bundle!!.prescriptions, members, { showEditor = false }) { record, prescriptions -> vm.saveRecord(record, prescriptions); showEditor = false }
}

@Composable
private fun HeaderPill(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp).clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp), color = MBColor.Surface, shadowElevation = 2.dp,
    ) { Row(Modifier.padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content) }
}

@Composable
private fun LabeledValue(label: String, value: String, stacked: Boolean = false) {
    if (stacked) Column(Modifier.padding(vertical = 5.dp)) {
        Text(label, color = MBColor.Ink3)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    } else Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = MBColor.Ink3, modifier = Modifier.width(84.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
fun MedicinesScreen(vm: MainViewModel, onMedicine: (String?) -> Unit) {
    val medicines by vm.medicines.collectAsState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(24.dp)) }
            if (medicines.isEmpty()) item { EmptyState(Icons.Rounded.Medication, stringResource(R.string.empty_medicine_box), stringResource(R.string.empty_medicine_hint)) }
            items(medicines, key = { it.id }) { medicine -> MedicineListCard(vm, medicine) { onMedicine(medicine.id) } }
        }
        BottomCenterAddButton(
            contentDescription = stringResource(R.string.add_medicine),
            onClick = { onMedicine(null) },
        )
    }
}

@Composable
private fun BoxScope.BottomCenterAddButton(contentDescription: String, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).size(58.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        containerColor = MBColor.Surface,
        contentColor = MBColor.Ink,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 5.dp, pressedElevation = 2.dp),
    ) {
        Icon(Icons.Rounded.Add, contentDescription, modifier = Modifier.size(31.dp))
    }
}

@Composable
private fun LazyItemScope.MedicineListCard(vm: MainViewModel, medicine: MedicineItem, onClick: () -> Unit) {
    val scans by remember(medicine.id) { vm.observeMedicineScans(medicine.id) }.collectAsState(initial = emptyList())
    PaperCard(Modifier.fillParentMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(medicine.name.ifBlank { stringResource(R.string.pending_medicine) }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            StatusBadge("${stringResource(R.string.stock)} ${medicine.stock}", if (medicine.stock <= 5) BadgeKind.Warning else BadgeKind.Success)
        }
        Spacer(Modifier.height(5.dp))
        Text(listOf(medicine.dosage, medicine.frequency).filter(String::isNotBlank).joinToString(" · ").ifBlank { stringResource(R.string.medication_missing) }, color = MBColor.Ink2, style = MaterialTheme.typography.bodyLarge)
        if (medicine.aiSummary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                StatusBadge(stringResource(R.string.ai_summary), BadgeKind.AI); Spacer(Modifier.width(8.dp))
                Text(medicine.aiSummary, color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium, maxLines = 2, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (scans.isNotEmpty()) { StatusBadge("${stringResource(R.string.images)} ${scans.size}", BadgeKind.Info); Spacer(Modifier.width(8.dp)) }
            Text(medicine.sourceHospital.ifBlank { stringResource(R.string.manual_source) }, color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineEditorScreen(vm: MainViewModel, medicineId: String?, onBack: () -> Unit) {
    val medicines by vm.medicines.collectAsState()
    val initial = medicineId?.let { id -> medicines.firstOrNull { it.id == id } }
    if (medicineId != null && initial == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    key(medicineId) { MedicineEditorContent(vm, initial, onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineEditorContent(vm: MainViewModel, initial: MedicineItem?, onBack: () -> Unit) {
    val draft = remember(initial?.id) { initial ?: MedicineItem(name = "") }
    var name by remember { mutableStateOf(draft.name) }; var dosage by remember { mutableStateOf(draft.dosage) }
    var frequency by remember { mutableStateOf(draft.frequency) }; var stock by remember { mutableStateOf(draft.stock.toString()) }
    var duration by remember { mutableIntStateOf(draft.durationDays) }; var note by remember { mutableStateOf(draft.note) }
    var times by remember { mutableStateOf(draft.scheduledTimes.joinToString(",")) }; var active by remember { mutableStateOf(draft.isActive) }
    fun currentMedicine() = draft.copy(name = name.trim(), dosage = dosage.trim(), frequency = frequency.trim(), durationDays = duration, note = note.trim(), stock = stock.toIntOrNull() ?: 0, scheduledTimes = times.split(',').map(String::trim).filter { Regex("\\d{2}:\\d{2}").matches(it) }, isActive = active)
    val scanPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.addMedicineScan(currentMedicine(), it) } }
    var showCamera by remember { mutableStateOf(false) }
    val scansFlow = remember(draft.id) { vm.observeMedicineScans(draft.id) }
    val scans by scansFlow.collectAsState(initial = emptyList())
    val mediaStates by vm.mediaStates.collectAsState()
    val mediaState = mediaStates[draft.id]
    val feedback by vm.backupMessage.collectAsState()
    Scaffold(
        containerColor = MBColor.Paper,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(if (initial == null) R.string.add_medicine else R.string.edit_medicine), fontWeight = FontWeight.SemiBold) },
                navigationIcon = { HeaderPill(onBack) { Text(stringResource(R.string.cancel)) } },
                actions = { HeaderPill({ vm.saveMedicine(currentMedicine()); onBack() }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save), color = if (name.isNotBlank()) MBColor.Ink else MBColor.Ink3) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MBColor.Paper),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(stringResource(R.string.complete_medicine_info), style = MaterialTheme.typography.titleLarge, color = MBColor.Ink3, fontWeight = FontWeight.SemiBold) }
            item {
                PaperCard(Modifier.fillParentMaxWidth()) {
                    EditorLineField(name, { name = it }, stringResource(R.string.medicine_name))
                    HorizontalDivider(color = MBColor.Hairline)
                    EditorLineField(dosage, { dosage = it }, stringResource(R.string.dosage))
                    HorizontalDivider(color = MBColor.Hairline)
                    EditorLineField(frequency, { frequency = it }, stringResource(R.string.frequency))
                    HorizontalDivider(color = MBColor.Hairline)
                    StepperRow(stringResource(R.string.duration), duration, { duration = (duration - 1).coerceAtLeast(0) }, { duration += 1 })
                    HorizontalDivider(color = MBColor.Hairline)
                    StepperRow(stringResource(R.string.stock), stock.toIntOrNull() ?: 0, { stock = ((stock.toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString() }, { stock = ((stock.toIntOrNull() ?: 0) + 1).toString() })
                    HorizontalDivider(color = MBColor.Hairline)
                    EditorLineField(note, { note = it }, stringResource(R.string.advice), singleLine = false)
                }
            }
            if (draft.aiSummary.isNotBlank()) {
                item { Text(stringResource(R.string.model_summary), style = MaterialTheme.typography.titleLarge, color = MBColor.Ink3, fontWeight = FontWeight.SemiBold) }
                item { PaperCard(Modifier.fillParentMaxWidth()) { Text(draft.aiSummary, style = MaterialTheme.typography.bodyLarge) } }
            }
            item { Text(stringResource(R.string.smart_organize), style = MaterialTheme.typography.titleLarge, color = MBColor.Ink3, fontWeight = FontWeight.SemiBold) }
            item {
                PaperCard(Modifier.fillParentMaxWidth()) {
                    ActionRow(Icons.Rounded.AutoAwesome, stringResource(R.string.organize_ocr), enabled = draft.aggregatedOcrText.isNotBlank() || scans.any { it.ocrText.isNotBlank() }) { vm.organizeMedicine(currentMedicine()) }
                    HorizontalDivider(Modifier.padding(start = 42.dp), color = MBColor.Hairline)
                    ActionRow(Icons.Rounded.Visibility, stringResource(R.string.vision_extract), enabled = scans.isNotEmpty()) { vm.organizeMedicine(currentMedicine()) }
                }
            }
            item { Text(stringResource(R.string.scan_notice), color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium) }
            item {
                PaperCard(Modifier.fillParentMaxWidth()) {
                    ActionRow(Icons.Rounded.AddAPhoto, stringResource(R.string.add_photo_ocr)) { scanPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    HorizontalDivider(Modifier.padding(start = 42.dp), color = MBColor.Hairline)
                    ActionRow(Icons.Rounded.CameraAlt, stringResource(R.string.capture_box)) { showCamera = true }
                    if (scans.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(start = 42.dp), color = MBColor.Hairline)
                        Text("${stringResource(R.string.collected_images)} · ${scans.size}", modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { scans.take(5).forEach { StoredThumbnail(it.thumbnailPath ?: it.imagePath, Modifier.size(72.dp)) } }
                    }
                }
            }
            mediaState?.let { item { MediaProcessNotice(it) } }
            item { Text(stringResource(R.string.medication_plan), style = MaterialTheme.typography.titleLarge, color = MBColor.Ink3, fontWeight = FontWeight.SemiBold) }
            item {
                PaperCard(Modifier.fillParentMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.enable_plan), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(active, { active = it }) }
                    if (active) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MBColor.Hairline)
                        EditorLineField(times, { times = it }, stringResource(R.string.medication_times))
                    }
                }
            }
            if (initial != null || scans.isNotEmpty()) item { TextButton({ vm.deleteMedicine(currentMedicine()); onBack() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_medicine)) } }
        }
    }
    if (showCamera) CameraCaptureDialog({ showCamera = false }) { vm.addMedicineScan(currentMedicine(), it); showCamera = false }
    feedback?.let { AlertDialog(onDismissRequest = vm::clearBackupMessage, confirmButton = { TextButton(vm::clearBackupMessage) { Text(stringResource(R.string.ok)) } }, text = { Text(it) }) }
}

@Composable
private fun EditorLineField(value: String, change: (String) -> Unit, placeholder: String, singleLine: Boolean = true) {
    TextField(
        value = value, onValueChange = change, modifier = Modifier.fillMaxWidth(), placeholder = { Text(placeholder, color = MBColor.Ink3) },
        singleLine = singleLine, colors = TextFieldDefaults.colors(focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun StepperRow(label: String, value: Int, decrease: () -> Unit, increase: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label: $value", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(22.dp), color = MBColor.Ink.copy(alpha = .06f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(decrease) { Icon(Icons.Rounded.Remove, null) }
                VerticalDivider(Modifier.height(28.dp), color = MBColor.Hairline)
                IconButton(increase) { Icon(Icons.Rounded.Add, null) }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (enabled) MBColor.Primary else MBColor.Ink3, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(14.dp))
        Text(label, color = if (enabled) MBColor.Primary else MBColor.Ink3, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable private fun Field(value: String, change: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text, multiline: Boolean = false) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = keyboard), singleLine = !multiline)
}

@Composable
private fun StoredThumbnail(path: String?, modifier: Modifier = Modifier.size(88.dp)) {
    val context = LocalContext.current
    val bitmap = remember(path) {
        path?.let { runCatching { BitmapFactory.decodeFile(context.filesDir.resolve(it).absolutePath) }.getOrNull() }
    }
    if (bitmap == null) {
        Box(modifier.clip(RoundedCornerShape(14.dp)).background(MBColor.PrimarySoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Photo, stringResource(R.string.saved_photo), tint = MBColor.Primary)
        }
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.saved_photo),
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(14.dp)),
        )
    }
}

@Composable
private fun MediaProcessNotice(state: MediaProcessState) {
    val (background, foreground) = when (state.stage) {
        MediaProcessStage.SAVED -> MBColor.SuccessSoft to MBColor.Success
        MediaProcessStage.ERROR -> MBColor.WarningSoft to MBColor.Warning
        else -> MBColor.AISoft to MBColor.AI
    }
    val title = when (state.stage) {
        MediaProcessStage.SAVING -> stringResource(R.string.photo_saving)
        MediaProcessStage.RECOGNIZING -> stringResource(R.string.ocr_recognizing)
        MediaProcessStage.SAVED -> if (state.recognizedCharacters > 0) stringResource(R.string.ocr_complete_count, state.recognizedCharacters) else stringResource(R.string.photo_saved_no_text)
        MediaProcessStage.ERROR -> stringResource(if (state.photoSaved) R.string.photo_saved_ocr_failed else R.string.photo_save_failed)
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = background) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.stage == MediaProcessStage.SAVING || state.stage == MediaProcessStage.RECOGNIZING) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = foreground)
            } else Icon(if (state.stage == MediaProcessStage.SAVED) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, tint = foreground)
            Column(Modifier.weight(1f)) {
                Text(title, color = foreground, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                state.error?.takeIf(String::isNotBlank)?.let { Text(it.take(160), color = foreground, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
fun QueryScreen(vm: MainViewModel) {
    val state by vm.query.collectAsState(); val acknowledged by vm.riskAcknowledged.collectAsState(); var question by remember { mutableStateOf("") }; var webSearch by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.running) {
        if (state.running) {
            kotlinx.coroutines.delay(120)
            listState.animateScrollToItem(3)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.local_query), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Surface(
                    modifier = Modifier.align(Alignment.CenterEnd).height(48.dp).clickable { question = ""; vm.clearQuery() },
                    shape = RoundedCornerShape(24.dp), color = MBColor.Surface, shadowElevation = 2.dp,
                ) { Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.clear), color = if (question.isNotEmpty() || state.answer.isNotEmpty()) MBColor.Ink else MBColor.Ink3) } }
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            PaperCard(Modifier.fillParentMaxWidth()) {
                Text(stringResource(R.string.usage_tips), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.ai_risk), color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.query_scope_notice), color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
                if (!acknowledged) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(false, { vm.acknowledgeRisk(it) }); Text(stringResource(R.string.risk_ack)) }
            }
        }
        item {
            PaperCard(Modifier.fillParentMaxWidth()) {
                Text(stringResource(R.string.local_query), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextField(
                    value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                    placeholder = { Text(stringResource(R.string.query_example), color = MBColor.Ink3) },
                    colors = TextFieldDefaults.colors(focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Language, null, tint = MBColor.Ink3); Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.use_web_search), color = MBColor.Ink2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(webSearch, { webSearch = it })
                }
                Text(stringResource(R.string.web_search_notice), color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Button({ vm.ask(question) }, enabled = acknowledged && question.isNotBlank() && !state.running, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) {
                    Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.query_local))
                }
                if (state.running) TextButton(vm::cancelQuery, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
        }
        if (state.running || state.answer.isNotBlank() || state.error != null) item {
            PaperCard(Modifier.fillParentMaxWidth()) {
                SectionLabel(stringResource(R.string.answer), "Grounded answer")
                Spacer(Modifier.height(12.dp))
                if (state.running && state.stage != QueryProgressStage.STREAMING) {
                    QueryProcessingPhrase(state.stage)
                } else {
                    Text(
                        state.streamed.ifBlank { state.answer }.ifBlank { state.error.orEmpty() },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (state.answer.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    StatusBadge(stringResource(R.string.grounded), BadgeKind.Success)
                }
            }
        }
    }
}

@Composable
private fun QueryProcessingPhrase(stage: QueryProgressStage) {
    val transition = rememberInfiniteTransition(label = "queryProcessing")
    val alpha by transition.animateFloat(
        initialValue = .35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "queryProcessingAlpha",
    )
    val label = stringResource(
        when (stage) {
            QueryProgressStage.SEARCHING -> R.string.query_searching
            QueryProgressStage.ORGANIZING -> R.string.query_organizing
            else -> R.string.query_thinking
        },
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = MBColor.AI, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = MBColor.AI, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val members by vm.members.collectAsState(); var showMember by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<FamilyMember?>(null) }
    val modelState by vm.modelState.collectAsState(); val backupMessage by vm.backupMessage.collectAsState()
    val context = LocalContext.current
    var showLanguage by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<Pair<String, android.net.Uri>?>(null) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { pendingBackup = "export" to it } }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { pendingBackup = "import" to it } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader(stringResource(R.string.tab_settings), "Settings") }
        item { PaperCard(Modifier.fillParentMaxWidth().clickable { showLanguage = true }) { SettingRow(Icons.Rounded.Language, stringResource(R.string.language), stringResource(R.string.language_desc)); Spacer(Modifier.height(8.dp)); Text(languageName(LocaleController.currentTag(context)), color = MBColor.Primary) } }
        item { SectionLabel(stringResource(R.string.family_members), "Family") { IconButton({ editing = null; showMember = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.add_member), tint = MBColor.Primary) } } }
        items(members) { member -> PaperCard(Modifier.fillParentMaxWidth().clickable { editing = member; showMember = true }) { Row(verticalAlignment = Alignment.CenterVertically) { MemberDot(member.name, members.indexOf(member)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(member.name, style = MaterialTheme.typography.titleLarge); Text(member.relationship, color = MBColor.Ink3) }; Icon(Icons.Rounded.ChevronRight, null, tint = MBColor.Ink3) } } }
        item { SectionLabel(stringResource(R.string.data_privacy), "Data & privacy") }
        item { PaperCard(Modifier.fillParentMaxWidth()) { SettingRow(Icons.Rounded.Lock, stringResource(R.string.local_storage), stringResource(R.string.local_storage_desc)); HorizontalDivider(Modifier.padding(vertical = 10.dp)); SettingRow(Icons.Rounded.Backup, stringResource(R.string.encrypted_backup), stringResource(R.string.backup_desc)); Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ createBackup.launch("MedicineBoxNotes-${LocalDate.now()}.mbn") }) { Text(stringResource(R.string.export)) }; OutlinedButton({ openBackup.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }) { Text(stringResource(R.string.import_data)) } } } }
        item { SectionLabel(stringResource(R.string.ai_features), "On-device AI") }
        item { PaperCard(Modifier.fillParentMaxWidth()) { SettingRow(Icons.Rounded.AutoAwesome, stringResource(R.string.rule_mode), stringResource(R.string.rule_mode_desc)); HorizontalDivider(Modifier.padding(vertical = 10.dp)); SettingRow(Icons.Rounded.Download, "Gemma 4 E2B", modelState.label()); Text(stringResource(R.string.download_source), color = MBColor.Ink3, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(10.dp)); when (modelState) { is ModelDownloadState.Downloading -> { val s = modelState as ModelDownloadState.Downloading; LinearProgressIndicator(progress = { if (s.total == null) 0f else (s.bytes.toFloat()/s.total.toFloat()).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth()); TextButton(vm::pauseModelDownload) { Text(stringResource(R.string.pause)) } }; is ModelDownloadState.Ready -> OutlinedButton(vm::deleteModel) { Text(stringResource(R.string.delete_model)) }; else -> Button(vm::startModelDownload) { Text(stringResource(if (modelState is ModelDownloadState.Paused) R.string.continue_download else R.string.download_model)) } } } }
        item { Text("MedicineBoxNotes Android 1.0.0", color = MBColor.Ink3, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth().padding(20.dp)) }
    }
    if (showMember) MemberEditorDialog(editing, { showMember = false }, { member -> vm.deleteMember(member); showMember = false }) { vm.saveMember(it); showMember = false }
    pendingBackup?.let { (action, uri) -> PasswordDialog(action == "export", dismiss = { pendingBackup = null }) { password -> if (action == "export") vm.exportBackup(uri, password) else vm.importBackup(uri, password); pendingBackup = null } }
    if (showLanguage) LanguageDialog(LocaleController.currentTag(context), { showLanguage = false }) { tag -> showLanguage = false; LocaleController.select(context.findActivity(), tag) }
    backupMessage?.let { AlertDialog(onDismissRequest = vm::clearBackupMessage, confirmButton = { TextButton(vm::clearBackupMessage) { Text(stringResource(R.string.ok)) } }, text = { Text(it) }) }
}

@Composable private fun ModelDownloadState.label(): String = when (this) { ModelDownloadState.NotDownloaded -> stringResource(R.string.model_size); is ModelDownloadState.Downloading -> stringResource(R.string.model_downloading, bytes / 1_000_000); is ModelDownloadState.Paused -> stringResource(R.string.model_paused); is ModelDownloadState.Ready -> stringResource(R.string.model_ready); is ModelDownloadState.Failed -> message }

@Composable private fun PasswordDialog(exporting: Boolean, dismiss: () -> Unit, submit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(if (exporting) R.string.set_backup_password else R.string.enter_backup_password)) }, text = { Column { Text(stringResource(R.string.password_notice), color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(10.dp)); OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.backup_password)) }, singleLine = true) } }, confirmButton = { Button({ submit(password) }, enabled = password.length >= 8) { Text(stringResource(if (exporting) R.string.encrypt_export else R.string.decrypt_import)) } }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MBColor.Primary); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium) } } }

@Composable private fun MemberEditorDialog(initial: FamilyMember?, dismiss: () -> Unit, delete: (FamilyMember) -> Unit, save: (FamilyMember) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var relationship by remember(initial?.id) { mutableStateOf(initial?.relationship.orEmpty()) }
    val cleanName = name.trim()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_member else R.string.edit_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, stringResource(R.string.name))
                Field(relationship, { relationship = it }, stringResource(R.string.relationship))
                if (initial != null) TextButton(
                    { delete(initial) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete_member)) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = initial ?: FamilyMember(name = cleanName, relationship = relationship.trim(), emoji = cleanName.take(1))
                    save(base.copy(name = cleanName, relationship = relationship.trim(), emoji = cleanName.take(1)))
                },
                enabled = cleanName.isNotEmpty(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable private fun languageName(tag: String): String = stringResource(when (tag) { "zh-CN" -> R.string.language_chinese; "ja" -> R.string.language_japanese; "fr" -> R.string.language_french; "de" -> R.string.language_german; "es" -> R.string.language_spanish; "ko" -> R.string.language_korean; else -> R.string.language_english })

@Composable private fun LanguageDialog(current: String, dismiss: () -> Unit, select: (String) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.language)) }, text = { Column { LocaleController.supported.forEach { tag -> Row(Modifier.fillMaxWidth().clickable { select(tag) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(current == tag, { select(tag) }); Text(languageName(tag)) } } } }, confirmButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

private tailrec fun Context.findActivity(): android.app.Activity = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Language selection requires an Activity context")
}
