package com.webchat.repository;

import com.webchat.model.MatchRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MatchJdbcRepository {
    private final JdbcTemplate jdbc;
    public MatchJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<MatchRecord> findAll() {
        return jdbc.query("SELECT * FROM matches ORDER BY finished_at DESC", (rs, row) -> {
            MatchRecord m = new MatchRecord();
            m.setId(rs.getLong("id")); m.setGameType(rs.getString("game_type"));
            m.setPlayerAId(nullableLong(rs, "player_a_id")); m.setPlayerBId(nullableLong(rs, "player_b_id"));
            m.setWinnerId(nullableLong(rs, "winner_id")); m.setResult(rs.getString("result"));
            m.setRankDeltaA(rs.getInt("rank_delta_a")); m.setRankDeltaB(rs.getInt("rank_delta_b"));
            m.setBoardLog(rs.getString("board_log")); m.setCreatedAt(rs.getLong("created_at"));
            m.setFinishedAt(rs.getLong("finished_at")); return m;
        });
    }

    public void save(MatchRecord m) {
        int changed = jdbc.update("UPDATE matches SET game_type=?,player_a_id=?,player_b_id=?,winner_id=?,result=?,rank_delta_a=?,rank_delta_b=?,board_log=?,created_at=?,finished_at=? WHERE id=?",
                m.getGameType(),m.getPlayerAId(),m.getPlayerBId(),m.getWinnerId(),m.getResult(),m.getRankDeltaA(),m.getRankDeltaB(),m.getBoardLog(),m.getCreatedAt(),m.getFinishedAt(),m.getId());
        if (changed == 0) jdbc.update("INSERT INTO matches(id,game_type,player_a_id,player_b_id,winner_id,result,rank_delta_a,rank_delta_b,board_log,created_at,finished_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                m.getId(),m.getGameType(),m.getPlayerAId(),m.getPlayerBId(),m.getWinnerId(),m.getResult(),m.getRankDeltaA(),m.getRankDeltaB(),m.getBoardLog(),m.getCreatedAt(),m.getFinishedAt());
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException { long v=rs.getLong(column); return rs.wasNull()?null:v; }
}
