package gg.maddkraft.prestige.model;

public record Requirements(
        double cost,
        double serverEarnings,
        long mcMmoXp,
        int rabbitHoles,
        int decreeObjectives,
        int bosses
) {
    public Requirements scaled(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException("multiplier must be finite and non-negative");
        }
        return new Requirements(
                roundMoney(cost * multiplier),
                roundMoney(serverEarnings * multiplier),
                Math.round(mcMmoXp * multiplier),
                (int) Math.ceil(rabbitHoles * multiplier),
                (int) Math.ceil(decreeObjectives * multiplier),
                (int) Math.ceil(bosses * multiplier)
        );
    }

    public Requirements discounted(double discount) {
        double safeDiscount = Math.max(0.0, Math.min(1.0, discount));
        double multiplier = 1.0 - safeDiscount;
        return new Requirements(
                roundMoney(cost * multiplier),
                roundMoney(serverEarnings * multiplier),
                Math.round(mcMmoXp * multiplier),
                (int) Math.ceil(rabbitHoles * multiplier),
                (int) Math.ceil(decreeObjectives * multiplier),
                (int) Math.ceil(bosses * multiplier)
        );
    }

    private static double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
