package com.vpen.syncfield.window;

import java.awt.Component;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

public class CheckBoxListRenderer extends JCheckBox implements ListCellRenderer<String> {

    private final Set<String> selectedItemsSet;

    public CheckBoxListRenderer() {
        selectedItemsSet = new HashSet<>();
    }

    public CheckBoxListRenderer(Set<String> selectedItems) {
        selectedItemsSet = selectedItems;
    }

    public Set<String> getSelectedItems() {
        return selectedItemsSet;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        setText(value);
        // 根据 selectedItemsSet 判断是否打勾
        setSelected(selectedItemsSet.contains(value));

        if (cellHasFocus) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }
        setEnabled(list.isEnabled());
        setFont(list.getFont());
        setFocusPainted(false);
        return this;
    }


}

