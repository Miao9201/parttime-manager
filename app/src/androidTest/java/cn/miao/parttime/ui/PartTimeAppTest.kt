package cn.miao.parttime.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import cn.miao.parttime.data.AppDatabase
import cn.miao.parttime.data.AppRepository
import cn.miao.parttime.theme.Theme
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Cancelling an edit must never write the draft into the saved record. */
class PartTimeAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before fun isolatedDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = AppRepository(database)
        compose.setContent {
            val model: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(repository) } })
            Theme { PartTimeApp(model) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("create_day").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun closeIsolatedDatabase() {
        compose.activityRule.scenario.close()
        database.close()
    }

    @Test fun cancellingCreationDoesNotAddARecord() {
        val name = "不应保存" + System.currentTimeMillis()
        compose.onNodeWithTag("create_day").performClick()
        compose.onNodeWithTag("day_location").performTextInput(name)
        compose.onNodeWithTag("cancel_day").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithText(name).assertDoesNotExist()
        compose.onNodeWithTag("create_day").assertExists()
    }

    @Test fun longListKeepsCreateVisibleAndLongLocationKeepsShiftsReachable() {
        val longLocation = "超长地点名称".repeat(33)
        runBlocking {
            repeat(30) { repository.saveDay(null, "列表地点$it", LocalDate.now().plusDays(it.toLong()).toString()) }
            repository.saveDay(null, longLocation, LocalDate.now().toString())
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("列表地点29").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home_list").performScrollToNode(hasText(longLocation))
        compose.onNodeWithTag("create_day").assertIsDisplayed()
        compose.onNodeWithText(longLocation).performClick()
        compose.onNodeWithTag("day_list").performScrollToNode(hasTestTag("shift_NOON"))
        compose.onNodeWithTag("shift_NOON").assertIsDisplayed().performClick()
        compose.onNodeWithTag("add_person").assertIsDisplayed()
    }

    @Test fun createThenEditAndCancelPreservesSavedPerson() {
        val location = "界面验证" + System.currentTimeMillis()
        compose.onNodeWithTag("create_day").performClick()
        compose.onNodeWithTag("day_location").performTextInput(location)
        compose.onNodeWithTag("save_day").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("shift_NOON").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shift_NOON").performClick()
        compose.onNodeWithTag("add_person").performClick()
        compose.onNodeWithTag("person_name").performTextInput("张三")
        selectTask("传菜")
        compose.onNodeWithTag("person_amount").performScrollTo().performTextInput("100.25")
        compose.onNodeWithTag("save_person").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("save_person").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("编辑").performScrollTo().performClick()
        compose.onNodeWithTag("person_name").performTextReplacement("未保存姓名")
        compose.onNodeWithTag("cancel_person").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithText("张三").assertExists()
        compose.onNodeWithText("未保存姓名").assertDoesNotExist()
        compose.onNodeWithText("编辑").performScrollTo().performClick()
        compose.onNodeWithTag("person_amount").performScrollTo().performTextReplacement("120.50")
        compose.onNodeWithTag("save_person").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("工资  120.50").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("工资  120.50").assertExists()
    }

    @Test fun overtimeDoesNotAddPeopleAndDuplicateNeedsExplicitConfirmation() {
        compose.onNodeWithTag("create_day").performClick()
        compose.onNodeWithTag("day_location").performTextInput("加班与重复验证")
        compose.onNodeWithTag("save_day").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("edit_fee").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("未设置").assertExists()
        compose.onNodeWithTag("edit_fee").performClick()
        compose.onNodeWithTag("head_fee").performTextInput("0")
        compose.onNodeWithTag("save_fee").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("0.00 / 人次").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shift_NOON").performClick()
        fillPerson("张三", "10")
        compose.onNodeWithTag("add_overtime").performScrollTo().performClick()
        compose.onNodeWithTag("ot_description_0_shortcut_1").performScrollTo().performClick()
        compose.onNodeWithTag("ot_description_0").assertTextContains("加班1小时")
        compose.onNodeWithTag("ot_amount_0").performScrollTo().performTextInput("20.25")
        compose.onNodeWithTag("add_overtime").performScrollTo().performClick()
        compose.onNodeWithTag("ot_description_1").performScrollTo().performTextInput("收尾")
        compose.onNodeWithTag("ot_amount_1").performScrollTo().performTextInput("5.00")
        compose.onNodeWithTag("save_person").performClick()
        waitForPersonSheetClosed()
        compose.onNodeWithText("1人").assertExists()
        compose.onNodeWithText("个人小计  35.25").performScrollTo().assertExists()
        compose.onNodeWithText("编辑").performScrollTo().performClick()
        compose.onNodeWithTag("ot_amount_0").performScrollTo().performTextReplacement("22.25")
        compose.onNodeWithTag("remove_ot_1").performScrollTo().performClick()
        compose.onNodeWithTag("save_person").performClick()
        waitForPersonSheetClosed()
        compose.onNodeWithText("个人小计  32.25").performScrollTo().assertExists()
        compose.onNodeWithText("＋ 收尾").assertDoesNotExist()
        fillPerson("张三", "10")
        compose.onNodeWithTag("save_person").performClick()
        compose.onNodeWithText("本班可能重复登记").assertExists()
        compose.onNodeWithText("返回检查").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("本班可能重复登记").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("person_name").assertTextContains("张三")
        compose.onNodeWithTag("person_amount").performScrollTo().performTextReplacement("12.50")
        compose.onNodeWithTag("person_amount").assertTextContains("12.50")
        compose.onNodeWithTag("cancel_person").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("放弃修改").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("放弃修改").performClick()
        waitForPersonSheetClosed()
        compose.onNodeWithText("1人").performScrollTo().assertExists()
        compose.onNodeWithText("个人小计  32.25").performScrollTo().assertExists()
        check(runBlocking { repository.days.first().single().people.size } == 1)
        fillPerson("张三", "10")
        compose.onNodeWithTag("save_person").performClick()
        compose.onNodeWithText("仍然保存").performClick()
        waitForPersonSheetClosed()
        compose.onNodeWithText("2人").performScrollTo().assertExists()
        compose.onNodeWithText("42.25").assertExists()
    }

    @Test fun taskMenuIsVerticalAndCustomTaskCanBeSavedWithoutPhoneOrStars() {
        openNewShift("任务菜单验证")
        compose.onNodeWithTag("add_person").performClick()
        compose.onNodeWithTag("person_phone").assertDoesNotExist()
        compose.onAllNodes(hasText("*", substring = true)).assertCountEquals(0)
        compose.onNodeWithTag("person_name").performTextInput("小陈")
        compose.onNodeWithTag("person_task").performClick()
        val choices = listOf("服务员", "传菜", "洗碗", "打荷", "自定义")
        val rows = choices.map { compose.onNodeWithTag("task_option_$it").assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        rows.zipWithNext().forEach { (first, second) -> check(first.bottom <= second.top) }
        compose.onNodeWithTag("task_option_自定义").performClick()
        compose.onNodeWithTag("person_custom_task").performTextInput("布置会场")
        compose.onNodeWithTag("person_amount").performScrollTo().performTextInput("99.05")
        compose.onNodeWithTag("save_person").performClick()
        waitForPersonSheetClosed()
        compose.onNodeWithText("布置会场").assertExists()
        compose.onNodeWithText("工资  99.05").assertExists()
        compose.onAllNodes(hasText("电话", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("¥", substring = true)).assertCountEquals(0)
    }

    @Test fun locationShortcutsAndRelativeDatesRemainEditableAboveCalendar() {
        compose.onNodeWithTag("create_day").performClick()
        compose.onNodeWithTag("location_香樟").performClick()
        compose.onNodeWithTag("day_location").assertTextContains("香樟")
        compose.onNodeWithTag("location_旺辉").performClick()
        compose.onNodeWithTag("day_location").assertTextContains("旺辉")
        compose.onNodeWithTag("day_location").performTextReplacement("自由地点")
        compose.onNodeWithTag("day_date").performScrollTo().assertIsDisplayed()
        val today = compose.onNodeWithTag("date_today").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val tomorrow = compose.onNodeWithTag("date_tomorrow").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val calendar = compose.onNodeWithTag("day_date").fetchSemanticsNode().boundsInRoot
        check(today.bottom <= calendar.top && tomorrow.bottom <= calendar.top)
        compose.onNodeWithTag("date_tomorrow").performClick()
        compose.onNodeWithTag("save_day").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("shift_NOON").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("自由地点")[0].assertExists()
    }

    @Test fun inlineOvertimeSavesEditsDeletesAndGuardsBackWithoutPersonSheet() {
        openNewShift("原位加班验证")
        fillPerson("小林", "100.25")
        compose.onNodeWithTag("save_person").performClick()
        waitForPersonSheetClosed()
        val id = runBlocking { repository.days.first().first().people.first().person.id }
        compose.onNodeWithTag("add_inline_ot_$id").performScrollTo().performClick()
        compose.onNodeWithTag("person_sheet").assertDoesNotExist()
        compose.onNodeWithTag("inline_ot_description_shortcut_1").performScrollTo().performClick()
        check(runBlocking { repository.days.first().single().people.single().overtime.isEmpty() })
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithText("继续填写").performClick()
        compose.onNodeWithTag("inline_ot_description").assertTextContains("加班1小时")
        compose.onNodeWithTag("inline_ot_amount").performScrollTo().performTextInput("20.15")
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        compose.onNodeWithTag("save_inline_ot").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("save_inline_ot").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("个人小计  120.40").performScrollTo().assertExists()
        check(runBlocking { repository.days.first().single().people.size } == 1)
        val otId = runBlocking { repository.days.first().first().people.first().overtime.first().id }
        compose.onNodeWithTag("edit_inline_ot_$otId").performScrollTo().performClick()
        compose.onNodeWithTag("inline_ot_amount").performScrollTo().performTextReplacement("21.15")
        compose.onNodeWithTag("edit_inline_ot_$otId").assertIsNotEnabled()
        compose.onNodeWithText("编辑").performScrollTo().performClick()
        compose.onNodeWithText("保存并继续").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("save_person").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ot_amount_0").performScrollTo().assertTextContains("21.15")
        compose.onNodeWithTag("save_person").performClick()
        waitForPersonSheetClosed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("个人小计  121.40").fetchSemanticsNodes().isNotEmpty() }
        val savedOtId = runBlocking { repository.days.first().single().people.single().overtime.single().id }
        compose.onNodeWithTag("delete_inline_ot_$savedOtId").performScrollTo().performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("个人小计  100.25").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("＋ 加班1小时").assertDoesNotExist()
        compose.onNodeWithTag("add_inline_ot_$id").performScrollTo().performClick()
        compose.onNodeWithTag("inline_ot_description").performScrollTo().performTextInput("不应保存")
        compose.onNodeWithTag("cancel_inline_ot").performScrollTo().performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithTag("save_inline_ot").assertDoesNotExist()
        compose.onNodeWithText("个人小计  100.25").performScrollTo().assertExists()
    }

    @Test fun exportRequiresSavingOrDiscardingTheHeadFeeDraft() {
        compose.onNodeWithTag("create_day").performClick()
        compose.onNodeWithTag("day_location").performTextInput("导出单价保护")
        compose.onNodeWithTag("save_day").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("edit_fee").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edit_fee").performClick()
        compose.onNodeWithTag("head_fee").performTextInput("8.25")
        compose.onNodeWithTag("export_day").performClick()
        compose.onNodeWithTag("export_DAY").assertDoesNotExist()
        compose.onNodeWithText("继续填写").performClick()
        compose.onNodeWithTag("head_fee").assertTextContains("8.25")
        compose.onNodeWithTag("export_day").performClick()
        compose.onNodeWithText("保存并继续").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("export_DAY").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("export_NOON").assertExists()
        compose.onNodeWithTag("export_EVENING").assertExists()
        check(runBlocking { repository.days.first().single().day.headFeeCents } == 825L)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("edit_fee").performClick()
        compose.onNodeWithTag("head_fee").performTextReplacement("99")
        compose.onNodeWithTag("export_day").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithTag("export_DAY").assertExists()
        check(runBlocking { repository.days.first().single().day.headFeeCents } == 825L)
    }

    private fun openNewShift(location: String) {
        compose.onNodeWithTag("create_day").performClick()
        compose.onNodeWithTag("day_location").performTextInput(location)
        compose.onNodeWithTag("save_day").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("shift_NOON").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shift_NOON").performClick()
    }

    private fun selectTask(task: String) {
        compose.onNodeWithTag("person_task").performScrollTo().performClick()
        compose.onNodeWithTag("task_option_$task").performClick()
    }

    private fun fillPerson(name: String, amount: String) {
        compose.onNodeWithTag("add_person").performClick()
        compose.onNodeWithTag("person_name").performTextInput(name)
        selectTask("传菜")
        compose.onNodeWithTag("person_amount").performScrollTo().performTextInput(amount)
    }

    private fun waitForPersonSheetClosed() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("save_person").fetchSemanticsNodes().isEmpty() }
    }
}
