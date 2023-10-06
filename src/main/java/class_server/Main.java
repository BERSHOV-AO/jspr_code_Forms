package class_server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


public class Main {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final int SERVER_PORT = 9999;

    public static void main(String[] args) {
        final var allowedMethod = List.of(GET, POST); // List<String>

        try (final var serverSocket = new ServerSocket(SERVER_PORT)) {
            while (true) {
                try (
                        final var socket = serverSocket.accept();
                        final var in = new BufferedInputStream(socket.getInputStream());
                        final var out = new BufferedOutputStream(socket.getOutputStream());
                ) {
                    // Лимит на запрос к нашему серверу, если у нас будет приходить слишком много пользователей,
                    // и у них будут слишком большие запросы, у нас не хватит памяти их всех обрабатывать, зависнем
                    // Лимит на request line + заголовки, по сути, лимит на количество символов.
                    final var limit = 4096;

                    // В классе BufferedInputStream метод mark(limit) используется для
                    // установки метки (пометки) в текущей позиции ввода с ограничением на размер буфера.
                    // Когда вызывается метод mark(limit) с аргументом limit, BufferedInputStream сохраняет
                    // внутреннее состояние буфера чтения и устанавливает метку в текущей позиции чтения с
                    // ограничением на размер буфера, указанным аргументом limit. Это позволяет вам вернуться к
                    // этому состоянию позже, используя метод reset().
                    // Используется для потока на чтение, он отмечает количество прочитанной информации, которая в
                    // нашем потоке будет считана, он дет в связке с reset, что бы потом прочитанную информацию можно
                    // было бы прочитать еще раз. Для удобства навигирования по информации которая нам пришла.
                    in.mark(limit);
                    // У нас есть буфер, в который мы будем считывать информацию
                    final var buffer = new byte[limit];
                    final var read = in.read(buffer);

                    // Ищем request line
                    final var requestLineDelimiter = new byte[]{'\r', '\n'};
                    // indexOf находим где заканчивается requestLineDelimiter
                    final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
                    // Если мы не смогли найти конец requestLineDelimiter, нам вернулся индекс -1, запрос сформирован
                    // не корректно, то мы обращаемся к методу badRequest(out);
                    if (requestLineEnd == -1) {
                        badRequest(out);
                        continue;
                    }

                    // Зачитываем request line, он должен состоять из трех параметров
                    final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
                    // если не три параметра, запрос сформирован не корректно, вызываем badRequest(out);
                    if (requestLine.length != 3) {
                        badRequest(out);
                        continue;
                    }

                    // Параметры которые должны придти, - это метод, путь, и само содержимое(тело запроса).

                    // Проверяем, содержится ли тот метод, который к нам пришел в том списке,
                    // который допустим для нашего сервера, у нас допустимые GET, POST.
                    final var method = requestLine[0];
                    if (!allowedMethod.contains(method)) {
                        badRequest(out);
                        continue;
                    }
                    System.out.println(method);

                    // Определяем путь, по которому к нам обратились, проверяем первый символ в пути. "/"
                    // Если данный символ отсутствует, то вызываем метод badRequest(out);
                    final var path = requestLine[1];
                    if (!path.startsWith("/")) {
                        badRequest(out);
                    }
                    System.out.println(path);

                    // После этого считываем заголовки (ищем заголовки)
                    final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
                    final var headersStart = requestLineEnd + requestLineDelimiter.length;
                    final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
                    if (headersEnd == -1) {
                        badRequest(out);
                        continue;
                    }

                    // отматываем на начала буфера
                    in.reset();
                    // пропускаем requestLine
                    in.skip(headersStart);

                    final var headersBytes = in.readNBytes(headersEnd - headersStart);
                    final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
                    System.out.println(headers);

                    // для GET тела нет
                    if (!method.equals(GET)) {
                        in.skip(headersDelimiter.length);
                        // вычитываем Content-Length, что бы прочитать body
                        final var contentLength = extractHeader(headers, "Content-Length");
                        if (contentLength.isPresent()) {
                            final var length = Integer.parseInt(contentLength.get());
                            final var bodyBytes = in.readNBytes(length);

                            final var body = new String(bodyBytes);
                            System.out.println(body);
                        }
                    }

                    out.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    out.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

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
}

