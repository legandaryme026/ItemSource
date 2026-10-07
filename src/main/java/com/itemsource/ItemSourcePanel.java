package com.itemsource;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.api.ItemComposition;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.coords.WorldPoint;

@Singleton
final class ItemSourcePanel extends PluginPanel
{
	private static final Color GOLD = new Color(232, 178, 55);
	private static final Color TEXT = new Color(225, 225, 225);
	private static final Color MUTED = new Color(166, 171, 177);
	private static final Color BACKGROUND = new Color(27, 29, 31);
	private static final Color CARD = new Color(35, 38, 41);
	private static final Color CARD_ALT = new Color(43, 46, 50);
	private static final Color DIVIDER = new Color(59, 63, 68);
	private static final Color SUCCESS = new Color(91, 196, 128);
	private static final Pattern MATERIAL = Pattern.compile("Requires: ([0-9,]+) × (.+)$");

	private final ItemSourceClient sourceClient;
	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final Client client;
	private final ItemSourceConfig config;
	private final JTextField searchField = new JTextField();
	private final JButton searchButton = new JButton("Search");
	private final JButton wikiButton = new JButton("Open Wiki");
	private final JLabel status = new JLabel("●  Ready to search", SwingConstants.LEFT);
	private final JPanel results = new WidthTrackingPanel();
	private final DefaultListModel<String> suggestionModel = new DefaultListModel<>();
	private final JList<String> suggestionList = new JList<>(suggestionModel);
	private final JScrollPane suggestionScroll = new JScrollPane(suggestionList);
	private final Timer suggestionTimer = new Timer(300, event -> requestSuggestions());
	private final JPanel recentContainer = verticalPanel(BACKGROUND);
	private final JPanel favoriteButtons = verticalPanel(BACKGROUND);
	private final JPanel recentButtons = verticalPanel(BACKGROUND);
	private final JLabel favoriteTitle = smallSectionLabel("FAVORITES");
	private final JLabel recentTitle = smallSectionLabel("RECENT");
	private final List<String> recentItems = new ArrayList<>();
	private final List<String> favoriteItems = new ArrayList<>();
	private String wikiUrl;
	private boolean settingSuggestion;
	private SourceCategory activeFilter;
	private ItemDisplayInfo currentItemInfo;
	private AccountInfo currentAccountInfo;
	private Recommendation currentRecommendation;
	private ItemSourceResult currentResult;

