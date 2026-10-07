package com.itemsource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import net.runelite.api.Quest;

final class ItemSourceParser
{
	private static final Pattern HEADING = Pattern.compile("(?is)<h[2-6][^>]*>(.*?)</h[2-6]>");
	private static final Pattern TABLE = Pattern.compile("(?is)<table[^>]*>(.*?)</table>");
	private static final Pattern ROW = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
	private static final Pattern CELL = Pattern.compile("(?is)<(th|td)[^>]*>(.*?)</\\1>");
	private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
	private static final Pattern HIDDEN = Pattern.compile("(?is)<(script|style|sup)[^>]*>.*?</\\1>");
	private static final Pattern FRACTION = Pattern.compile("^(\\d+(?:\\.\\d+)?)/(\\d+(?:\\.\\d+)?)$");
	private static final Pattern LINK_TITLE = Pattern.compile("(?is)<a[^>]*\\btitle=\"([^\"]+)\"");
	private static final String CELL_META = "\u001f";

	ItemSourceResult parse(String title, String html)
	{
		Map<SourceCategory, Set<SourceEntry>> found = new EnumMap<>(SourceCategory.class);
		for (SourceCategory category : SourceCategory.values()) found.put(category, new LinkedHashSet<>());
		List<Heading> headings = headings(html);
		for (int index = 0; index < headings.size(); index++)
		{
			Heading heading = headings.get(index);
			SourceCategory category = categoryFor(heading.text);
			if (category == null) continue;
			int end = index + 1 < headings.size() ? headings.get(index + 1).start : html.length();
			found.get(category).addAll(entries(category, html.substring(heading.end, end)));
		}
		Map<SourceCategory, List<SourceEntry>> sources = new EnumMap<>(SourceCategory.class);
		found.forEach((category, entries) -> {
			if (!entries.isEmpty()) sources.put(category, new ArrayList<>(entries));
		});
		return new ItemSourceResult(title,
			"https://oldschool.runescape.wiki/w/" + title.replace(' ', '_'), sources);
	}

	private static List<SourceEntry> entries(SourceCategory category, String section)
	{
		List<SourceEntry> entries = new ArrayList<>();
		for (List<List<String>> table : tables(section))
		{
			if (entries.size() >= 12) break;
			switch (category)
			{
				case MONSTER_DROPS: addDrops(entries, table); break;
				case SHOPS: addShops(entries, table); break;
				case GROUND_SPAWNS: addSpawns(entries, table); break;
				case CRAFTING_AND_SKILLS: addCreation(entries, table); break;
				case REWARDS: addRewards(entries, table); break;
				default: break;
			}
		}
		return entries.size() > 12 ? entries.subList(0, 12) : entries;
	}

	private static void addDrops(List<SourceEntry> output, List<List<String>> rows)
	{
		if (rows.size() < 2) return;
		int source = column(rows.get(0), "source");
		int rarity = column(rows.get(0), "rarity");
		if (source < 0 || rarity < 0) return;
		for (int i = 1; i < rows.size() && output.size() < 12; i++)
		{
			String displayedName = value(rows.get(i), source);
			String linkedPage = pageValue(rows.get(i), source);
			String name = linkedPage.isEmpty() ? displayedName : linkedPage;
			String rate = value(rows.get(i), rarity);
			String variant = displayedName.equalsIgnoreCase(name) ? "" : displayedName.substring(
				Math.min(displayedName.length(), name.length())).trim();
			if (!name.isEmpty()) output.add(new SourceEntry(name,
				(variant.isEmpty() ? "" : variant + " · ")
					+ (rate.isEmpty() ? "Drop rate unavailable" : "Drop rate: " + normalizeRate(rate)),
				null, 0, requiredQuests(rows.get(i))));
		}
	}

	private static void addShops(List<SourceEntry> output, List<List<String>> rows)
	{
		if (rows.size() < 2) return;
		List<String> headers = rows.get(0);
		int seller = column(headers, "seller");
		int location = column(headers, "location");
		int stock = column(headers, "number in stock");
		int price = column(headers, "price sold at");
		for (int i = 1; i < rows.size() && output.size() < 12; i++)
		{
			String name = pageValue(rows.get(i), seller);
			if (name.isEmpty()) name = value(rows.get(i), seller);
			if (name.isEmpty()) continue;
			String stockValue = value(rows.get(i), stock);
			List<String> details = new ArrayList<>();
			addDetail(details, value(rows.get(i), location));
			addLabeled(details, "Price", value(rows.get(i), price), " gp");
			addLabeled(details, "Stock", stockValue, "");
			output.add(new SourceEntry(name, String.join(" · ", details), null, 0,
				requiredQuests(rows.get(i)), null,
				"0".equals(stockValue.replace(",", "").trim()) ? "No stock in this shop state" : null));
		}
	}

