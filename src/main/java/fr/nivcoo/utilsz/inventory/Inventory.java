package fr.nivcoo.utilsz.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;

import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class Inventory {
    public static final String TICK = "tick";

    private final HashMap<String, Object> values;
    private final Player player;
    private final InventoryProvider inventoryProvider;
    private final int size;

    private final List<Integer> excludeCases;
    private final ClickableItem[] items;
    private org.bukkit.inventory.Inventory bukkitInventory;

    public Inventory(Player player, InventoryProvider inventoryProvider, Consumer<Inventory> params) {
        this.values = new HashMap<>();
        this.player = player;
        this.inventoryProvider = inventoryProvider;
        if (params != null) params.accept(this);
        this.excludeCases = inventoryProvider.excludeCases(this);
        this.size = inventoryProvider.rows(this);
        this.items = new ClickableItem[9 * size];

        Component initialTitle = inventoryProvider.title(this);
        this.bukkitInventory = Bukkit.createInventory(
                player, size * 9, initialTitle == null ? Component.empty() : initialTitle
        );

        put(TICK, 0);
    }

    public void updateTitle() {
        Component base = inventoryProvider.title(this);
        updateTitle(base == null ? Component.empty() : base);
    }

    public void updateTitle(String title) {
        updateTitle(Component.text(title == null ? "" : title));
    }

    public void updateTitle(Component newTitle) {
        Player p = getPlayer();
        InventoryView view = p.getOpenInventory();
        if (!view.getTopInventory().equals(bukkitInventory)) return;

        Component current = view.title();
        String curPlain = PlainTextComponentSerializer.plainText().serialize(current);
        String newPlain = PlainTextComponentSerializer.plainText().serialize(newTitle);
        if (curPlain.equals(newPlain)) return;

        org.bukkit.inventory.Inventory newInv =
                Bukkit.createInventory(p, bukkitInventory.getSize(), newTitle);
        newInv.setContents(bukkitInventory.getContents());

        this.bukkitInventory = newInv;
        p.openInventory(newInv);
    }

    public Player getPlayer() {
        return player;
    }

    public InventoryProvider getInventoryProvider() {
        return inventoryProvider;
    }

    public org.bukkit.inventory.Inventory getBukkitInventory() {
        return bukkitInventory;
    }

    public int getRows() {
        return size;
    }

    public List<Integer> getExcludeCases() {
        return excludeCases;
    }

    public void set(int col, int row, ClickableItem item) {
        if (col < 1 || col > 9)
            throw new IllegalArgumentException("col must be between 1 and 9 but is " + col);
        if (row < 1 || row > getRows())
            throw new IllegalArgumentException("row must be between 1 and " + getRows());
        set(locToPos(col, row), item);
    }

    public void set(int pos, ClickableItem item) {
        if (pos < 0 || pos > size * 9 - 1)
            throw new IllegalArgumentException("pos must be between 0 and " + (size * 9 - 1) + ", but is " + pos);
        items[pos] = item;
        bukkitInventory.setItem(pos, item.getItemStack());
    }

    public void fill(ClickableItem item) {
        for (int row = 0; row < size; row++)
            for (int col = 0; col < 9; col++)
                set(row * 9 + col, item);
    }

    public void rectangle(int col, int row, int width, int height, ClickableItem item) {
        if (col < 1 || col > 9)
            throw new IllegalArgumentException("col must be between 1 and 9");
        if (row < 1 || row > size)
            throw new IllegalArgumentException("row must be between 1 and the maximum number of rows");
        if (width < 1 || width > 10 - col)
            throw new IllegalArgumentException("The width must be between 1 and " + (10 - col));
        if (height < 1 || height > getRows() + 1 - row)
            throw new IllegalArgumentException("The height must be between 1 and " + (getRows() + 1 - row));
        rectangle(locToPos(col, row), width, height, item);
    }

    public void rectangle(int pos, int width, int height, ClickableItem item) {
        int[] colRow = posToLoc(pos);
        int col = colRow[0];
        int row = colRow[1];
        checkInventoryData(pos, col, row, width, height);
        for (int i = col; i < col + width; i++)
            for (int j = row; j < row + height; j++)
                if (i == col || i == col + width - 1 || j == row || j == row + height - 1)
                    set(i, j, item);
    }

    public void fillRectangle(int col, int row, int width, int height, ClickableItem item) {
        checkInventoryData(null, col, row, width, height);
        fillRectangle(locToPos(col, row), width, height, item);
    }

    public void fillRectangle(int pos, int width, int height, ClickableItem item) {
        int[] colRow = posToLoc(pos);
        int col = colRow[0];
        int row = colRow[1];
        checkInventoryData(pos, col, row, width, height);
        for (int i = col; i < col + width; i++)
            for (int j = row; j < row + height; j++)
                set(i, j, item);
    }

    public void checkInventoryData(Integer pos, Integer col, Integer row, Integer width, Integer height) {
        if (pos != null && (pos < 0 || pos >= size * 9))
            throw new IllegalArgumentException("pos must be between 0 and " + (size * 9 - 1) + ", but is " + pos);

        if (col != null && (col < 1 || col > 9))
            throw new IllegalArgumentException("col must be between 1 and 9, but is " + col);
        if (row != null && (row < 1 || row > size))
            throw new IllegalArgumentException("row must be between 1 and the maximum number of rows, but is " + row);

        if (width != null && col != null && (width < 1 || width > 10 - col))
            throw new IllegalArgumentException("The width must be between 1 and " + (10 - col) + ", but is " + width);
        if (height != null && row != null && (height < 1 || height > size + 1 - row))
            throw new IllegalArgumentException("The height must be between 1 and " + (size + 1 - row) + ", but is " + height);
    }

    public void open() {
        player.openInventory(bukkitInventory);
    }

    public void handler(InventoryClickEvent e) {
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
