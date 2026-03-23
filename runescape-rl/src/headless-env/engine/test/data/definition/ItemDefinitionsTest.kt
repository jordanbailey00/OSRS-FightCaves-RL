package sim.engine.data.definition

import sim.cache.definition.data.ItemDefinition
import sim.cache.definition.decoder.ItemDecoder

internal class ItemDefinitionsTest : DefinitionsDecoderTest<ItemDefinition, ItemDecoder, ItemDefinitions>() {

    override var decoder: ItemDecoder = ItemDecoder()
    override lateinit var definitions: Array<ItemDefinition>
    override val id: String = "lit_candle"
    override val intId: Int = 34

    override fun expected(): ItemDefinition = ItemDefinition(
        intId,
        stringId = id,
        extras = mutableMapOf(
            "examine" to "A candle.",
        ),
    )

    override fun empty(): ItemDefinition = ItemDefinition(-1)

    override fun definitions(): ItemDefinitions = ItemDefinitions.init(definitions)

    override fun load(definitions: ItemDefinitions) {
        val uri = ItemDefinitionsTest::class.java.getResource("test-item.toml")!!
        ItemDefinitions.load(listOf(uri.path))
    }
}
