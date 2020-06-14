package com.serene.tube.filter;

import com.serene.tube.FilterConfig;

public class Geoip2Config extends FilterConfig {
    private String ip;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
