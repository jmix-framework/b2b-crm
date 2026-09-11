package com.company.crm.view.payment;

import com.company.crm.app.feature.queryparameters.filters.FieldValueQueryParameterBinder;
import com.company.crm.app.util.constant.CrmConstants;
import com.company.crm.app.util.ui.renderer.CrmRenderers;
import com.company.crm.model.client.Client;
import com.company.crm.model.invoice.Invoice;
import com.company.crm.model.order.Order;
import com.company.crm.model.payment.Payment;
import com.company.crm.model.payment.PaymentRepository;
import com.company.crm.view.main.MainView;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.data.grid.DataGridItems;
import io.jmix.flowui.model.CollectionContainer;
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

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static com.company.crm.app.util.ui.CrmUiUtils.addRowSelectionInMultiSelectMode;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.addCondition;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.applyFiltersOnValueChange;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.installSortByCreatedDate;
import static com.company.crm.view.payment.PaymentListView.ROUTE;
import static io.jmix.core.querycondition.PropertyCondition.equal;
import static io.jmix.core.querycondition.PropertyCondition.greaterOrEqual;
import static io.jmix.core.querycondition.PropertyCondition.lessOrEqual;

@Route(value = ROUTE, layout = MainView.class)
@ViewDescriptor("payment-list-view.xml")
@ViewController(CrmConstants.ViewIds.PAYMENT_LIST)
@LookupComponent("paymentsDataGrid")
@DialogMode(width = "90%", resizable = true)
public class PaymentListView extends StandardListView<Payment> {

    public static final String ROUTE = "payments";

    @Autowired
    private CrmRenderers crmRenderers;
    @Autowired
    private PaymentRepository paymentRepository;

    @ViewComponent
    private CollectionLoader<Payment> paymentsDl;
    @ViewComponent
    private CollectionContainer<Invoice> invoicesDc;
    @ViewComponent
    private CollectionLoader<Invoice> invoicesDl;
    @ViewComponent
    private CollectionContainer<Order> ordersDc;
    @ViewComponent
    private CollectionLoader<Order> ordersDl;
    @ViewComponent
    private CollectionContainer<Client> clientsDc;
    @ViewComponent
    private CollectionLoader<Client> clientsDl;

    @ViewComponent
    private DataGrid<Payment> paymentsDataGrid;
    @ViewComponent
    private EntityComboBox<Client> payments_ClientComboBox;
    @ViewComponent
    private EntityComboBox<Order> payments_OrderComboBox;
    @ViewComponent
    private EntityComboBox<Invoice> payments_InvoiceComboBox;
    @ViewComponent
    private TypedDatePicker<LocalDate> payments_FromDatePicker;
    @ViewComponent
    private TypedDatePicker<LocalDate> payments_ToDatePicker;

    private final LogicalCondition filtersCondition = LogicalCondition.and();

    @Subscribe
    private void onInit(final InitEvent event) {
        installSortByCreatedDate(paymentsDl);
        installGridDefaultSorting();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        initialize();
    }

    @Install(to = "paymentsDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<Payment> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return paymentRepository.findAll(pageable, addCondition(context, filtersCondition)).getContent();
    }

    @Install(to = "payments_pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return paymentRepository.count(addCondition(context, filtersCondition));
    }

    @Install(to = "paymentsDataGrid.removeAction", subject = "delegate")
    private void paymentsDataGridRemoveDelegate(final Collection<Payment> collection) {
        paymentRepository.deleteAll(collection);
    }

    @Supply(to = "paymentsDataGrid.number", subject = "renderer")
    private Renderer<Payment> paymentsDataGridNumberRenderer() {
        return crmRenderers.uniqueNumber(Payment::getNumber);
    }

    @Supply(to = "paymentsDataGrid.[order.client]", subject = "renderer")
    private Renderer<Payment> paymentsDataGridOrderClientRenderer() {
        return crmRenderers.entityLink(paymentsDataGrid, p -> p.getOrder().getClient());
    }

    @Supply(to = "paymentsDataGrid.order", subject = "renderer")
    private Renderer<Payment> paymentsDataGridOrderRenderer() {
        return crmRenderers.entityLink(paymentsDataGrid, Payment::getOrder);
    }

    @Supply(to = "paymentsDataGrid.invoice", subject = "renderer")
    private Renderer<Payment> paymentsDataGridInvoiceRenderer() {
        return crmRenderers.entityLink(paymentsDataGrid, Payment::getInvoice);
    }

    private void installGridDefaultSorting() {
        if (paymentsDataGrid.getItems() instanceof DataGridItems.Sortable<Payment> sortable) {
            sortable.sort(new String[]{"date"}, new boolean[]{false});
        }
    }

    private void initialize() {
        loadData();
        registerUrlQueryParametersBinders();
        applyFilters();
        configureGrid();
    }

    private void loadData() {
        paymentsDl.load();
        clientsDl.load();
        ordersDl.load();
        invoicesDl.load();
    }

    private void configureGrid() {
        addRowSelectionInMultiSelectMode(paymentsDataGrid, "number");
    }

    private void registerUrlQueryParametersBinders() {
        applyFiltersOnValueChange(paymentsDl, this::applyFilters,
                payments_ClientComboBox, payments_OrderComboBox, payments_InvoiceComboBox,
                payments_FromDatePicker, payments_ToDatePicker);

        FieldValueQueryParameterBinder.builder(UiComponentUtils.getCurrentView())
                .addComboboxBinding(payments_OrderComboBox, () -> ordersDc.getItems())
                .addComboboxBinding(payments_ClientComboBox, () -> clientsDc.getItems())
                .addComboboxBinding(payments_InvoiceComboBox, () -> invoicesDc.getItems())
                .addDatePickerBinding(payments_FromDatePicker)
                .addDatePickerBinding(payments_ToDatePicker)
                .build();
    }

    private void applyFilters() {
        updateFiltersCondition();
        paymentsDl.load();
    }

    private void updateFiltersCondition() {
        filtersCondition.getConditions().clear();
        addSearchConditions();
    }

    private void addSearchConditions() {
        addSearchByOrderCondition();
        addSearchByClientCondition();
        addSearchByInvoiceCondition();
        addDateRangeConditions();
    }

    private void addSearchByClientCondition() {
        payments_ClientComboBox.getOptionalValue().ifPresent(client ->
                filtersCondition.add(equal("invoice.order.client", client)));
    }

    private void addSearchByOrderCondition() {
        payments_OrderComboBox.getOptionalValue().ifPresent(order ->
                filtersCondition.add(equal("invoice.order", order)));
    }

    private void addSearchByInvoiceCondition() {
        payments_InvoiceComboBox.getOptionalValue().ifPresent(invoice ->
                filtersCondition.add(equal("invoice", invoice)));
    }

    private void addDateRangeConditions() {
        addSearchByFromDateCondition();
        addSearchByToDateCondition();
    }

    private void addSearchByFromDateCondition() {
        payments_FromDatePicker.getOptionalValue().ifPresent(fromDate ->
                filtersCondition.add(greaterOrEqual("date", fromDate)));
    }

    private void addSearchByToDateCondition() {
        payments_ToDatePicker.getOptionalValue().ifPresent(fromDate ->
                filtersCondition.add(lessOrEqual("date", fromDate)));
    }
}