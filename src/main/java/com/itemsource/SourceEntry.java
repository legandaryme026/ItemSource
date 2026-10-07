package com.itemsource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class SourceEntry
{
	private final String title;
	private final String detail;
	private final String requiredSkill;
	private final int requiredLevel;
	private final Map<String, Integer> requiredLevels;
	private final int requiredQuestPoints;
	private final List<String> requiredQuests;
	private final String requiredSlayerTask;
	private final String availabilityIssue;
	private final boolean requirementDataChecked;
	private final Map<String, Integer> recommendedLevels;
	private final boolean wildernessRisk;
	private final Integer mapX;
	private final Integer mapY;

	SourceEntry(String title, String detail)
	{
		this(title, detail, null, 0);
	}

	SourceEntry(String title, String detail, String requiredSkill, int requiredLevel)
	{
		this(title, detail, requiredSkill, requiredLevel, Collections.emptyList());
	}

	SourceEntry(String title, String detail, String requiredSkill, int requiredLevel,
		List<String> requiredQuests)
	{
		this(title, detail, requiredSkill, requiredLevel, requiredQuests, null, null);
	}

	SourceEntry(String title, String detail, String requiredSkill, int requiredLevel,
		List<String> requiredQuests, String requiredSlayerTask, String availabilityIssue)
	{
		this.title = title;
		this.detail = detail;
		this.requiredSkill = requiredSkill;
		this.requiredLevel = requiredLevel;
		this.requiredLevels = Collections.unmodifiableMap(requirementMap(requiredSkill, requiredLevel));
		this.requiredQuestPoints = 0;
		this.requiredQuests = Collections.unmodifiableList(new ArrayList<>(requiredQuests));
		this.requiredSlayerTask = requiredSlayerTask;
		this.availabilityIssue = availabilityIssue;
		this.requirementDataChecked = requiredLevel > 0 || !requiredQuests.isEmpty();
		this.recommendedLevels = Collections.emptyMap();
		this.wildernessRisk = false;
		this.mapX = null;
		this.mapY = null;
	}

	private SourceEntry(String title, String detail, String requiredSkill, int requiredLevel,
		List<String> requiredQuests, String requiredSlayerTask, String availabilityIssue,
		boolean requirementDataChecked)
	{
		this(title, detail, requiredSkill, requiredLevel, requiredQuests, requiredSlayerTask,
			availabilityIssue, requirementDataChecked, Collections.emptyMap(), false, null, null);
	}

	private SourceEntry(String title, String detail, String requiredSkill, int requiredLevel,
		List<String> requiredQuests, String requiredSlayerTask, String availabilityIssue,
		boolean requirementDataChecked, Map<String, Integer> recommendedLevels)
	{
		this(title, detail, requiredSkill, requiredLevel, requiredQuests, requiredSlayerTask,
			availabilityIssue, requirementDataChecked, recommendedLevels, false, null, null);
	}

	private SourceEntry(String title, String detail, String requiredSkill, int requiredLevel,
		List<String> requiredQuests, String requiredSlayerTask, String availabilityIssue,
		boolean requirementDataChecked, Map<String, Integer> recommendedLevels,
		boolean wildernessRisk, Integer mapX, Integer mapY)
	{
		this(title, detail, requiredSkill, requiredLevel, requirementMap(requiredSkill, requiredLevel), 0,
			requiredQuests, requiredSlayerTask, availabilityIssue, requirementDataChecked,
			recommendedLevels, wildernessRisk, mapX, mapY);
	}

	private SourceEntry(String title, String detail, String requiredSkill, int requiredLevel,
		Map<String, Integer> requiredLevels, int requiredQuestPoints,
		List<String> requiredQuests, String requiredSlayerTask, String availabilityIssue,
		boolean requirementDataChecked, Map<String, Integer> recommendedLevels,
		boolean wildernessRisk, Integer mapX, Integer mapY)
	{
		this.title = title;
		this.detail = detail;
		this.requiredSkill = requiredSkill;
		this.requiredLevel = requiredLevel;
		this.requiredLevels = Collections.unmodifiableMap(new LinkedHashMap<>(requiredLevels));
		this.requiredQuestPoints = requiredQuestPoints;
		this.requiredQuests = Collections.unmodifiableList(new ArrayList<>(requiredQuests));
		this.requiredSlayerTask = requiredSlayerTask;
		this.availabilityIssue = availabilityIssue;
		this.requirementDataChecked = requirementDataChecked;
		this.recommendedLevels = Collections.unmodifiableMap(new LinkedHashMap<>(recommendedLevels));
		this.wildernessRisk = wildernessRisk;
		this.mapX = mapX;
		this.mapY = mapY;
	}

	String getTitle() { return title; }
	String getDetail() { return detail; }
	String getRequiredSkill() { return requiredSkill; }
	int getRequiredLevel() { return requiredLevel; }
	Map<String, Integer> getRequiredLevels() { return requiredLevels; }
	int getRequiredQuestPoints() { return requiredQuestPoints; }
	List<String> getRequiredQuests() { return requiredQuests; }
	String getRequiredSlayerTask() { return requiredSlayerTask; }
	String getAvailabilityIssue() { return availabilityIssue; }
	boolean isRequirementDataChecked() { return requirementDataChecked; }
	Map<String, Integer> getRecommendedLevels() { return recommendedLevels; }
	boolean hasWildernessRisk() { return wildernessRisk; }
	Integer getMapX() { return mapX; }
	Integer getMapY() { return mapY; }

	SourceEntry withRequirement(SourceRequirement requirement)
	{
		List<String> quests = new ArrayList<>(requiredQuests);
		for (String quest : requirement.getQuests()) if (!quests.contains(quest)) quests.add(quest);
		Map<String, Integer> levels = new LinkedHashMap<>(requiredLevels);
		requirement.getRequiredLevels().forEach((skillName, skillLevel) ->
			levels.merge(skillName, skillLevel, Math::max));
		String skill = levels.isEmpty() ? null : levels.keySet().iterator().next();
		int level = levels.isEmpty() ? 0 : levels.values().iterator().next();
		return new SourceEntry(title, detail, skill, level, levels,
			Math.max(requiredQuestPoints, requirement.getQuestPoints()), quests,
			requirement.getSlayerTask(), availabilityIssue, true,
			requirement.getRecommendedLevels(), requirement.hasWildernessRisk(),
			requirement.getMapX(), requirement.getMapY());
	}

	@Override public boolean equals(Object other)
	{
		if (this == other) return true;
		if (!(other instanceof SourceEntry)) return false;
		SourceEntry entry = (SourceEntry) other;
		return title.equals(entry.title) && detail.equals(entry.detail)
			&& requiredLevels.equals(entry.requiredLevels)
			&& requiredQuestPoints == entry.requiredQuestPoints
			&& requiredQuests.equals(entry.requiredQuests)
			&& Objects.equals(requiredSlayerTask, entry.requiredSlayerTask)
			&& Objects.equals(availabilityIssue, entry.availabilityIssue)
			&& requirementDataChecked == entry.requirementDataChecked
			&& recommendedLevels.equals(entry.recommendedLevels)
			&& wildernessRisk == entry.wildernessRisk
			&& Objects.equals(mapX, entry.mapX) && Objects.equals(mapY, entry.mapY);
	}

	@Override public int hashCode()
	{
		return Objects.hash(title, detail, requiredLevels, requiredQuestPoints, requiredQuests, requiredSlayerTask,
			availabilityIssue, requirementDataChecked, recommendedLevels, wildernessRisk, mapX, mapY);
	}

	private static Map<String, Integer> requirementMap(String skill, int level)
	{
		Map<String, Integer> levels = new LinkedHashMap<>();
		if (skill != null && level > 0) levels.put(skill, level);
		return levels;
	}
}
