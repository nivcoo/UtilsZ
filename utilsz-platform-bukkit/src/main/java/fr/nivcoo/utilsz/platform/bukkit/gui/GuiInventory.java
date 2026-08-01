package fr.nivcoo.utilsz.platform.bukkit.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class GuiInventory implements InventoryHolder {

    public static final String TICK = "tick";

    private final HashMap<String, Object> values;
    private final Player player;
    private final GuiProvider provider;
    private final GuiInventoryLayout inventoryLayout;
    private final int columns;
    private final int rows;
    private final AtomicBoolean refreshRequested = new AtomicBoolean();

    private final GuiEditableSlots editableSlots;
    private final ClickableItem[] items;
    private final Inventory bukkitInventory;
    private Component displayedTitle;
    private Component pendingTitle;

    public GuiInventory(Player player, GuiProvider provider, Consumer<GuiInventory> params) {
        this(player, provider, null, params);
    }

    GuiInventory(Player player, GuiProvider provider, Inventory inventory, Consumer<GuiInventory> params) {
        this.values = new HashMap<>();
        this.player = player;
        this.provider = provider;
        if (params != null) params.accept(this);

        this.inventoryLayout = Objects.requireNonNull(
                provider.inventoryLayout(this), "inventory layout");
        this.columns = inventoryLayout.columns();
        this.rows = inventoryLayout.rows();
        int size = inventoryLayout.size();
        this.items = new ClickableItem[size];
        this.editableSlots = Objects.requireNonNull(
                provider.editableSlots(this), "editable slots");
        for (int slot : editableSlots.slots()) {
            if (slot < 0 || slot >= size) throw new IllegalArgumentException(
                    "editable slot " + slot + " is outside inventory size " + size);
        }

        Component initialTitle = provider.title(this);
        if (inventory != null) {
            if (inventory.getSize() != size) {
                throw new IllegalArgumentException(
                        "shared inventory size " + inventory.getSize() + " does not match " + size);
            }
            if (inventoryLayout.type() == GuiInventoryType.HOPPER
                    && inventory.getType() != InventoryType.HOPPER) {
                throw new IllegalArgumentException(
                        "shared inventory type " + inventory.getType()
                                + " does not match HOPPER");
            }
            this.bukkitInventory = inventory;
        } else {
            Component title = initialTitle == null
                    ? Component.empty() : initialTitle;
            this.bukkitInventory = switch (inventoryLayout.type()) {
                case CHEST -> Bukkit.createInventory(
                        this, size, title);
                case HOPPER -> Bukkit.createInventory(
                        this, InventoryType.HOPPER, title);
            };
        }

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

        Component title = newTitle == null ? Component.empty() : newTitle;
        if (Objects.equals(displayedTitle, title)) return;

        pendingTitle = title;
        openInventory(bukkitInventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return bukkitInventory;
    }

    public Player getPlayer() {
        return player;
    }

    public GuiProvider getProvider() {
        return provider;
    }

    public Inventory getBukkitInventory() {
        return bukkitInventory;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public int getSize() {
        return items.length;
    }

    public GuiInventoryLayout getInventoryLayout() {
        return inventoryLayout;
    }

    public GuiEditableSlots getEditableSlots() {
        return editableSlots;
    }

    public boolean isManagedSlot(int slot) {
        return slot >= 0 && slot < items.length && items[slot] != null;
    }

    public List<ClickableItem> clickableSnapshot() {
        List<ClickableItem> snapshot =
                new ArrayList<>(items.length);
        for (ClickableItem item : items) {
            snapshot.add(item == null
                    ? null : item.cloneItem());
        }
        return Collections.unmodifiableList(
                snapshot);
    }

    public void restoreClickables(
            List<ClickableItem> snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.size() != items.length) {
            throw new IllegalArgumentException(
                    "clickable snapshot size "
                            + snapshot.size()
                            + " does not match inventory size "
                            + items.length);
        }
        for (int slot = 0;
                slot < items.length; slot++) {
            ClickableItem item =
                    snapshot.get(slot);
            items[slot] = item == null
                    ? null : item.cloneItem();
        }
    }

    public void set(int col, int row, ClickableItem item) {
        if (col < 1 || col > columns) throw new IllegalArgumentException(
                "col must be between 1 and " + columns + " but is " + col);
        if (row < 1 || row > rows) throw new IllegalArgumentException("row must be between 1 and " + rows);
        set(locToPos(col, row), item);
    }

    public void set(int pos, ClickableItem item) {
        if (pos < 0 || pos >= items.length)
            throw new IllegalArgumentException("pos must be between 0 and " + (items.length - 1) + ", but is " + pos);
        items[pos] = item;
        bukkitInventory.setItem(pos, item.getItemStack());
    }

    public void clear(int col, int row) {
        if (col < 1 || col > columns) throw new IllegalArgumentException(
                "col must be between 1 and " + columns + " but is " + col);
        if (row < 1 || row > rows) throw new IllegalArgumentException("row must be between 1 and " + rows);
        clear(locToPos(col, row));
    }

    public void clear(int pos) {
        if (pos < 0 || pos >= items.length)
            throw new IllegalArgumentException("pos must be between 0 and " + (items.length - 1) + ", but is " + pos);
        items[pos] = null;
        bukkitInventory.setItem(pos, null);
    }

    public void fill(ClickableItem item) {
        for (int slot = 0; slot < items.length; slot++) {
            set(slot, item);
        }
    }

    public void rectangle(int col, int row, int width, int height, ClickableItem item) {
        if (col < 1 || col > columns) throw new IllegalArgumentException(
                "col must be between 1 and " + columns);
        if (row < 1 || row > rows) throw new IllegalArgumentException("row must be between 1 and rows");
        if (width < 1 || width > columns + 1 - col) throw new IllegalArgumentException(
                "width must be between 1 and " + (columns + 1 - col));
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
        if (pos != null && (pos < 0 || pos >= items.length))
            throw new IllegalArgumentException("pos must be between 0 and " + (items.length - 1) + ", but is " + pos);
        if (col != null && (col < 1 || col > columns))
            throw new IllegalArgumentException("col must be between 1 and " + columns + ", but is " + col);
        if (row != null && (row < 1 || row > rows))
            throw new IllegalArgumentException("row must be between 1 and rows, but is " + row);
        if (width != null && col != null && (width < 1 || width > columns + 1 - col))
            throw new IllegalArgumentException("width must be between 1 and " + (columns + 1 - col) + ", but is " + width);
        if (height != null && row != null && (height < 1 || height > rows + 1 - row))
            throw new IllegalArgumentException("height must be between 1 and " + (rows + 1 - row) + ", but is " + height);
    }

    public void open() {
        openInventory(bukkitInventory);
    }

    void displayedTitle(Component displayedTitle) {
        this.displayedTitle = displayedTitle == null ? Component.empty() : displayedTitle;
        pendingTitle = null;
    }

    Component titleForOpen() {
        if (pendingTitle != null) return pendingTitle;
        Component title = provider.title(this);
        return title == null ? Component.empty() : title;
    }

    public void refresh() {
        refreshRequested.set(true);
    }

    boolean consumeRefreshRequest() {
        return refreshRequested.getAndSet(false);
    }

    private void openInventory(Inventory inventory) {
        ItemStack currentCursor = player.getItemOnCursor();
        boolean preserveCursor = currentCursor != null && !currentCursor.getType().isAir();
        ItemStack cursor = preserveCursor ? currentCursor.clone() : null;
        if (preserveCursor) player.setItemOnCursor(null);
        try {
            player.openInventory(inventory);
        } finally {
            if (preserveCursor) player.setItemOnCursor(cursor);
        }
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
        return new int[]{ (pos % columns) + 1, (pos / columns) + 1 };
    }

    public int locToPos(int col, int row) {
        return (row - 1) * columns + (col - 1);
    }
}
