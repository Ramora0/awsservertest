package com.leedavis.DynamoDB;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.leedavis.Constants;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

public class DynamoDB {
  private static final String date = DateTimeFormatter
      .ofPattern("MM/dd/yyyy")
      .format(LocalDate.now());

  private static DynamoDbClient ddb;

  public static void open() {
    if (ddb != null)
      return;

    ddb = DynamoDbClient.builder()
        .region(Region.US_EAST_1)
        .build();

    var todaysHistory = getItem(Constants.HISTORY_TABLE_NAME, "date", date);

    if (todaysHistory == null) {
      putItemInTable(Constants.HISTORY_TABLE_NAME, Map.of(
          "date", AttributeValue.builder().s(date).build(),
          "timesSeen", AttributeValue.builder().l(List.of()).build()));
    }
  }

  public static void close() {
    ddb.close();
    ddb = null;
  }

  public static void acceptReads(List<TagRead> readTags) {
    // if (readTags.size() == 0)
    // return;

    readTags.sort((tag1, tag2) -> {
      long time1 = tag1.getTimestamp();
      long time2 = tag2.getTimestamp();
      return (int) (time1 - time2);
    });

    TagReads currentTags = getTags();
    long currentTime = System.currentTimeMillis(); // readTags.get(readTags.size() - 1).getLatest();

    for (TagRead readTag : readTags) {
      TagRead currentTag = currentTags.getOrCreate(readTag.id);
      long newTimestamp = readTag.getTimestamp();
      if (currentTag.timestamps.size() > 0) {
        long previousTimestamp = currentTag.getLatest();
        long timeDifference = newTimestamp - previousTimestamp;

        if (timeDifference < Constants.LOOP_TIME - Constants.MAX_ERROR) {
          System.out.println("ME: Tag " + currentTag.id + " was too close together.");
          DynamoDB.storeTagHistory(readTag.id, previousTimestamp, currentTag.getFirst());
          currentTag.timestamps.clear();
        }
      }
      currentTag.timestamps.add(newTimestamp);
    }

    for (TagRead currentTag : currentTags.tags.values()) {
      if (currentTag.timestamps.size() == 0)
        continue;

      long latestSight = currentTag.getLatest();
      if (latestSight < currentTime - Constants.LOOP_TIME - Constants.MAX_ERROR) {
        System.out.println("ME: Tag " + currentTag.id + " has timed out.");
        DynamoDB.storeTagHistory(currentTag.id, latestSight, currentTag.getFirst());
        currentTag.timestamps.clear();
      }
    }

    updateTags(currentTags);
  }

  public static TagReads getTags() {
    Map<String, AttributeValue> item = getItem("wasabi-rfid", "id", "tag_timestamps");
    if (item == null)
      return null;

    return new TagReads(item.get("tags"));
  }

  public static void updateTags(TagReads tags) {
    putItemInTable(Constants.TABLE_NAME, tags.toDynamoDBItem());
  }

  public static void storeTagHistory(String id, long lastTimestamp, long firstTimestamp) {
    Map<String, AttributeValue> key = new HashMap<>();
    key.put("date", AttributeValue.builder().s(date).build());

    Map<String, String> expressionAttributeNames = new HashMap<>();
    expressionAttributeNames.put("#ts", "timesSeen");

    Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
    expressionAttributeValues.put(":newTime", AttributeValue.builder().l(
        AttributeValue.builder().m(
            Map.of(
                "id", AttributeValue.builder().s(id).build(),
                "lastTimestamp", AttributeValue.builder().n(String.valueOf(lastTimestamp)).build(),
                "firstTimestamp", AttributeValue.builder().n(String.valueOf(firstTimestamp)).build()))
            .build())
        .build());

    UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
        .tableName(Constants.HISTORY_TABLE_NAME)
        .key(key)
        .updateExpression("SET #ts = list_append(#ts, :newTime)")
        .expressionAttributeNames(expressionAttributeNames)
        .expressionAttributeValues(expressionAttributeValues)
        .build();

    try {
      ddb.updateItem(updateItemRequest);
    } catch (ResourceNotFoundException e) {
      System.err.format("Error: The Amazon DynamoDB table \"%s\" can't be found.\n", Constants.HISTORY_TABLE_NAME);
      System.err.println("Be sure that it exists and that you've typed its name correctly!");
      System.exit(1);
    } catch (DynamoDbException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }

  }

  // BASE FUNCTIONS \\

  public static Map<String, AttributeValue> getItem(String tableName, String key, String value) {
    HashMap<String, AttributeValue> keyToGet = new HashMap<>();
    keyToGet.put(key, AttributeValue.builder()
        .s(value)
        .build());

    GetItemRequest request = GetItemRequest.builder()
        .overrideConfiguration(o -> o.putHeader("Accept-Encoding", "gzip"))
        .key(keyToGet)
        .tableName(tableName)
        .build();

    try {
      Map<String, AttributeValue> returnedItem = ddb.getItem(request).item();
      if (returnedItem.isEmpty())
        throw new NoSuchElementException("No item found with key " + key + " value " + value);

      return returnedItem;
    } catch (DynamoDbException e) {
      System.err.println("DynamoDBEception!\n" + e.getMessage());
      return null;
    } catch (NoSuchElementException e) {
      System.err.println("Couldn't find element!\n" + e.getMessage());
      return null;
    }
  }

  public static void putItemInTable(String tableName, Map<String, AttributeValue> item) {
    PutItemRequest request = PutItemRequest.builder()
        .tableName(tableName)
        .item(item)
        .build();

    try {
      ddb.putItem(request);
    } catch (ResourceNotFoundException e) {
      System.err.format("Error: The Amazon DynamoDB table \"%s\" can't be found.\n", tableName);
      System.err.println("Be sure that it exists and that you've typed its name correctly!");
      System.exit(1);
    } catch (DynamoDbException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
