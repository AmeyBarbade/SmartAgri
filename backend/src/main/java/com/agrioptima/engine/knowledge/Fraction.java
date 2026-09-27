package com.agrioptima.engine.knowledge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigInteger;

/**
 * Exact non-negative rational number such as "1/3", written as text in the knowledge base so that split
 * fractions can be checked to sum to exactly 1.
 */
public record Fraction(long numerator, long denominator) {

    public static final Fraction ZERO = new Fraction(0, 1);
    public static final Fraction ONE = new Fraction(1, 1);

    public Fraction {
        if (denominator <= 0) {
            throw new IllegalArgumentException("denominator must be positive");
        }
        if (numerator < 0) {
            throw new IllegalArgumentException("fraction must not be negative");
        }
        long g = BigInteger.valueOf(numerator).gcd(BigInteger.valueOf(denominator)).longValueExact();
        if (g > 1) {
            numerator /= g;
            denominator /= g;
        }
    }

    /** Parses "a/b" or a whole number ("0", "1"). */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Fraction parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("fraction is empty");
        }
        String[] parts = text.trim().split("/");
        try {
            if (parts.length == 1) {
                return new Fraction(Long.parseLong(parts[0].trim()), 1);
            }
            if (parts.length == 2) {
                return new Fraction(Long.parseLong(parts[0].trim()), Long.parseLong(parts[1].trim()));
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        throw new IllegalArgumentException("not a fraction: '" + text + "'");
    }

    public Fraction plus(Fraction other) {
        return new Fraction(Math.addExact(Math.multiplyExact(numerator, other.denominator),
                Math.multiplyExact(other.numerator, denominator)), Math.multiplyExact(denominator, other.denominator));
    }

    public boolean isGreaterThan(Fraction other) {
        return Math.multiplyExact(numerator, other.denominator) > Math.multiplyExact(other.numerator, denominator);
    }

    public double toDouble() {
        return (double) numerator / denominator;
    }

    @JsonValue
    @Override
    public String toString() {
        return denominator == 1 ? Long.toString(numerator) : numerator + "/" + denominator;
    }
}
