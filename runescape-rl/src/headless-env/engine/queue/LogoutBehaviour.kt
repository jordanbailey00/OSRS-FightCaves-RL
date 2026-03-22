package sim.engine.queue

enum class LogoutBehaviour {
    /**
     * Execute immediately
     */
    Accelerate,

    /**
     * Don't process
     */
    Discard,
}
