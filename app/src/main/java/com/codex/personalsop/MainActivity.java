package com.codex.personalsop;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private EditText tokenInput;
    private TextView modulesHeader;
    private LinearLayout moduleGrid;
    private LinearLayout moduleEditorLayout;
    private EditText nameInput;
    private EditText messageInput;
    private CheckBox usesChecklistInput;
    private TextView checklistItemsLabel;
    private EditText checklistItemsInput;
    private LinearLayout checklistRuntimeLayout;
    private Spinner cycleInput;
    private TextView daysLabel;
    private Button selectAllDaysButton;
    private LinearLayout daysRow;
    private final CheckBox[] dayInputs = new CheckBox[7];
    private TextView monthlyOrdinalLabel;
    private Spinner monthlyOrdinalInput;
    private TextView monthlyDayLabel;
    private Spinner monthlyDayInput;
    private TextView intervalDaysLabel;
    private EditText intervalDaysInput;
    private TextView intervalStartDateLabel;
    private EditText intervalStartDateInput;
    private EditText startTimeInput;
    private EditText endTimeInput;
    private EditText intervalInput;
    private CheckBox enabledInput;
    private Button completeButton;
    private CheckBox testModeInput;
    private TextView feedbackView;
    private Button debugToggleButton;
    private LinearLayout debugLayout;
    private TextView statusView;
    private List<SopModule> modules = new ArrayList<>();
    private String selectedModuleId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadSettings();
        refreshStatus();
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("个人sop");
        title.setTextSize(28);
        title.setGravity(Gravity.START);
        root.addView(title, matchWrap());

        tokenInput = input("全能消息推送Bark 的 Token");
        root.addView(label("全局 Token"));
        root.addView(tokenInput, matchWrap());

        modulesHeader = label("模块");
        root.addView(modulesHeader, matchWrap());
        moduleGrid = new LinearLayout(this);
        moduleGrid.setOrientation(LinearLayout.VERTICAL);
        moduleGrid.setPadding(0, dp(8), 0, dp(10));
        root.addView(moduleGrid, matchWrap());

        moduleEditorLayout = new LinearLayout(this);
        moduleEditorLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(moduleEditorLayout, matchWrap());

        nameInput = input("模块名称，例如 出门检查");
        moduleEditorLayout.addView(label("模块名称"));
        moduleEditorLayout.addView(nameInput, matchWrap());

        messageInput = input("提醒文案");
        moduleEditorLayout.addView(label("提醒文案"));
        moduleEditorLayout.addView(messageInput, matchWrap());

        usesChecklistInput = new CheckBox(this);
        usesChecklistInput.setText("使用动作清单：全部勾选后才完成");
        usesChecklistInput.setTextSize(16);
        usesChecklistInput.setPadding(0, dp(10), 0, dp(4));
        usesChecklistInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateConditionalFields();
            }
        });
        moduleEditorLayout.addView(usesChecklistInput, matchWrap());

        checklistItemsInput = multilineInput("");
        checklistItemsLabel = label("动作清单");
        moduleEditorLayout.addView(checklistItemsLabel);
        moduleEditorLayout.addView(checklistItemsInput, matchWrap());

        cycleInput = new Spinner(this);
        ArrayAdapter<String> cycleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"每天", "每周", "每月", "每隔N天"});
        cycleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cycleInput.setAdapter(cycleAdapter);
        cycleInput.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateConditionalFields();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateConditionalFields();
            }
        });
        moduleEditorLayout.addView(label("周期类型"));
        moduleEditorLayout.addView(cycleInput, matchWrap());

        daysLabel = label("每周提醒日");
        moduleEditorLayout.addView(daysLabel);
        selectAllDaysButton = button("全选");
        selectAllDaysButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (CheckBox dayInput : dayInputs) {
                    dayInput.setChecked(true);
                }
            }
        });
        moduleEditorLayout.addView(selectAllDaysButton, matchWrap());
        daysRow = new LinearLayout(this);
        daysRow.setOrientation(LinearLayout.VERTICAL);
        String[] dayLabels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < dayInputs.length; i++) {
            dayInputs[i] = new CheckBox(this);
            dayInputs[i].setText(dayLabels[i]);
            dayInputs[i].setTextSize(15);
            daysRow.addView(dayInputs[i], matchWrap());
        }
        moduleEditorLayout.addView(daysRow, matchWrap());

        monthlyOrdinalLabel = label("每月第几个");
        moduleEditorLayout.addView(monthlyOrdinalLabel);
        monthlyOrdinalInput = new Spinner(this);
        ArrayAdapter<String> monthlyOrdinalAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"第1个", "第2个", "第3个", "第4个"});
        monthlyOrdinalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        monthlyOrdinalInput.setAdapter(monthlyOrdinalAdapter);
        moduleEditorLayout.addView(monthlyOrdinalInput, matchWrap());

        monthlyDayLabel = label("每月星期几");
        moduleEditorLayout.addView(monthlyDayLabel);
        monthlyDayInput = new Spinner(this);
        ArrayAdapter<String> monthlyDayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"});
        monthlyDayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        monthlyDayInput.setAdapter(monthlyDayAdapter);
        moduleEditorLayout.addView(monthlyDayInput, matchWrap());

        intervalDaysLabel = label("间隔天数");
        moduleEditorLayout.addView(intervalDaysLabel);
        intervalDaysInput = input("");
        intervalDaysInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        moduleEditorLayout.addView(intervalDaysInput, matchWrap());

        intervalStartDateLabel = label("起始日期");
        moduleEditorLayout.addView(intervalStartDateLabel);
        intervalStartDateInput = dateInput("");
        moduleEditorLayout.addView(intervalStartDateInput, matchWrap());

        startTimeInput = timeInput("");
        moduleEditorLayout.addView(label("开始时间"));
        moduleEditorLayout.addView(startTimeInput, matchWrap());

        endTimeInput = timeInput("");
        moduleEditorLayout.addView(label("结束时间"));
        moduleEditorLayout.addView(endTimeInput, matchWrap());

        intervalInput = input("");
        intervalInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        moduleEditorLayout.addView(label("提醒间隔（分钟）"));
        moduleEditorLayout.addView(intervalInput, matchWrap());

        enabledInput = new CheckBox(this);
        enabledInput.setText("启用当前模块");
        enabledInput.setTextSize(16);
        enabledInput.setPadding(0, dp(10), 0, dp(10));
        moduleEditorLayout.addView(enabledInput, matchWrap());

        testModeInput = new CheckBox(this);
        testModeInput.setText("测试模式：不限制时间窗口");
        testModeInput.setTextSize(16);
        testModeInput.setPadding(0, dp(2), 0, dp(10));

        moduleEditorLayout.addView(label("当前周期动作清单"));
        checklistRuntimeLayout = new LinearLayout(this);
        checklistRuntimeLayout.setOrientation(LinearLayout.VERTICAL);
        moduleEditorLayout.addView(checklistRuntimeLayout, matchWrap());

        completeButton = button("今天完成了");
        completeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                markCurrentModuleDone();
            }
        });
        moduleEditorLayout.addView(completeButton, matchWrap());

        Button saveButton = button("保存");
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveAndSchedule();
            }
        });
        moduleEditorLayout.addView(saveButton, matchWrap());

        Button deleteButton = button("删除当前模块");
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDeleteCurrentModule();
            }
        });
        moduleEditorLayout.addView(deleteButton, matchWrap());

        feedbackView = new TextView(this);
        feedbackView.setTextSize(14);
        feedbackView.setPadding(0, dp(12), 0, dp(8));
        moduleEditorLayout.addView(feedbackView, matchWrap());

        debugToggleButton = button("诊断/调试");
        debugToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleDebugLayout();
            }
        });
        moduleEditorLayout.addView(debugToggleButton, matchWrap());

        debugLayout = new LinearLayout(this);
        debugLayout.setOrientation(LinearLayout.VERTICAL);
        debugLayout.setVisibility(View.GONE);
        moduleEditorLayout.addView(debugLayout, matchWrap());

        debugLayout.addView(testModeInput, matchWrap());

        Button testButton = button("测试提醒");
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                testBark();
            }
        });
        debugLayout.addView(testButton, matchWrap());

        Button alarmTestButton = button("2 分钟后测试提醒");
        alarmTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scheduleTwoMinuteTest();
            }
        });
        debugLayout.addView(alarmTestButton, matchWrap());

        Button clearLogButton = button("清空发送日志");
        clearLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ReminderConfig.clearLog(MainActivity.this);
                refreshStatus();
            }
        });
        debugLayout.addView(clearLogButton, matchWrap());

        Button exactAlarmButton = button("打开精确闹钟权限设置");
        exactAlarmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openExactAlarmSettings();
            }
        });
        debugLayout.addView(exactAlarmButton, matchWrap());

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(0, dp(18), 0, 0);
        debugLayout.addView(statusView, matchWrap());

        setContentView(scrollView);
    }

    private void loadSettings() {
        SharedPreferences prefs = ReminderConfig.prefs(this);
        tokenInput.setText(prefs.getString(ReminderConfig.KEY_BARK_ENDPOINT, ""));
        modules = SopModuleStore.modules(this);
        renderModuleGrid();
        SopModule selected = SopModuleStore.selectedModule(this);
        if (selected == null) {
            selectedModuleId = "";
            if (moduleEditorLayout != null) {
                moduleEditorLayout.setVisibility(View.GONE);
            }
            return;
        }
        selectModule(selected.id);
    }

    private void selectModule(String moduleId) {
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).id.equals(moduleId)) {
                selectedModuleId = moduleId;
                SopModuleStore.saveSelectedModuleId(this, moduleId);
                renderModuleGrid();
                loadModule(modules.get(i));
                return;
            }
        }
        if (!modules.isEmpty()) {
            selectedModuleId = modules.get(0).id;
            SopModuleStore.saveSelectedModuleId(this, selectedModuleId);
            renderModuleGrid();
            loadModule(modules.get(0));
            return;
        }
        selectedModuleId = "";
        SopModuleStore.saveSelectedModuleId(this, "");
        renderModuleGrid();
        if (moduleEditorLayout != null) {
            moduleEditorLayout.setVisibility(View.GONE);
        }
    }

    private void renderModuleGrid() {
        if (moduleGrid == null) {
            return;
        }
        if (modulesHeader != null) {
            modulesHeader.setText("模块（" + modules.size() + "）");
        }
        moduleGrid.removeAllViews();
        List<View> cards = new ArrayList<>();
        for (SopModule module : modules) {
            cards.add(moduleCard(module));
        }
        cards.add(addModuleCard());

        for (int i = 0; i < cards.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, 0, dp(10));
            moduleGrid.addView(row, matchWrap());
            row.addView(cards.get(i), cardLayout(true));
            if (i + 1 < cards.size()) {
                row.addView(cards.get(i + 1), cardLayout(false));
            } else {
                TextView spacer = new TextView(this);
                row.addView(spacer, cardLayout(false));
            }
        }
    }

    private View moduleCard(SopModule module) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setMinimumHeight(dp(96));
        card.setBackground(cardBackground(module.id.equals(selectedModuleId), false));

        TextView name = new TextView(this);
        name.setText(displayModuleName(module));
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setTextColor(Color.BLACK);
        card.addView(name, matchWrap());

        TextView meta = new TextView(this);
        meta.setText((module.enabled ? "启用" : "停用") + " · " + cycleText(module));
        meta.setTextSize(13);
        meta.setTextColor(Color.rgb(90, 90, 90));
        meta.setPadding(0, dp(8), 0, 0);
        card.addView(meta, matchWrap());

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectModule(module.id);
            }
        });
        return card;
    }

    private View addModuleCard() {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER);
        card.setMinimumHeight(dp(96));
        card.setBackground(cardBackground(false, true));

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(42);
        plus.setGravity(Gravity.CENTER);
        plus.setTextColor(Color.BLACK);
        card.addView(plus, matchWrap());

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SopModule module = SopModuleStore.createModule(MainActivity.this);
                loadSettings();
                selectModule(module.id);
                setStatus("已新建模块。填好名称、提醒文案和时间后保存。");
            }
        });
        return card;
    }

    private GradientDrawable cardBackground(boolean selected, boolean addCard) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(16));
        drawable.setColor(addCard ? Color.rgb(250, 250, 250) : Color.WHITE);
        drawable.setStroke(dp(selected ? 2 : 1), selected ? Color.rgb(37, 99, 235) : Color.rgb(218, 218, 218));
        return drawable;
    }

    private LinearLayout.LayoutParams cardLayout(boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (left) {
            params.setMargins(0, 0, dp(6), 0);
        } else {
            params.setMargins(dp(6), 0, 0, 0);
        }
        return params;
    }

    private void loadModule(SopModule module) {
        if (moduleEditorLayout != null) {
            moduleEditorLayout.setVisibility(View.VISIBLE);
        }
        nameInput.setText(module.name == null ? "" : module.name);
        messageInput.setText(module.message == null ? "" : module.message);
        usesChecklistInput.setChecked(module.usesChecklist);
        checklistItemsInput.setText(checklistText(module));
        cycleInput.setSelection(cycleSelection(module.cycleType));
        for (int i = 0; i < dayInputs.length; i++) {
            dayInputs[i].setChecked((module.daysOfWeek & (1 << i)) != 0);
        }
        monthlyOrdinalInput.setSelection(monthlyOrdinalSelection(module.monthlyWeekOrdinal));
        monthlyDayInput.setSelection(clamp(module.monthlyDayOfWeek, 0, 6));
        intervalDaysInput.setText(formatIntervalDays(module.intervalDays));
        intervalStartDateInput.setText(formatDateOrToday(module.intervalStartEpochDay));
        startTimeInput.setText(formatTimeOrEmpty(module.startHour, module.startMinute));
        endTimeInput.setText(formatTimeOrEmpty(module.endHour, module.endMinute));
        enabledInput.setChecked(module.enabled);
        testModeInput.setChecked(module.testMode);
        intervalInput.setText(formatIntervalOrEmpty(module.intervalMinutes));
        updateConditionalFields();
        renderChecklist(module);
        refreshStatus();
    }

    private void updateConditionalFields() {
        if (checklistItemsLabel != null && checklistItemsInput != null && usesChecklistInput != null) {
            boolean usesChecklist = usesChecklistInput.isChecked();
            int checklistVisibility = usesChecklist ? View.VISIBLE : View.GONE;
            checklistItemsLabel.setVisibility(checklistVisibility);
            checklistItemsInput.setVisibility(checklistVisibility);
            if (completeButton != null) {
                completeButton.setVisibility(usesChecklist ? View.GONE : View.VISIBLE);
            }
        }
        if (daysLabel != null && daysRow != null && cycleInput != null) {
            int selectedCycle = cycleInput.getSelectedItemPosition();
            int weeklyVisibility = selectedCycle == 1 ? View.VISIBLE : View.GONE;
            int monthlyVisibility = selectedCycle == 2 ? View.VISIBLE : View.GONE;
            int intervalDaysVisibility = selectedCycle == 3 ? View.VISIBLE : View.GONE;
            daysLabel.setVisibility(weeklyVisibility);
            if (selectAllDaysButton != null) {
                selectAllDaysButton.setVisibility(weeklyVisibility);
            }
            daysRow.setVisibility(weeklyVisibility);
            if (monthlyOrdinalLabel != null && monthlyOrdinalInput != null
                    && monthlyDayLabel != null && monthlyDayInput != null) {
                monthlyOrdinalLabel.setVisibility(monthlyVisibility);
                monthlyOrdinalInput.setVisibility(monthlyVisibility);
                monthlyDayLabel.setVisibility(monthlyVisibility);
                monthlyDayInput.setVisibility(monthlyVisibility);
            }
            if (intervalDaysLabel != null && intervalDaysInput != null
                    && intervalStartDateLabel != null && intervalStartDateInput != null) {
                intervalDaysLabel.setVisibility(intervalDaysVisibility);
                intervalDaysInput.setVisibility(intervalDaysVisibility);
                intervalStartDateLabel.setVisibility(intervalDaysVisibility);
                intervalStartDateInput.setVisibility(intervalDaysVisibility);
            }
        }
    }

    private void toggleDebugLayout() {
        if (debugLayout == null) {
            return;
        }
        boolean show = debugLayout.getVisibility() != View.VISIBLE;
        debugLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (debugToggleButton != null) {
            debugToggleButton.setText(show ? "收起诊断/调试" : "诊断/调试");
        }
    }

    private void saveAndSchedule() {
        SopModule module = readModuleFromUi();
        if (module == null) {
            return;
        }
        String token = tokenInput.getText().toString().trim();
        ReminderConfig.prefs(this).edit()
                .putString(ReminderConfig.KEY_BARK_ENDPOINT, token)
                .putBoolean(ReminderConfig.KEY_ENABLED, ReminderConfig.isEnabled(this))
                .apply();
        SopModuleStore.upsert(this, module);

        boolean scheduled = ReminderScheduler.scheduleAll(this);
        loadSettings();
        selectModule(module.id);
        String saveStatus = saveStatusMessage(module, scheduled);
        ReminderConfig.appendLog(this, scheduled ? "INFO" : "ERROR", saveStatus);
        setStatus(saveStatus);
        refreshStatus();
    }

    private String saveStatusMessage(SopModule module, boolean scheduled) {
        if (!module.enabled) {
            return "已保存 [" + module.name + "]，当前模块未启用。";
        }
        if (!scheduled) {
            return "已保存 [" + module.name + "]，但未能安排提醒。请检查精确闹钟权限。";
        }
        long next = ReminderScheduler.nextTriggerMillis(this, System.currentTimeMillis(), module);
        if (next <= 0L) {
            return "已保存 [" + module.name + "]，但没有找到下一次提醒时间。";
        }
        return "已保存 [" + module.name + "]，" + cycleText(module) + "，下次提醒："
                + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(next));
    }

    private void testBark() {
        SopModule module = readModuleFromUi();
        if (module == null) {
            return;
        }
        String token = tokenInput.getText().toString().trim();
        if (token.isEmpty()) {
            setStatus("请先填写 Token。");
            return;
        }
        setStatus("正在发送测试提醒...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final BarkClient.Result result = BarkClient.send(token, module.name, module.message);
                ReminderConfig.appendLog(
                        MainActivity.this,
                        result.ok ? "INFO" : "ERROR",
                        "[" + module.name + "] 手动测试：" + result.message
                );
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshStatus();
                    }
                });
            }
        }, "sop-module-test").start();
    }

    private void scheduleTwoMinuteTest() {
        SopModule module = readModuleFromUi();
        if (module == null) {
            return;
        }
        String token = tokenInput.getText().toString().trim();
        if (token.isEmpty()) {
            setStatus("请先填写 Token。");
            return;
        }
        module.enabled = true;
        ReminderConfig.prefs(this).edit()
                .putString(ReminderConfig.KEY_BARK_ENDPOINT, token)
                .putString(ReminderConfig.KEY_LAST_TRIGGER_STATUS, "已安排 [" + module.name + "] 2 分钟后测试，等待系统闹钟唤醒")
                .apply();
        SopModuleStore.upsert(this, module);
        ReminderConfig.appendLog(this, "INFO", "已安排 [" + module.name + "] 2 分钟后测试，等待系统闹钟唤醒");

        boolean scheduled = ReminderScheduler.scheduleTestInMinutes(this, module.id, 2);
        loadSettings();
        selectModule(module.id);
        setStatus(scheduled ? "已安排 2 分钟后测试。请按 Home 或锁屏。" : "未能安排测试，请检查精确闹钟权限。");
        refreshStatus();
    }


    private void confirmDeleteCurrentModule() {
        final SopModule module = SopModuleStore.selectedModule(this);
        if (module == null) {
            setStatus("当前没有可删除的模块。");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除模块")
                .setMessage("确定删除「" + displayModuleName(module) + "」吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        ReminderScheduler.cancel(MainActivity.this, module.id);
                        SopModuleStore.delete(MainActivity.this, module.id);
                        ReminderScheduler.scheduleAll(MainActivity.this);
                        loadSettings();
                        setStatus("已删除「" + displayModuleName(module) + "」。");
                        refreshStatus();
                    }
                })
                .show();
    }

    private void markCurrentModuleDone() {
        SopModule module = readModuleFromUi();
        if (module == null) {
            return;
        }
        SopModuleStore.upsert(this, module);
        SopModuleStore.markCompleted(this, module, System.currentTimeMillis());
        ReminderScheduler.scheduleAll(this);
        loadSettings();
        selectModule(module.id);
        setStatus(SopModuleStore.isCompleted(this, module, System.currentTimeMillis())
                ? "今天完成了。"
                : "动作清单尚未全部完成，当前模块会继续提醒。");
        refreshStatus();
    }

    private void renderChecklist(SopModule module) {
        if (checklistRuntimeLayout == null) {
            return;
        }
        checklistRuntimeLayout.removeAllViews();
        if (!module.usesChecklist || module.checklistItems == null || module.checklistItems.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("当前模块未启用动作清单。");
            empty.setTextSize(14);
            checklistRuntimeLayout.addView(empty, matchWrap());
            return;
        }
        long now = System.currentTimeMillis();
        if (ReminderScheduler.occurrenceKey(now, module) == null) {
            TextView waiting = new TextView(this);
            waiting.setText("当前还未进入本模块的执行周期。");
            waiting.setTextSize(14);
            checklistRuntimeLayout.addView(waiting, matchWrap());
            return;
        }
        for (int i = 0; i < module.checklistItems.size() && i < 30; i++) {
            final int index = i;
            CheckBox item = new CheckBox(this);
            item.setText(module.checklistItems.get(i));
            item.setTextSize(15);
            item.setChecked(SopModuleStore.isChecklistItemChecked(this, module, now, i));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    SopModule latest = readModuleFromUi();
                    if (latest == null) {
                        return;
                    }
                    SopModuleStore.upsert(MainActivity.this, latest);
                    SopModuleStore.setChecklistItemChecked(
                            MainActivity.this,
                            latest,
                            System.currentTimeMillis(),
                            index,
                            ((CheckBox) view).isChecked()
                    );
                    ReminderScheduler.scheduleAll(MainActivity.this);
                    loadSettings();
                    selectModule(latest.id);
                    refreshStatus();
                }
            });
            checklistRuntimeLayout.addView(item, matchWrap());
        }
    }

    private SopModule readModuleFromUi() {
        SopModule selected = SopModuleStore.selectedModule(this);
        if (selected == null) {
            setStatus("请先点击 + 新建模块。");
            return null;
        }
        SopModule module = new SopModule();
        module.id = selected.id;
        module.name = nameInput.getText().toString().trim();
        module.message = messageInput.getText().toString().trim();
        module.usesChecklist = usesChecklistInput.isChecked();
        module.checklistItems = parseChecklistItems();
        module.enabled = enabledInput.isChecked();
        int cyclePosition = cycleInput.getSelectedItemPosition();
        if (cyclePosition == 1) {
            module.cycleType = SopModule.CYCLE_WEEKLY;
        } else if (cyclePosition == 2) {
            module.cycleType = SopModule.CYCLE_MONTHLY;
        } else if (cyclePosition == 3) {
            module.cycleType = SopModule.CYCLE_INTERVAL_DAYS;
        } else {
            module.cycleType = SopModule.CYCLE_DAILY;
        }
        module.daysOfWeek = SopModule.CYCLE_WEEKLY.equals(module.cycleType) ? readDays() : SopModule.ALL_DAYS;
        module.monthlyWeekOrdinal = monthlyOrdinalFromSelection(monthlyOrdinalInput.getSelectedItemPosition());
        module.monthlyDayOfWeek = clamp(monthlyDayInput.getSelectedItemPosition(), 0, 6);
        module.intervalDays = parseIntervalDays();
        LocalDate intervalStartDate = parseDateOrNull(intervalStartDateInput.getText().toString().trim());
        module.intervalStartEpochDay = intervalStartDate == null ? LocalDate.now().toEpochDay() : intervalStartDate.toEpochDay();
        module.requiresCompletion = false;
        module.testMode = testModeInput.isChecked();

        if (module.name.isEmpty()) {
            setStatus("请先填写模块名称。");
            return null;
        }
        if (module.message.isEmpty()) {
            setStatus("请先填写提醒文案。");
            return null;
        }
        if (SopModule.CYCLE_WEEKLY.equals(module.cycleType) && module.daysOfWeek == 0) {
            setStatus("请至少选择一个每周提醒日。");
            return null;
        }
        if (SopModule.CYCLE_INTERVAL_DAYS.equals(module.cycleType)) {
            if (!isValidIntervalDays(intervalDaysInput.getText().toString().trim())) {
                setStatus("间隔天数请填写 1-3650 之间的整数。");
                return null;
            }
            if (parseDateOrNull(intervalStartDateInput.getText().toString().trim()) == null) {
                setStatus("起始日期请使用 yyyy-MM-dd，例如 2026-07-04。");
                return null;
            }
        }
        if (module.usesChecklist && module.checklistItems.isEmpty()) {
            setStatus("已启用动作清单，请至少填写一个清单项。");
            return null;
        }
        String startTime = normalizeTimeInput(startTimeInput.getText().toString());
        String endTime = normalizeTimeInput(endTimeInput.getText().toString());
        if (!isValidTime(startTime) || !isValidTime(endTime)) {
            setStatus("时间格式请使用 HH:mm，例如 17:00。");
            return null;
        }
        int[] start = parseTime(startTime);
        int[] end = parseTime(endTime);
        module.startHour = start[0];
        module.startMinute = start[1];
        module.endHour = end[0];
        module.endMinute = end[1];
        module.intervalMinutes = parseInterval();
        if (!isValidInterval(intervalInput.getText().toString().trim())) {
            setStatus("提醒间隔请填写 1-720 之间的分钟数。");
            return null;
        }
        return module;
    }

    private int readDays() {
        int value = 0;
        for (int i = 0; i < dayInputs.length; i++) {
            if (dayInputs[i].isChecked()) {
                value |= 1 << i;
            }
        }
        return value;
    }

    private List<String> parseChecklistItems() {
        List<String> items = new ArrayList<>();
        String raw = checklistItemsInput.getText().toString();
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String item = line.trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private String checklistText(SopModule module) {
        if (module.checklistItems == null || module.checklistItems.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String item : module.checklistItems) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(item);
        }
        return builder.toString();
    }

    private void refreshStatus() {
        boolean exactAlarmOk = canScheduleExactAlarms();
        boolean notificationOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        long next = ReminderConfig.prefs(this).getLong(ReminderConfig.KEY_NEXT_TRIGGER, 0L);
        String nextText = next > 0
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(next))
                : "暂无";
        String lastStatus = ReminderConfig.prefs(this).getString(ReminderConfig.KEY_LAST_TRIGGER_STATUS, "暂无");
        String recentLog = ReminderConfig.recentLog(this);
        if (recentLog.isEmpty()) {
            recentLog = "暂无";
        }

        StringBuilder moduleText = new StringBuilder();
        long now = System.currentTimeMillis();
        for (SopModule module : SopModuleStore.modules(this)) {
            if (moduleText.length() > 0) {
                moduleText.append('\n');
            }
            moduleText.append(module.enabled ? "启用" : "停用")
                    .append(" - ")
                    .append(displayModuleName(module))
                    .append(" - ")
                    .append(cycleText(module))
                    .append(" ")
                    .append(module.windowText())
                    .append(" / ")
                    .append(module.intervalMinutes)
                    .append(" 分钟");
            if (module.enabled) {
                long moduleNext = ReminderScheduler.nextTriggerMillis(this, now, module);
                moduleText.append(" / 下次 ")
                        .append(new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(moduleNext)));
            }
            boolean completed = SopModuleStore.isCompleted(this, module, now);
            if (completed) {
                moduleText.append(" / 当前周期已完成");
            } else if (ReminderScheduler.occurrenceKey(now, module) != null) {
                moduleText.append(" / 当前周期提醒中");
            }
            if (module.usesChecklist) {
                moduleText.append(" / 清单 ")
                        .append(SopModuleStore.completedChecklistCount(this, module, now))
                        .append("/")
                        .append(module.checklistItems == null ? 0 : module.checklistItems.size());
            }
        }

        String status = "状态："
                + "\n精确闹钟权限：" + (exactAlarmOk ? "已允许" : "未允许")
                + "\n通知权限：" + (notificationOk ? "已允许" : "未允许")
                + "\n全局下一次提醒：" + nextText
                + "\n上次触发：" + lastStatus
                + "\n\n模块：\n" + moduleText
                + "\n\n最近发送日志：\n" + recentLog;
        if (statusView != null) {
            statusView.setText(status);
        }
    }

    private String cycleText(SopModule module) {
        if (SopModule.CYCLE_DAILY.equals(module.cycleType)) {
            return "每天";
        }
        if (SopModule.CYCLE_MONTHLY.equals(module.cycleType)) {
            return "每月" + monthlyOrdinalText(module.monthlyWeekOrdinal) + dayLabel(module.monthlyDayOfWeek);
        }
        if (SopModule.CYCLE_INTERVAL_DAYS.equals(module.cycleType)) {
            return "每隔" + clamp(module.intervalDays, SopModule.MIN_INTERVAL_DAYS, SopModule.MAX_INTERVAL_DAYS)
                    + "天（从" + formatDateOrToday(module.intervalStartEpochDay) + "开始）";
        }
        StringBuilder builder = new StringBuilder("每周");
        if (module.daysOfWeek == 0) {
            return "每周（未选择）";
        }
        for (int i = 0; i < dayInputs.length; i++) {
            if ((module.daysOfWeek & (1 << i)) != 0) {
                if (builder.length() > 2) {
                    builder.append("、");
                }
                builder.append(dayLabel(i));
            }
        }
        return builder.toString();
    }

    private int cycleSelection(String cycleType) {
        if (SopModule.CYCLE_WEEKLY.equals(cycleType)) {
            return 1;
        }
        if (SopModule.CYCLE_MONTHLY.equals(cycleType)) {
            return 2;
        }
        if (SopModule.CYCLE_INTERVAL_DAYS.equals(cycleType)) {
            return 3;
        }
        return 0;
    }

    private int monthlyOrdinalFromSelection(int selection) {
        return clamp(selection + 1, 1, 4);
    }

    private int monthlyOrdinalSelection(int ordinal) {
        return clamp(ordinal, 1, 4) - 1;
    }

    private String monthlyOrdinalText(int ordinal) {
        return "第" + clamp(ordinal, 1, 4) + "个";
    }

    private String dayLabel(int index) {
        String[] labels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return labels[clamp(index, 0, labels.length - 1)];
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            setStatus("当前 Android 版本不需要单独开启精确闹钟权限。");
        }
    }

    private boolean canScheduleExactAlarms() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || alarmManager == null
                || alarmManager.canScheduleExactAlarms();
    }

    private boolean isValidTime(String value) {
        if (value == null || !value.matches("\\d{1,2}:\\d{2}")) {
            return false;
        }
        int[] parsed = parseTime(value);
        return parsed[0] >= 0 && parsed[0] <= 23 && parsed[1] >= 0 && parsed[1] <= 59;
    }

    private String normalizeTimeInput(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace('：', ':')
                .replace(" ", "");
    }

    private int[] parseTime(String value) {
        try {
            String[] parts = value.split(":");
            return new int[]{
                    Math.max(0, Math.min(Integer.parseInt(parts[0]), 23)),
                    Math.max(0, Math.min(Integer.parseInt(parts[1]), 59))
            };
        } catch (RuntimeException ex) {
            return new int[]{0, 0};
        }
    }

    private String formatTime(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    private String formatTimeOrEmpty(int hour, int minute) {
        if (hour == SopModule.UNSET_TIME) {
            return "";
        }
        return formatTime(hour, minute);
    }

    private int parseInterval() {
        try {
            String raw = intervalInput.getText().toString().trim();
            if (raw.isEmpty()) {
                return SopModule.UNSET_INTERVAL;
            }
            int value = Integer.parseInt(raw);
            return Math.max(1, Math.min(value, 720));
        } catch (NumberFormatException ex) {
            return SopModule.UNSET_INTERVAL;
        }
    }

    private boolean isValidInterval(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 1 && parsed <= 720;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String formatIntervalOrEmpty(int intervalMinutes) {
        if (intervalMinutes == SopModule.UNSET_INTERVAL) {
            return "";
        }
        return String.valueOf(intervalMinutes);
    }

    private int parseIntervalDays() {
        try {
            String raw = intervalDaysInput.getText().toString().trim();
            if (raw.isEmpty()) {
                return SopModule.MIN_INTERVAL_DAYS;
            }
            int value = Integer.parseInt(raw);
            return clamp(value, SopModule.MIN_INTERVAL_DAYS, SopModule.MAX_INTERVAL_DAYS);
        } catch (NumberFormatException ex) {
            return SopModule.MIN_INTERVAL_DAYS;
        }
    }

    private boolean isValidIntervalDays(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= SopModule.MIN_INTERVAL_DAYS && parsed <= SopModule.MAX_INTERVAL_DAYS;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String formatIntervalDays(int intervalDays) {
        return String.valueOf(clamp(intervalDays, SopModule.MIN_INTERVAL_DAYS, SopModule.MAX_INTERVAL_DAYS));
    }

    private LocalDate parseDateOrNull(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String formatDateOrToday(long epochDay) {
        try {
            return LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (RuntimeException ex) {
            return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    private String displayModuleName(SopModule module) {
        if (module == null || module.name == null || module.name.trim().isEmpty()) {
            return "未命名模块";
        }
        return module.name.trim();
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setTextSize(16);
        return editText;
    }

    private EditText timeInput(String hint) {
        EditText editText = input(hint);
        editText.setInputType(InputType.TYPE_NULL);
        editText.setFocusable(false);
        editText.setCursorVisible(false);
        editText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTimePicker((EditText) view);
            }
        });
        return editText;
    }

    private EditText dateInput(String hint) {
        EditText editText = input(hint);
        editText.setInputType(InputType.TYPE_NULL);
        editText.setFocusable(false);
        editText.setCursorVisible(false);
        editText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker((EditText) view);
            }
        });
        return editText;
    }

    private void showDatePicker(final EditText target) {
        LocalDate date = parseDateOrNull(target.getText().toString());
        if (date == null) {
            date = LocalDate.now();
        }
        final DatePicker picker = new DatePicker(this);
        picker.init(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth(), null);

        new AlertDialog.Builder(this)
                .setTitle("选择起始日期")
                .setView(picker)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        LocalDate selected = LocalDate.of(
                                picker.getYear(),
                                picker.getMonth() + 1,
                                picker.getDayOfMonth()
                        );
                        target.setText(selected.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    }
                })
                .show();
    }

    private void showTimePicker(final EditText target) {
        int hour = 0;
        int minute = 0;
        String value = normalizeTimeInput(target.getText().toString());
        if (isValidTime(value)) {
            int[] parsed = parseTime(value);
            hour = parsed[0];
            minute = parsed[1];
        }

        LinearLayout pickerRow = new LinearLayout(this);
        pickerRow.setOrientation(LinearLayout.HORIZONTAL);
        pickerRow.setGravity(Gravity.CENTER);
        pickerRow.setPadding(dp(12), dp(8), dp(12), 0);

        final NumberPicker hourPicker = timeNumberPicker(0, 23, hour);
        final NumberPicker minutePicker = timeNumberPicker(0, 59, minute);

        TextView separator = new TextView(this);
        separator.setText(":");
        separator.setTextSize(28);
        separator.setGravity(Gravity.CENTER);

        pickerRow.addView(hourPicker, pickerLayout());
        pickerRow.addView(separator, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT));
        pickerRow.addView(minutePicker, pickerLayout());

        new AlertDialog.Builder(this)
                .setTitle("选择时间")
                .setView(pickerRow)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        target.setText(formatTime(hourPicker.getValue(), minutePicker.getValue()));
                    }
                })
                .show();
    }

    private NumberPicker timeNumberPicker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        disableNumberPickerTextInput(picker);
        picker.setFormatter(new NumberPicker.Formatter() {
            @Override
            public String format(int number) {
                return String.format(Locale.getDefault(), "%02d", number);
            }
        });
        return picker;
    }

    private void disableNumberPickerTextInput(NumberPicker picker) {
        for (int i = 0; i < picker.getChildCount(); i++) {
            View child = picker.getChildAt(i);
            if (child instanceof EditText) {
                EditText input = (EditText) child;
                input.setInputType(InputType.TYPE_NULL);
                input.setFocusable(false);
                input.setCursorVisible(false);
            }
        }
    }

    private LinearLayout.LayoutParams pickerLayout() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private EditText multilineInput(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(false);
        editText.setMinLines(4);
        editText.setTextSize(16);
        editText.setGravity(Gravity.TOP);
        return editText;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setPadding(0, dp(14), 0, 0);
        return label;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setStatus(String message) {
        if (feedbackView != null) {
            feedbackView.setText(message);
        }
    }
}
