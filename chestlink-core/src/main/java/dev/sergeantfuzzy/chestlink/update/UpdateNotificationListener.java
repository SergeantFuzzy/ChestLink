package dev.sergeantfuzzy.chestlink.update;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UpdateNotificationListener implements Listener {
    private final UpdateService updateService;

    public UpdateNotificationListener(UpdateService updateService) {
        this.updateService = updateService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (updateService != null) {
            updateService.notifyJoin(event.getPlayer());
        }
    }
}
