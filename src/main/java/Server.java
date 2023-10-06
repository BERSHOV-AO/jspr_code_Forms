import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final String NOT_FOUND_TEXT = "Not Found";
    private final String NOT_FOUND_CODE = "404";
    private final int NUMBER_THREADS = 64;
    private final int PORT_SERVER_SOCKET;
    List<String> validPathsList = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html",
            "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");

    private ExecutorService threadPool;
    private ConcurrentHashMap<String, Map<String, Handler>> handlersStorageMap;

    public Server(int port) {

        PORT_SERVER_SOCKET = port;
        threadPool = Executors.newFixedThreadPool(NUMBER_THREADS);
        handlersStorageMap = new ConcurrentHashMap<>();
    }

    public void start() {
        try (final var serverSocket = new ServerSocket(PORT_SERVER_SOCKET)) {
            System.out.println("Веб-сервер запущен на порту " + PORT_SERVER_SOCKET);
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Подключение принято от клиента: " + clientSocket.getInetAddress().getHostAddress());
                threadPool.execute(() -> handleConnection(clientSocket));
            }
        } catch (IOException e) {
            // throw new RuntimeException(e);
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private void handleConnection(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             final var out = new BufferedOutputStream(clientSocket.getOutputStream())) {

            final var requestLine = in.readLine();
            System.out.println("Получен HTTP-запрос: " + requestLine);

            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                System.out.println("not path");
                clientSocket.close();
                return;
            }

            String method = parts[0];
            final var path = parts[1];
            Request request = createRequest(method, path);


            // Проверяем наличие плохих запросов и разрываем соединение
            if (request == null || !handlersStorageMap.containsKey(request.getMethod())) {
                outContentResponse(out, NOT_FOUND_CODE, "Error Request");
                return;
            }

            // Получаем путь, MAP
            Map<String, Handler> handlerMap = handlersStorageMap.get(request.getMethod());
            String requestPath = request.getPath();
            if (handlerMap.containsKey(requestPath)) {
                Handler handler = handlerMap.get(requestPath);
                handler.handle(request, out);
            } else {
                // не найден
                if (!validPathsList.contains(request.getPath())) {
                    outContentResponse(out, NOT_FOUND_CODE, NOT_FOUND_TEXT);
                } else {
                    // default
                    System.out.println("default handler");
                    defaultHandler(out, path);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void addHandler(String method, String path, Handler handler) {
        if (!handlersStorageMap.containsKey(method)) {
            handlersStorageMap.put(method, new HashMap<>());
        }
        handlersStorageMap.get(method).put(path, handler);
    }

    public void outContentResponse(BufferedOutputStream out, String code, String status) throws IOException {
        out.write((
                "HTTP/1.1 " + code + " " + status + "\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();

    }

    private Request createRequest(String method, String path) {
        if (method != null) {
            return new Request(method, path);
        } else {
            return null;
        }
    }

    void defaultHandler(BufferedOutputStream out, String path) throws IOException {
        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        // Если у нас приходит запрос на страничку "/classic.html"
        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + content.length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.write(content);
            out.flush();
            return;
        }

        final var length = Files.size(filePath);
        out.write((
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + mimeType + "\r\n" +
                        "Content-Length: " + length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }
}