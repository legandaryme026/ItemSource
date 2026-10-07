package com.itemsource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

final class ItemSourceResult
{
	private final String itemName;
	private final String wikiUrl;
	private final Map<SourceCategory, List<SourceEntry>> sources;

	ItemSourceResult(String itemName, String wikiUrl, Map<SourceCategory, List<SourceEntry>> sources)
	{
		this.itemName = itemName;
		this.wikiUrl = wikiUrl;
		EnumMap<SourceCategory, List<SourceEntry>> copy = new EnumMap<>(SourceCategory.class);
		sources.forEach((key, value) -> copy.put(key,
			Collections.unmodifiableList(new ArrayList<>(value))));
		this.sources = Collections.unmodifiableMap(copy);
	}

	String getItemName() { return itemName; }
	String getWikiUrl() { return wikiUrl; }
	Map<SourceCategory, List<SourceEntry>> getSources() { return sources; }
	boolean hasSources() { return sources.values().stream().anyMatch(v -> !v.isEmpty()); }

	ItemSourceResult withRequirements(Map<String, SourceRequirement> requirements)
	{
		Map<SourceCategory, List<SourceEntry>> enriched = new EnumMap<>(SourceCategory.class);
		for (Map.Entry<SourceCategory, List<SourceEntry>> category : sources.entrySet())
		{
			List<SourceEntry> entries = new ArrayList<>();
			for (SourceEntry entry : category.getValue())
			{
				SourceRequirement requirement = requirements.get(requirementKey(entry.getTitle()));
				entries.add(requirement == null ? entry : entry.withRequirement(requirement));
			}
			enriched.put(category.getKey(), entries);
		}
		return new ItemSourceResult(itemName, wikiUrl, enriched);
	}

	static String requirementKey(String title)
	{
		return title.replaceFirst("\\s*\\([^)]*\\)\\s*$", "")
			.trim().toLowerCase(Locale.ENGLISH);
	}
}
