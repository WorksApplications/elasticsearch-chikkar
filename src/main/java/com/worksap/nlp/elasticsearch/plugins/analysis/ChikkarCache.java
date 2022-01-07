/*
 * Copyright (c) 2019 Works Applications Co., Ltd.
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

package com.worksap.nlp.elasticsearch.plugins.analysis;

import com.worksap.nlp.elasticsearch.plugins.chikkar.Chikkar;

import java.util.HashMap;
import java.util.Map;

public class ChikkarCache {

    private static ChikkarCache chikkarCache = null;

    private Map<String, Chikkar> systemDictCache;
    private Map<String, String> systemDictTimeStamp;

    private ChikkarCache() {
        this.systemDictCache = new HashMap<>();
        this.systemDictTimeStamp = new HashMap<>();
    }

    public static synchronized ChikkarCache getInstance() {
        if (chikkarCache == null) {
            chikkarCache = new ChikkarCache();
        }
        return chikkarCache;
    }

    public synchronized void put(String key, Chikkar value, String time) {
        systemDictCache.put(key, value);
        systemDictTimeStamp.put(key, time);
    }

    public synchronized Chikkar getSystemDictCache(String key) {
        return systemDictCache.get(key);
    }

    public synchronized String getSystemDictTimeStamp(String key) {
        return systemDictTimeStamp.get(key);
    }

}
