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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.data.TimeSeriesRecord;
import tech.pegasys.artemis.util.alogger.ALogger;

public class JSONProvider extends FileProvider<JSONProvider> {
  private static final ALogger LOG = new ALogger(JSONProvider.class.getName());

  public JSONProvider(String filename) {
    init(filename);
  }

  public JSONProvider(TimeSeriesRecord record) {
    this.record = record;
  }

  // adds the array brackets for JSON formatting
  private static void init(String filename) {
    try {
      BufferedWriter bw =
          new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename, true), "UTF-8"));
      StringBuilder line = new StringBuilder("[]");
      bw.write(line.toString());
      bw.flush();
      bw.close();
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
  }
  // reads in and inserts next JSON object
  private static String readInFileForEdit(String filename, StringBuilder insert)
      throws IOException {
    BufferedReader reader;
    reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(filename), Charset.forName("UTF-8")));
    String line = "";
    String patternEnd = "^[^\\]]*\\]$";
    StringBuilder builder = new StringBuilder();
    while (line != null && !line.matches(patternEnd)) {
      line = reader.readLine();
      builder.append(line);
    }
    if (builder.toString().contains("{")) builder.insert(builder.length() - 1, ",\n");
    builder.insert(builder.length() - 1, insert);
    return builder.toString();
  }

  public static <T> void output(String filename, T record) {

    try {
      StringBuilder line = new StringBuilder(record.toString());
      String readFile = readInFileForEdit(filename, line);
      BufferedWriter bw =
          new BufferedWriter(
              new OutputStreamWriter(new FileOutputStream(filename, false), "UTF-8"));
      bw.write(readFile);
      bw.flush();
      bw.close();
    } catch (IOException e) {
      LOG.log(Level.WARN, e.toString());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof JSONProvider)) {
      return false;
    }
    JSONProvider jSONProvider = (JSONProvider) o;
    return Objects.equals(record, jSONProvider.record);
  }

  @Override
  public int hashCode() {
    return Objects.hash(record);
  }

  @Override
  public String toString() {
    Gson gson = new GsonBuilder().create();
    return gson.toJson(record);
  }
}
