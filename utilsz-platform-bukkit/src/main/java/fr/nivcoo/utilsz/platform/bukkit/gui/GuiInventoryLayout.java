package fr.nivcoo.utilsz.platform.bukkit.gui;

import java.util.Objects;

@SuppressWarnings("unused")
public record GuiInventoryLayout(
        GuiInventoryType type,
        int columns,
        int rows
) {
    public GuiInventoryLayout {
        Objects.requireNonNull(type, "type");
        if (columns < 1) {
            throw new IllegalArgumentException("columns must be positive");
        }
        if (rows < 1) {
            throw new IllegalArgumentException("rows must be positive");
        }
        int size = Math.multiplyExact(columns, rows);
        if (type == GuiInventoryType.CHEST) {
            if (columns != 9 || rows > 6) {
                throw new IllegalArgumentException(
                        "chest inventories must use 9 columns and 1 to 6 rows");
            }
        } else if (type == GuiInventoryType.HOPPER
                && (columns != 5 || rows != 1)) {
            throw new IllegalArgumentException(
                    "hopper inventories must use 5 columns and 1 row");
        }
    }

    public static GuiInventoryLayout chest(int rows) {
        return new GuiInventoryLayout(
                GuiInventoryType.CHEST, 9, rows);
    }

    public static GuiInventoryLayout fixed(
            GuiInventoryType type,
            int columns,
            int rows
    ) {
        if (type == GuiInventoryType.CHEST) {
            throw new IllegalArgumentException(
                    "use chest(rows) for chest inventories");
        }
        return new GuiInventoryLayout(type, columns, rows);
    }

    public int size() {
        return columns * rows;
    }
}
