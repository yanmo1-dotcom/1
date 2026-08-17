package com.webchat.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Repository
public class UserJdbcRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public UserJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<User> findAll() {
        return jdbc.query("SELECT * FROM users ORDER BY id", (rs, row) -> {
            User u = new User();
            u.setId(rs.getLong("id")); u.setUsername(rs.getString("username"));
            u.setPassword(rs.getString("password")); u.setNickname(rs.getString("nickname"));
            u.setFriends(readList(rs.getString("friends_json"), new TypeReference<List<Long>>() {}));
            u.setGroups(readList(rs.getString("groups_json"), new TypeReference<List<String>>() {}));
            u.setRegisteredAt(rs.getLong("registered_at")); u.setLastLoginAt(rs.getLong("last_login_at"));
            u.setRankPoints(rs.getInt("rank_points")); u.setWins(rs.getInt("wins"));
            u.setLosses(rs.getInt("losses")); u.setDraws(rs.getInt("draws"));
            u.setCurrentStreak(rs.getInt("current_streak")); u.setStatus(rs.getString("status"));
            return u;
        });
    }

    @Transactional
    public void saveAll(List<User> users) { for (User user : users) save(user); }

    public void save(User u) {
        int changed = jdbc.update("UPDATE users SET username=?,password=?,nickname=?,friends_json=?,groups_json=?,registered_at=?,last_login_at=?,rank_points=?,wins=?,losses=?,draws=?,current_streak=?,status=? WHERE id=?",
                u.getUsername(), u.getPassword(), u.getNickname(), write(u.getFriends()), write(u.getGroups()),
                u.getRegisteredAt(), u.getLastLoginAt(), u.getRankPoints(), u.getWins(), u.getLosses(),
                u.getDraws(), u.getCurrentStreak(), u.getStatus(), u.getId());
        if (changed == 0) jdbc.update("INSERT INTO users(id,username,password,nickname,friends_json,groups_json,registered_at,last_login_at,rank_points,wins,losses,draws,current_streak,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                u.getId(), u.getUsername(), u.getPassword(), u.getNickname(), write(u.getFriends()), write(u.getGroups()),
                u.getRegisteredAt(), u.getLastLoginAt(), u.getRankPoints(), u.getWins(), u.getLosses(),
                u.getDraws(), u.getCurrentStreak(), u.getStatus());
    }

    private String write(Object value) { try { return json.writeValueAsString(value == null ? List.of() : value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private <T> T readList(String value, TypeReference<T> type) {
        try { return value == null || value.isBlank() ? json.readValue("[]", type) : json.readValue(value, type); }
        catch (Exception e) { try { return json.readValue("[]", type); } catch (Exception impossible) { throw new IllegalStateException(impossible); } }
    }
}
