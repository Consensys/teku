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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.data.IRecordAdapter;
import tech.pegasys.artemis.util.alogger.ALogger;

public class JSONProvider implements FileProvider {
  private static final ALogger LOG = new ALogger(JSONProvider.class.getName());

  public JSONProvider() {}

  @Override
  public void output(String filename, IRecordAdapter record) {
    try {
      RandomAccessFile r = new RandomAccessFile(new File(filename), "rw");
      RandomAccessFile rtemp = new RandomAccessFile(new File(filename + "~"), "rw");
      long fileSize = r.length();

      long offset = fileSize == 0 ? 0 : r.length() - 2;
      byte[] content =
          fileSize == 0
              ? ("[\n\t" + record.toJSON() + "\n]").getBytes(UTF_8)
              : (",\n\t" + record.toJSON()).getBytes(UTF_8);

      FileChannel sourceChannel = r.getChannel();
      FileChannel targetChannel = rtemp.getChannel();
      sourceChannel.transferTo(offset, (fileSize - offset), targetChannel);
      sourceChannel.truncate(offset);
      r.seek(offset);
      r.write(content);
      long newOffset = r.getFilePointer();
      targetChannel.position(0L);
      sourceChannel.transferFrom(targetChannel, newOffset, (fileSize - offset));
      sourceChannel.close();
      targetChannel.close();
    } catch (Exception e) {
      LOG.log(Level.ERROR, e.getMessage());
    }
  }
}
