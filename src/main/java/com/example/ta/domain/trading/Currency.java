package com.example.ta.domain.trading;

public enum Currency {
    USD("USD", "$"),
    EUR("EUR", "€"),
    GBP("GBP", "£"),
    RUB("RUB", "₽"),
    BTC("BTC", "₿"),
    ETH("ETH", "Ξ");

    private final String code;
    private final String symbol;

    Currency(String code, String symbol) {
        this.code = code;
        this.symbol = symbol;
    }

    public String getCode() {
        return code;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return code;
    }
}
