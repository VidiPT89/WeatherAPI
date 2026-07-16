package com.vidi.weather.dto;

import java.util.List;

public record CompareResponse(String city, List<ProviderComparisonEntry> results) {
}
