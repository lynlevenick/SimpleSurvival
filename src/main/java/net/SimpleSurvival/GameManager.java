package net.SimpleSurvival;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;


/**
 * Created by maldridge on 10/21/14.
 */
public class GameManager implements Listener {
    private final String worldUUID = UUID.randomUUID().toString();

    private final SimpleSurvival plugin;
    ArrayList<String> spectators = new ArrayList<String>();
    private GameState state = GameState.BEFORE_GAME;
    private ArrayList<InventoryHolder> openedChests = new ArrayList<InventoryHolder>();
    private GameTemplate staticSettings;
    private ArrayList<String> competitors;

    public GameManager(SimpleSurvival plugin, GameTemplate staticSettings, ArrayList<String> competitors) {
        this.plugin = plugin;
        this.staticSettings = staticSettings;
        this.competitors = competitors;
        this.plugin.getLogger().info("Warping the following players to  " + this.staticSettings.getSourceWorld() + ": " + competitors.toString());
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    public ArrayList<String> getCompetitors() {
        return competitors;
    }

    public boolean isCompetitor(String name) {
        return competitors.contains(name);
    }

    public List<Integer[]> getSpawns() {
        return this.staticSettings.getSpawns();
    }

    public HashMap<Material, Double> getLoot() {
        return this.staticSettings.getLoot();
    }

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return this.getWorld() + "-" + this.getWorldUUID().substring(0, 6);
    }

    public String getWorld() {
        return staticSettings.getSourceWorld();
    }

    public String getWorldUUID() {
        return worldUUID;
    }

    public boolean doAnimals() {
        return staticSettings.doAnimals();
    }

    public boolean doHostileMobs() {
        return staticSettings.doHostileMobs();
    }

    public boolean doAutoWarp() {
        return staticSettings.doAutoWarp();
    }

    public boolean doAutoStart() {
        return staticSettings.doAutoStart();
    }

    public void sendPlayersToSpawn() {
        for (int i = 0; i < competitors.size(); i++) {
            int x = staticSettings.getSpawns().get(i)[0];
            int y = staticSettings.getSpawns().get(i)[1];
            int z = staticSettings.getSpawns().get(i)[2];
            World w = Bukkit.getWorld(worldUUID);
            Location nextSpawn = new Location(w, x, y, z);
            Player player = Bukkit.getPlayer(competitors.get(i));
            player.teleport(nextSpawn);
            setCompetitorMode(player);
        }
    }

    public void start() {
        if (state == GameState.BEFORE_GAME) {
            new GameStarter(this.plugin, this, 15).runTaskTimer(this.plugin, 0, 20);
            state = GameState.STARTING;
        } else {
            this.plugin.getLogger().warning("start() was called on a game that wasn't in BEFORE_GAME");
        }
    }

    public void end(boolean silent) {
        if (silent) {
            for (Player p : Bukkit.getWorld(worldUUID).getPlayers()) {
                p.sendMessage("This world is being unloaded");
            }

            for (Player p : Bukkit.getWorld(worldUUID).getPlayers()) {
                p.teleport(Bukkit.getServer().getWorlds().get(0).getSpawnLocation());
            }

            plugin.worldManager.destroyWorld(worldUUID);
            unregisterListeners();
        } else {
            BukkitTask countDownTimer = new GameEnder(this.plugin, this, 10).runTaskTimer(this.plugin, 0, 20);
        }
    }

    public void announceWinner() {
        for (Player p : Bukkit.getWorld(worldUUID).getPlayers()) {
            if (competitors.size() == 1) {
                p.sendMessage(competitors.get(0) + " has won the round!");
            }
        }

        if (competitors.size() == 1) {
            this.plugin.getLogger().info(competitors.get(0) + " has won the round.");
        } else {
            this.plugin.getLogger().info("A serious error has occurred, winners list of size " + competitors.size() + ": " + competitors.toString());
        }
    }

