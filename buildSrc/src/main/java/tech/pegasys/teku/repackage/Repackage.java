/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.repackage;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Repackage {
  /**
   * To support reproducible builds, repackage the provided zip file with modification times set to
   * the provided date. This will replace the original zip file.
   *
   * @param zipFile The zip file to repackage.
   * @param date The files will have modification date.
   * @throws IOException
   */
  static void repackageZip(String zipFile, Date date) throws IOException {
    // Create a temporary directory to use as a working directory.
    // We will delete this when we are finished.
    Path temp = Path.of(Files.createTempDirectory("unzip").toFile().getAbsolutePath());

    try {
      try (ZipFile zf = new ZipFile(zipFile)) {
        Enumeration<? extends ZipEntry> zipEntries = zf.entries();
        zipEntries
            .asIterator()
            .forEachRemaining(
                entry -> {
                  try {
                    Path path = temp.resolve(entry.getName());
                    if (entry.isDirectory()) {
                      Files.createDirectories(path);
                    } else {
                      Files.createDirectories(path.getParent());
                      Files.copy(zf.getInputStream(entry), path);
                    }
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                });
      }

      FileTime time = FileTime.fromMillis(date.getTime());
      Optional<Path> distDir = Optional.empty();
      try (Stream<Path> files = Files.list(temp)) {
        distDir = files.findFirst();
      }
      Path zippedDistDir = temp.resolve(distDir.orElseThrow() + ".zip");
      try (ZipOutputStream output =
          new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zippedDistDir)))) {
        // Sorted so that it will be reproducible.
        try (Stream<Path> walk = Files.walk(distDir.orElseThrow())) {
          walk.sorted()
              .forEach(
                  path -> {
                    // Force file separators to be UNIX-style so that it's the same on Windows.
                    String relativePath = temp.relativize(path).toString().replace("\\", "/");
                    try {
                      if (Files.isDirectory(path)) {
                        ZipEntry entry = new ZipEntry(relativePath + "/");
                        entry.setLastModifiedTime(time);
                        output.putNextEntry(entry);
                        output.closeEntry();
                      } else {
                        ZipEntry entry = new ZipEntry(relativePath);
                        entry.setLastModifiedTime(time);
                        output.putNextEntry(entry);
                        output.write(Files.readAllBytes(path));
                        output.closeEntry();
                      }
                    } catch (IOException e) {
                      e.printStackTrace();
                    }
                  });
        }
      }

      // Set the modification date of the final zip file.
      Files.setLastModifiedTime(zippedDistDir, time);
      // Replace the original zip file with the repackaged one.
      Files.copy(zippedDistDir, Path.of(zipFile), COPY_ATTRIBUTES, REPLACE_EXISTING);
    } finally {
      // Delete the temporary directory.
      // Thank you, SubOptimal: https://stackoverflow.com/a/35989142
      try (Stream<Path> walk = Files.walk(temp)) {
        walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
  }
}
