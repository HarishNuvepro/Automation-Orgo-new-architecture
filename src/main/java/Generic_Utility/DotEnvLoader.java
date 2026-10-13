package Generic_Utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads credentials from an environment-specific file.
 *
 * Resolution order for active environment:
 *   1. -DENV=<name> JVM system property  (e.g. mvn test -DENV=miniprod)
 *   2. ENV=<name> key inside .env        (change one line to switch)
 *   3. Falls back to loading .env directly if ENV is absent
 *
 * Environment files live next to .env in the project root:
 *   .env           → contains only:  ENV=trail
 *   .env.trail     → trail URL + credentials
 *   .env.miniprod  → mini-prod URL + credentials
 *   .env.mainprod  → main-prod URL + credentials
 *
 * All .env* files are listed in .gitignore — never committed.
 */
public class DotEnvLoader {

    private static final Logger        log    = LoggerFactory.getLogger(DotEnvLoader.class);
    private static final String        BASE   = ".env";
    private static Map<String, String> envMap = new HashMap<>();

    private DotEnvLoader() {}

    /** Loads the active environment file. Safe to call multiple times — loads only once. */
    public static void load() {
        if (!envMap.isEmpty()) return;

        // 1. Determine active environment name
        String env = System.getProperty("ENV");                  // -DENV=miniprod from CLI
        if (blank(env)) {
            env = readKey(Paths.get(BASE), "ENV");               // ENV=trail inside .env
        }

        // 2. Load the environment-specific file, or fall back to .env
        if (!blank(env)) {
            String fileName = BASE + "." + env.trim().toLowerCase();
            Path   filePath = Paths.get(fileName);
            if (Files.exists(filePath)) {
                loadFile(filePath);
                log.info("Environment '{}' loaded from {} — {} key(s)",
                        env.trim(), filePath.toAbsolutePath(), envMap.size());
            } else {
                log.warn("ENV='{}' set but '{}' not found — loading {} directly",
                        env.trim(), fileName, BASE);
                loadFile(Paths.get(BASE));
            }
        } else {
            loadFile(Paths.get(BASE));
        }
    }

    /** Returns the value for the given key, or null if not present. */
    public static String get(String key) {
        return envMap.getOrDefault(key, null);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Parses all key=value pairs from a file into envMap. */
    private static void loadFile(Path path) {
        if (!Files.exists(path)) {
            log.debug("Env file not found at {} — using system env + Excel fallback",
                    path.toAbsolutePath());
            return;
        }
        try {
            Files.lines(path)
                 .map(String::trim)
                 .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                 .forEach(line -> {
                     int idx = line.indexOf('=');
                     if (idx > 0) {
                         String key   = line.substring(0, idx).trim();
                         String value = unquote(line.substring(idx + 1).trim());
                         if (!value.isEmpty()) {
                             envMap.put(key, value);
                         }
                     }
                 });
        } catch (IOException e) {
            log.warn("Failed to read {}: {} — continuing with system env + Excel fallback",
                    path, e.getMessage());
        }
    }

    /**
     * Reads a single key from a file without populating envMap.
     * Used to extract ENV= from .env before deciding which file to load.
     */
    private static String readKey(Path path, String targetKey) {
        if (!Files.exists(path)) return null;
        try {
            return Files.lines(path)
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .filter(line -> line.startsWith(targetKey + "="))
                        .map(line -> unquote(line.substring(targetKey.length() + 1).trim()))
                        .filter(v -> !v.isEmpty())
                        .findFirst()
                        .orElse(null);
        } catch (IOException e) {
            log.warn("Could not read key '{}' from {}: {}", targetKey, path, e.getMessage());
            return null;
        }
    }

    /**
     * Strips one pair of matching surrounding quotes (" or ') from a value, so
     * KEY="S6@jT1#nP9!wG3$" yields S6@jT1#nP9!wG3$ — special characters such as
     * #, $ and ! are kept literally (no expansion, no comment stripping).
     */
    private static String unquote(String value) {
        if (value != null && value.length() >= 2) {
            char first = value.charAt(0);
            char last  = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
