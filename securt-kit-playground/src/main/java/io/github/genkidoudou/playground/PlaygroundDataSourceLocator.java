package io.github.genkidoudou.playground;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 多数据源解析扩展点。
 *
 * <p>单 DS 应用可不提供本 Bean；多 DS 应用返回 id → DataSource 映射。</p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
public interface PlaygroundDataSourceLocator {

    /**
     * @return 非空映射；键为数据源 id（如 primary / secondary）
     */
    Map<String, DataSource> locate();
}
