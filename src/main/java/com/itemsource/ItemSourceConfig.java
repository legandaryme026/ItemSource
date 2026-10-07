package com.itemsource;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(ItemSourceConfig.GROUP)
public interface ItemSourceConfig extends Config
{
	String GROUP = "itemsource";

	@ConfigItem(keyName = "rankingPreference", name = "Ranking preference",
		description = "Choose how ItemSource ranks available methods", position = 0)
	default RankingPreference rankingPreference() { return RankingPreference.BALANCED; }

	@ConfigItem(keyName = "excludeWilderness", name = "Exclude Wilderness",
		description = "Never recommend sources in Wilderness or PvP areas", position = 1)
	default boolean excludeWilderness() { return false; }

	@Range(min = 0, max = 2_000_000_000)
	@ConfigItem(keyName = "maxShopBudget", name = "Maximum shop budget",
		description = "Do not recommend shops above this price; 0 means unlimited", position = 2)
	default int maxShopBudget() { return 0; }

	@ConfigItem(keyName = "hideLocked", name = "Hide locked sources",
		description = "Hide sources whose hard account requirements are not met", position = 3)
	default boolean hideLocked() { return false; }
}
