/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.driver.core.querybuilder;

import com.datastax.driver.core.AbstractTableMetadata;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.ColumnMetadata;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.MaterializedViewMetadata;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.TableMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A built SELECT statement. */
public class Select extends BuiltStatement {

  private static final List<Object> COUNT_ALL =
      Collections.<Object>singletonList(new Utils.FCall("count", new Utils.RawString("*")));

  private final String table;
  private final boolean isDistinct;
  private final boolean isJson;
  private final List<Object> columnNames;
  private final Where where;
  private final Options usings;
  private List<Ordering> orderings;
  private List<Object> groupByColumnNames;
  private Object limit;
  private Object perPartitionLimit;
  private boolean allowFiltering;
  private boolean bypassCache;

  Select(
      String keyspace, String table, List<Object> columnNames, boolean isDistinct, boolean isJson) {
    this(keyspace, table, null, null, columnNames, isDistinct, isJson);
  }

  Select(
      AbstractTableMetadata table, List<Object> columnNames, boolean isDistinct, boolean isJson) {
    this(
        Metadata.quoteIfNecessary(table.getKeyspace().getName()),
        Metadata.quoteIfNecessary(table.getName()),
        Arrays.asList(new Object[table.getPartitionKey().size()]),
        table.getPartitionKey(),
        columnNames,
        isDistinct,
        isJson);
  }

  Select(
      String keyspace,
      String table,
      List<Object> routingKeyValues,
      List<ColumnMetadata> partitionKey,
      List<Object> columnNames,
      boolean isDistinct,
      boolean isJson) {
    super(keyspace, partitionKey, routingKeyValues);
    this.table = table;
    this.columnNames = columnNames;
    this.isDistinct = isDistinct;
    this.isJson = isJson;
    this.where = new Where(this);
    this.usings = new Options(this);
  }

  @Override
  StringBuilder buildQueryString(List<Object> variables, CodecRegistry codecRegistry) {
    StringBuilder builder = new StringBuilder();

    builder.append("SELECT ");

    if (isJson) builder.append("JSON ");

    if (isDistinct) builder.append("DISTINCT ");

    if (columnNames == null) {
      builder.append('*');
    } else {
      Utils.joinAndAppendNames(builder, codecRegistry, columnNames);
    }
    builder.append(" FROM ");
    if (keyspace != null) Utils.appendName(keyspace, builder).append('.');
    Utils.appendName(table, builder);

    if (!where.clauses.isEmpty()) {
      builder.append(" WHERE ");
      Utils.joinAndAppend(builder, codecRegistry, " AND ", where.clauses, variables);
    }

    if (groupByColumnNames != null) {
      builder.append(" GROUP BY ");
      Utils.joinAndAppendNames(builder, codecRegistry, groupByColumnNames);
    }

    if (orderings != null) {
      builder.append(" ORDER BY ");
      Utils.joinAndAppend(builder, codecRegistry, ",", orderings, variables);
    }

    if (perPartitionLimit != null) {
      builder.append(" PER PARTITION LIMIT ").append(perPartitionLimit);
    }

    if (limit != null) {
      builder.append(" LIMIT ").append(limit);
    }

    if (allowFiltering) {
      builder.append(" ALLOW FILTERING");
    }

    if (bypassCache) {
      builder.append(" BYPASS CACHE");
    }

    if (!usings.usings.isEmpty()) {
      builder.append(" USING ");
      Utils.joinAndAppend(builder, codecRegistry, " AND ", usings.usings, variables);
    }

    return builder;
  }

  /**
   * Adds a {@code WHERE} clause to this statement.
   *
   * <p>This is a shorter/more readable version for {@code where().and(clause)}.
   *
   * @param clause the clause to add.
   * @return the where clause of this query to which more clause can be added.
   */
  public Where where(Clause clause) {
    return where.and(clause);
  }

  /**
   * Returns a {@code WHERE} statement for this query without adding clause.
   *
   * @return the where clause of this query to which more clause can be added.
   */
  public Where where() {
    return where;
  }

  /**
   * Adds a new options for this SELECT statement.
   *
   * @param using the option to add.
   * @return the options of this SELECT statement.
   */
  public Options using(Using using) {
    return usings.and(using);
  }

