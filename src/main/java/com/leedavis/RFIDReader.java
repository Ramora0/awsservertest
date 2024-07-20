package com.leedavis;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.leedavis.DynamoDB.DynamoDB;
import com.leedavis.DynamoDB.TagIDManager;
import com.leedavis.DynamoDB.TagRead;
import com.sirit.data.DataManager;
import com.sirit.driver.ConnectionException;
import com.sirit.mapping.EventInfo;
import com.sirit.mapping.InfoManager;
import com.sirit.mapping.ReaderException;
import com.sirit.mapping.ReaderManager;

public class RFIDReader {
  /** Most basic class for interacting with reader */
  public static DataManager dataManager;
  /** Used to register events with the reader */
  public static ReaderManager readerManager;

  /** The id of the event that runs every time the reader reads an RFID */
  public static String eventChannelId = null;
  /** Represents the last tagID of the previous flush so we don't double read */
  public static String lastTagID = null;

  /**
   * Maps the TagID read to the TagRead object, so tags aren't read multiple times
   */
  public static Map<String, TagRead> tagsRead = new HashMap<>();
  /**
   * Since the antenna reads stationary tags, we want to ignore tags that have
   * been read 3+ times consecutively
   */
  public static Map<String, Integer> consecutiveReads = new HashMap<>();

  /**
   * Some tags are null for some reason, temporarily storing them to figure out
   * whats going on
   */
  public static Set<String> nullTags = new HashSet<>();

  /** Launches the reader code and reads tags, flushing them every 10 seconds */
  public static void start() {
    String ipAddress = Constants.READER_IP;

    // Do basic startup tasks
    launch(ipAddress);
    try {
      // While we receive events asynchronously, we need to flush the reads every so
      // often.
      while (true) {
        Thread.sleep(Constants.SLEEP_TIME);
        flushTags();
      }
    } catch (Exception e) {
      System.out.println("ME: Exception in Main:\n" + e.toString());
      e.printStackTrace();
    } finally {
      System.out.println("ME: Null tags:");
      // Print the null tags for debugging
      for (String tag : nullTags) {
        System.out.println(tag);
      }
      // basic shutdown tasks
      shutdown();
    }
  }

