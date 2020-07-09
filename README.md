# tube

![](./tube.jpg)

tube是Logstash的Java实现，它包含的输出、过滤、输出插件并不全面，但是由于使用Java
编写，它的速度要比Logstash快上许多。

## 目录结构
将maven项目打包之后，把各个jar包放到合适的目录下：
```
.
├── cmd-1.2.jar
├── config  //配置文件
│   ├── filter.js
│   ├── full.json
│   ├── simple_linux.json
│   └── simple_windows.json
├── libs    //依赖
│   ├── args4j-2.33.jar
│   ├── commons-codec-1.11.jar
│   ├── commons-compress-1.20.jar
│   ├── commons-logging-1.2.jar
│   ├── commons-pool2-2.4.3.jar
│   ├── geoip2-2.13.1.jar
│   ├── httpclient-4.5.12.jar
│   ├── httpcore-4.4.13.jar
│   ├── jackson-annotations-2.9.10.jar
│   ├── jackson-core-2.9.10.jar
│   ├── jackson-databind-2.9.10.4.jar
│   ├── jcodings-1.0.13.jar
│   ├── jedis-2.10.2.jar
│   ├── joni-2.1.8.jar
│   ├── logback-classic-1.2.3.jar
│   ├── logback-core-1.2.3.jar
│   ├── maxmind-db-1.3.1.jar
│   ├── metrics-core-3.1.2.jar
│   └── slf4j-api-1.7.25.jar
├── log     //matric性能日志
│   └── tube-metric.log
├── modules //输出、输出、过滤插件
│   ├── core-1.2.jar
│   ├── filters
│   │   ├── filter-plugins-geoip2-1.2.jar
│   │   ├── filter-plugins-grok-1.2.jar
│   │   ├── filter-plugins-javascript-1.2.jar
│   │   └── filter-plugins-remove-1.2.jar
│   ├── inputs
│   │   ├── input-plugins-file-1.2.jar
│   │   ├── input-plugins-redis-1.2.jar
│   │   └── input-plugins-stdin-1.2.jar
│   └── outputs
│       ├── output-plugins-blackhole-1.2.jar
│       ├── output-plugins-elasticsearch-1.2.jar
│       ├── output-plugins-redis-1.2.jar
│       └── output-plugins-stdout-1.2.jar
├── tube    //启动脚本（Linux）
└── tube.bat//启动脚本（Windows）
```

## 输入插件

我们首先从输入插件开始，目前支持的输入插件包括：
- `Stdin` 从标准输入中读取
    ```json
    "Stdin": {
        "hostname": true,       //是否在生成的事件中包含主机名
        "type": "stdin",        //输入的类型
        "enableMeter": true,    //是否开启指标监控
        "codec": "plain"        //输出的编码格式（plain、json）
    }
    ```
- `Redis` 从Redis的channel里读取
    ```json
    "Redis": {
        "host": "localhost",    //Redis地址
        "port": 6379,           //Redis端口
        "password": "1234",     //Redis密码（没有可省略该字段）
        "channels": [           //需要监控的channels
            "hangout",
            "tube"
        ],
        "type": "microservices",//输入的类型
        "enableMeter": true,    //是否开启指标监控
        "codec": "json"         //输出的编码格式（plain、json）
    }
    ```
- `File` 从文件中读取
    ```json
    "File": {
        "codec": "plain",       //输出的编码格式（plain、json）
        "paths": [              //文件或目录的路径
            "D:\\temp\\log\\access_20200610.log.gz",
            "D:\\temp\\log\\access.log"
        ],
        "type": "nginx",        //输入的类型
        "encoding": "UTF-8",    //文件的编码格式
        "startPosition": "beginning",   //第一个读到文件时，是否从头开始读取
        "readPeriod": 3000,     //每个多长时间读取一次文件内容，ms
        "scanPeriod": 3000,     //多久扫描一次文件夹是否有新的文件产生，ms
        "threadNum": 3,         //指定用于读取文件的线程数
        "enableMeter": true     //是否开启指标监控
    }
    ```

## 过滤插件

- `Grok` 用于普通日志行的格式化
    ```json
    "Grok": {
        "patternPaths": [   //可以指定额外的模式文件
            "/opt/hangout/grokpatternpaths"
        ],
        "matches": [        //匹配规则
            "%{IP:client} %{WORD:method} %{URIPATHPARAM:request} %{NUMBER:bytes} %{NUMBER:duration}"
        ],
        "src": "@message",  //待匹配的文本在哪里
        "enableMeter": true //是否开启指标监控
    }
    ```
- `JavaScript` 外部脚本过滤
    ```json
    "JavaScript": {
        "condition": "event['@fields'] ? true : false",     //在什么情况下使用指定的脚本过滤
        "customFunctionPath": "D:\\tube\\config\\filter.js" //脚本所在位置
    }
    ```
- `Geoip2` 地理位置过滤
    ```json
    "Geoip2": {
        "condition": "event['remote_addr'] ? true : false", //在什么情况下才能过滤
        "ip": "remote_addr"                                 //要过滤的字段是哪个
    }
    ```
- `Remove` 删除指定的字段
    ```json
    "Remove": {
        "fields": [     //指定要删除的字段
            "client",
            "@message"
        ]
    }
    ```

## 输出插件
- `ElasticSearch` 输出到ES
    ```json
    "ElasticSearch": {
        "hosts": [  //ES节点地址
            "192.168.35.136:9200"
        ],
        "index": "tube-${type}-%{YYYY.MM.dd}",  //索引名
        "mapping": "{\"mappings\":{\"properties\":{\"@timestamp\":{\"type\":\"date\"},\"body_bytes_sent\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"country\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"geoip2\":{\"properties\":{\"cityName\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"countryName\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"location\":{\"properties\":{\"lat\":{\"type\":\"float\"},\"lon\":{\"type\":\"float\"}}}}},\"host\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"http_referrer\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"http_user_agent\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"http_x_forwarded_for\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"remote_addr\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"remote_user\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"request\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"request_method\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"request_time\":{\"type\":\"double\"},\"server_addr\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"server_name\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"status\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"type\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"upstream_addr\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"upstream_status\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"upstream_time\":{\"type\":\"double\"},\"uri\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\"url\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}}}}}", //指定映射类型，可选
        "enableMeter": true,    //是否开启指标监控
        "bulkSize": 300,        //批量发送的大小
        "period": 30            //发送间隔
    }
    ```
- `Output` 输出到标准输出
    ```json
    "Stdout": {
        "enableMeter": true //是否开启指标监控
    }
    ```

- `Redis` 输出到Redis
    ```json
    "Redis": {
        "host": "localhost",    //地址
        "port": 6379,           //端口
        "channel": "hangout",   //输出到的channel
        "enableMeter": true     //是否开启指标监控
    }
    ```

- `Blackhole` 相当于输出到null
    ```json
    "Blackhole": {
        "enableMeter": true //是否开启指标监控
    }
    ```
    
# 剩余任务
1. ES节点故障检测