/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.library;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.gallery.GalleryProvider2;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.unifile.UniFile;
import com.hippo.lib.yorozuya.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class LibraryExporter {

    private static final int BUFFER_SIZE = 1024 * 16;

    private LibraryExporter() {
    }

    public static int exportLibraryZip(@NonNull List<DownloadInfo> galleries,
            @NonNull UniFile outputFile) throws IOException {
        int exported = 0;
        OutputStream outputStream = null;
        ZipOutputStream zipOutputStream = null;
        try {
            outputStream = outputFile.openOutputStream();
            zipOutputStream = new ZipOutputStream(outputStream);
            for (DownloadInfo info : galleries) {
                UniFile dir = SpiderDen.getGalleryDownloadDir(info);
                if (dir == null || !dir.isDirectory()) {
                    continue;
                }
                LibraryManifest.write(info, SpiderInfo.getSpiderInfo(info), dir);
                String rootName = safeEntryName(dir.getName());
                if (rootName.length() == 0) {
                    rootName = Long.toString(info.gid);
                }
                addDirectory(zipOutputStream, dir, "galleries/" + rootName + "/");
                exported++;
            }
        } finally {
            IOUtils.closeQuietly(zipOutputStream);
            IOUtils.closeQuietly(outputStream);
        }
        return exported;
    }

    public static boolean exportGalleryCbz(@NonNull DownloadInfo info,
            @NonNull UniFile outputFile) throws IOException {
        UniFile dir = SpiderDen.getGalleryDownloadDir(info);
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        LibraryManifest.write(info, SpiderInfo.getSpiderInfo(info), dir);

        OutputStream outputStream = null;
        ZipOutputStream zipOutputStream = null;
        try {
            outputStream = outputFile.openOutputStream();
            zipOutputStream = new ZipOutputStream(outputStream);
            addImages(zipOutputStream, dir);
            UniFile manifest = dir.findFile(LibraryManifest.MANIFEST_FILENAME);
            if (manifest != null && manifest.isFile()) {
                addFile(zipOutputStream, manifest, LibraryManifest.MANIFEST_FILENAME);
            }
            return true;
        } finally {
            IOUtils.closeQuietly(zipOutputStream);
            IOUtils.closeQuietly(outputStream);
        }
    }

    public static int exportCbzFiles(@NonNull List<DownloadInfo> galleries,
            @NonNull UniFile outputDir) throws IOException {
        int exported = 0;
        for (DownloadInfo info : galleries) {
            if (exportGalleryCbzToDirectory(info, outputDir) != null) {
                exported++;
            }
        }
        return exported;
    }

    public static UniFile exportGalleryCbzToDirectory(@NonNull DownloadInfo info,
            @NonNull UniFile outputDir) throws IOException {
        UniFile dir = SpiderDen.getGalleryDownloadDir(info);
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        String filename = getCbzFilename(info);
        UniFile outputFile = outputDir.createFile(filename);
        if (outputFile != null && exportGalleryCbz(info, outputFile)) {
            return outputFile;
        }
        return null;
    }

    public static String getCbzFilename(@NonNull DownloadInfo info) {
        return safeFilename(String.format(Locale.US, "%d-%s.cbz", info.gid, info.token));
    }

    private static void addDirectory(ZipOutputStream zipOutputStream, UniFile dir,
            String entryPrefix) throws IOException {
        UniFile[] files = sortedFiles(dir);
        if (files == null) {
            return;
        }
        for (UniFile file : files) {
            String name = file.getName();
            if (name == null) {
                continue;
            }
            String entryName = entryPrefix + safeEntryName(name);
            if (file.isDirectory()) {
                addDirectory(zipOutputStream, file, entryName + "/");
            } else if (file.isFile()) {
                addFile(zipOutputStream, file, entryName);
            }
        }
    }

    private static void addImages(ZipOutputStream zipOutputStream, UniFile dir) throws IOException {
        UniFile[] files = sortedFiles(dir);
        if (files == null) {
            return;
        }
        for (UniFile file : files) {
            String name = file.getName();
            if (name == null || !file.isFile() || !isSupportedImage(name)) {
                continue;
            }
            addFile(zipOutputStream, file, safeEntryName(name));
        }
    }

    private static void addFile(ZipOutputStream zipOutputStream, UniFile file,
            String entryName) throws IOException {
        InputStream inputStream = null;
        try {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            inputStream = file.openInputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                zipOutputStream.write(buffer, 0, read);
            }
            zipOutputStream.closeEntry();
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    private static UniFile[] sortedFiles(UniFile dir) {
        UniFile[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        Arrays.sort(files, new Comparator<UniFile>() {
            @Override
            public int compare(UniFile lhs, UniFile rhs) {
                String lhsName = lhs.getName();
                String rhsName = rhs.getName();
                return (lhsName != null ? lhsName : "").compareTo(rhsName != null ? rhsName : "");
            }
        });
        return files;
    }

    private static boolean isSupportedImage(String name) {
        String lower = name.toLowerCase(Locale.US);
        for (String extension : GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String safeEntryName(String name) {
        return name.replace('\\', '/')
                .replace("../", "")
                .replace("..", "")
                .replaceAll("^/+", "");
    }

    private static String safeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
