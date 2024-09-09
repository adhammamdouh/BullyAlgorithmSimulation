public class Message {
    private MessageType type;
    private String content;
    private int receiverId;

    public Message(MessageType type, String content, int receiverId) {
        this.type = type;
        this.content = content;
        this.receiverId = receiverId;
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
}
