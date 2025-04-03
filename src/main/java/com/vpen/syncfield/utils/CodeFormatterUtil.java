package com.vpen.syncfield.utils;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import java.util.Set;

public class CodeFormatterUtil {

    public static void formatPsiClass(PsiClass psiClass, Project project) {
        if (psiClass == null || project == null || psiClass.getContainingFile() == null) {
            return;
        }

        // 使用 WriteCommandAction 包裹修改操作
        WriteCommandAction.runWriteCommandAction(project, () -> {
            CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            PsiFile psiFile = psiClass.getContainingFile();
            // 格式化整个文件
            codeStyleManager.reformat(psiFile);
            // 优化导入
            if (psiFile instanceof PsiJavaFile) {
                // JavaCodeStyleManager
                JavaCodeStyleManager javaStyleManager = JavaCodeStyleManager.getInstance(project);
                javaStyleManager.optimizeImports(psiFile);
            }
        });
    }

    public static void formatCode(Set<PsiClass> psiClassSet, Project project) {
        for (PsiClass psiClass : psiClassSet) {
            formatPsiClass(psiClass, project);
        }
    }
}
