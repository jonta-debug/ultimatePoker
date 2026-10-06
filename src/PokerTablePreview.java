import java.awt.Dimension;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Temporary standalone entry point for visually testing PokerTablePanel.
 */
public final class PokerTablePreview {
    private PokerTablePreview() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PokerTablePreview::showPreview);
    }

    private static void showPreview() {
        List<String> playerNames = Arrays.asList("Ole", "Mia", "Noah", "Lena", "Tom", "Ella");
        JFrame frame = new JFrame("Ultimate Poker - Connected Table Preview");
        PokerTableController controller = new PokerTableController(
            playerNames, 20, 1, frame::dispose);

        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(controller.getView());
        frame.setMinimumSize(new Dimension(960, 640));
        frame.setSize(1280, 800);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        controller.startNewRound();
    }
}
