import org.koin.dsl.module

data class HeadlessRuntimeConfig(
    val mode: String = RuntimeMode.Headless.id,
    val networkingEnabled: Boolean = false,
)

object HeadlessModules {
    fun module() = module {
        single { HeadlessRuntimeConfig() }
    }
}
