package com.serene.tube.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serene.tube.Event;
import com.serene.tube.Output;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ElasticSearch extends Output {
    private static Logger logger = LoggerFactory.getLogger(ElasticSearch.class);
    private Pattern fieldPattern;
    private Pattern datePattern;
    private Random random;
    private ObjectMapper objectMapper;
    private Map<String, List<Event>> eventsMap;
    private boolean shutdown = false;
    private CloseableHttpClient httpclient;
    private long startTime;

    public ElasticSearch(ElasticSearchConfig config) {
        super(config);
        startTime = System.currentTimeMillis() / 1000;
        objectMapper = new ObjectMapper();
        eventsMap = new ConcurrentHashMap<>();
        random = new Random();
        httpclient = HttpClients.createDefault();
        if (config.getBulkSize() == null) {
            config.setBulkSize(100);
        }
        if (config.getPeriod() == null || config.getPeriod() <= 0) {
            config.setPeriod(30);
        }
        if (config.getMapping() == null || config.getMapping().length() == 0) {
            logger.warn("No mapping specified for index");
            config.setMapping(null);
        }
        String fieldPattern = "\\$\\{[_0-9a-zA-Z]+\\}";
        String datePattern = "\\%\\{[_0-9a-zA-Z\\.]+\\}";
        this.fieldPattern = Pattern.compile(fieldPattern);
        this.datePattern = Pattern.compile(datePattern);

        List<String> hosts = ((ElasticSearchConfig) this.config).getHosts();
        logger.info("Start to detect ElasticSearch cluster...");
        List<String> dead = new ArrayList<>();
        for (String host : hosts) {
            HttpGet get = new HttpGet(String.format("http://%s", host));
            try {
                CloseableHttpResponse response = httpclient.execute(get);
                logger.info("{} : {}", host, response.getStatusLine());
            } catch (IOException e) {
                dead.add(host);
                logger.error("{} Connection failed", host);
            }
        }
        if (dead.size() == hosts.size()) {
            logger.error("Unable to connect to any ElasticSearch node");
            System.exit(1);
        } else if (dead.size() > 0) {
            logger.info("The following nodes cannot be connected:");
            for (String d : dead) {
                logger.info("{}", d);
            }
            hosts.removeAll(dead);
        }
    }

    @Override
    public void emit(Event event) {
        Date now = new Date();
        String index = ((ElasticSearchConfig) config).getIndex();

        Matcher fieldMatcher = fieldPattern.matcher(index);
        StringBuilder newIndex = new StringBuilder();
        int end = 0;
        while (fieldMatcher.find()) {
            newIndex.append(index.substring(end, fieldMatcher.start()));
            String fieldValue = (String) event.get(index.substring(fieldMatcher.start() + 2, fieldMatcher.end() - 1));
            newIndex.append(fieldValue);
            end = fieldMatcher.end();
        }
        newIndex.append(index.substring(end));

        end = 0;
        Matcher dateMatcher = datePattern.matcher(newIndex);
        StringBuilder nnewIndex = new StringBuilder();
        while (dateMatcher.find()) {
            nnewIndex.append(newIndex.substring(end, dateMatcher.start()));
            SimpleDateFormat dateFormat = new SimpleDateFormat(newIndex.substring(dateMatcher.start() + 2, dateMatcher.end() - 1));
            nnewIndex.append(dateFormat.format(now));
            end = dateMatcher.end();
        }
        nnewIndex.append(newIndex.substring(end));

        index = nnewIndex.toString();
        if (eventsMap.containsKey(index)) {
            eventsMap.get(index).add(event);
        } else {
            List<Event> events = Collections.synchronizedList(new ArrayList<>());
            events.add(event);
            eventsMap.put(index, events);
        }
        sendEventToES();
    }

    @Override
    public void shutdown() {
        if (!eventsMap.isEmpty()) {
            logger.info("Send the remaining events to ElasticSearch...");
            eventsMap.forEach((k, v) -> {
                if (v.size() > 0) {
                    shutdown = true;
                }
            });
            sendEventToES();
            logger.info("All events have been sent.(have a good day ^_^)");
        }
        try {
            httpclient.close();
            logger.info("[{}] output plugin shutdown success", this.getClass().getSimpleName());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void sendEventToES() {
        List<String> hosts = ((ElasticSearchConfig) this.config).getHosts();
        eventsMap.forEach((k, events) -> {
            if (shutdown || events.size() >= ((ElasticSearchConfig) config).getBulkSize() || timeout()) {
                CloseableHttpResponse response = null;
                try {
                    if(events.size() == 0)
                        return;
                    String host = hosts.get(random.nextInt(hosts.size()));
                    StringBuilder builder = new StringBuilder();
                    events.forEach(event -> {
                        try {
                            String tmp = "{\"index\":{}}\n" + objectMapper.writeValueAsString(event) + "\n";
                            builder.append(tmp);
                        } catch (Exception e) {
                            logger.warn("Encounter an event that cannot be resolved. {}", event);
                        }
                    });
                    if (!checkIndexExist(k) && ((ElasticSearchConfig) config).getMapping() != null) {//索引还不存在，创建
                        logger.debug("Index [{}] does not exist, creating...", k);
                        HttpPut httpPut = new HttpPut(String.format("http://%s/%s", host, k));
                        httpPut.setEntity(new ByteArrayEntity(((ElasticSearchConfig) config).getMapping().getBytes(StandardCharsets.UTF_8), ContentType.create("application/x-ndjson")));
                        response = httpclient.execute(httpPut);
                        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                            throw new RuntimeException("Failed to create index mapping");
                        }
                        logger.debug("Index [{}] created successfully", k);
                    }
                    HttpPost httpPost = new HttpPost(String.format("http://%s/%s/_bulk", host, k));
                    httpPost.setEntity(new ByteArrayEntity(builder.toString().getBytes(StandardCharsets.UTF_8), ContentType.create("application/x-ndjson")));
                    response = httpclient.execute(httpPost);
                    logger.debug(response.getStatusLine().toString());
                    logger.debug(builder.toString());
                    HttpEntity entity = response.getEntity();
                    EntityUtils.consume(entity);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    if (response != null) {
                        try {
                            response.close();
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                    events.clear();
                }
            }
        });
    }

    private boolean checkIndexExist(String index) throws IOException {
        List<String> hosts = ((ElasticSearchConfig) config).getHosts();
        String host = hosts.get(random.nextInt(hosts.size()));
        HttpHead httpHead = new HttpHead(String.format("http://%s/%s", host, index));
        CloseableHttpResponse response = httpclient.execute(httpHead);
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
    }

    @Override
    public void hurryOver() {
        this.shutdown = true;
    }

    /**
     * 定期发送数据到ES
     */
    private boolean timeout() {
        long curr = System.currentTimeMillis() / 1000;
        long diff = curr - startTime;
        startTime = curr;
        return diff >= ((ElasticSearchConfig) config).getPeriod();
    }
}
