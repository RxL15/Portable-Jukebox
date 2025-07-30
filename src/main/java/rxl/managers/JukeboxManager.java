package rxl.managers;

import rxl.Jukebox;
import rxl.utils.ItemUtil;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JukeboxManager {

    private final Jukebox plugin;
    private final DataManager dataManager;
    private final Map<String, ItemStack[]> jukeboxInventories = new ConcurrentHashMap<>();
    private final Map<String, Boolean> playingState = new ConcurrentHashMap<>();
    private final Map<String, Integer> currentTrackSlot = new ConcurrentHashMap<>();
    private final Map<String, BukkitRunnable> playingTasks = new ConcurrentHashMap<>();
    private final Map<String, BukkitRunnable> particleTasks = new ConcurrentHashMap<>();
    private final Map<String, BukkitRunnable> timerTasks = new ConcurrentHashMap<>();
    private final Map<String, Player> playingPlayers = new ConcurrentHashMap<>();
    private final Map<String, Long> trackStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> trackDurations = new ConcurrentHashMap<>();

    private static final List<Integer> QUEUE_ORDER = List.of(0, 9, 18, 1, 10, 19);
    private static final double HEARING_RANGE = 24.0;

    public JukeboxManager(Jukebox plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        loadData();
    }

    private void loadData() {
        Map<String, ItemStack[]> loadedData = dataManager.loadJukeboxData();
        jukeboxInventories.putAll(loadedData);
    }

    public void saveData() {
        dataManager.saveJukeboxData(new HashMap<>(jukeboxInventories));
    }

    public ItemStack[] getJukeboxInventory(String jukeboxId) {
        return jukeboxInventories.get(jukeboxId);
    }

    public void setJukeboxInventory(String jukeboxId, ItemStack[] inventory) {
        if (inventory == null) {
            jukeboxInventories.remove(jukeboxId);
        } else {
            jukeboxInventories.put(jukeboxId, inventory);
        }
    }

    public ItemStack getPlayingDisc(String jukeboxId) {
        Integer slot = currentTrackSlot.get(jukeboxId);
        if (slot == null) return null;
        ItemStack[] inventory = getJukeboxInventory(jukeboxId);
        if (inventory == null || slot >= inventory.length) return null;
        return inventory[slot];
    }

    public boolean hasMusicDisc(String jukeboxId) {
        ItemStack[] inventory = getJukeboxInventory(jukeboxId);
        if (inventory == null) return false;

        for (int slot : QUEUE_ORDER) {
            if (slot < inventory.length && ItemUtil.isMusicDisc(inventory[slot])) {
                return true;
            }
        }
        return false;
    }

    public boolean isPlaying(String jukeboxId) {
        return playingState.getOrDefault(jukeboxId, false);
    }

    public Integer getCurrentTrackSlot(String jukeboxId) {
        return currentTrackSlot.get(jukeboxId);
    }

    public void togglePlaying(String jukeboxId, Player player) {
        if (isPlaying(jukeboxId)) {
            stopPlaying(jukeboxId, player);
        } else {
            if (hasMusicDisc(jukeboxId)) {
                playNextTrack(jukeboxId, player, -1);
            } else {
                player.sendActionBar(Component.text("No music discs found in jukebox!", NamedTextColor.RED));
            }
        }
    }

    private void startPlaying(String jukeboxId, Player player, int slot) {
        ItemStack[] inventory = getJukeboxInventory(jukeboxId);
        if (inventory == null || slot >= inventory.length) return;

        ItemStack musicDisc = inventory[slot];
        if (!ItemUtil.isMusicDisc(musicDisc)) return;

        org.bukkit.Sound discSound = getBukkitSound(musicDisc.getType());
        if (discSound == null) {
            return;
        }

        Sound soundToPlay = Sound.sound(org.bukkit.NamespacedKey.minecraft(discSound.toString().toLowerCase()), Sound.Source.RECORD, 1.0f, 1.0f);
        player.getWorld().playSound(soundToPlay, player);
        playingState.put(jukeboxId, true);
        playingPlayers.put(jukeboxId, player);
        currentTrackSlot.put(jukeboxId, slot);
        long duration = getMusicDiscDuration(musicDisc.getType());
        trackStartTimes.put(jukeboxId, System.currentTimeMillis());
        trackDurations.put(jukeboxId, duration);
        startParticleEffects(jukeboxId, player);
        startTimerTask(jukeboxId, player);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (playingState.getOrDefault(jukeboxId, false)) {
                    playNextTrack(jukeboxId, player, slot);
                } else {
                    stopPlaying(jukeboxId, player);
                }
            }
        };
        task.runTaskLater(plugin, duration);
        playingTasks.put(jukeboxId, task);
        Component actionBarMessage = Component.text("♪ Now Playing: ", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(getMusicDiscName(musicDisc.getType()), NamedTextColor.YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false))
                .append(Component.text(" ♪", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));

        Location playerLocation = player.getLocation();
        for (Player nearbyPlayer : player.getWorld().getPlayers()) {
            if (nearbyPlayer.getLocation().distance(playerLocation) <= HEARING_RANGE) {
                nearbyPlayer.sendActionBar(actionBarMessage);
            }
        }
        updateLoreInPlayerInventory(player, jukeboxId, getMusicDiscName(musicDisc.getType()));
    }

    private void startTimerTask(String jukeboxId, Player player) {
        BukkitRunnable timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!playingState.getOrDefault(jukeboxId, false) || !player.isOnline()) {
                    this.cancel();
                    timerTasks.remove(jukeboxId);
                    return;
                }

                ItemStack playingDisc = getPlayingDisc(jukeboxId);
                if (playingDisc != null) {
                    updateLoreInPlayerInventory(player, jukeboxId, getMusicDiscName(playingDisc.getType()));
                }
            }
        };

        timerTask.runTaskTimer(plugin, 0L, 20L);
        timerTasks.put(jukeboxId, timerTask);
    }

    public void playNextTrack(String jukeboxId, Player player, int lastSlot) {
        ItemStack[] inventory = getJukeboxInventory(jukeboxId);
        if (inventory == null) {
            stopPlaying(jukeboxId, player);
            return;
        }

        int lastSlotIndex = QUEUE_ORDER.indexOf(lastSlot);
        for (int i = 0; i < QUEUE_ORDER.size(); i++) {
            int nextIndex = (lastSlotIndex + 1 + i) % QUEUE_ORDER.size();
            int nextSlot = QUEUE_ORDER.get(nextIndex);
            if (nextSlot < inventory.length && ItemUtil.isMusicDisc(inventory[nextSlot])) {
                startPlaying(jukeboxId, player, nextSlot);
                return;
            }
        }
        stopPlaying(jukeboxId, player);
    }

    private void startParticleEffects(String jukeboxId, Player player) {
        stopParticleEffects(jukeboxId);
        scheduleNextParticleEffect(jukeboxId, player);
    }

    private void scheduleNextParticleEffect(final String jukeboxId, final Player player) {
        BukkitRunnable particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!playingState.getOrDefault(jukeboxId, false) || !player.isOnline() || isCancelled()) {
                    particleTasks.remove(jukeboxId);
                    return;
                }

                Location playerLoc = player.getLocation();
                Location particleLoc = playerLoc.clone().add(0, 1.8, 0);
                int particleCount = 1 + (int)(Math.random() * 3);
                for (int i = 0; i < particleCount; i++) {
                    double offsetX = (Math.random() - 0.5) * 0.4;
                    double offsetZ = (Math.random() - 0.5) * 0.4;
                    double offsetY = Math.random() * 0.2;
                    Location spawnLoc = particleLoc.clone().add(offsetX, offsetY, offsetZ);
                    player.getWorld().spawnParticle(Particle.NOTE, spawnLoc, 1);
                }
                scheduleNextParticleEffect(jukeboxId, player);
            }
        };
        long randomDelay = 5L + (long)(Math.random() * 15);
        particleTask.runTaskLater(plugin, randomDelay);
        particleTasks.put(jukeboxId, particleTask);
    }

    private void stopParticleEffects(String jukeboxId) {
        BukkitRunnable particleTask = particleTasks.remove(jukeboxId);
        if (particleTask != null) {
            particleTask.cancel();
        }
    }

    private void stopTimerTask(String jukeboxId) {
        BukkitRunnable timerTask = timerTasks.remove(jukeboxId);
        if (timerTask != null) {
            timerTask.cancel();
        }
    }

    public void stopPlaying(String jukeboxId, Player player) {
        updateLoreInPlayerInventory(player, jukeboxId, null);
        BukkitRunnable task = playingTasks.remove(jukeboxId);
        if (task != null) {
            task.cancel();
        }

        playingState.put(jukeboxId, false);
        currentTrackSlot.remove(jukeboxId);
        trackStartTimes.remove(jukeboxId);
        trackDurations.remove(jukeboxId);
        stopParticleEffects(jukeboxId);
        stopTimerTask(jukeboxId);
        playingPlayers.remove(jukeboxId);
        if (player != null && player.isOnline()) {
            Location playerLocation = player.getLocation();
            Component actionBarMessage = Component.text("♪ Music Stopped", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(" ♪", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            for (Player nearbyPlayer : player.getWorld().getPlayers()) {
                if (nearbyPlayer.getLocation().distance(playerLocation) <= HEARING_RANGE) {
                    nearbyPlayer.stopSound(SoundStop.source(Sound.Source.RECORD));
                    nearbyPlayer.sendActionBar(actionBarMessage);
                }
            }
        }
    }

    public void stopPlayingById(String jukeboxId) {
        Player player = playingPlayers.get(jukeboxId);
        updateLoreInPlayerInventory(player, jukeboxId, null);
        BukkitRunnable task = playingTasks.remove(jukeboxId);
        if (task != null) {
            task.cancel();
        }

        if (player != null && player.isOnline()) {
            Location playerLocation = player.getLocation();
            Component actionBarMessage = Component.text("♪ Jukebox removed - Music stopped", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(" ♪", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            for (Player nearbyPlayer : player.getWorld().getPlayers()) {
                if (nearbyPlayer.getLocation().distance(playerLocation) <= HEARING_RANGE) {
                    nearbyPlayer.stopSound(SoundStop.source(Sound.Source.RECORD));
                    nearbyPlayer.sendActionBar(actionBarMessage);
                }
            }
        }

        playingState.put(jukeboxId, false);
        currentTrackSlot.remove(jukeboxId);
        trackStartTimes.remove(jukeboxId);
        trackDurations.remove(jukeboxId);
        stopParticleEffects(jukeboxId);
        stopTimerTask(jukeboxId);
        playingPlayers.remove(jukeboxId);
    }

    private org.bukkit.Sound getBukkitSound(Material material) {
        return switch (material) {
            case MUSIC_DISC_13 -> org.bukkit.Sound.MUSIC_DISC_13;
            case MUSIC_DISC_CAT -> org.bukkit.Sound.MUSIC_DISC_CAT;
            case MUSIC_DISC_BLOCKS -> org.bukkit.Sound.MUSIC_DISC_BLOCKS;
            case MUSIC_DISC_CHIRP -> org.bukkit.Sound.MUSIC_DISC_CHIRP;
            case MUSIC_DISC_FAR -> org.bukkit.Sound.MUSIC_DISC_FAR;
            case MUSIC_DISC_MALL -> org.bukkit.Sound.MUSIC_DISC_MALL;
            case MUSIC_DISC_MELLOHI -> org.bukkit.Sound.MUSIC_DISC_MELLOHI;
            case MUSIC_DISC_STAL -> org.bukkit.Sound.MUSIC_DISC_STAL;
            case MUSIC_DISC_STRAD -> org.bukkit.Sound.MUSIC_DISC_STRAD;
            case MUSIC_DISC_WARD -> org.bukkit.Sound.MUSIC_DISC_WARD;
            case MUSIC_DISC_11 -> org.bukkit.Sound.MUSIC_DISC_11;
            case MUSIC_DISC_WAIT -> org.bukkit.Sound.MUSIC_DISC_WAIT;
            case MUSIC_DISC_OTHERSIDE -> org.bukkit.Sound.MUSIC_DISC_OTHERSIDE;
            case MUSIC_DISC_5 -> org.bukkit.Sound.MUSIC_DISC_5;
            case MUSIC_DISC_PIGSTEP -> org.bukkit.Sound.MUSIC_DISC_PIGSTEP;
            case MUSIC_DISC_RELIC -> org.bukkit.Sound.MUSIC_DISC_RELIC;
            case MUSIC_DISC_CREATOR -> org.bukkit.Sound.MUSIC_DISC_CREATOR;
            case MUSIC_DISC_CREATOR_MUSIC_BOX -> org.bukkit.Sound.MUSIC_DISC_CREATOR_MUSIC_BOX;
            case MUSIC_DISC_PRECIPICE -> org.bukkit.Sound.MUSIC_DISC_PRECIPICE;
            default -> null;
        };
    }

    private String getMusicDiscName(Material material) {
        return switch (material) {
            case MUSIC_DISC_13 -> "13";
            case MUSIC_DISC_CAT -> "Cat";
            case MUSIC_DISC_BLOCKS -> "Blocks";
            case MUSIC_DISC_CHIRP -> "Chirp";
            case MUSIC_DISC_FAR -> "Far";
            case MUSIC_DISC_MALL -> "Mall";
            case MUSIC_DISC_MELLOHI -> "Mellohi";
            case MUSIC_DISC_STAL -> "Stal";
            case MUSIC_DISC_STRAD -> "Strd";
            case MUSIC_DISC_WARD -> "Ward";
            case MUSIC_DISC_11 -> "11";
            case MUSIC_DISC_WAIT -> "Wait";
            case MUSIC_DISC_OTHERSIDE -> "Otherside";
            case MUSIC_DISC_5 -> "5";
            case MUSIC_DISC_PIGSTEP -> "Pigstep";
            case MUSIC_DISC_RELIC -> "Relic";
            case MUSIC_DISC_CREATOR -> "Creator";
            case MUSIC_DISC_CREATOR_MUSIC_BOX -> "Creator (Music Box)";
            case MUSIC_DISC_PRECIPICE -> "Precipice";
            case MUSIC_DISC_TEARS -> "Tears";
            default -> "Unknown";
        };
    }

    private long getMusicDiscDuration(Material material) {
        return switch (material) {
            case MUSIC_DISC_13 -> 178 * 20L; // 2:58
            case MUSIC_DISC_CAT -> 185 * 20L; // 3:05
            case MUSIC_DISC_BLOCKS -> 345 * 20L; // 5:45
            case MUSIC_DISC_CHIRP -> 186 * 20L; // 3:06
            case MUSIC_DISC_FAR -> 174 * 20L; // 2:54
            case MUSIC_DISC_MALL -> 197 * 20L; // 3:17
            case MUSIC_DISC_MELLOHI -> 96 * 20L; // 1:36
            case MUSIC_DISC_STAL -> 150 * 20L; // 2:30
            case MUSIC_DISC_STRAD -> 188 * 20L; // 3:08
            case MUSIC_DISC_WARD -> 251 * 20L; // 4:11
            case MUSIC_DISC_11 -> 71 * 20L; // 1:11
            case MUSIC_DISC_WAIT -> 238 * 20L; // 3:58
            case MUSIC_DISC_OTHERSIDE -> 195 * 20L; // 3:15
            case MUSIC_DISC_5 -> 176 * 20L; // 2:56
            case MUSIC_DISC_PIGSTEP -> 148 * 20L; // 2:28
            case MUSIC_DISC_RELIC -> 218 * 20L; // 3:38
            case MUSIC_DISC_CREATOR -> 177 * 20L; // 2:57
            case MUSIC_DISC_CREATOR_MUSIC_BOX -> 170 * 20L; // 2:50
            case MUSIC_DISC_PRECIPICE -> 294 * 20L; // 4:54
            case MUSIC_DISC_TEARS -> 175 * 20L; // 2:55
            default -> 200 * 20L; // 3:20 (default)
        };
    }

    public String createJukeboxId() {
        return "jukebox-" + UUID.randomUUID();
    }

    public void cleanup() {
        saveData();
    }

    private String formatTime(long ticks) {
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void updateLoreInPlayerInventory(Player player, String jukeboxId, String trackName) {
        if (player == null || !player.isOnline()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && ItemUtil.isJukebox(item) && jukeboxId.equals(ItemUtil.getJukeboxId(item))) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta == null) continue;
                    List<Component> lore = new ArrayList<>();
                    if (trackName != null && !trackName.isEmpty()) {
                        lore.add(Component.empty());
                        lore.add(Component.text("♪ Playing: ", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                                .append(Component.text(trackName, NamedTextColor.YELLOW)));
                        Long startTime = trackStartTimes.get(jukeboxId);
                        Long duration = trackDurations.get(jukeboxId);
                        if (startTime != null && duration != null) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            long elapsedTicks = elapsed * 20 / 1000;
                            long remainingTicks = Math.max(0, duration - elapsedTicks);
                            String currentTime = formatTime(elapsedTicks);
                            String totalTime = formatTime(duration);
                            String remainingTime = formatTime(remainingTicks);
                            lore.add(Component.text("⏱ Time: ", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                                    .append(Component.text(currentTime + " / " + totalTime, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
                            lore.add(Component.text("⏳ Remaining: ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
                                    .append(Component.text(remainingTime, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
                        }
                    }
                    meta.lore(lore);
                    item.setItemMeta(meta);
                    player.getInventory().setItem(i, item);
                    return;
                }
            }
        });
    }
}