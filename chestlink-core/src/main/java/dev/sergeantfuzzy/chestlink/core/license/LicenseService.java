package dev.sergeantfuzzy.chestlink.core.license;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class LicenseService {
    private static final String ACTIVATE_URL = "https://license.sergeantfuzzy.dev/api_activate";
    private static final String VALIDATE_URL = "https://license.sergeantfuzzy.dev/api_validate";
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z0-9]{6}-[A-Z0-9]{5}-[A-Z0-9]{6}$");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_UNLICENSED = "UNLICENSED";
    private static final String STATUS_INVALID = "INVALID";

    private final JavaPlugin corePlugin;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final File metadataFile;
    private YamlConfiguration metadataConfig;

    private volatile boolean licensed;
    private volatile String licenseKey;
    private String serverId;

    public LicenseService(JavaPlugin corePlugin) {
        this.corePlugin = corePlugin;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.metadataFile = new File(corePlugin.getDataFolder(), "license-data.yml");
        loadMetadata();
        loadFromConfig();
    }

    private void loadMetadata() {
        if (metadataFile.exists()) {
            metadataConfig = YamlConfiguration.loadConfiguration(metadataFile);
            return;
        }
        File parent = metadataFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        metadataConfig = new YamlConfiguration();
    }

    private ConfigurationSection ensureLicenseSection(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("license");
        if (section == null) {
            section = config.createSection("license");
        }
        return section;
    }

    private void loadFromConfig() {
        FileConfiguration config = corePlugin.getConfig();
        ConfigurationSection section = ensureLicenseSection(config);
        boolean changed = false;
        if (!section.isSet("key")) {
            section.set("key", "");
            changed = true;
        }
        if (!section.isSet("status")) {
            section.set("status", STATUS_UNLICENSED);
            changed = true;
        }
        String storedId = section.getString("server-id", metadataConfig.getString("server-id", ""));
        if (storedId == null || storedId.isBlank()) {
            storedId = UUID.randomUUID().toString();
            section.set("server-id", storedId);
            changed = true;
        }
        serverId = storedId;
        String rawKey = section.getString("key", "");
        licenseKey = rawKey == null ? "" : rawKey.trim().toUpperCase(Locale.ENGLISH);
        if (!Objects.equals(rawKey, licenseKey)) {
            section.set("key", licenseKey);
            changed = true;
        }
        String status = section.getString("status", STATUS_UNLICENSED);
        licensed = STATUS_ACTIVE.equalsIgnoreCase(status);
        if (changed) {
            corePlugin.saveConfig();
        }
        saveMetadata(status, false);
    }

    private String consolePrefix() {
        return ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "[" + ChatColor.GOLD + "ChestLink" + ChatColor.DARK_GRAY + ChatColor.BOLD + "] " + ChatColor.RESET;
    }

    public void reload() {
        loadFromConfig();
    }

    public boolean isLicensed() {
        return licensed;
    }

    private boolean isEnforcedBuild() {
        return corePlugin instanceof ChestLinkPlugin plugin && plugin.isLicenseEnforced();
    }

    public void validateOnStartup() {
        if (licenseKey == null || licenseKey.isBlank()) {
            setStatus(STATUS_UNLICENSED, false, true, true);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(corePlugin, () -> {
            ActivationResponse response = sendValidationRequest();
            if (response == null) {
                corePlugin.getLogger().warning("[ChestLink] Unable to contact license server for validation.");
                return;
            }
            Bukkit.getScheduler().runTask(corePlugin, () -> applyValidationResponse(response));
        });
    }

    public CompletableFuture<ActivationResult> activateLicenseAsync(Player player, String key) {
        CompletableFuture<ActivationResult> future = new CompletableFuture<>();
        if (player == null) {
            future.complete(ActivationResult.error("Only players can activate licenses."));
            return future;
        }
        String normalized = key == null ? "" : key.trim().toUpperCase(Locale.ENGLISH);
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            future.complete(ActivationResult.invalidFormat("Invalid license key format. Expected: XXXXXX-XXXXX-XXXXXX."));
            return future;
        }
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        String serverName = Bukkit.getServer().getName();
        String pluginVersion = corePlugin.getDescription().getVersion();
        Bukkit.getScheduler().runTaskAsynchronously(corePlugin, () -> {
            ActivationResult result = performActivationRequest(normalized, playerId, playerName, serverName, pluginVersion);
            Bukkit.getScheduler().runTask(corePlugin, () -> {
                if (result.isSuccess()) {
                    setLicenseKey(normalized);
                    setStatus(STATUS_ACTIVE, true, true, true);
                } else if (result.getStatus() == ActivationResult.Status.DECLINED) {
                    setStatus(STATUS_INVALID, false, true, true);
                }
                future.complete(result);
            });
        });
        return future;
    }

    private void applyValidationResponse(ActivationResponse response) {
        String status = response.status == null ? STATUS_INVALID : response.status.toUpperCase(Locale.ENGLISH);
        if (response.ok && STATUS_ACTIVE.equalsIgnoreCase(status)) {
            setStatus(STATUS_ACTIVE, true, true, true);
            corePlugin.getLogger().info("[ChestLink] License validated successfully.");
        } else {
            setStatus(STATUS_INVALID, false, true, true);
            String detail = response.message == null ? "Unknown error" : response.message;
            if (isEnforcedBuild()) {
                Bukkit.getConsoleSender().sendMessage(consolePrefix() + ChatColor.YELLOW + "Stored license key is invalid or revoked - beta features remain locked.");
            }
            corePlugin.getLogger().warning("[ChestLink] License validation failed: " + detail);
        }
    }

    private ActivationResult performActivationRequest(String key, UUID playerId, String playerName, String serverName, String pluginVersion) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("licenseKey", key);
            body.addProperty("serverId", serverId);
            body.addProperty("serverName", serverName);
            body.addProperty("pluginVersion", pluginVersion);
            body.addProperty("playerUuid", playerId.toString());
            body.addProperty("playerName", playerName);
            body.addProperty("serverLocation", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(ACTIVATE_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ActivationResponse parsed = gson.fromJson(response.body(), ActivationResponse.class);
            if (parsed == null) {
                return ActivationResult.error("Unexpected response from license server.");
            }
            if (parsed.ok && STATUS_ACTIVE.equalsIgnoreCase(parsed.status)) {
                return ActivationResult.success(parsed.message == null ? "License activated successfully." : parsed.message);
            }
            return ActivationResult.declined(parsed.message == null ? "License activation failed." : parsed.message);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            corePlugin.getLogger().warning("[ChestLink] License activation request failed: " + e.getMessage());
            return ActivationResult.error("Unable to contact license server. Please try again later.");
        }
    }

    private ActivationResponse sendValidationRequest() {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("licenseKey", licenseKey);
            body.addProperty("serverId", serverId);
            HttpRequest request = HttpRequest.newBuilder(URI.create(VALIDATE_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                corePlugin.getLogger().warning("[ChestLink] License validation failed with HTTP " + response.statusCode());
                return null;
            }
            return gson.fromJson(response.body(), ActivationResponse.class);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            corePlugin.getLogger().warning("[ChestLink] License validation error: " + e.getMessage());
            return null;
        }
    }

    private void setLicenseKey(String key) {
        licenseKey = key == null ? "" : key;
    }

    private void setStatus(String status, boolean licensedValue, boolean saveConfig, boolean updateValidationTime) {
        licensed = licensedValue;
        FileConfiguration config = corePlugin.getConfig();
        ConfigurationSection section = ensureLicenseSection(config);
        section.set("key", licenseKey == null ? "" : licenseKey);
        section.set("status", status);
        section.set("server-id", serverId);
        if (saveConfig) {
            corePlugin.saveConfig();
        }
        saveMetadata(status, updateValidationTime);
    }

    private void saveMetadata(String status, boolean updateValidationTime) {
        if (metadataConfig == null) {
            metadataConfig = new YamlConfiguration();
        }
        metadataConfig.set("server-id", serverId);
        metadataConfig.set("status", status);
        if (updateValidationTime) {
            metadataConfig.set("last-validation", Instant.now().toString());
        }
        try {
            metadataConfig.save(metadataFile);
        } catch (IOException e) {
            corePlugin.getLogger().warning("[ChestLink] Failed to save license metadata: " + e.getMessage());
        }
    }

    private static final class ActivationResponse {
        private boolean ok;
        private String status;
        private String message;
    }
}
