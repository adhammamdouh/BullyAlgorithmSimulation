import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {
    private MessageType type;
    private String content;
    private int receiverId;
    private int senderId;

    public Message(int senderId, MessageType type, String content, int receiverId) {
        this.senderId = senderId;
        this.type = type;
        this.content = content;
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
                + " | From: " + senderId + " | " + type + " | " + content;
    }

    public static Message fromString(String message) {
        String[] parts = message.split(" \\| ");
        int senderId = Integer.parseInt(parts[1].split(": ")[1]);
        MessageType type = MessageType.valueOf(parts[2]);
        String content = parts[3];
        return new Message(senderId, type, content, -1);
    }
}
