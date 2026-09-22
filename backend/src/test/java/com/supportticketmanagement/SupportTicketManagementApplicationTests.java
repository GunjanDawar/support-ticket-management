package com.supportticketmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SupportTicketManagementApplicationTests {

    private final DataSource dataSource;

    @Autowired
    SupportTicketManagementApplicationTests(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    void contextLoadsWithH2TestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        }
    }
}
