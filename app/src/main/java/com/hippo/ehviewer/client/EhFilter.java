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

package com.hippo.ehviewer.client;

import android.util.Log;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.Filter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class EhFilter {

    private static final String TAG = EhFilter.class.getSimpleName();

    public static final int MODE_TITLE = 0;
    public static final int MODE_UPLOADER = 1;
    public static final int MODE_TAG = 2;
    public static final int MODE_TAG_NAMESPACE = 3;

    private final List<Filter> mTitleFilterList = new ArrayList<>();
    private final List<Filter> mUploaderFilterList = new ArrayList<>();
    private final List<Filter> mTagFilterList = new ArrayList<>();
    private final List<Filter> mTagNamespaceFilterList = new ArrayList<>();
    private final Set<String> mEnabledUploaderFilters = new HashSet<>();
    private final Set<String> mEnabledPlainTagFilters = new HashSet<>();
    private final Set<String> mEnabledAllTagNames = new HashSet<>();
    private final Set<String> mEnabledFullTagFilters = new HashSet<>();
    private final Set<String> mEnabledTagNamespaceFilters = new HashSet<>();

    private static EhFilter sInstance;

    public static EhFilter getInstance() {
        if (sInstance == null) {
            sInstance = new EhFilter();
        }
        return sInstance;
    }

    private EhFilter() {
        List<Filter> list = EhDB.getAllFilter();
        for (int i = 0, n = list.size(); i < n; i++) {
            Filter filter = list.get(i);
            switch (filter.mode) {
                case MODE_TITLE:
                    filter.text = filter.text.toLowerCase();
                    mTitleFilterList.add(filter);
                    break;
                case MODE_UPLOADER:
                    mUploaderFilterList.add(filter);
                    break;
                case MODE_TAG:
                    filter.text = filter.text.toLowerCase();
                    mTagFilterList.add(filter);
                    break;
                case MODE_TAG_NAMESPACE:
                    filter.text = filter.text.toLowerCase();
                    mTagNamespaceFilterList.add(filter);
                    break;
                default:
                    Log.d(TAG, "Unknown mode: " + filter.mode);
                    break;
            }
        }
        rebuildFilterIndexes();
    }

    public List<Filter> getTitleFilterList() {
        return mTitleFilterList;
    }

    public List<Filter> getUploaderFilterList() {
        return mUploaderFilterList;
    }

    public List<Filter> getTagFilterList() {
        return mTagFilterList;
    }

    public List<Filter> getTagNamespaceFilterList() {
        return mTagNamespaceFilterList;
    }

    public synchronized void addFilter(Filter filter) {
        // enable filter by default before it is added to database
        filter.enable = true;
        EhDB.addFilter(filter);

        switch (filter.mode) {
            case MODE_TITLE:
                filter.text = filter.text.toLowerCase();
                mTitleFilterList.add(filter);
                break;
            case MODE_UPLOADER:
                mUploaderFilterList.add(filter);
                break;
            case MODE_TAG:
                filter.text = filter.text.toLowerCase();
                mTagFilterList.add(filter);
                break;
            case MODE_TAG_NAMESPACE:
                filter.text = filter.text.toLowerCase();
                mTagNamespaceFilterList.add(filter);
                break;
            default:
                Log.d(TAG, "Unknown mode: " + filter.mode);
                break;
        }
        rebuildFilterIndexes();
    }

    public synchronized void triggerFilter(Filter filter) {
        EhDB.triggerFilter(filter);
        rebuildFilterIndexes();
    }

    public synchronized void deleteFilter(Filter filter) {
        EhDB.deleteFilter(filter);

        switch (filter.mode) {
            case MODE_TITLE:
                mTitleFilterList.remove(filter);
                break;
            case MODE_UPLOADER:
                mUploaderFilterList.remove(filter);
                break;
            case MODE_TAG:
                mTagFilterList.remove(filter);
                break;
            case MODE_TAG_NAMESPACE:
                mTagNamespaceFilterList.remove(filter);
                break;
            default:
                Log.d(TAG, "Unknown mode: " + filter.mode);
                break;
        }
        rebuildFilterIndexes();
    }

    private void rebuildFilterIndexes() {
        mEnabledUploaderFilters.clear();
        mEnabledPlainTagFilters.clear();
        mEnabledAllTagNames.clear();
        mEnabledFullTagFilters.clear();
        mEnabledTagNamespaceFilters.clear();

        for (Filter filter : mUploaderFilterList) {
            if (isEnabled(filter) && filter.text != null) {
                mEnabledUploaderFilters.add(filter.text);
            }
        }
        for (Filter filter : mTagFilterList) {
            if (!isEnabled(filter) || filter.text == null) {
                continue;
            }
            int index = filter.text.indexOf(':');
            if (index >= 0) {
                mEnabledFullTagFilters.add(filter.text);
                if (index + 1 < filter.text.length()) {
                    mEnabledAllTagNames.add(filter.text.substring(index + 1));
                }
            } else {
                mEnabledPlainTagFilters.add(filter.text);
                mEnabledAllTagNames.add(filter.text);
            }
        }
        for (Filter filter : mTagNamespaceFilterList) {
            if (isEnabled(filter) && filter.text != null) {
                mEnabledTagNamespaceFilters.add(filter.text);
            }
        }
    }

    private static boolean isEnabled(Filter filter) {
        return filter != null && Boolean.TRUE.equals(filter.enable);
    }

    public synchronized boolean needTags() {
        return 0 != mTagFilterList.size() || 0 != mTagNamespaceFilterList.size();
    }

    public synchronized boolean filterTitle(GalleryInfo info) {
        if (null == info) {
            return false;
        }

        // Title
        String title = info.title;
        List<Filter> filters = mTitleFilterList;
        if (null != title && filters.size() > 0) {
            String lowerTitle = title.toLowerCase(Locale.ROOT);
            for (int i = 0, n = filters.size(); i < n; i++) {
                Filter filter = filters.get(i);
                if (isEnabled(filter) && filter.text != null && lowerTitle.contains(filter.text)) {
                    return false;
                }
            }
        }

        return true;
    }

    public synchronized boolean filterUploader(GalleryInfo info) {
        if (null == info) {
            return false;
        }

        // Uploader
        String uploader = info.uploader;
        if (null != uploader && mEnabledUploaderFilters.contains(uploader)) {
            return false;
        }

        return true;
    }

    public synchronized boolean filterTag(GalleryInfo info) {
        if (null == info) {
            return false;
        }

        // Tag
        String[] tags = info.simpleTags;
        if (null != tags && (!mEnabledPlainTagFilters.isEmpty() ||
                !mEnabledAllTagNames.isEmpty() || !mEnabledFullTagFilters.isEmpty())) {
            for (String tag: tags) {
                if (tag == null) {
                    continue;
                }
                int index = tag.indexOf(':');
                String tagName = index >= 0 ? tag.substring(index + 1) : tag;
                if (index >= 0) {
                    if (mEnabledPlainTagFilters.contains(tagName) || mEnabledFullTagFilters.contains(tag)) {
                        return false;
                    }
                } else if (mEnabledAllTagNames.contains(tag)) {
                    return false;
                }
            }
        }

        return true;
    }

    public synchronized boolean filterTagNamespace(GalleryInfo info) {
        if (null == info) {
            return false;
        }

        String[] tags = info.simpleTags;
        if (null != tags && !mEnabledTagNamespaceFilters.isEmpty()) {
            for (String tag: tags) {
                if (tag == null) {
                    continue;
                }
                int index = tag.indexOf(':');
                if (index >= 0 && mEnabledTagNamespaceFilters.contains(tag.substring(0, index))) {
                    return false;
                }
            }
        }

        return true;
    }
}
