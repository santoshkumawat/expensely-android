package com.expensely.app;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code expensely://add} link other apps (Splitely) use to open the add-expense form already
 * filled in:
 *
 * <pre>expensely://add?description=Nainital%20trip&amount=2500&date=2026-10-08</pre>
 *
 * <p>All three fields are optional. Only {@code description} (max 100 characters), {@code amount}
 * (above 0, at most two decimals) and {@code date} (YYYY-MM-DD, a real date) are kept; anything
 * else is dropped, and a bad value is dropped on its own without losing the others.
 *
 * <p>{@link #webUrl()} is the page the WebView loads: the web app reads those same parameters,
 * opens its add form and the person presses Save. Nothing is created by the link itself. Pure Java
 * (no Android classes) so it can be unit tested on the JVM.
 */
final class AddLink {

    static final String WEB_BASE = "https://expensely-app.netlify.app";
    static final int MAX_DESCRIPTION = 100;

    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,9}(\\.\\d{1,2})?$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final String description; // null when absent or invalid
    private final String amount; // null when absent or invalid
    private final String date; // null when absent or invalid

    private AddLink(String description, String amount, String date) {
        this.description = description;
        this.amount = amount;
        this.date = date;
    }

    /** @return the sanitised link, or null if {@code url} is not an {@code expensely://add} link. */
    static AddLink parse(String url) {
        if (url == null) {
            return null;
        }
        String rest = stripPrefixIgnoreCase(url.trim(), "expensely://");
        if (rest == null) {
            return null;
        }
        int q = rest.indexOf('?');
        String host = q < 0 ? rest : rest.substring(0, q);
        int hash = host.indexOf('#');
        if (hash >= 0) {
            host = host.substring(0, hash);
        }
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        if (!"add".equalsIgnoreCase(host)) {
            return null;
        }

        String query = q < 0 ? "" : rest.substring(q + 1);
        int fragment = query.indexOf('#');
        if (fragment >= 0) {
            query = query.substring(0, fragment);
        }

        String description = null;
        String amount = null;
        String date = null;
        boolean seenDescription = false;
        boolean seenAmount = false;
        boolean seenDate = false;

        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            // first value wins when a parameter is repeated
            if ("description".equals(key) && !seenDescription) {
                seenDescription = true;
                description = cleanDescription(value);
            } else if ("amount".equals(key) && !seenAmount) {
                seenAmount = true;
                amount = cleanAmount(value);
            } else if ("date".equals(key) && !seenDate) {
                seenDate = true;
                date = cleanDate(value);
            }
        }
        return new AddLink(description, amount, date);
    }

    /** {@code https://expensely-app.netlify.app/expenses?add=1&...} with only the valid fields. */
    String webUrl() {
        List<String> all = new ArrayList<>();
        all.add("add=1");
        all.addAll(parts());
        return WEB_BASE + "/expenses?" + join("&", all);
    }

    String description() {
        return description;
    }

    String amount() {
        return amount;
    }

    String date() {
        return date;
    }

    private List<String> parts() {
        List<String> parts = new ArrayList<>();
        if (description != null) {
            parts.add("description=" + encode(description));
        }
        if (amount != null) {
            parts.add("amount=" + amount);
        }
        if (date != null) {
            parts.add("date=" + date);
        }
        return parts;
    }

    // ---- validation ----

    static String cleanDescription(String raw) {
        String trimmed = raw.trim();
        int[] codePoints = trimmed.codePoints().limit(MAX_DESCRIPTION).toArray();
        String cut = new String(codePoints, 0, codePoints.length).trim();
        return cut.isEmpty() ? null : cut;
    }

    static String cleanAmount(String raw) {
        String text = raw.trim();
        if (!AMOUNT.matcher(text).matches()) {
            return null;
        }
        // must be above zero ("0", "0.0" and "0.00" are not)
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '1' && c <= '9') {
                return text;
            }
        }
        return null;
    }

    static String cleanDate(String raw) {
        String text = raw.trim();
        if (!DATE.matcher(text).matches()) {
            return null;
        }
        // a real calendar date (rejects 2026-02-30, 2026-13-01 ...); no java.time: the app supports API 24
        int year = Integer.parseInt(text.substring(0, 4));
        int month = Integer.parseInt(text.substring(5, 7));
        int day = Integer.parseInt(text.substring(8, 10));
        if (month < 1 || month > 12 || day < 1) {
            return null;
        }
        int[] days = {31, isLeap(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        return day <= days[month - 1] ? text : null;
    }

    private static boolean isLeap(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    /** String.join needs API 26; this app supports 24. */
    private static String join(String separator, List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(separator);
            }
            out.append(parts.get(i));
        }
        return out.toString();
    }

    // ---- percent encoding (unreserved characters stay as they are, everything else is %XX) ----

    static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean unreserved =
                    (c >= 'A' && c <= 'Z')
                            || (c >= 'a' && c <= 'z')
                            || (c >= '0' && c <= '9')
                            || c == '-'
                            || c == '_'
                            || c == '.'
                            || c == '~';
            if (unreserved) {
                out.append((char) c);
            } else {
                out.append('%');
                out.append(Character.toUpperCase(Character.forDigit(c >> 4, 16)));
                out.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return out.toString();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return ""; // malformed escape: treat the value as missing
        }
    }

    private static String stripPrefixIgnoreCase(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length()) ? s.substring(prefix.length()) : null;
    }
}
