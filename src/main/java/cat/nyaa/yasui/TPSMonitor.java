package cat.nyaa.yasui;


import com.udojava.evalex.Expression;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;

public class TPSMonitor extends BukkitRunnable {
    private final Yasui plugin;
    private BigDecimal tps_1m = new BigDecimal(20);
    private BigDecimal tps_5m = new BigDecimal(20);
    private BigDecimal tps_15m = new BigDecimal(20);

    public TPSMonitor(Yasui pl) {
        plugin = pl;
        this.runTaskTimer(plugin, 20, plugin.config.check_interval_tick);
    }

    @Override
    public void run() {
        if (!plugin.config.enable) {
            return;
        }
        updateTPS();
        for (String key : plugin.config.rules.keySet()) {
            Rule rule = plugin.config.rules.get(key);
            if (rule != null && rule.enable && rule.condition != null && rule.condition.length() > 0) {
                BigDecimal result = null;
                if (rule.worlds == null || rule.worlds.isEmpty()) {
                    result = eval(rule.condition, null);
                    if (result != null && result.intValue() > 0) {
                        runRule(rule, null);
                    }
                } else {
                    for (String worldName : rule.worlds) {
                        World w = Bukkit.getWorld(worldName);
                        if (w != null) {
                            result = eval(rule.condition, w);
                            if (result != null && result.intValue() > 0) {
                                runRule(rule, w);
                            }
                        } else {
                            plugin.getLogger().warning(String.format("rule: %s, world %s not exist.", key, worldName));
                        }
                    }
                }
            }
        }
    }

    public void runRule(Rule rule, World world) {
        BigDecimal enableAI = eval(rule.enable_ai, world);
        if (enableAI != null && enableAI.intValue() > 0) {
            plugin.enableAI(world);
        }
        BigDecimal disableAI = eval(rule.disable_ai, world);
        if (disableAI != null && disableAI.intValue() > 0) {
            plugin.disableAI(world);
        }
        if (world != null) {
            BigDecimal tickSpeed = eval(rule.world_random_tick_speed, world);
            if (tickSpeed != null && tickSpeed.intValue() >= 0) {
                Utils.setRandomTickSpeed(world, tickSpeed.intValue());
            }
        }
    }

    public BigDecimal eval(String condition, World world) {
        if (condition != null && condition.length() > 0) {
            Expression exp = new Expression(condition)
                    .with("tps_1m", tps_1m)
                    .with("tps_5m", tps_5m)
                    .with("tps_15m", tps_15m)
                    .with("online_players", new BigDecimal(Bukkit.getOnlinePlayers().size()))
                    .with("random_tick_speed", new BigDecimal(Bukkit.getWorlds().get(0).getGameRuleValue(GameRule.RANDOM_TICK_SPEED)));
            if (world != null) {
                exp.with("world_random_tick_speed", new BigDecimal(world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED)))
                        .with("loaded_chunks", new BigDecimal(world.getLoadedChunks().length))
                        .with("world_players", new BigDecimal(world.getPlayers().size()))
                        .with("world_living_entities", new BigDecimal(world.getEntities().size()));
            }
            return exp.eval();
        }
        return null;
    }

    public void updateTPS() {
        long now = System.currentTimeMillis();
        double[] tps = Utils.getTPS();
        tps_1m = new BigDecimal(tps[0]);
        tps_5m = new BigDecimal(tps[1]);
        tps_15m = new BigDecimal(tps[2]);
    }
}