	private static void addSpawns(List<SourceEntry> output, List<List<String>> rows)
	{
		if (rows.size() < 2) return;
		int location = column(rows.get(0), "location");
		int count = column(rows.get(0), "spawns");
		for (int i = 1; i < rows.size() && output.size() < 12; i++)
		{
			String place = value(rows.get(i), location);
			if (!place.isEmpty()) output.add(new SourceEntry(place,
				value(rows.get(i), count).isEmpty() ? "Ground spawn" : "Spawns: " + value(rows.get(i), count),
				null, 0, requiredQuests(rows.get(i))));
		}
	}

	private static void addCreation(List<SourceEntry> output, List<List<String>> rows)
	{
		if (rows.size() < 2 || output.size() >= 12) return;
		for (int i = 0; i < rows.size() - 1; i++)
		{
			List<String> header = rows.get(i);
			if (column(header, "skill") < 0 || column(header, "level") < 0) continue;
			List<String> values = rows.get(i + 1);
			String skill = value(values, column(header, "skill"));
			if (skill.isEmpty()) continue;
			List<String> details = new ArrayList<>();
			String levelText = value(values, column(header, "level")).replace("(b)", "").trim();
			addLabeled(details, "Level", levelText, "");
			addLabeled(details, "XP", value(values, column(header, "xp")), "");
			String facility = findValueAfter(rows, "facilities");
			addDetail(details, facility);
			String ingredient = findIngredient(rows);
			addDetail(details, ingredient);
			List<String> questRequirements = new ArrayList<>();
			for (List<String> row : rows)
			{
				for (String quest : requiredQuests(row))
				{
					if (!questRequirements.contains(quest)) questRequirements.add(quest);
				}
			}
			output.add(new SourceEntry(skill, String.join(" · ", details), skill,
				leadingNumber(levelText), questRequirements));
			return;
		}
	}

	private static void addRewards(List<SourceEntry> output, List<List<String>> rows)
	{
		if (rows.size() < 2) return;
		for (int i = 1; i < rows.size() && output.size() < 12; i++)
		{
			List<String> row = rows.get(i);
			if (row.isEmpty()) continue;
			String title = clueTierName(value(row, 0));
			List<String> detailCells = new ArrayList<>();
			for (int cell = 1; cell < Math.min(row.size(), 3); cell++) detailCells.add(value(row, cell));
			String detail = detailCells.isEmpty() ? "Reward source" : String.join(" · ", detailCells);
			if (!title.isEmpty()) output.add(new SourceEntry(title, detail, null, 0,
				requiredQuests(row)));
		}
	}

	private static List<String> requiredQuests(List<String> row)
	{
		List<String> found = new ArrayList<>();
		for (String rawCell : row)
		{
			String cell = visible(rawCell);
			String text = normalizeForMatch(cell);
			Quest longest = null;
			for (Quest quest : Quest.values())
			{
				String questName = normalizeForMatch(quest.getName());
				if (containsPhrase(text, questName)
					&& (longest == null || questName.length() > normalizeForMatch(longest.getName()).length()))
				{
					longest = quest;
				}
			}
			if (longest != null && !found.contains(longest.getName())) found.add(longest.getName());
		}
		return found;
	}

	private static boolean containsPhrase(String text, String phrase)
	{
		int index = text.indexOf(phrase);
		if (index < 0) return false;
		int end = index + phrase.length();
		return (index == 0 || text.charAt(index - 1) == ' ')
			&& (end == text.length() || text.charAt(end) == ' ');
	}

	private static String normalizeForMatch(String value)
	{
		return value.toLowerCase(Locale.ENGLISH)
			.replace('’', '\'').replaceAll("[^a-z0-9']+", " ").trim();
	}

	private static String normalizeRate(String rate)
	{
		Matcher matcher = FRACTION.matcher(rate.replace(",", "").trim());
		if (!matcher.matches()) return rate;
		double numerator = Double.parseDouble(matcher.group(1));
		double denominator = Double.parseDouble(matcher.group(2));
		if (numerator <= 0 || denominator <= 0) return rate;
		double oneIn = denominator / numerator;
		DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		return "1/" + format.format(oneIn);
	}

	private static String findValueAfter(List<List<String>> rows, String label)
	{
		for (List<String> row : rows)
		{
			int index = column(row, label);
			if (index >= 0 && index + 1 < row.size()) return value(row, index + 1);
		}
		return "";
	}

