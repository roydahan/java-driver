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

/*
 * Copyright (C) 2020 ScyllaDB
 *
 * Modified by ScyllaDB
 */
package com.datastax.driver.core;

import com.datastax.driver.core.utils.MoreObjects;
import com.google.common.collect.ImmutableMap;
import java.nio.ByteBuffer;
import java.util.Map;

public class TableOptionsMetadata {

  private static final String COMMENT = "comment";
  private static final String READ_REPAIR_CHANCE = "read_repair_chance";
  private static final String DCLOCAL_READ_REPAIR_CHANCE = "dclocal_read_repair_chance";
  private static final String READ_REPAIR = "read_repair";
  private static final String LOCAL_READ_REPAIR_CHANCE = "local_read_repair_chance";
  private static final String REPLICATE_ON_WRITE = "replicate_on_write";
  private static final String GC_GRACE = "gc_grace_seconds";
  private static final String BF_FP_CHANCE = "bloom_filter_fp_chance";
  private static final String CACHING = "caching";
  private static final String COMPACTION = "compaction";
  private static final String COMPACTION_CLASS = "compaction_strategy_class";
  private static final String COMPACTION_OPTIONS = "compaction_strategy_options";
  private static final String POPULATE_CACHE_ON_FLUSH = "populate_io_cache_on_flush";
  private static final String COMPRESSION = "compression";
  private static final String COMPRESSION_PARAMS = "compression_parameters";
  private static final String MEMTABLE_FLUSH_PERIOD_MS = "memtable_flush_period_in_ms";
  private static final String DEFAULT_TTL = "default_time_to_live";
  private static final String SPECULATIVE_RETRY = "speculative_retry";
  private static final String INDEX_INTERVAL = "index_interval";
  private static final String MIN_INDEX_INTERVAL = "min_index_interval";
  private static final String MAX_INDEX_INTERVAL = "max_index_interval";
  private static final String CRC_CHECK_CHANCE = "crc_check_chance";
  private static final String EXTENSIONS = "extensions";
  private static final String CDC = "cdc";
  private static final String SCYLLA_CDC_EXTENSION = "cdc";
  private static final String SCYLLA_ENCRYPTION_OPTIONS_EXTENSION = "scylla_encryption_options";
  private static final String SCYLLA_TAGS_EXTENSION = "scylla_tags";

  private static final String[] MAP_EXTENSIONS = {
    SCYLLA_CDC_EXTENSION, SCYLLA_ENCRYPTION_OPTIONS_EXTENSION, SCYLLA_TAGS_EXTENSION
  };

  private static final String ADDITIONAL_WRITE_POLICY = "additional_write_policy";

  private static final boolean DEFAULT_REPLICATE_ON_WRITE = true;
  private static final double DEFAULT_BF_FP_CHANCE = 0.01;
  private static final boolean DEFAULT_POPULATE_CACHE_ON_FLUSH = false;
  private static final int DEFAULT_MEMTABLE_FLUSH_PERIOD = 0;
  private static final int DEFAULT_DEFAULT_TTL = 0;
  private static final String DEFAULT_SPECULATIVE_RETRY = "NONE";
  private static final int DEFAULT_INDEX_INTERVAL = 128;
  private static final int DEFAULT_MIN_INDEX_INTERVAL = 128;
  private static final int DEFAULT_MAX_INDEX_INTERVAL = 2048;
  private static final double DEFAULT_CRC_CHECK_CHANCE = 1.0;
  private static final boolean DEFAULT_CDC = false;
  private static final String DEFAULT_READ_REPAIR = "BLOCKING";
  private static final String DEFAULT_ADDITIONAL_WRITE_POLICY = "99p";

  private final boolean isCompactStorage;

  private final String comment;
  private final double readRepairChance;
  private final double localReadRepairChance;
  private final String readRepair;
  private final boolean replicateOnWrite;
  private final int gcGrace;
  private final double bfFpChance;
  private final Map<String, String> caching;
  private final boolean populateCacheOnFlush;
  private final int memtableFlushPeriodMs;
  private final int defaultTTL;
  private final String speculativeRetry;
  private final Integer indexInterval;
  private final Integer minIndexInterval;
  private final Integer maxIndexInterval;
  private final Map<String, String> compaction;
  private final Map<String, String> compression;
  private final Double crcCheckChance;
  private final Map<String, ByteBuffer> extensions;
  private final Map<String, Map<String, String>> mapExtensions;
  private final boolean cdc;
  private final String additionalWritePolicy;

