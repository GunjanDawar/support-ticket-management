package com.supportticketmanagement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceModelTests {

    private final EntityManager entityManager;
    private final DataSource dataSource;

    @Autowired
    PersistenceModelTests(EntityManager entityManager, DataSource dataSource) {
        this.entityManager = entityManager;
        this.dataSource = dataSource;
    }

    @Test
    void mapsExactlyTicketAndCommentAsEntities() {
        Set<Class<?>> entityTypes = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .collect(Collectors.toSet());

        assertThat(entityTypes).containsExactlyInAnyOrder(Ticket.class, Comment.class);
    }

    @Test
    void persistsTicketWithGeneratedLongIdNullableAssigneeAndTextEnums() {
        Ticket ticket = new Ticket("Title", "Description", Priority.CRITICAL, null);

        entityManager.persist(ticket);
        entityManager.flush();
        entityManager.clear();

        Ticket persisted = entityManager.find(Ticket.class, ticket.getId());
        assertThat(ticket.getId()).isInstanceOf(Long.class);
        assertThat(persisted.getTitle()).isEqualTo("Title");
        assertThat(persisted.getDescription()).isEqualTo("Description");
        assertThat(persisted.getPriority()).isEqualTo(Priority.CRITICAL);
        assertThat(persisted.getAssignee()).isNull();
        assertThat(persisted.getStatus()).isEqualTo(TicketStatus.OPEN);

        Object[] storedEnums = (Object[]) entityManager
                .createNativeQuery("select priority, status from tickets where id = :id")
                .setParameter("id", ticket.getId())
                .getSingleResult();
        assertThat(storedEnums).containsExactly("CRITICAL", "OPEN");
    }

    @Test
    void persistsCommentWithRequiredTicketAndInstant() {
        Ticket ticket = new Ticket("Title", "Description", Priority.LOW, "Support Team");
        entityManager.persist(ticket);

        Instant timestamp = Instant.parse("2026-09-22T17:00:00Z");
        Comment comment = new Comment(ticket, "Comment body", timestamp);
        entityManager.persist(comment);
        entityManager.flush();
        entityManager.clear();

        Comment persisted = entityManager.find(Comment.class, comment.getId());
        assertThat(comment.getId()).isInstanceOf(Long.class);
        assertThat(persisted.getTicket().getId()).isEqualTo(ticket.getId());
        assertThat(persisted.getBody()).isEqualTo("Comment body");
        assertThat(persisted.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void createsOnlyApprovedTablesIndexesAndForeignKey() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();

            assertThat(tableNames(metadata)).containsExactlyInAnyOrder("TICKETS", "COMMENTS");
            assertThat(indexNames(metadata, "TICKETS")).contains("IDX_TICKETS_STATUS");
            assertThat(indexNames(metadata, "COMMENTS")).contains("IDX_COMMENTS_TICKET_ID");
            assertThat(importedForeignKeys(metadata, "COMMENTS")).contains("FK_COMMENTS_TICKET");
        }
    }

    private Set<String> tableNames(DatabaseMetaData metadata) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet tables = metadata.getTables(null, "PUBLIC", null, new String[] {"TABLE"})) {
            while (tables.next()) {
                names.add(tables.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    private Set<String> indexNames(DatabaseMetaData metadata, String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet indexes = metadata.getIndexInfo(null, "PUBLIC", table, false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private Set<String> importedForeignKeys(DatabaseMetaData metadata, String table)
            throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet foreignKeys = metadata.getImportedKeys(null, "PUBLIC", table)) {
            while (foreignKeys.next()) {
                names.add(foreignKeys.getString("FK_NAME"));
            }
        }
        return names;
    }
}
