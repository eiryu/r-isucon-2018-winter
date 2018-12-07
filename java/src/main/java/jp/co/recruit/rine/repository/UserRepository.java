package jp.co.recruit.rine.repository;

import jp.co.recruit.rine.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    RowMapper<User> rowMapper = (rs, i) -> {
        User user = new User();
        user.setUsername(rs.getString("username"));
        user.setSalt(rs.getString("salt"));
        user.setHash(rs.getString("hash"));
        user.setIcon(rs.getString("icon"));
        user.setFirstname(rs.getString("firstname"));
        user.setLastname(rs.getString("lastname"));
        return user;
    };

    public User findByUsername(String username) {
        SqlParameterSource source = new MapSqlParameterSource().addValue("username", username);
        String sql = "select * from user WHERE username = :username";
        try {
            return jdbcTemplate.queryForObject(sql, source, rowMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int createUser(User user) {
        SqlParameterSource source = new MapSqlParameterSource()
                .addValue("username", user.getUsername())
                .addValue("salt", user.getSalt())
                .addValue("hash", user.getHash())
                .addValue("lastname", user.getLastname())
                .addValue("firstname", user.getFirstname())
                .addValue("icon", user.getIcon());
        String sql = "INSERT INTO user (username, salt, hash, lastname, firstname, icon) " +
                "VALUES (:username, :salt, :hash, :lastname, :firstname, :icon)";
        return jdbcTemplate.update(sql, source);
    }
}
