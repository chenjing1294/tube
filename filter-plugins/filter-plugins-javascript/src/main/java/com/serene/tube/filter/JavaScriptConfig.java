package com.serene.tube.filter;

import com.serene.tube.FilterConfig;

public class JavaScriptConfig extends FilterConfig {
    private String CustomFunctionPath;

    public String getCustomFunctionPath() {
        return CustomFunctionPath;
    }

    public void setCustomFunctionPath(String customFunctionPath) {
        CustomFunctionPath = customFunctionPath;
    }
}
