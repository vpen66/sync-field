package com.vpen.syncfield.utils;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.PsiTreeUtil;
import com.vpen.syncfield.model.SyncConfig;
import com.vpen.syncfield.window.RelatedClassToolWindowFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.collections.CollectionUtils;

public class SyncUtil {
    public enum SyncType { ADD, DELETE, UPDATE }

    public static final Map<PsiClass, SyncConfig> SYNC_CONFIG_MAP = new ConcurrentHashMap<>();

    private static final SyncConfig syncConfig = new SyncConfig();

    public static SyncConfig getSyncConfig(PsiClass psiClass) {
        return SYNC_CONFIG_MAP.getOrDefault(psiClass, syncConfig);
    }

    // 获取 PsiClass
    public static PsiClass getPsiClass(PsiFile psiFile) {
        if (psiFile instanceof PsiJavaFile javaFile) {
            PsiClass[] classes = javaFile.getClasses();
            if (classes.length > 0) {
                return classes[0];
            }
        }
        return null;
    }

    // 同步逻辑 - 新增/删除
    public static void syncToRelatedClasses(PsiClass currentClass, String content, SyncType type) {
        Set<PsiClass> relatedClasses = RelatedClassToolWindowFactory.getSelectedItems(currentClass);
        for (PsiClass relatedClass : relatedClasses) {
            syncContentToClass(currentClass, relatedClass, content, type);
        }
        // 格式化代码
        CodeFormatterUtil.formatCode(relatedClasses, currentClass.getProject());
    }

    // 同步逻辑 - 修改
    public static void syncToRelatedClasses(PsiClass currentClass, String originalText, String updatedText) {
        Set<PsiClass> relatedClasses = RelatedClassToolWindowFactory.getSelectedItems(currentClass);
        for (PsiClass relatedClass : relatedClasses) {
            syncContentToClass(currentClass, relatedClass, originalText, updatedText);
        }
        // 格式化代码
        CodeFormatterUtil.formatCode(relatedClasses, currentClass.getProject());
    }

