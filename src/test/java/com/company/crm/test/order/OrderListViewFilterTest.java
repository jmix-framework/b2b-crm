package com.company.crm.test.order;

import com.company.crm.AbstractUiTest;
import com.company.crm.app.ui.component.OrderStatusPipeline;
import com.company.crm.app.ui.component.OrderStatusPipeline.OrderStatusComponent;
import com.company.crm.model.client.Client;
import com.company.crm.model.order.Order;
import com.company.crm.model.order.OrderStatus;
import com.company.crm.view.order.OrderListView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.router.QueryParameters;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.ViewControllerUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the status pipeline of the order list (issue #35): paging must not leak into the next filter,
 * several statuses can be selected at once, and the pipeline counts cover all matching orders.
 */
class OrderListViewFilterTest extends AbstractUiTest {

    private static final String SELECTED_STATUS_PARAM = "selected_status";

    @Autowired
    private ViewNavigators viewNavigators;

    @Test
    void changingTheStatusFilterAfterTheLastPageShowsTheFirstPage() {
        Client client = entities.client("Order pipeline paging");
        givenOrders(client, OrderStatus.NEW, OrderStatus.NEW, OrderStatus.NEW, OrderStatus.DONE);

        OrderListView view = viewTestSupport.navigateTo(OrderListView.class);
        CollectionLoader<Order> ordersDl = loaderOf(view);
        CollectionContainer<Order> ordersDc = containerOf(view);
        OrderStatusPipeline pipeline = UiTestUtils.getComponent(view, "pipeLineFilter");

        ordersDl.setMaxResults(2);
        clickStatus(pipeline, OrderStatus.NEW);
        clickStatus(pipeline, OrderStatus.DONE);
        assertThat(ordersDc.getItems()).hasSize(2);

        // this is what the "last page" button of the pagination does
        ordersDl.setFirstResult(2);
        ordersDl.load();
        assertThat(ordersDc.getItems()).hasSize(2);

        // only one order is left under the narrowed filter, so the stale offset would show an empty grid
        clickStatus(pipeline, OrderStatus.NEW);

        assertThat(ordersDl.getFirstResult()).isZero();
        assertThat(ordersDc.getItems())
                .extracting(Order::getStatus)
                .containsExactly(OrderStatus.DONE);
    }

    @Test
    void selectingTwoStatusesFiltersByBothOfThem() {
        Client client = entities.client("Order pipeline multiselect");
        givenOrders(client, OrderStatus.NEW, OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.DONE);

        OrderListView view = viewTestSupport.navigateTo(OrderListView.class);
        CollectionContainer<Order> ordersDc = containerOf(view);
        OrderStatusPipeline pipeline = UiTestUtils.getComponent(view, "pipeLineFilter");

        clickStatus(pipeline, OrderStatus.NEW);
        clickStatus(pipeline, OrderStatus.ACCEPTED);

        assertThat(ordersDc.getItems())
                .extracting(Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.NEW, OrderStatus.NEW, OrderStatus.ACCEPTED);
        assertThat(isSelected(pipeline, OrderStatus.NEW)).isTrue();
        assertThat(isSelected(pipeline, OrderStatus.ACCEPTED)).isTrue();
        assertThat(isSelected(pipeline, OrderStatus.DONE)).isFalse();
    }

    @Test
    void clickingTheSelectedStatusAgainClearsTheStatusFilter() {
        Client client = entities.client("Order pipeline toggle");
        givenOrders(client, OrderStatus.NEW, OrderStatus.DONE);

        OrderListView view = viewTestSupport.navigateTo(OrderListView.class);
        CollectionContainer<Order> ordersDc = containerOf(view);
        OrderStatusPipeline pipeline = UiTestUtils.getComponent(view, "pipeLineFilter");

        clickStatus(pipeline, OrderStatus.NEW);
        assertThat(ordersDc.getItems()).hasSize(1);

        clickStatus(pipeline, OrderStatus.NEW);

        assertThat(ordersDc.getItems()).hasSize(2);
        assertThat(isSelected(pipeline, OrderStatus.NEW)).isFalse();
    }

    @Test
    void pipelineCountsAllMatchingOrdersNotOnlyTheCurrentPage() {
        Client client = entities.client("Order pipeline counts");
        givenOrders(client, OrderStatus.NEW, OrderStatus.NEW, OrderStatus.NEW, OrderStatus.DONE);

        OrderListView view = viewTestSupport.navigateTo(OrderListView.class);
        CollectionLoader<Order> ordersDl = loaderOf(view);
        OrderStatusPipeline pipeline = UiTestUtils.getComponent(view, "pipeLineFilter");

        ordersDl.setMaxResults(2);
        ordersDl.load();

        assertThat(titleOf(pipeline, OrderStatus.NEW)).endsWith("(3)");
        assertThat(titleOf(pipeline, OrderStatus.DONE)).endsWith("(1)");
        assertThat(titleOf(pipeline, OrderStatus.ACCEPTED)).endsWith("(0)");
    }

    @Test
    void pipelineCountsStayVisibleForTheStatusesThatAreNotSelected() {
        Client client = entities.client("Order pipeline counts while filtered");
        givenOrders(client, OrderStatus.NEW, OrderStatus.NEW, OrderStatus.DONE);

        OrderListView view = viewTestSupport.navigateTo(OrderListView.class);
        OrderStatusPipeline pipeline = UiTestUtils.getComponent(view, "pipeLineFilter");

        clickStatus(pipeline, OrderStatus.NEW);

        assertThat(titleOf(pipeline, OrderStatus.NEW)).endsWith("(2)");
        assertThat(titleOf(pipeline, OrderStatus.DONE)).endsWith("(1)");
    }

    @Test
    void restoresSelectedStatusesFromTheUrl() {
        Client client = entities.client("Order pipeline url");
        givenOrders(client, OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.DONE);

        OrderListView view = navigateWithStatusParameter("10,20");

        assertThat(containerOf(view).getItems())
                .extracting(Order::getStatus)
                .containsExactlyInAnyOrder(OrderStatus.NEW, OrderStatus.ACCEPTED);
    }

    @Test
    void restoresASingleStatusFromTheUrlWrittenBeforeMultiselect() {
        Client client = entities.client("Order pipeline legacy url");
        givenOrders(client, OrderStatus.NEW, OrderStatus.DONE);

        OrderListView view = navigateWithStatusParameter("40");

        assertThat(containerOf(view).getItems())
                .extracting(Order::getStatus)
                .containsExactly(OrderStatus.DONE);
    }

    private void givenOrders(Client client, OrderStatus... statuses) {
        for (OrderStatus status : statuses) {
            entities.order(client, LocalDate.now(), status);
        }
    }

    private OrderListView navigateWithStatusParameter(String value) {
        viewNavigators.view(UiTestUtils.getCurrentView(), OrderListView.class)
                .withQueryParameters(QueryParameters.of(SELECTED_STATUS_PARAM, value))
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    private static CollectionLoader<Order> loaderOf(OrderListView view) {
        return ViewControllerUtils.getViewData(view).getLoader("ordersDl");
    }

    private static CollectionContainer<Order> containerOf(OrderListView view) {
        return ViewControllerUtils.getViewData(view).getContainer("ordersDc");
    }

    private static void clickStatus(OrderStatusPipeline pipeline, OrderStatus status) {
        OrderStatusComponent component = statusComponent(pipeline, status);
        // the pipeline reacts to client-originated clicks only
        ComponentUtil.fireEvent(component,
                new ClickEvent<>(component, true, 0, 0, 0, 0, 1, 0, false, false, false, false));
    }

    private static boolean isSelected(OrderStatusPipeline pipeline, OrderStatus status) {
        return statusComponent(pipeline, status).getThemeNames().contains("selected");
    }

    private static String titleOf(OrderStatusPipeline pipeline, OrderStatus status) {
        return statusComponent(pipeline, status).getTitle();
    }

    private static OrderStatusComponent statusComponent(OrderStatusPipeline pipeline, OrderStatus status) {
        return pipeline.getStatusComponents()
                .filter(component -> component.getStatus() == status)
                .findFirst()
                .orElseThrow();
    }
}
