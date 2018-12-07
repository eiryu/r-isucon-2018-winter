package jp.co.recruit.rine.repository;

import jp.co.recruit.rine.model.BelongsUserGroup;
import jp.co.recruit.rine.model.Group;
import jp.co.recruit.rine.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BelongsUserGroupRepository {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    RowMapper<BelongsUserGroup> rowMapper = (rs, i) -> {
        BelongsUserGroup belongsUserGroup = new BelongsUserGroup();
        belongsUserGroup.setGroupId(rs.getInt("group_id"));
        belongsUserGroup.setUsername(rs.getString("username"));
        return belongsUserGroup;
    };

    public List<BelongsUserGroup> getBelongsUserGroups(User user) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("username", user.getUsername());
        String sql = "SELECT * FROM belongs_user_group WHERE username = :username ORDER BY group_id";
        return jdbcTemplate.query(sql, source, rowMapper);
    }

    public Integer countUsers(Group group) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("groupId", group.getId());
        String sql = "SELECT COUNT(*) as cnt FROM belongs_user_group WHERE group_id = :groupId";
        return jdbcTemplate.queryForObject(sql, source, Integer.class);
    }

    public boolean isBelonging(Group group, User user) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("groupId", group.getId())
                .addValue("username", user.getUsername());
        String sql = "SELECT * FROM belongs_user_group WHERE group_id = :groupId AND username = :username";
        return !jdbcTemplate.queryForList(sql, source).isEmpty();
    }

}
