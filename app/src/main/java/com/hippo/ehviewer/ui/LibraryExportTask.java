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

package com.hippo.ehviewer.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.library.LibraryExporter;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class LibraryExportTask extends AsyncTask<Void, Void, LibraryExportTask.Result> {

    private static final int MODE_LIBRARY_ZIP = 0;
    private static final int MODE_CBZ_FILES = 1;
    private static final int MODE_SINGLE_CBZ = 2;

    private final WeakReference<Context> mContext;
    private final List<DownloadInfo> mDownloads;
    private final int mMode;
    private ProgressDialog mProgressDialog;

    private LibraryExportTask(Context context, List<DownloadInfo> downloads, int mode) {
        mContext = new WeakReference<>(context);
        mDownloads = new ArrayList<>(downloads);
        mMode = mode;
    }

    public static void exportLibraryZip(Context context, List<DownloadInfo> downloads) {
        new LibraryExportTask(context, downloads, MODE_LIBRARY_ZIP).execute();
    }

    public static void exportCbzFiles(Context context, List<DownloadInfo> downloads) {
        new LibraryExportTask(context, downloads, MODE_CBZ_FILES).execute();
    }

    public static void exportSingleCbz(Context context, DownloadInfo download) {
        List<DownloadInfo> downloads = new ArrayList<>(1);
        downloads.add(download);
        new LibraryExportTask(context, downloads, MODE_SINGLE_CBZ).execute();
    }

    @Override
    protected void onPreExecute() {
        Context context = mContext.get();
        if (context == null) {
            return;
        }
        mProgressDialog = new ProgressDialog(context);
        mProgressDialog.setTitle(R.string.settings_download_export_library_running);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    @Override
    protected Result doInBackground(Void... voids) {
        UniFile dir = Settings.getDownloadLocation();
        if (dir == null || !dir.isDirectory()) {
            return Result.failed();
        }

        List<DownloadInfo> finishedDownloads = getFinishedDownloads(mDownloads);
        if (finishedDownloads.isEmpty()) {
            return Result.noItems();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        String timestamp = sdf.format(new Date());
        try {
            if (mMode == MODE_LIBRARY_ZIP) {
                String filename = "ehviewer-library-" + timestamp + ".zip";
                UniFile outputFile = dir.createFile(filename);
                if (outputFile == null) {
                    return Result.failed();
                }
                int exported = LibraryExporter.exportLibraryZip(finishedDownloads, outputFile);
                return Result.exported(exported, outputFile.getUri().toString());
            } else if (mMode == MODE_CBZ_FILES) {
                String dirname = "ehviewer-cbz-" + timestamp;
                UniFile outputDir = dir.createDirectory(dirname);
                if (outputDir == null) {
                    return Result.failed();
                }
                int exported = LibraryExporter.exportCbzFiles(finishedDownloads, outputDir);
                return Result.exported(exported, outputDir.getUri().toString());
            } else {
                UniFile outputFile = LibraryExporter.exportGalleryCbzToDirectory(
                        finishedDownloads.get(0), dir);
                if (outputFile == null) {
                    return Result.failed();
                }
                return Result.exported(1, outputFile.getUri().toString());
            }
        } catch (IOException e) {
            return Result.failed();
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        Context context = mContext.get();
        if (mProgressDialog != null) {
            if (context != null) {
                try {
                    if (mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                    }
                } catch (IllegalArgumentException e) {
                    ExceptionUtils.throwIfFatal(e);
                }
            }
            mProgressDialog = null;
        }
        if (context == null) {
            return;
        }
        if (result.exported > 0 && result.output != null) {
            Toast.makeText(context,
                    context.getString(R.string.settings_download_export_library_done,
                            result.exported, result.output),
                    Toast.LENGTH_LONG).show();
        } else if (result.noItems) {
            Toast.makeText(context,
                    R.string.settings_download_export_no_items,
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context,
                    R.string.settings_download_export_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static List<DownloadInfo> getFinishedDownloads(List<DownloadInfo> downloads) {
        List<DownloadInfo> finishedDownloads = new ArrayList<>();
        for (DownloadInfo info : downloads) {
            if (info.state == DownloadInfo.STATE_FINISH) {
                finishedDownloads.add(info);
            }
        }
        return finishedDownloads;
    }

    static final class Result {

        final int exported;
        final String output;
        final boolean noItems;

        private Result(int exported, String output, boolean noItems) {
            this.exported = exported;
            this.output = output;
            this.noItems = noItems;
        }

        static Result exported(int exported, String output) {
            if (exported <= 0) {
                return noItems();
            }
            return new Result(exported, output, false);
        }

        static Result noItems() {
            return new Result(0, null, true);
        }

        static Result failed() {
            return new Result(0, null, false);
        }
    }
}
