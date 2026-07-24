package com.vidi.weather.model;

public record TideEvent(String type, String time) {
    public static final String HIGH = "high";
    public static final String LOW = "low";
}
