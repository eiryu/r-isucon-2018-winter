package jp.co.recruit.rine.model;

public class Group {
    private Integer id;
    private String name;
    private String owner;
    private Long userCount;
    private Long chatCount;

    public Group() {
    }

    public Group(Integer id, String name, String owner, Long userCount, Long chatCount) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.userCount = userCount;
        this.chatCount = chatCount;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Long getUserCount() {
        return userCount;
    }

    public void setUserCount(Long userCount) {
        this.userCount = userCount;
    }

    public Long getChatCount() {
        return chatCount;
    }

    public void setChatCount(Long chatCount) {
        this.chatCount = chatCount;
    }

    @Override
    public String toString() {
        return "Group{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", owner='" + owner + '\'' +
                '}';
    }
}
