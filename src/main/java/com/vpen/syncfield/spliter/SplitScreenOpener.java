package com.vpen.syncfield.spliter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

public class SplitScreenOpener {

    public static final Map<PsiClass, EditorWindow> editorWindowMap = new ConcurrentHashMap<>();


    public static void openClassesInSplit(Project project, Set<PsiClass> classes) {
        AtomicReference<Editor> mainEditor = new AtomicReference<>();
        // 确保在UI线程执行
        ApplicationManager.getApplication().invokeLater(() -> {
            // 获取文件编辑器管理器
            FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);

            // 获取当前活动窗口
            EditorWindow currentWindow = fileEditorManager.getCurrentWindow();

            if (currentWindow == null || classes == null || classes.isEmpty()) {
                return;
            }
            mainEditor.set(getEditorFromWindow(currentWindow));
            if (mainEditor.get() == null) {
                return;
            }

            // 依次拆分并打开其他文件
            EditorWindow previousWindow = currentWindow;
            for (PsiClass psiClass : classes) {
                // 已经打开的文件跳过
                if (Objects.nonNull(editorWindowMap.get(psiClass))) {
                    continue;
                }
                VirtualFile file = getVirtualFile(psiClass);
                if (file != null) {
                    // 向右拆分窗口并打开文件
                    if (previousWindow != null) {
                        EditorWindow newEditorWindow = previousWindow.split(SwingConstants.VERTICAL, true, file, true);
                        editorWindowMap.put(psiClass, newEditorWindow);
                        ApplicationManager.getApplication().invokeLater(() -> {
                            // 通过 MessageBus 注册监听器
                            MessageBusConnection connection = project.getMessageBus().connect();
                            // 订阅 FileEditorManagerListener 事件
                            connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                                    new FileEditorManagerListener() {
                                        @Override
                                        public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                                            Editor secondEditor = getEditorFromWindow(newEditorWindow);
                                            if (secondEditor != null) {
                                                //SyncUtil.setupSyncBetweenEditors(mainEditor.get(), secondEditor);
                                                // 关闭连接，防止多次监听
                                                connection.disconnect();
                                            }
                                        }
                                    });

                        });
                        previousWindow = newEditorWindow;
                    }
                }
            }

        });


    }

    public static void closeSplit(PsiClass psiClass) {
        EditorWindow editorWindow = editorWindowMap.get(psiClass);
        if (editorWindow != null) {
            editorWindow.closeFile(Objects.requireNonNull(getVirtualFile(psiClass)));
            editorWindowMap.remove(psiClass);
        }
    }

    public static void closeSplit(Set<PsiClass> set) {
        for (PsiClass psiClass : set) {
            closeSplit(psiClass);
        }
    }


    public static VirtualFile getVirtualFile(PsiClass psiClass) {
        if (!psiClass.isValid()) {
            return null;
        }
        PsiFile containingFile = psiClass.getContainingFile();
        return containingFile != null ? containingFile.getVirtualFile() : null;
    }

    // 从EditorWindow获取Editor实例
    private static Editor getEditorFromWindow(EditorWindow window) {
        FileEditor fileEditor = Objects.requireNonNull(window.getSelectedComposite()).getSelectedEditor();
        return (fileEditor instanceof TextEditor) ?
                ((TextEditor) fileEditor).getEditor() : null;
    }

    private static boolean isSyncing = false;

    private static void setupSyncBetweenEditors(Editor source, Editor target) {
        Document sourceDoc = source.getDocument();
        Document targetDoc = target.getDocument();

        sourceDoc.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if (isSyncing) return;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!targetDoc.isWritable()) return;

                    WriteCommandAction.runWriteCommandAction(null, () -> {
                        isSyncing = true;
                        try {
                            // 获取源文档光标位置的行列
                            int sourceOffset = source.getCaretModel().getOffset();
                            int sourceLine = sourceDoc.getLineNumber(sourceOffset);
                            int sourceColumn = sourceOffset - sourceDoc.getLineStartOffset(sourceLine);

                            // 映射到目标文档的行列
                            int targetLine = Math.min(sourceLine, targetDoc.getLineCount() - 2);
                            int targetLineStart = (targetLine >= 0) ? targetDoc.getLineStartOffset(targetLine) : 0;
                            int targetColumn;
                            int targetOffset;

                            if (targetLine >= targetDoc.getLineCount()) {
                                // 目标行不存在，追加到末尾
                                targetOffset = targetDoc.getTextLength();
                            } else {
                                int maxColumn = targetDoc.getLineEndOffset(targetLine) - targetLineStart;
                                targetColumn = Math.min(sourceColumn, maxColumn);
                                targetOffset = targetLineStart + targetColumn;
                            }

                            // 执行同步操作
                            if (event.getOldLength() == 0) {
                                // 插入操作
                                targetDoc.insertString(targetOffset, event.getNewFragment());
                            } else {
                                // 删除操作
                                int targetDeleteEnd = Math.min(targetOffset + event.getOldLength(), targetDoc.getTextLength());
                                targetDoc.deleteString(targetOffset, targetDeleteEnd);
                            }
                        } finally {
                            isSyncing = false;
                        }
                    });
                });
            }
        });

        // 光标同步逻辑
        source.getCaretModel().addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                int sourceOffset = Objects.requireNonNull(event.getCaret()).getOffset();
                int sourceLine = sourceDoc.getLineNumber(sourceOffset);
                int sourceColumn = sourceOffset - sourceDoc.getLineStartOffset(sourceLine);

                int targetLine = Math.min(sourceLine, targetDoc.getLineCount() - 2);
                int targetLineStart = (targetLine >= 0) ? targetDoc.getLineStartOffset(targetLine) : 0;
                int targetColumn;
                int targetOffset;

                if (targetLine >= targetDoc.getLineCount()) {
                    targetOffset = targetDoc.getTextLength();
                } else {
                    int maxColumn = targetDoc.getLineEndOffset(targetLine) - targetLineStart;
                    targetColumn = Math.min(sourceColumn, maxColumn);
                    targetOffset = targetLineStart + targetColumn;
                }

                target.getCaretModel().moveToOffset(targetOffset);
                target.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
            }
        });
    }


//    private static void setupSyncBetweenEditors(Editor source, Editor target) {
//        Document sourceDoc = source.getDocument();
//        Document targetDoc = target.getDocument();
//
//        sourceDoc.addDocumentListener(new DocumentListener() {
//            @Override
//            public void documentChanged(@NotNull DocumentEvent event) {
//                ApplicationManager.getApplication().invokeLater(() -> {
//                    if (!targetDoc.isWritable()) {
//                        return;
//                    }
//                    WriteCommandAction.runWriteCommandAction(null, () -> {
//                        // 获取变更的详细信息
//                        int offset = event.getOffset();
//                        int oldLength = event.getOldLength();
//                        CharSequence newFragment = event.getNewFragment();
//
//                        // 应用增量更新到目标文档
//                        targetDoc.replaceString(offset, offset + oldLength, newFragment);
//                    });
//                });
//            }
//        });
//
//        // 光标同步逻辑保持不变
//        source.getCaretModel().addCaretListener(new CaretListener() {
//            @Override
//            public void caretPositionChanged(@NotNull CaretEvent event) {
//                int sourceOffset = Objects.requireNonNull(event.getCaret()).getOffset();
//                target.getCaretModel().moveToOffset(Math.max(sourceOffset, target.getCaretModel().getOffset()));
//                target.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
//            }
//        });
//    }

}