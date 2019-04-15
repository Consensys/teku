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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.data.IRecordAdapter;
import tech.pegasys.artemis.util.alogger.ALogger;

public class CSVProvider implements FileProvider {
  private static final ALogger LOG = new ALogger(CSVProvider.class.getName());

  public CSVProvider() {}

  @Override
  public void output(String filename, IRecordAdapter record) {
    try {
      BufferedWriter bw =
          new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename, true), "UTF-8"));
      StringBuilder line = new StringBuilder(record.toCSV());
      bw.write(line.toString());
      bw.newLine();
      bw.flush();
      bw.close();
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
  }
}
