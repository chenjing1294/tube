package com.serene.tube.filter;

import com.serene.tube.FilterConfig;

import java.util.List;

public class RemoveConfig extends FilterConfig {
    private List<String> fields;

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}
