package com.vpen.syncfield.spliter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class SplitFieldSyncOpener {

    private static final Map<String, PsiField> lastFieldMap = new HashMap<>();

    // === 启动字段同步监听 ===
    public static void startFieldSync(Project project, PsiClass sourceClass, PsiClass targetClass) {
        Document sourceDoc = getDocumentFromClass(sourceClass);
        if (sourceDoc == null) {
            System.out.println("无法获取源文档！");
            return;
        }

        // === 监听文档变化 ===
        sourceDoc.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    PsiFile sourceFile = PsiDocumentManager.getInstance(project).getPsiFile(sourceDoc);
                    if (sourceFile instanceof PsiJavaFile) {
                        PsiElement elementAtOffset = sourceFile.findElementAt(event.getOffset());

                        // 检测是否修改字段
                        PsiField modifiedField = PsiTreeUtil.getParentOfType(elementAtOffset, PsiField.class);
                        if (modifiedField != null) {
                            handleFieldChange(modifiedField, targetClass);
                        }
                    }
                });
            }
        });
    }

    // === 处理字段变化逻辑 ===
    private static void handleFieldChange(PsiField modifiedField, PsiClass targetClass) {
        String fieldName = modifiedField.getName();

        Map<String, PsiField> targetFields = getFieldMap(targetClass);
        PsiField targetField = targetFields.get(fieldName);

        if (targetField == null) {
            // === 新增字段 ===
            addFieldToTargetClass(targetClass, modifiedField);
        } else if (!areFieldsEqual(modifiedField, targetField)) {
            // === 更新字段 ===
            replaceTargetField(targetClass, targetField, modifiedField);
        }

        // === 处理字段删除 ===
        syncDeletedFields(targetClass, modifiedField.getContainingClass());
    }

    // === 处理字段删除 ===
    private static void syncDeletedFields(PsiClass targetClass, PsiClass sourceClass) {
        Map<String, PsiField> sourceFields = getFieldMap(sourceClass);
        Map<String, PsiField> targetFields = getFieldMap(targetClass);

        for (String fieldName : targetFields.keySet()) {
            if (!sourceFields.containsKey(fieldName)) {
                // === 目标类中有但源类中没有 -> 删除 ===
                removeFieldFromTargetClass(targetClass, targetFields.get(fieldName));
            }
        }
    }

    // === 获取 PsiClass 对应的字段映射 ===
    private static Map<String, PsiField> getFieldMap(PsiClass psiClass) {
        Map<String, PsiField> fieldMap = new HashMap<>();
        for (PsiField field : psiClass.getFields()) {
            fieldMap.put(field.getName(), field);
        }
        return fieldMap;
    }

    // === 判断两个字段是否内容相同 ===
    private static boolean areFieldsEqual(PsiField field1, PsiField field2) {
        return field1.getText().equals(field2.getText());
    }

    // === 添加字段到目标类 ===
    private static void addFieldToTargetClass(PsiClass targetClass, PsiField sourceField) {
        ApplicationManager.getApplication().invokeLater(() ->
                WriteCommandAction.runWriteCommandAction(targetClass.getProject(), () -> {
                    targetClass.add(sourceField);
                    System.out.println("字段 " + sourceField.getName() + " 已添加到目标类");
                })
        );
    }

    // === 替换目标类中的字段 ===
    private static void replaceTargetField(PsiClass targetClass, PsiField targetField, PsiField sourceField) {
        ApplicationManager.getApplication().invokeLater(() ->
                WriteCommandAction.runWriteCommandAction(targetClass.getProject(), () -> {
                    targetField.replace(sourceField);
                    System.out.println("字段 " + sourceField.getName() + " 已更新");
                })
        );
    }

    // === 删除目标类的字段 ===
    private static void removeFieldFromTargetClass(PsiClass targetClass, PsiField targetField) {
        ApplicationManager.getApplication().invokeLater(() ->
                WriteCommandAction.runWriteCommandAction(targetClass.getProject(), targetField::delete)
        );
        System.out.println("字段 " + targetField.getName() + " 已删除");
    }

    // === 获取 PsiClass 的 Document ===
    private static Document getDocumentFromClass(PsiClass psiClass) {
        PsiFile psiFile = psiClass.getContainingFile();
        if (psiFile == null) return null;
        return PsiDocumentManager.getInstance(psiClass.getProject()).getDocument(psiFile);
    }

    // === 根据 VirtualFile 获取 PsiClass ===
    public static PsiClass getPsiClassFromFile(Project project, VirtualFile file) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile instanceof PsiJavaFile) {
            PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
            if (classes.length > 0) {
                return classes[0];
            }
        }
        return null;
    }

    // === 获取 VirtualFile ===
    public static VirtualFile getVirtualFile(PsiClass psiClass) {
        return SplitScreenOpener.getVirtualFile(psiClass);
    }
}


