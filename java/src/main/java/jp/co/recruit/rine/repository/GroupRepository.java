package jp.co.recruit.rine.repository;

import jp.co.recruit.rine.model.BelongsUserGroup;
import jp.co.recruit.rine.model.Group;
import jp.co.recruit.rine.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class GroupRepository {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    BelongsUserGroupRepository belongsUserGroupRepository;
    @Autowired
    BelongsChatGroupRepository belongsChatGroupRepository;

    RowMapper<Group> rowMapper = (rs, i) -> {
        Group group = new Group();
        group.setId(rs.getInt("id"));
        group.setName(rs.getString("name"));
        group.setOwner(rs.getString("owner"));
        return group;
    };

    public Group findById(Integer groupId) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("groupId", groupId);
        String sql = "SELECT * FROM `groups` WHERE id = :groupId";
        try {
            return jdbcTemplate.queryForObject(sql, source, rowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Group> findByIds(Collection<Integer> groupIds) {
        if (groupIds.isEmpty()) {
            return Collections.emptyList();
        }
        SqlParameterSource source = new MapSqlParameterSource().addValue("groupIds", groupIds);
        String sql = "SELECT * FROM `groups` WHERE id in (:groupIds)";
        return jdbcTemplate.query(sql, source, rowMapper);
    }

    public Group findByNameAndOwner(String name, String owner) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("owner", owner);
        String sql = "SELECT * FROM `groups` WHERE name = :name and owner = :owner";
        try {
            return jdbcTemplate.queryForObject(sql, source, rowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Integer createOrUpdateGroup(User user, String groupName) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("owner", user.getUsername())
                .addValue("name", groupName);
        String sql = "INSERT INTO `groups` (name, owner) VALUES (:name, :owner)" +
                " ON DUPLICATE KEY UPDATE name = :name, owner = :owner";
        jdbcTemplate.update(sql, source);
        String sql2 = "SELECT id FROM `groups` WHERE name = :name AND owner = :owner";
        return jdbcTemplate.queryForObject(sql2, source, Integer.class);
    }

    public void addUserToGroup(Integer groupId, User user) {
        SqlParameterSource source;
        String sql = "INSERT INTO belongs_user_group (group_id, username) VALUES (:groupId, :username) " +
                "ON DUPLICATE KEY UPDATE group_id = :groupId, username = :username";
        source = new MapSqlParameterSource()
                .addValue("groupId", groupId)
                .addValue("username", user.getUsername());
            jdbcTemplate.update(sql, source);
    }

    public List<Group> getGroupsByUser(User user) {
        // ユーザーが紐付いているグループの人数を返す

        // ユーザーの所属するグループを取得
        List<BelongsUserGroup> userGroups = belongsUserGroupRepository.getBelongsUserGroups(user);
        Map<Integer, Long> belongsUserGroupLongMap = belongsUserGroupRepository.countUsersByGroups(userGroups);
        Map<Integer, Long> belongsChatGroupLongMap = belongsChatGroupRepository.countChatsByGroups(userGroups);

        return findByIds(userGroups.stream().map(BelongsUserGroup::getGroupId).collect(Collectors.toList())).stream()
                .map(g -> {
                    g.setUserCount(belongsUserGroupLongMap.computeIfAbsent(g.getId(), (key) -> 0L));
                    g.setChatCount(belongsChatGroupLongMap.computeIfAbsent(g.getId(), (key) -> 0L));
                    return g;
                })
                .collect(Collectors.toList());
    }
}
