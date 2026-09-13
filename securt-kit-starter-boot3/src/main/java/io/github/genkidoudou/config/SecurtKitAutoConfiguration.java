package io.github.genkidoudou.config;

import io.github.genkidoudou.core.TableCache;
import io.github.genkidoudou.core.config.EncryptModeHolder;
import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.crypto.DefaultFieldCryptoService;
import io.github.genkidoudou.core.crypto.FieldCryptoService;
import io.github.genkidoudou.core.crypto.FieldCryptoServiceHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Securt-Kit 自动配置（Boot3）：配置绑定、TableCache 初始化、FieldCryptoService、
 * 模式互斥校验，以及 MYBATIS 模式下的 EncryptInterceptor 注册。
 *
 * @author hexlodev
 * @since 1.3.0
 */
@Slf4j
@Configuration
@Import(cn.hutool.extra.spring.SpringUtil.class)
@EnableConfigurationProperties(FieldEncryptorProperties.class)
public class SecurtKitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FieldCryptoService.class)
    public FieldCryptoService fieldCryptoService() {
        DefaultFieldCryptoService service = new DefaultFieldCryptoService();
        FieldCryptoServiceHolder.set(service);
        return service;
    }

    @Bean
    @Order(0)
    public ApplicationRunner securtKitBootstrapRunner(FieldEncryptorProperties properties,
                                                      ObjectProvider<DataSource> dataSourceProvider) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                if (!properties.isEnable()) {
                    EncryptModeHolder.setMode(FieldEncryptorProperties.Mode.OFF);
                    log.info("【securt-kit】encryptor.enable=false, all channels OFF");
                    return;
                }
                TableCache.init(properties);
                FieldCryptoServiceHolder.set(new DefaultFieldCryptoService());
                FieldEncryptorProperties.Mode mode = properties.getMode() == null
                        ? FieldEncryptorProperties.Mode.JDBC
                        : properties.getMode();
                EncryptModeHolder.setMode(mode);
                validateMode(mode, dataSourceProvider);
                log.info("【securt-kit】initialized, mode={}", EncryptModeHolder.getMode());
            }
        };
    }

    @Configuration
    @ConditionalOnClass(name = {
            "org.apache.ibatis.plugin.Interceptor",
            "io.github.genkidoudou.mybatis.EncryptInterceptor"
    })
    @ConditionalOnProperty(prefix = FieldEncryptorProperties.PREFIX, name = "mode", havingValue = "MYBATIS")
    static class MybatisEncryptInterceptorConfiguration {

        @Bean(name = "securtKitEncryptInterceptor")
        @ConditionalOnMissingBean(name = "securtKitEncryptInterceptor")
        public org.apache.ibatis.plugin.Interceptor securtKitEncryptInterceptor() {
            return new io.github.genkidoudou.mybatis.EncryptInterceptor();
        }
    }

    private static void validateMode(FieldEncryptorProperties.Mode mode,
                                     ObjectProvider<DataSource> dataSourceProvider) {
        if (mode == FieldEncryptorProperties.Mode.MYBATIS) {
            if (!isClassPresent("org.apache.ibatis.plugin.Interceptor")) {
                throw new IllegalStateException(
                        "securtkit.encryptor.mode=MYBATIS requires MyBatis on classpath");
            }
            if (!isClassPresent("io.github.genkidoudou.mybatis.EncryptInterceptor")) {
                throw new IllegalStateException(
                        "securtkit.encryptor.mode=MYBATIS requires dependency securt-kit-mybatis");
            }
            for (DataSource ds : dataSourceProvider) {
                String url = resolveJdbcUrl(ds);
                if (url != null && url.contains("jdbc:interceptor:")) {
                    throw new IllegalStateException(
                            "securtkit.encryptor.mode=MYBATIS must NOT use jdbc:interceptor: URL "
                                    + "(found: " + url + "). Dual encryption is forbidden.");
                }
            }
        } else if (mode == FieldEncryptorProperties.Mode.JDBC) {
            boolean foundInterceptorUrl = false;
            List<DataSource> list = dataSourceProvider.stream().collect(Collectors.toList());
            if (list.isEmpty()) {
                log.warn("【securt-kit】mode=JDBC but no DataSource bean found; encryption may not apply");
            }
            for (DataSource ds : list) {
                String url = resolveJdbcUrl(ds);
                if (url != null && url.contains("jdbc:interceptor:")) {
                    foundInterceptorUrl = true;
                    break;
                }
                String driver = resolveDriverName(ds);
                if (driver != null && driver.contains("SimpleInterceptorDriver")) {
                    foundInterceptorUrl = true;
                    break;
                }
            }
            if (!list.isEmpty() && !foundInterceptorUrl) {
                log.warn("【securt-kit】mode=JDBC but DataSource URL does not contain jdbc:interceptor: "
                        + "and driver may not be SimpleInterceptorDriver; field encryption may NOT take effect");
            }
        }
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static String resolveJdbcUrl(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }
        try {
            for (String method : new String[]{"getJdbcUrl", "getUrl"}) {
                try {
                    Object url = dataSource.getClass().getMethod(method).invoke(dataSource);
                    if (url != null) {
                        return url.toString();
                    }
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
            try (Connection c = dataSource.getConnection()) {
                DatabaseMetaData meta = c.getMetaData();
                return meta != null ? meta.getURL() : null;
            }
        } catch (Exception e) {
            log.debug("Failed to resolve JDBC URL from DataSource: {}", e.getMessage());
            return null;
        }
    }

    private static String resolveDriverName(DataSource dataSource) {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();
            return meta != null ? meta.getDriverName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
