package com.vpen.syncfield.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.UIUtil;
import com.vpen.syncfield.spliter.SplitFieldSyncOpener;
import com.vpen.syncfield.utils.SyncUtil;
import com.vpen.syncfield.window.RelatedClassToolWindowFactory;
import java.awt.Component;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import org.jetbrains.annotations.NotNull;

public class SearchRelatedClassesAction extends AnAction {

    public static Map<PsiClass, Set<PsiClass>> selectedRelatedClassesMap = new ConcurrentHashMap<>();

    private String originalText = null;
    private String updatedText = null;
    private Timer selectionTimer;

    private CaretListener caretListener;  // 用来存储光标监听器


    private JPopupMenu popupMenu;  // 工具栏
    private JPanel panel;          // 按钮面板
    private JButton setOriginalButton;
    private JButton setUpdatedButton;
    private JButton createButton;
    private JButton deleteButton;
    private JButton closeButton;
    private String selectedText;   // 记录当前选择的文本
    private Editor currentEditor;  // 记录当前编辑器

    // 面板的状态，默认显示
    private boolean popupMenuVisible = true;

    private static SearchRelatedClassesAction instance;

    public SearchRelatedClassesAction() {
        // 设置菜单项的文本
        initInlineToolbar();
        instance = this;
    }

    public static SearchRelatedClassesAction getInstance() {
        return instance;
    }

    public JPopupMenu getPopupMenu() {
        return this.popupMenu;
    }

    public void doCreate() {
        selectedText = currentEditor.getSelectionModel().getSelectedText();
        this.createButton.doClick();
    }

    public void doDelete() {
        selectedText = currentEditor.getSelectionModel().getSelectedText();
        this.deleteButton.doClick();
    }

    public void doSetOriginal() {
        originalText = currentEditor.getSelectionModel().getSelectedText();
        this.setOriginalButton.doClick();
    }

    public void doSetUpdated() {
        updatedText = currentEditor.getSelectionModel().getSelectedText();
        this.setUpdatedButton.doClick();
    }


    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        //
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        String inputClassName = "";
        PsiClass selectedClass;
        // 如果有选中
        if (Objects.nonNull(element)) {
            if (element instanceof PsiClass) {
                // 设置默认值为当前选中类的名称
                selectedClass = (PsiClass) element;
                inputClassName = selectedClass.getName();
            } else {
                selectedClass = null;
            }
        } else if (Objects.nonNull(psiFile)) {
            selectedClass = getPsiClassFromFile(project, psiFile.getVirtualFile());
            inputClassName = selectedClass.getName();
        } else {
            selectedClass = null;
        }
        // 弹出输入框，用户输入类名，默认值为当前类名
        inputClassName = getInputClassName(project, inputClassName);


        if (StringUtil.isEmpty(inputClassName)) {
            return;
        }

        // 弹出对话框显示相关类列表
        String finalInputClassName = inputClassName;
        ApplicationManager.getApplication().invokeLater(() -> {
            searchClass(project, finalInputClassName, selectedClass);
            // RelatedClassesWindow在plugin定义的id
            RelatedClassToolWindowFactory.getInstance().refreshToolWindow(project, selectedClass);
        });


