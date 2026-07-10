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
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.TrustedWebRequestPolicy;
import com.hippo.ehviewer.client.TrustedWebViewSettings;
import com.hippo.ehviewer.client.WebViewCookieBridge;
import com.hippo.ehviewer.widget.DialogWebChromeClient;
import com.hippo.widget.ProgressView;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import okhttp3.HttpUrl;

public class MyTagsActivity extends ToolbarActivity {

    private static final String TAG = "MyTagsActivity";

    private WebView webView;
    private ProgressView progress;
    private String url;
    private String trustedHost;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            url = EhUrl.getMyTagsUrl();
            HttpUrl parsedUrl = HttpUrl.parse(url);
            if (parsedUrl == null || !TrustedWebRequestPolicy.isAllowedHttpsUrl(
                    url, EhUrl.DOMAIN_E, EhUrl.DOMAIN_EX)) {
                throw new IllegalArgumentException("Invalid my-tags URL");
            }
            trustedHost = parsedUrl.host();

            setContentView(R.layout.activity_my_tags);
            setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
            webView = findViewById(R.id.webview);
            progress = findViewById(R.id.progress);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            TrustedWebViewSettings.isolateLocalContent(settings);
            webView.setWebViewClient(new MyTagsWebViewClient());
            webView.setWebChromeClient(new DialogWebChromeClient(this));
            WebViewCookieBridge.injectCookiesFromStore(this, url, true, () -> {
                if (webView != null) {
                    webView.loadUrl(url);
                }
            });
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

    private boolean isTrustedMyTagsUrl(String candidate) {
        return TrustedWebRequestPolicy.isAllowedHttpsUrl(candidate, trustedHost);
    }

    private WebResourceResponse blockedResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                Collections.singletonMap("Cache-Control", "no-store"),
                new ByteArrayInputStream(new byte[0]));
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

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view,
                WebResourceRequest request) {
            return isTrustedMyTagsUrl(request.getUrl().toString())
                    ? super.shouldInterceptRequest(view, request)
                    : blockedResponse();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !isTrustedMyTagsUrl(request.getUrl().toString());
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return !isTrustedMyTagsUrl(url);
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
