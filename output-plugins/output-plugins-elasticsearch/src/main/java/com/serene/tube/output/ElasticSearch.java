package com.serene.tube.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serene.tube.Event;
import com.serene.tube.Output;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ElasticSearch extends Output {
    private static Logger logger = LoggerFactory.getLogger(ElasticSearch.class);
    private Pattern fieldPattern;
    private Pattern datePattern;
    private Random random = new Random();
    private ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, List<Event>> eventsMap = new HashMap<>();
    private boolean shutdown = false;
    private CloseableHttpClient httpclient = HttpClients.createDefault();

    public ElasticSearch(ElasticSearchConfig config) {
        super(config);
        if (config.getBulkSize() == null) {
            config.setBulkSize(100);
        }
        String fieldPattern = "\\$\\{[_0-9a-zA-Z]+\\}";
        String datePattern = "\\%\\{[_0-9a-zA-Z\\.]+\\}";
        this.fieldPattern = Pattern.compile(fieldPattern);
        this.datePattern = Pattern.compile(datePattern);
    }

    @Override
    public void emit(Event event) {
        Date now = new Date();
        String index = ((ElasticSearchConfig) config).getIndex();
        String documentType = ((ElasticSearchConfig) this.config).getDocumentType();

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

        end = 0;
        fieldMatcher = fieldPattern.matcher(documentType);
        StringBuilder newDocumentType = new StringBuilder();
        while (fieldMatcher.find()) {
            newDocumentType.append(documentType.substring(end, fieldMatcher.start()));
            String fieldValue = (String) event.get(documentType.substring(fieldMatcher.start() + 2, fieldMatcher.end() - 1));
            newDocumentType.append(fieldValue);
            end = fieldMatcher.end();
        }
        newDocumentType.append(documentType.substring(end));

        String iat = String.format("%s/%s", nnewIndex, newDocumentType);
        if (eventsMap.containsKey(iat)) {
            eventsMap.get(iat).add(event);
        } else {
            ArrayList<Event> events = new ArrayList<>();
            events.add(event);
            eventsMap.put(iat, events);
        }
        sendEventToES();
    }

    @Override
    public void shutdown() {
        logger.info("Send the remaining events to ElasticSearch...");
        eventsMap.forEach((k, v) -> {
            if (v.size() > 0) {
                shutdown = true;
            }
        });
        sendEventToES();
        try {
            httpclient.close();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        logger.info("All events have been sent.(have a good day ^_^)");
    }

    private void sendEventToES() {
        List<String> hosts = ((ElasticSearchConfig) this.config).getHosts();
        eventsMap.forEach((k, events) -> {
            if (shutdown || events.size() >= ((ElasticSearchConfig) config).getBulkSize()) {
                try {
                    StringBuilder builder = new StringBuilder();
                    for (Event e : events) {
                        builder.append("{\"index\":{}}").append("\n");
                        builder.append(objectMapper.writeValueAsString(e)).append("\n");
                    }

                    HttpPost httpPost = new HttpPost(String.format("http://%s/%s/_bulk", hosts.get(random.nextInt(hosts.size())), k));
                    httpPost.setEntity(new ByteArrayEntity(builder.toString().getBytes(), ContentType.create("application/x-ndjson")));
                    CloseableHttpResponse response = httpclient.execute(httpPost);
                    logger.info(response.getStatusLine().toString());
                    HttpEntity entity = response.getEntity();
                    /*InputStream content = entity.getContent();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(content, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }*/
                    // do something useful with the response body
                    // and ensure it is fully consumed
                    EntityUtils.consume(entity);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    events.clear();
                }
            }
        });
    }
}
