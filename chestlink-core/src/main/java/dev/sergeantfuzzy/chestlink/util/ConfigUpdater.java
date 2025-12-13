package dev.sergeantfuzzy.chestlink.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Merges new defaults into an existing config while preserving user values and default comments.
 */
public final class ConfigUpdater {
    private ConfigUpdater() {
    }

    public static void mergeWithDefaults(File configFile, InputStream defaultsStream, Logger logger) {
        if (configFile == null || defaultsStream == null) {
            return;
        }
        try {
            if (!configFile.exists()) {
                // Nothing to merge if file is missing.
                return;
            }
            List<String> defaultLines = readAll(defaultsStream);
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(configFile);
            StringBuilder merged = new StringBuilder();
            List<String> pathStack = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (String line : defaultLines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    merged.append(line).append(System.lineSeparator());
                    continue;
                }

                int indent = leadingSpaces(line);
                int level = indent / 2;
                String key = parseKey(trimmed);
                if (key == null) {
                    merged.append(line).append(System.lineSeparator());
                    continue;
                }

                while (pathStack.size() > level) {
                    pathStack.remove(pathStack.size() - 1);
                }
                if (pathStack.size() == level) {
                    pathStack.add(key);
                } else if (pathStack.size() > level) {
                    pathStack.set(level, key);
                } else {
                    // Fill any gaps defensively
                    while (pathStack.size() < level) {
                        pathStack.add("");
                    }
                    pathStack.add(key);
                }

                String path = String.join(".", pathStack.subList(0, level + 1));
                boolean section = trimmed.endsWith(":") && !trimmed.contains(": ");
                if (section) {
                    merged.append(line).append(System.lineSeparator());
                    seen.add(path);
                    continue;
                }

                Object currentValue = existing.get(path);
                String rendered = currentValue == null ? valueFromLine(trimmed) : serializeValue(currentValue);
                seen.add(path);
                String newLine = " ".repeat(indent) + key + ": " + rendered;
                merged.append(newLine).append(System.lineSeparator());
            }

            appendCustomEntries(existing, seen, merged);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                writer.write(merged.toString());
            }
            cleanupLegacyHeader(configFile);
        } catch (Exception ex) {
            if (logger != null) {
                logger.warning("Failed to merge config defaults: " + ex.getMessage());
            }
        }
    }

    public static void mergeWithValues(File configFile, InputStream defaultsStream, org.bukkit.configuration.ConfigurationSection source, Logger logger) {
        if (configFile == null || defaultsStream == null || source == null) {
            return;
        }
        try {
            List<String> defaultLines = readAll(defaultsStream);
            StringBuilder merged = new StringBuilder();
            List<String> pathStack = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            YamlConfiguration sourceYaml = new YamlConfiguration();
            source.getKeys(true).forEach(k -> sourceYaml.set(k, source.get(k)));

            for (String line : defaultLines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    merged.append(line).append(System.lineSeparator());
                    continue;
                }

                int indent = leadingSpaces(line);
                int level = indent / 2;
                String key = parseKey(trimmed);
                if (key == null) {
                    merged.append(line).append(System.lineSeparator());
                    continue;
                }

                while (pathStack.size() > level) {
                    pathStack.remove(pathStack.size() - 1);
                }
                if (pathStack.size() == level) {
                    pathStack.add(key);
                } else if (pathStack.size() > level) {
                    pathStack.set(level, key);
                } else {
                    while (pathStack.size() < level) {
                        pathStack.add("");
                    }
                    pathStack.add(key);
                }

                String path = String.join(".", pathStack.subList(0, level + 1));
                boolean section = trimmed.endsWith(":") && !trimmed.contains(": ");
                if (section) {
                    merged.append(line).append(System.lineSeparator());
                    seen.add(path);
                    continue;
                }

                Object currentValue = sourceYaml.get(path);
                String rendered = currentValue == null ? valueFromLine(trimmed) : serializeValue(currentValue);
                seen.add(path);
                String newLine = " ".repeat(indent) + key + ": " + rendered;
                merged.append(newLine).append(System.lineSeparator());
            }

            appendCustomEntries(sourceYaml, seen, merged);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                writer.write(merged.toString());
            }
            cleanupLegacyHeader(configFile);
        } catch (Exception ex) {
            if (logger != null) {
                logger.warning("Failed to merge config with comments: " + ex.getMessage());
            }
        }
    }

    private static List<String> readAll(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        }
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String parseKey(String trimmedLine) {
        int idx = trimmedLine.indexOf(':');
        if (idx <= 0) {
            return null;
        }
        return trimmedLine.substring(0, idx).trim();
    }

    private static String valueFromLine(String trimmedLine) {
        int idx = trimmedLine.indexOf(':');
        if (idx < 0 || idx >= trimmedLine.length() - 1) {
            return "";
        }
        return trimmedLine.substring(idx + 1).trim();
    }

    private static String serializeValue(Object value) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("v", value);
        String out = yaml.saveToString();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith("v:")) {
                return line.substring("v:".length()).trim();
            }
        }
        return String.valueOf(value);
    }

    private static void appendCustomEntries(YamlConfiguration existing, Set<String> seen, StringBuilder merged) {
        Set<String> customKeys = new TreeSet<>();
        collectKeys("", existing, customKeys);
        customKeys.removeAll(seen);
        if (customKeys.isEmpty()) {
            return;
        }
        for (String path : customKeys) {
            if (seen.contains(path) || existing.isConfigurationSection(path)) {
                continue;
            }
            Object val = existing.get(path);
            if (val == null) {
                continue; // avoid writing null placeholders
            }
            String rendered = serializeValue(val);
            String[] parts = path.split("\\.");
            String key = parts[parts.length - 1];
            int indent = (parts.length - 1) * 2;
            merged.append(" ".repeat(indent)).append(key).append(": ").append(rendered).append(System.lineSeparator());
        }
    }

    private static void collectKeys(String base, YamlConfiguration yaml, Set<String> keys) {
        for (String key : yaml.getKeys(false)) {
            String path = base.isEmpty() ? key : base + "." + key;
            keys.add(path);
            if (yaml.isConfigurationSection(path)) {
                collectKeys(path, yaml, keys);
            }
        }
    }

    private static void cleanupLegacyHeader(File configFile) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
            String legacy = "# Custom entries preserved from your existing config";
            List<String> cleaned = new ArrayList<>();
            boolean skipNextBlank = false;
            for (String line : lines) {
                if (line.trim().equals(legacy)) {
                    skipNextBlank = true;
                    continue;
                }
                if (skipNextBlank) {
                    if (line.trim().isEmpty()) {
                        skipNextBlank = false;
                        continue;
                    }
                    skipNextBlank = false;
                }
                cleaned.add(line);
            }
            if (!cleaned.equals(lines)) {
                java.nio.file.Files.write(configFile.toPath(), cleaned, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }
}
