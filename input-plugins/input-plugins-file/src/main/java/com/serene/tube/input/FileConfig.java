package com.serene.tube.input;

import com.serene.tube.InputConfig;

import java.util.List;

public class FileConfig extends InputConfig {
    private List<String> paths;     //目录或文件路径
    private String encoding;        //文件的编码格式
    private String startPosition;   //从头开始读取还是从文件末尾开始
    private Integer period;         //线程扫描周期
    private Integer threadNum;      //用于扫描读取文件的线程数

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    public Integer getThreadNum() {
        return threadNum;
    }

    public void setThreadNum(Integer threadNum) {
        this.threadNum = threadNum;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(String startPosition) {
        this.startPosition = startPosition;
    }
}
