# HackWars — Game Design Document

*A hacking MMO — your rig, your breach, your call.*

## 0. How to read this document

This is a **forward-looking design spec**: it describes the game as designed. One marker flags where it's worth slowing down:

> **Design Gap:** a place where the design is genuinely unfinished — what's missing or underspecified, and why it needs real work before this could ship.

---

## 1. Vision & Pillars

HackWars is an MMO where **hacking is a Kit you play live**. Players equip hardware and install Programs that each grant a handful of abilities, then play those abilities live, one step at a time, against a target's firewall — to attack, defend, and earn a living on a simulated internet shared with thousands of other players. Every other player is a standing target: a live Computer running live services that can be found, hacked, and drained, and who can just as easily hack you back while you're offline.

**Pillars:**

1. **Your rig is your arsenal.** Every offensive and defensive capability comes from what's equipped (hardware, and the unique ability each hardware *type* carries) and what's installed (Programs, bought or crafted). Skill expression is arsenal-building — what you bring — plus live tactical play — how you spend it.
2. **Everyone is a server.** There is no NPC-only "safe" version of the world. Player computers are first-class citizens of the same simulated internet as NPCs and quest-givers — encountered live on the Network Map (§17) or reached through an already-built Trace (§7), reachable through the same browser once you're there, attacked with the same tools.
3. **Asynchronous, persistent stakes.** Attacks unfold over minutes against a target who may not be online, and a weakened, undefended computer is a standing liability. Defense has to work without you present — so while an attacker plays their Kit live, a defender's installed Programs answer through a pre-configured Loadout instead. The tension is closer to a persistent base-defense game than to twitch PvP.
4. **The economy is the endgame.** Money, mined resources, and traded Programs/firewalls form a real player-driven economy; the marketplace and the black-market use of stolen petty cash are as central as combat. Player-owned Subnets (§3) are its capstone — infrastructure built and upgraded once that keeps paying out passively long after.

---

## 2. Setting & Tone

The world is a simulated internet under corporate control. A handful of megacorps — **Meridian** foremost among them, already the name behind the Corporate Backbone Network (§3) — own the infrastructure every Network runs on, and the central conflict is what a player does about that: work the system from inside, or work against it from **the Undernet**, the hacker underground that already gives its name to the game's own endgame zone (§3). Sites still carry the fictional `.hw` TLD, and the writing keeps its irreverent hacker-forum voice circa mid-2000s — self-aware, a little juvenile, unafraid of in-jokes (domains like `TheCakeIsaLie.hw` and a spoofed `google.hw`). That voice doesn't get replaced by the dystopia, it sits inside it: a world can be genuinely bleak about who owns the wires and still crack jokes about it from underneath — that's cyberpunk's whole register, not a contradiction. Quests, standing, and reward tracks all key off Meridian and the Undernet as the two named factions (§14).

---

## 3. World Structure

The world is organized into **Networks** — named zones, each with its own resident roster, shop, and (optionally) an `attack_probability` governing ambient danger. Networks connect to each other through **attached network** links, each with its own arrival flavor text, so moving between zones is a discrete, narrated transition rather than open-world travel.

Baseline networks:

| Network | Role |
|---|---|
| `UGOPNet` | The root/hub network. Home to the overwhelming majority of NPCs, the general store, and new-player content. |
| `RelayNet` | A secondary connected zone (linked bidirectionally to UGOPNet). |
| `ProgNet` | A third zone, thematically aligned with the programming-challenge track. |
| `JuniperPenitentiary` | A restricted/punitive network — reserved for consequence-driven content (e.g., where caught cheaters or story-flagged players are routed). |

> **Design Gap:** RelayNet, ProgNet, and JuniperPenitentiary don't yet have any authored Static Subnet content — no fixed roster, no hand-placed nodes, nothing beyond a name and a role. Random Subnets fill each Network with procedurally generated content automatically, so this doesn't block launch, but every Network still needs at least one real, hand-authored Static Subnet before it feels like a finished place rather than a label on the map.

### Subnets — the explorable layer