    private void dropPlayerInventory(Player player) {
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), i);
                player.getInventory().remove(i);
            }
        }
        player.getInventory().clear();
        // Whoever wrote this, why specifically 4 nulls? Is this some Bukkit internal thing?
        player.getInventory().setArmorContents(new ItemStack[]{null, null, null, null});
    }

    private void setSpectatorMode(String player) {
        Player p = Bukkit.getPlayer(player);
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setCanPickupItems(false);
        for (Player pl : p.getWorld().getPlayers()) {
            pl.hidePlayer(p);
        }
    }

    private void setCompetitorMode(Player player) {
        player.setAllowFlight(false);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFoodLevel(20);
        player.setHealth(20);
        player.setLevel(0);
        player.getInventory().clear();
    }

    public void unregisterListeners() {
        PlayerMoveEvent.getHandlerList().unregister(this);
        EntityDamageEvent.getHandlerList().unregister(this);
        EntityDamageByEntityEvent.getHandlerList().unregister(this);
        InventoryOpenEvent.getHandlerList().unregister(this);
        BlockBreakEvent.getHandlerList().unregister(this);
        PlayerPickupItemEvent.getHandlerList().unregister(this);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player ply = event.getPlayer();
        String plyName = ply.getName();
        if (ply.getWorld().getName().equals(worldUUID)) {
            if (isCompetitor(plyName)) {
                if (state != GameState.RUNNING) {
                    Vector to = event.getTo().toVector();
                    Vector from = event.getFrom().toVector();
                    if (to.getX() != from.getX() || to.getZ() != from.getZ()) {
                        ply.teleport(event.getFrom());
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity().getWorld().getName().equals(worldUUID)) {
            if ((event.getEntity() instanceof Player)) {
                Player ply = (Player) event.getEntity();
                String plyName = ply.getName();

                if (isCompetitor(plyName)) {
                    Player killer;

                    if (event.getDamager() instanceof Player) {
                        killer = (Player) event.getDamager();
                    } else if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
                        killer = (Player) ((Projectile) event.getDamager()).getShooter();
                    } else {
                        event.setCancelled(true);
                        return;
                    }

                    String killerName = killer.getName();
                    if (isCompetitor(killerName)) {
                        if (ply.getHealth() - event.getDamage() <= 0) {
                            event.setCancelled(true);
                            playerKilled(ply, killer);
                        }
                    } else {
                        event.setCancelled(true);
                    }
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity().getWorld().getName().equals(worldUUID)) {
            if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                if (event.getEntity() instanceof Player) {
                    Player ply = ((Player) event.getEntity()).getPlayer();
                    String plyName = ply.getName();
                    if (isCompetitor(plyName) && ply.getHealth() - event.getDamage() <= 0) {
                        //Player is dead
                        event.setCancelled(true);
                        competitors.remove(plyName);

                        dropPlayerInventory(ply);
                        setSpectatorMode(plyName);

                        for (Player pl : Bukkit.getWorld(worldUUID).getPlayers()) {
                            pl.sendMessage(ChatColor.RED + "[DEATH] " + ChatColor.BOLD + plyName + ChatColor.RESET + " was killed by " + ChatColor.BOLD + event.getCause().toString());
                        }

                        //if there is only one competitor left, set the game state to finished
                        if (competitors.size() <= 1) {
                            announceWinner();
                            state = GameState.FINISHED;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent inventoryOpenEvent) {
        if (inventoryOpenEvent.getPlayer().getWorld().getName().equals(worldUUID)) {
            String player = inventoryOpenEvent.getPlayer().getName();

            if (spectators.contains(player)) {
                inventoryOpenEvent.setCancelled(true);
                return;
            }

            Inventory inventory = inventoryOpenEvent.getInventory();
            InventoryHolder holder = inventory.getHolder();

            if (holder instanceof Chest || holder instanceof DoubleChest) {
                if (!openedChests.contains(holder)) {
                    openedChests.add(holder);
                    for (Map.Entry<Material, Double> lootEntry : staticSettings.getLoot().entrySet()) {
                        if (Math.random() * 100 < lootEntry.getValue()) {
                            inventory.addItem(new ItemStack(lootEntry.getKey()));
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent breakEvent) {
        if (!this.staticSettings.getBreakables().contains(breakEvent.getBlock().getType())) {
            breakEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (!isCompetitor(event.getPlayer().getName())) {
            event.setCancelled(true);
        }
    }

    private void playerKilled(Player ply, Player killer) {
        //Player is dead
        String plyName = ply.getName();
        competitors.remove(plyName);
        setSpectatorMode(plyName);
        dropPlayerInventory(ply);
        for (Player pl : Bukkit.getWorld(worldUUID).getPlayers()) {
            pl.sendMessage(ChatColor.RED + "[DEATH]" + ChatColor.BOLD + plyName + ChatColor.RESET + " was killed by " + ChatColor.BOLD + killer.getName());
        }
        //if there is only one competitor left, set the game state to finished
        if (competitors.size() <= 1) {
            announceWinner();
            state = GameState.FINISHED;
        }
    }

    public enum GameState {BEFORE_GAME, STARTING, RUNNING, PAUSED, FINISHED}
}

