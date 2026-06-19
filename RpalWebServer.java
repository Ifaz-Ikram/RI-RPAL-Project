import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight local web server for the RPAL interpreter.
 *
 * Usage:
 *   java RpalWebServer
 *   java RpalWebServer 8081
 */
public class RpalWebServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_REQUEST_BYTES = 256 * 1024;
    private static final int PROCESS_TIMEOUT_SECONDS = 10;
    private static final Path WEB_ROOT = Paths.get("web").toAbsolutePath().normalize();
    private static final Path TESTS_ROOT = Paths.get("tests").toAbsolutePath().normalize();

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/run", new RunHandler());
        server.createContext("/api/samples", new SamplesHandler());
        server.createContext("/api/sample", new SampleHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("RPAL web server running at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    private static int parsePort(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }
        if (args.length == 1) {
            try {
                int port = Integer.parseInt(args[0]);
                if (port >= 1 && port <= 65535) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to usage error.
            }
        }
        System.err.println("Usage: java RpalWebServer [port]");
        System.exit(1);
        return DEFAULT_PORT;
    }

    private static class RunHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"success\":false,\"error\":\"Method not allowed\"}");
                return;
            }

            String body;
            try {
                body = readRequestBody(exchange);
            } catch (IOException e) {
                sendJson(exchange, 413, "{\"success\":false,\"error\":\"Request body is too large\"}");
                return;
            }

            String code = Json.getString(body, "code");
            String mode = Json.getString(body, "mode");
            if (mode == null || mode.trim().isEmpty()) {
                mode = "run";
            }
            mode = mode.toLowerCase(Locale.ROOT);

            if (!"run".equals(mode) && !"ast".equals(mode) && !"st".equals(mode)
                    && !"tokens".equals(mode) && !"compare".equals(mode)) {
                sendJson(exchange, 400, "{\"success\":false,\"error\":\"Invalid execution mode\"}");
                return;
            }
            if (code == null || code.trim().isEmpty()) {
                sendJson(exchange, 400, "{\"success\":false,\"error\":\"Enter RPAL code or upload a .rpal file\"}");
                return;
            }

            // Demo-only API modes expose interpreter internals without changing
            // the original rpal20 CLI execution path.
            if ("tokens".equals(mode)) {
                String tokenJson = buildTokensJson(code);
                boolean success = !tokenJson.isEmpty();
                sendJson(exchange, 200, "{"
                        + "\"success\":" + success + ","
                        + "\"mode\":\"tokens\","
                        + "\"output\":\"\","
                        + "\"tree\":null,"
                        + "\"tokens\":" + (success ? tokenJson : "[]") + ","
                        + "\"error\":\"" + (success ? "" : "Unable to tokenize input") + "\","
                        + "\"exitCode\":" + (success ? 0 : 1)
                        + "}");
                return;
            }
            if ("compare".equals(mode)) {
                String astTree = buildTreeJson(code, "ast");
                String stTree = buildTreeJson(code, "st");
                boolean success = !astTree.isEmpty() && !stTree.isEmpty();
                sendJson(exchange, 200, "{"
                        + "\"success\":" + success + ","
                        + "\"mode\":\"compare\","
                        + "\"output\":\"\","
                        + "\"tree\":null,"
                        + "\"astTree\":" + (astTree.isEmpty() ? "null" : astTree) + ","
                        + "\"stTree\":" + (stTree.isEmpty() ? "null" : stTree) + ","
                        + "\"error\":\"" + (success ? "" : "Unable to build AST/ST comparison") + "\","
                        + "\"exitCode\":" + (success ? 0 : 1)
                        + "}");
                return;
            }

            RunResult result = executeInterpreter(code, mode);
            String treeJson = "";
            if (result.exitCode == 0 && ("ast".equals(mode) || "st".equals(mode))) {
                treeJson = buildTreeJson(code, mode);
            }
            String json = "{"
                    + "\"success\":" + (result.exitCode == 0) + ","
                    + "\"mode\":\"" + Json.escape(mode) + "\","
                    + "\"output\":\"" + Json.escape(result.output) + "\","
                    + "\"tree\":" + (treeJson.isEmpty() ? "null" : treeJson) + ","
                    + "\"tokens\":[],"
                    + "\"error\":\"" + Json.escape(result.error) + "\","
                    + "\"exitCode\":" + result.exitCode
                    + "}";
            sendJson(exchange, 200, json);
        }
    }

    private static String buildTokensJson(String code) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("rpal-tokens-", ".rpal");
            Files.write(tempFile, code.getBytes(StandardCharsets.UTF_8));

            Lexer lexer = new Lexer();
            List<Token> tokens = lexer.scan(tempFile.toString());
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < tokens.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                Token token = tokens.get(i);
                json.append("{\"value\":\"").append(Json.escape(token.value)).append("\",")
                        .append("\"type\":\"").append(Json.escape(token.type)).append("\"}");
            }
            json.append(']');
            return json.toString();
        } catch (RuntimeException | IOException e) {
            return "";
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Token temp file cleanup failure should not affect the API response.
                }
            }
        }
    }

    private static String buildTreeJson(String code, String mode) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("rpal-tree-", ".rpal");
            Files.write(tempFile, code.getBytes(StandardCharsets.UTF_8));

            Lexer lexer = new Lexer();
            List<Token> tokens = lexer.scan(tempFile.toString());
            Parser parser = new Parser(tokens);
            ASTNode root = parser.parse();
            if (parser.errorExist) {
                return "";
            }

            if ("st".equals(mode)) {
                Standardizer standardizer = new Standardizer();
                for (int i = 0; i < 10; i++) {
                    standardizer.standardize(root);
                }
            }

            return serializeNode(root);
        } catch (RuntimeException | IOException e) {
            return "";
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Temporary tree file cleanup failure should not affect the API response.
                }
            }
        }
    }

    private static String serializeNode(ASTNode node) {
        if (node == null) {
            return "null";
        }

        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"label\":\"").append(Json.escape(displayLabel(node))).append("\",");
        json.append("\"type\":\"").append(Json.escape(displayType(node))).append("\",");
        json.append("\"children\":[");

        // ASTNode uses left-child/right-sibling representation:
        // left is the first child; each child's right pointer is the next sibling.
        ASTNode child = node.left;
        boolean first = true;
        while (child != null) {
            if (!first) {
                json.append(',');
            }
            json.append(serializeNode(child));
            first = false;
            child = child.right;
        }

        json.append("]}");
        return json.toString();
    }

    private static String displayLabel(ASTNode node) {
        if ("ID".equals(node.type) || "STR".equals(node.type) || "INT".equals(node.type)) {
            return "<" + node.type + ":" + node.value + ">";
        }
        if ("BOOL".equals(node.type) || "NIL".equals(node.type) || "DUMMY".equals(node.type)) {
            return "<" + node.value + ">";
        }
        return node.value;
    }

    private static String displayType(ASTNode node) {
        if ("ID".equals(node.type)) {
            return "IDENTIFIER";
        }
        if ("INT".equals(node.type)) {
            return "INTEGER";
        }
        if ("STR".equals(node.type)) {
            return "STRING";
        }
        if ("BOOL".equals(node.type) || "NIL".equals(node.type) || "DUMMY".equals(node.type)) {
            return node.type;
        }
        if (isKeywordNode(node.value)) {
            return "KEYWORD";
        }
        return "OPERATOR";
    }

    private static boolean isKeywordNode(String value) {
        return "gamma".equals(value)
                || "lambda".equals(value)
                || "tau".equals(value)
                || "YSTAR".equals(value)
                || "let".equals(value)
                || "where".equals(value)
                || "within".equals(value)
                || "and".equals(value)
                || "rec".equals(value)
                || "->".equals(value);
    }

    private static RunResult executeInterpreter(String code, String mode) {
        Path tempFile = null;
        try {
            // The interpreter expects a file path, so browser input is copied to
            // an OS-managed temporary .rpal file instead of accepting user paths.
            tempFile = Files.createTempFile("rpal-web-", ".rpal");
            Files.write(tempFile, code.getBytes(StandardCharsets.UTF_8));

            // Keep the command fixed and argument-based. No shell is used, so
            // RPAL source text cannot become part of an executable command.
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add("rpal20");
            if ("ast".equals(mode)) {
                command.add("-ast");
            } else if ("st".equals(mode)) {
                command.add("-st");
            }
            command.add(tempFile.toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(Paths.get("").toAbsolutePath().toFile());
            Process process = processBuilder.start();

            // Read stdout and stderr concurrently to avoid blocking if either
            // stream produces enough data to fill its process pipe.
            StreamCollector stdout = new StreamCollector(process.getInputStream());
            StreamCollector stderr = new StreamCollector(process.getErrorStream());
            stdout.start();
            stderr.start();

            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                stdout.join(1000);
                stderr.join(1000);
                return new RunResult(stdout.getContent(), "Execution timed out.", 124);
            }

            stdout.join(1000);
            stderr.join(1000);
            return new RunResult(stdout.getContent(), stderr.getContent(), process.exitValue());
        } catch (Exception e) {
            return new RunResult("", e.getMessage(), 1);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Temporary-file cleanup failure should not hide interpreter output.
                }
            }
        }
    }

    private static class SamplesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            List<String> samples = new ArrayList<>();
            if (Files.isDirectory(TESTS_ROOT)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(TESTS_ROOT, "*.rpal")) {
                    for (Path path : stream) {
                        samples.add(path.getFileName().toString());
                    }
                }
            }
            samples.sort(Comparator.naturalOrder());

            StringBuilder json = new StringBuilder();
            json.append("{\"samples\":[");
            for (int i = 0; i < samples.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append('"').append(Json.escape(samples.get(i))).append('"');
            }
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    private static class SampleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String name = queryParam(exchange.getRequestURI(), "name");
            if (name == null || name.contains("/") || name.contains("\\") || !name.endsWith(".rpal")) {
                sendJson(exchange, 400, "{\"error\":\"Invalid sample name\"}");
                return;
            }

            // Only allow loading direct children of tests/ to avoid path traversal.
            Path sample = TESTS_ROOT.resolve(name).normalize();
            if (!sample.startsWith(TESTS_ROOT) || !Files.isRegularFile(sample)) {
                sendJson(exchange, 404, "{\"error\":\"Sample not found\"}");
                return;
            }

            String content = new String(Files.readAllBytes(sample), StandardCharsets.UTF_8);
            sendJson(exchange, 200, "{\"name\":\"" + Json.escape(name) + "\",\"code\":\"" + Json.escape(content) + "\"}");
        }
    }

    private static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed", "text/plain; charset=utf-8");
                return;
            }

            String requestPath = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8.name());
            if ("/".equals(requestPath)) {
                requestPath = "/index.html";
            }

            // Serve files only from web/, even if the URL contains ../ segments.
            Path file = WEB_ROOT.resolve(requestPath.substring(1)).normalize();
            if (!file.startsWith(WEB_ROOT) || !Files.isRegularFile(file)) {
                sendText(exchange, 404, "Not found", "text/plain; charset=utf-8");
                return;
            }

            byte[] bytes = Files.readAllBytes(file);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(file));
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) != -1) {
                total += read;
                if (total > MAX_REQUEST_BYTES) {
                    throw new IOException("Request too large");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String queryParam(URI uri, String key) throws IOException {
        String query = uri.getRawQuery();
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] pieces = part.split("=", 2);
            String name = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8.name());
            if (key.equals(name)) {
                return pieces.length == 2
                        ? URLDecoder.decode(pieces[1], StandardCharsets.UTF_8.name())
                        : "";
            }
        }
        return null;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendText(exchange, status, json, "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static class StreamCollector extends Thread {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        StreamCollector(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try (InputStream stream = input) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } catch (IOException ignored) {
                // If the process stream closes early, keep whatever was captured.
            }
        }

        String getContent() {
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static class RunResult {
        final String output;
        final String error;
        final int exitCode;

        RunResult(String output, String error, int exitCode) {
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
            this.exitCode = exitCode;
        }
    }

    private static class Json {
        static String getString(String json, String key) {
            String quotedKey = "\"" + key + "\"";
            int keyIndex = json.indexOf(quotedKey);
            if (keyIndex < 0) {
                return null;
            }
            int colon = json.indexOf(':', keyIndex + quotedKey.length());
            if (colon < 0) {
                return null;
            }
            int start = colon + 1;
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
                start++;
            }
            if (start >= json.length() || json.charAt(start) != '"') {
                return null;
            }
            return readString(json, start + 1);
        }

        private static String readString(String json, int index) {
            StringBuilder value = new StringBuilder();
            while (index < json.length()) {
                char c = json.charAt(index++);
                if (c == '"') {
                    return value.toString();
                }
                if (c == '\\' && index < json.length()) {
                    char escaped = json.charAt(index++);
                    switch (escaped) {
                        case '"':
                        case '\\':
                        case '/':
                            value.append(escaped);
                            break;
                        case 'b':
                            value.append('\b');
                            break;
                        case 'f':
                            value.append('\f');
                            break;
                        case 'n':
                            value.append('\n');
                            break;
                        case 'r':
                            value.append('\r');
                            break;
                        case 't':
                            value.append('\t');
                            break;
                        case 'u':
                            if (index + 4 <= json.length()) {
                                String hex = json.substring(index, index + 4);
                                value.append((char) Integer.parseInt(hex, 16));
                                index += 4;
                            }
                            break;
                        default:
                            value.append(escaped);
                            break;
                    }
                } else {
                    value.append(c);
                }
            }
            return null;
        }

        static String escape(String value) {
            StringBuilder escaped = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"':
                        escaped.append("\\\"");
                        break;
                    case '\\':
                        escaped.append("\\\\");
                        break;
                    case '\b':
                        escaped.append("\\b");
                        break;
                    case '\f':
                        escaped.append("\\f");
                        break;
                    case '\n':
                        escaped.append("\\n");
                        break;
                    case '\r':
                        escaped.append("\\r");
                        break;
                    case '\t':
                        escaped.append("\\t");
                        break;
                    default:
                        if (c < 0x20) {
                            escaped.append(String.format("\\u%04x", (int) c));
                        } else {
                            escaped.append(c);
                        }
                        break;
                }
            }
            return escaped.toString();
        }
    }
}
