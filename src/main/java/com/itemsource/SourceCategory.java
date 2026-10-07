package com.itemsource;

enum SourceCategory
{
	SHOPS("Shops"), MONSTER_DROPS("Monster drops"),
	CRAFTING_AND_SKILLS("Crafting & skills"), GROUND_SPAWNS("Ground spawns"),
	REWARDS("Rewards");

	private final String displayName;
	SourceCategory(String displayName) { this.displayName = displayName; }
	String getDisplayName() { return displayName; }
}
