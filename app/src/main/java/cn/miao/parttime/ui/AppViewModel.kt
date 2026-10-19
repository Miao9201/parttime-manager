package cn.miao.parttime.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.miao.parttime.data.*
import cn.miao.parttime.export.*
import cn.miao.parttime.domain.Money
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppState(
    val days: List<DayBundle> = emptyList(),
    val locations: List<SavedLocation> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val busy: Boolean = false,
    val exporting: Boolean = false,
    val savingReport: Boolean = false,
    val report: ReportArtifact? = null,
    val notice: String? = null,
)

private data class ActionState(val busy: Boolean = false, val error: String? = null)
private data class ExportState(val exporting: Boolean = false, val saving: Boolean = false,
    val report: ReportArtifact? = null, val notice: String? = null)

class AppViewModel(private val repository: AppRepository, private val reportExporter: ReportExporter? = null) : ViewModel() {
    private val actions = MutableStateFlow(ActionState())
    private val exports = MutableStateFlow(ExportState())
    val state: StateFlow<AppState> = combine(repository.days, repository.locations, actions, exports) { days, locations, action, export ->
        AppState(days, locations, loading = false, error = action.error, busy = action.busy,
            exporting = export.exporting, savingReport = export.saving, report = export.report, notice = export.notice)
    }.catch { error -> emit(AppState(loading = false, error = "读取本地数据失败：${error.message ?: "请重新打开应用"}")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    fun clearError() { actions.update { it.copy(error = null) } }

    private fun mutate(block: suspend () -> Unit) {
        if (actions.value.busy) return
        actions.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val message = when (error) {
                    is ArithmeticException -> "金额合计超出可保存范围，请减小金额"
                    is DuplicateDay -> "该地点当天已有记录，请进入已有记录"
                    is DuplicatePerson -> "本班已有同姓名人员，请确认是否需要重复登记"
                    is IllegalArgumentException -> error.message ?: "请检查填写内容"
                    else -> "保存失败，原有数据已保留。${error.message ?: "请重试"}"
                }
                actions.update { it.copy(error = message) }
            } finally { actions.update { it.copy(busy = false) } }
        }
    }

    fun saveDay(id: Long?, location: String, date: String, onSaved: (Long) -> Unit) =
        mutate { onSaved(repository.saveDay(id, location, date)) }

    fun saveHeadFee(dayId: Long, text: String, onSaved: () -> Unit) =
        mutate { repository.setHeadFee(dayId, text); onSaved() }

    fun deleteDay(dayId: Long, onSaved: () -> Unit) =
        mutate { repository.deleteDay(dayId); onSaved() }

    fun savePerson(dayId: Long, shift: ShiftKind, id: Long?, draft: PersonDraft, allowDuplicate: Boolean = false, onSaved: (Long) -> Unit) =
        mutate { onSaved(repository.savePerson(dayId, shift, id, draft, allowDuplicate)) }

    fun deletePerson(id: Long, onSaved: () -> Unit) =
        mutate { repository.deletePerson(id); onSaved() }

    fun saveOvertime(personId: Long, itemId: Long?, draft: OvertimeDraft, onSaved: () -> Unit) =
        mutate {
            val id = repository.saveOvertime(personId, itemId, draft)
            state.first { snapshot -> snapshot.days.any { day -> day.people.any { person ->
                person.person.id == personId && person.overtime.any {
                    it.id == id && it.description == draft.description.trim() && it.amountCents == Money.parse(draft.amount)
                }
            } } }
            onSaved()
        }

    fun deleteOvertime(personId: Long, itemId: Long, onSaved: () -> Unit) =
        mutate {
            repository.deleteOvertime(personId, itemId)
            state.first { snapshot -> snapshot.days.any { day -> day.people.any { person ->
                person.person.id == personId && person.overtime.none { it.id == itemId }
            } } }
            onSaved()
        }

    fun clearNotice() { exports.update { it.copy(notice = null) } }
    fun clearReport() { if (!exports.value.saving) exports.update { it.copy(report = null) } }

    fun exportReport(dayId: Long, scope: ReportScope) {
        if (exports.value.exporting || exports.value.saving || actions.value.busy) return
        exports.update { it.copy(exporting = true, report = null, notice = null) }
        actions.update { it.copy(error = null) }
        viewModelScope.launch {
            try {
                val exporter = requireNotNull(reportExporter) { "当前无法生成导出图片" }
                val day = repository.days.first().firstOrNull { it.day.id == dayId }
                    ?: error("此地点日期记录已不存在")
                val artifact = exporter.create(day, scope)
                exports.update { it.copy(report = artifact) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                actions.update { it.copy(error = "生成费用图片失败：${error.message ?: "请重试"}") }
            } finally { exports.update { it.copy(exporting = false) } }
        }
    }

    fun saveReport() = saveExport { exporter, artifact -> exporter.save(artifact); "已保存到相册的 Pictures/兼职管理器" }
    fun saveReportTo(uri: Uri) = saveExport { exporter, artifact -> exporter.saveTo(artifact, uri); "费用图片已保存" }

    private fun saveExport(block: suspend (ReportExporter, ReportArtifact) -> String) {
        val artifact = exports.value.report ?: return
        if (exports.value.saving || exports.value.exporting) return
        exports.update { it.copy(saving = true, notice = null) }
        viewModelScope.launch {
            try {
                val message = block(requireNotNull(reportExporter), artifact)
                exports.update { it.copy(notice = message) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { actions.update { it.copy(error = "保存图片失败：${error.message ?: "请重试"}") } }
            finally { exports.update { it.copy(saving = false) } }
        }
    }

    fun reportShareIntent(): Intent? {
        val artifact = exports.value.report ?: return null
        return try { reportExporter?.shareIntent(artifact) }
        catch (error: Exception) { actions.update { it.copy(error = "分享图片失败：${error.message ?: "请重试"}") }; null }
    }

}
