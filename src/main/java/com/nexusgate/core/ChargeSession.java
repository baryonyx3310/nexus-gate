package com.nexusgate.core;

/** State machine for one charging teleport. */
public final class ChargeSession {
    private final int totalTicks;
    private final double maxMove;
    private int elapsed;

    public ChargeSession(int totalTicks, double maxMove) {
        this.totalTicks = totalTicks;
        this.maxMove = maxMove;
    }

    public ChargeOutcome tick(TickInput in) {
        if (!in.playerPresent()) return ChargeOutcome.CANCEL_GONE;
        if (in.tookDamage()) return ChargeOutcome.CANCEL_DAMAGE;
        if (!in.sourceExists() || !in.targetExists()) return ChargeOutcome.CANCEL_STATION;
        if (ChargeRules.movedTooFar(in.dx(), in.dy(), in.dz(), maxMove)) return ChargeOutcome.CANCEL_MOVED;
        elapsed++;
        return elapsed >= totalTicks ? ChargeOutcome.COMPLETE : ChargeOutcome.CONTINUE;
    }

    public int elapsed() { return elapsed; }

    public int total() { return totalTicks; }
}
