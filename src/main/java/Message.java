import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class Message {
    private MessageType type;
    private int receiverId;
    private int senderId;
    private long timestamp;

    public Message(int senderId, MessageType type, int receiverId) {
        this.senderId = senderId;
        this.type = type;
        this.receiverId = receiverId;
        this.timestamp = System.currentTimeMillis();
    }

    private Message(int senderId, MessageType type, int receiverId, long timestamp) {
        this.senderId = senderId;
        this.type = type;
        this.receiverId = receiverId;
        this.timestamp = timestamp;
    }

    public int getSenderId() {
        return senderId;
    }

    public MessageType getType() {
        return type;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String toString() {
        return timestamp + " | From: " + senderId + " | " + type ;
    }

    public String toLogString() {
        return LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + " | From: " + senderId + " | " + type;
    }

    public static Message fromString(String message) {
        if (message == null) {
            return null;
        }
        String[] parts = message.split(" \\| ");
        if (parts.length != 3) {
            return null;
        }
        long timestamp = Long.parseLong(parts[0].trim());
        int senderId = Integer.parseInt(parts[1].split(": ")[1].trim());
        MessageType type = MessageType.valueOf(parts[2].trim());
        return new Message(senderId, type, -1, timestamp);
    }
}
