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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public Map<Integer, Long> countUsersByGroups(Collection<BelongsUserGroup> groups) {
        if (groups.isEmpty()) {
            return new HashMap<>();
        }
        SqlParameterSource source = new MapSqlParameterSource().addValue("groupIds", groups.stream().map(BelongsUserGroup::getGroupId).collect(Collectors.toList()));
        String sql = "SELECT * FROM belongs_user_group WHERE group_id in (:groupIds)";
        List<BelongsUserGroup> belongsUserGroups = jdbcTemplate.query(sql, source, rowMapper);
        return belongsUserGroups.stream().collect(Collectors.groupingBy(BelongsUserGroup::getGroupId, Collectors.counting()));
    }

    public void a() {
        String sql = "SELECT *,  as cnt FROM group WHERE group_id in :groupIds";

    }

    public boolean isBelonging(Group group, User user) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("groupId", group.getId())
                .addValue("username", user.getUsername());
        String sql = "SELECT * FROM belongs_user_group WHERE group_id = :groupId AND username = :username";
        return !jdbcTemplate.queryForList(sql, source).isEmpty();
    }

}
