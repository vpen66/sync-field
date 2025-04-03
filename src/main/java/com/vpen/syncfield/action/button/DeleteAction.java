package com.vpen.syncfield.action.button;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.vpen.syncfield.action.SearchRelatedClassesAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author vpen
 * @date 2025/3/25 21:28
 */

public class DeleteAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        SearchRelatedClassesAction action = SearchRelatedClassesAction.getInstance();
        if (action != null) {
            action.doDelete();
        }
    }
}
