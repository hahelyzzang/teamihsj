package net.sojeong.rescuecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * RescueCraft HUD overlay.
 * Injected via InGameHudMixin into Gui.extractRenderState().
 *
 * J key : toggle Todo list (top-right)
 * K key : toggle Command reference (center)
 * O key : toggle full chat history overlay
 */
public final class RescueCraftHud {

    // ---- todo state ----
    private static List<TodoEntry> entries = new ArrayList<>();
    private static boolean todoVisible = false;
    private static boolean helpVisible = false;
    private static boolean historyVisible = false;
    private static int historyScrollOffset = 0; // lines scrolled up from bottom
    private static int missionStage = 0; // 0=intro, 1=explore, 2=find Bori, 3=adopt Bori, 4+= server-driven
    private static String missionAnimalName = "the animal";

    // ---- layout ----
    private static final int PANEL_W     = 110;
    private static final int ROW_H       = 20;
    private static final int ICON_SIZE   = 16;
    private static final int PAD         = 4;

    // ---- colours ----
    private static final int COL_PANEL   = 0x88222222;
    private static final int COL_DONE    = 0xFF55FF55;
    private static final int COL_TODO    = 0xFFFFFFFF;
    private static final int COL_WHITE   = 0xFFFFFFFF;
    private static final int COL_HINT    = 0xAAFFFFFF;
    private static final int COL_PLAYER  = 0xFFFFFF55;  // yellow for player lines
    private static final int COL_SYSTEM  = 0xFFDDDDDD;  // light grey for system

    private static final String[] HELP_LINES = {
            "§e§lRescueCraft Commands",
            "",
            "§b/rcanimal interact",
            "  Right-click: befriend, feed, or water.",
            "",
            "§b/rcanimal talk <message>",
            "  Speak to the nearest befriended animal.",
            "  (Or just type in chat near it!)",
            "",
            "§b/rcanimal give",
            "  Give accepted food from inventory.",
            "",
            "§b/rcanimal water",
            "  Give water (hold a Water Bucket).",
            "",
            "§b/rcanimal status",
            "  Check trust state and herd need.",
            "",
            "§7Type §f!§7 first to send normal chat.",
            "",
            "§7Press §fK§7 to close",
    };


    private static String getMissionText() {
        return switch (missionStage) {
            case 0 -> "Introduce yourself to start your journey";
            case 1 -> "Explore the zoo and look around";
            case 2 -> "Find Bori the pig and right-click to befriend her";
            case 3 -> "Befriend Bori - right-click the pig!";
            case 4 -> "Help Bori: bring food and water for the herd";
            case 5 -> "Wait for Bori's herd to recover (3 days)";
            case 6 -> "Break the iron bars and set Bori free!";
            case 7 -> "Find another animal to help";
            case 8 -> "Help " + missionAnimalName + ": bring food and water";
            case 9 -> "Wait for " + missionAnimalName + "'s herd to recover";
            default -> "Explore and help the animals";
        };
    }

    private RescueCraftHud() {}

    public static void register() {}

    public static void updateEntries(List<TodoEntry> newEntries) {
        entries = new ArrayList<>(newEntries);
    }

    public static void toggleTodo()    { todoVisible    = !todoVisible; }
    public static void toggleHelp()    { helpVisible    = !helpVisible; }
    public static void toggleHistory() { historyVisible = !historyVisible; if (!historyVisible) historyScrollOffset = 0; }
    public static boolean isHistoryVisible() { return historyVisible; }
    public static void scrollHistory(double delta) { if (historyVisible) historyScrollOffset = Math.max(0, historyScrollOffset + (delta > 0 ? 1 : -1)); }
    public static void setClientMission(int stage) { missionStage = stage; }
    public static void updateMission(int stage, String animalName) { if (stage >= 0) { missionStage = stage; missionAnimalName = animalName; } }

    // =================== called from mixin ===================

    public static void onRender(GuiGraphicsExtractor gfx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        int sw = gfx.guiWidth();
        int sh = gfx.guiHeight();
        Font font = mc.font;

        // ---- key hint (bottom-right) ----
        String hint = "§7Todo=§fJ  §7Commands=§fK  §7History=§fO";
        gfx.text(font, hint, sw - font.width(hint) - PAD, sh - font.lineHeight - PAD, COL_HINT, true);

        // ---- mission banner (top-center) ----
        drawMission(gfx, font, sw);

        // ---- todo panel (top-right) ----
        if (todoVisible && !entries.isEmpty()) {
            int ph = PAD + entries.size() * ROW_H + PAD + font.lineHeight + 2;
            int px = sw - PANEL_W - PAD;
            int py = PAD + 20;
            gfx.fill(px, py, px + PANEL_W, py + ph, COL_PANEL);
            gfx.text(font, "§eTodo List", px + PAD, py + PAD / 2, COL_WHITE, true);
            int ry = py + PAD + font.lineHeight + 2;
            for (TodoEntry e : entries) {
                drawTodoRow(gfx, mc, font, px + PAD, ry, e);
                ry += ROW_H;
            }
        }

        // ---- command help overlay (center) ----
        if (helpVisible) {
            int lh = font.lineHeight + 2;
            int ow = 270;
            int oh = PAD * 2 + HELP_LINES.length * lh;
            int ox = (sw - ow) / 2;
            int oy = (sh - oh) / 2;
            gfx.fill(ox - 2, oy - 2, ox + ow + 2, oy + oh + 2, 0xCC111111);
            gfx.fill(ox, oy, ox + ow, oy + oh, 0xDD1E1E2E);
            int ty = oy + PAD;
            for (String line : HELP_LINES) {
                gfx.text(font, line, ox + PAD, ty, COL_WHITE, false);
                ty += lh;
            }
        }

        // ---- full history overlay ----
        if (historyVisible) {
            drawHistoryOverlay(gfx, mc, font, sw, sh);
        }
    }

