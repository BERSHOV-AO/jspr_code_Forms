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

                    final var method = requestLine[0];
                    if (!allowedMethod.contains(method)) {
                        badRequest(out);
                        continue;
                    }
                    System.out.println(method);

                    final var path = requestLine[1];
                    if (!path.startsWith("/")) {
                        badRequest(out);
                    }
                    System.out.println(path);


                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

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

