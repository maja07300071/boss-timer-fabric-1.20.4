package tw.bosstimer.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

final class BossAlertConfig {
    static final Identifier DRAGON = new Identifier("minecraft", "entity.ender_dragon.growl");
    static final Identifier LEVEL_UP = new Identifier("minecraft", "entity.player.levelup");
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("boss-timer.properties");

    static int seconds = 30;
    static Identifier sound = LEVEL_UP;
    static boolean enabled = true;
    static boolean titleEnabled = true;
    static int titleColor = 0xFFFFFF;
    static int hudX = -1;
    static int hudY = 8;
    static int hudScreenWidth;
    static int hudScreenHeight;
    static final int[] TITLE_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    static List<Identifier> allowedSounds() {
        return Registries.SOUND_EVENT.getIds().stream()
                .filter(BossAlertConfig::isAllowedSound)
                .sorted(Comparator.comparing((Identifier id) -> !id.equals(LEVEL_UP))
                        .thenComparing(Identifier::toString))
                .toList();
    }

    private static boolean isAllowedSound(Identifier id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return false;
        }

        String path = id.getPath();
        if (path.equals(LEVEL_UP.getPath())) return true;
        // Keep hostile/monster entity sounds while excluding blocks, items,
        // weather, UI and friendly animal sounds from the selector.
        String[] monsters = {
                "blaze", "bogged", "breeze", "cave_spider", "creeper", "drowned",
                "elder_guardian", "ender_dragon", "enderman", "endermite", "evoker",
                "ghast", "guardian", "hoglin", "husk", "illusioner", "magma_cube",
                "phantom", "piglin", "piglin_brute", "pillager", "ravager", "shulker",
                "silverfish", "skeleton", "slime", "spider", "stray", "vex", "vindicator",
                "warden", "witch", "wither", "wither_skeleton", "zoglin", "zombie",
                "zombie_villager"
        };
        for (String monster : monsters) {
            String prefix = "entity." + monster + ".";
            if (path.startsWith(prefix) && (path.endsWith(".ambient")
                    || path.endsWith(".idle_air") || path.endsWith(".idle_water")
                    || path.endsWith(".idle_land"))) return true;
        }
        return false;
    }

    static void load() {
        Properties values = new Properties();
        if (Files.exists(PATH)) {
            try (InputStream input = Files.newInputStream(PATH)) {
                values.load(input);
            } catch (IOException ignored) {
            }
        }
        try {
            seconds = Math.max(0, Math.min(86400, Integer.parseInt(values.getProperty("seconds", "30"))));
        } catch (NumberFormatException ignored) {
            seconds = 30;
        }
        Identifier id = Identifier.tryParse(values.getProperty("sound", LEVEL_UP.toString()));
        sound = id != null && Registries.SOUND_EVENT.containsId(id) && isAllowedSound(id)
                ? id
                : LEVEL_UP;
        enabled = Boolean.parseBoolean(values.getProperty("enabled", "true"));
        titleEnabled = Boolean.parseBoolean(values.getProperty("titleEnabled", "true"));
        try {
            titleColor = Integer.parseInt(values.getProperty("titleColor", "FFFFFF"), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            titleColor = 0xFFFFFF;
        }
        boolean validColor = false;
        for (int color : TITLE_COLORS) if (color == titleColor) validColor = true;
        if (!validColor) titleColor = 0xFFFFFF;
        try { hudX = Integer.parseInt(values.getProperty("hudX", "-1")); } catch (NumberFormatException ignored) { hudX = -1; }
        try { hudY = Integer.parseInt(values.getProperty("hudY", "8")); } catch (NumberFormatException ignored) { hudY = 8; }
        try { hudScreenWidth = Integer.parseInt(values.getProperty("hudScreenWidth", "0")); } catch (NumberFormatException ignored) { hudScreenWidth = 0; }
        try { hudScreenHeight = Integer.parseInt(values.getProperty("hudScreenHeight", "0")); } catch (NumberFormatException ignored) { hudScreenHeight = 0; }
    }

    static void save() {
        Properties values = new Properties();
        values.setProperty("seconds", Integer.toString(seconds));
        values.setProperty("sound", sound.toString());
        values.setProperty("enabled", Boolean.toString(enabled));
        values.setProperty("titleEnabled", Boolean.toString(titleEnabled));
        values.setProperty("titleColor", String.format("%06X", titleColor));
        values.setProperty("hudX", Integer.toString(hudX));
        values.setProperty("hudY", Integer.toString(hudY));
        values.setProperty("hudScreenWidth", Integer.toString(hudScreenWidth));
        values.setProperty("hudScreenHeight", Integer.toString(hudScreenHeight));
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream output = Files.newOutputStream(PATH)) {
                values.store(output, "Boss Timer settings");
            }
        } catch (IOException ignored) {
        }
    }
}
