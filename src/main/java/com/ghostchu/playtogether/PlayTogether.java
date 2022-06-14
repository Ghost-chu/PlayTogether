package com.ghostchu.playtogether;

import com.alessiodp.parties.api.Parties;
import com.alessiodp.parties.api.events.bukkit.player.BukkitPartiesPlayerPostJoinEvent;
import com.alessiodp.parties.api.interfaces.PartiesAPI;
import com.alessiodp.parties.api.interfaces.Party;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class PlayTogether extends JavaPlugin implements Listener {
    private PartiesAPI api;

    private final Queue<PlayerEntry> upcoming = new LinkedList<>();

    private final String PREFIX = ChatColor.RED + "PlayTogether>> " + ChatColor.GREEN;

    private List<Advancement> advancements = new ArrayList<>();

    private boolean syncing = false;


    @Override
    public void onEnable() {
        // Plugin startup logic
        this.api = Parties.getApi();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("PlayTogether 挂接 Parties 成功");
        getLogger().info("PlayTogether 准备OK");
        Bukkit.getWorlds().forEach(world -> world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false));
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            syncing = true;
            long startTime = System.currentTimeMillis();
            boolean reached = false;
            if (upcoming.size() > 100) {
                Bukkit.broadcastMessage(PREFIX + "检测到积压成就同步队列，共计 " + upcoming.size() + " 个成就等待同步，处理期间，服务器可能短暂卡顿。");
                reached = true;
            }
            PlayerEntry entry = upcoming.poll();
            while (entry != null) {
                getLogger().info("Sync " + entry.player.getName() + " " + entry.advancement.getKey());
                PlayerEntry entryCopy = entry;
                entry = upcoming.poll();
                unlock(entryCopy.player, entryCopy.advancement);
            }
            if (reached) {
                Bukkit.broadcastMessage(PREFIX + "处理完成。(" + (System.currentTimeMillis() - startTime) + "ms)");
            }
            syncing = false;

        }, 0, 10);
        advancements = Util.getAdvancements(true);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Party party = api.getPartyOfPlayer(player.getUniqueId());
            if (party == null) {
                player.sendMessage(PREFIX + "您不在任何小队中，无需同步！");
                return true;
            }
            sync(player, party);
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void joinedParty(BukkitPartiesPlayerPostJoinEvent event) {
        UUID partyPlayer = event.getPartyPlayer().getPlayerUUID();
        Player player = Bukkit.getPlayer(partyPlayer);
        if (player == null) return;
        player.sendMessage(PREFIX + "您已成功加入小队 " + ChatColor.AQUA + event.getParty().getName() + ChatColor.GREEN + "。现在成就和进度信息将会在小队中同步。");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.sendMessage(PREFIX + "正在同步小队数据...");
            sync(player, event.getParty());
        }, 20L);
    }

    private List<Player> getPartyMembers(Party party) {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : party.getMembers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            players.add(player);
        }
        return players;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Party party = api.getPartyOfPlayer(player.getUniqueId());
        if (party == null)
            return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            event.getPlayer().sendMessage(PREFIX + "正在同步小队数据...");
            sync(player, party);
        }, 20L);

    }

    private void sync(Player player, Party party) {
        for (Advancement advancement : Util.getAdvancements(true)) {
            if (player.getAdvancementProgress(advancement).isDone()) {
                getPartyMembers(party)
                        .stream()
                        .filter(online -> !online.getAdvancementProgress(advancement).isDone())
                        .filter(online -> !upcoming.contains(new PlayerEntry(online, advancement)))
                        .forEach(online -> this.upcoming.add(new PlayerEntry(online, advancement)));
                continue;
            }
            for (Player online2 : getPartyMembers(party)) {
                if (!online2.getAdvancementProgress(advancement).isDone()) continue;
                PlayerEntry entry = new PlayerEntry(online2, advancement);
                if (!this.upcoming.contains(entry))
                    this.upcoming.add(entry);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (syncing)
            return;
        Advancement advancement = event.getAdvancement();
        if (advancements.contains(advancement)) {
            Player player = event.getPlayer();
            Party party = api.getPartyOfPlayer(player.getUniqueId());
            if (party == null)
                return;
            if (this.upcoming.contains(new PlayerEntry(player, advancement)))
                return;
            getPartyMembers(party).forEach(online -> {
                if (!online.getAdvancementProgress(advancement).isDone()) {
                    this.upcoming.add(new PlayerEntry(online, advancement));
                }
            });
        }
    }

    private void unlock(Player player, Advancement advancement) {
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        progress.getRemainingCriteria().forEach(progress::awardCriteria);
    }

    private static class PlayerEntry {
        private final Player player;
        private final Advancement advancement;

        private PlayerEntry(Player player, Advancement advancement) {
            this.player = player;
            this.advancement = advancement;
        }
    }


}
