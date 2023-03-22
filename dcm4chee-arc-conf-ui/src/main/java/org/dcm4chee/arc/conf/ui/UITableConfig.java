package org.dcm4chee.arc.conf.ui;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class UITableConfig {
    String name;
    String[] username = {};
    String[] roles = {};
    String tableId;
    boolean isDefault;
    private final Map<String, UITableColumn> tableColumns = new HashMap<>();
    public UITableConfig(){}

    public UITableConfig(String name){
        this.setName(name);
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getUsername() {
        return username;
    }

    public void setUsername(String[] username) {
        this.username = username;
    }

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        this.isDefault = aDefault;
    }

    public Map<String, UITableColumn> getTableColumn() {
        return tableColumns;
    }
    public Collection<UITableColumn> getTableColumns(){
        return this.tableColumns.values();
    }

    public void addTableColumn(UITableColumn tableColumn){
        this.tableColumns.put(tableColumn.getColumnName(),tableColumn);
    }
    public UITableColumn getTableColumn(String name){
        return this.tableColumns.get(name);
    }

}
