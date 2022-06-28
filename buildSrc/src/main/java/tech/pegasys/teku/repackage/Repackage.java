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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

public class Repackage {
  /**
   * To support reproducible builds, repackage the provided distribution file with modification
   * times set to the provided date. This will replace the original distribution file.
   *
   * @param distFile The distribution file to repackage.
   * @param date The files will have this modification date.
   * @throws IOException
   */
  public static void repackage(String distFile, Date date) throws IOException {
    // Create a temporary directory to use as a working directory.
    // We will delete this when we are finished.
    Path tempDir = Path.of(Files.createTempDirectory("repackage").toFile().getAbsolutePath());

    try {
      Path distPath = Path.of(distFile);
      Path tempDistPath = tempDir.resolve(distPath.getFileName());
      FileTime fileTime = FileTime.fromMillis(date.getTime());

      if (distFile.endsWith(".zip")) {
        repackageZip(distPath, fileTime, tempDir);
      } else if (distFile.endsWith(".tar.gz")) {
        repackageTarGz(distPath, fileTime, tempDir);
      } else {
        throw new IllegalArgumentException("bad distribution");
      }

      // Set the modification date of the final distribution file.
      Files.setLastModifiedTime(tempDistPath, fileTime);
      // Replace the original distribution file with the repackaged one.
      Files.copy(tempDistPath, distPath, COPY_ATTRIBUTES, REPLACE_EXISTING);
    } finally {
      // Delete the temporary directory.
      // Thank you, SubOptimal: https://stackoverflow.com/a/35989142
      try (Stream<Path> walk = Files.walk(tempDir)) {
        walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
  }

  /**
   * To support reproducible builds, remove the "report generated at" timestamp from the license
   * report HTML index file. This will replace the original file.
   *
   * @param reportFile The license report file to fix.
   * @throws IOException
   */
  public static void removeTimestampFromLicenseReport(String reportFile) throws IOException {
    Path reportIndex = Path.of(reportFile);
    Path tempFile = Files.createTempFile("index", "html");
    BufferedReader reader = new BufferedReader(new FileReader(reportIndex.toFile()));
    BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()));

    String line;
    while ((line = reader.readLine()) != null) {
      if (line.contains("<p>Report Generated at: ")) {
        continue;
      }
      // Use UNIX line separator so it behaves the same on Windows.
      writer.write(line + "\n");
    }
    writer.close();
    reader.close();

    // Now replace the original and delete the temporary file.
    Files.copy(tempFile, reportIndex, REPLACE_EXISTING);
    Files.delete(tempFile);
  }

  private static void repackageZip(Path zipDist, FileTime fileTime, Path tempDir)
      throws IOException {
    try (ZipFile zf = new ZipFile(zipDist.toString())) {
      Enumeration<? extends ZipEntry> zipEntries = zf.entries();
      zipEntries
          .asIterator()
          .forEachRemaining(
              entry -> {
                try {
                  Path path = tempDir.resolve(entry.getName());
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

    Path newZipDist = tempDir.resolve(zipDist.getFileName());
    Path zipDistDir = tempDir.resolve(zipDist.getFileName().toString().replace(".zip", ""));
    try (ZipOutputStream output =
        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(newZipDist)))) {
      // Sorted so that it will be reproducible.
      try (Stream<Path> walk = Files.walk(zipDistDir)) {
        walk.sorted()
            .forEach(
                path -> {
                  // Force file separators to be UNIX-style so that it's the same on Windows.
                  String relativePath = toUnixPath(tempDir.relativize(path));
                  try {
                    if (Files.isDirectory(path)) {
                      ZipEntry entry = new ZipEntry(toDir(relativePath));
                      entry.setLastModifiedTime(fileTime);
                      output.putNextEntry(entry);
                      // Do not write a file. It's a directory.
                      output.closeEntry();
                    } else {
                      ZipEntry entry = new ZipEntry(relativePath);
                      entry.setLastModifiedTime(fileTime);
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
  }

  private static void repackageTarGz(Path tarDist, FileTime fileTime, Path tempDir)
      throws IOException {
    // Used this website as a resource for doing this:
    // https://mkyong.com/java/how-to-create-tar-gz-in-java/
    try (InputStream fi = Files.newInputStream(tarDist);
        BufferedInputStream bi = new BufferedInputStream(fi);
        GzipCompressorInputStream gzi = new GzipCompressorInputStream(bi);
        TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {

      ArchiveEntry entry;
      while ((entry = ti.getNextEntry()) != null) {
        Path path = tempDir.resolve(entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(path);
        } else {
          Files.createDirectories(path.getParent());
          Files.copy(ti, path);
        }
      }
    }

    Path newTarDist = tempDir.resolve(tarDist.getFileName());
    Path tarDistDir = tempDir.resolve(tarDist.getFileName().toString().replace(".tar.gz", ""));
    try (OutputStream fOut = Files.newOutputStream(newTarDist);
        BufferedOutputStream buffOut = new BufferedOutputStream(fOut);
        GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(buffOut);
        TarArchiveOutputStream tOut = new TarArchiveOutputStream(gzOut)) {
      try (Stream<Path> walk = Files.walk(tarDistDir)) {
        walk.sorted()
            .forEach(
                path -> {
                  // Force file separators to be UNIX-style so that it's the same on Windows.
                  String relativePath = toUnixPath(tempDir.relativize(path));
                  try {
                    if (Files.isDirectory(path)) {
                      TarArchiveEntry entry = new TarArchiveEntry(path, toDir(relativePath));
                      entry.setModTime(fileTime);
                      tOut.putArchiveEntry(entry);
                      // Do not write a file. It's a directory.
                      tOut.closeArchiveEntry();
                    } else {
                      TarArchiveEntry entry = new TarArchiveEntry(path, relativePath);
                      entry.setModTime(fileTime);
                      tOut.putArchiveEntry(entry);
                      Files.copy(path, tOut);
                      tOut.closeArchiveEntry();
                    }
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                });
      }
      tOut.finish();
    }
  }

  private static String toUnixPath(Path path) {
    return path.toString().replace("\\", "/");
  }

  private static String toDir(String path) {
    return path + "/";
  }
}
