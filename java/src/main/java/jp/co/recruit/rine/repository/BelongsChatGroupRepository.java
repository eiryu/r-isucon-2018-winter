package jp.co.recruit.rine.repository;

import jp.co.recruit.rine.model.BelongsChatGroup;
import jp.co.recruit.rine.model.Group;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BelongsChatGroupRepository {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    RowMapper<BelongsChatGroup> rowMapper = (rs, i) -> {
        BelongsChatGroup belongsChatGroup = new BelongsChatGroup();
        belongsChatGroup.setChatId(rs.getInt("chat_id"));
        belongsChatGroup.setGroupId(rs.getInt("group_id"));
        return belongsChatGroup;
    };

    public Integer findById(Group group, Integer chatId) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("groupId", group.getId())
                .addValue("chatId", chatId);
        String sql = "SELECT chat_id FROM belongs_chat_group WHERE group_id = :groupId AND chat_id = :chatId";
        return jdbcTemplate.queryForObject(sql, source, Integer.class);
    }

    public Integer countChats(Group group) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("groupId", group.getId());
        String sql = "SELECT COUNT(*) as cnt FROM belongs_chat_group WHERE group_id = :groupId";
        return jdbcTemplate.queryForObject(sql, source, Integer.class);
    }

    public List<Integer> getChatIdList(Group group) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("groupId", group.getId());
        String sql = "SELECT chat_id FROM belongs_chat_group WHERE group_id = :groupId ORDER BY chat_id DESC LIMIT 100";
        return jdbcTemplate.queryForList(sql, source, Integer.class);
    }

    public int insertBelongsChatGroup(Integer chatId, Group group) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("chatId", chatId)
                .addValue("groupId", group.getId());
        String sql = "INSERT INTO belongs_chat_group (chat_id, group_id) VALUES (:chatId, :groupId)";
        return jdbcTemplate.update(sql, source);
    }
}
