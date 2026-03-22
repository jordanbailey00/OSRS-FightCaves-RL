Void is a RuneScape private game server emulating January 2011 (revision 634).

It's designed to be fast, lightweight and easy to write content for, a modern base that anyone can use.

# Players
- Getting Started
  - [Installation](installation-guide)
  - [First launch](installation-guide#step-6-launch-the-server)
  - [Game Settings](properties)
- Player Commands
  - [How to make yourself admin](player-rights#modifying-rights)
  - [Using commands](commands)
  - [Spawning items](commands#item)
- Troubleshooting
  - [Common issues](troubleshooting)

# Content Creation
- [Overview](content-creation)
  - [Content Added](content-progress)
  - [Content Planned](roadmap)
- Setup
  - [IDE Setup](https://github.com/GregHib/void#development)
  - Git Basics
    - [Updating](update)
    - [Rebasing](rebase)
- Core Concepts
  - [Scripts](scripts)
    - [Event Handlers](event-handlers)
    - [Interactions](interaction)
    - [Wildcards](wildcards)
  - [Entities](entities)
    - [Players](players)
    - [Bots](bots)
    - [NPCs](npcs)
    - [Objects](game-objects)
    - [Floor Items](floor-items)
  - [Interfaces](interfaces)
    - [Dialogues](dialogues)
    - [Inventories](inventories)
      - [Transactions](transactions)
      - [Shops](shops)
  - [Definitions](definitions)
  - [Config Files](config-files)
  - [Variables](character-variables)
  - [Clocks](clocks)
  - [Timers](timers)
  - Sounds
  - [Teleports](config-files#teleports)
- Guides
  - [How to read an error](Reading-errors)
  - [Update your fork](rebase)
  - [Creating a Script](https://github.com/GregHib/void/wiki/scripts#creating-a-script)
  - [Adding an npc](npcs#adding-npcs)
  - [Adding an object](game-objects#adding-object)
  - [Adding a item spawn](floor-items#adding-floor-items)
  - [Build your own client](client-building)
  - [Build your own cache](cache-building)
  - [Adding a shop](shops#adding-default-shops)
  - [How to auto-format code](Code-formatting)
  - [Save to PostgreSQL](storage#postgresql)
  - [Save to MySQL](storage#driver-support)

# Developers
- [Setup](https://github.com/GregHib/void#development)
- Architecture
  - [Philosophy](philosophy)
  - [String Ids](string-identifiers)
- Technical Concepts
  - [Wildcard system](wildcard-system)
  - [Queues](queues)
  - [Combat](combat-scripts)
    - [Combat lifecycle](combat-lifecycle)
  - [Monster Drops](drop-tables)
  - [Instances](instances)
  - [Hunt Modes](hunt-modes)
  - [Events](events)
  - Item Charges & Degrading
  - Movement Modes
  - [Bot Architecture](bot-architecture)
  - [Database Storage](storage)
  - Delta's
  - Drops and variables
- Contributing
  - [Guidelines](https://github.com/GregHib/void/blob/main/CONTRIBUTING.md)
