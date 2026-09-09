package com.company.crm.ai.view.component.menu;

import com.company.crm.ai.context.AiContextEntityDefinition;
import com.company.crm.ai.context.AiContextEntityRegistry;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import io.jmix.flowui.component.upload.JmixUpload;
import io.jmix.flowui.view.MessageBundle;

import java.util.function.BiConsumer;

public class AddContextMenuFactory {

    private final BiConsumer<String, Class<?>> openEntityLookup;
    private final AiContextEntityRegistry contextEntityRegistry;
    private final MessageBundle messageBundle;

    public AddContextMenuFactory(BiConsumer<String, Class<?>> openEntityLookup,
                          AiContextEntityRegistry contextEntityRegistry,
                          MessageBundle messageBundle) {
        this.openEntityLookup = openEntityLookup;
        this.contextEntityRegistry = contextEntityRegistry;
        this.messageBundle = messageBundle;
    }

    public MenuBar createAddMenuBar(JmixUpload<?> attachmentUpload) {
        configureAttachmentUploadForMenu(attachmentUpload);

        MenuBar menuBar = new MenuBar();
        menuBar.addClassName("ai-timeline-add-menu");

        Icon addIcon = VaadinIcon.PLUS.create();
        addIcon.setSize("20px");

        MenuItem addItem = menuBar.addItem(addIcon);
        addItem.setAriaLabel(messageBundle.getMessage("addContextAction"));

        SubMenu subMenu = addItem.getSubMenu();
        subMenu.addItem(attachmentUpload, "");

        /*MenuItem crmEntityItem = subMenu.addItem(
                createMenuItemContent(VaadinIcon.DATABASE, messageBundle.getMessage("addCrmEntityAction")),
                null
        );
        SubMenu crmEntitySubMenu = crmEntityItem.getSubMenu();
        contextEntityRegistry.addMenuDefinitions()
                .forEach(definition -> addEntityLookupMenuItem(crmEntitySubMenu, definition));
*/
        return menuBar;
    }

    private void addEntityLookupMenuItem(SubMenu subMenu, AiContextEntityDefinition definition) {
        String label = messageBundle.getMessage(definition.menuMessageKey());
        subMenu.addItem(createMenuItemContent(definition.icon(), label),
                event -> openEntityLookup.accept(label, definition.entityClass()));
    }

    private void configureAttachmentUploadForMenu(JmixUpload<?> attachmentUpload) {
        attachmentUpload.setDropAllowed(false);
        attachmentUpload.setUploadButton(createMenuItemContent(VaadinIcon.UPLOAD, messageBundle.getMessage("uploadFileAction")));
        attachmentUpload.addClassName("ai-timeline-menu-upload");
    }

    private Component createMenuItemContent(VaadinIcon iconName, String labelText) {
        return new AiContextMenuItemContent(iconName, labelText);
    }
}
