object DemoLiteScopePolicy {
    val runtimeSettingsOverrides: Map<String, String> =
        linkedMapOf(
            "server.name" to "Fight Caves Demo Lite",
            "storage.data" to "./data/",
            "storage.data.modified" to "./temp/data/demo-lite-cache/modified.dat",
            "storage.cache.path" to "./data/cache/",
            "storage.wildcards" to "./temp/data/demo-lite-cache/wildcards.txt",
            "storage.caching.path" to "./temp/data/demo-lite-cache/",
            "storage.caching.active" to "false",
            "storage.players.path" to "./temp/data/demo-lite-saves/",
            "storage.players.logs" to "./temp/data/demo-lite-logs/",
            "storage.players.errors" to "./temp/data/demo-lite-errors/",
            "storage.autoSave.minutes" to "0",
            "storage.disabled" to "true",
            "events.shootingStars.enabled" to "false",
            "events.penguinHideAndSeek.enabled" to "false",
            "bots.count" to "0",
            "world.npcs.randomWalk" to "false",
            "spawns.npcs" to "tzhaar_city.npc-spawns.toml",
            "spawns.items" to "tzhaar_city.items.toml",
            "spawns.objects" to "tzhaar_city.objs.toml",
        )
}
