package com.itemsource;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SourceRequirementParserTest
{
	@Test public void findsSlayerQuestAndTaskRequirements()
	{
		String wikiText = "{{Infobox Monster\n|slaylvl = 93\n}}\n"
			+ "Players must have completed [[Monkey Madness II]] first. "
			+ "It can only be attacked on a smoke devil task.";

		SourceRequirement requirement = new SourceRequirementParser().parse(wikiText);

		assertEquals("Slayer", requirement.getSkill());
		assertEquals(93, requirement.getLevel());
		assertEquals("Monkey Madness II", requirement.getQuests().get(0));
		assertEquals("smoke devil", requirement.getSlayerTask());
	}

	@Test public void readsSuggestedCombatLevels()
	{
		String wikiText = "==Suggested skills==\n*{{SCP|Magic|70+}}\n*{{SCP|Prayer|40+}}\n==Equipment==";
		SourceRequirement requirement = new SourceRequirementParser().parseStrategy(wikiText);
		assertEquals(Integer.valueOf(70), requirement.getRecommendedLevels().get("Magic"));
		assertEquals(Integer.valueOf(40), requirement.getRecommendedLevels().get("Prayer"));
	}

	@Test public void detectsWildernessRisk()
	{
		SourceRequirement requirement = new SourceRequirementParser().parse(
			"{{Infobox Monster\n|location = Cave in the [[Wilderness]]\n}}");
		assertTrue(requirement.hasWildernessRisk());
	}

	@Test public void capturesLinkedLocationAndItsQuestRequirement()
	{
		SourceRequirement shop = new SourceRequirementParser().parse(
			"{{Infobox Shop\n|location = [[Prifddinas]]\n}}");
		SourceRequirement location = new SourceRequirementParser().parse(
			"In order to enter, [[Song of the Elves]] must be completed.");
		SourceRequirement combined = shop.merge(location);

		assertEquals("Prifddinas", combined.getLocationPage());
		assertEquals("Song of the Elves", combined.getQuests().get(0));
	}

	@Test public void findsLocationFromMonsterIntroduction()
	{
		SourceRequirement requirement = new SourceRequirementParser().parse(
			"Mithril dragons can be found and fought in the [[Ancient Cavern]].");
		assertEquals("Ancient Cavern", requirement.getLocationPage());
	}

	@Test public void readsWarriorsGuildCombinedLevelGate()
	{
		SourceRequirement requirement = new SourceRequirementParser().parse(
			"|requirement = Combined [[Attack]] and [[Strength]] level of at least 130, or level 99.");
		assertEquals("Attack + Strength", requirement.getSkill());
		assertEquals(130, requirement.getLevel());
	}

	@Test public void readsMapCoordinatesForDistanceEstimate()
	{
		SourceRequirement requirement = new SourceRequirementParser().parse(
			"{{Infobox Shop\n|location = [[Prifddinas]]\n|x = 3263\n|y = 6067\n}}");
		assertEquals(Integer.valueOf(3263), requirement.getMapX());
		assertEquals(Integer.valueOf(6067), requirement.getMapY());
	}

	@Test public void followsShopLocationsAfterFloorTemplates()
	{
		SourceRequirement requirement = new SourceRequirementParser().parse(
			"|location = {{FloorNumber|uk=2}} of [[Myths' Guild]]");
		assertEquals("Myths' Guild", requirement.getLocationPage());
	}

	@Test public void readsGuildQuestFromInfoboxRequirement()
	{
		SourceRequirement requirement = new SourceRequirementParser().parse(
			"|requirement = {{SCP|Quest}} [[Dragon Slayer II]]");
		assertEquals("Dragon Slayer II", requirement.getQuests().get(0));
	}

	@Test public void readsGuildSkillFromInfoboxRequirement()
	{
		SourceRequirement requirement = new SourceRequirementParser().parse(
			"|requirement = {{SCP|Magic|66}}");
		assertEquals("Magic", requirement.getSkill());
		assertEquals(66, requirement.getLevel());
	}

	@Test public void keepsEveryPublishedGuildSkillRequirement()
	{
		SourceRequirement requirement = new SourceRequirementParser().parse(
			"|requirement = {{SCP|Farming|45}} (beginner),<br/>{{SCP|Farming|65}} (intermediate),<br/>{{SCP|Woodcutting|60}}");
		assertEquals(Integer.valueOf(65), requirement.getRequiredLevels().get("Farming"));
		assertEquals(Integer.valueOf(60), requirement.getRequiredLevels().get("Woodcutting"));
	}

	@Test public void readsGuildQuestPointRequirement()
	{
		SourceRequirement requirement = new SourceRequirementParser().parse(
			"|requirement = 32 [[Quest points]]");
		assertEquals(32, requirement.getQuestPoints());
	}
}
