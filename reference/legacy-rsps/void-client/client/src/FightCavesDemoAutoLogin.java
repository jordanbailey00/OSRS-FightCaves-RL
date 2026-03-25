import javax.swing.JFrame;
import javax.swing.SwingUtilities;

final class FightCavesDemoAutoLogin implements Runnable {
    private static volatile String username = "";
    private static volatile String password = "";
    private static volatile boolean enabled = false;
    private static volatile boolean hideUntilInGame = false;
    private static volatile boolean started = false;
    private static volatile boolean submitted = false;
    private static volatile boolean readyLogged = false;
    private static volatile boolean frameRevealed = false;
    private static volatile long startedAtMillis = 0L;
    private static volatile long submittedAtMillis = 0L;
    private static volatile JFrame frame = null;

    private FightCavesDemoAutoLogin() {
    }

    static void configure(String pendingUsername, String pendingPassword, boolean pendingHideUntilInGame) {
        username = pendingUsername == null ? "" : pendingUsername;
        password = pendingPassword == null ? "" : pendingPassword;
        enabled = !username.isEmpty() && !password.isEmpty();
        hideUntilInGame = pendingHideUntilInGame;
        started = false;
        submitted = false;
        readyLogged = false;
        frameRevealed = false;
        startedAtMillis = 0L;
        submittedAtMillis = 0L;
    }

    static void attachFrame(JFrame bootstrapFrame) {
        frame = bootstrapFrame;
    }

    static boolean isEnabled() {
        return enabled;
    }

    static synchronized void start() {
        if (!enabled || started) {
            return;
        }
        started = true;
        startedAtMillis = System.currentTimeMillis();
        Thread thread = new Thread(new FightCavesDemoAutoLogin(), "fight-caves-demo-auto-login");
        thread.setDaemon(true);
        thread.start();
    }

    public void run() {
        long lastStatusLogMillis = 0L;
        int lastClientState = Integer.MIN_VALUE;
        int lastLoginStage = Integer.MIN_VALUE;
        int lastModalState = Integer.MIN_VALUE;
        while (enabled) {
            try {
                long now = System.currentTimeMillis();
                int clientState = Class240.anInt4674;
                int loginStage = Class225.anInt2955;
                int modalState = Class367_Sub2.anInt7297;
                if (clientState != lastClientState || loginStage != lastLoginStage || modalState != lastModalState) {
                    lastClientState = clientState;
                    lastLoginStage = loginStage;
                    lastModalState = modalState;
                    FightCavesDemoBootstrapWindow.updateState(clientState, loginStage, modalState);
                    System.out.println(
                        "FIGHT_CAVES_DEMO_BOOTSTRAP_STATE elapsed_ms=" + (now - startedAtMillis) +
                            " client_state=" + clientState +
                            " login_stage=" + loginStage +
                            " modal_state=" + modalState
                    );
                }
                if (isReadyToReveal(clientState, loginStage)) {
                    revealFrame("ready_state_" + clientState, now);
                    if (!readyLogged && submittedAtMillis > 0L) {
                        readyLogged = true;
                        System.out.println(
                            "FIGHT_CAVES_DEMO_READY elapsed_ms=" + (now - startedAtMillis) +
                                " login_roundtrip_ms=" + (now - submittedAtMillis)
                        );
                    } else if (!readyLogged) {
                        readyLogged = true;
                        System.out.println("FIGHT_CAVES_DEMO_READY elapsed_ms=" + (now - startedAtMillis));
                    }
                    if (frameRevealed || !hideUntilInGame) {
                        return;
                    }
                }
                if (!submitted && clientState == 3 && loginStage == 0 && modalState == 0) {
                    submitted = true;
                    submittedAtMillis = now;
                    System.out.println("FIGHT_CAVES_DEMO_AUTOLOGIN_SUBMIT username=" + mask(username));
                    Class253.method1922(password, 0, username, true);
                }
                if (now - lastStatusLogMillis >= 5000L) {
                    lastStatusLogMillis = now;
                    System.out.println(
                        "FIGHT_CAVES_DEMO_AUTOLOGIN_WAIT elapsed_ms=" + (now - startedAtMillis) +
                            " client_state=" + Class240.anInt4674 +
                            " login_stage=" + Class225.anInt2955 +
                            " modal_state=" + Class367_Sub2.anInt7297
                    );
                }
                Thread.sleep(200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static boolean isReadyToReveal(int clientState, int loginStage) {
        return submittedAtMillis > 0L && loginStage == 0 && (clientState == 7 || clientState == 10 || clientState == 11);
    }

    private static void revealFrame(final String reason, long now) {
        if (!hideUntilInGame || frameRevealed) {
            return;
        }
        final JFrame currentFrame = frame;
        if (currentFrame == null) {
            return;
        }
        frameRevealed = true;
        System.out.println("FIGHT_CAVES_DEMO_FRAME_REVEAL reason=" + reason + " elapsed_ms=" + (now - startedAtMillis));
        SwingUtilities.invokeLater(
            new Runnable() {
                public void run() {
                    FightCavesDemoBootstrapWindow.closeWindow();
                    currentFrame.setVisible(true);
                    currentFrame.toFront();
                    currentFrame.requestFocus();
                }
            }
        );
    }

    private static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "<empty>";
        }
        if (value.length() <= 2) {
            return "**";
        }
        return value.substring(0, 2) + "***";
    }
}
