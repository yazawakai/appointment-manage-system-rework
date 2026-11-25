package com.example.appointment.migration;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One-time, idempotent SQLite schema migration.
 * <p>
 * The very first version of {@code Appointment.slot} used a {@code @OneToOne} mapping, which
 * created a {@code UNIQUE} constraint on {@code appointment.slot_id}. That constraint prevents
 * re-booking a slot after it has been cancelled (the CANCELED appointment keeps the row).
 * Hibernate's {@code ddl-auto=update} adds but never drops constraints, so an existing database
 * would keep the stale constraint. This component rebuilds the {@code appointment} table without
 * the constraint while preserving the rows.
 * <p>
 * The migration only runs for SQLite and is a no-op on fresh databases and on other databases.
 */
@Component
public class SqliteSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(SqliteSchemaMigration.class);

    private static final String NEW_APPOINTMENT_DDL =
            "CREATE TABLE appointment (" +
                    "id integer, " +
                    "created_at timestamp not null, " +
                    "updated_at timestamp not null, " +
                    "booked_at timestamp, " +
                    "cancel_reason varchar(255), " +
                    "canceled_at timestamp, " +
                    "completed_at timestamp, " +
                    "confirmed_at timestamp, " +
                    "status varchar(20) not null check (status in ('BOOKED','CONFIRMED','COMPLETED','CANCELED')), " +
                    "version bigint not null, " +
                    "customer_id bigint not null, " +
                    "slot_id bigint not null, " +
                    "primary key (id))";

    private final DataSource dataSource;

    /**
     * The {@link EntityManagerFactory} parameter is intentionally injected (but not stored) to
     * guarantee this bean is created only after Hibernate has completed schema generation.
     */
    public SqliteSchemaMigration(DataSource dataSource, EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrate() {
        try (Connection conn = dataSource.getConnection()) {
            if (!isSqlite(conn)) {
                return;
            }
            String tableSql = appointmentTableSql(conn);
            if (tableSql == null || !hasStaleSlotUnique(tableSql)) {
                return;
            }
            rebuildAppointmentTable(conn);
            log.warn("Rebuilt 'appointment' table: removed stale UNIQUE(slot_id) constraint from the previous @OneToOne mapping");
        } catch (SQLException e) {
            throw new IllegalStateException("SQLite schema migration failed", e);
        }
    }

    private boolean isSqlite(Connection conn) throws SQLException {
        String url = conn.getMetaData().getURL();
        return url != null && url.startsWith("jdbc:sqlite");
    }

    private String appointmentTableSql(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "select sql from sqlite_master where type='table' and name='appointment'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private boolean hasStaleSlotUnique(String tableSql) {
        String normalized = tableSql.toLowerCase().replaceAll("\\s+", " ");
        return normalized.matches("(?s).*slot_id\\s+[a-z0-9()]+\\s+not\\s+null\\s+unique.*");
    }

    private void rebuildAppointmentTable(Connection conn) throws SQLException {
        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE appointment RENAME TO appointment_legacy");
            st.execute(NEW_APPOINTMENT_DDL);
            st.execute("INSERT INTO appointment (id, created_at, updated_at, booked_at, cancel_reason, " +
                    "canceled_at, completed_at, confirmed_at, status, version, customer_id, slot_id) " +
                    "SELECT id, created_at, updated_at, booked_at, cancel_reason, canceled_at, completed_at, " +
                    "confirmed_at, status, version, customer_id, slot_id FROM appointment_legacy");
            st.execute("DROP TABLE appointment_legacy");
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }
}
