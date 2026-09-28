package com.example.supplybeam.client;

import com.example.supplybeam.SupplyBeamConfig;
import com.example.supplybeam.SupplyBeamConfig.ConfigEntry;
import com.example.supplybeam.SupplyBeamConfig.ValueType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 图形配置界面：从模组列表的"配置"按钮进入。
 * 三个分页（刷新 / 光柱与补给箱 / 稀有度与颜色），保存时逐项校验，
 * 通过 {@link SupplyBeamConfig#applyValue} 热更新并写入 TOML 文件。
 */
public class SupplyBeamConfigScreen extends Screen {
    private static final int PAGE_COUNT = 3;
    private static final int ROW_HEIGHT = 26;
    private static final int LABEL_WIDTH = 118;
    private static final int BOX_WIDTH = 128;
    private static final int COLOR_SWATCH = 12;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int page;
    private Component result;
    private int resultColor = 0xFFA0FFA0;

    /** 单个配置行：元数据 + 输入框 + 页面布局信息。 */
    private record Row(ConfigEntry entry, EditBox box, int page, int col, int x, int y) {}

    public SupplyBeamConfigScreen(Screen parent) {
        super(Component.translatable("supplybeam.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.rows.clear();
        this.result = null;
        List<ConfigEntry> entries = new ArrayList<>(SupplyBeamConfig.entries().stream()
            .filter(e -> !e.key().equals("debug.debugMode"))
            .toList());

        int top = 52;
        int left = Math.max(24, this.width / 2 - 260);
        int colWidth = LABEL_WIDTH + BOX_WIDTH + 26;
        int singleColX = this.width / 2 - (LABEL_WIDTH + BOX_WIDTH + 8) / 2;

        // 分页与坐标：spawn → 页0单列；beam/crate → 页1单列；rarity → 页2双列（权重左、颜色右）
        int spawnRow = 0, beamRow = 0, weightRow = 0, colorRow = 0;
        for (ConfigEntry e : entries) {
            int p, x, y;
            if (e.key().startsWith("spawn.")) {
                p = 0;
                x = singleColX;
                y = top + spawnRow++ * ROW_HEIGHT;
            } else if (e.key().startsWith("beam.") || e.key().startsWith("crate.")) {
                p = 1;
                x = singleColX;
                y = top + beamRow++ * ROW_HEIGHT;
            } else if (e.key().startsWith("rarity.weight")) {
                p = 2;
                x = left;
                y = top + weightRow++ * ROW_HEIGHT;
            } else {
                p = 2;
                x = left + colWidth;
                y = top + colorRow++ * ROW_HEIGHT;
            }
            EditBox box = new EditBox(this.font, x + LABEL_WIDTH + 4, y - 4, BOX_WIDTH, 20, Component.literal(e.key()));
            box.setMaxLength(32);
            box.setValue(SupplyBeamConfig.valueAsString(e));
            box.setHint(Component.literal(switch (e.type()) {
                case INT -> "123";
                case DOUBLE -> "1.5";
                case COLOR -> "#RRGGBB";
                case BOOLEAN -> "true/false";
            }));
            box.setTooltip(Tooltip.create(Component.translatable(e.commentKey())));
            box.setVisible(p == this.page);
            this.addRenderableWidget(box);
            this.rows.add(new Row(e, box, p, 0, x, y));
        }

        // 分页页签
        for (int p = 0; p < PAGE_COUNT; p++) {
            final int pageIndex = p;
            Button tab = Button.builder(Component.translatable("supplybeam.screen.page" + p),
                    b -> switchPage(pageIndex))
                .bounds(this.width / 2 - 225 + p * 150, this.height - 56, 145, 20)
                .build();
            tab.active = p != this.page;
            this.addRenderableWidget(tab);
        }

        // 保存 / 返回
        this.addRenderableWidget(Button.builder(Component.translatable("supplybeam.screen.save"), b -> save())
            .bounds(this.width / 2 - 100, this.height - 28, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("supplybeam.screen.done"), b -> onClose())
            .bounds(this.width / 2 + 5, this.height - 28, 95, 20).build());
    }

    private void switchPage(int target) {
        this.page = target;
        this.minecraft.setScreenAndShow(this); // 重建 init，刷新页签激活态与可见性
    }

    private void save() {
        int saved = 0;
        List<String> failed = new ArrayList<>();
        for (Row row : this.rows) {
            String error = SupplyBeamConfig.applyValue(row.entry(), row.box().getValue());
            if (error == null) {
                row.box().setTextColor(0xFFE0E0E0);
                saved++;
            } else {
                row.box().setTextColor(0xFFFF5555);
                failed.add(row.entry().key());
            }
        }
        if (failed.isEmpty()) {
            this.result = Component.translatable("supplybeam.screen.saved", saved);
            this.resultColor = 0xFFA0FFA0;
        } else {
            this.result = Component.translatable("supplybeam.screen.save_failed", failed.size(),
                String.join(", ", failed));
            this.resultColor = 0xFFFF5555;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        graphics.centeredText(this.font,
            Component.translatable("supplybeam.screen.subtitle"), this.width / 2, 30, 0xFF909090);

        // 当前行：标签 + 颜色色块
        for (Row row : this.rows) {
            if (row.page() != this.page) {
                continue;
            }
            Component label = Component.translatable(row.entry().labelKey());
            graphics.text(this.font, label, row.x(), row.y() + 2, 0xFFE0E0E0, true);
            if (row.entry().type() == ValueType.COLOR) {
                int color = parseColor(row.box().getValue());
                int sx = row.x() + LABEL_WIDTH + 4 + BOX_WIDTH + 4;
                graphics.fill(sx, row.y() - 3, sx + COLOR_SWATCH, row.y() - 3 + COLOR_SWATCH, 0xFF000000 | color);
                graphics.outline(sx, row.y() - 3, COLOR_SWATCH, COLOR_SWATCH, 0xFF808080);
            }
        }

        graphics.centeredText(this.font,
            Component.translatable("supplybeam.screen.page_indicator", this.page + 1, PAGE_COUNT),
            this.width / 2, this.height - 66, 0xFF808080);
        if (this.result != null) {
            graphics.centeredText(this.font, this.result, this.width / 2, this.height - 76, this.resultColor);
        }
    }

    private static int parseColor(String raw) {
        try {
            String cleaned = raw.trim().replace("#", "");
            if (cleaned.length() == 6) {
                return Integer.parseInt(cleaned, 16) & 0xFFFFFF;
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFFFFFF; // 无效值先显示白色
    }
}
