package com.leedavis.DynamoDB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class TagRead {
  String id;
  List<Long> timestamps;

  public TagRead(String id, AttributeValue value) {
    this.id = id;
    this.timestamps = value.l().stream()
        .map(timestamp -> Long.parseLong(timestamp.n()))
        .collect(Collectors.toList());
  }

  public TagRead(String id, Long... timestamps) {
    this.id = id;
    this.timestamps = new ArrayList<Long>(Arrays.asList(timestamps));
  }

  public String getID() {
    return id;
  }

  public long getTimestamp() {
    if (timestamps.size() > 1) {
      throw new NoSuchElementException("Tag has multiple timestamps");
    }

    return timestamps.get(0);
  }

  public long getFirst() {
    return timestamps.get(0);
  }

  public long getLatest() {
    return timestamps.get(timestamps.size() - 1);
  }

  public AttributeValue toDynamoDBItem() {
    List<AttributeValue> timestampList = timestamps.stream()
        .map(timestamp -> AttributeValue.builder().n(Long.toString(timestamp)).build())
        .collect(Collectors.toList());
    return AttributeValue.builder().l(timestampList).build();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Tag: " + id + "\n");
    for (long timestamp : timestamps) {
      sb.append("Timestamp: " + timestamp + "\n");
    }
    return sb.toString();
  }
}
