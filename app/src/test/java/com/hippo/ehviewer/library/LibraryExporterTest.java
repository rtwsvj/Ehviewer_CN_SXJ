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

import static org.junit.Assert.assertEquals;

import com.hippo.ehviewer.dao.DownloadInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class LibraryExporterTest {

    @Test
    public void testGetCbzFilenameEscapesInvalidCharacters() {
        DownloadInfo info = new DownloadInfo();
        info.gid = 1234L;
        info.token = "a/b:c*d?e\"f<g>h|i";

        assertEquals("1234-a_b_c_d_e_f_g_h_i.cbz", LibraryExporter.getCbzFilename(info));
    }
}
