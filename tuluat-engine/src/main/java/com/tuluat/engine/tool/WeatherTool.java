package com.tuluat.engine.tool;

import java.util.Map;

/**
 * Built-in weather tool (simulated weather forecast provider).
 */
public class WeatherTool implements Tool {

	@Override
	public String name() {
		return "weather";
	}

	@Override
	public String description() {
		return "Returns weather forecasts for specified cities";
	}

	@Override
	public ToolResult execute(String input, Map<String, String> parameters) {
		String city = (input != null && !input.isBlank()) ? input.trim() : "Istanbul";
		String weatherInfo = String.format("Current weather in %s: 22°C, Partly Cloudy, Humidity 55%%", city);
		return ToolResult.success(name(), weatherInfo);
	}
}
