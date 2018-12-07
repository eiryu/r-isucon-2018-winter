package jp.co.recruit.rine.model;

public class BelongsChatGroup {
    private Integer chatId;
    private Integer groupId;

    public Integer getChatId() {
        return chatId;
    }

    public void setChatId(Integer chatId) {
        this.chatId = chatId;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    @Override
    public String toString() {
        return "BelongsChatGroup{" +
                "chatId=" + chatId +
                ", groupId=" + groupId +
                '}';
    }
}
