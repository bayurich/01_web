package ru.netology;


import java.io.BufferedOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {

  static final int PORT = 9999;
  static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ");

  public static void main(String[] args) {

    final Server server = new Server();

    // добавление хендлеров (обработчиков)
    server.addHandler("GET", "/messages", new Handler() {
      public void handle(Request request, BufferedOutputStream responseStream) throws IOException {
        // TODO: handlers code
        String response = "Response for GET /messages";
        server.setResponse200(responseStream, response.getBytes());
      }
    });
    server.addHandler("POST", "/messages", new Handler() {
      public void handle(Request request, BufferedOutputStream responseStream) throws IOException {
        // TODO: handlers code
        responseStream.write("Response for POST /messages".getBytes());
        responseStream.flush();
      }
    });

    log("Запуск сервера...");
    server.listen(PORT);
  }

  public static void log(String mes){
    System.out.println(sdf.format(new Date()) + mes);
  }
}


