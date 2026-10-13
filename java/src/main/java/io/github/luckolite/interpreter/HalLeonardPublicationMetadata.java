// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import javax.net.ssl.HttpsURLConnection;

/** Bounded public product metadata only; PDF bytes and OCR text are never sent. */
public final class HalLeonardPublicationMetadata {
    private HalLeonardPublicationMetadata() {}

    public static final int MAX_HTML_BYTES = 1_048_576;
    private static final Pattern LI = Pattern.compile("(?is)<li\\b([^>]*)>(.*?)</li\\s*>");
    private static final Pattern H1 = Pattern.compile("(?is)<h1\\b([^>]*)>(.*?)</h1\\s*>");
    private static final Pattern SPAN = Pattern.compile("(?is)<span\\b([^>]*)>(.*?)</span\\s*>");
    private static final Pattern LABEL = Pattern.compile("(?is)<label\\b[^>]*>(.*?)</label\\s*>");
    private static final Pattern LINK = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a\\s*>");
    private static final Pattern ENTITY =
            Pattern.compile("&(#(?:[0-9]+|[xX][0-9a-fA-F]+)|[A-Za-z][A-Za-z0-9]+);");

    public record Fetched(PublishedEditionCreditRecovery.Edition edition, byte[] html) {
        public Fetched {
            html = html.clone();
        }

        @Override
        public byte[] html() {
            return html.clone();
        }
    }

    private static String attribute(String attrs, String name) {
        var matcher =
                Pattern.compile("(?is)(?:^|\\s)" + Pattern.quote(name) + "\\s*=\\s*([\"'])(.*?)\\1")
                        .matcher(attrs);
        return matcher.find() ? matcher.group(2) : "";
    }

    private static boolean hasClass(String attrs, String value) {
        return Arrays.asList(attribute(attrs, "class").strip().split("\\s+")).contains(value);
    }

    private static String text(String html) throws IOException {
        String stripped = html.replaceAll("(?s)<[^>]*>", " ");
        var matcher = ENTITY.matcher(stripped);
        var out = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1), replacement;
            switch (token) {
                case "amp":
                    replacement = "&";
                    break;
                case "quot":
                    replacement = "\"";
                    break;
                case "apos":
                    replacement = "'";
                    break;
                case "nbsp":
                    replacement = " ";
                    break;
                case "lt":
                    replacement = "<";
                    break;
                case "gt":
                    replacement = ">";
                    break;
                default:
                    if (!token.startsWith("#"))
                        throw new IOException("Unsupported metadata entity");
                    try {
                        boolean hex =
                                token.length() > 2
                                        && (token.charAt(1) == 'x' || token.charAt(1) == 'X');
                        int code = Integer.parseInt(token.substring(hex ? 2 : 1), hex ? 16 : 10);
                        if (!Character.isValidCodePoint(code) || code >= 0xD800 && code <= 0xDFFF)
                            throw new NumberFormatException();
                        replacement = new String(Character.toChars(code));
                    } catch (NumberFormatException invalid) {
                        throw new IOException("Invalid metadata entity", invalid);
                    }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString().strip().replaceAll("[\\h\\r\\n]+", " ");
    }

