package com.vpen.syncfield.utils;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CompareFilesUtil {
    public static void compareTwoFiles(Project project, VirtualFile file1, VirtualFile file2) {
        if (file1 == null || file2 == null || project == null) {
            return;
        }

        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        var leftContent = contentFactory.create(project, file1);
        var rightContent = contentFactory.create(project, file2);

        SimpleDiffRequest
                request =
                new SimpleDiffRequest("Compare Files", leftContent, rightContent, file1.getName(), file2.getName());
        DiffManager.getInstance().showDiff(project, request);
    }

    public static void compareMultipleFiles(Project project, PsiClass currentClass, Set<PsiClass> files) {
        ApplicationManager.getApplication().invokeLater(()->{
            if (files == null || project == null || currentClass == null) {
                return;
            }

            DiffContentFactory contentFactory = DiffContentFactory.getInstance();
            List<ContentDiffRequest> diffRequests = new ArrayList<>();

            // 获取基准类的 VirtualFile 和文本内容
            PsiFile currentPsiFile = currentClass.getContainingFile();
            if (currentPsiFile == null) {
                return;
            }
            VirtualFile currentFile = currentPsiFile.getVirtualFile();
            if (currentFile == null) {
                return;
            }

            DiffContent currentContent = contentFactory.create(project, currentFile);

            // 遍历需要对比的 PsiClass
            for (PsiClass psiClass : files) {
                if (psiClass == null || psiClass.getContainingFile() == null) {
                    continue;
                }

                VirtualFile fileToCompare = psiClass.getContainingFile().getVirtualFile();
                if (fileToCompare == null) {
                    continue;
                }

                DiffContent compareContent = contentFactory.create(project, fileToCompare);

                SimpleDiffRequest request = new SimpleDiffRequest(
                        "Compare: " + currentClass.getName() + " vs " + psiClass.getName(),
                        currentContent, compareContent,
                        currentFile.getPresentableUrl(), fileToCompare.getPresentableUrl()
                );

                diffRequests.add(request);
            }

            if (!diffRequests.isEmpty()) {
                SimpleDiffRequestChain chain = new SimpleDiffRequestChain(diffRequests);
                DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT);
            }
        });

    }
}
