package com.vpen.syncfield.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.vpen.syncfield.linstener.CheckBoxMouseListener;
import com.vpen.syncfield.window.CheckBoxListRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.ListSelectionModel;

public class RelatedClassSearchDialog extends DialogWrapper {
    private final Project project;
    private final String inputClassName;
    private JBList<String> classList; // 使用 String 类型来显示全路径类名

    // 记录选中的类名
    private final Set<String> selectedItemsSet = new HashSet<>();

    // 存储类名与 PsiClass 对象的映射
    private final Map<String, PsiClass> classNameToPsiClassMap = new HashMap<>();
    private final PsiClass psiClass;

    public RelatedClassSearchDialog(Project project, String inputClassName, PsiClass psiClass) {
        super(project); // 必须调用父类构造方法
        this.project = project;
        this.inputClassName = inputClassName;
        this.psiClass = psiClass;
        setTitle("Select Related Classes");
        init(); // 初始化对话框
    }

    @Override
    protected JComponent createCenterPanel() {
        // 查找相关类并填充 JList
        List<String> relatedClassNames = findRelatedClasses(project, inputClassName);
        if (relatedClassNames.isEmpty()) {
            return new JLabel("No related classes found.");
        }

        // 使用 JBList 显示类的全路径名，并使用自定义的 CheckBoxListRenderer
        DefaultListModel<String> listModel = new DefaultListModel<>();
        relatedClassNames.forEach(listModel::addElement);
        classList = new JBList<>(listModel);
        classList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 设置自定义渲染器
        CheckBoxListRenderer checkBoxListRenderer = new CheckBoxListRenderer(selectedItemsSet);
        classList.setCellRenderer(checkBoxListRenderer);

        // 监听点击事件来切换复选框状态
        classList.addMouseListener(new CheckBoxMouseListener(classList, selectedItemsSet, listModel));

        return new JBScrollPane(classList); // 使用滚动面板显示类列表
    }

    /**
     * 查找相关类并填充类名
     */
    private List<String> findRelatedClasses(Project project, String className) {
        List<String> relatedClassNames = new ArrayList<>();

        // 使用 ProgressManager 异步执行任务
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            ApplicationManager.getApplication().runReadAction(() -> {
                GlobalSearchScope scope = GlobalSearchScope.allScope(project); // 全局范围

                // 获取项目的所有源代码文件夹
                VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();

                for (VirtualFile sourceRoot : sourceRoots) {
                    // 获取文件夹中的所有文件
                    PsiDirectory directory = PsiManager.getInstance(project).findDirectory(sourceRoot);
                    if (directory != null) {
                        // 递归查找目录中的所有 PsiClass
                        findClassesInDirectory(directory, className, relatedClassNames, scope);
                    }
                }
            });
        }, "查找相关类", true, project);

        return relatedClassNames;
    }


    /**
     * 递归查找目录中的类
     */
    private void findClassesInDirectory(PsiDirectory directory, String className, List<String> relatedClassNames,
                                        GlobalSearchScope scope) {

        for (PsiFile file : directory.getFiles()) {
            // 如果是 Java 类文件
            if (file instanceof PsiJavaFile javaFile) {
                PsiClass[] classes = javaFile.getClasses();
                for (PsiClass psiClass : classes) {
                    String qualifiedName = psiClass.getQualifiedName();
                    if (qualifiedName == null) {
                        continue;
                    }
                    // 模糊匹配类名，并排除自身
                    if (qualifiedName.contains(className) &&
                            !qualifiedName.equals(this.psiClass.getQualifiedName())) {
                        relatedClassNames.add(qualifiedName);
                        classNameToPsiClassMap.put(qualifiedName, psiClass);
                    }
                }
            }
        }
        // 递归查找子目录
        for (PsiDirectory subDirectory : directory.getSubdirectories()) {
            findClassesInDirectory(subDirectory, className, relatedClassNames, scope);
        }
    }

    /**
     * 返回用户选择的 PsiClass
     */
    public List<PsiClass> getSelectedClasses() {
        // 根据 selectedItemsSet 获取用户选中的类
        return selectedItemsSet.stream()
                .map(classNameToPsiClassMap::get)
                .filter(Objects::nonNull) // 过滤掉空值
                .collect(Collectors.toList());
    }

    @Override
    protected String getHelpId() {
        return super.getHelpId(); // 你可以根据需要返回帮助 ID
    }
}
