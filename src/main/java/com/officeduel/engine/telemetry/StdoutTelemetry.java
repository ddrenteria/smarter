package com.officeduel.engine.telemetry;

public final class StdoutTelemetry implements Telemetry {
    @Override
    public void emit(Event event) {
        System.out.println(event);
    }
}


