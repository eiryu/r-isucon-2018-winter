package jp.co.recruit.rine.repository;

import jp.co.recruit.rine.model.Chat;
import jp.co.recruit.rine.model.User;
import org.apache.tomcat.jni.Time;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class ChatRepository {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    RowMapper<Chat> rowMapper = (rs, i) -> {
        Chat chat = new Chat();
        chat.setId(rs.getInt("id"));
        chat.setComment(rs.getString("comment"));
        chat.setCommentBy(rs.getString("comment_by"));
        chat.setCommentAt(rs.getTimestamp("comment_at"));
        return chat;
    };

    public List<Chat> findChats(List<Integer> chatIds) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("chatIds", chatIds);
        String sql = "SELECT * FROM chat WHERE id IN (:chatIds) ORDER BY comment_at";
        return jdbcTemplate.query(sql, source, rowMapper);
    }

    public Integer insertChat(String comment,
                          User user) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("comment", comment)
                .addValue("comment_by", user.getUsername())
                .addValue("comment_at", timestamp);
        String sql = "INSERT INTO chat (comment, comment_by, comment_at) VALUES (:comment, :comment_by, :comment_at)";
        jdbcTemplate.update(sql, source, keyHolder);
        return keyHolder.getKey().intValue();
    }
}
