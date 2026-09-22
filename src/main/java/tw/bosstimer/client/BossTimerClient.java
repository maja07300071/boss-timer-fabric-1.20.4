package tw.bosstimer.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Minecraft 1.20.4 Fabric client-only BOSS respawn timer.
 *
 * Expected text examples:
 *   BOSS 崩裂重鎚・歐格 - 將在 455 後重生
 *   BOSS 觸骨母蛛・阿克拉娜 - 將在 524 秒後重生
 *
 * The mod scans nearby Text Display entities and entity custom names.
 */
public final class BossTimerClient implements ClientModInitializer {
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final double SCAN_RADIUS = 64.0;
    private static final long KEEP_AFTER_RESPAWN_SECONDS = 15 * 60L;

    // Supports -, －, – and — between boss name and countdown.
    private static final Pattern BOSS_PATTERN = Pattern.compile(
            "(?i)BOSS\\s+(.+?)\\s*[-－–—]\\s*將在\\s*(\\d{1,6})\\s*(?:秒)?\\s*後重生"
    );

    // Fallback for servers that omit the dash.
    private static final Pattern BOSS_PATTERN_LOOSE = Pattern.compile(
            "(?i)BOSS\\s+(.+?)\\s+將在\\s*(\\d{1,6})\\s*(?:秒)?\\s*後重生"
    );

    // Individual boss holograms put the name and countdown on separate lines/entities.
    private static final Pattern BOSS_NAME_PATTERN = Pattern.compile("(?i)^BOSS\\s+(.+?)\\s*$");
    private static final Pattern RESPAWN_PATTERN = Pattern.compile("將在\\s*(\\d{1,6})\\s*秒?\\s*後重生");
    private static final double HOLOGRAM_PAIR_DISTANCE_SQUARED = 9.0;
    private static final long TIMER_TOLERANCE_NANOS = 1_500_000_000L;
    private static final long TIMER_SNAP_NANOS = 5_000_000_000L;
    private static final long TIMER_CORRECTION_STEP_NANOS = 250_000_000L;

    private static final Map<String, BossTimer> TIMERS = new LinkedHashMap<>();

