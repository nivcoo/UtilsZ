package fr.nivcoo.utilsz.core.database;

public record ColumnDefinition(String name, String type, String constraints) {

    public ColumnDefinition(String name, String type) {
        this(name, type, null);
    }

    public ColumnDefinition(String name, String type, String constraints) {
        this.name = name;
        this.type = type;
        this.constraints = constraints;
    }

    @Override
    public String toString() {
        return name + " " + type + (constraints != null ? " " + constraints : "");
    }

}