  TableOptionsMetadata(Row row, boolean isCompactStorage, VersionNumber version) {

    boolean is120 = version.getMajor() < 2;
    boolean is200 = version.getMajor() == 2 && version.getMinor() == 0;
    boolean is210 = version.getMajor() == 2 && version.getMinor() >= 1;
    boolean is400OrHigher = version.getMajor() > 3;
    boolean is380OrHigher = is400OrHigher || version.getMajor() == 3 && version.getMinor() >= 8;
    boolean is300OrHigher = version.getMajor() > 2;
    boolean is210OrHigher = is210 || is300OrHigher;

    this.isCompactStorage = isCompactStorage;
    this.comment = isNullOrAbsent(row, COMMENT) ? "" : row.getString(COMMENT);
    this.readRepairChance = row.getDouble(READ_REPAIR_CHANCE);

    if (is400OrHigher) this.readRepair = row.getString(READ_REPAIR);
    else this.readRepair = DEFAULT_READ_REPAIR;

    if (is300OrHigher) this.localReadRepairChance = row.getDouble(DCLOCAL_READ_REPAIR_CHANCE);
    else this.localReadRepairChance = row.getDouble(LOCAL_READ_REPAIR_CHANCE);

    this.replicateOnWrite =
        is210OrHigher || isNullOrAbsent(row, REPLICATE_ON_WRITE)
            ? DEFAULT_REPLICATE_ON_WRITE
            : row.getBool(REPLICATE_ON_WRITE);
    this.gcGrace = row.getInt(GC_GRACE);
    this.bfFpChance =
        isNullOrAbsent(row, BF_FP_CHANCE) ? DEFAULT_BF_FP_CHANCE : row.getDouble(BF_FP_CHANCE);

    this.populateCacheOnFlush =
        isNullOrAbsent(row, POPULATE_CACHE_ON_FLUSH)
            ? DEFAULT_POPULATE_CACHE_ON_FLUSH
            : row.getBool(POPULATE_CACHE_ON_FLUSH);
    this.memtableFlushPeriodMs =
        is120 || isNullOrAbsent(row, MEMTABLE_FLUSH_PERIOD_MS)
            ? DEFAULT_MEMTABLE_FLUSH_PERIOD
            : row.getInt(MEMTABLE_FLUSH_PERIOD_MS);
    this.defaultTTL =
        is120 || isNullOrAbsent(row, DEFAULT_TTL) ? DEFAULT_DEFAULT_TTL : row.getInt(DEFAULT_TTL);
    this.speculativeRetry =
        is120 || isNullOrAbsent(row, SPECULATIVE_RETRY)
            ? DEFAULT_SPECULATIVE_RETRY
            : row.getString(SPECULATIVE_RETRY);

    if (is200)
      this.indexInterval =
          isNullOrAbsent(row, INDEX_INTERVAL) ? DEFAULT_INDEX_INTERVAL : row.getInt(INDEX_INTERVAL);
    else this.indexInterval = null;

    if (is210OrHigher) {
      this.minIndexInterval =
          isNullOrAbsent(row, MIN_INDEX_INTERVAL)
              ? DEFAULT_MIN_INDEX_INTERVAL
              : row.getInt(MIN_INDEX_INTERVAL);
      this.maxIndexInterval =
          isNullOrAbsent(row, MAX_INDEX_INTERVAL)
              ? DEFAULT_MAX_INDEX_INTERVAL
              : row.getInt(MAX_INDEX_INTERVAL);
    } else {
      this.minIndexInterval = null;
      this.maxIndexInterval = null;
    }

    if (is300OrHigher) {
      this.caching = ImmutableMap.copyOf(row.getMap(CACHING, String.class, String.class));
    } else if (is210) {
      this.caching = ImmutableMap.copyOf(SimpleJSONParser.parseStringMap(row.getString(CACHING)));
    } else {
      this.caching = ImmutableMap.of("keys", row.getString(CACHING));
    }

    if (is300OrHigher)
      this.compaction = ImmutableMap.copyOf(row.getMap(COMPACTION, String.class, String.class));
    else {
      this.compaction =
          ImmutableMap.<String, String>builder()
              .put("class", row.getString(COMPACTION_CLASS))
              .putAll(SimpleJSONParser.parseStringMap(row.getString(COMPACTION_OPTIONS)))
              .build();
    }

    if (is300OrHigher)
      this.compression = ImmutableMap.copyOf(row.getMap(COMPRESSION, String.class, String.class));
    else
      this.compression =
          ImmutableMap.copyOf(SimpleJSONParser.parseStringMap(row.getString(COMPRESSION_PARAMS)));

    if (is300OrHigher)
      this.crcCheckChance =
          isNullOrAbsent(row, CRC_CHECK_CHANCE)
              ? DEFAULT_CRC_CHECK_CHANCE
              : row.getDouble(CRC_CHECK_CHANCE);
    else this.crcCheckChance = null;

    if (is300OrHigher)
      this.extensions = ImmutableMap.copyOf(row.getMap(EXTENSIONS, String.class, ByteBuffer.class));
    else this.extensions = ImmutableMap.of();

    ImmutableMap.Builder<String, Map<String, String>> mapExtensionsBuilder =
        new ImmutableMap.Builder<String, Map<String, String>>();
    for (String mapExtension : MAP_EXTENSIONS) {
      ByteBuffer rawExtensionInfo = this.extensions.get(mapExtension);
      if (rawExtensionInfo != null) {
        Map<String, String> extensionInfo = new MapExtensionReader(rawExtensionInfo).parse();
        mapExtensionsBuilder.put(mapExtension, extensionInfo);
      }
    }
    this.mapExtensions = mapExtensionsBuilder.build();

    if (is380OrHigher) this.cdc = isNullOrAbsent(row, CDC) ? DEFAULT_CDC : row.getBool(CDC);
    else this.cdc = DEFAULT_CDC;

    if (is400OrHigher) this.additionalWritePolicy = row.getString(ADDITIONAL_WRITE_POLICY);
    else this.additionalWritePolicy = DEFAULT_ADDITIONAL_WRITE_POLICY;
  }

