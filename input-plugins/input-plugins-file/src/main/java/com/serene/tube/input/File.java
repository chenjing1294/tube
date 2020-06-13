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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Stream;

/**
 * 从文件获取输入
 *
 * @author 陈敬
 * @since 2020年6月11日
 */
public class File extends Input {
    private final static Logger logger = LoggerFactory.getLogger(File.class);
    private Map<String, Long> readLengths;
    private Set<Path> files;
    private String fileName;
    private Random random;

    public File(FileConfig config, String threadName) {
        super(config, threadName);
        random = new Random();
        readLengths = new ConcurrentHashMap<>();
        files = new ConcurrentSkipListSet<>();
        fileName = "tube-input-plugins-File.dat";

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

        if (config.getReadPeriod() == null || config.getReadPeriod() < 1000) {
            config.setReadPeriod(5000);
        }

        if (config.getScanPeriod() == null || config.getScanPeriod() < 1000) {
            config.setScanPeriod(1000 * 30);
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
                logger.error("The specified file or directory does not exist:\n{}", inexistence.toArray());
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
            for (Path p : files) {
                String absPath = p.toAbsolutePath().toString();
                if (!readLengths.containsKey(absPath)) {
                    readLengths.put(absPath, 0L);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            logger.warn("An exception occurred while reading [File] internal data structure, please restart", e);
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
    private static void getAllFileInDir(Path path, Set<Path> files) {
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
                try {
                    Stream<Path> list = Files.list(path);
                    list.forEach(p -> {
                        getAllFileInDir(p, files);
                    });
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            } else {
                for (Path p : files) {
                    if (p.toAbsolutePath().toString().equals(path.toAbsolutePath().toString())) {
                        return;
                    }
                }
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
            logger.info("Save tube-input-plugins-File.dat successfully");
        } catch (IOException e) {
            logger.error("An error occurred while saving internal data", e);
        }
    }

    @Override
    public void run() {
        Integer threadNum = ((FileConfig) config).getThreadNum();
        if (threadNum > files.size()) {
            threadNum = files.size();
        }
        int step = files.size() / threadNum;
        List<Set<Path>> conveyor = new ArrayList<>();
        Iterator<Path> iterator = files.iterator();
        for (int i = 0; i < threadNum; i++) {
            Set<Path> fs = new ConcurrentSkipListSet<>();
            for (int j = i * step; j < (i + 1) * step; j++) {
                fs.add(iterator.next());
            }
            if (i + 1 == threadNum) {
                while (iterator.hasNext()) {
                    fs.add(iterator.next());
                }
            }
            conveyor.add(fs);
            monitoringFile(fs);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Thread.sleep(((FileConfig) config).getScanPeriod());
                        for (String path : ((FileConfig) config).getPaths()) {
                            Path tem = Paths.get(path);
                            getAllFileInDir(tem, files);
                        }
                        for (Path p : files) {
                            boolean exist = false;
                            for (Set<Path> s : conveyor) {
                                if (s.contains(p)) {
                                    exist = true;
                                    break;
                                }
                            }
                            if (!exist) {
                                logger.trace("find new file {}", p.toAbsolutePath().toString());
                                readLengths.put(p.toAbsolutePath().toString(), 0L);
                                conveyor.get(random.nextInt(conveyor.size())).add(p);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }, "input-File-conveyor").start();
    }

    /**
     * 监控文件变化，并输出
     *
     * @param paths 要监控的文件
     */
    private void monitoringFile(Set<Path> paths) {
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
                        Thread.sleep(((FileConfig) config).getReadPeriod());
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
            if (readLength == 0 && ((FileConfig) config).getStartPosition() == null) {
                readLength = length;
                readLengths.put(path.toAbsolutePath().toString(), length);
            }
            if (readLength > length) {//重命名并压缩access.log，然后新建access.log继续记录日志
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
        //不跟踪压缩或打包文件
        if (readLength > 0 || ((FileConfig) config).getStartPosition() == null)
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
        if (readLength > 0 || ((FileConfig) config).getStartPosition() == null)
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
