package io.github.hexlodev.core.interceptor;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import io.github.hexlodev.core.TableCache;
import io.github.hexlodev.core.cache.StrategyCache;
import io.github.hexlodev.core.exception.EncryptionHandler;
import io.github.hexlodev.core.logging.SqlLogger;
import io.github.hexlodev.core.parser.SecurtkitUtils;
import io.github.hexlodev.core.parser.dto.ColumnTableDto;
import io.github.hexlodev.core.parser.dto.FieldEncryptorInfoDto;
import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;

import java.io.Reader;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLException;
import java.util.*;

/**
 * 参数加密器
 * <p>
 * 负责参数加密逻辑的统一处理，包括：
 * <ul>
 *   <li>判断参数是否需要加密</li>
 *   <li>执行参数加密</li>
 *   <li>处理加密异常</li>
 * </ul>
 * </p>
 *
 * @author hexlodev
 * @since 1.0.0
 */
@Slf4j
public class ParameterEncryptor {

    /**
     * 数据源标识
     */
    private final String datasourceId;

    /**
     * SQL 中涉及的表名集合
     */
    private final Set<String> tables;


    /**
     * 参数索引到字段信息的映射（优化查找性能）
     * key: 参数索引（从1开始，对应JDBC规范）
     * value: 字段信息DTO
     */
    private final Map<Integer, ColumnTableDto> parameterIndexToFieldMap;

    /**
     * 构造函数
     * <p>
     * 创建参数加密器实例，初始化参数索引到字段信息的映射。
     * </p>
     *
     * @param datasourceId  数据源标识，如果为 null 或空白则使用 "default"
     * @param tables        SQL 中涉及的表名集合，可以为 null
     * @param sqlParseResult SQL 解析结果，包含占位符到字段的映射，可以为 null
     */
    public ParameterEncryptor(String datasourceId, Set<String> tables,
                              Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> sqlParseResult) {
        this.datasourceId = StrUtil.isBlank(datasourceId) ? "default" : datasourceId;
        this.tables = tables != null ? new HashSet<>(tables) : Collections.emptySet();
        this.parameterIndexToFieldMap = buildParameterIndexMap(sqlParseResult);
    }