    // =================== mission banner ===================

    private static void drawMission(GuiGraphicsExtractor gfx, Font font, int sw) {
        String label = "§7Mission: §e" + getMissionText();
        int w = font.width(label);
        int x = (sw - w) / 2;
        int y = 8;
        gfx.fill(x - PAD, y - 2, x + w + PAD, y + font.lineHeight + 2, 0x88111111);
        gfx.text(font, label, x, y, COL_WHITE, true);
    }

    // =================== history overlay ===================

    private static void drawHistoryOverlay(GuiGraphicsExtractor gfx, Minecraft mc,
                                           Font font, int sw, int sh) {
        int ow    = sw - 80;
        int oh    = sh - 80;
        int ox    = 40;
        int oy    = 40;
        int lineH = font.lineHeight + 2;
        int titleH = lineH + 4;
        int footerH = lineH + PAD;
        int contentH = oh - PAD * 2 - titleH - footerH;
        int maxVisible = contentH / lineH;

        gfx.fill(ox - 2, oy - 2, ox + ow + 2, oy + oh + 2, 0xCC111111);
        gfx.fill(ox, oy, ox + ow, oy + oh, 0xEE1E1E2E);

        // title
        gfx.text(font, "§e§lRescueCraft Chat History", ox + PAD, oy + PAD, COL_WHITE, false);

        // build all wrapped lines
        List<RescueCraftChat.ChatLine> all = RescueCraftChat.all();
        List<int[]> wrappedLines = new ArrayList<>(); // [lineIndex, isPlayer]
        List<String> wrappedTexts = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            RescueCraftChat.ChatLine line = all.get(i);
            String display = line.isPlayer() ? "§eYou: §r" + line.text() : line.text();
            List<String> wrapped = wrapText(font, display, ow - PAD * 2);
            for (String w : wrapped) {
                wrappedTexts.add(w);
                wrappedLines.add(new int[]{i, line.isPlayer() ? 1 : 0});
            }
        }

        int totalLines = wrappedTexts.size();
        // clamp scroll
        historyScrollOffset = Math.min(historyScrollOffset, Math.max(0, totalLines - maxVisible));

        int start = Math.max(0, totalLines - maxVisible - historyScrollOffset);
        int end   = Math.min(totalLines, start + maxVisible);

        int ty = oy + PAD + titleH;
        for (int i = start; i < end; i++) {
            int isPlayer = wrappedLines.get(i)[1];
            gfx.text(font, wrappedTexts.get(i), ox + PAD, ty, isPlayer == 1 ? COL_PLAYER : COL_SYSTEM, false);
            ty += lineH;
        }

        // scroll indicator
        if (totalLines > maxVisible) {
            String scrollHint = historyScrollOffset > 0
                    ? "§7↑ scroll ↓  (§f" + historyScrollOffset + "§7 lines up)"
                    : "§7scroll up to see more";
            gfx.text(font, scrollHint, ox + PAD, oy + oh - footerH, COL_HINT, false);
        }
        gfx.text(font, "§7Press §fO§7 to close",
                ox + ow - font.width("§7Press §fO§7 to close") - PAD,
                oy + oh - footerH, COL_HINT, false);
    }

    private static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (font.width(text) <= maxWidth) {
            lines.add(text);
            return lines;
        }
        // simple split by words
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (font.width(candidate) > maxWidth) {
                if (current.length() > 0) lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    // =================== todo row ===================

    private static void drawTodoRow(GuiGraphicsExtractor gfx, Minecraft mc, Font font,
                                    int x, int y, TodoEntry e) {
        ItemStack stack = e.isWater()
                ? new ItemStack(Items.WATER_BUCKET)
                : new ItemStack(e.getItem());
        gfx.item(mc.player, stack, x, y + (ROW_H - ICON_SIZE) / 2, 0);

        int tx = x + ICON_SIZE + PAD;
        int ty = y + (ROW_H - font.lineHeight) / 2;
        String text;
        int col;
        if (e.isWater()) {
            text = "§7" + e.getAnimalName() + " §rWater";
            col  = COL_TODO;
        } else if (e.isDone()) {
            text = "§7" + e.getAnimalName() + " §r" + e.getGiven() + "/" + e.getNeeded() + " §a✓";
            col  = COL_DONE;
        } else {
            text = "§7" + e.getAnimalName() + " §r" + e.getGiven() + "/" + e.getNeeded();
            col  = COL_TODO;
        }
        gfx.text(font, text, tx, ty, col, true);
    }
}