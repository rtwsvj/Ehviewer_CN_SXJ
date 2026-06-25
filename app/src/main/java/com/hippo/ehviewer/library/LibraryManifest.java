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

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.util.JsonUtils;
import com.hippo.unifile.UniFile;
import com.hippo.lib.yorozuya.IOUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class LibraryManifest {

    public static final String MANIFEST_FILENAME = "manifest.json";
    private static final String SCHEMA = "ehview.library.manifest.v1";

    private LibraryManifest() {
    }

    @Nullable
    public static Record read(@NonNull UniFile dir) {
        UniFile manifestFile = dir.findFile(MANIFEST_FILENAME);
        if (manifestFile == null || !manifestFile.isFile()) {
            return null;
        }

        InputStream is = null;
        try {
            is = manifestFile.openInputStream();
            JSONObject manifest = new JSONObject(IOUtils.readString(is, StandardCharsets.UTF_8.name()));

            DownloadInfo info = readDownloadInfo(manifest);
            if (info == null || info.gid <= 0) {
                return Record.warning(dir.getName(), "manifest has no gallery info");
            }
            String dirname = dir.getName();
            SpiderInfo spiderInfo = readSpiderInfo(manifest, info);
            return new Record(dirname, info, spiderInfo, readFiles(manifest), null, true, false);
        } catch (Throwable e) {
            return Record.warning(dir.getName(), "manifest read failed");
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public static boolean write(@NonNull GalleryInfo galleryInfo, @Nullable SpiderInfo spiderInfo) {
        UniFile dir = SpiderDen.getGalleryDownloadDir(galleryInfo);
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        return write(galleryInfo, spiderInfo, dir);
    }

    public static boolean write(@NonNull GalleryInfo galleryInfo, @Nullable SpiderInfo spiderInfo,
            @NonNull UniFile dir) {
        UniFile manifestFile = dir.createFile(MANIFEST_FILENAME);
        if (manifestFile == null) {
            return false;
        }

        JSONObject manifest = new JSONObject();
        JsonUtils.put(manifest, "schema", SCHEMA);
        JsonUtils.put(manifest, "generatedAt", System.currentTimeMillis());

        JSONObject source = new JSONObject();
        JsonUtils.put(source, "type", "ehentai");
        JsonUtils.put(source, "gid", galleryInfo.gid);
        JsonUtils.put(source, "token", galleryInfo.token);
        JsonUtils.put(source, "galleryUrl", EhUrl.getGalleryDetailUrl(galleryInfo.gid, galleryInfo.token));
        JsonUtils.put(manifest, "source", source);

        JsonUtils.put(manifest, "gallery", galleryInfo.toJson());
        if (galleryInfo instanceof DownloadInfo) {
            JsonUtils.put(manifest, "download", ((DownloadInfo) galleryInfo).toJson());
        }

        JSONObject reading = new JSONObject();
        if (spiderInfo != null) {
            JsonUtils.put(reading, "startPage", spiderInfo.startPage);
            JsonUtils.put(reading, "pages", spiderInfo.pages);
            JsonUtils.put(reading, "previewPages", spiderInfo.previewPages);
            JsonUtils.put(reading, "previewPerPage", spiderInfo.previewPerPage);
        } else {
            JsonUtils.put(reading, "startPage", 0);
            JsonUtils.put(reading, "pages", galleryInfo.pages);
        }
        JsonUtils.put(manifest, "reading", reading);
        JsonUtils.put(manifest, "files", listFiles(dir));

        OutputStream os = null;
        try {
            os = manifestFile.openOutputStream();
            os.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    private static JSONArray listFiles(@NonNull UniFile dir) {
        JSONArray filesJson = new JSONArray();
        UniFile[] files = dir.listFiles();
        if (files == null) {
            return filesJson;
        }
        Arrays.sort(files, new Comparator<UniFile>() {
            @Override
            public int compare(UniFile lhs, UniFile rhs) {
                String lhsName = lhs.getName();
                String rhsName = rhs.getName();
                return (lhsName != null ? lhsName : "").compareTo(rhsName != null ? rhsName : "");
            }
        });
        for (UniFile file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name == null || name.startsWith(".") || MANIFEST_FILENAME.equals(name)) {
                continue;
            }
            JSONObject fileJson = new JSONObject();
            JsonUtils.put(fileJson, "name", name);
            JsonUtils.put(fileJson, "length", file.length());
            filesJson.put(fileJson);
        }
        return filesJson;
    }

    @Nullable
    private static DownloadInfo readDownloadInfo(JSONObject manifest) {
        JSONObject download = manifest.optJSONObject("download");
        if (download != null) {
            return DownloadInfo.downloadInfoFromJson(download);
        }

        JSONObject gallery = manifest.optJSONObject("gallery");
        if (gallery != null) {
            return GalleryInfo.galleryInfoFromJson(gallery).getDownloadInfo(null);
        }

        JSONObject source = manifest.optJSONObject("source");
        if (source == null) {
            return null;
        }
        DownloadInfo info = new DownloadInfo();
        info.gid = source.optLong("gid");
        info.token = JsonUtils.optStringOrNull(source, "token");
        return info;
    }

    @Nullable
    private static SpiderInfo readSpiderInfo(JSONObject manifest, DownloadInfo info) {
        JSONObject reading = manifest.optJSONObject("reading");
        if (reading == null) {
            return null;
        }

        SpiderInfo spiderInfo = new SpiderInfo();
        spiderInfo.gid = info.gid;
        spiderInfo.token = info.token;
        spiderInfo.startPage = Math.max(0, reading.optInt("startPage"));
        spiderInfo.pages = reading.optInt("pages");
        spiderInfo.previewPages = reading.optInt("previewPages");
        spiderInfo.previewPerPage = reading.optInt("previewPerPage");
        if (spiderInfo.pages <= 0) {
            spiderInfo.pages = info.pages;
        }
        if (spiderInfo.pages > 0) {
            spiderInfo.pTokenMap = new SparseArray<>(spiderInfo.pages);
        }
        return spiderInfo.pages > 0 ? spiderInfo : null;
    }

    private static List<FileEntry> readFiles(JSONObject manifest) {
        List<FileEntry> result = new ArrayList<>();
        JSONArray files = manifest.optJSONArray("files");
        if (files == null) {
            return result;
        }
        for (int i = 0, n = files.length(); i < n; i++) {
            JSONObject file = files.optJSONObject(i);
            if (file == null) {
                continue;
            }
            String name = JsonUtils.optStringOrNull(file, "name");
            if (name == null) {
                continue;
            }
            result.add(new FileEntry(name, file.optLong("length")));
        }
        return result;
    }

    public static final class Record {
        @Nullable
        public final String dirname;
        @Nullable
        public final DownloadInfo downloadInfo;
        @Nullable
        public final SpiderInfo spiderInfo;
        @NonNull
        public final List<FileEntry> files;
        @Nullable
        public final String warning;
        public final boolean fromManifest;
        public final boolean fromLegacySpiderInfo;

        public Record(@Nullable String dirname, @Nullable DownloadInfo downloadInfo,
                @Nullable SpiderInfo spiderInfo, @NonNull List<FileEntry> files,
                @Nullable String warning, boolean fromManifest, boolean fromLegacySpiderInfo) {
            this.dirname = dirname;
            this.downloadInfo = downloadInfo;
            this.spiderInfo = spiderInfo;
            this.files = files;
            this.warning = warning;
            this.fromManifest = fromManifest;
            this.fromLegacySpiderInfo = fromLegacySpiderInfo;
        }

        static Record warning(@Nullable String dirname, @NonNull String warning) {
            return new Record(dirname, null, null, new ArrayList<>(), warning, false, false);
        }
    }

    public static final class FileEntry {
        @NonNull
        public final String name;
        public final long length;

        FileEntry(@NonNull String name, long length) {
            this.name = name;
            this.length = length;
        }
    }
}
