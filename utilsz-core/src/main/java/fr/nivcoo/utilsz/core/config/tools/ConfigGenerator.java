package fr.nivcoo.utilsz.core.config.tools;

import fr.nivcoo.utilsz.core.config.ConfigManager;

import java.nio.file.*;

public final class ConfigGenerator {
    public static void generate(String baseDir, String outRelPath, String cfgClassName, boolean overwrite) {
        try {
            Path resDir = Path.of(baseDir, "src/main/resources");
            Files.createDirectories(resDir);
            Path out = resDir.resolve(outRelPath);

            if (overwrite && Files.exists(out)) Files.delete(out);

            Class<?> cfgClass = Class.forName(cfgClassName);
            new ConfigManager(resDir.toFile()).load(outRelPath, cfgClass);
            System.out.println("[ConfigGenerator] Généré: " + out);
        } catch (Throwable t) {
            throw new RuntimeException("Generation failed for " + outRelPath + " using " + cfgClassName, t);
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Args: <outRelPath> <cfgClassName> [--overwrite]");
        String baseDir = System.getProperty("basedir", ".");
        boolean overwrite = args.length >= 3 && "--overwrite".equalsIgnoreCase(args[2]);
        generate(baseDir, args[0], args[1], overwrite);
    }
}