Every Network contains **Subnets** — the actual space a player explores, distinct from the Network itself, which stays the map-level container that gates attacks and travel (§7's "share the same Network" rule governs Networks, not Subnets). There are three kinds, and subnets can nest: exploring one can surface an entry point to another, one level deeper.

- **Static Subnets** — fixed, hand-authored, always visible on every player's Network map (some further gated by a dedicated quest chain, e.g. the already-established Meridian Corporate Backbone, Plutonium Corridor, and The Undernet). Layout and resident roster (Mining/Attack/Quest NPCs, §13) don't change between visits — a static subnet is a place, not an event.

> **Design Gap:** the actual quest chains gating Meridian Corporate Backbone, Plutonium Corridor, and The Undernet aren't written yet — this section only establishes that access is quest-gated rather than skill-gated. The same approach should extend to every additional Network as the world grows, but each one needs its own real chain designed.
- **Quest Subnets** — instanced story content tied to §14's quest system, also always visible once unlocked by its quest, and exempt from the occupancy-scan risk below.
- **Random Subnets** — procedurally generated per connection: layout, node population, and loot regenerate fresh rather than being handcrafted once. Unlike the other two, visibility is neither fixed nor shared — see below.

**Random Subnets reveal probabilistically, and privately.** A Network scan doesn't unlock a Random Subnet outright; it rolls independently against each currently-unrevealed one in the Network's live pool, with odds that improve the more Enumeration-focused a player's build is. Because that roll runs separately for every player, two players scanning the same Network at the same moment won't necessarily see the same Random Subnets appear — seeing one on your map is no guarantee anyone else can, though another player's own roll can independently hit that same subnet, which is exactly what makes a Random Subnet real PvP risk and not a private instance. A looted Route Fragment (§13) skips the roll entirely, guaranteeing a reveal.

**Revealed-subnet capacity is a hard cap**, not an unlimited list: **3 base + ⌊Effective Memory Tier ÷ 2⌋ (up to +4 at Tier 8)** — 3 to 7 Random Subnets held at once, hardware mattering the way it does everywhere else in this design (§12). Once full, a scan can't add a new one until an existing entry expires or its interior is fully looted out and drops off the map, freeing a slot. Static and Quest Subnets don't count against this cap — it governs Random Subnet discovery specifically.

> **Design Gap:** this cap used to also scale with Enumeration skill levels (+1 per 20 levels, up to +5 at level 100); that bonus needs a new, non-skill source now that Enumeration isn't a leveled skill — most likely a piece of gear.

**Supply always exceeds demand.** Each Network's live Random Subnet pool is kept above its current online population — a standing server invariant, not a one-time seed — so there are always more Random Subnets available than there are players to occupy them. This doesn't eliminate contested subnets (reveal is still probabilistic and shared, above), it just guarantees scarcity is never the reason two players collide in the same one.

**Nesting.** Any subnet's interior can contain an entry point one level deeper — most commonly a Random Subnet surfacing another, its Danger Tier (§13) nudged up and capped at 6, turning "how deep do I push" into a real risk/reward call. Diving into a nested entry found this way is immediate and direct — it doesn't touch the revealed-subnet cap above, which governs only what a Network scan puts on the map, not what a player stumbles into once already inside.

**Faction turf gives `attack_probability` a real job.** Meridian Corporate Backbone and The Undernet are the two factions' respective home turf — the first Static Subnet each faction actually controls. Showing up there without matching Faction Standing (§14) raises that Subnet's `attack_probability`, the field named at the top of this section but otherwise unused until now: low or hostile standing means hotter, more aggressive patrol activity on the other faction's ground, while high standing with the controlling faction reads as safe passage.

**Connecting carries risk.** Static and Random Subnets maintain a live **occupant list** — any other player currently connected to that same subnet can scan it to see who else is in there right now, a session-scoped discovery surface layered on top of (not replacing) the search-index discovery already covered in §16. Leaving the subnet removes you from the list — this is live presence, not a persistent flag. The noob-protection floor (§7) still applies in full: a protected player can appear on the list but can't be attacked. Quest Subnets don't maintain an occupant list at all, so a player inside one can't be found through it — though anyone already holding a standing Trace on you from a past fight can, as always, still connect to your Computer directly (§7); a Quest Subnet grants no protection against that, only exemption from being discovered through this list.

Random Subnets are populated by procedurally spawned **nodes**, not persistent named individuals — the full patrol/cache/route-item taxonomy is in §13.

### Player Subnets — the endgame layer

A fourth Subnet kind, alongside Static, Quest, and Random: **Player Subnets**, built and owned by a player — or, as a **Guild Subnet**, jointly by a guild — rather than authored or procedurally spawned. Where the other three kinds are things a player *visits*, a Player Subnet is something a player *runs*: endgame infrastructure for passive income and standing capability, set up once and then upgraded and maintained over time rather than played moment to moment.

A Player Subnet can host:

- **A website** — hosted via an **HTTP server node**, the owner's site content generating ongoing **traffic revenue** (§11) scaled by votes and any advertising spend (§16), on top of whatever presentational role a website already serves.
- **Extra file storage** — a vault distinct from the owner's own HD Storage Capacity (§12), for files that don't fit the rig they're actually carrying.
- **Linked systems** — player-configured nodes that extend the existing zombie/relay-chain framework (§7) rather than replacing it: multiple owned nodes chained together for a bigger coordinated attack, or layered as a standing defensive relay, all maintained passively rather than played live.

Player Subnets carry **default elevated scan resistance** — harder to reveal through ordinary Enumeration than a Static or Random Subnet — which is what makes them worth defending rather than just another target on the map. **Guild Subnets** use the same framework under joint rather than single ownership.

> **Design Gap:** guild governance — who can build, upgrade, or defend a Guild Subnet, and how ownership and permissions work — isn't designed yet; this section establishes that Guild Subnets exist and share the Player Subnet framework, not how a guild actually administers one. Exact numbers for scan resistance, traffic-revenue scaling, storage capacity, node-linking limits, and upgrade costs are unspecified and need a real balance pass.

---

## 4. Player Identity & the Computer

Every player's Computer carries an IP address, but that address identifies their **website** specifically — not a way to walk up and attack them directly. There is no separate "character name" layer for combat purposes either: PvP identity is still tied to your Computer, but reaching it means encountering you live on the Network Map (§3, §17) or holding a Trace already built on you from a past fight (§7), not looking up an address. This single decision is what makes the rest of the design cohere: your website stays public and searchable (§16) the same way a defaced site is public, while your Computer itself — the thing actually worth attacking — has to be found, not looked up. Being seen on a Subnet's occupant list (§3) and being held on someone's Trace (§7) are the game's two ways of being found; neither is "always on" the way a raw address lookup would be.

Each player owns a **Computer**, simulated server-side, consisting of:

- **Hardware** — CPU, hard drive, and memory, each independently craftable equipment with 8 types and 8 tiers of increasing power (the full crafted-equipment system, including exact stat formulas, is in §12). Hardware sets hard ceilings on capability: CPU governs maximum sustained load and heat tolerance, Memory governs how many Programs can be installed at once and how many rules a defensive Loadout can hold, HD governs file storage and transfer speed.
- **System** — every Computer has one attack surface, not several: **100 HP and a single firewall**, protecting everything the Computer holds. There's no separate port to pick between — breaching the system is breaching the Computer, full stop.
- **Files** — the file system holds installed Programs, firewalls, quest items, and loot, each an inspectable object with a type and (for Programs) a price if listed for sale.
- **Two-tier currency** — *Cash* (liquid, exposed — stealable as a finalize action against a weakened player or a Banking System node, §13) versus *Bitcoin* (fully safe: cannot be hacked or stolen by anyone, accessible only by the account that owns it).

A player's Computer keeps running and defending itself while they're offline — installed defense Programs and firewalls all operate autonomously. This is deliberate: idle players are not safe players, and defense is something you build once and rely on, not something you personally have to be present for.

### Dormancy & auto-shutdown

If a player is offline for **7 consecutive days**, their Computer's system is automatically switched off, using the same toggle already exposed in the client (§17); if they own a Player Subnet (§3), its hosted site goes dark too. The server just flips the same switch a player would use to go dark manually.

With the system down, the Computer can't be scanned or attacked (nothing open to hit) and any Player Subnet site the player owns drops out of the search index (§16) — the player effectively vanishes from the game world rather than sitting there as an unattended, undefended target.

Two things make this a *protection*, not a penalty:

- **The system stays off when the player returns.** Reactivating it is a deliberate, manual action, not something the game does automatically. The whole point is to prevent "logged back in after two weeks away, already hacked" — coming home should mean choosing when you're exposed again, not being exposed the instant you log in.
- **A warning lands first.** Around day 5, a notification arrives in the player's inbox — the same channel already used for attack alerts and low-balance warnings — so anyone who's simply between sessions — not actually gone — gets a heads-up before anything changes.

The 7-day clock resets on login, using the same `last_logged_in` timestamp the search engine's activity gate already relies on (§16) — one signal, used twice, not two separate systems to keep in sync.

---

## 5. Career Track

Skills are gone; a player's whole rig — every piece of equipped hardware and every installed Program alike (§8) — now carries all of a player's long-term progression. Everything in a rig quietly earns experience just from being used, split across two tracks that stack together: a **Category Track** shared by every item of that category, and an **Item Track** unique to the specific type of item equipped or installed. Leveling either one raises how strong that piece of your rig actually is — there's no separate "character level" anymore, just how much you've used what you're running.

### Category Track

Each of the twelve categories — six hardware (CPU, HD, Memory, Firewall, PCI, AGP) and six Program (Bastion, Sentry, Retaliator, Sunder, Ghostcoat, Nullstrike, below) — has its own Category Track, leveling from **1 to 100** as a player earns XP from using that category (playing a CPU-sourced ability advances CPU, absorbing a hit with an equipped Firewall advances Firewall, playing or resolving a Program's ability advances that Program's own category, and so on — each category earns XP from the kind of use that's already native to it). A Category Track's level does three things at once:

1. **Raises the maximum tier you're allowed to equip or install** in that category (see Tier Access, below) — a hard ceiling independent of whether you can afford or craft a higher tier.
2. **Unlocks category-wide abilities at fixed levels as the track climbs** — shared by every item type in that category, on top of the one signature ability that's always tied to its specific type (§12 for hardware, §8 for Programs) and never locks. See each category's own table, below, for exactly which ability unlocks at which level — full effects and Runtime Footprint costs for all of them are in §9.
3. **Adds a flat Item Power bonus to every item you own in that category** — small per level, meaningful by level 100.

### Item Track

Underneath each Category Track sit the **Item Tracks** — one for each of the named types within that category (e.g., CPU's Standard, Overclocked, Industrial, Efficiency, Distributed, Hardened, Signal, and Blackout types each level independently; every Program category below works the same way, just with six named types instead of eight). An Item Track only gains XP while that *exact* type is the one currently equipped or installed — swap to a different type in the same category and its Item Track stops earning, even though the Category Track keeps climbing regardless of which type you're running. Item Tracks also run **1 to 100**, and a level in one does two things:

1. **Adds a large Item Power bonus to that specific item type** — far bigger than anything the Category Track grants on its own.
2. **Adds a small Item Power bonus to every item in the category** — the same kind of bonus the Category Track gives, just smaller per level, and it stacks across every Item Track a player has invested in, not just one.

### Item Power math

- **Category Track:** +0.5 Item Power per level to every item in that category, capping at **+50 at level 100**.
- **Item Track:** +2 Item Power per level to that specific item type, capping at **+200 at level 100**, plus +0.1 Item Power per level to every item in the category, capping at **+10 at level 100**.

These stack. A player with a maxed Category Track and three fully-mastered Item Tracks in the same category is carrying +50 (Category) + 3×10 (three maxed Item Tracks' shared bonus) = **+80 Item Power on every single item they own in that category** — including types they've never specifically leveled — and whichever of those three mastered types they're actually wearing gets its own +200 on top of that shared +80.

### Tier access

The same schedule gates every category:

| Category Level | Max Equippable Tier |
|---|---|
| 1 | Tier 1 |
| 15 | Tier 2 |
| 30 | Tier 3 |
| 45 | Tier 4 |
| 60 | Tier 5 |
| 75 | Tier 6 |
| 90 | Tier 7 |
| 100 | Tier 8 |

Crafting a tier still costs the resources §12 already sets out — this schedule only governs whether you're *allowed* to equip one, a second gate independent of whether you can afford or gather for it.

### CPU

Trained by playing any ability sourced from an equipped CPU — baseline, signature, or category-wide.

| Level | Ability |
|---|---|
| 1 | **Initiate Breach / Read Absorption** |
| 15 | **Direct Strike** |
| 30 | **Cycle Burst** |
| 45 | **Berserk** |
| 60 | **Overclock** |
| 75 | **Ghost Protocol** |

Your equipped CPU type's own signature ability (§12) is available from the moment it's equipped and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Eight Item Tracks: Standard, Overclocked, Industrial, Efficiency, Distributed, Hardened, Signal, Blackout (§12).

### HD

Trained by playing any file-related ability sourced from an equipped HD — baseline, signature, or category-wide.

| Level | Ability |
|---|---|
| 1 | **Quick List** |
| 15 | **Archive Dive** |
| 30 | **Fragment Scan** |
| 45 | **Shadow Copy** |
| 60 | **Purge** |
| 75 | **Deep Copy** |

Your equipped HD type's own signature ability (§12) is available from the moment it's equipped and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Eight Item Tracks: Standard, Vault, Raid Array, Archive, Flash, Encrypted, Redundant, Compact (§12).

### Memory

Trained by playing any ability while running under real Concurrent Execution load — the more of your Runtime Footprint budget an equipped Memory is actually supporting, the faster it earns.

| Level | Ability |
|---|---|
| 1 | **Split Cycle** |
| 15 | **Snap Cache** |
| 30 | **Thread Lock** |
| 45 | **Buffer Flush** |
| 60 | **Deep Cache** |
| 75 | **Multi-Thread** |

Your equipped Memory type's own signature ability (§12) is available from the moment it's equipped and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Eight Item Tracks: Standard, Expanded, Sentinel Array, Parallel, Low-Latency, Dense, Buffered, Economy (§12).

### Firewall

Trained by absorbing incoming damage while equipped — the same "XP off damage absorbed" channel this design has always used for defense.

| Level | Ability |
|---|---|
| 1 | **Hold the Line** |
| 15 | **Ping Back** |
| 30 | **Countered? (self-check)** |
| 45 | **Brace** |
| 60 | **Patch Job** |
| 75 | **Second Guess** |

Your equipped Firewall type's own signature ability (§12) is available from the moment it's equipped and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Eight Item Tracks: Standard, Bunker, Reflector, Trapster, Lean, Adaptive, Honeypot-Tuned, Overbuilt (§12).

### PCI

Trained by completing utility/economy actions an equipped PCI card is actually boosting — FTP transfers, installs, storage use, Marketplace trades.

| Level | Ability |
|---|---|
| 1 | **Fast Post** |
| 15 | **Audit Trail** |
| 30 | **Route Map** |
| 45 | **Fee Waiver** |
| 60 | **Toll Skim** |
| 75 | **Rate Lock** |
| 90 | **Freight Discount** |
| 100 | **Bulk Transfer** |

Your equipped PCI type's own signature ability (§12) is available from the moment it's equipped and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Eight Item Tracks: Standard (Basic I/O), Courier, Installer, Archivist, Broker, Balanced, Overclocked I/O, Salvage (§12).

### AGP

Trained by playing combat or relay-chain actions an equipped AGP card is actually boosting.

| Level | Ability |
|---|---|
| 1 | **Short Hop** |
| 10 | **Read Manifest** |
| 20 | **Steady Signal** |
| 30 | **Disambiguate Route** |
| 40 | **Scan Shipments / Intercept Shipment** |
| 50 | **Read Convoy Manifest** |
| 60 | **Toll Booth** |
| 70 | **Reroute** |
| 80 | **Signal Boost** |
| 90 | **Detect Zombie Relay** |
| 100 | **Hard Line** |

Your equipped AGP type's own signature ability (§12) is available from the moment it's equipped and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Eight Item Tracks: Standard, Relay-Tuned, Cryo-Stable, Combat-Rated, Overhead, Balanced, Botnet, Salvage (§12).

### Program categories

Programs work exactly like hardware: instead of one flat "Program" bucket, there are **six separate Program categories**, each running its own full Category Track (§5) with six named Item Tracks underneath it — the same two-track structure CPU or Firewall uses, just for software instead of silicon. Three categories are Defense-role, three are Offense-role, and each Offense category is purpose-built to counter one specific Defense category:

| Defense category | Countered by |
|---|---|
| **Bastion** (raw-defense flavor: flat damage/Absorption boosts) | **Sunder** (ignores a portion of a target's flat damage reduction) |
| **Sentry** (detection flavor: identifies and logs attackers) | **Ghostcoat** (suppresses detection/logging for the rest of the session) |
| **Retaliator** (counter-attack flavor: reflects damage back at an attacker) | **Nullstrike** (the next hit landed can't trigger a reflect/counter effect) |

These three Defense flavors are the same raw-defense / detection / counter-attack split a rolled firewall Subroutine already uses (§7) — a Defense Program just brings that same identity to the Loadout layer as an installed, always-on source instead of a per-fight random roll, and each Offense category exists specifically to punch through one of them.

Unlike hardware, more than one Program can be installed at once, up to Memory's Program Capacity (§4) — every currently-installed Program's own Item Track earns XP simultaneously, not just whichever one happens to be "equipped."

Item Power bonuses apply the same way they do to hardware, but a Program has one ability instead of a stat block, so the bonus feeds directly into that ability's own numbers instead — more damage ignored for a Sunder, a bigger reflect for a Retaliator, and so on. The exact conversion rate isn't tuned yet (§20).

### Bastion

Trained by a Bastion Program resolving against an incoming attack while installed — the same "earns XP from absorbing" channel Firewall already uses for defense.

| Level | Ability |
|---|---|
| 1 | **Guard Up** |
| 15 | **Patch Seal** |
| 30 | **Brace** |
| 45 | **Hard Point** |
| 60 | **Long Guard** |
| 75 | **Overguard** |

Your installed Bastion type's own signature ability (§8) is available from the moment it's installed and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Six Item Tracks: Standard, Ironclad, Aegis, Bulwark, Rampart, Grid Lock (§8).

### Sentry

Trained by a Sentry Program resolving against an incoming scan or attack while installed — the same "detect and log" channel as Firewall's Subroutine detection flavor, just always-on.

| Level | Ability |
|---|---|
| 1 | **Log Entry** |
| 15 | **Ping Alert** |
| 30 | **Quiet Watch** |
| 45 | **Scan Log** |
| 60 | **Cross Check** |
| 75 | **Deep Read** |

Your installed Sentry type's own signature ability (§8) is available from the moment it's installed and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Six Item Tracks: Standard, Overwatch, Tripline, Archive Watch, Quietkeep, Mirror Log (§8).

### Retaliator

Trained by a Retaliator Program resolving against an incoming hit while installed — the same "counter-attack" channel a rolled firewall Subroutine already uses.

| Level | Ability |
|---|---|
| 1 | **Snap Back** |
| 15 | **Cheap Bounce** |
| 30 | **Slow Burn** |
| 45 | **Return Volley** |
| 60 | **Overflow** |
| 75 | **Guard Break** |

Your installed Retaliator type's own signature ability (§8) is available from the moment it's installed and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Six Item Tracks: Standard, Backlash, Spike Coil, Echo, Cutrate, Feedback Loop (§8).

### Sunder

Trained by playing a Sunder Program's ability during a live attack. Sunder is the Offense category built to counter **Bastion** (above).

| Level | Ability |
|---|---|
| 1 | **Access Push** |
| 15 | **Cheap Crack** |
| 30 | **Crack Line** |
| 45 | **Weak Point** |
| 60 | **Split Guard** |
| 75 | **Overpressure** |

Your installed Sunder type's own signature ability (§8) is available from the moment it's installed and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Six Item Tracks: Standard, Sledgehammer, Wear Point, Chainbreaker, Crude Edge, Overrun (§8).

### Ghostcoat

Trained by playing a Ghostcoat Program's ability during a live attack. Ghostcoat is the Offense category built to counter **Sentry** (above).

| Level | Ability |
|---|---|
| 1 | **Trace Scrub** |
| 15 | **Cheap Cover** |
| 30 | **Quiet Entry** |
| 45 | **Blend In** |
| 60 | **Static Veil** |
| 75 | **Double Blind** |

Your installed Ghostcoat type's own signature ability (§8) is available from the moment it's installed and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Six Item Tracks: Standard, Blackout, Fade Step, Long Shadow, Dim Signal, Mirror Break (§8).

### Nullstrike

Trained by playing a Nullstrike Program's ability during a live attack. Nullstrike is the Offense category built to counter **Retaliator** (above).

| Level | Ability |
|---|---|
| 1 | **Exploit Chain** |
| 15 | **Cheap Snap** |
| 30 | **Steady Hand** |
| 45 | **Guard Snap** |
| 60 | **Cut Line** |
| 75 | **Overwatch Null** |

Your installed Nullstrike type's own signature ability (§8) is available from the moment it's installed and never locks. Full effects and Runtime Footprint costs for everything above are in §9.

Six Item Tracks: Standard, Deadbolt, Absorb Return, Chain Null, Half Null, Quiet Strike (§8).

---

## 6. Core Gameplay Loop

1. **Discover** — encounter a target live on the Network Map (§17): a Static or Random Subnet's occupant list (§3), a standing Trace already built from a past fight (§7), or — for a player-owned subnet — a successful Locate off a website found through search or voting (§16).
2. **Reconnoiter** — once connected, run a system scan to see what firewall tier is guarding it.
3. **Prepare** — install Programs suited to the target's defenses; make sure your own CPU/Cash/Footprint budget can sustain the attempt.
4. **Attack** — launch; play your Kit against the target's firewall, one step at a time, until the system is weakened or you run out of resources to keep going.
5. **Capitalize** — once weakened, execute finalize actions (drain Cash, browse files, redirect resources, plant a Virus, disable defenses). Landing the breach was the hard part — a finalize action itself doesn't fail.
6. **Defend** — expect retaliation. Loadouts, counter-firewalls, and healing all run whether or not you're at the keyboard.
7. **Reinvest** — spend earnings on hardware, firewalls, mined-resource equipment, or marketplace Programs; bank what you can't afford to lose.

This loop runs in parallel with three side loops that share its economy: **mining** (visiting resource NPCs for Duct Tape/Germanium/Silicon/YBCO/Plutonium, used in equipment crafting), **Loadout logic puzzles** (self-contained sequencing puzzles, see §15), and **subnet exploration** (diving Random Subnets for patrol fights, Cache-node loot, and Route Fragments that unlock further Subnets, §3/§13 — the one side loop that's also live PvP risk, since Static and Random Subnets expose your presence to anyone else currently connected).

---

## 7. Combat System

Combat is **rig vs. rig**, resolved in discrete steps, not a real-time duel — but the two sides experience it differently on purpose. The attacker plays live: each step, they choose one ability from their current **Kit** — the abilities their equipped hardware and installed Programs make available right now — and spend **Runtime Footprint** to play it. The defender doesn't get to react live at all; their firewall and any installed defense Programs resolve automatically against whatever the attacker throws, following the **Loadout** the defender configured in advance. This split is deliberate: defense has to keep working while a player is offline (§1), so only offense gets to be a live minigame.

- **Initiating an attack** costs a flat **$10 Cash**.
- **Building a Kit:** everything a player can play during an attack comes from what's already equipped and installed before the fight starts — a baseline action from each hardware category, one signature ability per equipped hardware *type* (§12), and whatever Programs are installed (§8). Nothing new can be installed mid-fight; the fight is where a rig built during **Prepare** (§6) gets *played*, not assembled.
- **Per-step resolution:** the attacker sees the target's current HP, whichever Subroutines are currently visible on its firewall, and their own remaining Runtime Footprint, then plays one (or several, if cheap enough) ability from their Kit. Unless it explicitly breaks a Subroutine, base damage is reduced by the firewall's rolled **Absorption** (§12) with **±5% random variance**.
- **Complete-miss roll:** every attack step carries a chance for the firewall to fail outright, landing full damage before Subroutines are even in play — a genuine "clean hit" outcome layered on top of Absorption.
- **Noob protection:** any account that hasn't yet moved past its starting gear is exempt from being attacked by other players — a hard floor, not a suggestion, and critical to keeping the early game survivable in a world where everyone is a live target from minute one.
- **Subroutines resolve per step** (full mechanics in "Subroutines," below): each one a firewall has rolled either gets broken by a matching ability that step, or it fires its effect against the attacker. A firewall's Absorption always applies; Subroutines are the layer that rewards (or punishes) bringing the right tool.
- **Weakened state:** the system hits 0 HP and becomes weakened, unlocking finalize actions. It self-resets after a fixed window (30s soft reset / 45s hard reset) if the attacker doesn't capitalize.
- **Finalize actions** (attacker's choice, once weakened): drain Cash, browse files (view, download, or delete anything on the system), redirect resources from a warehouse system or a resource-carrying player (§13), plant a malicious Program — a **Virus**, whether or not it's completing a hacker-faction bounty (§14) — disable installed defense Programs, or **steal a Trace** the defender is currently holding on someone else (§14; bounty-sourced Traces can't be stolen this way). Once the system is weakened, a finalize action simply succeeds — landing the breach was the hard part, and there's no separate roll to fail it afterward.
- **Healing** costs **$2 per HP**, discounted by equipment, capped at **9 heals per weakened cycle** — healing is a real, metered cost, not a free reset.
- **Zombie/relay attacks:** a system can be "zombied," authorizing a specific third-party IP to trigger it remotely. Because nobody's live-playing a Kit for a zombie trigger, these resolve against a simple automated policy instead — weaker and less precise than a player actually playing the fight, which is the point: quantity over precision is the zombie/botnet identity, not a shortcut to the same result.
- **Networks gate attacks:** an attack can only proceed if attacker and defender share the same network — this is what makes zone identity mechanically real, not just cosmetic. This governs Computer-vs-Computer targeting specifically; a live Subnet encounter (§3) is a separate discovery layer that still resolves through this same rule once the two players are actually connected.
- **Subnet patrol encounters reuse this same resolution:** a patrol node that catches a player inside a Random Subnet (§13) plays a Kit against them exactly like a player would, using AI tuned to the patrol's tier (§13) — no separate combat model exists for exploration content.
- **Dormant computers can't be attacked at all:** once a player's 7-day auto-shutdown kicks in (§4), the system is off, so there's nothing to scan or target — the same rule that makes them invisible to search also makes them a non-target for combat.

### Subroutines

A firewall rolls 0–2 **Subroutines** at craft time (§12). Each one is tagged to a flavor (a raw-defense flavor, a detection/logging flavor, a counter-attack flavor) and, if the attacker doesn't play a matching ability that step, it fires one of three effect types instead:

- **Wear damage** — adds Wear to a thematically matched hardware component, repaired the same way any Wear is (§12), same channel a successful attack against *you* already uses.
- **Progress reset** — undoes some of the attacker's own progress that step (re-raising absorption, for instance) **without refunding the Runtime Footprint already spent** — a real cost for guessing wrong, not just a non-event.
- **Trace increase** — raises the attacker's Trace Level against whoever owns the firewall (§14).

Which effect a given Subroutine deals is fixed at roll time — a firewall's rolled mix is part of what makes two same-tier firewalls feel different, on top of the stat variance §12 already provides.

**The Tracer Subroutine is the one exception to "resolves per attack step."** Instead of firing once per unbroken tick during combat, it accrues continuously against anyone connected to that firewall's Computer — attacking, scanning, or just present — completing in **8 minutes of uninterrupted connection** if never broken. High-value NPC nodes always carry one; a player has to equip a Tracer-capable firewall to get the same passive protection. This is also what powers ambient Subnet danger (§13): a faction-controlled Subnet's patrol network runs a Tracer against its own controlling faction's Trace Level, which is why lingering in hostile territory is dangerous even when nobody's actively hunting you.

### Footprint

Programs cost two different things, because "installed" and "actively firing" are genuinely different costs:

- **Install Footprint** (checked against HD Storage Capacity, §12) — paid continuously just for having a Program installed, competing with actual files for storage space. A powerful Program is expensive to *own*.
- **Runtime Footprint** (checked against CPU Max Load + Memory Concurrent Execution, §12) — paid only in the step an ability actually fires. A demanding Program is expensive to *use*. This resource refreshes to full at the start of each new attack step, so a cheap ability spammed every step and one expensive ability played less often are both legitimate ways to spend it.

Footprint is a hard ceiling on how much a player can throw per unit of time, grounded in the hardware they actually equip.

### The Trace System

Every player carries a **Trace Level** — a 0–100% meter — against every faction and every individual player they've crossed paths with, each one independent. Trace Levels build from failed Subroutine breaks (above), from being on the losing end of an attack, and from a Tracer Subroutine's ambient accrual. They decay continuously — not a countdown that resets, but a live recalculation where remaining time-to-zero is always *current level × 2 hours* (tunable), so a Trace Level that's decayed to 25% is 30 minutes from zero, but a fresh hit back up to 50% puts it an hour out again, regardless of how much time had already passed.

**When a Trace Level hits 100%, it triggers and resets to 0** — a fresh Trace Level starts accruing immediately, independent of however long the triggered consequence below takes to resolve. What triggers depends on who was holding the meter:

- **Corp Trace (Meridian, or any corp-aligned node)** — the corp posts a bounty on the traced player through the existing Bounty system (§11): open to any player for a claim window (a few days, tunable), falling to an NPC bounty hunter (tier-scaled to the node's Danger Tier, §13) if nobody claims it. Completed by dealing a threshold amount of **Wear damage** to the traced player's hardware — a corp bounty is enforcement, not a fight to the death. Reward is cash plus a corp-specific loot pool, scaled to the triggering node's value.
- **Hacker-faction Trace (the Undernet, or any Undernet-aligned node)** — same bounty structure, but completed by landing a **Virus** (the plant-a-malicious-Program finalize action, always available in any fight — see §7) rather than dealing damage — infiltration over force, matching the faction's identity. Reward is cash plus an Undernet-specific loot pool, same value scaling.
- **Player Trace** — no bounty. The player holding the meter instead gets the traced player's Computer added to their personal network and trace list — a standing, private target rather than a broadcast contract. A player Trace can be bought off by the traced player for a fine, but only after a **12-hour minimum hold**, paid to whoever holds the Trace; it auto-clears after **7 days** regardless. Another player attacking the traced target can attempt to **steal** a player-held Trace (finalize action, above) — bounty-sourced Traces can't be stolen this way.

**Connecting through a Trace moves you to wherever the target actually is.** Whether it's a standing Player Trace built through combat (above) or a temporary Trace granted by a successful Locate against a Player Subnet (§16), using it doesn't hand you an address to visit later — it transports the connecting player directly into the target's current live Subnet. If the target isn't currently connected to any Subnet, the connecting player lands in the root Network instead. If the target is in a Network the connecting player can't yet reach, the connection simply fails — a Trace gets you to where someone is, it doesn't get you past a Network you haven't unlocked via its quest chain (§3).

Faction bounty fines follow the same logic as a player buying out a hunter: paying the fine before the bounty is claimed sends the money into the system as a sink; paying after it's claimed sends the money to whoever claimed it instead, buying out the hunt in progress.

### Cross-Program effects are asynchronous

No Program ever directly triggers, or blocks waiting on, another player's Program. When an attack triggers the defender's installed defense Program (counter-attacking, healing, shutting down the system), that Program resolves as its **own independent event**, not nested inside the attacker's current step. The attacker's step resolves from host-computed combat math alone and returns immediately; the defender's response lands shortly after, on its own.

This means a player who wants to react to a counter-attack has to explicitly poll for "was I hit" the same way any two independent systems have to poll or listen for something happening outside their own process, rather than assume an action they took will magically reflect a side effect that happened somewhere else. It's a deliberate constraint, not an oversight: letting one player's action wait live on another's has no natural bound on how deep a chain of triggered counter-responses could go, and no clean way to avoid two sides each waiting on state the other one holds. Keeping every cross-Program effect asynchronous sidesteps both, at the cost of a small, honest delay between "I got attacked" and "my defense reacted" — a trade worth making.

---

## 8. Programs & the Rig

There is no player-facing programming language in this design. A player's combat and utility power comes from two sources: the hardware they've equipped (§12, each piece contributing a baseline action plus one signature ability per equipped *type*) and the Programs they've installed. Both together make up a player's **rig** — everything they can draw from when playing a Kit or configuring a Loadout (§7).

**Programs are authored content** — a curated catalog tagged by role (Offense, Defense) rather than by port, since there's no port left to gate them (§4). A player chooses which Programs to bring, same as choosing equipment.

**Acquiring a Program** works exactly like acquiring equipment (§12): craft one via Fabrication from mined resources, or buy a finished one on the Marketplace (§11). Fabrication's existing crafting-progression framework (tier gates, resource bands) applies unchanged.

**Tagged by role and category:** a Program's role (Offense or Defense) is fixed at creation — a defensive Program can't be repurposed as an offensive weapon by accident, even without a port to enforce the split. Every Program also belongs to one of **six Program categories**, each running its own full Career Track (§5) exactly like a hardware category does — three Defense-role (**Bastion**, **Sentry**, **Retaliator**) and three Offense-role (**Sunder**, **Ghostcoat**, **Nullstrike**), each Offense category built to counter one specific Defense category. Within each Program category sit six named types (Bastion's Standard, Ironclad, Aegis, Bulwark, Rampart, and Grid Lock, and so on for the other five — full rosters in §5), each carrying its own Item Track and one signature ability, the same way a hardware type does. A Program carries one ability and a Footprint cost (§7) where a piece of hardware carries a stat block — Career Track Item Power bonuses scale that one ability directly instead of a set of stats.

**A Loadout is a defender's pre-set answer key**, built from installed defense Programs plus simple conditional rules ("if HP < 30%, Counter-Strike; else Heal") — no branching language needed, just an ordered list of conditions a player arranges once and revisits when their rig changes. A Kit, by contrast, is never pre-built — it's just whatever's currently available to play live, drawn fresh from the same installed rig (§7).

---

## 9. Ability & Action Index

A flat index of every named ability in the game, grouped by the Career Track category (§5) that grants it, since that's the axis players actually build around — and a roster this size needs to be scannable by category to spot gaps and keep each one balanced against the others. Every category — the six hardware categories and the six Program categories alike — carries at least six shared abilities (available to any equipped or installed item in that category) plus exactly one unique ability per named item type (§8 for Programs, §12 for hardware) — the same signature-ability rule already established, just made concrete here instead of left as an example. Every ability lists its **effect** and its **Runtime Footprint** cost (§7) — what it costs to play in the step it fires. Plain account actions — banking, repairs, listings — aren't abilities at all and belong to their own sections instead (§10, §12), not this index.

> **Design Gap:** what actually unlocks each ability below — gear tier, Marketplace cost, or just available by default — isn't decided now that skill levels no longer gate them. Every former "Lvl N" unlock has been removed from this index; nothing replaces it yet.

Every ability still ships with a full description and, where it's played in combat, a worked example — that documentation quality is a real bar to hit, since this index *is* the game's tutorial for what a rig can actually do.

### CPU abilities

**Shared** — available to any equipped CPU, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Initiate Breach / Read Absorption | Opens an attack session; recon on a scanned target's rolled absorption | 5 |
| Direct Strike | A basic, no-frills Kit damage ability | 10 |
| Cycle Burst | A small self-heal, once per attack session | 15 |
| Berserk | Deals extra damage but self-damages the attacking system for half that amount, once per attack session | 35 |
| Overclock | Temporarily exceeds rated max load at the cost of accelerated Wear | 40 |
| Ghost Protocol | Capstone — one free re-initiation/day after a complete-miss loss | 0 (account action) |

**Unique** — one signature ability per CPU type (§12), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Balanced Strike | A reliable damage ability with no downside and no upside | 15 |
| Overclocked | Overclock Strike | A big Runtime Footprint burst for heavy damage, with extra self-Wear | 45 |
| Industrial | Redline | Briefly exceeds the normal Runtime Footprint ceiling without the Wear penalty another CPU would take for it | 20 |
| Efficiency | Rapid Cycle | Play this ability twice in the same step at a small combined Footprint discount | 20 |
| Distributed | Split Load | Halves the Footprint cost of your next ability this step | 15 |
| Hardened | Thermal Lock | The next two Wear-damage Subroutine effects against you this fight are halved | 20 |
| Signal | Deep Ping | A cheap live-Kit action that reveals a Subroutine before committing to break it | 8 |
| Blackout | Power Spike | This step's Overclock can critically strike for double damage | 30 |

### HD abilities

**Shared** — available to any equipped HD, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Quick List | Basic recon: preview a weakened target's top-level file directory | 5 |
| Shadow Copy | Once per weakened cycle, reroll a stolen file's rarity and keep the better result | 20 |
| Deep Copy | Steals two files in one finalize action instead of one | 30 |
| Fragment Scan | Reveals whether a target's files include a Route Fragment before committing to steal | 15 |
| Purge | Deletes a target's file without stealing it — a denial play | 20 |
| Archive Dive | Reviews your own history against a target for repeat-loot odds | 10 |

**Unique** — one signature ability per HD type (§12), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Clean Read | A plain, reliable file read with no side effects | 10 |
| Vault | Lockdown Read | Resists the target's own Theft Resistance stat for this one action | 20 |
| Raid Array | Parallel Pull | Pulls from two files at once, faster but easier for the target to notice | 25 |
| Archive | Deep Archive | Can retrieve a file even from a target whose system is currently dormant | 30 |
| Flash | Flash Grab | A near-instant file grab at minimal cost | 5 |
| Encrypted | Silent Extract | This steal adds no Wear to the target's HD — it leaves no trace | 20 |
| Redundant | Backup Read | Guarantees a partial result even if the steal attempt fails | 15 |
| Compact | Budget Copy | The cheapest possible file-theft action; lower-value loot | 5 |

### Memory abilities

**Shared** — available to any equipped Memory, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Split Cycle | A small Footprint discount when chaining two cheap abilities in the same step | 10 |
| Snap Cache | Once per attack session, refund half the Footprint of your next ability | 15 |
| Multi-Thread | Once per attack session, play two full abilities — including expensive ones — in the same step, at a flat surcharge | 40 |
| Deep Cache | Once per attack session, fully refund the Footprint of the single most expensive ability you played that session | 30 |
| Buffer Flush | Clears any Progress-reset penalty from your last failed Subroutine break | 20 |
| Thread Lock | Prevents any further Subroutine Progress-reset penalty for the rest of this step | 15 |

**Unique** — one signature ability per Memory type (§12), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Steady Thread | A small, reliable Footprint refund | 10 |
| Expanded | Wide Cast | This step's ability can check an extra Subroutine instead of just one | 25 |
| Sentinel Array | Watch Loop | Passively slows a Tracer Subroutine's accrual rate against you while connected | 15 |
| Parallel | Dual Process | Halves the Footprint cost of exactly two abilities played this step | 20 |
| Low-Latency | Instant Response | Further reduces your Loadout's response time this session, stacking with Response Latency | 10 |
| Dense | Overload Buffer | Temporarily raises your Runtime Footprint ceiling for this step at a Wear cost | 30 |
| Buffered | Smooth Cycle | Removes the ±5% Absorption variance on your next attack, guaranteeing the expected value | 20 |
| Economy | Lean Thread | The cheapest ability in the game to play; minimal effect | 5 |

### Firewall abilities

**Shared** — available to any equipped Firewall, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Hold the Line | A small flat damage reduction on the next incoming hit | 10 |
| Ping Back | Reflects a small flat amount of damage on a successful Absorb | 15 |
| Countered? (self-check) | Poll (not callback, per the async design, §7) whether your last attack got countered | 5 |
| Second Guess | Once per attack session, force one rolled Subroutine check in your favor | 25 |
| Brace | Applies a flat penalty to the attacker's next Complete-miss roll against you | 20 |
| Patch Job | Repairs a small amount of your own system HP mid-fight | 20 |

**Unique** — one signature ability per Firewall type (§12), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Steady Absorb | A small, reliable Absorption boost for one incoming hit | 10 |
| Bunker | Wall Up | Massively increases Absorption for one hit, at the cost of increased CPU Upkeep afterward | 30 |
| Reflector | Full Reflect | Reflects a much larger share of incoming damage than Ping Back, once per session | 35 |
| Trapster | Ambush Break | Guarantees one of your rolled Subroutines fires its effect regardless of whether the attacker's ability matched it | 25 |
| Lean | Cheap Hold | A discount Absorption boost — less effective, but nearly free | 5 |
| Adaptive | Shift Pattern | Rerolls which Subroutine is currently visible to the attacker | 20 |
| Honeypot-Tuned | False Read | Shows the attacker fake HP/Subroutine info for one scan | 15 |
| Overbuilt | Iron Wall | The single strongest flat damage reduction in the game — expensive to maintain | 40 |

### PCI abilities

**Shared** — available to any equipped PCI card, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Fast Post | Lists an item on the Marketplace at a reduced fee, once per day — a plain account action, not a combat play | 0 (account action) |
| Route Map | Reveals attached networks two hops out | 10 |
| Bulk Transfer | The next file-related finalize action this fight moves an extra file for free | 20 |
| Toll Skim | A successful Cash-drain finalize action pulls a small amount of bonus Cash | 15 |
| Fee Waiver | Removes the Footprint surcharge from your next Program-based finalize action | 10 |
| Rate Lock | Guarantees your next Cash-drain finalize action isn't reduced by the target's defenses | 20 |
| Freight Discount | Reduces the Footprint cost of a Redirect finalize action | 15 |
| Audit Trail | Reveals how much Cash a target is holding before you commit to draining it | 10 |

**Unique** — one signature ability per PCI type (§12), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard (Basic I/O) | Basic Transfer | A plain, reliable boost to a Cash-drain finalize action | 10 |
| Courier | Rapid Exfil | Speeds up a file-theft finalize action at reduced Footprint | 10 |
| Installer | Install Rush | Your next installed Program (after this fight) costs less Install Footprint | 10 |
| Archivist | Deep Storage | Raises how many files you can steal before hitting a storage limit this fight | 20 |
| Broker | Broker's Cut | A successful Cash-drain finalize action generates bonus value | 15 |
| Balanced | Even Trade | A small flat bonus to whichever finalize action you play next | 15 |
| Overclocked I/O | Burst Transfer | A very fast, high-Footprint file or Cash transfer with bonus yield | 30 |
| Salvage | Scrap Value | Redirect and resource-theft finalize actions yield bonus resources | 15 |

### AGP abilities

**Shared** — available to any equipped AGP card, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Short Hop | A one-time minor relay-chain range extension | 10 |
| Steady Signal | Reduces this attack session's Trace Level gain by a small flat amount | 15 |
| Reroute | Patches a severed relay-chain hop on the next step | 20 |
| Detect Zombie Relay | Exposes whether an attacker is itself a zombie relay | 10 |
| Signal Boost | Increases damage dealt through a zombie/relay chain attack | 25 |
| Hard Line | This step's ability can't be Countered, bypassing the target's Firewall entirely for one hit | 30 |
| Scan Shipments / Intercept Shipment | Lists and hits in-transit manifests — shipments are hijacked the same way a firewall is breached (§7), one shared combat model | 10 |
| Read Manifest | Cheap pre-commit query on a manifest's contents | 5 |
| Disambiguate Route | Decoy-lane disambiguation | 15 |
| Toll Booth | Standing ambush infrastructure, queues intercepts while offline | 20 |
| Read Convoy Manifest | Reveals which manifest in a convoy actually carries goods | 15 |

**Unique** — one signature ability per AGP type (§12), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Clean Relay | A reliable, no-frills relay-chain action | 10 |
| Relay-Tuned | Chain Extend | Extends max relay-chain length by 1 for this session | 20 |
| Cryo-Stable | Cold Run | This step generates no Wear regardless of load | 15 |
| Combat-Rated | Overcharge Strike | A big flat Combat Damage bonus for one hit | 30 |
| Overhead | Heavy Load | Lets you play an unusually expensive ability this step at a reduced penalty | 25 |
| Balanced | Even Signal | A small flat bonus split between damage and Trace reduction | 15 |
| Botnet | Swarm Call | Temporarily boosts a zombie/relay attack's precision closer to a live player's | 25 |
| Salvage | Scavenge Signal | A cheap, weak version of Short Hop | 5 |

### Bastion abilities

**Shared** — available to any installed Bastion Program, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Guard Up | A small flat damage reduction on the next incoming hit | 15 |
| Brace | Reduces incoming damage further if HP is already below 50% | 15 |
| Patch Seal | Repairs a small amount of HP after absorbing a hit | 15 |
| Hard Point | The reduction also applies to Wear-damage Subroutine effects, not just direct damage | 20 |
| Long Guard | The reduction lasts for the next two incoming hits instead of one | 20 |
| Overguard | A much stronger reduction, but only usable once per attack session | 30 |

**Unique** — one signature ability per Bastion type (§5), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Steady Guard | A reliable flat damage reduction with no special effect | 15 |
| Ironclad | Absolute Guard | The single strongest one-hit reduction any Program offers | 35 |
| Aegis | Guard and Mend | The reduction also heals a small amount of HP | 25 |
| Bulwark | Rising Wall | The reduction grows by a small amount each step it stays active this fight | 20 |
| Rampart | Cheap Guard | A minimal reduction at minimal Footprint | 5 |
| Grid Lock | Sap Load | The reduction also slightly drains the attacker's Runtime Footprint regen this step | 25 |

### Sentry abilities

**Shared** — available to any installed Sentry Program, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Log Entry | Records the current attacker's identity automatically, even while the defender is offline | 10 |
| Ping Alert | Pushes an inbox notification the instant an attack starts, even offline | 5 |
| Scan Log | Records who scanned you, not just who attacked | 10 |
| Cross Check | Compares this attacker's identity against your own scan history for repeat-offender flags | 15 |
| Quiet Watch | A cheap, low-Footprint version of Log Entry | 5 |
| Deep Read | Reveals the attacker's Kit composition, not just their identity | 20 |

**Unique** — one signature ability per Sentry type (§5), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Basic Log | Identifies the attacker, nothing more | 10 |
| Overwatch | Full Sweep | Identity, Kit, and a scan-history pull, all in one action | 30 |
| Tripline | Instant Flag | Identifies the attacker the moment the very first step starts, before any damage lands | 20 |
| Archive Watch | Long Memory | Retains attacker identity and history for a much longer window than normal | 20 |
| Quietkeep | Light Log | A cheap, minimal-Footprint identity check | 5 |
| Mirror Log | False Face | Shows the attacker a decoy read while secretly logging their true identity | 25 |

### Retaliator abilities

**Shared** — available to any installed Retaliator Program, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Snap Back | Reflects a small flat amount of damage back at an attacker, once per attack session | 15 |
| Return Volley | The reflect scales slightly with the incoming hit's own damage | 20 |
| Guard Break | The reflect also briefly reduces the attacker's next Absorption-ignoring ability's effectiveness | 20 |
| Slow Burn | The reflect applies as a delayed tick over the next two steps instead of instantly | 15 |
| Cheap Bounce | A minimal, low-Footprint reflect | 5 |
| Overflow | The reflect triggers twice if the incoming hit exceeded a damage threshold | 20 |

**Unique** — one signature ability per Retaliator type (§5), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Return Fire | A basic reflect, once per attack session | 15 |
| Backlash | Full Return | The single biggest reflect any Program offers | 35 |
| Spike Coil | Corrosive Return | Reflected damage applies as Wear instead of HP damage | 25 |
| Echo | Standing Echo | A small reflect fires on every hit landed against you this fight, not just once | 25 |
| Cutrate | Small Return | A minimal, cheap reflect | 5 |
| Feedback Loop | Traceback | The reflect also raises the attacker's Trace Level | 20 |

### Sunder abilities

Sunder is the Offense category built to counter **Bastion** (above).

**Shared** — available to any installed Sunder Program, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Access Push | A basic, no-frills Kit damage/finalize-adjacent action | 10 |
| Crack Line | Ignores a small flat portion of Absorption on the next hit | 15 |
| Weak Point | Bonus damage if the target's Absorption was already reduced this fight | 15 |
| Split Guard | Ignoring Absorption also slightly lowers the target's Wear resistance | 20 |
| Cheap Crack | A minimal, low-Footprint version of Crack Line | 5 |
| Overpressure | The Absorption-ignore also applies to the target's next incoming hit from anyone, not just you | 20 |

**Unique** — one signature ability per Sunder type (§5), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Punch Through | Ignores a modest flat portion of Absorption | 15 |
| Sledgehammer | Shatter Guard | The single biggest Absorption-ignore of any Program | 35 |
| Wear Point | Corrode Strike | Ignoring Absorption also adds bonus Wear to a hardware component | 25 |
| Chainbreaker | Building Force | Each consecutive successful hit ignores more Absorption than the last | 20 |
| Crude Edge | Rough Strike | A cheap, minor Absorption-ignore | 5 |
| Overrun | Break Rhythm | Ignoring Absorption also slightly lowers the target's next Absorption roll | 25 |

### Ghostcoat abilities

Ghostcoat is the Offense category built to counter **Sentry** (above).

**Shared** — available to any installed Ghostcoat Program, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Trace Scrub | Reduces this attack session's Trace Level gain by a small flat amount | 15 |
| Quiet Entry | The first step of an attack can't trigger a Tripwire-style alert | 10 |
| Blend In | Reduces the odds this attacker shows up in the target's scan history | 15 |
| Static Veil | Briefly hides this attacker's Kit composition from a Deep Scan-style read | 20 |
| Cheap Cover | A minimal, low-Footprint version of Quiet Entry | 5 |
| Double Blind | The suppression also applies to a second observer, not just the direct target | 20 |

**Unique** — one signature ability per Ghostcoat type (§5), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | Go Dark | Suppresses identity detection for this attack | 15 |
| Blackout | Total Blackout | Suppresses identity, Kit reveal, and logging all at once | 35 |
| Fade Step | Erase Trail | Also retroactively removes this attacker from the target's scan history | 25 |
| Long Shadow | Extended Cover | The suppression lasts the whole session, not just one attack | 20 |
| Dim Signal | Quiet Step | A cheap, minor suppression | 5 |
| Mirror Break | See Through | Also cancels any decoy or false read a defender's Program tries to show you | 25 |

### Nullstrike abilities

Nullstrike is the Offense category built to counter **Retaliator** (above).

**Shared** — available to any installed Nullstrike Program, regardless of type:

| Ability | Effect | Footprint |
|---|---|---|
| Exploit Chain | Costs less Footprint to play if the previous ability this step landed successfully | 15 |
| Guard Snap | The next hit ignores one Subroutine's counter-attack effect specifically | 15 |
| Steady Hand | Reduces the odds of triggering any reflect this step, without fully blocking it | 10 |
| Cut Line | Nullifying a reflect also removes one step of Progress-reset penalty | 20 |
| Cheap Snap | A minimal, low-Footprint version of Guard Snap | 5 |
| Overwatch Null | Nullifies reflect for two consecutive steps instead of one | 20 |

**Unique** — one signature ability per Nullstrike type (§5), never locked out:

| Item | Ability | Effect | Footprint |
|---|---|---|---|
| Standard | No Bounce | The next hit landed can't trigger a reflect | 15 |
| Deadbolt | Lockout | Blocks all reflect/counter effects for the rest of the fight | 35 |
| Absorb Return | Steal Back | Blocked reflect damage heals the attacker instead of vanishing | 25 |
| Chain Null | Jam Circuit | Nullifying a reflect also raises the Footprint cost of the target's next Retaliator-type ability | 20 |
| Half Null | Soft Null | A cheap ability that halves, rather than fully blocks, one reflect | 5 |
| Quiet Strike | Silent Hit | Nullifying reflect also prevents any Trace Level gain from that specific hit | 25 |

---

## 10. The Rig Builder & Loadout Editor

The Rig Builder is a browsing-and-arranging tool — a first-class, multi-tab interface (up to ~10 open tabs) for the two things a player actually configures: which Programs are installed, and how a defensive Loadout is arranged.

**Installing a Program** works through a catalog tagged by role — Offense or Defense (§8) — rather than by port. Two explicit actions bridge browser and server:

- **Preview** — shows projected Install/Runtime Footprint (§7) and crafting/purchase cost without spending anything.
- **Install** — actually spends money and installs the Program, with an explicit cost confirmation dialog (cost, Footprint, and current Cash shown together) before commit.

**The Loadout Editor** is where a defender arranges their installed defense Programs into the conditional rules a Loadout runs on while its owner is offline (§7, §8) — an ordered list of simple "if condition, then ability" rules, reordered by drag-and-drop. Sequencing and prioritization are still real decisions, built by arranging cards.

This up-front cost transparency matters: players are making a real economic decision every time they install something, and hiding that cost behind a generic "confirm" dialog would undercut the whole "hacking has consequences" feel.

---

## 11. Economy

**Currency:** Cash (liquid, exposed — stealable as a finalize action against a weakened player or a Banking System node, §13) and Bitcoin (fully safe: cannot be hacked or stolen by anyone, accessible only by the account that owns it). *(The mechanics for moving money between the two — deposit tiers, cuts, a Heat-style system — are entirely undesigned: they used to live in the Laundering skill, which no longer exists now that skills have been cut from the game (§20).)*

**Resources:** five mined materials — **Duct Tape, Germanium, Silicon, YBCO, Plutonium** — gathered from dedicated mining NPCs, feeding the full crafted-equipment system in §12: CPU, HD, Memory, Firewall, and PCI/AGP expansion cards alike.

**Loot:** items drop from weighted **drop tables** — cumulative-weight random selection, with common items at high weight (~100) and rare gear as low as weight 1, and some slots reserved for guaranteed/quest-only items at weight 0 (never randomly rolled, only handed out directly). Random Subnet Cache nodes (§13) roll against these same tables, scaled by the subnet's Danger Tier, and are the sole source of **Route Fragments** — a file type that reveals a previously-hidden Subnet entry point when used.

**Marketplace:** players sell Programs, firewalls, and items to each other directly — files carry real listed prices as a native property, not a bolt-on trading UI.

**Traffic revenue:** a Player Subnet's hosted website (§3) generates passive income scaled by votes and advertising spend (§16) — the game's clearest passive-income stream, and a big part of why building one is worth the investment.

**Bounties:** a lightweight contract system, independent of authored story quests — post a target IP, a reward, an iteration count, and a bounty type (**Scan, Attack, Install Script, Vote, Deface Subnet Site, Neutralize Defenses**), optionally anonymously or open to any player. Each matching event against the target decrements the counter; at zero the bounty pays out and closes. This is the game's primary mercenary/PvP-contract loop and should be kept intact — it's what turns "someone ganked me" into "someone can pay to have someone else ganked." Most bounties are player-posted, but a faction (§14) can post one too, using the same mechanism — no separate system needed for "Meridian wants this Undernet contact hit."

---

## 12. Equipment & Crafting

Every piece of hardware in the game — CPU, HD, Memory, Firewall, PCI, and AGP — uses one consistent, player-crafted equipment framework: real tiers, real Bins, real player agency at every level. Internally consistent with everything above: Fabrication's crafting role, the five mined resources, the Wear system.

### The framework

Every piece of hardware has a **base tier** and a **Bin** (0–3, borrowed from real semiconductor manufacturing — chip foundries grade their output by how far it exceeds spec), written `tier.bin` — e.g. `1.2` means base Tier 1, Bin 2. Each Bin level adds a full tier's worth of power, so `1.2` and `3.0` are mechanically identical. Every tier can carry the same 0–3 Bin range — there's no artificial ceiling tying Bin availability to how high the base tier already is.

**Effective Tier = base tier + Bin. Item Power = Effective Tier × 100.** Every category — CPU, HD, Memory, Firewall, PCI, AGP — uses the same 8-tier base scale (Item Power 100–800 at Bin 0, up to 1100 at Tier `8.3`). No category is a special case.

Because Bin is uncapped-by-tier, a high-bin low tier can approximate a higher tier's power (`1.3` matches `4.0` exactly, both Item Power 400) but never leapfrog past a higher tier's *own* Bin range (`1.3` at 400 still falls short of `5.0` at 500) — a good Bin gets you more out of a tier you already have, it doesn't let a cheap tier substitute for reaching a genuinely higher one.

Every stat on every item is `Value = Intercept + Slope × Item Power` — linear, but each stat has its own intercept and slope, so no two stats grow at the same rate and doubling Item Power never uniformly doubles every stat on an item. On top of that formula, every crafted item also rolls a small **±5% variance** per stat at craft time — the property firewalls already had before this framework existed, now extended to all hardware — so no two same-tier/Bin/type items are perfectly identical, which is what actually sustains a hardware marketplace instead of a single best-in-slot answer.

**Getting Bins.** Ordinary crafting rolls them randomly, with sharply diminishing odds for each additional level. **Foundry Rights** — fully custom-spec cards with player-chosen stat rolls within a budget — is the deliberate alternative — spend a resource budget to hand-pick an item's Bin instead of leaving it to the foundry's roll.

**Resource cost scales with tier**, the same banding for every category: Tier 1–2 (Duct Tape), Tier 3–4 (Germanium), Tier 5–6 (Silicon), Tier 7 (YBCO), Tier 8 (Plutonium) — the same banding **Repair Component** uses (below), so learning to repair a tier and learning to craft one draw on the same materials, for any piece of hardware.

**Repairing Wear** is a plain account action, not a combat ability: Repair Component fixes Wear on a component (Tier 1–2 with Duct Tape, Tier 3–6 with Germanium/Silicon, Tier 7 with YBCO, Tier 8 with Plutonium), and **Auto-Repair Schedule** auto-queues repairs from stockpiled resources while offline.

**Firewalls craft the same way as everything else** — the same Fabrication-and-resources pipeline, consistent with the founding rule that all equipment is player-craftable. Players who don't want to craft one themselves can still just buy a finished firewall from another player on the Marketplace (§11) — that path is already there for Programs and items, and covers hardware too.

**Every equipped hardware type carries one signature ability**, on top of the stat multipliers below — this is what actually fills out a player's Kit before a single Program gets installed (§7, §8). A type's existing "Identity" flavor is the seed for its ability: Overclocked CPU's "glass cannon" identity becomes **Overclock Strike** (a big Runtime Footprint burst for heavy damage, with extra self-Wear); Signal CPU's "cheaper scan-type Programs" identity becomes **Deep Ping** (a cheap live-Kit action that reveals a Subroutine before committing to break it); Industrial CPU's "resists heat-driven Wear" identity becomes **Redline** (briefly exceed the normal Runtime Footprint ceiling without the Wear penalty another CPU would take for it). The full roster — up to one ability per type per category, PCI/AGP most likely staying passive-only given their existing expansion-slot identity — is real content work, the equipment equivalent of §14's quest-writing gap, but the pattern above is the whole method: read the identity that's already written, turn it into something playable.

### CPU

Four stats, each tied to something already established rather than invented in a vacuum: **Max Load** (§4's existing hard ceiling), **Heat Threshold** (the CPU heat gauge from §17, and running hot feeding the Wear system directly, §9), **Install Speed** (the installing-time economy from §8), **Overclock Headroom** (directly gates the Overclock ability, §5).

| Tier | Item Power | Max Load | Heat Threshold | Install Speed | Overclock Headroom |
|---|---|---|---|---|---|
| 1 | 100 | 50 | 45 | 6.5% | 12.5% |
| 2 | 200 | 100 | 60 | 11% | 15% |
| 3 | 300 | 150 | 75 | 15.5% | 17.5% |
| 4 | 400 | 200 | 90 | 20% | 20% |
| 5 | 500 | 250 | 105 | 24.5% | 22.5% |
| 6 | 600 | 300 | 120 | 29% | 25% |
| 7 | 700 | 350 | 135 | 33.5% | 27.5% |
| 8 | 800 | 400 | 150 | 38% | 30% |

*(Formulas: Max Load = 0.50×IP; Heat Threshold = 30 + 0.15×IP; Install Speed = 2 + 0.045×IP; Overclock Headroom = 10 + 0.025×IP.)*

This table stops at Tier 8 (Item Power 800) since that's the highest Bin-0 item — the same four formulas keep working unchanged past that point, so a `7.2` or `8.3` CPU (Item Power 900–1100) simply plugs a bigger number in; no separate high-end table needed. Those are baseline ("Standard" type) values. Eight CPU types apply a per-stat multiplier on top — this is where build identity actually lives:

| Type | Max Load | Heat | Install Spd | Overclock | Identity |
|---|---|---|---|---|---|
| Standard | 1.0 | 1.0 | 1.0 | 1.0 | Baseline, cheapest to craft |
| Overclocked | 1.3 | 0.7 | 1.0 | 1.4 | Glass cannon, built for Overclock |
| Industrial | 0.85 | 1.5 | 0.9 | 0.8 | Tanky, resists heat-driven Wear |
| Efficiency | 0.9 | 1.1 | 1.3 | 0.9 | Fast iteration — Breaching-heavy players |
| Distributed | 1.1 | 1.0 | 0.85 | 1.0 | Cheaper relay-chain Programs — Networking synergy |
| Hardened | 0.8 | 1.6 | 0.85 | 0.7 | Lowest Wear accrual overall — Fortification-adjacent |
| Signal | 1.0 | 0.9 | 1.15 | 1.0 | Cheaper scan-type Programs — Enumeration synergy |
| Blackout | 1.15 | 0.75 | 0.9 | 1.25 | High-risk: overclocking can crit a live attack step |

### HD

HD's four stats give real weight to the hardware component §4 already names: **Storage Capacity** (file-system slots, §4's Files, and also the Install Footprint ceiling for Programs, §7), **Transfer Speed** (FTP Put/Get speed, Breaching), **Corruption Resistance** (resists Wear-driven data loss), **Theft Resistance** (lowers the odds a file-theft finalize action against you succeeds).

| Tier | Item Power | Storage | Transfer Spd | Corruption Res | Theft Res |
|---|---|---|---|---|---|
| 1 | 100 | 50 | 7% | 7% | 3% |
| 2 | 200 | 80 | 11% | 9% | 6% |
| 3 | 300 | 110 | 15% | 11% | 9% |
| 4 | 400 | 140 | 19% | 13% | 12% |
| 5 | 500 | 170 | 23% | 15% | 15% |
| 6 | 600 | 200 | 27% | 17% | 18% |
| 7 | 700 | 230 | 31% | 19% | 21% |
| 8 | 800 | 260 | 35% | 21% | 24% |

*(Formulas: Storage = 20 + 0.30×IP; Transfer Speed = 3 + 0.04×IP; Corruption Res = 5 + 0.02×IP; Theft Res = 0.03×IP.)*

| Type | Storage | Transfer | Corruption | Theft | Identity |
|---|---|---|---|---|---|
| Standard | 1.0 | 1.0 | 1.0 | 1.0 | Baseline |
| Vault | 0.8 | 0.8 | 1.5 | 1.4 | Defensive — Fortification-adjacent |
| Raid Array | 1.3 | 1.3 | 0.7 | 0.7 | Speed and capacity, fragile |
| Archive | 1.6 | 0.6 | 1.1 | 0.9 | Max-storage hoarder |
| Flash | 0.6 | 1.6 | 0.9 | 0.9 | Speed-run FTP specialist |
| Encrypted | 0.9 | 0.85 | 1.4 | 1.1 | Hardened against data corruption specifically |
| Redundant | 0.85 | 0.9 | 1.1 | 1.3 | A stolen file partially survives instead of a hard loss |
| Compact | 0.7 | 0.7 | 0.7 | 0.7 | Cheap to craft, budget option |

### Memory

**Program Capacity** and **Loadout Capacity** (both already established in §4), plus **Concurrent Execution** (reduces contention running multiple Programs at once, and also the Runtime Footprint ceiling for abilities played in a Kit, §7) and **Response Latency** (shrinks the async defense-trigger delay from §7).

| Tier | Item Power | Program Cap | Loadout Cap | Concurrent | Latency |
|---|---|---|---|---|---|
| 1 | 100 | 8 | 4 | 2% | 3% |
| 2 | 200 | 12 | 6 | 4% | 5% |
| 3 | 300 | 16 | 8 | 6% | 7% |
| 4 | 400 | 20 | 10 | 8% | 9% |
| 5 | 500 | 24 | 12 | 10% | 11% |
| 6 | 600 | 28 | 14 | 12% | 13% |
| 7 | 700 | 32 | 16 | 14% | 15% |
| 8 | 800 | 36 | 18 | 16% | 17% |

*(Formulas: Program Capacity = 4 + IP/25; Loadout Capacity = 2 + IP/50; Concurrent Execution = 0.02×IP; Response Latency = 1 + 0.02×IP.)*

| Type | Program | Loadout | Concurrent | Latency | Identity |
|---|---|---|---|---|---|
| Standard | 1.0 | 1.0 | 1.0 | 1.0 | Baseline |
| Expanded | 1.4 | 0.7 | 1.0 | 0.9 | Program-heavy |
| Sentinel Array | 0.7 | 1.5 | 0.9 | 1.1 | Loadout-heavy — build-defining for Sentinel investment |
| Parallel | 0.9 | 0.9 | 1.5 | 0.9 | Concurrent-execution focused |
| Low-Latency | 0.9 | 0.9 | 0.9 | 1.5 | Latency-focused — also Sentinel-adjacent |
| Dense | 1.3 | 1.3 | 0.9 | 0.9 | High both capacities; higher Wear accrual as the tradeoff |
| Buffered | 0.75 | 0.75 | 1.3 | 1.3 | Quality over quantity |
| Economy | 0.7 | 0.7 | 0.7 | 0.7 | Cheap to craft, budget option |

### Firewall

Same four mechanical ideas §7 already established — **Absorption**, **CPU Upkeep**, **Subroutine Potency** (the strength/chance behind the 0–2 rolled Subroutines, including whether one of them is a Tracer, §7), **Counter-Damage** (for firewalls with offensive capability) — now expressed as the shared framework's per-stat linear formulas instead of a hand-tuned curve. Tier 1 and Tier 8 are fixed anchor values; the six tiers between them and all four formulas are a balance pass built to land smoothly between those two points, not a lookup.

| Tier | Item Power | Absorption | CPU Upkeep | Subroutine Potency | Counter-Dmg |
|---|---|---|---|---|---|
| 1 | 100 | 15% | 2 | 15% | 3% |
| 2 | 200 | 25% | 5 | 21% | 7% |
| 3 | 300 | 35% | 7 | 28% | 11% |
| 4 | 400 | 45% | 10 | 34% | 15% |
| 5 | 500 | 55% | 12 | 41% | 18% |
| 6 | 600 | 65% | 15 | 47% | 22% |
| 7 | 700 | 75% | 17 | 54% | 26% |
| 8 | 800 | 85% | 20 | 60% | 30% |

*(Formulas: Absorption = 5 + 0.1×IP exactly; CPU Upkeep ≈ -0.57 + 0.0257×IP; Subroutine Potency ≈ 8.57 + 0.0643×IP; Counter-Damage ≈ -0.86 + 0.0386×IP.)*

| Type | Absorption | CPU | Subroutine | Counter | Identity |
|---|---|---|---|---|---|
| Standard | 1.0 | 1.0 | 1.0 | 1.0 | Baseline |
| Bunker | 1.3 | 1.2 | 0.8 | 0.7 | Pure tank |
| Reflector | 0.85 | 1.0 | 0.9 | 1.6 | Counter-attack focused |
| Trapster | 0.9 | 0.9 | 1.5 | 0.9 | Unpredictable — leans on Subroutines, most likely of the eight to roll a Tracer |
| Lean | 0.95 | 0.6 | 0.95 | 0.95 | Cheap CPU upkeep, budget-conscious |
| Adaptive | 1.05 | 1.1 | 1.2 | 1.0 | Rerolls one Subroutine check per weakened cycle in your favor — echoes the Firewall category's own Second Guess ability |
| Honeypot-Tuned | 0.8 | 0.85 | 1.3 | 1.1 | Pairs with a Loadout's deception settings for baiting plays |
| Overbuilt | 1.2 | 1.4 | 1.1 | 1.1 | Expensive, top-heavy — late-game resource sink |

### PCI Cards

PCI is the utility/economy expansion slot; the Basic I/O Card is its baseline type, one of eight below rather than a one-off item. Four stats: **FTP Transfer Bonus** (stacks with HD's own Transfer Speed), **Install Discount** (reduces the cost of installing Programs), **Storage Bonus** (stacks with HD's Storage Capacity), **Trade Fee Reduction** (Marketplace/Laundering-adjacent).

| Tier | Item Power | FTP Transfer | Install Discount | Storage | Trade Fee Reduction |
|---|---|---|---|---|---|
| 1 | 100 | 3% | 2% | 5 | 1% |
| 2 | 200 | 6% | 4% | 10 | 2% |
| 3 | 300 | 9% | 6% | 15 | 3% |
| 4 | 400 | 12% | 8% | 20 | 4% |
| 5 | 500 | 15% | 10% | 25 | 5% |
| 6 | 600 | 18% | 12% | 30 | 6% |
| 7 | 700 | 21% | 14% | 35 | 7% |
| 8 | 800 | 24% | 16% | 40 | 8% |

*(Formulas: FTP Transfer = 0.03×IP; Install Discount = 0.02×IP; Storage = 0.05×IP; Trade Fee Reduction = 0.01×IP.)*

| Type | FTP | Install | Storage | Trade Fee | Identity |
|---|---|---|---|---|---|
| Standard (Basic I/O) | 1.0 | 1.0 | 1.0 | 1.0 | Baseline — the Basic I/O Card |
| Courier | 1.5 | 0.8 | 0.8 | 0.9 | FTP-speed specialist — Breaching exfiltration |
| Installer | 0.8 | 1.6 | 0.8 | 0.9 | The deepest Install Discount specialist |
| Archivist | 0.8 | 0.8 | 1.6 | 0.9 | Storage specialist |
| Broker | 0.85 | 0.85 | 0.85 | 1.5 | Marketplace/Laundering-adjacent |
| Balanced | 1.15 | 1.15 | 1.1 | 1.1 | No specialty, solid across the board |
| Overclocked I/O | 1.3 | 1.0 | 0.7 | 1.0 | Fast transfer, higher Wear on the host CPU |
| Salvage | 0.9 | 0.9 | 1.2 | 1.2 | Cheap to craft, biased toward economy stats |

### AGP Cards

AGP is the combat/networking expansion slot — Relay AGP Card and Cryo-Stable AGP Card, both referenced earlier as Fabrication crafting outputs, are types within this category. Four stats: **Relay Overhead Reduction** (cuts the cost of relay-chain Programs), **Wear Resistance** (reduces Wear accrual), **Combat Damage Bonus**, **Footprint Bonus** (extra Runtime Footprint headroom per step, §7).

| Tier | Item Power | Relay Overhead | Wear Resistance | Combat Dmg | Footprint |
|---|---|---|---|---|---|
| 1 | 100 | 2% | 3% | 1% | 2% |
| 2 | 200 | 4% | 6% | 2% | 4% |
| 3 | 300 | 6% | 9% | 3% | 6% |
| 4 | 400 | 8% | 12% | 4% | 8% |
| 5 | 500 | 10% | 15% | 5% | 10% |
| 6 | 600 | 12% | 18% | 6% | 12% |
| 7 | 700 | 14% | 21% | 7% | 14% |
| 8 | 800 | 16% | 24% | 8% | 16% |

*(Formulas: Relay Overhead = 0.02×IP; Wear Resistance = 0.03×IP; Combat Damage = 0.01×IP; Footprint = 0.02×IP.)*

| Type | Relay | Wear Res | Combat Dmg | Footprint | Identity |
|---|---|---|---|---|---|
| Standard | 1.0 | 1.0 | 1.0 | 1.0 | Baseline |
| Relay-Tuned | 1.6 | 0.8 | 0.8 | 0.9 | The Relay AGP Card — Networking specialist |
| Cryo-Stable | 0.8 | 1.6 | 0.8 | 0.9 | The Cryo-Stable AGP Card — Wear specialist |
| Combat-Rated | 0.8 | 0.8 | 1.6 | 0.9 | Raw damage specialist |
| Overhead | 0.85 | 0.85 | 0.85 | 1.5 | Footprint specialist — heavier Programs per step |
| Balanced | 1.15 | 1.15 | 1.1 | 1.1 | No specialty, solid across the board |
| Botnet | 1.3 | 0.9 | 1.0 | 0.8 | Best on zombie/relay-chain-heavy Breaching builds |
| Salvage | 0.9 | 1.2 | 0.9 | 0.9 | Cheap to craft, biased toward Wear resistance |

---

## 13. NPCs, Nodes & the World Roster

Population splits along the Subnet line drawn in §3: **Static Subnets** carry the fixed, hand-authored NPC roster below — persistent individuals you can grind, spar with, or quest through, spatially organized by IP range so a player can "read" a subnet's danger/purpose at a glance. **Random Subnets** carry procedurally spawned **nodes** instead — ephemeral, regenerated on every connection, reusing the same tier vocabulary the NPC roster already established rather than inventing a parallel one.

### Static Subnet roster

- **Mining NPCs** — one per resource, e.g. Warehouse Admin (Duct Tape), Shipping Supply Admin (Germanium), Warehouse Operator (YBCO), Government Supply Admin (Plutonium — notably rarer, fewer nodes). These same warehouse systems are the fixed non-player target for a Redirect finalize action (§7, §9), alongside any resource-carrying player.
- **Banking System nodes** — NPC-run cash depots, one per Network (e.g., Branch Manager), the fixed non-player target for a Cash-drain finalize action — same system/firewall machinery as anything else here, just holding Cash instead of mined resources or files.
- **Attack NPCs** — a clean level ladder players grind through: **Script Kiddie → Script Guru → Hobby Hacker → Career Hacker → Hacking Guru → "1337" Hacker.** This ladder alone communicates the game's whole power fantasy in six names, and doubles as the tier vocabulary Random Subnet patrol nodes borrow below.
- **Quest NPCs** — named individuals with titles and personality, e.g. Katja (Security Specialist), Chet (Website Designer), Ombeke (Private Detective), Ragnar (Digital Assassin), Zahara (Computer Repair Expert), Marvin (Nerd), Ming (Shipping Specialist), Alexi (Financial Manager), Dedrick ("The Anti-Noob") — residents of Quest Subnets specifically (§3, §14).

> **Design Gap:** the Attack NPC ladder and Mining NPC roster are finished — names, tiers, and roles are settled and ready to use as-is. Quest NPCs have names and titles but no written dialogue or quest content yet; that's real, from-scratch writing work still ahead.

### Random Subnet nodes

A Random Subnet rolls a **Danger Tier** (1–6) at generation, reusing the Attack-NPC ladder as its difficulty vocabulary. Danger Tier sets patrol density and toughness, the loot-quality band Cache nodes roll against (§11's existing weighted drop tables, just scaled), and how often a Cache node carries a Route Fragment. A subnet reached by nesting one level deeper (§3) rolls its Danger Tier with a bump over its parent's, capped at 6 — depth is itself a difficulty signal.

| Danger Tier | Patrol Analog | Patrol Detection | Loot Quality Band | Route Fragment Odds |
|---|---|---|---|---|
| 1 | Script Kiddie | Low | Common-heavy | None |
| 2 | Script Guru | Low–Medium | Common/Uncommon | Rare |
| 3 | Hobby Hacker | Medium | Uncommon-heavy | Occasional |
| 4 | Career Hacker | Medium–High | Uncommon/Rare | Common |
| 5 | Hacking Guru | High | Rare-heavy | Frequent |
| 6 | "1337" Hacker | Very High | Rare, some guaranteed-unique | Guaranteed |

- **Patrol nodes** — lightweight simulated Computers (same system/firewall/rig machinery as a player, §4) that actively search for connected players rather than sitting still. Detection runs on a Tracer Subroutine (§7) tied to the Subnet's controlling faction — Breaching's existing stealth curve slows how fast it accrues, so high Breaching genuinely buys more time before a patrol notices, not just better odds once caught. A patrol that detects a player plays a Kit against them exactly like a player would, on AI tuned to its Danger Tier (Script Kiddie plays close to randomly from its Kit; "1337" Hacker plays close to optimally) — no new combat model, just this system's own reason to trigger the existing one.
- **Cache nodes** — the "chests": a no-combat interact action that rolls the subnet's Danger-Tier-scaled drop table (§11). A fraction are trapped, using the same rolled resist/counter flavor firewalls already have (§7, §12) — a sprung trap either pings nearby patrol nodes toward the player's position or lands a single weak counter-hit, never a full combat exchange of its own.
- **Route Fragments** — a new lootable file type (`ROUTE`, alongside the existing `QUEST_ITEM`, §4), dropped only by Cache nodes per the odds above. Using one permanently reveals one previously-hidden Random Subnet entry point within the current Network on the player's map — loot with a structural payoff, not just an economic one, and the intended way most players find their next Random Subnet once the easy Enumeration-scan ones (§3) run out.

---

## 14. Quests, Bounties & Faction Standing

Quest **state tracking** (current/completed/involved quest IDs, per-quest task-completion maps) is simple and server-owned. Quest **content** (dialogue, branching, reward text) belongs inside the game server, authored and versioned alongside everything else, not federated out to a second system that can silently drift or go dark. Quest rewards are ordinary typed inventory files (a `QUEST_ITEM` type) — no separate reward system needed.

Bounties (§11) are the natural complement: quests are authored, curated content; bounties are emergent contracts, usually player-authored but sometimes posted by a faction itself. Both should exist, and both now feed the system below — a faction-branded quest and a faction-posted bounty pay into it exactly the same way.

### Faction Standing

Every player carries a single **Standing** value, −100 (full Undernet) to +100 (full Meridian), 0 being unaligned (§2). It's one axis, not two independent trackers — the "diametrically opposed" framing is load-bearing: completing a Meridian quest pushes Standing up, which by construction pushes it away from the Undernet end, and vice versa. The sidebar HUD (§17) reads it as two meters pivoting off a shared center for legibility, but underneath it's one number.

Standing gates access to better-paying, more consequential work from whichever faction it's closest to — the corporate ladder and the underground ladder both exist, and climbing one is, by the same motion, descending the other:

| Standing (either side) | Band | Access |
|---|---|---|
| 0–20 | Unaligned | Baseline jobs from either faction, no exclusivity |
| 20–50 | Contact | Mid-tier paid jobs; first faction-exclusive Quest NPCs |
| 50–80 | Trusted | High-value jobs; faction-exclusive gear and recipes |
| 80–100 | Inner Circle | Endgame jobs and capstone rewards; safe passage on that faction's home turf (§3) instead of raised `attack_probability` |

**Switching sides is possible, and meant to cost something.** Because Standing is one shared axis, grinding the opposite faction doesn't just build a second reputation — it actively unwinds the first. A maxed Meridian contact who starts running Undernet jobs is choosing to spend down Inner Circle access, not stacking a second one on top; that's a real decision with a real cost, not an accounting quirk.

### Weekly Faction Points

Standing caps at 100 per side, but faction-aligned work keeps paying out past that cap through a separate, uncapped **Faction Points** counter that resets every week. The same quest or bounty that moves Standing (while there's still room to move) always awards Faction Points too — so a brand-new recruit and an Inner Circle veteran are both earning toward the same weekly ladder, just for different reasons.

| Weekly Points | Reward |
|---|---|
| 50 | Cash payout |
| 150 | Mined-resource bundle, tier scaled to average character level |
| 300 | Equipment crafting materials, upper-tier band (§12) |
| 500 | Guaranteed Route Fragment (§13) or a faction-exclusive cosmetic |

This is the answer to "why keep working for a faction I've already maxed out with": Standing is a one-time ladder to climb, but Points are a reason to keep showing up every week regardless of where on that ladder a player already stands.

### Where Trace bounties fit

The full Trace System — how a Trace Level builds, decays, and triggers — lives in §7, since it's fed by combat. What lands here is just the payoff: a maxed corp or hacker-faction Trace posts a bounty through the exact same system described above, no separate mechanism needed. It's a bounty source alongside ordinary player-posted contracts — authored by an entity instead of a person, claimed the same way, paid out the same way. A maxed *player* Trace skips the Bounty Board entirely and becomes a private, standing target on the tracer's own network instead (§7) — the one case where "being traced" doesn't mean "being hunted publicly."

---

## 15. Loadout Logic Puzzles

A safe, zero-stakes practice space, decoupled from the live economy's attack surface, that ramps a player up on sequencing conditional rules into a working Loadout (§8, §10). A puzzle here is a fixed scenario (a known firewall, a known set of Subroutines, a fixed Footprint budget) and a target outcome; the player arranges an ordered rule list and submits it, graded by whether it produces the right sequence of plays against that scenario.

Fifteen puzzles, spanning a genuine difficulty curve:

| Difficulty band | Examples | Reward range |
|---|---|---|
| Tutorial (level 0, "clue" flagged) | First Rule, Two Conditions, Order Matters, Break the Tie, Priority Chain | $0 / 0 XP — practice only |
| Early (level 5–15) | Spend It All, The Overdraft, Cheap vs. Expensive | $200–$2,500 / 25–500 XP |
| Mid (level 25–40) | Three Flavors, The Decoy Subroutine | $3,000–$4,000 / 750–1,000 XP |
| Advanced (level 30–50) | Wear and Tear, The Long Fight, The Nested Dive | $3,200–$4,200 / 800–1,200 XP |
| Capstone | A full simulated run against a named Attack-NPC tier — real stakes shape, no real risk | top-tier reward |

This track matters beyond XP for the same reason it always did: it's the game's **onboarding ramp**, letting a brand-new player learn how a Loadout actually thinks — condition, priority, budget — with zero risk before ever configuring one with real economic stakes riding on it.

---

## 16. Discovery & Social Systems

**Subnet occupant scanning:** a live, session-scoped discovery surface distinct from the search index below — anyone connected to the same Static or Random Subnet (§3) can see who else is currently in it, no skill gate required. Far narrower than the search engine (scoped to one subnet, one moment) but far cheaper, which is exactly what makes a contested Subnet feel dangerous rather than decorative.

**Search engine:** not a lookup table — a real inverted-index engine with TF-IDF-style ranking. Every hosted site's content — a Player Subnet's website (§3) or an NPC's — gets tokenized, stripped of stop-words, and indexed; searches merge per-term site lists and rank by combined score, returning the top 10 results. Indexing is activity-gated: only recently-active sites stay searchable, so an abandoned Player Subnet naturally falls out of the discovery surface instead of cluttering it forever. That gate isn't a separate timer to tune — it's a direct consequence of the 7-day dormancy rule (§4): once a Player Subnet owner goes dormant, their site goes dark and drops out of the index for the same reason their system drops off the attack surface. This system is the connective tissue of the entire game — worth building as a real, tunable search service, not faking it.

**Browser:** a genuine embedded HTML renderer, not a stylized fake UI — pages are server-generated HTML fetched live and rendered with real link navigation and form submission. Visiting another player's or NPC's Computer surfaces a tabbed control panel over their machine (System, Firewall, Files, Games, Commodities, hardware slots, Bounties, and — for a Player Subnet — its Website) — a real, necessary window for the slice of play that's genuinely about a rendered page, reached from the Network Map (§17), which is the client's default view and the primary way a player gets anywhere.

**Voting:** visited sites carry a visible vote count and a per-player daily vote budget — a lightweight popularity/discoverability signal layered on top of raw search ranking. The client's browser home page (§17) surfaces the current top-voted sites, giving popularity a real payoff beyond bragging rights.

**Advertising:** a player can spend to advertise their site, boosting its traffic revenue (§11) and planting backlinks on NPC-owned sites — both raise the odds someone stumbles onto it through ordinary browsing or search, which matters most for a Player Subnet's owner (§3), since more eyes on the site means more attempts at Locating the subnet behind it.

**Locating a Player Subnet:** finding one (§3) starts with finding its website — through search, the top-voted list above, or a backlink. From there, a player can attempt to **Locate** it: a separate, Enumeration-flavored success check rolled against the subnet's default elevated scan resistance (§3), distinct from the combat-driven Trace Level of §7. A successful Locate grants a **temporary Trace, valid for 2 hours**, letting the locating player connect into that subnet once within the window (§7).

**Chat:** real-time channels, capacity-capped at **120 users**, with automatic overflow channels when a room fills, password-protectable rooms, an admin-transfers-on-departure ownership model, whispers, and a friends/ignore (relations) list. Solid, provable-at-scale defaults — keep them as the starting caps and tune from there.

---

## 17. Client Experience

The client presents itself as a single piece of software the player is running — not a conventional game menu, but a dark control-surface shell holding several independent panel-windows, each one a distinct instrument rather than a generic screen. Windows can be dragged, resized, and stacked freely inside that shell; a docked launcher along one edge is how a player opens, closes, and switches between them. The whole shell reads like a technician's control console rather than a game HUD: color leans monochrome — one glowing accent color against a near-black background — with a faint scanline/glow treatment on whichever window is active. Type is monospace wherever the fiction calls for something a real machine would print — status readouts, file listings, scan output, the scrolling log of a live attack — and switches to ordinary proportional type only on the handful of screens genuinely meant to read like a rendered web page (the Web Browser, Chat).

A persistent instrument panel runs down one side of the shell, visible no matter which windows are open, carrying the whole "vitals" read at a glance:
- A real-time **CPU load gauge**, rendered as an analog dial with a moving needle rather than a flat bar — it reads as a piece of equipment, not a progress meter.
- Cash, Bitcoin, the **Hack-o-Meter**, and the **Vote-o-Meter**, each a small digital counter.
- The five mined-resource counts, shown as compact tally chips.
- Current **Faction Standing** (§14), shown as two needles pivoting from a shared center point rather than one flat number — how far each needle has swung tells you your standing at a glance.
- The player's IP address, stamped in one corner the way a real device prints its own identifier — this is the player's in-world identity, and it's always on screen.

Opening a window pulls up its own dedicated instrument panel rather than a generic template: a scan result looks like scan output, a file listing looks like a real directory, and a live attack renders as a scrolling monospace log of the exact actions being played — one line per step — so a fight reads like a transcript as much as it plays like a minigame. Connecting into a target for the first time (§7's Initiate Breach) plays out as a brief handshake/authenticating transition rather than an instant cut, reinforcing that the player is establishing a real connection, not opening a menu.

### Network Map — the primary navigation surface

The Map is the client's **default view on login** — a real spatial layout of every known Network, its Subnets (§3), and any bookmarked or recently-visited targets within them, rendered as a dark geospatial display: every node glows against a satellite-style backdrop, the same visual language a real reconnaissance or geolocation tool would use. Static and Quest Subnets sit on it permanently once known; Random Subnets appear only when a scan's independent roll reveals one to *that specific player* (§3), so no two players' maps necessarily look alike. Faction-controlled turf (Meridian Corporate Backbone, The Undernet, §14) tints visibly hostile or safe depending on the viewing player's own Standing, giving `attack_probability` (§3) a visual read at a glance instead of a hidden number.

Opening any node — a Network, a Subnet, or a target you're currently connected to via occupancy or a held Trace (§3, §7) — from the Map is how a player actually gets anywhere. The Web Browser (§16) still opens beneath it for the slice of play that's genuinely about a rendered page (a Player Subnet's website, a bounty board), but it's a window the Map sends you to, not the app's front door.

Every other screen is one of these same instrument-panel windows, opened from the Map or the launcher: **Network Map** (the default landing view, above); **Home**, a file-directory-and-shop hybrid styled like a real filesystem browser; **System Management**, a status console for your own system — on/off toggle, assigned firewall, health readout, heal action, honeypot/deception toggle; **System Scan**, recon rendered as scrolling diagnostic text; **Files**, a raw browse/download/delete directory listing; **Web Browser** (as above); **Rig Builder & Loadout Editor** (§10), presented as a schematic-style component board; **Chat**, a plain text comms window; **Inventory/Equipment**, a slotted equipment grid including PCI/AGP card bays; **Bounty Board**, styled like a contract listing; and a **guided Tutorial** overlay that gates progress on the player actually performing the taught action rather than just clicking through text.

---

## 18. Systems Architecture (engineering reference)

At a high level, four cooperating services, unified around one identity model (every Computer keyed by its own IP):

- **Game server** — authoritative simulation: computers, systems, attacks, firewalls, economy. Owns the attack-step resolution and runs the dormancy sweep that auto-disables a player's system after 7 consecutive offline days (§4). Live Kit play, Loadout evaluation, and AI-driven Kits (zombie relays, Patrol/Attack NPCs, §7, §13) all resolve inside this same authoritative state — deliberately scoped out of this document's detail beyond that, since the resolution engine's implementation is an engineering concern rather than a game-design one.
- **Search service** — indexes player/NPC HTTP content; serves discovery.
- **Chat service** — channels, whispers, relations; independent of game-server load so combat spikes don't degrade chat.
- **Web/account layer** — login, account creation, and the authoritative home for quest and marketing content.

> **Design Gap, load-bearing:** this design has no authentication system and no admin/moderation panel specified anywhere. Both are required for any live multiplayer service and need real design work — user accounts, session security, moderation tooling, all of it — before this could safely launch. Neither should be treated as already solved just because the game around them is fully designed.

---

## 19. Monetization & Live-Ops Levers

The monetization surface is small, explicit, and gameplay-relevant rather than cosmetic-only:

- **Runtime Footprint ceiling tier** — free accounts cap Runtime Footprint (§7) lower than paying accounts, gating how much a player can spend per attack step. This is a meaningfully different (and more skill-respecting) lever than a generic XP-boost pass — a bigger budget lets a player chain more abilities in a step, it doesn't make any single ability hit harder.
- **Vanity/domain pack** — a small one-time purchase (~$5) unlocking extra domain aliases for your website.

Both are solid starting points; neither compromises the "your rig is your arsenal" pillar (§1) by letting money buy raw combat power directly.

---

## 20. Known Design Gaps

These are places the design is genuinely unfinished — flagged rather than guessed at:

1. **Installing-cost formula** — target cost numbers exist elsewhere in this document (§9), but the actual formula that should generate them from a player's equipment isn't designed yet; needs a fresh, server-owned design (§8).
2. **Quest dialogue/content** — NPC names and titles are settled; the quest text itself hasn't been written. Needs real writing from scratch.
3. **Firewall Subroutine full catalog** — the shape is designed (the Wear/Progress-reset/Trace taxonomy in §7), but the complete, tuned catalog of named Subroutines isn't written yet.
4. **Anti-abuse/bot protection** — no anti-automation system is designed yet. Worth an explicit, well-documented system given how bot-friendly a live-multiplayer hacking MMO inherently is.
5. **Internal balance simulator** — no internal tuning tool exists for testing firewall-tier math and combat balance as content is added. Not a player-facing feature, but worth building — exactly the kind of harness that keeps that math honest over time.
6. **Player Subnet numbers & guild governance** — scan-resistance values, traffic-revenue scaling, extra storage capacity, node-linking limits, upgrade costs, and Locate's exact success-chance formula (§3, §16) are all unspecified; Guild Subnet ownership and permissions aren't designed at all.
7. **Ability/action unlock gating** — the abilities gated behind Category Track progress are covered by the Career Track (§5). Everything else across §9's per-category and Program tables — the baseline recon actions, the AGP shipment tools, and every unique Offense/Defense Program ability — still has no defined unlock: gear tier, Marketplace cost, quest completion, or just available by default is all still undecided. The Runtime Footprint costs in §9 are a first pass and haven't been balance-tested against actual CPU Max Load and Memory Concurrent Execution budgets (§7, §12).
8. **Complete-miss roll formula** — used to be a Fortification-skill comparison between attacker and defender; now that skills are gone, the actual odds/formula behind a firewall failing outright (§7) needs a new basis, most likely tied to gear tier.
9. **Noob-protection exact definition** — "any account that hasn't yet moved past its starting gear" (§7) is a placeholder. The precise qualifying condition, and how to prevent a player from gaming it by deliberately never upgrading, isn't designed.
10. **Network access quest chains** — the actual quest chains gating Meridian Corporate Backbone, Plutonium Corridor, and The Undernet (§3) aren't written, and every additional Network planned for the future will need its own.
11. **Money-laundering and active-defense-monitoring content** — the Heat/Wash Cash/Structure Deposit/Money Mule/Fence Goods/Rewrite Ledger mechanics and the Watch Drills/Watch Log/Counter-Recon/Panopticon mechanics both lived inside the now-removed skill system, and the deposit-ladder abilities were pulled from the Ability Index entirely (§9). The underlying ideas are worth revisiting, but need to be rebuilt from scratch as equipment- or Program-based systems rather than skill progression.
12. **Program Item Power conversion** — the Career Track's Item Power bonus (§5) scales a hardware stat block through the Value = Intercept + Slope × Item Power formula (§12); a Program has one ability instead of a stat block, and exactly how Item Power turns into a bigger number on that one ability — for each of the 36 named Programs across the six Program categories — hasn't been worked out yet.
