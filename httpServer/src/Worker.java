import Logger.Logger;
import RSON.RSON;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;

public class Worker {

    final RSON conf;
    Logger logger;
    Socket socket;
    final String CRLF = "\r\n";

    InputStream inputStream;
    InputStreamReader reader;
    OutputStream writer;


    public Worker(RSON conf, Socket socket) {
        this.socket = socket;
        this.conf = conf;
        logger = new Logger(this.conf.getValue("loggerpath"));
    }

    public void run() {
        logger.log(socket.getInetAddress() + " connected");
        String request = "";

        try {
            inputStream = socket.getInputStream();
            writer = socket.getOutputStream();

            request = getRequest();
            requestParser parser = new requestParser(request);

            logger.log(parser.requestType + " " + parser.requestedFile);
            ResponseBuilder response = new ResponseBuilder();
            if (parser.validRequest) {
                ContentType ct = new ContentType();
                if (parser.requestType.toLowerCase().equals("get")) {
                    File f = new File(Main.WEBROOT + parser.requestedFile);
                    if (f.exists() || parser.requestedFile.equals("/")) {
                        byte[] content = Files.readAllBytes(f.toPath());
                        if (ct.isText(parser.fileType)) {

                            if (parser.requestedFile.equals("/")) {

                                response.addToHeader("HTTP/1.1 200 ok");
                                response.addToHeader("Content-Type: text/html");
                                response.addToHeader("Content-Length: " + content.length);
                                response.addToHeader("Connection: close");
                                response.addToBody(f);

                            } else {
                                response.addToHeader("HTTP/1.1 200 ok");
                                response.addToHeader("Content-Type: text/" + parser.fileType);
                                response.addToHeader("Content-Length: " + content.length);
                                response.addToHeader("Connection: close");
                                response.addToBody(f);
                            }


                        } else {

                            response.addToHeader("HTTP/1.1 200 ok");
                            response.addToHeader("Content-Type: image/" + parser.fileType);
                            response.addToHeader("Connection: close");
                            response.addToBody(f);

                        }

                    } else {
                        if ((parser.fileType.equals("null") || ct.isText(parser.fileType)) && conf.getBool("404")) {
                            f = new File(Main.WEBROOT + "/404.html");
                            byte[] content = Files.readAllBytes(f.toPath());
                            response.addToHeader("HTTP/1.1 404 Not Found");
                            response.addToHeader("Content-Type: text/html");
                            response.addToHeader("Content-Length: " + content.length);
                            response.addToHeader("Connection: close");
                            response.addToBody(f);
                        } else {
                            response.addToHeader("HTTP/1.1 404 Not Found");
                            response.addToHeader("Connection: close");
                        }

                    }
                    writer.write(response.getResponse());

                }
            } else {
                //iternal server error | invalid request
                writer.write(("HTTP/1.1 500 Internal Server Error" + CRLF + "Connection: close" + CRLF + CRLF).getBytes());
            }
            writer.flush();
        } catch (IOException e) {
            //this probably need more error handling but i dont wanna do it :c
            logger.log("IOexception");
        }

    }


    private String getRequest() throws IOException {
        String request = "";
        reader = new InputStreamReader(inputStream);
        Instant start = Instant.now();
        while (!reader.ready()) {
            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);
            if (duration.toSeconds() > 2.5)
                break;
        }
        while (reader.ready()) {
            request += (char) reader.read();
        }
        //logger.log("Full request: " + request);
        return request;
    }


    private String toString(File file) throws FileNotFoundException {

        Scanner scanner = null;
        scanner = new Scanner(file);
        String str = "";
        while (scanner.hasNextLine())
            str += scanner.nextLine() + "\n";

        scanner.close();
        return str + " ";

    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public void close() throws IOException {
        if (inputStream != null && writer != null && reader != null) {
            reader.close();
            inputStream.close();
            writer.close();
        } else
            logger.log("The socket variables haven't been initialized.");

        logger.log(socket.getInetAddress() + " connection closed");
        socket.close();
        logger.log("");
    }

}