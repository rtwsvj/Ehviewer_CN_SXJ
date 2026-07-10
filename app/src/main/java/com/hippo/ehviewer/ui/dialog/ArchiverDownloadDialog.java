package com.hippo.ehviewer.ui.dialog;

import static com.hippo.ehviewer.client.EhConfig.ARCHIVER_PATH;
import static com.hippo.ehviewer.ui.scene.BaseScene.LENGTH_LONG;
import static com.hippo.ehviewer.ui.scene.BaseScene.LENGTH_SHORT;

import android.app.Dialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.ArchiverData;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.exception.NoHAtHClientException;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.gallery.GalleryProvider2;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.scene.EhCallback;
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene;
import com.hippo.ehviewer.util.GZIPUtils;
import com.hippo.scene.SceneFragment;
import com.hippo.unifile.UniFile;
import com.hippo.util.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ArchiverDownloadDialog implements
        DialogInterface.OnDismissListener, EhClient.Callback<ArchiverData> {
    private static final long MAX_ARCHIVE_DOWNLOAD_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_IMPORT_IMAGE_BYTES = 256L * 1024L * 1024L;
    final private GalleryDetail galleryDetail;
    final private Context context;
    final private GalleryDetailScene detailScene;
    private final DownloadReceiver downloadReceiver;

    private Dialog dialog;

    private TextView currentFunds;
    private TextView originalCost;
    private TextView originalSize;
    private TextView resampleCost;
    private TextView resampleSize;
    private Button resampleDownload;
    private Button originalDownload;

    private ProgressBar progressBar;
    private LinearLayout body;

    private long myDownloadId;


    private ArchiverData data = new ArchiverData();


    public ArchiverDownloadDialog(GalleryDetail galleryDetail, GalleryDetailScene detailScene) {
        this.galleryDetail = galleryDetail;
        this.detailScene = detailScene;
        this.context = detailScene.getEHContext();
        downloadReceiver = new DownloadReceiver(galleryDetail);
    }

    public void showDialog() {
        dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.dialog_archiver_title)
                .setView(R.layout.dialog_archiver)
                .setOnDismissListener(this)
                .show();
        currentFunds = dialog.findViewById(R.id.dialog_archiver_current_funds);
        originalCost = dialog.findViewById(R.id.dialog_archiver_original_cost);
        originalSize = dialog.findViewById(R.id.dialog_archiver_original_size);
        resampleCost = dialog.findViewById(R.id.dialog_archiver_resample_cost);
        resampleSize = dialog.findViewById(R.id.dialog_archiver_resample_size);
        resampleDownload = dialog.findViewById(R.id.dialog_archiver_resample_download);
        originalDownload = dialog.findViewById(R.id.dialog_archiver_original_download);
        progressBar = dialog.findViewById(R.id.dialog_archiver_progress);
        body = dialog.findViewById(R.id.dialog_archiver_body);
        resampleDownload.setOnClickListener(this::onArchiverDownload);
        originalDownload.setOnClickListener(this::onArchiverDownload);
        EhRequest mRequest = new EhRequest().setMethod(EhClient.METHOD_ARCHIVER)
                .setArgs(galleryDetail.archiveUrl, galleryDetail.gid, galleryDetail.token)
                .setCallback(this);
        assert mRequest != null;
        EhApplication.getEhClient(context).execute(mRequest);
    }

    private void onArchiverDownload(View view) {
        try {
            String url = null;
            String dltype = null;
            String dlcheck = null;
            if (view == originalDownload) {
                url = data.originalUrl;
                dltype = "org";
                dlcheck = "Download Original Archive";
            } else if (view == resampleDownload) {
                url = data.resampleUrl;
                dltype = "res";
                dlcheck = "Download Resample Archive";
            }
            if (url == null) {
                return;
            }
            MainActivity activity = detailScene.getActivity2();
            if (null != context && null != activity && galleryDetail != null) {

                EhRequest request = new EhRequest();
                request.setMethod(EhClient.METHOD_DOWNLOAD_ARCHIVER);
                request.setArgs(url, galleryDetail.archiveUrl, dltype, dlcheck);
                request.setCallback(new DownloadArchiverListener(context, activity.getStageId(), detailScene.getTag(), this));
                EhApplication.getEhClient(context).execute(request);
            }
        } finally {
            progressBar.setVisibility(View.VISIBLE);
            body.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {

    }

    @Override
    public void onSuccess(ArchiverData result) {
        data = result;
        String cF;
        if (Settings.getGallerySite() == EhUrl.SITE_E) {
            cF = context.getString(R.string.archiver_dialog_current_funds) + data.funds;
        } else {
            cF = data.funds;
        }

        currentFunds.setText(cF);
        String oC = context.getString(R.string.archiver_dialog_cost, data.originalCost);
        String rC = context.getString(R.string.archiver_dialog_cost, data.resampleCost);
        originalCost.setText(oC);
        resampleCost.setText(rC);
        String oS = context.getString(R.string.archiver_dialog_size, data.originalSize);
        String rS = context.getString(R.string.archiver_dialog_size, data.resampleSize);
        originalSize.setText(oS);
        resampleSize.setText(rS);
        progressBar.setVisibility(View.GONE);
        body.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFailure(Exception e) {

    }

    @Override
    public void onCancel() {

    }


    private class DownloadArchiverListener extends EhCallback<GalleryDetailScene, String> {
        final ArchiverDownloadDialog archiverDownloadDialog;
        final Context context;

        public DownloadArchiverListener(Context context, int stageId, String sceneTag, ArchiverDownloadDialog archiverDownloadDialog) {
            super(context, stageId, sceneTag);
            this.context = context;
            this.archiverDownloadDialog = archiverDownloadDialog;
        }

        @Override
        public void onSuccess(String downloadUrl) {
            if (dialog != null && !dialog.isShowing()) {
                return;
            }
            if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
                Toast.makeText(context,R.string.download_state_failed,Toast.LENGTH_LONG).show();
                return;
            }
            progressBar.setVisibility(View.INVISIBLE);
            body.setVisibility(View.VISIBLE);
            dialog.dismiss();
            showTip(R.string.download_archive_started, LENGTH_SHORT);
//            String fileName = galleryDetail.title.replaceAll("/","");
            String fileName = createFileName(galleryDetail.title, galleryDetail.gid);
            if (fileName.isEmpty()) {
                Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show();
                return;
            }
            Uri downloadUri = Uri.parse(downloadUrl);
            if (!isTrustedArchiveDownloadUri(downloadUri)) {
                Log.w("ArchiverDownloadDialog", "Blocked untrusted archive download URL");
                Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show();
                return;
            }
            DownloadManager.Request request;
            try {
                request = new DownloadManager.Request(downloadUri);
            } catch (IllegalArgumentException e) {
                Log.e("ArchiverDownloadDialog", "Invalid download URL: " + downloadUrl, e);
                Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show();
                return;
            }
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
            request.setAllowedOverRoaming(true);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setTitle(galleryDetail.title);
            request.setDescription(context.getString(R.string.download_archive_started));
            request.setVisibleInDownloadsUi(true);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, ARCHIVER_PATH+fileName + ".zip");
            request.allowScanningByMediaScanner();

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Toast.makeText(context, R.string.download_state_failed, Toast.LENGTH_LONG).show();
                return;
            }

//            downloadManager.query(new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PAUSED));

            myDownloadId = downloadManager.enqueue(request);
            Settings.putArchiverDownloadId(galleryDetail.gid,myDownloadId);
            Settings.putArchiverDownload(myDownloadId,galleryDetail);
            detailScene.bindArchiverProgress(galleryDetail);

            ContextCompat.registerReceiver(context, downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        @Override
        public void onFailure(Exception e) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            if (e instanceof NoHAtHClientException) {
                showTip(R.string.download_h_h_failure_no_hath, LENGTH_LONG);
            } else {
                showTip(R.string.download_archive_failure, LENGTH_LONG);
            }
        }

        @Override
        public void onCancel() {
        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryDetailScene;
        }
    }

    private class DownloadReceiver extends BroadcastReceiver {
        private final static String TAG = "DownloadReceiver";

        private final GalleryDetail galleryDetail;

        public DownloadReceiver(GalleryDetail galleryDetail) {
            this.galleryDetail = galleryDetail;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                long downloadId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                if (myDownloadId != downloadId) {
                    return;
                }
                android.app.DownloadManager downloadManager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                //检查下载状态
                checkDownloadStatus(downloadId, downloadManager);
            }
        }

        private void checkDownloadStatus(long downloadId, android.app.DownloadManager downloadManager) {
            android.app.DownloadManager.Query query = new android.app.DownloadManager.Query();
            query.setFilterById(downloadId);//筛选下载任务，传入任务ID，可变参数
            Cursor c = null;
            try {
                c = downloadManager.query(query);
                if (c != null && c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS));
                    switch (status) {
                        case android.app.DownloadManager.STATUS_PAUSED:
                            Log.i(TAG, ">>>下载暂停");
                            break;
                        case android.app.DownloadManager.STATUS_PENDING:
                            Log.i(TAG, ">>>下载延迟");
                            break;
                        case android.app.DownloadManager.STATUS_SUCCESSFUL:
                            Log.i(TAG, ">>>下载完成");
                            unzipAndImportFile(c);
                            break;
                        case android.app.DownloadManager.STATUS_FAILED:
                            Log.i(TAG, ">>>下载失败");
                            break;
                        case android.app.DownloadManager.STATUS_RUNNING:
                        default:
                            Log.i(TAG, ">>>正在下载");// 此处无法监听到
                            break;
                    }
                }
            } catch (IllegalArgumentException | URISyntaxException | NullPointerException e) {
                Log.e(TAG, e.getMessage(), e);
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }

        private void unzipAndImportFile(Cursor cursor) throws IllegalArgumentException, URISyntaxException {
            String path = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            Uri uri = Uri.parse(path);
            long archiveBytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if (!isArchiveDownloadSizeAllowed(archiveBytes)) {
                Log.w(TAG, "Downloaded archive is empty or exceeds the processing limit");
                return;
            }
            File tempDir = AppConfig.getArchiverDir();
            if (tempDir == null) {
                return;
            }
            long downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
//            String fileName = galleryDetail.title.replaceAll("/","");
            String fileName = createFileName(galleryDetail.title, galleryDetail.gid);
            File extractionDir = new File(tempDir,
                    "archiver-" + downloadId + "-" + UUID.randomUUID());
            String tempFilePath = extractionDir.getPath();
            
            // Handle content:// URI by copying to temp file first
            new Thread(() -> {
                File tempZipFile = new File(tempDir, extractionDir.getName() + ".zip");
                try {
                    // Snapshot both file:// and content:// downloads into private cache before
                    // parsing so another app cannot race or inject extraction inputs.
                    UniFile sourceFile = UniFile.fromUri(context, uri);
                    if (sourceFile == null || !copyArchiveSnapshot(sourceFile, tempZipFile,
                            archiveBytes, MAX_ARCHIVE_DOWNLOAD_BYTES)) {
                        Log.e(TAG, "Failed to snapshot archive into private cache");
                        return;
                    }

                    boolean result = GZIPUtils.UnZipFolder(tempZipFile.getPath(), tempFilePath);
                    if (!result) {
                        return;
                    }
                    if (!importGallery(tempFilePath, downloadId)) {
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(context, R.string.download_state_failed,
                                        Toast.LENGTH_LONG).show());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in unzipAndImportFile", e);
                } finally {
                    if (tempZipFile.exists()) {
                        tempZipFile.delete();
                    }
                    deleteRecursively(extractionDir);
                }
            }).start();
        }

        private boolean importGallery(String tempFilePath, long downloadId) {
            if (tempFilePath.isEmpty() || context == null) {
                return false;
            }

            File tempFile = new File(tempFilePath);

            File[] tempPictures = tempFile.listFiles();
            if (tempPictures == null) {
                return false;
            }
            List<File> importablePictures = new ArrayList<>();
            for (File picture : tempPictures) {
                if (isImportableArchiveImage(picture)) {
                    importablePictures.add(picture);
                }
            }
            if (importablePictures.isEmpty()
                    || (galleryDetail.pages > 0
                    && importablePictures.size() != galleryDetail.pages)) {
                Log.w(TAG, "Archive image count does not match gallery metadata");
                return false;
            }
            File[] pictures = importablePictures.toArray(new File[0]);
            Arrays.sort(pictures, (file1, file2) -> {
                String f1N = file1.getName();
                String f2N = file2.getName();
                return f1N.compareTo(f2N);
            });

            SpiderDen spiderDen = new SpiderDen(galleryDetail);
            spiderDen.setMode(SpiderQueen.MODE_DOWNLOAD);
            UniFile downloadDir = spiderDen.getDownloadDir();

            if (downloadDir == null) {
                return false;
            }
            List<UniFile> stagedFiles = new ArrayList<>();
            List<String> destinationNames = new ArrayList<>();
            try {
                for (int i = 0; i < pictures.length; i++) {
                    File picture = pictures[i];

                    String fileName = picture.getName();
                    String extension = fileName.substring(fileName.lastIndexOf('.'));
                    String newName = SpiderDen.generateImageFilename(i, extension);
                    String stagingName = ".archiver-import-" + UUID.randomUUID() + extension;
                    UniFile stagedFile = downloadDir.createFile(stagingName);
                    if (stagedFile == null) {
                        deleteFiles(stagedFiles);
                        return false;
                    }
                    UniFile sourceFile = UniFile.fromFile(picture);
                    if (!FileUtils.copyFile(sourceFile, stagedFile, false)) {
                        stagedFile.delete();
                        deleteFiles(stagedFiles);
                        return false;
                    }
                    stagedFiles.add(stagedFile);
                    destinationNames.add(newName);
                }

                // Never overwrite an existing gallery page during archive import. SAF cannot
                // provide an atomic multi-file rollback, so fail before the first rename.
                for (String destinationName : destinationNames) {
                    UniFile existing = downloadDir.findFile(destinationName);
                    if (existing != null && existing.exists()) {
                        deleteFiles(stagedFiles);
                        return false;
                    }
                }
                if (!commitStagedFiles(stagedFiles, destinationNames)) {
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in importGallery", e);
                deleteFiles(stagedFiles);
                return false;
            }
            String finalFileName = tempFile.getName();
            new Handler(Looper.getMainLooper()).post(() -> {
                String labelName = context.getString(R.string.download_label_archiver);
                com.hippo.ehviewer.download.DownloadManager manager = EhApplication.getDownloadManager(context);
                manager.addLabel(labelName);
                manager.addDownload(galleryDetail, labelName, DownloadInfo.STATE_FINISH);
                Toast.makeText(context,context.getString(R.string.stat_download_done_line_succeeded, finalFileName),Toast.LENGTH_LONG).show();
                if (downloadReceiver != null) {
                    context.unregisterReceiver(downloadReceiver);
                }
                GalleryInfo info = Settings.getArchiverDownload(downloadId);
                if (info==null){
                    return;
                }
                Settings.deleteArchiverDownloadId(info.gid);
                Settings.deleteArchiverDownload(downloadId);
            });
            return true;
        }
    }

    static boolean isArchiveDownloadSizeAllowed(long bytes) {
        return bytes > 0L && bytes <= MAX_ARCHIVE_DOWNLOAD_BYTES;
    }

    static boolean isImportableArchiveImage(File file) {
        if (file == null || !file.isFile() || file.length() <= 0L
                || file.length() > MAX_IMPORT_IMAGE_BYTES) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.US);
        for (String extension : GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS) {
            if (name.endsWith(extension)) {
                if (!hasExpectedImageSignature(file, extension)) {
                    return false;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), options);
                return options.outWidth > 0 && options.outHeight > 0;
            }
        }
        return false;
    }

    private static boolean hasExpectedImageSignature(File file, String extension) {
        byte[] header = new byte[12];
        int read;
        try (InputStream input = new FileInputStream(file)) {
            read = input.read(header);
        } catch (IOException e) {
            return false;
        }
        if (".jpg".equals(extension) || ".jpeg".equals(extension)) {
            return read >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff;
        }
        if (".png".equals(extension)) {
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
            return read >= png.length && Arrays.equals(png, Arrays.copyOf(header, png.length));
        }
        if (".gif".equals(extension)) {
            return read >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == '8' && (header[4] == '7' || header[4] == '9')
                    && header[5] == 'a';
        }
        if (".webp".equals(extension)) {
            return read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                    && header[3] == 'F' && header[8] == 'W' && header[9] == 'E'
                    && header[10] == 'B' && header[11] == 'P';
        }
        return false;
    }

    static boolean copyArchiveSnapshot(UniFile source, File destination, long expectedBytes,
            long maxBytes) {
        if (source == null || destination == null || expectedBytes <= 0L || maxBytes <= 0L
                || expectedBytes > maxBytes) {
            return false;
        }
        boolean success = false;
        try (InputStream input = new BufferedInputStream(source.openInputStream());
                OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[32 * 1024];
            long copied = 0L;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > maxBytes - copied) {
                    return false;
                }
                output.write(buffer, 0, count);
                copied += count;
            }
            output.flush();
            success = copied == expectedBytes;
            return success;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (!success && destination.exists()) {
                destination.delete();
            }
        }
    }

    static boolean commitStagedFiles(List<UniFile> stagedFiles, List<String> destinationNames) {
        if (stagedFiles.size() != destinationNames.size()) {
            deleteFiles(stagedFiles);
            return false;
        }
        for (int i = 0; i < stagedFiles.size(); i++) {
            if (!stagedFiles.get(i).renameTo(destinationNames.get(i))) {
                // RawFile and TreeDocumentFile update their own target after a successful rename,
                // so deleting the complete list also rolls back pages already committed.
                deleteFiles(stagedFiles);
                return false;
            }
        }
        return true;
    }

    private static void deleteFiles(List<UniFile> files) {
        for (UniFile file : files) {
            if (file != null) {
                file.delete();
            }
        }
    }

    /**
     * Linux 单路径段 NAME_MAX 为 255 字节（UTF-8）；归档保存为 {@code name + ".zip"}，基名须预留后缀长度。
     */
    private static final int MAX_ARCHIVER_BASENAME_UTF8_BYTES =
            255 - ".zip".getBytes(StandardCharsets.UTF_8).length;

    /**
     * 将字符串截断为不超过 maxBytes 个 UTF-8 字节，不在多字节字符或代理对中间切开。
     */
    private static String truncateUtf8ToMaxBytes(String s, int maxBytes) {
        if (s == null || s.isEmpty() || maxBytes <= 0) {
            return s == null ? "" : s;
        }
        int byteCount = 0;
        int cutCharEnd = 0;
        for (int i = 0; i < s.length(); ) {
            char ch = s.charAt(i);
            int charUtf8Bytes;
            int charWidth = 1;
            if (ch <= 0x7F) {
                charUtf8Bytes = 1;
            } else if (ch <= 0x7FF) {
                charUtf8Bytes = 2;
            } else if (Character.isHighSurrogate(ch)) {
                charUtf8Bytes = 4;
                charWidth = 2;
                if (i + 1 >= s.length()) {
                    break;
                }
            } else {
                charUtf8Bytes = 3;
            }
            if (byteCount + charUtf8Bytes > maxBytes) {
                break;
            }
            byteCount += charUtf8Bytes;
            i += charWidth;
            cutCharEnd = i;
        }
        return cutCharEnd < s.length() ? s.substring(0, cutCharEnd) : s;
    }

    /**
     * 统一净化归档文件名，避免 DownloadManager 因非法路径或文件名过长抛错。
     */
    private static String createFileName(String name, long gid) {
        String result = name == null ? "" : com.hippo.lib.yorozuya.FileUtils.sanitizeFilename(name);
        result = truncateUtf8ToMaxBytes(result, MAX_ARCHIVER_BASENAME_UTF8_BYTES);
        if (result.isEmpty()) {
            result = gid > 0 ? "archiver_" + gid : "archiver";
        }
        return result;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    static boolean isTrustedArchiveDownloadUri(Uri uri) {
        if (uri == null || uri.getUserInfo() != null
                || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        return host.equals(EhUrl.DOMAIN_E) || host.endsWith("." + EhUrl.DOMAIN_E)
                || host.equals(EhUrl.DOMAIN_EX) || host.endsWith("." + EhUrl.DOMAIN_EX);
    }
}
