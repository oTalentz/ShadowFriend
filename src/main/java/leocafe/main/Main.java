package leocafe.main;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.command.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Sound;
import org.bukkit.Effect;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Main extends JavaPlugin implements Listener {
    private ConcurrentHashMap<UUID, LivingShadow> playerShadows = new ConcurrentHashMap<>();
    private ConcurrentHashMap<UUID, Boolean> manuallyDisabled = new ConcurrentHashMap<>();

    private FileConfiguration config;
    private int maxShadowsPerServer;
    private int spawnCooldown;
    private boolean defaultAttackMobs;

    @Override
    public void onEnable() {
        // Configuração
        saveDefaultConfig();
        config = getConfig();
        loadConfig();

        // Registro de eventos e comandos
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("shadow").setExecutor(new ShadowCommand(this));

        // Verificador de noite
        startNightChecker();

        getLogger().info("Plugin Shadow iniciado!");
    }

    private void loadConfig() {
        maxShadowsPerServer = config.getInt("maxShadowsPerServer", 50);
        spawnCooldown = config.getInt("spawnCooldown", 60);
        defaultAttackMobs = config.getBoolean("defaultAttackMobs", true);
    }

    private void startNightChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkNightAndSpawnShadows();
            }
        }.runTaskTimer(this, 0L, 100L); // Verifica a cada 5 segundos ao invés de 1
    }

    private void checkNightAndSpawnShadows() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.hasPermission("shadow.use")) continue;

            UUID playerId = player.getUniqueId();
            LivingShadow shadow = playerShadows.get(playerId);
            Boolean disabled = manuallyDisabled.get(playerId);

            if (disabled == Boolean.TRUE) {
                if (shadow != null) {
                    removeShadow(player);
                }
                continue;
            }

            World world = player.getWorld();
            long time = world.getTime();
            boolean isNight = time >= 13000 && time <= 23000;

            if (isNight && shadow == null && playerShadows.size() < maxShadowsPerServer) {
                spawnShadow(player);
            } else if (!isNight && shadow != null) {
                removeShadow(player);
            }
        }
    }

    public void spawnShadow(Player player) {
        if (!playerShadows.containsKey(player.getUniqueId()) &&
                player.hasPermission("shadow.use")) {
            manuallyDisabled.remove(player.getUniqueId());
            LivingShadow shadow = new LivingShadow(player, this, defaultAttackMobs);
            playerShadows.put(player.getUniqueId(), shadow);

            // Efeitos
            player.playSound(player.getLocation(), Sound.WITHER_SPAWN, 0.5f, 1.0f);
            player.getWorld().playEffect(player.getLocation(), Effect.SMOKE, 0);
        }
    }

    public void removeShadow(Player player) {
        LivingShadow shadow = playerShadows.remove(player.getUniqueId());
        if (shadow != null) {
            shadow.remove();
            player.playSound(player.getLocation(), Sound.WITHER_DEATH, 0.5f, 1.0f);
            player.getWorld().playEffect(player.getLocation(), Effect.SMOKE, 0);
            manuallyDisabled.put(player.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeShadow(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        removeShadow(event.getEntity());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§8Menu da Sombra")) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String itemName = clickedItem.getItemMeta().getDisplayName();
        if (itemName.contains("Ativar")) {
            spawnShadow(player);
            player.sendMessage("§aSombra ativada!");
        } else if (itemName.contains("Desativar")) {
            removeShadow(player);
            player.sendMessage("§cSombra desativada!");
        } else if (itemName.contains("Mobs")) {
            boolean attackMobs = toggleShadowAttackMobs(player);
            player.sendMessage(attackMobs ? "§aSombra agora atacará mobs!" : "§cSombra não atacará mais mobs!");
        } else if (itemName.contains("PvP") && player.hasPermission("shadow.admin")) {
            boolean attackPlayers = toggleShadowAttackPlayers(player);
            player.sendMessage(attackPlayers ? "§aSombra agora atacará jogadores!" : "§cSombra não atacará mais jogadores!");
        } else if (itemName.contains("Efeito:")) {
            LivingShadow shadow = this.getPlayerShadows().get(player.getUniqueId());
            if (shadow != null) {
                if (itemName.contains("Fumaça")) {
                    shadow.setEffect(Effect.SMOKE);
                } else if (itemName.contains("Chamas")) {
                    shadow.setEffect(Effect.FLAME);
                } else if (itemName.contains("Mágico")) {
                    shadow.setEffect(Effect.SPELL);
                }
                player.sendMessage("§aEfeito da sombra alterado!");
            }
        }

        player.closeInventory();
    }

    public boolean toggleShadowAttackMobs(Player player) {
        LivingShadow shadow = playerShadows.get(player.getUniqueId());
        if (shadow != null) {
            boolean newState = shadow.toggleAttackMobs();
            return newState;
        }
        return false;
    }

    public boolean toggleShadowAttackPlayers(Player player) {
        if (!player.hasPermission("shadow.admin")) return false;

        LivingShadow shadow = playerShadows.get(player.getUniqueId());
        if (shadow != null) {
            boolean newState = shadow.toggleAttackPlayers();
            return newState;
        }
        return false;
    }

    public HashMap<UUID, LivingShadow> getPlayerShadows() {
        return new HashMap<>(playerShadows);
    }
}

class ShadowCommand implements CommandExecutor {
    private final Main plugin;

    public ShadowCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando só pode ser usado por jogadores!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("shadow.use")) {
            player.sendMessage("§cVocê não tem permissão para usar este comando!");
            return true;
        }

        if (args.length > 0) {
            handleArguments(player, args);
        } else {
            openShadowMenu(player);
        }
        return true;
    }

    private void handleArguments(Player player, String[] args) {
        switch (args[0].toLowerCase()) {
            case "on":
                plugin.spawnShadow(player);
                player.sendMessage("§aSombra ativada!");
                break;
            case "off":
                plugin.removeShadow(player);
                player.sendMessage("§cSombra desativada!");
                break;
            case "attack":
                boolean attackMobs = plugin.toggleShadowAttackMobs(player);
                player.sendMessage(attackMobs ? "§aSombra agora atacará mobs!" : "§cSombra não atacará mais mobs!");
                break;
            case "pvp":
                if (player.hasPermission("shadow.admin")) {
                    boolean attackPlayers = plugin.toggleShadowAttackPlayers(player);
                    player.sendMessage(attackPlayers ? "§aSombra agora atacará jogadores!" : "§cSombra não atacará mais jogadores!");
                } else {
                    player.sendMessage("§cVocê não tem permissão para usar este comando!");
                }
                break;
            default:
                openShadowMenu(player);
        }
    }

    private void openShadowMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 36, "§8Menu da Sombra");

        // Ativar/Desativar Sombra
        ItemStack toggleItem = new ItemStack(Material.BONE);
        ItemMeta toggleMeta = toggleItem.getItemMeta();
        toggleMeta.setDisplayName(plugin.getPlayerShadows().containsKey(player.getUniqueId()) ? "§cDesativar Sombra" : "§aAtivar Sombra");
        toggleItem.setItemMeta(toggleMeta);
        menu.setItem(11, toggleItem);

        // Toggle Ataque a Mobs
        ItemStack mobsItem = new ItemStack(Material.ROTTEN_FLESH);
        ItemMeta mobsMeta = mobsItem.getItemMeta();
        mobsMeta.setDisplayName("§eToggle Ataque a Mobs");
        mobsItem.setItemMeta(mobsMeta);
        menu.setItem(13, mobsItem);

        // Efeitos
        ItemStack smokeEffect = new ItemStack(Material.SULPHUR);
        ItemMeta smokeMeta = smokeEffect.getItemMeta();
        smokeMeta.setDisplayName("§7Efeito: Fumaça");
        smokeEffect.setItemMeta(smokeMeta);
        menu.setItem(21, smokeEffect);

        ItemStack flameEffect = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta flameMeta = flameEffect.getItemMeta();
        flameMeta.setDisplayName("§6Efeito: Chamas");
        flameEffect.setItemMeta(flameMeta);
        menu.setItem(22, flameEffect);

        ItemStack magicEffect = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta magicMeta = magicEffect.getItemMeta();
        magicMeta.setDisplayName("§dEfeito: Mágico");
        magicEffect.setItemMeta(magicMeta);
        menu.setItem(23, magicEffect);


        // Toggle PvP (Admin)
        if (player.hasPermission("shadow.admin")) {
            ItemStack pvpItem = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta pvpMeta = pvpItem.getItemMeta();
            pvpMeta.setDisplayName("§cToggle PvP");
            pvpItem.setItemMeta(pvpMeta);
            menu.setItem(15, pvpItem);
        }

        player.openInventory(menu);
    }
}

