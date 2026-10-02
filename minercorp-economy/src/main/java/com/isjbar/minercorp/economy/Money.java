package com.isjbar.minercorp.economy;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Conversion y formato de montos. Adentro del plugin todo es {@code long} en
 * centavos (sin errores de redondeo de double); la API publica usa double con
 * 2 decimales.
 */
final class Money {

    private final String simbolo;
    private final boolean simboloAlFinal;
    private final char sepMiles;
    private final char sepDecimal;
    private final Pattern conMiles;

    Money(ConfigurationSection cfg) {
        this.simbolo = cfg == null ? "$" : cfg.getString("simbolo", "$");
        this.simboloAlFinal = cfg != null && cfg.getBoolean("simbolo-al-final", false);
        this.sepMiles = primerCaracter(cfg == null ? "." : cfg.getString("separador-miles", "."), '.');
        this.sepDecimal = primerCaracter(cfg == null ? "," : cfg.getString("separador-decimal", ","), ',');
        this.conMiles = Pattern.compile("\\d{1,3}(" + Pattern.quote(String.valueOf(sepMiles)) + "\\d{3})+("
                + Pattern.quote(String.valueOf(sepDecimal)) + "\\d+)?");
    }

    private static char primerCaracter(String s, char def) {
        return s == null || s.isEmpty() ? def : s.charAt(0);
    }

    /** Convierte a centavos. Lanza IllegalArgumentException si el monto es NaN o infinito. */
    static long toCents(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Monto invalido: " + amount);
        }
        try {
            return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Monto fuera de rango: " + amount);
        }
    }

    static double toDouble(long cents) {
        return cents / 100.0;
    }

    /** "$1.234,50", "$1.234" si no tiene centavos, "-$12" si es negativo. */
    String format(long cents) {
        boolean negativo = cents < 0;
        long abs = Math.abs(cents);
        long entero = abs / 100;
        long centavos = abs % 100;

        String digitos = Long.toString(entero);
        StringBuilder sb = new StringBuilder();
        int primerGrupo = digitos.length() % 3 == 0 ? 3 : digitos.length() % 3;
        sb.append(digitos, 0, primerGrupo);
        for (int i = primerGrupo; i < digitos.length(); i += 3) {
            sb.append(sepMiles).append(digitos, i, i + 3);
        }
        if (centavos != 0) {
            sb.append(sepDecimal).append(centavos < 10 ? "0" : "").append(centavos);
        }
        String numero = sb.toString();
        String conSimbolo = simboloAlFinal ? numero + " " + simbolo : simbolo + numero;
        return negativo ? "-" + conSimbolo : conSimbolo;
    }

    String format(double amount) {
        return format(toCents(amount));
    }

    /**
     * Lee lo que escribe un jugador: "500", "2,5", "1.234,50", "1.5k", "2m".
     * Devuelve -1 si no es un monto positivo valido.
     */
    long parse(String input) {
        if (input == null) return -1;
        String s = input.trim().toLowerCase(Locale.ROOT).replace(simbolo.toLowerCase(Locale.ROOT), "");
        if (s.isEmpty()) return -1;

        double multiplicador = 1;
        char ultimo = s.charAt(s.length() - 1);
        if (ultimo == 'k') multiplicador = 1_000;
        else if (ultimo == 'm') multiplicador = 1_000_000;
        if (multiplicador != 1) s = s.substring(0, s.length() - 1);

        if (conMiles.matcher(s).matches()) {
            s = s.replace(String.valueOf(sepMiles), "");
        }
        s = s.replace(sepDecimal, '.').replace(',', '.');
        try {
            BigDecimal valor = new BigDecimal(s).multiply(BigDecimal.valueOf(multiplicador));
            if (valor.signum() <= 0 || valor.compareTo(BigDecimal.valueOf(1_000_000_000_000L)) > 0) return -1;
            return valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }
}
