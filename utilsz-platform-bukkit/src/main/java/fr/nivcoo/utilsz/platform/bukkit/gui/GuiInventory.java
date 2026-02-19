package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public final class GuiInventory {

    public static final String TICK = "tick";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final HashMap<String, Object> values;
    private final Player player;
    private final GuiProvider provider;
    private final int rows;

    private final List<Integer> excludeCases;
    private final ClickableItem[] items;
    private org.bukkit.inventory.Inventory bukkitInventory;

    public GuiInventory(Player player, GuiProvider provider, Consumer<GuiInventory> params) {
        this.values = new HashMap<>();
        this.player = player;
        this.provider = provider;
        if (params != null) params.accept(this);

        this.excludeCases = provider.excludeCases(this);
        this.rows = provider.rows(this);
        this.items = new ClickableItem[9 * rows];

        Component initialTitle = provider.title(this);
        this.bukkitInventory = Bukkit.createInventory(
                player, rows * 9, initialTitle == null ? Component.empty() : initialTitle
        );

        put(TICK, 0);
    }

    public void updateTitle() {
        Component base = provider.title(this);
        updateTitle(base == null ? Component.empty() : base);
    }

    public void updateTitle(String title) {
        updateTitle(Component.text(title == null ? "" : title));
    }

    public void updateTitle(Component newTitle) {
        InventoryView view = player.getOpenInventory();
        if (!view.getTopInventory().equals(bukkitInventory)) return;

        Component current = view.title();
        String curPlain = PLAIN.serialize(current);
        String newPlain = PLAIN.serialize(newTitle);
        if (curPlain.equals(newPlain)) return;

        org.bukkit.inventory.Inventory newInv =
                Bukkit.createInventory(player, bukkitInventory.getSize(), newTitle);
        newInv.setContents(bukkitInventory.getContents());

        this.bukkitInventory = newInv;
        player.openInventory(newInv);
    }

    public Player getPlayer() {
        return player;
    }

    public GuiProvider getProvider() {
        return provider;
    }

    public org.bukkit.inventory.Inventory getBukkitInventory() {
        return bukkitInventory;
    }

    public int getRows() {
        return rows;
    }

    public List<Integer> getExcludeCases() {
        return excludeCases;
    }

    public void set(int col, int row, ClickableItem item) {
        if (col < 1 || col > 9) throw new IllegalArgumentException("col must be between 1 and 9 but is " + col);
        if (row < 1 || row > rows) throw new IllegalArgumentException("row must be between 1 and " + rows);
        set(locToPos(col, row), item);
    }

    public void set(int pos, ClickableItem item) {
        if (pos < 0 || pos > rows * 9 - 1)
            throw new IllegalArgumentException("pos must be between 0 and " + (rows * 9 - 1) + ", but is " + pos);
        items[pos] = item;
        bukkitInventory.setItem(pos, item.getItemStack());
    }

    public void fill(ClickableItem item) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                set(r * 9 + c, item);
            }
        }
    }

    public void rectangle(int col, int row, int width, int height, ClickableItem item) {
        if (col < 1 || col > 9) throw new IllegalArgumentException("col must be between 1 and 9");
        if (row < 1 || row > rows) throw new IllegalArgumentException("row must be between 1 and rows");
        if (width < 1 || width > 10 - col) throw new IllegalArgumentException("width must be between 1 and " + (10 - col));
        if (height < 1 || height > rows + 1 - row) throw new IllegalArgumentException("height must be between 1 and " + (rows + 1 - row));
        rectangle(locToPos(col, row), width, height, item);
    }

    public void rectangle(int pos, int width, int height, ClickableItem item) {
        int[] cr = posToLoc(pos);
        int col = cr[0];
        int row = cr[1];
        checkInventoryData(pos, col, row, width, height);
        for (int i = col; i < col + width; i++) {
            for (int j = row; j < row + height; j++) {
                if (i == col || i == col + width - 1 || j == row || j == row + height - 1) {
                    set(i, j, item);
                }
            }
        }
    }

    public void fillRectangle(int col, int row, int width, int height, ClickableItem item) {
        checkInventoryData(null, col, row, width, height);
        fillRectangle(locToPos(col, row), width, height, item);
    }

    public void fillRectangle(int pos, int width, int height, ClickableItem item) {
        int[] cr = posToLoc(pos);
        int col = cr[0];
        int row = cr[1];
        checkInventoryData(pos, col, row, width, height);
        for (int i = col; i < col + width; i++) {
            for (int j = row; j < row + height; j++) {
                set(i, j, item);
            }
        }
    }

    public void checkInventoryData(Integer pos, Integer col, Integer row, Integer width, Integer height) {
        if (pos != null && (pos < 0 || pos >= rows * 9))
            throw new IllegalArgumentException("pos must be between 0 and " + (rows * 9 - 1) + ", but is " + pos);
        if (col != null && (col < 1 || col > 9))
            throw new IllegalArgumentException("col must be between 1 and 9, but is " + col);
        if (row != null && (row < 1 || row > rows))
            throw new IllegalArgumentException("row must be between 1 and rows, but is " + row);
        if (width != null && col != null && (width < 1 || width > 10 - col))
            throw new IllegalArgumentException("width must be between 1 and " + (10 - col) + ", but is " + width);
        if (height != null && row != null && (height < 1 || height > rows + 1 - row))
            throw new IllegalArgumentException("height must be between 1 and " + (rows + 1 - row) + ", but is " + height);
    }

    public void open() {
        player.openInventory(bukkitInventory);
    }

    public void handleClick(InventoryClickEvent e) {
        int pos = e.getSlot();
        if (pos < 0 || pos >= items.length) return;
        ClickableItem item = items[pos];
        if (item == null) return;
        item.run(e);
    }

    public void put(String key, Object value) {
        values.put(key, value);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public int[] posToLoc(int pos) {
        return new int[]{ (pos % 9) + 1, (pos / 9) + 1 };
    }

    public int locToPos(int col, int row) {
        return (row - 1) * 9 + (col - 1);
    }
}
