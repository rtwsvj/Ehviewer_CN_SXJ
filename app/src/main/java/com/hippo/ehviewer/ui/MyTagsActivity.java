/*
 * Copyright 2019 Hippo Seven
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

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.WebViewCookieBridge;
import com.hippo.ehviewer.widget.DialogWebChromeClient;
import com.hippo.widget.ProgressView;

public class MyTagsActivity extends ToolbarActivity {

    private static final String TAG = "MyTagsActivity";

    private WebView webView;
    private ProgressView progress;
    private String url;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            url = EhUrl.getMyTagsUrl();

            setContentView(R.layout.activity_my_tags);
            setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
            webView = findViewById(R.id.webview);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebViewClient(new MyTagsWebViewClient());
            webView.setWebChromeClient(new DialogWebChromeClient(this));
            WebViewCookieBridge.injectCookiesFromStore(this, url, true, () -> {
                if (webView != null) {
                    webView.loadUrl(url);
                }
            });
            progress = findViewById(R.id.progress);
        } catch (Throwable t) {
            Log.e(TAG, "WebView/CookieManager init failed", t);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.webview_unavailable_title)
                    .setMessage(R.string.webview_unavailable_message)
                    .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                    .setOnCancelListener(d -> finish())
                    .show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        WebViewCookieBridge.clear();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class MyTagsWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Never load other urls
            return !url.equals(MyTagsActivity.this.url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
        }
    }
}
