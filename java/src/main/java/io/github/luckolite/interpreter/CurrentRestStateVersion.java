// Copyright 2026 Music Sheets contributors.
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Exact raw layout routing BEFORE JSONObject's numeric conversion. Other scalar readers unchanged. */
final class CurrentRestStateVersion {
    private CurrentRestStateVersion() {}

    static final int MAX_BYTES = 64 * 1024 * 1024,
            MAX_VERSION_TOKEN = 4096,
            MAX_DEPTH = 64,
            MAX_VALUES = 2_000_000;

    static String readText(File file) throws IOException {
        try (var input = new FileInputStream(file);
                var out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int n; (n = input.read(buffer)) != -1; ) {
                if (n > MAX_BYTES - out.size())
                    throw new IOException("Retained metadata exceeds bound");
                out.write(buffer, 0, n);
            }
            return text(out.toByteArray());
        }
    }

    static int read(byte[] bytes) throws IOException {
        return read(text(bytes));
    }

    static String text(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("Retained metadata exceeds bound");
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid retained metadata UTF8", e);
        }
    }

    static int read(String text) throws IOException {
        if (text.length() > MAX_BYTES)
            throw new IOException("Retained metadata text exceeds bound");
        var scan = new Scan(text);
        scan.whitespace();
        if (scan.peek() != '{') throw new IOException("Retained metadata root must be an object");
        scan.value(0, true);
        scan.whitespace();
        if (scan.at != text.length() || scan.version < 1 || scan.version > 3)
            throw new IOException("Missing or unsupported exact State/Baseline version");
        return scan.version;
    }

    private static final class Scan {
        final String text;
        int at, values, version;

        Scan(String text) {
            this.text = Objects.requireNonNull(text);
        }

        char peek() {
            return at < text.length() ? text.charAt(at) : '\0';
        }

        void whitespace() {
            while (at < text.length()) {
                char c = peek();
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
                at++;
            }
        }

        void require(char c) throws IOException {
            whitespace();
            if (peek() != c) throw new IOException("Invalid retained JSON structure");
            at++;
        }

        void value(int depth, boolean root) throws IOException {
            if (depth > MAX_DEPTH || ++values > MAX_VALUES)
                throw new IOException("Retained JSON structure bound");
            whitespace();
            char c = peek();
            if (c == '{') {
                at++;
                whitespace();
                var names = new HashSet<String>();
                if (peek() == '}') {
                    at++;
                    return;
                }
                while (true) {
                    whitespace();
                    if (peek() != '\"') throw new IOException("Invalid retained JSON key");
                    String key = string(true);
                    if (!names.add(key))
                        throw new IOException("Duplicate decoded retained JSON key");
                    require(':');
                    whitespace();
                    if (root && key.equals("version")) {
                        if (depth + 1 > MAX_DEPTH || ++values > MAX_VALUES)
                            throw new IOException("Retained JSON structure bound");
                        int start = at;
                        number();
                        if (at - start > MAX_VERSION_TOKEN)
                            throw new IOException("Retained version token bound");
                        version = integer(text.substring(start, at));
                    } else value(depth + 1, false);
                    whitespace();
                    if (peek() == '}') {
                        at++;
                        return;
                    }
                    require(',');
                }
            }
            if (c == '[') {
                at++;
                whitespace();
                if (peek() == ']') {
                    at++;
                    return;
                }
                while (true) {
                    value(depth + 1, false);
                    whitespace();
                    if (peek() == ']') {
                        at++;
                        return;
                    }
                    require(',');
                }
            }
            if (c == '\"') {
                string(false);
                return;
            }
            if (c == 't') {
                literal("true");
                return;
            }
            if (c == 'f') {
                literal("false");
                return;
            }
            if (c == 'n') {
                literal("null");
                return;
            }
            number();
        }

        void literal(String s) throws IOException {
            if (!text.startsWith(s, at)) throw new IOException("Invalid retained JSON literal");
            at += s.length();
        }

        String string(boolean decoded) throws IOException {
            require('\"');
            StringBuilder out = decoded ? new StringBuilder() : null;
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '\"') return decoded ? out.toString() : null;
                if (c < 0x20) throw new IOException("Invalid retained JSON string");
                if (c == '\\') {
                    if (at >= text.length())
                        throw new IOException("Truncated retained JSON escape");
                    char e = text.charAt(at++);
                    switch (e) {
                        case '\"':
                        case '\\':
                        case '/':
                            c = e;
                            break;
                        case 'b':
                            c = '\b';
                            break;
                        case 'f':
                            c = '\f';
                            break;
                        case 'n':
                            c = '\n';
                            break;
                        case 'r':
                            c = '\r';
                            break;
                        case 't':
                            c = '\t';
                            break;
                        case 'u':
                            {
                                if (text.length() - at < 4)
                                    throw new IOException("Truncated retained Unicode escape");
                                int code = 0;
                                for (int i = 0; i < 4; i++) {
                                    char h = text.charAt(at++);
                                    int digit =
                                            h >= '0' && h <= '9'
                                                    ? h - '0'
                                                    : h >= 'a' && h <= 'f'
                                                            ? h - 'a' + 10
                                                            : h >= 'A' && h <= 'F'
                                                                    ? h - 'A' + 10
                                                                    : -1;
                                    if (digit < 0)
                                        throw new IOException("Invalid retained Unicode escape");
                                    code = (code << 4) | digit;
                                }
                                c = (char) code;
                                break;
                            }
                        default:
                            throw new IOException("Invalid retained JSON escape");
                    }
                }
                if (decoded) out.append(c);
            }
            throw new IOException("Unterminated retained JSON string");
        }

        void number() throws IOException {
            if (peek() == '-') at++;
            if (peek() == '0') {
                at++;
                if (digit(peek())) throw new IOException("Leading zero in retained JSON number");
            } else {
                if (peek() < '1' || peek() > '9')
                    throw new IOException("Invalid retained JSON number");
                while (digit(peek())) at++;
            }
            if (peek() == '.') {
                at++;
                int start = at;
                while (digit(peek())) at++;
                if (at == start) throw new IOException("Invalid retained JSON fraction");
            }
            if (peek() == 'e' || peek() == 'E') {
                at++;
                if (peek() == '+' || peek() == '-') at++;
                int start = at;
                while (digit(peek())) at++;
                if (at == start) throw new IOException("Invalid retained JSON exponent");
            }
        }
    }

    // Numeric classifier mechanically adapted from reviewed ConvertedLayoutEnvelopeNumbers.
    private record NumberToken(
            String raw,
            boolean negative,
            boolean zero,
            int first,
            int last,
            int wholeDigits,
            boolean integral,
            int shift) {}

    static int integer(String raw) throws IOException {
        var n = token(raw);
        if (n.zero()) return 0;
        if (!n.integral() || n.wholeDigits() > 10) throw bad();
        long value = accumulate(n, n.negative() ? 2147483648L : 2147483647L);
        return (int) (n.negative() ? -value : value);
    }

    private static NumberToken token(String raw) throws IOException {
        String s = raw;
        int length = s.length();
        if (length == 0 || length > MAX_VERSION_TOKEN) throw bad();
        int i = 0;
        boolean negative = s.charAt(0) == '-';
        if (negative && ++i == length) throw bad();
        int integerStart = i,
                first = -1,
                last = -1,
                firstOrdinal = -1,
                lastOrdinal = -1,
                digits = 0,
                fraction = 0;
        while (i < length && digit(s.charAt(i))) {
            char c = s.charAt(i);
            if (c != '0') {
                if (first < 0) {
                    first = i;
                    firstOrdinal = digits;
                }
                last = i;
                lastOrdinal = digits;
            }
            digits++;
            i++;
        }
        if (i == integerStart || i - integerStart > 1 && s.charAt(integerStart) == '0') throw bad();
        if (i < length && s.charAt(i) == '.') {
            int start = ++i;
            while (i < length && digit(s.charAt(i))) {
                char c = s.charAt(i);
                if (c != '0') {
                    if (first < 0) {
                        first = i;
                        firstOrdinal = digits;
                    }
                    last = i;
                    lastOrdinal = digits;
                }
                digits++;
                fraction++;
                i++;
            }
            if (i == start) throw bad();
        }
        int exponent = 0;
        if (i < length && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            boolean minus = false;
            if (i < length && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                minus = s.charAt(i) == '-';
                i++;
            }
            int start = i, cap = length + 32;
            while (i < length && digit(s.charAt(i))) {
                int d = s.charAt(i++) - '0';
                exponent = exponent > (cap - d) / 10 ? cap : Math.min(cap, exponent * 10 + d);
            }
            if (i == start) throw bad();
            if (minus) exponent = -exponent;
        }
        if (i != length) throw bad();
        if (first < 0) return new NumberToken(s, negative, true, -1, -1, 1, true, 0);
        int trailing = digits - lastOrdinal - 1, significant = lastOrdinal - firstOrdinal + 1;
        // |exponent| is saturated beyond all possible mantissa cancellation by token length.
        int shift = exponent - fraction + trailing;
        return new NumberToken(
                s, negative, false, first, last, significant + shift, shift >= 0, shift);
    }

    private static long accumulate(NumberToken n, long limit) throws IOException {
        long value = 0;
        // At most19 significant integer digits reach this method; scan never traverses the zero
        // tail.
        for (int i = n.first(); i <= n.last(); i++) {
            char c = n.raw().charAt(i);
            if (c == '.') continue;
            int d = c - '0';
            if (value > limit / 10 || value == limit / 10 && d > limit % 10) throw bad();
            value = value * 10 + d;
        }
        for (int i = 0; i < n.shift(); i++) {
            if (value > limit / 10) throw bad();
            value *= 10;
        }
        return value;
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    private static IOException bad() {
        return new IOException("Invalid bounded exact envelope number");
    }
}
