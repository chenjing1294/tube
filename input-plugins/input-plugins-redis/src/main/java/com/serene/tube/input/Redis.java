package com.serene.tube.input;

import com.serene.tube.Input;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class Redis extends Input {
    private final Logger logger = LoggerFactory.getLogger(Redis.class);
    private JedisPool jedisPool;

    public Redis(RedisConfig config, String threadName) {
        super(config, threadName);

        if (config.getHost() == null) {
            logger.error("please config [host] for redis input plugin!");
            System.exit(1);
        }

        if (config.getPort() == null) {
            logger.error("please config [port] for Redis input plugin!");
            System.exit(1);
        }

        if (config.getChannels() == null) {
            logger.error("please config [channels] for Redis input plugin!");
            System.exit(1);
        }
        this.jedisPool = new JedisPool(new GenericObjectPoolConfig(), config.getHost(), config.getPort(), Protocol.DEFAULT_TIMEOUT, config.getPassword());
        try {
            logger.info("Start to detect whether the Redis connection...");
            Jedis resource = jedisPool.getResource();
            resource.close();
        } catch (JedisConnectionException e) {
            logger.error("Redis connection failed, start was cancelled...");
            System.exit(1);
        }
    }

    @Override
    public void run() {
        try (Jedis jedis = jedisPool.getResource()) {
            JedisPubSub jedisPubSub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    super.onMessage(channel, message);
                    process(message);
                }
            };
            String[] channels = ((RedisConfig) config).getChannels().toArray(new String[0]);
            jedis.subscribe(jedisPubSub, channels);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        jedisPool.close();
        logger.info("[{}] input plugin shutdown success", this.getClass().getName());
    }
}