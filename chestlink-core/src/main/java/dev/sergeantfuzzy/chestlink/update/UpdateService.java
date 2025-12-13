package dev.sergeantfuzzy.chestlink.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sergeantfuzzy.chestlink.ChestLinkPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class UpdateService {
    private static final String SPIGOT_URL = "https://www.spigotmc.org/resources/chestlink.130427/";
    private static final String SPIGET_VERSION_URL = "https://api.spiget.org/v2/resources/130427/versions/latest";
    private static final String GITHUB_RELEASE_API = "https://api.github.com/repos/SergeantFuzzy/ChestLink/releases/latest";
    private static final String GITHUB_RELEASE_URL = "https://github.com/SergeantFuzzy/ChestLink/releases/latest";

    private final ChestLinkPlugin plugin;
    private final HttpClient client;
    private volatile UpdateResult lastResult;
    private final Map<UUID, String> notifiedVersion = new ConcurrentHashMap<>();

    public UpdateService(ChestLinkPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
    }

    public boolean isMasterEnabled() {
        return plugin.getConfig().getBoolean("update-notifications.enabled", true);
    }

    public boolean isCheckEnabled() {
        return isMasterEnabled() && plugin.getConfig().getBoolean("update-notifications.check-enabled", true);
    }

    public boolean isPlayerNotifyEnabled() {
        return isMasterEnabled() && plugin.getConfig().getBoolean("update-notifications.player-notify-enabled", true);
    }

    public UpdateResult getLastResult() {
        return lastResult;
    }

    public CompletableFuture<UpdateResult> checkNowAsync(boolean logToConsole) {
        if (!isCheckEnabled()) {
            UpdateResult res = UpdateResult.unknown(plugin.getDescription().getVersion());
            lastResult = res;
            if (logToConsole) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().info("Update checks are disabled in config.yml (update-notifications.enabled/check-enabled)."));
            }
            return CompletableFuture.completedFuture(res);
        }
        String current = plugin.getDescription().getVersion();
        return CompletableFuture.supplyAsync(() -> fetchLatest(current)).whenComplete((result, error) -> {
            if (result != null) {
                lastResult = result;
                if (logToConsole) {
                    Bukkit.getScheduler().runTask(plugin, () -> announceConsole(result));
                }
            } else if (logToConsole) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().warning("Update check failed: " + (error == null ? "Unknown error" : error.getMessage())));
            }
        });
    }

    public void runStartupCheck() {
        checkNowAsync(true);
    }

    public void notifyJoin(Player player) {
        if (!isPlayerNotifyEnabled() || !isCheckEnabled() || player == null || (!player.isOp() && !player.hasPermission("chestlink.update.notify"))) {
            return;
        }
        UpdateResult cached = lastResult;
        CompletableFuture<UpdateResult> future;
        if (cached == null && isCheckEnabled()) {
            future = checkNowAsync(false);
        } else {
            future = CompletableFuture.completedFuture(Optional.ofNullable(cached).orElse(UpdateResult.unknown(plugin.getDescription().getVersion())));
        }
        future.thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> sendJoinMessage(player, result)));
    }

    private UpdateResult fetchLatest(String current) {
        Map<String, String> sources = new LinkedHashMap<>();
        String spigotVersion = fetchSpigetVersion().map(v -> {
            sources.put("Spigot", SPIGOT_URL);
            return v;
        }).orElse(null);
        String githubVersion = fetchGithubVersion().map(v -> {
            sources.put("GitHub", GITHUB_RELEASE_URL);
            return v;
        }).orElse(null);

        String latest = highestVersion(List.of(spigotVersion, githubVersion, current));
        UpdateResult.Status status = determineStatus(current, latest);
        return new UpdateResult(current, latest, status, sources, Instant.now());
    }

    private Optional<String> fetchSpigetVersion() {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(SPIGET_VERSION_URL)).GET().build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null) return Optional.empty();
            JsonElement element = JsonParser.parseString(resp.body());
            if (!element.isJsonObject()) return Optional.empty();
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("name")) {
                return Optional.ofNullable(obj.get("name").getAsString());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private Optional<String> fetchGithubVersion() {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(GITHUB_RELEASE_API)).header("Accept", "application/vnd.github+json").GET().build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null) return Optional.empty();
            JsonElement element = JsonParser.parseString(resp.body());
            if (!element.isJsonObject()) return Optional.empty();
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("tag_name")) {
                return Optional.ofNullable(obj.get("tag_name").getAsString());
            }
            if (obj.has("name")) {
                return Optional.ofNullable(obj.get("name").getAsString());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private UpdateResult.Status determineStatus(String current, String latest) {
        int cmp = compareVersions(current, latest);
        if (cmp < 0) {
            return UpdateResult.Status.OUTDATED;
        }
        if (cmp > 0) {
            return UpdateResult.Status.AHEAD;
        }
        return UpdateResult.Status.UP_TO_DATE;
    }

    private String highestVersion(List<String> candidates) {
        String best = null;
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            if (best == null || compareVersions(candidate, best) > 0) {
                best = candidate;
            }
        }
        return best == null ? "Unknown" : best;
    }

    private int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        List<String> partsA = splitVersion(a);
        List<String> partsB = splitVersion(b);
        int max = Math.max(partsA.size(), partsB.size());
        for (int i = 0; i < max; i++) {
            String pa = i < partsA.size() ? partsA.get(i) : "0";
            String pb = i < partsB.size() ? partsB.get(i) : "0";
            boolean na = pa.matches("\\d+");
            boolean nb = pb.matches("\\d+");
            if (na && nb) {
                int diff = Integer.compare(Integer.parseInt(pa), Integer.parseInt(pb));
                if (diff != 0) return diff;
            } else {
                int diff = pa.compareToIgnoreCase(pb);
                if (diff != 0) return diff;
            }
        }
        return 0;
    }

    private List<String> splitVersion(String version) {
        String[] tokens = version.split("[^A-Za-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isBlank()) {
                out.add(token);
            }
        }
        return out;
    }

    private void announceConsole(UpdateResult result) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        if (result.getStatus() == UpdateResult.Status.OUTDATED) {
            console.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "ChestLink" + ChatColor.DARK_GRAY + "] "
                    + ChatColor.YELLOW + "Update available! Current: " + ChatColor.WHITE + result.getCurrentVersion()
                    + ChatColor.YELLOW + " Latest: " + ChatColor.WHITE + result.getLatestVersion());
            result.getSources().forEach((name, url) -> console.sendMessage(ChatColor.DARK_AQUA + " - " + name + ": " + ChatColor.UNDERLINE + url));
        } else {
            console.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "ChestLink" + ChatColor.DARK_GRAY + "] "
                    + ChatColor.GREEN + "Running latest (or newer) build: " + ChatColor.WHITE + result.getCurrentVersion());
        }
        warnIfPreRelease(console, result.getCurrentVersion());
    }

    private void sendJoinMessage(Player player, UpdateResult result) {
        if (result == null) {
            return;
        }
        String cacheKey = result.getLatestVersion() + ":" + result.getStatus().name();
        if (cacheKey.equals(notifiedVersion.get(player.getUniqueId()))) {
            return;
        }
        notifiedVersion.put(player.getUniqueId(), cacheKey);

        if (result.getStatus() == UpdateResult.Status.OUTDATED) {
            String url = Optional.ofNullable(result.bestUrl()).orElse(SPIGOT_URL);
            net.md_5.bungee.api.chat.TextComponent base = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GRAY + "[" + ChatColor.GOLD + "ChestLink" + ChatColor.GRAY + "] " + ChatColor.YELLOW + "Update available! ");
            net.md_5.bungee.api.chat.TextComponent versions = new net.md_5.bungee.api.chat.TextComponent(ChatColor.WHITE + result.getCurrentVersion() + ChatColor.GRAY + " -> " + ChatColor.GREEN + result.getLatestVersion() + " ");
            net.md_5.bungee.api.chat.TextComponent button = new net.md_5.bungee.api.chat.TextComponent(ChatColor.AQUA + "" + ChatColor.UNDERLINE + "[Download]");
            button.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
            button.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{new net.md_5.bungee.api.chat.TextComponent(ChatColor.YELLOW + "Click to open the latest ChestLink download page")}));
            base.addExtra(versions);
            base.addExtra(button);
            player.spigot().sendMessage(base);
        } else {
            net.md_5.bungee.api.chat.TextComponent base = new net.md_5.bungee.api.chat.TextComponent(ChatColor.GRAY + "[" + ChatColor.GOLD + "ChestLink" + ChatColor.GRAY + "] " + ChatColor.GREEN + "You're up to date! ");
            net.md_5.bungee.api.chat.TextComponent button = new net.md_5.bungee.api.chat.TextComponent(ChatColor.AQUA + "" + ChatColor.UNDERLINE + "[Open Wiki]");
            button.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, "https://sergeantfuzzy.dev/wiki/plugins/chestlink/"));
            button.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{new net.md_5.bungee.api.chat.TextComponent(ChatColor.YELLOW + "Click to view the ChestLink wiki")}));
            base.addExtra(button);
            player.spigot().sendMessage(base);
        }
    }

    private void warnIfPreRelease(ConsoleCommandSender console, String version) {
        if (version == null) {
            return;
        }
        String upper = version.toUpperCase(Locale.ENGLISH);
        String tag = null;
        for (String candidate : List.of("SNAPSHOT", "ALPHA", "BETA")) {
            if (upper.contains(candidate)) {
                tag = candidate;
                break;
            }
        }
        if (tag == null) {
            return;
        }
        String divider = ChatColor.DARK_GRAY + "----------------------------------------";
        console.sendMessage("");
        console.sendMessage(divider);
        console.sendMessage(ChatColor.GOLD + "⚠ " + ChatColor.WHITE + "You are running a " + ChatColor.YELLOW + tag + ChatColor.WHITE + " build of ChestLink.");
        console.sendMessage(ChatColor.GOLD + "⚠ " + ChatColor.YELLOW + "Backups are strongly recommended.");
        console.sendMessage(ChatColor.GOLD + "⚠ " + ChatColor.YELLOW + "Report bugs at: " + ChatColor.AQUA + "https://github.com/SergeantFuzzy/ChestLink/issues");
        console.sendMessage(divider);
    }
}
