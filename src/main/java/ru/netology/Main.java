package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public class Main {

  static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ");

  public static void main(String[] args) {

    log("Запуск сервера...");
    new Server().start();
  }

  public static void log(String mes){
    System.out.println(sdf.format(new Date()) + mes);
  }
}


