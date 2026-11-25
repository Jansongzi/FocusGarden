package com.focusgarden.app;

import android.app.Dialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.focusgarden.app.GardenUi.*;

final class DisciplineCategoryPicker extends LinearLayout {
    private final MainActivity activity;
    private final FocusState state;
    private final LinearLayout options;
    private final TextView manage;
    private final Map<String, TextView> chips = new LinkedHashMap<>();
    private String selection = FocusState.DisciplineCategory.STUDY.id();

    DisciplineCategoryPicker(MainActivity activity, FocusState state) {
        super(activity);
        this.activity = activity;
        this.state = state;
        setOrientation(VERTICAL);

        ScrollView scroller = new ScrollView(activity);
        scroller.setVerticalScrollBarEnabled(false);
        options = column(activity);
        scroller.addView(options, -1, -2);
        addView(scroller, new LayoutParams(-1, 0, 1f));

        LinearLayout actions = row(activity);
        TextView add = secondaryButton(activity, "+ 自定义分类");
        add.setOnClickListener(v -> {
            tapHaptic(v);
            edit(null, () -> {});
        });
        actions.addView(add);
        space(actions, 8);
        manage = secondaryButton(activity, "管理分类");
        manage.setOnClickListener(v -> {
            tapHaptic(v);
            LinearLayout body = column(activity);
            renderManagement(body);
            bottomSheet(activity, "管理自定义分类", null, body);
        });
        actions.addView(manage);
        gap(this, 8);
        addView(actions);
        render();
    }

    FocusState.DisciplineCategory selected() {
        return state.disciplineCategory(selection);
    }

    private void render() {
        options.removeAllViews();
        chips.clear();
        var categories = state.disciplineCategories();
        for (int i = 0; i < categories.size(); i += 2) {
            if (i > 0) gap(options, 8);
            LinearLayout line = row(activity);
            weight(line, chip(categories.get(i)));
            space(line, 8);
            weight(line, i + 1 < categories.size() ? chip(categories.get(i + 1)) : new View(activity));
            options.addView(line, -1, -2);
        }
        manage.setVisibility(categories.size() > FocusState.DisciplineCategory.PRESETS.size() ? VISIBLE : GONE);
        updateSelection();
    }

    private TextView chip(FocusState.DisciplineCategory category) {
        String label = category.icon().isEmpty() ? category.label() : category.icon() + " " + category.label();
        TextView chip = pillButton(activity, label, SURFACE_SUBTLE, INK, 13, 49);
        chip.setContentDescription(category.label());
        chip.setOnClickListener(v -> {
            tapHaptic(v);
            selection = category.id();
            updateSelection();
        });
        chips.put(category.id(), chip);
        return chip;
    }

    private void updateSelection() {
        chips.forEach((id, chip) -> {
            boolean selected = id.equals(selection);
            chip.setSelected(selected);
            chip.setTextColor(selected ? BLUE : INK);
            chip.setBackground(touchCard(activity, selected ? BLUE_SOFT : SURFACE_SUBTLE,
                    selected ? BLUE : BORDER, 13));
        });
    }

    private void renderManagement(LinearLayout body) {
        body.removeAllViews();
        for (var category : state.disciplineCategories()) {
            if (!state.customDisciplineCategories.containsKey(category.id())) continue;
            TextView name = secondaryButton(activity, category.label());
            name.setOnClickListener(v -> {
                tapHaptic(v);
                edit(category, () -> renderManagement(body));
            });
            body.addView(name, -1, -2);
            gap(body, 8);
        }
        if (body.getChildCount() == 0) body.addView(text(activity, "暂无自定义分类", 13, MUTED, false));
    }

    private void edit(FocusState.DisciplineCategory category, Runnable onChanged) {
        LinearLayout form = column(activity);
        EditText name = styledInput(activity, "分类名称", category == null ? "" : category.label(), InputType.TYPE_CLASS_TEXT);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        form.addView(name, -1, -2);
        gap(form, 10);
        TextView save = primaryButton(activity, "保存分类");
        form.addView(save, -1, -2);
        TextView remove = null;
        if (category != null) {
            remove = dangerButton(activity, "移除分类");
            gap(form, 8);
            form.addView(remove, -1, -2);
        }
        Dialog editor = cardDialog(activity, R.drawable.ic_edit, BLUE_SOFT, BLUE,
                category == null ? "新增计时分类" : "编辑计时分类", null, form,
                "取消", null, false, true, null);
        save.setOnClickListener(v -> {
            var result = state.saveDisciplineCategory(category == null ? null : category.id(), name.getText().toString());
            if (result == null) {
                name.setError("请输入未被使用的分类名称");
                return;
            }
            tapHaptic(v);
            if (category == null) selection = result.id();
            saveAndRefresh(onChanged);
            editor.dismiss();
        });
        if (remove != null) remove.setOnClickListener(v ->
                confirmDialog(activity, R.drawable.ic_close, RED_SOFT, RED, "移除这个分类？",
                        "已记录的计时时长会保留。", "取消", "移除", true, () -> {
                            state.archivedDisciplineCategories.add(category.id());
                            if (category.id().equals(selection)) selection = FocusState.DisciplineCategory.STUDY.id();
                            saveAndRefresh(onChanged);
                            editor.dismiss();
                        }));
    }

    private void saveAndRefresh(Runnable onChanged) {
        FocusStore.get(activity).save();
        render();
        onChanged.run();
    }
}
