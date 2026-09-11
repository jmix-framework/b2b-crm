package com.company.crm.view.order;

import com.company.crm.app.feature.queryparameters.SimpleUrlQueryParametersBinder;
import com.company.crm.app.feature.queryparameters.filters.FieldValueQueryParameterBinder;
import com.company.crm.app.ui.component.OrderStatusPipeline;
import com.company.crm.app.ui.component.OrderStatusPipeline.OrderStatusComponent;
import com.company.crm.app.util.constant.CrmConstants;
import com.company.crm.app.util.ui.renderer.CrmRenderers;
import com.company.crm.model.client.Client;
import com.company.crm.model.invoice.Invoice;
import com.company.crm.model.order.Order;
import com.company.crm.model.order.OrderRepository;
import com.company.crm.model.order.OrderStatus;
import com.company.crm.view.invoice.InvoiceDetailView;
import com.company.crm.view.main.MainView;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.core.metamodel.datatype.DatatypeFormatter;
import io.jmix.core.querycondition.Condition;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.PrimaryListView;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Supply;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.company.crm.app.util.ui.CrmUiUtils.addColumnHeaderCurrencySuffix;
import static com.company.crm.app.util.ui.CrmUiUtils.addRowSelectionInMultiSelectMode;
import static com.company.crm.app.util.ui.CrmUiUtils.setSearchHintPopover;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.addCondition;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.applyFiltersOnValueChange;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.installSortByCreatedDate;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.resetToFirstPage;
import static com.company.crm.model.datatype.PriceDataType.formatWithoutCurrency;
import static com.company.crm.view.order.OrderListView.ROUTE;
import static io.jmix.core.querycondition.PropertyCondition.equal;
import static io.jmix.core.querycondition.PropertyCondition.greaterOrEqual;
import static io.jmix.core.querycondition.PropertyCondition.inList;
import static io.jmix.core.querycondition.PropertyCondition.lessOrEqual;

@Route(value = ROUTE, layout = MainView.class)
@ViewController(id = CrmConstants.ViewIds.ORDER_LIST)
@ViewDescriptor(path = "order-list-view.xml")
@LookupComponent("ordersDataGrid")
@DialogMode(width = "90%", resizable = true)
@PrimaryListView(Order.class)
public class OrderListView extends StandardListView<Order> {

    public static final String ROUTE = "orders";

    private static final String SELECTED_STATUS_PARAM = "selected_status";
    private static final String SELECTED_STATUS_SEPARATOR = ",";

    @Autowired
    private Messages messages;
    @Autowired
    private CrmRenderers crmRenderers;
    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private DatatypeFormatter datatypeFormatter;

    @ViewComponent
    private CollectionLoader<Order> ordersDl;
    @ViewComponent
    private CollectionLoader<Client> clientsDl;
    @ViewComponent
    private CollectionContainer<Client> clientsDc;

    @ViewComponent
    private TypedTextField<String> searchField;
    @ViewComponent
    private EntityComboBox<Client> clientComboBox;
    @ViewComponent
    private TypedDatePicker<LocalDate> fromDatePicker;
    @ViewComponent
    private TypedDatePicker<LocalDate> toDatePicker;
    @ViewComponent
    private OrderStatusPipeline pipeLineFilter;
    @ViewComponent
    private DataGrid<Order> ordersDataGrid;

    private final LogicalCondition filtersCondition = LogicalCondition.and();

    private Set<OrderStatus> selectedStatuses = Set.of();
    private SimpleUrlQueryParametersBinder selectedStatusUrlParameterBinder;

    /**
     * The context the data loader was last loaded with. It carries the conditions of the advanced filter,
     * which are owned by the loader and not by {@link #filtersCondition}, so the pipeline counts need it to
     * stay consistent with the grid.
     */
    private JmixDataRepositoryContext lastRepositoryContext = JmixDataRepositoryContext.builder().build();

    @Subscribe
    private void onInit(final InitEvent event) {
        installSortByCreatedDate(ordersDl);
        configureGrid();
        clientsDl.load();
        registerUrlQueryParametersBinders();
    }

    @Subscribe
    private void onBeforeShow(final BeforeShowEvent event) {
        initializeFilterFields();
        applyFilters();
    }

