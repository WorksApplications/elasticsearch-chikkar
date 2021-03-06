# elasticsearch-chikkar

[![Build Status](https://travis-ci.com/WorksApplications/elasticsearch-chikkar.svg?branch=develop)](https://travis-ci.com/WorksApplications/elasticsearch-chikkar)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.worksap.nlp%3Aanalysis-chikkar&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.worksap.nlp%3Aanalysis-chikkar)

A synonym token filter plugin for Elasticsearch

# usage example:

suppose we use `Sudachi` for tokenizer.

## use synonym dictionaries in text format

* Pass `chikkar_synonym` for the type of chikkar plugin.
* Set `restrict_mode` to true to ignore directed synonyms. Default value is false.
* Set `dict_list` with a list of synonym dictionary paths. The paths should be relative path to the ES config folder, as ES can only access files under config folder.

```
{
  "settings": {
    "index": {
      "analysis": {
        "tokenizer": {
          "sudachi_tokenizer": {
            "type": "sudachi_tokenizer",
            "mode": "search",
            "discard_punctuation": true,
            "resources_path": "sudachi_tokenizer"
          }
        },
        "filter" : {
            "chikkar_filter" : {
                "type" : "chikkar_synonym",
                "restrict_mode" : false,
                "dict_list" : ["chikkar/synonym1.txt", "chikkar/synonym2.txt", "chikkar/synonym3.txt"]
            }
        },
        "analyzer": {
          "sudachi_analyzer_no_synonym": {
            "filter": ["sudachi_normalizedform"],
            "tokenizer": "sudachi_tokenizer",
            "type": "custom"
          },
          "sudachi_analyzer_chikkar_synonym": {
            "filter": [
              "sudachi_normalizedform",
              "chikkar_filter"
            ],
            "tokenizer": "sudachi_tokenizer",
            "type": "custom"
          }
        }
      }
    }
  },
  "mappings": {
    "properties": {
        "content": {
            "type": "text",
            "analyzer": "sudachi_analyzer_no_synonym",
            "search_analyzer": "sudachi_analyzer_chikkar_synonym",
            "term_vector": "with_positions_offsets"
        }
    }
  }
}
```

## use built synonym dictionaries in binary format

For big dictionaries, it is much faster using the pre-built binary format of them to load.

* Pass `chikkar_synonym` for the type of chikkar plugin.
* Set `dict_bin_path` with relative path of the binary file. The path should be relative path to the ES config folder, as ES can only access files under config folder.
* Note that current implementation don't allow to set `restrict_mode` for binary dictionary. If user set `restrict_mode` for binary dictionary, it will not take effect.

```
{
  "settings": {
    "index": {
      "analysis": {
        "tokenizer": {
          "sudachi_tokenizer": {
            "type": "sudachi_tokenizer",
            "mode": "search",
            "discard_punctuation": true,
            "resources_path": "sudachi_tokenizer"
          }
        },
        "filter" : {
            "chikkar_filter" : {
                "type" : "chikkar_synonym",
                "dict_bin_path" : "chikkar/synonym.bin"
            }
        },
        "analyzer": {
          "sudachi_analyzer_no_synonym": {
            "filter": ["sudachi_normalizedform"],
            "tokenizer": "sudachi_tokenizer",
            "type": "custom"
          },
          "sudachi_analyzer_chikkar_synonym": {
            "filter": [
              "sudachi_normalizedform",
              "chikkar_filter"
            ],
            "tokenizer": "sudachi_tokenizer",
            "type": "custom"
          }
        }
      }
    }
  },
  "mappings": {
    "properties": {
        "content": {
            "type": "text",
            "analyzer": "sudachi_analyzer_no_synonym",
            "search_analyzer": "sudachi_analyzer_chikkar_synonym",
            "term_vector": "with_positions_offsets"
        }
    }
  }
}
```
