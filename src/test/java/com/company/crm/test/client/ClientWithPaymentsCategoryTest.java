package com.company.crm.test.client;

import com.company.crm.AbstractTest;
import com.company.crm.model.client.Client;
import com.company.crm.model.client.ClientRepository;
import com.company.crm.model.order.Order;
import com.company.crm.model.order.OrderStatus;
import io.jmix.core.querycondition.LogicalCondition;
import io.jmix.core.repository.JmixDataRepositoryContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static io.jmix.core.querycondition.PropertyCondition.isCollectionEmpty;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the condition behind the "With payments" category of the client list (issue #51).
 * <p>
 * The condition walks the nested collection path {@code invoices.payments}. Translated into a join, it
 * returns a client once per paid invoice and counts it the same way, which fills the grid with duplicates
 * and inflates the pagination total. Reproducible on Jmix 3.0.1, fixed by the framework in 3.0.2, and this
 * test fails again if a future version goes back to the join.
 */
class ClientWithPaymentsCategoryTest extends AbstractTest {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void clientWithSeveralPaidInvoicesIsReturnedAndCountedOnce() {
        Client withTwoPaidInvoices = entities.client("Category-A-two-paid-invoices");
        Client withUnpaidInvoice = entities.client("Category-B-unpaid-invoice");
        entities.client("Category-C-no-invoices");

        Order firstOrder = entities.order(withTwoPaidInvoices, LocalDate.now(), OrderStatus.DONE);
        Order secondOrder = entities.order(withTwoPaidInvoices, LocalDate.now(), OrderStatus.DONE);
        entities.payment(entities.invoice(withTwoPaidInvoices, firstOrder), LocalDate.now());
        entities.payment(entities.invoice(withTwoPaidInvoices, secondOrder), LocalDate.now());

        entities.invoice(withUnpaidInvoice, entities.order(withUnpaidInvoice, LocalDate.now(), OrderStatus.DONE));

        JmixDataRepositoryContext context = JmixDataRepositoryContext
                .condition(LogicalCondition.and(isCollectionEmpty("invoices.payments", false)))
                .build();

        List<Client> found = clientRepository.findAll(Pageable.unpaged(), context).getContent();

        assertThat(found).containsExactly(withTwoPaidInvoices);
        assertThat(clientRepository.count(context)).isEqualTo(1);
    }
}
