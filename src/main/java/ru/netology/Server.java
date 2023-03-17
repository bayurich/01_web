package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.netology.Main.log;
import static ru.netology.RequestMethod.GET;


public class Server {


    static final int POOL_SIZE = 64;
    private static final String HEADER_CONTENT_LENGHT = "Content-Length";

    final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");

    ConcurrentMap<String, ConcurrentMap<String, Handler>> handlers = new ConcurrentHashMap<>();

    public void listen(int port) {
        final ExecutorService threadPool = Executors.newFixedThreadPool(POOL_SIZE);

        try (final var serverSocket = new ServerSocket(port)) {
            log("Сервер запущен. Порт: " + port);
            while (true) {
                try {
                    final var socket = serverSocket.accept();
                    //log("request IN");
                    threadPool.submit(process(socket));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Runnable process(Socket socket){

        return  new Runnable() {
            @Override
            public void run() {
                try (final BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                     final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());) {

                    Request request = parseRequest(in);
                    if (request == null) {
                        setResponse400(out);
                        return;
                    }

                    String method = request.getMethod();
                    String path = request.getPath();

                    if (!handlers.containsKey(method)) {
                        setResponse404(out);
                        return;
                    }
                    else if (handlers.get(method).containsKey(path)) {
                        try {
                            handlers.get(method).get(path).handle(request, out);
                        } catch (IOException e) {
                            log("error while executing handler for " + method + " " + path + ": " + e.getMessage());
                            setResponse500(out);
                        }
                    }
                    else if (!validPaths.contains(path)) {
                        setResponse404(out);
                        return;
                    }
                    else {
                        final var filePath = Path.of(".", "public", path);
                        final var mimeType = Files.probeContentType(filePath);

                        // special case for classic
                        if (path.equals("/classic.html")) {
                            final var template = Files.readString(filePath);
                            final var content = template.replace(
                                    "{time}",
                                    LocalDateTime.now().toString()
                            ).getBytes();

                            Map<String,String> headers = new HashMap<>();
                            headers.put("Content-Type", mimeType);
                            headers.put("Content-Length", String.valueOf(content.length));
                            setResponse200(out, headers, content);
                            return;
                        }

                        final var length = Files.size(filePath);

                        Map<String,String> headers = new HashMap<>();
                        headers.put("Content-Type", mimeType);
                        headers.put("Content-Length", String.valueOf(length));
                        setResponse200(out, headers, Files.readAllBytes(filePath));
                    }
                } catch (IOException e) {
                e.printStackTrace();
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    private Request parseRequest(BufferedInputStream in) throws IOException {
        // лимит на request line + заголовки
        final var limit = 4096;

        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);

        // ищем request line
        final var requestLineDelimiter = new byte[]{'\r', '\n'};
        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        if (requestLineEnd == -1) {
            log("error while finding requestLine");
            return null;
        }

        // читаем request line
        final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (requestLine.length != 3) {
            log("error requestLine format: " + Arrays.toString(requestLine));
            return null;
        }

        final var method = requestLine[0];
        /*if (!allowedMethods.contains(method)) {
            return null;
        }*/
        if (!isAllwedMethod(method)) {
            log("error method: " + method);
            return null;
        }


        final var path = requestLine[1];
        if (!path.startsWith("/")) {
            log("error path: " + path);
            return null;
        }


        // ищем заголовки
        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            log("error while finding headers");
            return null;
        }

        // отматываем на начало буфера
        in.reset();
        // пропускаем requestLine
        in.skip(headersStart);

        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        //final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));

        String[] str = new String(headersBytes).split("\r\n");
        final Map<String, String> headers = Stream.of(str)
                .map(s -> s.split(":\\s"))
                .filter(s -> s.length == 2)
                .collect(Collectors.toMap(s -> s[0], s -> s[1]));

        log("headers: " + headers);


        // для GET тела нет
        byte[] bodyBytes = null;
        if (!method.equals(GET.name())) {
            in.skip(headersDelimiter.length);
            // вычитываем Content-Length, чтобы прочитать body
            /*final var contentLength = extractHeader(headers, "Content-Length");
            if (contentLength.isPresent()) {
                final var length = Integer.parseInt(contentLength.get());
                bodyBytes = in.readNBytes(length);

                log("body: " + new String(bodyBytes));
            }*/

            if (headers.containsKey(HEADER_CONTENT_LENGHT)) {
                final String contentLength = headers.get(HEADER_CONTENT_LENGHT);
                final var length = Integer.parseInt(contentLength);

                bodyBytes = in.readNBytes(length);

                //log("body: " + new String(bodyBytes));
            }
            else {
                log("header " + HEADER_CONTENT_LENGHT + " not found");
            }
        }

        Request request = new Request(method, path, headers, bodyBytes);

        log("method: " + request.getMethod());
        log("path: " + request.getPath());
        log("query: " + request.getPath());
        log("====== headers: ===========");
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            log(entry.getKey() + ": " + entry.getValue());
        }
        log("====== headers end ===========");
        log("body: " + request.getBodyAsString());

        return request;
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) {
            handlers.put(method, new ConcurrentHashMap<>());
        }
        handlers.get(method).put(path, handler);
    }

    private void setResponse400(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    private void setResponse404(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    private void setResponse500(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 500 Internal Server Error\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    public void setResponse200(BufferedOutputStream out, byte[] content) throws IOException {
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Length: " + content.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.write(content);
        out.flush();
    }

    public void setResponse200(BufferedOutputStream out, Map<String, String> headers, byte[] content) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put("Connection", "close");
        for (Map.Entry<String,String> entry : headers.entrySet()) {
            sb.append(entry.getKey());
            sb.append(": ");
            sb.append(entry.getValue());
            sb.append("\r\n");
        }
        sb.append("\r\n");
        //log("response:\n" + sb.toString());

        out.write(sb.toString().getBytes());
        out.write(content);
        out.flush();
    }

    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static boolean isAllwedMethod(String name){
        for (RequestMethod method : RequestMethod.values()) {
            if (method.name().equals(name)) {
                return true;
            }
        }

        return false;
    }

}
