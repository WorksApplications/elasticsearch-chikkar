# elasticsearch-chikkar

[![Build Status](https://github.com/WorksApplications/elasticsearch-chikkar/actions/workflows/gradle.yml/badge.svg)](https://github.com/WorksApplications/elasticsearch-chikkar/actions/workflows/gradle.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.worksap.nlp%3Aanalysis-chikkar&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.worksap.nlp%3Aanalysis-chikkar)

This project will be merged into [elasticsearch-sudachi](https://github.com/WorksApplications/elasticsearch-sudachi).

A synonym token filter plugin for Elasticsearch

# usage example:

suppose we use `Sudachi` for tokenizer.

## use synonym dictionaries in text format

* Pass `chikkar_synonym` for the type of chikkar plugin.
* Set `system_dict` to specifiy system synonym dictionary path. The path should be relative path to the ES config folder, as ES can only access files under config folder.
* Set `user_dict_list` with a list of user synonym dictionary paths. The paths should be relative path to the ES config folder, as ES can only access files under config folder.

```
{
  "settings": {
    "index": {
      "analysis": {
        "tokenizer": {
          "sudachi_tokenizer": {
            "type": "sudachi_tokenizer",
            "split_mode" : "C",
            "discard_punctuation": true,
            "resources_path": "/usr/share/elasticsearch/config"
          }
        },
        "filter" : {
            "chikkar_filter" : {
                "type" : "chikkar_synonym",
                "system_dict": "hr/hr_synonym_core.txt",
                "user_dict_list": ["hr/user_dict_1.txt", "hr/user_dict_2.txt", "hr/user_dict_3.txt"]
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
