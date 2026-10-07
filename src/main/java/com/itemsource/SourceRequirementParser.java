package com.itemsource;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Quest;

final class SourceRequirementParser
{
	private static final Pattern SLAYER_LEVEL = Pattern.compile("(?im)^\\|\\s*slaylvl\\s*=\\s*(\\d+)");
	private static final Pattern SLAYER_TASK = Pattern.compile(
		"(?is)(?:only|must).{0,45}?(?:attacked|killed|fought).{0,45}?on (?:an? )?\\[?\\[?([a-z][a-z '\\-]+?)(?:\\]\\])? task");
	private static final int REQUIREMENT_CONTEXT = 100;
	private static final Pattern RECOMMENDED_SKILL = Pattern.compile(
		"(?i)\\{\\{SCP\\|(Attack|Strength|Defence|Ranged|Magic|Prayer|Hitpoints)\\|(\\d+)");
	private static final Pattern LOCATION_PAGE = Pattern.compile(
		"(?im)^\\|\\s*location\\s*=.*?\\[\\[([^]|#]+)");
	private static final Pattern LOCATION_SENTENCE = Pattern.compile(
		"(?is)(?:found|located|cyclopes).{0,90}?\\b(?:in|at)\\s+(?:the\\s+)?\\[\\[([^]|#]+)");
	private static final Pattern COMBINED_ATTACK_STRENGTH = Pattern.compile(
		"(?is)combined\\s+\\[\\[Attack\\]\\]\\s+and\\s+\\[\\[Strength\\]\\]\\s+level.{0,30}?(\\d+)");
	private static final Pattern REQUIREMENT_FIELD = Pattern.compile(
		"(?im)^\\|\\s*requirements?\\s*=\\s*([^\\r\\n]*)");
	private static final Pattern ACCESS_SKILL = Pattern.compile(
		"(?i)\\{\\{SCP\\|(?!Quest\\b)([A-Za-z ]+)\\|(\\d+)");
	private static final Pattern QUEST_POINTS = Pattern.compile(
		"(?i)(\\d+)\\s*\\[\\[Quest points?\\]\\]");
	private static final Pattern MAP_X = Pattern.compile("(?i)\\|\\s*x\\s*=\\s*(\\d+)");
	private static final Pattern MAP_Y = Pattern.compile("(?i)\\|\\s*y\\s*=\\s*(\\d+)");

