package com.supportticketmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import com.supportticketmanagement.repository.CommentRepository;
import com.supportticketmanagement.repository.TicketRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "DB_URL=jdbc:postgresql://postgresql.invalid:5432/configuration_check",
            "DB_USERNAME=configuration-check",
                "DB_PASSWORD=",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
        })
class PostgreSqlConfigurationTests {

    private final DataSource dataSource;

    @MockitoBean
    private TicketRepository ticketRepository;

    @MockitoBean
    private CommentRepository commentRepository;

    @Autowired
    PostgreSqlConfigurationTests(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    void externalPropertiesConfigurePostgreSqlDataSource() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);

        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        assertThat(hikariDataSource.getJdbcUrl())
                .isEqualTo("jdbc:postgresql://postgresql.invalid:5432/configuration_check");
        assertThat(hikariDataSource.getUsername()).isEqualTo("configuration-check");
        assertThat(hikariDataSource.getDriverClassName()).isEqualTo("org.postgresql.Driver");
    }
}
