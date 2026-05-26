package net.sojeong.rescuecraft;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

public final class WorldTemplateInstaller {
    private static final String WORLD_NAME = "RescueCraft_World";

    private static final String TEMPLATE_FOLDER_NAME = "rescuecraft_world";

    // 개발 중에는 true 추천. 실행할 때마다 월드를 템플릿으로 다시 복사합니다.
    private static final boolean RESET_WORLD_EVERY_LAUNCH = true;

    private WorldTemplateInstaller() {
    }

    public static void installWorldTemplate() {
        System.out.println("[RescueCraft] World installer is running!");

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path projectDir = gameDir.getParent();

        if (projectDir == null) {
            System.err.println("[RescueCraft] Could not find project directory.");
            return;
        }

        Path templateWorldDir = projectDir
                .resolve("src")
                .resolve("main")
                .resolve("resources")
                .resolve("assets")
                .resolve("rescuecraft")
                .resolve("world_templates")
                .resolve(TEMPLATE_FOLDER_NAME);

        Path savesDir = gameDir.resolve("saves");
        Path targetWorldDir = savesDir.resolve(WORLD_NAME);

        System.out.println("[RescueCraft] Game dir: " + gameDir);
        System.out.println("[RescueCraft] Project dir: " + projectDir);
        System.out.println("[RescueCraft] Template dir: " + templateWorldDir);
        System.out.println("[RescueCraft] Target world dir: " + targetWorldDir);

        try {
            if (!Files.exists(templateWorldDir)) {
                System.err.println("[RescueCraft] Template folder does not exist: " + templateWorldDir);
                return;
            }

            if (!Files.exists(templateWorldDir.resolve("level.dat"))) {
                System.err.println("[RescueCraft] level.dat not found directly inside: " + templateWorldDir);
                return;
            }

            Files.createDirectories(savesDir);

            if (Files.exists(targetWorldDir)) {
                if (!RESET_WORLD_EVERY_LAUNCH) {
                    System.out.println("[RescueCraft] World already exists. Skipping copy.");
                    return;
                }

                deleteDirectory(targetWorldDir);
            }

            copyDirectory(templateWorldDir, targetWorldDir);

            System.out.println("[RescueCraft] Installed template world successfully.");

        } catch (Exception e) {
            System.err.println("[RescueCraft] Failed to install template world.");
            e.printStackTrace();
        }
    }

    private static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.forEach(sourcePath -> {
                try {
                    Path relativePath = sourceDir.relativize(sourcePath);

                    if (relativePath.toString().isEmpty()) {
                        Files.createDirectories(targetDir);
                        return;
                    }

                    if (shouldSkipWorldEntry(relativePath)) {
                        return;
                    }

                    Path targetPath = targetDir.resolve(relativePath);

                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static boolean shouldSkipWorldEntry(Path relativePath) {
        String normalized = relativePath.toString().replace("\\", "/");

        return normalized.startsWith("playerdata/")
                || normalized.startsWith("stats/")
                || normalized.startsWith("advancements/")
                || normalized.equals("session.lock")
                || normalized.equals("uid.dat");
    }

    private static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}