    @Install(to = "ordersDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<Order> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        lastRepositoryContext = context;
        return orderRepository.findAll(pageable, addCondition(context, filtersCondition)).getContent();
    }

    @Subscribe(id = "ordersDl", target = Target.DATA_LOADER)
    private void onOrdersDlPostLoad(final CollectionLoader.PostLoadEvent<Order> event) {
        updatePipeLineFilter();
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return orderRepository.count(addCondition(context, filtersCondition));
    }

    @Install(to = "ordersDataGrid.removeAction", subject = "delegate")
    private void ordersDataGridRemoveDelegate(final Collection<Order> collection) {
        orderRepository.deleteAll(collection);
    }

    @Subscribe("ordersDataGrid.addInvoice")
    private void onOrdersDataGridAddInvoice(final ActionPerformedEvent event) {
        Order order = ordersDataGrid.getSingleSelectedItem();
        if (order == null) {
            return;
        }

        dialogWindows.detail(this, Invoice.class)
                .newEntity()
                .withInitializer(invoice -> invoice.setOrder(order))
                .withViewClass(InvoiceDetailView.class)
                .withViewConfigurer(InvoiceDetailView::forbidChangeOrder)
                .open();
    }

    @Supply(to = "ordersDataGrid.itemDetails", subject = "renderer")
    private Renderer<Order> ordersDataGridItemDetailsRenderer() {
        return crmRenderers.itemDetailsColumnRenderer(ordersDataGrid);
    }

    @Supply(to = "ordersDataGrid.client", subject = "renderer")
    private Renderer<Order> ordersDataGridClientRenderer() {
        return crmRenderers.orderClientLink(ordersDataGrid);
    }

    @Supply(to = "ordersDataGrid.status", subject = "renderer")
    private Renderer<Order> ordersDataGridStatusRenderer() {
        return crmRenderers.orderStatus();
    }

    @Supply(to = "ordersDataGrid.total", subject = "renderer")
    private Renderer<Order> ordersDataGridTotalRenderer() {
        return new TextRenderer<>(order -> formatWithoutCurrency(order.getTotal(), datatypeFormatter));
    }

    @Supply(to = "ordersDataGrid.number", subject = "renderer")
    private Renderer<Order> ordersDataGridNumberRenderer() {
        return crmRenderers.uniqueNumber(Order::getNumber);
    }

    @Supply(to = "ordersDataGrid.invoiced", subject = "renderer")
    private Renderer<Order> ordersDataGridInvoicedRenderer() {
        return new TextRenderer<>(order -> formatWithoutCurrency(order.getInvoiced(), datatypeFormatter));
    }

    @Supply(to = "ordersDataGrid.paid", subject = "renderer")
    private Renderer<Order> ordersDataGridPaidRenderer() {
        return new TextRenderer<>(order -> formatWithoutCurrency(order.getPaid(), datatypeFormatter));
    }

    @Supply(to = "ordersDataGrid.leftOverSum", subject = "renderer")
    private Renderer<Order> ordersDataGridLeftOverRenderer() {
        return crmRenderers.orderLeftOverSumRenderer();
    }

    @Install(to = "ordersDataGrid.addInvoice", subject = "enabledRule")
    private boolean ordersDataGridAddInvoiceEnabledRule() {
        return ordersDataGrid.getSelectedItems().size() == 1;
    }

    private void initializeFilterFields() {
        initializePipelineFilter();
        setSearchHintPopover(searchField);
        applyFiltersOnValueChange(ordersDl, this::applyFilters,
                searchField, clientComboBox, fromDatePicker, toDatePicker);
    }

    private void configureGrid() {
        addColumnHeaderCurrencySuffix(ordersDataGrid, "total", "invoiced", "paid", "leftOver");
        addRowSelectionInMultiSelectMode(ordersDataGrid, "itemDetails", "number");
        ordersDataGrid.setItemDetailsRenderer(crmRenderers.orderDetails());
        ordersDataGrid.setDetailsVisibleOnClick(false);
    }

