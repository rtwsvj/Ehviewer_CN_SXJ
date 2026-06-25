/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.ehviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.BlackList;
import com.hippo.ehviewer.dao.LocalFavoriteInfo;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Verifies the synchronous DB cores used by the {@code ...Async} wrappers return identical
 * data, and that the async wrappers deliver that same data on the main thread. This pins the
 * behaviour-preservation contract of the off-main-thread DB work (anti-ANR, DB-2).
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class EhDBAsyncTest {

    // ---- synchronous cores (deterministic, no threading) ----

    @Test
    public void getAllBlackListReturnsInsertedRows() {
        resetDb();

        BlackList a = new BlackList();
        a.badgayname = "alice";
        a.add_time = "1";
        EhDB.insertBlackList(a);

        BlackList b = new BlackList();
        b.badgayname = "bob";
        b.add_time = "2";
        EhDB.insertBlackList(b);

        List<BlackList> list = EhDB.getAllBlackList();
        assertEquals(2, list.size());
    }

    @Test
    public void getAllLocalFavoritesReturnsInsertedRows() {
        resetDb();

        EhDB.putLocalFavorite(makeFavorite(10L, "alpha"));
        EhDB.putLocalFavorite(makeFavorite(20L, "beta"));

        List<GalleryInfo> list = EhDB.getAllLocalFavorites();
        assertEquals(2, list.size());
    }

    @Test
    public void searchLocalFavoritesFiltersByTitle() {
        resetDb();

        EhDB.putLocalFavorite(makeFavorite(10L, "alpha gallery"));
        EhDB.putLocalFavorite(makeFavorite(20L, "beta gallery"));

        List<GalleryInfo> list = EhDB.searchLocalFavorites("alpha");
        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).gid);
    }

    // ---- async wrappers deliver the same data on the main thread ----

    @Test
    public void getAllLocalFavoritesAsyncDeliversSameDataOnMainThread() throws Exception {
        resetDb();

        EhDB.putLocalFavorite(makeFavorite(10L, "alpha"));
        EhDB.putLocalFavorite(makeFavorite(20L, "beta"));

        AtomicReference<List<GalleryInfo>> delivered = new AtomicReference<>();
        AtomicReference<Boolean> onMain = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        EhDB.getAllLocalFavoritesAsync(result -> {
            onMain.set(Looper.myLooper() == Looper.getMainLooper());
            delivered.set(result);
            latch.countDown();
        });

        // The callback is posted to the main looper, which is paused under Robolectric.
        // Idle it until the posted runnable executes (polling to absorb the background hop).
        long deadline = System.currentTimeMillis() + 5000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(5);
        }

        assertTrue("callback should have fired", latch.await(1, TimeUnit.SECONDS));
        assertTrue("callback must run on the main thread", Boolean.TRUE.equals(onMain.get()));
        assertEquals(2, delivered.get().size());
    }

    private static GalleryInfo makeFavorite(long gid, String title) {
        LocalFavoriteInfo info = new LocalFavoriteInfo();
        info.gid = gid;
        info.title = title;
        info.time = gid; // deterministic order
        return info;
    }

    private static void resetDb() {
        Context app = RuntimeEnvironment.application;
        app.deleteDatabase("eh.db");
        Settings.initialize(app);
        EhDB.initialize(app);
    }
}