    public static void syncAll(PsiClass sourceClass, Set<PsiClass> psiClassSet) {
        if (sourceClass == null || psiClassSet == null || psiClassSet.isEmpty()) {
            return;
        }
        Project project = sourceClass.getProject();
        SyncConfig config = getSyncConfig(sourceClass);
        //操作类，需要这个线程操作
        psiClassSet.forEach(targetClass -> {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                // 同步类注解和注释 不同步类注解
                //syncClassAnnotationsAndComments(sourceClass, targetClass);

                // 同步字段
                syncFields(sourceClass.getFields(), targetClass, config);

                // 同步方法
                syncMethods(sourceClass.getMethods(), targetClass, config);

                // 同步 import
                syncImports(sourceClass, targetClass);
            });
        });

        CodeFormatterUtil.formatCode(psiClassSet, project);
    }

    /**
     * 同步 import 语句
     */
    private static void syncImports(PsiClass sourceClass, PsiClass targetClass) {
        // 获取源类的 PsiJavaFile
        PsiJavaFile sourceFile = (PsiJavaFile) sourceClass.getContainingFile();
        PsiJavaFile targetFile = (PsiJavaFile) targetClass.getContainingFile();

        if (sourceFile == null || targetFile == null) {
            return;
        }

        // 获取源类和目标类的 import 列表
        PsiImportList sourceImportList = sourceFile.getImportList();
        PsiImportList targetImportList = targetFile.getImportList();

        if (sourceImportList == null || targetImportList == null) {
            return;
        }

        // 创建一个 Set 来存储目标类已有的 import
        Set<String> existingImports = new HashSet<>();
        for (PsiImportStatement importStatement : targetImportList.getImportStatements()) {
            if (importStatement.getQualifiedName() != null) {
                existingImports.add(importStatement.getQualifiedName());
            }
        }

        // 遍历源类的 import 列表
        for (PsiImportStatement importStatement : sourceImportList.getImportStatements()) {
            String qualifiedName = importStatement.getQualifiedName();
            if (qualifiedName != null && !existingImports.contains(qualifiedName)) {
                // 如果目标类中不存在相同的 import，则添加
                targetImportList.add(importStatement);
            }
        }
    }


    private static void syncClassAnnotationsAndComments(PsiClass sourceClass, PsiClass targetClass,
                                                        SyncConfig syncConfig) {
        // 同步类注解
        syncModifierList(sourceClass, targetClass, syncConfig);

        if (!syncConfig.isComment()) {
            return;
        }

        // 同步类注释
        PsiComment sourceComment = getClassComment(sourceClass);
        PsiComment targetComment = getClassComment(targetClass);
        if (sourceComment != null && targetComment == null) {
            targetClass.addBefore(sourceComment, targetClass.getFirstChild());
        }
    }

    // 同步字段/方法的注解和注释
    private static void syncAnnotationsAndComments(PsiModifierListOwner sourceElement,
                                                   PsiModifierListOwner targetElement,
                                                   SyncConfig syncConfig) {

        syncModifierList(sourceElement, targetElement, syncConfig);
        if (!syncConfig.isComment()) {
            return;
        }
        // 同步注释
        PsiComment sourceComment = getElementComment(sourceElement);
        PsiComment targetComment = getElementComment(targetElement);
        if (sourceComment != null && targetComment == null) {
            try {
                targetElement.addBefore(sourceComment, targetElement.getFirstChild());
            } catch (Throwable ignored) {
            }
        }

    }

    /**
     * 同步注解
     */
    private static void syncModifierList(PsiModifierListOwner sourceElement,
                                         PsiModifierListOwner targetElement,
                                         SyncConfig syncConfig) {
        if (!syncConfig.isAnnotation()) {
            return;
        }
        PsiModifierList sourceModifierList = sourceElement.getModifierList();
        PsiModifierList targetModifierList = targetElement.getModifierList();

        if (sourceModifierList != null && targetModifierList != null) {
            PsiAnnotation[] sourceAnnotations = sourceModifierList.getAnnotations();
            PsiAnnotation[] targetAnnotations = targetModifierList.getAnnotations();
            Arrays.stream(targetAnnotations).forEach(PsiElement::delete);
            for (PsiAnnotation sourceAnnotation : sourceAnnotations) {
                String qualifiedName = sourceAnnotation.getQualifiedName();
                if (qualifiedName != null && targetModifierList.findAnnotation(qualifiedName) == null) {
                    try {
                        Set<String> annotations = syncConfig.getExtStr();

                        String annotationName = "@" + qualifiedName;
                        if (StringUtil.contains(qualifiedName, ".")) {
                            annotationName = "@" + StringUtil.substringAfterLast(qualifiedName, ".");
                        }
                        if (!annotations.contains(annotationName)) {
                            // 这里有问题，如果原来的重写了get set方法，然后对方是使用@Data,这里就不能操作了
                            PsiAnnotation newAnnotation = JavaPsiFacade.getElementFactory(targetElement.getProject())
                                    .createAnnotationFromText(sourceAnnotation.getText(), targetElement);
                            targetModifierList.addBefore(newAnnotation, targetModifierList.getFirstChild());
                        }
                    } catch (Throwable ignored) {

                    }
                }
            }
        }
    }


    private static void syncFields(PsiField[] fields, PsiClass targetClass, SyncConfig syncConfig) {
        if (!syncConfig.isField()) {
            return;
        }
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(targetClass.getProject());
        for (PsiField sourceField : fields) {
            PsiField targetField = targetClass.findFieldByName(sourceField.getName(), true);
            if (targetField == null) {
                // 复制新字段 createFieldFromText方法可以直接创建出字段、注解，注释
                PsiField newField = factory.createFieldFromText(sourceField.getText(), targetClass);
                removeCreateFromText(newField, syncConfig);
                targetClass.add(newField);
            } else {
                // 同步注解、注释
                syncAnnotationsAndComments(sourceField, targetField, syncConfig);
            }
        }
    }


    private static void removeCreateFromText(PsiElement newField, SyncConfig syncConfig) {
        // 如果不同步注释，就删除createFromText中的注释
        PsiElement firstChild = newField.getFirstChild();
        List<PsiElement> removeList = new ArrayList<>();
        do {
            // 不同步字段
            if (!syncConfig.isComment() && firstChild instanceof PsiComment) {
                removeList.add(firstChild);
            }
            // 不同步注解或者某些注解不同步
            if ((!syncConfig.isAnnotation() || CollectionUtils.isNotEmpty(syncConfig.getExtStr())) &&
                    firstChild instanceof PsiModifierList) {
                PsiAnnotation[] annotations = ((PsiModifierList) firstChild).getAnnotations();
                for (PsiAnnotation annotation : annotations) {
                    String qualifiedName = annotation.getQualifiedName();


                    if (qualifiedName == null) {
                        continue;
                    }
                    String annotationName = "@" + qualifiedName;
                    if (StringUtil.contains(qualifiedName, ".")) {
                        annotationName = "@" + StringUtil.substringAfterLast(qualifiedName, ".");
                    }
                    // 某些注解不同步
                    if (CollectionUtils.isEmpty(syncConfig.getExtStr())) {
                        removeList.add(annotation);
                    } else if (syncConfig.getExtStr()
                            .contains(annotationName)) {
                        removeList.add(annotation);
                    }
                }
            }

        } while (Objects.nonNull(firstChild = firstChild.getNextSibling()));

        if (removeList.isEmpty()) {
            return;
        }
        // 移除不同步
        for (PsiElement psiElement : removeList) {
            psiElement.delete();
        }
    }


    private static void syncMethods(PsiMethod[] methods, PsiClass targetClass, SyncConfig syncConfig) {
        if (!syncConfig.isMethod()) {
            return;
        }

        PsiElementFactory factory = JavaPsiFacade.getElementFactory(targetClass.getProject());
        for (PsiMethod sourceMethod : methods) {
            PsiMethod targetMethod = findMethodBySignature(targetClass, sourceMethod);
            boolean sourceMethodLombokGenerated = isLombokGenerated(sourceMethod);
            if (targetMethod == null && !sourceMethodLombokGenerated) {
                // 复制新方法
                PsiMethod newMethod = factory.createMethodFromText(sourceMethod.getText(), targetClass);
                removeCreateFromText(newMethod, syncConfig);
                targetClass.add(newMethod);
            } else {
                boolean targetLombokGenerated = isLombokGenerated(targetMethod);
                // 源重写了，目标是生成的，就生成新的方法
                if (!sourceMethodLombokGenerated && targetLombokGenerated) {
                    PsiMethod newMethod = factory.createMethodFromText(sourceMethod.getText(), targetClass);
                    removeCreateFromText(newMethod, syncConfig);
                    targetClass.add(newMethod);
                }
                if (!sourceMethodLombokGenerated && !targetLombokGenerated) {
                    // 同步注解、注释
                    syncAnnotationsAndComments(sourceMethod, targetMethod, syncConfig);
                }
            }
        }
    }


    // 获取类的注释
    private static PsiComment getClassComment(PsiClass psiClass) {
        PsiElement[] children = psiClass.getChildren();
        for (PsiElement child : children) {
            if (child instanceof PsiComment) {
                return (PsiComment) child;
            }
        }
        return null;
    }

    // 获取字段/方法的注释
    private static PsiComment getElementComment(PsiElement element) {
//        方法	作用	获取的内容	使用场景
//        getFirstChild()	获取当前元素的第一个子元素	注解、注释、类/方法/字段修饰符	获取类/方法/字段的注释
//        getPrevSibling()	获取当前元素的前一个同级元素	可能是 PsiComment、PsiWhiteSpace 查找前一个注释或空格节点
//        PsiElement prevSibling = element.getPrevSibling();
//        while (prevSibling != null) {
//            if (prevSibling instanceof PsiComment) {
//                return (PsiComment) prevSibling;
//            }
//            prevSibling = prevSibling.getPrevSibling();
//        }

        PsiElement firstChild = element.getFirstChild();
        do {
            if (Objects.isNull(firstChild)) {
                continue;
            }
            if (firstChild instanceof PsiComment) {
                return (PsiComment) firstChild;
            }
        } while (Objects.nonNull(firstChild = firstChild.getNextSibling()));
        return null;
    }

    // 根据方法签名查找目标类中的对应方法，这个方法会把lombok的get set方法也匹配上
    private static PsiMethod findMethodBySignature(PsiClass targetClass, PsiMethod searchMethod) {
        for (PsiMethod targetMethod : targetClass.getMethods()) {
            if (targetMethod.getName().equals(searchMethod.getName())
                    && targetMethod.getParameterList().getText().equals(searchMethod.getParameterList().getText())) {
                return targetMethod;
            }
        }
        return null;
    }

    private static boolean isLombokGenerated(PsiMethod method) {
        if (method == null) {
            return false;
        }
        // Lombok 生成的方法
        return method instanceof LightMethodBuilder;
    }


    // 根据方法签名查找目标类中的对应方法
    private static PsiMethod findMethodByName(PsiClass targetClass, PsiMethod sourceMethod) {
        PsiMethod[] methods = targetClass.findMethodsByName(sourceMethod.getName(), true);
        for (PsiMethod targetMethod : methods) {
            if (targetMethod.getParameterList().getText().trim()
                    .equals(sourceMethod.getParameterList().getText().trim())) {
                return targetMethod;
            }
        }
        return null;
    }


    private static void syncContentToClass(PsiClass sourceClass, PsiClass targetClass, String before, String after) {
        Project project = sourceClass.getProject();
        before = parse(before);
        after = parse(after);
        String finalBefore = before;
        String finalAfter = after;
        SyncConfig config = getSyncConfig(sourceClass);
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiClass beforePsiClass = createPsiClassFromText(project, targetClass, finalBefore);
            PsiClass afterPsiClass = createPsiClassFromText(project, targetClass, finalAfter);
            // 删除在添加
            removeFieldElement(beforePsiClass.getFields(), targetClass);
            removeMethodElement(beforePsiClass.getMethods(), targetClass);
            syncImports(sourceClass, targetClass);

            syncFields(afterPsiClass.getFields(), targetClass, config);
            syncMethods(afterPsiClass.getMethods(), targetClass, config);
        });
    }

    private static PsiClass createPsiClassFromText(Project project, PsiClass targetClass, String text) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        return PsiTreeUtil.findChildOfType(factory.createClassFromText(text, targetClass), PsiClass.class);
    }

    private static void syncContentToClass(PsiClass sourceClass, PsiClass targetClass, String content, SyncType type) {
        Project project = sourceClass.getProject();
        content = parse(content);
        String finalContent = content;
        SyncConfig config = getSyncConfig(sourceClass);
        WriteCommandAction.runWriteCommandAction(project, () -> {
            // 选中的
            PsiClass modifyClass = createPsiClassFromText(project, targetClass, finalContent);
            if (Objects.isNull(modifyClass)) {
                return;
            }
            if (type == SyncType.ADD) {
                syncFields(modifyClass.getFields(), targetClass, config);
                syncMethods(modifyClass.getMethods(), targetClass, config);
            } else if (type == SyncType.DELETE) {
                // 删除目标
                PsiField[] fields = modifyClass.getFields();
                PsiMethod[] methods = modifyClass.getMethods();
                // 删除源
                removeFieldElement(fields, targetClass);
                removeFieldElement(fields, sourceClass);
                removeMethodElement(methods, targetClass);
                removeMethodElement(methods, sourceClass);
            }
            syncImports(sourceClass, targetClass);
        });
    }


    /***
     * 删除字段
     * @param fields - 要删除的字段
     * @param targetPsiClass - 要删除的字段所在的类
     */
    private static void removeFieldElement(PsiField[] fields, PsiClass targetPsiClass) {
        if (Objects.isNull(fields)) {
            return;
        }
        for (PsiField field : fields) {
            // 只找当前类
            PsiField fieldByName = targetPsiClass.findFieldByName(field.getName(), false);
            if (Objects.nonNull(fieldByName)) {
                fieldByName.delete();
            }
        }
    }

    /**
     * 删除方法元素
     *
     * @param methods  - 要删除的方法
     * @param psiClass - 要删除的方法所在的类
     */
    private static void removeMethodElement(PsiMethod[] methods, PsiClass psiClass) {
        if (Objects.isNull(methods)) {
            return;
        }
        for (PsiMethod method : methods) {
            PsiMethod methodBySignature = findMethodBySignature(psiClass, method);
            if (Objects.nonNull(methodBySignature) && !isLombokGenerated(methodBySignature)) {
                methodBySignature.delete();
            }
        }
    }

    private static String replace(String commentText) {
        return commentText.replaceAll("\r\n", "\n");
    }

    public static String parse(String javaCode) {
        if (javaCode == null) {
            javaCode = "";
        }
        if (!javaCode.contains("class")) {
            javaCode = "public class Temp {\n" + javaCode + "\n}";
        }
        return replace(javaCode);
    }
}
