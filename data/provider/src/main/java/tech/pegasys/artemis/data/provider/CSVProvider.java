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
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Objects;
import net.consensys.cava.bytes.Bytes32;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.data.RawRecord;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class CSVProvider {
  private static final Logger LOG = LogManager.getLogger(CSVProvider.class.getName());
  TimeSeriesRecord record;

  public CSVProvider() {}

  public CSVProvider(TimeSeriesRecord record) {
    this.record = record;
  }

  public TimeSeriesRecord getRecord() {
    return this.record;
  }

  public void setRecord(TimeSeriesRecord record) {
    this.record = record;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof CSVProvider)) {
      return false;
    }
    CSVProvider cSVProvider = (CSVProvider) o;
    return Objects.equals(record, cSVProvider.record);
  }

  @Override
  public int hashCode() {
    return Objects.hash(record);
  }

  @Override
  public String toString() {
    return " '"
        + record.getTime()
        + "'"
        + ", '"
        + record.getSlot()
        + "'"
        + ", '"
        + record.getBlockRoot().toHexString()
        + "'"
        + ", '"
        + record.getStateRoot().toHexString()
        + "'"
        + ", '"
        + record.getParentBlockRoot().toHexString()
        + "'";
  }

  public static void output(String path, String filename, List<CSVProvider> records) {
    try {
      BufferedWriter bw =
          new BufferedWriter(
              new OutputStreamWriter(
                  new FileOutputStream(path + "/" + filename + ".csv"), "UTF-8"));
      for (CSVProvider record : records) {
        StringBuilder line = new StringBuilder(record.toString());
        bw.write(line.toString());
        bw.newLine();
      }
      bw.flush();
      bw.close();
    } catch (IOException e) {
      LOG.warn(e);
    }
  }

  public static void output(String path, String filename, CSVProvider record) {
    try {
      BufferedWriter bw =
          new BufferedWriter(
              new OutputStreamWriter(
                  new FileOutputStream(path + "/" + filename + ".csv", true), "UTF-8"));
      StringBuilder line = new StringBuilder(record.toString());
      bw.write(line.toString());
      bw.newLine();
      bw.flush();
      bw.close();
    } catch (IOException e) {
      LOG.warn(e);
    }
  }

  @Subscribe
  public void onDataEvent(RawRecord record) {
    BeaconBlock block = record.getBlock();
    BeaconState state = record.getState();
    Bytes32 block_root = HashTreeUtil.hash_tree_root(block.toBytes());
    TimeSeriesRecord tsRecord =
        new TimeSeriesRecord(
            record.getNodeTime(),
            record.getNodeSlot(),
            block_root,
            block.getState_root(),
            block.getParent_root());
    CSVProvider csvRecord = new CSVProvider(tsRecord);
    CSVProvider.output(
        "/Users/jonny/projects/consensys/pegasys/artemis/", "artemis.csv", csvRecord);
  }
}
