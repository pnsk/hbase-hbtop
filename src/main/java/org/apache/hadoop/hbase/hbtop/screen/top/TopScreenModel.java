/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.hbtop.screen.top;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.hbtop.Record;
import org.apache.hadoop.hbase.hbtop.RecordFilter;
import org.apache.hadoop.hbase.hbtop.field.Field;
import org.apache.hadoop.hbase.hbtop.field.FieldInfo;
import org.apache.hadoop.hbase.hbtop.field.FieldValue;
import org.apache.hadoop.hbase.hbtop.mode.DrillDownInfo;
import org.apache.hadoop.hbase.hbtop.mode.Mode;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * The data and business logic for the top screen.
 */
@InterfaceAudience.Private
public class TopScreenModel {

  private static final Logger LOGGER = LoggerFactory.getLogger(TopScreenModel.class);

  private final Admin admin;

  private Mode currentMode;
  private Field currentSortField;
  private List<FieldInfo> fieldInfos;
  private List<Field> fields;

  private Summary summary;
  private List<Record> records;

  private final List<RecordFilter> filters = new ArrayList<>();
  private final List<String> filterHistories = new ArrayList<>();

  private boolean ascendingSort;

  public TopScreenModel(Admin admin, Mode initialMode) {
    this.admin = Objects.requireNonNull(admin);
    switchMode(Objects.requireNonNull(initialMode), null, false);
  }

  public void switchMode(Mode nextMode, List<RecordFilter> initialFilters,
    boolean keepSortFieldAndSortOrderIfPossible) {

    currentMode = nextMode;
    fieldInfos = Collections.unmodifiableList(new ArrayList<>(currentMode.getFieldInfos()));
    fields = Collections.unmodifiableList(currentMode.getFieldInfos().stream()
      .map(FieldInfo::getField).collect(Collectors.toList()));

    if (keepSortFieldAndSortOrderIfPossible) {
      boolean match = fields.stream().anyMatch(f -> f == currentSortField);
      if (!match) {
        currentSortField = nextMode.getDefaultSortField();
        ascendingSort = false;
      }
    } else {
      currentSortField = nextMode.getDefaultSortField();
      ascendingSort = false;
    }

    clearFilters();
    if (initialFilters != null) {
      filters.addAll(initialFilters);
    }
  }

  public void setSortFieldAndFields(Field sortField, List<Field> fields) {
    this.currentSortField = sortField;
    this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
  }

  /*
   * HBTop only calls this from a single thread, and if that ever changes, this needs
   * synchronization
   */
  public void refreshMetricsData() {
    ClusterMetrics clusterMetrics;
    try {
      clusterMetrics = admin.getClusterMetrics();
    } catch (Exception e) {
      LOGGER.error("Unable to get cluster metrics", e);
      return;
    }

    refreshSummary(clusterMetrics);
    refreshRecords(clusterMetrics);
  }

  private void refreshSummary(ClusterMetrics clusterMetrics) {
    String currentTime = DateFormatUtils.ISO_8601_EXTENDED_TIME_FORMAT
      .format(System.currentTimeMillis());
    String version = clusterMetrics.getHBaseVersion();
    String clusterId = clusterMetrics.getClusterId();
    int liveServers = clusterMetrics.getLiveServerMetrics().size();
    int deadServers = clusterMetrics.getDeadServerNames().size();
    int regionCount = clusterMetrics.getRegionCount();
    int ritCount = clusterMetrics.getRegionStatesInTransition().size();
    double averageLoad = clusterMetrics.getAverageLoad();
    long aggregateRequestPerSecond = clusterMetrics.getLiveServerMetrics().entrySet().stream()
      .mapToLong(e -> e.getValue().getRequestCountPerSecond()).sum();

    summary = new Summary(currentTime, version, clusterId, liveServers + deadServers,
      liveServers, deadServers, regionCount, ritCount, averageLoad, aggregateRequestPerSecond);
  }

  private void refreshRecords(ClusterMetrics clusterMetrics) {
    List<Record> records = currentMode.getRecords(clusterMetrics);

    // Filter and sort
    records = records.stream()
      .filter(r -> filters.stream().allMatch(f -> f.execute(r)))
      .sorted((recordLeft, recordRight) -> {
        FieldValue left = recordLeft.get(currentSortField);
        FieldValue right = recordRight.get(currentSortField);
        return (ascendingSort ? 1 : -1) * left.compareTo(right);
      }).collect(Collectors.toList());

    this.records = Collections.unmodifiableList(records);
  }

  public void switchSortOrder() {
    ascendingSort = !ascendingSort;
  }

  public boolean addFilter(String filterString, boolean ignoreCase) {
    RecordFilter filter = RecordFilter.parse(filterString, fields, ignoreCase);
    if (filter == null) {
      return false;
    }

    filters.add(filter);
    filterHistories.add(filterString);
    return true;
  }

  public void clearFilters() {
    filters.clear();
  }

  public boolean drillDown(Record selectedRecord) {
    DrillDownInfo drillDownInfo = currentMode.drillDown(selectedRecord);
    if (drillDownInfo == null) {
      return false;
    }
    switchMode(drillDownInfo.getNextMode(), drillDownInfo.getInitialFilters(), true);
    return true;
  }

  public Mode getCurrentMode() {
    return currentMode;
  }

  public Field getCurrentSortField() {
    return currentSortField;
  }

  public List<FieldInfo> getFieldInfos() {
    return fieldInfos;
  }

  public List<Field> getFields() {
    return fields;
  }

  public Summary getSummary() {
    return summary;
  }

  public List<Record> getRecords() {
    return records;
  }

  public List<RecordFilter> getFilters() {
    return Collections.unmodifiableList(filters);
  }

  public List<String> getFilterHistories() {
    return Collections.unmodifiableList(filterHistories);
  }
}
