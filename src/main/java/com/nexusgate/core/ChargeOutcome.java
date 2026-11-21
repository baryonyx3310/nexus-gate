package com.nexusgate.core;

public enum ChargeOutcome {
    CONTINUE, COMPLETE, CANCEL_DAMAGE, CANCEL_MOVED, CANCEL_STATION, CANCEL_GONE;

    public boolean isCancel() {
        return name().startsWith("CANCEL_");
    }
}
