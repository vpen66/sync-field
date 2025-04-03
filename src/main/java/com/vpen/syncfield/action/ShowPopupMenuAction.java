package com.vpen.syncfield.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author vpen
 * @date 2025/3/24 16:24
 */

public class ShowPopupMenuAction extends AnAction {


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        SearchRelatedClassesAction action = SearchRelatedClassesAction.getInstance();
        if (action != null) {
            action.showPopupMenu();
        }
    }
}
