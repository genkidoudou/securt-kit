package io.github.genkidoudou.core.digest;

import io.github.genkidoudou.core.config.FieldEncryptorProperties;
import io.github.genkidoudou.core.strategy.FieldEncryptorStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 启动时完成继承与校验后的摘要规则。
 */
public final class ResolvedDigestRule {

    private final String tableName;
    private final List<String> sourceFields;
    private final String targetField;
    private final Class<? extends FieldEncryptorStrategy> strategyClass;
    private final FieldEncryptorProperties.PartialUpdate partialUpdate;
    private final boolean verifyOnRead;
    private final FieldEncryptorProperties.FailurePolicy failurePolicy;

    public ResolvedDigestRule(String tableName,
                              List<String> sourceFields,
                              String targetField,
                              Class<? extends FieldEncryptorStrategy> strategyClass,
                              FieldEncryptorProperties.PartialUpdate partialUpdate,
                              boolean verifyOnRead,
                              FieldEncryptorProperties.FailurePolicy failurePolicy) {
        this.tableName = tableName;
        this.sourceFields = Collections.unmodifiableList(new ArrayList<>(sourceFields));
        this.targetField = targetField;
        this.strategyClass = strategyClass;
        this.partialUpdate = partialUpdate;
        this.verifyOnRead = verifyOnRead;
        this.failurePolicy = failurePolicy;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getSourceFields() {
        return sourceFields;
    }

    public String getTargetField() {
        return targetField;
    }

    public Class<? extends FieldEncryptorStrategy> getStrategyClass() {
        return strategyClass;
    }

    public FieldEncryptorProperties.PartialUpdate getPartialUpdate() {
        return partialUpdate;
    }

    public boolean isVerifyOnRead() {
        return verifyOnRead;
    }

    public FieldEncryptorProperties.FailurePolicy getFailurePolicy() {
        return failurePolicy;
    }
}