    /**
     * 构建参数索引到字段信息的映射
     * <p>
     * 将 SQL 解析结果中的占位符映射转换为参数索引映射，用于 O(1) 查找。
     * </p>
     *
     * @param pair SQL解析结果对，包含占位符到字段信息的映射
     * @return 参数索引到字段信息的映射，如果不需要加密或解析结果为 null 则返回空Map
     */
    private Map<Integer, ColumnTableDto> buildParameterIndexMap(
            Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair) {
        if (pair == null || pair.getKey() == null) {
            log.debug("buildParameterIndexMap: pair or key is null");
            return Collections.emptyMap();
        }

        Map<Integer, ColumnTableDto> indexMap = new HashMap<>();
        for (Map.Entry<String, ColumnTableDto> entry : pair.getKey().entrySet()) {
            String key = entry.getKey();
            ColumnTableDto dto = entry.getValue();
            Integer index = dto.getInsertFieldIndex();
            if (index != null && index > 0) {
                // 如果同一个索引对应多个字段，保留第一个（通常不会发生）
                indexMap.putIfAbsent(index, dto);
                if (log.isDebugEnabled()) {
                    log.debug("Mapped parameterIndex {} -> table: {}, column: {} (placeholder: {}, datasource-id: {})",
                            index, dto.getSourceTableName(), dto.getSourceColumn(), key, this.datasourceId);
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping placeholder {}: insertFieldIndex is null or <= 0 (index: {}, datasource-id: {})",
                            key, index, this.datasourceId);
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("buildParameterIndexMap: built {} mappings from {} placeholders (datasource-id: {})",
                    indexMap.size(), pair.getKey().size(), this.datasourceId);
        }
        return indexMap;
    }

    /**
     * 判断参数是否需要加密
     *
     * @param parameterIndex 参数索引（从1开始）
     * @return 如果需要加密返回 true，否则返回 false
     */
    public boolean needEncrypt(int parameterIndex) {
        if (!SecurtkitUtils.needEncrypt(this.tables, this.datasourceId)) {
            return false;
        }
        return parameterIndexToFieldMap.containsKey(parameterIndex);
    }

    /**
     * 获取参数对应的字段信息
     *
     * @param parameterIndex 参数索引（从1开始）
     * @return 字段信息DTO，如果参数不需要加密则返回 null
     */
    public ColumnTableDto getFieldInfo(int parameterIndex) {
        return parameterIndexToFieldMap.get(parameterIndex);
    }

    /**
     * 加密字符串参数
     * <p>
     * 根据参数索引查找对应的字段配置，如果字段需要加密则进行加密处理。
     * 使用统一的异常处理器和日志记录器。
     * </p>
     *
     * @param parameterIndex 参数索引（从1开始，对应JDBC规范）
     * @param value          原始值，可以为 null
     * @return 加密后的值，如果不需要加密或加密失败（根据策略）则返回原值
     */
    public String encryptString(int parameterIndex, String value) {
        if (value == null || !needEncrypt(parameterIndex)) {
            return value;
        }

        try {
            ColumnTableDto dto = parameterIndexToFieldMap.get(parameterIndex);
            if (dto != null) {
                String sourceColumn = dto.getSourceColumn();
                if (StrUtil.isNotBlank(dto.getSourceTableName()) && StrUtil.isNotBlank(sourceColumn)) {
                    Class<? extends FieldEncryptorStrategy> fieldEncryptorStrategy =
                            TableCache.getTableFieldEncryptStrategy(dto.getSourceTableName(), sourceColumn, this.datasourceId);

                    if (fieldEncryptorStrategy != null) {
                        FieldEncryptorStrategy strategy = StrategyCache.getStrategy(fieldEncryptorStrategy);
                        String encrypted = EncryptionHandler.handleEncryption(
                                value,
                                dto.getSourceTableName(),
                                sourceColumn,
                                () -> {
                                    String enc = strategy.encryption(value);
                                    // 使用统一的日志记录器
                                    SqlLogger.logEncryption(dto.getSourceTableName(), sourceColumn, 
                                            parameterIndex, this.datasourceId, value, enc);
                                    return enc;
                                },
                                null
                        );
                        return encrypted != null ? encrypted : value;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ENCRYPTION ERROR] Failed to encrypt string value at parameter index {} (datasource-id: {})", 
                    parameterIndex, this.datasourceId, e);
        }

        return value;
    }

    /**
     * 加密对象参数（通用方法）
     * <p>
     * 通用的对象加密方法，目前只处理字符串类型，其他类型直接返回原值。
     * </p>
     *
     * @param parameterIndex 参数索引（从1开始，对应JDBC规范）
     * @param value          原始值，可以为 null
     * @return 加密后的值，如果不需要加密或不是字符串类型则返回原值
     */
    public Object encryptObject(int parameterIndex, Object value) {
        if (value == null || !needEncrypt(parameterIndex)) {
            return value;
        }

        // 只处理字符串类型
        if (value instanceof String) {
            return encryptString(parameterIndex, (String) value);
        }

        return value;
    }

    /**
     * 加密 Reader 参数
     * <p>
     * 读取 Reader 内容并加密，支持指定最大读取长度。
     * </p>
     *
     * @param parameterIndex 参数索引（从1开始，对应JDBC规范）
     * @param reader        Reader 对象，不能为 null
     * @param length        最大读取长度，如果为 Long.MAX_VALUE 则读取全部
     * @return 加密后的字符串，如果不需要加密或转换失败则返回 null
     */
    public String encryptReader(int parameterIndex, Reader reader, long length) {
        if (reader == null || !needEncrypt(parameterIndex)) {
            return null;
        }

        try {
            String value = readerToString(reader, length);
            if (value != null) {
                return encryptString(parameterIndex, value);
            }
        } catch (Exception e) {
            log.warn("[ENCRYPTION ERROR] Failed to encrypt Reader value at parameter index {} (datasource-id: {})", 
                    parameterIndex, this.datasourceId, e);
        }

        return null;
    }

    /**
     * 加密 Clob 参数
     * <p>
     * 读取 Clob 内容并加密。
     * </p>
     *
     * @param parameterIndex 参数索引（从1开始，对应JDBC规范）
     * @param clob          Clob 对象，不能为 null
     * @return 加密后的字符串，如果不需要加密或转换失败则返回 null
     */
    public String encryptClob(int parameterIndex, Clob clob) {
        if (clob == null || !needEncrypt(parameterIndex)) {
            return null;
        }

        try {
            String value = clobToString(clob);
            if (value != null) {
                return encryptString(parameterIndex, value);
            }
        } catch (Exception e) {
            log.warn("[ENCRYPTION ERROR] Failed to encrypt Clob value at parameter index {} (datasource-id: {})", 
                    parameterIndex, this.datasourceId, e);
        }

        return null;
    }

    /**
     * 加密 NClob 参数
     * <p>
     * 读取 NClob 内容并加密。
     * </p>
     *
     * @param parameterIndex 参数索引（从1开始，对应JDBC规范）
     * @param nClob         NClob 对象，不能为 null
     * @return 加密后的字符串，如果不需要加密或转换失败则返回 null
     */
    public String encryptNClob(int parameterIndex, NClob nClob) {
        if (nClob == null || !needEncrypt(parameterIndex)) {
            return null;
        }

        try {
            String value = nClobToString(nClob);
            if (value != null) {
                return encryptString(parameterIndex, value);
            }
        } catch (Exception e) {
            log.warn("[ENCRYPTION ERROR] Failed to encrypt NClob value at parameter index {} (datasource-id: {})", 
                    parameterIndex, this.datasourceId, e);
        }

        return null;
    }

    /**
     * 将 Clob 转换为 String
     * <p>
     * 读取 Clob 的全部内容并转换为字符串。如果 Clob 长度超过 Integer.MAX_VALUE，会截断。
     * </p>
     *
     * @param clob Clob 对象，不能为 null
     * @return 字符串内容，如果转换失败返回 null
     */
    private String clobToString(Clob clob) {
        try {
            long length = clob.length();
            if (length > Integer.MAX_VALUE) {
                log.warn("[CLOB CONVERSION] Clob length {} exceeds Integer.MAX_VALUE, truncating", length);
                length = Integer.MAX_VALUE;
            }
            return clob.getSubString(1, (int) length);
        } catch (SQLException e) {
            log.error("[CLOB CONVERSION ERROR] Failed to convert Clob to String", e);
            return null;
        }
    }

    /**
     * 将 NClob 转换为 String
     * <p>
     * 读取 NClob 的全部内容并转换为字符串。如果 NClob 长度超过 Integer.MAX_VALUE，会截断。
     * </p>
     *
     * @param nClob NClob 对象，不能为 null
     * @return 字符串内容，如果转换失败返回 null
     */
    private String nClobToString(NClob nClob) {
        try {
            long length = nClob.length();
            if (length > Integer.MAX_VALUE) {
                log.warn("[NCLOB CONVERSION] NClob length {} exceeds Integer.MAX_VALUE, truncating", length);
                length = Integer.MAX_VALUE;
            }
            return nClob.getSubString(1, (int) length);
        } catch (SQLException e) {
            log.error("[NCLOB CONVERSION ERROR] Failed to convert NClob to String", e);
            return null;
        }
    }

    /**
     * 将 Reader 转换为 String
     * <p>
     * 读取 Reader 的内容并转换为字符串，支持指定最大读取长度。
     * </p>
     *
     * @param reader Reader 对象，不能为 null
     * @param length 最大读取长度（如果为 Long.MAX_VALUE 则读取全部）
     * @return 字符串内容，如果转换失败返回 null
     */
    private String readerToString(Reader reader, long length) {
        try {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            long totalRead = 0;
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (length != Long.MAX_VALUE && totalRead + read > length) {
                    int remaining = (int) (length - totalRead);
                    sb.append(buffer, 0, remaining);
                    totalRead += remaining;
                    break;
                }
                sb.append(buffer, 0, read);
                totalRead += read;
                if (length != Long.MAX_VALUE && totalRead >= length) {
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[READER CONVERSION ERROR] Failed to convert Reader to String", e);
            return null;
        }
    }
}

