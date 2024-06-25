/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.oceanbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import io.airbyte.cdk.db.Database;
import io.airbyte.cdk.db.factory.DSLContextFactory;
import io.airbyte.cdk.db.jdbc.JdbcUtils;
import io.airbyte.cdk.integrations.base.JavaBaseConstants;
import io.airbyte.cdk.integrations.destination.StandardNameTransformer;
import io.airbyte.cdk.integrations.util.HostPortResolver;
import io.airbyte.commons.json.Jsons;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.Disabled;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
@Disabled
public class SslOceanBaseDestinationAcceptanceTest extends OceanBaseDestinationAcceptanceTest {

  private DSLContext dslContext;
  private final StandardNameTransformer namingResolver = new OceanBaseNameTransformer();

  @Override
  protected JsonNode getConfig() {
    return Jsons.jsonNode(ImmutableMap.builder()
        .put(JdbcUtils.HOST_KEY, HostPortResolver.resolveHost(db))
        .put(JdbcUtils.USERNAME_KEY, db.getUsername())
        .put(JdbcUtils.PASSWORD_KEY, db.getPassword())
        .put(JdbcUtils.DATABASE_KEY, db.getDatabaseName())
        .put(JdbcUtils.SCHEMA_KEY, db.getDatabaseName())
        .put(JdbcUtils.PORT_KEY, HostPortResolver.resolvePort(db))
        .put(JdbcUtils.SSL_KEY, true)
        .build());
  }

  @Override
  protected JsonNode getFailCheckConfig() {
    return Jsons.jsonNode(ImmutableMap.builder()
        .put(JdbcUtils.HOST_KEY, HostPortResolver.resolveHost(db))
        .put(JdbcUtils.USERNAME_KEY, db.getUsername())
        .put(JdbcUtils.PASSWORD_KEY, "wrong password")
        .put(JdbcUtils.DATABASE_KEY, db.getDatabaseName())
        .put(JdbcUtils.SCHEMA_KEY, db.getDatabaseName())
        .put(JdbcUtils.PORT_KEY, HostPortResolver.resolvePort(db))
        .put(JdbcUtils.SSL_KEY, false)
        .build());
  }

  @Override
  protected List<JsonNode> retrieveRecords(final TestDestinationEnv testEnv,
                                           final String streamName,
                                           final String namespace,
                                           final JsonNode streamSchema)
      throws Exception {
    return retrieveRecordsFromTable(namingResolver.getRawTableName(streamName), namespace)
        .stream()
        .map(r -> r.get(JavaBaseConstants.COLUMN_NAME_DATA))
        .collect(Collectors.toList());
  }

  @Override
  protected List<JsonNode> retrieveNormalizedRecords(final TestDestinationEnv testEnv, final String streamName, final String namespace)
      throws Exception {
    final String tableName = namingResolver.getIdentifier(streamName);
    final String schema = namingResolver.getIdentifier(namespace);
    return retrieveRecordsFromTable(tableName, schema);
  }

  @Override
  protected void setup(final TestDestinationEnv testEnv, final HashSet<String> TEST_SCHEMAS) {
    super.setup(testEnv, TEST_SCHEMAS);

    dslContext = DSLContextFactory.create(
        db.getUsername(),
        db.getPassword(),
        db.getDriverClassName(),
        String.format("jdbc:oceanbase://%s:%s/%s?useSSL=true&requireSSL=true&verifyServerCertificate=false",
            db.getHost(),
            db.getFirstMappedPort(),
            db.getDatabaseName()),
        SQLDialect.DEFAULT);
  }

  @Override
  protected void tearDown(final TestDestinationEnv testEnv) {
    db.stop();
    db.close();
  }

  private List<JsonNode> retrieveRecordsFromTable(final String tableName, final String schemaName) throws SQLException {
    return new Database(dslContext).query(
        ctx -> ctx
            .fetch(String.format("SELECT * FROM %s.%s ORDER BY %s ASC;", schemaName, tableName,
                JavaBaseConstants.COLUMN_NAME_EMITTED_AT))
            .stream()
            .map(this::getJsonFromRecord)
            .collect(Collectors.toList()));
  }

  private void setLocalInFileToTrue() {
    executeQuery("set global local_infile=true");
  }

  private void revokeAllPermissions() {
    executeQuery("REVOKE ALL PRIVILEGES, GRANT OPTION FROM " + db.getUsername() + "@'%';");
  }

  private void grantCorrectPermissions() {
    executeQuery("GRANT ALTER, CREATE, INSERT, SELECT, DROP ON *.* TO " + db.getUsername() + "@'%';");
  }

  private void executeQuery(final String query) {
    final DSLContext dslContext = DSLContextFactory.create(
        "root",
        "test",
        db.getDriverClassName(),
        String.format("jdbc:oceanbase://%s:%s/%s?useSSL=true&requireSSL=true&verifyServerCertificate=false",
            db.getHost(),
            db.getFirstMappedPort(),
            db.getDatabaseName()),
        SQLDialect.DEFAULT);
    try {
      new Database(dslContext).query(ctx -> ctx.execute(query));
    } catch (final SQLException e) {
      throw new RuntimeException(e);
    }
  }

}