    private void registerUrlQueryParametersBinders() {
        //noinspection unchecked
        FieldValueQueryParameterBinder.builder(this)
                .addStringBinding(searchField)
                .addComboboxBinding(clientComboBox, () -> clientsDc.getItems())
                .addDatePickerBinding(fromDatePicker)
                .addDatePickerBinding(toDatePicker)
                .build();

        selectedStatusUrlParameterBinder = SimpleUrlQueryParametersBinder.registerBinder(this,
                () -> QueryParameters.of(SELECTED_STATUS_PARAM, serializeSelectedStatuses()),
                qp -> qp.getSingleParameter(SELECTED_STATUS_PARAM).ifPresent(ids ->
                        selectedStatuses = deserializeStatuses(ids)));
    }

    private String serializeSelectedStatuses() {
        return selectedStatuses.stream()
                .map(status -> status.getId().toString())
                .collect(Collectors.joining(SELECTED_STATUS_SEPARATOR));
    }

    private static Set<OrderStatus> deserializeStatuses(String ids) {
        EnumSet<OrderStatus> statuses = EnumSet.noneOf(OrderStatus.class);
        for (String id : ids.split(SELECTED_STATUS_SEPARATOR)) {
            OrderStatus status = OrderStatus.fromStringId(id);
            if (status != null) {
                statuses.add(status);
            }
        }
        return Collections.unmodifiableSet(statuses);
    }

    private void applyFilters() {
        updateFiltersCondition();
        ordersDl.load();
    }

    private void updateFiltersCondition() {
        filtersCondition.getConditions().clear();
        nonStatusConditions().forEach(filtersCondition::add);
        selectedStatusesCondition().ifPresent(filtersCondition::add);
    }

    /**
     * Returns the conditions of every filter except the status pipeline. The pipeline counts reuse them:
     * each status has to be counted as if it were the selected one.
     */
    private List<Condition> nonStatusConditions() {
        List<Condition> conditions = new ArrayList<>();
        searchField.getOptionalValue().ifPresent(number -> conditions.add(equal("number", number)));
        clientComboBox.getOptionalValue().ifPresent(client -> conditions.add(equal("client", client)));
        fromDatePicker.getOptionalValue().ifPresent(fromDate -> conditions.add(greaterOrEqual("date", fromDate)));
        toDatePicker.getOptionalValue().ifPresent(toDate -> conditions.add(lessOrEqual("date", toDate)));
        return conditions;
    }

    private Optional<Condition> selectedStatusesCondition() {
        return selectedStatuses.isEmpty()
                ? Optional.empty()
                : Optional.of(inList("status", List.copyOf(selectedStatuses)));
    }

    private void initializePipelineFilter() {
        updatePipelineSelection();
        pipeLineFilter.addStatusClickListener(this::onStatusFilterClick);
    }

    /**
     * Adds the clicked status to the selection, or removes it when it is already selected. An empty
     * selection means no status filter at all.
     */
    private void onStatusFilterClick(OrderStatusComponent component) {
        selectedStatuses = toggleStatus(selectedStatuses, component.getStatus());

        selectedStatusUrlParameterBinder.fireQueryParametersChanged();
        updatePipelineSelection();

        resetToFirstPage(ordersDl);
        applyFilters();
    }

    private static Set<OrderStatus> toggleStatus(Set<OrderStatus> statuses, OrderStatus status) {
        EnumSet<OrderStatus> result = EnumSet.noneOf(OrderStatus.class);
        result.addAll(statuses);
        if (!result.remove(status)) {
            result.add(status);
        }
        return Collections.unmodifiableSet(result);
    }

    private void updatePipelineSelection() {
        pipeLineFilter.deselectAllStatuses();
        pipeLineFilter.selectStatus(selectedStatuses.toArray(new OrderStatus[0]));
    }

    private void updatePipeLineFilter() {
        List<Condition> baseConditions = nonStatusConditions();

        pipeLineFilter.getStatusComponents().forEach(component -> {
            OrderStatus status = component.getStatus();
            component.setTitle(messages.getMessage(status) + " (" + countOrders(baseConditions, status) + ")");
        });
    }

    /**
     * Counts all orders in the given status under the other active filters, not only the ones on the
     * current page. One count query per status - the enum has four of them.
     */
    private long countOrders(List<Condition> baseConditions, OrderStatus status) {
        List<Condition> conditions = new ArrayList<>(baseConditions);
        conditions.add(equal("status", status));

        return orderRepository.count(addCondition(lastRepositoryContext,
                LogicalCondition.and(conditions.toArray(new Condition[0]))));
    }
}
