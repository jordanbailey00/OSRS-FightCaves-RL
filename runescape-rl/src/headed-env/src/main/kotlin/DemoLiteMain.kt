import javax.swing.SwingUtilities

object DemoLiteMain {
    @JvmStatic
    fun main(args: Array<String>) {
        SwingUtilities.invokeLater {
            val runtime = DemoLiteRuntime.create()
            DemoLiteUi(runtime).showWindow()
        }
    }
}
