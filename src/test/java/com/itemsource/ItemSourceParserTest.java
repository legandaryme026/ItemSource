package com.itemsource;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ItemSourceParserTest
{
	@Test public void formatsDropsShopsAndSpawnsWithoutUnrelatedColumns()
	{
		String html = "<h2>Item sources</h2><table>"
			+ "<tr><th>Source</th><th>Level</th><th>Quantity</th><th>Rarity</th></tr>"
			+ "<tr><td>Fire giant</td><td>86; 104; 109</td><td>1</td><td>1/128</td></tr></table>"
			+ "<h3>Shop locations</h3><table>"
			+ "<tr><th>Seller</th><th>Location</th><th>Number in stock</th><th>Price sold at</th></tr>"
			+ "<tr><td>Scavvo</td><td>Champions' Guild</td><td>1</td><td>32,000</td></tr></table>"
			+ "<h2>Spawn locations</h2><table>"
			+ "<tr><th>Location</th><th>Members</th><th>Spawns</th><th>Map</th></tr>"
			+ "<tr><td>Varrock Sewers</td><td></td><td>2</td><td>Show map</td></tr></table>";
		ItemSourceResult result = new ItemSourceParser().parse("Rune scimitar", html);

		SourceEntry drop = result.getSources().get(SourceCategory.MONSTER_DROPS).get(0);
		assertEquals("Fire giant", drop.getTitle());
		assertEquals("Drop rate: 1/128", drop.getDetail());
		SourceEntry shop = result.getSources().get(SourceCategory.SHOPS).get(0);
		assertEquals("Champions' Guild · Price: 32,000 gp · Stock: 1", shop.getDetail());
		SourceEntry spawn = result.getSources().get(SourceCategory.GROUND_SPAWNS).get(0);
		assertEquals("Varrock Sewers", spawn.getTitle());
		assertEquals("Spawns: 2", spawn.getDetail());
	}

	@Test public void normalizesMultiNumeratorDropRates()
	{
		String html = "<h2>Item sources</h2><table>"
			+ "<tr><th>Source</th><th>Rarity</th></tr>"
			+ "<tr><td>Jogre</td><td>5/129</td></tr>"
			+ "<tr><td>H.A.M. Member</td><td>2/102</td></tr>"
			+ "<tr><td>TzHaar-Hur</td><td>8/128</td></tr></table>";
		ItemSourceResult result = new ItemSourceParser().parse("Test item", html);
		assertEquals("Drop rate: 1/25.8", result.getSources().get(SourceCategory.MONSTER_DROPS).get(0).getDetail());
		assertEquals("Drop rate: 1/51", result.getSources().get(SourceCategory.MONSTER_DROPS).get(1).getDetail());
		assertEquals("Drop rate: 1/16", result.getSources().get(SourceCategory.MONSTER_DROPS).get(2).getDetail());
	}

	@Test public void summarizesCreationAsOneMethod()
	{
		String html = "<h2>Creation</h2><table>"
			+ "<tr><th>Skill</th><th>Level</th><th>XP</th></tr>"
			+ "<tr><td>Smithing</td><td>90 (b)</td><td>150</td></tr>"
			+ "<tr><th>Tools</th><td></td><th>Facilities</th><td>Anvil</td></tr>"
			+ "<tr><th>Item</th><th>Quantity</th><th>Cost</th></tr>"
			+ "<tr><td></td><td>Runite bar</td><td>2</td><td>24,390</td></tr></table>";
		ItemSourceResult result = new ItemSourceParser().parse("Rune scimitar", html);
		SourceEntry method = result.getSources().get(SourceCategory.CRAFTING_AND_SKILLS).get(0);
		assertEquals("Smithing", method.getTitle());
		assertEquals("Level: 90 · XP: 150 · Anvil · Requires: 2 × Runite bar", method.getDetail());
	}

	@Test public void capturesExplicitQuestRequirementsFromSourceRows()
	{
		String html = "<h3>Shop locations</h3><table>"
			+ "<tr><th>Seller</th><th>Location</th><th>Requirements</th></tr>"
			+ "<tr><td>Amlodd's Magical Supplies</td><td>Prifddinas</td>"
			+ "<td>Completion of Song of the Elves</td></tr></table>";
		ItemSourceResult result = new ItemSourceParser().parse("Test item", html);

		SourceEntry shop = result.getSources().get(SourceCategory.SHOPS).get(0);
		assertEquals(1, shop.getRequiredQuests().size());
		assertEquals("Song of the Elves", shop.getRequiredQuests().get(0));
	}

	@Test public void marksZeroStockShopVariantsUnavailable()
	{
		String html = "<h3>Shop locations</h3><table>"
			+ "<tr><th>Seller</th><th>Location</th><th>Number in stock</th></tr>"
			+ "<tr><td>Daga's Scimitar Smithy</td><td>Ape Atoll</td><td>0</td></tr></table>";
		ItemSourceResult result = new ItemSourceParser().parse("Dragon scimitar", html);

		SourceEntry shop = result.getSources().get(SourceCategory.SHOPS).get(0);
		assertEquals("No stock in this shop state", shop.getAvailabilityIssue());
	}

	@Test public void usesCanonicalWikiPageForMonsterVariants()
	{
		String html = "<h2>Item sources</h2><table>"
			+ "<tr><th>Source</th><th>Rarity</th></tr>"
			+ "<tr><td><a href='/w/Jelly#Regular' title=\"Jelly\">Jelly "
			+ "<span>Regular</span></a></td><td>1/128</td></tr></table>";
		SourceEntry drop = new ItemSourceParser().parse("Rune full helm", html)
			.getSources().get(SourceCategory.MONSTER_DROPS).get(0);
		assertEquals("Jelly", drop.getTitle());
		assertEquals("Regular · Drop rate: 1/128", drop.getDetail());
	}

	@Test public void expandsClueTierRewardNames()
	{
		String html = "<h2>Treasure Trails rewards</h2><table>"
			+ "<tr><th>Tier</th><th>Emotes</th></tr>"
			+ "<tr><td>Hard</td><td>Laugh</td></tr></table>";
		SourceEntry reward = new ItemSourceParser().parse("Rune full helm", html)
			.getSources().get(SourceCategory.REWARDS).get(0);
		assertEquals("Hard clue scroll", reward.getTitle());
	}
}
