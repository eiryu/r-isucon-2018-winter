package jp.co.recruit.rine.repository;

import jp.co.recruit.rine.model.ReadChat;
import jp.co.recruit.rine.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class ReadChatRepository {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    RowMapper<ReadChat> rowMapper = (rs, i) -> {
        ReadChat readChat = new ReadChat();
        readChat.setChatId(rs.getInt("chat_id"));
        readChat.setUsername(rs.getString("username"));
        return readChat;
    };

    public int markRead(Integer chatId, User user) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("chatId", chatId)
                .addValue("username", user.getUsername());
        String sql = "INSERT INTO read_chat (chat_id, username) VALUES (:chatId, :username) ON DUPLICATE KEY UPDATE chat_id = :chatId, username = :username";
        return jdbcTemplate.update(sql, source);
    };

    public Integer getReadCount(Integer chatId) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("chatId", chatId);
        String sql = "SELECT COUNT(*) as cnt FROM read_chat WHERE chat_id = :chatId";
        return jdbcTemplate.queryForObject(sql, source, Integer.class);
    }

    public Map<Integer, Long> getReadCountByIds(Collection<Integer> chatIds) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("chatIds", chatIds);
        String sql = "SELECT * FROM read_chat WHERE chat_id in (:chatIds)";
        List<ReadChat> readChats = jdbcTemplate.query(sql, source, rowMapper);
        return readChats.stream().collect(Collectors.groupingBy(ReadChat::getChatId, Collectors.counting()));
    }

}
