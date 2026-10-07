package com.itemsource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

final class SourceRequirement
{
	private final Map<String, Integer> requiredLevels;
	private final int questPoints;
	private final List<String> quests;
	private final String slayerTask;
	private final Map<String, Integer> recommendedLevels;
	private final boolean wildernessRisk;
	private final String locationPage;
	private final Integer mapX;
	private final Integer mapY;

	SourceRequirement(String skill, int level, List<String> quests, String slayerTask)
	{
		this(skill, level, quests, slayerTask, Collections.emptyMap());
	}

	SourceRequirement(String skill, int level, List<String> quests, String slayerTask,
		Map<String, Integer> recommendedLevels)
	{
		this(skill, level, quests, slayerTask, recommendedLevels, false);
	}

	SourceRequirement(String skill, int level, List<String> quests, String slayerTask,
		Map<String, Integer> recommendedLevels, boolean wildernessRisk)
	{
		this(skill, level, quests, slayerTask, recommendedLevels, wildernessRisk, null);
	}

	SourceRequirement(String skill, int level, List<String> quests, String slayerTask,
		Map<String, Integer> recommendedLevels, boolean wildernessRisk, String locationPage)
	{
		this(skill, level, quests, slayerTask, recommendedLevels, wildernessRisk, locationPage, null, null);
	}

	SourceRequirement(String skill, int level, List<String> quests, String slayerTask,
		Map<String, Integer> recommendedLevels, boolean wildernessRisk, String locationPage,
		Integer mapX, Integer mapY)
	{
		this(requirementMap(skill, level), 0, quests, slayerTask, recommendedLevels,
			wildernessRisk, locationPage, mapX, mapY);
	}

	SourceRequirement(Map<String, Integer> requiredLevels, int questPoints,
		List<String> quests, String slayerTask, Map<String, Integer> recommendedLevels,
		boolean wildernessRisk, String locationPage, Integer mapX, Integer mapY)
	{
		this.requiredLevels = Collections.unmodifiableMap(new LinkedHashMap<>(requiredLevels));
		this.questPoints = questPoints;
		this.quests = Collections.unmodifiableList(new ArrayList<>(quests));
		this.slayerTask = slayerTask;
		this.recommendedLevels = Collections.unmodifiableMap(new LinkedHashMap<>(recommendedLevels));
		this.wildernessRisk = wildernessRisk;
		this.locationPage = locationPage;
		this.mapX = mapX;
		this.mapY = mapY;
	}

	String getSkill() { return requiredLevels.isEmpty() ? null : requiredLevels.keySet().iterator().next(); }
	int getLevel() { return requiredLevels.isEmpty() ? 0 : requiredLevels.values().iterator().next(); }
	Map<String, Integer> getRequiredLevels() { return requiredLevels; }
	int getQuestPoints() { return questPoints; }
	List<String> getQuests() { return quests; }
	String getSlayerTask() { return slayerTask; }
	Map<String, Integer> getRecommendedLevels() { return recommendedLevels; }
	boolean hasWildernessRisk() { return wildernessRisk; }
	String getLocationPage() { return locationPage; }
	Integer getMapX() { return mapX; }
	Integer getMapY() { return mapY; }

	SourceRequirement merge(SourceRequirement other)
	{
		List<String> combinedQuests = new ArrayList<>(quests);
		for (String quest : other.quests) if (!combinedQuests.contains(quest)) combinedQuests.add(quest);
		Map<String, Integer> combinedLevels = new LinkedHashMap<>(recommendedLevels);
		other.recommendedLevels.forEach((skillName, skillLevel) ->
			combinedLevels.merge(skillName, skillLevel, Math::max));
		Map<String, Integer> combinedRequired = new LinkedHashMap<>(requiredLevels);
		other.requiredLevels.forEach((skillName, skillLevel) ->
			combinedRequired.merge(skillName, skillLevel, Math::max));
		return new SourceRequirement(combinedRequired, Math.max(questPoints, other.questPoints), combinedQuests,
			slayerTask != null ? slayerTask : other.slayerTask, combinedLevels,
			wildernessRisk || other.wildernessRisk,
			locationPage != null ? locationPage : other.locationPage,
			mapX != null ? mapX : other.mapX, mapY != null ? mapY : other.mapY);
	}

	private static Map<String, Integer> requirementMap(String skill, int level)
	{
		Map<String, Integer> levels = new LinkedHashMap<>();
		if (skill != null && level > 0) levels.put(skill, level);
		return levels;
	}
}
