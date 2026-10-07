package com.itemsource;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Singleton
final class ItemSourceClient
{
	private static final String API = "https://oldschool.runescape.wiki/api.php";
	private final OkHttpClient client;
	private final Gson gson;
	private final ItemSourceParser parser = new ItemSourceParser();
	private final SourceRequirementParser requirementParser = new SourceRequirementParser();
	private final Set<Call> calls = Collections.synchronizedSet(new HashSet<>());
	private volatile Call suggestionCall;
	private final Map<String, CachedResult> resultCache = new HashMap<>();
	private static final long CACHE_MILLIS = 10 * 60 * 1000L;

	@Inject
	private ItemSourceClient(OkHttpClient client, Gson gson) { this.client = client; this.gson = gson; }

	void search(String query, Consumer<ItemSourceResult> success, Consumer<String> error)
	{
		HttpUrl url = base().newBuilder().addQueryParameter("action", "query")
			.addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
			.addQueryParameter("list", "search").addQueryParameter("srsearch", query + " incategory:Items")
			.addQueryParameter("srnamespace", "0").addQueryParameter("srlimit", "1").build();
		request(url, json -> {
			SearchResponse response = gson.fromJson(json, SearchResponse.class);
			if (response == null || response.query == null || response.query.search == null
				|| response.query.search.isEmpty()) error.accept("No matching OSRS item was found.");
			else loadPage(response.query.search.get(0).title, success, error);
		}, error);
	}

