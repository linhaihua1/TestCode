package com.apiweb.engine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.util.JMeterUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 嵌入式 JMeter 启动器。
 *
 * <p>从 classpath 拷贝 4 个 JMeter 配置到临时目录，调用 {@link JMeterUtils} 初始化，
 * 让应用进程内直接具备 JMeter 全套能力（StandardJMeterEngine、SaveService、ResultCollector）。
 *
 * <p>从此性能测试不再依赖外部 {@code bin/jmeter} 命令或 {@code JMETER_HOME} 环境变量，
 * 拷贝部署 jar 即可运行（仅需 Java 17 + MySQL/Redis/RabbitMQ）。
 *
 * <p>4 个配置文件来自 {@code src/main/resources/jmeter/}：
 * <ul>
 *   <li>jmeter.properties —— 主配置</li>
 *   <li>saveservice.properties —— 结果保存字段</li>
 *   <li>upgrade.properties —— 版本升级表（空即可）</li>
 *   <li>log4j2.xml —— JMeter 自己的日志（避免它找错位置）</li>
 * </ul>
 */
@Slf4j
@Component
public class JmeterBootstrapper {

    private static final String[] CONFIG_FILES = {
            "jmeter/jmeter.properties",
            "jmeter/saveservice.properties",
            "jmeter/upgrade.properties",
            "jmeter/log4j2.xml"
    };

    @Getter
    private File jmeterHome;

    @Getter
    private boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            jmeterHome = Files.createTempDirectory("apiweb-jmeter-").toFile();
            File binDir = new File(jmeterHome, "bin");
            binDir.mkdirs();
            File libDir = new File(jmeterHome, "lib");
            libDir.mkdirs();
            File libExtDir = new File(jmeterHome, "lib/ext");
            libExtDir.mkdirs();

            // 1. 拷贝配置文件
            for (String resource : CONFIG_FILES) {
                String fileName = resource.substring(resource.indexOf('/') + 1);
                copyResource(resource, new File(binDir, fileName));
            }

            // 2. 写入 upgrades 目录（让 UpgradeService 不再尝试加载资源）
            new File(binDir, "upgrades").mkdirs();

            // 3. 初始化 JMeterUtils
            JMeterUtils.setJMeterHome(jmeterHome.getAbsolutePath());
            JMeterUtils.loadJMeterProperties(new File(binDir, "jmeter.properties").getAbsolutePath());
            JMeterUtils.initLocale();
            JMeterUtils.initLogging();
            // 显式覆盖一些关键默认值（让 SaveService 取到正确值）
            JMeterUtils.setProperty("jmeter.save.saveservice.output_format", "csv");
            JMeterUtils.setProperty("jmeter.save.saveservice.print_field_names", "true");

            initialized = true;
            log.info("嵌入式 JMeter 已就绪：home={}", jmeterHome);
        } catch (Exception e) {
            log.error("JMeter 启动失败，性能测试将无法执行", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (jmeterHome != null && jmeterHome.exists()) {
            try {
                deleteRecursively(jmeterHome.toPath());
            } catch (IOException ignored) {
                // 临时目录，JVM 退出时会自动清理
            }
        }
    }

    private static void copyResource(String classpathPath, File target) throws IOException {
        try (InputStream is = JmeterBootstrapper.class.getClassLoader().getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IOException("JMeter config resource not found on classpath: " + classpathPath);
            }
            target.getParentFile().mkdirs();
            Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path p : stream.toArray(Path[]::new)) {
                    deleteRecursively(p);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
