package com.vpen.syncfield.model;

import com.intellij.openapi.util.text.StringUtil;
import java.util.HashSet;
import java.util.Set;

/**
 * @author vpen
 * @date 2025/3/26 16:37
 */
public class SyncConfig {
    private boolean field = true;
    private boolean method = true;
    private boolean annotation = true;
    private boolean comment = true;
    private boolean classAnnotation = false;
    private String excludeStr;

    public boolean isField() {
        return field;
    }

    public void setField(boolean field) {
        this.field = field;
    }

    public boolean isMethod() {
        return method;
    }

    public void setMethod(boolean method) {
        this.method = method;
    }

    public boolean isAnnotation() {
        return annotation;
    }

    public void setAnnotation(boolean annotation) {
        this.annotation = annotation;
    }

    public boolean isComment() {
        return comment;
    }

    public void setComment(boolean comment) {
        this.comment = comment;
    }

    public Set<String> getExtStr() {
        if (StringUtil.isEmpty(this.excludeStr)) {
            return new HashSet<>();
        }
        return Set.of(excludeStr.split(";"));
    }

    public void setExtStr(String extStr) {
        this.excludeStr = extStr;
    }

    public boolean isClassAnnotation() {
        return classAnnotation;
    }
    public void setClassAnnotation(boolean classAnnotation) {
        this.classAnnotation = classAnnotation;
    }
}
