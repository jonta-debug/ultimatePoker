import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

public class PokerAssets {
    private static final int CARD_START_X = 1;
    private static final int CARD_START_Y = 1;
    private static final int CARD_STEP_X = 48;
    private static final int CARD_STEP_Y = 64;
    private static final int CARD_CELL_WIDTH = 46;
    private static final int CARD_CELL_HEIGHT = 62;
    private static final int DISPLAY_CARD_WIDTH = 80;
    private static final int DISPLAY_CARD_HEIGHT = 108;

    private static final String PRIMARY_ASSET_DIR = "assets/Poker cards 1.3";
    private static final String LEGACY_ASSET_DIR = "Poker cards 1.3";
    private static final String CARD_SHEET = "1.2 Poker cards.png";
    private static final String DECK_SHEET = "Deck of cards ( full cards ).png";
    private static final String CHIP_SHEET = "fiches addon (Poker Cards).png";

    private final BufferedImage cardSheet;
    private final BufferedImage deckSheet;
    private final BufferedImage chipSheet;
    private final Map<String, ImageIcon> scaledCache;
    private final Map<Suit, Integer> suitRows;
    private final Map<Rank, Integer> rankColumns;

    public PokerAssets() {
        try {
            File assetDir = resolveAssetDir();
            this.cardSheet = ImageIO.read(new File(assetDir, CARD_SHEET));
            this.deckSheet = ImageIO.read(new File(assetDir, DECK_SHEET));
            this.chipSheet = ImageIO.read(new File(assetDir, CHIP_SHEET));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load poker assets", exception);
        }

        this.scaledCache = new HashMap<>();
        this.suitRows = new EnumMap<>(Suit.class);
        this.rankColumns = new EnumMap<>(Rank.class);

        suitRows.put(Suit.HEARTS, 0);
        suitRows.put(Suit.DIAMONDS, 1);
        suitRows.put(Suit.SPADES, 2);
        suitRows.put(Suit.CLUBS, 3);

        rankColumns.put(Rank.ACE, 0);
        rankColumns.put(Rank.TWO, 1);
        rankColumns.put(Rank.THREE, 2);
        rankColumns.put(Rank.FOUR, 3);
        rankColumns.put(Rank.FIVE, 4);
        rankColumns.put(Rank.SIX, 5);
        rankColumns.put(Rank.SEVEN, 6);
        rankColumns.put(Rank.EIGHT, 7);
        rankColumns.put(Rank.NINE, 8);
        rankColumns.put(Rank.TEN, 9);
        rankColumns.put(Rank.JACK, 10);
        rankColumns.put(Rank.QUEEN, 11);
        rankColumns.put(Rank.KING, 12);
    }

    private File resolveAssetDir() {
        File primaryDir = new File(PRIMARY_ASSET_DIR);
        if (primaryDir.isDirectory()) {
            return primaryDir;
        }

        File legacyDir = new File(LEGACY_ASSET_DIR);
        if (legacyDir.isDirectory()) {
            return legacyDir;
        }

        throw new IllegalStateException(
            "Could not find asset directory. Expected '" + PRIMARY_ASSET_DIR + "' or '" + LEGACY_ASSET_DIR + "'.");
    }

    public ImageIcon getCardIcon(Card card) {
        return scaledIcon("card-" + card.getSuit() + "-" + card.getRank(),
            sliceCard(card), DISPLAY_CARD_WIDTH, DISPLAY_CARD_HEIGHT);
    }

    public ImageIcon getCardBackIcon() {
        return scaledIcon("card-back", sliceSheetCell(0, 4),
            DISPLAY_CARD_WIDTH, DISPLAY_CARD_HEIGHT);
    }

    public ImageIcon getDeckIcon() {
        return scaledIcon("deck-stack", deckSheet.getSubimage(48, 356, 51, 76), 92, 138);
    }

    public ImageIcon getPlaceholderIcon() {
        String key = "placeholder";
        if (!scaledCache.containsKey(key)) {
            BufferedImage image = new BufferedImage(DISPLAY_CARD_WIDTH, DISPLAY_CARD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(new Color(232, 243, 236, 36));
            graphics.fill(new RoundRectangle2D.Double(0, 0, DISPLAY_CARD_WIDTH - 1, DISPLAY_CARD_HEIGHT - 1, 20, 20));
            graphics.setColor(new Color(232, 243, 236, 85));
            graphics.draw(new RoundRectangle2D.Double(1, 1, DISPLAY_CARD_WIDTH - 3, DISPLAY_CARD_HEIGHT - 3, 20, 20));
            graphics.dispose();
            scaledCache.put(key, new ImageIcon(image));
        }
        return scaledCache.get(key);
    }

    public ImageIcon getChipStackIcon(double amount) {
        int chipCount = Math.max(1, Math.min(6, (int) Math.ceil(amount)));
        BufferedImage chip = selectChip(amount);
        int width = 34;
        int height = 18 + chipCount * 10;
        BufferedImage stack = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = stack.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (int index = 0; index < chipCount; index++) {
            int y = height - 24 - (index * 8);
            graphics.drawImage(chip, 1, y, 32, 44, null);
        }

        graphics.dispose();
        return new ImageIcon(stack.getScaledInstance(30, Math.max(40, height), Image.SCALE_SMOOTH));
    }

    private BufferedImage sliceCard(Card card) {
        int column = rankColumns.get(card.getRank());
        int row = suitRows.get(card.getSuit());
        return sliceSheetCell(column, row);
    }

    private BufferedImage sliceSheetCell(int column, int row) {
        int x = CARD_START_X + (column * CARD_STEP_X);
        int y = CARD_START_Y + (row * CARD_STEP_Y);
        return cardSheet.getSubimage(x, y, CARD_CELL_WIDTH, CARD_CELL_HEIGHT);
    }

    private BufferedImage selectChip(double amount) {
        int row;
        if (amount <= 1) {
            row = 1;
        } else if (amount <= 5) {
            row = 0;
        } else if (amount <= 10) {
            row = 3;
        } else if (amount <= 25) {
            row = 2;
        } else {
            row = 1;
        }

        int column = amount > 10 ? 4 : 0;
        return chipSheet.getSubimage(column * 48, 4 + (row * 48), 32, 44);
    }

    private ImageIcon scaledIcon(String key, BufferedImage source, int width, int height) {
        String cacheKey = key + "-" + width + "x" + height;
        if (!scaledCache.containsKey(cacheKey)) {
            scaledCache.put(cacheKey, new ImageIcon(source.getScaledInstance(width, height, Image.SCALE_SMOOTH)));
        }
        return scaledCache.get(cacheKey);
    }
}