  private static boolean isNullOrAbsent(Row row, String name) {
    return row.getColumnDefinitions().getIndexOf(name) < 0 || row.isNull(name);
  }

  /**
   * Returns whether the table uses the {@code COMPACT STORAGE} option.
   *
   * @return whether the table uses the {@code COMPACT STORAGE} option.
   */
  public boolean isCompactStorage() {
    return isCompactStorage;
  }

  /**
   * Returns the commentary set for this table.
   *
   * @return the commentary set for this table, or {@code null} if noe has been set.
   */
  public String getComment() {
    return comment;
  }

  /**
   * Returns the chance with which a read repair is triggered for this table.
   *
   * @return the read repair chance set for table (in [0.0, 1.0]).
   */
  public double getReadRepairChance() {
    return readRepairChance;
  }

  /**
   * Returns the read_repair option for this table. <b>NOTE:</b> this is a Cassandra® 4.0 and newer
   * option (described here: <a
   * href="http://cassandra.apache.org/doc/latest/operating/read_repair.html">
   * http://cassandra.apache.org/doc/latest/operating/read_repair.html</a>). Possible values are
   * {@code BLOCKING} or {@code NONE}, with the default being {@code BLOCKING}.
   *
   * @return the read repair option (either {@code BLOCKING} or {@code NONE}).
   */
  public String getReadRepair() {
    return readRepair;
  }

  /**
   * Returns the cluster local read repair chance set for this table.
   *
   * @return the local read repair chance set for table (in [0.0, 1.0]).
   */
  public double getLocalReadRepairChance() {
    return localReadRepairChance;
  }

  /**
   * Returns whether replicateOnWrite is set for this table.
   *
   * <p>This is only meaningful for tables holding counters.
   *
   * @return whether replicateOnWrite is set for this table.
   */
  public boolean getReplicateOnWrite() {
    return replicateOnWrite;
  }

  /**
   * Returns the tombstone garbage collection grace time in seconds for this table.
   *
   * @return the tombstone garbage collection grace time in seconds for this table.
   */
  public int getGcGraceInSeconds() {
    return gcGrace;
  }

  /**
   * Returns the false positive chance for the Bloom filter of this table.
   *
   * @return the Bloom filter false positive chance for this table (in [0.0, 1.0]).
   */
  public double getBloomFilterFalsePositiveChance() {
    return bfFpChance;
  }

  /**
   * Returns the caching options for this table.
   *
   * @return an immutable map containing the caching options for this table.
   */
  public Map<String, String> getCaching() {
    return caching;
  }

