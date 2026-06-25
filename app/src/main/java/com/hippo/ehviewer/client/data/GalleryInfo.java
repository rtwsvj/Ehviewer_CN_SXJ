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

package com.hippo.ehviewer.client.data;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.util.JsonUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

public class GalleryInfo implements Parcelable {

    /**
     * ISO 639-1
     */
    @SuppressWarnings("unused")
    public static final String S_LANG_JA = "JA";
    public static final String S_LANG_EN = "EN";
    public static final String S_LANG_ZH = "ZH";
    public static final String S_LANG_NL = "NL";
    public static final String S_LANG_FR = "FR";
    public static final String S_LANG_DE = "DE";
    public static final String S_LANG_HU = "HU";
    public static final String S_LANG_IT = "IT";
    public static final String S_LANG_KO = "KO";
    public static final String S_LANG_PL = "PL";
    public static final String S_LANG_PT = "PT";
    public static final String S_LANG_RU = "RU";
    public static final String S_LANG_ES = "ES";
    public static final String S_LANG_TH = "TH";
    public static final String S_LANG_VI = "VI";

    public static final String[] S_LANGS = {
            S_LANG_EN,
            S_LANG_ZH,
            S_LANG_ES,
            S_LANG_KO,
            S_LANG_RU,
            S_LANG_FR,
            S_LANG_PT,
            S_LANG_TH,
            S_LANG_DE,
            S_LANG_IT,
            S_LANG_VI,
            S_LANG_PL,
            S_LANG_HU,
            S_LANG_NL,
    };

