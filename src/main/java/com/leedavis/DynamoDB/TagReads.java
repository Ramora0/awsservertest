package com.leedavis.DynamoDB;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Represents the DynamoDB table item of all tags and their tag reads */
public class TagReads {
  Map<String, TagRead> tags = new HashMap<>();

  public TagReads(AttributeValue tags) {
    Map<String, AttributeValue> tagMap = tags.m();

    for (Map.Entry<String, AttributeValue> entry : tagMap.entrySet()) {
      String id = entry.getKey();
      TagRead tag = new TagRead(id, entry.getValue());
      this.tags.put(tag.id, tag);
    }
  }

  public Map<String, AttributeValue> toDynamoDBItem() {
    Map<String, AttributeValue> item = new HashMap<>();
    Map<String, AttributeValue> tagMap = new HashMap<>();

    for (Map.Entry<String, TagRead> entry : tags.entrySet()) {
      TagRead tag = entry.getValue();
      tagMap.put(tag.id, tag.toDynamoDBItem());
    }

    item.put("id", AttributeValue.builder()
        .s("tag_timestamps")
        .build());
    item.put("tags", AttributeValue.builder()
        .m(tagMap)
        .build());

    return item;
  }

  public TagRead get(String id) {
    return tags.get(id);
  }

  public TagRead getOrCreate(String id) {
    TagRead tag = tags.get(id);
    if (tag == null) {
      tag = new TagRead(id);
      tags.put(id, tag);
    }
    return tag;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, TagRead> entry : tags.entrySet()) {
      sb.append(entry.getValue().toString() + "\n");
    }
    return sb.toString();
  }
}
