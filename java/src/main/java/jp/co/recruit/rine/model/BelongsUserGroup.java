package jp.co.recruit.rine.model;

public class BelongsUserGroup {
    private Integer groupId;
    private String username;

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return "BelongsUserGroupRepository{" +
                "groupId=" + groupId +
                ", username='" + username + '\'' +
                '}';
    }
}