        currentEditor = e.getData(CommonDataKeys.EDITOR);
        if (currentEditor == null || psiFile == null) {
            return;
        }
        //  面板弹出监听
        addCaretListenerIfNeeded(currentEditor);
    }

    public static String getInputClassName(Project project, String InitValue) {
        return Messages.showInputDialog(
                project,
                "Enter class name to search for related classes:",
                "Search Related Classes",
                Messages.getQuestionIcon(),
                InitValue,  // 默认值
                null // 可以为输入框提供一个过滤器
        );
    }

    public static void searchClass(Project project, String finalInputClassName, PsiClass selectedClass) {
        RelatedClassSearchDialog dialog = new RelatedClassSearchDialog(project, finalInputClassName, selectedClass);
        dialog.show();
        // 获取选中的类并更新 selectedRelatedClasses
        List<PsiClass> list = dialog.getSelectedClasses();
        if (list != null && !list.isEmpty()) {
            Set<PsiClass> classes = selectedRelatedClassesMap.get(selectedClass);
            if (classes == null) {
                classes = new HashSet<>();
            }
            classes.addAll(list);
            selectedRelatedClassesMap.put(selectedClass, classes);
        }
    }

    private void showInlineToolbar(Editor editor, String text) {
        this.selectedText = text.trim();
        // 获取光标的像素坐标
        Point point = editor.logicalPositionToXY(editor.getCaretModel().getLogicalPosition());
        // 弹出工具栏
        UIUtil.invokeLaterIfNeeded(() -> {
            popupMenu.show(editor.getContentComponent(), point.x, point.y);
        });
    }


    // 创建一个样式化按钮的方法
    private JButton createStyledButton(String text) {
        return new JButton(text);
    }

    private JButton createStyledButton(String text, String actionKey, String shortcut) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        button.setFocusable(false);

        // 解析快捷键字符串（支持跨平台）
        KeyStroke keyStroke = parseKeyStroke(shortcut);

        // 绑定到全局窗口焦点（即使焦点不在按钮上也能触发）
        button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, actionKey);
        button.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                button.doClick(); // 触发按钮点击
            }
        });

        return button;
    }

    // 解析快捷键字符串（支持跨平台修饰键）
    private KeyStroke parseKeyStroke(String shortcut) {
        int modifiers = 0;
        String[] parts = shortcut.split(" ");
        String key = parts[parts.length - 1];

        for (String part : parts) {
            if (part.equalsIgnoreCase("alt")) {
                modifiers |= InputEvent.ALT_DOWN_MASK;
            } else if (part.equalsIgnoreCase("shift")) {
                modifiers |= InputEvent.SHIFT_DOWN_MASK;
            } else if (part.equalsIgnoreCase("ctrl") || part.equalsIgnoreCase("meta")) {
                // IDEA 自动处理 macOS 的 Meta 键转换
                modifiers |= SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
            }
        }

        int keyCode = switch (key.toUpperCase()) {
            case "ESCAPE" -> KeyEvent.VK_ESCAPE;
            case "ENTER" -> KeyEvent.VK_ENTER;
            case "F1" -> KeyEvent.VK_F1;
            case "F2" -> KeyEvent.VK_F2;
            // 可以继续扩展其他特殊按键
            default ->
                // 处理普通字符的快捷键
                    KeyEvent.getExtendedKeyCodeForChar(key.charAt(0));
        };

        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }


    private void triggerSyncIfReady(Editor editor) {
        // 如果修改前和修改后的文本都已经设置，则触发同步
        if (originalText != null && updatedText != null) {
            PsiClass currentPsiClass = getPsiClassFromFile(editor.getProject(), editor.getVirtualFile());
            if (currentPsiClass != null) {
                SyncUtil.syncToRelatedClasses(currentPsiClass, originalText.trim(), updatedText.trim());
                showInfoMessage("已更新");
            }
            originalText = null;
            updatedText = null;
        }
    }

    private void triggerSyncIfReady(Editor editor, SyncUtil.SyncType type) {
        // 如果修改前和修改后的文本都已经设置，则触发同步
        if (originalText != null) {
            PsiClass currentPsiClass = getPsiClassFromFile(editor.getProject(), editor.getVirtualFile());
            if (currentPsiClass != null) {
                SyncUtil.syncToRelatedClasses(currentPsiClass, originalText.trim(), type);
            }
            originalText = null;
        }
    }

    // === 根据 VirtualFile 获取 PsiClass ===
    public static PsiClass getPsiClassFromFile(Project project, VirtualFile file) {
        return SplitFieldSyncOpener.getPsiClassFromFile(project, file);
    }

    private void showInfoMessage(String message) {
        JOptionPane.showMessageDialog(null, message);
    }

    private void initInlineToolbar() {
        // 创建面板
        panel = new JBPanel<>();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Gray._240);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // 创建按钮
        setOriginalButton = createStyledButton("修改前", "setOriginalAction", "alt shift B");
        setUpdatedButton = createStyledButton("修改后", "setUpdatedAction", "alt shift A");
        setUpdatedButton.setVisible(false);
        createButton = createStyledButton("新增", "createAction", "alt shift C");
        deleteButton = createStyledButton("删除", "deleteAction", "alt shift D");
        closeButton = createStyledButton("关闭", "closeAction", "alt shift E");

        // 绑定按钮事件
        bindButtonActions();

        // 将按钮添加到面板
        panel.add(createButton);
        panel.add(setOriginalButton);
        panel.add(setUpdatedButton);
        panel.add(deleteButton);
        panel.add(closeButton);

        // 创建并添加面板到 JPopupMenu
        popupMenu = new JPopupMenu();
        popupMenu.add(panel);
    }

    // 绑定按钮的事件监听器
    private void bindButtonActions() {
        // 修改前按钮事件
        setOriginalButton.addActionListener(e -> {
            originalText = selectedText;
            triggerSyncIfReady(currentEditor);
            popupHide();
            setOriginalButton.setVisible(false);
            setUpdatedButton.setVisible(true);
        });

        // 修改后按钮事件
        setUpdatedButton.addActionListener(e -> {
            updatedText = selectedText;
            triggerSyncIfReady(currentEditor);
            popupHide();
            setOriginalButton.setVisible(true);
            setUpdatedButton.setVisible(false);
        });

        // 新增按钮事件
        createButton.addActionListener(e -> {
            originalText = selectedText;
            triggerSyncIfReady(currentEditor, SyncUtil.SyncType.ADD);
            popupHide();
        });

        // 删除按钮事件
        deleteButton.addActionListener(e -> {
            originalText = selectedText;
            triggerSyncIfReady(currentEditor, SyncUtil.SyncType.DELETE);
            popupHide();
        });

        // 关闭按钮事件
        closeButton.addActionListener(e -> {
            if (caretListener != null && currentEditor != null) {
                currentEditor.getCaretModel().removeCaretListener(caretListener);
            }
            popupHide();
            popupMenuVisible = false;
        });
    }

    private void popupHide() {
        if (Objects.nonNull(popupMenu)) {
            popupMenu.setVisible(false);
        }
    }

    public void showPopupMenu() {
        if (popupMenu != null) {
            popupMenu.setVisible(true);
        }
        popupMenuVisible = true;
        if (caretListener == null) {
            addCaretListenerIfNeeded(currentEditor);
        } else {
            currentEditor.getCaretModel().addCaretListener(caretListener);
        }
    }

    private void addCaretListenerIfNeeded(Editor editor) {
        if (caretListener == null) {
            caretListener = new CaretListener() {
                @Override
                public void caretPositionChanged(@NotNull CaretEvent e) {
                    String selectedText = editor.getSelectionModel().getSelectedText();
                    if (StringUtil.isNotEmpty(selectedText)) {
                        // 如果光标还是运动的，说明是再选择的过程
                        if (selectionTimer != null && selectionTimer.isRunning()) {
                            selectionTimer.stop();
                        }
                        //300ms后，如果没有动作了，则弹出面板
                        selectionTimer = new Timer(500, event -> {
                            if (popupMenuVisible && editor.getSelectionModel().getSelectedText() != null) {
                                // 弹出面板
                                showInlineToolbar(editor, editor.getSelectionModel().getSelectedText());
                            }
                        });
                        selectionTimer.setRepeats(false);
                        selectionTimer.start();
                    }
                }
            };
            editor.getCaretModel().addCaretListener(caretListener);
        }
    }
}