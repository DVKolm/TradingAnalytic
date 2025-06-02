package com.example.ta.domain;

public enum TradeStatus {
    OPEN("Открыта"), CLOSED("Закрыта");

    private final String displayName;

    TradeStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