  /**
   * Adds an {@code ORDER BY} clause to this statement.
   *
   * @param orderings the orderings to define for this query.
   * @return this statement.
   * @throws IllegalStateException if an {@code ORDER BY} clause has already been provided.
   */
  public Select orderBy(Ordering... orderings) {
    if (this.orderings != null)
      throw new IllegalStateException("An ORDER BY clause has already been provided");

    if (orderings.length == 0)
      throw new IllegalArgumentException(
          "Invalid ORDER BY argument, the orderings must not be empty.");

    this.orderings = Arrays.asList(orderings);
    for (Ordering ordering : orderings) checkForBindMarkers(ordering);
    return this;
  }

  /**
   * Adds a {@code GROUP BY} clause to this statement.
   *
   * <p>Note: support for {@code GROUP BY} clause is only available from Cassandra 3.10 onwards.
   *
   * @param columns the columns to group by.
   * @return this statement.
   * @throws IllegalStateException if a {@code GROUP BY} clause has already been provided.
   */
  public Select groupBy(Object... columns) {
    if (this.groupByColumnNames != null)
      throw new IllegalStateException("A GROUP BY clause has already been provided");

    this.groupByColumnNames = Arrays.asList(columns);
    return this;
  }

  /**
   * Adds a {@code LIMIT} clause to this statement.
   *
   * @param limit the limit to set.
   * @return this statement.
   * @throws IllegalArgumentException if {@code limit <= 0}.
   * @throws IllegalStateException if a {@code LIMIT} clause has already been provided.
   */
  public Select limit(int limit) {
    if (limit <= 0)
      throw new IllegalArgumentException("Invalid LIMIT value, must be strictly positive");

    if (this.limit != null)
      throw new IllegalStateException("A LIMIT value has already been provided");

    this.limit = limit;
    setDirty();
    return this;
  }

  /**
   * Adds a prepared {@code LIMIT} clause to this statement.
   *
   * @param marker the marker to use for the limit.
   * @return this statement.
   * @throws IllegalStateException if a {@code LIMIT} clause has already been provided.
   */
  public Select limit(BindMarker marker) {
    if (this.limit != null)
      throw new IllegalStateException("A LIMIT value has already been provided");

    this.limit = marker;
    checkForBindMarkers(marker);
    return this;
  }

  /**
   * Adds a {@code PER PARTITION LIMIT} clause to this statement.
   *
   * <p>Note: support for {@code PER PARTITION LIMIT} clause is only available from Cassandra 3.6
   * onwards.
   *
   * @param perPartitionLimit the limit to set per partition.
   * @return this statement.
   * @throws IllegalArgumentException if {@code perPartitionLimit <= 0}.
   * @throws IllegalStateException if a {@code PER PARTITION LIMIT} clause has already been
   *     provided.
   * @throws IllegalStateException if this statement is a {@code SELECT DISTINCT} statement.
   */
  public Select perPartitionLimit(int perPartitionLimit) {
    if (perPartitionLimit <= 0)
      throw new IllegalArgumentException(
          "Invalid PER PARTITION LIMIT value, must be strictly positive");

    if (this.perPartitionLimit != null)
      throw new IllegalStateException("A PER PARTITION LIMIT value has already been provided");
    if (isDistinct)
      throw new IllegalStateException(
          "PER PARTITION LIMIT is not allowed with SELECT DISTINCT queries");

    this.perPartitionLimit = perPartitionLimit;
    setDirty();
    return this;
  }

  /**
   * Adds a prepared {@code PER PARTITION LIMIT} clause to this statement.
   *
   * <p>Note: support for {@code PER PARTITION LIMIT} clause is only available from Cassandra 3.6
   * onwards.
   *
   * @param marker the marker to use for the limit per partition.
   * @return this statement.
   * @throws IllegalStateException if a {@code PER PARTITION LIMIT} clause has already been
   *     provided.
   * @throws IllegalStateException if this statement is a {@code SELECT DISTINCT} statement.
   */
  public Select perPartitionLimit(BindMarker marker) {
    if (this.perPartitionLimit != null)
      throw new IllegalStateException("A PER PARTITION LIMIT value has already been provided");
    if (isDistinct)
      throw new IllegalStateException(
          "PER PARTITION LIMIT is not allowed with SELECT DISTINCT queries");

    this.perPartitionLimit = marker;
    checkForBindMarkers(marker);
    return this;
  }

