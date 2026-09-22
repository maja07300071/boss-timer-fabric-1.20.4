package tw.bosstimer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Settings screen with a searchable, scrollable sound drop-down. */
final class BossTimerConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 15;
    private static final int MAX_ROWS = 5;
    private final Screen parent;
    private TextFieldWidget secondsField;
    private TextFieldWidget searchField;
    private ButtonWidget soundButton;
    private List<Identifier> allSounds = List.of();
    private List<Identifier> filteredSounds = List.of();
    private boolean dropDownOpen;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private float uiScale = 1.0F;

    BossTimerConfigScreen(Screen parent) {
        super(Text.literal("Boss 計時器音效提醒"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int top = height / 2 - 135;
        allSounds = new ArrayList<>(BossAlertConfig.allowedSounds());
        filteredSounds = allSounds;
        secondsField = new TextFieldWidget(textRenderer, center - 75, top, 150, 20, Text.literal("提醒秒數"));
        secondsField.setText(Integer.toString(BossAlertConfig.seconds));
        secondsField.setMaxLength(5);
        secondsField.setTextPredicate(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        addDrawableChild(secondsField);
        searchField = new TextFieldWidget(textRenderer, center - 155, top + 35, 310, 20, Text.literal("搜尋音效"));
        searchField.setMaxLength(128);
        searchField.setChangedListener(this::updateFilter);
        addDrawableChild(searchField);
        soundButton = addDrawableChild(ButtonWidget.builder(soundText(), button -> {
            dropDownOpen = !dropDownOpen;
            if (dropDownOpen) updateFilter(searchField.getText());
        }).dimensions(center - 155, top + 60, 310, 20).build());
        String[] colorNames = { "黑", "深藍", "深綠", "深青", "深紅", "深紫", "金", "灰",
                "深灰", "藍", "綠", "青", "紅", "紫", "黃", "白" };
        for (int i = 0; i < BossAlertConfig.TITLE_COLORS.length; i++) {
            int color = BossAlertConfig.TITLE_COLORS[i];
            int x = center - 155 + (i % 8) * 39;
            int y = top + 180 + (i / 8) * 20;
            addDrawableChild(ButtonWidget.builder(Text.literal(colorNames[i]).styled(style -> style.withColor(color)),
                    button -> BossAlertConfig.titleColor = color)
                    .dimensions(x, y, 37, 18).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("音效提醒：" + (BossAlertConfig.enabled ? "開啟" : "關閉")), button -> {
            BossAlertConfig.enabled = !BossAlertConfig.enabled;
            button.setMessage(Text.literal("音效提醒：" + (BossAlertConfig.enabled ? "開啟" : "關閉")));
        }).dimensions(center - 155, top + 225, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("文字通知：" + (BossAlertConfig.titleEnabled ? "開啟" : "關閉")), button -> {
            BossAlertConfig.titleEnabled = !BossAlertConfig.titleEnabled;
            button.setMessage(Text.literal("文字通知：" + (BossAlertConfig.titleEnabled ? "開啟" : "關閉")));
        }).dimensions(center + 5, top + 225, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("測試音效"), button -> BossTimerClient.playConfiguredSound())
                .dimensions(center - 155, top + 250, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("調整框位置"), button -> {
            BossAlertConfig.save();
            MinecraftClient.getInstance().setScreen(new BossHudPositionScreen(this));
        }).dimensions(center + 5, top + 250, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("完成"), button -> close())
                .dimensions(center - 75, top + 275, 150, 20).build());
    }

    private Text soundText() { return Text.literal("音效：" + displayName(BossAlertConfig.sound)); }

    private String displayName(Identifier id) {
        String path = id.getPath();
        if (path.equals("entity.player.levelup")) return "經驗值升等";
        String[] monsters = { "blaze:烈焰使者", "bogged:沼骸", "breeze:微風", "cave_spider:洞穴蜘蛛",
                "creeper:苦力怕", "drowned:沉屍", "elder_guardian:遠古守衛者", "ender_dragon:終界龍",
                "enderman:終界使者", "endermite:終界蟎", "evoker:喚魔者", "ghast:地獄幽靈",
                "guardian:守衛者", "hoglin:豬布獸", "husk:屍殼", "illusioner:幻術師", "magma_cube:岩漿怪",
                "phantom:幻翼", "piglin:豬布林", "piglin_brute:豬布林蠻兵", "pillager:掠奪者",
                "ravager:劫獸", "shulker:界伏蚌", "silverfish:蠹蟲", "skeleton:骷髏",
                "slime:史萊姆", "spider:蜘蛛", "stray:流髑", "vex:惱鬼", "vindicator:衛道士",
                "warden:監守者", "witch:女巫", "wither:凋零", "wither_skeleton:凋零骷髏",
                "zoglin:殭屍疣豬", "zombie:殭屍", "zombie_villager:殭屍村民" };
        for (String entry : monsters) {
            String[] parts = entry.split(":", 2);
            if (path.startsWith("entity." + parts[0] + ".")) {
                String kind = path.substring(path.lastIndexOf('.') + 1);
                String kindName = kind.equals("ambient") ? "自然音效"
                        : kind.equals("idle_air") ? "待機音效"
                        : kind.equals("idle_water") ? "水中待機音效" : "地面待機音效";
                return parts[1] + "（" + kindName + "）";
            }
        }
        return id.toString();
    }

    private void updateFilter(String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        filteredSounds = allSounds.stream().filter(id -> needle.isEmpty()
                || id.toString().toLowerCase(Locale.ROOT).contains(needle)
                || displayName(id).toLowerCase(Locale.ROOT).contains(needle)).toList();
        scrollOffset = 0;
    }

    private void selectSound(Identifier sound) {
        BossAlertConfig.sound = sound;
        soundButton.setMessage(soundText());
        dropDownOpen = false;
        BossTimerClient.playConfiguredSound();
    }

    private float calculateUiScale() {
        float widthScale = (width - 16.0F) / 470.0F;
        float heightScale = (height - 16.0F) / 350.0F;
        return Math.max(0.5F, Math.min(1.0F, Math.min(widthScale, heightScale)));
    }

    private double toLayoutX(double mouseX) {
        return width / 2.0 + (mouseX - width / 2.0) / uiScale;
    }

    private double toLayoutY(double mouseY) {
        return height / 2.0 + (mouseY - height / 2.0) / uiScale;
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = toLayoutX(mouseX);
        mouseY = toLayoutY(mouseY);
        if (dropDownOpen) {
            int left = width / 2 - 155;
            int top = height / 2 - 51;
            if (mouseX >= left + 302 && mouseX < left + 310 && mouseY >= top
                    && mouseY < top + MAX_ROWS * ROW_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, top);
                return true;
            }
            if (mouseX >= left && mouseX < left + 310 && mouseY >= top && mouseY < top + MAX_ROWS * ROW_HEIGHT) {
                int index = scrollOffset + (int) ((mouseY - top) / ROW_HEIGHT);
                if (index >= 0 && index < filteredSounds.size()) selectSound(filteredSounds.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        mouseX = toLayoutX(mouseX);
        mouseY = toLayoutY(mouseY);
        deltaX /= uiScale;
        deltaY /= uiScale;
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY, height / 2 - 51);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = toLayoutX(mouseX);
        mouseY = toLayoutY(mouseY);
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollFromMouse(double mouseY, int top) {
        int maxOffset = Math.max(0, filteredSounds.size() - MAX_ROWS);
        if (maxOffset == 0) return;
        double ratio = Math.max(0.0, Math.min(1.0, (mouseY - top) / (double) (MAX_ROWS * ROW_HEIGHT)));
        scrollOffset = (int) Math.round(ratio * maxOffset);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        mouseX = toLayoutX(mouseX);
        mouseY = toLayoutY(mouseY);
        if (dropDownOpen) {
            int maxOffset = Math.max(0, filteredSounds.size() - MAX_ROWS);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        try { BossAlertConfig.seconds = Math.max(0, Math.min(86400, Integer.parseInt(secondsField.getText()))); }
        catch (NumberFormatException ignored) { BossAlertConfig.seconds = 30; }
        BossAlertConfig.save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x90000000);

        uiScale = calculateUiScale();
        context.getMatrices().push();
        context.getMatrices().translate(width * (1.0F - uiScale) / 2.0F,
                height * (1.0F - uiScale) / 2.0F, 0.0F);
        context.getMatrices().scale(uiScale, uiScale, 1.0F);
        int center = width / 2;
        int top = height / 2 - 135;
        context.drawCenteredTextWithShadow(textRenderer, title, center, top - 25, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "剩餘秒數", center - 75, top - 15, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, "搜尋怪物自然音效（點擊下方選單選擇）", center - 155, top + 24, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, "標題顏色（16 色）", center - 155, top + 169, 0xA0A0A0);
        super.render(context, (int) toLayoutX(mouseX), (int) toLayoutY(mouseY), delta);
        context.drawTextWithShadow(textRenderer, "▼", center + 137, top + 65, 0xFFFFFF);
        if (dropDownOpen) renderDropDown(context, center - 155, top + 84);
        context.getMatrices().pop();
    }
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The full-screen overlay is drawn before applying the scaled panel transform.
    }

    private void renderDropDown(DrawContext context, int left, int top) {
        int shown = Math.min(MAX_ROWS, Math.max(0, filteredSounds.size() - scrollOffset));
        int boxHeight = Math.max(ROW_HEIGHT, shown * ROW_HEIGHT);
        context.fill(left, top, left + 310, top + boxHeight, 0xF0101010);
        for (int i = 0; i < shown; i++) {
            Identifier id = filteredSounds.get(scrollOffset + i);
            int y = top + i * ROW_HEIGHT;
            if (id.equals(BossAlertConfig.sound)) context.fill(left, y, left + 310, y + ROW_HEIGHT, 0x804080A0);
            context.drawTextWithShadow(textRenderer, displayName(id), left + 5, y + 3, 0xFFFFFF);
        }
        if (shown == 0) context.drawTextWithShadow(textRenderer, "沒有符合的音效", left + 5, top + 3, 0xAAAAAA);
        int trackHeight = MAX_ROWS * ROW_HEIGHT;
        context.fill(left + 302, top, left + 310, top + trackHeight, 0xFF303030);
        int handleHeight = filteredSounds.size() <= MAX_ROWS ? trackHeight
                : Math.max(12, trackHeight * MAX_ROWS / filteredSounds.size());
        int maxOffset = Math.max(1, filteredSounds.size() - MAX_ROWS);
        int handleTop = top + (trackHeight - handleHeight) * scrollOffset / maxOffset;
        context.fill(left + 303, handleTop, left + 309, handleTop + handleHeight, 0xFFAAAAAA);
    }
}
