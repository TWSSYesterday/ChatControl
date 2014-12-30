package kangarko.chatcontrol.hooks;

import java.io.File;

import kangarko.chatcontrol.ChatControl;
import kangarko.chatcontrol.model.Settings;
import kangarko.chatcontrol.rules.ChatCeaser.PacketCancelledException;
import kangarko.chatcontrol.utils.Common;
import kangarko.chatcontrol.utils.Permissions;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

public class ProtocolLibHook {

	private static final ProtocolManager manager = ProtocolLibrary.getProtocolManager();
	private static final JSONParser parser = new JSONParser();

	public static void init() {
		if (Settings.Packets.DISABLE_TAB_COMPLETE) {
			if (new File("spigot.yml").exists())
				Common.LogInFrame(false, "&aIf you want to disable tab complete, set", "&bcommands.tab-complete &ato 0 in &fspigot.yml &afile.", "&aFunction in ChatControl was disabled.");
			else {
				manager.addPacketListener(new PacketAdapter(ChatControl.instance(), PacketType.Play.Client.TAB_COMPLETE) {

					@Override
					public void onPacketReceiving(PacketEvent e) {
						if (Common.hasPerm(e.getPlayer(), Permissions.Bypasses.TAB_COMPLETE))
							return;

						String msg = e.getPacket().getStrings().read(0);

						if (msg.startsWith("/") && !msg.contains(" "))
							e.setCancelled(true);
					}
				});
			}
		}

		if (Settings.Rules.CHECK_PACKETS) {
			manager.addPacketListener(new PacketAdapter(ChatControl.instance(), PacketType.Play.Server.CHAT) {

				@Override
				public void onPacketSending(PacketEvent e) {
					StructureModifier<WrappedChatComponent> chat = e.getPacket().getChatComponents();

					try {
						JSONObject json = (JSONObject) parser.parse(chat.read(0).getJson());
						
						try {
							ChatControl.instance().chatCeaser.parsePacketRules(json);
						
							chat.write(0, WrappedChatComponent.fromJson(json.toJSONString()));
						} catch (PacketCancelledException e1) {
							e.setCancelled(true);
						}
					} catch (ArrayIndexOutOfBoundsException ex) { 
						Common.Error("Skipping invalid chat packet for " + e.getPlayer().getName());
						
					} catch (ParseException ex) {
						Common.Error("Unable to parse chat packet", ex);
					}
				}
			});
		}
	}
}