    private static String sha(byte[] data) throws IOException {
        try {
            var out = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(data))
                out.append(String.format(Locale.ROOT, "%02x", value & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    public static PublishedEditionCreditRecovery.Edition parse(
            String productId, String sourceUrl, byte[] bytes) throws IOException {
        if (!PublishedEditionCreditRecovery.validProductId(productId)
                || !PublishedEditionCreditRecovery.validSourceUrl(sourceUrl, productId)
                || bytes == null
                || bytes.length == 0
                || bytes.length > MAX_HTML_BYTES)
            throw new IOException("Invalid publication response provenance or bounds");
        String html =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
        html =
                html.replaceAll("(?is)<!--.*?-->", " ")
                        .replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>", " ");
        var titles = new LinkedHashSet<String>();
        var inventories = new ArrayList<String>();
        var composers = new LinkedHashSet<String>();
        var arrangers = new LinkedHashSet<String>();
        var headings = H1.matcher(html);
        while (headings.find())
            if (hasClass(headings.group(1), "product-title")) {
                var subheads = SPAN.matcher(headings.group(2));
                var title = new StringBuffer();
                while (subheads.find())
                    subheads.appendReplacement(
                            title,
                            Matcher.quoteReplacement(
                                    hasClass(subheads.group(1), "subhead")
                                            ? " "
                                            : subheads.group()));
                subheads.appendTail(title);
                titles.add(text(title.toString()));
            }
        var items = LI.matcher(html);
        while (items.find()) {
            String attrs = items.group(1), content = items.group(2);
            if (!hasClass(attrs, "pdp-details-list-item")) continue;
            if (hasClass(attrs, "inventory-details")) {
                var inventory =
                        Pattern.compile("(?iu)^Inventory:\\h*#HL\\h*0*([1-9][0-9]{0,7})$")
                                .matcher(text(content));
                if (!inventory.matches()) throw new IOException("Unrecognized publisher inventory");
                inventories.add(inventory.group(1));
            }
            if (!hasClass(attrs, "contributor-details")) continue;
            var label = LABEL.matcher(content);
            if (!label.find()) throw new IOException("Missing contributor role");
            String role =
                    text(label.group(1)).toLowerCase(Locale.ROOT).replaceFirst(":$", "").strip();
            boolean composer = role.equals("composer") || role.equals("composers");
            boolean arranger = role.equals("arranger") || role.equals("arrangers");
            if (!composer && !arranger) continue;
            var links = LINK.matcher(content);
            int added = 0;
            while (links.find()) {
                String href = attribute(links.group(1), "href");
                if (!href.matches(
                        "/search/"
                                + (composer ? "composer" : "arranger")
                                + "/[1-9][0-9]*/[a-z0-9-]+"))
                    throw new IOException("Contributor link disagrees with printed publisher role");
                (composer ? composers : arrangers).add(text(links.group(2)));
                added++;
            }
            if (added == 0) throw new IOException("No explicit linked contributor");
        }
        if (titles.size() != 1 || inventories.size() != 1 || !inventories.get(0).equals(productId))
            throw new IOException("Publisher title or exact inventory identity is ambiguous");
        try {
            return new PublishedEditionCreditRecovery.Edition(
                    productId,
                    titles.iterator().next(),
                    List.copyOf(composers),
                    List.copyOf(arrangers),
                    sourceUrl,
                    sha(bytes));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid publisher credit facts", invalid);
        }
    }

    public static Fetched fetch(String productId, int timeoutMillis)
            throws IOException, InterruptedException {
        if (!PublishedEditionCreditRecovery.validProductId(productId)
                || timeoutMillis < 100
                || timeoutMillis > 10000)
            throw new IllegalArgumentException("Invalid publication request");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        String url = "https://www.halleonard.com/product/" + productId;
        var connection = (HttpsURLConnection) new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestProperty(
                    "User-Agent", "Mozilla/5.0 (compatible; MusicSheetsMetadata/1.0)");
            connection.setRequestProperty("Accept", "text/html");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (connection.getResponseCode() != 200
                    || connection.getContentType() == null
                    || !connection.getContentType().toLowerCase(Locale.ROOT).startsWith("text/html")
                    || connection.getContentLength() > MAX_HTML_BYTES)
                throw new IOException("Publisher metadata request unavailable or outside bounds");
            String encoding = connection.getContentEncoding();
            if (encoding != null && !encoding.equalsIgnoreCase("identity"))
                throw new IOException("Unsupported publication transfer encoding");
            var bytes = new ByteArrayOutputStream();
            long started = System.nanoTime();
            try (var in = connection.getInputStream()) {
                byte[] block = new byte[8192];
                int count;
                while ((count = in.read(block)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (bytes.size() + count > MAX_HTML_BYTES
                            || System.nanoTime() - started > timeoutMillis * 1_000_000L)
                        throw new IOException("Publisher metadata body exceeds bounds");
                    bytes.write(block, 0, count);
                }
            }
            byte[] body = bytes.toByteArray();
            return new Fetched(parse(productId, url, body), body);
        } finally {
            connection.disconnect();
        }
    }
}
