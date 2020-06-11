package com.serene.tube.input;

import com.serene.tube.Input;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 从文件获取输入
 *
 * @author 陈敬
 * @since 2020年6月11日
 */
public class File extends Input {
    private final static Logger logger = LoggerFactory.getLogger(File.class);
    private Map<String, Long> readLengths = new HashMap<>();
    private List<Path> files = new ArrayList<>();
    private String fileName = "tube-input-plugins-File.dat";

    public File(FileConfig config, String threadName) {
        super(config, threadName);
        if (config.getEncoding() == null) {
            config.setEncoding("UTF8");
        }
        if (config.getPaths() == null) {
            logger.error("The [paths] attribute is not set for the [File] plugin");
            System.exit(1);
        }
        if (config.getStartPosition() != null && !config.getStartPosition().equals("beginning")) {
            logger.error("The [startPosition] attribute of the [File] plugin can only be [beginning] or do not set.");
            System.exit(1);
        }

        if (config.getPeriod() == null || config.getPeriod() < 100) {
            config.setPeriod(5000);
        }

        if (config.getThreadNum() == null || config.getThreadNum() < 1) {
            config.setThreadNum(1);
        }

        for (String path : config.getPaths()) {
            Path tem = Paths.get(path);
            List<String> inexistence = new ArrayList<>();
            if (!Files.exists(tem)) {
                inexistence.add(path);
            } else {
                getAllFileInDir(tem, files);
            }
            if (inexistence.size() > 0) {
                logger.error("指定的文件或目录不存在:\n{}", inexistence.toArray());
                System.exit(1);
            }
        }

        Path path = Paths.get(System.getProperty("user.home"), ".tube", fileName);
        try {
            if (Files.exists(path)) {
                ObjectInputStream oin = new ObjectInputStream(new FileInputStream(path.toFile()));
                readLengths = (Map<String, Long>) oin.readObject();
            } else {
                readLengths = new HashMap<>();
            }
            for (int i = 0; i < files.size(); i++) {
                String absPath = files.get(i).toAbsolutePath().toString();
                if (!readLengths.containsKey(absPath)) {
                    readLengths.put(absPath, 0L);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            logger.warn("读取[File]内部数据结构时发生异常，请重新启动", e);
            try {
                Files.delete(path);
            } catch (IOException e1) {
                logger.error(e.getMessage(), e);
            } finally {
                System.exit(1);
            }
        }
    }

    /**
     * 递归地获取所有文件
     *
     * @param path  目录
     * @param files 文件存放位置
     */
    private static void getAllFileInDir(Path path, List<Path> files) {
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
                try {
                    Stream<Path> list = Files.list(path);
                    list.forEach(p -> {
                        if (!Files.isDirectory(p)) {
                            files.add(p);
                        } else {
                            getAllFileInDir(p, files);
                        }
                    });
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            } else {
                files.add(path);
            }
        }
    }

    @Override
    public void shutdown() {
        //记录文件读取位置
        Path file = Paths.get(System.getProperty("user.home"), ".tube", fileName);
        if (!Files.exists(file)) {
            Path dir = Paths.get(System.getProperty("user.home"), ".tube");
            if (!Files.exists(dir)) {
                try {
                    Files.createDirectories(dir);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
            try {
                file = Files.createFile(file);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file.toFile()))) {
            out.writeObject(readLengths);
            logger.info("save tube-input-plugins-File.dat successfully");
        } catch (IOException e) {
            logger.error("保存内部数据时发生错误", e);
        }
    }

    @Override
    public void run() {
        Integer threadNum = ((FileConfig) config).getThreadNum();
        int step = files.size() / threadNum;
        for (int i = 0; i < threadNum; i++) {
            List<Path> fs = new ArrayList<>();
            for (int j = i * step; j < (i + 1) * step && j < files.size(); j++) {
                fs.add(files.get(j));
            }
            monitoringFile(fs);
        }
    }

    /**
     * 监控文件变化，并输出
     *
     * @param paths 要监控的文件
     */
    private void monitoringFile(List<Path> paths) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    for (Path path : paths) {
                        if (path.toAbsolutePath().toString().endsWith(".tar.gz")) {
                            readTarGzipFile(path);
                        } else if (path.toAbsolutePath().toString().endsWith(".gz")) {
                            readGzipFile(path);
                        } else {
                            readPlainFile(path);
                        }
                    }
                    try {
                        Thread.sleep(((FileConfig) config).getPeriod());
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }).start();
    }

    /**
     * 读取普通文本文件
     */
    private void readPlainFile(Path path) {
        Charset charset = Charset.forName(((FileConfig) config).getEncoding());
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            Long readLength = readLengths.get(path.toAbsolutePath().toString());
            long length = file.length();
            if (readLength > length) {
                readLength = 0L;
            }
            if (readLength < length) {
                file.seek(readLength);
                String line;
                while ((line = file.readLine()) != null) {
                    if (line.length() > 0) {
                        process(new String(line.getBytes(StandardCharsets.ISO_8859_1), charset));
                    }
                }
                readLengths.put(path.toAbsolutePath().toString(), file.getFilePointer());
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 读取*.gz文件
     */
    private void readGzipFile(Path path) {
        Long readLength = readLengths.get(path.toAbsolutePath().toString());
        if (readLength > 0)
            return;
        Charset charset = Charset.forName(((FileConfig) config).getEncoding());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GzipCompressorInputStream(new FileInputStream(path.toFile()), true), charset));) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() != 0) {
                    process(line);
                }
            }
            readLengths.put(path.toAbsolutePath().toString(), 1L);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * 读取*.tar.gz文件
     */
    private void readTarGzipFile(Path path) {
        Long readLength = readLengths.get(path.toAbsolutePath().toString());
        if (readLength > 0)
            return;
        Charset charset = Charset.forName(((FileConfig) config).getEncoding());
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(path.toFile()), true));) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(tar, charset));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() != 0) {
                        process(line);
                    }
                }
            }
            readLengths.put(path.toAbsolutePath().toString(), 1L);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
