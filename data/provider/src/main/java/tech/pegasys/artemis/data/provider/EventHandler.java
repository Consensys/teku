/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.data.provider;

import com.google.common.eventbus.Subscribe;
import java.util.List;
import tech.pegasys.artemis.data.IRecordAdapter;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.data.adapter.TimeSeriesAdapter;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class EventHandler {
  List<String> events;
  FileProvider fileProvider;
  boolean isFormat;

  public EventHandler(ArtemisConfiguration config, FileProvider fileProvider) {
    this.events = config.getEvents();
    this.isFormat = config.isFormat();
    this.fileProvider = fileProvider;
  }

  @Subscribe
  public void onDataEvent(RawRecord record) {
    TimeSeriesAdapter adapter = new TimeSeriesAdapter(record);
    TimeSeriesRecord tsRecord = adapter.transform();
    if (events == null || events.contains(tsRecord.getClass().getSimpleName())) output(tsRecord);
  }

  @Subscribe
  public void onEvent(IRecordAdapter record) {
    String name = record.getClass().getSimpleName();
    if (events != null && events.contains(name)) output(record);
  }

  private void output(IRecordAdapter record) {
    if (isFormat) {
      fileProvider.formattedOutput(record);
    } else {
      fileProvider.serialOutput(record);
    }
  }
}
