package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ru.netology.Main.log;


public class Server {


    static final int POOL_SIZE = 64;

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
                try (final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     final BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());) {
                    // read only request line for simplicity
                    // must be in form GET /path HTTP/1.1
                    final String requestLine = in.readLine();
                    final String[] parts = requestLine.split(" ");

                    if (parts.length != 3) {
                        // just close socket
                        return;
                    }

                    final String method = parts[0];
                    final String path = parts[1];

                    //TODO добавить чтение заголовков и тела
                    Request request = new Request(method, path, null, null);

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

    public void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) {
            handlers.put(method, new ConcurrentHashMap<>());
        }
        handlers.get(method).put(path, handler);
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
}
