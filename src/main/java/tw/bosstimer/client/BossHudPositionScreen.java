package tw.bosstimer.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Simple edit mode for dragging the live HUD away from a minimap. */
final class BossHudPositionScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private int offsetX;
    private int offsetY;
    private final int initialHudX;
    private final int initialHudY;
    private final int initialScreenWidth;
    private final int initialScreenHeight;

    BossHudPositionScreen(Screen parent) {
        super(Text.literal("調整 Boss 框位置"));
        this.parent = parent;
        this.initialHudX = BossAlertConfig.hudX;
        this.initialHudY = BossAlertConfig.hudY;
        this.initialScreenWidth = BossAlertConfig.hudScreenWidth;
        this.initialScreenHeight = BossAlertConfig.hudScreenHeight;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("儲存"), button -> saveAndReturn())
                .dimensions(width / 2 - 115, height - 85, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> cancelAndReturn())
                .dimensions(width / 2 + 5, height - 85, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("恢復預設位置"), button -> {
            BossAlertConfig.hudX = -1;
            BossAlertConfig.hudY = 8;
            BossAlertConfig.hudScreenWidth = 0;
            BossAlertConfig.hudScreenHeight = 0;
            BossAlertConfig.save();
        }).dimensions(width / 2 - 75, height - 55, 150, 20).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = BossTimerClient.renderedHudX;
        int y = BossTimerClient.renderedHudY;
        if (mouseX >= x && mouseX <= x + BossTimerClient.renderedHudWidth
                && mouseY >= y && mouseY <= y + BossTimerClient.renderedHudHeight) {
            dragging = true;
            offsetX = (int) mouseX - x;
            offsetY = (int) mouseY - y;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            BossAlertConfig.hudX = Math.max(0, Math.min(width - BossTimerClient.renderedHudWidth, (int) mouseX - offsetX));
            BossAlertConfig.hudY = Math.max(0, Math.min(height - BossTimerClient.renderedHudHeight, (int) mouseY - offsetY));
            BossAlertConfig.hudScreenWidth = width;
            BossAlertConfig.hudScreenHeight = height;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        BossAlertConfig.save();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        cancelAndReturn();
    }

    private void saveAndReturn() {
        BossAlertConfig.save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void cancelAndReturn() {
        BossAlertConfig.hudX = initialHudX;
        BossAlertConfig.hudY = initialHudY;
        BossAlertConfig.hudScreenWidth = initialScreenWidth;
        BossAlertConfig.hudScreenHeight = initialScreenHeight;
        BossAlertConfig.save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private int currentX() {
        if (BossAlertConfig.hudX >= 0) return BossAlertConfig.hudX;
        return MinecraftClient.getInstance().getWindow().getScaledWidth() - 370;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Match the translucent background used by the settings screen.
        context.fill(0, 0, width, height, 0x90000000);
        BossTimerClient.renderHud(context, delta);
        context.drawCenteredTextWithShadow(textRenderer, "拖曳右上角 Boss 框到想要的位置，按「儲存」保存，或按「取消」/ Esc 還原",
                width / 2, height - 25, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // The matching translucent overlay is drawn before rendering the draggable HUD.
    }
}
