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
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.unifile.UniFile;
import com.hippo.lib.yorozuya.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

public final class LibraryManifest {

    public static final String MANIFEST_FILENAME = "manifest.json";
    private static final String SCHEMA = "ehview.library.manifest.v1";

    private LibraryManifest() {
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
        manifest.put("schema", SCHEMA);
        manifest.put("generatedAt", System.currentTimeMillis());

        JSONObject source = new JSONObject();
        source.put("type", "ehentai");
        source.put("gid", galleryInfo.gid);
        source.put("token", galleryInfo.token);
        source.put("galleryUrl", EhUrl.getGalleryDetailUrl(galleryInfo.gid, galleryInfo.token));
        manifest.put("source", source);

        manifest.put("gallery", galleryInfo.toJson());
        if (galleryInfo instanceof DownloadInfo) {
            manifest.put("download", ((DownloadInfo) galleryInfo).toJson());
        }

        JSONObject reading = new JSONObject();
        if (spiderInfo != null) {
            reading.put("startPage", spiderInfo.startPage);
            reading.put("pages", spiderInfo.pages);
            reading.put("previewPages", spiderInfo.previewPages);
            reading.put("previewPerPage", spiderInfo.previewPerPage);
        } else {
            reading.put("startPage", 0);
            reading.put("pages", galleryInfo.pages);
        }
        manifest.put("reading", reading);
        manifest.put("files", listFiles(dir));

        OutputStream os = null;
        try {
            os = manifestFile.openOutputStream();
            os.write(manifest.toJSONString().getBytes(StandardCharsets.UTF_8));
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
        Arrays.sort(files, Comparator.comparing(file -> {
            String name = file.getName();
            return name != null ? name : "";
        }));
        for (UniFile file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name == null || name.startsWith(".")) {
                continue;
            }
            JSONObject fileJson = new JSONObject();
            fileJson.put("name", name);
            fileJson.put("length", file.length());
            filesJson.add(fileJson);
        }
        return filesJson;
    }
}