  /**
   * Adds an {@code ALLOW FILTERING} directive to this statement.
   *
   * @return this statement.
   */
  public Select allowFiltering() {
    allowFiltering = true;
    return this;
  }

  /**
   * Adds on {@code BYPASS CACHE} clause to this statement.
   *
   * @return this statement.
   */
  public Select bypassCache() {
    bypassCache = true;
    return this;
  }

  /** The {@code WHERE} clause of a {@code SELECT} statement. */
  public static class Where extends BuiltStatement.ForwardingStatement<Select> {

    private final List<Clause> clauses = new ArrayList<Clause>();

    Where(Select statement) {
      super(statement);
    }

    /**
     * Adds the provided clause to this {@code WHERE} clause.
     *
     * @param clause the clause to add.
     * @return this {@code WHERE} clause.
     */
    public Where and(Clause clause) {
      clauses.add(clause);
      statement.maybeAddRoutingKey(clause.name(), clause.firstValue());
      checkForBindMarkers(clause);
      return this;
    }

    /**
     * Adds an option to the SELECT statement this WHERE clause is part of.
     *
     * @param using the using clause to add.
     * @return the options of the SELECT statement this WHERE clause is part of.
     */
    public Options using(Using using) {
      return statement.using(using);
    }

    /**
     * Adds an ORDER BY clause to the {@code SELECT} statement this {@code WHERE} clause if part of.
     *
     * @param orderings the orderings to add.
     * @return the {@code SELECT} statement this {@code WHERE} clause is part of.
     * @throws IllegalStateException if an {@code ORDER BY} clause has already been provided.
     */
    public Select orderBy(Ordering... orderings) {
      return statement.orderBy(orderings);
    }

    /**
     * Adds a {@code GROUP BY} clause to this statement.
     *
     * <p>Note: support for {@code GROUP BY} clause is only available from Cassandra 3.10 onwards.
     *
     * @param columns the columns to group by.
     * @return the {@code SELECT} statement this {@code WHERE} clause is part of.
     * @throws IllegalStateException if a {@code GROUP BY} clause has already been provided.
     */
    public Select groupBy(Object... columns) {
      return statement.groupBy(columns);
    }

    /**
     * Adds a {@code LIMIT} clause to the {@code SELECT} statement this {@code WHERE} clause is part
     * of.
     *
     * @param limit the limit to set.
     * @return the {@code SELECT} statement this {@code WHERE} clause is part of.
     * @throws IllegalArgumentException if {@code limit <= 0}.
     * @throws IllegalStateException if a {@code LIMIT} clause has already been provided.
     */
    public Select limit(int limit) {
      return statement.limit(limit);
    }

    /**
     * Adds a bind marker for the {@code LIMIT} clause to the {@code SELECT} statement this {@code
     * WHERE} clause is part of.
     *
     * @param limit the bind marker to use as limit.
     * @return the {@code SELECT} statement this {@code WHERE} clause is part of.
     * @throws IllegalStateException if a {@code LIMIT} clause has already been provided.
     */
    public Select limit(BindMarker limit) {
      return statement.limit(limit);
    }

    /**
     * Adds a {@code PER PARTITION LIMIT} clause to the {@code SELECT} statement this {@code WHERE}
     * clause is part of.
     *
     * <p>Note: support for {@code PER PARTITION LIMIT} clause is only available from Cassandra 3.6
     * onwards.
     *
     * @param perPartitionLimit the limit to set per partition.
     * @return the {@code SELECT} statement this {@code WHERE} clause is part of.
     * @throws IllegalArgumentException if {@code perPartitionLimit <= 0}.
     * @throws IllegalStateException if a {@code PER PARTITION LIMIT} clause has already been
     *     provided.
     * @throws IllegalStateException if this statement is a {@code SELECT DISTINCT} statement.
     */
    public Select perPartitionLimit(int perPartitionLimit) {
      return statement.perPartitionLimit(perPartitionLimit);
    }

