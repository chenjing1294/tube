package com.serene.tube.filter;

import com.serene.tube.FilterConfig;

import java.util.List;

public class GrokConfig extends FilterConfig {
    private List<String> patternPaths;
    private List<String> matches;
    private String src;

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public List<String> getPatternPaths() {
        return patternPaths;
    }

    public void setPatternPaths(List<String> patternPaths) {
        this.patternPaths = patternPaths;
    }

    public List<String> getMatches() {
        return matches;
    }

    public void setMatches(List<String> matches) {
        this.matches = matches;
    }
}
