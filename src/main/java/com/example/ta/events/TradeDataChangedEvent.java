package com.example.ta.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TradeDataChangedEvent extends ApplicationEvent {

    private final String action; // "CREATED", "UPDATED", "DELETED"
    private final Long tradeId;

    public TradeDataChangedEvent(Object source, String action, Long tradeId) {
        super(source);
        this.action = action;
        this.tradeId = tradeId;
    }

    public TradeDataChangedEvent(Object source, String action) {
        this(source, action, null);
    }
}
