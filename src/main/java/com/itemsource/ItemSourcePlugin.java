package com.itemsource;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "ItemSource",
	internalName = "itemsource",
	description = "Find shops, drops, crafting methods, spawns, and rewards for OSRS items",
	tags = {"items", "wiki", "ironman", "shops", "drops", "crafting", "spawns", "rewards"}
)
public class ItemSourcePlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientToolbar toolbar;
	@Inject private ItemManager itemManager;
	@Inject private ItemSourcePanel panel;
	private NavigationButton navigation;

	@Provides
	ItemSourceConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ItemSourceConfig.class);
	}

	@Override protected void startUp()
	{
		SwingUtilities.invokeLater(() -> { navigation = NavigationButton.builder().tooltip("ItemSource")
			.icon(ImageUtil.loadImageResource(ItemSourcePlugin.class, "/icon.png"))
			.priority(7).panel(panel).build(); toolbar.addNavigation(navigation); });
		log.debug("ItemSource started");
	}

	@Override protected void shutDown()
	{
		panel.deactivate();
		SwingUtilities.invokeLater(() -> { if (navigation != null) { toolbar.removeNavigation(navigation); navigation = null; } });
		log.debug("ItemSource stopped");
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!"Examine".equals(event.getOption()) || event.getItemId() < 0) return;
		String itemName = itemManager.getItemComposition(event.getItemId()).getName();
		if (itemName == null || itemName.isEmpty() || "null".equals(itemName)) return;
		client.getMenu().createMenuEntry(-1)
			.setOption("Search in ItemSource")
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(entry -> SwingUtilities.invokeLater(() ->
			{
				toolbar.openPanel(navigation);
				panel.searchFor(itemName);
			}));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ItemSourceConfig.GROUP.equals(event.getGroup()))
		{
			SwingUtilities.invokeLater(panel::refreshCurrent);
		}
	}

}
