package me.fhyk.antiSpamAlacity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;




public class AntiSpamAlacity extends JavaPlugin implements Listener, TabExecutor {
    private List<String> keywords;
    private int keywordMessagesCount;
    private long keywordTime;
    private int normalMessages;
    private long normalMsgTime;
    private boolean sendMuteMessage;
    private String muteMessage;
    private boolean kick;
    private String kickMessage;
    private boolean sendMessage;
    private String spamMessage;
    private long spamMuteTime;
    private Set<String> mutes =new HashSet<>();
    private Set<String> spamBypassPlayers=new HashSet<>();
    private final Map<String, List<Long>> keywordmessages=new ConcurrentHashMap<>();
    private final Map<String, List<Long>> normalmessages=new ConcurrentHashMap<>();
    private final Map<String, Long> shadowmutetime=new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("antispam").setExecutor(this);
        getCommand("mute").setExecutor(this);
        getCommand("unmute").setExecutor(this);

        getCommand("mutelist").setExecutor(this);
    }

    private void loadSettings() {
        FileConfiguration cfg=getConfig();
        keywords=cfg.getStringList("filtered-words");
        keywordMessagesCount=cfg.getInt("keyword-messages-count", 1);
        keywordTime =cfg.getInt("keyword-time-period", 200)*1000L;
        normalMessages=cfg.getInt("normal-messages-count", 5);
        normalMsgTime =cfg.getInt("normal-time-period", 200)*1000L;
        sendMuteMessage=cfg.getBoolean("send-mute-message", false);
        muteMessage=ChatColor.translateAlternateColorCodes('&', cfg.getString("mute-message", "You are muted."));
        kick=cfg.getBoolean("kick-for-spam", false);
        kickMessage=ChatColor.translateAlternateColorCodes('&', cfg.getString("kick-message", "You have been kicked for spamming"));
        sendMessage=cfg.getBoolean("send-spam-message", false);
        spamMessage=ChatColor.translateAlternateColorCodes('&', cfg.getString("spam-message", "Your messages were filtered as spam. Stop spamming!"));
        spamMuteTime =cfg.getLong("spam-mute-duration", 300)*1000L;
        mutes =new HashSet<>(cfg.getStringList("muted-players"));
        spamBypassPlayers=new HashSet<>();
        for (String s: cfg.getStringList("spam-players")) {
            spamBypassPlayers.add(s.toLowerCase());

        }

    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String name=event.getPlayer().getName().toLowerCase();
        keywordmessages.remove(name);
        normalmessages.remove(name);
        shadowmutetime.remove(name);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String name=event.getPlayer().getName().toLowerCase();
        keywordmessages.remove(name);
        normalmessages.remove(name);
        shadowmutetime.remove(name);

    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        String name=event.getPlayer().getName().toLowerCase();
        keywordmessages.remove(name);
        normalmessages.remove(name);
        shadowmutetime.remove(name);
    }
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        String name = p.getName().toLowerCase();
        long now = System.currentTimeMillis();

        Long until = shadowmutetime.get(name);
        if (until != null) {
            if (now < until) {
                event.setCancelled(true);
                p.sendMessage(ChatColor.GRAY + p.getName() + ": " + event.getMessage());
                return;
            } else {
                shadowmutetime.remove(name);
            }
        }
        if (mutes.contains(name)) {
            event.setCancelled(true);
            if (sendMuteMessage) p.sendMessage(muteMessage);
            p.sendMessage(ChatColor.GRAY + p.getName() + ": " + event.getMessage());
            return;
        }

        if (spamBypassPlayers.contains(name)) return;

        String msg = event.getMessage().toLowerCase();

        // Keyword check
        boolean containsKeyword = false;
        for (String word : keywords) {
            if (msg.contains(word.toLowerCase())) {
                containsKeyword = true;
                break;
            }
        }

        if (containsKeyword) {
            List<Long> list = keywordmessages.computeIfAbsent(name, k -> new ArrayList<>());
            list.add(now);
            list.removeIf(t -> now - t > keywordTime);

            if (list.size() >= keywordMessagesCount) {
                event.setCancelled(true);

                if (sendMessage) p.sendMessage(spamMessage);

                shadowmutetime.put(name, now + spamMuteTime);
                list.clear();
                normalmessages.remove(name);
                p.sendMessage(ChatColor.GRAY + p.getName() + ": " + event.getMessage());
                return;
            }

            return;
        }
        List<Long> norm = normalmessages.computeIfAbsent(name, k -> new ArrayList<>());
        norm.add(now);
        norm.removeIf(t -> now - t > normalMsgTime);

        if (norm.size() >= normalMessages) {
            event.setCancelled(true);
            if (sendMessage) p.sendMessage(spamMessage);
            shadowmutetime.put(name, now + spamMuteTime);
            norm.clear();
            keywordmessages.remove(name);
            p.sendMessage(ChatColor.GRAY + p.getName() + ": " + event.getMessage());
            if (kick) {
                Bukkit.getScheduler().runTask(this, () -> p.kickPlayer(kickMessage));
            }
        }
    }

    private boolean canUseCmd(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) return true;
        if (sender instanceof Player) {
            return ((Player)sender).isOp();
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!canUseCmd(sender)) {
            sender.sendMessage(ChatColor.RED+"You don't have permission to use this command.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("antispam")) {
            if (args.length==1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadSettings();
                sender.sendMessage(ChatColor.GREEN+"Reloaded config.");
                return true;
            }
            sender.sendMessage(ChatColor.RED+"Usage: /antispam reload");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("mute")) {
            if (args.length!=1) {
                sender.sendMessage(ChatColor.RED+"Usage: /mute <player>");
                return true;
            }


            String targetName=args[0].toLowerCase();
            mutes.add(targetName);
            saveMutes();
            sender.sendMessage(ChatColor.GREEN+args[0]+" has been muted.");
            Player target=Bukkit.getPlayerExact(args[0]);
            if (target!=null && sendMuteMessage) target.sendMessage(muteMessage);
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("unmute")) {
            if (args.length!=1) {
                sender.sendMessage(ChatColor.RED+"Usage: /unmute <player>");
                return true;
            }
            String targetName=args[0].toLowerCase();
            mutes.remove(targetName);
            saveMutes();
            sender.sendMessage(ChatColor.GREEN+args[0]+" has been unmuted.");
            Player target=Bukkit.getPlayerExact(args[0]);
            if (target!=null && sendMuteMessage) target.sendMessage(ChatColor.GREEN+"You have been unmuted.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("mutelist")) {
            sender.sendMessage(ChatColor.YELLOW+"Muted players:");
            if (mutes.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY+" - none");
                return true;
            }
            for (String m: mutes) {
                sender.sendMessage(ChatColor.YELLOW+" - "+m);
            }
            return true;
        }

        return false;
    }

    private void saveMutes() {
        getConfig().set("muted-players", new ArrayList<>(mutes));
        saveConfig();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        return Collections.emptyList();
    }
}