	@Inject
	private ItemSourcePanel(ItemSourceClient sourceClient, ConfigManager configManager,
		ItemManager itemManager, ClientThread clientThread, Client client, ItemSourceConfig config)
	{
		super(false);
		this.sourceClient = sourceClient;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.client = client;
		this.config = config;
		loadRecents();
		loadFavorites();
		setLayout(new BorderLayout(0, 10));
		setBorder(BorderFactory.createEmptyBorder(12, 11, 10, 11));
		setBackground(BACKGROUND);
		add(searchArea(), BorderLayout.NORTH);

		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
		results.setBackground(BACKGROUND);
		JScrollPane scroll = new JScrollPane(results);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(BACKGROUND);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);
		showWelcome();
	}

	void deactivate()
	{
		suggestionTimer.stop();
		sourceClient.cancelAll();
		SwingUtilities.invokeLater(() -> setLoading(false));
	}

	void searchFor(String itemName)
	{
		settingSuggestion = true;
		searchField.setText(itemName);
		settingSuggestion = false;
		startSearch();
	}

	void refreshCurrent()
	{
		if (currentResult != null) renderResult(currentResult);
	}

	private JPanel searchArea()
	{
		JPanel area = verticalPanel(BACKGROUND);
		JPanel brand = new JPanel(new BorderLayout(8, 0));
		brand.setBackground(BACKGROUND);
		brand.setBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, GOLD));
		brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 47));
		brand.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel brandText = verticalPanel(BACKGROUND);
		brandText.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
		JLabel title = new JLabel("ItemSource");
		title.setForeground(GOLD);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 21f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		brandText.add(title);

		JLabel subtitle = new JLabel("Smart item acquisition for your account");
		subtitle.setForeground(MUTED);
		subtitle.setFont(subtitle.getFont().deriveFont(10f));
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		brandText.add(subtitle);
		brand.add(brandText, BorderLayout.CENTER);
		area.add(brand);
		area.add(Box.createVerticalStrut(14));

		searchField.setToolTipText("Type at least two letters");
		searchField.setFont(searchField.getFont().deriveFont(14f));
		searchField.setBackground(CARD);
		searchField.setForeground(TEXT);
		searchField.setCaretColor(GOLD);
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(82, 87, 93)),
			BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchField.addActionListener(event -> startSearch());
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent event) { changed(); }
			@Override public void removeUpdate(DocumentEvent event) { changed(); }
			@Override public void changedUpdate(DocumentEvent event) { changed(); }
			private void changed()
			{
				if (settingSuggestion) return;
				suggestionTimer.restart();
			}
		});
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override public void keyPressed(KeyEvent event)
			{
				if (event.getKeyCode() == KeyEvent.VK_DOWN && suggestionScroll.isVisible()
					&& !suggestionModel.isEmpty())
				{
					suggestionList.setSelectedIndex(0);
					suggestionList.requestFocusInWindow();
				}
			}
		});
		area.add(searchField);

		configureSuggestions();
		suggestionScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		area.add(suggestionScroll);
		area.add(Box.createVerticalStrut(7));

		JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 0));
		buttons.setBackground(BACKGROUND);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		styleButton(searchButton, true);
		styleButton(wikiButton, false);
		searchButton.addActionListener(event -> startSearch());
		wikiButton.setEnabled(false);
		wikiButton.addActionListener(event -> { if (wikiUrl != null) LinkBrowser.browse(wikiUrl); });
		buttons.add(searchButton);
		buttons.add(wikiButton);
		area.add(buttons);
		area.add(Box.createVerticalStrut(9));

		status.setForeground(new Color(187, 191, 196));
		status.setFont(status.getFont().deriveFont(11f));
		status.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
			BorderFactory.createEmptyBorder(8, 1, 1, 1)));
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		area.add(status);
		area.add(Box.createVerticalStrut(7));
		configureRecents();
		area.add(recentContainer);
		return area;
	}

	private void configureRecents()
	{
		recentContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		recentContainer.add(favoriteTitle);
		recentContainer.add(Box.createVerticalStrut(3));
		favoriteButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
		recentContainer.add(favoriteButtons);
		recentContainer.add(Box.createVerticalStrut(5));
		recentContainer.add(recentTitle);
		recentContainer.add(Box.createVerticalStrut(3));
		recentButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
		recentContainer.add(recentButtons);
		refreshRecents();
	}

	private static JLabel smallSectionLabel(String text)
	{
		JLabel label = new JLabel("—  " + text);
		label.setForeground(MUTED);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private void loadRecents()
	{
		String stored = configManager.getConfiguration("itemsource", "recentItems");
		if (stored == null || stored.isEmpty()) return;
		for (String item : stored.split("\\|\\|"))
		{
			if (!item.isEmpty() && recentItems.size() < 5) recentItems.add(item);
		}
	}

	private void addRecent(String itemName)
	{
		recentItems.remove(itemName);
		recentItems.add(0, itemName);
		while (recentItems.size() > 5) recentItems.remove(recentItems.size() - 1);
		configManager.setConfiguration("itemsource", "recentItems", String.join("||", recentItems));
		refreshRecents();
	}

	private void loadFavorites()
	{
		String stored = configManager.getConfiguration("itemsource", "favoriteItems");
		if (stored == null || stored.isEmpty()) return;
		for (String item : stored.split("\\|\\|")) if (!item.isEmpty()) favoriteItems.add(item);
	}

	private void toggleFavorite(String itemName)
	{
		if (!favoriteItems.remove(itemName)) favoriteItems.add(itemName);
		configManager.setConfiguration("itemsource", "favoriteItems", String.join("||", favoriteItems));
		refreshRecents();
	}

	private void refreshRecents()
	{
		favoriteTitle.setVisible(!favoriteItems.isEmpty());
		favoriteButtons.setVisible(!favoriteItems.isEmpty());
		recentTitle.setVisible(!recentItems.isEmpty());
		recentButtons.setVisible(!recentItems.isEmpty());
		favoriteButtons.removeAll();
		for (String item : favoriteItems) favoriteButtons.add(historyButton("★  " + item, item));
		recentButtons.removeAll();
		for (int index = 0; index < Math.min(3, recentItems.size()); index++)
			recentButtons.add(historyButton(recentItems.get(index), recentItems.get(index)));
		recentContainer.setVisible(!recentItems.isEmpty() || !favoriteItems.isEmpty());
		favoriteButtons.revalidate();
		favoriteButtons.repaint();
		recentButtons.revalidate();
		recentButtons.repaint();
	}

	private JButton historyButton(String label, String item)
	{
		JButton button = new JButton(label);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
		button.setBackground(BACKGROUND);
		button.setForeground(new Color(190, 194, 198));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
		button.addActionListener(event -> searchFor(item));
		return button;
	}

	private void configureSuggestions()
	{
		suggestionTimer.setRepeats(false);
		suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		suggestionList.setBackground(CARD_ALT);
		suggestionList.setForeground(TEXT);
		suggestionList.setSelectionBackground(new Color(105, 77, 29));
		suggestionList.setSelectionForeground(Color.WHITE);
		suggestionList.setFixedCellHeight(30);
		suggestionList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override public Component getListCellRendererComponent(JList<?> list, Object value,
				int index, boolean selected, boolean focus)
			{
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
				label.setText("  " + value);
				label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER));
				return label;
			}
		});
		suggestionList.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseClicked(MouseEvent event) { selectSuggestion(); }
		});
		suggestionList.addKeyListener(new KeyAdapter()
		{
			@Override public void keyPressed(KeyEvent event)
			{
				if (event.getKeyCode() == KeyEvent.VK_ENTER) { selectSuggestion(); event.consume(); }
				else if (event.getKeyCode() == KeyEvent.VK_ESCAPE) { hideSuggestions(); searchField.requestFocusInWindow(); }
			}
		});
		suggestionScroll.setBorder(BorderFactory.createLineBorder(new Color(82, 87, 93)));
		suggestionScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		suggestionScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 151));
		suggestionScroll.setPreferredSize(new Dimension(PANEL_WIDTH, 121));
		suggestionScroll.setVisible(false);
	}

	private void requestSuggestions()
	{
		String query = searchField.getText().trim();
		if (query.length() < 2) { sourceClient.cancelSuggestions(); hideSuggestions(); return; }
		sourceClient.suggest(query,
			items -> SwingUtilities.invokeLater(() -> showSuggestions(query, items)),
			() -> SwingUtilities.invokeLater(this::hideSuggestions));
	}

	private void showSuggestions(String query, List<String> items)
	{
		if (!searchField.getText().trim().equals(query)) return;
		suggestionModel.clear();
		for (String item : items) suggestionModel.addElement(item);
		suggestionScroll.setVisible(!items.isEmpty());
		revalidate();
		repaint();
	}

	private void selectSuggestion()
	{
		String selected = suggestionList.getSelectedValue();
		if (selected == null) return;
		settingSuggestion = true;
		searchField.setText(selected);
		settingSuggestion = false;
		hideSuggestions();
		startSearch();
	}

	private void hideSuggestions()
	{
		suggestionModel.clear();
		suggestionScroll.setVisible(false);
		revalidate();
	}

	private void startSearch()
	{
		String query = searchField.getText().trim();
		if (query.isEmpty()) { status.setForeground(new Color(226, 122, 115)); status.setText("●  Enter an item name"); return; }
		suggestionTimer.stop();
		hideSuggestions();
		sourceClient.cancelAll();
		setLoading(true);
		currentResult = null;
		wikiUrl = null;
		wikiButton.setEnabled(false);
		status.setForeground(GOLD);
		status.setText("●  Checking sources and account requirements...");
		results.removeAll();
		refresh();
		sourceClient.search(query,
			this::prepareResult,
			error -> SwingUtilities.invokeLater(() -> showError(error)));
	}

	private void prepareResult(ItemSourceResult value)
	{
		clientThread.invokeLater(() ->
		{
			AccountMode accountMode = AccountMode.fromValue(client.getVarbitValue(VarbitID.IRONMAN));
			Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
			for (Skill skill : Skill.values())
			{
				if (!"Overall".equals(skill.getName())) levels.put(skill, client.getRealSkillLevel(skill));
			}
			Map<String, QuestState> questStates = new HashMap<>();
			for (List<SourceEntry> entries : value.getSources().values())
			{
				for (SourceEntry entry : entries)
				{
					for (String questName : entry.getRequiredQuests())
					{
						Quest quest = questByName(questName);
						if (quest != null && !questStates.containsKey(questName))
						{
							questStates.put(questName, quest.getState(client));
						}
					}
				}
			}
			String slayerTask = configManager.getRSProfileConfiguration("slayer", "taskName");
			Map<String, Integer> carriedItems = new HashMap<>();
			addContainerItems(carriedItems, client.getItemContainer(InventoryID.INV));
			addContainerItems(carriedItems, client.getItemContainer(InventoryID.WORN));
			Map<String, Integer> bankItems = new HashMap<>();
			ItemContainer bank = client.getItemContainer(InventoryID.BANK);
			if (bank != null) addContainerItems(bankItems, bank);
			AccountInfo accountInfo = new AccountInfo(accountMode, levels, questStates,
				client.getVarpValue(VarPlayerID.AWARDED_POINTS), slayerTask,
				carriedItems, bankItems, bank != null,
				client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation());
			ItemDisplayInfo info = null;
			ItemPrice price = exactItem(value.getItemName());
			if (price != null)
			{
				ItemComposition composition = itemManager.getItemComposition(price.getId());
				AsyncBufferedImage image = itemManager.getImage(price.getId());
				info = new ItemDisplayInfo(price, composition.isMembers(), composition.isTradeable(), image);
			}
			ItemDisplayInfo finalInfo = info;
			SwingUtilities.invokeLater(() -> showResult(value, finalInfo, accountInfo));
		});
	}

	private void addContainerItems(Map<String, Integer> quantities, ItemContainer container)
	{
		if (container == null) return;
		for (Item item : container.getItems())
		{
			if (item.getId() < 0 || item.getQuantity() <= 0) continue;
			String name = itemManager.getItemComposition(item.getId()).getName();
			if (name != null && !name.isEmpty())
				quantities.merge(name.toLowerCase(Locale.ENGLISH), item.getQuantity(), Integer::sum);
		}
	}

	private void showResult(ItemSourceResult value, ItemDisplayInfo info, AccountInfo accountInfo)
	{
		setLoading(false);
		currentResult = value;
		currentItemInfo = info;
		currentAccountInfo = accountInfo;
		addRecent(value.getItemName());
		wikiUrl = value.getWikiUrl();
		wikiButton.setEnabled(true);
		status.setForeground(SUCCESS);
		status.setText("●  Sources ready for " + value.getItemName());
		activeFilter = null;
		renderResult(value);
	}

	private void renderResult(ItemSourceResult value)
	{
		currentRecommendation = chooseRecommendation(value);
		results.removeAll();
		results.add(itemHeader(value.getItemName()));
		results.add(Box.createVerticalStrut(7));
		JPanel tracked = trackedPanel(value);
		if (tracked != null) { results.add(tracked); results.add(Box.createVerticalStrut(7)); }
		results.add(recommendationCard(value));
		results.add(Box.createVerticalStrut(7));
		results.add(comparisonPanel(value));
		results.add(Box.createVerticalStrut(7));
		results.add(filterBar(value));
		results.add(Box.createVerticalStrut(9));
		if (!value.hasSources()) addMessage("No supported source tables found. Open the Wiki for full details.");
		else for (SourceCategory category : SourceCategory.values())
		{
			if (activeFilter != null && activeFilter != category) continue;
			List<SourceEntry> entries = value.getSources().get(category);
			if (entries != null && config.hideLocked())
			{
				List<SourceEntry> visible = new ArrayList<>();
				for (SourceEntry entry : entries) if (entryAvailable(entry)) visible.add(entry);
				entries = visible;
			}
			if (entries != null && !entries.isEmpty())
			{
				results.add(categoryCard(category, entries));
				results.add(Box.createVerticalStrut(9));
			}
			else if (category == SourceCategory.GROUND_SPAWNS)
			{
				results.add(emptySpawnCard());
				results.add(Box.createVerticalStrut(9));
			}
		}
		refresh();
	}

	private JPanel recommendationCard(ItemSourceResult value)
	{
		boolean ironman = currentAccountInfo != null && currentAccountInfo.accountMode.isIronman();
		String account = ironman ? currentAccountInfo.accountMode.getDisplayName() : "Standard account";
		Recommendation recommendation = currentRecommendation;

		Color recommendationBackground = new Color(31, 47, 39);
		JPanel card = verticalPanel(recommendationBackground);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, SUCCESS),
			BorderFactory.createEmptyBorder(10, 10, 9, 9)));
		JLabel accountLabel = new JLabel("BEST MATCH  •  " + account.toUpperCase());
		accountLabel.setForeground(new Color(143, 211, 165));
		accountLabel.setFont(accountLabel.getFont().deriveFont(Font.BOLD, 10f));
		card.add(accountLabel);
		card.add(Box.createVerticalStrut(4));
		JLabel title = new JLabel(html("<b style='color:#f0f0f0'>Recommended: " + escape(recommendation.title) + "</b>"));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		card.add(title);
		JLabel detail = new JLabel(html("<span style='color:#aeb8b1'>" + escape(recommendation.summary) + "</span>"));
		card.add(detail);
		JPanel reasons = verticalPanel(recommendationBackground);
		reasons.setVisible(false);
		for (String reason : recommendation.reasons)
		{
			JLabel reasonLabel = new JLabel(html("<span style='color:#aeb8b1'>• " + escape(reason) + "</span>"));
			reasonLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			reasons.add(reasonLabel);
		}
		JButton why = new JButton("Why this method? ▼");
		why.setForeground(new Color(143, 211, 165));
		why.setBackground(recommendationBackground);
		why.setBorder(BorderFactory.createEmptyBorder(7, 0, 2, 0));
		why.setHorizontalAlignment(SwingConstants.LEFT);
		why.setFocusPainted(false);
		why.addActionListener(event ->
		{
			reasons.setVisible(!reasons.isVisible());
			why.setText(reasons.isVisible() ? "Why this method? ▲" : "Why this method? ▼");
			refresh();
		});
		card.add(why);
		card.add(reasons);
		if (recommendation.entry != null)
		{
			JButton track = new JButton("Track this method");
			styleCompactButton(track, GOLD);
			track.setAlignmentX(Component.LEFT_ALIGNMENT);
			track.addActionListener(event ->
			{
				configManager.setConfiguration(ItemSourceConfig.GROUP, "trackedItem", value.getItemName());
				configManager.setConfiguration(ItemSourceConfig.GROUP, "trackedSource", recommendation.entry.getTitle());
				configManager.setConfiguration(ItemSourceConfig.GROUP, "trackedCategory", recommendation.category.name());
				configManager.setConfiguration(ItemSourceConfig.GROUP, "trackedAttempts", 0);
				renderResult(value);
			});
			card.add(Box.createVerticalStrut(5));
			card.add(track);
		}
		return card;
	}

	private JPanel trackedPanel(ItemSourceResult value)
	{
		String trackedItem = configManager.getConfiguration(ItemSourceConfig.GROUP, "trackedItem");
		if (!value.getItemName().equalsIgnoreCase(trackedItem)) return null;
		String source = configManager.getConfiguration(ItemSourceConfig.GROUP, "trackedSource");
		String category = configManager.getConfiguration(ItemSourceConfig.GROUP, "trackedCategory");
		int attempts = trackedAttempts();
		JPanel card = verticalPanel(new Color(47, 43, 31));
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, GOLD),
			BorderFactory.createEmptyBorder(7, 9, 7, 8)));
		card.add(new JLabel(html("<b style='color:#e8b237'>TRACKING: " + escape(source) + "</b>")));
		if (SourceCategory.MONSTER_DROPS.name().equals(category))
		{
			JLabel count = new JLabel("Attempts: " + QuantityFormatter.formatNumber(attempts));
			count.setForeground(TEXT);
			card.add(count);
			JPanel buttons = new JPanel(new GridLayout(1, 3, 5, 0));
			buttons.setBackground(new Color(47, 43, 31));
			JButton add = new JButton("+1");
			JButton reset = new JButton("Reset");
			JButton stop = new JButton("Stop");
			styleCompactButton(add, GOLD);
			styleCompactButton(reset, MUTED);
			styleCompactButton(stop, new Color(226, 122, 115));
			add.addActionListener(event -> { configManager.setConfiguration(ItemSourceConfig.GROUP, "trackedAttempts", attempts + 1); renderResult(value); });
			reset.addActionListener(event -> { configManager.setConfiguration(ItemSourceConfig.GROUP, "trackedAttempts", 0); renderResult(value); });
			stop.addActionListener(event -> { clearTracking(); renderResult(value); });
			buttons.add(add); buttons.add(reset); buttons.add(stop); card.add(buttons);
		}
		else
		{
			JButton stop = new JButton("Stop tracking");
			styleCompactButton(stop, new Color(226, 122, 115));
			stop.addActionListener(event -> { clearTracking(); renderResult(value); });
			card.add(stop);
		}
		return card;
	}

	private void clearTracking()
	{
		configManager.unsetConfiguration(ItemSourceConfig.GROUP, "trackedItem");
		configManager.unsetConfiguration(ItemSourceConfig.GROUP, "trackedSource");
		configManager.unsetConfiguration(ItemSourceConfig.GROUP, "trackedCategory");
		configManager.unsetConfiguration(ItemSourceConfig.GROUP, "trackedAttempts");
	}

	private int trackedAttempts()
	{
		String value = configManager.getConfiguration(ItemSourceConfig.GROUP, "trackedAttempts");
		if (value == null) return 0;
		try
		{
			return Math.max(0, Integer.parseInt(value));
		}
		catch (NumberFormatException ignored)
		{
			return 0;
		}
	}

	private JPanel comparisonPanel(ItemSourceResult value)
	{
		List<Candidate> candidates = rankedCandidates(value);
		JPanel card = verticalPanel(CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(48, 52, 56)),
			BorderFactory.createEmptyBorder(8, 9, 7, 9)));
		JLabel heading = new JLabel("TOP 3 FOR YOUR ACCOUNT");
		heading.setForeground(GOLD);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 11f));
		card.add(heading);
		int shown = 0;
		boolean ironman = currentAccountInfo != null && currentAccountInfo.accountMode.isIronman();
		if (!ironman && currentItemInfo != null && currentItemInfo.tradeable)
		{
			card.add(comparisonRow(1, "Grand Exchange", "Direct · GE price"));
			shown++;
		}
		for (Candidate candidate : candidates)
		{
			if (shown >= 3) break;
			card.add(comparisonRow(shown + 1, candidate.entry.getTitle(),
				comparisonSummary(candidate.category, candidate.entry)));
			shown++;
		}
		if (shown == 0) card.add(new JLabel("No currently suitable methods"));
		return card;
	}

	private JPanel comparisonRow(int rank, String title, String detail)
	{
		JPanel row = verticalPanel(CARD);
		row.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER));
		row.add(Box.createVerticalStrut(6));
		row.add(new JLabel(html("<b style='color:#e8b237'>" + rank + "</b>&nbsp;&nbsp;<b style='color:#f0f0f0'>" + escape(title) + "</b>")));
		row.add(new JLabel(html("<span style='color:#aeb3b8'>" + escape(detail) + "</span>")));
		return row;
	}

	private String comparisonSummary(SourceCategory category, SourceEntry entry)
	{
		String risk = entry.hasWildernessRisk() ? " · Wilderness risk" : "";
		int distance = sourceDistance(entry);
		String travel = distance > 0 ? " · ~" + distance + " tiles" : "";
		switch (category)
		{
			case SHOPS:
				return (shopPrice(entry) > 0 ? QuantityFormatter.formatNumber((long) shopPrice(entry)) + " gp" : "Shop") + " · No RNG" + risk + travel;
			case GROUND_SPAWNS: return "Free · No RNG" + risk + travel;
			case CRAFTING_AND_SKILLS: return "Skill method · Materials checked";
			case MONSTER_DROPS: return entry.getDetail() + risk + travel;
			default: return "Reward source" + risk + travel;
		}
	}

	private List<Candidate> rankedCandidates(ItemSourceResult value)
	{
		List<Candidate> candidates = new ArrayList<>();
		for (SourceCategory category : SourceCategory.values())
		{
			List<SourceEntry> entries = value.getSources().get(category);
			if (entries == null) continue;
			for (SourceEntry entry : entries)
			{
				if (!entryAvailable(entry) || !candidateAllowed(category, entry)
					|| (category == SourceCategory.MONSTER_DROPS && !recommendationsMet(entry))) continue;
				candidates.add(new Candidate(category, entry, sourceScore(category, entry)));
			}
		}
		candidates.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.score).reversed());
		return candidates;
	}

	private Recommendation chooseRecommendation(ItemSourceResult value)
	{
		boolean ironman = currentAccountInfo != null && currentAccountInfo.accountMode.isIronman();
		if (!ironman && currentItemInfo != null && currentItemInfo.tradeable)
			return new Recommendation("Grand Exchange", "Fastest direct option for this account.",
				listOf("The item is tradeable", "No quest, skill or drop RNG is required"), null, null);

		SourceEntry best = null;
		SourceCategory bestCategory = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (SourceCategory category : SourceCategory.values())
		{
			List<SourceEntry> entries = value.getSources().get(category);
			if (entries == null) continue;
			for (SourceEntry entry : entries)
			{
				if (!entryAvailable(entry) || !candidateAllowed(category, entry)
					|| (category == SourceCategory.MONSTER_DROPS && !recommendationsMet(entry))) continue;
				double score = sourceScore(category, entry);
				if (score > bestScore) { bestScore = score; best = entry; bestCategory = category; }
			}
		}
		if (best == null)
			return new Recommendation("No suitable source yet",
				"The currently known sources are locked or do not fit this account.",
				listOf("Unlock a red requirement or review the Wiki for alternatives"), null, null);

		List<String> reasons = new ArrayList<>();
		if (!best.getRequiredQuests().isEmpty()) reasons.add("Required quest is complete");
		if (!best.getRequiredLevels().isEmpty()) reasons.add("All published skill requirements are met");
		if (best.getRequiredQuestPoints() > 0) reasons.add("Quest-point requirement is met");
		String summary;
		switch (bestCategory)
		{
			case SHOPS:
				summary = "Direct purchase without drop RNG.";
				reasons.add(best.getDetail());
				if (shopPrice(best) > 0) reasons.add("Shop cost was weighed against free and RNG-based alternatives");
				break;
			case GROUND_SPAWNS:
				summary = "No coins or drop luck required.";
				reasons.add("Known account requirements are met");
				break;
			case CRAFTING_AND_SKILLS:
				summary = "Your account can create this item.";
				reasons.add(best.getDetail());
				break;
			case MONSTER_DROPS:
				summary = "Best suitable monster source found for this account.";
				reasons.add("Your combat stats meet the Wiki recommendations");
				double kills = expectedKills(best);
				if (kills > 0)
				{
					reasons.add("Average: about " + QuantityFormatter.formatNumber((long) Math.ceil(kills)) + " kills; RNG is not guaranteed");
					reasons.add("About 50% chance by " + QuantityFormatter.formatNumber(attemptsForChance(kills, 0.50))
						+ " kills and 90% by " + QuantityFormatter.formatNumber(attemptsForChance(kills, 0.90)));
				}
				if (best.hasWildernessRisk()) reasons.add("Wilderness/PvP risk lowered this source's ranking");
				break;
			default:
				summary = "Known account requirements are met.";
				reasons.add(best.getDetail());
		}
		return new Recommendation(best.getTitle(), summary, reasons, bestCategory, best);
	}

	private boolean candidateAllowed(SourceCategory category, SourceEntry entry)
	{
		if (config.excludeWilderness() && entry.hasWildernessRisk()) return false;
		double price = category == SourceCategory.SHOPS ? shopPrice(entry) : -1;
		return config.maxShopBudget() <= 0 || price <= 0 || price <= config.maxShopBudget();
	}

	private double sourceScore(SourceCategory category, SourceEntry entry)
	{
		double score;
		switch (category)
		{
			case SHOPS:
				double price = shopPrice(entry);
				score = 1050 - (price > 0 ? Math.min(300, price / 1000d) : 100);
				break;
			case GROUND_SPAWNS: score = 1000; break;
			case CRAFTING_AND_SKILLS: score = 850; break;
			case MONSTER_DROPS:
				double kills = expectedKills(entry);
				score = 700 - (kills > 0 ? Math.min(300, kills / 5d) : 200)
					- (entry.hasWildernessRisk() ? 180 : 0);
				break;
			case REWARDS: score = 550; break;
			default: score = 0;
		}
		switch (config.rankingPreference())
		{
			case FASTEST:
				if (category == SourceCategory.SHOPS) score += 250;
				if (category == SourceCategory.GROUND_SPAWNS) score += 120;
				break;
			case CHEAPEST:
				if (category == SourceCategory.GROUND_SPAWNS) score += 300;
				if (category == SourceCategory.CRAFTING_AND_SKILLS) score += 180;
				if (category == SourceCategory.SHOPS) score -= Math.min(350, Math.max(0, shopPrice(entry) / 500d));
				break;
			case SAFEST:
				if (entry.hasWildernessRisk()) score -= 1000;
				if (category == SourceCategory.SHOPS || category == SourceCategory.GROUND_SPAWNS) score += 150;
				break;
			default: break;
		}
		if (!suppliesReady(category, entry)) score -= 175;
		int distance = sourceDistance(entry);
		if (distance > 0) score -= Math.min(100, distance / 10d);
		return score;
	}

	private static double shopPrice(SourceEntry entry)
	{
		String marker = "Price: ";
		int start = entry.getDetail().indexOf(marker);
		if (start < 0) return -1;
		start += marker.length();
		int end = entry.getDetail().indexOf(" gp", start);
		if (end < 0) return -1;
		try { return Double.parseDouble(entry.getDetail().substring(start, end).replace(",", "").trim()); }
		catch (NumberFormatException ignored) { return -1; }
	}

	private static double expectedKills(SourceEntry entry)
	{
		String marker = "Drop rate: 1/";
		int index = entry.getDetail().indexOf(marker);
		if (index < 0) return -1;
		String number = entry.getDetail().substring(index + marker.length()).replace(",", "").trim();
		try { return Double.parseDouble(number); }
		catch (NumberFormatException ignored) { return -1; }
	}

	private static long attemptsForChance(double oneIn, double chance)
	{
		if (oneIn <= 1) return 1;
		double probability = 1d / oneIn;
		return (long) Math.ceil(Math.log(1d - chance) / Math.log(1d - probability));
	}

	private static List<String> listOf(String... values)
	{
		List<String> result = new ArrayList<>();
		for (String value : values) result.add(value);
		return result;
	}

	private boolean hasAvailableSource(ItemSourceResult value, SourceCategory category)
	{
		List<SourceEntry> entries = value.getSources().get(category);
		if (entries == null) return false;
		for (SourceEntry entry : entries)
		{
			if (entryAvailable(entry)) return true;
		}
		return false;
	}

	private boolean hasSuitableSource(ItemSourceResult value, SourceCategory category)
	{
		List<SourceEntry> entries = value.getSources().get(category);
		if (entries == null) return false;
		for (SourceEntry entry : entries)
		{
			if (entryAvailable(entry) && recommendationsMet(entry)) return true;
		}
		return false;
	}

	private SourceEntry firstAvailableCraft(ItemSourceResult value)
	{
		List<SourceEntry> entries = value.getSources().get(SourceCategory.CRAFTING_AND_SKILLS);
		if (entries == null) return null;
		for (SourceEntry entry : entries)
		{
			if (entryAvailable(entry)) return entry;
		}
		return null;
	}

	private JPanel filterBar(ItemSourceResult value)
	{
		JPanel bar = new JPanel(new BorderLayout(7, 0));
		bar.setBackground(BACKGROUND);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		bar.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel label = new JLabel("FILTER");
		label.setForeground(MUTED);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
		bar.add(label, BorderLayout.WEST);
		JComboBox<String> filter = new JComboBox<>(new String[] {
			"All sources", "Shops", "Monster drops", "Crafting & skills", "Ground spawns", "Rewards"
		});
		filter.setSelectedIndex(activeFilter == null ? 0 : activeFilter.ordinal() + 1);
		filter.addActionListener(event ->
		{
			int index = filter.getSelectedIndex();
			activeFilter = index == 0 ? null : SourceCategory.values()[index - 1];
			renderResult(value);
		});
		bar.add(filter, BorderLayout.CENTER);
		return bar;
	}

	private JPanel itemHeader(String itemName)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(CARD_ALT);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(82, 66, 33)),
			BorderFactory.createEmptyBorder(11, 11, 11, 11)));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(38, 38));
		panel.add(icon, BorderLayout.WEST);

		JPanel text = verticalPanel(CARD_ALT);
		JLabel name = new JLabel(itemName);
		name.setForeground(Color.WHITE);
		name.setFont(name.getFont().deriveFont(Font.BOLD, 16f));
		text.add(name);

		ItemDisplayInfo info = currentItemInfo;
		if (info != null)
		{
			info.image.addTo(icon);
			long price = info.itemPrice.getWikiPrice() > 0 ? info.itemPrice.getWikiPrice() : info.itemPrice.getPrice();
			String metadata = (info.members ? "Members" : "Free-to-play")
				+ "  •  " + (info.tradeable ? "Tradeable" : "Untradeable")
				+ (price > 0 ? "  •  GE: " + QuantityFormatter.formatNumber(price) + " gp" : "");
			JLabel meta = new JLabel(metadata);
			meta.setForeground(MUTED);
			meta.setFont(meta.getFont().deriveFont(10f));
			text.add(Box.createVerticalStrut(3));
			text.add(meta);
		}
		panel.add(text, BorderLayout.CENTER);

		JButton favorite = new JButton(favoriteItems.contains(itemName) ? "★" : "☆");
		favorite.setToolTipText("Add or remove favorite");
		favorite.setForeground(GOLD);
		favorite.setBackground(CARD_ALT);
		favorite.setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 0));
		favorite.setFocusPainted(false);
		favorite.setFont(favorite.getFont().deriveFont(19f));
		favorite.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		favorite.addActionListener(event ->
		{
			toggleFavorite(itemName);
			favorite.setText(favoriteItems.contains(itemName) ? "★" : "☆");
		});
		panel.add(favorite, BorderLayout.EAST);
		return panel;
	}

	private ItemPrice exactItem(String itemName)
	{
		List<ItemPrice> matches = itemManager.search(itemName);
		if (matches == null) return null;
		for (ItemPrice item : matches)
			if (itemName.equalsIgnoreCase(item.getName())) return item;
		return null;
	}

	private static final class ItemDisplayInfo
	{
		private final ItemPrice itemPrice;
		private final boolean members;
		private final boolean tradeable;
		private final AsyncBufferedImage image;

		private ItemDisplayInfo(ItemPrice itemPrice, boolean members, boolean tradeable, AsyncBufferedImage image)
		{
			this.itemPrice = itemPrice;
			this.members = members;
			this.tradeable = tradeable;
			this.image = image;
		}
	}

	private static final class AccountInfo
	{
		private final AccountMode accountMode;
		private final Map<Skill, Integer> levels;
		private final Map<String, QuestState> questStates;
		private final int questPoints;
		private final String slayerTask;
		private final Map<String, Integer> carriedItems;
		private final Map<String, Integer> bankItems;
		private final boolean bankKnown;
		private final WorldPoint location;

		private AccountInfo(AccountMode accountMode, Map<Skill, Integer> levels,
			Map<String, QuestState> questStates, int questPoints, String slayerTask,
			Map<String, Integer> carriedItems, Map<String, Integer> bankItems, boolean bankKnown,
			WorldPoint location)
		{
			this.accountMode = accountMode;
			this.levels = levels;
			this.questStates = questStates;
			this.questPoints = questPoints;
			this.slayerTask = slayerTask;
			this.carriedItems = carriedItems;
			this.bankItems = bankItems;
			this.bankKnown = bankKnown;
			this.location = location;
		}
	}

	private static final class Recommendation
	{
		private final String title;
		private final String summary;
		private final List<String> reasons;
		private final SourceCategory category;
		private final SourceEntry entry;

		private Recommendation(String title, String summary, List<String> reasons,
			SourceCategory category, SourceEntry entry)
		{
			this.title = title;
			this.summary = summary;
			this.reasons = reasons;
			this.category = category;
			this.entry = entry;
		}
	}

	private static final class Candidate
	{
		private final SourceCategory category;
		private final SourceEntry entry;
		private final double score;

		private Candidate(SourceCategory category, SourceEntry entry, double score)
		{
			this.category = category;
			this.entry = entry;
			this.score = score;
		}
	}

	private enum AccountMode
	{
		STANDARD(0, "Standard account", false),
		IRONMAN(1, "Ironman", true),
		ULTIMATE_IRONMAN(2, "Ultimate ironman", true),
		HARDCORE_IRONMAN(3, "Hardcore ironman", true),
		GROUP_IRONMAN(4, "Group ironman", true),
		HARDCORE_GROUP_IRONMAN(5, "Hardcore group ironman", true);

		private final int value;
		private final String displayName;
		private final boolean ironman;

		AccountMode(int value, String displayName, boolean ironman)
		{
			this.value = value;
			this.displayName = displayName;
			this.ironman = ironman;
		}

		private static AccountMode fromValue(int value)
		{
			for (AccountMode mode : values())
			{
				if (mode.value == value) return mode;
			}
			return STANDARD;
		}

		private String getDisplayName()
		{
			return displayName;
		}

		private boolean isIronman()
		{
			return ironman;
		}
	}

	private JPanel categoryCard(SourceCategory category, List<SourceEntry> entries)
	{
		JPanel card = verticalPanel(CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, GOLD),
			BorderFactory.createEmptyBorder(6, 8, 8, 8)));
		JButton heading = new JButton("▼  " + category.getDisplayName().toUpperCase() + "  (" + entries.size() + ")");
		heading.setForeground(GOLD);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 12f));
		heading.setBackground(CARD);
		heading.setBorder(BorderFactory.createEmptyBorder(5, 0, 7, 0));
		heading.setFocusPainted(false);
		heading.setHorizontalAlignment(SwingConstants.LEFT);
		heading.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(heading);

		JPanel body = verticalPanel(CARD);
		body.setAlignmentX(Component.LEFT_ALIGNMENT);
		List<JPanel> extraRows = new ArrayList<>();
		for (int index = 0; index < entries.size(); index++)
		{
			SourceEntry entry = entries.get(index);
			JPanel rowPanel = verticalPanel(CARD);
			String availability = availability(category, entry);
			JLabel row = new JLabel(html("<b style='color:#f0f0f0'>" + escape(entry.getTitle())
				+ "</b><br><span style='color:#aeb3b8'>" + escape(entry.getDetail()) + "</span>"
				+ (availability.isEmpty() ? "" : "<br>" + availability)));
			row.setForeground(TEXT);
			row.setBorder(BorderFactory.createEmptyBorder(7, 1, 8, 1));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			rowPanel.add(row);
			if (index < entries.size() - 1)
			{
				JPanel line = new JPanel();
				line.setBackground(DIVIDER);
				line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
				line.setAlignmentX(Component.LEFT_ALIGNMENT);
				rowPanel.add(line);
			}
			if (index >= 5) { rowPanel.setVisible(false); extraRows.add(rowPanel); }
			body.add(rowPanel);
		}

		if (!extraRows.isEmpty())
		{
			JButton more = new JButton("Show " + extraRows.size() + " more");
			more.setForeground(GOLD);
			more.setBackground(CARD_ALT);
			more.setFocusPainted(false);
			more.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			more.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
			more.setAlignmentX(Component.LEFT_ALIGNMENT);
			more.addActionListener(event ->
			{
				boolean show = !extraRows.get(0).isVisible();
				extraRows.forEach(row -> row.setVisible(show));
				more.setText(show ? "Show fewer" : "Show " + extraRows.size() + " more");
				refresh();
			});
			body.add(Box.createVerticalStrut(4));
			body.add(more);
		}
		card.add(body);
		heading.addActionListener(event ->
		{
			body.setVisible(!body.isVisible());
			heading.setText((body.isVisible() ? "▼  " : "▶  ")
				+ category.getDisplayName().toUpperCase() + "  (" + entries.size() + ")");
			refresh();
		});
		return card;
	}

	private String availability(SourceCategory category, SourceEntry entry)
	{
		List<String> messages = new ArrayList<>();
		if (currentRecommendation != null && currentRecommendation.category == category
			&& currentRecommendation.entry == entry)
		{
			messages.add("<span style='color:#6fcf86'><b>★ BEST CHOICE FOR YOUR ACCOUNT</b></span>");
		}
		else if (!entry.isRequirementDataChecked())
		{
			messages.add("<span style='color:#aeb3b8'><b>● UNKNOWN — requirements not verified</b></span>");
		}
		else if (!hardRequirementsMet(entry))
		{
			messages.add("<span style='color:#e27a73'><b>● LOCKED</b></span>");
		}
		else if (!recommendationsMet(entry))
		{
			messages.add("<span style='color:#e0a84f'><b>● NOT RECOMMENDED</b></span>");
		}
		else
		{
			messages.add("<span style='color:#6fcf86'><b>● AVAILABLE</b></span>");
		}
		if (entry.getAvailabilityIssue() != null)
		{
			messages.add("<span style='color:#e27a73'>● Unavailable — "
				+ escape(entry.getAvailabilityIssue()) + "</span>");
		}
		for (Map.Entry<String, Integer> requirement : entry.getRequiredLevels().entrySet())
		{
			if (currentAccountInfo != null)
			{
				String skillName = requirement.getKey();
				int required = requirement.getValue();
				int actual = actualRequirementLevel(skillName);
				if (meetsRequirement(skillName, required))
					messages.add("<span style='color:#6fcf86'>● Skill ready — " + actual + "/" + required + " " + escape(skillName) + "</span>");
				else
					messages.add("<span style='color:#e27a73'>● Locked — " + actual + "/" + required + " " + escape(skillName) + "</span>");
			}
		}
		if (entry.getRequiredQuestPoints() > 0 && currentAccountInfo != null)
		{
			boolean ready = currentAccountInfo.questPoints >= entry.getRequiredQuestPoints();
			messages.add("<span style='color:" + (ready ? "#6fcf86'>● Quest points ready — " : "#e27a73'>● Locked — ")
				+ currentAccountInfo.questPoints + "/" + entry.getRequiredQuestPoints() + " quest points</span>");
		}
		if (!entry.getRequiredQuests().isEmpty() && currentAccountInfo != null)
		{
			List<String> unfinished = unfinishedQuests(entry);
			if (unfinished.isEmpty())
				messages.add("<span style='color:#6fcf86'>● Quest complete — "
					+ escape(String.join(", ", entry.getRequiredQuests())) + "</span>");
			else
				messages.add("<span style='color:#e27a73'>● Locked — finish "
					+ escape(String.join(", ", unfinished)) + "</span>");
		}
		if (entry.getRequiredSlayerTask() != null && currentAccountInfo != null)
		{
			if (hasRequiredSlayerTask(entry))
				messages.add("<span style='color:#6fcf86'>● Slayer task ready — "
					+ escape(entry.getRequiredSlayerTask()) + "</span>");
			else
				messages.add("<span style='color:#e27a73'>● Locked — requires "
					+ escape(entry.getRequiredSlayerTask()) + " Slayer task</span>");
		}
		if (entryAvailable(entry) && !entry.getRecommendedLevels().isEmpty()
			&& currentAccountInfo != null)
		{
			List<String> missing = missingRecommendations(entry);
			if (missing.isEmpty())
				messages.add("<span style='color:#6fcf86'>● Recommended for your combat stats</span>");
			else
				messages.add("<span style='color:#e0a84f'>● Not recommended — "
					+ escape(String.join(", ", missing)) + "</span>");
		}
		if (entryAvailable(entry) && entry.hasWildernessRisk())
		{
			messages.add("<span style='color:#e0a84f'>● High risk — Wilderness/PvP area</span>");
		}
		int distance = sourceDistance(entry);
		if (entryAvailable(entry) && distance > 0)
			messages.add("<span style='color:#aeb3b8'>● About " + distance
				+ " tiles straight-line; obstacles and teleports excluded</span>");
		messages.addAll(supplyMessages(category, entry));
		return String.join("<br>", messages);
	}

	private List<String> supplyMessages(SourceCategory category, SourceEntry entry)
	{
		List<String> messages = new ArrayList<>();
		if (currentAccountInfo == null || !entryAvailable(entry)) return messages;
		if (category == SourceCategory.SHOPS && shopPrice(entry) > 0)
		{
			long needed = (long) shopPrice(entry);
			long have = itemCount("Coins");
			messages.add(supplyLine("Coins", have, needed));
		}
		Matcher material = MATERIAL.matcher(entry.getDetail());
		if (material.find())
		{
			long needed = Long.parseLong(material.group(1).replace(",", ""));
			String name = material.group(2).trim();
			messages.add(supplyLine(name, itemCount(name), needed));
		}
		return messages;
	}

	private String supplyLine(String name, long have, long needed)
	{
		String source = currentAccountInfo.bankKnown ? "inventory/equipment/bank" : "carried; bank not checked";
		String color = have >= needed ? "#6fcf86" : "#e0a84f";
		String prefix = have >= needed ? "Ready" : "Need " + QuantityFormatter.formatNumber(needed - have) + " more";
		return "<span style='color:" + color + "'>● " + prefix + " — " + escape(name) + " "
			+ QuantityFormatter.formatNumber(have) + "/" + QuantityFormatter.formatNumber(needed)
			+ " (" + source + ")</span>";
	}

	private long itemCount(String name)
	{
		if (currentAccountInfo == null) return 0;
		String key = name.toLowerCase(Locale.ENGLISH);
		return (long) currentAccountInfo.carriedItems.getOrDefault(key, 0)
			+ currentAccountInfo.bankItems.getOrDefault(key, 0);
	}

	private boolean suppliesReady(SourceCategory category, SourceEntry entry)
	{
		if (currentAccountInfo == null) return true;
		if (category == SourceCategory.SHOPS && shopPrice(entry) > itemCount("Coins")) return false;
		Matcher material = MATERIAL.matcher(entry.getDetail());
		if (!material.find()) return true;
		long needed = Long.parseLong(material.group(1).replace(",", ""));
		return itemCount(material.group(2).trim()) >= needed;
	}

	private int sourceDistance(SourceEntry entry)
	{
		if (currentAccountInfo == null || currentAccountInfo.location == null
			|| entry.getMapX() == null || entry.getMapY() == null) return -1;
		int distance = Math.max(Math.abs(currentAccountInfo.location.getX() - entry.getMapX()),
			Math.abs(currentAccountInfo.location.getY() - entry.getMapY()));
		return distance > 2000 ? -1 : distance;
	}

	private List<String> unfinishedQuests(SourceEntry entry)
	{
		List<String> unfinished = new ArrayList<>();
		if (currentAccountInfo == null) return unfinished;
		for (String quest : entry.getRequiredQuests())
		{
			if (currentAccountInfo.questStates.get(quest) != QuestState.FINISHED) unfinished.add(quest);
		}
		return unfinished;
	}

	private boolean questsComplete(SourceEntry entry)
	{
		return entry.getRequiredQuests().isEmpty() || unfinishedQuests(entry).isEmpty();
	}

	private boolean hasRequiredSlayerTask(SourceEntry entry)
	{
		if (entry.getRequiredSlayerTask() == null) return true;
		if (currentAccountInfo == null || currentAccountInfo.slayerTask == null) return false;
		String current = normalizeRequirement(currentAccountInfo.slayerTask);
		String required = normalizeRequirement(entry.getRequiredSlayerTask());
		if (current.isEmpty() || required.isEmpty()) return false;
		return current.contains(required) || required.contains(current);
	}

	private boolean entryAvailable(SourceEntry entry)
	{
		return entry.isRequirementDataChecked() && hardRequirementsMet(entry);
	}

	private boolean hardRequirementsMet(SourceEntry entry)
	{
		return entry.getAvailabilityIssue() == null && meetsRequirement(entry)
			&& questsComplete(entry) && hasRequiredSlayerTask(entry);
	}

	private boolean recommendationsMet(SourceEntry entry)
	{
		return missingRecommendations(entry).isEmpty();
	}

	private List<String> missingRecommendations(SourceEntry entry)
	{
		List<String> missing = new ArrayList<>();
		if (currentAccountInfo == null) return missing;
		entry.getRecommendedLevels().forEach((skillName, recommended) ->
		{
			Skill skill = skillFor(skillName);
			int actual = skill == null ? 0 : currentAccountInfo.levels.getOrDefault(skill, 0);
			if (actual < recommended) missing.add(skillName + " " + actual + "/" + recommended);
		});
		return missing;
	}

	private static String normalizeRequirement(String value)
	{
		return value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "").replaceAll("s$", "");
	}

	private boolean meetsRequirement(SourceEntry entry)
	{
		if (currentAccountInfo == null)
			return entry.getRequiredLevels().isEmpty() && entry.getRequiredQuestPoints() <= 0;
		if (currentAccountInfo.questPoints < entry.getRequiredQuestPoints()) return false;
		for (Map.Entry<String, Integer> requirement : entry.getRequiredLevels().entrySet())
			if (!meetsRequirement(requirement.getKey(), requirement.getValue())) return false;
		return true;
	}

	private boolean meetsRequirement(String skillName, int requiredLevel)
	{
		if ("Attack + Strength".equalsIgnoreCase(skillName))
		{
			int attack = currentAccountInfo.levels.getOrDefault(Skill.ATTACK, 0);
			int strength = currentAccountInfo.levels.getOrDefault(Skill.STRENGTH, 0);
			return attack >= 99 || strength >= 99 || attack + strength >= requiredLevel;
		}
		Skill skill = skillFor(skillName);
		return skill != null && currentAccountInfo.levels.getOrDefault(skill, 0) >= requiredLevel;
	}

	private int actualRequirementLevel(String skillName)
	{
		if (currentAccountInfo == null) return 0;
		if ("Attack + Strength".equalsIgnoreCase(skillName))
			return currentAccountInfo.levels.getOrDefault(Skill.ATTACK, 0)
				+ currentAccountInfo.levels.getOrDefault(Skill.STRENGTH, 0);
		Skill skill = skillFor(skillName);
		return skill == null ? 0 : currentAccountInfo.levels.getOrDefault(skill, 0);
	}

	private static Skill skillFor(String name)
	{
		if (name == null) return null;
		for (Skill skill : Skill.values()) if (skill.getName().equalsIgnoreCase(name)) return skill;
		if ("Runecrafting".equalsIgnoreCase(name)) return Skill.RUNECRAFT;
		return null;
	}

	private static Quest questByName(String name)
	{
		for (Quest quest : Quest.values())
		{
			if (quest.getName().equalsIgnoreCase(name)) return quest;
		}
		return null;
	}

	private JPanel emptySpawnCard()
	{
		JPanel card = verticalPanel(CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(92, 97, 102)),
			BorderFactory.createEmptyBorder(9, 9, 9, 8)));
		JLabel heading = new JLabel("GROUND SPAWNS");
		heading.setForeground(MUTED);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 12f));
		card.add(heading);
		JLabel none = new JLabel(html("<span style='color:#989da2'>No known ground spawns for this item.</span>"));
		none.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		card.add(none);
		return card;
	}

	private void showWelcome()
	{
		results.removeAll();
		addMessage("Start typing an item name. Smart suggestions appear automatically.");
		results.add(Box.createVerticalStrut(8));
		addMessage("Try “rune sci”, “dragon scimitar”, or “clue scroll”.");
	}

	private void showError(String error)
	{
		setLoading(false);
		wikiButton.setEnabled(false);
		status.setForeground(new Color(226, 122, 115));
		status.setText("●  Search failed");
		results.removeAll();
		addMessage(error);
		refresh();
	}

	private void addMessage(String text)
	{
		JLabel label = new JLabel(html(escape(text)));
		label.setOpaque(true);
		label.setBackground(CARD);
		label.setForeground(MUTED);
		label.setBorder(BorderFactory.createEmptyBorder(11, 10, 11, 10));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		results.add(label);
	}

	private void setLoading(boolean loading)
	{
		searchButton.setEnabled(!loading);
		searchField.setEnabled(!loading);
	}

	private void refresh() { results.revalidate(); results.repaint(); }

	private static JPanel verticalPanel(Color background)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(background);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static void styleButton(JButton button, boolean primary)
	{
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
		button.setBackground(primary ? new Color(143, 101, 28) : CARD_ALT);
		button.setForeground(Color.WHITE);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(primary ? new Color(184, 132, 36) : new Color(82, 87, 93)),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));
	}

	private static void styleCompactButton(JButton button, Color foreground)
	{
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setForeground(foreground);
		button.setBackground(CARD_ALT);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(DIVIDER),
			BorderFactory.createEmptyBorder(4, 7, 4, 7)));
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String html(String body)
	{
		return "<html><div style='width:158px; line-height:1.2'>" + body + "</div></html>";
	}

	private static final class WidthTrackingPanel extends JPanel implements Scrollable
	{
		@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
		@Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(16, visible.height - 16); }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return false; }
	}
}
