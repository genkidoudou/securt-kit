package io.github.genkidoudou.core.crypto;

/**
 * 字段加解密统一门面
 * <p>
 * 将「查配置 → 取策略 → 执行加解密 → 失败策略 → 日志」收敛到唯一入口，
 * JDBC 拦截通道与 MyBatis 插件通道均通过该门面完成加解密，
 * 不允许各通道自行实现策略调用逻辑。
 * </p>
 *
 * @author hexlodev
 * @since 1.3.0
 */
public interface FieldCryptoService {

    /**
     * 判断字段是否需要加密
     *
     * @param table        表名，可包含库名/schema 前缀
     * @param column       列名
     * @param datasourceId 数据源标识，为空时按默认数据源处理
     * @return true 表示该字段配置了加密策略
     */
    boolean needEncrypt(String table, String column, String datasourceId);

    /**
     * 加密字段值
     *
     * @param table        表名
     * @param column       列名
     * @param plain        明文，可为 null
     * @param datasourceId 数据源标识，为空时按默认数据源处理
     * @return 密文；若无需加密或加密失败按降级策略处理，则返回原值
     */
    String encrypt(String table, String column, String plain, String datasourceId);

    /**
     * 解密字段值
     *
     * @param table        表名
     * @param column       列名
     * @param cipher       密文，可为 null
     * @param datasourceId 数据源标识，为空时按默认数据源处理
     * @return 明文；若无需解密或解密失败按降级策略处理，则返回原值（迁移期明文兼容）
     */
    String decrypt(String table, String column, String cipher, String datasourceId);
}
