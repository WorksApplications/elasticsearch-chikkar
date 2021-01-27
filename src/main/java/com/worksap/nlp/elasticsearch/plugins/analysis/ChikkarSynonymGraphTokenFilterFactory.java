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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.CustomAnalyzer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;

import com.worksap.nlp.elasticsearch.plugins.chikkar.Chikkar;

public class ChikkarSynonymGraphTokenFilterFactory extends AbstractTokenFilterFactory {

    private static final Logger log = LogManager.getLogger(ChikkarSynonymGraphTokenFilterFactory.class);

    private final boolean ignoreCase;
    private final boolean enableDictCache;
    private final String systemDictId;
    private final String systemDict;
    private final List<String> userDictList;
    protected final Settings settings;
    protected final Environment environment;

    /**
     * Constructor with argument
     *
     * @param indexSettings
     *            {@link IndexSettings} of elasticsearch server
     * @param env
     *            {@link Environment} of elasticsearch server
     * @param name
     *            Name of this token filter
     * @param settings
     *            {@link Settings} of this token filter
     */
    public ChikkarSynonymGraphTokenFilterFactory(IndexSettings indexSettings, Environment env, String name,
            Settings settings) {
        super(indexSettings, name, settings);

        // get the filter setting params
        this.ignoreCase = settings.getAsBoolean("ignore_case", false);
        this.enableDictCache = settings.getAsBoolean("enable_cache", false);
        this.systemDictId = settings.get("system_dict_id", "dummy_system_dict");
        this.systemDict = settings.get("system_dict");
        this.userDictList = settings.getAsList("user_dict_list");
        this.settings = settings;
        this.environment = env;
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        throw new IllegalStateException(
                "Call createPerAnalyzerSynonymFactory to specialize this factory for an analysis chain first");
    }

    @Override
    public TokenFilterFactory getChainAwareTokenFilterFactory(TokenizerFactory tokenizer,
            List<CharFilterFactory> charFilters, List<TokenFilterFactory> previousTokenFilters,
            Function<String, TokenFilterFactory> allFilters) {
        final Analyzer analyzer = buildSynonymAnalyzer(tokenizer, charFilters, previousTokenFilters);

        if (systemDict == null) {
            log.error("Missing system_dict in settings. You need to set system_dict in settings.");
            throw new IllegalArgumentException(
                    "Missing system_dict in settings. You need to set system_dict in settings.");
        }
        if (userDictList.isEmpty()) {
            log.warn("Missing user_dict_list in settings. Will only use system_dict to build synonyms.");
        }

        ChikkarSynonymMap synonyms;
        if (enableDictCache) {
            final String cachedSystemDictKey = String.join("-", systemDictId, generateMD5Hash(systemDict));
            synonyms = buildUserSynonyms(analyzer, cachedSystemDictKey);
        } else {
            synonyms = buildUserSynonyms(analyzer);
        }

        final String name = name();
        return new TokenFilterFactory() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public TokenStream create(TokenStream tokenStream) {
                return synonyms.fst == null ? tokenStream
                        : new ChikkarSynonymGraphTokenFilter(tokenStream, synonyms, ignoreCase);
            }
        };
    }

    Analyzer buildSynonymAnalyzer(TokenizerFactory tokenizer, List<CharFilterFactory> charFilters,
            List<TokenFilterFactory> tokenFilters) {
        return new CustomAnalyzer(tokenizer, charFilters.toArray(new CharFilterFactory[0]),
                tokenFilters.stream().map(TokenFilterFactory::getSynonymFilter).toArray(TokenFilterFactory[]::new));
    }

    ChikkarSynonymMap buildUserSynonyms(Analyzer analyzer) {
        try {
            Chikkar chikkarUser = new Chikkar(analyzer);
            chikkarUser.loadDictionary(environment.configFile().resolve(systemDict));
            for (String dp : userDictList) {
                chikkarUser.loadDictionary(environment.configFile().resolve(dp));
            }
            ChikkarSynonymMap.Builder builder = new ChikkarSynonymMap.Builder(true);
            return builder.build(chikkarUser);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to build synonyms", e);
        }
    }

    ChikkarSynonymMap buildUserSynonyms(Analyzer analyzer, String systemDictCacheKey) {
        try {
            Chikkar chikkarSystem = ChikkarCache.getInstance().getSystemDictCache(systemDictCacheKey);
            if (chikkarSystem == null) {
                chikkarSystem = new Chikkar(analyzer);
                chikkarSystem.loadDictionary(environment.configFile().resolve(systemDict));
                ChikkarCache.getInstance().put(systemDictCacheKey, chikkarSystem);
            }
            Chikkar chikkarUser = Chikkar.clone(chikkarSystem);
            for (String dp : userDictList) {
                chikkarUser.loadDictionary(environment.configFile().resolve(dp));
            }
            ChikkarSynonymMap.Builder builder = new ChikkarSynonymMap.Builder(true);
            return builder.build(chikkarUser);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to build synonyms", e);
        }
    }

    String generateMD5Hash(String value) {
        try {
            if (value == null) {
                return "";
            }
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            return new String(digest.digest(), StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            log.error("NoSuchAlgorithmException for MD5");
            return "dummy-md5-hash";
        }
    }

}
