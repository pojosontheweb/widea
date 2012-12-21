/*
 * Copyright 2001-2012 Remi Vankeisbelck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package woko.idea;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WokoToolWindow {

    private JPanel panel1;
    private JTable table1;
    private JButton reloadButton;
    private JTextField textFieldFilter;
    private JButton clearButton;
    private JCheckBox includeLibsCheckBox;
    private JButton pushButton;

    private Project project;

    public WokoToolWindow() {
        reloadButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                reloadButton.setEnabled(false);
                getWpc().refresh();
                textFieldFilter.setEnabled(true);
                reloadButton.setEnabled(true);
            }
        });
        textFieldFilter.getDocument().addDocumentListener(
                new DocumentListener() {
                    public void changedUpdate(DocumentEvent e) {
                        filter();
                    }

                    public void insertUpdate(DocumentEvent e) {
                        filter();
                    }

                    public void removeUpdate(DocumentEvent e) {
                        filter();
                    }
                });
        textFieldFilter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent keyEvent) {
                int kc = keyEvent.getKeyCode();
                if (keyEvent.isControlDown()) {
                    // sorting shortcuts
                    switch (kc) {
                        case KeyEvent.VK_N: {
                            // sort by facet name
                            sortColumn(1);
                            break;
                        }
                        case KeyEvent.VK_P: {
                            // sort by profile
                            sortColumn(2);
                            break;
                        }
                        case KeyEvent.VK_T: {
                            // sort by target type
                            sortColumn(3);
                            break;
                        }
                        case KeyEvent.VK_F: {
                            // sort by facet class
                            sortColumn(4);
                            break;
                        }
                    }

                } else {
                    switch(kc) {
                        case KeyEvent.VK_UP : {
                            RowSorter<TableModel> rowSorter = (RowSorter<TableModel>) table1.getRowSorter();
                            int viewRowCount = rowSorter.getViewRowCount();
                            int selectedRow = table1.getSelectedRow();
                            if (selectedRow==0) {
                                selectedRow = viewRowCount - 1;
                            } else {
                                selectedRow--;
                            }
                            table1.getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
                            break;
                        }
                        case KeyEvent.VK_DOWN: {
                            int selectedRow = table1.getSelectedRow();
                            int rowCount = table1.getRowSorter().getViewRowCount();
                            if (selectedRow==rowCount-1) {
                                selectedRow = 0;
                            } else {
                                selectedRow++;
                            }
                            table1.getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
                            break;
                        }
                        case KeyEvent.VK_ENTER: {
                            openInEditor();
                        }
                    }
                }
            }
        });
        table1.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) {
                    // dbl-clicked : get selected row
                    openInEditor();
                }
            }
        });
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                textFieldFilter.setText(null);
            }
        });
        includeLibsCheckBox.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                filter();
            }
        });
        pushButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                getWpc().openPushDialog();
            }
        });
    }

    private void sortColumn(int index) {
        boolean sortKeyPresent = false;
        DefaultRowSorter rs = (DefaultRowSorter)table1.getRowSorter();
        for (Object sk : rs.getSortKeys()) {
            RowSorter.SortKey sortKey = (RowSorter.SortKey)sk;
            if (sortKey.getColumn()==index) {
                // sort key already present
                sortKeyPresent = true;
            }
        }
        if (!sortKeyPresent) {
            List<RowSorter.SortKey> keys = new ArrayList<RowSorter.SortKey>();
            keys.add(new RowSorter.SortKey(index, SortOrder.ASCENDING));
            rs.setSortKeys(keys);
        } else {
            rs.toggleSortOrder(index);
        }
    }

    private void openInEditor() {
        int selectedRow = table1.getSelectedRow();
        if (selectedRow!=-1) {
            int row = table1.convertRowIndexToModel(selectedRow);
            FacetDescriptorTableModel model = (FacetDescriptorTableModel) table1.getModel();
            WideaFacetDescriptor fd = model.getFacetDescriptorAt(row);
            if (fd!=null) {
                getWpc().openClassInEditor(fd.getFacetClassName());
            }
        }
    }

    private WokoProjectComponent getWpc() {
        return project.getComponent(WokoProjectComponent.class);
    }

    private void filter() {
        final WokoProjectComponent wpc = getWpc();
        setFacetTableFilterCallback(table1, new FilterCallback() {
            @Override
            protected boolean matches(WideaFacetDescriptor fd) {
                String text = textFieldFilter.getText();
                boolean fdMatch = fdMatch(fd, text);
                if (fdMatch) {
                    // do we include libs or not ?
                    if (!includeLibsCheckBox.isSelected()) {
                        // check if class is project class
                        return !fd.getType().equals(FdType.Compiled);
                    }
                }
                return fdMatch;
            }
        });
    }

    public void init(Project project) {
        this.project = project;
        FacetDescriptorTableModel model = new FacetDescriptorTableModel(project);
        TableRowSorter<FacetDescriptorTableModel> sorter = new TableRowSorter<FacetDescriptorTableModel>(model);
        table1.setModel(model);
        table1.setRowSorter(sorter);
        table1.setIntercellSpacing(new Dimension(0, 0));
        table1.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        TableColumnModel colModel = table1.getColumnModel();
        TableColumn c0 = colModel.getColumn(0);
        c0.setMinWidth(30);
        c0.setMaxWidth(30);
        c0.setResizable(false);
        c0.setCellRenderer(new FacetTypeCellRenderer(project));
        for (int i=1; i<model.getColumnCount(); i++) {
            colModel.getColumn(i).setCellRenderer(new FacetCellRenderer(project));
        }
    }

    public void refreshContents() {
        // refresh the table
        ((FacetDescriptorTableModel)table1.getModel()).fireTableDataChanged();
        // do we enable push ?
        WokoProjectComponent w = project.getComponent(WokoProjectComponent.class);
        pushButton.setEnabled(w.hasPushableFacets());
        // focus the search field
        textFieldFilter.requestFocus();
    }

    public static abstract class FilterCallback {

        protected abstract boolean matches(WideaFacetDescriptor fd);

        protected boolean strMatch(String s, String filterText) {
            return filterText == null
                    || filterText.equals("")
                    || s == null
                    || s.equals("")
                    || s.toLowerCase().contains(filterText.toLowerCase());
        }

        protected boolean fdMatch(WideaFacetDescriptor fd, String filterText) {
            return fd == null
                    || strMatch(fd.getName(), filterText)
                    || strMatch(fd.getProfileId(), filterText)
                    || strMatch(fd.getTargetObjectTypeName(), filterText)
                    || strMatch(fd.getFacetClassName(), filterText);
        }

    }

    public void setFacetTableFilterCallback(JTable table, final FilterCallback callback) {
        TableRowSorter<FacetDescriptorTableModel> sorter = (TableRowSorter<FacetDescriptorTableModel>)table.getRowSorter();
        if (sorter!=null) {
            sorter.setRowFilter(new RowFilter<FacetDescriptorTableModel,Integer>() {
                @Override
                public boolean include(Entry<? extends FacetDescriptorTableModel, ? extends Integer> entry) {
                    FacetDescriptorTableModel model = entry.getModel();
                    WideaFacetDescriptor fd = model.getFacetDescriptorAt(entry.getIdentifier());
                    return callback.matches(fd);
                }
            });
        }
    }

    public JPanel getMainPanel() {
        return panel1;
    }



}

