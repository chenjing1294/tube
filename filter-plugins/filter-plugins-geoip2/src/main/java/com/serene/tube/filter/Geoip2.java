package com.serene.tube.filter;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.serene.tube.Event;
import com.serene.tube.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class Geoip2 extends Filter {
    private final static Logger logger = LoggerFactory.getLogger(Geoip2.class);
    private DatabaseReader reader;

    public Geoip2(Geoip2Config config, String threadName) {
        super(config, threadName);

        if (config.getIp() == null || config.getIp().length() == 0) {
            logger.error("The [ip] attribute is not specified or the attribute is empty");
            System.exit(1);
        }
        InputStream resourceAsStream = this.getClass().getResourceAsStream("/geoip2database/GeoLite2-City.mmdb");
        try {
            reader = new DatabaseReader.Builder(resourceAsStream).withCache(new CHMCache()).build();
        } catch (IOException e) {
            logger.error("An error occurred while reading the geolocation database.", e);
            if (resourceAsStream != null) {
                try {
                    resourceAsStream.close();
                } catch (IOException e1) {
                    logger.error(e1.getMessage(), e1);
                }
            }
        }
    }

    @Override
    public Event filter(Event event) {
        String ipField = ((Geoip2Config) config).getIp();
        String ip = (String) event.get(ipField);
        if (ip != null && ip.length() > 0) {
            try {
                InetAddress ipAddress = InetAddress.getByName(ip);
                CityResponse response = reader.city(ipAddress);
                Country country = response.getCountry();
                String countryName = country.getName();

                City city = response.getCity();
                String cityName = city.getName();

                Location location = response.getLocation();
                Double latitude = location.getLatitude();
                Double longitude = location.getLongitude();

                Map<String, Object> geoip2 = new HashMap<>();
                geoip2.put("countryName", countryName);
                geoip2.put("cityName", cityName);
                Map<String, Double> loc = new HashMap<>();
                loc.put("lat", latitude);
                loc.put("lon", longitude);
                geoip2.put("location", loc);
                event.put("geoip2", geoip2);
            } catch (GeoIp2Exception | IOException e) {
                logger.warn("An exception occurred while parsing the IP address({})", ip);
            }
        }
        return event;
    }

    @Override
    public void shutdown() {
        try {
            reader.close();
            logger.info("[{}] filter plugin shutdown success", this.getClass().getSimpleName());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
