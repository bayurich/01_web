package ru.netology;


import java.text.SimpleDateFormat;
import java.util.Date;

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