    private static int tickCounter = 0;
    private static Object lastServerKey;
    static int renderedHudX;
    static int renderedHudY;
    static int renderedHudWidth;
    static int renderedHudHeight;
    private static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        BossAlertConfig.load();
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.bosstimer.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.bosstimer"));
        ClientTickEvents.END_CLIENT_TICK.register(BossTimerClient::onEndClientTick);
        HudRenderCallback.EVENT.register(BossTimerClient::renderHud);
    }

    private static void onEndClientTick(MinecraftClient client) {
        while (settingsKey.wasPressed()) {
            client.setScreen(new BossTimerConfigScreen(client.currentScreen));
        }
        // A proxy may rebuild the network handler when moving between shards.
        // The server address remains stable across those transfers.
        Object serverKey = client.getCurrentServerEntry() != null
                ? client.getCurrentServerEntry().address
                : client.getServer();
        if (serverKey != null && !serverKey.equals(lastServerKey)) {
            TIMERS.clear();
            lastServerKey = serverKey;
            tickCounter = 0;
        }

        if (client.world == null || client.player == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        scanNearbyBossText(client);
        removeVeryOldTimers();
        playDueAlerts(client);
    }

    private static void playDueAlerts(MinecraftClient client) {
        if (client.player == null || (!BossAlertConfig.enabled && !BossAlertConfig.titleEnabled)) return;
        long now = System.nanoTime();
        for (BossTimer timer : TIMERS.values()) {
            long remaining = timer.remainingSeconds(now);
            if (!timer.alertPlayed && remaining > 0 && remaining <= BossAlertConfig.seconds) {
                timer.alertPlayed = true;
                if (BossAlertConfig.enabled) playConfiguredSound();
                if (BossAlertConfig.titleEnabled) {
                    client.inGameHud.setTitleTicks(10, 40, 10);
                    int color = BossAlertConfig.titleColor;
                    client.inGameHud.setTitle(Text.literal("⚔ " + timer.name)
                            .styled(style -> style.withColor(color)));
                    client.inGameHud.setSubtitle(Text.literal("剩餘 " + remaining + " 秒"));
                }
                // Keep the compact action-bar notification regardless of title setting.
                client.player.sendMessage(Text.literal(timer.name + " 剩餘 " + remaining + " 秒"), true);
            }
        }
    }

    static void playConfiguredSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.playSound(Registries.SOUND_EVENT.get(BossAlertConfig.sound), 1.0F, 1.0F);
        }
    }

    private static void scanNearbyBossText(MinecraftClient client) {
        double maxDistanceSquared = SCAN_RADIUS * SCAN_RADIUS;
        List<HologramLine> names = new ArrayList<>();
        List<HologramLine> countdowns = new ArrayList<>();

        for (Entity entity : client.world.getEntities()) {
            if (client.player.squaredDistanceTo(entity) > maxDistanceSquared) {
                continue;
            }

            // Minecraft 1.20.x Text Display holograms.
            if (entity instanceof DisplayEntity.TextDisplayEntity textDisplay) {
                DisplayEntity.TextDisplayEntity.Data data = textDisplay.getData();
                if (data != null && data.text() != null) {
                    parseText(data.text().getString(), entity, names, countdowns);
                }
            }

            // Older hologram plugins often use invisible armor stands/custom names.
            Text customName = entity.getCustomName();
            if (customName != null) {
                parseText(customName.getString(), entity, names, countdowns);
            }
        }

        for (HologramLine countdown : countdowns) {
            HologramLine nearestName = null;
            double nearestDistance = HOLOGRAM_PAIR_DISTANCE_SQUARED;
            for (HologramLine name : names) {
                double verticalDifference = name.y - countdown.y;
                if (verticalDifference < -0.25 || verticalDifference > 2.5) {
                    continue;
                }
                double distance = name.distanceSquared(countdown);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestName = name;
                }
            }
            if (nearestName != null) {
                updateTimer(nearestName.text, countdown.seconds);
            }
        }
    }

    private static void parseText(String rawText, Entity entity,
                                  List<HologramLine> names, List<HologramLine> countdowns) {
        if (rawText == null || rawText.isBlank()) {
            return;
        }

        String normalized = rawText
                .replace('\u00A0', ' ')
                .replace('\r', '\n');

        // A single Text Display may contain several lines.
        for (String line : normalized.split("\\n+")) {
            String cleaned = line.replaceAll("\\s+", " ").trim();
            parseLine(cleaned);
            Matcher nameMatcher = BOSS_NAME_PATTERN.matcher(cleaned);
            if (nameMatcher.matches() && !cleaned.contains("後重生")) {
                names.add(new HologramLine(cleanupBossName(nameMatcher.group(1)), 0,
                        entity.getX(), entity.getY(), entity.getZ()));
            }
            Matcher countdownMatcher = RESPAWN_PATTERN.matcher(cleaned);
            if (countdownMatcher.find() && !cleaned.toUpperCase().contains("BOSS")) {
                countdowns.add(new HologramLine("", Integer.parseInt(countdownMatcher.group(1)),
                        entity.getX(), entity.getY(), entity.getZ()));
            }
        }
    }

    private static void parseLine(String line) {
        if (line.isEmpty() || !line.toUpperCase().contains("BOSS") || !line.contains("後重生")) {
            return;
        }

        Matcher matcher = BOSS_PATTERN.matcher(line);
        boolean matched = matcher.find();
        if (!matched) {
            matcher = BOSS_PATTERN_LOOSE.matcher(line);
            matched = matcher.find();
        }
        if (!matched) {
            return;
        }

        String bossName = cleanupBossName(matcher.group(1));
        int seconds;
        try {
            seconds = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
            return;
        }

        updateTimer(bossName, seconds);
    }

    private static void updateTimer(String bossName, int seconds) {
        if (bossName.isBlank() || seconds < 0) {
            return;
        }

        long now = System.nanoTime();
        long deadline = now + seconds * 1_000_000_000L;

        TIMERS.compute(bossName, (name, oldTimer) -> {
            if (oldTimer == null) {
                return new BossTimer(name, deadline);
            }
            if (oldTimer.remainingSeconds(now) == 0 && seconds > 0) {
                oldTimer.alertPlayed = false;
            }
            // Server text is rounded to whole seconds and may arrive late. Keep the
            // local clock steady for small differences, but follow real corrections.
            long difference = deadline - oldTimer.deadlineNanos;
            if (Math.abs(difference) >= TIMER_SNAP_NANOS) {
                oldTimer.deadlineNanos = deadline;
            } else if (Math.abs(difference) > TIMER_TOLERANCE_NANOS) {
                oldTimer.deadlineNanos += Math.max(-TIMER_CORRECTION_STEP_NANOS,
                        Math.min(TIMER_CORRECTION_STEP_NANOS, difference));
            }
            return oldTimer;
        });
    }

    private static final class HologramLine {
        private final String text;
        private final int seconds;
        private final double x;
        private final double y;
        private final double z;

        private HologramLine(String text, int seconds, double x, double y, double z) {
            this.text = text;
            this.seconds = seconds;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private double distanceSquared(HologramLine other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static String cleanupBossName(String input) {
        String name = input.trim();

        // Remove accidental separators captured at the end of the name.
        while (name.endsWith("-") || name.endsWith("－") || name.endsWith("–") || name.endsWith("—")) {
            name = name.substring(0, name.length() - 1).trim();
        }

        return name;
    }

    private static void removeVeryOldTimers() {
        long now = System.nanoTime();
        long keepNanos = KEEP_AFTER_RESPAWN_SECONDS * 1_000_000_000L;
        TIMERS.values().removeIf(timer -> now - timer.deadlineNanos > keepNanos);
    }

    static void renderHud(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || TIMERS.isEmpty()) {
            return;
        }

        long now = System.nanoTime();
        List<BossTimer> timers = new ArrayList<>(TIMERS.values());
        timers.sort(Comparator.comparingInt((BossTimer timer) -> bossOrder(timer.name))
                .thenComparing(timer -> timer.name));

        int padding = 6;
        int lineHeight = 11;
        int titleHeight = 13;
        int maxWidth = client.textRenderer.getWidth("BOSS 重生倒數");

        List<String> rows = new ArrayList<>();
        for (BossTimer timer : timers) {
            long remaining = timer.remainingSeconds(now);
            String row = timer.name + "  " + formatRemaining(remaining);
            rows.add(row);
            maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(row));
        }

        int boxWidth = maxWidth + padding * 2;
        int boxHeight = titleHeight + rows.size() * lineHeight + padding * 2;
        float fitScale = Math.min(
                client.getWindow().getScaledWidth() / 960.0F,
                client.getWindow().getScaledHeight() / 540.0F);
        float hudScale = Math.max(0.55F, Math.min(1.08F, fitScale * 1.08F));
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        renderedHudWidth = Math.round(boxWidth * hudScale);
        renderedHudHeight = Math.round(boxHeight * hudScale);
        int savedX = BossAlertConfig.hudX;
        int savedY = BossAlertConfig.hudY;
        if (savedX >= 0 && BossAlertConfig.hudScreenWidth > 0) {
            savedX = Math.round(savedX * (screenWidth / (float) BossAlertConfig.hudScreenWidth));
        }
        if (BossAlertConfig.hudScreenHeight > 0) {
            savedY = Math.round(savedY * (screenHeight / (float) BossAlertConfig.hudScreenHeight));
        }
        int maxX = Math.max(0, screenWidth - renderedHudWidth);
        int maxY = Math.max(0, screenHeight - renderedHudHeight);
        int x = savedX < 0 ? Math.max(0, maxX - 8) : Math.min(maxX, savedX);
        int y = Math.min(maxY, Math.max(0, savedY));
        renderedHudX = x;
        renderedHudY = y;

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0.0F);
        context.getMatrices().scale(hudScale, hudScale, 1.0F);
        context.fill(0, 0, boxWidth, boxHeight, 0xA0000000);
        context.drawTextWithShadow(client.textRenderer, "⚔ BOSS 重生倒數", padding, padding, 0xFFFFAA00);

        int rowY = padding + titleHeight;
        for (int i = 0; i < timers.size(); i++) {
            BossTimer timer = timers.get(i);
            long remaining = timer.remainingSeconds(now);
            int color = timerColor(remaining);
            context.drawTextWithShadow(client.textRenderer, rows.get(i), padding, rowY, color);
            rowY += lineHeight;
        }
        context.getMatrices().pop();    }

    private static int timerColor(long remainingSeconds) {
        if (remainingSeconds <= 0) {
            return 0xFF55FF55;
        }
        if (remainingSeconds <= 60) {
            return 0xFFFF5555;
        }
        if (remainingSeconds <= 300) {
            return 0xFFFFFF55;
        }
        return 0xFFFFFFFF;
    }

    private static int bossOrder(String name) {
        if (name.contains("崩裂重")) return 0;
        if (name.contains("母蛛")) return 1;
        if (name.contains("風化大")) return 2;
        if (name.contains("萬菌主宰")) return 3;
        if (name.contains("沼澤吞噬者")) return 4;
        return 5;
    }

    private static String formatRemaining(long remainingSeconds) {
        if (remainingSeconds <= 0) {
            return "已重生";
        }

        long hours = remainingSeconds / 3600;
        long minutes = (remainingSeconds % 3600) / 60;
        long seconds = remainingSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static final class BossTimer {
        private final String name;
        private long deadlineNanos;
        private boolean alertPlayed;

        private BossTimer(String name, long deadlineNanos) {
            this.name = name;
            this.deadlineNanos = deadlineNanos;
        }

        private long remainingSeconds(long nowNanos) {
            long nanos = deadlineNanos - nowNanos;
            if (nanos <= 0) {
                return 0;
            }
            // Round up so 0.2 sec remaining still displays 00:01.
            return (nanos + 999_999_999L) / 1_000_000_000L;
        }
    }
}