  /**
   * Whether the populate I/O cache on flush is set on this table.
   *
   * @return whether the populate I/O cache on flush is set on this table.
   */
  public boolean getPopulateIOCacheOnFlush() {
    return populateCacheOnFlush;
  }

  /*
   * Returns the memtable flush period (in milliseconds) option for this table.
   * <p>
   * Note: this option is not available in Cassandra 1.2 and will return 0 (no periodic
   * flush) when connected to 1.2 nodes.
   *
   * @return the memtable flush period option for this table or 0 if no
   * periodic flush is configured.
   */
  public int getMemtableFlushPeriodInMs() {
    return memtableFlushPeriodMs;
  }

  /**
   * Returns the default TTL for this table.
   *
   * <p>Note: this option is not available in Cassandra 1.2 and will return 0 (no default TTL) when
   * connected to 1.2 nodes.
   *
   * @return the default TTL for this table or 0 if no default TTL is configured.
   */
  public int getDefaultTimeToLive() {
    return defaultTTL;
  }

  /**
   * Returns the speculative retry option for this table.
   *
   * <p>Note: this option is not available in Cassandra 1.2 and will return "NONE" (no speculative
   * retry) when connected to 1.2 nodes.
   *
   * @return the speculative retry option this table.
   */
  public String getSpeculativeRetry() {
    return speculativeRetry;
  }

  /**
   * Returns the index interval option for this table.
   *
   * <p>Note: this option is not available in Cassandra 1.2 (more precisely, it is not configurable
   * per-table) and will return 128 (the default index interval) when connected to 1.2 nodes. It is
   * deprecated in Cassandra 2.1 and above, and will therefore return {@code null} for 2.1 nodes.
   *
   * @return the index interval option for this table.
   */
  public Integer getIndexInterval() {
    return indexInterval;
  }

  /**
   * Returns the minimum index interval option for this table.
   *
   * <p>Note: this option is available in Cassandra 2.1 and above, and will return {@code null} for
   * earlier versions.
   *
   * @return the minimum index interval option for this table.
   */
  public Integer getMinIndexInterval() {
    return minIndexInterval;
  }

  /**
   * Returns the maximum index interval option for this table.
   *
   * <p>Note: this option is available in Cassandra 2.1 and above, and will return {@code null} for
   * earlier versions.
   *
   * @return the maximum index interval option for this table.
   */
  public Integer getMaxIndexInterval() {
    return maxIndexInterval;
  }

  /**
   * When compression is enabled, this option defines the probability with which checksums for
   * compressed blocks are checked during reads. The default value for this options is 1.0 (always
   * check).
   *
   * <p>Note that this option is available in Cassandra 3.0.0 and above, when it became a
   * "top-level" table option, whereas previously it was a suboption of the {@link #getCompression()
   * compression} option.
   *
   * <p>For Cassandra versions prior to 3.0.0, this method always returns {@code null}.
   *
   * @return the probability with which checksums for compressed blocks are checked during reads
   */
  public Double getCrcCheckChance() {
    return crcCheckChance;
  }

  /**
   * Returns the compaction options for this table.
   *
   * @return an immutable map containing the compaction options for this table.
   */
  public Map<String, String> getCompaction() {
    return compaction;
  }

  /**
   * Returns the compression options for this table.
   *
   * @return an immutable map containing the compression options for this table.
   */
  public Map<String, String> getCompression() {
    return compression;
  }

  /**
   * Returns the extension options for this table.
   *
   * <p>For Cassandra versions prior to 3.0.0, this method always returns an empty map.
   *
   * @return an immutable map containing the extension options for this table.
   */
  public Map<String, ByteBuffer> getExtensions() {
    return extensions;
  }

  /**
   * Returns the extension options for this table, but only those represented by {@code Map<String,
   * String>}.
   *
   * @return an immutable map containing the extension options for this table, but only those
   *     represented by {@code Map<String, String>}.
   */
  public Map<String, Map<String, String>> getMapExtensions() {
    return mapExtensions;
  }

  /**
   * Returns whether or not change data capture is enabled for this table.
   *
   * <p>For Cassandra versions prior to 3.8.0, this method always returns false.
   *
   * @return whether or not change data capture is enabled for this table.
   */
  public boolean isCDC() {
    return cdc;
  }

  /**
   * Returns whether or not Scylla change data capture is enabled for this table.
   *
   * @return whether or not Scylla change data capture is enabled for this table.
   */
  public boolean isScyllaCDC() {
    return mapExtensions.containsKey(SCYLLA_CDC_EXTENSION);
  }

