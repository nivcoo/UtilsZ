package fr.nivcoo.utilsz.core.database;

@SuppressWarnings("unused")
public final class ColumnConstraints {

    private ColumnConstraints() {
    }

    public static String none() {
        return "";
    }

    public static String notNull() {
        return "NOT NULL";
    }

    public static String defaultNull() {
        return "DEFAULT NULL";
    }

    public static String defaultValue(boolean value) {
        return "DEFAULT " + (value ? 1 : 0);
    }

    public static String defaultValue(Number value) {
        if (value == null) return defaultNull();
        return "DEFAULT " + value;
    }

    public static String defaultValue(String value) {
        if (value == null) return defaultNull();
        return "DEFAULT '" + value.replace("'", "''") + "'";
    }

    public static String notNullDefault(boolean value) {
        return notNull() + " " + defaultValue(value);
    }

    public static String notNullDefault(Number value) {
        return notNull() + " " + defaultValue(value);
    }

    public static String notNullDefault(String value) {
        return notNull() + " " + defaultValue(value);
    }
}
