package com.example.feat1.DDD.realdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Tag("real-db")
@EnabledIfEnvironmentVariable(named = "RUN_REAL_DB_TESTS", matches = "true")
class RealDatabaseSeedDataIntegrationTest {
  private static final String DEFAULT_URL =
      "jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

  @Test
  void demoAdminCanAuthenticateFromRealSeedData() throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                SELECT u.name,
                       u.email_verified,
                       c.auth_provider,
                       c.provider_user_id,
                       c.password_hash,
                       GROUP_CONCAT(r.name ORDER BY r.name) AS roles
                FROM users u
                JOIN credentials c ON c.user_id = u.id
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.email = ?
                GROUP BY u.id, u.name, u.email_verified, c.auth_provider, c.provider_user_id, c.password_hash
                """)) {
      statement.setString(1, "admin@feat1.local");

      try (ResultSet resultSet = statement.executeQuery()) {
        assertThat(resultSet.next()).as("admin@feat1.local exists in the real DB").isTrue();
        assertThat(resultSet.getString("name")).isEqualTo("Admin Demo");
        assertThat(resultSet.getBoolean("email_verified")).isTrue();
        assertThat(resultSet.getString("auth_provider")).isEqualTo("LOCAL");
        assertThat(resultSet.getString("provider_user_id")).isEqualTo("admin@feat1.local");
        assertThat(resultSet.getString("roles")).isEqualTo("ADMIN");
        assertThat(
                new BCryptPasswordEncoder()
                    .matches("admin123", resultSet.getString("password_hash")))
            .as("seeded admin password matches the documented local credential")
            .isTrue();
        assertThat(resultSet.next()).isFalse();
      }
    }
  }

  @Test
  void adminManagementModulesHaveRealSeedRows() throws Exception {
    try (Connection connection = connection()) {
      assertCountAtLeast(connection, "menu_categories", 2);
      assertCountAtLeast(connection, "dishes", 2);
      assertCountAtLeast(connection, "topping_options", 2);
      assertCountAtLeast(connection, "inventory_ingredients", 3);
      assertCountAtLeast(connection, "inventory_stock_balances", 3);
      assertCountAtLeast(connection, "orders", 1);
      assertCountAtLeast(connection, "payments", 1);
      assertCountAtLeast(connection, "kitchen_tickets", 1);
      assertCountAtLeast(connection, "kitchen_ticket_items", 1);

      assertThat(
              queryBigDecimal(
                  connection, "SELECT base_price FROM dishes WHERE name = 'Pho Bo Demo'"))
          .isEqualByComparingTo("65000.00");
      assertThat(
              queryBigDecimal(
                  connection,
                  "SELECT quantity_on_hand FROM inventory_stock_balances b JOIN inventory_ingredients i ON i.id = b.ingredient_id WHERE i.name = 'Demo Beef'"))
          .isEqualByComparingTo("7000.000000");
      assertThat(
              queryString(
                  connection,
                  "SELECT state FROM table_occupancy o JOIN dining_tables t ON t.id = o.table_id WHERE t.code = 'A01'"))
          .isEqualTo("OCCUPIED");
    }
  }

  @Test
  void demoOrderPaymentAndKitchenRowsAreLinked() throws Exception {
    try (Connection connection = connection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                SELECT BIN_TO_UUID(o.id) AS order_id,
                       o.status AS order_status,
                       o.total,
                       p.method AS payment_method,
                       p.amount AS payment_amount,
                       kt.created_at AS ticket_created_at,
                       kti.dish_name AS kitchen_item_dish,
                       kti.status AS kitchen_item_status
                FROM orders o
                JOIN payments p ON p.order_id = o.id
                JOIN kitchen_tickets kt ON kt.order_id = o.id
                JOIN kitchen_ticket_items kti ON kti.ticket_id = kt.id
                WHERE o.id = UUID_TO_BIN(?)
                """)) {
      statement.setString(1, "60000000-0000-0000-0000-000000000001");

      try (ResultSet resultSet = statement.executeQuery()) {
        assertThat(resultSet.next())
            .as("demo order is linked through payment and kitchen")
            .isTrue();
        assertThat(resultSet.getString("order_status")).isEqualTo("CONFIRMED");
        assertThat(resultSet.getBigDecimal("total")).isEqualByComparingTo("90000.00");
        assertThat(resultSet.getString("payment_method")).isEqualTo("CASH");
        assertThat(resultSet.getBigDecimal("payment_amount")).isEqualByComparingTo("90000.00");
        assertThat(resultSet.getTimestamp("ticket_created_at")).isNotNull();
        assertThat(resultSet.getString("kitchen_item_dish")).isEqualTo("Pho Bo Demo");
        assertThat(resultSet.getString("kitchen_item_status")).isEqualTo("QUEUED");
        assertThat(resultSet.next()).isFalse();
      }
    }
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        envOrDefault("REAL_DB_URL", DEFAULT_URL),
        envOrDefault("REAL_DB_USERNAME", "root"),
        envOrDefault("REAL_DB_PASSWORD", "123456"));
  }

  private static String envOrDefault(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static void assertCountAtLeast(Connection connection, String table, int minimum)
      throws SQLException {
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT COUNT(*) AS row_count FROM " + table);
        ResultSet resultSet = statement.executeQuery()) {
      assertThat(resultSet.next()).isTrue();
      assertThat(resultSet.getInt("row_count"))
          .as(table + " row count")
          .isGreaterThanOrEqualTo(minimum);
    }
  }

  private static BigDecimal queryBigDecimal(Connection connection, String sql) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      assertThat(resultSet.next()).as(sql).isTrue();
      return resultSet.getBigDecimal(1);
    }
  }

  private static String queryString(Connection connection, String sql) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      assertThat(resultSet.next()).as(sql).isTrue();
      return resultSet.getString(1);
    }
  }
}
