package com.neobank.module.repository;

import com.neobank.module.dto.CardCaseSummary;
import com.neobank.module.model.BureauStatus;
import com.neobank.module.model.CardOutcome;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Read-only UC-01 queries over the durable {@code card_record} table. */
@Repository
public class CardSearchRepository {

    private static final String SELECT_COLUMNS = """
            SELECT application_id, outcome, pan_last4, bureau_status, issued_at
              FROM card_record
            """;

    private static final RowMapper<CardCaseSummary> ROW_MAPPER = (result, rowNumber) -> {
        Timestamp issuedAt = result.getTimestamp("issued_at");
        String bureauStatus = result.getString("bureau_status");
        return new CardCaseSummary(
                result.getString("application_id"),
                CardOutcome.valueOf(result.getString("outcome")),
                result.getString("pan_last4"),
                bureauStatus == null ? null : BureauStatus.valueOf(bureauStatus),
                issuedAt == null ? null : issuedAt.toInstant());
    };

    private final JdbcTemplate jdbc;

    public CardSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CardCaseSummary> findByApplicationId(String applicationId) {
        List<CardCaseSummary> rows = jdbc.query(
                SELECT_COLUMNS + """
                 WHERE application_id = ?
                 ORDER BY issued_at DESC, application_id ASC
                 LIMIT 1
                """,
                ROW_MAPPER,
                applicationId);
        return rows.stream().findFirst();
    }

    public List<CardCaseSummary> findByApplicationIds(List<String> applicationIds, int limit) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(
                ", ", Collections.nCopies(applicationIds.size(), "?"));
        String sql = SELECT_COLUMNS
                + " WHERE application_id IN (" + placeholders + ")"
                + " ORDER BY issued_at DESC, application_id ASC"
                + " LIMIT ?";

        Object[] parameters = new Object[applicationIds.size() + 1];
        for (int index = 0; index < applicationIds.size(); index++) {
            parameters[index] = applicationIds.get(index);
        }
        parameters[parameters.length - 1] = limit;

        return jdbc.query(sql, ROW_MAPPER, parameters);
    }
}