//package com.vpen.syncfield.spliter;
//
//import com.intellij.openapi.Disposable;
//import com.intellij.openapi.application.ApplicationManager;
//import com.intellij.openapi.command.WriteCommandAction;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.vfs.VirtualFile;
//import com.intellij.psi.PsiClass;
//import com.intellij.psi.PsiElement;
//import com.intellij.psi.PsiField;
//import com.intellij.psi.PsiFile;
//import com.intellij.psi.PsiJavaFile;
//import com.intellij.psi.PsiManager;
//import com.intellij.psi.PsiTreeChangeAdapter;
//import com.intellij.psi.PsiTreeChangeEvent;
//import com.intellij.psi.PsiTreeChangeListener;
//import org.jetbrains.annotations.NotNull;
//
//public class SplitFieldSyncOpener {
//
//    // 启动字段同步监听
//    public static void startFieldSync(Project project, PsiClass sourceClass, PsiClass targetClass, Disposable parentDisposable) {
//        PsiTreeChangeListener listener = new PsiTreeChangeAdapter() {
//
//            @Override
//            public void childAdded(@NotNull PsiTreeChangeEvent event) {
//                PsiElement element = event.getChild();
//                System.out.println("子节点已add：" + element.getText());
//                if (element instanceof PsiField) {
//                    syncAddedField((PsiField) element, targetClass);
//                }
//            }
//
//            @Override
//            public void childRemoved(@NotNull PsiTreeChangeEvent event) {
//                PsiElement element = event.getChild();
//                System.out.println("子节点已删除：" + element.getText());
//                if (element instanceof PsiField) {
//                    syncRemovedField((PsiField) element, targetClass);
//                }
//            }
//
//            @Override
//            public void childReplaced(@NotNull PsiTreeChangeEvent event) {
//                PsiElement oldElement = event.getOldChild();
//                PsiElement newElement = event.getNewChild();
//                System.out.println("oldElement子节点已删除：" + oldElement.getText());
//                System.out.println("newElement子节点已删除：" + newElement.getText());
//                if (oldElement instanceof PsiField && newElement instanceof PsiField) {
//                    syncUpdatedField((PsiField) oldElement, (PsiField) newElement, targetClass);
//                }
//            }
//        };
//
//        // ✅ 使用新的 API 进行监听
//        PsiManager.getInstance(project).addPsiTreeChangeListener(listener, parentDisposable);
//    }
//
//    // === 处理新增字段 ===
//    private static void syncAddedField(PsiField sourceField, PsiClass targetClass) {
//        if (hasSameField(targetClass, sourceField)) {
//            System.out.println("字段已存在，不再添加：" + sourceField.getName());
//            return;
//        }
//        ApplicationManager.getApplication().invokeLater(() ->
//                WriteCommandAction.runWriteCommandAction(targetClass.getProject(), () -> {
//                    targetClass.add(sourceField);
//                    System.out.println("新增字段同步到目标类：" + sourceField.getName());
//                })
//        );
//    }
//
//    // === 处理删除字段 ===
//    private static void syncRemovedField(PsiField sourceField, PsiClass targetClass) {
//        PsiField targetField = getFieldByName(targetClass, sourceField.getName());
//        if (targetField != null) {
//            ApplicationManager.getApplication().invokeLater(() ->
//                    WriteCommandAction.runWriteCommandAction(targetClass.getProject(), targetField::delete)
//            );
//            System.out.println("字段已删除：" + sourceField.getName());
//        }
//    }
//
//    // === 处理修改字段 ===
//    private static void syncUpdatedField(PsiField oldField, PsiField newField, PsiClass targetClass) {
//        PsiField targetField = getFieldByName(targetClass, oldField.getName());
//        if (targetField != null && !areFieldsEqual(targetField, newField)) {
//            ApplicationManager.getApplication().invokeLater(() ->
//                    WriteCommandAction.runWriteCommandAction(targetClass.getProject(), () -> {
//                        targetField.replace(newField);
//                        System.out.println("字段已更新：" + newField.getName());
//                    })
//            );
//        }
//    }
//
//    // === 判断目标类是否有相同字段 ===
//    private static boolean hasSameField(PsiClass targetClass, PsiField sourceField) {
//        for (PsiField field : targetClass.getFields()) {
//            if (field.getName().equals(sourceField.getName())) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    // === 获取目标类中的字段 ===
//    private static PsiField getFieldByName(PsiClass targetClass, String fieldName) {
//        for (PsiField field : targetClass.getFields()) {
//            if (field.getName().equals(fieldName)) {
//                return field;
//            }
//        }
//        return null;
//    }
//
//    // === 判断两个字段是否内容相同 ===
//    private static boolean areFieldsEqual(PsiField field1, PsiField field2) {
//        return field1.getText().equals(field2.getText());
//    }
//
//    // === 获取 PsiClass 的 VirtualFile ===
//    public static VirtualFile getVirtualFile(PsiClass psiClass) {
//        return SplitScreenOpener.getVirtualFile(psiClass);
//    }
//
//    // === 根据 VirtualFile 获取 PsiClass ===
//    public static PsiClass getPsiClassFromFile(Project project, VirtualFile file) {
//        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
//        if (psiFile instanceof PsiJavaFile) {
//            PsiClass[] classes = ((PsiJavaFile) psiFile).getClasses();
//            if (classes.length > 0) {
//                return classes[0];
//            }
//        }
//        return null;
//    }
//}
