package com.company.crm.view.invoice;

import com.company.crm.app.feature.queryparameters.filters.FieldValueQueryParameterBinder;
import com.company.crm.app.service.finance.InvoiceService;
import com.company.crm.app.ui.component.CrmCard;
import com.company.crm.app.util.constant.CrmConstants;
import com.company.crm.app.util.report.CrmReportUtils;
import com.company.crm.app.util.ui.CrmUiUtils;
import com.company.crm.app.util.ui.renderer.CrmRenderers;
import com.company.crm.app.util.ui.theme.CrmStyleUtility;
import com.company.crm.model.client.Client;
import com.company.crm.model.contact.Contact;
import com.company.crm.model.invoice.Invoice;
import com.company.crm.model.invoice.InvoiceRepository;
import com.company.crm.model.invoice.InvoiceStatus;
import com.company.crm.model.order.Order;
import com.company.crm.report.CategoryCashflowRiskReport;
import com.company.crm.view.main.MainView;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Unit;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.component.button.JmixButton;
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
import io.jmix.reports.entity.ReportOutputType;
import io.jmix.reportsflowui.runner.UiReportRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.company.crm.app.util.ui.CrmUiUtils.addRowSelectionInMultiSelectMode;
import static com.company.crm.app.util.ui.CrmUiUtils.setBadge;
import static com.company.crm.app.util.ui.color.EnumClassColors.getBadgeVariant;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.addCondition;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.applyFiltersOnValueChange;
import static com.company.crm.app.util.ui.datacontext.DataContextUtils.installSortByCreatedDate;
import static com.company.crm.view.invoice.InvoiceListView.ROUTE;
import static io.jmix.core.querycondition.PropertyCondition.equal;
import static io.jmix.core.querycondition.PropertyCondition.greaterOrEqual;
import static io.jmix.core.querycondition.PropertyCondition.lessOrEqual;
import static io.jmix.flowui.component.UiComponentUtils.getCurrentView;

@Route(value = ROUTE, layout = MainView.class)
@ViewDescriptor("invoice-list-view.xml")
@ViewController(CrmConstants.ViewIds.INVOICE_LIST)
@LookupComponent("invoicesDataGrid")
@PrimaryListView(Invoice.class)
@DialogMode(width = "90%", resizable = true)
public class InvoiceListView extends StandardListView<Invoice> {

    public static final String ROUTE = "invoices";

    @Autowired
    private Messages messages;
    @Autowired
    private CrmRenderers crmRenderers;
    @Autowired
    private CrmReportUtils crmReportUtils;
    @Autowired
    private Notifications notifications;
    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private UiReportRunner uiReportRunner;

    @ViewComponent
    private CollectionContainer<Order> ordersDc;
    @ViewComponent
    private CollectionLoader<Order> ordersDl;
    @ViewComponent
    private CollectionContainer<Client> clientsDc;
    @ViewComponent
    private CollectionLoader<Client> clientsDl;

    @ViewComponent
    private CrmCard invoiceStatusCard;
    @ViewComponent
    private EntityComboBox<Client> invoices_ClientComboBox;
    @ViewComponent
    private EntityComboBox<Order> invoices_OrderComboBox;
    @ViewComponent
    private JmixSelect<InvoiceStatus> invoices_StatusSelect;
    @ViewComponent
    private TypedDatePicker<LocalDate> invoices_FromDatePicker;
    @ViewComponent
    private TypedDatePicker<LocalDate> invoices_ToDatePicker;
    @ViewComponent
    private CollectionLoader<Invoice> invoicesDl;
    @ViewComponent
    private DataGrid<Invoice> invoicesDataGrid;

    private final LogicalCondition filtersCondition = LogicalCondition.and();

    @Subscribe
    private void onInit(final InitEvent event) {
        installSortByCreatedDate(invoicesDl);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        initialize();
    }

    @Install(to = "invoicesDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<Invoice> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return invoiceRepository.findAll(pageable, addCondition(context, filtersCondition)).getContent();
    }

    @Install(to = "invoices_pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return invoiceRepository.count(addCondition(context, filtersCondition));
    }

