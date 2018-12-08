package jp.co.recruit.rine.model;

import java.sql.Timestamp;

public class Chat {
    private Integer id;
    private String comment;
    private String commentBy;
    private Timestamp commentAt;
    private User commentUser;
    private Long count;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getCommentBy() {
        return commentBy;
    }

    public void setCommentBy(String commentBy) {
        this.commentBy = commentBy;
    }

    public Timestamp getCommentAt() {
        return commentAt;
    }

    public void setCommentAt(Timestamp commentAt) {
        this.commentAt = commentAt;
    }

    public User getCommentUser() {
        return commentUser;
    }

    public void setCommentUser(User commentUser) {
        this.commentUser = commentUser;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return "Chat{" +
                "id=" + id +
                ", comment='" + comment + '\'' +
                ", commentBy='" + commentBy + '\'' +
                ", commentAt='" + commentAt + '\'' +
                '}';
    }
}