	private static String findIngredient(List<List<String>> rows)
	{
		for (int i = 0; i < rows.size() - 1; i++)
		{
			int item = column(rows.get(i), "item");
			int quantity = column(rows.get(i), "quantity");
			if (item < 0 || quantity < 0) continue;
			List<String> row = rows.get(i + 1);
			String name = value(row, item);
			String count = value(row, quantity);
			if (name.isEmpty() && item + 2 < row.size())
			{
				name = value(row, item + 1);
				count = value(row, item + 2);
			}
			if (!name.isEmpty()) return "Requires: " + (count.isEmpty() ? "" : count + " × ") + name;
		}
		return "";
	}

	private static List<List<List<String>>> tables(String section)
	{
		List<List<List<String>>> result = new ArrayList<>();
		Matcher tableMatcher = TABLE.matcher(section);
		while (tableMatcher.find())
		{
			List<List<String>> rows = new ArrayList<>();
			Matcher rowMatcher = ROW.matcher(tableMatcher.group(1));
			while (rowMatcher.find())
			{
				List<String> cells = new ArrayList<>();
				Matcher cellMatcher = CELL.matcher(rowMatcher.group(1));
				while (cellMatcher.find())
				{
					String raw = cellMatcher.group(2);
					Matcher link = LINK_TITLE.matcher(raw);
					String page = link.find() ? decode(link.group(1)) : "";
					cells.add(clean(raw) + CELL_META + page);
				}
				if (!cells.isEmpty()) rows.add(cells);
			}
			if (!rows.isEmpty()) result.add(rows);
		}
		return result;
	}

	private static List<Heading> headings(String html)
	{
		List<Heading> result = new ArrayList<>();
		Matcher matcher = HEADING.matcher(html);
		while (matcher.find()) result.add(new Heading(clean(matcher.group(1)), matcher.start(), matcher.end()));
		return result;
	}

	private static SourceCategory categoryFor(String value)
	{
		String heading = value.toLowerCase(Locale.ENGLISH);
		if (heading.contains("shop") || heading.contains("store location")) return SourceCategory.SHOPS;
		if (heading.equals("item sources") || heading.contains("drop source") || heading.contains("monster drop")) return SourceCategory.MONSTER_DROPS;
		if (heading.equals("creation") || heading.contains("creating") || heading.contains("crafting") || heading.contains("harvesting")) return SourceCategory.CRAFTING_AND_SKILLS;
		if (heading.contains("spawn location") || heading.contains("item spawn") || heading.equals("spawns")) return SourceCategory.GROUND_SPAWNS;
		if (heading.contains("reward") || heading.contains("treasure trail")) return SourceCategory.REWARDS;
		return null;
	}

	private static int column(List<String> cells, String wanted)
	{
		for (int i = 0; i < cells.size(); i++) if (visible(cells.get(i)).toLowerCase(Locale.ENGLISH).equals(wanted)) return i;
		return -1;
	}

	private static String value(List<String> row, int index) { return index >= 0 && index < row.size() ? visible(row.get(index)) : ""; }
	private static String pageValue(List<String> row, int index)
	{
		if (index < 0 || index >= row.size()) return "";
		String cell = row.get(index);
		int split = cell.indexOf(CELL_META);
		return split < 0 ? "" : cell.substring(split + CELL_META.length());
	}
	private static String visible(String cell)
	{
		int split = cell.indexOf(CELL_META);
		return split < 0 ? cell : cell.substring(0, split);
	}
	private static void addDetail(List<String> details, String value) { if (!value.isEmpty()) details.add(value); }
	private static void addLabeled(List<String> details, String label, String value, String suffix) { if (!value.isEmpty()) details.add(label + ": " + value + suffix); }
	private static int leadingNumber(String value)
	{
		Matcher matcher = Pattern.compile("^(\\d+)").matcher(value);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
	}

	private static String clean(String html)
	{
		return decode(TAG.matcher(HIDDEN.matcher(html).replaceAll(" ")).replaceAll(" "))
			.replaceAll("\\s+", " ").trim();
	}

	private static String decode(String value)
	{
		return value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
			.replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
			.replace("&#160;", " ").replace("&apos;", "'");
	}

	private static String clueTierName(String value)
	{
		switch (value.toLowerCase(Locale.ENGLISH))
		{
			case "beginner": case "easy": case "medium": case "hard": case "elite": case "master":
				return value + " clue scroll";
			default: return value;
		}
	}

	private static final class Heading
	{
		private final String text; private final int start; private final int end;
		private Heading(String text, int start, int end) { this.text = text; this.start = start; this.end = end; }
	}
}
