package com.hippo.ehviewer.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser;
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser;
import com.hippo.ehviewer.dao.LocalFavoriteInfo;
import com.hippo.ehviewer.ui.scene.ProgressScene;
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.scene.Announcer;
import com.hippo.util.ExceptionUtils;


public class ClipboardUtil {

    /**
     * 实现文本复制功能
     *
     * @param galleryInfo 复制的对象
     */
    public static void copy(GalleryInfo galleryInfo) {
        //对象转换
        String content = reduceString(galleryInfo);

        clearClipboard();
        if (!TextUtils.isEmpty(content)) {
            // 得到剪贴板管理器
            ClipboardManager cmb = (ClipboardManager) EhApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
            cmb.setText(content.trim());
            // 创建一个剪贴数据集，包含一个普通文本数据条目（需要复制的数据）
            ClipData clipData = ClipData.newPlainText(null, content);
            // 把数据集设置（复制）到剪贴板
            cmb.setPrimaryClip(clipData);
        }
    }

    /**
     * 实现文本复制功能
     *
     * @param text 复制的文本
     */
    public static void copyText(String text) {

        clearClipboard();
        if (!TextUtils.isEmpty(text)) {
            // 得到剪贴板管理器
            ClipboardManager cmb = (ClipboardManager) EhApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建一个剪贴数据集，包含一个普通文本数据条目（需要复制的数据）
            ClipData clipData = ClipData.newPlainText(null, text);
            // 把数据集设置（复制）到剪贴板
            cmb.setPrimaryClip(clipData);
        }
    }


    /**
     * 从剪切板获取数据
     * @return
     */
    public static GalleryInfo getGalleryInfoFromClip() {

        String compressString = getClipContent();

        String galleryString = GZIPUtils.uncompress(compressString);


        clearClipboard();
        return decodeGalleryInfo(galleryString);
    }

    private static String reduceString(GalleryInfo galleryInfo){
        LocalFavoriteInfo localFavoriteInfo = (LocalFavoriteInfo) galleryInfo;
        return GZIPUtils.compress(encodeFavorite(localFavoriteInfo));
    }

    /**
     * Serializes a favorite into the clipboard JSON shape exchanged between Ehviewer instances:
     * a flat object with the gid/token/title/.../simpleLanguage keys (the same names
     * {@link GalleryInfo#galleryInfoFromJson} reads back). The volatile/derived fields that the
     * previous fastjson reflection produced and then stripped are simply never written here.
     * Package-private + static for JVM round-trip testing.
     */
    static String encodeFavorite(LocalFavoriteInfo localFavoriteInfo) {
        JSONObject favoriteJson = new JSONObject();
        JsonUtils.put(favoriteJson, "gid", localFavoriteInfo.gid);
        JsonUtils.put(favoriteJson, "token", localFavoriteInfo.token);
        JsonUtils.put(favoriteJson, "title", localFavoriteInfo.title);
        JsonUtils.put(favoriteJson, "titleJpn", localFavoriteInfo.titleJpn);
        JsonUtils.put(favoriteJson, "thumb", localFavoriteInfo.thumb);
        JsonUtils.put(favoriteJson, "category", localFavoriteInfo.category);
        JsonUtils.put(favoriteJson, "posted", localFavoriteInfo.posted);
        JsonUtils.put(favoriteJson, "uploader", localFavoriteInfo.uploader);
        JsonUtils.put(favoriteJson, "rating", (Object) Float.valueOf(localFavoriteInfo.rating));
        JsonUtils.put(favoriteJson, "simpleLanguage", localFavoriteInfo.simpleLanguage);
        return favoriteJson.toString();
    }

    /**
     * Parses the clipboard JSON produced by {@link #encodeFavorite} (or by an older fastjson build)
     * back into a {@link GalleryInfo}. {@link GalleryInfo#galleryInfoFromJson} supplies the remaining
     * defaults (favoriteSlot=-2, pages=0, rated=false, ...), matching the previous behaviour of
     * merging defaultInfo before binding. Returns {@code null} for empty/malformed input.
     * Package-private + static for JVM round-trip testing.
     */
    @Nullable
    static GalleryInfo decodeGalleryInfo(@Nullable String galleryString) {
        if (galleryString == null || galleryString.isEmpty()) {
            return null;
        }
        JSONObject object;
        try {
            object = new JSONObject(galleryString);
        } catch (JSONException e) {
            return null;
        }
        return GalleryInfo.galleryInfoFromJson(object);
    }

    /**
     * 清空剪贴板内容
     */
    private static void clearClipboard() {
        ClipboardManager manager = (ClipboardManager) EhApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            try {
                manager.setPrimaryClip(manager.getPrimaryClip());
                manager.setText(null);
            } catch (Exception e) {
                ExceptionUtils.getReadableString(e);
            }
        }
    }
    /**
     * 获取系统剪贴板内容
     */
    private static String getClipContent() {

        ClipboardManager manager = (ClipboardManager) EhApplication.getInstance().getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            if (manager.hasPrimaryClip() && manager.getPrimaryClip().getItemCount() > 0) {
                CharSequence addedText = manager.getPrimaryClip().getItemAt(0).getText();
                String addedTextString = String.valueOf(addedText);
                if (!TextUtils.isEmpty(addedTextString)) {
                    return addedTextString;
                }
            }
        }
        return "";
    }

    @Nullable
    public static Announcer createAnnouncerFromClipboardUrl(String url) {
        GalleryDetailUrlParser.Result result1 = GalleryDetailUrlParser.parse(url, false);
        if (result1 != null) {
            Bundle args = new Bundle();
            args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GID_TOKEN);
            args.putLong(GalleryDetailScene.KEY_GID, result1.gid);
            args.putString(GalleryDetailScene.KEY_TOKEN, result1.token);
            return new Announcer(GalleryDetailScene.class).setArgs(args);
        }

        GalleryPageUrlParser.Result result2 = GalleryPageUrlParser.parse(url, false);
        if (result2 != null) {
            Bundle args = new Bundle();
            args.putString(ProgressScene.KEY_ACTION, ProgressScene.ACTION_GALLERY_TOKEN);
            args.putLong(ProgressScene.KEY_GID, result2.gid);
            args.putString(ProgressScene.KEY_PTOKEN, result2.pToken);
            args.putInt(ProgressScene.KEY_PAGE, result2.page);
            return new Announcer(ProgressScene.class).setArgs(args);
        }

        return null;
    }
}
