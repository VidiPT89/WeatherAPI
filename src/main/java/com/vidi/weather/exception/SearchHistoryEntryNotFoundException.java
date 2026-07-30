package com.vidi.weather.exception;

public class SearchHistoryEntryNotFoundException extends WeatherServiceException {

    public SearchHistoryEntryNotFoundException(Long id) {
        super("Search history entry not found: '%d'".formatted(id));
    }
}
