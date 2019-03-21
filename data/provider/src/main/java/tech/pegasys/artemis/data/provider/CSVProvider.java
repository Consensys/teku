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

import java.util.Objects;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.util.alogger.ALogger;

public class CSVProvider extends FileProvider<CSVProvider> {
  private static final ALogger LOG = new ALogger(CSVProvider.class.getName());

  public CSVProvider() {}

  public CSVProvider(TimeSeriesRecord record) {
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
        + record.getIndex()
        + "'"
        + ", '"
        + record.getSlot()
        + "'"
        + ", '"
        + record.getEpoch()
        + "'"
        + ", '"
        + record.getHeadBlockRoot()
        + "'"
        + ", '"
        + record.getHeadStateRoot()
        + "'"
        + ", '"
        + record.getParentHeadBlockRoot()
        + "'"
        + ", '"
        + record.getNumValidators()
        + "'"
        + ", '"
        + record.getJustifiedBlockRoot()
        + "'"
        + ", '"
        + record.getJustifiedStateRoot()
        + "'";
  }
}
