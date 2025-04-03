package com.vpen.syncfield.linstener;

import com.intellij.ui.components.JBList;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;
import javax.swing.DefaultListModel;

/**
 * 鼠标监听器
 * @author vpen
 * @date 2025/3/25 13:42
 */

public class CheckBoxMouseListener extends MouseAdapter {

    private JBList<String> classList;
    private Set<String> selectedItemsSet;
    private DefaultListModel<String> listModel;

    public CheckBoxMouseListener(JBList<String> classList,
                                 Set<String> selectedItemsSet,
                                 DefaultListModel<String> listModel) {
        this.classList = classList;
        this.selectedItemsSet = selectedItemsSet;
        this.listModel = listModel;
    }

    /**
     * 监听鼠标但单击
     **/
    @Override
    public void mouseClicked(MouseEvent e) {
        int index = classList.locationToIndex(e.getPoint());
        if (index != -1) {
            String className = listModel.getElementAt(index);
            if (selectedItemsSet.contains(className)) {
                // 取消选中
                selectedItemsSet.remove(className);
            } else {
                // 选中
                selectedItemsSet.add(className);
            }
            // 重新绘制复选框
            classList.repaint();
        }
    }
}
