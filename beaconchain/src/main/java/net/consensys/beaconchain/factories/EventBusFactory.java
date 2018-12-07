package net.consensys.beaconchain.services;

import java.util.concurrent.Executors;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

public class EventBusFactory {

    private static final EventBus eventBus = new AsyncEventBus(Executors.newCachedThreadPool());

    public static EventBus getInstance() {
        return eventBus;
    }

}
