package com.vpen.syncfield.linstener;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.vpen.syncfield.action.SearchRelatedClassesAction;
import com.vpen.syncfield.window.RelatedClassToolWindowFactory;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * 处理拖拽事件
 */
public class ClassDropTargetListener extends DropTargetAdapter {
    private final Project project;
    private final PsiClass targetClass; // 目标 Panel 的主类

    public ClassDropTargetListener(Project project, PsiClass targetClass) {
        this.project = project;
        this.targetClass = targetClass;
    }

    @Override
    public void drop(DropTargetDropEvent event) {
        try {
            Transferable transferable = event.getTransferable();
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                event.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

                //异步处理拖拽事件
                ApplicationManager.getApplication().executeOnPooledThread(() -> processFiles(files, event));
            } else {
                event.rejectDrop();
            }
        } catch (Exception e) {
            event.rejectDrop();
        }
    }

    private void processFiles(List<File> files, DropTargetDropEvent event) {
        for (File file : files) {
            // 通过文件获取 VirtualFile
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
            if (virtualFile != null) {
                // 读取 PsiFile 必须在 ReadAction 中进行
                ApplicationManager.getApplication().runReadAction(() -> {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (psiFile instanceof PsiJavaFile javaFile) {
                        for (PsiClass psiClass : javaFile.getClasses()) {
                            handleDroppedClass(psiClass);
                        }
                    }
                });
            }
        }
        // 确保 dropComplete 调用在 EDT 上执行
        ApplicationManager.getApplication().invokeLater(() -> {
            event.dropComplete(true);
        });
    }



    private void handleDroppedClass(@NotNull PsiClass psiClass) {
        if (psiClass.equals(targetClass)) {
            Messages.showWarningDialog("不能将自身添加为关联类！", "警告");
            return;
        }

        //  添加到 selectedRelatedClassesMap
        Set<PsiClass> relatedClasses =
                SearchRelatedClassesAction.selectedRelatedClassesMap.computeIfAbsent(targetClass, k -> new HashSet<>());

        if (relatedClasses.add(psiClass)) {
            RelatedClassToolWindowFactory.getInstance().refreshToolWindow(project, targetClass);
        }
    }
}
