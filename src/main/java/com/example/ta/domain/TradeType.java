package com.example.ta.domain;

public enum TradeType {
    LONG("Лонг"),
    SHORT("Шорт");

    private final String displayName;

    TradeType(String displayName) {
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
