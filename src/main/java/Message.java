import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {
    private MessageType type;
    private String content;
    private int receiverId;
    private int senderId;
    private LocalDateTime timestamp;

    public Message(int senderId, MessageType type, int receiverId) {
        this.senderId = senderId;
        this.type = type;
        this.receiverId = receiverId;
    }

    public int getSenderId() {
        return senderId;
    }

    public MessageType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public String toString() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + " | From: " + senderId + " | " + type ;
    }

    public static Message fromString(String message) {
        if (message == null) {
            return null;
        }
        String[] parts = message.split(" \\| ");
        if (parts.length != 3) {
            return null;
        }
        LocalDateTime timestamp = LocalDateTime.parse(parts[0].trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        int senderId = Integer.parseInt(parts[1].split(": ")[1].trim());
        MessageType type = MessageType.valueOf(parts[2].trim());
        return new Message(senderId, type, -1);
    }
}
