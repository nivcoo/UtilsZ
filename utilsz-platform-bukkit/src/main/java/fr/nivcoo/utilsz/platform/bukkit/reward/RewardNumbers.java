package fr.nivcoo.utilsz.platform.bukkit.reward;

import java.math.BigDecimal;

@SuppressWarnings("unused")
public final class RewardNumbers {
    private RewardNumbers() {
    }

    public static BigDecimal decimal(Object source, String path) {
        if (!(source instanceof Number) && !(source instanceof String)) throw invalid(path, "must be a number");
        try {
            return source instanceof BigDecimal decimal ? decimal : new BigDecimal(source.toString().strip());
        } catch (NumberFormatException exception) {
            throw invalid(path, "must be finite");
        }
    }

    public static BigDecimal positiveDecimal(Object source, BigDecimal maximum, String path) {
        BigDecimal amount = decimal(source, path);
        if (amount.signum() <= 0 || amount.compareTo(maximum) > 0)
            throw invalid(path, "must be positive and at most " + maximum.toPlainString());
        return amount;
    }

    public static int wholeInt(Object source, int minimum, int maximum, String path) {
        BigDecimal amount = decimal(source, path);
        try {
            int value = amount.intValueExact();
            if (value < minimum || value > maximum)
                throw invalid(path, "must be between " + minimum + " and " + maximum);
            return value;
        } catch (ArithmeticException exception) {
            throw invalid(path, "must be a whole number between " + minimum + " and " + maximum);
        }
    }

    private static IllegalArgumentException invalid(String path, String reason) {
        return new IllegalArgumentException("Invalid reward: " + path + ' ' + reason + '.');
    }
}