  public static void launch(String ipAddress) {
    try {
      DynamoDB.open();
      System.out.println("ME: DynamoDB connection opened.");
      // Get the RFID to tagID mapping
      TagIDManager.loadTagIDs();
      System.out.println("ME: Tag IDs gotten");

      // Open the connection with the reader
      dataManager = new DataManager(DataManager.ConnectionTypes.SOCKET, ipAddress, 0);
      System.out.println("ME: Opening connection to RFID reader at " + ipAddress);
      dataManager.open();

      // Print basic info about the reader to make sure the connection works
      printInfo();

      readerManager = new ReaderManager(dataManager);
      // Inform the reader of our event
      enforceEventChannelId();
      // Register the event so it actually goes off
      register();
    } catch (Exception e) {
      System.out.println("ME: Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void shutdown() {
    try {
      readerManager.eventsUnregister(eventChannelId, Constants.REGISTER_EVENT);
      System.out.println("ME: Event unregistered");

      dataManager.close();
      System.out.println("ME: Connection closed");
      DynamoDB.close();
      System.out.println("ME: DynamoDB connection closed.");
    } catch (ConnectionException e) {
      e.printStackTrace();
    }
  }

  /**
   * Essentially creates the event with the RFID reader; the event isn't
   * activated, however, and needs to be registered
   */
  private static void enforceEventChannelId() {
    if (eventChannelId == null) {
      System.out.println("ME: Attempting to get event channel ID.");
      eventChannelId = dataManager.getEventChannel(RFIDReader::EventFound);
      System.out.println("ME: Event Channel ID set to: " + eventChannelId);
    }
  }

  public static void EventFound(Object sender, EventInfo info) {
    String rfid = info.getParameter(EventInfo.EVENT_TAG_ARRIVE_PARAMS.TAG_ID);
    String tagID = TagIDManager.getID(rfid);
    // String timestamp = info.getParameter(EventInfo.EVENT_TAG_ARRIVE_PARAMS.TIME);
    long timestamp = System.currentTimeMillis();
    // System.out.println("ME: " + timestamp + ": " + rfid + " -> " + tagID);
    // 231n

    if (tagID == null) {
      if (!nullTags.contains(rfid)) {
        System.out.println("ME: TagID is null. RFID is " + rfid + ".");
        nullTags.add(rfid);
      }
      return;
    }

    // If we've already seen it this flush, ignore it. Also if it was the last one
    // we saw last time, ignore it.
    if (tagsRead.containsKey(tagID) || tagID.equals(lastTagID)) {
      return;
    }

    lastTagID = tagID;
    TagRead read = new TagRead(tagID, timestamp);
    tagsRead.put(tagID, read);
    System.out.println("ME: Tag: " + tagID + " (" + (consecutiveReads.getOrDefault(tagID, 0) + 1) + ")");
  }

  /**
   * Uses the channel ID to register for events. The channel ID references the
   * event handler we passed earlier.
   */
  public static void register() {
    boolean registrationSuccess = readerManager.eventsRegister(eventChannelId, Constants.REGISTER_EVENT);
    if (!registrationSuccess) {
      try {
        throw new Exception("Failure to register for event: " + readerManager.getLastErrorMessage());
      } catch (Exception e) {
        System.out.println("ME: Error during event registration: " + e.toString());
      }
    } else {
      System.out.println("ME: Event registration success!");
    }
  }

  public static void flushTags() {
    List<TagRead> tags = new ArrayList<>(tagsRead.values().stream().toList());

    System.out.println("ME: Flush!");

    Set<String> currentTagIds = tags.stream().map(TagRead::getID).collect(Collectors.toSet());

    // Iterate over the consecutiveReads map
    consecutiveReads.keySet().removeIf(key -> !currentTagIds.contains(key) && consecutiveReads.get(key) < 3);
    currentTagIds.forEach(id -> consecutiveReads.merge(id, 1, Integer::sum));

    // for (Map.Entry<String, Integer> entry : consecutiveReads.entrySet()) {
    // System.out.println("ME: " + entry.getKey() + " -> " + entry.getValue());
    // }

    DynamoDB.acceptReads(new ArrayList<>(tags.stream().filter(tag -> {
      int count = consecutiveReads.getOrDefault(tag.getID(), 0);
      return count < 3;
    }).toList()));
    tagsRead.clear();
  }

  public static void printInfo() throws ReaderException, ConnectionException {
    // TODO: Syncs the readers time

    System.out.println("ME: Reader Information:");
    System.out.println("ME: -------------------");
    System.out.println("ME: Identification:");
    System.out.println("ME:   Serial Number: " + dataManager.get(InfoManager.SERIAL_NUMBER));

    System.out.println("ME: Description:");
    System.out.println("ME:   Name: " + dataManager.get(InfoManager.NAME));

    System.out.println("ME: Manufacturer:");
    System.out.println("ME:   Make: " + dataManager.get(InfoManager.MAKE));
    System.out.println("ME:   Manufacturer: " + dataManager.get(InfoManager.MANUFACTURER));
    System.out.println("ME:   Manufacturer Description: " + dataManager.get(InfoManager.MANUFACTURER_DESCRIPTION));
    System.out.println("ME:   Model: " + dataManager.get(InfoManager.MODEL));
    System.out.println("ME:   Submodel: " + dataManager.get(InfoManager.SUBMODEL));

    System.out.println("ME: Time:");
    String time = dataManager.get(InfoManager.TIME);
    long readerMillis = LocalDateTime.parse(time)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli();
    long millis = System.currentTimeMillis();
    System.out.println("ME:   Time: " + time);
    System.out.println("ME:   Time Reporting: " + dataManager.get(InfoManager.TIME_REPORTING));
    System.out.println("ME:   Time Zone: " + dataManager.get(InfoManager.TIME_ZONE));

    System.out.println();
    if (millis > readerMillis) {
      System.out.println("ME: Reader time is behind by " + (millis - readerMillis) + " milliseconds.");
    } else if (millis < readerMillis) {
      System.out.println("ME: Reader time is ahead by " + (readerMillis - millis) + " milliseconds.");
    } else {
      System.out.println("ME: Reader time is in sync. (super weird its down to the millisecond)");
    }
  }
}