	void suggest(String query, Consumer<List<String>> success, Runnable error)
	{
		cancelSuggestions();
		HttpUrl url = base().newBuilder().addQueryParameter("action", "query")
			.addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
			.addQueryParameter("list", "search")
			.addQueryParameter("srsearch", query + " incategory:Items")
			.addQueryParameter("srnamespace", "0").addQueryParameter("srlimit", "8").build();
		Request request = new Request.Builder().url(url)
			.header("User-Agent", "RuneLite ItemSource plugin/1.0").build();
		Call call = client.newCall(request);
		suggestionCall = call;
		call.enqueue(new Callback()
		{
			@Override public void onFailure(Call completed, IOException exception)
			{
				if (completed == suggestionCall && !completed.isCanceled()) error.run();
			}
			@Override public void onResponse(Call completed, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (completed != suggestionCall) return;
					if (!response.isSuccessful() || body == null) { error.run(); return; }
					try
					{
						SearchResponse result = gson.fromJson(body.string(), SearchResponse.class);
						if (result == null || result.query == null || result.query.search == null) error.run();
						else success.accept(result.query.search.stream().map(hit -> hit.title).collect(Collectors.toList()));
					}
					catch (RuntimeException exception) { error.run(); }
				}
			}
		});
	}

	void cancelSuggestions()
	{
		Call call = suggestionCall;
		suggestionCall = null;
		if (call != null) call.cancel();
	}

	void cancelAll()
	{
		cancelSuggestions();
		synchronized (calls) { calls.forEach(Call::cancel); calls.clear(); }
	}

	private void loadPage(String title, Consumer<ItemSourceResult> success, Consumer<String> error)
	{
		String cacheKey = title.toLowerCase(Locale.ENGLISH);
		synchronized (resultCache)
		{
			CachedResult cached = resultCache.get(cacheKey);
			if (cached != null && cached.expiresAt > System.currentTimeMillis())
			{
				success.accept(cached.result);
				return;
			}
			resultCache.remove(cacheKey);
		}
		HttpUrl url = base().newBuilder().addQueryParameter("action", "parse")
			.addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
			.addQueryParameter("page", title).addQueryParameter("prop", "text")
			.addQueryParameter("redirects", "1").build();
		request(url, json -> {
			ParseResponse response = gson.fromJson(json, ParseResponse.class);
			if (response == null || response.parse == null || response.parse.text == null)
				error.accept("The Wiki page could not be read.");
			else loadRequirements(parser.parse(response.parse.title, response.parse.text), value -> {
				synchronized (resultCache)
				{
					resultCache.put(cacheKey, new CachedResult(value, System.currentTimeMillis() + CACHE_MILLIS));
				}
				success.accept(value);
			}, error);
		}, error);
	}

	private void loadRequirements(ItemSourceResult result, Consumer<ItemSourceResult> success,
		Consumer<String> error)
	{
		Set<String> titles = new LinkedHashSet<>();
		for (Map.Entry<SourceCategory, List<SourceEntry>> category : result.getSources().entrySet())
		{
			if (category.getKey() == SourceCategory.CRAFTING_AND_SKILLS) continue;
			for (SourceEntry entry : category.getValue())
			{
				String title = entry.getTitle().replaceFirst("\\s*\\([^)]*\\)\\s*$", "").trim();
				titles.add(title);
				if (category.getKey() == SourceCategory.MONSTER_DROPS) titles.add(title + "/Strategies");
			}
		}
		if (titles.isEmpty())
		{
			success.accept(result);
			return;
		}

		HttpUrl url = base().newBuilder().addQueryParameter("action", "query")
			.addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
			.addQueryParameter("prop", "revisions").addQueryParameter("rvprop", "content")
			.addQueryParameter("rvslots", "main").addQueryParameter("redirects", "1")
			.addQueryParameter("titles", String.join("|", titles)).build();
		request(url, json -> {
			RequirementResponse response = gson.fromJson(json, RequirementResponse.class);
			if (response == null || response.query == null || response.query.pages == null)
			{
				success.accept(result);
				return;
			}
			Map<String, SourceRequirement> requirements = new HashMap<>();
			for (RequirementPage page : response.query.pages)
			{
				if (page.revisions == null || page.revisions.isEmpty()) continue;
				RequirementRevision revision = page.revisions.get(0);
				if (revision.slots == null || revision.slots.main == null
					|| revision.slots.main.content == null) continue;
				boolean strategy = page.title.endsWith("/Strategies");
				String baseTitle = strategy ? page.title.substring(0, page.title.length() - 11) : page.title;
				String key = ItemSourceResult.requirementKey(baseTitle);
				SourceRequirement parsed = strategy
					? requirementParser.parseStrategy(revision.slots.main.content)
					: requirementParser.parse(revision.slots.main.content);
				requirements.merge(key, parsed, SourceRequirement::merge);
			}
			if (response.query.redirects != null)
			{
				for (RequirementRedirect redirect : response.query.redirects)
				{
					SourceRequirement requirement = requirements.get(
						ItemSourceResult.requirementKey(redirect.to));
					if (requirement != null)
						requirements.put(ItemSourceResult.requirementKey(redirect.from), requirement);
				}
			}
			loadLocationRequirements(result, requirements, success);
		}, ignored -> success.accept(result));
	}

	private void loadLocationRequirements(ItemSourceResult result,
		Map<String, SourceRequirement> requirements, Consumer<ItemSourceResult> success)
	{
		Set<String> locations = new LinkedHashSet<>();
		for (SourceRequirement requirement : requirements.values())
		{
			if (requirement.getLocationPage() != null) locations.add(requirement.getLocationPage());
		}
		if (locations.isEmpty())
		{
			success.accept(result.withRequirements(requirements));
			return;
		}
		HttpUrl url = base().newBuilder().addQueryParameter("action", "query")
			.addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
			.addQueryParameter("prop", "revisions").addQueryParameter("rvprop", "content")
			.addQueryParameter("rvslots", "main").addQueryParameter("redirects", "1")
			.addQueryParameter("titles", String.join("|", locations)).build();
		request(url, json -> {
			RequirementResponse response = gson.fromJson(json, RequirementResponse.class);
			Map<String, SourceRequirement> locationRequirements = new HashMap<>();
			if (response != null && response.query != null && response.query.pages != null)
			{
				for (RequirementPage page : response.query.pages)
				{
					if (page.revisions == null || page.revisions.isEmpty()) continue;
					RequirementRevision revision = page.revisions.get(0);
					if (revision.slots == null || revision.slots.main == null
						|| revision.slots.main.content == null) continue;
					locationRequirements.put(ItemSourceResult.requirementKey(page.title),
						requirementParser.parse(revision.slots.main.content));
				}
				if (response.query.redirects != null)
				{
					for (RequirementRedirect redirect : response.query.redirects)
					{
						SourceRequirement requirement = locationRequirements.get(
							ItemSourceResult.requirementKey(redirect.to));
						if (requirement != null)
							locationRequirements.put(ItemSourceResult.requirementKey(redirect.from), requirement);
					}
				}
			}
			Map<String, SourceRequirement> verified = new HashMap<>();
			requirements.forEach((key, requirement) -> {
				if (requirement.getLocationPage() == null) return;
				SourceRequirement location = locationRequirements.get(
					ItemSourceResult.requirementKey(requirement.getLocationPage()));
				if (location != null) verified.put(key, requirement.merge(location));
			});
			requirements.forEach((key, requirement) -> {
				if (requirement.getLocationPage() == null) verified.put(key, requirement);
			});
			success.accept(result.withRequirements(verified));
		}, ignored -> {
			Map<String, SourceRequirement> verified = new HashMap<>();
			requirements.forEach((key, requirement) -> {
				if (requirement.getLocationPage() == null) verified.put(key, requirement);
			});
			success.accept(result.withRequirements(verified));
		});
	}

	private void request(HttpUrl url, Consumer<String> success, Consumer<String> error)
	{
		Call call = client.newCall(new Request.Builder().url(url)
			.header("User-Agent", "RuneLite ItemSource plugin/1.0").build());
		calls.add(call);
		call.enqueue(new Callback() {
			@Override public void onFailure(Call call, IOException ex) {
				calls.remove(call); if (!call.isCanceled()) error.accept("The OSRS Wiki could not be reached.");
			}
			@Override public void onResponse(Call call, Response response) throws IOException {
				calls.remove(call);
				try (ResponseBody body = response.body()) {
					if (!response.isSuccessful() || body == null) error.accept("Wiki error (HTTP " + response.code() + ").");
					else try { success.accept(body.string()); }
					catch (RuntimeException ex) { error.accept("The Wiki response could not be understood."); }
				}
			}
		});
	}

	private static HttpUrl base() {
		HttpUrl url = HttpUrl.parse(API); if (url == null) throw new IllegalStateException("Invalid API URL"); return url;
	}
	private static final class SearchResponse { private Query query; }
	private static final class Query { private List<SearchHit> search; }
	private static final class SearchHit { private String title; }
	private static final class ParseResponse { private ParsedPage parse; }
	private static final class ParsedPage { private String title; private String text; }
	private static final class RequirementResponse { private RequirementQuery query; }
	private static final class RequirementQuery
	{
		private List<RequirementPage> pages;
		private List<RequirementRedirect> redirects;
	}
	private static final class RequirementPage
	{
		private String title;
		private List<RequirementRevision> revisions;
	}
	private static final class RequirementRevision { private RequirementSlots slots; }
	private static final class RequirementSlots { private RequirementSlot main; }
	private static final class RequirementSlot { private String content; }
	private static final class RequirementRedirect { private String from; private String to; }
	private static final class CachedResult
	{
		private final ItemSourceResult result;
		private final long expiresAt;
		private CachedResult(ItemSourceResult result, long expiresAt)
		{
			this.result = result;
			this.expiresAt = expiresAt;
		}
	}
}