    /**
     * Adds a bind marker for the {@code PER PARTITION LIMIT} clause to the {@code SELECT} statement
     * this {@code WHERE} clause is part of.
     *
     * <p>Note: support for {@code PER PARTITION LIMIT} clause is only available from Cassandra 3.6
     * onwards.
     *
     * @param limit the bind marker to use as limit per partition.
     * @return the {@code SELECT} statement this {@code WHERE} clause is part of.
     * @throws IllegalStateException if a {@code PER PARTITION LIMIT} clause has already been
     *     provided.
     * @throws IllegalStateException if this statement is a {@code SELECT DISTINCT} statement.
     */
    public Select perPartitionLimit(BindMarker limit) {
      return statement.perPartitionLimit(limit);
    }

    /**
     * Adds an {@code ALLOW FILTERING} directive to the {@code SELECT} statement this {@code WHERE}
     * clause is part of.
     *
     * @return the {@code SELECT} statement this {@code WHERE} clause is part of.
     */
    public Select allowFiltering() {
      return statement.allowFiltering();
    }

    /**
     * Adds an {@code BYPASS CACHE} clause to the {@code SELECT} statement this {@code WHERE} clause
     * is part of.
     *
     * @return the {@code SELECT} statement this {@code WHERE} clause is part of.
     */
    public Select bypassCache() {
      return statement.bypassCache();
    }
  }

  /** The options of a SELECT statement. */
  public static class Options extends BuiltStatement.ForwardingStatement<Select> {
    private final List<Using> usings = new ArrayList<Using>();

    Options(Select statement) {
      super(statement);
    }

    /**
     * Adds the provided option.
     *
     * @param using a SELECT option.
     * @return this {@code Options} object.
     */
    public Options and(Using using) {
      usings.add(using);
      checkForBindMarkers(using);
      return this;
    }
  }

  /** An in-construction SELECT statement. */
  public static class Builder {

    List<Object> columnNames;
    boolean isDistinct;
    boolean isJson;

    Builder() {}

    Builder(List<Object> columnNames) {
      this.columnNames = columnNames;
    }

    /**
     * Uses DISTINCT selection.
     *
     * @return this in-build SELECT statement.
     */
    public Builder distinct() {
      this.isDistinct = true;
      return this;
    }

    /**
     * Uses JSON selection.
     *
     * <p>Cassandra 2.2 introduced JSON support to SELECT statements: the {@code JSON} keyword can
     * be used to return each row as a single JSON encoded map.
     *
     * @return this in-build SELECT statement.
     * @see <a href="http://cassandra.apache.org/doc/cql3/CQL-2.2.html#json">JSON Support for
     *     CQL</a>
     * @see <a href="http://www.datastax.com/dev/blog/whats-new-in-cassandra-2-2-json-support">JSON
     *     Support in Cassandra 2.2</a>
     * @see <a href="https://docs.datastax.com/en/cql/3.3/cql/cql_using/useQueryJSON.html">Data
     *     retrieval using JSON</a>
     */
    public Builder json() {
      this.isJson = true;
      return this;
    }

    /**
     * Adds the table to select from.
     *
     * @param table the name of the table to select from.
     * @return a newly built SELECT statement that selects from {@code table}.
     */
    public Select from(String table) {
      return from(null, table);
    }

    /**
     * Adds the table to select from.
     *
     * @param keyspace the name of the keyspace to select from.
     * @param table the name of the table to select from.
     * @return a newly built SELECT statement that selects from {@code keyspace.table}.
     */
    public Select from(String keyspace, String table) {
      return new Select(keyspace, table, columnNames, isDistinct, isJson);
    }

    /**
     * Adds the table to select from.
     *
     * @param table the table to select from.
     * @return a newly built SELECT statement that selects from {@code table}.
     */
    public Select from(TableMetadata table) {
      return new Select(table, columnNames, isDistinct, isJson);
    }

    /**
     * Adds the materialized view to select from.
     *
     * @param view the materialized view to select from.
     * @return a newly built SELECT statement that selects from {@code view}.
     */
    public Select from(MaterializedViewMetadata view) {
      return new Select(view, columnNames, isDistinct, isJson);
    }
  }

