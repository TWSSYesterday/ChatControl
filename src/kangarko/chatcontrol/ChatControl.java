package kangarko.chatcontrol;

import java.util.logging.Filter;

import kangarko.chatcontrol.checks.UpdateCheck;
import kangarko.chatcontrol.filter.ConsoleFilter;
import kangarko.chatcontrol.filter.Log4jFilter;
import kangarko.chatcontrol.hooks.AuthMeHook;
import kangarko.chatcontrol.hooks.ProtocolLibHook;
import kangarko.chatcontrol.listener.ChatListener;
import kangarko.chatcontrol.listener.CommandListener;
import kangarko.chatcontrol.listener.PlayerListener;
import kangarko.chatcontrol.model.ConfHelper;
import kangarko.chatcontrol.model.Settings;
import kangarko.chatcontrol.model.SettingsConsole;
import kangarko.chatcontrol.model.Variables;
import kangarko.chatcontrol.utils.Common;
import kangarko.chatcontrol.utils.MissingResourceException;
import kangarko.chatcontrol.utils.safety.SafeMap;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.earth2me.essentials.CommandSource;
import com.earth2me.essentials.Essentials;

// TODO implement death messages
// TODO implement timed messages
// TODO replace remap and pwnfilter
// TODO Country flag and name (and IP) from API from Authme
public class ChatControl extends JavaPlugin {

	private static ChatControl instance;

	// Player IP, Time
	public static SafeMap<String, Long> ipLastLogin = new SafeMap<>();

	// Player Name, Player Cache
	private static SafeMap<String, PlayerCache> playerData = new SafeMap<>();

	private Essentials ess;
	private ChatFormatter formatter;

	public void onEnable() {
		instance = this;

		try {
			ConfHelper.loadAll();
		} catch (Throwable t) {
			t.printStackTrace();
			Common.Log("&4!----------------------------------------------!");
			Common.Log(" &cError loading ChatControl, plugin is disabled!");
			Common.Log("&4!----------------------------------------------!");
			if (t instanceof InvalidConfigurationException) {
				Common.Log(" &cIt seems like your config is not a valid YAML.");
				Common.Log(" &cUse online services like");
				Common.Log(" &chttp://yaml-online-parser.appspot.com/");
				Common.Log(" &cto check for syntax errors!");
			} else if (t instanceof IllegalStateException) {
				Common.Log(" &cChatControl doesn't have the locale: " + Settings.General.LOCALIZATION_SUFFIX);
			} else if (t instanceof MissingResourceException) {
				Common.Log(" &c" + t.getMessage());
				Common.Log(" &cTo fix it, create a blank file with");
				Common.Log(" &cthe name &f" + ((MissingResourceException)t).file + " &cin plugin folder.");
				Common.Log(" &cIt will be filled with default values.");
				Common.Log(" &ePlease inform the developer about this error.");
			} else
				Common.Log(" &cThe error was: " + t.getMessage());
			Common.Log("&4!----------------------------------------------!");
			getPluginLoader().disablePlugin(this);
			return;
		}

		if (Settings.General.DEBUG) {
			ipLastLogin.displayWarnings();
			playerData.displayWarnings();
		}

		for (Player pl : getOnlinePlayers())
			createDataIfNotExistFor(pl.getName());

		if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
			ess = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
			Common.Log("&fHooked with Essentials!");
		}
		
		if (Bukkit.getPluginManager().getPlugin("AuthMe") != null) {
			AuthMeHook.hooked = true;
			Common.Log("&fHooked with AuthMe!");
		}
			
		getServer().getPluginManager().registerEvents(new ChatListener(), this);
		getServer().getPluginManager().registerEvents(new PlayerListener(), this);
		getServer().getPluginManager().registerEvents(new CommandListener(), this);

		if (SettingsConsole.FILTER_ENABLED || SettingsConsole.FILTER_COLORS_TO_ANSI) {
			try {
				new Log4jFilter().init();
				Common.Debug("Console filtering now using Log4j Filter.");
			} catch (NoClassDefFoundError err) {				
				Filter filter = new ConsoleFilter();
				
				if (SettingsConsole.FILTER_FILTER_PLUGINS)
					for (Plugin p : getServer().getPluginManager().getPlugins())
						p.getLogger().setFilter(filter);

				Bukkit.getLogger().setFilter(filter);
				Common.Debug("Console filtering initiated (MC 1.6.4 and lower).");
			}
		}

		if (Settings.Packets.DISABLE_TAB_COMPLETE) {
			if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
				Common.LogInFrame(false, "Cannot enable packet features!", "Required plugin missing: ProtocolLib");
			} else {
				ProtocolLibHook.init();
				Common.Log("&fHooked with ProtocolLib (Packet listener)!");
			}
		}

		if (Settings.Chat.Formatter.ENABLED) {
			if (getServer().getPluginManager().getPlugin("Vault") == null) {
				Common.LogInFrame(false, "You need Vault to enable ChatFormatter.");
			} else {
				if (getServer().getPluginManager().getPlugin("ChatManager") != null) {
					Common.LogInFrame(true, "Detected ChatManager!", "Please copy settings from it", "to ChatControl and remove it then!");
				} else {
					formatter = new ChatFormatter();
					getServer().getPluginManager().registerEvents(formatter, this);
				}
			}
		}

		getCommand("chatcontrol").setExecutor(new CommandsHandler());

		if (Settings.Updater.ENABLED)
			getServer().getScheduler().runTaskAsynchronously(this, new UpdateCheck("https://raw.github.com/kangarko/ChatControl/master/plugin.yml"));

		Common.LogInFrame(false, "&aThis is a beta version, it might contains bugs!");
	}

	public void onDisable() {
		playerData.clear();
		ipLastLogin.clear();

		Variables.reset();

		ess = null;
		instance = null;
	}

	public boolean canSoundNotify(String name) {
		if (!Settings.SoundNotify.ONLY_WHEN_AFK)
			return true;

		if (ess == null || ess.getUserMap().getUser(name) == null)
			return true;

		return ess.getUserMap().getUser(name).isAfk();
	}

	public Player getReplyTo(Player pl) {
		if (ess == null)
			return null;

		CommandSource cmdSource = ess.getUserMap().getUser(pl.getName()).getReplyTo();
		if (cmdSource == null || !cmdSource.isPlayer())
			return null;

		Player source = cmdSource.getPlayer();
		if (source == null || !source.isOnline())
			return null;

		return source;
	}

	public String formatPlayerVariables(Player pl, String message) {
		if (formatter == null)
			return message;
		
		return formatter.replacePlayerVariables(pl, message);
	}
	
	public static void createDataIfNotExistFor(String pl) {
		playerData.putIfAbsent(pl, new PlayerCache());
	}

	public static PlayerCache getDataFor(Player pl) {
		return getDataFor(pl.getName());
	}

	public static PlayerCache getDataFor(String pl) {
		createDataIfNotExistFor(pl);

		return playerData.get(pl);
	}

	@SuppressWarnings("deprecation")
	public static Player[] getOnlinePlayers() {
		return Bukkit.getOnlinePlayers();
	}
	
	public static ChatControl instance() {
		if (instance == null) {
			instance = new ChatControl();

			Common.Warn("ChatControl instance is null! Was the plugin reloaded? Creating new..");
		}

		return instance;
	}
}