    @Install(to = "invoicesDataGrid.removeAction", subject = "delegate")
    private void invoicesDataGridRemoveDelegate(final Collection<Invoice> collection) {
        invoiceRepository.deleteAll(collection);
    }

    @Supply(to = "invoicesDataGrid.itemDetails", subject = "renderer")
    private Renderer<Invoice> invoicesDataGridItemDetailsRenderer() {
        return crmRenderers.itemDetailsColumnRenderer(invoicesDataGrid);
    }

    @Supply(to = "invoicesDataGrid.status", subject = "renderer")
    private Renderer<Invoice> invoicesDataGridStatusRenderer() {
        return crmRenderers.invoiceStatus();
    }

    @Supply(to = "invoicesDataGrid.[order.number]", subject = "renderer")
    private Renderer<Invoice> invoicesDataGridOrderNumberRenderer() {
        return crmRenderers.invoiceOrderLink(invoicesDataGrid);
    }

    @Supply(to = "invoicesDataGrid.number", subject = "renderer")
    private Renderer<Invoice> invoicesDataGridNumberRenderer() {
        return crmRenderers.uniqueNumber(Invoice::getNumber);
    }

    @Supply(to = "invoicesDataGrid.client", subject = "renderer")
    private Renderer<Invoice> invoicesDataGridClientRenderer() {
        return crmRenderers.invoiceClientLink(invoicesDataGrid);
    }

    @Install(to = "invoicesDataGrid.emailAction", subject = "enabledRule")
    private boolean invoicesDataGridEmailActionEnabledRule() {
        return invoicesDataGrid.getSelectedItems().size() == 1;
    }

    @Install(to = "invoicesDataGrid.downloadAction", subject = "enabledRule")
    private boolean invoicesDataGridDownloadActionEnabledRule() {
        return invoicesDataGrid.getSelectedItems().size() == 1;
    }

    @Subscribe("invoicesDataGrid.downloadAction")
    private void onInvoicesDataGridDownloadAction(final ActionPerformedEvent event) {
        Invoice invoice = invoicesDataGrid.getSingleSelectedItem();
        if (invoice != null) {
            crmReportUtils.runAndDownloadReport(invoice);
        }
    }

    @Subscribe("invoicesDataGrid.emailAction")
    private void onInvoicesDataGridEmailAction(final ActionPerformedEvent event) {
        Invoice invoice = invoicesDataGrid.getSingleSelectedItem();
        if (invoice == null) {
            return;
        }

        List<Contact> contacts = invoice.getClient().getContacts();
        if (contacts.isEmpty()) {
            notifications.create("Client has no contacts to send email").withType(Notifications.Type.ERROR).show();
        }

        List<String> emails = contacts.stream().map(Contact::getEmail).toList();
        CrmUiUtils.showEmailSendingDialog(emails, false);
    }

    @Subscribe(id = "runCategoryCashflowRiskReportButton", subject = "clickListener")
    private void onRunCategoryCashflowRiskReportButtonClick(final ClickEvent<JmixButton> event) {
        var runner = uiReportRunner.byReportCode(CategoryCashflowRiskReport.CODE)
                .withTemplateCode("XLSX")
                .withOutputType(ReportOutputType.XLSX);

        Date fromDate = toDate(invoices_FromDatePicker.getValue());
        if (fromDate != null) {
            runner.addParam("fromDate", fromDate);
        }

        Date toDate = toDate(invoices_ToDatePicker.getValue());
        if (toDate != null) {
            runner.addParam("toDate", toDate);
        }

        invoices_ClientComboBox.getOptionalValue().ifPresent(client -> runner.addParam("client", client));
        runner.addParam("asOfDate", toDate(LocalDate.now()));
        runner.runAndShow();
    }

    private void initialize() {
        loadData();
        registerUrlQueryParametersBinders();
        applyFilters();
        configureGrid();
    }

    private void loadData() {
        clientsDl.load();
        ordersDl.load();
    }

    private void configureGrid() {
        addRowSelectionInMultiSelectMode(invoicesDataGrid, "itemDetails", "number");
        invoicesDataGrid.setItemDetailsRenderer(crmRenderers.invoiceDetails());
        invoicesDataGrid.setDetailsVisibleOnClick(false);
    }

    private void applyFilters() {
        updateFiltersCondition();
        updateInvoiceStatusCard();
        invoicesDl.load();
    }

