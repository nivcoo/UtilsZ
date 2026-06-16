package fr.nivcoo.utilsz.core.database;

public record TypedColumnDefinition(String name, ColumnType type, int length, String constraints) {
}