	SourceRequirement parse(String wikitext)
	{
		String requirementText = wikitext.substring(0, Math.min(wikitext.length(), 6000));
		Matcher levelMatcher = SLAYER_LEVEL.matcher(requirementText);
		int slayerLevel = levelMatcher.find() ? Integer.parseInt(levelMatcher.group(1)) : 0;
		List<String> quests = new ArrayList<>();
		for (Quest quest : Quest.values())
		{
			String link = "[[" + quest.getName();
			int from = 0;
			while ((from = indexOfIgnoreCase(requirementText, link, from)) >= 0)
			{
				int afterLink = from + link.length();
				if (afterLink < requirementText.length() && requirementText.charAt(afterLink) != ']'
					&& requirementText.charAt(afterLink) != '|' && requirementText.charAt(afterLink) != '#')
				{
					from = afterLink;
					continue;
				}
				int start = Math.max(0, from - REQUIREMENT_CONTEXT);
				int end = Math.min(requirementText.length(), from + link.length() + REQUIREMENT_CONTEXT);
				String context = requirementText.substring(start, end).toLowerCase(Locale.ENGLISH);
				if (isRequirementContext(context))
				{
					quests.add(quest.getName());
					break;
				}
				from += link.length();
			}
		}

		Matcher taskMatcher = SLAYER_TASK.matcher(requirementText);
		String task = taskMatcher.find() ? cleanTask(taskMatcher.group(1)) : null;
		if ("slayer".equalsIgnoreCase(task)) task = null;
		boolean wilderness = Pattern.compile("(?im)^\\|\\s*location\\s*=.*wilderness")
			.matcher(requirementText).find() || requirementText.toLowerCase(Locale.ENGLISH).contains("wilderness boss");
		Matcher locationMatcher = LOCATION_PAGE.matcher(requirementText);
		String locationPage = locationMatcher.find() ? locationMatcher.group(1).trim() : null;
		if (locationPage == null)
		{
			locationMatcher = LOCATION_SENTENCE.matcher(requirementText);
			if (locationMatcher.find()) locationPage = locationMatcher.group(1).trim();
		}
		Matcher combinedMatcher = COMBINED_ATTACK_STRENGTH.matcher(requirementText);
		int combinedLevel = combinedMatcher.find() ? Integer.parseInt(combinedMatcher.group(1)) : 0;
		Map<String, Integer> requiredLevels = new LinkedHashMap<>();
		if (slayerLevel > 0) requiredLevels.put("Slayer", slayerLevel);
		if (combinedLevel > 0) requiredLevels.put("Attack + Strength", combinedLevel);
		int questPoints = 0;
		Matcher requirementFieldMatcher = REQUIREMENT_FIELD.matcher(requirementText);
		while (requirementFieldMatcher.find())
		{
			String field = requirementFieldMatcher.group(1);
			Matcher accessSkillMatcher = ACCESS_SKILL.matcher(field);
			while (accessSkillMatcher.find())
			{
				String accessSkill = accessSkillMatcher.group(1).trim();
				int accessLevel = Integer.parseInt(accessSkillMatcher.group(2));
				requiredLevels.merge(accessSkill, accessLevel, Math::max);
			}
			Matcher questPointMatcher = QUEST_POINTS.matcher(field);
			if (questPointMatcher.find()) questPoints = Math.max(questPoints,
				Integer.parseInt(questPointMatcher.group(1)));
		}
		Matcher xMatcher = MAP_X.matcher(requirementText);
		Matcher yMatcher = MAP_Y.matcher(requirementText);
		Integer mapX = xMatcher.find() ? Integer.parseInt(xMatcher.group(1)) : null;
		Integer mapY = yMatcher.find() ? Integer.parseInt(yMatcher.group(1)) : null;
		return new SourceRequirement(requiredLevels, questPoints, quests, task,
			new LinkedHashMap<>(), wilderness, locationPage, mapX, mapY);
	}

	SourceRequirement parseStrategy(String wikitext)
	{
		String section = recommendationSection(wikitext);
		Map<String, Integer> levels = new LinkedHashMap<>();
		Matcher matcher = RECOMMENDED_SKILL.matcher(section);
		while (matcher.find())
		{
			String skill = matcher.group(1);
			int level = Integer.parseInt(matcher.group(2));
			levels.merge(skill, level, Math::max);
		}
		boolean wilderness = section.toLowerCase(Locale.ENGLISH).contains("wilderness");
		return new SourceRequirement(null, 0, new ArrayList<>(), null, levels, wilderness);
	}

	private static String recommendationSection(String text)
	{
		Matcher start = Pattern.compile("(?im)^={2,3}(Suggested skills|Recommendations)={2,3}\\s*$").matcher(text);
		if (!start.find()) return "";
		int headingLevel = start.group().startsWith("===") ? 3 : 2;
		Matcher next = Pattern.compile("(?m)^={2," + headingLevel + "}[^=].*?={2," + headingLevel + "}\\s*$")
			.matcher(text);
		next.region(start.end(), text.length());
		int end = next.find() ? next.start() : text.length();
		return text.substring(start.end(), end);
	}

	private static boolean isRequirementContext(String context)
	{
		return context.contains("requires completion") || context.contains("require completion")
			|| context.contains("completed") || context.contains("completion of")
			|| context.contains("must complete") || context.contains("after completing")
			|| context.contains("not yet complete") || context.contains("quest is completed")
			|| context.matches("(?s).*\\|\\s*requirements?\\s*=.*")
			|| context.matches("(?s).*\\|\\s*quest\\s*=.*");
	}

	private static int indexOfIgnoreCase(String text, String wanted, int from)
	{
		return text.toLowerCase(Locale.ENGLISH).indexOf(wanted.toLowerCase(Locale.ENGLISH), from);
	}

	private static String cleanTask(String value)
	{
		return value.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
	}
}