  /**
   * Returns the options of Scylla Change Data Capture (CDC) for this table or @{code null} if CDC
   * is disabled on this table.
   *
   * @see <a href="https://docs.scylladb.com/using-scylla/cdc/#cdc-parameters">Scylla CDC
   *     Parameters</a>
   * @return the options of Scylla Change Data Capture (CDC) for this table or @{code null} if CDC
   *     is disabled on this table.
   */
  public Map<String, String> getScyllaCDCOptions() {
    return mapExtensions.get(SCYLLA_CDC_EXTENSION);
  }

  /**
   * Returns the options of Scylla Encryption at Rest for this table or @{code null} if Scylla
   * Encryption at Rest is disabled on this table.
   *
   * @see <a href="https://docs.scylladb.com/operating-scylla/security/encryption-at-rest/">Scylla
   *     Encryption at Rest</a>
   * @return the options of Scylla Encryption at Rest for this table or @{code null} if Scylla
   *     Encryption at Rest is disabled on this table.
   */
  public Map<String, String> getScyllaEncryptionOptions() {
    return mapExtensions.get(SCYLLA_ENCRYPTION_OPTIONS_EXTENSION);
  }

  /**
   * Returns the DynamoDB tags associated with this table if this is a valid Scylla Alternator table
   * or @{code null} otherwise.
   *
   * @see <a href="https://docs.scylladb.com/using-scylla/alternator/">Scylla Alternator</a>
   * @see <a
   *     href="https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Tagging.Operations.html">Tagging
   *     Resources in DynamoDB</a>
   * @return the DynamoDB tags associated with this table if this is a valid Scylla Alternator table
   *     or @{code null} otherwise.
   */
  public Map<String, String> getScyllaAlternatorTags() {
    return mapExtensions.get(SCYLLA_TAGS_EXTENSION);
  }

  /**
   * The threshold at which a cheap quorum write will be upgraded to include transient replicas.
   *
   * <p>This option is only available in Cassandra® 4.0 and above. Default value is {@code 99p}.
   *
   * @return The additional write policy for this table (ex. '99p').
   */
  public String getAdditionalWritePolicy() {
    return additionalWritePolicy;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof TableOptionsMetadata)) return false;

    TableOptionsMetadata that = (TableOptionsMetadata) other;
    return this.isCompactStorage == that.isCompactStorage
        && MoreObjects.equal(this.comment, that.comment)
        && this.readRepairChance == that.readRepairChance
        && this.localReadRepairChance == that.localReadRepairChance
        && MoreObjects.equal(this.readRepair, that.readRepair)
        && this.replicateOnWrite == that.replicateOnWrite
        && this.gcGrace == that.gcGrace
        && this.bfFpChance == that.bfFpChance
        && MoreObjects.equal(this.caching, that.caching)
        && this.populateCacheOnFlush == that.populateCacheOnFlush
        && this.memtableFlushPeriodMs == that.memtableFlushPeriodMs
        && this.defaultTTL == that.defaultTTL
        && this.cdc == that.cdc
        && MoreObjects.equal(this.speculativeRetry, that.speculativeRetry)
        && MoreObjects.equal(this.indexInterval, that.indexInterval)
        && MoreObjects.equal(this.minIndexInterval, that.minIndexInterval)
        && MoreObjects.equal(this.maxIndexInterval, that.maxIndexInterval)
        && MoreObjects.equal(this.compaction, that.compaction)
        && MoreObjects.equal(this.compression, that.compression)
        && MoreObjects.equal(this.crcCheckChance, that.crcCheckChance)
        && MoreObjects.equal(this.additionalWritePolicy, that.additionalWritePolicy)
        && MoreObjects.equal(this.extensions, that.extensions)
        && MoreObjects.equal(this.mapExtensions, that.mapExtensions);
  }

  @Override
  public int hashCode() {
    return MoreObjects.hashCode(
        isCompactStorage,
        comment,
        readRepairChance,
        localReadRepairChance,
        readRepair,
        replicateOnWrite,
        gcGrace,
        bfFpChance,
        caching,
        populateCacheOnFlush,
        memtableFlushPeriodMs,
        defaultTTL,
        speculativeRetry,
        indexInterval,
        minIndexInterval,
        maxIndexInterval,
        compaction,
        compression,
        crcCheckChance,
        extensions,
        cdc,
        mapExtensions,
        additionalWritePolicy);
  }
}
