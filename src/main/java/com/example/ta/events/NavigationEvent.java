package com.example.ta.events;

import com.example.ta.domain.trading.Trade;
import lombok.Getter;

@Getter
public class NavigationEvent {
    private final NavigationType navigationType;
    private final Trade trade;

    public NavigationEvent(NavigationType navigationType) {
        this.navigationType = navigationType;
        this.trade = null;
    }

    public NavigationEvent(NavigationType navigationType, Trade trade) {
        this.navigationType = navigationType;
        this.trade = trade;
    }

    public enum NavigationType {
        ADD_TRADE,
        VIEW_TRADES,
        STATISTICS,
        VIEW_TRADE_DETAILS,
        EDIT_TRADE,
        HOME,
        POSITION_CALCULATOR
    }
}