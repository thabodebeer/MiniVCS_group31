package minivcs.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.border.TitledBorder;

/**
 * the colours and fonts used by the GUI, kept in one place so they are easy to change.
 */
public final class Theme {

    private Theme() {
    }

    public static final Color NAVY = new Color(0x1F3A5F);        // title bar and panel titles
    public static final Color BACKGROUND = new Color(0xF4F6F9);  // window background
    public static final Color OUTPUT_BG = new Color(0xFAFAFA);   // output area

    // text colours for the changes list
    public static final Color STAGED = new Color(0x1E7B34);      // green
    public static final Color MODIFIED = new Color(0xB35C00);    // orange
    public static final Color DELETED = new Color(0xB3261E);     // red
    public static final Color UNTRACKED = new Color(0x666666);   // grey

    // header and button colours
    public static final Color HEADER = new Color(0x1E88E5);      // blue title bar
    public static final Color BTN_BLUE = new Color(0x2F80ED);
    public static final Color BTN_GREEN = new Color(0x43A047);
    public static final Color BTN_RED = new Color(0xE53935);
    public static final Color BTN_PURPLE = new Color(0x7B1FA2);
    public static final Color BTN_TEAL = new Color(0x00897B);
    public static final Color BTN_DARK = new Color(0x333333);

    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    /** a titled border with a navy, bold title. */
    public static TitledBorder titled(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0xC5CCD6)), title);
        border.setTitleColor(NAVY);
        border.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        return border;
    }

    /** the colour of a button, chosen from its label. */
    public static Color buttonColor(String text) {
        switch (text) {
            case "Stage selected":
            case "Stage all":
            case "Initialise repository":
                return BTN_GREEN;
            case "Unstage selected":
            case "Roll back to selected...":
                return BTN_RED;
            case "Diff selected vs working folder":
            case "Diff two selected commits":
                return BTN_PURPLE;
            case "Status":
            case "Show commit details":
                return BTN_TEAL;
            case "Activity log":
            case "Clear output":
                return BTN_DARK;
            default:
                return BTN_BLUE; // Browse..., Open, Commit
        }
    }

    /** gives a button a solid colour with white bold text; a disabled button is shown lighter. */
    public static void style(JButton b) {
        Color colour = buttonColor(b.getText());
        Color faded = new Color((colour.getRed() + 2 * 255) / 3, (colour.getGreen() + 2 * 255) / 3,
                (colour.getBlue() + 2 * 255) / 3);
        b.setUI(new BasicButtonUI() {   // the Windows look ignores button background colours
            @Override
            protected void paintText(Graphics g, AbstractButton button, Rectangle textRect, String text) {
                FontMetrics fm = g.getFontMetrics();
                g.setColor(button.getForeground());   // keep white text, also when disabled
                BasicGraphicsUtils.drawString(g, text, -1, textRect.x, textRect.y + fm.getAscent());
            }
        });
        b.setOpaque(true);
        b.setBackground(b.isEnabled() ? colour : faded);
        b.setForeground(Color.WHITE);
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        b.addPropertyChangeListener("enabled", e -> b.setBackground(b.isEnabled() ? colour : faded));
    }
}
