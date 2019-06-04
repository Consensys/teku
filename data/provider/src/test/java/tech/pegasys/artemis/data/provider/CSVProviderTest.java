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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.tuweni.junit.TempDirectory;
import org.apache.tuweni.junit.TempDirectoryExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.data.TimeSeriesRecord;

@ExtendWith(TempDirectoryExtension.class)
class CSVProviderTest {

  @Test
  void testCSVPrint(@TempDirectory Path tempDirectory) throws IOException {
    Path logFile = tempDirectory.resolve("log.csv");
    CSVProvider provider = new CSVProvider(logFile);
    ArrayList<String> outFieldList = new ArrayList<String>();
    outFieldList.add("date");
    TimeSeriesRecord timeSeriesRecord1 = new TimeSeriesRecord();
    timeSeriesRecord1.filterOutputFields(outFieldList);
    TimeSeriesRecord timeSeriesRecord2 = new TimeSeriesRecord();
    timeSeriesRecord2.filterOutputFields(outFieldList);
    provider.serialOutput(timeSeriesRecord1);
    provider.serialOutput(timeSeriesRecord2);
    List<String> lines = Files.readAllLines(logFile);
    assertEquals(2, lines.size());
    String firstLine = lines.get(0);
    String date = firstLine.substring(1, firstLine.indexOf("'", 2));
    assertTrue(!date.isEmpty());
    Date parsedDate = new Date(Long.parseLong(date));
  }
}
