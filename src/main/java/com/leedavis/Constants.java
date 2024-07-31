package com.leedavis;

public class Constants {
  public static final String REGISTER_EVENT = "event.tag.report";
  public static final long SLEEP_TIME = 1000 * 60 + 1;
  public static final String READER_IP = "72.221.12.244";// "192.168.250.110";//
  public static final String TABLE_NAME = "wasabi-rfid";
  public static final String HISTORY_TABLE_NAME = "wasabi-rfid-history";

  public static final long LOOP_TIME = 13 * 60 * 1000 + 5 * 1000 + 15 * 10;
  public static final long MAX_ERROR = 45 * 1000;
}