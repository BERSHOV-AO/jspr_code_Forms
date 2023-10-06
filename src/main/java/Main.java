import java.io.IOException;

public class Main {

    private static final int PORT_SERVER = 9999;

    public static void main(String[] args) {
        Server server = new Server(PORT_SERVER);

        server.addHandler("POST", "/messages", (request, responseStream) -> {
            try {
                server.outContentResponse(responseStream, "502", "Bad Gateway!!!");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        server.addHandler("GET", "/messages", (request, responseStream) -> {
            try {
                server.outContentResponse(responseStream, "404", "Not Found!!!");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        server.addHandler("GET", "/", ((request, outputStream) ->
                server.defaultHandler(outputStream, "index.html")));

        server.start();
    }
}
