package jp.co.recruit.rine.model;

public class ReadChat {
    private Integer chatId;
    private String username;

    public Integer getChatId() {
        return chatId;
    }

    public void setChatId(Integer chatId) {
        this.chatId = chatId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return "ReadChat{" +
                "chatId=" + chatId +
                ", username='" + username + '\'' +
                '}';
    }
}
