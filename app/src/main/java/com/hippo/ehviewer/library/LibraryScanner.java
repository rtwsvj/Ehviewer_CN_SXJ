/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.library;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.GalleryTags;
import com.hippo.ehviewer.gallery.GalleryProvider2;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.unifile.UniFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class LibraryScanner {

    private LibraryScanner() {
    }

    @NonNull
    public static Result scan(@Nullable UniFile root) {
        Result result = new Result();
        if (root == null || !root.isDirectory()) {
            result.failed++;
            result.warnings.add("local library root is not readable");
            return result;
        }

        UniFile[] files = root.listFiles();
        if (files == null) {
            result.failed++;
            result.warnings.add("local library root cannot be listed");
            return result;
        }
        Arrays.sort(files, new Comparator<UniFile>() {
            @Override
            public int compare(UniFile lhs, UniFile rhs) {
                String lhsName = lhs.getName();
                String rhsName = rhs.getName();
                return (lhsName != null ? lhsName : "").compareTo(rhsName != null ? rhsName : "");
            }
        });

        for (UniFile dir : files) {
            if (dir == null || !dir.isDirectory()) {
                continue;
            }
            result.scanned++;
            Item item = scanDirectory(dir, result);
            if (item != null) {
                result.items.add(item);
                result.found++;
            }
        }
        return result;
    }

    @Nullable
    private static Item scanDirectory(@NonNull UniFile dir, @NonNull Result result) {
        LibraryManifest.Record record = LibraryManifest.read(dir);
        if (record == null) {
            record = readLegacySpiderInfo(dir);
        }
        if (record == null) {
            result.skipped++;
            return null;
        }
        if (record.warning != null) {
            result.warnings.add(record.warning);
        }
        DownloadInfo info = record.downloadInfo;
        if (info == null || info.gid <= 0 || info.token == null) {
            result.failed++;
            return null;
        }

        SpiderInfo spiderInfo = record.spiderInfo;
        int pages = getPages(info, spiderInfo);
        int imageCount = countImages(dir);
        boolean complete = info.state == DownloadInfo.STATE_FINISH || (pages > 0 && imageCount >= pages);

        if (info.title == null || info.title.length() == 0) {
            info.title = record.dirname != null ? record.dirname : Long.toString(info.gid);
        }
        info.state = complete ? DownloadInfo.STATE_FINISH : DownloadInfo.STATE_NONE;
        info.total = pages > 0 ? pages : imageCount;
        info.finished = complete ? info.total : Math.min(imageCount, Math.max(info.total, 0));
        info.downloaded = info.finished;
        info.legacy = Math.max(0, info.total - info.finished);
        if (info.time <= 0) {
            info.time = System.currentTimeMillis();
        }

        GalleryTags tags = buildGalleryTags(info);
        return new Item(record.dirname, info, spiderInfo, tags, imageCount, complete,
                record.fromManifest, record.fromLegacySpiderInfo);
    }

    @Nullable
    private static LibraryManifest.Record readLegacySpiderInfo(@NonNull UniFile dir) {
        UniFile file = dir.findFile(SpiderQueen.SPIDER_INFO_FILENAME);
        SpiderInfo spiderInfo = SpiderInfo.read(file);
        if (spiderInfo == null) {
            return null;
        }
        DownloadInfo info = new DownloadInfo();
        info.gid = spiderInfo.gid;
        info.token = spiderInfo.token;
        info.pages = spiderInfo.pages;
        info.total = spiderInfo.pages;
        info.title = dir.getName();
        return new LibraryManifest.Record(dir.getName(), info, spiderInfo,
                new ArrayList<LibraryManifest.FileEntry>(), null, false, true);
    }

    private static int getPages(@NonNull DownloadInfo info, @Nullable SpiderInfo spiderInfo) {
        if (spiderInfo != null && spiderInfo.pages > 0) {
            return spiderInfo.pages;
        }
        if (info.pages > 0) {
            return info.pages;
        }
        if (info.total > 0) {
            return info.total;
        }
        return 0;
    }

    private static int countImages(@NonNull UniFile dir) {
        UniFile[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (UniFile file : files) {
            String name = file.getName();
            if (file.isFile() && name != null && isSupportedImage(name)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isSupportedImage(@NonNull String name) {
        String lower = name.toLowerCase(Locale.US);
        for (String extension : GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static GalleryTags buildGalleryTags(@NonNull GalleryInfo info) {
        ArrayList<String> tags = info.tgList;
        String[] simpleTags = info.simpleTags;
        if ((tags == null || tags.isEmpty()) && (simpleTags == null || simpleTags.length == 0)) {
            return null;
        }

        GalleryTags galleryTags = new GalleryTags(info.gid);
        if (tags != null) {
            for (String tag : tags) {
                addTag(galleryTags, tag);
            }
        }
        if (simpleTags != null) {
            for (String tag : simpleTags) {
                addTag(galleryTags, tag);
            }
        }
        Date now = new Date();
        galleryTags.create_time = now;
        galleryTags.update_time = now;
        return galleryTags;
    }

    private static void addTag(@NonNull GalleryTags tags, @Nullable String tag) {
        if (tag == null) {
            return;
        }
        int index = tag.indexOf(':');
        if (index <= 0 || index >= tag.length() - 1) {
            return;
        }
        String namespace = tag.substring(0, index);
        String value = tag.substring(index + 1);
        switch (namespace) {
            case "rows":
                tags.rows = append(tags.rows, value);
                break;
            case "artist":
                tags.artist = append(tags.artist, value);
                break;
            case "cosplayer":
                tags.cosplayer = append(tags.cosplayer, value);
                break;
            case "character":
                tags.character = append(tags.character, value);
                break;
            case "female":
                tags.female = append(tags.female, value);
                break;
            case "group":
                tags.group = append(tags.group, value);
                break;
            case "language":
                tags.language = append(tags.language, value);
                break;
            case "male":
                tags.male = append(tags.male, value);
                break;
            case "misc":
                tags.misc = append(tags.misc, value);
                break;
            case "mixed":
                tags.mixed = append(tags.mixed, value);
                break;
            case "other":
                tags.other = append(tags.other, value);
                break;
            case "parody":
                tags.parody = append(tags.parody, value);
                break;
            case "reclass":
                tags.reclass = append(tags.reclass, value);
                break;
            default:
                break;
        }
    }

    private static String append(@Nullable String existing, @NonNull String value) {
        return existing == null || existing.length() == 0 ? value : existing + "," + value;
    }

    public static final class Result {
        public int scanned;
        public int found;
        public int imported;
        public int updated;
        public int skipped;
        public int failed;
        @NonNull
        public final List<Item> items = new ArrayList<>();
        @NonNull
        public final List<String> warnings = new ArrayList<>();
    }

    public static final class Item {
        @Nullable
        public final String dirname;
        @NonNull
        public final DownloadInfo downloadInfo;
        @Nullable
        public final SpiderInfo spiderInfo;
        @Nullable
        public final GalleryTags galleryTags;
        public final int imageCount;
        public final boolean complete;
        public final boolean fromManifest;
        public final boolean fromLegacySpiderInfo;

        Item(@Nullable String dirname, @NonNull DownloadInfo downloadInfo,
                @Nullable SpiderInfo spiderInfo, @Nullable GalleryTags galleryTags,
                int imageCount, boolean complete, boolean fromManifest, boolean fromLegacySpiderInfo) {
            this.dirname = dirname;
            this.downloadInfo = downloadInfo;
            this.spiderInfo = spiderInfo;
            this.galleryTags = galleryTags;
            this.imageCount = imageCount;
            this.complete = complete;
            this.fromManifest = fromManifest;
            this.fromLegacySpiderInfo = fromLegacySpiderInfo;
        }
    }
}