    private void updateFiltersCondition() {
        filtersCondition.getConditions().clear();
        addSearchConditions();
    }

    private void addSearchConditions() {
        addSearchByOrderCondition();
        addSearchByClientCondition();
        addSearchBySelectedStatus();
        addDateRangeConditions();
    }

    private void addSearchBySelectedStatus() {
        invoices_StatusSelect.getOptionalValue().ifPresent(status ->
                filtersCondition.add(equal("status", status)));
    }

    private void addSearchByClientCondition() {
        invoices_ClientComboBox.getOptionalValue().ifPresent(client ->
                filtersCondition.add(equal("client", client)));
    }

    private void addSearchByOrderCondition() {
        invoices_OrderComboBox.getOptionalValue().ifPresent(order ->
                filtersCondition.add(equal("order", order)));
    }

    private void addDateRangeConditions() {
        addSearchByFromDateCondition();
        addSearchByToDateCondition();
    }

    private void addSearchByFromDateCondition() {
        invoices_FromDatePicker.getOptionalValue().ifPresent(fromDate ->
                filtersCondition.add(greaterOrEqual("date", fromDate)));
    }

    private void addSearchByToDateCondition() {
        invoices_ToDatePicker.getOptionalValue().ifPresent(fromDate ->
                filtersCondition.add(lessOrEqual("date", fromDate)));
    }

    private void registerUrlQueryParametersBinders() {
        applyFiltersOnValueChange(invoicesDl, this::applyFilters,
                invoices_ClientComboBox, invoices_OrderComboBox, invoices_StatusSelect,
                invoices_FromDatePicker, invoices_ToDatePicker);

        //noinspection unchecked
        FieldValueQueryParameterBinder.builder(getCurrentView())
                .addEnumBinding(InvoiceStatus.class, invoices_StatusSelect)
                .addComboboxBinding(invoices_OrderComboBox, () -> ordersDc.getItems())
                .addComboboxBinding(invoices_ClientComboBox, () -> clientsDc.getItems())
                .addDatePickerBinding(invoices_FromDatePicker)
                .addDatePickerBinding(invoices_ToDatePicker)
                .build();
    }

    private void updateInvoiceStatusCard() {
        var layout = createStatusCountsLayout();
        List.of(InvoiceStatus.PENDING, InvoiceStatus.PAID, InvoiceStatus.OVERDUE).forEach(status -> {
            long paidCount = invoiceService.getInvoicesCount(status);
            layout.add(createStatusCountBlock(status, paidCount));
        });
        invoiceStatusCard.fillAsStaticCard(messages.getMessage("com.company.crm.view.invoice/statusCounts"), layout);
    }

    private HorizontalLayout createStatusCountsLayout() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.CENTER);
        layout.setJustifyContentMode(JustifyContentMode.CENTER);
        return layout;
    }

    private Component createStatusCountBlock(InvoiceStatus status, long count) {
        Span statusLabel = new Span(messages.getMessage(status));
        setBadge(statusLabel, getBadgeVariant(status));

        String countValue;
        if (count > 1000) {
            countValue = "1000+";
        } else {
            countValue = String.valueOf(count);
        }

        VerticalLayout block = new VerticalLayout(statusLabel, new H1(countValue));
        block.setSpacing(false);
        block.setPadding(false);
        block.setMaxWidth(8, Unit.EM);
        CrmUiUtils.setClickableCursor(block);
        block.setAlignItems(Alignment.CENTER);
        block.setJustifyContentMode(JustifyContentMode.CENTER);
        block.addClassName(Objects.equals(status, invoices_StatusSelect.getValue())
                ? CrmStyleUtility.Background.CONTRAST_10 : CrmStyleUtility.Background.CONTRAST_5);
        block.addClassNames(CrmStyleUtility.BorderRadius.FULL, CrmStyleUtility.Margin.AUTO, CrmStyleUtility.Padding.Bottom.MEDIUM);
        block.addClickListener(e -> {
            boolean unselectStatus = Objects.equals(status, invoices_StatusSelect.getValue());
            invoices_StatusSelect.setValue(unselectStatus ? null : status);
        });

        return block;
    }

    private Date toDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
