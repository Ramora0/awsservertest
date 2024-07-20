package com.leedavis;

public class Main {

  public static void main(String[] args) {
    // ip: 192.168.250.110
    // Event port: 50008
    // Comand port: 50007
    // com.llrp.client_ip_address

    // Test DynamoDB
    // DynamoDB.open();
    // TagReads tags = DynamoDB.getTags();
    // System.out.println("ME: "+tags);
    // DynamoDB.close();

    // Start Reader
    RFIDReader.start();

    // RFIDReader.launch(Constants.READER_IP);
    // System.out.println("ME: Shutting down...");
    // RFIDReader.shutdown();

    // DynamoDB.open();
    // RFIDReader.flushTags();
  }
}