package net.sojeong.rescuecraft;

public class PlayerEnglishProfile {
    private static String currentLevel = null;

    public static String getCurrentLevel() {
        return currentLevel;
    }

    public static void saveLevel(String level) {
        currentLevel = level;
        System.out.println("[RescueCraft] Saved English level: " + level);
    }

    public static boolean hasLevel() {
        return currentLevel != null;
    }
}