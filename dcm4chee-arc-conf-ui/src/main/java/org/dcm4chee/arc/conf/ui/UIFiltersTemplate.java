package org.dcm4chee.arc.conf.ui;

/**
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since August 2018
 */
public class UIFiltersTemplate {
    private String filterGroupID;
    private String filterGroupName;
    private String filterGroupDescription;
    private String filterGroupUsername;
    private String filterGroupRole;
    private String[] filters;
    private boolean isDefault = false;

    public UIFiltersTemplate(){}
    public UIFiltersTemplate(String name){
        this.setFilterGroupName(name);
    }

    public String getFilterGroupID() {
        return filterGroupID;
    }

    public void setFilterGroupID(String filterGroupID) {
        this.filterGroupID = filterGroupID;
    }

    public String getFilterGroupName() {
        return filterGroupName;
    }

    public void setFilterGroupName(String filterGroupName) {
        this.filterGroupName = filterGroupName;
    }

    public String getFilterGroupDescription() {
        return filterGroupDescription;
    }

    public void setFilterGroupDescription(String filterGroupDescription) {
        this.filterGroupDescription = filterGroupDescription;
    }

    public String getFilterGroupUsername() {
        return filterGroupUsername;
    }

    public void setFilterGroupUsername(String filterGroupUsername) {
        this.filterGroupUsername = filterGroupUsername;
    }

    public String getFilterGroupRole() {
        return filterGroupRole;
    }

    public void setFilterGroupRole(String filterGroupRole) {
        this.filterGroupRole = filterGroupRole;
    }

    public String[] getFilters() {
        return filters;
    }

    public void setFilters(String[] filters) {
        this.filters = filters;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

}
