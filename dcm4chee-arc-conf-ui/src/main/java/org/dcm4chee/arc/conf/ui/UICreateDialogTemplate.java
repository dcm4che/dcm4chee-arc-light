package org.dcm4chee.arc.conf.ui;

/**
 * @author Shefki Esadi <shralsheki@gmail.com>
 * @since Oct 2021
 */
public class UICreateDialogTemplate {
    public String templateName;
    public String templateDescription;
    public String dialog;
    public String[] templateTag = {};

    public UICreateDialogTemplate() {
    }

    public UICreateDialogTemplate(String name) {
        setTemplateName(name);
    }
    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateDescription() {
        return templateDescription;
    }

    public void setTemplateDescription(String templateDescription) {
        this.templateDescription = templateDescription;
    }

    public String getDialog() {
        return dialog;
    }

    public void setDialog(String dialog) {
        this.dialog = dialog;
    }

    public String[] getTemplateTag() {
        return templateTag;
    }

    public void setTemplateTag(String[] templateTag) {
        this.templateTag = templateTag;
    }
}
