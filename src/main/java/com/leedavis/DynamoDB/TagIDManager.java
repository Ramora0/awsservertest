package com.leedavis.DynamoDB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Maps the RFID tags to the internal tagID */
public class TagIDManager {
  static Map<String, String> tagIDMap = new HashMap<>();

  public static void loadTagIDs() {
    var item = DynamoDB.getItem("wasabi-rfid", "id", "tag_ids");
    List<AttributeValue> tagIDs = item.get("ids").l();
    for (AttributeValue tag : tagIDs) {
      Map<String, AttributeValue> tagMap = tag.m();
      String rfid1 = tagMap.get("rfid1").s();
      String rfid2 = tagMap.get("rfid2").s();
      String tagID = tagMap.get("tagID").s();

      tagIDMap.put(rfid1, tagID);
      tagIDMap.put(rfid2, tagID);
    }
  }

  public static String getID(String tagID) {
    return "1";
    // return tagIDMap.get(tagID);
  }
}
