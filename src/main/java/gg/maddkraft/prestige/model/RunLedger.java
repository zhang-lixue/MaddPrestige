package gg.maddkraft.prestige.model;

public final class RunLedger {
    private double serverEarnings;
    private long mcMmoXp;
    private int rabbitHoles;
    private int decreeObjectives;
    private int bosses;

    public RunLedger() {
    }

    public RunLedger(double serverEarnings, long mcMmoXp, int rabbitHoles, int decreeObjectives, int bosses) {
        this.serverEarnings = Math.max(0.0, serverEarnings);
        this.mcMmoXp = Math.max(0L, mcMmoXp);
        this.rabbitHoles = Math.max(0, rabbitHoles);
        this.decreeObjectives = Math.max(0, decreeObjectives);
        this.bosses = Math.max(0, bosses);
    }

    public synchronized void addServerEarnings(double amount) {
        if (Double.isFinite(amount) && amount > 0.0) serverEarnings += amount;
    }

    public synchronized void addMcMmoXp(long amount) {
        if (amount > 0L) mcMmoXp += amount;
    }

    public synchronized void addRabbitHoles(int amount) {
        if (amount > 0) rabbitHoles += amount;
    }

    public synchronized void addDecreeObjectives(int amount) {
        if (amount > 0) decreeObjectives += amount;
    }

    public synchronized void addBosses(int amount) {
        if (amount > 0) bosses += amount;
    }

    public synchronized void reset() {
        serverEarnings = 0.0;
        mcMmoXp = 0L;
        rabbitHoles = 0;
        decreeObjectives = 0;
        bosses = 0;
    }

    public synchronized RunLedger snapshot() {
        return new RunLedger(serverEarnings, mcMmoXp, rabbitHoles, decreeObjectives, bosses);
    }

    public synchronized double serverEarnings() { return serverEarnings; }
    public synchronized long mcMmoXp() { return mcMmoXp; }
    public synchronized int rabbitHoles() { return rabbitHoles; }
    public synchronized int decreeObjectives() { return decreeObjectives; }
    public synchronized int bosses() { return bosses; }

    public synchronized boolean satisfies(Requirements requirements) {
        return serverEarnings + 0.0001 >= requirements.serverEarnings()
                && mcMmoXp >= requirements.mcMmoXp()
                && rabbitHoles >= requirements.rabbitHoles()
                && decreeObjectives >= requirements.decreeObjectives()
                && bosses >= requirements.bosses();
    }
}