  /** An Selection clause for an in-construction SELECT statement. */
  public abstract static class Selection extends Builder {

    /**
     * Uses DISTINCT selection.
     *
     * @return this in-build SELECT statement.
     */
    @Override
    public Selection distinct() {
      this.isDistinct = true;
      return this;
    }

    /**
     * Uses JSON selection.
     *
     * <p>Cassandra 2.2 introduced JSON support to SELECT statements: the {@code JSON} keyword can
     * be used to return each row as a single JSON encoded map.
     *
     * @return this in-build SELECT statement.
     * @see <a href="http://cassandra.apache.org/doc/cql3/CQL-2.2.html#json">JSON Support for
     *     CQL</a>
     * @see <a href="http://www.datastax.com/dev/blog/whats-new-in-cassandra-2-2-json-support">JSON
     *     Support in Cassandra 2.2</a>
     * @see <a href="https://docs.datastax.com/en/cql/3.3/cql/cql_using/useQueryJSON.html">Data
     *     retrieval using JSON</a>
     */
    @Override
    public Selection json() {
      this.isJson = true;
      return this;
    }

    /**
     * Selects all columns (i.e. "SELECT * ...")
     *
     * @return an in-build SELECT statement.
     * @throws IllegalStateException if some columns had already been selected for this builder.
     */
    public abstract Builder all();

    /**
     * Selects the count of all returned rows (i.e. "SELECT count(*) ...").
     *
     * @return an in-build SELECT statement.
     * @throws IllegalStateException if some columns had already been selected for this builder.
     */
    public abstract Builder countAll();

    /**
     * Selects the provided column.
     *
     * @param name the new column name to add.
     * @return this in-build SELECT statement
     */
    public abstract SelectionOrAlias column(String name);

    /**
     * Selects the write time of provided column.
     *
     * <p>This is a shortcut for {@code fcall("writetime", QueryBuilder.column(name))}.
     *
     * @param name the name of the column to select the write time of.
     * @return this in-build SELECT statement
     */
    public abstract SelectionOrAlias writeTime(String name);

    /**
     * Selects the ttl of provided column.
     *
     * <p>This is a shortcut for {@code fcall("ttl", QueryBuilder.column(name))}.
     *
     * @param name the name of the column to select the ttl of.
     * @return this in-build SELECT statement
     */
    public abstract SelectionOrAlias ttl(String name);

    /**
     * Creates a function call.
     *
     * <p>Please note that the parameters are interpreted as values, and so {@code
     * fcall("textToBlob", "foo")} will generate the string {@code "textToBlob('foo')"}. If you want
     * to generate {@code "textToBlob(foo)"}, i.e. if the argument must be interpreted as a column
     * name (in a select clause), you will need to use the {@link QueryBuilder#column} method, and
     * so {@code fcall("textToBlob", QueryBuilder.column(foo)}.
     *
     * @param name the name of the function.
     * @param parameters the parameters for the function call.
     * @return this in-build SELECT statement
     */
    public abstract SelectionOrAlias fcall(String name, Object... parameters);

    /**
     * Creates a cast of an expression to a given CQL type.
     *
     * @param column the expression to cast. It can be a complex expression like a {@link
     *     QueryBuilder#fcall(String, Object...) function call}.
     * @param targetType the target CQL type to cast to. Use static methods such as {@link
     *     DataType#text()}.
     * @return this in-build SELECT statement.
     */
    public SelectionOrAlias cast(Object column, DataType targetType) {
      // This method should be abstract like others here. But adding an abstract method is not
      // binary-compatible,
      // so we add this dummy implementation to make Clirr happy.
      throw new UnsupportedOperationException(
          "Not implemented. This should only happen if you've written your own implementation of Selection");
    }

    /**
     * Selects the provided raw expression.
     *
     * <p>The provided string will be appended to the query as-is, without any form of escaping or
     * quoting.
     *
     * @param rawString the raw expression to add.
     * @return this in-build SELECT statement
     */
    public SelectionOrAlias raw(String rawString) {
      // This method should be abstract like others here. But adding an abstract method is not
      // binary-compatible,
      // so we add this dummy implementation to make Clirr happy.
      throw new UnsupportedOperationException(
          "Not implemented. This should only happen if you've written your own implementation of Selection");
    }

