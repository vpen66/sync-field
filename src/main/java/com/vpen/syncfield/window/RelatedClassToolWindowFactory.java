package com.vpen.syncfield.window;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.vpen.syncfield.action.SearchRelatedClassesAction;
import com.vpen.syncfield.linstener.CheckBoxMouseListener;
import com.vpen.syncfield.linstener.ClassDropTargetListener;
import com.vpen.syncfield.model.SyncConfig;
import com.vpen.syncfield.spliter.SplitScreenOpener;
import com.vpen.syncfield.utils.CodeFormatterUtil;
import com.vpen.syncfield.utils.CompareFilesUtil;
import com.vpen.syncfield.utils.SyncUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 关联类 ToolWindow 工厂
 */
public class RelatedClassToolWindowFactory implements ToolWindowFactory {

    private static final String TOOL_WINDOW_ID = "RCW";

    private static RelatedClassToolWindowFactory instance; // 方便外部调用

    /**
     * 当前选中的类及其打勾类
     */
    private static final Map<PsiClass, Set<String>> selectedItemsMap = new ConcurrentHashMap<>();

    public RelatedClassToolWindowFactory() {
        instance = this; // 赋值实例
    }

    public static RelatedClassToolWindowFactory getInstance() {
        return instance;
    }

    public static Set<PsiClass> getSelectedItems(PsiClass selectClass) {
        Set<PsiClass> rcSet = SearchRelatedClassesAction.selectedRelatedClassesMap.get(selectClass);
        Set<String> selectSet = selectedItemsMap.get(selectClass);
        return rcSet.stream().filter(psiClass -> selectSet.contains(psiClass.getQualifiedName())).collect(
                Collectors.toSet());
    }

    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 在此注册 PsiTreeChangeListener
        PsiManager psiManager = PsiManager.getInstance(project);

        //PsiChangeListener.registerDocumentListener(project);

        ContentManager contentManager = toolWindow.getContentManager();

        // 清除旧的内容，避免重复
        contentManager.removeAllContents(true);


