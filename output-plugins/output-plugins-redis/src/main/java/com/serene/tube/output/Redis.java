package com.serene.tube.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serene.tube.Event;
import com.serene.tube.Output;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class Redis extends Output {
    private final static Logger logger = LoggerFactory.getLogger(Redis.class);
    private JedisPool jedisPool;
    private ObjectMapper objectMapper = new ObjectMapper();

    public Redis(RedisConfig config) {
        super(config);
        if (config.getHost() == null) {
            logger.error("please config [host] for redis input plugin!");
            System.exit(1);
        }

        if (config.getPort() == null) {
            logger.error("please config [port] for Redis input plugin!");
            System.exit(1);
        }

        if (config.getChannel() == null) {
            logger.error("please config [channel] for Redis input plugin!");
            System.exit(1);
        }
        this.jedisPool = new JedisPool(new GenericObjectPoolConfig(), config.getHost(), config.getPort(), Protocol.DEFAULT_TIMEOUT, config.getPassword());
        try {
            logger.info("Start to detect whether the Redis connection...");
            Jedis jedis = jedisPool.getResource();
            jedis.close();
        } catch (JedisConnectionException e) {
            logger.error("Redis connection failed, start was cancelled...");
        }
    }

    @Override
    protected void emit(Event event) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(((RedisConfig) config).getChannel(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }

    }

    @Override
    public void shutdown() {
        jedisPool.close();
        logger.info("[{}] input plugin shutdown success", this.getClass().getName());
    }
}