    /**
     * Selects the provided path.
     *
     * <p>All given path {@code segments} will be concatenated together with dots. If any segment
     * contains an identifier that needs quoting, caller code is expected to call {@link
     * QueryBuilder#quote(String)} prior to invoking this method.
     *
     * <p>This method is currently only useful when accessing individual fields of a {@link
     * com.datastax.driver.core.UserType user-defined type} (UDT), which is only possible since
     * CASSANDRA-7423.
     *
     * <p>Note that currently nested UDT fields are not supported and will be rejected by the server
     * as a {@link com.datastax.driver.core.exceptions.SyntaxError syntax error}.
     *
     * @param segments the segments of the path to create.
     * @return this in-build SELECT statement
     * @see <a href="https://issues.apache.org/jira/browse/CASSANDRA-7423">CASSANDRA-7423</a>
     */
    public SelectionOrAlias path(String... segments) {
      // This method should be abstract like others here. But adding an abstract method is not
      // binary-compatible,
      // so we add this dummy implementation to make Clirr happy.
      throw new UnsupportedOperationException(
          "Not implemented. This should only happen if you've written your own implementation of Selection");
    }

    /**
     * Creates a {@code toJson()} function call. This is a shortcut for {@code fcall("toJson",
     * QueryBuilder.column(name))}.
     *
     * <p>Support for JSON functions has been added in Cassandra 2.2. The {@code toJson()} function
     * is similar to {@code SELECT JSON} statements, but applies to a single column value instead of
     * the entire row, and produces a JSON-encoded string representing the normal Cassandra column
     * value.
     *
     * <p>It may only be used in the selection clause of a {@code SELECT} statement.
     *
     * @param column the column to retrieve JSON from.
     * @return the function call.
     * @see <a href="http://cassandra.apache.org/doc/cql3/CQL-2.2.html#json">JSON Support for
     *     CQL</a>
     * @see <a href="http://www.datastax.com/dev/blog/whats-new-in-cassandra-2-2-json-support">JSON
     *     Support in Cassandra 2.2</a>
     */
    public SelectionOrAlias toJson(String column) {
      // This method should be abstract like others here. But adding an abstract method is not
      // binary-compatible,
      // so we add this dummy implementation to make Clirr happy.
      throw new UnsupportedOperationException(
          "Not implemented. This should only happen if you've written your own implementation of Selection");
    }

    /**
     * Creates a {@code count(x)} built-in function call.
     *
     * @return the function call.
     */
    public SelectionOrAlias count(Object column) {
      // This method should be abstract like others here. But adding an abstract method is not
      // binary-compatible,
      // so we add this dummy implementation to make Clirr happy.
      throw new UnsupportedOperationException(
          "Not implemented. This should only happen if you've written your own implementation of Selection");
    }

    /**
     * Creates a {@code max(x)} built-in function call.
     *
     * @return the function call.
     */
    public SelectionOrAlias max(Object column) {
      // This method should be abstract like others here. But adding an abstract method is not
      // binary-compatible,
      // so we add this dummy implementation to make Clirr happy.
      throw new UnsupportedOperationException(
          "Not implemented. This should only happen if you've written your own implementation of Selection");
    }

    /**
     * Creates a {@code min(x)} built-in function call.
     *
     * @return the function call.
     */
    public SelectionOrAlias min(Object column) {
      // This method should be abstract like others here. But adding an abstract method is not
      // binary-compatible,
      // so we add this dummy implementation to make Clirr happy.
      throw new UnsupportedOperationException(
          "Not implemented. This should only happen if you've written your own implementation of Selection");
    }

    /**
     * Creates a {@code sum(x)} built-in function call.
     *
     * @return the function call.
     */
    public SelectionOrAlias sum(Object column) {
      // This method should be abstract like others here. But adding an abstract method is not
      // binary-compatible,
      // so we add this dummy implementation to make Clirr happy.
      throw new UnsupportedOperationException(
          "Not implemented. This should only happen if you've written your own implementation of Selection");
    }

