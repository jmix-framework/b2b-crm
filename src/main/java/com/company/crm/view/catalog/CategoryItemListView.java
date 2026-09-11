package com.company.crm.view.catalog;

import com.company.crm.app.feature.queryparameters.filters.FieldValueQueryParameterBinder;
import com.company.crm.app.service.catalog.CatalogImportSettings;
import com.company.crm.app.service.catalog.CatalogService;
import com.company.crm.app.util.constant.CrmConstants;
import com.company.crm.app.util.ui.renderer.CrmRenderers;
import com.company.crm.model.catalog.category.Category;
import com.company.crm.model.catalog.category.CategoryRepository;
import com.company.crm.model.catalog.item.CategoryItem;
import com.company.crm.model.catalog.item.CategoryItemRepository;
import com.company.crm.view.main.MainView;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.upload.FileUploadField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.upload.event.FileUploadSucceededEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Supply;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;

import static com.company.crm.app.util.ui.CrmUiUtils.addRowSelectionInMultiSelectMode;
import static com.company.crm.app.util.ui.CrmUiUtils.setSearchHintPopover;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.addCondition;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.applyFiltersOnValueChange;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.installSortByCreatedDate;
import static com.company.crm.view.catalog.CategoryItemListView.ROUTE;
import static io.jmix.core.querycondition.PropertyCondition.contains;
import static io.jmix.core.querycondition.PropertyCondition.equal;

@Route(value = ROUTE, layout = MainView.class)
@ViewController(id = CrmConstants.ViewIds.CATEGORY_ITEM_LIST)
@ViewDescriptor(path = "category-item-list-view.xml")
@LookupComponent("categoryItemsDataGrid")
@DialogMode(width = "64em", resizable = true)
public class CategoryItemListView extends StandardListView<CategoryItem> {

    public static final String ROUTE = "products";

    @Autowired
    private CrmRenderers crmRenderers;
    @Autowired
    private CategoryItemRepository itemRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private CatalogService catalogService;

    @ViewComponent
    private TypedTextField<String> items_searchField;
    @ViewComponent
    private JmixSelect<Category> items_categorySelect;
    @ViewComponent
    private CollectionLoader<CategoryItem> categoryItemsDl;
    @ViewComponent
    private FileUploadField updateCatalogField;
    @ViewComponent
    private DataGrid<CategoryItem> categoryItemsDataGrid;

    private final LogicalCondition filtersCondition = LogicalCondition.and();

    @Subscribe
    private void onInit(final InitEvent event) {
        installSortByCreatedDate(categoryItemsDl);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        initialize();
    }

    @Install(to = "categoryItemsDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<CategoryItem> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return itemRepository.findAll(pageable, addCondition(context, filtersCondition)).getContent();
    }

    @Install(to = "items_pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return itemRepository.count(addCondition(context, filtersCondition));
    }

    @Install(to = "categoryItemsDataGrid.removeAction", subject = "delegate")
    private void categoryItemsDataGridRemoveDelegate(final Collection<CategoryItem> collection) {
        itemRepository.deleteAll(collection);
    }

    @Supply(to = "categoryItemsDataGrid.name", subject = "renderer")
    private Renderer<CategoryItem> categoryItemsDataGridNameRenderer() {
        return crmRenderers.detailLink(categoryItemsDataGrid);
    }

    @Supply(to = "categoryItemsDataGrid.category", subject = "renderer")
    private Renderer<CategoryItem> categoryItemsDataGridCategoryRenderer() {
        return crmRenderers.entityLink(categoryItemsDataGrid, CategoryItem::getCategory);
    }

    @Supply(to = "categoryItemsDataGrid.code", subject = "renderer")
    private Renderer<CategoryItem> categoryItemsDataGridCodeRenderer() {
        return crmRenderers.categoryItemCode();
    }

    @Subscribe("updateCatalogField")
    public void onImportCatalogFieldFileUploadSucceeded(FileUploadSucceededEvent<FileUploadField, byte[]> event) {
        byte[] content = updateCatalogField.getValue();
        if (content != null) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
            CatalogImportSettings importSettings = new CatalogImportSettings(inputStream);
            catalogService.updateCatalog(importSettings);
            categoryItemsDl.load();
        }
    }

    @Subscribe("categoryItemsDataGrid.downloadXls")
    private void onCategoryItemsDataGridDownloadXls(final ActionPerformedEvent event) {
        catalogService.downloadCatalogXls();
        categoryItemsDl.load();
    }

    private void initialize() {
        loadData();
        initializeFilterFields();
        addRowSelectionInMultiSelectMode(categoryItemsDataGrid, "code");
    }

    private void loadData() {
        categoryItemsDl.load();
    }

    private void updateFiltersCondition() {
        filtersCondition.getConditions().clear();
        addSearchByNameCondition();
        addSearchByCategoryCondition();
    }

    private void addSearchByNameCondition() {
        items_searchField.getOptionalValue().ifPresent(name ->
                filtersCondition.add(contains("name", name)));
    }

    private void addSearchByCategoryCondition() {
        items_categorySelect.getOptionalValue().ifPresent(category ->
                filtersCondition.add(equal("category", category)));
    }

    private void initializeFilterFields() {
        List<Category> categories = categoryRepository.findAll();
        items_categorySelect.setItems(categories);

        setSearchHintPopover(items_searchField);
        applyFiltersOnValueChange(categoryItemsDl, this::applyFilters,
                items_searchField, items_categorySelect);

        //noinspection unchecked
        FieldValueQueryParameterBinder.builder(this)
                .addStringBinding(items_searchField)
                .addEntitySelectBinding(items_categorySelect, () -> categories)
                .build();
    }

    private void applyFilters() {
        updateFiltersCondition();
        categoryItemsDl.load();
    }
}