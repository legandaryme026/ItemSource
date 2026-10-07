# ItemSource

ItemSource is a standalone RuneLite sidebar plugin that finds ways to obtain an
Old School RuneScape item. Search results from the public OSRS Wiki are grouped
into shops, monster drops, crafting/skills, ground spawns, and rewards. The
**Open Wiki** button opens the complete matched page.

The MVP also includes live item suggestions, favorites, recent searches,
source filters, item icons and market metadata, collapsible result sections,
an account-aware recommendation card, and a compact comparison of the three
best suitable methods. While logged in, the card recognizes standard and
ironman account modes and compares every listed skill requirement, quest-point
gate, and quest requirement with live account state. ItemSource also reads the linked Wiki pages
for individual shops, monsters, spawns, and rewards. Published skill, quest,
stock-state, and mandatory Slayer-task requirements are checked against live
RuneLite account state and shown as met or locked. If a source page cannot be
verified, the UI says so instead of guessing.

For monster sources, ItemSource also reads the Wiki strategy page's suggested
combat levels. A monster can therefore be accessible but still show a precise
account-specific warning such as `Not recommended — Magic 63/70`.

The recommendation card ranks concrete sources for the active account. It
prefers verified direct methods over RNG-heavy methods, excludes locked and
combat-unsuitable sources, marks the selected row as the best choice, and
offers an expandable explanation of the factors used. Drop recommendations
show the average kills implied by the published rate and explicitly note that
RNG is not guaranteed.

Shop prices now influence the ranking, so a free verified ground spawn can beat
an expensive purchase. Sources in a published Wilderness location are clearly
marked as high-risk and receive a PvP-risk penalty in the ranking. The settings
let users rank methods as balanced, fastest, cheapest, or safest; exclude
Wilderness sources; set a maximum shop budget; and hide locked sources.

Available inventory, equipment, and—when RuneLite currently has it—bank data
are checked for listed materials and shop coins. If bank data is unavailable,
ItemSource says that it was not checked rather than claiming an item is absent.
This account snapshot stays inside RuneLite and is never submitted with Wiki
requests.

Access checks follow a source's linked location page once. This catches area
and guild gates that are not repeated on the shop or monster page, such as
Prifddinas requiring Song of the Elves and Myths' Guild requiring Dragon
Slayer II. Multiple published skill gates are combined. If a linked page cannot
be verified, the source remains unknown and is excluded from recommendations
instead of being optimistically marked available.

Variant labels in drop tables use their canonical Wiki page for verification
(`Jelly Regular` resolves to `Jelly`, for example). Clue reward tiers are shown
with complete names such as `Hard clue scroll`.

Every source row begins with one consistent status: best choice, available,
not recommended, locked, or unknown. The selected drop explanation includes
the average attempts plus the approximate 50% and 90% chance points.

Where a Wiki infobox publishes map coordinates, ItemSource shows a rough
straight-line tile distance from the character and uses it as a small ranking
factor. It deliberately does not claim to calculate a walking route: obstacles,
transport, teleports, planes, and instances are not included.

A user can manually track the selected method. Monster-drop tracking offers a
plain `+1` attempt counter, reset, and stop controls. It never watches combat,
clicks, types, or sends gameplay actions. Searches are cached in memory for ten
minutes to make repeat lookups quicker without creating local data files.

The requirement regression suite covers quest-gated cities and shops, Slayer
levels/tasks, Wilderness strategy advice, canonical monster variants, clue
tiers, crafting, spawns, linked dungeon access, and the Warriors' Guild's
combined Attack/Strength gate.

It uses RuneLite's injected `OkHttpClient` and `Gson`. There are no credentials
or secrets, and no player or account data is sent. A search necessarily exposes
the user's IP address and entered item text to `oldschool.runescape.wiki`; this
is disclosed in `runelite-plugin.properties`.

## Build and run

Requirements: JDK 11 or newer and internet access.

```powershell
.\build-local.bat clean build
.\build-local.bat run
```

The batch helper works without changing PowerShell's execution policy and keeps
Gradle's cache inside this project directory. The second command starts a
RuneLite development client. Enable **ItemSource**,
open its sidebar panel, and try `Rune scimitar`. For Jagex account login, follow
RuneLite's official [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
instructions.

## Structure

Production code is under `src/main/java/com/itemsource`; parser tests and the
development launcher are under `src/test/java/com/itemsource`. The root
`icon.png` is the Plugin Hub icon (48x48), while
`src/main/resources/icon.png` is the identical classpath copy used by the
RuneLite sidebar.

## Created desktop directories

The only project root created is:

`C:\Users\Gebruiker\Desktop\ItemSource`

Gradle creates normal generated output only beneath it, including
`C:\Users\Gebruiker\Desktop\ItemSource\build`,
`C:\Users\Gebruiker\Desktop\ItemSource\.gradle`, and (when using the included
PowerShell helper) `C:\Users\Gebruiker\Desktop\ItemSource\.gradle-user-home`.

ItemSource is independent from RuneRadar and does not read, write, or depend on
RuneRadar files.

During automated verification in the Codex Windows environment only, the
support directory `C:\Users\Gebruiker\Desktop\I` was also created as a short
temporary path to work around a Java loopback-path limitation. It contains only
temporary Java process metadata, no ItemSource source/build data, and is not
part of the plugin repository.

For the Plugin Hub submission, the separate fork checkout
`C:\Users\Gebruiker\Desktop\ItemSource-PluginHub` was created. It contains only
the Plugin Hub repository and the `plugins/itemsource` metadata marker; the
ItemSource source code remains exclusively in the ItemSource repository.

## Plugin Hub readiness

The implementation targets Java 11, uses injected RuneLite networking/JSON
services, performs no game input or automation, and contains no process
execution, reflection, hardcoded secrets, or direct file access. Favorites,
recent searches, preferences, and the optional manual tracker use RuneLite's
`ConfigManager`; account information and the short-lived search cache stay in
memory and are never sent over the network. The repository icon is a genuine
optimized transparent PNG within RuneLite's published 48x72 maximum. Before a
future Plugin Hub
submission, copy the network warning into the Plugin Hub marker entry and set
that entry to the exact ItemSource repository URL and exact plugin commit hash.
Update the hash after every plugin commit.

## License

BSD 2-Clause License. See [LICENSE](LICENSE).