    /**
     * Creates an {@code avg(x)} built-in function call.
     *
     * @return the function call.
     */
    public SelectionOrAlias avg(Object column) {
      // This method should be abstract like others here. But adding an abstract method is not
      // binary-compatible,
      // so we add this dummy implementation to make Clirr happy.
      throw new UnsupportedOperationException(
          "Not implemented. This should only happen if you've written your own implementation of Selection");
    }
  }

  /**
   * An Selection clause for an in-construction SELECT statement.
   *
   * <p>This only differs from {@link Selection} in that you can add an alias for the previously
   * selected item through {@link SelectionOrAlias#as}.
   */
  public static class SelectionOrAlias extends Selection {

    private Object previousSelection;

    /**
     * Adds an alias for the just selected item.
     *
     * @param alias the name of the alias to use.
     * @return this in-build SELECT statement
     */
    public Selection as(String alias) {
      assert previousSelection != null;
      Object a = new Utils.Alias(previousSelection, alias);
      previousSelection = null;
      return addName(a);
    }

    // We don't return SelectionOrAlias on purpose
    private Selection addName(Object name) {
      if (columnNames == null) columnNames = new ArrayList<Object>();

      columnNames.add(name);
      return this;
    }

    private SelectionOrAlias queueName(Object name) {
      if (previousSelection != null) addName(previousSelection);

      previousSelection = name;
      return this;
    }

    @Override
    public Builder all() {
      if (columnNames != null)
        throw new IllegalStateException(
            String.format("Some columns (%s) have already been selected.", columnNames));
      if (previousSelection != null)
        throw new IllegalStateException(
            String.format("Some columns ([%s]) have already been selected.", previousSelection));

      return (Builder) this;
    }

    @Override
    public Builder countAll() {
      if (columnNames != null)
        throw new IllegalStateException(
            String.format("Some columns (%s) have already been selected.", columnNames));
      if (previousSelection != null)
        throw new IllegalStateException(
            String.format("Some columns ([%s]) have already been selected.", previousSelection));

      columnNames = COUNT_ALL;
      return (Builder) this;
    }

    @Override
    public SelectionOrAlias column(String name) {
      return queueName(name);
    }

    @Override
    public SelectionOrAlias writeTime(String name) {
      return queueName(new Utils.FCall("writetime", new Utils.CName(name)));
    }

    @Override
    public SelectionOrAlias ttl(String name) {
      return queueName(new Utils.FCall("ttl", new Utils.CName(name)));
    }

    @Override
    public SelectionOrAlias fcall(String name, Object... parameters) {
      return queueName(new Utils.FCall(name, parameters));
    }

    @Override
    public SelectionOrAlias cast(Object column, DataType targetType) {
      return queueName(QueryBuilder.cast(column, targetType));
    }

    @Override
    public SelectionOrAlias raw(String rawString) {
      return queueName(QueryBuilder.raw(rawString));
    }

    @Override
    public SelectionOrAlias path(String... segments) {
      return queueName(QueryBuilder.path(segments));
    }

    @Override
    public SelectionOrAlias toJson(String name) {
      return queueName(QueryBuilder.toJson(name));
    }

    @Override
    public SelectionOrAlias count(Object column) {
      return queueName(QueryBuilder.count(column));
    }

    @Override
    public SelectionOrAlias max(Object column) {
      return queueName(QueryBuilder.max(column));
    }

    @Override
    public SelectionOrAlias min(Object column) {
      return queueName(QueryBuilder.min(column));
    }

    @Override
    public SelectionOrAlias sum(Object column) {
      return queueName(QueryBuilder.sum(column));
    }

    @Override
    public SelectionOrAlias avg(Object column) {
      return queueName(QueryBuilder.avg(column));
    }

    @Override
    public Select from(String keyspace, String table) {
      if (previousSelection != null) addName(previousSelection);
      previousSelection = null;
      return super.from(keyspace, table);
    }

    @Override
    public Select from(TableMetadata table) {
      if (previousSelection != null) addName(previousSelection);
      previousSelection = null;
      return super.from(table);
    }

    @Override
    public Select from(MaterializedViewMetadata view) {
      if (previousSelection != null) {
        addName(previousSelection);
      }
      previousSelection = null;
      return super.from(view);
    }
  }
}