        // 遍历 selectedRelatedClassesMap，创建每个类独立的 Tab
        for (Map.Entry<PsiClass, Set<PsiClass>> entry : SearchRelatedClassesAction.selectedRelatedClassesMap.entrySet()) {
            PsiClass className = entry.getKey();  // 当前类的名字
            Set<PsiClass> relatedClasses = entry.getValue();  // 获取与该类相关的所有类

            JPanel panel = createClassPanel(project, className, relatedClasses);  // 创建类的 UI 面板

            // 创建并添加 Content
            Content content = contentManager.getFactory().createContent(panel, className.getQualifiedName(), false);
            contentManager.addContent(content);
        }
    }

    /**
     * 为指定 PsiClass 创建 UI 面板
     */
    private JPanel createClassPanel(Project project, PsiClass selectClass, Set<PsiClass> relatedClasses) {
        // 过滤掉自身
        relatedClasses = relatedClasses.stream()
                .filter(rc -> !Objects.equals(rc.getQualifiedName(), selectClass.getQualifiedName())).collect(
                        Collectors.toSet());


        JPanel panel = new JPanel(new BorderLayout());
        // 设置拖拽监听，当有类拖拽进来的时候，我添加到对应的关联关系中
        panel.setDropTarget(
                new DropTarget(panel, DnDConstants.ACTION_LINK, new ClassDropTargetListener(project, selectClass),
                        true));

        // 关联类的标签
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JBList<String> classList = new JBList<>(listModel);
        classList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // 默认全选中
        Set<String> selectedItemsSet =
                CollectionUtils.isNotEmpty(relatedClasses) ?
                        relatedClasses.stream().map(PsiClass::getQualifiedName).collect(Collectors.toSet()) :
                        new HashSet<>();

        //自定义多选操作 CheckBoxListRenderer 渲染，
        classList.setCellRenderer(new CheckBoxListRenderer(selectedItemsSet));

        // 把全路径类名添加到列表中
        for (PsiClass relatedClass : relatedClasses) {
            String classQualifiedName = relatedClass.getQualifiedName();
            listModel.addElement(classQualifiedName);
        }

        //  添加点击监听器：切换勾选状态
        classList.addMouseListener(new CheckBoxMouseListener(classList, selectedItemsSet, listModel));
        // 当前选择的哪些类
        selectedItemsMap.put(selectClass, selectedItemsSet);
        // 创建移除关联类的按钮
        JPanel removeClassPanel = getPanel(new JButton("取消关联"), selectClass, classList, selectedItemsSet,
                (selected, classSet) -> {
                    classSet.removeIf(psiClass -> selected.contains(psiClass.getQualifiedName()));
                    // 重新刷新窗口
                    refreshToolWindow(project, selectClass);
                }
        );

        // 打开多个拆分到窗口
        JPanel openPanel = getPanel(new JButton("打开拆分"), selectClass, classList, selectedItemsSet,
                (selected, classSet) -> {
                    Set<PsiClass> openSpLitSet =
                            classSet.stream().filter(psiClass -> selected.contains(psiClass.getQualifiedName()))
                                    .collect(
                                            Collectors.toSet());
                    //打开之前，先关闭是否已经有的
                    SplitScreenOpener.closeSplit(openSpLitSet);
                    SplitScreenOpener.openClassesInSplit(project, openSpLitSet);
                }
        );

        // 比较文件
        JPanel ComparePanel = getPanel(new JButton("比较文件"), selectClass, classList, selectedItemsSet,
                (selected, classSet) -> {
                    Set<PsiClass> openSpLitSet =
                            classSet.stream().filter(psiClass -> selected.contains(psiClass.getQualifiedName()))
                                    .collect(
                                            Collectors.toSet());
                    CompareFilesUtil.compareMultipleFiles(project, selectClass, openSpLitSet);
                }
        );

        // 关闭拆分按钮
        JPanel closePanel = getPanel(new JButton("关闭拆分"), selectClass, classList, selectedItemsSet,
                (selected, classSet) -> {
                    Set<PsiClass> closeList =
                            classSet.stream().filter(psiClass -> selected.contains(psiClass.getQualifiedName()))
                                    .collect(Collectors.toSet());
                    SplitScreenOpener.closeSplit(closeList);
                }
        );

        // 格式化代码
        JPanel formatPanel = getPanel(new JButton("格式化代码"), selectClass, classList, selectedItemsSet,
                (selected, classSet) -> {
                    Set<PsiClass> psiClassSet =
                            classSet.stream().filter(psiClass -> selected.contains(psiClass.getQualifiedName()))
                                    .collect(Collectors.toSet());
                    CodeFormatterUtil.formatCode(psiClassSet, project);
                }
        );
        // 同步所有字段
        JPanel syncAll = getPanel(new JButton("同步所有"), selectClass, classList, selectedItemsSet,
                (selected, classSet) -> {
                    Set<PsiClass> psiClassSet =
                            classSet.stream().filter(psiClass -> selected.contains(psiClass.getQualifiedName()))
                                    .collect(Collectors.toSet());
                    SyncUtil.syncAll(selectClass, psiClassSet);
                }
        );

        JPanel containerPanel = new JPanel();
        containerPanel.setLayout(new BorderLayout()); // 改成 BorderLayout 垂直排列

        // 创建配置面板
        JPanel configPanel = getConfigPanel(selectClass);


        // 创建按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));  // 保持按钮水平排列
        buttonPanel.add(removeClassPanel);
        buttonPanel.add(ComparePanel);
        buttonPanel.add(syncAll);
        buttonPanel.add(formatPanel);
        buttonPanel.add(openPanel);
        buttonPanel.add(closePanel);

        // 将配置面板添加到容器的 NORTH 区域
        containerPanel.add(configPanel, BorderLayout.NORTH);

        // 将按钮面板添加到容器的 CENTER 区域
        containerPanel.add(buttonPanel, BorderLayout.CENTER);

        // 将容器面板添加到主面板的 SOUTH 区域
        panel.add(new JBScrollPane(classList), BorderLayout.CENTER);
        panel.add(containerPanel, BorderLayout.SOUTH);

        // 搜索按钮
        JButton searchButton = getSearchButton(project, selectClass);
        JCheckBox checkBoxAllButton = getCheckBoxAllButton(project, selectClass, classList, listModel);
        JPanel addButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButtonPanel.add(checkBoxAllButton);
        addButtonPanel.add(searchButton);
        panel.add(addButtonPanel, BorderLayout.NORTH);

        // 更新面板
        panel.revalidate();
        panel.repaint();

        return panel;
    }

    private JButton getSearchButton(Project project, PsiClass selectClass) {
        JButton searchButton = new JButton(AllIcons.Actions.Find);
        // 去掉按钮的边框和背景，使其看起来更像图标
        searchButton.setToolTipText("搜索");
        searchButton.setPreferredSize(new Dimension(30, 30)); // 设置按钮大小
        searchButton.addActionListener(e -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                ApplicationManager.getApplication().runReadAction(() -> {
                    String inputClassName =
                            SearchRelatedClassesAction.getInputClassName(project, selectClass.getName());
                    SearchRelatedClassesAction.searchClass(project, inputClassName, selectClass);
                });
                refreshToolWindow(project, selectClass);
            });
        });
        return searchButton;
    }

    private JCheckBox getCheckBoxAllButton(Project project, PsiClass selectClass,
                                           JBList<String> classList,
                                           DefaultListModel<String> listModel) {
        JCheckBox checkBoxAllButton = new JCheckBox("", true);
        checkBoxAllButton.setToolTipText("全选/不选/反选");
        checkBoxAllButton.setPreferredSize(new Dimension(30, 30)); // 设置按钮大小

        // 监听点击事件
        checkBoxAllButton.addActionListener(e -> {
            Set<String> selectedItemsSet = selectedItemsMap.computeIfAbsent(selectClass, k -> new HashSet<>());

            boolean hasSelected = !selectedItemsSet.isEmpty(); // 检查是否有选中项
            boolean isAllSelected = selectedItemsSet.size() == listModel.getSize(); // 检查是否全选

            if (hasSelected && !isAllSelected) {
                //  反选当前选中的项
                Set<String> tempSet = new HashSet<>(selectedItemsSet); // 备份当前选中项
                selectedItemsSet.clear();
                for (int i = 0; i < listModel.getSize(); i++) {
                    String item = listModel.getElementAt(i);
                    if (!tempSet.contains(item)) {
                        selectedItemsSet.add(item); // 未选中的变成选中
                    }
                }
            } else if (isAllSelected) {
                //  如果是全选状态，则清空所有选中项
                selectedItemsSet.clear();
            } else {
                //  如果没有选中项，则默认全选
                for (int i = 0; i < listModel.getSize(); i++) {
                    selectedItemsSet.add(listModel.getElementAt(i));
                }
            }

            // 触发重新渲染，更新复选框状态
            classList.repaint();
        });

        return checkBoxAllButton;
    }


    private static JPanel getConfigPanel(PsiClass selectClass) {
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JCheckBox syncFieldsCheckbox = new JCheckBox("字段", true);
        JCheckBox syncMethodsCheckbox = new JCheckBox("方法", true);
        JCheckBox syncCommentsCheckbox = new JCheckBox("注释", true);
        JCheckBox syncAnnotationsCheckbox = new JCheckBox("注解", true);
        JTextField annotationFilterField = new JTextField(15);
        annotationFilterField.setToolTipText("排除某种注解，用;分隔");

        syncFieldsCheckbox.addActionListener(action -> {
            SyncConfig config = SyncUtil.SYNC_CONFIG_MAP.get(selectClass);
            if (Objects.isNull(config)) {
                config = new SyncConfig();
                SyncUtil.SYNC_CONFIG_MAP.put(selectClass, config);
            }
            config.setField(syncFieldsCheckbox.isSelected());
        });
        syncMethodsCheckbox.addActionListener(action -> {
            SyncConfig config = SyncUtil.SYNC_CONFIG_MAP.get(selectClass);
            if (Objects.isNull(config)) {
                config = new SyncConfig();
                SyncUtil.SYNC_CONFIG_MAP.put(selectClass, config);
            }
            config.setMethod(syncMethodsCheckbox.isSelected());
        });
        syncCommentsCheckbox.addActionListener(action -> {
            SyncConfig config = SyncUtil.SYNC_CONFIG_MAP.get(selectClass);
            if (Objects.isNull(config)) {
                config = new SyncConfig();
                SyncUtil.SYNC_CONFIG_MAP.put(selectClass, config);
            }
            config.setComment(syncCommentsCheckbox.isSelected());
        });
        syncAnnotationsCheckbox.addActionListener(action -> {
            SyncConfig config = SyncUtil.SYNC_CONFIG_MAP.get(selectClass);
            if (Objects.isNull(config)) {
                config = new SyncConfig();
                SyncUtil.SYNC_CONFIG_MAP.put(selectClass, config);
            }
            config.setAnnotation(syncAnnotationsCheckbox.isSelected());
        });
        annotationFilterField.addActionListener(action -> {

        });

        // 添加 FocusListener
        annotationFilterField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                // 这里是获得焦点时的逻辑（如果不需要可留空）
            }

            @Override
            public void focusLost(FocusEvent e) {
                // 失去焦点时触发的逻辑
                SyncConfig config = SyncUtil.SYNC_CONFIG_MAP.get(selectClass);
                if (Objects.isNull(config)) {
                    config = new SyncConfig();
                    SyncUtil.SYNC_CONFIG_MAP.put(selectClass, config);
                }
                config.setExtStr(annotationFilterField.getText());
            }
        });
        configPanel.add(syncFieldsCheckbox);
        configPanel.add(syncMethodsCheckbox);
        configPanel.add(syncCommentsCheckbox);
        configPanel.add(syncAnnotationsCheckbox);
        configPanel.add(annotationFilterField);
        return configPanel;
    }

    private JPanel getPanel(JButton button, PsiClass selectClass, JBList<String> classList,
                            Set<String> selectedClassNames,
                            BiConsumer<Set<String>, Set<PsiClass>> biConsumer) {
        button.addActionListener(e -> {
            // 获取选中类名列表
            //List<String> selectedClassNames = classList.getSelectedValuesList();

            // 获取当前类的关联类集合
            Set<PsiClass> classes = SearchRelatedClassesAction.selectedRelatedClassesMap.get(selectClass);
            if (classes != null) {
                biConsumer.accept(selectedClassNames, classes);
            }
        });
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
        buttonPanel.add(button);
        return buttonPanel;
    }

    /**
     * 刷新 ToolWindow，使标签页更新
     */
    public void refreshToolWindow(Project project, PsiClass selectClass) {
        // 在 EDT 中执行 UI 操作
        // 如果你需要修改 UI 组件（例如 JPanel 或 PsiClass），可以将这些操作包裹在 invokeLater 中，从而确保它们在 EDT 上执行
        ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
            if (toolWindow != null) {
                ContentManager contentManager = toolWindow.getContentManager();

                ApplicationManager.getApplication().runReadAction(() -> {
                    // 遍历 selectedRelatedClassesMap，更新内容
                    for (Map.Entry<PsiClass, Set<PsiClass>> entry : SearchRelatedClassesAction.selectedRelatedClassesMap.entrySet()) {
                        PsiClass className = entry.getKey();  // 当前类的名字
                        Set<PsiClass> relatedClasses = entry.getValue();  // 获取与该类相关的所有类

                        JPanel panel = createClassPanel(project, className, relatedClasses);  // 创建类的 UI 面板

                        // 更新或添加内容，避免移除已有内容
                        boolean contentExists = false;
                        for (Content content : contentManager.getContents()) {
                            if (content.getDisplayName().equals(className.getQualifiedName())) {
                                // 如果已经有该类的 Tab，则更新其内容
                                content.setComponent(panel);
                                contentExists = true;
                                break;
                            }
                        }

                        if (!contentExists) {
                            // 如果没有该类的 Tab，则创建新的 Tab
                            Content content =
                                    contentManager.getFactory()
                                            .createContent(panel, className.getQualifiedName(), false);
                            // 为该标签设置 Disposer，当标签关闭时清除关联数据
                            Disposer.register(content, () -> {
                                // 在标签关闭时从 selectedRelatedClassesMap 移除对应的 PsiClass
                                SearchRelatedClassesAction.selectedRelatedClassesMap.remove(className);
                                selectedItemsMap.remove(className);
                                SyncUtil.SYNC_CONFIG_MAP.remove(className);
                            });
                            contentManager.addContent(content);
                        }
                    }
                    if (!toolWindow.isVisible()) {
                        toolWindow.show();
                    }
                });
            }
        });

    }

    public void refreshToolWindow(Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow != null) {
            createToolWindowContent(project, toolWindow);  // 刷新内容
        }
    }


}
