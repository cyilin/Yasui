package cat.nyaa.yasui;


import cat.nyaa.nyaacore.Message;
import com.udojava.evalex.AbstractFunction;
import com.udojava.evalex.Expression;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigDecimal;
import java.util.List;

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
        //HashSet<String> old_disableAIWorlds = new HashSet<>(plugin.disableAIWorlds);
        //HashSet<String> old_entityLimitWorlds = new HashSet<>(plugin.entityLimitWorlds);
        for (String key : plugin.config.rules.keySet()) {
            Rule rule = plugin.config.rules.get(key);
            if (rule != null && rule.enable && rule.condition != null && rule.condition.length() > 0) {
                BigDecimal result = null;
                if (rule.worlds != null && !rule.worlds.isEmpty()) {
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
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.config.ignored_world.contains(world.getName())) {
                String name = world.getName();
                //if ((!old_entityLimitWorlds.contains(name) && plugin.entityLimitWorlds.contains(name)) ||
                //        (old_disableAIWorlds.contains(name) != plugin.disableAIWorlds.contains(name))) {
                    Utils.checkWorld(world);
                //}
            }
        }
    }

    public void runRule(Rule rule, World world) {
        BigDecimal enableAI = eval(rule.enable_ai, world);
        if (enableAI != null && enableAI.intValue() > 0) {
            plugin.disableAIWorlds.remove(world.getName());
        }
        BigDecimal disableAI = eval(rule.disable_ai, world);
        if (disableAI != null && disableAI.intValue() > 0) {
            plugin.disableAIWorlds.add(world.getName());
        }
        BigDecimal tickSpeed = eval(rule.world_random_tick_speed, world);
        if (tickSpeed != null && tickSpeed.intValue() >= 0) {
            Utils.setRandomTickSpeed(world, tickSpeed.intValue());
        }
        BigDecimal limitEnable = eval(rule.entity_limit_enable, world);
        if (limitEnable != null && limitEnable.intValue() > 0) {
            plugin.entityLimitWorlds.add(world.getName());
        }
        BigDecimal limitDisable = eval(rule.entity_limit_disable, world);
        if (limitDisable != null && limitDisable.intValue() > 0) {
            plugin.entityLimitWorlds.remove(world.getName());
        }
        if (rule.messageType != null && rule.message != null) {
            String msg = rule.message.replaceAll("\\{tps_1m}", String.format("%.2f", tps_1m.doubleValue()))
                    .replaceAll("\\{tps_5m}", String.format("%.2f", tps_5m.doubleValue()))
                    .replaceAll("\\{tps_15m}", String.format("%.2f", tps_15m.doubleValue()))
                    .replaceAll("\\{tps_1m}", String.format("%.2f", tps_1m.doubleValue()));
            if (tickSpeed != null) {
                msg = msg.replaceAll("\\{world_random_tick_speed}", String.valueOf(tickSpeed.intValue()));
            }
            new Message(ChatColor.translateAlternateColorCodes('&', msg)).broadcast(rule.messageType, p -> (p.getWorld().equals(world)));
        }
        if (rule.commands != null) {
            for (String cmd : rule.commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
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
                    .with("random_tick_speed", new BigDecimal(world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED)));
            if (Yasui.hasNU) {
                exp.addFunction(new AbstractFunction("getTPSFromNU", 1) {
                    @Override
                    public BigDecimal eval(List<BigDecimal> parameters) {
                        return Utils.getTPSFromNU(parameters.get(0).intValue());
                    }
                });
            }
            exp.with("world_random_tick_speed", new BigDecimal(world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED)))
                    .with("loaded_chunks", new BigDecimal(world.getLoadedChunks().length))
                    .with("world_players", new BigDecimal(world.getPlayers().size()))
                    .with("world_living_entities", new BigDecimal(world.getLivingEntities().size()));
            return exp.eval();
        }
        return null;
    }

    public void updateTPS() {
        double[] tps = Utils.getTPS();
        tps_1m = new BigDecimal(tps[0]);
        tps_5m = new BigDecimal(tps[1]);
        tps_15m = new BigDecimal(tps[2]);
    }
}