class LivingShadow {
    private final Player owner;
    private Skeleton entity;
    private final JavaPlugin plugin;
    private boolean attackMobs;
    private boolean attackPlayers;
    private boolean isActive;
    private Effect currentEffect;

    public LivingShadow(Player owner, JavaPlugin plugin, boolean defaultAttackMobs) {
        this.owner = owner;
        this.plugin = plugin;
        this.attackMobs = defaultAttackMobs;
        this.attackPlayers = false;
        this.isActive = true;
        this.currentEffect = Effect.SMOKE;
        this.entity = spawn();
    }

    private Skeleton spawn() {
        Skeleton skeleton = (Skeleton) owner.getWorld().spawnEntity(owner.getLocation(), EntityType.SKELETON);
        skeleton.setCustomName("§8Sombra de " + owner.getName());
        skeleton.setCustomNameVisible(true);
        skeleton.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0));
        skeleton.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));

        startFollowTask();

        return skeleton;
    }

    private void startFollowTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive || !owner.isOnline()) {
                    this.cancel();
                    return;
                }

                if (entity == null || entity.isDead()) {
                    if (isActive) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (isActive) {
                                    entity = spawn();
                                }
                            }
                        }.runTaskLater(plugin, 200L); // 10 segundos
                    }
                    return;
                }

                Location ownerLoc = owner.getLocation();
                Location targetLoc = ownerLoc.clone();
                targetLoc.add(ownerLoc.getDirection().multiply(-4)); // 4 blocos atrás
                entity.teleport(targetLoc);

                if (attackMobs || attackPlayers) {
                    for (Entity nearby : entity.getNearbyEntities(5, 5, 5)) {
                        if (nearby instanceof LivingEntity && nearby != owner && nearby != entity) {
                            if ((nearby instanceof Monster && attackMobs) ||
                                    (nearby instanceof Player && attackPlayers && nearby != owner)) {
                                LivingEntity target = (LivingEntity) nearby;
                                if (target != owner) { // Previne dano ao dono
                                    entity.setTarget(target);
                                    break;
                                }
                            }
                        }
                    }
                }

                owner.getWorld().playEffect(entity.getLocation(), currentEffect, 0);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    public void setEffect(Effect effect) {
        this.currentEffect = effect;
    }

    public Effect getCurrentEffect() {
        return this.currentEffect;
    }

    public void remove() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
    }

    public boolean toggleAttackMobs() {
        this.attackMobs = !this.attackMobs;
        return this.attackMobs;
    }

    public boolean toggleAttackPlayers() {
        this.attackPlayers = !this.attackPlayers;
        return this.attackPlayers;
    }
}