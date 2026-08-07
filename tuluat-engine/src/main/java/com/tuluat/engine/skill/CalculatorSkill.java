package com.tuluat.engine.skill;

import java.util.Map;
import java.util.regex.Pattern;

public final class CalculatorSkill implements Skill {
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)");

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "Performs basic mathematical calculations (+, -, *, /)";
    }

    @Override
    public SkillResult execute(String input, Map<String, String> parameters) {
        if (input == null || input.isBlank()) {
            return SkillResult.failure(name(), "Empty math expression provided");
        }
        var matcher = EXPRESSION_PATTERN.matcher(input);
        if (!matcher.find()) {
            return SkillResult.failure(name(), "Invalid format. Expected e.g. '24 + 42'");
        }
        try {
            double a = Double.parseDouble(matcher.group(1));
            String op = matcher.group(2);
            double b = Double.parseDouble(matcher.group(3));
            
            double res = switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> {
                    if (b == 0) throw new ArithmeticException("Division by zero");
                    yield a / b;
                }
                default -> throw new IllegalArgumentException("Unknown operator: " + op);
            };
            return SkillResult.success(name(), String.format("%.2f %s %.2f = %.2f", a, op, b, res));
        } catch (Exception e) {
            return SkillResult.failure(name(), "Calculation error: " + e.getMessage());
        }
    }
}
