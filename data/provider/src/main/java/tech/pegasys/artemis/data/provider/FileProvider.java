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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import tech.pegasys.artemis.datastructures.interfaces.IRecordAdapter;

public interface FileProvider {
  static Path uniqueFilename(String filename) throws IOException {
    String newFilename = filename;
    File f = new File(filename);
    int version = 1;
    while (f.exists()) {
      newFilename = filename + "." + version;
      f = new File(newFilename);
      version++;
    }
    return Paths.get(newFilename);
  }

  void serialOutput(IRecordAdapter record);

  void formattedOutput(IRecordAdapter record);

  default void close() {}
}
