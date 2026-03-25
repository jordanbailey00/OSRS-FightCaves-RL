import javax.swing.*;
import java.awt.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

final class FightCavesDemoBootstrapWindow {
    private static volatile JFrame frame;
    private static volatile BootstrapPanel panel;
    private static volatile long startedAtMillis = 0L;
    private static volatile int clientState = 0;
    private static volatile int loginStage = 0;
    private static volatile int modalState = 0;

    private FightCavesDemoBootstrapWindow() {
    }

    static void showWindow() {
        startedAtMillis = System.currentTimeMillis();
        clientState = 0;
        loginStage = 0;
        modalState = 0;
        SwingUtilities.invokeLater(
            new Runnable() {
                public void run() {
                    if (frame == null) {
                        frame = buildFrame();
                        panel = new BootstrapPanel();
                        frame.getContentPane().add(panel, BorderLayout.CENTER);
                    }
                    panel.reset();
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                    frame.toFront();
                }
            }
        );
    }

    static void updateState(int currentClientState, int currentLoginStage, int currentModalState) {
        clientState = currentClientState;
        loginStage = currentLoginStage;
        modalState = currentModalState;
        final BootstrapPanel currentPanel = panel;
        if (currentPanel == null) {
            return;
        }
        SwingUtilities.invokeLater(
            new Runnable() {
                public void run() {
                    currentPanel.updateState(clientState, loginStage, modalState, System.currentTimeMillis() - startedAtMillis);
                }
            }
        );
    }

    static void closeWindow() {
        SwingUtilities.invokeLater(
            new Runnable() {
                public void run() {
                    if (panel != null) {
                        panel.finish();
                    }
                    if (frame != null) {
                        frame.setVisible(false);
                        frame.dispose();
                        frame = null;
                        panel = null;
                    }
                }
            }
        );
    }

    private static JFrame buildFrame() {
        JFrame bootstrapFrame = new JFrame("Client");
        bootstrapFrame.setLayout(new BorderLayout());
        bootstrapFrame.setSize(new Dimension(765, 503));
        bootstrapFrame.setResizable(false);
        bootstrapFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ArrayList<Image> icons = new ArrayList<Image>();
        for (String name : Arrays.asList("icon-16.png", "icon-32.png", "icon-64.png", "icon-128.png", "icon-256.png")) {
            URL resource = Loader.class.getResource(name);
            if (resource != null) {
                icons.add(new ImageIcon(resource).getImage());
            }
        }
        bootstrapFrame.setIconImages(icons);
        return bootstrapFrame;
    }

    private static final class BootstrapPanel extends JPanel {
        private final Timer timer;
        private int displayProgress = 2;
        private int targetProgress = 2;
        private String statusText = "Checking for updates - 2%";

        private BootstrapPanel() {
            setBackground(Color.BLACK);
            timer = new Timer(
                100,
                new java.awt.event.ActionListener() {
                    public void actionPerformed(java.awt.event.ActionEvent event) {
                        tick();
                    }
                }
            );
            timer.start();
        }

        void reset() {
            displayProgress = 2;
            targetProgress = 2;
            statusText = "Checking for updates - 2%";
            if (!timer.isRunning()) {
                timer.start();
            }
            repaint();
        }

        void updateState(int currentClientState, int currentLoginStage, int currentModalState, long elapsedMillis) {
            targetProgress = Math.max(targetProgress, progressFor(currentClientState, currentLoginStage, currentModalState, elapsedMillis));
            statusText = messageFor(currentClientState, currentLoginStage, currentModalState, Math.max(displayProgress, targetProgress));
            repaint();
        }

        void finish() {
            targetProgress = 100;
            displayProgress = 100;
            statusText = "Loading Fight Caves - 100%";
            repaint();
            timer.stop();
        }

        private void tick() {
            long elapsedMillis = System.currentTimeMillis() - startedAtMillis;
            targetProgress = Math.max(targetProgress, progressFor(clientState, loginStage, modalState, elapsedMillis));
            if (displayProgress < targetProgress) {
                displayProgress = Math.min(targetProgress, displayProgress + 1);
            }
            statusText = messageFor(clientState, loginStage, modalState, displayProgress);
            repaint();
        }

        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, getWidth(), getHeight());

                g.setColor(Color.WHITE);
                g.setFont(new Font("Dialog", Font.PLAIN, 20));
                drawCentered(g, "Loading - please wait.", getWidth() / 2, getHeight() / 2 - 48);

                int boxWidth = 304;
                int boxHeight = 34;
                int boxX = (getWidth() - boxWidth) / 2;
                int boxY = (getHeight() - boxHeight) / 2;

                g.setColor(new Color(140, 0, 0));
                g.drawRect(boxX, boxY, boxWidth, boxHeight);

                int fillWidth = Math.max(4, ((boxWidth - 4) * displayProgress) / 100);
                g.setColor(new Color(140, 0, 0));
                g.fillRect(boxX + 2, boxY + 2, fillWidth, boxHeight - 3);

                g.setColor(Color.WHITE);
                g.setFont(new Font("Dialog", Font.PLAIN, 18));
                drawCentered(g, statusText, getWidth() / 2, boxY + 23);
            } finally {
                g.dispose();
            }
        }

        private void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
            FontMetrics metrics = g.getFontMetrics();
            int textX = centerX - metrics.stringWidth(text) / 2;
            g.drawString(text, textX, baselineY);
        }

        private int progressFor(int currentClientState, int currentLoginStage, int currentModalState, long elapsedMillis) {
            if (currentModalState != 0) {
                return 94;
            }
            if (currentClientState <= 0) {
                return Math.min(28, 2 + (int) (elapsedMillis / 450L));
            }
            if (currentClientState == 1) {
                return 34;
            }
            if (currentClientState == 2) {
                return 46;
            }
            if (currentClientState == 3) {
                if (currentLoginStage == 0) {
                    return 56;
                }
                if (currentLoginStage == 2) {
                    return Math.min(66, 60 + (int) (elapsedMillis / 1500L));
                }
                if (currentLoginStage == 3) {
                    return Math.min(80, 64 + (int) (elapsedMillis / 1200L));
                }
                if (currentLoginStage == 4) {
                    return 84;
                }
                if (currentLoginStage == 11) {
                    return 90;
                }
                return 72;
            }
            if (currentClientState == 10 || currentClientState == 11) {
                return 96;
            }
            return 92;
        }

        private String messageFor(int currentClientState, int currentLoginStage, int currentModalState, int progress) {
            if (currentModalState != 0) {
                return "Finishing setup - " + progress + "%";
            }
            if (currentClientState <= 2) {
                return "Checking for updates - " + progress + "%";
            }
            if (currentClientState == 3 && currentLoginStage == 0) {
                return "Preparing login - " + progress + "%";
            }
            if (currentClientState == 3) {
                return "Logging in - " + progress + "%";
            }
            return "Loading Fight Caves - " + progress + "%";
        }
    }
}
