package io.github.hexlodev.core.digest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SQL 摘要列改写结果。
 */
public final class DigestRewriteResult {

    private final String sql;
    private final List<Integer> appendedParameterIndexes;
    private final List<String> appendedTargetFields;
    private final boolean rewritten;
    private final String warnMessage;

    public DigestRewriteResult(String sql,
                               List<Integer> appendedParameterIndexes,
                               boolean rewritten,
                               String warnMessage) {
        this(sql, appendedParameterIndexes, Collections.<String>emptyList(), rewritten, warnMessage);
    }

    public DigestRewriteResult(String sql,
                               List<Integer> appendedParameterIndexes,
                               List<String> appendedTargetFields,
                               boolean rewritten,
                               String warnMessage) {
        this.sql = sql;
        this.appendedParameterIndexes = appendedParameterIndexes == null
                ? Collections.<Integer>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(appendedParameterIndexes));
        this.appendedTargetFields = appendedTargetFields == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(appendedTargetFields));
        this.rewritten = rewritten;
        this.warnMessage = warnMessage;
    }

    public String getSql() {
        return sql;
    }

    public List<Integer> getAppendedParameterIndexes() {
        return appendedParameterIndexes;
    }

    public List<String> getAppendedTargetFields() {
        return appendedTargetFields;
    }

    public boolean isRewritten() {
        return rewritten;
    }

    public String getWarnMessage() {
        return warnMessage;
    }

    static DigestRewriteResult unchanged(String sql) {
        return new DigestRewriteResult(sql, Collections.<Integer>emptyList(), false, null);
    }

    static DigestRewriteResult notRewritten(String sql, String warnMessage) {
        return new DigestRewriteResult(sql, Collections.<Integer>emptyList(), false, warnMessage);
    }

    static DigestRewriteResult rewritten(String sql,
                                         List<Integer> appendedParameterIndexes,
                                         List<String> appendedTargetFields) {
        return new DigestRewriteResult(
                sql, appendedParameterIndexes, appendedTargetFields, true, null);
    }
}