    public static final Pattern[] S_LANG_PATTERNS = {
            Pattern.compile("[(\\[]eng(?:lish)?[)\\]]|英訳", Pattern.CASE_INSENSITIVE),
            // [(（\[]ch(?:inese)?[)）\]]|[汉漢]化|中[国國][语語]|中文|中国翻訳
            Pattern.compile("[(\uFF08\\[]ch(?:inese)?[)\uFF09\\]]|[汉漢]化|中[国國][语語]|中文|中国翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]spanish[)\\]]|[(\\[]Español[)\\]]|スペイン翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]korean?[)\\]]|韓国翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]rus(?:sian)?[)\\]]|ロシア翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]fr(?:ench)?[)\\]]|フランス翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]portuguese|ポルトガル翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]thai(?: ภาษาไทย)?[)\\]]|แปลไทย|タイ翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]german[)\\]]|ドイツ翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]italiano?[)\\]]|イタリア翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]vietnamese(?: Tiếng Việt)?[)\\]]|ベトナム翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]polish[)\\]]|ポーランド翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]hun(?:garian)?[)\\]]|ハンガリー翻訳", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[(\\[]dutch[)\\]]|オランダ翻訳", Pattern.CASE_INSENSITIVE),
    };

    public static final String[] S_LANG_TAGS = {
            "language:english",
            "language:chinese",
            "language:spanish",
            "language:korean",
            "language:russian",
            "language:french",
            "language:portuguese",
            "language:thai",
            "language:german",
            "language:italian",
            "language:vietnamese",
            "language:polish",
            "language:hungarian",
            "language:dutch",
    };

    public long gid;
    public String token;
    public String title;
    public String titleJpn;
    public String thumb;
    public int category;
    public String posted;
    public String uploader;
    public float rating;
    public boolean rated;
    @Nullable
    public String[] simpleTags;
    public int pages;

    public int thumbWidth;
    public int thumbHeight;

    public int spanSize;
    public int spanIndex;
    public int spanGroupIndex;
    @Nullable
    public ArrayList<String> tgList;

    /**
     * language from title
     */
    public String simpleLanguage;

    public int favoriteSlot = -2;
    public String favoriteName;

    public String toCSV() {
        return gid + "," +
                token + "," +
                title + "," +
                titleJpn + "," +
                thumb + "," +
                category + "," +
                posted + "," +
                uploader + "," +
                rating + "," +
                rated + "," +
                simpleLanguage + "," +
                Arrays.toString(simpleTags) + "," +
                thumbWidth + "," +
                thumbHeight + "," +
                spanSize + "," +
                spanIndex + "," +
                spanGroupIndex + "," +
                favoriteSlot + "," +
                favoriteName + "," +
                pages + "\n";
    }

    public static GalleryInfo fromCSV(String csv) {
        String[] values = csv.split(",");
        if (values.length < 20) {
            return null;
        }
        GalleryInfo gi = new GalleryInfo();
        try {
            gi.gid = Long.parseLong(values[0]);
            gi.token = values[1];
            gi.title = values[2];
            gi.titleJpn = values[3];
            gi.thumb = values[4];
            gi.category = Integer.parseInt(values[5]);
            gi.posted = values[6];
            gi.uploader = values[7];
            gi.rating = Float.parseFloat(values[8]);
            gi.rated = Boolean.parseBoolean(values[9]);
            gi.simpleLanguage = values[10];
            gi.simpleTags = values[11].substring(1, values[11].length() - 1).split(", ");
            gi.thumbWidth = Integer.parseInt(values[12]);
            gi.thumbHeight = Integer.parseInt(values[13]);
            gi.spanSize = Integer.parseInt(values[14]);
            gi.spanIndex = Integer.parseInt(values[15]);
            gi.spanGroupIndex = Integer.parseInt(values[16]);
            gi.favoriteSlot = Integer.parseInt(values[17]);
            gi.favoriteName = values[18];
            gi.pages = Integer.parseInt(values[19].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return gi;
    }

    public final void generateSLang() {
        if (simpleTags != null) {
            generateSLangFromTags();
        }
        if (simpleLanguage == null && title != null) {
            generateSLangFromTitle();
        }
    }

    private void generateSLangFromTags() {
        if(simpleTags==null){
            return;
        }
        for (String tag : simpleTags) {
            for (int i = 0; i < S_LANGS.length; i++) {
                if (S_LANG_TAGS[i].equals(tag)) {
                    simpleLanguage = S_LANGS[i];
                    return;
                }
            }
        }
    }

    private void generateSLangFromTitle() {
        for (int i = 0; i < S_LANGS.length; i++) {
            if (S_LANG_PATTERNS[i].matcher(title).find()) {
                simpleLanguage = S_LANGS[i];
                return;
            }
        }
        simpleLanguage = null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.gid);
        dest.writeString(this.token);
        dest.writeString(this.title);
        dest.writeString(this.titleJpn);
        dest.writeString(this.thumb);
        dest.writeInt(this.category);
        dest.writeString(this.posted);
        dest.writeString(this.uploader);
        dest.writeFloat(this.rating);
        dest.writeByte(this.rated ? (byte) 1 : (byte) 0);
        dest.writeString(this.simpleLanguage);
        dest.writeStringArray(this.simpleTags);
        dest.writeInt(this.thumbWidth);
        dest.writeInt(this.thumbHeight);
        dest.writeInt(this.spanSize);
        dest.writeInt(this.spanIndex);
        dest.writeInt(this.spanGroupIndex);
        dest.writeInt(this.favoriteSlot);
        dest.writeString(this.favoriteName);
        dest.writeList(this.tgList);
    }

    public GalleryInfo() {
    }

    protected GalleryInfo(Parcel in) {
        this.gid = in.readLong();
        this.token = in.readString();
        this.title = in.readString();
        this.titleJpn = in.readString();
        this.thumb = in.readString();
        this.category = in.readInt();
        this.posted = in.readString();
        this.uploader = in.readString();
        this.rating = in.readFloat();
        this.rated = in.readByte() != 0;
        this.simpleLanguage = in.readString();
        this.simpleTags = in.createStringArray();
        this.thumbWidth = in.readInt();
        this.thumbHeight = in.readInt();
        this.spanSize = in.readInt();
        this.spanIndex = in.readInt();
        this.spanGroupIndex = in.readInt();
        this.favoriteSlot = in.readInt();
        this.favoriteName = in.readString();
        this.tgList = in.readArrayList(String.class.getClassLoader());
    }

    public static final Creator<GalleryInfo> CREATOR = new Creator<>() {

        @Override
        public GalleryInfo createFromParcel(Parcel source) {
            return new GalleryInfo(source);
        }

        @Override
        public GalleryInfo[] newArray(int size) {
            return new GalleryInfo[size];
        }
    };

    public DownloadInfo getDownloadInfo(@Nullable DownloadInfo info) {
        DownloadInfo i = new DownloadInfo();
        i.gid = gid;
        i.token = token;
        i.title = title;
        i.titleJpn = titleJpn;
        i.thumb = thumb;
        i.category = category;
        i.posted = posted;
        i.uploader = uploader;
        i.rating = rating;
        i.rated = rated;
        i.simpleLanguage = simpleLanguage;
        i.simpleTags = simpleTags;
        i.thumbWidth = thumbWidth;
        i.thumbHeight = thumbHeight;
        i.spanSize = spanSize;
        i.spanIndex = spanIndex;
        i.spanGroupIndex = spanGroupIndex;
        i.favoriteSlot = favoriteSlot;
        i.favoriteName = favoriteName;
        i.tgList = tgList;
        if (info != null) {
            i.state = info.state;
            i.legacy = info.legacy;
            i.time = info.time;
            i.label = info.label;
        }

        return i;
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        JsonUtils.put(jsonObject, "gid", gid);
        JsonUtils.put(jsonObject, "token", token);
        JsonUtils.put(jsonObject, "title", title);
        JsonUtils.put(jsonObject, "titleJpn", titleJpn);
        JsonUtils.put(jsonObject, "thumb", thumb);
        JsonUtils.put(jsonObject, "category", category);
        JsonUtils.put(jsonObject, "posted", posted);
        JsonUtils.put(jsonObject, "uploader", uploader);
        JsonUtils.put(jsonObject, "rating", (Object) Float.valueOf(rating));
        JsonUtils.put(jsonObject, "rated", rated);
        JsonUtils.put(jsonObject, "simpleLanguage", simpleLanguage);
        if (simpleTags != null) {
            JsonUtils.put(jsonObject, "simpleTags", new JSONArray(Arrays.asList(simpleTags)));
        }
        JsonUtils.put(jsonObject, "thumbHeight", thumbHeight);
        JsonUtils.put(jsonObject, "thumbWidth", thumbWidth);
        JsonUtils.put(jsonObject, "spanSize", spanSize);
        JsonUtils.put(jsonObject, "spanIndex", spanIndex);
        JsonUtils.put(jsonObject, "spanGroupIndex", spanGroupIndex);
        JsonUtils.put(jsonObject, "favoriteSlot", favoriteSlot);
        JsonUtils.put(jsonObject, "favoriteName", favoriteName);
        JSONArray tagListJson = new JSONArray();
        if (tgList != null) {
            for (String tag : tgList) {
                tagListJson.put(tag);
            }
        }
        JsonUtils.put(jsonObject, "tgList", tagListJson);
        JsonUtils.put(jsonObject, "pages", pages);
        return jsonObject;
    }

    public static GalleryInfo galleryInfoFromJson(JSONObject object) {
        GalleryInfo galleryInfo = new GalleryInfo();
        galleryInfo.posted = JsonUtils.optStringOrNull(object, "posted");
        galleryInfo.category = object.optInt("category");
        galleryInfo.favoriteName = JsonUtils.optStringOrNull(object, "favoriteName");
        galleryInfo.favoriteSlot = object.optInt("favoriteSlot");
        galleryInfo.gid = object.optLong("gid");
        galleryInfo.pages = object.optInt("pages");
        galleryInfo.rated = object.optBoolean("rated");
        galleryInfo.rating = (float) object.optDouble("rating", 0);
        galleryInfo.simpleLanguage = JsonUtils.optStringOrNull(object, "simpleLanguage");
        JSONArray simpleTagsArr = object.optJSONArray("simpleTags");
        if (simpleTagsArr != null) {
            try {
                galleryInfo.simpleTags = JsonUtils.toStringList(simpleTagsArr).toArray(new String[0]);
            } catch (ClassCastException ignore) {
            }
        }
        galleryInfo.spanGroupIndex = object.optInt("spanGroupIndex");
        galleryInfo.spanIndex = object.optInt("spanIndex");
        galleryInfo.spanSize = object.optInt("spanSize");
        JSONArray tgArray = object.optJSONArray("tgList");
        if (tgArray != null) {
            try {
                if (tgArray.length() > 0 && tgArray.opt(0) instanceof JSONArray) {
                    galleryInfo.tgList = new ArrayList<>(JsonUtils.toStringList((JSONArray) tgArray.opt(0)));
                } else {
                    galleryInfo.tgList = new ArrayList<>(JsonUtils.toStringList(tgArray));
                }
            } catch (ClassCastException ignore) {
            }
        }

        galleryInfo.thumb = JsonUtils.optStringOrNull(object, "thumb");
        galleryInfo.thumbHeight = object.optInt("thumbHeight");
        galleryInfo.thumbWidth = object.optInt("thumbWidth");
        galleryInfo.title = JsonUtils.optStringOrNull(object, "title");
        galleryInfo.titleJpn = JsonUtils.optStringOrNull(object, "titleJpn");
        galleryInfo.token = JsonUtils.optStringOrNull(object, "token");
        galleryInfo.uploader = JsonUtils.optStringOrNull(object, "uploader");
        return galleryInfo;
    }
}
